package com.inik.camcon.data.datasource.ptpip

import android.app.Application
import com.inik.camcon.data.network.ptpip.connection.PtpipConnectionManager
import com.inik.camcon.data.network.ptpip.discovery.DiscoveryBudget
import com.inik.camcon.data.network.ptpip.discovery.PtpipDiscoveryService
import com.inik.camcon.data.network.ptpip.wifi.WifiNetworkHelper
import com.inik.camcon.domain.model.CameraDiscoverySource
import com.inik.camcon.domain.model.PtpipCamera
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 검색 single-flight 회귀 가드.
 *
 * 배경: 후보 목록의 권위는 `PtpipDiscoveryCoordinator._discoveredCameras` 하나인데, 전경 검색(사용자
 * 탭)과 배경 폴링(WifiMonitoringService 4초 tick)이 **각자의 호출 로컬 스냅샷**을 그 StateFlow에
 * 덮어쓸 수 있었다. 배경 예산은 기지 IP 캐시 히트 시 후보 1건만 담아 즉시 반환하므로, 배경 tick이
 * 전경보다 늦게 끝나면 목록이 1건으로 고정돼 두 번째 카메라를 선택할 수 없었다 — 이 웨이브가 고친
 * "후보 1대로 접힘"과 정확히 같은 증상이다. 상태는 검색 중에도 DISCONNECTED라 세션 점유 가드로는
 * 막히지 않는다.
 */
// addManualCamera가 IpAddressValidator → android.util.Patterns를 쓰므로 Robolectric이 필요하다
// (순수 JVM에서는 Patterns.IP_ADDRESS가 null이다).
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
// application 오버라이드: 실제 CamCon Application은 Hilt·Firebase를 부팅해 단위 테스트에서 죽는다
// (프로젝트 Robolectric 선례와 동일한 처리).
@Config(sdk = [34], application = Application::class)
class PtpipDiscoverySingleFlightTest {

    private fun camera(ip: String, source: CameraDiscoverySource = CameraDiscoverySource.MDNS) =
        PtpipCamera(
            ipAddress = ip,
            port = 15740,
            name = "Z_8_${ip.substringAfterLast('.')}",
            isOnline = true,
            discoverySource = source
        )

    private fun wifiHelper(): WifiNetworkHelper = mockk(relaxed = true) {
        every { isWifiConnected() } returns true
        every { isHotspotEnabled() } returns true
        every { isConnectedToCameraAP() } returns false
    }

    private fun coordinator(
        service: PtpipDiscoveryService,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
        blocked: () -> Boolean = { false }
    ) = PtpipDiscoveryCoordinator(
        wifiHelper = wifiHelper(),
        discoveryService = service,
        connectionManager = mockk<PtpipConnectionManager>(relaxed = true),
        discoveryBlockedProvider = blocked,
        ioDispatcher = dispatcher
    )

    @Test
    fun `전경 검색 진행 중 배경 검색은 실행되지 않고 현재 목록을 그대로 반환한다`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val service = mockk<PtpipDiscoveryService>()
        val release = CompletableDeferred<Unit>()

        // 전경 검색은 release 가 풀릴 때까지 붙잡아 둔다(진행 중 상태 재현).
        coEvery {
            service.discoverCameras(any(), DiscoveryBudget.UserInitiated, any())
        } coAnswers {
            release.await()
            listOf(camera("192.168.49.10"), camera("192.168.49.11"))
        }
        coEvery {
            service.discoverCameras(any(), DiscoveryBudget.BackgroundReconnect, any())
        } returns listOf(camera("192.168.49.10"))

        val sut = coordinator(service, dispatcher)

        val foreground = launch(dispatcher) {
            sut.discoverCameras(forceApMode = false, budget = DiscoveryBudget.UserInitiated)
        }
        advanceUntilIdle() // 전경 검색이 락을 잡고 대기 상태로 들어간다

        val backgroundResult =
            sut.discoverCameras(forceApMode = false, budget = DiscoveryBudget.BackgroundReconnect)

        // 배경 호출은 서비스를 건드리지 않고 즉시 반환해야 한다.
        coVerify(exactly = 0) {
            service.discoverCameras(any(), DiscoveryBudget.BackgroundReconnect, any())
        }
        assertEquals(sut.discoveredCameras.value, backgroundResult)

        release.complete(Unit)
        foreground.join()
        advanceUntilIdle()

        // 전경 결과 2건이 배경 호출에 의해 1건으로 축소되지 않는다.
        assertEquals(2, sut.discoveredCameras.value.size)
    }

    @Test
    fun `세션 점유 중에는 검색을 건너뛰고 기존 목록을 유지한다`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val service = mockk<PtpipDiscoveryService>(relaxed = true)
        val sut = coordinator(service, dispatcher, blocked = { true })

        // 목록에 후보가 이미 있는 상태에서 차단되면 emptyList로 지워서는 안 된다(UI 목록 소실).
        sut.addManualCamera("192.168.49.50", "ignored", 15740)

        val result =
            sut.discoverCameras(forceApMode = false, budget = DiscoveryBudget.UserInitiated)

        assertEquals(1, result.size)
        assertEquals("192.168.49.50", result.first().ipAddress)
        coVerify(exactly = 0) { service.discoverCameras(any(), any(), any()) }
    }

    @Test
    fun `검색 결과가 사용자 직접 입력 후보를 지우지 않는다`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val service = mockk<PtpipDiscoveryService>()
        coEvery { service.discoverCameras(any(), any(), any()) } returns
            listOf(camera("192.168.49.10"))

        val sut = coordinator(service, dispatcher)
        sut.addManualCamera("192.168.49.50", "ignored", 15740)

        sut.discoverCameras(forceApMode = false, budget = DiscoveryBudget.UserInitiated)
        advanceUntilIdle()

        val ips = sut.discoveredCameras.value.map { it.ipAddress }
        assertTrue("mDNS 후보 누락: $ips", ips.contains("192.168.49.10"))
        assertTrue("수동 입력 후보가 지워졌다: $ips", ips.contains("192.168.49.50"))
    }
}
