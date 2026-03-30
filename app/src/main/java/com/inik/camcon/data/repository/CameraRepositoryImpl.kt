package com.inik.camcon.data.repository

import android.content.Context
import android.util.Log
import com.inik.camcon.data.datasource.local.AppPreferencesDataSource
import com.inik.camcon.data.datasource.nativesource.CameraCaptureListener
import com.inik.camcon.data.datasource.nativesource.LiveViewCallback
import com.inik.camcon.data.datasource.nativesource.NativeCameraDataSource
import com.inik.camcon.data.datasource.ptpip.PtpipDataSource
import com.inik.camcon.data.datasource.usb.UsbCameraManager
import com.inik.camcon.data.repository.managers.CameraConnectionManager
import com.inik.camcon.data.repository.managers.CameraEventManager
import com.inik.camcon.data.repository.managers.PhotoDownloadManager
import com.inik.camcon.domain.model.BracketingSettings
import com.inik.camcon.domain.model.Camera
import com.inik.camcon.domain.model.CameraCapabilities
import com.inik.camcon.domain.model.CameraPhoto
import com.inik.camcon.domain.model.CameraSettings
import com.inik.camcon.domain.model.CapturedPhoto
import com.inik.camcon.domain.model.LiveViewFrame
import com.inik.camcon.domain.model.PaginatedCameraPhotos
import com.inik.camcon.domain.model.ShootingMode
import com.inik.camcon.domain.model.TimelapseSettings
import com.inik.camcon.domain.repository.CameraRepository
import com.inik.camcon.domain.repository.ColorTransferRepository
import com.inik.camcon.domain.usecase.camera.PhotoCaptureEventManager
import com.inik.camcon.domain.usecase.GetSubscriptionUseCase
import com.inik.camcon.utils.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import android.provider.MediaStore
import com.inik.camcon.CameraNative

/**
 * CameraRepository 구현체
 *
 * 개선사항:
 * - Facade 패턴 적용하여 의존성 감소
 * - 중복 코드 제거
 * - 에러 처리 일관성 개선
 */
@Singleton
class CameraRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val nativeDataSource: NativeCameraDataSource,
    private val ptpipDataSource: PtpipDataSource,
    private val usbCameraManager: UsbCameraManager,
    private val photoCaptureEventManager: PhotoCaptureEventManager,
    private val appPreferencesDataSource: AppPreferencesDataSource,
    private val colorTransferRepository: ColorTransferRepository,
    private val connectionManager: CameraConnectionManager,
    private val eventManager: CameraEventManager,
    private val downloadManager: PhotoDownloadManager,
    private val uiStateManager: com.inik.camcon.presentation.viewmodel.state.CameraUiStateManager,
    private val getSubscriptionUseCase: GetSubscriptionUseCase,
    private val errorHandlingManager: com.inik.camcon.domain.manager.ErrorHandlingManager
) : CameraRepository {

    companion object {
        private const val TAG = "카메라레포지토리"

        // 중복 처리 방지 윈도우 시간 (밀리초)
        private const val DUP_WINDOW_MS = 1500L
    }

    // 중복 처리 방지를 위한 변수들
    private val processedFiles = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private val _capturedPhotos = MutableStateFlow<List<CapturedPhoto>>(emptyList())
    private val _cameraSettings = MutableStateFlow<CameraSettings?>(null)

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        initializeRepository()
    }

    /**
     * Repository 초기화
     * 개선: 초기화 로직을 별도 메서드로 분리하여 가독성 향상
     */
    private fun initializeRepository() {
        // GPU 초기화
        colorTransferRepository.initializeGPU(context)

        // PTPIP 콜백 설정
        setupPtpipCallbacks()

        // USB 분리 이벤트 구독
        subscribeToUsbEvents()
    }

    /**
     * PTPIP 콜백 설정
     * 개선: 콜백 설정 로직을 별도 메서드로 분리
     */
    private fun setupPtpipCallbacks() {
        // PTPIP 사진 다운로드 콜백 설정
        ptpipDataSource.setPhotoDownloadedCallback { filePath, fileName, imageData ->
            com.inik.camcon.utils.LogcatManager.d(TAG, "PTPIP에서 사진 다운로드 완료: $fileName")
            com.inik.camcon.utils.LogcatManager.d(TAG, "  📁 카메라 경로: $filePath")
            com.inik.camcon.utils.LogcatManager.d(TAG, "  📊 데이터 크기: ${imageData.size / 1024}KB")

            // PhotoDownloadManager를 통해 실제 파일 저장 및 MediaStore 등록
            repositoryScope.launch {
                com.inik.camcon.utils.LogcatManager.d(TAG, "🚀 PTPIP - handleNativePhotoDownload 호출")

                val capturedPhoto = downloadManager.handleNativePhotoDownload(
                    filePath = filePath,
                    fileName = fileName,
                    imageData = imageData,
                    cameraCapabilities = connectionManager.cameraCapabilities.value,
                    cameraSettings = _cameraSettings.value
                )

                if (capturedPhoto != null) {
                    com.inik.camcon.utils.LogcatManager.d(
                        TAG,
                        "✅ PTPIP 사진 저장 성공: ${capturedPhoto.filePath}"
                    )
                    updateDownloadedPhoto(capturedPhoto)
                } else {
                    Log.e(TAG, "❌ PTPIP 사진 저장 실패: $fileName")
                }
            }
        }

        // PTPIP 연결 끊어짐 콜백 설정
        ptpipDataSource.setConnectionLostCallback {
            com.inik.camcon.utils.LogcatManager.w(TAG, "🚨 PTPIP Wi-Fi 연결이 끊어졌습니다")
            handlePtpipDisconnection()
        }
    }

    /**
     * PTPIP 연결 끊어짐 처리
     * 개선: 에러 처리 로직을 별도 메서드로 분리
     */
    private fun handlePtpipDisconnection() {
        repositoryScope.launch {
            try {
                CameraNative.stopListenCameraEvents()
                com.inik.camcon.utils.LogcatManager.d(
                    TAG,
                    "🛑 PTPIP 연결 끊어짐으로 인한 이벤트 리스너 중지 완료"
                )
            } catch (e: Exception) {
                com.inik.camcon.utils.LogcatManager.e(TAG, "이벤트 리스너 중지 실패", e)
            }
        }
    }

    /**
     * USB 이벤트 구독
     * 개선: 이벤트 구독 로직을 별도 메서드로 분리
     */
    private fun subscribeToUsbEvents() {
        repositoryScope.launch {
            errorHandlingManager.usbDisconnectedEvent.collect {
                com.inik.camcon.utils.LogcatManager.d(TAG, "USB 분리 이벤트 감지 - USB 분리 처리 시작")
                usbCameraManager.handleUsbDisconnection()
            }
        }
    }

    override fun getCameraFeed(): Flow<List<Camera>> =
        connectionManager.cameraFeed

    override suspend fun connectCamera(cameraId: String): Result<Boolean> {
        val result = connectionManager.connectCamera(cameraId)
        if (result.isSuccess) {
            com.inik.camcon.utils.LogcatManager.d(TAG, "카메라 연결 완료 - 안정화 대기 시작")
            kotlinx.coroutines.delay(300)
            // 이벤트 리스너는 UI에서 명시적으로 시작되도록 변경
        }
        return result
    }

    override suspend fun disconnectCamera(): Result<Boolean> {
        // 이벤트 리스너 중지
        eventManager.stopCameraEventListener()
        return connectionManager.disconnectCamera()
    }

    override fun isCameraConnected(): Flow<Boolean> =
        combine(
            connectionManager.isConnected,
            eventManager.isEventListenerActive
        ) { isConnected, isListenerActive ->
            // libgphoto2(AP 강제) 경로에서도 이벤트 리스너가 활성화되어 있으면 연결로 간주
            isConnected || isListenerActive
        }

    override fun isInitializing(): Flow<Boolean> =
        connectionManager.isInitializing

    override fun isEventListenerActive(): Flow<Boolean> =
        eventManager.isEventListenerActive

    override fun setPhotoPreviewMode(enabled: Boolean) {
        eventManager.setPhotoPreviewMode(enabled)
    }

    override suspend fun startCameraEventListener(): Result<Boolean> {
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
                uiStateManager.updateCameraInitialization(false)
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
     * 사진 다운로드 콜백 처리
     * 개선: 중복 처리 방지 로직을 별도 메서드로 분리
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

    /**
     * 파일 처리 여부 확인 (중복 방지)
     * 개선: 중복 체크 로직을 별도 메서드로 분리하여 재사용성 향상
     */
    private fun shouldProcessFile(fileKey: String): Boolean {
        return processedFiles.add(fileKey)
    }

    override suspend fun stopCameraEventListener(): Result<Boolean> {
        return eventManager.stopCameraEventListener()
    }

    override suspend fun getCameraSettings(): Result<CameraSettings> {
        return withContext(Dispatchers.IO) {
            try {
                // 캐시된 설정이 있으면 우선 반환
                _cameraSettings.value?.let { cachedSettings ->
                    com.inik.camcon.utils.LogcatManager.d(TAG, "캐시된 카메라 설정 반환")
                    return@withContext Result.success(cachedSettings)
                }

                val widgetJson = getWidgetJsonFromSource()
                val settings = parseWidgetJsonToSettings(widgetJson)

                withContext(Dispatchers.Main) {
                    _cameraSettings.value = settings
                }

                com.inik.camcon.utils.LogcatManager.d(TAG, "카메라 설정 업데이트")
                Result.success(settings)
            } catch (e: Exception) {
                Log.e(TAG, "카메라 설정 가져오기 실패", e)
                Result.failure(e)
            }
        }
    }

    /**
     * 위젯 JSON 가져오기
     * 개선: JSON 소스 선택 로직을 별도 메서드로 분리
     */
    private suspend fun getWidgetJsonFromSource(): String {
        return if (usbCameraManager.isNativeCameraConnected.value) {
            com.inik.camcon.utils.LogcatManager.d(TAG, "USB 카메라 연결됨 - 마스터 데이터 사용")
            usbCameraManager.buildWidgetJsonFromMaster()
        } else {
            val masterData = usbCameraManager.buildWidgetJsonFromMaster()
            if (masterData.isNotEmpty()) {
                com.inik.camcon.utils.LogcatManager.d(TAG, "USB 카메라 미연결이지만 마스터 데이터 사용")
                masterData
            } else {
                com.inik.camcon.utils.LogcatManager.d(TAG, "마스터 데이터 없음 - 직접 네이티브 호출")
                nativeDataSource.buildWidgetJson()
            }
        }
    }

    /**
     * 위젯 JSON을 CameraSettings로 변환
     * 개선: 파싱 로직을 별도 메서드로 분리
     */
    private fun parseWidgetJsonToSettings(widgetJson: String): CameraSettings {
        // TODO: JSON 파싱하여 설정 추출
        return CameraSettings(
            iso = "100",
            shutterSpeed = "1/125",
            aperture = "2.8",
            whiteBalance = "자동",
            focusMode = "AF-S",
            exposureCompensation = "0"
        )
    }

    override suspend fun getCameraInfo(): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val summary = nativeDataSource.getCameraSummary()
                Result.success(summary.name)
            } catch (e: Exception) {
                Log.e(TAG, "카메라 정보 가져오기 실패", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun updateCameraSetting(key: String, value: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                com.inik.camcon.utils.LogcatManager.d(TAG, "카메라 설정 업데이트: $key = $value")
                Result.success(true)
            } catch (e: Exception) {
                Log.e(TAG, "카메라 설정 업데이트 실패", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun capturePhoto(mode: ShootingMode): Result<CapturedPhoto> {
        return suspendCancellableCoroutine<Result<CapturedPhoto>> { continuation ->
            val saveDir = downloadManager.getSaveDirectory()
            com.inik.camcon.utils.LogcatManager.d(TAG, "=== 사진 촬영 시작 ===")
            com.inik.camcon.utils.LogcatManager.d(TAG, "촬영 모드: $mode")
            com.inik.camcon.utils.LogcatManager.d(TAG, "저장 디렉토리: $saveDir")
            com.inik.camcon.utils.LogcatManager.d(
                TAG,
                "카메라 연결 상태: ${connectionManager.isConnected.value}"
            )

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
                continuation.resumeWithException(e)
            }
        }
    }

    /**
     * 카메라 연결 상태 검증
     * 개선: 검증 로직을 별도 메서드로 분리
     */
    private fun validateCameraConnection(
        continuation: kotlin.coroutines.Continuation<Result<CapturedPhoto>>
    ): Boolean {
        if (!connectionManager.isConnected.value) {
            Log.e(TAG, "카메라가 연결되지 않은 상태에서 사진 촬영 불가")
            continuation.resumeWithException(Exception("카메라가 연결되지 않음"))
            return false
        }
        return true
    }

    /**
     * 캡처 리스너 생성
     * 개선: 리스너 생성 로직을 별도 메서드로 분리하여 가독성 향상
     */
    private fun createCaptureListener(
        continuation: kotlin.coroutines.Continuation<Result<CapturedPhoto>>
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
                continuation.resumeWithException(Exception("사진 촬영 실패: 오류 코드 $errorCode"))
            }

            override fun onUsbDisconnected() {
                Log.e(TAG, "USB 디바이스 분리 감지 - 촬영 실패 처리")
                continuation.resumeWithException(
                    Exception("USB 디바이스가 분리되어 촬영을 완료할 수 없습니다")
                )
            }
        }
    }

    /**
     * 사진 촬영 완료 처리
     * 개선: 촬영 완료 로직을 별도 메서드로 분리
     */
    private fun handlePhotoCaptured(
        fullPath: String,
        fileName: String,
        continuation: kotlin.coroutines.Continuation<Result<CapturedPhoto>>
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

        repositoryScope.launch {
            downloadManager.handlePhotoDownload(
                photo = photo,
                fullPath = fullPath,
                fileName = fileName,
                cameraCapabilities = connectionManager.cameraCapabilities.value,
                cameraSettings = _cameraSettings.value,
                onPhotoDownloaded = { downloadedPhoto ->
                    updateDownloadedPhoto(downloadedPhoto)
                },
                onDownloadFailed = { failedFileName ->
                    updatePhotoDownloadFailed(failedFileName)
                }
            )
        }

        continuation.resume(Result.success(photo))
    }

    /**
     * 지원 이미지 포맷 확인
     * 개선: 포맷 검증 로직을 별도 메서드로 분리
     */
    private fun isSupportedImageFormat(extension: String): Boolean {
        return extension in Constants.ImageProcessing.SUPPORTED_IMAGE_EXTENSIONS
    }

    /**
     * CapturedPhoto 객체 생성
     * 개선: 객체 생성 로직을 별도 메서드로 분리하여 재사용성 향상
     */
    private fun createCapturedPhoto(fullPath: String, fileName: String): CapturedPhoto {
        return CapturedPhoto(
            id = UUID.randomUUID().toString(),
            filePath = fullPath,
            thumbnailPath = null,
            captureTime = System.currentTimeMillis(),
            cameraModel = connectionManager.cameraCapabilities.value?.model ?: "알 수 없음",
            settings = _cameraSettings.value,
            size = 0,
            width = 0,
            height = 0,
            isDownloading = true
        )
    }

    override fun startBurstCapture(count: Int): Flow<CapturedPhoto> = flow {
        // TODO: 연속 촬영 기능 구현
    }

    override fun startTimelapse(settings: TimelapseSettings): Flow<CapturedPhoto> = callbackFlow {
        awaitClose {
            // 타임랩스 종료 처리
        }
    }

    override fun startBracketing(settings: BracketingSettings): Flow<CapturedPhoto> = flow {
        // TODO: 브라켓팅 기능 구현
    }

    override suspend fun startBulbCapture(): Result<Boolean> {
        // TODO: 벌브 촬영 기능 구현
        return Result.success(true)
    }

    override suspend fun stopBulbCapture(): Result<CapturedPhoto> {
        // TODO: 벌브 촬영 중지 기능 구현
        return Result.failure(Exception("아직 구현되지 않음"))
    }

    override fun startLiveView(): Flow<LiveViewFrame> = callbackFlow {
        com.inik.camcon.utils.LogcatManager.d(TAG, "=== 라이브뷰 시작 (Repository) ===")
        com.inik.camcon.utils.LogcatManager.d(
            TAG,
            "USB 연결 상태: ${connectionManager.isConnected.value}"
        )
        com.inik.camcon.utils.LogcatManager.d(
            TAG,
            "PTPIP 연결 상태: ${connectionManager.isPtpipConnected.value}"
        )

        // 연결 상태 확인 (USB 또는 PTPIP 연결)
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
                override fun onLiveViewFrame(frame: ByteBuffer) {
                    try {
                        com.inik.camcon.utils.LogcatManager.d(
                            TAG,
                            "라이브뷰 프레임 콜백 수신: position=${frame.position()}, limit=${frame.limit()}"
                        )

                        val bytes = ByteArray(frame.remaining())
                        frame.get(bytes)

                        com.inik.camcon.utils.LogcatManager.d(
                            TAG,
                            "라이브뷰 프레임 변환 완료: ${bytes.size} bytes"
                        )

                        val liveViewFrame = LiveViewFrame(
                            data = bytes,
                            width = 0, // TODO: 실제 크기 가져오기
                            height = 0,
                            timestamp = System.currentTimeMillis()
                        )

                        val result = trySend(liveViewFrame)
                        com.inik.camcon.utils.LogcatManager.d(TAG, "프레임 전송 결과: ${result.isSuccess}")
                    } catch (e: Exception) {
                        Log.e(TAG, "라이브뷰 프레임 처리 실패", e)
                    }
                }

                override fun onLivePhotoCaptured(path: String) {
                    com.inik.camcon.utils.LogcatManager.d(TAG, "라이브뷰 중 사진 촬영: $path")
                    // 라이브뷰 중 촬영된 사진 처리
                }
            })

            com.inik.camcon.utils.LogcatManager.d(TAG, "라이브뷰 콜백 등록 완료")
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

    override suspend fun stopLiveView(): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                com.inik.camcon.utils.LogcatManager.d(TAG, "라이브뷰 명시적 중지")
                nativeDataSource.stopLiveView()
                Result.success(true)
            } catch (e: Exception) {
                Log.e(TAG, "라이브뷰 중지 실패", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun autoFocus(): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                com.inik.camcon.utils.LogcatManager.d(TAG, "자동초점 시작")
                val result = nativeDataSource.autoFocus()
                com.inik.camcon.utils.LogcatManager.d(TAG, "자동초점 결과: $result")
                Result.success(result)
            } catch (e: Exception) {
                Log.e(TAG, "자동초점 실패", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun manualFocus(x: Float, y: Float): Result<Boolean> {
        // TODO: 수동 초점 기능 구현
        return Result.success(true)
    }

    override suspend fun setFocusPoint(x: Float, y: Float): Result<Boolean> {
        // TODO: 초점 포인트 설정 기능 구현
        return Result.success(true)
    }

    override fun getCapturedPhotos(): Flow<List<CapturedPhoto>> =
        _capturedPhotos.asStateFlow()

    override suspend fun deletePhoto(photoId: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                com.inik.camcon.utils.LogcatManager.d(TAG, "사진 삭제: $photoId")
                withContext(Dispatchers.Main) {
                    _capturedPhotos.value = _capturedPhotos.value.filter { it.id != photoId }
                }
                Result.success(true)
            } catch (e: Exception) {
                Log.e(TAG, "사진 삭제 실패", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun downloadPhotoFromCamera(photoId: String): Result<CapturedPhoto> {
        return downloadManager.downloadPhotoFromCamera(
            photoId = photoId,
            cameraCapabilities = connectionManager.cameraCapabilities.value,
            cameraSettings = _cameraSettings.value
        )
    }

    override suspend fun getCameraCapabilities(): Result<CameraCapabilities?> {
        return withContext(Dispatchers.IO) {
            try {
                val capabilities = connectionManager.cameraCapabilities.value
                    ?: nativeDataSource.getCameraCapabilities()
                Result.success(capabilities)
            } catch (e: Exception) {
                Log.e(TAG, "카메라 기능 정보 가져오기 실패", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun getCameraPhotos(): Result<List<CameraPhoto>> {
        val result = downloadManager.getCameraPhotos()

        // 이벤트 리스너가 중지되었을 가능성이 있으므로 안전하게 재시작
        if (connectionManager.isConnected.value && result.isSuccess) {
            com.inik.camcon.utils.LogcatManager.d(TAG, "사진 목록 가져오기 후 이벤트 리스너 상태 확인 및 재시작")
            kotlinx.coroutines.delay(500)

            if (!eventManager.isRunning()) {
                try {
                    com.inik.camcon.utils.LogcatManager.d(TAG, "이벤트 리스너 재시작 시도")
                    startEventListenerInternal()
                } catch (e: Exception) {
                    Log.w(TAG, "이벤트 리스너 재시작 실패, 나중에 다시 시도", e)
                }
            } else {
                com.inik.camcon.utils.LogcatManager.d(TAG, "이벤트 리스너가 이미 실행 중")
            }
        }

        return result
    }

    override suspend fun getCameraPhotosPaged(
        page: Int,
        pageSize: Int
    ): Result<PaginatedCameraPhotos> {
        return downloadManager.getCameraPhotosPaged(
            page = page,
            pageSize = pageSize,
            isPhotoPreviewMode = eventManager.isPhotoPreviewMode(),
            onEventListenerRestart = {
                if (!eventManager.isRunning()) {
                    startEventListenerInternal()
                }
            }
        )
    }

    override suspend fun getCameraThumbnail(photoPath: String): Result<ByteArray> {
        return downloadManager.getCameraThumbnail(
            photoPath = photoPath,
            isConnected = connectionManager.isConnected.value,
            isInitializing = connectionManager.isInitializing.value,
            isNativeCameraConnected = usbCameraManager.isNativeCameraConnected.value
        )
    }

    /**
     * PTPIP 연결 상태
     */
    override fun isPtpipConnected(): Flow<Boolean> =
        connectionManager.isPtpipConnected

    /**
     * 이벤트 리스너를 재시도 로직과 함께 시작
     */
    private suspend fun startEventListenerWithRetry(): Unit {
        com.inik.camcon.utils.LogcatManager.d(TAG, "이벤트 리스너 시작 시도")
        startEventListenerInternal()
        com.inik.camcon.utils.LogcatManager.d(TAG, "이벤트 리스너 시작 후 상태: ${eventManager.isRunning()}")

        // 이벤트 리스너가 제대로 시작되었는지 확인 (재시도 로직 강화)
        var retryCount = 0
        val maxRetries = 5

        while (!eventManager.isRunning() && retryCount < maxRetries) {
            retryCount++
            com.inik.camcon.utils.LogcatManager.w(TAG, "이벤트 리스너 시작 실패, 재시도 $retryCount/$maxRetries")
            kotlinx.coroutines.delay(2000)

            // 카메라 연결 상태 재확인
            if (connectionManager.isConnected.value) {
                startEventListenerInternal()
                com.inik.camcon.utils.LogcatManager.d(
                    TAG,
                    "이벤트 리스너 재시도 후 상태: ${eventManager.isRunning()}"
                )
            } else {
                Log.e(TAG, "카메라 연결이 끊어져서 이벤트 리스너 재시도 중단")
                break
            }
        }

        if (!eventManager.isRunning()) {
            Log.e(TAG, "이벤트 리스너 시작 최종 실패 - 최대 재시도 횟수 초과")
        } else {
            com.inik.camcon.utils.LogcatManager.d(TAG, "이벤트 리스너 시작 성공")
        }
    }

    /**
     * 내부용 이벤트 리스너 시작
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
            onPhotoDownloaded = null, // 중복 처리 방지를 위해 null로 설정
            onFlushComplete = {
                com.inik.camcon.utils.LogcatManager.d(TAG, "🎯 카메라 이벤트 플러시 완료 - 초기화 상태 해제")
                uiStateManager.updateCameraInitialization(false)
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
     * 네이티브 다운로드된 사진 처리 - PhotoDownloadManager 통한 단일 다운로드
     */
    private fun handleNativePhotoDownload(
        fullPath: String,
        fileName: String,
        imageData: ByteArray
    ) {
        com.inik.camcon.utils.LogcatManager.d(TAG, "🎯 handleNativePhotoDownload 호출됨: $fileName")
        com.inik.camcon.utils.LogcatManager.d(TAG, "  📁 fullPath: $fullPath")
        com.inik.camcon.utils.LogcatManager.d(TAG, "  📊 imageData size: ${imageData.size} bytes")
        com.inik.camcon.utils.LogcatManager.d(TAG, "  🧵 스레드: ${Thread.currentThread().name}")

        // 파일 확장자 확인
        val extension = fileName.substringAfterLast(".", "").lowercase()
        if (extension !in Constants.ImageProcessing.SUPPORTED_IMAGE_EXTENSIONS) {
            com.inik.camcon.utils.LogcatManager.d(
                TAG,
                "❌ 지원하지 않는 파일 무시: $fileName (확장자: $extension)"
            )
            return
        }

        // PhotoDownloadManager의 handleNativePhotoDownload를 직접 호출
        repositoryScope.launch {
            com.inik.camcon.utils.LogcatManager.d(
                TAG,
                "🚀 PhotoDownloadManager.handleNativePhotoDownload 시작: $fileName"
            )

            val capturedPhoto = downloadManager.handleNativePhotoDownload(
                filePath = fullPath,
                fileName = fileName,
                imageData = imageData,
                cameraCapabilities = connectionManager.cameraCapabilities.value,
                cameraSettings = _cameraSettings.value
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

    /**
     * 네이티브에서 완전 처리된 사진 정보 업데이트 (실제 저장 경로 기반)
     */
    private fun handleNativePhotoDownloaded(
        filePath: String,
        fileName: String,
        imageData: ByteArray
    ) {
        com.inik.camcon.utils.LogcatManager.d(TAG, "=== 네이티브 사진 다운로드 완료 처리 ===")
        com.inik.camcon.utils.LogcatManager.d(TAG, "카메라 내부 경로: $filePath")
        com.inik.camcon.utils.LogcatManager.d(TAG, "파일명: $fileName")
        com.inik.camcon.utils.LogcatManager.d(TAG, "데이터 크기: ${imageData.size / 1024}KB")

        // 파일 확장자 확인
        val extension = fileName.substringAfterLast(".", "").lowercase()
        if (extension !in Constants.ImageProcessing.SUPPORTED_IMAGE_EXTENSIONS) {
            com.inik.camcon.utils.LogcatManager.d(TAG, "❌ 지원하지 않는 파일 확장자: $extension")
            return
        }

        // 카메라 폴더 구조 추출하여 실제 저장 경로 생성
        val cameraSubFolder = extractCameraSubFolder(filePath)
        val fileNameWithFolder = if (cameraSubFolder.isNotEmpty()) {
            "$cameraSubFolder/$fileName"
        } else {
            fileName
        }

        // 실제 저장될 경로 생성
        val actualFilePath = "/storage/emulated/0/DCIM/CamCon/$fileNameWithFolder"

        com.inik.camcon.utils.LogcatManager.d(TAG, "✅ 예상 저장 경로: $actualFilePath")

        // 실제 저장된 파일 정보로 CapturedPhoto 생성
        val photo = CapturedPhoto(
            id = UUID.randomUUID().toString(),
            filePath = actualFilePath, // 폴더 구조를 반영한 안드로이드 저장 경로 사용
            thumbnailPath = null,
            captureTime = System.currentTimeMillis(),
            cameraModel = "PTPIP Camera",
            settings = null,
            size = imageData.size.toLong(),
            width = 2000, // FREE 티어 리사이즈 크기
            height = 1330,
            isDownloading = false
        )

        com.inik.camcon.utils.LogcatManager.d(TAG, "네이티브 다운로드 완료 사진 객체 생성: ${photo.id}")

        // StateFlow에 추가하여 UI 업데이트
        updateDownloadedPhoto(photo)
        com.inik.camcon.utils.LogcatManager.d(TAG, "✅ 네이티브 다운로드 완료 사진 UI 업데이트 완료: $fileName")
    }

    /**
     * 카메라 내부 경로에서 서브폴더를 추출
     * 예: /store_00010001/DCIM/105KAY_1/KY6_0035.JPG → 105KAY_1
     */
    private fun extractCameraSubFolder(cameraFilePath: String): String {
        return try {
            // DCIM 다음의 첫 번째 폴더를 서브폴더로 사용
            val pathParts = cameraFilePath.split("/")
            val dcimIndex = pathParts.indexOfFirst { it.equals("DCIM", ignoreCase = true) }

            if (dcimIndex >= 0 && dcimIndex + 1 < pathParts.size) {
                val subFolder = pathParts[dcimIndex + 1]
                com.inik.camcon.utils.LogcatManager.d(
                    TAG,
                    "카메라 서브폴더 추출: $cameraFilePath → $subFolder"
                )
                subFolder
            } else {
                Log.w(TAG, "DCIM 폴더를 찾을 수 없음: $cameraFilePath")
                ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "카메라 서브폴더 추출 실패: $cameraFilePath", e)
            ""
        }
    }

    /**
     * PTPIP에서 다운로드된 사진의 실제 경로를 생성
     */
    private fun extractAndBuildActualPath(filePath: String, fileName: String): String {
        val cameraSubFolder = extractCameraSubFolder(filePath)
        val fileNameWithFolder = if (cameraSubFolder.isNotEmpty()) {
            "$cameraSubFolder/$fileName"
        } else {
            fileName
        }

        // 실제 저장될 경로 생성
        val actualFilePath = "/storage/emulated/0/DCIM/CamCon/$fileNameWithFolder"
        return actualFilePath
    }

    /**
     * 다운로드 완료된 사진 정보 업데이트
     * 개선: 중복 검사 로직 추가
     */
    private fun updateDownloadedPhoto(downloadedPhoto: CapturedPhoto) {
        val beforeCount = _capturedPhotos.value.size

        com.inik.camcon.utils.LogcatManager.d(TAG, "🔄 updateDownloadedPhoto 호출됨")
        com.inik.camcon.utils.LogcatManager.d(TAG, "  📷 사진 ID: ${downloadedPhoto.id}")
        com.inik.camcon.utils.LogcatManager.d(TAG, "  📁 파일 경로: ${downloadedPhoto.filePath}")
        com.inik.camcon.utils.LogcatManager.d(TAG, "  📊 파일 크기: ${downloadedPhoto.size} bytes")
        com.inik.camcon.utils.LogcatManager.d(TAG, "  📋 현재 StateFlow 크기: $beforeCount 개")
        com.inik.camcon.utils.LogcatManager.d(TAG, "  🧵 스레드: ${Thread.currentThread().name}")

        _capturedPhotos.value = _capturedPhotos.value + downloadedPhoto
        val afterCount = _capturedPhotos.value.size

        com.inik.camcon.utils.LogcatManager.d(
            TAG,
            "✅ StateFlow 업데이트 완료: $beforeCount -> $afterCount 개"
        )
        com.inik.camcon.utils.LogcatManager.d(TAG, "  🎯 총 사진 개수: ${_capturedPhotos.value.size}개")

        // 중복 확인
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

    /**
     * 다운로드 실패한 사진 제거
     */
    private fun updatePhotoDownloadFailed(fileName: String) {
        repositoryScope.launch(Dispatchers.Main) {
            _capturedPhotos.value = _capturedPhotos.value.filter { it.filePath != fileName }
            com.inik.camcon.utils.LogcatManager.d(TAG, "❌ 다운로드 실패한 사진 제거: $fileName")
        }
    }

    /**
     * USB 분리 콜백 설정
     */
    fun setUsbDisconnectionCallback(callback: () -> Unit) {
        eventManager.onUsbDisconnectedCallback = callback
    }

    /**
     * RAW 파일 제한 다이얼로그 콜백 설정
     */
    override fun setRawFileRestrictionCallback(
        callback: ((fileName: String, restrictionMessage: String) -> Unit)?
    ) {
        eventManager.onRawFileRestricted = callback
    }
}