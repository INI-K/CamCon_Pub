package com.inik.camcon.presentation.viewmodel

import android.content.Context
import app.cash.turbine.test
import com.inik.camcon.domain.manager.CameraConnectionGlobalManager
import com.inik.camcon.domain.model.CameraConnectionType
import com.inik.camcon.domain.model.ConnectionMethod
import com.inik.camcon.domain.model.DiscoveryAttemptResult
import com.inik.camcon.domain.model.DiscoveryEmptyReason
import com.inik.camcon.domain.model.GlobalCameraConnectionState
import com.inik.camcon.domain.model.KnownCameraRef
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.PtpipCameraInfo
import com.inik.camcon.domain.model.PtpipConnectFailure
import com.inik.camcon.domain.model.PtpipConnectionState
import com.inik.camcon.domain.model.WifiCapabilities
import com.inik.camcon.domain.model.WifiNetworkState
import com.inik.camcon.domain.repository.PtpipPreferencesRepository
import com.inik.camcon.domain.repository.PtpipRepository
import com.inik.camcon.domain.repository.WifiCapabilityProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * PtpipViewModel의 폰 핫스팟 STA 모드 신규 API에 대한 StateFlow 방출 회귀 테스트.
 *
 * 검증 대상 (architect §8):
 *  - selectConnectionMethod(method) → activeConnectionMethod StateFlow 방출
 *  - setManualIp(ip) → manualIp StateFlow 방출
 *  - connectManualCamera() → 빈 IP 시 errorMessage 방출 + repository 호출 없음
 *  - connectManualCamera() → 정상 IP 시 repository.addManualCamera + connectToCamera(forceApMode=false) 호출
 *  - discoverCamerasHotspot() → repository.discoverCameras(forceApMode=false) 호출
 *
 * 원칙: ViewModel 구현 세부는 보지 않고 **StateFlow/SharedFlow 방출과 위임 호출**만 검증.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PtpipViewModelHotspotTest {

    private lateinit var ptpipRepository: PtpipRepository
    private lateinit var preferencesRepository: PtpipPreferencesRepository
    private lateinit var globalManager: CameraConnectionGlobalManager
    private lateinit var handoffTracker: ConnectionHandoffTracker
    private lateinit var connectionHelper: PtpipConnectionHelper
    private lateinit var discoveryHelper: PtpipDiscoveryHelper
    private lateinit var debugHelper: PtpipDebugHelper
    private lateinit var wifiCapabilityProvider: WifiCapabilityProvider
    private lateinit var appContext: Context

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        ptpipRepository = mockk(relaxed = true)
        preferencesRepository = mockk(relaxed = true)
        globalManager = mockk(relaxed = true)
        handoffTracker = mockk(relaxed = true)
        connectionHelper = mockk(relaxed = true)
        discoveryHelper = mockk(relaxed = true)
        debugHelper = mockk(relaxed = true)
        wifiCapabilityProvider = mockk(relaxed = true)
        appContext = mockk(relaxed = true)

        // PtpipRepository에서 ViewModel이 직접 노출하는 StateFlow들 셋업
        every { ptpipRepository.connectionState } returns
            MutableStateFlow(PtpipConnectionState.DISCONNECTED)
        every { ptpipRepository.connectionProgressMessage } returns MutableStateFlow("")
        every { ptpipRepository.discoveredCameras } returns MutableStateFlow(emptyList())
        every { ptpipRepository.cameraInfo } returns
            MutableStateFlow<PtpipCameraInfo?>(null)
        every { ptpipRepository.wifiNetworkState } returns
            MutableStateFlow(WifiNetworkState(false, false, null, null))
        every { ptpipRepository.connectionLostMessage } returns
            MutableStateFlow<String?>(null)

        // 핫스팟 신규 StateFlow — 통합 PR에서 PtpipRepository에 추가될 멤버.
        every { ptpipRepository.activeConnectionMethod } returns
            MutableStateFlow<ConnectionMethod?>(null)
        every { ptpipRepository.manualIp } returns MutableStateFlow("")
        every { ptpipRepository.connectFailure } returns
            MutableStateFlow<PtpipConnectFailure?>(null)
        every { ptpipRepository.sshHostKeyFingerprint } returns MutableStateFlow<String?>(null)

        every { globalManager.globalConnectionState } returns
            MutableStateFlow(GlobalCameraConnectionState())
        every { globalManager.activeConnectionType } returns
            MutableStateFlow<CameraConnectionType?>(null)

        every { preferencesRepository.isPtpipEnabled } returns flowOf(true)
        every { preferencesRepository.isAutoReconnectEnabled } returns flowOf(false)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): PtpipViewModel = PtpipViewModel(
        appContext = appContext,
        ptpipRepository = ptpipRepository,
        preferencesRepository = preferencesRepository,
        globalManager = globalManager,
        handoffTracker = handoffTracker,
        connectionHelper = connectionHelper,
        discoveryHelper = discoveryHelper,
        debugHelper = debugHelper,
        wifiCapabilityProvider = wifiCapabilityProvider,
        ioDispatcher = testDispatcher,
    )

    @Test
    fun `setManualIp emits to manualIp StateFlow via repository delegate`() = runTest {
        val viewModel = createViewModel()

        viewModel.manualIp.test {
            assertEquals("", awaitItem())

            viewModel.setManualIp("192.168.49.137")

            // repository에 위임되어 StateFlow가 갱신되어야 한다.
            coVerify { ptpipRepository.setManualIp("192.168.49.137") }
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `selectConnectionMethod emits to activeConnectionMethod`() = runTest {
        val viewModel = createViewModel()

        viewModel.activeConnectionMethod.test {
            assertEquals(null, awaitItem())

            viewModel.selectConnectionMethod(ConnectionMethod.STA_PHONE_HOTSPOT)

            // ViewModel은 repository에 위임하거나 자체 StateFlow를 갱신해야 한다.
            // 위임 모드: repository.setActiveConnectionMethod(...) 호출 검증.
            coVerify { ptpipRepository.setActiveConnectionMethod(ConnectionMethod.STA_PHONE_HOTSPOT) }
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `connectManualCamera with blank ip emits errorMessage and skips repository`() = runTest {
        val viewModel = createViewModel()
        every { ptpipRepository.manualIp } returns MutableStateFlow("")

        viewModel.errorMessage.test {
            // 초기 errorMessage = null
            assertEquals(null, awaitItem())

            viewModel.connectManualCamera()

            val err = awaitItem()
            assertEquals(true, err?.isNotBlank())

            // 빈 IP는 repository 호출 없음.
            coVerify(exactly = 0) { ptpipRepository.addManualCamera(any(), any(), any()) }
            coVerify(exactly = 0) { ptpipRepository.connectToCamera(any(), any()) }
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `connectManualCamera with valid ip delegates to repository with forceApMode false`() =
        runTest {
            val ipFlow = MutableStateFlow("192.168.49.137")
            every { ptpipRepository.manualIp } returns ipFlow
            val cam = PtpipCamera("192.168.49.137", 15740, "Manual (192.168.49.137)")
            coEvery {
                ptpipRepository.addManualCamera("192.168.49.137", any(), 15740)
            } returns cam
            coEvery { ptpipRepository.connectToCamera(cam, false) } returns true

            val viewModel = createViewModel()
            viewModel.connectManualCamera()

            coVerify { ptpipRepository.addManualCamera("192.168.49.137", any(), 15740) }
            coVerify { ptpipRepository.connectToCamera(cam, forceApMode = false) }
        }

    @Test
    fun `discoverCamerasHotspot delegates to discoveryHelper with forceApMode false`() = runTest {
        // 핫스팟 모드 고정(setActiveConnectionMethod) + 검색 전용 헬퍼 위임을 검증한다.
        // (헬퍼는 더 이상 자동선택/자동연결을 하지 않으므로 콜백이 2개로 축소됐다)
        val viewModel = createViewModel()

        viewModel.discoverCamerasHotspot()

        verify {
            ptpipRepository.setActiveConnectionMethod(ConnectionMethod.STA_PHONE_HOTSPOT)
        }
        verify {
            discoveryHelper.discoverCameras(
                forceApMode = false,
                onDiscoveringChanged = any(),
                onResult = any()
            )
        }
    }

    /** 헬퍼에 넘어간 onResult 콜백을 캡처해 정책 분기를 구동한다. */
    private fun captureOnResult(): (DiscoveryAttemptResult) -> Unit {
        val slot = slot<(DiscoveryAttemptResult) -> Unit>()
        verify {
            discoveryHelper.discoverCameras(
                forceApMode = any(),
                onDiscoveringChanged = any(),
                onResult = capture(slot)
            )
        }
        return slot.captured
    }

    @Test
    fun `정책이 AutoConnect면 연결한다(신뢰 링크 + 기지 기기 1개)`() = runTest {
        // 폰 핫스팟(폰이 게이트웨이) = 신뢰 링크, 기억된 IP와 일치하는 후보 1개.
        every { ptpipRepository.wifiNetworkState } returns MutableStateFlow(
            WifiNetworkState(
                isConnected = false,
                isConnectedToCameraAP = false,
                ssid = null,
                detectedCameraIP = null,
                isHotspotEnabled = true
            )
        )
        every { preferencesRepository.lastConnectedIp } returns flowOf("192.168.49.137")
        // Wave 3: 기지 판정이 IP 단독에서 KnownCameraRef(이름 → IP)로 바뀌었다.
        val known = KnownCameraRef(
            ipHint = "192.168.49.137",
            serviceName = "Z_8_5003869",
            autoConnectApproved = true
        )
        every { preferencesRepository.knownCamera } returns flowOf(known)
        coEvery { preferencesRepository.getKnownCamera() } returns known
        every { ptpipRepository.isDiscoveryBlocked() } returns false
        coEvery { connectionHelper.connectToCamera(any(), any()) } returns true

        val viewModel = createViewModel()
        viewModel.discoverCameras(forceApMode = false)

        val camera = PtpipCamera("192.168.49.137", 15740, "Z_8_5003869")
        captureOnResult().invoke(
            DiscoveryAttemptResult(listOf(camera), DiscoveryEmptyReason.NONE)
        )
        advanceTimeBy(1500)

        coVerify(exactly = 1) { connectionHelper.connectToCamera(camera, false) }
    }

    @Test
    fun `정책이 RequireSelection이면 연결하지 않는다(첫 페어링)`() = runTest {
        every { ptpipRepository.wifiNetworkState } returns MutableStateFlow(
            WifiNetworkState(
                isConnected = false,
                isConnectedToCameraAP = false,
                ssid = null,
                detectedCameraIP = null,
                isHotspotEnabled = true
            )
        )
        // 기억된 카메라 없음 → 첫 페어링은 항상 사용자 탭.
        every { preferencesRepository.lastConnectedIp } returns flowOf(null)
        every { ptpipRepository.isDiscoveryBlocked() } returns false

        val viewModel = createViewModel()
        viewModel.discoverCameras(forceApMode = false)

        captureOnResult().invoke(
            DiscoveryAttemptResult(
                listOf(
                    PtpipCamera("192.168.49.137", 15740, "Z_8_5003869"),
                    PtpipCamera("192.168.49.200", 15740, "Z_6_5000784")
                ),
                DiscoveryEmptyReason.NONE
            )
        )
        advanceTimeBy(1500)

        coVerify(exactly = 0) { connectionHelper.connectToCamera(any(), any()) }
    }

    @Test
    fun `검색 0건은 discoveryEmptyReason만 갱신한다`() = runTest {
        every { ptpipRepository.isDiscoveryBlocked() } returns false
        val viewModel = createViewModel()

        assertEquals(DiscoveryEmptyReason.NOT_SEARCHED, viewModel.discoveryEmptyReason.value)

        viewModel.discoverCameras(forceApMode = false)
        captureOnResult().invoke(
            DiscoveryAttemptResult(emptyList(), DiscoveryEmptyReason.NOT_FOUND)
        )
        advanceTimeBy(1500)

        assertEquals(DiscoveryEmptyReason.NOT_FOUND, viewModel.discoveryEmptyReason.value)
        coVerify(exactly = 0) { connectionHelper.connectToCamera(any(), any()) }
    }

    @Test
    fun `cancelConnecting은 requestConnectCancel을 disconnect보다 먼저 호출한다`() = runTest {
        val viewModel = createViewModel()

        viewModel.cancelConnecting()

        // 순서가 뒤바뀌면 disconnect가 연결 mutex에 먼저 큐잉되어 취소가 다시 무력화된다.
        coVerifyOrder {
            ptpipRepository.requestConnectCancel()
            connectionHelper.disconnect()
        }
    }

    @Test
    fun `activeConnectionMethod StateFlow is exposed to UI layer`() = runTest {
        val state = MutableStateFlow<ConnectionMethod?>(ConnectionMethod.STA_PHONE_HOTSPOT)
        every { ptpipRepository.activeConnectionMethod } returns state
        val viewModel = createViewModel()

        viewModel.activeConnectionMethod.test {
            assertEquals(ConnectionMethod.STA_PHONE_HOTSPOT, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }
}
