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
import com.inik.camcon.domain.usecase.ColorTransferUseCase
import com.inik.camcon.domain.usecase.camera.PhotoCaptureEventManager
import com.inik.camcon.domain.usecase.GetSubscriptionUseCase
import com.inik.camcon.utils.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

@Singleton
class CameraRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val nativeDataSource: NativeCameraDataSource,
    private val ptpipDataSource: PtpipDataSource,
    private val usbCameraManager: UsbCameraManager,
    private val photoCaptureEventManager: PhotoCaptureEventManager,
    private val appPreferencesDataSource: AppPreferencesDataSource,
    private val colorTransferUseCase: ColorTransferUseCase,
    private val connectionManager: CameraConnectionManager,
    private val eventManager: CameraEventManager,
    private val downloadManager: PhotoDownloadManager,
    private val uiStateManager: com.inik.camcon.presentation.viewmodel.state.CameraUiStateManager,
    private val connectionGlobalManager: com.inik.camcon.domain.manager.CameraConnectionGlobalManager,
    private val getSubscriptionUseCase: GetSubscriptionUseCase
) : CameraRepository {

    init {
        // GPU 초기화
        colorTransferUseCase.initializeGPU(context)

        // PTPIP 사진 다운로드 콜백 설정
        ptpipDataSource.setPhotoDownloadedCallback { filePath, fileName, imageData ->
            handleNativePhotoDownload(filePath, fileName, imageData)
        }

        // PTPIP 연결 끊어짐 콜백 설정
        ptpipDataSource.setConnectionLostCallback {
            com.inik.camcon.utils.LogcatManager.w("카메라레포지토리", "🚨 PTPIP Wi-Fi 연결이 끊어졌습니다")
            // 추가 처리: 이벤트 리스너 중지, 상태 초기화 등
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // 네이티브 이벤트 리스너 중지
                    CameraNative.stopListenCameraEvents()
                    com.inik.camcon.utils.LogcatManager.d(
                        "카메라레포지토리",
                        "🛑 PTPIP 연결 끊어짐으로 인한 이벤트 리스너 중지 완료"
                    )
                } catch (e: Exception) {
                    com.inik.camcon.utils.LogcatManager.e("카메라레포지토리", "이벤트 리스너 중지 실패", e)
                }
            }
        }
    }

    private val _capturedPhotos = MutableStateFlow<List<CapturedPhoto>>(emptyList())
    private val _cameraSettings = MutableStateFlow<CameraSettings?>(null)

    override fun getCameraFeed(): Flow<List<Camera>> =
        connectionManager.cameraFeed

    override suspend fun connectCamera(cameraId: String): Result<Boolean> {
        val result = connectionManager.connectCamera(cameraId)
        if (result.isSuccess) {
            // 카메라 연결 완료 후 안정화 대기
            Log.d("카메라레포지토리", "카메라 연결 완료 - 안정화 대기 시작")
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
        // val subscriptionTier = getSubscriptionUseCase.getSubscriptionTier().first() // Flow에서 한 번만 가져올 때
        return eventManager.startCameraEventListener(
            isConnected = connectionManager.isConnected.value,
            isInitializing = connectionManager.isInitializing.value,
            saveDirectory = downloadManager.getSaveDirectory(),
            onPhotoCaptured = { _, _ ->
                // handleNativePhotoDownloaded에서만 처리하므로 빈 콜백
            },
            onPhotoDownloaded = { fullPath, fileName, imageData ->
                handleNativePhotoDownload(fullPath, fileName, imageData)
            },
            onFlushComplete = {
                Log.d("카메라레포지토리", "🎯 카메라 이벤트 플러시 완료 - 초기화 상태 해제")
                uiStateManager.updateCameraInitialization(false)
                Log.d("카메라레포지토리", "✅ UI 블로킹 해제 완료 (isCameraInitializing = false)")
            },
            onCaptureFailed = { errorCode ->
                Log.e("카메라레포지토리", "외부 셔터 촬영 실패: $errorCode")
            }
            // , subscriptionTier = subscriptionTier
        )
    }

    override suspend fun stopCameraEventListener(): Result<Boolean> {
        return eventManager.stopCameraEventListener()
    }

    override suspend fun getCameraSettings(): Result<CameraSettings> {
        return withContext(Dispatchers.IO) {
            try {
                // 캐시된 설정이 있으면 우선 반환
                _cameraSettings.value?.let { cachedSettings ->
                    Log.d("카메라레포지토리", "캐시된 카메라 설정 반환")
                    return@withContext Result.success(cachedSettings)
                }

                // 위젯 JSON에서 설정 파싱 - 마스터 데이터를 우선 사용
                val widgetJson = if (usbCameraManager.isNativeCameraConnected.value) {
                    Log.d("카메라레포지토리", "USB 카메라 연결됨 - 마스터 데이터 사용")
                    usbCameraManager.buildWidgetJsonFromMaster()
                } else {
                    // 마스터 데이터가 있으면 우선 사용, 없으면 직접 호출
                    val masterData = usbCameraManager.buildWidgetJsonFromMaster()
                    if (masterData.isNotEmpty()) {
                        Log.d("카메라레포지토리", "USB 카메라 미연결이지만 마스터 데이터 사용")
                        masterData
                    } else {
                        Log.d("카메라레포지토리", "마스터 데이터 없음 - 직접 네이티브 호출")
                        nativeDataSource.buildWidgetJson()
                    }
                }

                // TODO: JSON 파싱하여 설정 추출
                val settings = CameraSettings(
                    iso = "100",
                    shutterSpeed = "1/125",
                    aperture = "2.8",
                    whiteBalance = "자동",
                    focusMode = "AF-S",
                    exposureCompensation = "0"
                )

                withContext(Dispatchers.Main) {
                    _cameraSettings.value = settings
                }

                Log.d("카메라레포지토리", "카메라 설정 업데이트")
                Result.success(settings)
            } catch (e: Exception) {
                Log.e("카메라레포지토리", "카메라 설정 가져오기 실패", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun getCameraInfo(): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val summary = nativeDataSource.getCameraSummary()
                Result.success(summary.name)
            } catch (e: Exception) {
                Log.e("카메라레포지토리", "카메라 정보 가져오기 실패", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun updateCameraSetting(key: String, value: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("카메라레포지토리", "카메라 설정 업데이트: $key = $value")
                Result.success(true)
            } catch (e: Exception) {
                Log.e("카메라레포지토리", "카메라 설정 업데이트 실패", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun capturePhoto(mode: ShootingMode): Result<CapturedPhoto> {
        return suspendCancellableCoroutine<Result<CapturedPhoto>> { continuation ->
            val saveDir = downloadManager.getSaveDirectory()
            Log.d("카메라레포지토리", "=== 사진 촬영 시작 ===")
            Log.d("카메라레포지토리", "촬영 모드: $mode")
            Log.d("카메라레포지토리", "저장 디렉토리: $saveDir")
            Log.d("카메라레포지토리", "카메라 연결 상태: ${connectionManager.isConnected.value}")

            // 연결 상태 확인
            if (!connectionManager.isConnected.value) {
                Log.e("카메라레포지토리", "카메라가 연결되지 않은 상태에서 사진 촬영 불가")
                continuation.resumeWithException(Exception("카메라가 연결되지 않음"))
                return@suspendCancellableCoroutine
            }

            try {
                Log.d("카메라레포지토리", "비동기 사진 촬영 호출 시작")
                continuation.invokeOnCancellation {
                    Log.d("카메라레포지토리", "사진 촬영 취소됨")
                }

                nativeDataSource.capturePhotoAsync(object : CameraCaptureListener {
                    override fun onFlushComplete() {
                        Log.d("카메라레포지토리", "✓ 사진 촬영 플러시 완료")
                    }

                    override fun onPhotoCaptured(fullPath: String, fileName: String) {
                        Log.d("카메라레포지토리", "✓ 사진 촬영 완료!!!")
                        Log.d("카메라레포지토리", "파일명: $fileName")
                        Log.d("카메라레포지토리", "전체 경로: $fullPath")

                        // 파일 확장자 확인 
                        val extension = fileName.substringAfterLast(".", "").lowercase()
                        if (extension !in Constants.ImageProcessing.SUPPORTED_IMAGE_EXTENSIONS) {
                            Log.d("카메라레포지토리", "지원하지 않는 파일 무시: $fileName (확장자: $extension)")
                            return
                        }

                        val photo = CapturedPhoto(
                            id = UUID.randomUUID().toString(),
                            filePath = fullPath,
                            thumbnailPath = null,
                            captureTime = System.currentTimeMillis(),
                            cameraModel = connectionManager.cameraCapabilities.value?.model
                                ?: "알 수 없음",
                            settings = _cameraSettings.value,
                            size = 0,
                            width = 0,
                            height = 0,
                            isDownloading = true
                        )

                        CoroutineScope(Dispatchers.IO).launch {
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

                    override fun onPhotoDownloaded(
                        filePath: String,
                        fileName: String,
                        imageData: ByteArray
                    ) {
                        Log.d("카메라레포지토리", "✓ Native 사진 다운로드 완료!!!")
                        Log.d("카메라레포지토리", "파일명: $fileName")
                        Log.d("카메라레포지토리", "데이터 크기: ${imageData.size / 1024}KB")

                        handleNativePhotoDownload(filePath, fileName, imageData)
                    }

                    override fun onCaptureFailed(errorCode: Int) {
                        Log.e("카메라레포지토리", "✗ 사진 촬영 실패, 오류 코드: $errorCode")
                        continuation.resumeWithException(Exception("사진 촬영 실패: 오류 코드 $errorCode"))
                    }

                    override fun onUsbDisconnected() {
                        Log.e("카메라레포지토리", "USB 디바이스 분리 감지 - 촬영 실패 처리")
                        continuation.resumeWithException(Exception("USB 디바이스가 분리되어 촬영을 완료할 수 없습니다"))
                    }
                }, saveDir)

                Log.d("카메라레포지토리", "비동기 사진 촬영 호출 완료, 콜백 대기 중...")
            } catch (e: Exception) {
                Log.e("카메라레포지토리", "사진 촬영 중 예외 발생", e)
                continuation.resumeWithException(e)
            }
        }
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
        Log.d("카메라레포지토리", "=== 라이브뷰 시작 (Repository) ===")
        Log.d("카메라레포지토리", "카메라 연결 상태: ${connectionManager.isConnected.value}")

        // 연결 상태 확인
        if (!connectionManager.isConnected.value) {
            Log.e("카메라레포지토리", "카메라가 연결되지 않은 상태에서 라이브뷰 시작 불가")
            close(IllegalStateException("카메라가 연결되지 않음"))
            return@callbackFlow
        }

        try {
            Log.d("카메라레포지토리", "네이티브 startLiveView 호출 시작 (자동초점 생략)")
            nativeDataSource.startLiveView(object : LiveViewCallback {
                override fun onLiveViewFrame(frame: ByteBuffer) {
                    try {
                        Log.d(
                            "카메라레포지토리",
                            "라이브뷰 프레임 콜백 수신: position=${frame.position()}, limit=${frame.limit()}"
                        )

                        val bytes = ByteArray(frame.remaining())
                        frame.get(bytes)

                        Log.d("카메라레포지토리", "라이브뷰 프레임 변환 완료: ${bytes.size} bytes")

                        val liveViewFrame = LiveViewFrame(
                            data = bytes,
                            width = 0, // TODO: 실제 크기 가져오기
                            height = 0,
                            timestamp = System.currentTimeMillis()
                        )

                        val result = trySend(liveViewFrame)
                        Log.d("카메라레포지토리", "프레임 전송 결과: ${result.isSuccess}")
                    } catch (e: Exception) {
                        Log.e("카메라레포지토리", "라이브뷰 프레임 처리 실패", e)
                    }
                }

                override fun onLivePhotoCaptured(path: String) {
                    Log.d("카메라레포지토리", "라이브뷰 중 사진 촬영: $path")
                    // 라이브뷰 중 촬영된 사진 처리
                }
            })

            Log.d("카메라레포지토리", "라이브뷰 콜백 등록 완료")
        } catch (e: Exception) {
            Log.e("카메라레포지토리", "라이브뷰 시작 실패", e)
            close(e)
        }

        awaitClose {
            Log.d("카메라레포지토리", "라이브뷰 중지 (awaitClose)")
            try {
                nativeDataSource.stopLiveView()
                Log.d("카메라레포지토리", "라이브뷰 중지 완료")
            } catch (e: Exception) {
                Log.e("카메라레포지토리", "라이브뷰 중지 중 오류", e)
            }
        }
    }

    override suspend fun stopLiveView(): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("카메라레포지토리", "라이브뷰 명시적 중지")
                nativeDataSource.stopLiveView()
                Result.success(true)
            } catch (e: Exception) {
                Log.e("카메라레포지토리", "라이브뷰 중지 실패", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun autoFocus(): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("카메라레포지토리", "자동초점 시작")
                val result = nativeDataSource.autoFocus()
                Log.d("카메라레포지토리", "자동초점 결과: $result")
                Result.success(result)
            } catch (e: Exception) {
                Log.e("카메라레포지토리", "자동초점 실패", e)
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
                Log.d("카메라레포지토리", "사진 삭제: $photoId")
                withContext(Dispatchers.Main) {
                    _capturedPhotos.value = _capturedPhotos.value.filter { it.id != photoId }
                }
                Result.success(true)
            } catch (e: Exception) {
                Log.e("카메라레포지토리", "사진 삭제 실패", e)
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
                Log.e("카메라레포지토리", "카메라 기능 정보 가져오기 실패", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun getCameraPhotos(): Result<List<CameraPhoto>> {
        val result = downloadManager.getCameraPhotos()

        // 이벤트 리스너가 중지되었을 가능성이 있으므로 안전하게 재시작
        if (connectionManager.isConnected.value && result.isSuccess) {
            Log.d("카메라레포지토리", "사진 목록 가져오기 후 이벤트 리스너 상태 확인 및 재시작")
            kotlinx.coroutines.delay(500)

            if (!eventManager.isRunning()) {
                try {
                    Log.d("카메라레포지토리", "이벤트 리스너 재시작 시도")
                    startEventListenerInternal()
                } catch (e: Exception) {
                    Log.w("카메라레포지토리", "이벤트 리스너 재시작 실패, 나중에 다시 시도", e)
                }
            } else {
                Log.d("카메라레포지토리", "이벤트 리스너가 이미 실행 중")
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
        Log.d("카메라레포지토리", "이벤트 리스너 시작 시도")
        startEventListenerInternal()
        Log.d("카메라레포지토리", "이벤트 리스너 시작 후 상태: ${eventManager.isRunning()}")

        // 이벤트 리스너가 제대로 시작되었는지 확인 (재시도 로직 강화)
        var retryCount = 0
        val maxRetries = 5

        while (!eventManager.isRunning() && retryCount < maxRetries) {
            retryCount++
            Log.w("카메라레포지토리", "이벤트 리스너 시작 실패, 재시도 $retryCount/$maxRetries")
            kotlinx.coroutines.delay(2000)

            // 카메라 연결 상태 재확인
            if (connectionManager.isConnected.value) {
                startEventListenerInternal()
                Log.d("카메라레포지토리", "이벤트 리스너 재시도 후 상태: ${eventManager.isRunning()}")
            } else {
                Log.e("카메라레포지토리", "카메라 연결이 끊어져서 이벤트 리스너 재시도 중단")
                break
            }
        }

        if (!eventManager.isRunning()) {
            Log.e("카메라레포지토리", "이벤트 리스너 시작 최종 실패 - 최대 재시도 횟수 초과")
        } else {
            Log.d("카메라레포지토리", "이벤트 리스너 시작 성공")
        }
    }

    /**
     * 내부용 이벤트 리스너 시작
     */
    private suspend fun startEventListenerInternal() {
        eventManager.startCameraEventListener(
            isConnected = connectionManager.isConnected.value,
            isInitializing = connectionManager.isInitializing.value,
            saveDirectory = downloadManager.getSaveDirectory(),
            onPhotoCaptured = { _, _ ->
                // handleNativePhotoDownloaded에서만 처리하므로 빈 콜백
            },
            onPhotoDownloaded = { fullPath, fileName, imageData ->
                handleNativePhotoDownload(fullPath, fileName, imageData)
            },
            onFlushComplete = {
                Log.d("카메라레포지토리", "🎯 카메라 이벤트 플러시 완료 - 초기화 상태 해제")
                uiStateManager.updateCameraInitialization(false)
                Log.d("카메라레포지토리", "✅ UI 블로킹 해제 완료 (isCameraInitializing = false)")
            },
            onCaptureFailed = { errorCode ->
                Log.e("카메라레포지토리", "외부 셔터 촬영 실패: $errorCode")
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
        Log.d("카메라레포지토리", "🎯 네이티브 사진 다운로드 처리: $fileName")

        // 파일 확장자 확인
        val extension = fileName.substringAfterLast(".", "").lowercase()
        if (extension !in Constants.ImageProcessing.SUPPORTED_IMAGE_EXTENSIONS) {
            Log.d("카메라레포지토리", "지원하지 않는 파일 무시: $fileName (확장자: $extension)")
            return
        }

        val tempPhoto = CapturedPhoto(
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

        // PhotoDownloadManager를 통한 단일 다운로드 처리
        CoroutineScope(Dispatchers.IO).launch {
            Log.d("카메라레포지토리", "📥 PhotoDownloadManager를 통한 다운로드 시작: $fileName")
            downloadManager.handlePhotoDownload(
                photo = tempPhoto,
                fullPath = fullPath,
                fileName = fileName,
                cameraCapabilities = connectionManager.cameraCapabilities.value,
                cameraSettings = _cameraSettings.value,
                imageData = imageData,
                onPhotoDownloaded = { downloadedPhoto ->
                    updateDownloadedPhoto(downloadedPhoto)
                    Log.d("카메라레포지토리", "✅ 네이티브 사진 다운로드 완료: $fileName")
                },
                onDownloadFailed = { failedFileName ->
                    updatePhotoDownloadFailed(failedFileName)
                    Log.e("카메라레포지토리", "❌ 네이티브 사진 다운로드 실패: $failedFileName")
                }
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
        com.inik.camcon.utils.LogcatManager.d("카메라레포지토리", "=== 네이티브 사진 다운로드 완료 처리 ===")
        com.inik.camcon.utils.LogcatManager.d("카메라레포지토리", "카메라 내부 경로: $filePath")
        com.inik.camcon.utils.LogcatManager.d("카메라레포지토리", "파일명: $fileName")
        com.inik.camcon.utils.LogcatManager.d("카메라레포지토리", "데이터 크기: ${imageData.size / 1024}KB")

        // 파일 확장자 확인
        val extension = fileName.substringAfterLast(".", "").lowercase()
        if (extension !in Constants.ImageProcessing.SUPPORTED_IMAGE_EXTENSIONS) {
            com.inik.camcon.utils.LogcatManager.d("카메라레포지토리", "❌ 지원하지 않는 파일 확장자: $extension")
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

        com.inik.camcon.utils.LogcatManager.d("카메라레포지토리", "✅ 예상 저장 경로: $actualFilePath")

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

        com.inik.camcon.utils.LogcatManager.d("카메라레포지토리", "네이티브 다운로드 완료 사진 객체 생성: ${photo.id}")

        // StateFlow에 추가하여 UI 업데이트
        updateDownloadedPhoto(photo)
        Log.d("카메라레포지토리", "✅ 네이티브 다운로드 완료 사진 UI 업데이트 완료: $fileName")
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
                Log.d("카메라레포지토리", "카메라 서브폴더 추출: $cameraFilePath → $subFolder")
                subFolder
            } else {
                Log.w("카메라레포지토리", "DCIM 폴더를 찾을 수 없음: $cameraFilePath")
                ""
            }
        } catch (e: Exception) {
            Log.e("카메라레포지토리", "카메라 서브폴더 추출 실패: $cameraFilePath", e)
            ""
        }
    }

    /**
     * 다운로드 완료된 사진 정보 업데이트
     */
    private fun updateDownloadedPhoto(downloadedPhoto: CapturedPhoto) {
        val beforeCount = _capturedPhotos.value.size
        _capturedPhotos.value = _capturedPhotos.value + downloadedPhoto
        val afterCount = _capturedPhotos.value.size

        Log.d("카메라레포지토리", "✓ 사진 다운로드 완료 업데이트. 총 ${_capturedPhotos.value.size}개")
        com.inik.camcon.utils.LogcatManager.d("카메라레포지토리", "=== 사진 StateFlow 업데이트 ===")
        com.inik.camcon.utils.LogcatManager.d("카메라레포지토리", "업데이트 전: ${beforeCount}개")
        com.inik.camcon.utils.LogcatManager.d("카메라레포지토리", "업데이트 후: ${afterCount}개")
        com.inik.camcon.utils.LogcatManager.d("카메라레포지토리", "추가된 사진 ID: ${downloadedPhoto.id}")
        com.inik.camcon.utils.LogcatManager.d("카메라레포지토리", "추가된 사진 경로: ${downloadedPhoto.filePath}")
        com.inik.camcon.utils.LogcatManager.d("카메라레포지토리", "✅ _capturedPhotos StateFlow 업데이트 완료")
    }

    /**
     * 다운로드 실패한 사진 제거
     */
    private fun updatePhotoDownloadFailed(fileName: String) {
        CoroutineScope(Dispatchers.Main).launch {
            _capturedPhotos.value = _capturedPhotos.value.filter { it.filePath != fileName }
            Log.d("카메라레포지토리", "❌ 다운로드 실패한 사진 제거: $fileName")
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
    fun setRawFileRestrictionCallback(callback: (fileName: String, restrictionMessage: String) -> Unit) {
        eventManager.onRawFileRestricted = callback
    }
}