package com.inik.camcon.data.service

import android.util.Log
import com.inik.camcon.data.datasource.local.PtpipPreferencesDataSource
import com.inik.camcon.domain.model.AutoConnectNetworkConfig
import com.inik.camcon.data.network.ptpip.wifi.WifiNetworkHelper
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
        Log.d(TAG, "========================================")
        Log.d(TAG, "🔧 자동 연결 활성화 시작")
        Log.d(TAG, "  - SSID: ${config.ssid}")
        Log.d(TAG, "========================================")

        if (!wifiNetworkHelper.isNetworkSuggestionSupported()) {
            Log.w(TAG, "❌ Network Suggestion 미지원 (Android ${android.os.Build.VERSION.SDK_INT})")
            return false to "Android 10 이상에서만 자동 연결이 가능합니다."
        }

        Log.d(TAG, "✅ Network Suggestion 지원됨")
        Log.d(TAG, "🔄 Suggestion 등록 시도 중...")

        val suggestionResult = wifiNetworkHelper.registerNetworkSuggestion(config)
        if (!suggestionResult.success) {
            Log.w(TAG, "❌ 자동 연결 등록 실패: ${suggestionResult.message}")
            return false to suggestionResult.message
        }

        Log.d(TAG, "✅ Suggestion 등록 성공")
        Log.d(TAG, "💾 자동 연결 설정 저장 중...")

        val bssidToPersist = config.bssid ?: wifiNetworkHelper.getCurrentBssid()
        val updatedConfig = config.copy(
            lastUpdatedEpochMillis = System.currentTimeMillis(),
            bssid = bssidToPersist
        )

        Log.d(TAG, "  - 저장할 BSSID: $bssidToPersist")
        Log.d(TAG, "  - 타임스탬프: ${updatedConfig.lastUpdatedEpochMillis}")

        preferencesDataSource.saveAutoConnectNetworkConfig(updatedConfig)
        preferencesDataSource.setAutoConnectEnabled(true)

        Log.d(TAG, "✅✅✅ 자동 연결 활성화 완료! ✅✅✅")
        Log.d(TAG, "========================================")
        return true to suggestionResult.message
    }

    suspend fun disableAutoConnect() {
        Log.d(TAG, "========================================")
        Log.d(TAG, "🔧 자동 연결 비활성화 시작")

        val config = preferencesDataSource.getAutoConnectNetworkConfig()
        if (config != null) {
            Log.d(TAG, "  - 기존 설정 발견: ${config.ssid}")
            Log.d(TAG, "🔄 Suggestion 제거 시도 중...")
            val result = wifiNetworkHelper.removeNetworkSuggestion(config)
            Log.d(TAG, "  - 제거 결과: ${if (result.success) "성공" else "실패"} - ${result.message}")
        } else {
            Log.d(TAG, "  - 저장된 자동 연결 설정 없음")
        }

        Log.d(TAG, "💾 자동 연결 설정 삭제 중...")
        preferencesDataSource.clearAutoConnectNetworkConfig()
        preferencesDataSource.setAutoConnectEnabled(false)

        Log.d(TAG, "✅ 자동 연결 비활성화 완료")
        Log.d(TAG, "========================================")
    }

    suspend fun getStoredConfig(): AutoConnectNetworkConfig? {
        return preferencesDataSource.getAutoConnectNetworkConfig()
    }

    suspend fun isEnabled(): Boolean {
        return preferencesDataSource.isAutoConnectEnabledNow()
    }
}
