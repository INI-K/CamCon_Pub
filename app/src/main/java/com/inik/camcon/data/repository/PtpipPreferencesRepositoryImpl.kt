package com.inik.camcon.data.repository

import com.inik.camcon.data.datasource.local.PtpipPreferencesDataSource
import com.inik.camcon.domain.model.AutoConnectNetworkConfig
import com.inik.camcon.domain.model.SavedWifiCredential
import com.inik.camcon.domain.repository.PtpipPreferencesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PtpipPreferencesDataSource를 래핑하여 domain의 PtpipPreferencesRepository 인터페이스를 구현한다.
 */
@Singleton
class PtpipPreferencesRepositoryImpl @Inject constructor(
    private val dataSource: PtpipPreferencesDataSource
) : PtpipPreferencesRepository {

    // ── Flow 위임 ──

    override val isPtpipEnabled: Flow<Boolean> get() = dataSource.isPtpipEnabled
    override val isAutoDiscoveryEnabled: Flow<Boolean> get() = dataSource.isAutoDiscoveryEnabled
    override val isAutoConnectEnabled: Flow<Boolean> get() = dataSource.isAutoConnectEnabled
    override val isAutoReconnectEnabled: Flow<Boolean> get() = dataSource.isAutoReconnectEnabled
    override val isWifiConnectionModeEnabled: Flow<Boolean> get() = dataSource.isWifiConnectionModeEnabled
    override val autoConnectNetworkConfig: Flow<AutoConnectNetworkConfig?>
        get() = dataSource.autoConnectNetworkConfig
    override val lastConnectedIp: Flow<String?> get() = dataSource.lastConnectedIp
    override val lastConnectedName: Flow<String?> get() = dataSource.lastConnectedName
    override val connectionTimeout: Flow<Int> get() = dataSource.connectionTimeout
    override val discoveryTimeout: Flow<Int> get() = dataSource.discoveryTimeout
    override val ptpipPort: Flow<Int> get() = dataSource.ptpipPort
    override val savedWifiCredentials: Flow<List<SavedWifiCredential>>
        get() = dataSource.savedWifiCredentials

    // ── 설정 변경 위임 ──

    override suspend fun setPtpipEnabled(enabled: Boolean) = dataSource.setPtpipEnabled(enabled)

    override suspend fun setAutoDiscoveryEnabled(enabled: Boolean) =
        dataSource.setAutoDiscoveryEnabled(enabled)

    override suspend fun setAutoConnectEnabled(enabled: Boolean) =
        dataSource.setAutoConnectEnabled(enabled)

    override suspend fun setAutoReconnectEnabled(enabled: Boolean) =
        dataSource.setAutoReconnectEnabled(enabled)

    override suspend fun setWifiConnectionModeEnabled(enabled: Boolean) =
        dataSource.setWifiConnectionModeEnabled(enabled)

    override suspend fun setConnectionTimeout(timeout: Int) =
        dataSource.setConnectionTimeout(timeout)

    override suspend fun setDiscoveryTimeout(timeout: Int) =
        dataSource.setDiscoveryTimeout(timeout)

    override suspend fun setPtpipPort(port: Int) = dataSource.setPtpipPort(port)

    override suspend fun clearAllSettings() = dataSource.clearAllSettings()

    // ── 카메라 정보 ──

    override suspend fun saveLastConnectedCamera(ip: String, name: String) =
        dataSource.saveLastConnectedCamera(ip, name)

    // ── 자동 연결 네트워크 설정 ──

    override suspend fun saveAutoConnectNetworkConfig(config: AutoConnectNetworkConfig) =
        dataSource.saveAutoConnectNetworkConfig(config)

    override suspend fun getAutoConnectNetworkConfig(): AutoConnectNetworkConfig? =
        dataSource.getAutoConnectNetworkConfig()

    override suspend fun updateAutoConnectNetworkTimestamp() =
        dataSource.updateAutoConnectNetworkTimestamp()

    override suspend fun isAutoConnectEnabledNow(): Boolean =
        dataSource.isAutoConnectEnabledNow()

    // ── Wi-Fi 자격 증명 ──

    override suspend fun saveWifiCredential(credential: SavedWifiCredential) =
        dataSource.saveWifiCredential(credential)

    override suspend fun getSavedWifiCredential(ssid: String): SavedWifiCredential? =
        dataSource.getSavedWifiCredential(ssid)

    override suspend fun deleteSavedWifiCredential(ssid: String) =
        dataSource.deleteSavedWifiCredential(ssid)
}
