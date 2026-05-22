package com.inik.camcon.data.network.ptpip.wifi

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 카메라 AP SSID 패턴 매칭 회귀 테스트.
 *
 * origin에는 `CAMERA_AP_PATTERNS` companion 상수와 `isCameraNetwork(ssid)` private 함수만
 * 존재하여 외부 검증이 불가능하다. 통합 PR에서는 패턴 가드를 위해 동등한
 * `WifiNetworkHelper.isCameraApSsid(ssid)` companion 함수를 노출한다.
 *
 * 패턴이 의도치 않게 좁아지는 회귀(Phase 2 이전의 "nikon/canon/sony/camera" 제한)를 막기 위한 가드.
 */
class WifiCameraSsidPatternTest {

    @Test
    fun `nikon ssid matches`() {
        assertTrue(WifiNetworkHelper.isCameraApSsid("Nikon_Z6_1234"))
    }

    @Test
    fun `canon ssid matches`() {
        assertTrue(WifiNetworkHelper.isCameraApSsid("Canon EOS R5"))
    }

    @Test
    fun `sony ssid matches`() {
        assertTrue(WifiNetworkHelper.isCameraApSsid("DIRECT-SonyA7"))
    }

    @Test
    fun `fujifilm ssid matches`() {
        assertTrue(WifiNetworkHelper.isCameraApSsid("FUJIFILM-X-T4"))
    }

    @Test
    fun `panasonic lumix ssid matches`() {
        assertTrue(WifiNetworkHelper.isCameraApSsid("LUMIX-GH6"))
    }

    @Test
    fun `direct prefix matches wifi direct camera ssid`() {
        assertTrue(WifiNetworkHelper.isCameraApSsid("DIRECT-XX-Camera"))
    }

    @Test
    fun `model prefix matches nikon z series`() {
        assertTrue(WifiNetworkHelper.isCameraApSsid("Z9-12345"))
    }

    @Test
    fun `regular home ssid does not match`() {
        assertFalse(WifiNetworkHelper.isCameraApSsid("MyHomeWifi"))
    }

    @Test
    fun `tethering ssid does not match`() {
        assertFalse(WifiNetworkHelper.isCameraApSsid("AndroidHotspot-2024"))
    }

    @Test
    fun `coffee shop wifi does not match`() {
        assertFalse(WifiNetworkHelper.isCameraApSsid("Starbucks-WiFi"))
    }
}
