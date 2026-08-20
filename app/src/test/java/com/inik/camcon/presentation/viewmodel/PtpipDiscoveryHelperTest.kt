package com.inik.camcon.presentation.viewmodel

import com.inik.camcon.domain.model.DiscoveryAttemptResult
import com.inik.camcon.domain.model.DiscoveryEmptyReason
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.UiText
import com.inik.camcon.domain.model.WifiNetworkState
import com.inik.camcon.domain.repository.PtpipRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [PtpipDiscoveryHelper.discoverCameras] 단위 테스트.
 *
 * 회귀 1: 폰 핫스팟(STA_PHONE_HOTSPOT) 모드에선 폰이 SoftAP 라 Wi-Fi 클라이언트 연결이 없어
 * `isWifiConnected()=false` 가 정상인데, 게이트가 이를 "Wi-Fi 미연결"로 오판해 검색을 중단하던 버그.
 * (실기기 로그: "사용자가 카메라 검색을 요청했습니다" → "Wi-Fi가 연결되어 있지 않습니다" → 즉시 종료)
 *
 * 회귀 2: 이 헬퍼가 `cameras.first()`를 자동 선택해 바로 연결까지 수행했다. `@Singleton` +
 * `@ApplicationScope`라서 화면이 종료된 뒤에도 연결이 계속 진행되고 취소도 불가능했다.
 * 이제 헬퍼는 **검색 전용**이며 사유(reason)만 반환한다 — 연결 결정은 ViewModel + 정책이 담당한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PtpipDiscoveryHelperTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: PtpipRepository
    private lateinit var helper: PtpipDiscoveryHelper

    @Before
    fun setup() {
        repo = mockk(relaxed = true)
        every { repo.isDiscoveryBlocked() } returns false
        every { repo.discoveredCameras } returns MutableStateFlow(emptyList())
        helper = PtpipDiscoveryHelper(repo, CoroutineScope(dispatcher))
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun networkState(
        hotspot: Boolean,
        cameraAp: Boolean = false
    ) = WifiNetworkState(
        isConnected = false,
        isConnectedToCameraAP = cameraAp,
        ssid = null,
        detectedCameraIP = null,
        isHotspotEnabled = hotspot
    )

    private fun discover(): List<DiscoveryAttemptResult> {
        val results = mutableListOf<DiscoveryAttemptResult>()
        helper.discoverCameras(
            forceApMode = false,
            onDiscoveringChanged = {},
            onResult = { results.add(it) }
        )
        return results
    }

    @Test
    fun `핫스팟 모드 - 클라이언트 미연결이어도 검색 게이트를 통과한다`() = runTest(dispatcher) {
        every { repo.isWifiConnected() } returns false
        every { repo.getCurrentWifiNetworkState() } returns networkState(hotspot = true)
        coEvery { repo.discoverCameras(any()) } returns emptyList()

        val results = discover()
        advanceUntilIdle()

        // 게이트 통과 → 실제 mDNS 검색(discoverCameras)까지 도달해야 한다.
        coVerify(exactly = 1) { repo.discoverCameras(false) }
        assertEquals(DiscoveryEmptyReason.NOT_FOUND, results.single().reason)
    }

    @Test
    fun `클라이언트 미연결 + 핫스팟 꺼짐 - NO_NETWORK로 차단한다`() = runTest(dispatcher) {
        every { repo.isWifiConnected() } returns false
        every { repo.getCurrentWifiNetworkState() } returns networkState(hotspot = false)
        coEvery { repo.discoverCameras(any()) } returns emptyList()

        val results = discover()
        advanceUntilIdle()

        // 진짜로 네트워크가 없으면 검색을 시도하지 않고 차단해야 한다.
        coVerify(exactly = 0) { repo.discoverCameras(any()) }
        assertEquals(DiscoveryEmptyReason.NO_NETWORK, results.single().reason)
    }

    @Test
    fun `헬퍼는 절대 연결하지 않는다(자동 연결 제거 회귀 감시)`() = runTest(dispatcher) {
        every { repo.isWifiConnected() } returns true
        every { repo.getCurrentWifiNetworkState() } returns networkState(hotspot = false)
        coEvery { repo.discoverCameras(any()) } returns listOf(
            PtpipCamera("192.168.0.11", 15740, "Z_8_1"),
            PtpipCamera("192.168.0.12", 15740, "Z_6_2")
        )

        val results = discover()
        advanceUntilIdle()

        coVerify(exactly = 0) { repo.connectToCamera(any(), any()) }
        assertEquals(DiscoveryEmptyReason.NONE, results.single().reason)
        assertEquals(2, results.single().cameras.size)
    }

    @Test
    fun `카메라 AP 연결 상태에서 0건이면 CAMERA_AP_EMPTY`() = runTest(dispatcher) {
        every { repo.isWifiConnected() } returns true
        every { repo.getCurrentWifiNetworkState() } returns
            networkState(hotspot = false, cameraAp = true)
        coEvery { repo.discoverCameras(any()) } returns emptyList()

        val results = discover()
        advanceUntilIdle()

        assertEquals(DiscoveryEmptyReason.CAMERA_AP_EMPTY, results.single().reason)
    }

    @Test
    fun `세션 점유 중이면 검색을 스킵하고 기존 목록을 유지한다`() = runTest(dispatcher) {
        val existing = listOf(PtpipCamera("192.168.49.10", 15740, "Z_8_1"))
        every { repo.isWifiConnected() } returns true
        every { repo.getCurrentWifiNetworkState() } returns networkState(hotspot = false)
        every { repo.isDiscoveryBlocked() } returns true
        every { repo.discoveredCameras } returns MutableStateFlow(existing)

        val results = discover()
        advanceUntilIdle()

        coVerify(exactly = 0) { repo.discoverCameras(any()) }
        assertEquals(DiscoveryEmptyReason.BLOCKED_BUSY, results.single().reason)
        assertEquals(
            "목록 소실은 곧 UI 회귀 — 기존 후보를 그대로 돌려준다",
            existing,
            results.single().cameras
        )
    }

    @Test
    fun `검색 중 예외는 UiText Resource로 전달된다(하드코딩 한국어 제거)`() = runTest(dispatcher) {
        every { repo.isWifiConnected() } returns true
        every { repo.getCurrentWifiNetworkState() } returns networkState(hotspot = false)
        coEvery { repo.discoverCameras(any()) } throws IllegalStateException("boom")

        val results = discover()
        advanceUntilIdle()

        val error = results.single().error
        assertTrue("에러 문구는 리소스 기반이어야 한다", error is UiText.Resource)
        assertEquals(listOf("boom"), (error as UiText.Resource).args)
    }
}
