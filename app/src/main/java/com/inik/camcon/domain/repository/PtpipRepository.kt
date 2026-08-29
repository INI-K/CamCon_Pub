package com.inik.camcon.domain.repository

import com.inik.camcon.domain.model.AutoConnectNetworkConfig
import com.inik.camcon.domain.model.CameraCaptureCallback
import com.inik.camcon.domain.model.ConnectionMethod
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.PtpipCameraInfo
import com.inik.camcon.domain.model.PtpipConnectFailure
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

    /**
     * 직전 연결 시도가 실패한 구조적 사유. null이면 사유가 없다.
     *
     * 문자열 진행 메시지와 달리 이 값으로는 "무엇을 띄울지"를 결정할 수 있다. 페어링 대기는 안내만
     * 하고 재시도를 유지하는 반면, SSH 계열은 자격증명 입력이나 지문 대조 다이얼로그가 필요하다.
     */
    val connectFailure: StateFlow<PtpipConnectFailure?>

    /**
     * 서버가 제시한 SSH 호스트키 지문. 호스트키 계열 실패에서만 값이 차고, 나머지는 null이다.
     *
     * TOFU 다이얼로그가 이 값을 사용자에게 보여 주고 카메라 본체 화면과 대조하게 한다.
     * [connectFailure]와 짝으로 읽는다.
     */
    val sshHostKeyFingerprint: StateFlow<String?>

    /** 현재 활성화된 사용자 시나리오 (AP / STA_ROUTER / STA_PHONE_HOTSPOT). */
    val activeConnectionMethod: StateFlow<ConnectionMethod?>

    /** 사용자 수동 입력 IP. 폰 핫스팟 모드의 mDNS 폴백용. */
    val manualIp: StateFlow<String>

    // ── 카메라 연결/해제 ──

    /** 카메라 연결 (AP/STA 모드 지원) */
    suspend fun connectToCamera(camera: PtpipCamera, forceApMode: Boolean = false): Boolean

    /** 사용자 시나리오 선택. */
    fun setActiveConnectionMethod(method: ConnectionMethod)

    /** 사용자 입력 IP 갱신. */
    fun setManualIp(ip: String)

    /** 사용자가 직접 입력한 IP를 카메라 후보 목록에 추가. */
    suspend fun addManualCamera(ip: String, name: String, port: Int): PtpipCamera

    /** 카메라 연결 해제 */
    suspend fun disconnect()

    /**
     * 진행 중 연결의 협조적 취소 요청. mutex를 획득하지 않고 즉시 반환한다.
     *
     * 반드시 `disconnect()`보다 **먼저** 호출한다 — 순서가 뒤바뀌면 disconnect가 연결 mutex에
     * 먼저 큐잉되어 취소가 다시 무력화된다.
     */
    fun requestConnectCancel()

    /** 세션 점유로 검색을 시도하면 안 되는 상태(CONNECTING/CONNECTED/무선수신). */
    fun isDiscoveryBlocked(): Boolean

    /**
     * **자동 연결**(무탭) 금지 상태. [isDiscoveryBlocked]보다 강하다 —
     * 여기에는 살아있는 USB 세션·영상녹화·라이브뷰 종료전이·취소 직후 쿨다운이 포함된다.
     *
     * ⚠️ 검색 스킵 판정([isDiscoveryBlocked])과 혼용하지 말 것. `initCameraWithPtpip`는 USB 공유
     * 네이티브 핸들을 무경고 파괴하므로, 자동 연결 분기는 반드시 이 함수를 써야 한다.
     * 전경(ViewModel)과 배경(WifiMonitoringService)이 서로 다른 조건을 쓰면 같은 상황에서
     * 결과가 갈린다.
     */
    fun isAutoConnectBlocked(): Boolean

    /** 리소스 정리 */
    fun cleanup()

    // ── 카메라 검색 ──

    /** Wi-Fi 네트워크에서 PTP/IP 카메라 검색 */
    suspend fun discoverCameras(forceApMode: Boolean = false): List<PtpipCamera>

    /**
     * 서브넷 TCP 스윕(최후 폴백). mDNS/SSDP가 0건일 때 **사용자가 명시적으로** 실행한다.
     * 결과는 기존 후보에 병합되고 자동 연결 대상에서는 제외된다(이름·제조사 신호 없음).
     */
    suspend fun sweepSubnetForCameras(): List<PtpipCamera>

    /** 서브넷 스윕 실행 가능 여부(프리픽스 취득 가능). false면 UI가 버튼을 노출하지 않는다. */
    fun isSubnetSweepAvailable(): Boolean

    // ── 촬영 ──

    /** 수동 사진 촬영 */
    suspend fun capturePhoto(callback: CameraCaptureCallback?)

    /** 물리 셔터 무선 수신 모드 시작/중지 (니콘 STA vendor 0x****/0x**** 풀해상도). */
    fun startShutterListening(camera: PtpipCamera)
    fun stopShutterListening()

    // ── 네트워크 상태 조회 ──

    fun isWifiConnected(): Boolean
    fun isWifiEnabled(): Boolean
    fun isLocationEnabled(): Boolean
    fun isStaConcurrencySupported(): Boolean
    fun getWifiCapabilities(): WifiCapabilities
    fun getCurrentWifiNetworkState(): WifiNetworkState

    /** 네트워크 상태를 즉시 재평가해 [wifiNetworkState]에 반영(핫스팟 토글 후 UI 갱신용). */
    suspend fun refreshWifiNetworkState()

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

    /** Wi-Fi 모니터링 Foreground Service 시작 (자동 연결 등록 성공 시). */
    fun startWifiMonitoring()

    /** Wi-Fi 모니터링 서비스 중지 (자동 연결 해제 시). */
    fun stopWifiMonitoring()

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
