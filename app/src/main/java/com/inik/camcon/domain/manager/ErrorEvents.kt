package com.inik.camcon.domain.manager

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
