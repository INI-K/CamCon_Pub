package com.inik.camcon.data.repository.managers

import android.util.Log
import com.inik.camcon.data.datasource.nativesource.CameraCaptureListener
import com.inik.camcon.data.datasource.nativesource.NativeCameraDataSource
import com.inik.camcon.data.datasource.usb.UsbCameraManager
import com.inik.camcon.domain.usecase.ValidateImageFormatUseCase
import com.inik.camcon.utils.Constants
import com.inik.camcon.utils.SubscriptionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraEventManager @Inject constructor(
    private val nativeDataSource: NativeCameraDataSource,
    private val usbCameraManager: UsbCameraManager,
    private val validateImageFormatUseCase: ValidateImageFormatUseCase
) {
    // 카메라 이벤트 리스너 상태 추적
    private val _isEventListenerActive = MutableStateFlow(false)
    val isEventListenerActive = _isEventListenerActive.asStateFlow()

    // 스레드 안전한 실행 상태 관리
    private val isEventListenerRunning = AtomicBoolean(false)
    private val isEventListenerStarting = AtomicBoolean(false)

    // 사진 미리보기 모드 상태 추가 (이벤트 리스너 자동 시작 방지용)
    private val _isPhotoPreviewMode = MutableStateFlow(false)

    // USB 분리 처리 상태 추가 (무한 루프 방지)
    private val isHandlingUsbDisconnection = AtomicBoolean(false)

    // USB 분리 콜백
    var onUsbDisconnectedCallback: (() -> Unit)? = null

    // PTPIP 분리 콜백
    var onPtpipDisconnectedCallback: (() -> Unit)? = null

    // RAW 파일 제한 콜백 추가
    var onRawFileRestricted: ((fileName: String, restrictionMessage: String) -> Unit)? = null

    fun setPhotoPreviewMode(enabled: Boolean) {
        _isPhotoPreviewMode.value = enabled
        Log.d("카메라이벤트매니저", "사진 미리보기 모드 설정: $enabled")
    }

    /**
     * 카메라 이벤트 리스너 시작 (public)
     */
    suspend fun startCameraEventListener(
        isConnected: Boolean,
        isInitializing: Boolean,
        saveDirectory: String,
        onPhotoCaptured: (String, String) -> Unit,
        onFlushComplete: () -> Unit,
        onCaptureFailed: (Int) -> Unit,
        connectionType: ConnectionType = ConnectionType.USB
    ): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                // 중복 시작 방지 - 원자적 연산으로 체크
                if (!isEventListenerStarting.compareAndSet(false, true)) {
                    Log.d("카메라이벤트매니저", "카메라 이벤트 리스너 시작이 이미 진행 중입니다")
                    return@withContext Result.success(true)
                }

                try {
                    if (isEventListenerRunning.get()) {
                        Log.d("카메라이벤트매니저", "카메라 이벤트 리스너가 이미 실행 중입니다 (public)")
                        return@withContext Result.success(true)
                    }

                    // 카메라 연결 상태 확인
                    if (!isConnected) {
                        Log.e("카메라이벤트매니저", "카메라가 연결되지 않은 상태에서 이벤트 리스너 시작 불가 (public)")
                        return@withContext Result.failure(Exception("카메라가 연결되지 않음"))
                    }

                    // 연결 타입에 따른 초기화 상태 확인
                    when (connectionType) {
                        ConnectionType.USB -> {
                            // USB 전용 체크
                            if (!usbCameraManager.isNativeCameraConnected.value) {
                                Log.e("카메라이벤트매니저", "USB 네이티브 카메라가 연결되지 않은 상태에서 이벤트 리스너 시작 불가")
                                return@withContext Result.failure(Exception("USB 네이티브 카메라가 연결되지 않음"))
                            }
                            // USB용 네이티브 카메라 초기화 검증
                            if (!validateUsbCameraInitialization()) {
                                return@withContext Result.failure(Exception("USB 네이티브 카메라 초기화 실패"))
                            }
                        }
                        ConnectionType.PTPIP -> {
                            // PTPIP용 네이티브 카메라 초기화 검증
                            if (!validatePtpipCameraInitialization()) {
                                return@withContext Result.failure(Exception("PTPIP 네이티브 카메라 초기화 실패"))
                            }
                        }
                    }

                    Log.d("카메라이벤트매니저", "=== 카메라 이벤트 리스너 시작 (${connectionType.name} 모드) ===")

                    // 내부 함수 호출
                    startCameraEventListenerInternal(
                        isConnected,
                        false,
                        saveDirectory,
                        onPhotoCaptured,
                        onFlushComplete,
                        onCaptureFailed,
                        connectionType
                    )

                    Result.success(true)
                } finally {
                    isEventListenerStarting.set(false)
                }
            } catch (e: Exception) {
                Log.e("카메라이벤트매니저", "❌ 카메라 이벤트 리스너 시작 실패 (public)", e)
                isEventListenerStarting.set(false)
                Result.failure(e)
            }
        }
    }

    /**
     * 카메라 이벤트 리스너 중지 (public)
     */
    suspend fun stopCameraEventListener(): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                if (!isEventListenerRunning.get()) {
                    return@withContext Result.success(true)
                }

                Log.d("카메라이벤트매니저", "카메라 이벤트 리스너 중지 (public)")

                // 안전한 중지를 위해 네이티브 중지 호출을 try-catch로 보호
                try {
                    nativeDataSource.stopListenCameraEvents()
                } catch (e: Exception) {
                    Log.w("카메라이벤트매니저", "네이티브 이벤트 리스너 중지 중 예외", e)
                }

                isEventListenerRunning.set(false)
                CoroutineScope(Dispatchers.Main).launch {
                    _isEventListenerActive.value = false
                }
                Log.d("카메라이벤트매니저", "✓ 카메라 이벤트 리스너 중지 완료 (public)")
                Result.success(true)
            } catch (e: Exception) {
                Log.e("카메라이벤트매니저", "❌ 카메라 이벤트 리스너 중지 실패 (public)", e)
                Result.failure(e)
            }
        }
    }

    private fun startCameraEventListenerInternal(
        isConnected: Boolean,
        isInitializing: Boolean,
        saveDirectory: String,
        onPhotoCaptured: (String, String) -> Unit,
        onFlushComplete: () -> Unit,
        onCaptureFailed: (Int) -> Unit,
        connectionType: ConnectionType = ConnectionType.USB
    ) {
        // 원자적 연산으로 중복 실행 방지
        if (!isEventListenerRunning.compareAndSet(false, true)) {
            Log.d("카메라이벤트매니저", "카메라 이벤트 리스너가 이미 실행 중입니다")
            return
        }

        try {
            // 연결 타입별 상태 확인
            val connectionValid = when (connectionType) {
                ConnectionType.USB -> isConnected && usbCameraManager.isNativeCameraConnected.value
                ConnectionType.PTPIP -> isConnected // PTPIP는 네트워크 연결이므로 기본 연결 상태만 체크
            }

            if (!connectionValid) {
                Log.e("카메라이벤트매니저", "${connectionType.name} 카메라 연결 상태 재확인 실패")
                isEventListenerRunning.set(false)
                return
            }

            Log.d("카메라이벤트매니저", "이벤트 리스너 저장 디렉토리: $saveDirectory")
            Log.d("카메라이벤트매니저", "=== ${connectionType.name} 카메라 이벤트 리스너 시작 ===")

            // 이벤트 리스너를 백그라운드 스레드에서 시작
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // 안정화를 위한 추가 대기 시간
                    kotlinx.coroutines.delay(500)

                    // 상태 재확인 (비동기 지연 후)
                    val connectionStillValid = when (connectionType) {
                        ConnectionType.USB -> isConnected && usbCameraManager.isNativeCameraConnected.value
                        ConnectionType.PTPIP -> isConnected
                    }

                    if (!connectionStillValid) {
                        Log.e("카메라이벤트매니저", "지연 후 ${connectionType.name} 카메라 연결 상태 재확인 실패")
                        isEventListenerRunning.set(false)
                        return@launch
                    }

                    var retryCount = 0
                    val maxRetries = 1

                    while (retryCount < maxRetries && isConnected) {
                        try {
                            Log.d(
                                "카메라이벤트매니저",
                                "${connectionType.name} CameraNative.listenCameraEvents 호출 시작 (시도 ${retryCount + 1}/$maxRetries)"
                            )

                            // 네이티브 이벤트 리스너 시작 (USB/PTPIP 공통)
                            nativeDataSource.listenCameraEvents(
                                createCameraCaptureListener(
                                    connectionType,
                                    onPhotoCaptured,
                                    onFlushComplete,
                                    onCaptureFailed
                                )
                            )

                            Log.d("카메라이벤트매니저", "✓ ${connectionType.name} 카메라 이벤트 리스너 설정 완료")
                            break // 성공적으로 시작되었으므로 반복 종료

                        } catch (e: Exception) {
                            Log.e(
                                "카메라이벤트매니저",
                                "❌ ${connectionType.name} 카메라 이벤트 리스너 시작 실패 (시도 ${retryCount + 1}/$maxRetries)",
                                e
                            )
                            retryCount++

                            if (retryCount < maxRetries) {
                                Log.d("카메라이벤트매니저", "이벤트 리스너 재시도 대기 중...")
                                kotlinx.coroutines.delay(1000) // 1초 대기 후 재시도
                            } else {
                                Log.e("카메라이벤트매니저", "❌ ${connectionType.name} 이벤트 리스너 시작 최대 재시도 초과")
                                isEventListenerRunning.set(false)
                                CoroutineScope(Dispatchers.Main).launch {
                                    _isEventListenerActive.value = false
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("카메라이벤트매니저", "❌ ${connectionType.name} 이벤트 리스너 스레드 실행 중 예외", e)
                    isEventListenerRunning.set(false)
                    CoroutineScope(Dispatchers.Main).launch {
                        _isEventListenerActive.value = false
                    }
                }
            }

            CoroutineScope(Dispatchers.Main).launch {
                _isEventListenerActive.value = true
            }

        } catch (e: Exception) {
            Log.e("카메라이벤트매니저", "❌ ${connectionType.name} 이벤트 리스너 내부 시작 실패", e)
            isEventListenerRunning.set(false)
        }
    }

    /**
     * (내부용) 카메라 이벤트 리스너 중지
     */
    fun stopCameraEventListenerInternal() {
        if (!isEventListenerRunning.get()) {
            return
        }

        Log.d("카메라이벤트매니저", "카메라 이벤트 리스너 내부 중지")
        try {
            nativeDataSource.stopListenCameraEvents()
            Log.d("카메라이벤트매니저", "✓ 카메라 이벤트 리스너 내부 중지 완료")
        } catch (e: Exception) {
            Log.e("카메라이벤트매니저", "❌ 카메라 이벤트 리스너 내부 중지 실패", e)
        } finally {
            isEventListenerRunning.set(false)
            CoroutineScope(Dispatchers.Main).launch {
                _isEventListenerActive.value = false
            }
        }
    }

    fun isRunning(): Boolean = isEventListenerRunning.get()

    fun isPhotoPreviewMode(): Boolean = _isPhotoPreviewMode.value

    /**
     * USB 카메라 초기화 검증
     */
    private suspend fun validateUsbCameraInitialization(): Boolean {
        Log.d("카메라이벤트매니저", "USB 네이티브 카메라 초기화 상태 검증 시작...")

        var isCameraInitialized = false
        var waitTime = 0
        val maxWaitTime = 15000 // 최대 15초 대기

        while (!isCameraInitialized && waitTime < maxWaitTime) {
            try {
                // USB 카메라 초기화 상태 체크
                isCameraInitialized = nativeDataSource.isCameraInitialized()

                if (isCameraInitialized) {
                    Log.d("카메라이벤트매니저", "USB 네이티브 카메라 초기화 완료 확인됨")

                    // 추가 검증: 카메라 요약 정보도 가져올 수 있는지 확인
                    try {
                        val summary = nativeDataSource.getCameraSummary()
                        if (summary.name.isNotEmpty() && !summary.name.contains(
                                "error",
                                ignoreCase = true
                            )
                        ) {
                            Log.d("카메라이벤트매니저", "USB 카메라 요약 정보 확인 완료: ${summary.name}")
                            break
                        } else {
                            Log.w("카메라이벤트매니저", "USB 카메라 요약 정보에 오류 포함: ${summary.name}")
                            isCameraInitialized = false
                        }
                    } catch (e: Exception) {
                        Log.w("카메라이벤트매니저", "USB 카메라 요약 정보 확인 실패: ${e.message}")
                        isCameraInitialized = false
                    }
                }

                if (!isCameraInitialized) {
                    Log.d("카메라이벤트매니저", "USB 네이티브 카메라 초기화 대기 중... (${waitTime}ms/${maxWaitTime}ms)")
                    kotlinx.coroutines.delay(500)
                    waitTime += 500

                    // USB 연결 상태 재확인
                    if (!usbCameraManager.isNativeCameraConnected.value) {
                        Log.e("카메라이벤트매니저", "대기 중 USB 네이티브 카메라 연결이 끊어짐")
                        return false
                    }
                }
            } catch (e: Exception) {
                Log.w("카메라이벤트매니저", "USB 네이티브 카메라 초기화 상태 확인 중 예외: ${e.message}")
                kotlinx.coroutines.delay(500)
                waitTime += 500
            }
        }

        if (!isCameraInitialized) {
            Log.e("카메라이벤트매니저", "USB 네이티브 카메라 초기화 타임아웃 또는 실패 (${maxWaitTime}ms)")
            return false
        }

        Log.d("카메라이벤트매니저", "USB 네이티브 카메라 초기화 완료")
        return true
    }

    /**
     * PTPIP 카메라 초기화 검증
     */
    private suspend fun validatePtpipCameraInitialization(): Boolean {
        Log.d("카메라이벤트매니저", "PTPIP 네이티브 카메라 초기화 상태 검증 시작...")

        var isCameraInitialized = false
        var waitTime = 0
        val maxWaitTime = 10000 // PTPIP는 더 빠르게 초기화되므로 10초 대기

        while (!isCameraInitialized && waitTime < maxWaitTime) {
            try {
                // PTPIP 카메라 초기화 상태 체크
                isCameraInitialized = nativeDataSource.isCameraInitialized()

                if (isCameraInitialized) {
                    Log.d("카메라이벤트매니저", "PTPIP 네이티브 카메라 초기화 완료 확인됨")

                    // PTPIP용 추가 검증
                    try {
                        val summary = nativeDataSource.getCameraSummary()
                        if (summary.name.isNotEmpty() && !summary.name.contains(
                                "error",
                                ignoreCase = true
                            )
                        ) {
                            Log.d("카메라이벤트매니저", "PTPIP 카메라 요약 정보 확인 완료: ${summary.name}")
                            break
                        } else {
                            Log.w("카메라이벤트매니저", "PTPIP 카메라 요약 정보에 오류 포함: ${summary.name}")
                            isCameraInitialized = false
                        }
                    } catch (e: Exception) {
                        Log.w("카메라이벤트매니저", "PTPIP 카메라 요약 정보 확인 실패: ${e.message}")
                        isCameraInitialized = false
                    }
                }

                if (!isCameraInitialized) {
                    Log.d(
                        "카메라이벤트매니저",
                        "PTPIP 네이티브 카메라 초기화 대기 중... (${waitTime}ms/${maxWaitTime}ms)"
                    )
                    kotlinx.coroutines.delay(500)
                    waitTime += 500
                }
            } catch (e: Exception) {
                Log.w("카메라이벤트매니저", "PTPIP 네이티브 카메라 초기화 상태 확인 중 예외: ${e.message}")
                kotlinx.coroutines.delay(500)
                waitTime += 500
            }
        }

        if (!isCameraInitialized) {
            Log.e("카메라이벤트매니저", "PTPIP 네이티브 카메라 초기화 타임아웃 또는 실패 (${maxWaitTime}ms)")
            return false
        }

        Log.d("카메라이벤트매니저", "PTPIP 네이티브 카메라 초기화 완료")
        return true
    }

    /**
     * 연결 타입별 CameraCaptureListener 생성
     */
    private fun createCameraCaptureListener(
        connectionType: ConnectionType,
        onPhotoCaptured: (String, String) -> Unit,
        onFlushComplete: () -> Unit,
        onCaptureFailed: (Int) -> Unit
    ): CameraCaptureListener {
        return object : CameraCaptureListener {
            override fun onFlushComplete() {
                Log.d("카메라이벤트매니저", "✓ ${connectionType.name} 카메라 이벤트 큐 플러시 완료")
                try {
                    onFlushComplete()
                } catch (e: Exception) {
                    Log.w("카메라이벤트매니저", "플러시 콜백 호출 중 예외", e)
                }
            }

            override fun onPhotoCaptured(filePath: String, fileName: String) {
                Log.d("카메라이벤트매니저", "🎉 ${connectionType.name} 외부 셔터 사진 촬영 감지: $fileName")
                Log.d("카메라이벤트매니저", "${connectionType.name} 외부 촬영 저장됨: $filePath")

                try {
                    // 파일 확장자 확인 
                    val extension = fileName.substringAfterLast(".", "").lowercase()

                    if (extension !in Constants.ImageProcessing.SUPPORTED_IMAGE_EXTENSIONS) {
                        Log.d(
                            "카메라이벤트매니저",
                            "지원하지 않는 파일 무시: $fileName (확장자: $extension)"
                        )
                        return
                    }

                    // RAW 파일 검증 추가
                    if (SubscriptionUtils.isRawFile(fileName)) {
                        Log.d("카메라이벤트매니저", "🔍 ${connectionType.name} RAW 파일 촬영 감지: $fileName")

                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val validationResult =
                                    validateImageFormatUseCase.validateRawFileAccess(fileName)

                                if (!validationResult.isSupported) {
                                    Log.w(
                                        "카메라이벤트매니저",
                                        "🚫 ${connectionType.name} RAW 파일 접근 제한: $fileName"
                                    )

                                    // 메인 스레드에서 다이얼로그 표시
                                    try {
                                        CoroutineScope(Dispatchers.Main).launch {
                                            try {
                                                onRawFileRestricted?.invoke(
                                                    fileName,
                                                    validationResult.restrictionMessage
                                                        ?: "RAW 파일전송은 지금 준비중입니다."
                                                )
                                            } catch (e: Exception) {
                                                Log.w("카메라이벤트매니저", "RAW 파일 제한 콜백 호출 중 예외", e)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.w("카메라이벤트매니저", "RAW 파일 제한 스레드 시작 중 예외", e)
                                    }

                                    Log.d(
                                        "카메라이벤트매니저",
                                        "📵 ${connectionType.name} RAW 파일 수신 중단: $fileName"
                                    )
                                    return@launch
                                } else {
                                    Log.d(
                                        "카메라이벤트매니저",
                                        "✅ ${connectionType.name} RAW 파일 접근 허용: $fileName"
                                    )

                                    // 안전한 콜백 호출
                                    try {
                                        onPhotoCaptured(filePath, fileName)
                                    } catch (e: Exception) {
                                        Log.e("카메라이벤트매니저", "RAW 파일 처리 중 onPhotoCaptured 콜백 예외", e)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("카메라이벤트매니저", "${connectionType.name} RAW 파일 검증 중 오류", e)
                                // 오류 발생 시 기본적으로 차단
                                try {
                                    CoroutineScope(Dispatchers.Main).launch {
                                        try {
                                            onRawFileRestricted?.invoke(
                                                fileName,
                                                "파일 형식을 확인할 수 없습니다."
                                            )
                                        } catch (e: Exception) {
                                            Log.w("카메라이벤트매니저", "RAW 파일 오류 콜백 호출 중 예외", e)
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.w("카메라이벤트매니저", "RAW 파일 오류 스레드 시작 중 예외", e)
                                }
                            }
                        }
                    } else {
                        // 일반 파일은 바로 처리 - 안전한 콜백 호출
                        try {
                            onPhotoCaptured(filePath, fileName)
                        } catch (e: Exception) {
                            Log.e("카메라이벤트매니저", "일반 파일 처리 중 onPhotoCaptured 콜백 예외", e)
                        }
                    }

                } catch (e: Exception) {
                    Log.e(
                        "카메라이벤트매니저",
                        "❌ ${connectionType.name} onPhotoCaptured 전체 처리 중 예외 - 이벤트 리스너 계속 동작",
                        e
                    )

                    // 예외 발생 시에도 최소한 파일 정보는 전달 시도
                    try {
                        onPhotoCaptured(filePath, fileName)
                    } catch (e2: Exception) {
                        Log.e("카메라이벤트매니저", "긴급 콜백 호출도 실패", e2)
                    }
                }
            }

            override fun onCaptureFailed(errorCode: Int) {
                Log.e("카메라이벤트매니저", "❌ ${connectionType.name} 외부 셔터 촬영 실패, 오류 코드: $errorCode")
                try {
                    onCaptureFailed(errorCode)
                } catch (e: Exception) {
                    Log.w("카메라이벤트매니저", "${connectionType.name} 촬영 실패 콜백 호출 중 예외", e)
                }
            }

            override fun onUsbDisconnected() {
                when (connectionType) {
                    ConnectionType.USB -> {
                        Log.e("카메라이벤트매니저", "❌ USB 디바이스 분리 감지됨")
                        handleUsbDisconnection()
                    }

                    ConnectionType.PTPIP -> {
                        Log.e("카메라이벤트매니저", "❌ PTPIP 네트워크 연결 끊김 감지됨")
                        handlePtpipDisconnection()
                    }
                }
            }
        }
    }

    /**
     * USB 연결 해제 처리
     */
    private fun handleUsbDisconnection() {
        // 중복 처리 방지 - 원자적 연산으로 체크
        if (!isHandlingUsbDisconnection.compareAndSet(false, true)) {
            Log.d("카메라이벤트매니저", "USB 분리 처리가 이미 진행 중 - 중복 방지")
            return
        }

        try {
            // 이벤트 리스너 자동 중지
            isEventListenerRunning.set(false)
            CoroutineScope(Dispatchers.Main).launch {
                _isEventListenerActive.value = false
            }

            // UsbCameraManager에 USB 분리 알림
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    usbCameraManager.handleUsbDisconnection()
                } catch (e: Exception) {
                    Log.e("카메라이벤트매니저", "USB 분리 처리 중 오류", e)
                } finally {
                    // 처리 완료 후 상태 리셋 (5초 후)
                    kotlinx.coroutines.delay(5000)
                    isHandlingUsbDisconnection.set(false)
                    Log.d("카메라이벤트매니저", "USB 분리 처리 상태 리셋")
                }
            }

            // 추가적인 콜백 처리를 위한 확장 가능한 구조
            onUsbDisconnectedCallback?.invoke()
        } catch (e: Exception) {
            Log.e("카메라이벤트매니저", "USB 분리 콜백 처리 중 예외", e)
            isHandlingUsbDisconnection.set(false)
        }
    }

    /**
     * PTPIP 연결 해제 처리
     */
    private fun handlePtpipDisconnection() {
        try {
            Log.d("카메라이벤트매니저", "PTPIP 네트워크 연결 해제 처리 시작")

            // 이벤트 리스너 자동 중지
            isEventListenerRunning.set(false)
            CoroutineScope(Dispatchers.Main).launch {
                _isEventListenerActive.value = false
            }

            // PTPIP 특화 콜백 (필요시 추가)
            onPtpipDisconnectedCallback?.invoke()

            Log.d("카메라이벤트매니저", "PTPIP 네트워크 연결 해제 처리 완료")
        } catch (e: Exception) {
            Log.e("카메라이벤트매니저", "PTPIP 연결 해제 처리 중 예외", e)
        }
    }

    /**
     * 연결 타입 열거형
     */
    enum class ConnectionType {
        USB, PTPIP
    }
}