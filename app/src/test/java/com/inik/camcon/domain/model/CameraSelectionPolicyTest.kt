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
    fun `같은 IP를 두 포트로 광고해도 한 기기로 접어 자동 연결한다`() {
        val trust = NetworkTrust.TRUSTED_DIRECT_LINK
        // 후보 키가 IP:port라 같은 본체가 2건으로 잡힌다. IP는 곧 기기 1대이므로 사용자에게
        // 물을 이유가 없다 — 물으면 목록에 같은 카메라가 두 번 보일 뿐이고, 사용자에게는
        // "기억한 카메라에 자동 연결"이 원인 불명으로 안 되는 증상이 된다.
        val cameras = listOf(
            camera("192.168.49.10").copy(port = 15741),
            camera("192.168.49.10")
        )
        val outcome = CameraSelectionPolicy.decide(
            candidates(cameras, "192.168.49.10", trust),
            trust,
            autoConnectBlocked = false
        )

        assertTrue("같은 IP 2건이 자동 연결을 막았다: $outcome", outcome is SelectionOutcome.AutoConnect)
        // 접을 때는 표준 포트를 결정적으로 고른다(입력 순서에 의존하면 안 된다).
        assertEquals(15740, (outcome as SelectionOutcome.AutoConnect).camera.port)
    }

    @Test
    fun `서로 다른 기기가 2대면 사용자 선택을 요구한다`() {
        val trust = NetworkTrust.TRUSTED_DIRECT_LINK
        // IP가 다르면 실제로 다른 기기다 — 이때만 모호하므로 물어야 한다.
        // (knownIp가 둘 다에 매칭되도록 serviceName을 공유시킨다)
        val cameras = listOf(
            camera("192.168.49.10"),
            camera("192.168.49.11")
        )
        val known = KnownCameraRef(serviceName = cameras.first().name)
        val built = CameraSelectionPolicy.buildCandidates(cameras, known, trust)

        val outcome = CameraSelectionPolicy.decide(
            built.map { it.copy(isKnown = true) },
            trust,
            autoConnectBlocked = false
        )

        assertTrue(outcome is SelectionOutcome.RequireSelection)
    }

    @Test
    fun `후지 포크 포트 후보도 기지면 자동 연결한다`() {
        val trust = NetworkTrust.TRUSTED_DIRECT_LINK
        // 네이티브 selectPtpipModel(55740 → "Fuji X (WLAN)")로 전송 경로가 열렸으므로
        // 후지 후보를 자동 연결에서 배제할 이유가 없어졌다.
        val fuji = camera("192.168.49.10").copy(port = CameraProtocol.PTPIP_FUJI.port)

        val outcome = CameraSelectionPolicy.decide(
            candidates(listOf(fuji), "192.168.49.10", trust),
            trust,
            autoConnectBlocked = false
        )

        assertTrue("후지 후보가 자동 연결에서 막혔다: $outcome", outcome is SelectionOutcome.AutoConnect)
    }

    @Test
    fun `연결 불가 프로토콜은 자동 연결 대상에서 제외된다`() {
        // 현재 모든 스윕 포트가 연결 가능하므로 이 가드는 향후 프로토콜 추가를 위한 것이다.
        // 계약만 고정한다 — 연결 불가 프로토콜이 생기면 decide()가 걸러야 한다.
        assertTrue(
            "연결 불가 프로토콜이 추가되면 자동 연결 필터와 이 테스트를 함께 갱신할 것",
            CameraProtocol.entries.all { it.isConnectable }
        )
    }
}
