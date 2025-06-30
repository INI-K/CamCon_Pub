package com.inik.camcon.data.repository

import android.util.Log
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
import com.inik.camcon.domain.model.ShootingMode
import com.inik.camcon.domain.model.TimelapseSettings
import com.inik.camcon.domain.repository.CameraRepository
import com.inik.camcon.domain.usecase.camera.PhotoCaptureEventManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class CameraRepositoryImpl @Inject constructor(
    private val nativeDataSource: NativeCameraDataSource,
    private val usbCameraManager: UsbCameraManager,
    private val photoCaptureEventManager: PhotoCaptureEventManager
) : CameraRepository {

    private val _cameraFeed = MutableStateFlow<List<Camera>>(emptyList())
    private val _isConnected = MutableStateFlow(false)
    private val _capturedPhotos = MutableStateFlow<List<CapturedPhoto>>(emptyList())
    private val _cameraCapabilities = MutableStateFlow<CameraCapabilities?>(null)
    private val _cameraSettings = MutableStateFlow<CameraSettings?>(null)

    // 다운로드 큐 관리
    private val downloadQueue = mutableListOf<Pair<CapturedPhoto, String>>()
    private var isProcessingQueue = false

    // 카메라 이벤트 리스너 상태 추적
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
                                updateCameraCapabilities()
                                Log.d("카메라레포지토리", "이벤트 리스너 시작 시도")
                                startCameraEventListener()
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
                        updateCameraCapabilities()
                        Log.d("카메라레포지토리", "이벤트 리스너 시작 시도")
                        startCameraEventListener()
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
                stopCameraEventListener()

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
            val saveDir = "/data/data/com.inik.camcon/files"
            Log.d("카메라레포지토리", "=== 사진 촬영 시작 ===")
            Log.d("카메라레포지토리", "촬영 모드: $mode")
            Log.d("카메라레포지토리", "저장 디렉토리: $saveDir")
            Log.d("카메라레포지토리", "카메라 연결 상태: ${_isConnected.value}")

            // 연결 상태 확인
            if (!_isConnected.value) {
                Log.e("카메라레포지토리", "카메라가 연결되지 않음")
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

                        // 파일 확장자 확인 로그 추가
                        val extension = fileName.substringAfterLast(".", "").lowercase()
                        Log.d("카메라레포지토리", "촬영된 파일: $fileName (확장자: $extension)")

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

                        // UI에 즉시 반영
                        CoroutineScope(Dispatchers.Main).launch {
                            _capturedPhotos.value = _capturedPhotos.value + photo
                            Log.d("카메라레포지토리", "⚡ 사진 즉시 목록 추가: $fileName (다운로드 시작)")
                        }

                        // 백그라운드에서 비동기 다운로드 처리
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
        // TODO: 카메라에서 사진 다운로드 기능 구현
        return Result.failure(Exception("아직 구현되지 않음"))
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
                val nativePhotos = nativeDataSource.getCameraPhotos()

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
                    updateCameraCapabilities()
                } else {
                    withContext(Dispatchers.Main) {
                        _cameraFeed.value = emptyList()
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

    private fun startCameraEventListener() {
        if (isEventListenerRunning) {
            Log.d("카메라레포지토리", "카메라 이벤트 리스너가 이미 실행 중입니다")
            return
        }

        // 카메라 연결 상태 확인
        if (!_isConnected.value) {
            Log.e("카메라레포지토리", "카메라가 연결되지 않은 상태에서 이벤트 리스너 시작 불가")
            return
        }

        Log.d("카메라레포지토리", "=== 카메라 이벤트 리스너 시작 ===")
        isEventListenerRunning = true

        // 이벤트 리스너를 백그라운드 스레드에서 시작
        CoroutineScope(Dispatchers.IO).launch {
            var retryCount = 0
            val maxRetries = 3
            
            while (retryCount < maxRetries && _isConnected.value) {
                try {
                    Log.d("카메라레포지토리", "CameraNative.listenCameraEvents 호출 시작 (시도 ${retryCount + 1}/$maxRetries)")
                    
                    nativeDataSource.listenCameraEvents(object : CameraCaptureListener {
                        override fun onFlushComplete() {
                            Log.d("카메라레포지토리", "✓ 카메라 이벤트 큐 플러시 완료")
                        }

                        override fun onPhotoCaptured(fullPath: String, fileName: String) {
                            Log.d("카메라레포지토리", "🎉 외부 셔터 사진 촬영 감지: $fileName")
                            
                            // 즉시 UI에 임시 사진 정보 추가 (썸네일 없이)
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

                            // UI에 즉시 반영
                            CoroutineScope(Dispatchers.Main).launch {
                                _capturedPhotos.value = _capturedPhotos.value + tempPhoto
                                Log.d("카메라레포지토리", "⚡ 사진 즉시 목록 추가: $fileName (다운로드 시작)")
                            }

                            // 백그라운드에서 비동기 다운로드 처리
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
    }

    private fun stopCameraEventListener() {
        if (!isEventListenerRunning) {
            return
        }

        Log.d("카메라레포지토리", "카메라 이벤트 리스너 중지")
        try {
            nativeDataSource.stopListenCameraEvents()
            Log.d("카메라레포지토리", "✓ 카메라 이벤트 리스너 중지 완료")
        } catch (e: Exception) {
            Log.e("카메라레포지토리", "❌ 카메라 이벤트 리스너 중지 실패", e)
        } finally {
            isEventListenerRunning = false
        }
    }

    /**
     * 사진 다운로드를 비동기로 처리
     */
    private suspend fun handlePhotoDownload(
        tempPhoto: CapturedPhoto,
        remotePath: String,
        fileName: String
    ) {
        try {
            Log.d("카메라레포지토리", "📥 사진 다운로드 시작: $fileName")
            val startTime = System.currentTimeMillis()

            // 파일 확인 - 빠른 체크
            val file = File(remotePath)
            if (!file.exists()) {
                Log.e("카메라레포지토리", "❌ 사진 파일을 찾을 수 없음: $remotePath")
                updatePhotoDownloadFailed(tempPhoto.id)
                return
            }

            val fileSize = file.length()
            val extension = fileName.substringAfterLast(".", "").lowercase()
            val isRawFile = extension in listOf("arw", "cr2", "nef", "dng", "raf", "orf")

            Log.d("카메라레포지토리", "✓ 사진 파일 확인: $fileName")
            Log.d("카메라레포지토리", "   크기: ${fileSize / 1024}KB, RAW: $isRawFile")

            // 작은 파일(JPG)은 즉시 처리, 큰 파일(RAW)은 큐에 추가
            if (isRawFile && fileSize > 10 * 1024 * 1024) { // 10MB 이상
                synchronized(downloadQueue) {
                    downloadQueue.add(tempPhoto to remotePath)
                }
                processDownloadQueue()
                return
            }

            // 작은 파일은 즉시 처리
            completePhotoDownload(tempPhoto, fileSize, fileName, startTime)

        } catch (e: Exception) {
            Log.e("카메라레포지토리", "❌ 사진 다운로드 실패: $fileName", e)
            updatePhotoDownloadFailed(tempPhoto.id)
        }
    }

    /**
     * 다운로드 큐 처리 (RAW 파일 등 큰 파일들)
     */
    private fun processDownloadQueue() {
        if (isProcessingQueue) return
        isProcessingQueue = true

        CoroutineScope(Dispatchers.IO).launch {
            try {
                while (downloadQueue.isNotEmpty()) {
                    val (photo, path) = synchronized(downloadQueue) {
                        downloadQueue.removeFirstOrNull() ?: return@launch
                    }

                    val file = File(path)
                    if (file.exists()) {
                        val fileName = file.name
                        val startTime = System.currentTimeMillis()

                        Log.d("카메라레포지토리", "🔄 큐에서 처리 중: $fileName")
                        completePhotoDownload(photo, file.length(), fileName, startTime)

                        // 큰 파일 처리 후 잠시 대기 (시스템 부하 방지)
                        kotlinx.coroutines.delay(100)
                    } else {
                        updatePhotoDownloadFailed(photo.id)
                    }
                }
            } finally {
                isProcessingQueue = false
            }
        }
    }

    /**
     * 사진 다운로드 완료 처리
     */
    private suspend fun completePhotoDownload(
        photo: CapturedPhoto,
        fileSize: Long,
        fileName: String,
        startTime: Long
    ) {
        val downloadedPhoto = photo.copy(
            size = fileSize,
            isDownloading = false,
            downloadCompleteTime = System.currentTimeMillis()
        )

        // UI 업데이트
        withContext(Dispatchers.Main) {
            updateDownloadedPhoto(downloadedPhoto)
        }

        val downloadTime = System.currentTimeMillis() - startTime
        Log.d("카메라레포지토리", "✅ 사진 다운로드 완료: $fileName (${downloadTime}ms)")

        // 사진 촬영 이벤트 발생
        photoCaptureEventManager.emitPhotoCaptured()
    }

    /**
     * 다운로드 완료된 사진 정보 업데이트
     */
    private fun updateDownloadedPhoto(downloadedPhoto: CapturedPhoto) {
        _capturedPhotos.value = _capturedPhotos.value.map { photo ->
            if (photo.id == downloadedPhoto.id) {
                downloadedPhoto
            } else {
                photo
            }
        }
        Log.d(
            "카메라레포지토리",
            "✓ 사진 다운로드 완료 업데이트. 총 ${_capturedPhotos.value.size}개"
        )
    }

    /**
     * 다운로드 실패한 사진 제거
     */
    private fun updatePhotoDownloadFailed(photoId: String) {
        CoroutineScope(Dispatchers.Main).launch {
            _capturedPhotos.value = _capturedPhotos.value.filter { it.id != photoId }
            Log.d("카메라레포지토리", "❌ 다운로드 실패한 사진 제거: $photoId")
        }
    }
}
