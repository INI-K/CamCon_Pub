package com.inik.camcon.data.datasource.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
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
        private val WIFI_STA_MODE = booleanPreferencesKey("wifi_sta_mode")
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
     * Wi-Fi STA 모드 활성화 여부 (기본값: true - STA 모드 우선)
     */
    val isWifiStaModeEnabled: Flow<Boolean> = context.ptpipDataStore.data
        .map { preferences ->
            preferences[WIFI_STA_MODE] ?: true
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
     * Wi-Fi STA 모드 활성화/비활성화
     * true: 동일 네트워크에서 카메라 검색 (STA 모드)
     * false: 카메라 AP 모드 (레거시 지원용)
     */
    suspend fun setWifiStaModeEnabled(enabled: Boolean) {
        context.ptpipDataStore.edit { preferences ->
            preferences[WIFI_STA_MODE] = enabled
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