package com.inik.camcon.presentation.viewmodel

import com.inik.camcon.domain.model.WifiNetworkState
import com.inik.camcon.domain.repository.PtpipRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [PtpipDiscoveryHelper.discoverCameras] 의 네트워크 사전 게이트 단위 테스트.
 *
 * 회귀: 폰 핫스팟(STA_PHONE_HOTSPOT) 모드에선 폰이 SoftAP 라 Wi-Fi 클라이언트 연결이 없어
 * `isWifiConnected()=false` 가 정상인데, 게이트가 이를 "Wi-Fi 미연결"로 오판해 검색을 중단하던 버그.
 * (실기기 로그: "사용자가 카메라 검색을 요청했습니다" → "Wi-Fi가 연결되어 있지 않습니다" → 즉시 종료)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PtpipDiscoveryHelperTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: PtpipRepository
    private lateinit var helper: PtpipDiscoveryHelper

    private val wifiNotConnectedMessage = "Wi-Fi가 연결되어 있지 않"

    @Before
    fun setup() {
        repo = mockk(relaxed = true)
        helper = PtpipDiscoveryHelper(repo, CoroutineScope(dispatcher))
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun networkState(hotspot: Boolean) = WifiNetworkState(
        isConnected = false,
        isConnectedToCameraAP = false,
        ssid = null,
        detectedCameraIP = null,
        isHotspotEnabled = hotspot
    )

    @Test
    fun `핫스팟 모드 - 클라이언트 미연결이어도 검색 게이트를 통과한다`() = runTest(dispatcher) {
        every { repo.isWifiConnected() } returns false
        every { repo.getCurrentWifiNetworkState() } returns networkState(hotspot = true)
        coEvery { repo.discoverCameras(any()) } returns emptyList()

        val errors = mutableListOf<String?>()
        helper.discoverCameras(
            forceApMode = false,
            onDiscoveringChanged = {},
            onConnectingChanged = {},
            onErrorChanged = { errors.add(it) },
            onCameraSelected = {}
        )
        advanceUntilIdle()

        // 게이트 통과 → 실제 mDNS 검색(discoverCameras)까지 도달해야 한다.
        coVerify(exactly = 1) { repo.discoverCameras(false) }
        // "Wi-Fi 미연결" 에러는 발생하면 안 된다(핫스팟은 정상 토폴로지).
        assertFalse(
            "핫스팟 모드인데 'Wi-Fi 미연결' 에러로 차단됨",
            errors.any { it?.contains(wifiNotConnectedMessage) == true }
        )
    }

    @Test
    fun `클라이언트 미연결 + 핫스팟 꺼짐 - Wi-Fi 미연결 에러로 차단한다`() = runTest(dispatcher) {
        every { repo.isWifiConnected() } returns false
        every { repo.getCurrentWifiNetworkState() } returns networkState(hotspot = false)
        coEvery { repo.discoverCameras(any()) } returns emptyList()

        val errors = mutableListOf<String?>()
        helper.discoverCameras(
            forceApMode = false,
            onDiscoveringChanged = {},
            onConnectingChanged = {},
            onErrorChanged = { errors.add(it) },
            onCameraSelected = {}
        )
        advanceUntilIdle()

        // 진짜로 네트워크가 없으면 검색을 시도하지 않고 차단해야 한다.
        coVerify(exactly = 0) { repo.discoverCameras(any()) }
        assertTrue(
            "네트워크 전무인데 Wi-Fi 미연결 에러가 없음",
            errors.any { it?.contains(wifiNotConnectedMessage) == true }
        )
    }
}
