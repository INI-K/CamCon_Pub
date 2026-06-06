package com.inik.camcon.data.service

import android.util.Log
import com.inik.camcon.data.datasource.local.PtpipPreferencesDataSource
import com.inik.camcon.domain.model.AutoConnectNetworkConfig
import com.inik.camcon.data.network.ptpip.wifi.WifiNetworkHelper
import com.inik.camcon.utils.LogMask
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoConnectManager @Inject constructor(
    private val preferencesDataSource: PtpipPreferencesDataSource,
    private val wifiNetworkHelper: WifiNetworkHelper
) {
    companion object {
        private const val TAG = "AutoConnectManager"
    }

    suspend fun enableAutoConnect(config: AutoConnectNetworkConfig): Pair<Boolean, String> {
        Log.d(TAG, "자동 연결 활성화 시작: SSID=${LogMask.ssid(config.ssid)}")

        if (!wifiNetworkHelper.isNetworkSuggestionSupported()) {
            Log.w(TAG, "Network Suggestion 미지원 (Android ${android.os.Build.VERSION.SDK_INT})")
            return false to "Android 10 이상에서만 자동 연결이 가능합니다."
        }

        val suggestionResult = wifiNetworkHelper.registerNetworkSuggestion(config)
        if (!suggestionResult.success) {
            Log.w(TAG, "자동 연결 등록 실패: ${suggestionResult.message}")
            return false to suggestionResult.message
        }

        val bssidToPersist = config.bssid ?: wifiNetworkHelper.getCurrentBssid()
        val updatedConfig = config.copy(
            lastUpdatedEpochMillis = System.currentTimeMillis(),
            bssid = bssidToPersist
        )

        preferencesDataSource.saveAutoConnectNetworkConfig(updatedConfig)
        preferencesDataSource.setAutoConnectEnabled(true)

        Log.d(TAG, "자동 연결 활성화 완료: SSID=${LogMask.ssid(config.ssid)}, BSSID=${LogMask.bssid(bssidToPersist)}")
        return true to suggestionResult.message
    }

    suspend fun disableAutoConnect() {
        val config = preferencesDataSource.getAutoConnectNetworkConfig()
        if (config != null) {
            val result = wifiNetworkHelper.removeNetworkSuggestion(config)
            Log.d(TAG, "자동 연결 비활성화 - Suggestion 제거: ${if (result.success) "성공" else "실패"} - ${result.message}")
        }

        preferencesDataSource.clearAutoConnectNetworkConfig()
        preferencesDataSource.setAutoConnectEnabled(false)
        Log.d(TAG, "자동 연결 비활성화 완료")
    }

    suspend fun getStoredConfig(): AutoConnectNetworkConfig? {
        return preferencesDataSource.getAutoConnectNetworkConfig()
    }

    suspend fun isEnabled(): Boolean {
        return preferencesDataSource.isAutoConnectEnabledNow()
    }
}
