package com.inik.camcon.data.network.ptpip.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * RFC 6763 메타 쿼리 결과 재조립 가드.
 *
 * 메타 쿼리(`_services._dns-sd._udp`)는 **인스턴스가 아니라 타입 레이블**을 돌려준다
 * (`serviceName="_ptp"`, `serviceType="_tcp.local."`). 이걸 `_ptp._tcp`로 재조립해 2차 discover를
 * 걸어야 실제 카메라가 잡힌다. 재조립이 깨지면 메타 쿼리가 통째로 무효가 되고, 제조사별 타입
 * 하드코딩을 제거한 근거도 함께 무너진다.
 */
class MetaServiceTypeTest {

    @Test
    fun `표준 메타 결과를 2차 검색용 타입으로 재조립한다`() {
        assertEquals(
            "_ptp._tcp",
            PtpipDiscoveryService.reassembleMetaServiceType("_ptp", "_tcp.local.")
        )
        assertEquals(
            "_nikon._tcp",
            PtpipDiscoveryService.reassembleMetaServiceType("_nikon", "_tcp.local.")
        )
    }

    @Test
    fun `점 표기 변형을 견딘다`() {
        // 스택/버전에 따라 트레일링 점·local 유무가 다르다.
        listOf("_tcp.local.", "_tcp.local", "_tcp.", "_tcp").forEach { proto ->
            assertEquals(
                "재조립 실패: proto=$proto",
                "_canon-ptpip._tcp",
                PtpipDiscoveryService.reassembleMetaServiceType("_canon-ptpip", proto)
            )
        }
    }

    @Test
    fun `제조사를 모르는 미지 타입도 그대로 따라간다`() {
        // 하드코딩 목록에 없는 타입이 바로 이 기능의 존재 이유다.
        assertEquals(
            "_someunknownvendor._tcp",
            PtpipDiscoveryService.reassembleMetaServiceType("_someunknownvendor", "_tcp.local.")
        )
    }

    @Test
    fun `UDP 타입은 따라가지 않는다`() {
        // PTP/IP는 TCP다. UDP까지 따라가면 한정된 listener 슬롯만 소모한다.
        assertNull(PtpipDiscoveryService.reassembleMetaServiceType("_ntp", "_udp.local."))
    }

    @Test
    fun `언더스코어로 시작하지 않거나 비어 있으면 거부한다`() {
        assertNull(PtpipDiscoveryService.reassembleMetaServiceType("ptp", "_tcp.local."))
        assertNull(PtpipDiscoveryService.reassembleMetaServiceType("", "_tcp.local."))
        assertNull(PtpipDiscoveryService.reassembleMetaServiceType(null, "_tcp.local."))
        assertNull(PtpipDiscoveryService.reassembleMetaServiceType("_ptp", null))
        assertNull(PtpipDiscoveryService.reassembleMetaServiceType("_ptp", ""))
    }
}
