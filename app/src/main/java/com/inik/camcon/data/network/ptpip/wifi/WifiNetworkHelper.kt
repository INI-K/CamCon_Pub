package com.inik.camcon.data.network.ptpip.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.inik.camcon.domain.model.WifiCapabilities
import com.inik.camcon.domain.model.WifiNetworkState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wi-Fi 네트워크 상태 + 카메라 AP/핫스팟 감지 헬퍼.
 *
 * 책임:
 * - 활성 Wi-Fi 연결 상태와 SSID를 노출
 * - 카메라 AP일 가능성을 SSID로 추정 (제조사 패턴 매칭)
 * - 게이트웨이 IP / 서브넷 prefix 를 LinkProperties에서 동적으로 추출
 * - 폰이 직접 테더링(AP)을 켠 상태인지 감지
 */
@Singleton
class WifiNetworkHelper @Inject constructor(
    @ApplicationContext context: Context
) {
    companion object {
        private const val TAG = "WifiNetworkHelper"

        /**
         * 카메라 AP의 SSID 패턴.
         *
         * 제조사 표기(`nikon`, `canon`, …) + 일반 camera 키워드 + 모델 prefix(`Z6-`, `D850-`, …) +
         * Wi-Fi Direct prefix(`DIRECT-`) + 설정 모드 prefix(`SETUP-`) 까지 폭넓게 인식한다.
         */
        private val CAMERA_AP_REGEX = Regex(
            pattern = "(?i)" +
                "(nikon|canon|sony|fujifilm|fuji|olympus|panasonic|lumix|leica|pentax|ricoh|hasselblad|sigma|gopro)" +
                "|^(direct-|setup-)" +
                "|^(z[0-9]|d[0-9]{3,4}|r[0-9p]|a[1-9]|x[a-z][0-9]?-|e-m|gh[1-9]|s5|s1)" +
                "|(_camera|-camera)" +
                "|^camera"
        )

        /** 단위 테스트용 정적 진입점. */
        @JvmStatic
        fun isCameraApSsid(ssid: String): Boolean = CAMERA_AP_REGEX.containsMatchIn(ssid)
    }

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val _networkState = MutableStateFlow(readState())
    val networkStateFlow: StateFlow<WifiNetworkState> = _networkState.asStateFlow()

    fun isWifiConnected(): Boolean {
        refresh()
        return _networkState.value.isConnected
    }

    fun isConnectedToCameraAP(): Boolean {
        refresh()
        return _networkState.value.isConnectedToCameraAP
    }

    fun isHotspotEnabled(): Boolean {
        refresh()
        return _networkState.value.isHotspotEnabled
    }

    fun getCurrentSSID(): String? = readSsid()

    /**
     * 카메라가 있을 가능성이 높은 IP. 우선순위:
     * 1. AP/핫스팟 모드의 게이트웨이 IP
     * 2. 활성 Wi-Fi 게이트웨이 IP
     */
    fun findAvailableCameraIP(): String? {
        refresh()
        return _networkState.value.detectedCameraIP
    }

    fun getGatewayIp(): String? {
        refresh()
        return _networkState.value.gatewayIp
    }

    fun getSubnetPrefix(): Int? {
        refresh()
        return _networkState.value.subnetPrefix
    }

    fun isStaConcurrencySupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }

    fun getWifiCapabilities(): WifiCapabilities {
        refresh()
        val state = _networkState.value
        val info = runCatching { wifiManager.connectionInfo }.getOrNull()
        return WifiCapabilities(
            isConnected = state.isConnected,
            isStaConcurrencySupported = isStaConcurrencySupported(),
            isConnectedToCameraAP = state.isConnectedToCameraAP,
            networkName = state.ssid,
            linkSpeed = info?.linkSpeed,
            frequency = info?.frequency,
            ipAddress = info?.ipAddress,
            macAddress = info?.macAddress,
            detectedCameraIP = state.detectedCameraIP
        )
    }

    private fun refresh() {
        _networkState.value = readState()
    }

    private fun readState(): WifiNetworkState = try {
        val active = connectivityManager.activeNetwork
        val caps = active?.let { connectivityManager.getNetworkCapabilities(it) }
        val connected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        val linkProps: LinkProperties? = active?.let { connectivityManager.getLinkProperties(it) }
        val gatewayIp = extractGatewayIp(linkProps)
        val subnetPrefix = extractSubnetPrefix(linkProps)

        val ssid = readSsid()
        val isCameraAp = ssid != null && looksLikeCameraAp(ssid)
        val isHotspot = isHotspotActive()

        WifiNetworkState(
            isConnected = connected,
            isConnectedToCameraAP = isCameraAp,
            ssid = ssid,
            detectedCameraIP = when {
                isCameraAp -> gatewayIp ?: "192.168.1.1"
                isHotspot -> null // 폰이 AP — 클라이언트(카메라) IP는 mDNS/ARP/사용자 입력으로 별도 탐색
                else -> gatewayIp
            },
            gatewayIp = gatewayIp,
            subnetPrefix = subnetPrefix,
            isHotspotEnabled = isHotspot,
        )
    } catch (e: Exception) {
        Log.w(TAG, "Wi-Fi 상태 조회 실패", e)
        WifiNetworkState(
            isConnected = false,
            isConnectedToCameraAP = false,
            ssid = null,
            detectedCameraIP = null,
        )
    }

    private fun readSsid(): String? {
        return runCatching {
            wifiManager.connectionInfo?.ssid
                ?.trim('"')
                ?.takeUnless { it.isBlank() || it == "<unknown ssid>" }
        }.getOrNull()
    }

    private fun extractGatewayIp(linkProps: LinkProperties?): String? {
        if (linkProps == null) return null
        return linkProps.routes
            .asSequence()
            .filter { it.isDefaultRoute }
            .mapNotNull { it.gateway }
            .filterIsInstance<Inet4Address>()
            .map { it.hostAddress }
            .firstOrNull()
    }

    private fun extractSubnetPrefix(linkProps: LinkProperties?): Int? {
        if (linkProps == null) return null
        return linkProps.linkAddresses
            .asSequence()
            .filter { it.address is Inet4Address }
            .map(LinkAddress::getPrefixLength)
            .firstOrNull()
    }

    /**
     * 폰이 직접 핫스팟(테더링)을 켠 상태인지 감지.
     *
     * 표준 API가 없어 reflection 사용. 실패 시 false (안전한 기본값).
     */
    private fun isHotspotActive(): Boolean = runCatching {
        val method = WifiManager::class.java.getDeclaredMethod("isWifiApEnabled")
        method.isAccessible = true
        method.invoke(wifiManager) as? Boolean ?: false
    }.getOrElse { false }

    @Suppress("MemberVisibilityCanBePrivate")
    internal fun looksLikeCameraAp(ssid: String): Boolean = isCameraApSsid(ssid)
}
