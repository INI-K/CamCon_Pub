package com.inik.camcon.data.network.ptpip.discovery

import com.inik.camcon.domain.model.CameraVendor
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.VendorConfidence
import com.inik.camcon.domain.model.VendorVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CameraVendorClassifier] 단위 테스트.
 *
 * 설계 근거: docs/superpowers/specs/2026-07-06-multivendor-camera-discovery-design.md §2.2
 *
 * 핵심 규약(테스트로 고정하는 불변식):
 * - mDNS: `_nikon._tcp` 서비스 타입이 이름보다 우선(확정), 이름 패턴은 확정/추정으로 분기.
 * - SSDP: Canon/Sony/Panasonic URN·server 헤더로 판별, 공유기 응답은 UNKNOWN.
 * - UNKNOWN은 "해당 제조사 아님"이 아니라 "연결 전 신호로는 판별 불가" — 기기명을 바꾼
 *   니콘은 이름 신호가 사라지므로 UNKNOWN이 될 수 있고, 연결 후 DeviceInfo로만 확정된다.
 */
class CameraVendorClassifierTest {

    // ── 1. classifyMdns: 서비스 타입이 이름보다 우선 ──────────────────────────

    @Test
    fun `mdns nikon service type overrides renamed name`() {
        // 사용자가 이름을 "MyRenamedCam"으로 바꿔도 _nikon._tcp 타입이 있으면 확정.
        val verdict = CameraVendorClassifier.classifyMdns(
            serviceName = "MyRenamedCam",
            serviceType = "_nikon._tcp"
        )
        assertEquals(CameraVendor.NIKON, verdict.vendor)
        assertEquals(VendorConfidence.CONFIRMED, verdict.confidence)
    }

    // ── 2. classifyMdns: 이름에 nikon 포함 → 확정 (대소문자 무관) ───────────────

    @Test
    fun `mdns name containing Nikon is confirmed`() {
        val verdict = CameraVendorClassifier.classifyMdns("Nikon_Z8", serviceType = null)
        assertEquals(CameraVendor.NIKON, verdict.vendor)
        assertEquals(VendorConfidence.CONFIRMED, verdict.confidence)
    }

    @Test
    fun `mdns lowercase nikon name is confirmed`() {
        val verdict = CameraVendorClassifier.classifyMdns("nikon z fc", serviceType = null)
        assertEquals(CameraVendor.NIKON, verdict.vendor)
        assertEquals(VendorConfidence.CONFIRMED, verdict.confidence)
    }

    // ── 3. classifyMdns: 실기 관측 프리픽스(Z_/D_) → 추정(LIKELY) ────────────────

    @Test
    fun `mdns Z prefix serial name is likely nikon`() {
        val verdict = CameraVendorClassifier.classifyMdns("Z_6_5000784", serviceType = null)
        assertEquals(CameraVendor.NIKON, verdict.vendor)
        assertEquals(VendorConfidence.LIKELY, verdict.confidence)
    }

    @Test
    fun `mdns D prefix serial name is likely nikon`() {
        val verdict = CameraVendorClassifier.classifyMdns("D_850_1234567", serviceType = null)
        assertEquals(CameraVendor.NIKON, verdict.vendor)
        assertEquals(VendorConfidence.LIKELY, verdict.confidence)
    }

    @Test
    fun `mdns lowercase z prefix serial name is likely nikon`() {
        val verdict = CameraVendorClassifier.classifyMdns("z_6_5000784", serviceType = null)
        assertEquals(CameraVendor.NIKON, verdict.vendor)
        assertEquals(VendorConfidence.LIKELY, verdict.confidence)
    }

    // ── 4. classifyMdns: 기기명 변경 시나리오 → UNKNOWN ─────────────────────────

    @Test
    fun `mdns renamed device with plain ptp type is unknown`() {
        // 사용자가 니콘 기기명을 "StudioCam"으로 바꾸고 표준 _ptp._tcp로만 광고하면
        // 연결 전 신호가 사라진다. 이때 UNKNOWN은 "니콘 아님"이 아니라
        // "연결 전 판별 불가"를 뜻한다 — 확정은 연결 후 DeviceInfo(manufacturer)로만 가능.
        val verdict = CameraVendorClassifier.classifyMdns("StudioCam", serviceType = "_ptp._tcp")
        assertEquals(CameraVendor.UNKNOWN, verdict.vendor)
        assertEquals(VendorConfidence.UNKNOWN, verdict.confidence)
    }

    // ── 5. classifyMdns: serviceType null 허용 ─────────────────────────────────

    @Test
    fun `mdns null service type is allowed and yields unknown for generic name`() {
        val verdict = CameraVendorClassifier.classifyMdns("StudioCam", serviceType = null)
        assertEquals(CameraVendor.UNKNOWN, verdict.vendor)
        assertEquals(VendorConfidence.UNKNOWN, verdict.confidence)
    }

    // ── 6. classifySsdp: Canon 3종 URN (st 또는 usn 어느 쪽에 있어도) → 확정 ──────

    @Test
    fun `ssdp canon smartphone urn in st is confirmed`() {
        val verdict = CameraVendorClassifier.classifySsdp(
            st = "urn:schemas-canon-com:service:ICPO-SmartPhoneEOSSystemService:1",
            usn = null,
            server = null
        )
        assertEquals(CameraVendor.CANON, verdict.vendor)
        assertEquals(VendorConfidence.CONFIRMED, verdict.confidence)
    }

    @Test
    fun `ssdp canon wft urn in usn is confirmed`() {
        val verdict = CameraVendorClassifier.classifySsdp(
            st = "upnp:rootdevice",
            usn = "uuid:1234::urn:schemas-canon-com:service:ICPO-WFTEOSSystemService:1",
            server = null
        )
        assertEquals(CameraVendor.CANON, verdict.vendor)
        assertEquals(VendorConfidence.CONFIRMED, verdict.confidence)
    }

    @Test
    fun `ssdp canon ccapi urn is confirmed`() {
        val verdict = CameraVendorClassifier.classifySsdp(
            st = "urn:schemas-canon-com:service:ICPO-CameraControlAPIService:1",
            usn = null,
            server = null
        )
        assertEquals(CameraVendor.CANON, verdict.vendor)
        assertEquals(VendorConfidence.CONFIRMED, verdict.confidence)
    }

    // ── 7. classifySsdp: Sony URN — ScalarWebAPI 확정 / MtpNullService 추정 ──────

    @Test
    fun `ssdp sony scalar web api is confirmed`() {
        val verdict = CameraVendorClassifier.classifySsdp(
            st = "urn:schemas-sony-com:service:ScalarWebAPI:1",
            usn = null,
            server = null
        )
        assertEquals(CameraVendor.SONY, verdict.vendor)
        assertEquals(VendorConfidence.CONFIRMED, verdict.confidence)
    }

    @Test
    fun `ssdp sony mtp null service is likely`() {
        // A7s 실측: 범용 MTP 가능성이 있어 확정 아닌 추정.
        val verdict = CameraVendorClassifier.classifySsdp(
            st = "urn:microsoft-com:service:MtpNullService:1",
            usn = null,
            server = null
        )
        assertEquals(CameraVendor.SONY, verdict.vendor)
        assertEquals(VendorConfidence.LIKELY, verdict.confidence)
    }

    // ── 8. classifySsdp: Panasonic server 헤더 → 추정 ──────────────────────────

    @Test
    fun `ssdp panasonic server header is likely`() {
        val verdict = CameraVendorClassifier.classifySsdp(
            st = "upnp:rootdevice",
            usn = null,
            server = "Panasonic UPnP/1.0"
        )
        assertEquals(CameraVendor.PANASONIC, verdict.vendor)
        assertEquals(VendorConfidence.LIKELY, verdict.confidence)
    }

    // ── 9. classifySsdp: 공유기 응답 → UNKNOWN ────────────────────────────────

    @Test
    fun `ssdp router response is unknown`() {
        val verdict = CameraVendorClassifier.classifySsdp(
            st = "upnp:rootdevice",
            usn = null,
            server = "MiWiFi/1.0"
        )
        assertEquals(CameraVendor.UNKNOWN, verdict.vendor)
        assertEquals(VendorConfidence.UNKNOWN, verdict.confidence)
    }

    // ── 10. confirmFromDeviceInfo: manufacturer 문자열 → 제조사 확정 ─────────────

    @Test
    fun `device info manufacturer maps to vendor`() {
        assertEquals(
            CameraVendor.NIKON,
            CameraVendorClassifier.confirmFromDeviceInfo("Nikon Corporation")
        )
        assertEquals(
            CameraVendor.CANON,
            CameraVendorClassifier.confirmFromDeviceInfo("Canon Inc.")
        )
        assertEquals(
            CameraVendor.SONY,
            CameraVendorClassifier.confirmFromDeviceInfo("Sony Corporation")
        )
        assertEquals(
            CameraVendor.FUJIFILM,
            CameraVendorClassifier.confirmFromDeviceInfo("FUJIFILM")
        )
        assertEquals(
            CameraVendor.PANASONIC,
            CameraVendorClassifier.confirmFromDeviceInfo("Panasonic")
        )
    }

    @Test
    fun `device info null manufacturer is unknown`() {
        assertEquals(
            CameraVendor.UNKNOWN,
            CameraVendorClassifier.confirmFromDeviceInfo(null)
        )
    }

    @Test
    fun `device info unmapped manufacturer is unknown`() {
        assertEquals(
            CameraVendor.UNKNOWN,
            CameraVendorClassifier.confirmFromDeviceInfo("Ricoh")
        )
    }

    // ── 11. isLikelyNikon: verdict 우선, verdict 미산정 시 이름 폴백 ─────────────

    @Test
    fun `is likely nikon true when verdict is nikon`() {
        val camera = PtpipCamera(
            ipAddress = "192.168.1.10",
            port = 15740,
            name = "SomeCam",
            vendorVerdict = VendorVerdict(CameraVendor.NIKON, VendorConfidence.CONFIRMED)
        )
        assertTrue(CameraVendorClassifier.isLikelyNikon(camera))
    }

    @Test
    fun `is likely nikon true via name fallback when verdict unknown`() {
        // 캐시/수동 IP 등 verdict 미산정 경로 폴백: 이름 패턴으로 재판별.
        val camera = PtpipCamera(
            ipAddress = "192.168.1.11",
            port = 15740,
            name = "Z_8_9999",
            vendorVerdict = VendorVerdict.unknown()
        )
        assertTrue(CameraVendorClassifier.isLikelyNikon(camera))
    }

    @Test
    fun `is likely nikon false when verdict unknown and name is non-nikon`() {
        val camera = PtpipCamera(
            ipAddress = "192.168.1.12",
            port = 15740,
            name = "EOS R5",
            vendorVerdict = VendorVerdict.unknown()
        )
        assertFalse(CameraVendorClassifier.isLikelyNikon(camera))
    }

    // ── 12. confidenceRank: CONFIRMED > LIKELY > UNKNOWN ───────────────────────

    @Test
    fun `confidence rank orders confirmed above likely above unknown`() {
        val confirmed = CameraVendorClassifier.confidenceRank(
            VendorVerdict(CameraVendor.NIKON, VendorConfidence.CONFIRMED)
        )
        val likely = CameraVendorClassifier.confidenceRank(
            VendorVerdict(CameraVendor.NIKON, VendorConfidence.LIKELY)
        )
        val unknown = CameraVendorClassifier.confidenceRank(VendorVerdict.unknown())

        assertTrue("CONFIRMED must outrank LIKELY", confirmed > likely)
        assertTrue("LIKELY must outrank UNKNOWN", likely > unknown)
    }
}
