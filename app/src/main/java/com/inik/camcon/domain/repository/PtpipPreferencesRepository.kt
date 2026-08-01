package com.inik.camcon.domain.repository

import com.inik.camcon.domain.model.AutoConnectNetworkConfig
import com.inik.camcon.domain.model.KnownCameraRef
import com.inik.camcon.domain.model.SavedWifiCredential
import kotlinx.coroutines.flow.Flow

/**
 * PTP/IP 관련 설정값 읽기/쓰기를 위한 Repository 인터페이스.
 *
 * presentation 레이어는 PtpipPreferencesDataSource를 직접 참조하지 않고
 * 이 인터페이스를 통해 설정에 접근한다.
 */
interface PtpipPreferencesRepository {

    // ── 설정 상태 관찰 (Flow) ──

    val isPtpipEnabled: Flow<Boolean>
    val isAutoConnectEnabled: Flow<Boolean>
    val isAutoReconnectEnabled: Flow<Boolean>
    val autoConnectNetworkConfig: Flow<AutoConnectNetworkConfig?>
    val lastConnectedIp: Flow<String?>
    val lastConnectedName: Flow<String?>
    val connectionTimeout: Flow<Int>
    val discoveryTimeout: Flow<Int>
    val ptpipPort: Flow<Int>
    val savedWifiCredentials: Flow<List<SavedWifiCredential>>

    // ── 설정 변경 ──

    suspend fun setPtpipEnabled(enabled: Boolean)
    suspend fun setAutoConnectEnabled(enabled: Boolean)
    suspend fun setAutoReconnectEnabled(enabled: Boolean)
    suspend fun setConnectionTimeout(timeout: Int)
    suspend fun setDiscoveryTimeout(timeout: Int)
    suspend fun setPtpipPort(port: Int)
    suspend fun clearAllSettings()

    // ── 카메라 정보 ──

    suspend fun saveLastConnectedCamera(ip: String, name: String)

    /** 마지막 연결 카메라 (ip, name) 즉시 조회. 한 번도 연결 성공 없으면 null. */
    suspend fun getLastConnectedCameraInfo(): Pair<String, String?>?

    /**
     * 기억된 카메라(자동 연결 판정 근거).
     *
     * 승인 플래그가 없으면 기존 사용자로 보고 승인으로 읽는다 — 소급 승인 요구는 업데이트 직후
     * 자동 연결을 무증상 사망시킨다(배경 폴링 경로에 승인 UI가 없다).
     */
    val knownCamera: Flow<KnownCameraRef>
    suspend fun getKnownCamera(): KnownCameraRef

    /** 연결 성공 후 본체 지문 기록(없으면 키 제거 = 지문 없음). */
    suspend fun saveCameraFingerprint(fingerprint: String?)

    /** 자동 연결 승인 갱신(지문 불일치 시 회수). */
    suspend fun setAutoConnectApproved(approved: Boolean)

    // ── 자동 연결 네트워크 설정 ──

    suspend fun saveAutoConnectNetworkConfig(config: AutoConnectNetworkConfig)
    suspend fun getAutoConnectNetworkConfig(): AutoConnectNetworkConfig?
    suspend fun updateAutoConnectNetworkTimestamp()
    suspend fun isAutoConnectEnabledNow(): Boolean

    // ── Wi-Fi 자격 증명 관리 ──

    suspend fun saveWifiCredential(credential: SavedWifiCredential)
    suspend fun getSavedWifiCredential(ssid: String): SavedWifiCredential?
    suspend fun deleteSavedWifiCredential(ssid: String)
}
