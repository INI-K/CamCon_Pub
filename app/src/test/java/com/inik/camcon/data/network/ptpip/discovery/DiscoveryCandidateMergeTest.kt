package com.inik.camcon.data.network.ptpip.discovery

import com.inik.camcon.domain.model.CameraDiscoverySource
import com.inik.camcon.domain.model.CameraVendor
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.VendorConfidence
import com.inik.camcon.domain.model.VendorVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

/**
 * [PtpipDiscoveryService.mergeCandidates] 다중 후보 누적 회귀 테스트.
 *
 * 회귀 배경(후보가 1대로 접히던 5개 원인 중 병합 관련분):
 * - 타입당 첫 resolve 성공 즉시 resume + stopServiceDiscovery → 2대 이상이 잡히지 않았다.
 * - 캐시 히트 시 mDNS 전면 생략 + 즉시 return → 캐시 후보 1건만 남았다.
 * 이제 후보는 예산 소진까지 **누적**되며, 같은 IP:port만 병합된다.
 */
class DiscoveryCandidateMergeTest {

    private fun camera(
        ip: String,
        port: Int = 15740,
        name: String = "cam",
        vendor: CameraVendor = CameraVendor.UNKNOWN,
        confidence: VendorConfidence = VendorConfidence.UNKNOWN,
        serviceType: String? = null,
        source: CameraDiscoverySource = CameraDiscoverySource.MDNS
    ) = PtpipCamera(
        ipAddress = ip,
        port = port,
        name = name,
        discoveredServiceType = serviceType,
        vendorVerdict = VendorVerdict(vendor, confidence),
        discoverySource = source
    )

    @Test
    fun `서로 다른 IP의 mDNS 후보 2건이 모두 남는다`() {
        val merged = PtpipDiscoveryService.mergeCandidates(
            existing = listOf(camera("192.168.49.10", name = "Z_8_1")),
            incoming = listOf(camera("192.168.49.20", name = "Z_6_2"))
        )

        assertEquals(2, merged.size)
        assertEquals(
            setOf("192.168.49.10", "192.168.49.20"),
            merged.map { it.ipAddress }.toSet()
        )
    }

    @Test
    fun `같은 IP포트에서 nikon 타입 CONFIRMED가 표준 ptp UNKNOWN을 대체한다`() {
        val standard = camera(
            "192.168.49.10",
            name = "MyCam",
            serviceType = "_ptp._tcp"
        )
        val nikonType = camera(
            "192.168.49.10",
            name = "MyCam",
            vendor = CameraVendor.NIKON,
            confidence = VendorConfidence.CONFIRMED,
            serviceType = "_nikon._tcp"
        )

        val merged = PtpipDiscoveryService.mergeCandidates(listOf(standard), listOf(nikonType))

        assertEquals(1, merged.size)
        assertEquals(CameraVendor.NIKON, merged.single().vendorVerdict.vendor)
        assertEquals("_nikon._tcp", merged.single().discoveredServiceType)
    }

    @Test
    fun `같은 IP에서 NIKON verdict는 SSDP CANON CONFIRMED에 덮이지 않는다`() {
        val nikonMdns = camera(
            "192.168.49.10",
            name = "Z_8_5003869",
            vendor = CameraVendor.NIKON,
            confidence = VendorConfidence.LIKELY,
            serviceType = "_ptp._tcp",
            source = CameraDiscoverySource.MDNS
        )
        val canonSsdp = camera(
            "192.168.49.10",
            name = "Canon Server",
            vendor = CameraVendor.CANON,
            confidence = VendorConfidence.CONFIRMED,
            source = CameraDiscoverySource.SSDP
        )

        val merged = PtpipDiscoveryService.mergeCandidates(listOf(nikonMdns), listOf(canonSsdp))

        assertEquals(1, merged.size)
        assertEquals(
            "NIKON verdict가 덮이면 STA 인증이 생략되어 첫 페어링이 InitFail 0x1로 파손된다",
            CameraVendor.NIKON,
            merged.single().vendorVerdict.vendor
        )
        assertEquals("Z_8_5003869", merged.single().name)
    }

    @Test
    fun `캐시 후보와 다른 IP의 mDNS 후보가 공존한다`() {
        val cached = camera(
            "192.168.49.10",
            name = "Z_8_5003869",
            vendor = CameraVendor.NIKON,
            confidence = VendorConfidence.LIKELY,
            source = CameraDiscoverySource.CACHED_IP
        )
        val live = camera("192.168.49.55", name = "Z_6_5000784", source = CameraDiscoverySource.MDNS)

        val merged = PtpipDiscoveryService.mergeCandidates(listOf(cached), listOf(live))

        assertEquals(2, merged.size)
        assertTrue(merged.any { it.discoverySource == CameraDiscoverySource.CACHED_IP })
        assertTrue(merged.any { it.discoverySource == CameraDiscoverySource.MDNS })
    }

    @Test
    fun `같은 IP면 라이브 mDNS가 캐시 후보를 대체한다(동일 신뢰도 tie-break)`() {
        val cached = camera("192.168.49.10", source = CameraDiscoverySource.CACHED_IP)
        val live = camera("192.168.49.10", source = CameraDiscoverySource.MDNS)

        val merged = PtpipDiscoveryService.mergeCandidates(listOf(cached), listOf(live))

        assertEquals(1, merged.size)
        assertEquals(CameraDiscoverySource.MDNS, merged.single().discoverySource)
    }

    @Test
    fun `동일 항목 재도착은 목록을 늘리지 않는다`() {
        val existing = listOf(camera("192.168.49.10"), camera("192.168.49.20"))
        val merged = PtpipDiscoveryService.mergeCandidates(existing, existing)

        assertEquals(2, merged.size)
    }

    @Test
    fun `같은 IP라도 포트가 다르면 별개 후보다`() {
        val merged = PtpipDiscoveryService.mergeCandidates(
            existing = listOf(camera("192.168.49.10", port = 15740)),
            incoming = listOf(camera("192.168.49.10", port = 15741))
        )

        assertEquals(2, merged.size)
    }

    @Test
    fun `빈 incoming은 기존 목록을 그대로 반환한다`() {
        val existing = listOf(camera("192.168.49.10"))
        assertEquals(existing, PtpipDiscoveryService.mergeCandidates(existing, emptyList()))
    }

    @Test
    fun `IPv4 호스트만 후보로 허용된다`() {
        // libgphoto2 ptpip은 AF_INET 전용이고 ptpip:IP:PORT 경로가 콜론 구분이라 IPv6 불가.
        assertTrue(PtpipDiscoveryService.isSupportedHost(InetAddress.getByName("192.168.49.10")))
        assertFalse(PtpipDiscoveryService.isSupportedHost(InetAddress.getByName("::1")))
        assertFalse(PtpipDiscoveryService.isSupportedHost(null))
    }

    // ───────────────────── 프로브 ≥1s 쿨다운 ─────────────────────

    @Test
    fun `같은 IP로 1000ms 이내 재프로브는 직전 결과를 재사용한다`() {
        val lastAt = 10_000L
        assertTrue(PtpipDiscoveryService.ProbeCooldown.shouldReuse(lastAt, 10_001L))
        assertTrue(PtpipDiscoveryService.ProbeCooldown.shouldReuse(lastAt, 10_999L))
        assertFalse(PtpipDiscoveryService.ProbeCooldown.shouldReuse(lastAt, 11_000L))
    }

    @Test
    fun `프로브 기록이 없으면 즉시 새 TCP를 허용한다`() {
        assertFalse(PtpipDiscoveryService.ProbeCooldown.shouldReuse(0L, 10_000L))
        assertEquals(0L, PtpipDiscoveryService.ProbeCooldown.remainingMs(0L, 10_000L))
    }

    @Test
    fun `남은 쿨다운은 0에서 1000ms 사이로 클램프된다`() {
        assertEquals(700L, PtpipDiscoveryService.ProbeCooldown.remainingMs(10_000L, 10_300L))
        assertEquals(0L, PtpipDiscoveryService.ProbeCooldown.remainingMs(10_000L, 12_000L))
        assertEquals(
            PtpipDiscoveryService.ProbeCooldown.MIN_INTERVAL_MS,
            PtpipDiscoveryService.ProbeCooldown.remainingMs(10_000L, 10_000L)
        )
    }
}
