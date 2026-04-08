package com.inik.camcon.domain.repository

import com.inik.camcon.domain.model.AutoConnectNetworkConfig
import com.inik.camcon.domain.model.CameraCaptureCallback
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.PtpipCameraInfo
import com.inik.camcon.domain.model.PtpipConnectionState
import com.inik.camcon.domain.model.WifiCapabilities
import com.inik.camcon.domain.model.WifiNetworkState
import kotlinx.coroutines.flow.StateFlow

/**
 * PTP/IP 카메라 연결, 검색, 촬영, 네트워크 상태 관찰을 위한 Repository 인터페이스.
 *
 * presentation 레이어는 PtpipDataSource를 직접 참조하지 않고
 * 이 인터페이스를 통해 PTP/IP 기능에 접근한다.
 */
interface PtpipRepository {

    // ── 상태 관찰 (StateFlow) ──

    /** PTP/IP 연결 상태 */
    val connectionState: StateFlow<PtpipConnectionState>

    /** 연결 진행 메시지 */
    val connectionProgressMessage: StateFlow<String>

    /** 발견된 카메라 목록 */
    val discoveredCameras: StateFlow<List<PtpipCamera>>

    /** 현재 연결된 카메라 정보 */
    val cameraInfo: StateFlow<PtpipCameraInfo?>

    /** Wi-Fi 네트워크 상태 */
    val wifiNetworkState: StateFlow<WifiNetworkState>

    /** 연결 끊어짐 알림 메시지 */
    val connectionLostMessage: StateFlow<String?>

    // ── 카메라 연결/해제 ──

    /** 카메라 연결 (AP/STA 모드 지원) */
    suspend fun connectToCamera(camera: PtpipCamera, forceApMode: Boolean = false): Boolean

    /** 카메라 연결 해제 */
    suspend fun disconnect()

    /** 리소스 정리 */
    fun cleanup()

    // ── 카메라 검색 ──

    /** Wi-Fi 네트워크에서 PTP/IP 카메라 검색 */
    suspend fun discoverCameras(forceApMode: Boolean = false): List<PtpipCamera>

    // ── 촬영 ──

    /** 수동 사진 촬영 */
    suspend fun capturePhoto(callback: CameraCaptureCallback?)

    // ── 네트워크 상태 조회 ──

    fun isWifiConnected(): Boolean
    fun isWifiEnabled(): Boolean
    fun isLocationEnabled(): Boolean
    fun isStaConcurrencySupported(): Boolean
    fun getWifiCapabilities(): WifiCapabilities
    fun getCurrentWifiNetworkState(): WifiNetworkState

    // ── Wi-Fi 연결 관리 ──

    /**
     * WifiNetworkSpecifier로 SSID 연결 요청.
     * 콜백 기반이므로 suspend가 아닌 일반 함수.
     */
    fun requestWifiConnection(
        ssid: String,
        passphrase: String?,
        onResult: (Boolean) -> Unit,
        onError: ((String) -> Unit)?
    )

    /** 현재 SSID의 보안 타입 조회 */
    fun getWifiSecurityType(ssid: String): String?

    /** 현재 연결된 BSSID 조회 */
    fun getCurrentBssid(): String?

    /** 카메라 IP 감지 */
    fun detectCameraIPFromCurrentNetwork(): String?

    /** Wi-Fi 락 해제 */
    fun releaseWifiLock()

    /** Wi-Fi 락 보유 여부 */
    fun isWifiLockHeld(): Boolean

    // ── 자동 연결 관련 ──

    /** NetworkSuggestion 등록 */
    fun registerNetworkSuggestion(config: AutoConnectNetworkConfig): NetworkSuggestionResult

    /** NetworkSuggestion 제거 */
    fun removeNetworkSuggestion(config: AutoConnectNetworkConfig): NetworkSuggestionResult

    /** 자동 연결 브로드캐스트 전송 */
    fun sendAutoConnectBroadcast(ssid: String)

    /** 자동 재연결 활성화/비활성화 */
    fun setAutoReconnectEnabled(enabled: Boolean)

    /** 연결 끊어짐 메시지 클리어 */
    fun clearConnectionLostMessage()

    // ── 위치 설정 ──

    /**
     * 위치 설정 확인 (Google Play Services).
     * Task API를 노출하지 않고 콜백으로 결과를 전달한다.
     */
    fun checkLocationSettings(
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    )

    // ── Wi-Fi SSID 스캔 ──

    /** 주변 Wi-Fi SSID 스캔 */
    suspend fun scanNearbyWifiSSIDs(): List<String>
}

/** NetworkSuggestion 등록/제거 결과 */
data class NetworkSuggestionResult(
    val success: Boolean,
    val message: String
)
