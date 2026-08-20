package com.inik.camcon.data.network.ptpip.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 서브넷 스윕 대상 산정 가드(순수 로직).
 *
 * 스윕은 최후 폴백이므로 "언제 실행하지 않는가"가 계약의 핵심이다. 프리픽스가 /24보다 넓으면
 * 호스트 수가 폭발하고(예: /16 = 65,534개) 예산 안에 끝나지 않으면서 네트워크만 흔든다.
 * 자기 자신·네트워크 주소·브로드캐스트를 대상에 넣으면 무의미한 connect가 늘어난다.
 */
class SubnetSweepTargetTest {

    @Test
    fun `24 서브넷은 자기 자신과 네트워크·브로드캐스트를 제외한 253개를 훑는다`() {
        val hosts = SubnetSweepDiscoverySource.hostsOf(SweepTarget("192.168.49.137", 24))

        assertEquals(253, hosts.size)
        assertFalse("자기 자신 제외", hosts.contains("192.168.49.137"))
        assertFalse("네트워크 주소 제외", hosts.contains("192.168.49.0"))
        assertFalse("브로드캐스트 제외", hosts.contains("192.168.49.255"))
        assertTrue(hosts.contains("192.168.49.1"))
        assertTrue(hosts.contains("192.168.49.254"))
    }

    @Test
    fun `24보다 넓은 서브넷은 스윕을 거부한다`() {
        // /16 = 65,534 호스트. 1.5초 예산으로 끝낼 수 없고 네트워크·배터리만 소모한다.
        assertTrue(SubnetSweepDiscoverySource.hostsOf(SweepTarget("192.168.49.137", 16)).isEmpty())
        assertTrue(SubnetSweepDiscoverySource.hostsOf(SweepTarget("10.0.0.5", 8)).isEmpty())
    }

    @Test
    fun `31 32 서브넷은 대상 호스트가 없어 거부한다`() {
        assertTrue(SubnetSweepDiscoverySource.hostsOf(SweepTarget("192.168.49.137", 31)).isEmpty())
        assertTrue(SubnetSweepDiscoverySource.hostsOf(SweepTarget("192.168.49.137", 32)).isEmpty())
    }

    @Test
    fun `30 서브넷은 나머지 호스트 1개만 훑는다`() {
        // 192.168.49.136/30 → 네트워크 .136, 사용 .137·.138, 브로드캐스트 .139
        val hosts = SubnetSweepDiscoverySource.hostsOf(SweepTarget("192.168.49.137", 30))

        assertEquals(listOf("192.168.49.138"), hosts)
    }

    @Test
    fun `잘못된 IP 문자열은 거부한다`() {
        assertTrue(SubnetSweepDiscoverySource.hostsOf(SweepTarget("not-an-ip", 24)).isEmpty())
        assertTrue(SubnetSweepDiscoverySource.hostsOf(SweepTarget("192.168.1", 24)).isEmpty())
        assertTrue(SubnetSweepDiscoverySource.hostsOf(SweepTarget("192.168.1.300", 24)).isEmpty())
    }

    @Test
    fun `IPv4 정수 변환은 왕복해도 값이 보존된다`() {
        listOf("0.0.0.0", "192.168.49.137", "10.0.0.1", "255.255.255.255").forEach { ip ->
            val asInt = SubnetSweepDiscoverySource.ipv4ToInt(ip)
            assertEquals(ip, asInt?.let(SubnetSweepDiscoverySource::intToIpv4))
        }
    }
}
