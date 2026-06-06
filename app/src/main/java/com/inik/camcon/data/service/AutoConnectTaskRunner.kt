package com.inik.camcon.data.service

import android.util.Log
import com.inik.camcon.data.datasource.ptpip.PtpipDataSource
import com.inik.camcon.data.network.ptpip.wifi.WifiNetworkHelper
import com.inik.camcon.di.IoDispatcher
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.PtpipConnectionState
import com.inik.camcon.domain.model.WifiNetworkState
import com.inik.camcon.utils.LogMask
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoConnectTaskRunner @Inject constructor(
    private val ptpipDataSource: PtpipDataSource,
    private val autoConnectManager: AutoConnectManager,
    private val wifiNetworkHelper: WifiNetworkHelper,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    companion object {
        private const val TAG = "AutoConnectTaskRunner"
        const val EXTRA_SSID = "extra_auto_connect_ssid"
    }

    // 중복 실행 방지를 위한 플래그 (check-then-set race 방지를 위해 CAS 사용)
    private val isRunning = AtomicBoolean(false)

    suspend fun handlePostConnection(ssid: String?) = withContext(ioDispatcher) {
        // 중복 실행 방지 (compareAndSet으로 진입을 원자적으로 직렬화)
        if (!isRunning.compareAndSet(false, true)) {
            Log.d(TAG, "⚠️ 이미 자동 연결 처리 실행 중 - 중복 요청 무시")
            return@withContext
        }

        try {
            handlePostConnectionInternal(ssid)
        } finally {
            isRunning.set(false)
        }
    }

    private suspend fun handlePostConnectionInternal(ssid: String?) {
        Log.d(TAG, "자동 연결 처리 시작 - 요청 SSID: ${LogMask.ssid(ssid)}")

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

        Log.d(
            TAG,
            "저장된 자동 연결 구성 확인 - SSID: ${LogMask.ssid(config.ssid)}, BSSID: ${
                LogMask.bssid(config.bssid)
            }, Hidden: ${config.isHidden}, Security: ${config.securityType}"
        )

        Log.d(TAG, "네트워크 연결 대기 중...")
        val networkState = awaitConnectedState(config.ssid)
        if (networkState == null) {
            Log.w(
                TAG,
                "대상 SSID(${LogMask.ssid(config.ssid)}) 연결 확인 실패 - 현재 SSID: ${
                    LogMask.ssid(wifiNetworkHelper.getCurrentSSID())
                }, WiFi 연결: ${wifiNetworkHelper.isWifiConnected()}"
            )
            return
        }

        Log.d(
            TAG,
            "네트워크 연결 확인 - SSID: ${LogMask.ssid(networkState.ssid)}, 카메라 AP: ${
                networkState.isConnectedToCameraAP
            }, 카메라 IP: ${LogMask.id(networkState.detectedCameraIP)}"
        )

        val cameraIp = networkState.detectedCameraIP ?: wifiNetworkHelper
            .detectCameraIPFromCurrentNetwork()
        if (cameraIp.isNullOrBlank()) {
            Log.w(TAG, "❌ 카메라 IP를 감지하지 못했습니다")
            return
        }

        Log.d(TAG, "카메라 IP 감지 성공: ${LogMask.id(cameraIp)} - 연결 시도 중")

        // 네트워크 바인딩 (중요!)
        val bindSuccess = wifiNetworkHelper.bindToCurrentNetwork()
        if (!bindSuccess) {
            Log.w(TAG, "네트워크 바인딩 실패 - 연결 시도는 계속 진행")
        } else {
            Log.d(TAG, "네트워크 바인딩 성공")
        }

        val camera = PtpipCamera(
            ipAddress = cameraIp,
            port = 15740,
            name = "${config.ssid} (자동 연결)",
            isOnline = true
        )

        val success = ptpipDataSource.connectToCamera(camera, forceApMode = true)
        if (!success) {
            Log.w(TAG, "자동 카메라 연결 실패 - 카메라 IP: ${LogMask.id(cameraIp)}, 포트: 15740")
            wifiNetworkHelper.unbindFromCurrentNetwork() // 네트워크 바인딩 해제
            return
        }

        Log.d(
            TAG,
            "자동 카메라 연결 성공 - 카메라 IP: ${LogMask.id(cameraIp)}, SSID: ${
                LogMask.ssid(config.ssid)
            } (WiFi 바인딩 유지)"
        )

        wifiNetworkHelper.sendAutoConnectSuccessBroadcast(
            ssid = config.ssid,
            cameraIp = cameraIp
        )
        // TODO: 필요 시 이벤트 리스너 시작 호출
    }

    private suspend fun awaitConnectedState(
        targetSsid: String,
        maxAttempts: Int = 15  // 15초로 증가 (WiFi Suggestion 연결 대기)
    ): WifiNetworkState? {
        Log.d(
            TAG,
            "네트워크 연결 상태 확인 중 (최대 ${maxAttempts}초) - 대상 SSID: ${LogMask.ssid(targetSsid)}"
        )

        repeat(maxAttempts) { attempt ->
            // WiFi 연결 상태만 확인 (SSID는 가져올 수 없을 수 있음)
            val isWifiConnected = wifiNetworkHelper.isWifiConnected()
            val isCameraAP = wifiNetworkHelper.isConnectedToCameraAP()

            // WiFi에 연결되어 있으면 성공으로 간주 (SSID 비교 불가)
            if (isWifiConnected) {
                val detectedIp = wifiNetworkHelper.detectCameraIPFromCurrentNetwork()
                Log.d(
                    TAG,
                    "WiFi 연결 확인됨 (시도 ${attempt + 1}) - 저장된 SSID(${
                        LogMask.ssid(targetSsid)
                    }) 사용, 감지된 카메라 IP: ${LogMask.id(detectedIp)}"
                )

                return WifiNetworkState(
                    isConnected = true,
                    isConnectedToCameraAP = isCameraAP,
                    ssid = targetSsid, // 저장된 SSID 사용
                    detectedCameraIP = detectedIp
                )
            }
            delay(1000)
        }

        Log.w(
            TAG,
            "${maxAttempts}초 동안 WiFi 연결 대기 실패 - WiFi 연결 상태: ${wifiNetworkHelper.isWifiConnected()}"
        )
        return null
    }
}
