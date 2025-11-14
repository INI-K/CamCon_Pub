package com.inik.camcon.data.service

import android.util.Log
import com.inik.camcon.data.datasource.ptpip.PtpipDataSource
import com.inik.camcon.data.network.ptpip.wifi.WifiNetworkHelper
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.PtpipConnectionState
import com.inik.camcon.domain.model.WifiNetworkState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoConnectTaskRunner @Inject constructor(
    private val ptpipDataSource: PtpipDataSource,
    private val autoConnectManager: AutoConnectManager,
    private val wifiNetworkHelper: WifiNetworkHelper
) {
    companion object {
        private const val TAG = "AutoConnectTaskRunner"
        const val EXTRA_SSID = "extra_auto_connect_ssid"
    }

    // 중복 실행 방지를 위한 플래그
    @Volatile
    private var isRunning = false

    suspend fun handlePostConnection(ssid: String?) = withContext(Dispatchers.IO) {
        // 중복 실행 방지
        if (isRunning) {
            Log.d(TAG, "⚠️ 이미 자동 연결 처리 실행 중 - 중복 요청 무시")
            return@withContext
        }

        isRunning = true
        try {
            handlePostConnectionInternal(ssid)
        } finally {
            isRunning = false
        }
    }

    private suspend fun handlePostConnectionInternal(ssid: String?) {
        Log.d(TAG, "========================================")
        Log.d(TAG, "🚀 자동 연결 처리 시작")
        Log.d(TAG, "  - 요청 SSID: $ssid")
        Log.d(TAG, "========================================")

        // 이미 카메라에 연결되어 있으면 건너뛰기
        val currentState = ptpipDataSource.connectionState.value
        if (currentState == PtpipConnectionState.CONNECTED) {
            Log.d(TAG, "✅ 카메라가 이미 연결되어 있습니다 - 자동 연결 건너뛰기")
            return
        }
        if (currentState == PtpipConnectionState.CONNECTING) {
            Log.d(TAG, "⏳ 카메라 연결 진행 중 - 자동 연결 건너뛰기")
            return
        }

        val config = autoConnectManager.getStoredConfig()
        if (config == null) {
            Log.w(TAG, "❌ 저장된 자동 연결 설정이 없습니다")
            return
        }

        Log.d(TAG, "✅ 저장된 자동 연결 구성 확인:")
        Log.d(TAG, "  - SSID: ${config.ssid}")
        Log.d(TAG, "  - BSSID: ${config.bssid}")
        Log.d(TAG, "  - Hidden: ${config.isHidden}")
        Log.d(TAG, "  - Security: ${config.securityType}")
        Log.d(TAG, "  - 마지막 업데이트: ${config.lastUpdatedEpochMillis}")

        Log.d(TAG, "⏳ 네트워크 연결 대기 중...")
        val networkState = awaitConnectedState(config.ssid)
        if (networkState == null) {
            Log.w(TAG, "❌ 대상 SSID(${config.ssid})에 연결된 상태를 확인하지 못했습니다")
            Log.w(TAG, "  - 현재 연결된 SSID: ${wifiNetworkHelper.getCurrentSSID()}")
            Log.w(TAG, "  - WiFi 연결 상태: ${wifiNetworkHelper.isWifiConnected()}")
            return
        }

        Log.d(TAG, "✅ 네트워크 연결 확인:")
        Log.d(TAG, "  - SSID: ${networkState.ssid}")
        Log.d(TAG, "  - 카메라 AP: ${networkState.isConnectedToCameraAP}")
        Log.d(TAG, "  - 감지된 카메라 IP: ${networkState.detectedCameraIP}")

        val cameraIp = networkState.detectedCameraIP ?: wifiNetworkHelper
            .detectCameraIPFromCurrentNetwork()
        if (cameraIp.isNullOrBlank()) {
            Log.w(TAG, "❌ 카메라 IP를 감지하지 못했습니다")
            return
        }

        Log.d(TAG, "📡 카메라 IP 감지 성공: $cameraIp")
        Log.d(TAG, "🔌 카메라 연결 시도 중...")

        // 네트워크 바인딩 (중요!)
        Log.d(TAG, "📡 WiFi 네트워크에 프로세스 바인딩 중...")
        val bindSuccess = wifiNetworkHelper.bindToCurrentNetwork()
        if (!bindSuccess) {
            Log.w(TAG, "⚠️ 네트워크 바인딩 실패 - 연결 시도는 계속 진행")
        } else {
            Log.d(TAG, "✅ 네트워크 바인딩 성공")
        }

        val camera = PtpipCamera(
            ipAddress = cameraIp,
            port = 15740,
            name = "${config.ssid} (자동 연결)",
            isOnline = true
        )

        val success = ptpipDataSource.connectToCamera(camera, forceApMode = true)
        if (!success) {
            Log.w(TAG, "❌ 자동 카메라 연결 실패")
            Log.w(TAG, "  - 카메라 IP: $cameraIp")
            Log.w(TAG, "  - 포트: 15740")
            wifiNetworkHelper.unbindFromCurrentNetwork() // 네트워크 바인딩 해제
            return
        }

        Log.d(TAG, "✅✅✅ 자동 카메라 연결 성공! ✅✅✅")
        Log.d(TAG, "  - 카메라 IP: $cameraIp")
        Log.d(TAG, "  - SSID: ${config.ssid}")
        Log.d(TAG, "  ✅ WiFi 네트워크 바인딩 유지 (카메라 통신용)")

        Log.d(TAG, "📢 자동 연결 성공 브로드캐스트 전송 중...")
        wifiNetworkHelper.sendAutoConnectSuccessBroadcast(
            ssid = config.ssid,
            cameraIp = cameraIp
        )

        Log.d(TAG, "✅ 이벤트 리스너 준비 완료")
        Log.d(TAG, "========================================")
        // TODO: Invoke event listener start if needed
    }

    private suspend fun awaitConnectedState(
        targetSsid: String,
        maxAttempts: Int = 15  // 15초로 증가 (WiFi Suggestion 연결 대기)
    ): WifiNetworkState? {
        Log.d(TAG, "⏳ 네트워크 연결 상태 확인 중 (최대 ${maxAttempts}초 대기)...")
        Log.d(TAG, "  📌 대상 SSID: $targetSsid (WiFi Suggestion 연결 대기 중)")

        repeat(maxAttempts) { attempt ->
            // WiFi 연결 상태만 확인 (SSID는 가져올 수 없을 수 있음)
            val isWifiConnected = wifiNetworkHelper.isWifiConnected()
            val isCameraAP = wifiNetworkHelper.isConnectedToCameraAP()

            Log.d(
                TAG,
                "  [${attempt + 1}/$maxAttempts] WiFi 연결: $isWifiConnected, 카메라 AP: $isCameraAP"
            )

            // WiFi에 연결되어 있으면 성공으로 간주 (SSID 비교 불가)
            if (isWifiConnected) {
                Log.d(TAG, "✅ WiFi 연결 확인됨 (시도 ${attempt + 1})")
                Log.d(TAG, "  📌 Android 13+ 보안 정책으로 SSID 확인 불가")
                Log.d(TAG, "  📌 저장된 SSID($targetSsid) 사용")

                val detectedIp = wifiNetworkHelper.detectCameraIPFromCurrentNetwork()
                Log.d(TAG, "  - 감지된 카메라 IP: $detectedIp")

                return WifiNetworkState(
                    isConnected = true,
                    isConnectedToCameraAP = isCameraAP,
                    ssid = targetSsid, // 저장된 SSID 사용
                    detectedCameraIP = detectedIp
                )
            }
            delay(1000)
        }

        Log.w(TAG, "❌ ${maxAttempts}초 동안 WiFi 연결 대기 실패")
        Log.w(TAG, "  - WiFi 연결 상태: ${wifiNetworkHelper.isWifiConnected()}")
        return null
    }
}
