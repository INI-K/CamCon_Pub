package com.inik.camcon.domain.manager

import android.util.Log
import com.inik.camcon.CameraNative
import com.inik.camcon.NativeErrorCallback
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 에러 처리 및 네이티브 콜백 관리 전용 매니저
 * 단일책임: 모든 에러 처리 로직을 중앙 집중화
 * Domain Layer: Android Framework에 독립적
 */
@Singleton
class ErrorHandlingManager @Inject constructor() {

    companion object {
        private const val TAG = "에러핸들링매니저"

        // 에러 코드 상수
        const val ERROR_USB_TIMEOUT = -10
        const val ERROR_USB_DETECTION_FAILED = -52
        const val ERROR_USB_WRITE_FAILED = -35
    }

    // 에러 이벤트 스트림
    private val _errorEvent = MutableSharedFlow<ErrorEvent>()
    val errorEvent: SharedFlow<ErrorEvent> = _errorEvent.asSharedFlow()

    // 네이티브 에러 스트림
    private val _nativeErrorEvent = MutableSharedFlow<NativeErrorEvent>()
    val nativeErrorEvent: SharedFlow<NativeErrorEvent> = _nativeErrorEvent.asSharedFlow()

    private var isCallbackRegistered = false

    /**
     * 에러 처리 시스템 초기화
     */
    fun initialize() {
        if (!isCallbackRegistered) {
            registerNativeErrorCallback()
            isCallbackRegistered = true
            Log.d(TAG, "에러 처리 시스템 초기화 완료")
        }
    }

    /**
     * 네이티브 에러 콜백 등록
     */
    private fun registerNativeErrorCallback() {
        try {
            CameraNative.setErrorCallback(object : NativeErrorCallback {
                override fun onNativeError(errorCode: Int, errorMessage: String) {
                    handleNativeError(errorCode, errorMessage)
                }
            })
            Log.d(TAG, "네이티브 에러 콜백 등록 완료")
        } catch (e: Exception) {
            Log.e(TAG, "네이티브 에러 콜백 등록 실패", e)
            emitError(ErrorType.INITIALIZATION, "네이티브 에러 콜백 등록 실패: ${e.message}")
        }
    }

    /**
     * 네이티브 에러 처리
     */
    private fun handleNativeError(errorCode: Int, errorMessage: String) {
        Log.e(TAG, "네이티브 에러 감지: 코드=$errorCode, 메시지=$errorMessage")

        val errorEvent = when (errorCode) {
            ERROR_USB_TIMEOUT -> {
                NativeErrorEvent(
                    errorCode = errorCode,
                    originalMessage = errorMessage,
                    userFriendlyMessage = "USB 포트 타임아웃 에러가 발생했습니다",
                    severity = ErrorSeverity.WARNING,
                    actionRequired = ErrorAction.RETRY_CONNECTION
                )
            }

            ERROR_USB_DETECTION_FAILED -> {
                NativeErrorEvent(
                    errorCode = errorCode,
                    originalMessage = errorMessage,
                    userFriendlyMessage = "USB 카메라를 감지할 수 없습니다. 앱을 재시작해주세요.",
                    severity = ErrorSeverity.CRITICAL,
                    actionRequired = ErrorAction.RESTART_APP
                )
            }

            ERROR_USB_WRITE_FAILED -> {
                NativeErrorEvent(
                    errorCode = errorCode,
                    originalMessage = errorMessage,
                    userFriendlyMessage = "USB 연결에 문제가 있습니다.\n\nUSB 케이블을 확인하거나 카메라를 재연결하세요.",
                    severity = ErrorSeverity.HIGH,
                    actionRequired = ErrorAction.RECONNECT_CAMERA
                )
            }

            else -> {
                NativeErrorEvent(
                    errorCode = errorCode,
                    originalMessage = errorMessage,
                    userFriendlyMessage = "알 수 없는 네이티브 에러가 발생했습니다 ($errorCode)",
                    severity = ErrorSeverity.MEDIUM,
                    actionRequired = ErrorAction.SHOW_ERROR
                )
            }
        }

        // 네이티브 에러 이벤트 발생
        try {
            _nativeErrorEvent.tryEmit(errorEvent)
        } catch (e: Exception) {
            Log.e(TAG, "네이티브 에러 이벤트 발생 실패", e)
        }
    }

    /**
     * 일반적인 에러 이벤트 발생
     */
    fun emitError(
        type: ErrorType,
        message: String,
        exception: Throwable? = null,
        severity: ErrorSeverity = ErrorSeverity.MEDIUM
    ) {
        val errorEvent = ErrorEvent(
            type = type,
            message = message,
            exception = exception,
            severity = severity,
            timestamp = System.currentTimeMillis()
        )

        try {
            _errorEvent.tryEmit(errorEvent)
            Log.e(TAG, "에러 발생: $type - $message", exception)
        } catch (e: Exception) {
            Log.e(TAG, "에러 이벤트 발생 실패", e)
        }
    }

    /**
     * 카메라 연결 관련 에러 처리
     */
    fun handleConnectionError(exception: Throwable): String {
        return when {
            exception.message?.contains("timeout", ignoreCase = true) == true -> {
                emitError(ErrorType.CONNECTION, "카메라 연결 타임아웃", exception, ErrorSeverity.HIGH)
                "카메라 연결 시간이 초과되었습니다. 카메라 상태를 확인하고 다시 시도해주세요."
            }

            exception.message?.contains("permission", ignoreCase = true) == true -> {
                emitError(ErrorType.PERMISSION, "카메라 권한 오류", exception, ErrorSeverity.HIGH)
                "카메라 접근 권한이 없습니다. USB 권한을 확인해주세요."
            }

            exception.message?.contains("not found", ignoreCase = true) == true -> {
                emitError(ErrorType.CONNECTION, "카메라 미발견", exception, ErrorSeverity.MEDIUM)
                "카메라를 찾을 수 없습니다. USB 연결을 확인해주세요."
            }

            else -> {
                emitError(ErrorType.CONNECTION, "카메라 연결 실패", exception, ErrorSeverity.MEDIUM)
                "카메라 연결에 실패했습니다: ${exception.message ?: "알 수 없는 오류"}"
            }
        }
    }

    /**
     * 사진 촬영 관련 에러 처리
     */
    fun handleCaptureError(exception: Throwable): String {
        return when {
            exception.message?.contains("busy", ignoreCase = true) == true -> {
                emitError(ErrorType.OPERATION, "카메라 사용 중", exception, ErrorSeverity.MEDIUM)
                "카메라가 다른 작업을 수행 중입니다. 잠시 후 다시 시도해주세요."
            }

            exception.message?.contains("storage", ignoreCase = true) == true -> {
                emitError(ErrorType.STORAGE, "저장소 부족", exception, ErrorSeverity.HIGH)
                "저장 공간이 부족합니다. 공간을 확보한 후 다시 시도해주세요."
            }

            else -> {
                emitError(ErrorType.OPERATION, "사진 촬영 실패", exception, ErrorSeverity.MEDIUM)
                "사진 촬영에 실패했습니다: ${exception.message ?: "알 수 없는 오류"}"
            }
        }
    }

    /**
     * 파일 작업 관련 에러 처리
     */
    fun handleFileError(exception: Throwable, operation: String): String {
        return when {
            exception.message?.contains("not found", ignoreCase = true) == true -> {
                emitError(ErrorType.FILE_SYSTEM, "파일 미발견", exception, ErrorSeverity.MEDIUM)
                "파일을 찾을 수 없습니다."
            }

            exception.message?.contains("permission", ignoreCase = true) == true -> {
                emitError(ErrorType.PERMISSION, "파일 권한 오류", exception, ErrorSeverity.HIGH)
                "파일 접근 권한이 없습니다."
            }

            exception.message?.contains("space", ignoreCase = true) == true -> {
                emitError(ErrorType.STORAGE, "저장 공간 부족", exception, ErrorSeverity.HIGH)
                "저장 공간이 부족합니다."
            }

            else -> {
                emitError(ErrorType.FILE_SYSTEM, "$operation 실패", exception, ErrorSeverity.MEDIUM)
                "$operation 중 오류가 발생했습니다: ${exception.message ?: "알 수 없는 오류"}"
            }
        }
    }

    /**
     * 네트워크 관련 에러 처리
     */
    fun handleNetworkError(exception: Throwable): String {
        return when {
            exception.message?.contains("timeout", ignoreCase = true) == true -> {
                emitError(ErrorType.NETWORK, "네트워크 타임아웃", exception, ErrorSeverity.MEDIUM)
                "네트워크 연결 시간이 초과되었습니다."
            }

            exception.message?.contains("connection", ignoreCase = true) == true -> {
                emitError(ErrorType.NETWORK, "네트워크 연결 실패", exception, ErrorSeverity.MEDIUM)
                "네트워크 연결에 실패했습니다. Wi-Fi 상태를 확인해주세요."
            }

            else -> {
                emitError(ErrorType.NETWORK, "네트워크 오류", exception, ErrorSeverity.MEDIUM)
                "네트워크 오류가 발생했습니다: ${exception.message ?: "알 수 없는 오류"}"
            }
        }
    }

    /**
     * 에러 처리 시스템 정리
     */
    fun cleanup() {
        try {
            CameraNative.setErrorCallback(null)
            isCallbackRegistered = false
            Log.d(TAG, "에러 처리 시스템 정리 완료")
        } catch (e: Exception) {
            Log.w(TAG, "에러 처리 시스템 정리 중 오류", e)
        }
    }
}

/**
 * 에러 이벤트 데이터 클래스
 */
data class ErrorEvent(
    val type: ErrorType,
    val message: String,
    val exception: Throwable?,
    val severity: ErrorSeverity,
    val timestamp: Long
)

/**
 * 네이티브 에러 이벤트 데이터 클래스
 */
data class NativeErrorEvent(
    val errorCode: Int,
    val originalMessage: String,
    val userFriendlyMessage: String,
    val severity: ErrorSeverity,
    val actionRequired: ErrorAction
)

/**
 * 에러 타입 열거형
 */
enum class ErrorType {
    CONNECTION,
    PERMISSION,
    OPERATION,
    FILE_SYSTEM,
    NETWORK,
    STORAGE,
    INITIALIZATION,
    UNKNOWN
}

/**
 * 에러 심각도 열거형
 */
enum class ErrorSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
    WARNING
}

/**
 * 에러 발생 시 필요한 액션 열거형
 */
enum class ErrorAction {
    SHOW_ERROR,
    RETRY_CONNECTION,
    RECONNECT_CAMERA,
    RESTART_APP,
    CHECK_PERMISSIONS,
    FREE_STORAGE,
    CHECK_NETWORK
}