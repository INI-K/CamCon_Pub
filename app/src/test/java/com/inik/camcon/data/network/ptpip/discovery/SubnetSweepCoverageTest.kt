package com.inik.camcon.data.network.ptpip.discovery

import com.inik.camcon.domain.model.CameraProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 스윕 커버리지 가드.
 *
 * 회귀 배경: in-flight 슬롯은 **완료돼야만** 반환되는데 응답 없는 주소는 커널 ARP 재시도(≈3s)까지
 * selectable이 되지 않는다. `MAX_IN_FLIGHT=64` + 예산 2.5s + **호스트-major** 정렬 조합에서는
 * 첫 64개 엔드포인트(= 앞쪽 32호스트, 그마저 절반은 연결 불가 포트 55740)가 예산 전체를 점유해
 * /24의 대부분이 영영 프로브되지 않았다. 사용자에겐 "스윕을 눌러도 못 찾음"으로만 보였다.
 *
 * 그래서 이 파일은 **대상 순서와 예산 산정 규약**을 고정한다.
 */
class SubnetSweepCoverageTest {

    private fun targetsOf(hosts: List<String>): List<Pair<String, Int>> =
        CameraProtocol.SWEEP_PORTS.flatMap { port -> hosts.map { host -> host to port } }

    @Test
    fun `대상은 포트-major 정렬이다 - 전 호스트를 표준 포트로 먼저 훑는다`() {
        val hosts = listOf("192.168.49.2", "192.168.49.3", "192.168.49.4")

        val targets = targetsOf(hosts)

        // 앞쪽 절반이 전부 15740이어야 한다. 호스트-major면 [h2:15740, h2:55740, h3:15740...]이 되어
        // in-flight 앞부분의 절반이 연결 불가 포트로 낭비된다.
        val firstHalf = targets.take(hosts.size)
        assertTrue(
            "포트-major가 아님: ${firstHalf.map { it.second }}",
            firstHalf.all { it.second == CameraProtocol.PTPIP_STANDARD.port }
        )
        assertEquals(hosts.size * CameraProtocol.SWEEP_PORTS.size, targets.size)
    }

    @Test
    fun `24 서브넷 전체가 첫 배치에 들어간다`() {
        // /24 = 253 호스트. in-flight 상한이 이보다 작으면 무응답 주소가 슬롯을 붙잡는 동안
        // 뒤쪽 호스트가 예산 안에 시도조차 되지 않는다.
        val hosts = SubnetSweepDiscoverySource.hostsOf(SweepTarget("192.168.49.137", 24))

        assertEquals(253, hosts.size)
        assertTrue(
            "in-flight 상한이 /24 호스트 수보다 작으면 커버리지가 잘린다",
            SubnetSweepDiscoverySource.maxInFlightForTest >= hosts.size
        )
    }

    @Test
    fun `예산은 커널 ARP 재시도보다 길다`() {
        // 무응답 주소는 ARP 재시도(약 3초) 뒤에야 실패로 확정된다. 예산이 그보다 짧으면
        // 1차 포트 배치가 끝나기 전에 소진돼 2차 포트를 아예 훑지 못한다.
        assertTrue(
            "예산 ${SubnetSweepDiscoverySource.DEFAULT_BUDGET_MS}ms 는 ARP 실패(≈3000ms)보다 길어야 한다",
            SubnetSweepDiscoverySource.DEFAULT_BUDGET_MS > 3_000L
        )
    }
}
