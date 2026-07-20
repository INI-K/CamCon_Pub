package com.inik.camcon.presentation.viewmodel

import android.content.Context
import android.util.Log
import com.inik.camcon.R
import com.inik.camcon.domain.model.PtpTimeoutException
import com.inik.camcon.domain.model.UiText
import com.inik.camcon.domain.model.UsbDeviceInfo
import com.inik.camcon.domain.repository.AppSettingsRepository
import com.inik.camcon.domain.repository.CameraRepository
import com.inik.camcon.domain.repository.UsbDeviceRepository
import com.inik.camcon.domain.usecase.camera.ConnectCameraUseCase
import com.inik.camcon.domain.usecase.camera.DisconnectCameraUseCase
import com.inik.camcon.domain.usecase.usb.RefreshUsbDevicesUseCase
import com.inik.camcon.domain.usecase.usb.RequestUsbPermissionUseCase
import com.inik.camcon.presentation.viewmodel.state.CameraUiStateManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkStatic
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
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
 * [UsbAutoConnectManager] 단위 테스트 — USB 디바이스 관찰·자동 연결 트리거·usbInit 메시지(UiText)의
 * **StateFlow 방출**을 검증한다(구현 세부 검증 금지, 프로젝트 규약).
 *
 * 협력자:
 *  - uiStateManager: 실제 [CameraUiStateManager] — uiState 방출로 결과 관찰.
 *  - usbDeviceRepository: MutableStateFlow 로 device/permission 을 구동해 관찰 파이프라인을 실제로 태운다.
 *  - UseCase/repository: mockk. 성공/실패 `Result` 로 시나리오 표현.
 *  - appScope 및 observe scope: [StandardTestDispatcher] 공유 — `advanceUntilIdle` 로 구동.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UsbAutoConnectManagerTest {

    private val context = mockk<Context>(relaxed = true)
    private val cameraRepository = mockk<CameraRepository>(relaxed = true)
    private val connectCameraUseCase = mockk<ConnectCameraUseCase>()
    private val disconnectCameraUseCase = mockk<DisconnectCameraUseCase>()
    private val refreshUsbDevicesUseCase = mockk<RefreshUsbDevicesUseCase>()
    private val requestUsbPermissionUseCase = mockk<RequestUsbPermissionUseCase>(relaxed = true)
    private val usbDeviceRepository = mockk<UsbDeviceRepository>(relaxed = true)
    private val appSettingsRepository = mockk<AppSettingsRepository>(relaxed = true)

    private val deviceCountFlow = MutableStateFlow(0)
    private val permissionFlow = MutableStateFlow(false)
    private val nativeConnectedFlow = MutableStateFlow(false)

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.i(any(), any()) } returns 0

        every { usbDeviceRepository.connectedDeviceCount } returns deviceCountFlow
        every { usbDeviceRepository.hasUsbPermission } returns permissionFlow
        every { usbDeviceRepository.isNativeCameraConnected } returns nativeConnectedFlow
        coEvery { usbDeviceRepository.checkPowerStateAndTest() } returns Unit
        // 자동 시작 비활성 → tryAutoStartEventListener 가 조기 리턴(성공 경로가 이벤트 리스너까지 가지 않도록).
        every { appSettingsRepository.isAutoStartEventListenerEnabled } returns flowOf(false)
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    private fun TestScope.createManager(): UsbAutoConnectManager {
        val appScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        return UsbAutoConnectManager(
            context = context,
            cameraRepository = cameraRepository,
            connectCameraUseCase = connectCameraUseCase,
            disconnectCameraUseCase = disconnectCameraUseCase,
            refreshUsbDevicesUseCase = refreshUsbDevicesUseCase,
            requestUsbPermissionUseCase = requestUsbPermissionUseCase,
            usbDeviceRepository = usbDeviceRepository,
            appSettingsRepository = appSettingsRepository,
            appScope = appScope
        )
    }

    // ── autoConnectCamera ──

    @Test
    fun `autoConnectCamera 성공 시 연결 성공 상태로 방출하고 자동연결 플래그를 내린다`() = runTest {
        coEvery { connectCameraUseCase("auto") } returns Result.success(true)
        val ui = CameraUiStateManager()
        val manager = createManager()

        manager.autoConnectCamera(ui)
        advanceUntilIdle()

        val state = ui.uiState.value
        assertTrue(state.isConnected)
        assertFalse(state.connection.isUsbInitializing)
        assertNull(state.usbInitializationMessage)
        assertFalse(manager.isAutoConnecting.value)
        coVerify(exactly = 1) { connectCameraUseCase("auto") }
    }

    @Test
    fun `autoConnectCamera 실패 시 연결 실패 방출하고 초기화 상태를 해제한다`() = runTest {
        coEvery { connectCameraUseCase("auto") } returns Result.failure(RuntimeException("연결 실패"))
        val ui = CameraUiStateManager()
        val manager = createManager()

        manager.autoConnectCamera(ui)
        advanceUntilIdle()

        val state = ui.uiState.value
        assertFalse(state.isConnected)
        assertNotNull(state.error)
        assertFalse(state.connection.isUsbInitializing)
        assertFalse(manager.isAutoConnecting.value)
    }

    @Test
    fun `autoConnectCamera PtpTimeout 실패 시 재시작 다이얼로그를 띄운다`() = runTest {
        coEvery { connectCameraUseCase("auto") } returns
            Result.failure(PtpTimeoutException("PTP timeout"))
        val ui = CameraUiStateManager()
        val manager = createManager()

        manager.autoConnectCamera(ui)
        advanceUntilIdle()

        val state = ui.uiState.value
        assertTrue(state.showRestartDialog)
        assertTrue(state.connection.isPtpTimeout)
        assertFalse(manager.isAutoConnecting.value)
    }

    // ── disconnectCamera ──

    @Test
    fun `disconnectCamera는 UseCase 호출 후 연결 해제 상태로 방출한다`() = runTest {
        coEvery { disconnectCameraUseCase() } returns Result.success(true)
        val ui = CameraUiStateManager()
        // 먼저 연결 상태로 만든 뒤 해제.
        ui.updateConnectionState(true)
        val manager = createManager()

        manager.disconnectCamera(ui)
        advanceUntilIdle()

        val state = ui.uiState.value
        assertFalse(state.isConnected)
        assertFalse(state.isNativeCameraConnected)
        assertNull(state.error)
        coVerify(exactly = 1) { disconnectCameraUseCase() }
    }

    // ── requestUsbPermission (usbInit 메시지 = UiText) ──

    @Test
    fun `requestUsbPermission은 디바이스가 있으면 권한 요청 후 대기 메시지를 방출한다`() = runTest {
        every { refreshUsbDevicesUseCase() } returns listOf(sampleDevice())
        val ui = CameraUiStateManager()
        val manager = createManager()

        manager.requestUsbPermission(ui)
        advanceUntilIdle()

        verify(exactly = 1) { requestUsbPermissionUseCase("dev-1") }
        val message = ui.uiState.value.usbInitializationMessage
        assertTrue("usbInit 메시지는 UiText.Resource 여야 한다", message is UiText.Resource)
        assertEquals(
            R.string.usb_init_waiting_permission,
            (message as UiText.Resource).resId
        )
        assertFalse(ui.uiState.value.connection.isUsbInitializing)
        assertNotNull(ui.uiState.value.error)
    }

    @Test
    fun `requestUsbPermission은 디바이스가 없으면 에러 방출하고 초기화 상태를 해제한다`() = runTest {
        every { refreshUsbDevicesUseCase() } returns emptyList()
        val ui = CameraUiStateManager()
        val manager = createManager()

        manager.requestUsbPermission(ui)
        advanceUntilIdle()

        verify(exactly = 0) { requestUsbPermissionUseCase(any()) }
        assertNotNull(ui.uiState.value.error)
        assertFalse(ui.uiState.value.connection.isUsbInitializing)
    }

    // ── refreshUsbDevices ──

    @Test
    fun `refreshUsbDevices는 디바이스가 없으면 미감지 에러를 방출한다`() = runTest {
        every { refreshUsbDevicesUseCase() } returns emptyList()
        val ui = CameraUiStateManager()
        val manager = createManager()

        manager.refreshUsbDevices(ui)
        advanceUntilIdle()

        assertNotNull(ui.uiState.value.error)
        // 미감지 상태에서 연결 시도가 일어나면 안 된다.
        coVerify(exactly = 0) { connectCameraUseCase(any()) }
    }

    // ── observeUsbDevices (자동 연결 트리거) ──

    @Test
    fun `observeUsbDevices는 디바이스와 권한이 모두 충족되면 자동 연결을 트리거한다`() = runTest {
        coEvery { connectCameraUseCase("auto") } returns Result.success(true)
        val ui = CameraUiStateManager()
        val appScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val manager = UsbAutoConnectManager(
            context = context,
            cameraRepository = cameraRepository,
            connectCameraUseCase = connectCameraUseCase,
            disconnectCameraUseCase = disconnectCameraUseCase,
            refreshUsbDevicesUseCase = refreshUsbDevicesUseCase,
            requestUsbPermissionUseCase = requestUsbPermissionUseCase,
            usbDeviceRepository = usbDeviceRepository,
            appSettingsRepository = appSettingsRepository,
            appScope = appScope
        )

        // 디바이스 연결 + 권한 보유 상태에서 관찰 시작.
        deviceCountFlow.value = 1
        permissionFlow.value = true
        manager.observeUsbDevices(appScope, ui)
        advanceUntilIdle()

        coVerify(atLeast = 1) { connectCameraUseCase("auto") }
        assertTrue(ui.uiState.value.isConnected)
        assertFalse(manager.isAutoConnecting.value)

        appScope.cancel()
    }

    // ── 헬퍼 ──

    private fun sampleDevice() = UsbDeviceInfo(
        deviceId = "dev-1",
        deviceName = "Nikon Z8",
        vendorId = 0x04b0,
        productId = 0x0000
    )
}
