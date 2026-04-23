package com.inik.camcon.data.repository

import android.content.Context
import com.inik.camcon.data.datasource.nativesource.NativeCameraDataSource
import com.inik.camcon.data.datasource.ptpip.PtpipDataSource
import com.inik.camcon.data.datasource.usb.UsbCameraManager
import com.inik.camcon.data.repository.managers.CameraConnectionManager
import com.inik.camcon.data.repository.managers.CameraEventManager
import com.inik.camcon.data.repository.managers.PhotoDownloadManager
import com.inik.camcon.domain.manager.CameraStateObserver
import com.inik.camcon.domain.model.ShootingMode
import com.inik.camcon.domain.model.UnsupportedShootingModeException
import com.inik.camcon.domain.repository.ColorTransferRepository
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Issue W2: 미구현 촬영 모드 예외 처리 테스트
 * BURST, TIMELAPSE, HDR_BRACKET, BULB 모드는 현재 미구현 상태이므로
 * capturePhoto()가 UnsupportedShootingModeException을 Result.failure로 반환해야 한다.
 *
 * H8 분해(2026-04-23) 후: Facade가 `captureRepo.capturePhoto(mode)`로 delegate. 예외 처리는 Capture sub-impl에 위치.
 */
class CameraRepositoryImplShootingModeTest {

    private lateinit var repository: CameraRepositoryImpl

    @Before
    fun setUp() {
        val context = mockk<Context>(relaxed = true)
        val colorTransferRepository = mockk<ColorTransferRepository>(relaxed = true)
        val cameraStateObserver = mockk<CameraStateObserver>(relaxed = true)

        // Capture sub-impl의 의존성 mocks
        val nativeDataSource = mockk<NativeCameraDataSource>(relaxed = true)
        val ptpipDataSource = mockk<PtpipDataSource>(relaxed = true)
        val usbCameraManager = mockk<UsbCameraManager>(relaxed = true)
        val connectionManager = mockk<CameraConnectionManager>(relaxed = true)
        val eventManager = mockk<CameraEventManager>(relaxed = true)
        val downloadManager = mockk<PhotoDownloadManager>(relaxed = true)
        val scope = mockk<CoroutineScope>(relaxed = true)
        val ioDispatcher: CoroutineDispatcher = StandardTestDispatcher()

        // 실제 Capture sub-impl (미구현 모드 검증 로직 포함)
        val captureRepo = CameraCaptureRepositoryImpl(
            context = context,
            nativeDataSource = nativeDataSource,
            ptpipDataSource = ptpipDataSource,
            usbCameraManager = usbCameraManager,
            connectionManager = connectionManager,
            eventManager = eventManager,
            downloadManager = downloadManager,
            scope = scope,
            ioDispatcher = ioDispatcher
        )

        val lifecycleRepo = mockk<CameraLifecycleRepositoryImpl>(relaxed = true)
        val controlRepo = mockk<CameraControlRepositoryImpl>(relaxed = true)

        repository = CameraRepositoryImpl(
            context = context,
            colorTransferRepository = colorTransferRepository,
            cameraStateObserver = cameraStateObserver,
            lifecycleRepo = lifecycleRepo,
            captureRepo = captureRepo,
            controlRepo = controlRepo
        )
    }

    @Test
    fun burstModeReturnsUnsupportedShootingModeException() = runTest {
        val result = repository.capturePhoto(ShootingMode.BURST)
        assertTrue("Result must be failure", result.isFailure)

        val exception = result.exceptionOrNull() as? UnsupportedShootingModeException
        assertTrue("Exception should be UnsupportedShootingModeException", exception != null)
        assertTrue("Exception mode should be BURST", exception?.mode == ShootingMode.BURST)
    }

    @Test
    fun timelapseModReturnsUnsupportedShootingModeException() = runTest {
        val result = repository.capturePhoto(ShootingMode.TIMELAPSE)
        assertTrue("Result must be failure", result.isFailure)

        val exception = result.exceptionOrNull() as? UnsupportedShootingModeException
        assertTrue("Exception should be UnsupportedShootingModeException", exception != null)
        assertTrue("Exception mode should be TIMELAPSE", exception?.mode == ShootingMode.TIMELAPSE)
    }

    @Test
    fun hdrBracketModeReturnsUnsupportedShootingModeException() = runTest {
        val result = repository.capturePhoto(ShootingMode.HDR_BRACKET)
        assertTrue("Result must be failure", result.isFailure)

        val exception = result.exceptionOrNull() as? UnsupportedShootingModeException
        assertTrue("Exception should be UnsupportedShootingModeException", exception != null)
        assertTrue("Exception mode should be HDR_BRACKET", exception?.mode == ShootingMode.HDR_BRACKET)
    }

    @Test
    fun bulbModeReturnsUnsupportedShootingModeException() = runTest {
        val result = repository.capturePhoto(ShootingMode.BULB)
        assertTrue("Result must be failure", result.isFailure)

        val exception = result.exceptionOrNull() as? UnsupportedShootingModeException
        assertTrue("Exception should be UnsupportedShootingModeException", exception != null)
        assertTrue("Exception mode should be BULB", exception?.mode == ShootingMode.BULB)
    }

    @Test
    fun exceptionOnlyContainsUnsupportedMode() = runTest {
        val result = repository.capturePhoto(ShootingMode.BURST)
        val exception = result.exceptionOrNull() as? UnsupportedShootingModeException

        assertTrue(
            "SINGLE should be in supported modes",
            ShootingMode.SINGLE in (exception?.supportedModes ?: emptyList())
        )
        assertTrue(
            "Supported modes should only contain SINGLE",
            exception?.supportedModes?.size == 1
        )
    }

    @Test
    fun allUnsupportedModesThrowSameExceptionType() = runTest {
        val unsupportedModes = listOf(
            ShootingMode.BURST,
            ShootingMode.TIMELAPSE,
            ShootingMode.HDR_BRACKET,
            ShootingMode.BULB
        )

        for (mode in unsupportedModes) {
            val result = repository.capturePhoto(mode)
            assertTrue("Result must be failure", result.isFailure)

            val exception = result.exceptionOrNull() as? UnsupportedShootingModeException
            assertTrue("Exception should be UnsupportedShootingModeException", exception != null)
            assertTrue("Exception mode should be $mode", exception?.mode == mode)
            assertTrue(
                "SINGLE should be in supported modes",
                exception?.supportedModes?.contains(ShootingMode.SINGLE) == true
            )
        }
    }
}
