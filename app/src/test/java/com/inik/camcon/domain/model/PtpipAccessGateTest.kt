package com.inik.camcon.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PtpipAccessGate] 단위 테스트.
 *
 * 이 테스트가 고정하는 핵심 불변식은 하나다: **probed=false는 "SSH 불필요"가 아니라
 * "판정하지 못함"이다.** 이 구별이 무너지면 XML 조회에 실패한 소니 카메라가 SSH 불필요로
 * 취급되어 15740 직결을 반복하며 영영 연결되지 않는다.
 */
class PtpipAccessGateTest {

    // ── 1. 판정 실패(probed=false)는 SSH 경로로 분기하지 않는다 ────────────────

    @Test
    fun `unprobed 는 SSH 로 분기하지 않는다`() {
        val gate = PtpipAccessGate.unprobed()

        assertFalse("판정 실패 상태에서는 기존 무인증 경로로 폴백한다", gate.requiresSshTunnel())
    }

    @Test
    fun `probed 가 거짓이면 sshRequired 가 참이어도 SSH 로 분기하지 않는다`() {
        // 판정하지 못한 채 sshRequired 만 참인 값은 신뢰할 수 없다. probed 를 빼먹은 분기가
        // 생기지 않도록 이 모순 조합에서 반드시 거짓이어야 한다.
        val gate = PtpipAccessGate(probed = false, sshRequired = true)

        assertFalse(gate.requiresSshTunnel())
    }

    // ── 2. 판정 성공일 때만 sshRequired 를 따른다 ──────────────────────────────

    @Test
    fun `probed 이고 sshRequired 이면 SSH 로 분기한다`() {
        val gate = PtpipAccessGate(probed = true, sshRequired = true)

        assertTrue(gate.requiresSshTunnel())
    }

    @Test
    fun `probed 이고 sshRequired 가 거짓이면 기존 경로를 쓴다`() {
        val gate = PtpipAccessGate(probed = true, sshRequired = false)

        assertFalse(gate.requiresSshTunnel())
    }

    // ── 3. 기본값 ─────────────────────────────────────────────────────────────

    @Test
    fun `unprobed 의 기본값은 모두 판정 이전 상태다`() {
        val gate = PtpipAccessGate.unprobed()

        assertFalse(gate.probed)
        assertFalse(gate.sshRequired)
        assertFalse(gate.pairingRequired)
        assertNull(gate.serverVersion)
        assertNull(gate.udn)
    }

    // ── 4. PtpipCamera 확장이 기존 동일성 규칙을 건드리지 않는다 ────────────────

    @Test
    fun `PtpipCamera 의 accessGate 기본값은 unprobed 다`() {
        // 기존 positional 호출부가 무변경으로 컴파일되는지도 함께 확인한다.
        val camera = PtpipCamera("192.168.137.130", 15740, "ILCE-7M5")

        assertEquals(PtpipAccessGate.unprobed(), camera.accessGate)
    }

    @Test
    fun `게이트를 채워도 카메라의 주소와 포트는 그대로다`() {
        // 동일성/식별은 계속 ipAddress + port 만 쓴다. 게이트는 식별 축이 아니다.
        val camera = PtpipCamera("192.168.137.130", 15740, "ILCE-7M5")

        val probed = camera.copy(
            accessGate = PtpipAccessGate(
                probed = true,
                sshRequired = true,
                udn = "uuid:00000000-0005-0010-8000-0c802f5bf771"
            )
        )

        assertEquals(camera.ipAddress, probed.ipAddress)
        assertEquals(camera.port, probed.port)
        assertTrue(probed.accessGate.requiresSshTunnel())
    }
}
