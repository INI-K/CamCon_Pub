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
 * 니콘 카메라 연결 모드 (AP/STA/UNKNOWN).
 *
 * 레거시 진단/디버그 코드 호환용. 신규 코드는 [ConnectionMethod]를 사용한다.
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
    val isOnline: Boolean = true
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
 * - [isHotspotEnabled]: 이 폰이 직접 핫스팟(테더링)을 켜고 있는 상태. 카메라 STA 클라이언트를 받는 시나리오.
 * - [gatewayIp]: 현재 네트워크의 게이트웨이 IP. AP 모드에서는 카메라 IP 후보.
 * - [subnetPrefix]: 현재 서브넷 prefix 길이 (CIDR). 검색 범위 한정에 사용.
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
