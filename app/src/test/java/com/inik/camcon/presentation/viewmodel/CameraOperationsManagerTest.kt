package com.inik.camcon.presentation.viewmodel

import android.util.Log
import app.cash.turbine.test
import com.inik.camcon.domain.model.CameraCapabilities
import com.inik.camcon.domain.model.CameraSettings
import com.inik.camcon.domain.model.CapturedPhoto
import com.inik.camcon.domain.model.LiveViewFrame
import com.inik.camcon.domain.model.ShootingMode
import com.inik.camcon.domain.model.TimelapseSettings
import com.inik.camcon.domain.model.UnsupportedShootingModeException
import com.inik.camcon.domain.usecase.camera.CapturePhotoUseCase
import com.inik.camcon.domain.usecase.camera.PerformAutoFocusUseCase
import com.inik.camcon.domain.usecase.camera.StartLiveViewUseCase
import com.inik.camcon.domain.usecase.camera.StartTimelapseUseCase
import com.inik.camcon.domain.usecase.camera.StopLiveViewUseCase
import com.inik.camcon.presentation.viewmodel.state.CameraUiStateManager
import com.inik.camcon.presentation.viewmodel.state.InfoMessage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [CameraOperationsManager] 단위 테스트 — 촬영/라이브뷰/타임랩스/AF 위임의 **상태 방출**과
 * jobLock 기반 Job 수명(중복 시작 방지·cleanup 취소)을 검증한다.
 *
 * 검증 원칙(프로젝트 규약): 구현 세부가 아니라 실제 협력자([CameraUiStateManager])의 StateFlow/SharedFlow
 * 방출과 UseCase 호출 횟수만으로 불변식을 방어한다.
 *  - uiStateManager: 실제 [CameraUiStateManager](네이티브 비의존) — uiState/liveViewFrame/infoMessage 로 결과 관찰.
 *  - UseCase 5종: mockk. Flow 는 완결/무한(`awaitCancellation`) Flow 로 Job 수명 시나리오를 표현.
 *  - appScope: [StandardTestDispatcher] 기반 — managerScope 가 이를 상속, `advanceUntilIdle` 로 구동.
 *
 * (W3a 가 setError 를 UiText 로 옮기는 중 — 본 테스트는 error 값의 타입/문자열이 아닌 **존재 여부**만 확인해
 *  API 변경에 견디게 했다.)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CameraOperationsManagerTest {

    private val capturePhotoUseCase = mockk<CapturePhotoUseCase>()
    private val startLiveViewUseCase = mockk<StartLiveViewUseCase>()
    private val stopLiveViewUseCase = mockk<StopLiveViewUseCase>()
    private val performAutoFocusUseCase = mockk<PerformAutoFocusUseCase>()
    private val startTimelapseUseCase = mockk<StartTimelapseUseCase>()

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.i(any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    private fun TestScope.createManager(): CameraOperationsManager {
        val appScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        return CameraOperationsManager(
            capturePhotoUseCase = capturePhotoUseCase,
            startLiveViewUseCase = startLiveViewUseCase,
            stopLiveViewUseCase = stopLiveViewUseCase,
            performAutoFocusUseCase = performAutoFocusUseCase,
            startTimelapseUseCase = startTimelapseUseCase,
            appScope = appScope
        )
    }

    // ── 촬영 ──

    @Test
    fun `capturePhoto 성공 시 촬영 종료 후 isCapturing false와 에러 없음`() = runTest {
        coEvery { capturePhotoUseCase(ShootingMode.SINGLE) } returns Result.success(samplePhoto())
        val ui = CameraUiStateManager()
        val manager = createManager()

        manager.capturePhoto(ShootingMode.SINGLE, ui)
        advanceUntilIdle()

        assertFalse(ui.uiState.value.capture.isCapturing)
        assertNull(ui.uiState.value.error)
        coVerify(exactly = 1) { capturePhotoUseCase(ShootingMode.SINGLE) }
        manager.cleanup()
    }

    @Test
    fun `capturePhoto 미지원 모드 예외 시 shootingModeError에 마커를 세운다`() = runTest {
        coEvery { capturePhotoUseCase(ShootingMode.BURST) } returns
            Result.failure(UnsupportedShootingModeException(ShootingMode.BURST))
        val ui = CameraUiStateManager()
        val manager = createManager()

        manager.capturePhoto(ShootingMode.BURST, ui)
        advanceUntilIdle()

        assertEquals(
            CameraOperationsManager.UNSUPPORTED_MODE_PREFIX + "BURST",
            ui.uiState.value.dialog.shootingModeError
        )
        assertFalse(ui.uiState.value.capture.isCapturing)
        manager.cleanup()
    }

    @Test
    fun `capturePhoto 일반 실패 시 error를 방출하고 isCapturing false`() = runTest {
        coEvery { capturePhotoUseCase(ShootingMode.SINGLE) } returns
            Result.failure(RuntimeException("셔터 실패"))
        val ui = CameraUiStateManager()
        val manager = createManager()

        manager.capturePhoto(ShootingMode.SINGLE, ui)
        advanceUntilIdle()

        assertNotNull(ui.uiState.value.error)
        assertFalse(ui.uiState.value.capture.isCapturing)
        manager.cleanup()
    }

    @Test
    fun `capturePhoto 시 활성 라이브뷰 Job을 취소하고 라이브뷰 상태를 리셋한다`() = runTest {
        // 무한 Flow 로 라이브뷰를 활성 상태로 유지.
        every { startLiveViewUseCase() } returns flow {
            emit(sampleFrame())
            awaitCancellation()
        }
        coEvery { capturePhotoUseCase(ShootingMode.SINGLE) } returns Result.success(samplePhoto())
        val ui = CameraUiStateManager()
        val manager = createManager()

        manager.startLiveView(isConnected = true, cameraCapabilities = null, uiStateManager = ui)
        advanceUntilIdle()
        assertTrue("촬영 전 라이브뷰 활성", manager.isLiveViewActive())

        manager.capturePhoto(ShootingMode.SINGLE, ui)
        advanceUntilIdle()

        assertFalse("촬영이 라이브뷰 Job을 취소", manager.isLiveViewActive())
        assertFalse(ui.uiState.value.liveView.isLiveViewActive)
        assertNull(ui.liveViewFrame.value)
        manager.cleanup()
    }

    // ── 라이브뷰 ──

    @Test
    fun `startLiveView 미연결이면 error 방출하고 UseCase 미호출`() = runTest {
        val ui = CameraUiStateManager()
        val manager = createManager()

        manager.startLiveView(isConnected = false, cameraCapabilities = null, uiStateManager = ui)
        advanceUntilIdle()

        assertNotNull(ui.uiState.value.error)
        assertFalse(ui.uiState.value.liveView.isLiveViewActive)
        verify(exactly = 0) { startLiveViewUseCase() }
        manager.cleanup()
    }

    @Test
    fun `startLiveView 라이브뷰 미지원 카메라면 error 방출하고 UseCase 미호출`() = runTest {
        val ui = CameraUiStateManager()
        val manager = createManager()

        manager.startLiveView(
            isConnected = true,
            cameraCapabilities = sampleCapabilities(canLiveView = false),
            uiStateManager = ui
        )
        advanceUntilIdle()

        assertNotNull(ui.uiState.value.error)
        verify(exactly = 0) { startLiveViewUseCase() }
        manager.cleanup()
    }

    @Test
    fun `startLiveView 성공 시 활성-비로딩 상태와 프레임을 방출한다`() = runTest {
        val frame = sampleFrame()
        every { startLiveViewUseCase() } returns flowOf(frame)
        val ui = CameraUiStateManager()
        val manager = createManager()

        manager.startLiveView(isConnected = true, cameraCapabilities = null, uiStateManager = ui)
        advanceUntilIdle()

        assertTrue(ui.uiState.value.liveView.isLiveViewActive)
        assertFalse(ui.uiState.value.liveView.isLiveViewLoading)
        assertEquals(frame, ui.liveViewFrame.value)
        verify(exactly = 1) { startLiveViewUseCase() }
        manager.cleanup()
    }

    @Test
    fun `startLiveView 중복 호출 시 두 번째는 무시되어 UseCase는 1회만 호출된다`() = runTest {
        every { startLiveViewUseCase() } returns flow {
            emit(sampleFrame())
            awaitCancellation()
        }
        val ui = CameraUiStateManager()
        val manager = createManager()

        // 첫 호출로 liveViewJob 이 active 가 되면 두 번째는 jobLock 가드로 무시된다.
        manager.startLiveView(isConnected = true, cameraCapabilities = null, uiStateManager = ui)
        manager.startLiveView(isConnected = true, cameraCapabilities = null, uiStateManager = ui)
        advanceUntilIdle()

        verify(exactly = 1) { startLiveViewUseCase() }
        manager.cleanup()
    }

    @Test
    fun `stopLiveView는 Job을 취소하고 UseCase 호출 후 라이브뷰 상태를 리셋한다`() = runTest {
        every { startLiveViewUseCase() } returns flow {
            emit(sampleFrame())
            awaitCancellation()
        }
        coEvery { stopLiveViewUseCase() } returns Result.success(true)
        val ui = CameraUiStateManager()
        val manager = createManager()

        manager.startLiveView(isConnected = true, cameraCapabilities = null, uiStateManager = ui)
        advanceUntilIdle()
        assertTrue(manager.isLiveViewActive())

        manager.stopLiveView(ui)
        advanceUntilIdle()

        assertFalse(manager.isLiveViewActive())
        assertFalse(ui.uiState.value.liveView.isLiveViewActive)
        assertNull(ui.liveViewFrame.value)
        coVerify(exactly = 1) { stopLiveViewUseCase() }
        manager.cleanup()
    }

    // ── 타임랩스 ──

    @Test
    fun `startTimelapse 성공 시 TIMELAPSE 모드 설정 후 완료되면 isCapturing false`() = runTest {
        every { startTimelapseUseCase(any()) } returns flowOf(samplePhoto())
        val ui = CameraUiStateManager()
        val manager = createManager()

        manager.startTimelapse(interval = 2, totalShots = 30, uiStateManager = ui)
        advanceUntilIdle()

        assertEquals(ShootingMode.TIMELAPSE, ui.uiState.value.capture.shootingMode)
        assertFalse(ui.uiState.value.capture.isCapturing)
        // duration = (interval * totalShots) / 60 = 1.
        verify(exactly = 1) { startTimelapseUseCase(TimelapseSettings(interval = 2, totalShots = 30, duration = 1)) }
        manager.cleanup()
    }

    @Test
    fun `startTimelapse 미지원 모드 예외 시 마커를 세우고 모드를 SINGLE로 롤백한다`() = runTest {
        every { startTimelapseUseCase(any()) } returns flow<CapturedPhoto> {
            throw UnsupportedShootingModeException(ShootingMode.TIMELAPSE)
        }
        val ui = CameraUiStateManager()
        val manager = createManager()

        manager.startTimelapse(interval = 2, totalShots = 30, uiStateManager = ui)
        advanceUntilIdle()

        assertEquals(
            CameraOperationsManager.UNSUPPORTED_MODE_PREFIX + "TIMELAPSE",
            ui.uiState.value.dialog.shootingModeError
        )
        // 셔터가 TIMELAPSE 무한 실패 루프에 묶이지 않도록 SINGLE 로 롤백.
        assertEquals(ShootingMode.SINGLE, ui.uiState.value.capture.shootingMode)
        assertFalse(ui.uiState.value.capture.isCapturing)
        manager.cleanup()
    }

    @Test
    fun `startTimelapse 중복 호출 시 두 번째는 무시되어 UseCase는 1회만 호출된다`() = runTest {
        every { startTimelapseUseCase(any()) } returns flow {
            emit(samplePhoto())
            awaitCancellation()
        }
        val ui = CameraUiStateManager()
        val manager = createManager()

        manager.startTimelapse(interval = 2, totalShots = 30, uiStateManager = ui)
        manager.startTimelapse(interval = 2, totalShots = 30, uiStateManager = ui)
        advanceUntilIdle()

        verify(exactly = 1) { startTimelapseUseCase(any()) }
        manager.cleanup()
    }

    @Test
    fun `stopTimelapse는 Job을 취소하고 isCapturing false`() = runTest {
        every { startTimelapseUseCase(any()) } returns flow {
            emit(samplePhoto())
            awaitCancellation()
        }
        val ui = CameraUiStateManager()
        val manager = createManager()

        manager.startTimelapse(interval = 2, totalShots = 30, uiStateManager = ui)
        advanceUntilIdle()
        assertTrue(manager.isTimelapseActive())

        manager.stopTimelapse(ui)
        advanceUntilIdle()

        assertFalse(manager.isTimelapseActive())
        assertFalse(ui.uiState.value.capture.isCapturing)
        manager.cleanup()
    }

    // ── 자동초점 ──

    @Test
    fun `performAutoFocus 성공 시 isFocusing false와 AutoFocusCompleted 정보 메시지 방출`() = runTest {
        coEvery { performAutoFocusUseCase() } returns Result.success(true)
        val ui = CameraUiStateManager()
        val manager = createManager()

        // Turbine 이 액션 전에 구독을 확립해 1-shot SharedFlow(replay=0) 방출을 놓치지 않게 한다.
        ui.infoMessage.test {
            manager.performAutoFocus(ui)
            advanceUntilIdle()
            assertEquals(InfoMessage.AutoFocusCompleted, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertFalse(ui.uiState.value.capture.isFocusing)
        manager.cleanup()
    }

    @Test
    fun `performAutoFocus 실패 시 error 방출하고 isFocusing false`() = runTest {
        coEvery { performAutoFocusUseCase() } returns Result.failure(RuntimeException("AF 실패"))
        val ui = CameraUiStateManager()
        val manager = createManager()

        manager.performAutoFocus(ui)
        advanceUntilIdle()

        assertNotNull(ui.uiState.value.error)
        assertFalse(ui.uiState.value.capture.isFocusing)
        manager.cleanup()
    }

    // ── cleanup ──

    @Test
    fun `cleanup은 진행 중인 라이브뷰-타임랩스 Job을 모두 취소한다`() = runTest {
        every { startLiveViewUseCase() } returns flow {
            emit(sampleFrame())
            awaitCancellation()
        }
        every { startTimelapseUseCase(any()) } returns flow {
            emit(samplePhoto())
            awaitCancellation()
        }
        val ui = CameraUiStateManager()
        val manager = createManager()

        manager.startLiveView(isConnected = true, cameraCapabilities = null, uiStateManager = ui)
        manager.startTimelapse(interval = 2, totalShots = 30, uiStateManager = ui)
        advanceUntilIdle()
        assertTrue(manager.isLiveViewActive())
        assertTrue(manager.isTimelapseActive())

        manager.cleanup()
        advanceUntilIdle()

        assertFalse(manager.isLiveViewActive())
        assertFalse(manager.isTimelapseActive())
    }

    // ── 헬퍼 ──

    private fun samplePhoto() = CapturedPhoto(
        id = "1",
        filePath = "/tmp/KAY_1000.jpg",
        thumbnailPath = null,
        captureTime = 0L,
        cameraModel = "Nikon Z8",
        settings = null,
        size = 1024L,
        width = 100,
        height = 100
    )

    private fun sampleFrame() = LiveViewFrame(
        data = ByteArray(4),
        width = 2,
        height = 2,
        timestamp = 0L
    )

    private fun sampleCapabilities(canLiveView: Boolean) = CameraCapabilities(
        model = "Nikon Z8",
        canCapturePhoto = true,
        canCaptureVideo = false,
        canLiveView = canLiveView,
        canTriggerCapture = true,
        supportsBurstMode = false,
        supportsTimelapse = false,
        supportsBracketing = false,
        supportsBulbMode = false,
        supportsAutofocus = true,
        supportsManualFocus = false,
        supportsFocusPoint = false,
        canDownloadFiles = true,
        canDeleteFiles = true,
        canPreviewFiles = true,
        availableIsoSettings = emptyList(),
        availableShutterSpeeds = emptyList(),
        availableApertures = emptyList(),
        availableWhiteBalanceSettings = emptyList(),
        supportsRemoteControl = true,
        supportsConfigChange = true
    )
}
