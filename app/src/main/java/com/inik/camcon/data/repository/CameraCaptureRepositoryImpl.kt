package com.inik.camcon.data.repository

import android.content.Context
import android.util.Log
import com.inik.camcon.data.datasource.nativesource.CameraCaptureListener
import com.inik.camcon.data.datasource.nativesource.LiveViewCallback
import com.inik.camcon.data.datasource.nativesource.NativeCameraDataSource
import com.inik.camcon.data.datasource.ptpip.PtpipDataSource
import com.inik.camcon.data.datasource.usb.UsbCameraManager
import com.inik.camcon.data.repository.managers.CameraConnectionManager
import com.inik.camcon.data.repository.managers.CameraEventManager
import com.inik.camcon.data.repository.managers.PhotoDownloadManager
import com.inik.camcon.di.ApplicationScope
import com.inik.camcon.di.IoDispatcher
import com.inik.camcon.domain.model.BracketingSettings
import com.inik.camcon.domain.model.CameraSettings
import com.inik.camcon.domain.model.CapturedPhoto
import com.inik.camcon.domain.model.LiveViewFrame
import com.inik.camcon.domain.model.ShootingMode
import com.inik.camcon.domain.model.TimelapseSettings
import com.inik.camcon.domain.model.UnsupportedShootingModeException
import com.inik.camcon.utils.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 카메라 이벤트 리스너·촬영·라이브뷰·캡처 사진 StateFlow·LRU 중복 방지 담당 sub-impl.
 *
 * H8 분해: 원본 CameraRepositoryImpl의 Event(3) + Capture(6) + LiveView(2) + CapturedPhotos(3) = 14개 override 이동.
 *
 * 공유 상태 소유:
 *  - `processedFiles` LRU 1000개 (다운로드 콜백 중복 방지)
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
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    companion object {
        private const val TAG = "카메라캡처레포"
    }

    /**
     * Facade가 주입하는 설정 공급자. 기본값 null (Facade 주입 전·테스트).
     */
    @Volatile
    var cameraSettingsProvider: () -> CameraSettings? = { null }

    /** Facade가 주입하는 flush 완료 훅 (CameraStateObserver.updateCameraInitialization(false)). */
    @Volatile
    var onFlushCompleteCallback: (() -> Unit)? = null

    // 중복 처리 방지를 위한 변수들 (LRU 방식으로 최대 1000개 유지)
    private val processedFiles: MutableSet<String> = java.util.Collections.synchronizedSet(
        java.util.Collections.newSetFromMap(object : LinkedHashMap<String, Boolean>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
                return size > 1000
            }
        })
    )

    private val _capturedPhotos = MutableStateFlow<List<CapturedPhoto>>(emptyList())

    // ── Test helpers (Issue C5 LRU 캐시 테스트) ──
    fun markFileAsProcessed(filePath: String) {
        processedFiles.add(filePath)
    }

    fun isFileProcessed(filePath: String): Boolean {
        return processedFiles.contains(filePath)
    }

    fun getProcessedFilesCount(): Int {
        return processedFiles.size
    }

    fun clearProcessedFiles() {
        processedFiles.clear()
    }

    // ── Event Listener ──

    fun isEventListenerActive(): Flow<Boolean> =
        eventManager.isEventListenerActive

    fun setPhotoPreviewMode(enabled: Boolean) {
        eventManager.setPhotoPreviewMode(enabled)
    }

    suspend fun startCameraEventListener(): Result<Boolean> {
        com.inik.camcon.utils.LogcatManager.d(TAG, "🚀 startCameraEventListener 호출됨 (USB 연결용)")

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
                com.inik.camcon.utils.LogcatManager.d(TAG, "🎯 카메라 이벤트 플러시 완료 - 초기화 상태 해제")
                onFlushCompleteCallback?.invoke()
                com.inik.camcon.utils.LogcatManager.d(
                    TAG,
                    "✅ UI 블로킹 해제 완료 (isCameraInitializing = false)"
                )
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
            "🎯 onPhotoDownloaded 콜백 호출됨! $fileName (size=${imageData.size})"
        )

        val fileKey = "$fullPath|$fileName|${imageData.size}"

        if (shouldProcessFile(fileKey)) {
            com.inik.camcon.utils.LogcatManager.d(TAG, "📥 handleNativePhotoDownload 호출: $fileName")
            handleNativePhotoDownload(fullPath, fileName, imageData)
        } else {
            com.inik.camcon.utils.LogcatManager.d(TAG, "⏭️ 중복으로 인해 처리 건너뜀: $fileName")
        }
    }

    private fun shouldProcessFile(fileKey: String): Boolean {
        return processedFiles.add(fileKey)
    }

    suspend fun stopCameraEventListener(): Result<Boolean> {
        return eventManager.stopCameraEventListener()
    }

    /** Facade가 Control의 getCameraPhotos(Paged) 콜백으로 주입. */
    suspend fun restartEventListenerIfNeeded() {
        if (!eventManager.isRunning()) {
            startEventListenerInternal()
        }
    }

    /**
     * 내부용 이벤트 리스너 시작 (중복 처리 방지를 위해 onPhotoDownloaded=null)
     */
    private suspend fun startEventListenerInternal() {
        com.inik.camcon.utils.LogcatManager.d(TAG, "🔧 startEventListenerInternal 호출됨 (내부용 - 중복 방지)")

        eventManager.startCameraEventListener(
            isConnected = connectionManager.isConnected.value,
            isInitializing = connectionManager.isInitializing.value,
            saveDirectory = downloadManager.getSaveDirectory(),
            onPhotoCaptured = { _, _ ->
                // handleNativePhotoDownload에서만 처리하므로 빈 콜백
            },
            onPhotoDownloaded = null,
            onFlushComplete = {
                com.inik.camcon.utils.LogcatManager.d(TAG, "🎯 카메라 이벤트 플러시 완료 - 초기화 상태 해제")
                onFlushCompleteCallback?.invoke()
                com.inik.camcon.utils.LogcatManager.d(
                    TAG,
                    "✅ UI 블로킹 해제 완료 (isCameraInitializing = false)"
                )
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
            com.inik.camcon.utils.LogcatManager.d(TAG, "=== 사진 촬영 시작 ===")
            com.inik.camcon.utils.LogcatManager.d(TAG, "촬영 모드: $mode")
            com.inik.camcon.utils.LogcatManager.d(TAG, "저장 디렉토리: $saveDir")
            com.inik.camcon.utils.LogcatManager.d(
                TAG,
                "카메라 연결 상태: ${connectionManager.isConnected.value}"
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
            Log.w(TAG, "❌ 미구현 촬영 모드: $mode")
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
                com.inik.camcon.utils.LogcatManager.d(TAG, "✓ 사진 촬영 플러시 완료")
            }

            override fun onPhotoCaptured(fullPath: String, fileName: String) {
                handlePhotoCaptured(fullPath, fileName, continuation)
            }

            override fun onPhotoDownloaded(
                filePath: String,
                fileName: String,
                imageData: ByteArray
            ) {
                com.inik.camcon.utils.LogcatManager.d(TAG, "✓ Native 사진 다운로드 완료!!!")
                com.inik.camcon.utils.LogcatManager.d(TAG, "파일명: $fileName")
                com.inik.camcon.utils.LogcatManager.d(TAG, "데이터 크기: ${imageData.size / 1024}KB")
                handleNativePhotoDownload(filePath, fileName, imageData)
            }

            override fun onCaptureFailed(errorCode: Int) {
                Log.e(TAG, "✗ 사진 촬영 실패, 오류 코드: $errorCode")
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
        com.inik.camcon.utils.LogcatManager.d(TAG, "✓ 사진 촬영 완료!!!")
        com.inik.camcon.utils.LogcatManager.d(TAG, "파일명: $fileName")
        com.inik.camcon.utils.LogcatManager.d(TAG, "전체 경로: $fullPath")

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
        com.inik.camcon.utils.LogcatManager.d(TAG, "=== 라이브뷰 시작 (Repository) ===")
        com.inik.camcon.utils.LogcatManager.d(
            TAG,
            "USB 연결 상태: ${connectionManager.isConnected.value}"
        )
        com.inik.camcon.utils.LogcatManager.d(
            TAG,
            "PTPIP 연결 상태: ${connectionManager.isPtpipConnected.value}"
        )

        val isAnyConnectionActive = connectionManager.isConnected.value ||
                connectionManager.isPtpipConnected.value

        if (!isAnyConnectionActive) {
            Log.e(TAG, "카메라가 연결되지 않은 상태에서 라이브뷰 시작 불가")
            close(IllegalStateException("카메라가 연결되지 않음"))
            return@callbackFlow
        }

        com.inik.camcon.utils.LogcatManager.d(TAG, "✅ 카메라 연결 확인 완료 - 라이브뷰 시작")

        try {
            com.inik.camcon.utils.LogcatManager.d(TAG, "네이티브 startLiveView 호출 시작 (자동초점 생략)")
            nativeDataSource.startLiveView(object : LiveViewCallback {
                override fun onLiveViewFrame(frame: ByteArray) {
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
                    com.inik.camcon.utils.LogcatManager.d(TAG, "라이브뷰 중 사진 촬영: $path")
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

    // ── PTPIP 사진 다운로드 콜백 설치 (Facade init에서 1회 호출) ──

    fun installPtpipDownloadCallback() {
        ptpipDataSource.setPhotoDownloadedCallback { filePath, fileName, imageData ->
            com.inik.camcon.utils.LogcatManager.d(TAG, "PTPIP에서 사진 다운로드 완료: $fileName")
            com.inik.camcon.utils.LogcatManager.d(TAG, "  📁 저장 경로: $filePath")
            com.inik.camcon.utils.LogcatManager.d(TAG, "  📊 데이터 크기: ${imageData.size / 1024}KB")

            scope.launch(ioDispatcher) {
                val actualSize = try {
                    java.io.File(filePath).length()
                } catch (_: Exception) {
                    imageData.size.toLong()
                }

                val capturedPhoto = CapturedPhoto(
                    id = java.util.UUID.randomUUID().toString(),
                    filePath = filePath,
                    thumbnailPath = null,
                    captureTime = System.currentTimeMillis(),
                    cameraModel = connectionManager.cameraCapabilities.value?.model ?: "알 수 없음",
                    settings = cameraSettingsProvider(),
                    size = actualSize,
                    width = 0,
                    height = 0,
                    isDownloading = false,
                    downloadCompleteTime = System.currentTimeMillis()
                )

                com.inik.camcon.utils.LogcatManager.d(
                    TAG,
                    "✅ PTPIP 사진 저장 성공: ${capturedPhoto.filePath}"
                )
                updateDownloadedPhoto(capturedPhoto)
            }
        }
    }

    // ── Native Photo Download Helpers ──

    private fun handleNativePhotoDownload(
        fullPath: String,
        fileName: String,
        imageData: ByteArray
    ) {
        com.inik.camcon.utils.LogcatManager.d(TAG, "🎯 handleNativePhotoDownload 호출됨: $fileName")
        com.inik.camcon.utils.LogcatManager.d(TAG, "  📁 fullPath: $fullPath")
        com.inik.camcon.utils.LogcatManager.d(TAG, "  📊 imageData size: ${imageData.size} bytes")
        com.inik.camcon.utils.LogcatManager.d(TAG, "  🧵 스레드: ${Thread.currentThread().name}")

        val extension = fileName.substringAfterLast(".", "").lowercase()
        if (extension !in Constants.ImageProcessing.SUPPORTED_IMAGE_EXTENSIONS) {
            com.inik.camcon.utils.LogcatManager.d(
                TAG,
                "❌ 지원하지 않는 파일 무시: $fileName (확장자: $extension)"
            )
            return
        }

        scope.launch(ioDispatcher) {
            com.inik.camcon.utils.LogcatManager.d(
                TAG,
                "🚀 PhotoDownloadManager.handleNativePhotoDownload 시작: $fileName"
            )

            val capturedPhoto = downloadManager.handleNativePhotoDownload(
                filePath = fullPath,
                fileName = fileName,
                imageData = imageData,
                cameraCapabilities = connectionManager.cameraCapabilities.value,
                cameraSettings = cameraSettingsProvider()
            )

            if (capturedPhoto != null) {
                com.inik.camcon.utils.LogcatManager.d(
                    TAG,
                    "✅ 네이티브 사진 저장 성공: ${capturedPhoto.filePath}"
                )
                updateDownloadedPhoto(capturedPhoto)
            } else {
                Log.e(TAG, "❌ 네이티브 사진 저장 실패: $fileName")
                updatePhotoDownloadFailed(fileName)
            }

            com.inik.camcon.utils.LogcatManager.d(
                TAG,
                "🏁 PhotoDownloadManager.handleNativePhotoDownload 완료: $fileName"
            )
        }
    }

    private fun updateDownloadedPhoto(downloadedPhoto: CapturedPhoto) {
        val beforeCount = _capturedPhotos.value.size

        com.inik.camcon.utils.LogcatManager.d(TAG, "🔄 updateDownloadedPhoto 호출됨")
        com.inik.camcon.utils.LogcatManager.d(TAG, "  📷 사진 ID: ${downloadedPhoto.id}")
        com.inik.camcon.utils.LogcatManager.d(TAG, "  📁 파일 경로: ${downloadedPhoto.filePath}")
        com.inik.camcon.utils.LogcatManager.d(TAG, "  📊 파일 크기: ${downloadedPhoto.size} bytes")
        com.inik.camcon.utils.LogcatManager.d(TAG, "  📋 현재 StateFlow 크기: $beforeCount 개")
        com.inik.camcon.utils.LogcatManager.d(TAG, "  🧵 스레드: ${Thread.currentThread().name}")

        _capturedPhotos.update { current -> current + downloadedPhoto }
        val afterCount = _capturedPhotos.value.size

        com.inik.camcon.utils.LogcatManager.d(
            TAG,
            "✅ StateFlow 업데이트 완료: $beforeCount -> $afterCount 개"
        )
        com.inik.camcon.utils.LogcatManager.d(TAG, "  🎯 총 사진 개수: ${_capturedPhotos.value.size}개")

        val sameNamePhotos = _capturedPhotos.value.filter {
            it.filePath.contains(downloadedPhoto.filePath.substringAfterLast("/"))
        }
        if (sameNamePhotos.size > 1) {
            Log.w(TAG, "⚠️ 같은 파일명의 사진이 ${sameNamePhotos.size}개 발견됨!")
            sameNamePhotos.forEachIndexed { index, photo ->
                Log.w(TAG, "    [$index] ID: ${photo.id}, 경로: ${photo.filePath}")
            }
        }

        com.inik.camcon.utils.LogcatManager.d(TAG, "=== 사진 StateFlow 업데이트 ===")
        com.inik.camcon.utils.LogcatManager.d(TAG, "업데이트 전: ${beforeCount}개")
        com.inik.camcon.utils.LogcatManager.d(TAG, "업데이트 후: ${afterCount}개")
        com.inik.camcon.utils.LogcatManager.d(TAG, "추가된 사진 ID: ${downloadedPhoto.id}")
        com.inik.camcon.utils.LogcatManager.d(TAG, "추가된 사진 경로: ${downloadedPhoto.filePath}")
        com.inik.camcon.utils.LogcatManager.d(TAG, "✅ _capturedPhotos StateFlow 업데이트 완료")
    }

    private fun updatePhotoDownloadFailed(fileName: String) {
        _capturedPhotos.update { current -> current.filter { it.filePath != fileName } }
        com.inik.camcon.utils.LogcatManager.d(TAG, "다운로드 실패한 사진 제거: $fileName")
    }
}
