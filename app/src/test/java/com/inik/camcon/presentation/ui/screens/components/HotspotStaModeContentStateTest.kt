package com.inik.camcon.presentation.ui.screens.components

import com.inik.camcon.domain.model.WifiNetworkState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * HotspotStaModeContent의 상태 표시 로직 회귀 테스트.
 *
 * 컴포저블 자체는 androidTest(Compose UI)에서 검증하지만, 본 단위 테스트는
 * 컴포저블이 화면에 그릴 **상태 문구를 결정하는 순수 함수**
 * `HotspotStaContentState.fromWifiState`를 검증한다.
 *
 * 핫스팟 OFF 상태에서 사용자에게 "꺼져 있음" 메시지가 노출되어야 한다.
 */
class HotspotStaModeContentStateTest {

    @Test
    fun `hotspot enabled true yields enabled state with gateway`() {
        val wifi = WifiNetworkState(
            isConnected = true,
            isConnectedToCameraAP = false,
            ssid = "MyPhoneHotspot",
            detectedCameraIP = null,
            gatewayIp = "192.168.49.1",
            subnetPrefix = 24,
            isHotspotEnabled = true,
        )

        val state = HotspotStaContentState.fromWifiState(wifi)

        assertEquals(HotspotStaContentState.HotspotStatus.ENABLED, state.status)
        assertEquals("MyPhoneHotspot", state.ssidLabel)
        assertEquals("192.168.49.1", state.gatewayLabel)
    }

    @Test
    fun `hotspot disabled yields disabled state with no ssid`() {
        val wifi = WifiNetworkState(
            isConnected = false,
            isConnectedToCameraAP = false,
            ssid = null,
            detectedCameraIP = null,
            gatewayIp = null,
            subnetPrefix = null,
            isHotspotEnabled = false,
        )

        val state = HotspotStaContentState.fromWifiState(wifi)

        assertEquals(HotspotStaContentState.HotspotStatus.DISABLED, state.status)
        // OFF 상태에서는 사용자에게 "꺼져 있음" 안내가 노출되어야 한다.
        assertEquals(null, state.ssidLabel)
        assertEquals(null, state.gatewayLabel)
    }

    @Test
    fun `legacy 4-arg WifiNetworkState defaults isHotspotEnabled false`() {
        // origin 호출부 `WifiNetworkState(false, false, null, null)`이 핫스팟 미감지로 매핑되는지.
        val wifi = WifiNetworkState(false, false, null, null)
        val state = HotspotStaContentState.fromWifiState(wifi)
        assertEquals(HotspotStaContentState.HotspotStatus.DISABLED, state.status)
    }

    @Test
    fun `connect button is disabled when hotspot off`() {
        val wifi = WifiNetworkState(false, false, null, null)
        val state = HotspotStaContentState.fromWifiState(wifi)
        // 핫스팟이 꺼져 있으면 카메라 검색 버튼을 비활성화하여 사용자 혼란 방지.
        assertEquals(false, state.canDiscover)
    }

    @Test
    fun `connect button is enabled when hotspot on`() {
        val wifi = WifiNetworkState(
            isConnected = true,
            isConnectedToCameraAP = false,
            ssid = "Hotspot",
            detectedCameraIP = null,
            isHotspotEnabled = true,
        )
        val state = HotspotStaContentState.fromWifiState(wifi)
        assertEquals(true, state.canDiscover)
    }
}
