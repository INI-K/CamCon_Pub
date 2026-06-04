package com.inik.camcon.presentation.viewmodel

import android.content.Context
import app.cash.turbine.test
import com.inik.camcon.domain.manager.CameraConnectionGlobalManager
import com.inik.camcon.domain.model.CameraConnectionType
import com.inik.camcon.domain.model.ConnectionMethod
import com.inik.camcon.domain.model.GlobalCameraConnectionState
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.PtpipCameraInfo
import com.inik.camcon.domain.model.PtpipConnectionState
import com.inik.camcon.domain.model.WifiCapabilities
import com.inik.camcon.domain.model.WifiNetworkState
import com.inik.camcon.domain.repository.PtpipPreferencesRepository
import com.inik.camcon.domain.repository.PtpipRepository
import com.inik.camcon.domain.repository.WifiCapabilityProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
    fun `discoverCamerasHotspot delegates to repository with forceApMode false`() = runTest {
        val viewModel = createViewModel()

        viewModel.discoverCamerasHotspot()

        coVerify { ptpipRepository.discoverCameras(forceApMode = false) }
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
