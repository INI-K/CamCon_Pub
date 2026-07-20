package com.inik.camcon.data.datasource.ptpip

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.inik.camcon.data.network.ptpip.wifi.WifiNetworkHelper
import com.inik.camcon.data.service.AutoConnectManager
import com.inik.camcon.data.service.AutoConnectTaskRunner
import com.inik.camcon.domain.model.WifiNetworkState
import com.inik.camcon.utils.LogMask
import dagger.Lazy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 자동 연결 브로드캐스트/리시버 조율 협력자 (PtpipDataSource에서 분리).
 *
 * 네트워크 상태 변화(파사드의 networkStateFlow 관찰)를 받아, 저장된 auto-connect 대상 SSID/BSSID와
 * 일치하고 아직 발송하지 않았으면 자동 연결 브로드캐스트를 발송한다. 브로드캐스트를 받은
 * [autoConnectReceiver]는 [AutoConnectTaskRunner]에 후속 연결 처리를 위임한다.
 *
 * `Lazy<AutoConnectTaskRunner>`는 AutoConnectTaskRunner ↔ PtpipDataSource(파사드) 순환 의존을
 * 끊기 위한 것으로, 이 협력자로 그대로 이관한다(무리한 해소 금지 — 순환은 파사드 레벨에 여전히 존재).
 */
internal class PtpipAutoConnectBroadcaster(
    private val context: Context,
    private val wifiHelper: WifiNetworkHelper,
    private val autoConnectManager: AutoConnectManager,
    private val autoConnectTaskRunnerProvider: Lazy<AutoConnectTaskRunner>,
    private val coroutineScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher
) {
    private companion object {
        private const val TAG = "PtpipDataSource"
    }

    private var lastAutoConnectBroadcastSsid: String? = null
    private var lastAutoConnectBroadcastBssid: String? = null

    fun maybeTriggerAutoConnect(networkState: WifiNetworkState) {
        if (!networkState.isConnected) {
            resetAutoConnectBroadcastState()
            return
        }

        if (networkState.ssid.isNullOrBlank()) {
            resetAutoConnectBroadcastState()
            return
        }

        coroutineScope.launch(ioDispatcher) {
            try {
                val isAutoConnectEnabled = autoConnectManager.isEnabled()
                if (!isAutoConnectEnabled) {
                    Log.d(TAG, "자동 연결 비활성화 상태 - 브로드캐스트 건너뜀")
                    resetAutoConnectBroadcastState()
                    return@launch
                }

                val storedConfig = autoConnectManager.getStoredConfig()
                if (storedConfig == null) {
                    Log.d(TAG, "자동 연결 설정이 없어 브로드캐스트 건너뜀")
                    resetAutoConnectBroadcastState()
                    return@launch
                }

                val ssidMatches = networkState.ssid.equals(storedConfig.ssid, ignoreCase = true)
                val currentBssid = wifiHelper.getCurrentBssid()
                val bssidMatches = storedConfig.bssid.isNullOrBlank() ||
                        currentBssid.equals(storedConfig.bssid, ignoreCase = true)

                if (!ssidMatches || !bssidMatches) {
                    Log.d(TAG, "자동 연결 대상 SSID/BSSID 불일치 - 브로드캐스트 생략")
                    resetAutoConnectBroadcastState()
                    return@launch
                }

                val alreadySentForSsid = lastAutoConnectBroadcastSsid.equals(
                    networkState.ssid,
                    ignoreCase = true
                )
                val alreadySentForBssid = when {
                    currentBssid == null -> storedConfig.bssid.isNullOrBlank() &&
                            lastAutoConnectBroadcastBssid.isNullOrBlank()

                    else -> currentBssid.equals(lastAutoConnectBroadcastBssid, ignoreCase = true)
                }

                if (alreadySentForSsid && alreadySentForBssid) {
                    Log.d(TAG, "같은 SSID/BSSID에 대해 이미 자동 연결 브로드캐스트 발송됨")
                    return@launch
                }

                Log.i(TAG, "네트워크 상태 감지 기반 자동 연결 브로드캐스트 발송: ${LogMask.ssid(networkState.ssid)}")
                wifiHelper.sendAutoConnectBroadcast(storedConfig.ssid)
                lastAutoConnectBroadcastSsid = networkState.ssid
                lastAutoConnectBroadcastBssid = currentBssid ?: storedConfig.bssid
            } catch (error: Exception) {
                Log.e(TAG, "자동 연결 브로드캐스트 발송 중 오류", error)
            }
        }
    }

    private fun resetAutoConnectBroadcastState() {
        if (lastAutoConnectBroadcastSsid != null || lastAutoConnectBroadcastBssid != null) {
            Log.d(TAG, "자동 연결 브로드캐스트 상태 초기화")
        }
        lastAutoConnectBroadcastSsid = null
        lastAutoConnectBroadcastBssid = null
    }

    private val autoConnectReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == WifiNetworkHelper.ACTION_AUTO_CONNECT_TRIGGER) {
                val targetSsid = intent.getStringExtra(WifiNetworkHelper.EXTRA_AUTO_CONNECT_SSID)
                    ?: return

                coroutineScope.launch(ioDispatcher) {
                    try {
                        autoConnectTaskRunnerProvider.get().handlePostConnection(targetSsid)
                    } catch (e: Exception) {
                        Log.e(TAG, "자동 연결 처리 중 오류", e)
                    }
                }
            }
        }
    }

    private var autoConnectReceiverRegistered = false

    fun registerAutoConnectReceiver() {
        if (autoConnectReceiverRegistered) return
        val filter = IntentFilter(WifiNetworkHelper.ACTION_AUTO_CONNECT_TRIGGER)
        context.registerReceiver(autoConnectReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        autoConnectReceiverRegistered = true
    }
}
