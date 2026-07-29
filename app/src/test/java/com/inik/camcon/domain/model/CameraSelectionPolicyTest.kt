package com.inik.camcon.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CameraSelectionPolicy] 순수 함수 회귀 테스트.
 *
 * 회귀 배경: 0/1/2+ 분기와 자동 연결 허용 판정이 `PtpipDiscoveryHelper`에 박혀 있어
 * (`cameras.first()` → `delay(500)` → `connectToCamera`) 단위 테스트가 불가능했고,
 * 그 결과 "후보 2대 중 아무거나 자동 연결"이 정상 동작으로 취급됐다.
 */
class CameraSelectionPolicyTest {

    private fun camera(
        ip: String,
        name: String = "Z_8_5003869",
        vendor: CameraVendor = CameraVendor.UNKNOWN,
        confidence: VendorConfidence = VendorConfidence.UNKNOWN,
        source: CameraDiscoverySource = CameraDiscoverySource.MDNS
    ) = PtpipCamera(
        ipAddress = ip,
        port = 15740,
        name = name,
        vendorVerdict = VendorVerdict(vendor, confidence),
        discoverySource = source
    )

    private fun wifiState(
        connected: Boolean = false,
        cameraAp: Boolean = false,
        hotspot: Boolean = false
    ) = WifiNetworkState(
        isConnected = connected,
        isConnectedToCameraAP = cameraAp,
        ssid = null,
        detectedCameraIP = null,
        isHotspotEnabled = hotspot
    )

    // ───────────────────────── trustOf ─────────────────────────

    @Test
    fun `카메라 AP 직결은 신뢰 링크`() {
        assertEquals(
            NetworkTrust.TRUSTED_DIRECT_LINK,
            CameraSelectionPolicy.trustOf(wifiState(connected = true, cameraAp = true))
        )
    }

    @Test
    fun `핫스팟 켜짐 + 클라이언트 미연결은 신뢰 링크(폰이 게이트웨이)`() {
        assertEquals(
            NetworkTrust.TRUSTED_DIRECT_LINK,
            CameraSelectionPolicy.trustOf(wifiState(connected = false, hotspot = true))
        )
    }

    @Test
    fun `핫스팟 켜짐 + 클라이언트 연결(STA 동시)은 공유 네트워크`() {
        assertEquals(
            NetworkTrust.UNTRUSTED_SHARED,
            CameraSelectionPolicy.trustOf(wifiState(connected = true, hotspot = true))
        )
    }

    @Test
    fun `셋 다 false면 네트워크 없음`() {
        assertEquals(NetworkTrust.NO_NETWORK, CameraSelectionPolicy.trustOf(wifiState()))
    }

    @Test
    fun `공유기 클라이언트 연결은 공유 네트워크`() {
        assertEquals(
            NetworkTrust.UNTRUSTED_SHARED,
            CameraSelectionPolicy.trustOf(wifiState(connected = true))
        )
    }

    // ───────────────────────── buildCandidates ─────────────────────────

    @Test
    fun `기지 IP 후보만 isKnown이 true다`() {
        val candidates = CameraSelectionPolicy.buildCandidates(
            cameras = listOf(camera("192.168.49.10"), camera("192.168.49.20")),
            knownIp = "192.168.49.20",
            trust = NetworkTrust.TRUSTED_DIRECT_LINK
        )
        assertEquals("192.168.49.20", candidates.first().camera.ipAddress)
        assertTrue(candidates.first().isKnown)
        assertFalse(candidates.last().isKnown)
    }

    @Test
    fun `빈 knownIp는 어떤 후보도 기지로 만들지 않는다`() {
        val candidates = CameraSelectionPolicy.buildCandidates(
            cameras = listOf(camera("192.168.49.10")),
            knownIp = "   ",
            trust = NetworkTrust.TRUSTED_DIRECT_LINK
        )
        assertFalse(candidates.single().isKnown)
    }

    @Test
    fun `비신뢰 링크에서 기지 아닌 후보는 확인 다이얼로그가 필요하다`() {
        val candidates = CameraSelectionPolicy.buildCandidates(
            cameras = listOf(camera("192.168.0.11"), camera("192.168.0.12")),
            knownIp = "192.168.0.12",
            trust = NetworkTrust.UNTRUSTED_SHARED
        )
        val known = candidates.first { it.isKnown }
        val unknown = candidates.first { !it.isKnown }
        assertFalse("기지 기기는 확인 없이 연결 가능", known.requiresConfirm)
        assertTrue("비신뢰 링크의 낯선 카메라는 확인 필요", unknown.requiresConfirm)
    }

    @Test
    fun `신뢰 링크에서는 확인 다이얼로그가 필요 없다`() {
        val candidates = CameraSelectionPolicy.buildCandidates(
            cameras = listOf(camera("192.168.49.10")),
            knownIp = null,
            trust = NetworkTrust.TRUSTED_DIRECT_LINK
        )
        assertFalse(candidates.single().requiresConfirm)
    }

    @Test
    fun `정렬 - 기지 우선 다음 verdict 신뢰도 다음 출처 마지막 IP(결정적)`() {
        val cameras = listOf(
            camera("192.168.49.50", source = CameraDiscoverySource.CACHED_IP),
            camera(
                "192.168.49.40",
                vendor = CameraVendor.NIKON,
                confidence = VendorConfidence.LIKELY
            ),
            camera(
                "192.168.49.30",
                vendor = CameraVendor.NIKON,
                confidence = VendorConfidence.CONFIRMED
            ),
            camera("192.168.49.20", source = CameraDiscoverySource.MDNS),
            camera("192.168.49.99") // 기지
        )
        val ordered = CameraSelectionPolicy.buildCandidates(
            cameras = cameras,
            knownIp = "192.168.49.99",
            trust = NetworkTrust.TRUSTED_DIRECT_LINK
        ).map { it.camera.ipAddress }

        assertEquals(
            listOf(
                "192.168.49.99", // isKnown
                "192.168.49.30", // CONFIRMED
                "192.168.49.40", // LIKELY
                "192.168.49.20", // UNKNOWN + MDNS
                "192.168.49.50"  // UNKNOWN + CACHED_IP
            ),
            ordered
        )

        // 결정성: 입력 순서를 뒤집어도 같은 결과.
        val reversed = CameraSelectionPolicy.buildCandidates(
            cameras = cameras.reversed(),
            knownIp = "192.168.49.99",
            trust = NetworkTrust.TRUSTED_DIRECT_LINK
        ).map { it.camera.ipAddress }
        assertEquals(ordered, reversed)
    }

    // ───────────────────────── decide ─────────────────────────

    private fun candidates(
        cameras: List<PtpipCamera>,
        knownIp: String?,
        trust: NetworkTrust
    ) = CameraSelectionPolicy.buildCandidates(cameras, knownIp, trust)

    @Test
    fun `후보 0건은 Empty`() {
        assertEquals(
            SelectionOutcome.Empty,
            CameraSelectionPolicy.decide(emptyList(), NetworkTrust.TRUSTED_DIRECT_LINK, false)
        )
    }

    @Test
    fun `신뢰 링크 + 기지 1개(후보 1) - 자동 연결`() {
        val trust = NetworkTrust.TRUSTED_DIRECT_LINK
        val outcome = CameraSelectionPolicy.decide(
            candidates(listOf(camera("192.168.49.10")), "192.168.49.10", trust),
            trust,
            autoConnectBlocked = false
        )
        assertTrue(outcome is SelectionOutcome.AutoConnect)
        assertEquals(
            "192.168.49.10",
            (outcome as SelectionOutcome.AutoConnect).camera.ipAddress
        )
    }

    @Test
    fun `신뢰 링크 + 기지 0개(첫 페어링) - 사용자 선택 필요`() {
        val trust = NetworkTrust.TRUSTED_DIRECT_LINK
        val outcome = CameraSelectionPolicy.decide(
            candidates(listOf(camera("192.168.49.10")), null, trust),
            trust,
            autoConnectBlocked = false
        )
        assertTrue(
            "첫 페어링은 Nikon 승인 60초 락을 사용자 의도 없이 시작하지 않는다",
            outcome is SelectionOutcome.RequireSelection
        )
    }

    @Test
    fun `신뢰 링크 + 기지 1개(후보 3) - 그 후보에 자동 연결`() {
        val trust = NetworkTrust.TRUSTED_DIRECT_LINK
        val cameras = listOf(
            camera("192.168.49.10"),
            camera("192.168.49.20"),
            camera("192.168.49.30")
        )
        val outcome = CameraSelectionPolicy.decide(
            candidates(cameras, "192.168.49.20", trust),
            trust,
            autoConnectBlocked = false
        )
        assertTrue(outcome is SelectionOutcome.AutoConnect)
        assertEquals(
            "192.168.49.20",
            (outcome as SelectionOutcome.AutoConnect).camera.ipAddress
        )
    }

    @Test
    fun `비신뢰 링크 + 기지 1개 - 사용자 선택 필요`() {
        val trust = NetworkTrust.UNTRUSTED_SHARED
        val outcome = CameraSelectionPolicy.decide(
            candidates(listOf(camera("192.168.0.11")), "192.168.0.11", trust),
            trust,
            autoConnectBlocked = false
        )
        assertTrue(outcome is SelectionOutcome.RequireSelection)
    }

    @Test
    fun `autoConnectBlocked면 어떤 조합에서도 사용자 선택 필요`() {
        val trust = NetworkTrust.TRUSTED_DIRECT_LINK
        val outcome = CameraSelectionPolicy.decide(
            candidates(listOf(camera("192.168.49.10")), "192.168.49.10", trust),
            trust,
            autoConnectBlocked = true
        )
        assertTrue(outcome is SelectionOutcome.RequireSelection)
    }

    @Test
    fun `수동 입력 후보는 기지여도 자동 연결 대상이 아니다`() {
        val trust = NetworkTrust.TRUSTED_DIRECT_LINK
        val manual = camera("192.168.49.10", source = CameraDiscoverySource.MANUAL_INPUT)
        val outcome = CameraSelectionPolicy.decide(
            candidates(listOf(manual), "192.168.49.10", trust),
            trust,
            autoConnectBlocked = false
        )
        assertTrue(outcome is SelectionOutcome.RequireSelection)
    }

    @Test
    fun `기지 후보가 2개 이상이면 자동 연결하지 않는다`() {
        val trust = NetworkTrust.TRUSTED_DIRECT_LINK
        // 같은 IP로 포트만 다른 두 후보 = 기지 판정이 2건 → 모호하므로 사용자 선택.
        val cameras = listOf(
            camera("192.168.49.10"),
            camera("192.168.49.10").copy(port = 15741)
        )
        val outcome = CameraSelectionPolicy.decide(
            candidates(cameras, "192.168.49.10", trust),
            trust,
            autoConnectBlocked = false
        )
        assertTrue(outcome is SelectionOutcome.RequireSelection)
    }
}
