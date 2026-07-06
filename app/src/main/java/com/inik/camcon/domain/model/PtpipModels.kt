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
 * 카메라 제조사 (디스커버리 신호 기반 판별)
 */
enum class CameraVendor {
    NIKON,
    CANON,
    SONY,
    FUJIFILM,
    PANASONIC,
    UNKNOWN
}

/**
 * 제조사 판별 신뢰도.
 *
 * UNKNOWN은 "해당 제조사가 아님"이 아니라 "연결 전 신호로는 판별 불가"를 뜻한다.
 * 확정은 연결 후 PTP DeviceInfo의 manufacturer 문자열로만 가능하다
 * (Vendor Extension ID는 Nikon Z8이 Microsoft로 보고하므로 판별에 사용 금지).
 */
enum class VendorConfidence {
    CONFIRMED,
    LIKELY,
    UNKNOWN
}

/**
 * 제조사 판별 결과 (제조사 + 신뢰도)
 */
data class VendorVerdict(
    val vendor: CameraVendor,
    val confidence: VendorConfidence
) {
    companion object {
        fun unknown() = VendorVerdict(CameraVendor.UNKNOWN, VendorConfidence.UNKNOWN)
    }
}

/**
 * PTPIP 카메라 정보
 */
data class PtpipCamera(
    val ipAddress: String,
    val port: Int,
    val name: String,
    val isOnline: Boolean = true,
    val discoveredServiceType: String? = null,  // mDNS 서비스 타입: "_ptp._tcp" 또는 "_ptpip._tcp"
    val vendorVerdict: VendorVerdict = VendorVerdict.unknown()  // 디스커버리 신호 기반 제조사 판별
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
 * Wi-Fi 네트워크 상태 정보.
 *
 * 신규 3 필드(`gatewayIp`, `subnetPrefix`, `isHotspotEnabled`)는 폰 핫스팟 STA 모드를
 * 표현하기 위해 추가됐다. default 값을 가지므로 origin의 positional 4-arg 호출부는
 * 그대로 컴파일된다.
 */
data class WifiNetworkState(
    val isConnected: Boolean,
    val isConnectedToCameraAP: Boolean,
    val ssid: String?,
    val detectedCameraIP: String?,
    val gatewayIp: String? = null,
    val subnetPrefix: Int? = null,
    val isHotspotEnabled: Boolean = false,
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
 * Wi-Fi 스캔 권한 및 전제조건 상태.
 *
 * 권한 보유 여부와 Wi-Fi/위치 서비스 활성화 여부를 종합해 스캔 가능 여부를 표현한다.
 * 시스템 권한/설정 조회 결과를 담는 framework-독립 도메인 모델이다.
 */
data class WifiScanPermissionStatus(
    val hasFineLocationPermission: Boolean,
    val hasNearbyWifiDevicesPermission: Boolean,
    val isWifiEnabled: Boolean,
    val isLocationEnabled: Boolean,
    val canScan: Boolean,
    val androidVersion: Int,
    val missingPermissions: List<String>
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