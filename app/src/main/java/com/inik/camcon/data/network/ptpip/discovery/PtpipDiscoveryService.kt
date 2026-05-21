package com.inik.camcon.data.network.ptpip.discovery

import android.content.Context
import android.util.Log
import com.inik.camcon.data.network.ptpip.wifi.WifiNetworkHelper
import com.inik.camcon.domain.model.CameraEndpoint
import com.inik.camcon.domain.model.ConnectionMethod
import com.inik.camcon.domain.model.EndpointSource
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.utils.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PTP/IP 카메라 검색 진입점.
 *
 * 검색 전략은 [ConnectionMethod]에 따라 분기:
 * - [ConnectionMethod.AP]: 게이트웨이 IP를 카메라로 추정 (빠르고 결정적)
 * - [ConnectionMethod.STA_ROUTER] / [ConnectionMethod.STA_PHONE_HOTSPOT]:
 *   mDNS(NsdManager)로 같은 서브넷의 카메라를 광고로부터 발견
 *
 * 모든 경로의 결과는 [CameraEndpoint] 리스트로 통일한다. 호출자가 [PtpipCamera]로 변환.
 */
@Singleton
class PtpipDiscoveryService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wifiHelper: WifiNetworkHelper,
    private val mdns: MdnsCameraDiscovery,
) {
    companion object {
        private const val TAG = "PtpipDiscoveryService"
    }

    /**
     * 카메라 검색.
     *
     * @param method 사용자가 선택한 연결 방식. null이면 환경(AP SSID / 핫스팟)을 보고 자동 추정.
     */
    suspend fun discoverCameras(method: ConnectionMethod? = null): List<CameraEndpoint> {
        if (!wifiHelper.isWifiConnected() && !wifiHelper.isHotspotEnabled()) {
            Log.w(TAG, "Wi-Fi 미연결 + 핫스팟 비활성 — 검색 불가")
            return emptyList()
        }

        val effective = method ?: inferMethod()
        Log.d(TAG, "검색 시작: $effective")

        return when (effective) {
            ConnectionMethod.AP -> discoverAp()
            ConnectionMethod.STA_ROUTER,
            ConnectionMethod.STA_PHONE_HOTSPOT -> discoverSta(effective)
        }
    }

    /** 레거시 호출자(파라미터 없는 API) 호환용. */
    suspend fun discoverCameras(): List<CameraEndpoint> = discoverCameras(method = null)

    fun stopDiscovery() {
        Log.d(TAG, "검색 중지 요청 (mDNS는 timeout 기반이라 별도 작업 없음)")
    }

    private fun inferMethod(): ConnectionMethod = when {
        wifiHelper.isHotspotEnabled() -> ConnectionMethod.STA_PHONE_HOTSPOT
        wifiHelper.isConnectedToCameraAP() -> ConnectionMethod.AP
        else -> ConnectionMethod.STA_ROUTER
    }

    private fun discoverAp(): List<CameraEndpoint> {
        val ip = wifiHelper.findAvailableCameraIP() ?: return emptyList()
        val ssid = wifiHelper.getCurrentSSID() ?: "Camera AP"
        return listOf(
            CameraEndpoint(
                ipAddress = ip,
                port = Constants.Network.PTPIP_DEFAULT_PORT,
                name = "$ssid (AP)",
                source = EndpointSource.GATEWAY,
            )
        )
    }

    private suspend fun discoverSta(method: ConnectionMethod): List<CameraEndpoint> {
        val found = mdns.discover()
        if (found.isNotEmpty()) {
            Log.i(TAG, "mDNS 발견 ${found.size}개")
            return found
        }
        Log.w(TAG, "mDNS 빈 결과 — 사용자 수동 입력 흐름으로 폴백 권장 (method=$method)")
        return emptyList()
    }
}
