package com.inik.camcon.presentation.viewmodel

import android.content.Context
import app.cash.turbine.test
import com.inik.camcon.domain.manager.CameraConnectionGlobalManager
import com.inik.camcon.domain.model.CameraConnectionType
import com.inik.camcon.domain.model.ConnectionMethod
import com.inik.camcon.domain.model.GlobalCameraConnectionState
import com.inik.camcon.domain.model.PtpipCameraInfo
import com.inik.camcon.domain.model.PtpipConnectionState
import com.inik.camcon.domain.model.WifiNetworkState
import com.inik.camcon.domain.repository.PtpipPreferencesRepository
import com.inik.camcon.domain.repository.PtpipRepository
import com.inik.camcon.domain.repository.WifiCapabilityProvider
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
 * `PtpipViewModel` 수동 IP 입력 필드 회귀 테스트 (감사 확정 MAJOR: "수동 IP 입력 필드가 타이핑으로 사용 불가").
 *
 * 근본 원인: TextField가 검증된 IP StateFlow(manualIp)에 controlled 바인딩돼 있어, 데이터소스가
 * 완전한 사설 IP만 반영하는 setManualIp를 거치면 "1"·"192.168.4" 등 모든 중간 입력이 거부되어
 * 매 키 입력이 빈 값으로 리셋됐다(붙여넣기만 동작). 수정: 자유 타이핑 원문을 VM 로컬 상태
 * (manualIpInput)에 유지하고 UI가 그 원문을 바인딩하도록 분리. 검증은 여전히 데이터소스에 위임.
 *
 * 원칙(ViewModel 테스트) = StateFlow 방출 검증. 부분 입력이 manualIpInput에 그대로 유지되는지 확인한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PtpipViewModelManualIpInputTest {

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

        every { ptpipRepository.connectionState } returns
            MutableStateFlow(PtpipConnectionState.DISCONNECTED)
        every { ptpipRepository.connectionProgressMessage } returns MutableStateFlow("")
        every { ptpipRepository.discoveredCameras } returns MutableStateFlow(emptyList())
        every { ptpipRepository.cameraInfo } returns MutableStateFlow<PtpipCameraInfo?>(null)
        every { ptpipRepository.wifiNetworkState } returns
            MutableStateFlow(WifiNetworkState(false, false, null, null))
        every { ptpipRepository.connectionLostMessage } returns MutableStateFlow<String?>(null)
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
    fun `부분 IP 타이핑은 manualIpInput에 원문 그대로 유지된다`() = runTest {
        val viewModel = createViewModel()

        viewModel.manualIpInput.test {
            assertEquals("", awaitItem())

            // 검증에 실패하는 중간 입력도 리셋 없이 그대로 유지되어야 한다(핵심 회귀).
            viewModel.setManualIp("1")
            assertEquals("1", awaitItem())

            viewModel.setManualIp("19")
            assertEquals("19", awaitItem())

            viewModel.setManualIp("192.168.4")
            assertEquals("192.168.4", awaitItem())

            // 완전한 유효 IP까지 타이핑으로 도달 가능.
            viewModel.setManualIp("192.168.49.137")
            assertEquals("192.168.49.137", awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `setManualIp는 원문 유지와 별개로 데이터소스 검증 위임을 호출한다`() = runTest {
        val viewModel = createViewModel()

        viewModel.setManualIp("192.168.4")

        // 자유 타이핑 원문 유지 + 검증(화이트리스트)은 데이터소스에 위임(통과분만 manualIp 반영).
        assertEquals("192.168.4", viewModel.manualIpInput.value)
        coVerify { ptpipRepository.setManualIp("192.168.4") }
    }
}
