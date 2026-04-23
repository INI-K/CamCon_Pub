package com.inik.camcon.domain.repository

import com.inik.camcon.domain.model.AutoConnectNetworkConfig
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
    val isAutoDiscoveryEnabled: Flow<Boolean>
    val isAutoConnectEnabled: Flow<Boolean>
    val isAutoReconnectEnabled: Flow<Boolean>
    val isWifiConnectionModeEnabled: Flow<Boolean>
    val autoConnectNetworkConfig: Flow<AutoConnectNetworkConfig?>
    val lastConnectedIp: Flow<String?>
    val lastConnectedName: Flow<String?>
    val connectionTimeout: Flow<Int>
    val discoveryTimeout: Flow<Int>
    val ptpipPort: Flow<Int>
    val savedWifiCredentials: Flow<List<SavedWifiCredential>>

    // ── 설정 변경 ──

    suspend fun setPtpipEnabled(enabled: Boolean)
    suspend fun setAutoDiscoveryEnabled(enabled: Boolean)
    suspend fun setAutoConnectEnabled(enabled: Boolean)
    suspend fun setAutoReconnectEnabled(enabled: Boolean)
    suspend fun setWifiConnectionModeEnabled(enabled: Boolean)
    suspend fun setConnectionTimeout(timeout: Int)
    suspend fun setDiscoveryTimeout(timeout: Int)
    suspend fun setPtpipPort(port: Int)
    suspend fun clearAllSettings()

    // ── 카메라 정보 ──

    suspend fun saveLastConnectedCamera(ip: String, name: String)

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
