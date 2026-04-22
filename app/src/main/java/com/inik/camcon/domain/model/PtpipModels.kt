package com.inik.camcon.domain.model

/**
 * PTPIP 연결 상태
 */
enum class PtpipConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

/**
 * PTPIP 연결 단계 (상세 상태)
 *
 * mDNS 검색부터 세션 준비까지의 세부 단계를 정의한다.
 */
enum class PtpipConnectionPhase {
    IDLE,
    DISCOVERING,
    DISCOVERED,
    TCP_CONNECTING,
    HANDSHAKING,
    AUTHENTICATING,
    SESSION_READY,
    ERROR
}

/**
 * 니콘 카메라 연결 모드 (AP/STA/UNKNOWN)
 */
enum class NikonConnectionMode {
    AP_MODE,
    STA_MODE,
    UNKNOWN
}

/**
 * PTPIP 카메라 정보
 */
data class PtpipCamera(
    val ipAddress: String,
    val port: Int,
    val name: String,
    val isOnline: Boolean = true,
    val discoveredServiceType: String? = null  // mDNS 서비스 타입: "_ptp._tcp" 또는 "_ptpip._tcp"
)

/**
 * PTPIP 카메라 상세 정보
 */
data class PtpipCameraInfo(
    val manufacturer: String,
    val model: String,
    val version: String,
    val serialNumber: String
)

/**
 * Wi-Fi 네트워크 상태 정보
 */
data class WifiNetworkState(
    val isConnected: Boolean,
    val isConnectedToCameraAP: Boolean,
    val ssid: String?,
    val detectedCameraIP: String?
)

/**
 * Wi-Fi 기능 정보
 */
data class WifiCapabilities(
    val isConnected: Boolean,
    val isStaConcurrencySupported: Boolean,
    val isConnectedToCameraAP: Boolean,
    val networkName: String?,
    val linkSpeed: Int?,
    val frequency: Int?,
    val ipAddress: Int?,
    val macAddress: String?,
    val detectedCameraIP: String?
)

/**
 * PTP 세션 상태머신
 *
 * 소켓 연결부터 세션 준비까지의 전이를 정의한다.
 * 유효하지 않은 상태 전이를 방지하여 동시성 문제를 예방한다.
 */
enum class PtpSessionState {
    DISCONNECTED,
    SOCKET_CONNECTING,
    SOCKET_CONNECTED,
    OPENING,
    OPEN,
    READY,
    CLOSING;

    companion object {
        private val VALID_TRANSITIONS: Map<PtpSessionState, Set<PtpSessionState>> = mapOf(
            DISCONNECTED to setOf(SOCKET_CONNECTING),
            SOCKET_CONNECTING to setOf(SOCKET_CONNECTED, DISCONNECTED),
            SOCKET_CONNECTED to setOf(OPENING, CLOSING, DISCONNECTED),
            OPENING to setOf(OPEN, SOCKET_CONNECTED, DISCONNECTED),
            OPEN to setOf(READY, CLOSING, DISCONNECTED),
            READY to setOf(CLOSING, DISCONNECTED),
            CLOSING to setOf(SOCKET_CONNECTED, DISCONNECTED)
        )

        fun isValidTransition(from: PtpSessionState, to: PtpSessionState): Boolean {
            return VALID_TRANSITIONS[from]?.contains(to) == true
        }
    }
}