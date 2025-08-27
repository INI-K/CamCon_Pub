package com.inik.camcon.utils

import android.util.Log
import com.inik.camcon.BuildConfig

/**
 * 로그 출력을 중앙에서 관리하는 매니저
 * 릴리즈 빌드에서는 로그를 비활성화하여 성능을 향상시킵니다.
 * 개발 중에는 특정 컴포넌트별로 로그를 선택적으로 활성화할 수 있습니다.
 */
object LogcatManager {

    /**
     * 기본 로그 출력 활성화 여부
     * DEBUG 빌드에서만 로그가 출력됩니다.
     */
    private val isLoggingEnabled = BuildConfig.DEBUG

    /**
     * 상세 로그 출력 활성화 여부
     * 더 자세한 로그를 원할 때 사용
     */
    private val verboseLoggingEnabled = isLoggingEnabled && Constants.Logging.ENABLE_VERBOSE_LOGGING

    /**
     * 컴포넌트별 로그 활성화 상태 확인
     */
    private fun isComponentLoggingEnabled(tag: String): Boolean {
        if (!isLoggingEnabled) return false

        return when {
            tag.contains("카메라이벤트", ignoreCase = true) ||
                    tag.contains(
                        "CameraEvent",
                        ignoreCase = true
                    ) -> Constants.Logging.ENABLE_CAMERA_EVENT_LOGS

            tag.contains("USB", ignoreCase = true) ||
                    tag.contains("UsbCamera", ignoreCase = true) ||
                    tag.contains(
                        "UsbDevice",
                        ignoreCase = true
                    ) -> Constants.Logging.ENABLE_USB_CONNECTION_LOGS

            tag.contains("PTPIP", ignoreCase = true) ||
                    tag.contains("PtpipCamera", ignoreCase = true) ||
                    tag.contains(
                        "NikonAuth",
                        ignoreCase = true
                    ) -> Constants.Logging.ENABLE_PTPIP_CONNECTION_LOGS

            tag.contains(
                "ColorTransfer",
                ignoreCase = true
            ) -> Constants.Logging.ENABLE_COLOR_TRANSFER_LOGS

            tag.contains(
                "BackgroundSync",
                ignoreCase = true
            ) -> Constants.Logging.ENABLE_BACKGROUND_SERVICE_LOGS

            else -> true // 다른 컴포넌트는 기본적으로 활성화
        }
    }

    /**
     * DEBUG 레벨 로그
     */
    fun d(tag: String, message: String) {
        if (isComponentLoggingEnabled(tag)) {
            Log.d(tag, message)
        }
    }

    /**
     * INFO 레벨 로그
     */
    fun i(tag: String, message: String) {
        if (isComponentLoggingEnabled(tag)) {
            Log.i(tag, message)
        }
    }

    /**
     * WARNING 레벨 로그
     */
    fun w(tag: String, message: String) {
        if (isComponentLoggingEnabled(tag)) {
            Log.w(tag, message)
        }
    }

    /**
     * WARNING 레벨 로그 (예외 포함)
     */
    fun w(tag: String, message: String, throwable: Throwable?) {
        if (isComponentLoggingEnabled(tag)) {
            Log.w(tag, message, throwable)
        }
    }

    /**
     * ERROR 레벨 로그
     * 에러는 컴포넌트 설정과 관계없이 항상 출력
     */
    fun e(tag: String, message: String) {
        if (isLoggingEnabled) {
            Log.e(tag, message)
        }
    }

    /**
     * ERROR 레벨 로그 (예외 포함)
     * 에러는 컴포넌트 설정과 관계없이 항상 출력
     */
    fun e(tag: String, message: String, throwable: Throwable?) {
        if (isLoggingEnabled) {
            Log.e(tag, message, throwable)
        }
    }

    /**
     * VERBOSE 레벨 로그
     * 매우 상세한 로그, 개발 시에만 활성화
     */
    fun v(tag: String, message: String) {
        if (verboseLoggingEnabled && isComponentLoggingEnabled(tag)) {
            Log.v(tag, message)
        }
    }

    /**
     * 조건부 로그 출력
     * 특정 조건을 만족할 때만 로그를 출력합니다.
     */
    fun conditionalLog(condition: Boolean, tag: String, message: String, level: Int = Log.DEBUG) {
        if (condition && isComponentLoggingEnabled(tag)) {
            when (level) {
                Log.VERBOSE -> Log.v(tag, message)
                Log.DEBUG -> Log.d(tag, message)
                Log.INFO -> Log.i(tag, message)
                Log.WARN -> Log.w(tag, message)
                Log.ERROR -> Log.e(tag, message)
            }
        }
    }

    /**
     * 성능 측정용 로그 (시작)
     */
    fun perfStart(tag: String, operation: String) {
        if (verboseLoggingEnabled && isComponentLoggingEnabled(tag)) {
            Log.d(tag, "⏱️ PERF_START: $operation")
        }
    }

    /**
     * 성능 측정용 로그 (종료)
     */
    fun perfEnd(tag: String, operation: String, startTime: Long) {
        if (verboseLoggingEnabled && isComponentLoggingEnabled(tag)) {
            val duration = System.currentTimeMillis() - startTime
            Log.d(tag, "⏱️ PERF_END: $operation (${duration}ms)")
        }
    }

    /**
     * 현재 로그 설정 상태를 출력
     */
    fun printLogSettings() {
        if (isLoggingEnabled) {
            Log.i("LogcatManager", "=== 로그 설정 상태 ===")
            Log.i("LogcatManager", "전체 로깅: $isLoggingEnabled")
            Log.i("LogcatManager", "상세 로깅: $verboseLoggingEnabled")
            Log.i("LogcatManager", "카메라 이벤트 로그: ${Constants.Logging.ENABLE_CAMERA_EVENT_LOGS}")
            Log.i("LogcatManager", "USB 연결 로그: ${Constants.Logging.ENABLE_USB_CONNECTION_LOGS}")
            Log.i("LogcatManager", "PTPIP 연결 로그: ${Constants.Logging.ENABLE_PTPIP_CONNECTION_LOGS}")
            Log.i("LogcatManager", "색상 전송 로그: ${Constants.Logging.ENABLE_COLOR_TRANSFER_LOGS}")
            Log.i(
                "LogcatManager",
                "백그라운드 서비스 로그: ${Constants.Logging.ENABLE_BACKGROUND_SERVICE_LOGS}"
            )
        }
    }
}