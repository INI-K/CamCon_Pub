package com.inik.camcon.data.network.ptpip.discovery

import com.inik.camcon.domain.model.CameraVendor
import com.inik.camcon.domain.model.VendorConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * SSDP 헤더 파서·판별 매핑 단위 테스트.
 *
 * 순수 함수(`SsdpDiscoveryService.parseSsdpHeaders`, `CameraVendorClassifier.classifySsdp`)만
 * 검증하므로 안드로이드/네트워크 의존이 없다(Robolectric 불필요).
 * 실측 Canon 6D의 NOTIFY/200 OK 원문을 기준으로 파싱 정확성을 확인한다.
 */
class SsdpDiscoveryServiceTest {

    // 실측 Canon 200 OK 응답 (M-SEARCH 유니캐스트 응답 형태)
    private val canon200Ok =
        "HTTP/1.1 200 OK\r\n" +
            "CACHE-CONTROL: max-age=1800\r\n" +
            "ST: urn:schemas-canon-com:service:ICPO-SmartPhoneEOSSystemService:1\r\n" +
            "USN: uuid:00000000-0000-0000-0000-000000000000::" +
            "urn:schemas-canon-com:service:ICPO-SmartPhoneEOSSystemService:1\r\n" +
            "SERVER: Camera OS/1.0 UPnP/1.0 Canon Device Discovery/1.0\r\n" +
            "LOCATION: http://192.168.1.2:49152/desc.xml\r\n" +
            "\r\n"

    // 실측 Canon NOTIFY 알림 (NT/NTS 헤더 사용)
    private val canonNotify =
        "NOTIFY * HTTP/1.1\r\n" +
            "HOST: 239.255.255.250:1900\r\n" +
            "NT: urn:schemas-canon-com:service:ICPO-SmartPhoneEOSSystemService:1\r\n" +
            "NTS: ssdp:alive\r\n" +
            "USN: uuid:00000000-0000-0000-0000-000000000000::" +
            "urn:schemas-canon-com:service:ICPO-SmartPhoneEOSSystemService:1\r\n" +
            "SERVER: Camera OS/1.0 UPnP/1.0 Canon Device Discovery/1.0\r\n" +
            "\r\n"

    @Test
    fun `Canon 200 OK 원문에서 핵심 헤더를 정확히 파싱한다`() {
        val headers = SsdpDiscoveryService.parseSsdpHeaders(canon200Ok)

        assertEquals(
            "urn:schemas-canon-com:service:ICPO-SmartPhoneEOSSystemService:1",
            headers["ST"]
        )
        assertEquals(
            "Camera OS/1.0 UPnP/1.0 Canon Device Discovery/1.0",
            headers["SERVER"]
        )
        assertEquals("http://192.168.1.2:49152/desc.xml", headers["LOCATION"])
        assertEquals("max-age=1800", headers["CACHE-CONTROL"])
    }

    @Test
    fun `NOTIFY 원문의 NT USN 헤더를 파싱한다`() {
        val headers = SsdpDiscoveryService.parseSsdpHeaders(canonNotify)

        assertEquals(
            "urn:schemas-canon-com:service:ICPO-SmartPhoneEOSSystemService:1",
            headers["NT"]
        )
        assertEquals("ssdp:alive", headers["NTS"])
        // status/request line(0번째 줄)은 헤더로 취급되지 않는다
        assertNull(headers["NOTIFY * HTTP/1.1"])
    }

    @Test
    fun `대소문자 섞인 헤더명을 대문자로 정규화한다`() {
        val text =
            "HTTP/1.1 200 OK\r\n" +
                "St: urn:schemas-sony-com:service:ScalarWebAPI:1\r\n" +
                "server: UPnP/1.0 SonyImagingDevice/1.0\r\n" +
                "uSn: uuid:abcd::urn:schemas-sony-com:service:ScalarWebAPI:1\r\n" +
                "\r\n"

        val headers = SsdpDiscoveryService.parseSsdpHeaders(text)

        assertEquals(
            "urn:schemas-sony-com:service:ScalarWebAPI:1",
            headers["ST"]
        )
        assertEquals("UPnP/1.0 SonyImagingDevice/1.0", headers["SERVER"])
        assertEquals(
            "uuid:abcd::urn:schemas-sony-com:service:ScalarWebAPI:1",
            headers["USN"]
        )
    }

    @Test
    fun `값에 콜론이 포함된 URN을 첫 콜론 기준으로 분리한다`() {
        val text =
            "HTTP/1.1 200 OK\r\n" +
                "ST: urn:schemas-canon-com:service:ICPO-WFTEOSSystemService:1\r\n" +
                "\r\n"

        val headers = SsdpDiscoveryService.parseSsdpHeaders(text)

        // "ST" 이후의 콜론들은 값에 그대로 보존되어야 한다
        assertEquals(
            "urn:schemas-canon-com:service:ICPO-WFTEOSSystemService:1",
            headers["ST"]
        )
    }

    @Test
    fun `CRLF와 LF 혼용을 허용한다`() {
        val text =
            "HTTP/1.1 200 OK\n" +
                "ST: urn:schemas-canon-com:service:ICPO-SmartPhoneEOSSystemService:1\r\n" +
                "SERVER: Canon Device Discovery/1.0\n" +
                "\n"

        val headers = SsdpDiscoveryService.parseSsdpHeaders(text)

        assertEquals(
            "urn:schemas-canon-com:service:ICPO-SmartPhoneEOSSystemService:1",
            headers["ST"]
        )
        assertEquals("Canon Device Discovery/1.0", headers["SERVER"])
    }

    @Test
    fun `콜론 없는 줄과 빈 줄은 무시한다`() {
        val text =
            "HTTP/1.1 200 OK\r\n" +
                "\r\n" +
                "garbage-line-without-colon\r\n" +
                "ST: upnp:rootdevice\r\n" +
                "\r\n"

        val headers = SsdpDiscoveryService.parseSsdpHeaders(text)

        assertEquals(1, headers.size)
        assertEquals("upnp:rootdevice", headers["ST"])
    }

    @Test
    fun `파싱된 Canon 헤더는 classifySsdp에서 CANON CONFIRMED로 판별된다`() {
        val headers = SsdpDiscoveryService.parseSsdpHeaders(canon200Ok)
        val verdict = CameraVendorClassifier.classifySsdp(
            headers["ST"],
            headers["USN"],
            headers["SERVER"]
        )

        assertEquals(CameraVendor.CANON, verdict.vendor)
        assertEquals(VendorConfidence.CONFIRMED, verdict.confidence)
    }

    @Test
    fun `upnp rootdevice 잡음은 UNKNOWN으로 판별되어 필터 대상이 된다`() {
        val text =
            "HTTP/1.1 200 OK\r\n" +
                "ST: upnp:rootdevice\r\n" +
                "USN: uuid:router-0000::upnp:rootdevice\r\n" +
                "SERVER: Linux/3.14 UPnP/1.0 MiniUPnPd/1.9\r\n" +
                "\r\n"

        val headers = SsdpDiscoveryService.parseSsdpHeaders(text)
        val verdict = CameraVendorClassifier.classifySsdp(
            headers["ST"],
            headers["USN"],
            headers["SERVER"]
        )

        assertEquals(CameraVendor.UNKNOWN, verdict.vendor)
    }
}
