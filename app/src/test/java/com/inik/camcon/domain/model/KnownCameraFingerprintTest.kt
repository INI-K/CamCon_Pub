package com.inik.camcon.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 본체 지문 + 기억된 카메라 판정 가드.
 *
 * 이 파일이 지키는 두 가지 사고:
 * 1. **`"None"` 지문 오인** — libgphoto2는 카메라가 시리얼을 보고하지 않으면 리터럴 `"None"`을 준다.
 *    유효 지문으로 받으면 같은 기종 두 대의 지문이 동일해져 렌탈샵·스튜디오에서 **남의 바디**가
 *    자동 연결 게이트를 통과한다.
 * 2. **그랜드파더링 파손** — 기존 사용자에게 저장된 것은 IP·이름뿐이다. 승인 절차를 소급 적용하면
 *    업데이트 직후 자동 연결이 무증상 사망한다(배경 폴링 경로에 승인 UI가 없다).
 */
class KnownCameraFingerprintTest {

    private fun camera(ip: String, name: String) = PtpipCamera(
        ipAddress = ip,
        port = 15740,
        name = name,
        isOnline = true
    )

    // ── 지문 유효성 ──

    @Test
    fun `libgphoto2의 None 시리얼은 지문으로 인정하지 않는다`() {
        assertNull(CameraFingerprint.of("None", "Z 8"))
        assertNull(CameraFingerprint.of("none", "Z 8"))
        assertNull(CameraFingerprint.of(" NONE ", "Z 8"))
        assertFalse(CameraFingerprint.isValid("None|Z 8"))
    }

    @Test
    fun `빈 시리얼이나 빈 모델은 지문을 만들지 않는다`() {
        assertNull(CameraFingerprint.of("", "Z 8"))
        assertNull(CameraFingerprint.of(null, "Z 8"))
        assertNull(CameraFingerprint.of("5003869", ""))
        assertNull(CameraFingerprint.of("5003869", null))
    }

    @Test
    fun `유효한 시리얼과 모델은 지문이 된다`() {
        val fingerprint = CameraFingerprint.of("5003869", "Z 8")

        assertEquals("5003869|Z 8", fingerprint)
        assertTrue(CameraFingerprint.isValid(fingerprint))
    }

    // ── 기지 기기 판정 ──

    @Test
    fun `IP가 바뀌어도 mDNS 인스턴스명이 같으면 기지 기기다`() {
        // DHCP 재할당으로 IP만 바뀐 상황. IP 단독 판정이면 자동 연결이 조용히 죽는다.
        val known = KnownCameraRef(ipHint = "192.168.49.10", serviceName = "Z_8_5003869")

        assertTrue(known.matches(camera("192.168.49.77", "Z_8_5003869")))
    }

    @Test
    fun `이름이 달라도 IP 힌트가 맞으면 기지 기기다`() {
        val known = KnownCameraRef(ipHint = "192.168.49.10", serviceName = "Z_8_5003869")

        assertTrue(known.matches(camera("192.168.49.10", "")))
    }

    @Test
    fun `이름도 IP도 다르면 기지 기기가 아니다`() {
        val known = KnownCameraRef(ipHint = "192.168.49.10", serviceName = "Z_8_5003869")

        assertFalse(known.matches(camera("192.168.49.77", "Z_6_5000784")))
    }

    @Test
    fun `첫 실행은 비어 있는 기억이고 어떤 후보도 기지가 아니다`() {
        val known = KnownCameraRef()

        assertTrue(known.isEmpty())
        assertFalse(known.matches(camera("192.168.49.10", "Z_8_5003869")))
    }

    // ── 다른 본체 판정 ──

    @Test
    fun `양쪽 지문이 유효하고 다르면 다른 본체다`() {
        val known = KnownCameraRef(fingerprint = "5003869|Z 8")

        assertTrue(known.isDifferentBody("7009999|Z 8"))
    }

    @Test
    fun `지문이 한쪽만 있으면 다른 본체로 단정하지 않는다`() {
        // 시리얼 미보고·abilities 파싱 실패 경로. 단정하면 정상 사용자의 자동 연결이 끊긴다.
        assertFalse(KnownCameraRef(fingerprint = "5003869|Z 8").isDifferentBody(null))
        assertFalse(KnownCameraRef(fingerprint = null).isDifferentBody("5003869|Z 8"))
        assertFalse(KnownCameraRef(fingerprint = "None|Z 8").isDifferentBody("None|Z 8"))
    }

    @Test
    fun `같은 지문이면 다른 본체가 아니다`() {
        val known = KnownCameraRef(fingerprint = "5003869|Z 8")

        assertFalse(known.isDifferentBody("5003869|Z 8"))
    }

    // ── 그랜드파더링 / 승인 회수 ──

    @Test
    fun `승인 플래그 기본값은 true다 - 기존 사용자 자동연결 보존`() {
        val migrated = KnownCameraRef(ipHint = "192.168.49.10", serviceName = "Z_8_5003869")

        assertTrue("그랜드파더링 실패 - 업데이트 직후 자동연결 사망", migrated.autoConnectApproved)
    }

    @Test
    fun `승인이 회수되면 신뢰 링크의 기지 기기여도 자동 연결하지 않는다`() {
        val known = KnownCameraRef(
            ipHint = "192.168.49.10",
            serviceName = "Z_8_5003869",
            autoConnectApproved = false
        )
        val candidates = CameraSelectionPolicy.buildCandidates(
            cameras = listOf(
                camera("192.168.49.10", "Z_8_5003869")
                    .copy(discoverySource = CameraDiscoverySource.MDNS)
            ),
            known = known,
            trust = NetworkTrust.TRUSTED_DIRECT_LINK
        )

        val outcome = CameraSelectionPolicy.decide(
            candidates = candidates,
            trust = NetworkTrust.TRUSTED_DIRECT_LINK,
            autoConnectBlocked = false,
            autoConnectApproved = known.autoConnectApproved
        )

        assertTrue(
            "승인 회수 상태에서 자동 연결됐다: $outcome",
            outcome is SelectionOutcome.RequireSelection
        )
    }

    @Test
    fun `그랜드파더링된 기존 사용자는 이름 일치로 자동 연결된다`() {
        // IP가 바뀐 기존 사용자. 지문은 없다(연결 전이므로 알 수 없고, 과거 기록에도 없다).
        val known = KnownCameraRef(ipHint = "192.168.49.10", serviceName = "Z_8_5003869")
        val candidates = CameraSelectionPolicy.buildCandidates(
            cameras = listOf(
                camera("192.168.49.88", "Z_8_5003869")
                    .copy(discoverySource = CameraDiscoverySource.MDNS)
            ),
            known = known,
            trust = NetworkTrust.TRUSTED_DIRECT_LINK
        )

        val outcome = CameraSelectionPolicy.decide(
            candidates = candidates,
            trust = NetworkTrust.TRUSTED_DIRECT_LINK,
            autoConnectBlocked = false,
            autoConnectApproved = known.autoConnectApproved
        )

        assertTrue(outcome is SelectionOutcome.AutoConnect)
        assertEquals(
            "192.168.49.88",
            (outcome as SelectionOutcome.AutoConnect).camera.ipAddress
        )
    }
}
