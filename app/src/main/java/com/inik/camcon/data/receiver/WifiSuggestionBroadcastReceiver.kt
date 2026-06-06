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
import com.inik.camcon.utils.LogMask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        Log.d(TAG, "브로드캐스트 수신 (앱 종료 상태): $action")

        // Application에서 의존성 가져오기
        val application = context.applicationContext as? CamCon
        if (application == null) {
            Log.e(TAG, "Application을 가져올 수 없습니다")
            return
        }

        when (action) {
            // 자동 연결 성공 (커스텀 브로드캐스트)
            WifiNetworkHelper.ACTION_AUTO_CONNECT_SUCCESS -> {
                val ssid = intent.getStringExtra(WifiNetworkHelper.EXTRA_AUTO_CONNECT_SSID)
                Log.i(TAG, "자동 연결 성공: SSID=${LogMask.ssid(ssid)}")
                return@onReceive
            }

            // WiFi 네트워크 상태 변화 (실제로 연결/해제될 때)
            "android.net.wifi.STATE_CHANGE" -> {
                handleNetworkChangeAsync(context, application)
                return@onReceive
            }

            // WiFi Supplicant 연결 변화
            "android.net.wifi.supplicant.CONNECTION_CHANGE" -> {
                handleNetworkChangeAsync(context, application)
                return@onReceive
            }

            // WiFi Suggestion 연결 (시스템 브로드캐스트 - 잘 안 옴)
            WifiManager.ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION -> {
                handleNetworkChangeAsync(context, application)
                return@onReceive
            }

            // 커스텀 트리거
            WifiNetworkHelper.ACTION_AUTO_CONNECT_TRIGGER -> {
                handleNetworkChangeAsync(context, application)
                return@onReceive
            }

            else -> {
                Log.d(TAG, "알 수 없는 액션: $action")
            }
        }
    }

    /**
     * 네트워크 변화 처리 (비동기 - goAsync 사용)
     *
     * 수정사항 (C-2 해결):
     * - receiverScope 인스턴스 필드 제거
     * - 로컬 CoroutineScope 생성 (함수 내 생명주기)
     * - 5초 타임아웃으로 pendingResult.finish() 호출 보장
     */
    private fun handleNetworkChangeAsync(context: Context, application: CamCon) {
        val pendingResult = goAsync()

        // 로컬 스코프: 이 함수 내에서만 유효
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        scope.launch {
            try {
                // 5초 타임아웃: goAsync() 제한 시간 내 완료 보장
                kotlinx.coroutines.withTimeoutOrNull(5000) {
                    // Application에서 preferencesDataSource 가져오기
                    val wifiNetworkHelper = application.wifiNetworkHelper
                    val preferencesDataSource = application.preferencesDataSource

                    // 1. 자동 연결이 활성화되어 있는지 확인
                    val isAutoConnectEnabled = preferencesDataSource.isAutoConnectEnabledNow()
                    if (!isAutoConnectEnabled) {
                        Log.d(TAG, "자동 연결이 비활성화되어 있음")
                        return@withTimeoutOrNull
                    }

                    // 2. WiFi 연결 확인
                    val connectivityManager =
                        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    val network = connectivityManager?.activeNetwork
                    val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }

                    if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) {
                        Log.d(TAG, "WiFi에 연결되어 있지 않음")
                        return@withTimeoutOrNull
                    }

                    // 3. 현재 연결된 SSID 확인
                    val currentSSID = wifiNetworkHelper.getCurrentSSID()
                    if (currentSSID.isNullOrEmpty()) {
                        Log.d(TAG, "현재 WiFi SSID를 가져올 수 없음")
                        return@withTimeoutOrNull
                    }

                    // 4. 저장된 자동 연결 설정 확인
                    val autoConnectConfig = preferencesDataSource.getAutoConnectNetworkConfig()
                    if (autoConnectConfig == null) {
                        Log.d(TAG, "저장된 자동 연결 설정 없음")
                        return@withTimeoutOrNull
                    }

                    // 5. SSID 일치 확인
                    if (currentSSID != autoConnectConfig.ssid) {
                        Log.d(TAG, "SSID 불일치: 현재=${LogMask.ssid(currentSSID)}, 설정=${LogMask.ssid(autoConnectConfig.ssid)}")
                        return@withTimeoutOrNull
                    }

                    // 6. 카메라 AP 확인
                    val isCameraAP = wifiNetworkHelper.isConnectedToCameraAP()
                    if (!isCameraAP) {
                        Log.d(TAG, "카메라 AP가 아님: ${LogMask.ssid(currentSSID)}")
                        return@withTimeoutOrNull
                    }

                    Log.d(TAG, "자동 연결 조건 충족 (브로드캐스트): SSID=${LogMask.ssid(currentSSID)}")

                    // 7. AutoConnectForegroundService 시작
                    AutoConnectForegroundService.start(context.applicationContext, currentSSID)
                }
            } catch (e: Exception) {
                Log.e(TAG, "자동 연결 처리 중 오류", e)
            } finally {
                // 반드시 finish 호출 (한 번만!) - scope 정리 전
                try {
                    pendingResult.finish()
                } catch (e: Exception) {
                    Log.w(TAG, "pendingResult.finish() 호출 실패: ${e.message}")
                }
                // scope 정리
                scope.cancel()
            }
        }
    }

    companion object {
        private const val TAG = "WifiSuggestionReceiver"
    }
}
