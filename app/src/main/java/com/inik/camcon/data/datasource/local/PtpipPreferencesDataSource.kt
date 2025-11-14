package com.inik.camcon.data.datasource.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.ptpipDataStore: DataStore<Preferences> by preferencesDataStore(name = "ptpip_settings")

/**
 * PTPIP 설정 정보를 관리하는 DataSource
 * STA 모드 기반 설정을 기본으로 지원
 */
@Singleton
class PtpipPreferencesDataSource @Inject constructor(
    private val context: Context
) {
    companion object {
        private val PTPIP_ENABLED = booleanPreferencesKey("ptpip_enabled")
        private val AUTO_DISCOVERY = booleanPreferencesKey("auto_discovery")
        private val LAST_CONNECTED_IP = stringPreferencesKey("last_connected_ip")
        private val LAST_CONNECTED_NAME = stringPreferencesKey("last_connected_name")
        private val CONNECTION_TIMEOUT = intPreferencesKey("connection_timeout")
        private val DISCOVERY_TIMEOUT = intPreferencesKey("discovery_timeout")
        private val PTPIP_PORT = intPreferencesKey("ptpip_port")
        private val AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        private val WIFI_CONNECTION_MODE = booleanPreferencesKey("wifi_connection_mode")
        private val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        private val AUTO_CONNECT_SSID = stringPreferencesKey("auto_connect_ssid")
        private val AUTO_CONNECT_SECURITY = stringPreferencesKey("auto_connect_security")
        private val AUTO_CONNECT_PASSPHRASE = stringPreferencesKey("auto_connect_passphrase")
        private val AUTO_CONNECT_HIDDEN = booleanPreferencesKey("auto_connect_hidden")
        private val AUTO_CONNECT_BSSID = stringPreferencesKey("auto_connect_bssid")
        private val AUTO_CONNECT_UPDATED_AT = longPreferencesKey("auto_connect_updated_at")
    }

    /**
     * PTPIP 기능 활성화 여부
     */
    val isPtpipEnabled: Flow<Boolean> = context.ptpipDataStore.data
        .map { preferences ->
            preferences[PTPIP_ENABLED] ?: false
        }

    /**
     * 자동 카메라 검색 활성화 여부
     */
    val isAutoDiscoveryEnabled: Flow<Boolean> = context.ptpipDataStore.data
        .map { preferences ->
            preferences[AUTO_DISCOVERY] ?: true
        }

    /**
     * 마지막 연결된 카메라 IP
     */
    val lastConnectedIp: Flow<String?> = context.ptpipDataStore.data
        .map { preferences ->
            preferences[LAST_CONNECTED_IP]
        }

    /**
     * 마지막 연결된 카메라 이름
     */
    val lastConnectedName: Flow<String?> = context.ptpipDataStore.data
        .map { preferences ->
            preferences[LAST_CONNECTED_NAME]
        }

    /**
     * 연결 타임아웃 (밀리초)
     */
    val connectionTimeout: Flow<Int> = context.ptpipDataStore.data
        .map { preferences ->
            preferences[CONNECTION_TIMEOUT] ?: 10000
        }

    /**
     * 카메라 검색 타임아웃 (밀리초)
     */
    val discoveryTimeout: Flow<Int> = context.ptpipDataStore.data
        .map { preferences ->
            preferences[DISCOVERY_TIMEOUT] ?: 30000
        }

    /**
     * PTPIP 포트 번호
     */
    val ptpipPort: Flow<Int> = context.ptpipDataStore.data
        .map { preferences ->
            preferences[PTPIP_PORT] ?: 15740
        }

    /**
     * 자동 연결 활성화 여부
     */
    val isAutoConnectEnabled: Flow<Boolean> = context.ptpipDataStore.data
        .map { preferences ->
            preferences[AUTO_CONNECT] ?: false
        }

    /**
     * 자동 재연결 활성화 여부
     */
    val isAutoReconnectEnabled: Flow<Boolean> = context.ptpipDataStore.data
        .map { preferences ->
            preferences[AUTO_RECONNECT] ?: true
        }

    /**
     * Wi-Fi 연결 활성화 여부 (기본값: true - WIFI 연결 우선)
     *
     * @return Wi-Fi 연결 활성화 여부
     * @since 2024
     */
    val isWifiConnectionModeEnabled: Flow<Boolean> = context.ptpipDataStore.data
        .map { preferences ->
            preferences[WIFI_CONNECTION_MODE] ?: true
        }

    /**
     * 자동 연결용 Wi-Fi 제안 설정 정보
     */
    val autoConnectNetworkConfig: Flow<com.inik.camcon.domain.model.AutoConnectNetworkConfig?> =
        context.ptpipDataStore.data
            .map { preferences ->
                val ssid = preferences[AUTO_CONNECT_SSID]
                if (ssid.isNullOrBlank()) {
                    null
                } else {
                    com.inik.camcon.domain.model.AutoConnectNetworkConfig(
                        ssid = ssid,
                        passphrase = preferences[AUTO_CONNECT_PASSPHRASE],
                        securityType = preferences[AUTO_CONNECT_SECURITY],
                        isHidden = preferences[AUTO_CONNECT_HIDDEN] ?: false,
                        bssid = preferences[AUTO_CONNECT_BSSID],
                        lastUpdatedEpochMillis = preferences[AUTO_CONNECT_UPDATED_AT]
                            ?: System.currentTimeMillis()
                    )
                }
            }

    /**
     * PTPIP 기능 활성화/비활성화
     */
    suspend fun setPtpipEnabled(enabled: Boolean) {
        context.ptpipDataStore.edit { preferences ->
            preferences[PTPIP_ENABLED] = enabled
        }
    }

    /**
     * 자동 카메라 검색 활성화/비활성화
     */
    suspend fun setAutoDiscoveryEnabled(enabled: Boolean) {
        context.ptpipDataStore.edit { preferences ->
            preferences[AUTO_DISCOVERY] = enabled
        }
    }

    /**
     * 마지막 연결된 카메라 정보 저장
     */
    suspend fun saveLastConnectedCamera(ip: String, name: String) {
        context.ptpipDataStore.edit { preferences ->
            preferences[LAST_CONNECTED_IP] = ip
            preferences[LAST_CONNECTED_NAME] = name
        }
    }

    /**
     * 연결 타임아웃 설정
     */
    suspend fun setConnectionTimeout(timeout: Int) {
        context.ptpipDataStore.edit { preferences ->
            preferences[CONNECTION_TIMEOUT] = timeout
        }
    }

    /**
     * 카메라 검색 타임아웃 설정
     */
    suspend fun setDiscoveryTimeout(timeout: Int) {
        context.ptpipDataStore.edit { preferences ->
            preferences[DISCOVERY_TIMEOUT] = timeout
        }
    }

    /**
     * PTPIP 포트 번호 설정
     */
    suspend fun setPtpipPort(port: Int) {
        context.ptpipDataStore.edit { preferences ->
            preferences[PTPIP_PORT] = port
        }
    }

    /**
     * 자동 연결 활성화/비활성화
     */
    suspend fun setAutoConnectEnabled(enabled: Boolean) {
        context.ptpipDataStore.edit { preferences ->
            preferences[AUTO_CONNECT] = enabled
        }
    }

    /**
     * 현재 자동 연결 활성화 여부 즉시 조회
     */
    suspend fun isAutoConnectEnabledNow(): Boolean {
        return context.ptpipDataStore.data.first()[AUTO_CONNECT] ?: false
    }

    /**
     * 자동 재연결 활성화/비활성화
     */
    suspend fun setAutoReconnectEnabled(enabled: Boolean) {
        context.ptpipDataStore.edit { preferences ->
            preferences[AUTO_RECONNECT] = enabled
        }
    }

    /**
     * Wi-Fi 연결 활성화/비활성화
     * true: 동일 네트워크에서 카메라 검색 (WIFI 연결)
     * false: 직접 연결 (AP 모드)
     *
     * @param enabled 활성화 여부
     * @since 2024
     */
    suspend fun setWifiConnectionModeEnabled(enabled: Boolean) {
        context.ptpipDataStore.edit { preferences ->
            preferences[WIFI_CONNECTION_MODE] = enabled
        }
    }

    /**
     * 자동 연결을 위한 Wi-Fi 네트워크 정보 저장
     */
    suspend fun saveAutoConnectNetworkConfig(config: com.inik.camcon.domain.model.AutoConnectNetworkConfig) {
        context.ptpipDataStore.edit { preferences ->
            preferences[AUTO_CONNECT_SSID] = config.ssid
            if (config.securityType.isNullOrEmpty()) {
                preferences.remove(AUTO_CONNECT_SECURITY)
            } else {
                preferences[AUTO_CONNECT_SECURITY] = config.securityType
            }
            if (config.passphrase.isNullOrEmpty()) {
                preferences.remove(AUTO_CONNECT_PASSPHRASE)
            } else {
                preferences[AUTO_CONNECT_PASSPHRASE] = config.passphrase
            }
            preferences[AUTO_CONNECT_HIDDEN] = config.isHidden
            config.bssid?.let { preferences[AUTO_CONNECT_BSSID] = it } ?: preferences.remove(
                AUTO_CONNECT_BSSID
            )
            preferences[AUTO_CONNECT_UPDATED_AT] = config.lastUpdatedEpochMillis
        }
    }

    /**
     * 자동 연결용 Wi-Fi 네트워크 정보 조회
     */
    suspend fun getAutoConnectNetworkConfig(): com.inik.camcon.domain.model.AutoConnectNetworkConfig? {
        val preferences = context.ptpipDataStore.data.first()
        val ssid = preferences[AUTO_CONNECT_SSID]
        if (ssid.isNullOrBlank()) {
            return null
        }
        return com.inik.camcon.domain.model.AutoConnectNetworkConfig(
            ssid = ssid,
            passphrase = preferences[AUTO_CONNECT_PASSPHRASE],
            securityType = preferences[AUTO_CONNECT_SECURITY],
            isHidden = preferences[AUTO_CONNECT_HIDDEN] ?: false,
            bssid = preferences[AUTO_CONNECT_BSSID],
            lastUpdatedEpochMillis = preferences[AUTO_CONNECT_UPDATED_AT]
                ?: System.currentTimeMillis()
        )
    }

    /**
     * 자동 연결 네트워크 구성의 타임스탬프만 현재값으로 업데이트
     */
    suspend fun updateAutoConnectNetworkTimestamp(newTimestamp: Long = System.currentTimeMillis()) {
        context.ptpipDataStore.edit { preferences ->
            preferences[AUTO_CONNECT_UPDATED_AT] = newTimestamp
        }
    }

    /**
     * 마지막 연결된 카메라 정보 조회
     */
    suspend fun getLastConnectedCameraInfo(): Pair<String, String?>? {
        val preferences = context.ptpipDataStore.data.first()
        val ip = preferences[LAST_CONNECTED_IP]
        if (ip.isNullOrBlank()) {
            return null
        }
        val name = preferences[LAST_CONNECTED_NAME]
        return ip to name
    }

    /**
     * 자동 연결 네트워크 정보 초기화
     */
    suspend fun clearAutoConnectNetworkConfig() {
        context.ptpipDataStore.edit { preferences ->
            preferences.remove(AUTO_CONNECT_SSID)
            preferences.remove(AUTO_CONNECT_SECURITY)
            preferences.remove(AUTO_CONNECT_PASSPHRASE)
            preferences.remove(AUTO_CONNECT_HIDDEN)
            preferences.remove(AUTO_CONNECT_BSSID)
            preferences.remove(AUTO_CONNECT_UPDATED_AT)
        }
    }

    /**
     * 모든 PTPIP 설정 초기화
     */
    suspend fun clearAllSettings() {
        context.ptpipDataStore.edit { preferences ->
            preferences.clear()
        }
    }
}