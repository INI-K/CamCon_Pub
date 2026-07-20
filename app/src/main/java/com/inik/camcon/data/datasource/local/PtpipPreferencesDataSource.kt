package com.inik.camcon.data.datasource.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.inik.camcon.domain.model.SavedWifiCredential
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
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
        private const val TAG = "PtpipPreferencesDS"

        // 패스프레이즈 전용 암호화 저장소(EncryptedSharedPreferences). 평문 DataStore와 분리.
        private const val SECURE_FILE_NAME = "ptpip_secure_prefs"
        private const val SECURE_AUTO_CONNECT_PASSPHRASE = "auto_connect_passphrase"
        private const val SECURE_CRED_PREFIX = "cred_pass_"

        private val PTPIP_ENABLED = booleanPreferencesKey("ptpip_enabled")
        private val LAST_CONNECTED_IP = stringPreferencesKey("last_connected_ip")
        private val LAST_CONNECTED_NAME = stringPreferencesKey("last_connected_name")
        private val CONNECTION_TIMEOUT = intPreferencesKey("connection_timeout")
        private val DISCOVERY_TIMEOUT = intPreferencesKey("discovery_timeout")
        private val PTPIP_PORT = intPreferencesKey("ptpip_port")
        private val AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        private val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        private val AUTO_CONNECT_SSID = stringPreferencesKey("auto_connect_ssid")
        private val AUTO_CONNECT_SECURITY = stringPreferencesKey("auto_connect_security")
        private val AUTO_CONNECT_PASSPHRASE = stringPreferencesKey("auto_connect_passphrase")
        private val AUTO_CONNECT_HIDDEN = booleanPreferencesKey("auto_connect_hidden")
        private val AUTO_CONNECT_BSSID = stringPreferencesKey("auto_connect_bssid")
        private val AUTO_CONNECT_UPDATED_AT = longPreferencesKey("auto_connect_updated_at")
        private val SAVED_WIFI_CREDENTIALS = stringPreferencesKey("saved_wifi_credentials")
    }

    // ── Wi-Fi 패스프레이즈 암호화 저장소 ──
    // 패스프레이즈는 평문 DataStore가 아니라 EncryptedSharedPreferences에 저장한다.
    // 초기화 실패(널) 시에만 기존 평문 DataStore 폴백을 유지한다(회귀 방지).
    private val securePrefs: SharedPreferences? by lazy { createSecurePrefs() }
    private val migrationDone = AtomicBoolean(false)

    private fun createSecurePrefs(): SharedPreferences? = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            SECURE_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e(TAG, "PTPIP 암호화 저장소 초기화 실패 - 패스프레이즈 평문 폴백 유지", e)
        null
    }

    /** 읽기 우선순위: 암호화 저장소 → (미마이그레이션) 평문 DataStore 값 */
    private fun readAutoConnectPassphrase(plaintextFallback: String?): String? {
        securePrefs?.getString(SECURE_AUTO_CONNECT_PASSPHRASE, null)?.let { return it }
        return plaintextFallback?.ifBlank { null }
    }

    private fun readCredPassphrase(ssid: String, plaintextFallback: String): String {
        securePrefs?.getString(SECURE_CRED_PREFIX + ssid, null)?.let { return it }
        return plaintextFallback
    }

    /** 반환값: 암호화 저장소에 저장 성공 여부(false면 DataStore 평문 폴백 필요) */
    private fun writeAutoConnectPassphraseSecure(passphrase: String?): Boolean {
        val sp = securePrefs ?: return false
        sp.edit().apply {
            if (passphrase.isNullOrEmpty()) remove(SECURE_AUTO_CONNECT_PASSPHRASE)
            else putString(SECURE_AUTO_CONNECT_PASSPHRASE, passphrase)
        }.apply()
        return true
    }

    private fun writeCredPassphraseSecure(ssid: String, passphrase: String): Boolean {
        val sp = securePrefs ?: return false
        sp.edit().putString(SECURE_CRED_PREFIX + ssid, passphrase).apply()
        return true
    }

    private fun removeCredPassphraseSecure(ssid: String) {
        securePrefs?.edit()?.remove(SECURE_CRED_PREFIX + ssid)?.apply()
    }

    private fun hydrateCredentials(creds: List<SavedWifiCredential>): List<SavedWifiCredential> =
        creds.map { it.copy(passphrase = readCredPassphrase(it.ssid, it.passphrase)) }

    /**
     * 앱 이전 버전이 평문 DataStore에 남긴 패스프레이즈를 암호화 저장소로 1회 이전하고
     * DataStore 평문 값은 삭제한다. 암호화 저장소 불가(널) 시 이전하지 않는다(평문 유지).
     */
    private suspend fun migratePlaintextPassphrasesIfNeeded() {
        if (securePrefs == null) return
        if (!migrationDone.compareAndSet(false, true)) return
        try {
            context.ptpipDataStore.edit { preferences ->
                val acPass = preferences[AUTO_CONNECT_PASSPHRASE]
                if (!acPass.isNullOrEmpty()) {
                    writeAutoConnectPassphraseSecure(acPass)
                    preferences.remove(AUTO_CONNECT_PASSPHRASE)
                }
                val json = preferences[SAVED_WIFI_CREDENTIALS]
                if (!json.isNullOrEmpty()) {
                    val creds = parseSavedCredentials(json)
                    var changed = false
                    val sanitized = creds.map { c ->
                        if (c.passphrase.isNotEmpty()) {
                            writeCredPassphraseSecure(c.ssid, c.passphrase)
                            changed = true
                            c.copy(passphrase = "")
                        } else c
                    }
                    if (changed) preferences[SAVED_WIFI_CREDENTIALS] = serializeCredentials(sanitized)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "패스프레이즈 마이그레이션 실패 - 다음 기회에 재시도", e)
            migrationDone.set(false)
        }
    }

    /**
     * PTPIP 기능 활성화 여부
     */
    val isPtpipEnabled: Flow<Boolean> = context.ptpipDataStore.data
        .map { preferences ->
            preferences[PTPIP_ENABLED] ?: false
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
                        passphrase = readAutoConnectPassphrase(preferences[AUTO_CONNECT_PASSPHRASE]),
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
     * 자동 연결을 위한 Wi-Fi 네트워크 정보 저장
     */
    suspend fun saveAutoConnectNetworkConfig(config: com.inik.camcon.domain.model.AutoConnectNetworkConfig) {
        migratePlaintextPassphrasesIfNeeded()
        // 패스프레이즈는 암호화 저장소로. 저장 성공 시 DataStore에는 평문을 남기지 않는다.
        val storedSecurely = writeAutoConnectPassphraseSecure(config.passphrase)
        context.ptpipDataStore.edit { preferences ->
            preferences[AUTO_CONNECT_SSID] = config.ssid
            if (config.securityType.isNullOrEmpty()) {
                preferences.remove(AUTO_CONNECT_SECURITY)
            } else {
                preferences[AUTO_CONNECT_SECURITY] = config.securityType
            }
            if (storedSecurely || config.passphrase.isNullOrEmpty()) {
                preferences.remove(AUTO_CONNECT_PASSPHRASE)
            } else {
                // 암호화 저장소 불가 시에만 기존 동작(평문 폴백) 유지
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
        migratePlaintextPassphrasesIfNeeded()
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
        securePrefs?.edit()?.remove(SECURE_AUTO_CONNECT_PASSPHRASE)?.apply()
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
        securePrefs?.edit()?.clear()?.apply()
        context.ptpipDataStore.edit { preferences ->
            preferences.clear()
        }
    }

    // ── 저장된 Wi-Fi 자격 증명 관리 ──

    /**
     * 저장된 Wi-Fi 자격 증명 목록 (Flow)
     */
    val savedWifiCredentials: Flow<List<SavedWifiCredential>> = context.ptpipDataStore.data
        .map { preferences ->
            val json = preferences[SAVED_WIFI_CREDENTIALS] ?: return@map emptyList()
            hydrateCredentials(parseSavedCredentials(json))
        }

    /**
     * Wi-Fi 연결 성공 시 자격 증명 저장 (SSID 기준 upsert)
     */
    suspend fun saveWifiCredential(credential: SavedWifiCredential) {
        migratePlaintextPassphrasesIfNeeded()
        // 패스프레이즈는 암호화 저장소로. 저장 성공 시 JSON에는 빈 값을 남긴다.
        val securedCred = if (writeCredPassphraseSecure(credential.ssid, credential.passphrase))
            credential.copy(passphrase = "") else credential
        context.ptpipDataStore.edit { preferences ->
            val existing = parseSavedCredentials(preferences[SAVED_WIFI_CREDENTIALS] ?: "[]")
            val updated = existing.filter { it.ssid != credential.ssid } + securedCred
            preferences[SAVED_WIFI_CREDENTIALS] = serializeCredentials(updated)
        }
    }

    /**
     * SSID로 저장된 자격 증명 조회
     */
    suspend fun getSavedWifiCredential(ssid: String): SavedWifiCredential? {
        migratePlaintextPassphrasesIfNeeded()
        val preferences = context.ptpipDataStore.data.first()
        val json = preferences[SAVED_WIFI_CREDENTIALS] ?: return null
        return parseSavedCredentials(json).find { it.ssid == ssid }
            ?.let { it.copy(passphrase = readCredPassphrase(it.ssid, it.passphrase)) }
    }

    /**
     * 저장된 Wi-Fi 자격 증명 삭제
     */
    suspend fun deleteSavedWifiCredential(ssid: String) {
        removeCredPassphraseSecure(ssid)
        context.ptpipDataStore.edit { preferences ->
            val existing = parseSavedCredentials(preferences[SAVED_WIFI_CREDENTIALS] ?: "[]")
            val updated = existing.filter { it.ssid != ssid }
            preferences[SAVED_WIFI_CREDENTIALS] = serializeCredentials(updated)
        }
    }

    private fun parseSavedCredentials(json: String): List<SavedWifiCredential> {
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                SavedWifiCredential(
                    ssid = obj.getString("ssid"),
                    passphrase = obj.getString("passphrase"),
                    security = obj.optString("security", "WPA2"),
                    bssid = if (obj.has("bssid")) obj.getString("bssid") else null,
                    lastConnectedAt = obj.optLong("lastConnectedAt", 0)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeCredentials(credentials: List<SavedWifiCredential>): String {
        val array = JSONArray()
        credentials.forEach { c ->
            array.put(JSONObject().apply {
                put("ssid", c.ssid)
                put("passphrase", c.passphrase)
                put("security", c.security)
                c.bssid?.let { put("bssid", it) }
                put("lastConnectedAt", c.lastConnectedAt)
            })
        }
        return array.toString()
    }
}