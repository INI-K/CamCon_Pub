package com.inik.camcon.domain.usecase.camera

import com.inik.camcon.data.repository.fake.FakeCameraRepositoryBasic
import com.inik.camcon.domain.model.CameraSettings
import com.inik.camcon.domain.model.CapturedPhoto
import com.inik.camcon.domain.model.ShootingMode
import com.inik.camcon.domain.model.UnsupportedShootingModeException
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

class CapturePhotoUseCaseTest {

    private lateinit var useCase: CapturePhotoUseCase
    private lateinit var fakeRepository: FakeCameraRepositoryBasic

    @Before
    fun setUp() {
        fakeRepository = FakeCameraRepositoryBasic()
        useCase = CapturePhotoUseCase(fakeRepository)
    }

    // ====== Happy Path Tests ======

    @Test
    fun singleModeSuccessfullyCapturesPhoto() = runTest {
        // given
        val testPhoto = CapturedPhoto(
            id = "photo-001",
            filePath = "/sdcard/DCIM/photo.jpg",
            thumbnailPath = "/sdcard/DCIM/.thumb/photo.jpg",
            captureTime = System.currentTimeMillis(),
            cameraModel = "Canon EOS 5D Mark IV",
            settings = CameraSettings(
                iso = "100",
                shutterSpeed = "1/1000",
                aperture = "f/2.8",
                whiteBalance = "Auto",
                focusMode = "AF-S",
                exposureCompensation = "0.0"
            ),
            size = 5242880L,
            width = 5472,
            height = 3648
        )
        fakeRepository.capturePhotoResult = Result.success(testPhoto)

        // when
        val result = useCase(ShootingMode.SINGLE)

        // then
        assertTrue("Result must be success", result.isSuccess)
        val capturedPhoto = result.getOrNull()
        assertNotNull("Captured photo must not be null", capturedPhoto)
        assertEquals("Photo ID must match", testPhoto.id, capturedPhoto?.id)
        assertEquals("Camera model must match", testPhoto.cameraModel, capturedPhoto?.cameraModel)
    }

    @Test
    fun capturePhotoParametersPropagateCorrectly() = runTest {
        // given
        val testPhoto = CapturedPhoto(
            id = "photo-002",
            filePath = "/sdcard/test.jpg",
            thumbnailPath = null,
            captureTime = 1000L,
            cameraModel = "Test Camera",
            settings = null,
            size = 1000L,
            width = 1920,
            height = 1080
        )
        fakeRepository.capturePhotoResult = Result.success(testPhoto)

        // when
        val result = useCase(ShootingMode.SINGLE)

        // then
        assertTrue("Result must be success", result.isSuccess)
        assertEquals("Last capture mode must be SINGLE", ShootingMode.SINGLE, fakeRepository.lastCapturePhotoMode)
        assertEquals("Capture photo call count must be 1", 1, fakeRepository.capturePhotoCallCount)
    }

    @Test
    fun capturePhotoReturnsCompletePhotoData() = runTest {
        // given
        val expectedSettings = CameraSettings(
            iso = "400",
            shutterSpeed = "1/500",
            aperture = "f/4.0",
            whiteBalance = "Daylight",
            focusMode = "AF-C",
            exposureCompensation = "+1.0"
        )
        val testPhoto = CapturedPhoto(
            id = "photo-003",
            filePath = "/sdcard/DCIM/complete.jpg",
            thumbnailPath = "/sdcard/DCIM/.thumb/complete.jpg",
            captureTime = 2000L,
            cameraModel = "Complete Camera",
            settings = expectedSettings,
            size = 8388608L,
            width = 6000,
            height = 4000
        )
        fakeRepository.capturePhotoResult = Result.success(testPhoto)

        // when
        val result = useCase(ShootingMode.SINGLE)

        // then
        assertTrue("Result must be success", result.isSuccess)
        val photo = result.getOrNull()
        assertNotNull("Photo must not be null", photo)
        assertNotNull("Settings must not be null", photo?.settings)
        assertEquals("ISO must match", "400", photo?.settings?.iso)
        assertEquals("Shutter speed must match", "1/500", photo?.settings?.shutterSpeed)
        assertEquals("Aperture must match", "f/4.0", photo?.settings?.aperture)
        assertEquals("Width must match", 6000, photo?.width)
        assertEquals("Height must match", 4000, photo?.height)
    }

    // ====== Unsupported Shooting Modes Tests ======

    @Test
    fun burstModeThrowsUnsupportedShootingModeException() = runTest {
        // given
        val exception = UnsupportedShootingModeException(
            mode = ShootingMode.BURST,
            supportedModes = listOf(ShootingMode.SINGLE)
        )
        fakeRepository.capturePhotoResult = Result.failure(exception)

        // when
        val result = useCase(ShootingMode.BURST)

        // then
        assertTrue("Result must be failure", result.isFailure)
        val capturedException = result.exceptionOrNull() as? UnsupportedShootingModeException
        assertNotNull("Exception must be UnsupportedShootingModeException", capturedException)
        assertEquals("Exception mode must be BURST", ShootingMode.BURST, capturedException?.mode)
    }

    @Test
    fun timelapseModThrowsUnsupportedShootingModeException() = runTest {
        // given
        val exception = UnsupportedShootingModeException(
            mode = ShootingMode.TIMELAPSE,
            supportedModes = listOf(ShootingMode.SINGLE)
        )
        fakeRepository.capturePhotoResult = Result.failure(exception)

        // when
        val result = useCase(ShootingMode.TIMELAPSE)

        // then
        assertTrue("Result must be failure", result.isFailure)
        val capturedException = result.exceptionOrNull() as? UnsupportedShootingModeException
        assertNotNull("Exception must be UnsupportedShootingModeException", capturedException)
        assertEquals("Exception mode must be TIMELAPSE", ShootingMode.TIMELAPSE, capturedException?.mode)
    }

    @Test
    fun hdrBracketModeThrowsUnsupportedShootingModeException() = runTest {
        // given
        val exception = UnsupportedShootingModeException(
            mode = ShootingMode.HDR_BRACKET,
            supportedModes = listOf(ShootingMode.SINGLE)
        )
        fakeRepository.capturePhotoResult = Result.failure(exception)

        // when
        val result = useCase(ShootingMode.HDR_BRACKET)

        // then
        assertTrue("Result must be failure", result.isFailure)
        val capturedException = result.exceptionOrNull() as? UnsupportedShootingModeException
        assertNotNull("Exception must be UnsupportedShootingModeException", capturedException)
        assertEquals("Exception mode must be HDR_BRACKET", ShootingMode.HDR_BRACKET, capturedException?.mode)
    }

    @Test
    fun bulbModeThrowsUnsupportedShootingModeException() = runTest {
        // given
        val exception = UnsupportedShootingModeException(
            mode = ShootingMode.BULB,
            supportedModes = listOf(ShootingMode.SINGLE)
        )
        fakeRepository.capturePhotoResult = Result.failure(exception)

        // when
        val result = useCase(ShootingMode.BULB)

        // then
        assertTrue("Result must be failure", result.isFailure)
        val capturedException = result.exceptionOrNull() as? UnsupportedShootingModeException
        assertNotNull("Exception must be UnsupportedShootingModeException", capturedException)
        assertEquals("Exception mode must be BULB", ShootingMode.BULB, capturedException?.mode)
    }

    @Test
    fun allUnsupportedModesThrowSameExceptionType() = runTest {
        // given
        val unsupportedModes = listOf(
            ShootingMode.BURST,
            ShootingMode.TIMELAPSE,
            ShootingMode.HDR_BRACKET,
            ShootingMode.BULB
        )

        for (mode in unsupportedModes) {
            val exception = UnsupportedShootingModeException(
                mode = mode,
                supportedModes = listOf(ShootingMode.SINGLE)
            )
            fakeRepository.capturePhotoResult = Result.failure(exception)

            // when
            val result = useCase(mode)

            // then
            assertTrue("Result must be failure for mode $mode", result.isFailure)
            val capturedException = result.exceptionOrNull() as? UnsupportedShootingModeException
            assertNotNull("Exception should be UnsupportedShootingModeException for mode $mode", capturedException)
            assertEquals("Exception mode should be $mode", mode, capturedException?.mode)
            assertTrue(
                "SINGLE should be in supported modes for mode $mode",
                capturedException?.supportedModes?.contains(ShootingMode.SINGLE) == true
            )
        }
    }

    // ====== Error Scenarios Tests ======

    @Test
    fun cameraNotConnectedReturnsFailure() = runTest {
        // given
        val exception = IllegalStateException("Camera not connected")
        fakeRepository.capturePhotoResult = Result.failure(exception)

        // when
        val result = useCase(ShootingMode.SINGLE)

        // then
        assertTrue("Result must be failure", result.isFailure)
        assertFalse("Result must not be success", result.isSuccess)
        val capturedException = result.exceptionOrNull()
        assertNotNull("Exception must not be null", capturedException)
        assertEquals("Exception message must indicate camera not connected", "Camera not connected", capturedException?.message)
    }

    @Test
    fun repositoryErrorIsPropagated() = runTest {
        // given
        val customException = RuntimeException("Custom repository error")
        fakeRepository.capturePhotoResult = Result.failure(customException)

        // when
        val result = useCase(ShootingMode.SINGLE)

        // then
        assertTrue("Result must be failure", result.isFailure)
        val capturedException = result.exceptionOrNull()
        assertNotNull("Exception must be propagated", capturedException)
        assertEquals("Exception type must match", RuntimeException::class.java, capturedException?.javaClass)
    }

    @Test
    fun repositoryErrorMessageIsPreserved() = runTest {
        // given
        val errorMessage = "Detailed camera error: sensor malfunction"
        val exception = Exception(errorMessage)
        fakeRepository.capturePhotoResult = Result.failure(exception)

        // when
        val result = useCase(ShootingMode.SINGLE)

        // then
        assertTrue("Result must be failure", result.isFailure)
        val capturedException = result.exceptionOrNull()
        assertEquals("Error message must be preserved", errorMessage, capturedException?.message)
    }

    // ====== Edge Cases Tests ======

    @Test
    fun multipleCaptureCallsTrackSeparately() = runTest {
        // given
        val photo1 = CapturedPhoto(
            id = "photo-first",
            filePath = "/sdcard/first.jpg",
            thumbnailPath = null,
            captureTime = 1000L,
            cameraModel = "Test",
            settings = null,
            size = 1000L,
            width = 1920,
            height = 1080
        )
        val photo2 = CapturedPhoto(
            id = "photo-second",
            filePath = "/sdcard/second.jpg",
            thumbnailPath = null,
            captureTime = 2000L,
            cameraModel = "Test",
            settings = null,
            size = 1000L,
            width = 1920,
            height = 1080
        )
        fakeRepository.capturePhotoResult = Result.success(photo1)

        // when - first call
        val result1 = useCase(ShootingMode.SINGLE)

        // then
        assertTrue("First result must be success", result1.isSuccess)
        assertEquals("Call count must be 1 after first call", 1, fakeRepository.capturePhotoCallCount)

        // when - update mock and second call
        fakeRepository.capturePhotoResult = Result.success(photo2)
        val result2 = useCase(ShootingMode.SINGLE)

        // then
        assertTrue("Second result must be success", result2.isSuccess)
        assertEquals("Call count must be 2 after second call", 2, fakeRepository.capturePhotoCallCount)
        assertEquals("First result photo ID must be different from second", photo1.id, result1.getOrNull()?.id)
        assertEquals("Second result photo ID must match", photo2.id, result2.getOrNull()?.id)
    }

    @Test
    fun successAfterFailureIndicatesRecovery() = runTest {
        // given
        val exception = RuntimeException("Temporary capture failure")
        fakeRepository.capturePhotoResult = Result.failure(exception)

        // when - first call fails
        val result1 = useCase(ShootingMode.SINGLE)

        // then
        assertTrue("First result must be failure", result1.isFailure)
        assertEquals("Call count must be 1 after failure", 1, fakeRepository.capturePhotoCallCount)

        // when - update mock to success
        val recoveryPhoto = CapturedPhoto(
            id = "photo-recovery",
            filePath = "/sdcard/recovery.jpg",
            thumbnailPath = null,
            captureTime = 3000L,
            cameraModel = "Test",
            settings = null,
            size = 1000L,
            width = 1920,
            height = 1080
        )
        fakeRepository.capturePhotoResult = Result.success(recoveryPhoto)
        val result2 = useCase(ShootingMode.SINGLE)

        // then
        assertTrue("Second result must be success after recovery", result2.isSuccess)
        assertEquals("Call count must be 2 after recovery", 2, fakeRepository.capturePhotoCallCount)
        assertEquals("Recovery photo ID must match", recoveryPhoto.id, result2.getOrNull()?.id)
    }

    @Test
    fun differentShootingModesAreTracked() = runTest {
        // given
        val testPhoto = CapturedPhoto(
            id = "photo-mode-test",
            filePath = "/sdcard/mode.jpg",
            thumbnailPath = null,
            captureTime = 4000L,
            cameraModel = "Test",
            settings = null,
            size = 1000L,
            width = 1920,
            height = 1080
        )
        fakeRepository.capturePhotoResult = Result.success(testPhoto)

        // when - call with SINGLE
        useCase(ShootingMode.SINGLE)
        val mode1 = fakeRepository.lastCapturePhotoMode

        // when - call with BURST (even though unsupported)
        val exception = UnsupportedShootingModeException(
            mode = ShootingMode.BURST,
            supportedModes = listOf(ShootingMode.SINGLE)
        )
        fakeRepository.capturePhotoResult = Result.failure(exception)
        useCase(ShootingMode.BURST)
        val mode2 = fakeRepository.lastCapturePhotoMode

        // then
        assertEquals("First mode must be SINGLE", ShootingMode.SINGLE, mode1)
        assertEquals("Second mode must be BURST", ShootingMode.BURST, mode2)
        assertEquals("Call count must be 2", 2, fakeRepository.capturePhotoCallCount)
    }
}
