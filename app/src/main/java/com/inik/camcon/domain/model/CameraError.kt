package com.inik.camcon.domain.model

/**
 * 카메라 도메인 에러 정의
 *
 * 개선: sealed class로 타입 안전한 에러 처리
 */
sealed class CameraError {
    /**
     * 연결 관련 에러
     */
    data class ConnectionError(
        val code: Int,
        val message: String,
        val recoverable: Boolean = true
    ) : CameraError()

    /**
     * 촬영 관련 에러
     */
    data class CaptureError(
        val reason: CaptureFailureReason,
        val errorCode: Int
    ) : CameraError()

    /**
     * 네트워크 관련 에러
     */
    data class NetworkError(
        val message: String,
        val isWifiIssue: Boolean = false
    ) : CameraError()

    /**
     * USB 관련 에러
     */
    data class UsbError(
        val message: String,
        val deviceName: String?
    ) : CameraError()

    /**
     * 파일 시스템 에러
     */
    data class FileSystemError(
        val message: String,
        val path: String?
    ) : CameraError()

    /**
     * 초기화 에러
     */
    data class InitializationError(
        val message: String,
        val component: String
    ) : CameraError()

    /**
     * 타임아웃 에러
     */
    data class TimeoutError(
        val operation: String,
        val timeoutMs: Long
    ) : CameraError()

    /**
     * 권한 에러
     */
    data class PermissionError(
        val permission: String,
        val message: String
    ) : CameraError()

    /**
     * 알 수 없는 에러
     */
    data class UnknownError(
        val message: String,
        val throwable: Throwable? = null
    ) : CameraError()
}

/**
 * 촬영 실패 이유
 */
enum class CaptureFailureReason {
    CAMERA_NOT_READY,
    CAMERA_DISCONNECTED,
    STORAGE_FULL,
    BATTERY_LOW,
    TIMEOUT,
    INTERNAL_ERROR,
    UNSUPPORTED_MODE,
    FOCUS_FAILURE,
    EXPOSURE_FAILURE,
    UNKNOWN
}

/**
 * 에러를 사용자 친화적인 메시지로 변환
 */
fun CameraError.toUserMessage(): String {
    return when (this) {
        is CameraError.ConnectionError -> "카메라 연결 오류: $message"
        is CameraError.CaptureError -> when (reason) {
            CaptureFailureReason.CAMERA_NOT_READY -> "카메라가 준비되지 않았습니다"
            CaptureFailureReason.CAMERA_DISCONNECTED -> "카메라 연결이 끊어졌습니다"
            CaptureFailureReason.STORAGE_FULL -> "저장 공간이 부족합니다"
            CaptureFailureReason.BATTERY_LOW -> "카메라 배터리가 부족합니다"
            CaptureFailureReason.TIMEOUT -> "촬영 시간이 초과되었습니다"
            CaptureFailureReason.FOCUS_FAILURE -> "초점을 맞출 수 없습니다"
            else -> "촬영 실패 (코드: $errorCode)"
        }

        is CameraError.NetworkError -> if (isWifiIssue) {
            "Wi-Fi 연결 오류: $message"
        } else {
            "네트워크 오류: $message"
        }

        is CameraError.UsbError -> "USB 오류: $message"
        is CameraError.FileSystemError -> "파일 시스템 오류: $message"
        is CameraError.InitializationError -> "$component 초기화 실패: $message"
        is CameraError.TimeoutError -> "$operation 시간 초과 (${timeoutMs}ms)"
        is CameraError.PermissionError -> "권한 필요: $message"
        is CameraError.UnknownError -> "알 수 없는 오류: $message"
    }
}

/**
 * 에러가 복구 가능한지 확인
 */
fun CameraError.isRecoverable(): Boolean {
    return when (this) {
        is CameraError.ConnectionError -> recoverable
        is CameraError.CaptureError -> reason in listOf(
            CaptureFailureReason.TIMEOUT,
            CaptureFailureReason.FOCUS_FAILURE
        )

        is CameraError.NetworkError -> isWifiIssue
        is CameraError.TimeoutError -> true
        else -> false
    }
}
