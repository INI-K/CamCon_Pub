package com.inik.camcon.presentation.viewmodel

import android.content.Context
import com.inik.camcon.data.repository.fake.FakeCameraRepositoryBasic
import com.inik.camcon.domain.model.LiveViewQuality
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.repository.AppSettingsRepository
import com.inik.camcon.domain.repository.PtpipPreferencesRepository
import com.inik.camcon.domain.repository.UsbDeviceRepository
import com.inik.camcon.domain.usecase.GetSubscriptionUseCase
import com.inik.camcon.presentation.viewmodel.state.CameraSettingsManager
import com.inik.camcon.presentation.viewmodel.state.CameraUiStateManager
import com.inik.camcon.presentation.viewmodel.state.ErrorHandlingManager
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/**
 * "카메라 컨트롤 표시"를 끄면 **라이브뷰도 멈춘다**는 계약.
 *
 * 두 토글의 AND 는 화면에 그릴지의 조건일 뿐이라, 예전에는 컨트롤 표시를 꺼도 네이티브 펌프가
 * 계속 돌았다 — 보이지 않는 채로 프레임 수신·디코딩·배터리·대역폭을 먹는 상태다.
 *
 * 검증 원칙(프로젝트 규약): private 관찰자를 직접 부르지 않고 **트리거(설정 방출)와 협력자 호출**로만 본다.
 * 하네스 구성은 [CameraViewModelLiveViewQualityTest] 와 같다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CameraViewModelControlsToggleTest {

    private lateinit var context: Context
    private lateinit var cameraRepository: FakeCameraRepositoryBasic
    private lateinit var usbDeviceRepository: UsbDeviceRepository
    private lateinit var getSubscriptionUseCase: GetSubscriptionUseCase
    private lateinit var uiStateManager: CameraUiStateManager
    private lateinit var usbAutoConnectManager: UsbAutoConnectManager
    private lateinit var operationsManager: CameraOperationsManager
    private lateinit var settingsManager: CameraSettingsManager
    private lateinit var errorHandlingManager: ErrorHandlingManager
    private lateinit var handoffTracker: ConnectionHandoffTracker
    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var ptpipPreferencesRepository: PtpipPreferencesRepository
    private lateinit var advancedCaptureManager: CameraAdvancedCaptureManager
    private lateinit var focusManager: CameraFocusManager
    private lateinit var fileManager: CameraFileManager
    private lateinit var streamingManager: CameraStreamingManager
    private lateinit var diagnosticsManager: CameraDiagnosticsManager

    private val testDispatcher = StandardTestDispatcher()

    /** 관찰 대상. replay=1 로 두어 "초기값 → 변경" 순서를 그대로 재현한다. */
    private val cameraControlsFlow = MutableSharedFlow<Boolean>(replay = 1, extraBufferCapacity = 8)

    private var liveViewActive = false

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        context = mockk(relaxed = true)
        cameraRepository = FakeCameraRepositoryBasic()
        getSubscriptionUseCase = mockk(relaxed = true)
        usbDeviceRepository = mockk(relaxed = true)
        uiStateManager = CameraUiStateManager()
        usbAutoConnectManager = mockk(relaxed = true)
        operationsManager = mockk(relaxed = true)
        settingsManager = mockk(relaxed = true)
        errorHandlingManager = mockk(relaxed = true)
        handoffTracker = mockk(relaxed = true)
        appSettingsRepository = mockk(relaxed = true)
        ptpipPreferencesRepository = mockk(relaxed = true)
        advancedCaptureManager = mockk(relaxed = true)
        focusManager = mockk(relaxed = true)
        fileManager = mockk(relaxed = true)
        streamingManager = mockk(relaxed = true)
        diagnosticsManager = mockk(relaxed = true)

        every { appSettingsRepository.isCameraControlsEnabled } returns cameraControlsFlow
        every { appSettingsRepository.liveViewQuality } returns emptyFlow()
        every { appSettingsRepository.subscriptionTierEnum } returns flowOf(SubscriptionTier.FREE)
        every { appSettingsRepository.isRawFileDownloadEnabled } returns flowOf(true)
        every { appSettingsRepository.isHistogramEnabled } returns flowOf(false)
        every { ptpipPreferencesRepository.isAutoConnectEnabled } returns flowOf(false)
        every { ptpipPreferencesRepository.lastConnectedName } returns flowOf(null)
        every { getSubscriptionUseCase.getSubscriptionTier() } returns emptyFlow()
        every { usbDeviceRepository.isNativeCameraConnected } returns MutableStateFlow(false)
        every { settingsManager.cameraSettings } returns MutableStateFlow(null)
        every { settingsManager.cameraCapabilities } returns MutableStateFlow(null)
        every { errorHandlingManager.errorEvent } returns MutableSharedFlow()
        every { errorHandlingManager.nativeErrorEvent } returns MutableSharedFlow()

        every { operationsManager.isLiveViewActive() } answers { liveViewActive }
        every { operationsManager.stopLiveView(any()) } answers { liveViewActive = false }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): CameraViewModel = CameraViewModel(
        context = context,
        cameraRepository = cameraRepository,
        usbDeviceRepository = usbDeviceRepository,
        getSubscriptionUseCase = getSubscriptionUseCase,
        uiStateManager = uiStateManager,
        usbAutoConnectManager = usbAutoConnectManager,
        operationsManager = operationsManager,
        settingsManager = settingsManager,
        errorHandlingManager = errorHandlingManager,
        handoffTracker = handoffTracker,
        appSettingsRepository = appSettingsRepository,
        ptpipPreferencesRepository = ptpipPreferencesRepository,
        advancedCaptureManager = advancedCaptureManager,
        focusManager = focusManager,
        fileManager = fileManager,
        streamingManager = streamingManager,
        diagnosticsManager = diagnosticsManager,
        nikonApplicationModeManager = mockk(relaxed = true),
        ioDispatcher = testDispatcher as CoroutineDispatcher
    )

    /**
     * 라이브뷰 펌프가 도는 상태를 만든다.
     *
     * ⚠️ `uiState.isLiveViewActive` 는 건드리지 않는다. 그 값이 true 가 되면 VM 의 2.5초 설정
     * 안전망 폴링이 돌기 시작해, 가상 시간에서 `advanceUntilIdle` 이 영원히 진행된다(OOM).
     * 판정 출처도 펌프 플래그이므로 이 상태만으로 충분하다.
     */
    private fun markLiveViewRunning() {
        liveViewActive = true
    }

    @Test
    fun `컨트롤 표시를 끄면 도는 라이브뷰가 멈춘다`() = runTest {
        cameraControlsFlow.emit(true)
        val viewModel = createViewModel()
        advanceUntilIdle()
        markLiveViewRunning()

        cameraControlsFlow.emit(false)
        advanceUntilIdle()

        verify { operationsManager.stopLiveView(any()) }
        // 토글도 함께 꺼야 다시 켰을 때 몰래 스트리밍이 재개되지 않는다.
        coVerify { appSettingsRepository.setLiveViewEnabled(false) }
        assertFalse(liveViewActive)
    }

    @Test
    fun `라이브뷰가 꺼져 있으면 아무 일도 하지 않는다`() = runTest {
        cameraControlsFlow.emit(true)
        val viewModel = createViewModel()
        advanceUntilIdle()

        cameraControlsFlow.emit(false)
        advanceUntilIdle()

        verify(exactly = 0) { operationsManager.stopLiveView(any()) }
        coVerify(exactly = 0) { appSettingsRepository.setLiveViewEnabled(any()) }
    }

    @Test
    fun `앱 시작 시의 초기값으로는 라이브뷰를 죽이지 않는다`() = runTest {
        // DataStore 는 구독 즉시 현재값을 흘린다. 그 첫 emit 이 false 라고 해서(컨트롤을 꺼 둔
        // 사용자) 돌고 있던 라이브뷰를 끄면 안 된다 — drop(1) 이 지키는 계약이다.
        cameraControlsFlow.emit(false)
        val viewModel = createViewModel()
        advanceUntilIdle()
        markLiveViewRunning()
        advanceUntilIdle()

        verify(exactly = 0) { operationsManager.stopLiveView(any()) }
    }

    @Test
    fun `컨트롤 표시를 다시 켜도 라이브뷰를 자동 재시작하지 않는다`() = runTest {
        cameraControlsFlow.emit(true)
        val viewModel = createViewModel()
        advanceUntilIdle()
        markLiveViewRunning()

        cameraControlsFlow.emit(false)
        advanceUntilIdle()
        cameraControlsFlow.emit(true)
        advanceUntilIdle()

        // 사용자가 토글을 직접 누르는 것이 명시적이다 — 화면을 켜자마자 스트리밍이 되살아나면
        // 사용자가 모르는 사이 대역폭·배터리를 다시 먹는다.
        verify(exactly = 0) { operationsManager.startLiveView(any(), any(), any()) }
    }
}
