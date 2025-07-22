package com.inik.camcon.data.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.inik.camcon.data.datasource.local.AppPreferencesDataSource
import com.inik.camcon.data.datasource.nativesource.CameraCaptureListener
import com.inik.camcon.data.datasource.nativesource.LiveViewCallback
import com.inik.camcon.data.datasource.nativesource.NativeCameraDataSource
import com.inik.camcon.data.datasource.usb.UsbCameraManager
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class CameraRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val nativeDataSource: NativeCameraDataSource,
    private val usbCameraManager: UsbCameraManager,
    private val photoCaptureEventManager: PhotoCaptureEventManager,
    private val appPreferencesDataSource: AppPreferencesDataSource,
    private val colorTransferUseCase: ColorTransferUseCase
) : CameraRepository {

    init {
        // GPU 초기화
        colorTransferUseCase.initializeGPU(context)
    }

    private val _cameraFeed = MutableStateFlow<List<Camera>>(emptyList())
    private val _isConnected = MutableStateFlow(false)
    private val _capturedPhotos = MutableStateFlow<List<CapturedPhoto>>(emptyList())
    private val _cameraCapabilities = MutableStateFlow<CameraCapabilities?>(null)
    private val _cameraSettings = MutableStateFlow<CameraSettings?>(null)

    // 카메라 이벤트 리스너 상태 추적
    private val _isEventListenerActive = MutableStateFlow(false)
    private var isEventListenerRunning = false

    init {
        // USB 카메라 매니저의 네이티브 카메라 연결 상태를 관찰
        observeNativeCameraConnection()
    }

    override fun getCameraFeed(): Flow<List<Camera>> = _cameraFeed.asStateFlow()

    override suspend fun connectCamera(cameraId: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("카메라레포지토리", "카메라 연결 시작: $cameraId")

                // USB 디바이스 확인 및 연결
                // StateFlow를 통해 이미 검색된 디바이스 목록 사용 (중복 검색 방지)
                val usbDevices = usbCameraManager.connectedDevices.value
                if (usbDevices.isNotEmpty()) {
                    val device = usbDevices.first()
                    Log.d("카메라레포지토리", "연결된 USB 디바이스 발견: ${device.deviceName}")

                    // USB 권한 요청
                    if (!usbCameraManager.hasUsbPermission.value) {
                        Log.d("카메라레포지토리", "USB 권한 없음, 권한 요청")
                        withContext(Dispatchers.Main) {
                            usbCameraManager.requestPermission(device)
                        }
                        Result.failure(Exception("USB 권한이 필요합니다"))
                    } else {
                        // 파일 디스크립터를 사용한 네이티브 초기화
                        val fd = usbCameraManager.getFileDescriptor()
                        if (fd != null) {
                            Log.d("카메라레포지토리", "파일 디스크립터로 카메라 초기화: $fd")
                            val nativeLibDir = "/data/data/com.inik.camcon/lib"
                            val result = nativeDataSource.initCameraWithFd(fd, nativeLibDir)
                            if (result == 0) {
                                Log.d("카메라레포지토리", "네이티브 카메라 초기화 성공")
                                withContext(Dispatchers.Main) {
                                    _isConnected.value = true
                                }
                                updateCameraList()
                                // updateCameraCapabilities() 제거 - observeNativeCameraConnection에서 처리됨
                                Log.d("카메라레포지토리", "이벤트 리스너 시작 시도")
                                startCameraEventListenerInternal()
                                Log.d("카메라레포지토리", "이벤트 리스너 시작 후 상태: $isEventListenerRunning")

                                // 이벤트 리스너가 제대로 시작되었는지 확인
                                kotlinx.coroutines.delay(1000) // 1초 대기
                                Log.d("카메라레포지토리", "이벤트 리스너 1초 후 상태: $isEventListenerRunning")

                                Result.success(true)
                            } else {
                                Log.e("카메라레포지토리", "네이티브 카메라 초기화 실패: $result")
                                Result.failure(Exception("카메라 연결 실패: $result"))
                            }
                        } else {
                            Result.failure(Exception("파일 디스크립터를 가져올 수 없음"))
                        }
                    }
                } else {
                    // USB 연결이 안되면 일반 초기화 시도
                    Log.d("카메라레포지토리", "일반 카메라 초기화 시도")
                    val result = nativeDataSource.initCamera()
                    if (result.contains("success", ignoreCase = true)) {
                        Log.d("카메라레포지토리", "일반 카메라 초기화 성공")
                        withContext(Dispatchers.Main) {
                            _isConnected.value = true
                        }
                        updateCameraList()
                        // updateCameraCapabilities() 제거 - observeNativeCameraConnection에서 처리됨
                        Log.d("카메라레포지토리", "이벤트 리스너 시작 시도")
                        startCameraEventListenerInternal()
                        Log.d("카메라레포지토리", "이벤트 리스너 시작 후 상태: $isEventListenerRunning")

                        // 이벤트 리스너가 제대로 시작되었는지 확인
                        kotlinx.coroutines.delay(1000) // 1초 대기
                        Log.d("카메라레포지토리", "이벤트 리스너 1초 후 상태: $isEventListenerRunning")

                        Result.success(true)
                    } else {
                        Log.e("카메라레포지토리", "일반 카메라 초기화 실패: $result")
                        Result.failure(Exception("카메라 연결 실패: $result"))
                    }
                }
            } catch (e: Exception) {
                Log.e("카메라레포지토리", "카메라 연결 중 예외 발생", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun disconnectCamera(): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("카메라레포지토리", "카메라 연결 해제 시작")

                // 이벤트 리스너 중지
                stopCameraEventListenerInternal()

                // 네이티브 카메라 연결 해제
                nativeDataSource.closeCamera()

                withContext(Dispatchers.Main) {
                    _isConnected.value = false
                    _cameraFeed.value = emptyList()
                }

                Log.d("카메라레포지토리", "카메라 연결 해제 완료")
                Result.success(true)
            } catch (e: Exception) {
                Log.e("카메라레포지토리", "카메라 연결 해제 중 오류", e)
                Result.failure(e)
            }
        }
    }

    override fun isCameraConnected(): Flow<Boolean> = _isConnected.asStateFlow()

    override fun isEventListenerActive(): Flow<Boolean> = _isEventListenerActive.asStateFlow()

    /**
     * 카메라 이벤트 리스너 시작 (public)
     */
    override suspend fun startCameraEventListener(): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                if (isEventListenerRunning) {
                    Log.d("카메라레포지토리", "카메라 이벤트 리스너가 이미 실행 중입니다 (public)")
                    return@withContext Result.success(true)
                }

                // 카메라 연결 상태 확인
                if (!_isConnected.value) {
                    Log.e("카메라레포지토리", "카메라가 연결되지 않은 상태에서 이벤트 리스너 시작 불가 (public)")
                    return@withContext Result.failure(Exception("카메라가 연결되지 않음"))
                }

                Log.d("카메라레포지토리", "=== 카메라 이벤트 리스너 시작 (public) ===")

                // 내부 함수 호출
                startCameraEventListenerInternal()

                Result.success(true)
            } catch (e: Exception) {
                Log.e("카메라레포지토리", "❌ 카메라 이벤트 리스너 시작 실패 (public)", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun getCameraSettings(): Result<CameraSettings> {
        return withContext(Dispatchers.IO) {
            try {
                // 위젯 JSON에서 설정 파싱 - 무거운 작업
                val widgetJson = nativeDataSource.buildWidgetJson()
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
            // 네이티브 코드에서 자동으로 우선순위에 따라 저장 경로 결정
            val saveDir = getSaveDirectory()
            Log.d("카메라레포지토리", "=== 사진 촬영 시작 ===")
            Log.d("카메라레포지토리", "촬영 모드: $mode")
            Log.d("카메라레포지토리", "저장 디렉토리: $saveDir")
            Log.d("카메라레포지토리", "카메라 연결 상태: ${_isConnected.value}")

            // 연결 상태 확인
            if (!_isConnected.value) {
                Log.e("카메라레포지토리", "카메라가 연결되지 않은 상태에서 사진 촬영 불가")
                continuation.resumeWithException(Exception("카메라가 연결되지 않음"))
                return@suspendCancellableCoroutine
            }

            try {
                Log.d("카메라레포지토리", "비동기 사진 촬영 호출 시작")
                continuation.invokeOnCancellation {
                    Log.d("카메라레포지토리", "사진 촬영 취소됨")
                    // 진행 중인 촬영 작업이 있다면 취소 처리
                }

                nativeDataSource.capturePhotoAsync(object : CameraCaptureListener {
                    override fun onFlushComplete() {
                        Log.d("카메라레포지토리", "✓ 사진 촬영 플러시 완료")
                    }

                    override fun onPhotoCaptured(fullPath: String, fileName: String) {
                        Log.d("카메라레포지토리", "✓ 사진 촬영 완료!!!")
                        Log.d("카메라레포지토리", "파일명: $fileName")
                        Log.d("카메라레포지토리", "전체 경로: $fullPath")

                        // 파일 확장자 확인 - JPEG만 처리
                        val extension = fileName.substringAfterLast(".", "").lowercase()
                        if (extension !in listOf("jpg", "jpeg")) {
                            Log.d("카메라레포지토리", "JPEG가 아닌 파일 무시: $fileName (확장자: $extension)")
                            return
                        }

                        Log.d("카메라레포지토리", "JPEG 파일 처리: $fileName (확장자: $extension)")

                        // 파일 존재 확인
                        val file = File(fullPath)
                        Log.d("카메라레포지토리", "파일 존재: ${file.exists()}")
                        if (file.exists()) {
                            Log.d("카메라레포지토리", "파일 크기: ${file.length()} 바이트")
                        }

                        val photo = CapturedPhoto(
                            id = UUID.randomUUID().toString(),
                            filePath = fullPath,
                            thumbnailPath = null,
                            captureTime = System.currentTimeMillis(),
                            cameraModel = _cameraCapabilities.value?.model ?: "알 수 없음",
                            settings = _cameraSettings.value,
                            size = 0, // 아직 다운로드 전
                            width = 0,
                            height = 0,
                            isDownloading = true // 다운로드 중 표시
                        )

                        CoroutineScope(Dispatchers.IO).launch {
                            handlePhotoDownload(photo, fullPath, fileName)
                        }

                        continuation.resume(Result.success(photo))
                    }

                    override fun onCaptureFailed(errorCode: Int) {
                        Log.e(
                            "카메라레포지토리",
                            "✗ 사진 촬영 실패, 오류 코드: $errorCode"
                        )
                        continuation.resume(Result.failure(Exception("사진 촬영 실패: 오류 코드 $errorCode")))
                    }
                }, saveDir)

                Log.d("카메라레포지토리", "비동기 사진 촬영 호출 완료, 콜백 대기 중...")
            } catch (e: Exception) {
                Log.e("카메라레포지토리", "사진 촬영 중 예외 발생", e)
                continuation.resume(Result.failure(e))
            }
        }
    }

    override fun startBurstCapture(count: Int): Flow<CapturedPhoto> = flow {
        // TODO: 연속 촬영 기능 구현
    }

    override fun startTimelapse(settings: TimelapseSettings): Flow<CapturedPhoto> = callbackFlow {
        // 타임랩스는 이제 일반 이벤트 리스너를 통해 처리됨
        // 타임랩스 특정 로직은 추후 구현 필요
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
        Log.d("카메라레포지토리", "라이브뷰 시작")

        try {
            // 라이브뷰 시작 전에 자동초점 활성화 - IO 스레드에서 실행
            launch(Dispatchers.IO) {
                try {
                    nativeDataSource.autoFocus()
                } catch (e: Exception) {
                    Log.w("카메라레포지토리", "라이브뷰 시작 전 자동초점 실패", e)
                }
            }

            nativeDataSource.startLiveView(object : LiveViewCallback {
                override fun onLiveViewFrame(frame: ByteBuffer) {
                    try {
                        val bytes = ByteArray(frame.remaining())
                        frame.get(bytes)

                        trySend(
                            LiveViewFrame(
                                data = bytes,
                                width = 0, // TODO: 실제 크기 가져오기
                                height = 0,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    } catch (e: Exception) {
                        Log.e("카메라레포지토리", "라이브뷰 프레임 처리 실패", e)
                    }
                }

                override fun onLivePhotoCaptured(path: String) {
                    Log.d("카메라레포지토리", "라이브뷰 중 사진 촬영: $path")
                    // 라이브뷰 중 촬영된 사진 처리
                }
            })
        } catch (e: Exception) {
            Log.e("카메라레포지토리", "라이브뷰 시작 실패", e)
            close(e)
        }

        awaitClose {
            Log.d("카메라레포지토리", "라이브뷰 중지")
            try {
                nativeDataSource.stopLiveView()
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

    override fun getCapturedPhotos(): Flow<List<CapturedPhoto>> = _capturedPhotos.asStateFlow()

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
        return withContext(Dispatchers.IO) {
            try {
                Log.d("카메라레포지토리", "=== 카메라에서 사진 다운로드 시작: $photoId ===")

                // 네이티브 코드를 통해 실제 파일 데이터 다운로드
                val imageData = nativeDataSource.downloadCameraPhoto(photoId)

                if (imageData != null && imageData.isNotEmpty()) {
                    Log.d("카메라레포지토리", "네이티브 다운로드 성공: ${imageData.size} bytes")

                    // 임시 파일 생성
                    val fileName = photoId.substringAfterLast("/")
                    val tempFile = File(context.cacheDir, "temp_downloads/$fileName")

                    // 디렉토리 생성
                    tempFile.parentFile?.mkdirs()

                    // 데이터를 파일로 저장
                    tempFile.writeBytes(imageData)

                    Log.d("카메라레포지토리", "임시 파일 저장 완료: ${tempFile.absolutePath}")

                    // 후처리 (MediaStore 저장 등)
                    val finalPath = postProcessPhoto(tempFile.absolutePath, fileName)

                    val capturedPhoto = CapturedPhoto(
                        id = UUID.randomUUID().toString(),
                        filePath = finalPath,
                        thumbnailPath = null,
                        captureTime = System.currentTimeMillis(),
                        cameraModel = _cameraCapabilities.value?.model ?: "알 수 없음",
                        settings = _cameraSettings.value,
                        size = imageData.size.toLong(),
                        width = 0,
                        height = 0,
                        isDownloading = false,
                        downloadCompleteTime = System.currentTimeMillis()
                    )

                    Log.d("카메라레포지토리", "✅ 카메라에서 사진 다운로드 완료: $finalPath")
                    Result.success(capturedPhoto)
                } else {
                    Log.e("카메라레포지토리", "네이티브 다운로드 실패: 데이터가 비어있음")
                    Result.failure(Exception("카메라에서 사진 데이터를 가져올 수 없습니다"))
                }
            } catch (e: Exception) {
                Log.e("카메라레포지토리", "카메라에서 사진 다운로드 실패", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun getCameraCapabilities(): Result<CameraCapabilities?> {
        return withContext(Dispatchers.IO) {
            try {
                val capabilities =
                    _cameraCapabilities.value ?: nativeDataSource.getCameraCapabilities()
                Result.success(capabilities)
            } catch (e: Exception) {
                Log.e("카메라레포지토리", "카메라 기능 정보 가져오기 실패", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun getCameraPhotos(): Result<List<CameraPhoto>> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("카메라레포지토리", "카메라 사진 목록 가져오기 시작")

                // 네이티브 데이터소스를 통해 카메라의 사진 목록 가져오기
                // (내부에서 이벤트 리스너를 일시 중지함)
                val nativePhotos = nativeDataSource.getCameraPhotos()

                // 이벤트 리스너가 중지되었을 가능성이 있으므로 안전하게 재시작
                if (_isConnected.value) {
                    Log.d("카메라레포지토리", "사진 목록 가져오기 후 이벤트 리스너 상태 확인 및 재시작")

                    // 충분한 대기 시간 후 재시작 (JNI 스레드 정리 대기)
                    kotlinx.coroutines.delay(500)

                    // 기존 리스너가 완전히 정리되었는지 확인
                    if (!isEventListenerRunning) {
                        try {
                            Log.d("카메라레포지토리", "이벤트 리스너 재시작 시도")
                            startCameraEventListenerInternal()
                        } catch (e: Exception) {
                            Log.w("카메라레포지토리", "이벤트 리스너 재시작 실패, 나중에 다시 시도", e)
                            // 실패해도 사진 목록 반환에는 영향 없음
                        }
                    } else {
                        Log.d("카메라레포지토리", "이벤트 리스너가 이미 실행 중")
                    }
                }

                // 사진 정보를 CameraPhoto 모델로 변환
                val cameraPhotos = nativePhotos.map { nativePhoto ->
                    CameraPhoto(
                        path = nativePhoto.path,
                        name = nativePhoto.name,
                        size = nativePhoto.size,
                        date = nativePhoto.date,
                        width = nativePhoto.width,
                        height = nativePhoto.height,
                        thumbnailPath = nativePhoto.thumbnailPath
                    )
                }

                Log.d("카메라레포지토리", "카메라 사진 목록 가져오기 완료: ${cameraPhotos.size}개")
                Result.success(cameraPhotos)
            } catch (e: Exception) {
                Log.e("카메라레포지토리", "카메라 사진 목록 가져오기 실패", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun getCameraPhotosPaged(
        page: Int,
        pageSize: Int
    ): Result<PaginatedCameraPhotos> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("카메라레포지토리", "페이징 카메라 사진 목록 가져오기 시작 (페이지: $page, 크기: $pageSize)")

                // 네이티브 데이터소스를 통해 페이징된 카메라 사진 목록 가져오기
                val paginatedNativePhotos = nativeDataSource.getCameraPhotosPaged(page, pageSize)

                // 이벤트 리스너 재시작 처리
                if (_isConnected.value) {
                    Log.d("카메라레포지토리", "페이징 사진 목록 가져오기 후 이벤트 리스너 상태 확인")

                    kotlinx.coroutines.delay(500)

                    if (!isEventListenerRunning) {
                        try {
                            Log.d("카메라레포지토리", "이벤트 리스너 재시작 시도")
                            startCameraEventListenerInternal()
                        } catch (e: Exception) {
                            Log.w("카메라레포지토리", "이벤트 리스너 재시작 실패", e)
                        }
                    } else {
                        Log.d("카메라레포지토리", "이벤트 리스너가 이미 실행 중")
                    }
                }

                // 사진 정보를 도메인 모델로 변환
                val cameraPhotos = paginatedNativePhotos.photos.map { nativePhoto ->
                    CameraPhoto(
                        path = nativePhoto.path,
                        name = nativePhoto.name,
                        size = nativePhoto.size,
                        date = nativePhoto.date,
                        width = nativePhoto.width,
                        height = nativePhoto.height,
                        thumbnailPath = nativePhoto.thumbnailPath
                    )
                }

                val domainPaginatedPhotos = PaginatedCameraPhotos(
                    photos = cameraPhotos,
                    currentPage = paginatedNativePhotos.currentPage,
                    pageSize = paginatedNativePhotos.pageSize,
                    totalItems = paginatedNativePhotos.totalItems,
                    totalPages = paginatedNativePhotos.totalPages,
                    hasNext = paginatedNativePhotos.hasNext
                )

                Log.d(
                    "카메라레포지토리",
                    "페이징 카메라 사진 목록 가져오기 완료: ${cameraPhotos.size}개 (페이지 ${paginatedNativePhotos.currentPage}/${paginatedNativePhotos.totalPages})"
                )
                Result.success(domainPaginatedPhotos)

            } catch (e: Exception) {
                Log.e("카메라레포지토리", "페이징 카메라 사진 목록 가져오기 실패", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun getCameraThumbnail(photoPath: String): Result<ByteArray> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("카메라레포지토리", "썸네일 가져오기 시작: $photoPath")

                val thumbnailData = nativeDataSource.getCameraThumbnail(photoPath)

                if (thumbnailData != null) {
                    Log.d("카메라레포지토리", "썸네일 가져오기 성공: ${thumbnailData.size} bytes")
                    Result.success(thumbnailData)
                } else {
                    Log.w("카메라레포지토리", "썸네일 가져오기 실패: $photoPath")
                    Result.failure(Exception("썸네일을 가져올 수 없습니다"))
                }

            } catch (e: Exception) {
                Log.e("카메라레포지토리", "썸네일 가져오기 중 예외", e)
                Result.failure(e)
            }
        }
    }

    private suspend fun updateCameraList() = withContext(Dispatchers.IO) {
        try {
            Log.d("카메라레포지토리", "카메라 목록 업데이트")
            val detected = nativeDataSource.detectCamera()
            if (detected != "No camera detected") {
                val cameras = detected.split("\n")
                    .filter { it.isNotBlank() }
                    .mapIndexed { index, line ->
                        val parts = line.split(" @ ")
                        Camera(
                            id = "camera_$index",
                            name = parts.getOrNull(0) ?: "알 수 없음",
                            isActive = true
                        )
                    }
                withContext(Dispatchers.Main) {
                    _cameraFeed.value = cameras
                }
                Log.d("카메라레포지토리", "카메라 목록 업데이트 완료: ${cameras.size}개")
            } else {
                Log.d("카메라레포지토리", "카메라가 감지되지 않음")
                withContext(Dispatchers.Main) {
                    _cameraFeed.value = emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e("카메라레포지토리", "카메라 목록 업데이트 실패", e)
        }
    }

    private fun observeNativeCameraConnection() {
        CoroutineScope(Dispatchers.IO).launch {
            usbCameraManager.isNativeCameraConnected.collect { isConnected ->
                Log.d("카메라레포지토리", "네이티브 카메라 연결 상태 변경: $isConnected")

                withContext(Dispatchers.Main) {
                    _isConnected.value = isConnected
                }

                if (isConnected) {
                    updateCameraList()
                    // 카메라 기능 정보는 한 번만 업데이트하도록 중복 방지 로직 추가
                    if (_cameraCapabilities.value == null) {
                        updateCameraCapabilities()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _cameraFeed.value = emptyList()
                        // 연결이 끊어지면 capabilities도 초기화
                        _cameraCapabilities.value = null
                    }
                }
            }
        }
    }

    private suspend fun updateCameraCapabilities() = withContext(Dispatchers.IO) {
        try {
            Log.d("카메라레포지토리", "카메라 기능 정보 업데이트")
            val capabilities = nativeDataSource.getCameraCapabilities()
            capabilities?.let {
                withContext(Dispatchers.Main) {
                    _cameraCapabilities.value = it
                }
                Log.d("카메라레포지토리", "카메라 기능 정보 업데이트 완료: ${it.model}")
            }
        } catch (e: Exception) {
            Log.e("카메라레포지토리", "카메라 기능 정보 업데이트 실패", e)
        }
    }

    private fun startCameraEventListenerInternal() {
        if (isEventListenerRunning) {
            Log.d("카메라레포지토리", "카메라 이벤트 리스너가 이미 실행 중입니다")
            return
        }

        // 카메라 연결 상태 확인
        if (!_isConnected.value) {
            Log.e("카메라레포지토리", "카메라가 연결되지 않은 상태에서 이벤트 리스너 시작 불가")
            return
        }

        // 저장 디렉토리 결정
        val saveDir = getSaveDirectory()
        Log.d("카메라레포지토리", "이벤트 리스너 저장 디렉토리: $saveDir")

        Log.d("카메라레포지토리", "=== 카메라 이벤트 리스너 시작 ===")
        isEventListenerRunning = true

        // 이벤트 리스너를 백그라운드 스레드에서 시작
        CoroutineScope(Dispatchers.IO).launch {
            var retryCount = 0
            val maxRetries = 1

            while (retryCount < maxRetries && _isConnected.value) {
                try {
                    Log.d("카메라레포지토리", "CameraNative.listenCameraEvents 호출 시작 (시도 ${retryCount + 1}/$maxRetries)")

                    nativeDataSource.listenCameraEvents(object : CameraCaptureListener {
                        override fun onFlushComplete() {
                            Log.d("카메라레포지토리", "✓ 카메라 이벤트 큐 플러시 완료")
                        }

                        override fun onPhotoCaptured(fullPath: String, fileName: String) {
                            Log.d("카메라레포지토리", "🎉 외부 셔터 사진 촬영 감지: $fileName")
                            Log.d("카메라레포지토리", "외부 촬영 저장됨: $fullPath")

                            // 파일 확장자 확인 - JPEG만 처리
                            val extension = fileName.substringAfterLast(".", "").lowercase()
                            if (extension !in listOf("jpg", "jpeg")) {
                                Log.d("카메라레포지토리", "JPEG가 아닌 파일 무시: $fileName (확장자: $extension)")
                                return
                            }

                            // 임시 사진 정보 생성
                            val tempPhoto = CapturedPhoto(
                                id = UUID.randomUUID().toString(),
                                filePath = fullPath,
                                thumbnailPath = null,
                                captureTime = System.currentTimeMillis(),
                                cameraModel = _cameraCapabilities.value?.model ?: "알 수 없음",
                                settings = _cameraSettings.value,
                                size = 0, // 아직 다운로드 전
                                width = 0,
                                height = 0,
                                isDownloading = true // 다운로드 중 표시
                            )

                            // 백그라운드에서 비동기 다운로드 처리 (UI 추가는 완료 후)
                            CoroutineScope(Dispatchers.IO).launch {
                                handlePhotoDownload(tempPhoto, fullPath, fileName)
                            }
                        }

                        override fun onCaptureFailed(errorCode: Int) {
                            Log.e(
                                "카메라레포지토리",
                                "❌ 외부 셔터 촬영 실패, 오류 코드: $errorCode"
                            )
                        }
                    })

                    Log.d("카메라레포지토리", "✓ 카메라 이벤트 리스너 설정 완료")
                    break // 성공적으로 시작되었으므로 반복 종료

                } catch (e: Exception) {
                    Log.e("카메라레포지토리", "❌ 카메라 이벤트 리스너 시작 실패 (시도 ${retryCount + 1}/$maxRetries)", e)
                    retryCount++

                    if (retryCount < maxRetries) {
                        Log.d("카메라레포지토리", "이벤트 리스너 재시도 대기 중...")
                        kotlinx.coroutines.delay(1000) // 1초 대기 후 재시도
                    } else {
                        Log.e("카메라레포지토리", "❌ 이벤트 리스너 시작 최대 재시도 초과")
                        isEventListenerRunning = false
                    }
                }
            }
        }
        CoroutineScope(Dispatchers.Main).launch {
            _isEventListenerActive.value = true
        }
    }

    /**
     * 카메라 이벤트 리스너 중지 (public)
     */
    override suspend fun stopCameraEventListener(): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                if (!isEventListenerRunning) {
                    return@withContext Result.success(true)
                }

                Log.d("카메라레포지토리", "카메라 이벤트 리스너 중지 (public)")
                nativeDataSource.stopListenCameraEvents()
                isEventListenerRunning = false
                CoroutineScope(Dispatchers.Main).launch {
                    _isEventListenerActive.value = false
                }
                Log.d("카메라레포지토리", "✓ 카메라 이벤트 리스너 중지 완료 (public)")
                Result.success(true)
            } catch (e: Exception) {
                Log.e("카메라레포지토리", "❌ 카메라 이벤트 리스너 중지 실패 (public)", e)
                Result.failure(e)
            }
        }
    }

    /**
     * (내부용) 카메라 이벤트 리스너 중지
     */
    private fun stopCameraEventListenerInternal() {
        if (!isEventListenerRunning) {
            return
        }

        Log.d("카메라레포지토리", "카메라 이벤트 리스너 내부 중지")
        try {
            nativeDataSource.stopListenCameraEvents()
            Log.d("카메라레포지토리", "✓ 카메라 이벤트 리스너 내부 중지 완료")
        } catch (e: Exception) {
            Log.e("카메라레포지토리", "❌ 카메라 이벤트 리스너 내부 중지 실패", e)
        } finally {
            isEventListenerRunning = false
            CoroutineScope(Dispatchers.Main).launch {
                _isEventListenerActive.value = false
            }
        }
    }

    /**
     * JPEG 사진 다운로드를 비동기로 처리
     */
    private suspend fun handlePhotoDownload(
        photo: CapturedPhoto,
        fullPath: String,
        fileName: String
    ) {
        try {
            Log.d("카메라레포지토리", "📥 JPEG 사진 다운로드 시작: $fileName")
            val startTime = System.currentTimeMillis()

            // 파일 확인 - 빠른 체크
            val file = File(fullPath)
            if (!file.exists()) {
                Log.e("카메라레포지토리", "❌ 사진 파일을 찾을 수 없음: $fullPath")
                updatePhotoDownloadFailed(fileName)
                return
            }

            val fileSize = file.length()
            Log.d("카메라레포지토리", "✓ JPEG 파일 확인: $fileName")
            Log.d("카메라레포지토리", "   크기: ${fileSize / 1024}KB")

            // 색감 전송 적용 확인
            val isColorTransferEnabled = appPreferencesDataSource.isColorTransferEnabled.first()
            val referenceImagePath =
                appPreferencesDataSource.colorTransferReferenceImagePath.first()

            var processedPath = fullPath

            if (isColorTransferEnabled && referenceImagePath != null && File(referenceImagePath).exists()) {
                Log.d("카메라레포지토리", "🎨 색감 전송 적용 시작: $fileName")

                try {
                    // 메모리 효율성을 위한 사전 검사
                    val runtime = Runtime.getRuntime()
                    val freeMemory = runtime.freeMemory()
                    val totalMemory = runtime.totalMemory()
                    val maxMemory = runtime.maxMemory()
                    val usedMemory = totalMemory - freeMemory
                    val availableMemory = maxMemory - usedMemory

                    Log.d("카메라레포지토리", "메모리 상태 - 사용중: ${usedMemory / 1024 / 1024}MB, 사용가능: ${availableMemory / 1024 / 1024}MB")

                    // 색감 전송 적용 (원본 해상도로 처리)
                    val colorTransferredFile = File(
                        file.parent,
                        "${file.nameWithoutExtension}_color_transferred.jpg"
                    )

                    val transferredBitmap = colorTransferUseCase.applyColorTransferWithGPUAndSave(
                        file.absolutePath, // 입력 파일 경로
                        referenceImagePath, // 참조 이미지 경로
                        colorTransferredFile.absolutePath // 출력 파일 경로
                    )

                    if (transferredBitmap != null) {
                        processedPath = colorTransferredFile.absolutePath
                        Log.d(
                            "카메라레포지토리",
                            "✅ 색감 전송 적용 완료 (원본 해상도): ${colorTransferredFile.name}"
                        )

                        // 메모리 정리 - 즉시 해제
                        transferredBitmap.recycle()
                    } else {
                        Log.w("카메라레포지토리", "⚠️ 색감 전송 실패, 원본 이미지 사용")
                    }
                } catch (e: OutOfMemoryError) {
                    Log.e("카메라레포지토리", "❌ 메모리 부족으로 색감 전송 실패", e)
                    // 메모리 부족 시 강제 GC 실행 및 메모리 정리
                    System.gc()
                    Thread.sleep(100) // GC 완료 대기
                    Log.d("카메라레포지토리", "메모리 정리 완료")
                } catch (e: Exception) {
                    Log.e("카메라레포지토리", "❌ 색감 전송 처리 중 오류", e)
                    // 오류 발생 시 원본 이미지 사용
                }

            } else {
                if (isColorTransferEnabled) {
                    Log.d("카메라레포지토리", "⚠️ 색감 전송 활성화되어 있지만 참조 이미지가 없음")
                }
            }

            // SAF를 사용한 후처리 (Android 10+에서 MediaStore로 이동)
            val finalPath = postProcessPhoto(processedPath, fileName)
            Log.d("카메라레포지토리", "✅ 사진 후처리 완료: $finalPath")

            // 즉시 UI에 임시 사진 정보 추가 (썸네일 없이)
            val tempPhoto = photo.copy(
                filePath = finalPath,
                isDownloading = false
            )

            // UI 업데이트
            CoroutineScope(Dispatchers.Main).launch {
                updateDownloadedPhoto(tempPhoto)
            }

            val downloadTime = System.currentTimeMillis() - startTime
            Log.d("카메라레포지토리", "✅ JPEG 사진 다운로드 완료: $fileName (${downloadTime}ms)")

            // 사진 촬영 이벤트 발생
            photoCaptureEventManager.emitPhotoCaptured()

            // 메모리 정리 - 마지막에 한 번 더 실행
            if (isColorTransferEnabled) {
                System.gc()
            }
        } catch (e: Exception) {
            Log.e("카메라레포지토리", "❌ JPEG 사진 다운로드 실패: $fileName", e)
            updatePhotoDownloadFailed(fileName)
        }
    }

    /**
     * 이미지 크기에 따른 샘플링 비율 계산
     */
    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var inSampleSize = 1

        // 더 큰 임계값 사용 (4K 이상에서만 다운샘플링)
        val maxWidth = maxOf(reqWidth, 3840) // 4K 너비
        val maxHeight = maxOf(reqHeight, 2160) // 4K 높이

        if (height > maxHeight || width > maxWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            // 요구되는 크기보다 작아질 때까지 샘플링 비율을 2배씩 증가
            while ((halfHeight / inSampleSize) >= maxHeight && (halfWidth / inSampleSize) >= maxWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    /**
     * 다운로드 완료된 사진 정보 업데이트
     */
    private fun updateDownloadedPhoto(downloadedPhoto: CapturedPhoto) {
        _capturedPhotos.value = _capturedPhotos.value + downloadedPhoto
        Log.d(
            "카메라레포지토리",
            "✓ 사진 다운로드 완료 업데이트. 총 ${_capturedPhotos.value.size}개"
        )
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
     * 저장소 권한 확인
     */
    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: 세분화된 미디어 권한
            context.checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6-12: 기존 저장소 권한
            context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            // Android 5 이하: 권한 확인 불필요
            true
        }
    }

    /**
     * SAF를 고려한 저장 디렉토리 결정
     * Android 10+ 에서는 SAF 사용, 그 이전에는 직접 파일 시스템 접근
     */
    private fun getSaveDirectory(): String {
        return try {
            // 권한 확인
            if (!hasStoragePermission()) {
                Log.w("카메라레포지토리", "저장소 권한 없음, 내부 저장소 사용")
                return File(context.cacheDir, "temp_photos").apply { mkdirs() }.absolutePath
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ : SAF 사용하므로 임시 디렉토리 반환
                val tempDir = File(context.cacheDir, "temp_photos")
                if (!tempDir.exists()) {
                    tempDir.mkdirs()
                }
                Log.d("카메라레포지토리", "✅ SAF 사용 - 임시 디렉토리: ${tempDir.absolutePath}")
                tempDir.absolutePath
            } else {
                // Android 9 이하: 직접 외부 저장소 접근 가능
                val externalStorageState = Environment.getExternalStorageState()
                if (externalStorageState == Environment.MEDIA_MOUNTED) {
                    val dcimDir = File(Environment.getExternalStorageDirectory(), "DCIM/CamCon")
                    if (!dcimDir.exists()) {
                        dcimDir.mkdirs()
                    }
                    Log.d("카메라레포지토리", "✅ 직접 외부 저장소 사용: ${dcimDir.absolutePath}")
                    dcimDir.absolutePath
                } else {
                    // 외부 저장소를 사용할 수 없으면 내부 저장소
                    val internalDir = File(context.filesDir, "photos")
                    if (!internalDir.exists()) {
                        internalDir.mkdirs()
                    }
                    Log.w("카메라레포지토리", "⚠️ 내부 저장소 사용: ${internalDir.absolutePath}")
                    internalDir.absolutePath
                }
            }
        } catch (e: Exception) {
            Log.e("카메라레포지토리", "저장 디렉토리 결정 실패, 기본값 사용", e)
            context.filesDir.absolutePath
        }
    }

    /**
     * 사진 후처리 - SAF를 사용하여 최종 저장소에 저장
     */
    private suspend fun postProcessPhoto(tempFilePath: String, fileName: String): String {
        return withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10+: MediaStore API 사용
                    saveToMediaStore(tempFilePath, fileName)
                } else {
                    // Android 9 이하: 이미 올바른 위치에 저장되어 있음
                    tempFilePath
                }
            } catch (e: Exception) {
                Log.e("카메라레포지토리", "사진 후처리 실패", e)
                tempFilePath // 실패 시 원본 경로 반환
            }
        }
    }

    /**
     * MediaStore를 사용하여 사진을 외부 저장소에 저장
     */
    private fun saveToMediaStore(tempFilePath: String, fileName: String): String {
        return try {
            val tempFile = File(tempFilePath)
            if (!tempFile.exists()) {
                Log.e("카메라레포지토리", "임시 파일이 존재하지 않음: $tempFilePath")
                return tempFilePath
            }

            // MediaStore를 사용하여 DCIM 폴더에 저장
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/CamCon")
            }

            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    FileInputStream(tempFile).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                // 임시 파일 삭제
                tempFile.delete()

                // MediaStore URI를 파일 경로로 변환
                val savedPath = getPathFromUri(uri) ?: uri.toString()
                Log.d("카메라레포지토리", "✅ MediaStore 저장 성공: $savedPath")
                savedPath
            } else {
                Log.e("카메라레포지토리", "MediaStore URI 생성 실패")
                tempFilePath
            }
        } catch (e: Exception) {
            Log.e("카메라레포지토리", "MediaStore 저장 실패", e)
            tempFilePath
        }
    }

    /**
     * URI를 실제 파일 경로로 변환
     */
    private fun getPathFromUri(uri: Uri): String? {
        return try {
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(MediaStore.Images.Media.DATA),
                null,
                null,
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val columnIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                    it.getString(columnIndex)
                } else null
            }
        } catch (e: Exception) {
            Log.e("카메라레포지토리", "URI 경로 변환 실패", e)
            null
        }
    }
}
