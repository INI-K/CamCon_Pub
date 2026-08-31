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
    val isAnyConnectionActive: Boolean = false,
    /**
     * 지금 **자동** 재연결을 돌고 있는가.
     *
     * 자동 시도는 사용자가 요청하지 않은 배경 작업이다. 시도마다 CONNECTING↔ERROR 가 토글되는데,
     * 그 CONNECTING 으로 차단 오버레이를 띄우면 사용자가 아무것도 안 했는데 화면이 반복해서
     * 깜빡인다. UI 는 이 값이 true 인 동안 차단 오버레이를 띄우지 않는다.
     */
    val isAutoReconnecting: Boolean = false
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