package com.inik.camcon.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Log
import com.inik.camcon.CamCon
import com.inik.camcon.data.network.ptpip.wifi.WifiNetworkHelper
import com.inik.camcon.data.service.AutoConnectForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * WiFi Suggestion 브로드캐스트 리시버
 *
 * 주의: 앱이 종료된 상태에서 받을 수 있는 브로드캐스트는 매우 제한적입니다.
 * - WiFi Suggestion 연결 시: ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION (제한적)
 * - WiFi 상태 변화: STATE_CHANGE, supplicant.CONNECTION_CHANGE (Android 버전별 차이)
 *
 * 가장 안정적인 방법은 Application의 NetworkCallback을 사용하는 것입니다.
 */
class WifiSuggestionBroadcastReceiver : BroadcastReceiver() {

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        Log.d(TAG, "========================================")
        Log.d(TAG, "📡 브로드캐스트 수신! (앱 종료 상태)")
        Log.d(TAG, "  - Action: $action")
        Log.d(TAG, "  - Time: ${System.currentTimeMillis()}")
        Log.d(TAG, "========================================")

        // Application에서 의존성 가져오기
        val application = context.applicationContext as? CamCon
        if (application == null) {
            Log.e(TAG, "❌ Application을 가져올 수 없습니다")
            return
        }

        // 인텐트 정보 로깅
        intent.extras?.let { extras ->
            Log.d(TAG, "Extras:")
            for (key in extras.keySet()) {
                Log.d(TAG, "  $key = ${extras.get(key)}")
            }
        }

        when (action) {
            // 자동 연결 성공 (커스텀 브로드캐스트)
            WifiNetworkHelper.ACTION_AUTO_CONNECT_SUCCESS -> {
                val ssid = intent.getStringExtra(WifiNetworkHelper.EXTRA_AUTO_CONNECT_SSID)
                val cameraIp = intent.getStringExtra(WifiNetworkHelper.EXTRA_CAMERA_IP)
                Log.i(TAG, "✅ 자동 연결 성공! SSID: $ssid, IP: $cameraIp")
                return@onReceive
            }

            // WiFi 네트워크 상태 변화 (실제로 연결/해제될 때)
            "android.net.wifi.STATE_CHANGE" -> {
                Log.d(TAG, "📡 WiFi 네트워크 상태 변화")
                handleNetworkChangeAsync(context, application)
                return@onReceive
            }

            // WiFi Supplicant 연결 변화
            "android.net.wifi.supplicant.CONNECTION_CHANGE" -> {
                Log.d(TAG, "📡 WiFi Supplicant 연결 변화")
                handleNetworkChangeAsync(context, application)
                return@onReceive
            }

            // WiFi Suggestion 연결 (시스템 브로드캐스트 - 잘 안 옴)
            WifiManager.ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION -> {
                Log.d(TAG, "📡 WiFi Suggestion 연결 (시스템)")
                handleNetworkChangeAsync(context, application)
                return@onReceive
            }

            // 커스텀 트리거
            WifiNetworkHelper.ACTION_AUTO_CONNECT_TRIGGER -> {
                val targetSsid = intent.getStringExtra(WifiNetworkHelper.EXTRA_AUTO_CONNECT_SSID)
                Log.d(TAG, "📡 커스텀 트리거 (ssid=$targetSsid)")
                handleNetworkChangeAsync(context, application)
                return@onReceive
            }

            else -> {
                Log.d(TAG, "❌ 알 수 없는 액션: $action")
            }
        }
    }

    /**
     * 네트워크 변화 처리 (비동기 - goAsync 사용)
     */
    private fun handleNetworkChangeAsync(context: Context, application: CamCon) {
        val pendingResult = goAsync()

        receiverScope.launch {
            try {
                Log.d(TAG, "🔍 자동 연결 조건 확인 시작")

                // Application에서 preferencesDataSource 가져오기
                val wifiNetworkHelper = application.wifiNetworkHelper
                val preferencesDataSource = application.preferencesDataSource

                // 1. 자동 연결이 활성화되어 있는지 확인
                val isAutoConnectEnabled = preferencesDataSource.isAutoConnectEnabledNow()
                if (!isAutoConnectEnabled) {
                    Log.d(TAG, "자동 연결이 비활성화되어 있음")
                    return@launch
                }

                // 2. WiFi 연결 확인
                val connectivityManager =
                    context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val network = connectivityManager?.activeNetwork
                val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }

                if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) {
                    Log.d(TAG, "WiFi에 연결되어 있지 않음")
                    return@launch
                }

                // 3. 현재 연결된 SSID 확인
                val currentSSID = wifiNetworkHelper.getCurrentSSID()
                if (currentSSID.isNullOrEmpty()) {
                    Log.d(TAG, "현재 WiFi SSID를 가져올 수 없음")
                    return@launch
                }

                // 4. 저장된 자동 연결 설정 확인
                val autoConnectConfig = preferencesDataSource.getAutoConnectNetworkConfig()
                if (autoConnectConfig == null) {
                    Log.d(TAG, "저장된 자동 연결 설정 없음")
                    return@launch
                }

                // 5. SSID 일치 확인
                if (currentSSID != autoConnectConfig.ssid) {
                    Log.d(TAG, "SSID 불일치: 현재=$currentSSID, 설정=${autoConnectConfig.ssid}")
                    return@launch
                }

                // 6. 카메라 AP 확인
                val isCameraAP = wifiNetworkHelper.isConnectedToCameraAP()
                if (!isCameraAP) {
                    Log.d(TAG, "카메라 AP가 아님: $currentSSID")
                    return@launch
                }

                Log.d(TAG, "========================================")
                Log.d(TAG, "✅✅✅ 자동 연결 조건 충족! (브로드캐스트) ✅✅✅")
                Log.d(TAG, "  - SSID: $currentSSID")
                Log.d(TAG, "  - 카메라 AP: true")
                Log.d(TAG, "========================================")

                // 7. AutoConnectForegroundService 시작
                Log.d(TAG, "🚀 AutoConnectForegroundService 시작 (브로드캐스트)")
                AutoConnectForegroundService.start(context.applicationContext, currentSSID)

                Log.d(TAG, "✅ 자동 연결 처리 완료")
            } catch (e: Exception) {
                Log.e(TAG, "❌ 자동 연결 처리 중 오류", e)
            } finally {
                // 반드시 finish 호출 (한 번만!)
                try {
                    pendingResult.finish()
                } catch (e: Exception) {
                    Log.w(TAG, "pendingResult.finish() 호출 실패: ${e.message}")
                }
            }
        }
    }

    companion object {
        private const val TAG = "WifiSuggestionReceiver"
    }
}
