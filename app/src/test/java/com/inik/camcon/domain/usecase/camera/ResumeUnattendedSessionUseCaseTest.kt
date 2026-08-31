package com.inik.camcon.domain.usecase.camera

import com.inik.camcon.domain.manager.CameraConnectionGlobalManager
import com.inik.camcon.domain.model.CameraConnectionType
import com.inik.camcon.domain.model.GlobalCameraConnectionState
import com.inik.camcon.domain.model.PersistedUnattendedSession
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.UsbDeviceInfo
import com.inik.camcon.domain.repository.PtpipPreferencesRepository
import com.inik.camcon.domain.repository.PtpipRepository
import com.inik.camcon.domain.repository.UnattendedSessionRepository
import com.inik.camcon.domain.repository.UsbDeviceRepository
import com.inik.camcon.domain.util.Logger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * 프로세스 재기동 복구의 판단 규칙.
 *
 * 이 UseCase 가 지켜야 하는 계약은 "되살릴 근거가 있을 때만, 상한 안에서 한 번 시도한다"이다.
 * 특히 **USB 권한이 없으면 시도조차 하지 않는다** — 무인 상태에서 권한 대화상자를 띄우면
 * 아무도 응답하지 못한 채 상한까지 시간만 태우고, 그동안 WakeLock 이 잡혀 있다.
 *
 * 실제 연결(JNI·PTP/IP)은 실물 장비가 필요한 영역이라 협력자를 대역으로 두고 **호출 여부와
 * 판정 결과**만 검증한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ResumeUnattendedSessionUseCaseTest {

    private val startMillis = 1_788_100_000_000L

    private val sessionRepository: UnattendedSessionRepository = mockk(relaxed = true)
    private val usbDeviceRepository: UsbDeviceRepository = mockk(relaxed = true)
    private val ptpipPreferencesRepository: PtpipPreferencesRepository = mockk(relaxed = true)
    private val ptpipRepository: PtpipRepository = mockk(relaxed = true)
    private val logger: Logger = mockk(relaxed = true)

    private val globalState = MutableStateFlow(GlobalCameraConnectionState())
    private val globalManager: CameraConnectionGlobalManager = mockk(relaxed = true) {
        every { globalConnectionState } returns globalState
    }

    private val fixedClock: Clock = Clock.fixed(
        Instant.ofEpochMilli(startMillis),
        ZoneOffset.UTC
    )

    private fun TestScope.createUseCase() = ResumeUnattendedSessionUseCase(
        sessionRepository = sessionRepository,
        usbDeviceRepository = usbDeviceRepository,
        ptpipPreferencesRepository = ptpipPreferencesRepository,
        ptpipRepository = ptpipRepository,
        globalConnectionManager = globalManager,
        logger = logger,
        clock = fixedClock,
        ioDispatcher = UnconfinedTestDispatcher(testScheduler)
    )

    private fun savedSession(type: CameraConnectionType) = PersistedUnattendedSession(
        startedAtMillis = startMillis,
        connectionType = type,
        cameraLabel = "Niko***"
    )

    private fun attachUsbDevice() {
        every { usbDeviceRepository.getCameraDevices() } returns listOf(
            UsbDeviceInfo(
                deviceId = "1",
                deviceName = "/dev/bus/usb/001/002",
                vendorId = 0x04B0,
                productId = 0x0447
            )
        )
    }

    /** 연결 요청이 들어오면 전역 상태가 활성으로 서는 실제 흐름을 흉내 낸다. */
    private fun connectSucceeds() {
        globalState.value = GlobalCameraConnectionState(
            isAnyConnectionActive = true,
            activeConnectionType = CameraConnectionType.USB
        )
    }

    @Test
    fun `USB 장치가 붙어 있고 권한도 있으면 재초기화한다`() = runTest {
        coEvery { sessionRepository.load() } returns savedSession(CameraConnectionType.USB)
        attachUsbDevice()
        every { usbDeviceRepository.hasPermissionForAttachedCamera() } returns true
        coEvery { usbDeviceRepository.connectToFirstCamera() } answers { connectSucceeds() }

        val recovered = createUseCase().invoke()

        assertTrue("장치·권한이 모두 있으면 복구에 성공해야 한다", recovered)
        coVerify(exactly = 1) { usbDeviceRepository.connectToFirstCamera() }
    }

    @Test
    fun `USB 권한이 없으면 연결을 시도조차 하지 않는다`() = runTest {
        coEvery { sessionRepository.load() } returns savedSession(CameraConnectionType.USB)
        attachUsbDevice()
        every { usbDeviceRepository.hasPermissionForAttachedCamera() } returns false

        val recovered = createUseCase().invoke()

        assertFalse(recovered)
        // 무인 상태에서 권한 대화상자를 띄우면 응답할 사람이 없다. 시도 자체를 하지 않아야 한다.
        coVerify(exactly = 0) { usbDeviceRepository.connectToFirstCamera() }
    }

    @Test
    fun `USB 장치가 빠져 있으면 복구하지 않는다`() = runTest {
        coEvery { sessionRepository.load() } returns savedSession(CameraConnectionType.USB)
        every { usbDeviceRepository.getCameraDevices() } returns emptyList()

        val recovered = createUseCase().invoke()

        assertFalse(recovered)
        coVerify(exactly = 0) { usbDeviceRepository.connectToFirstCamera() }
    }

    @Test
    fun `Wi-Fi 는 자동 재연결이 켜져 있을 때만 한 번 시도한다`() = runTest {
        coEvery { sessionRepository.load() } returns savedSession(CameraConnectionType.STA_MODE)
        every { ptpipPreferencesRepository.isAutoReconnectEnabled } returns flowOf(true)
        every { ptpipPreferencesRepository.ptpipPort } returns flowOf(15740)
        coEvery { ptpipPreferencesRepository.getLastConnectedCameraInfo() } returns
                ("192.168.10.1" to "Z8")
        coEvery { ptpipRepository.connectToCamera(any(), any()) } answers {
            connectSucceeds()
            true
        }

        val recovered = createUseCase().invoke()

        assertTrue(recovered)
        coVerify(exactly = 1) {
            ptpipRepository.connectToCamera(
                PtpipCamera(ipAddress = "192.168.10.1", port = 15740, name = "Z8"),
                any()
            )
        }
    }

    @Test
    fun `Wi-Fi 자동 재연결이 꺼져 있으면 시도하지 않는다`() = runTest {
        coEvery { sessionRepository.load() } returns savedSession(CameraConnectionType.STA_MODE)
        every { ptpipPreferencesRepository.isAutoReconnectEnabled } returns flowOf(false)

        val recovered = createUseCase().invoke()

        assertFalse(recovered)
        // 사용자가 끈 동작을 배경에서 되살리면 안 된다.
        coVerify(exactly = 0) { ptpipRepository.connectToCamera(any(), any()) }
    }

    @Test
    fun `세션 기록이 없으면 아무것도 되살리지 않는다`() = runTest {
        coEvery { sessionRepository.load() } returns null

        val recovered = createUseCase().invoke()

        assertFalse(recovered)
        coVerify(exactly = 0) { usbDeviceRepository.connectToFirstCamera() }
        coVerify(exactly = 0) { ptpipRepository.connectToCamera(any(), any()) }
    }

    @Test
    fun `이미 연결이 살아 있으면 다시 연결하지 않는다`() = runTest {
        coEvery { sessionRepository.load() } returns savedSession(CameraConnectionType.USB)
        connectSucceeds()

        val recovered = createUseCase().invoke()

        assertTrue(recovered)
        coVerify(exactly = 0) { usbDeviceRepository.connectToFirstCamera() }
    }
}
