package com.inik.camcon.domain.model

/**
 * 카메라 연결 타입
 */
enum class CameraConnectionType {
    USB,        // USB 연결
    AP_MODE,    // AP 모드 (카메라가 핫스팟 생성)
    STA_MODE    // STA 모드 (카메라가 기존 네트워크에 연결)
}

/**
 * 전역 카메라 연결 상태
 */
data class GlobalCameraConnectionState(
    val isUsbConnected: Boolean = false,
    val ptpipConnectionState: PtpipConnectionState = PtpipConnectionState.DISCONNECTED,
    val wifiNetworkState: WifiNetworkState = WifiNetworkState(false, false, null, null),
    val discoveredCameras: List<PtpipCamera> = emptyList(),
    val activeConnectionType: CameraConnectionType? = null,
    val isAnyConnectionActive: Boolean = false
)

/**
 * 카메라 연결 상태 이벤트
 */
sealed class CameraConnectionEvent {
    object Connected : CameraConnectionEvent()
    object Disconnected : CameraConnectionEvent()
    object Connecting : CameraConnectionEvent()
    data class Error(val message: String) : CameraConnectionEvent()
    data class ConnectionTypeChanged(val type: CameraConnectionType) : CameraConnectionEvent()
}

/**
 * 카메라 연결 능력 (각 연결 타입별 지원 여부)
 */
data class CameraConnectionCapabilities(
    val supportsUsb: Boolean = false,
    val supportsApMode: Boolean = false,
    val supportsStaMode: Boolean = false,
    val hasUsbPermission: Boolean = false,
    val hasLocationPermission: Boolean = false,
    val isWifiEnabled: Boolean = false
)