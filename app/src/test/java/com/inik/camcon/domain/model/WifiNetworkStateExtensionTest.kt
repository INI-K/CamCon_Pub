package com.inik.camcon.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WifiNetworkState 신규 필드 default 회귀 테스트.
 *
 * 폰 핫스팟 STA 모드를 지원하기 위해 `gatewayIp`, `subnetPrefix`, `isHotspotEnabled` 세
 * 필드가 추가된다. origin의 positional 4-arg 생성자 호출부(`WifiNetworkHelper.kt:525-530`,
 * `PtpipDataSource.kt:120`)가 컴파일 보존되어야 하므로 모두 default 값을 가져야 한다.
 */
class WifiNetworkStateExtensionTest {

    @Test
    fun `legacy positional 4-arg constructor still compiles and yields default new fields`() {
        // origin 호출 형태: WifiNetworkState(false, false, null, null)
        val state = WifiNetworkState(false, false, null, null)

        assertFalse(state.isConnected)
        assertFalse(state.isConnectedToCameraAP)
        assertNull(state.ssid)
        assertNull(state.detectedCameraIP)

        // 신규 필드들은 default 값을 가져야 한다 (비파괴 보장).
        assertNull(state.gatewayIp)
        assertNull(state.subnetPrefix)
        assertFalse(state.isHotspotEnabled)
    }

    @Test
    fun `hotspot scenario populates new fields`() {
        val state = WifiNetworkState(
            isConnected = true,
            isConnectedToCameraAP = false,
            ssid = "MyPhoneHotspot",
            detectedCameraIP = null,
            gatewayIp = "192.168.49.1",
            subnetPrefix = 24,
            isHotspotEnabled = true,
        )

        assertTrue(state.isHotspotEnabled)
        assertEquals("192.168.49.1", state.gatewayIp)
        assertEquals(24, state.subnetPrefix)
    }

    @Test
    fun `copy preserves existing fields when only new fields change`() {
        val base = WifiNetworkState(true, false, "WiFi", null)
        val withHotspot = base.copy(isHotspotEnabled = true, gatewayIp = "10.0.0.1")

        assertTrue(withHotspot.isConnected)
        assertEquals("WiFi", withHotspot.ssid)
        assertTrue(withHotspot.isHotspotEnabled)
        assertEquals("10.0.0.1", withHotspot.gatewayIp)
    }
}
