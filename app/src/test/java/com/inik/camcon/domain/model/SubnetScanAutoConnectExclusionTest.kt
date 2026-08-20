package com.inik.camcon.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 서브넷 스윕 후보의 자동 연결 제외 가드.
 *
 * ⚠️ 이 가드가 무너지면 Nikon 첫 페어링이 깨진다. 스윕 결과에는 mDNS 이름·서비스타입이 전혀 없어
 * `CameraVendorClassifier.isLikelyNikon`이 false를 내고, 그러면 STA 인증(`0x935a` 승인)과 GUID
 * 주입이 생략된 채 연결이 시작돼 InitFail 0x1로 파손된다. 게다가 공용망에서는 타인 기기일 수 있다.
 */
class SubnetScanAutoConnectExclusionTest {

    private fun camera(ip: String, source: CameraDiscoverySource) = PtpipCamera(
        ipAddress = ip,
        port = 15740,
        name = ip,
        isOnline = true,
        discoverySource = source
    )

    @Test
    fun `스윕 후보는 신뢰 링크에서 기지 기기여도 자동 연결하지 않는다`() {
        val known = camera("192.168.49.10", CameraDiscoverySource.SUBNET_SCAN)
        val candidates = CameraSelectionPolicy.buildCandidates(
            cameras = listOf(known),
            knownIp = "192.168.49.10",
            trust = NetworkTrust.TRUSTED_DIRECT_LINK
        )

        val outcome = CameraSelectionPolicy.decide(
            candidates = candidates,
            trust = NetworkTrust.TRUSTED_DIRECT_LINK,
            autoConnectBlocked = false
        )

        assertTrue(
            "스윕 후보가 자동 연결됐다: $outcome",
            outcome is SelectionOutcome.RequireSelection
        )
    }

    @Test
    fun `스윕 후보는 신뢰 링크에서도 항상 확인을 요구한다`() {
        val candidates = CameraSelectionPolicy.buildCandidates(
            cameras = listOf(camera("192.168.49.10", CameraDiscoverySource.SUBNET_SCAN)),
            knownIp = "192.168.49.10",
            trust = NetworkTrust.TRUSTED_DIRECT_LINK
        )

        assertTrue("확인 없이 즉시 연결된다", candidates.single().requiresConfirm)
    }

    @Test
    fun `mDNS 기지 후보는 스윕 후보가 섞여도 자동 연결된다`() {
        val cameras = listOf(
            camera("192.168.49.10", CameraDiscoverySource.MDNS),
            camera("192.168.49.77", CameraDiscoverySource.SUBNET_SCAN)
        )
        val candidates = CameraSelectionPolicy.buildCandidates(
            cameras = cameras,
            knownIp = "192.168.49.10",
            trust = NetworkTrust.TRUSTED_DIRECT_LINK
        )

        val outcome = CameraSelectionPolicy.decide(
            candidates = candidates,
            trust = NetworkTrust.TRUSTED_DIRECT_LINK,
            autoConnectBlocked = false
        )

        assertTrue(outcome is SelectionOutcome.AutoConnect)
        assertEquals(
            "192.168.49.10",
            (outcome as SelectionOutcome.AutoConnect).camera.ipAddress
        )
    }

    @Test
    fun `라이브 신호가 스윕보다 우선 정렬된다`() {
        val candidates = CameraSelectionPolicy.buildCandidates(
            cameras = listOf(
                camera("192.168.49.77", CameraDiscoverySource.SUBNET_SCAN),
                camera("192.168.49.10", CameraDiscoverySource.MDNS)
            ),
            knownIp = null,
            trust = NetworkTrust.TRUSTED_DIRECT_LINK
        )

        assertEquals(
            CameraDiscoverySource.MDNS,
            candidates.first().camera.discoverySource
        )
    }
}
