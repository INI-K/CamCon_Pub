package com.inik.camcon.data.network.ptpip.discovery

import com.inik.camcon.domain.model.ConnectionMethod
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.WifiNetworkState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * STA_PHONE_HOTSPOT 분기에서 mDNS 광고가 도달하지 않을 때의 수동 IP 폴백 회귀 테스트.
 *
 * architect 설계 §6.3: 핫스팟 모드에서 카메라가 광고하지 않을 수 있으므로
 * `HotspotStaModeContent`의 ManualIpInputCard → ViewModel.connectManualCamera() →
 * `PtpipDataSource.addManualCamera()` 경로가 폴백으로 제공된다.
 *
 * 본 테스트는 **폴백 결정 함수**(`HotspotDiscoveryFallback.decide`)의 순수 로직을 검증한다.
 * mDNS 결과가 비어 있고 사용자가 IP를 입력한 경우에만 수동 IP 폴백을 시도해야 한다.
 */
class PtpipDiscoveryHotspotFallbackTest {

    private fun hotspotState(enabled: Boolean = true, gateway: String? = "192.168.49.1") =
        WifiNetworkState(
            isConnected = enabled,
            isConnectedToCameraAP = false,
            ssid = if (enabled) "MyPhoneHotspot" else null,
            detectedCameraIP = null,
            gatewayIp = gateway,
            subnetPrefix = 24,
            isHotspotEnabled = enabled,
        )

    @Test
    fun `empty mdns result with hotspot on and user ip falls back to manual`() {
        val result = HotspotDiscoveryFallback.decide(
            method = ConnectionMethod.STA_PHONE_HOTSPOT,
            mdnsResults = emptyList(),
            wifiState = hotspotState(),
            userIp = "192.168.49.137",
        )

        assertTrue(
            "Empty mDNS + user IP must trigger manual fallback",
            result is HotspotDiscoveryFallback.Decision.UseManual
        )
        assertEquals("192.168.49.137", (result as HotspotDiscoveryFallback.Decision.UseManual).ip)
    }

    @Test
    fun `non-empty mdns result uses mdns regardless of user ip`() {
        val cam = PtpipCamera("10.0.0.5", 15740, "Z9-1234")
        val result = HotspotDiscoveryFallback.decide(
            method = ConnectionMethod.STA_PHONE_HOTSPOT,
            mdnsResults = listOf(cam),
            wifiState = hotspotState(),
            userIp = "192.168.49.137",
        )

        assertTrue(
            "mDNS hits take precedence over manual IP",
            result is HotspotDiscoveryFallback.Decision.UseMdns
        )
        assertEquals(
            listOf(cam),
            (result as HotspotDiscoveryFallback.Decision.UseMdns).cameras
        )
    }

    @Test
    fun `empty mdns and blank ip yields no candidate`() {
        val result = HotspotDiscoveryFallback.decide(
            method = ConnectionMethod.STA_PHONE_HOTSPOT,
            mdnsResults = emptyList(),
            wifiState = hotspotState(),
            userIp = "",
        )

        assertTrue(result is HotspotDiscoveryFallback.Decision.NoCandidate)
    }

    @Test
    fun `non-hotspot method skips manual fallback even with empty mdns`() {
        val result = HotspotDiscoveryFallback.decide(
            method = ConnectionMethod.STA_ROUTER,
            mdnsResults = emptyList(),
            wifiState = hotspotState(enabled = false),
            userIp = "192.168.49.137",
        )

        // 일반 STA 라우터 모드에서는 mDNS만 신뢰 — 수동 IP 폴백 미적용.
        assertTrue(result is HotspotDiscoveryFallback.Decision.NoCandidate)
    }

    @Test
    fun `hotspot off but method is phone hotspot still allows manual fallback`() {
        // 사용자가 핫스팟 모드 탭을 선택했고 IP를 입력했으면 핫스팟 상태와 무관하게 시도.
        val result = HotspotDiscoveryFallback.decide(
            method = ConnectionMethod.STA_PHONE_HOTSPOT,
            mdnsResults = emptyList(),
            wifiState = hotspotState(enabled = false, gateway = null),
            userIp = "192.168.49.137",
        )
        assertTrue(result is HotspotDiscoveryFallback.Decision.UseManual)
    }

    @Test
    fun `manual ip is trimmed before fallback`() {
        val result = HotspotDiscoveryFallback.decide(
            method = ConnectionMethod.STA_PHONE_HOTSPOT,
            mdnsResults = emptyList(),
            wifiState = hotspotState(),
            userIp = "  192.168.49.137  ",
        )
        assertNotNull(result)
        assertEquals("192.168.49.137", (result as HotspotDiscoveryFallback.Decision.UseManual).ip)
    }
}
