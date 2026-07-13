package com.inik.camcon.data.repository

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.inik.camcon.R
import com.inik.camcon.data.cache.CacheSweeper
import com.inik.camcon.data.datasource.nativesource.CameraCaptureListener
import com.inik.camcon.data.datasource.nativesource.LiveViewCallback
import com.inik.camcon.data.datasource.nativesource.NativeCameraDataSource
import com.inik.camcon.data.datasource.ptpip.PtpipDataSource
import com.inik.camcon.data.datasource.ptpip.PtpipPhotoEvent
import com.inik.camcon.data.datasource.usb.UsbCameraManager
import com.inik.camcon.data.repository.managers.CameraConnectionManager
import com.inik.camcon.data.repository.managers.CameraEventManager
import com.inik.camcon.data.repository.managers.PhotoDownloadManager
import com.inik.camcon.data.repository.managers.TransferProgressTracker
import com.inik.camcon.data.util.ExifCaptureTime
import com.inik.camcon.di.ApplicationScope
import com.inik.camcon.di.IoDispatcher
import com.inik.camcon.domain.cache.ProcessedFileCache
import com.inik.camcon.domain.manager.ErrorNotifier
import com.inik.camcon.domain.manager.ErrorSeverity
import com.inik.camcon.domain.manager.ErrorType
import com.inik.camcon.domain.model.BracketingSettings
import com.inik.camcon.domain.model.CameraSettings
import com.inik.camcon.domain.model.CapturedPhoto
import com.inik.camcon.domain.model.LiveViewFrame
import com.inik.camcon.domain.model.ShootingMode
import com.inik.camcon.domain.model.TimelapseSettings
import com.inik.camcon.domain.model.TransferQueueState
import com.inik.camcon.domain.model.UnsupportedShootingModeException
import com.inik.camcon.utils.Constants
import com.inik.camcon.utils.LogMask
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 카메라 이벤트 리스너·촬영·라이브뷰·캡처 사진 StateFlow·dedup 캐시 담당 sub-impl.
 *
 * H8 분해: 원본 CameraRepositoryImpl의 Event(3) + Capture(6) + LiveView(2) + CapturedPhotos(3) = 14개 override 이동.
 *
 * 공유 상태 소유:
 *  - `processedFileCache` LRU 1000 + TTL 24h (다운로드 콜백 중복 방지, lazy on read + 1h sweep)
 *  - `_capturedPhotos: MutableStateFlow<List<CapturedPhoto>>`
 *
 * 외부 의존: `CameraSettings?` 현재 값을 Facade 콜백(`cameraSettingsProvider`)으로 받아 PTPIP/capture 메타데이터에 사용.
 * Facade가 Control의 `getCachedSettings()`를 provider로 주입한다.
 */
@Singleton
class CameraCaptureRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val nativeDataSource: NativeCameraDataSource,
    private val ptpipDataSource: PtpipDataSource,
    private val usbCameraManager: UsbCameraManager,
    private val connectionManager: CameraConnectionManager,
    private val eventManager: CameraEventManager,
    private val downloadManager: PhotoDownloadManager,
    private val transferProgressTracker: TransferProgressTracker,
    private val errorNotifier: ErrorNotifier,
    private val processedFileCache: ProcessedFileCache,
    @Suppress("unused") private val cacheSweeper: CacheSweeper,
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    companion object {
        private const val TAG = "카메라캡처레포"

        /** 세션 내 capturedPhotos StateFlow 상한 (장시간 테더링 메모리 누수 방지) */
        private const val MAX_CAPTURED_PHOTOS = 1000

        /** 라이브뷰 첫 프레임 대기 상한 — 초과 시 네이티브 무응답으로 보고 재시도 가능한 에러로 종료 */
        private const val LIVEVIEW_FIRST_FRAME_TIMEOUT_MS = 5000L

        /** capturedPhotos 정렬 기준 — captureTime 오름차순, 동률이면 basename·확장자 순 */
        private val PHOTO_ORDER_COMPARATOR = compareBy<CapturedPhoto>(
            { it.captureTime },
            { it.filePath.substringAfterLast("/").substringBeforeLast(".") },
            { it.filePath.substringAfterLast(".") }
        )
    }

    /**
     * Facade가 주입하는 설정 공급자. 기본값 null (Facade 주입 전·테스트).
     */
    @Volatile
    var cameraSettingsProvider: () -> CameraSettings? = { null }

    /** Facade가 주입하는 flush 완료 훅 (CameraStateObserver.updateCameraInitialization(false)). */
    @Volatile
    var onFlushCompleteCallback: (() -> Unit)? = null

    private val _capturedPhotos = MutableStateFlow<List<CapturedPhoto>>(emptyList())

    /** 네이티브 사진 다운로드 잡 — 유계 큐 항목 */
    private class NativeDownloadJob(
        val fullPath: String,
        val fileName: String,
        val imageData: ByteArray
    )

    // 연사 버스트 시 콜백마다 코루틴을 띄우면 25-30MB ByteArray가 코루틴 수만큼
    // 동시에 적체되어 OOM이 난다. 유계 큐(3) + 단일 컨슈머로 동시 보유 메모리를 캡한다.
    private val nativeDownloadQueue = Channel<NativeDownloadJob>(capacity = 3)
    private val nativeDownloadConsumerStarted = AtomicBoolean(false)

    /**
     * 첫 잡 투입 시 1회만 컨슈머 코루틴을 기동한다.
     * (생성자(init)에서 launch 하면 테스트의 모킹된 scope 에서 생성 자체가 실패하므로 지연 기동)
     */
    private fun ensureNativeDownloadConsumer() {
        if (!nativeDownloadConsumerStarted.compareAndSet(false, true)) return
        // 단일 컨슈머 — 잡 단위 예외 격리로 루프 생존 보장
        scope.launch(ioDispatcher) {
            for (job in nativeDownloadQueue) {
                try {
                    processNativePhotoDownload(job)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "네이티브 사진 처리 실패: ${job.fileName}", e)
                    processedFileCache.remove(job.fileName)
                    updatePhotoDownloadFailed(job.fileName)
                }
            }
        }
    }

    // ── 테스트 헬퍼 (Issue C5 dedup 캐시 회귀 테스트) ──
    // 외부 시그니처 보존, 내부는 ProcessedFileCache(LRU 1000 + TTL 24h) 직접 위임.
    @VisibleForTesting
    fun markFileAsProcessed(filePath: String) {
        processedFileCache.add(filePath)
    }

    @VisibleForTesting
    fun isFileProcessed(filePath: String): Boolean {
        return processedFileCache.contains(filePath)
    }

    @VisibleForTesting
    fun getProcessedFilesCount(): Int {
        return processedFileCache.size()
    }

    @VisibleForTesting
    fun clearProcessedFiles() {
        processedFileCache.clear()
    }

    /**
     * 정렬/중복 회귀 테스트용 시드(요구: captureTime 오름차순·동일 파일명 dedup).
     * 프로덕션의 [updateDownloadedPhoto] 동일 경로를 그대로 통과시켜 정렬·중복 안전망을 검증한다.
     * 동작 변경 없음 — 기존 private 로직에 대한 테스트 진입점만 노출한다.
     */
    @VisibleForTesting
    fun seedDownloadedPhotoForTest(photo: CapturedPhoto) {
        updateDownloadedPhoto(photo)
    }

    /**
     * 다운로드 실패 키 정합(basename) 회귀 테스트용 진입점. 프로덕션의 [updatePhotoDownloadFailed]
     * 동일 경로를 그대로 통과시킨다(동작 변경 없음 — private 로직에 대한 테스트 진입점만 노출).
     */
    @VisibleForTesting
    fun markPhotoDownloadFailedForTest(fileName: String) {
        updatePhotoDownloadFailed(fileName)
    }

    // ── Event Listener ──

    fun isEventListenerActive(): Flow<Boolean> =
        eventManager.isEventListenerActive

    // 카메라 본체 설정(노출) 변경 푸시 — 폴링 대체. 수집측이 debounce 후 경량 재조회.
    fun settingChanged(): Flow<Unit> =
        eventManager.settingChanged

    fun isPhotoPreviewMode(): Flow<Boolean> =
        eventManager.isPhotoPreviewModeFlow

    fun setPhotoPreviewMode(enabled: Boolean) {
        eventManager.setPhotoPreviewMode(enabled)
    }

    suspend fun startCameraEventListener(): Result<Boolean> {
        // Wi-Fi(PTPIP) 연결은 PtpipDataSource의 자체 저장 콜백 경로로 재시작해야 한다.
        // (아래 USB 경로는 usbCameraManager 연결 검증 때문에 Wi-Fi에서 항상 실패 —
        //  BackgroundSyncService 감독 루프가 Wi-Fi 리스너를 복구하지 못하던 원인)
        if (connectionManager.isPtpipConnected.value &&
            !usbCameraManager.isNativeCameraConnected.value
        ) {
            com.inik.camcon.utils.LogcatManager.d(TAG, "startCameraEventListener 호출됨 (PTPIP 연결용)")
            return ptpipDataSource.restartEventListenerIfNeeded()
        }

        com.inik.camcon.utils.LogcatManager.d(TAG, "startCameraEventListener 호출됨 (USB 연결용)")

        return eventManager.startCameraEventListener(
            isConnected = connectionManager.isConnected.value,
            isInitializing = connectionManager.isInitializing.value,
            saveDirectory = downloadManager.getSaveDirectory(),
            onPhotoCaptured = { _, _ ->
                // handleNativePhotoDownload에서만 처리하므로 빈 콜백
            },
            onPhotoDownloaded = { fullPath, fileName, imageData ->
                handlePhotoDownloadCallback(fullPath, fileName, imageData)
            },
            onFlushComplete = {
                com.inik.camcon.utils.LogcatManager.d(
                    TAG,
                    "카메라 이벤트 플러시 완료 - 초기화 상태 해제 (isCameraInitializing = false)"
                )
                onFlushCompleteCallback?.invoke()
            },
            onCaptureFailed = { errorCode ->
                Log.e(TAG, "외부 셔터 촬영 실패: $errorCode")
            }
        )
    }

    /**
     * 사진 다운로드 콜백 처리 — 중복 방지 + handleNativePhotoDownload
     */
    private fun handlePhotoDownloadCallback(
        fullPath: String,
        fileName: String,
        imageData: ByteArray
    ) {
        com.inik.camcon.utils.LogcatManager.d(
            TAG,
            "onPhotoDownloaded 콜백 호출됨: $fileName (size=${imageData.size})"
        )

        // USB 경로 중복 방지 키 — fileName 단독.
        // (fullPath/size 조합은 동일 컷이 경로/크기 미세 차이로 중복 통과할 수 있어 fileName 으로 강화.)
        // 실패 복구 remove 키(handleNativePhotoDownload)와 반드시 동일해야 재시도 차단 해제가 동작한다.
        val fileKey = fileName

        if (shouldProcessFile(fileKey)) {
            com.inik.camcon.utils.LogcatManager.d(TAG, "handleNativePhotoDownload 호출: $fileName")
            handleNativePhotoDownload(fullPath, fileName, imageData)
        } else {
            com.inik.camcon.utils.LogcatManager.d(TAG, "중복으로 인해 처리 건너뜀: $fileName")
            // 중복 스킵 파일은 handleNativePhotoDownload 로 넘어가지 않으므로,
            // onPhotoCaptured 가 미리 markDownloading 한 듀얼슬롯/중복 파일을 진행 큐에서 제거(누수 방지).
            transferProgressTracker.markDone(fileName)
        }
    }

    private fun shouldProcessFile(fileKey: String): Boolean = processedFileCache.add(fileKey)

    suspend fun stopCameraEventListener(): Result<Boolean> {
        return eventManager.stopCameraEventListener()
    }

    /** Facade가 Control의 getCameraPhotos(Paged) 콜백으로 주입. */
    suspend fun restartEventListenerIfNeeded() {
        if (!eventManager.isRunning()) {
            // Wi-Fi(PTPIP)는 자체 콜백 경로로 복구 (USB 내부 시작은 Wi-Fi에서 항상 실패)
            if (connectionManager.isPtpipConnected.value &&
                !usbCameraManager.isNativeCameraConnected.value
            ) {
                ptpipDataSource.restartEventListenerIfNeeded()
            } else {
                startEventListenerInternal()
            }
        }
    }

    /**
     * 내부용 이벤트 리스너 시작 (중복 처리 방지를 위해 onPhotoDownloaded=null)
     */
    private suspend fun startEventListenerInternal() {
        com.inik.camcon.utils.LogcatManager.d(TAG, "startEventListenerInternal 호출됨 (내부용 - 중복 방지)")

        eventManager.startCameraEventListener(
            isConnected = connectionManager.isConnected.value,
            isInitializing = connectionManager.isInitializing.value,
            saveDirectory = downloadManager.getSaveDirectory(),
            onPhotoCaptured = { _, _ ->
                // handleNativePhotoDownload에서만 처리하므로 빈 콜백
            },
            onPhotoDownloaded = null,
            onFlushComplete = {
                com.inik.camcon.utils.LogcatManager.d(
                    TAG,
                    "카메라 이벤트 플러시 완료 - 초기화 상태 해제 (isCameraInitializing = false)"
                )
                onFlushCompleteCallback?.invoke()
            },
            onCaptureFailed = { errorCode ->
                Log.e(TAG, "외부 셔터 촬영 실패: $errorCode")
            }
        )
    }

    // ── Capture ──

    suspend fun capturePhoto(mode: ShootingMode): Result<CapturedPhoto> {
        return suspendCancellableCoroutine<Result<CapturedPhoto>> { continuation ->
            val saveDir = downloadManager.getSaveDirectory()
            com.inik.camcon.utils.LogcatManager.d(
                TAG,
                "사진 촬영 시작: 모드=$mode, 저장=${LogMask.path(saveDir)}, 연결=${connectionManager.isConnected.value}"
            )

            if (!validateShootingMode(mode, continuation)) {
                return@suspendCancellableCoroutine
            }
            if (!validateCameraConnection(continuation)) {
                return@suspendCancellableCoroutine
            }

            try {
                com.inik.camcon.utils.LogcatManager.d(TAG, "비동기 사진 촬영 호출 시작")
                continuation.invokeOnCancellation {
                    com.inik.camcon.utils.LogcatManager.d(TAG, "사진 촬영 취소됨")
                }

                nativeDataSource.capturePhotoAsync(createCaptureListener(continuation), saveDir)
                com.inik.camcon.utils.LogcatManager.d(TAG, "비동기 사진 촬영 호출 완료, 콜백 대기 중...")
            } catch (e: Exception) {
                Log.e(TAG, "사진 촬영 중 예외 발생", e)
                if (continuation.isActive) {
                    continuation.resumeWithException(e)
                }
            }
        }
    }

    private fun validateShootingMode(
        mode: ShootingMode,
        continuation: CancellableContinuation<Result<CapturedPhoto>>
    ): Boolean {
        val unsupportedModes = setOf(
            ShootingMode.BURST,
            ShootingMode.TIMELAPSE,
            ShootingMode.HDR_BRACKET,
            ShootingMode.BULB
        )

        if (mode in unsupportedModes) {
            Log.w(TAG, "미구현 촬영 모드: $mode")
            val exception = UnsupportedShootingModeException(
                mode = mode,
                supportedModes = listOf(ShootingMode.SINGLE)
            )
            if (continuation.isActive) {
                continuation.resume(Result.failure(exception))
            }
            return false
        }
        return true
    }

    private fun validateCameraConnection(
        continuation: CancellableContinuation<Result<CapturedPhoto>>
    ): Boolean {
        val isAnyConnectionActive = connectionManager.isConnected.value ||
                connectionManager.isPtpipConnected.value
        if (!isAnyConnectionActive) {
            Log.e(TAG, "카메라가 연결되지 않은 상태에서 사진 촬영 불가")
            if (continuation.isActive) {
                continuation.resumeWithException(Exception("카메라가 연결되지 않음"))
            }
            return false
        }
        return true
    }

    private fun createCaptureListener(
        continuation: CancellableContinuation<Result<CapturedPhoto>>
    ): CameraCaptureListener {
        return object : CameraCaptureListener {
            override fun onFlushComplete() {
                com.inik.camcon.utils.LogcatManager.d(TAG, "사진 촬영 플러시 완료")
            }

            override fun onPhotoCaptured(fullPath: String, fileName: String) {
                handlePhotoCaptured(fullPath, fileName, continuation)
            }

            override fun onPhotoDownloaded(
                filePath: String,
                fileName: String,
                imageData: ByteArray
            ) {
                com.inik.camcon.utils.LogcatManager.d(
                    TAG,
                    "Native 사진 다운로드 완료: $fileName (${imageData.size / 1024}KB)"
                )
                handleNativePhotoDownload(filePath, fileName, imageData)
            }

            override fun onCaptureFailed(errorCode: Int) {
                Log.e(TAG, "사진 촬영 실패, 오류 코드: $errorCode")
                if (continuation.isActive) {
                    continuation.resumeWithException(Exception("사진 촬영 실패: 오류 코드 $errorCode"))
                }
            }

            override fun onUsbDisconnected() {
                Log.e(TAG, "USB 디바이스 분리 감지 - 촬영 실패 처리")
                if (continuation.isActive) {
                    continuation.resumeWithException(
                        Exception("USB 디바이스가 분리되어 촬영을 완료할 수 없습니다")
                    )
                }
            }
        }
    }

    private fun handlePhotoCaptured(
        fullPath: String,
        fileName: String,
        continuation: CancellableContinuation<Result<CapturedPhoto>>
    ) {
        com.inik.camcon.utils.LogcatManager.d(
            TAG,
            "사진 촬영 완료: $fileName (${LogMask.path(fullPath)})"
        )

        val extension = fileName.substringAfterLast(".", "").lowercase()
        if (!isSupportedImageFormat(extension)) {
            com.inik.camcon.utils.LogcatManager.d(TAG, "지원하지 않는 파일 무시: $fileName (확장자: $extension)")
            return
        }

        val photo = createCapturedPhoto(fullPath, fileName)

        scope.launch(ioDispatcher) {
            downloadManager.handlePhotoDownload(
                photo = photo,
                fullPath = fullPath,
                fileName = fileName,
                cameraCapabilities = connectionManager.cameraCapabilities.value,
                cameraSettings = cameraSettingsProvider(),
                onPhotoDownloaded = { downloadedPhoto ->
                    updateDownloadedPhoto(downloadedPhoto)
                },
                onDownloadFailed = { failedFileName ->
                    updatePhotoDownloadFailed(failedFileName)
                }
            )
        }

        if (continuation.isActive) {
            continuation.resume(Result.success(photo))
        }
    }

    private fun isSupportedImageFormat(extension: String): Boolean {
        return extension in Constants.ImageProcessing.SUPPORTED_IMAGE_EXTENSIONS
    }

    private fun createCapturedPhoto(fullPath: String, fileName: String): CapturedPhoto {
        return CapturedPhoto(
            id = UUID.randomUUID().toString(),
            filePath = fullPath,
            thumbnailPath = null,
            captureTime = System.currentTimeMillis(),
            cameraModel = connectionManager.cameraCapabilities.value?.model ?: "알 수 없음",
            settings = cameraSettingsProvider(),
            size = 0,
            width = 0,
            height = 0,
            isDownloading = true
        )
    }

    fun startBurstCapture(count: Int): Flow<CapturedPhoto> = flow {
        // Issue W2: BURST 모드는 현재 미구현
        throw UnsupportedShootingModeException(
            mode = ShootingMode.BURST,
            supportedModes = listOf(ShootingMode.SINGLE)
        )
    }

    fun startTimelapse(settings: TimelapseSettings): Flow<CapturedPhoto> = callbackFlow {
        // Issue W2: TIMELAPSE 모드는 현재 미구현
        throw UnsupportedShootingModeException(
            mode = ShootingMode.TIMELAPSE,
            supportedModes = listOf(ShootingMode.SINGLE)
        )
    }

    fun startBracketing(settings: BracketingSettings): Flow<CapturedPhoto> = flow {
        // Issue W2: HDR_BRACKET 모드는 현재 미구현
        throw UnsupportedShootingModeException(
            mode = ShootingMode.HDR_BRACKET,
            supportedModes = listOf(ShootingMode.SINGLE)
        )
    }

    suspend fun startBulbCapture(): Result<Boolean> {
        // Issue W2: BULB 모드는 현재 미구현
        return Result.failure(
            UnsupportedShootingModeException(
                mode = ShootingMode.BULB,
                supportedModes = listOf(ShootingMode.SINGLE)
            )
        )
    }

    suspend fun stopBulbCapture(): Result<CapturedPhoto> {
        // Issue W2: BULB 모드는 현재 미구현
        return Result.failure(
            UnsupportedShootingModeException(
                mode = ShootingMode.BULB,
                supportedModes = listOf(ShootingMode.SINGLE)
            )
        )
    }

    // ── Live View ──

    fun startLiveView(): Flow<LiveViewFrame> = callbackFlow {
        com.inik.camcon.utils.LogcatManager.d(
            TAG,
            "라이브뷰 시작(Repository): USB=${connectionManager.isConnected.value}, PTPIP=${connectionManager.isPtpipConnected.value}"
        )

        val isAnyConnectionActive = connectionManager.isConnected.value ||
                connectionManager.isPtpipConnected.value

        if (!isAnyConnectionActive) {
            Log.e(TAG, "카메라가 연결되지 않은 상태에서 라이브뷰 시작 불가")
            close(IllegalStateException("카메라가 연결되지 않음"))
            return@callbackFlow
        }

        // 재연결 직후 등 네이티브 핸들이 닫혀 있으면 네이티브 startLiveView가 void로 조용히
        // no-op 한다(프레임 영영 없음 → isLoading=true 고착 = "라이브뷰 시작 중..." 무한). 시작 전
        // 네이티브 초기화 여부를 확인해 즉시 실패로 전파한다(연결 플래그만으론 재연결 경합에서 stale).
        if (!nativeDataSource.isCameraInitialized()) {
            Log.e(TAG, "네이티브 카메라 미초기화 — 라이브뷰 시작 불가")
            close(IllegalStateException("카메라가 아직 준비되지 않았습니다. 잠시 후 다시 시도해주세요."))
            return@callbackFlow
        }

        // 네이티브 startLiveView는 미지원·활성화 실패 시에도 void로 조용히 끝나 프레임이 안 온다.
        // 첫 프레임 워치독으로 무한 로딩을 막고 재시도 가능한 에러로 종료시킨다.
        val firstFrameReceived = AtomicBoolean(false)
        var firstFrameWatchdog: Job? = null

        // 카메라가 끊기면(껐다 켜기 등) 네이티브 프레임 펌프는 죽지만 이 flow는 awaitClose에서
        // 영원히 대기 → liveViewJob이 stale-active로 남아 재연결 후 "이미 활성화"로 재시작이 막힌다.
        // 연결 끊김을 감지해 flow를 종료시켜 job이 깨끗이 끝나도록 한다(촬영 중 프레임 멈춤엔
        // 영향 없는 안전 신호 = 연결 상태). 시작 시점엔 이미 연결됨이라 즉시 close되지 않는다.
        val connectionWatcher = launch {
            combine(
                connectionManager.isConnected,
                connectionManager.isPtpipConnected
            ) { usb, ptpip -> usb || ptpip }
                .collect { stillConnected ->
                    if (!stillConnected) {
                        Log.w(TAG, "라이브뷰 중 카메라 연결 끊김 — 라이브뷰 flow 종료")
                        close(IllegalStateException("카메라 연결이 끊어졌습니다"))
                    }
                }
        }

        try {
            com.inik.camcon.utils.LogcatManager.d(TAG, "네이티브 startLiveView 호출 시작 (자동초점 생략)")
            firstFrameWatchdog = launch {
                delay(LIVEVIEW_FIRST_FRAME_TIMEOUT_MS)
                if (!firstFrameReceived.get()) {
                    Log.e(TAG, "라이브뷰 첫 프레임 타임아웃(${LIVEVIEW_FIRST_FRAME_TIMEOUT_MS}ms) — 시작 실패로 종료")
                    close(IllegalStateException("라이브뷰 시작 실패(응답 없음)"))
                }
            }
            nativeDataSource.startLiveView(object : LiveViewCallback {
                override fun onLiveViewFrame(frame: ByteArray) {
                    firstFrameReceived.set(true)
                    try {
                        val liveViewFrame = LiveViewFrame(
                            data = frame,
                            width = 0,
                            height = 0,
                            timestamp = System.currentTimeMillis()
                        )
                        trySend(liveViewFrame)
                    } catch (e: Exception) {
                        Log.e(TAG, "라이브뷰 프레임 처리 실패", e)
                    }
                }

                override fun onLivePhotoCaptured(path: String) {
                    com.inik.camcon.utils.LogcatManager.d(TAG, "라이브뷰 중 사진 촬영: ${LogMask.path(path)}")
                }

                override fun onPhotoCaptured(filePath: String, fileName: String) {
                    com.inik.camcon.utils.LogcatManager.d(
                        TAG,
                        "라이브뷰 중 물리 셔터 사진 감지: $fileName"
                    )
                }

                override fun onPhotoDownloaded(
                    filePath: String,
                    fileName: String,
                    imageData: ByteArray
                ) {
                    com.inik.camcon.utils.LogcatManager.d(
                        TAG,
                        "라이브뷰 중 물리 셔터 사진 다운로드 완료: $fileName (${imageData.size / 1024}KB)"
                    )
                    handleNativePhotoDownload(filePath, fileName, imageData)
                }
            })

            com.inik.camcon.utils.LogcatManager.d(TAG, "라이브뷰 콜백 등록 완료")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "라이브뷰 시작 실패", e)
            close(e)
        }

        awaitClose {
            com.inik.camcon.utils.LogcatManager.d(TAG, "라이브뷰 중지 (awaitClose)")
            firstFrameWatchdog?.cancel()
            connectionWatcher.cancel()
            try {
                nativeDataSource.stopLiveView()
                com.inik.camcon.utils.LogcatManager.d(TAG, "라이브뷰 중지 완료")
            } catch (e: Exception) {
                Log.e(TAG, "라이브뷰 중지 중 오류", e)
            }
        }
    }

    suspend fun stopLiveView(): Result<Boolean> {
        return withContext(ioDispatcher) {
            try {
                com.inik.camcon.utils.LogcatManager.d(TAG, "라이브뷰 명시적 중지")
                nativeDataSource.stopLiveView()
                Result.success(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "라이브뷰 중지 실패", e)
                Result.failure(e)
            }
        }
    }

    // ── Captured Photos StateFlow ──

    fun getCapturedPhotos(): Flow<List<CapturedPhoto>> =
        _capturedPhotos.asStateFlow()

    /** 다운로드/처리 진행 카운트 (요구 E4). Tracker 의 StateFlow 를 그대로 노출. */
    fun getTransferQueue(): Flow<TransferQueueState> =
        transferProgressTracker.state

    suspend fun deletePhoto(photoId: String): Result<Boolean> {
        return withContext(ioDispatcher) {
            try {
                com.inik.camcon.utils.LogcatManager.d(TAG, "사진 삭제: $photoId")
                _capturedPhotos.update { current -> current.filter { it.id != photoId } }
                Result.success(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "사진 삭제 실패", e)
                Result.failure(e)
            }
        }
    }

    fun setRawFileRestrictionCallback(
        callback: ((fileName: String, restrictionMessage: String) -> Unit)?
    ) {
        eventManager.onRawFileRestricted = callback
    }

    // ── PTPIP 사진 이벤트 수집 (Facade init에서 시작) ──

    private val ptpipPhotoEventCollectionStarted = AtomicBoolean(false)

    /**
     * [PtpipDataSource.photoEvents] 수집 시작 (멱등).
     *
     * 과거 콜백 슬롯 방식은 화면 cleanup(`PtpipDataSource.cleanup()`)이 콜백을 null로 지우면
     * 재설치 경로가 없어 capturedPhotos 갱신이 조용히 멈췄다. 싱글톤 [scope] 수집은
     * 화면 수명주기와 무관하게 프로세스 종료까지 유지된다.
     */
    fun startPtpipPhotoEventCollection() {
        if (!ptpipPhotoEventCollectionStarted.compareAndSet(false, true)) return

        scope.launch {
            ptpipDataSource.photoEvents.collect { event ->
                when (event) {
                    is PtpipPhotoEvent.Downloaded -> handlePtpipPhotoDownloaded(event)

                    // Wi-Fi 경로 저장 실패도 USB 경로와 동일하게 처리 — 무음 유실 방지
                    is PtpipPhotoEvent.DownloadFailed -> {
                        Log.e(TAG, "PTPIP 사진 저장 실패: ${event.fileName}")
                        processedFileCache.remove(event.fileName)
                        updatePhotoDownloadFailed(event.fileName)
                    }
                }
            }
        }
    }

    private fun handlePtpipPhotoDownloaded(event: PtpipPhotoEvent.Downloaded) {
        val filePath = event.filePath
        val fileName = event.fileName
        val imageData = event.imageData
        com.inik.camcon.utils.LogcatManager.d(
            TAG,
            "PTPIP에서 사진 다운로드 완료: $fileName (${LogMask.path(filePath)}, ${imageData.size / 1024}KB)"
        )

        // 이벤트별 별도 코루틴 — EXIF 파싱이 수집 루프(발행측 백프레셔)를 막지 않게 한다
        scope.launch(ioDispatcher) {
            val capturedPhoto = CapturedPhoto(
                id = java.util.UUID.randomUUID().toString(),
                filePath = filePath,
                thumbnailPath = null,
                // 정렬 기준 안정화: PtpipDataSource 가 넘긴 원본 바이트의 EXIF 촬영 시각 사용, 실패 시 현재 시각 폴백
                captureTime = ExifCaptureTime.parseMillis(imageData) ?: System.currentTimeMillis(),
                cameraModel = connectionManager.cameraCapabilities.value?.model ?: "알 수 없음",
                settings = cameraSettingsProvider(),
                // 크기는 방금 다운로드한 imageData 기준(정본). 합성경로를 File().length() 로
                // 재조회하면 stale 물리파일 크기를 읽어 실제 컷과 어긋난다(수정1과 세트).
                size = imageData.size.toLong(),
                width = 0,
                height = 0,
                isDownloading = false,
                downloadCompleteTime = System.currentTimeMillis()
            )

            com.inik.camcon.utils.LogcatManager.d(
                TAG,
                "PTPIP 사진 저장 성공: ${LogMask.path(capturedPhoto.filePath)}"
            )
            updateDownloadedPhoto(capturedPhoto)
        }
    }

    // ── Native Photo Download Helpers ──

    private fun handleNativePhotoDownload(
        fullPath: String,
        fileName: String,
        imageData: ByteArray
    ) {
        com.inik.camcon.utils.LogcatManager.d(
            TAG,
            "handleNativePhotoDownload 호출됨: $fileName (${LogMask.path(fullPath)}, ${imageData.size} bytes)"
        )

        val extension = fileName.substringAfterLast(".", "").lowercase()
        if (extension !in Constants.ImageProcessing.SUPPORTED_IMAGE_EXTENSIONS) {
            com.inik.camcon.utils.LogcatManager.d(
                TAG,
                "지원하지 않는 파일 무시: $fileName (확장자: $extension)"
            )
            return
        }

        // 유계 큐로 전달. 큐가 가득 차면 네이티브 이벤트 스레드가 여기서 대기하여
        // 카메라 측으로 자연스러운 백프레셔가 걸린다 (사진 유실 없음).
        ensureNativeDownloadConsumer()
        nativeDownloadQueue.trySendBlocking(
            NativeDownloadJob(fullPath, fileName, imageData)
        ).onFailure { e ->
            Log.e(TAG, "네이티브 다운로드 큐 전달 실패: $fileName", e)
            processedFileCache.remove(fileName)
            updatePhotoDownloadFailed(fileName)
        }
    }

    private suspend fun processNativePhotoDownload(job: NativeDownloadJob) {
        val capturedPhoto = downloadManager.handleNativePhotoDownload(
            filePath = job.fullPath,
            fileName = job.fileName,
            imageData = job.imageData,
            cameraCapabilities = connectionManager.cameraCapabilities.value,
            cameraSettings = cameraSettingsProvider()
        )

        if (capturedPhoto != null) {
            com.inik.camcon.utils.LogcatManager.d(
                TAG,
                "네이티브 사진 저장 성공: ${LogMask.path(capturedPhoto.filePath)}"
            )
            updateDownloadedPhoto(capturedPhoto)
        } else {
            Log.e(TAG, "네이티브 사진 저장 실패: ${job.fileName}")
            // 저장 실패 시 dedup 키를 제거해 동일 컷의 재수신/재시도가 24h 동안 차단되지 않게 한다(사진 유실 방지).
            // handlePhotoDownloadCallback 의 fileKey 와 동일하게 fileName 단독으로 맞춘다(미동기화 시 재시도 차단 해제가 no-op).
            processedFileCache.remove(job.fileName)
            updatePhotoDownloadFailed(job.fileName)
        }
    }

    private fun updateDownloadedPhoto(downloadedPhoto: CapturedPhoto) {
        val beforeCount = _capturedPhotos.value.size

        com.inik.camcon.utils.LogcatManager.d(
            TAG,
            "updateDownloadedPhoto: id=${downloadedPhoto.id}, ${LogMask.path(downloadedPhoto.filePath)}, ${downloadedPhoto.size} bytes"
        )

        _capturedPhotos.update { current ->
            // (1) 중복 안전망 — 동일 파일명(확장자 포함)이 이미 있으면 추가하지 않음.
            //     Z8 듀얼슬롯(store_00010001/00020001)이 같은 fileName 으로 두 번 내려보내는 중복을 제거한다.
            //     RAW+JPG 는 확장자가 달라 통과한다(별개 컷 아님, 동일 컷의 다른 포맷).
            val newSegment = downloadedPhoto.filePath.substringAfterLast("/")
            val isDuplicate = current.any { it.filePath.substringAfterLast("/") == newSegment }
            if (isDuplicate) {
                com.inik.camcon.utils.LogcatManager.d(TAG, "중복 파일명 감지 - 추가 건너뜀: $newSegment")
                return@update current
            }

            // (2) 정렬 삽입 — captureTime 오름차순(최신=꼬리). UI 가 takeLast/lastOrNull 로 최신을 꼬리에서 읽으므로 반드시 오름차순.
            //     리스트는 이 함수로만 갱신되어 항상 PHOTO_ORDER_COMPARATOR 순서가 유지되므로,
            //     매 이벤트 전체 재정렬(O(n log n) + 비교마다 substring 할당) 대신 이진 탐색 삽입을 쓴다.
            val searchResult = current.binarySearch(downloadedPhoto, PHOTO_ORDER_COMPARATOR)
            val insertIndex = if (searchResult < 0) -(searchResult + 1) else searchResult + 1
            val sorted = ArrayList<CapturedPhoto>(current.size + 1).apply {
                addAll(current)
                add(insertIndex, downloadedPhoto)
            }

            // (3) 장시간 테더링 세션에서 무한 증가하지 않도록 최근 N개만 유지
            if (sorted.size > MAX_CAPTURED_PHOTOS) sorted.takeLast(MAX_CAPTURED_PHOTOS) else sorted
        }
        val afterCount = _capturedPhotos.value.size

        com.inik.camcon.utils.LogcatManager.d(
            TAG,
            "StateFlow 업데이트 완료: $beforeCount -> $afterCount 개"
        )

        // (4) 위 (1) 중복 안전망으로 같은 파일명은 1개만 유지되므로 경고만 남긴다(제거는 안전망이 담당).
        val downloadedFileName = downloadedPhoto.filePath.substringAfterLast("/")
        val sameNamePhotos = _capturedPhotos.value.filter {
            it.filePath.substringAfterLast("/") == downloadedFileName
        }
        if (sameNamePhotos.size > 1) {
            Log.w(TAG, "같은 파일명의 사진이 ${sameNamePhotos.size}개 발견됨")
            sameNamePhotos.forEachIndexed { index, photo ->
                Log.w(TAG, "    [$index] ID: ${photo.id}, 경로: ${LogMask.path(photo.filePath)}")
            }
        }
    }

    /**
     * 테더링 촬영물 저장 실패 처리 — 모든 실패 경로(네이티브 큐 실패·처리 예외·저장 null·PTPIP 실패·
     * PhotoDownloadManager onDownloadFailed 콜백)의 단일 수렴점.
     *
     * (1) 실패 항목 제거(등록과 동일 basename 키)와 (2) 사용자 통지를 여기서 함께 수행한다.
     * 통지가 없으면 저장 실패(디스크 풀 등) 시 배지만 조용히 사라져 사용자가 촬영물 유실을 인지하지 못한다.
     */
    private fun updatePhotoDownloadFailed(fileName: String) {
        // 등록(updateDownloadedPhoto)은 filePath 의 basename 을 dedup 키로 쓰므로 제거도 basename 으로 맞춘다.
        // (기존 `it.filePath != fileName` 은 전체경로 vs basename 비교라 항상 참 → 아무것도 제거하지 못하는 no-op)
        _capturedPhotos.update { current ->
            current.filter { it.filePath.substringAfterLast("/") != fileName }
        }
        com.inik.camcon.utils.LogcatManager.d(TAG, "다운로드 실패한 사진 제거: $fileName")

        // 사용자 통지 — 기존 UI 에러 채널(ErrorNotifier→errorEvent→setError)로 유실을 알린다.
        // 파일명은 현지화 대상이 아니므로 재사용 문자열 뒤에 그대로 덧붙인다(어떤 컷이 유실됐는지 식별).
        try {
            errorNotifier.emitError(
                type = ErrorType.STORAGE,
                message = "${context.getString(R.string.photo_save_failed)}: $fileName",
                severity = ErrorSeverity.HIGH
            )
        } catch (e: Exception) {
            Log.w(TAG, "다운로드 실패 통지 방출 실패: $fileName", e)
        }
    }
}
