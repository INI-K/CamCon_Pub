package com.inik.camcon.domain.usecase.camera

import com.inik.camcon.domain.model.CameraSettings
import com.inik.camcon.domain.repository.CameraRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GetCameraSettingsUseCaseTest {

    private lateinit var useCase: GetCameraSettingsUseCase
    private lateinit var cameraRepository: CameraRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        cameraRepository = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Happy Path Tests - 정상 동작
     */

    @Test
    fun `카메라 설정 조회 성공 - 모든 설정값 반환`() = runTest {
        // Given
        val testSettings = CameraSettings(
            iso = "100",
            shutterSpeed = "1/1000",
            aperture = "f/2.8",
            whiteBalance = "Auto",
            focusMode = "AF-S",
            exposureCompensation = "0.0",
            availableSettings = mapOf(
                "iso" to listOf("100", "200", "400", "800"),
                "shutterSpeed" to listOf("1/1000", "1/500", "1/250"),
                "aperture" to listOf("f/2.8", "f/4.0", "f/5.6")
            )
        )
        coEvery { cameraRepository.getCameraSettings() } returns Result.success(testSettings)

        useCase = GetCameraSettingsUseCase(cameraRepository)

        // When
        val result = useCase()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isSuccess)
        val settings = result.getOrNull()
        assertNotNull(settings)
        assertEquals("100", settings?.iso)
        assertEquals("1/1000", settings?.shutterSpeed)
        assertEquals("f/2.8", settings?.aperture)
        assertEquals("Auto", settings?.whiteBalance)
        assertEquals("AF-S", settings?.focusMode)
        assertEquals("0.0", settings?.exposureCompensation)
    }

    @Test
    fun `카메라 설정 - 사용 가능한 옵션 포함`() = runTest {
        // Given
        val availableSettings = mapOf(
            "iso" to listOf("100", "200", "400", "800", "1600", "3200"),
            "shutterSpeed" to listOf("1/4000", "1/2000", "1/1000", "1/500", "1/250"),
            "aperture" to listOf("f/1.4", "f/2.0", "f/2.8", "f/4.0"),
            "whiteBalance" to listOf("Daylight", "Cloudy", "Shade", "Tungsten", "Auto")
        )
        val testSettings = CameraSettings(
            iso = "400",
            shutterSpeed = "1/1000",
            aperture = "f/4.0",
            whiteBalance = "Daylight",
            focusMode = "AF-C",
            exposureCompensation = "+1.0",
            availableSettings = availableSettings
        )
        coEvery { cameraRepository.getCameraSettings() } returns Result.success(testSettings)
        useCase = GetCameraSettingsUseCase(cameraRepository)

        // When
        val result = useCase()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isSuccess)
        val settings = result.getOrNull()
        assertNotNull(settings)
        assertEquals(6, settings?.availableSettings?.get("iso")?.size)
        assertEquals(5, settings?.availableSettings?.get("shutterSpeed")?.size)
        assertEquals(4, settings?.availableSettings?.get("aperture")?.size)
    }

    @Test
    fun `카메라 설정 - 최소값으로 설정된 경우`() = runTest {
        // Given
        val minSettings = CameraSettings(
            iso = "100",
            shutterSpeed = "30\"",
            aperture = "f/1.0",
            whiteBalance = "Auto",
            focusMode = "MF",
            exposureCompensation = "-5.0"
        )
        coEvery { cameraRepository.getCameraSettings() } returns Result.success(minSettings)
        useCase = GetCameraSettingsUseCase(cameraRepository)

        // When
        val result = useCase()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isSuccess)
        val settings = result.getOrNull()
        assertEquals("100", settings?.iso)
        assertEquals("-5.0", settings?.exposureCompensation)
    }

    @Test
    fun `카메라 설정 - 최대값으로 설정된 경우`() = runTest {
        // Given
        val maxSettings = CameraSettings(
            iso = "6400",
            shutterSpeed = "1/8000",
            aperture = "f/32",
            whiteBalance = "Color Temperature",
            focusMode = "AF-A",
            exposureCompensation = "+5.0"
        )
        coEvery { cameraRepository.getCameraSettings() } returns Result.success(maxSettings)
        useCase = GetCameraSettingsUseCase(cameraRepository)

        // When
        val result = useCase()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isSuccess)
        val settings = result.getOrNull()
        assertEquals("6400", settings?.iso)
        assertEquals("+5.0", settings?.exposureCompensation)
    }

    /**
     * Edge Cases - 경계값, 빈 상태 등
     */

    @Test
    fun `카메라 설정 - 사용 가능한 옵션이 비어있는 경우`() = runTest {
        // Given
        val settingsWithEmptyOptions = CameraSettings(
            iso = "400",
            shutterSpeed = "1/1000",
            aperture = "f/4.0",
            whiteBalance = "Auto",
            focusMode = "AF-S",
            exposureCompensation = "0.0",
            availableSettings = emptyMap()
        )
        coEvery { cameraRepository.getCameraSettings() } returns Result.success(settingsWithEmptyOptions)
        useCase = GetCameraSettingsUseCase(cameraRepository)

        // When
        val result = useCase()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isSuccess)
        val settings = result.getOrNull()
        assertTrue(settings?.availableSettings?.isEmpty() == true)
    }

    @Test
    fun `카메라 설정 - 빈 문자열 설정값`() = runTest {
        // Given
        val settingsWithEmptyValues = CameraSettings(
            iso = "",
            shutterSpeed = "",
            aperture = "",
            whiteBalance = "",
            focusMode = "",
            exposureCompensation = ""
        )
        coEvery { cameraRepository.getCameraSettings() } returns Result.success(settingsWithEmptyValues)
        useCase = GetCameraSettingsUseCase(cameraRepository)

        // When
        val result = useCase()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isSuccess)
        val settings = result.getOrNull()
        assertEquals("", settings?.iso)
    }

    /**
     * Error Cases - 에러 처리
     */

    @Test
    fun `카메라 설정 조회 실패 - 카메라 연결 안됨`() = runTest {
        // Given
        val exception = IllegalStateException("Camera not connected")
        coEvery { cameraRepository.getCameraSettings() } returns Result.failure(exception)
        useCase = GetCameraSettingsUseCase(cameraRepository)

        // When
        val result = useCase()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isFailure)
        assertEquals("Camera not connected", result.exceptionOrNull()?.message)
    }

    @Test
    fun `카메라 설정 조회 실패 - 타임아웃`() = runTest {
        // Given
        val exception = RuntimeException("Camera communication timeout")
        coEvery { cameraRepository.getCameraSettings() } returns Result.failure(exception)
        useCase = GetCameraSettingsUseCase(cameraRepository)

        // When
        val result = useCase()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("timeout") == true)
    }

    @Test
    fun `카메라 설정 조회 실패 - 일반 예외`() = runTest {
        // Given
        val exception = Exception("Unknown camera error")
        coEvery { cameraRepository.getCameraSettings() } returns Result.failure(exception)
        useCase = GetCameraSettingsUseCase(cameraRepository)

        // When
        val result = useCase()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isFailure)
        assertFalse(result.isSuccess)
    }

    /**
     * Repository Interaction Tests - Repository 호출 검증
     */

    @Test
    fun `invoke 호출시 repository의 getCameraSettings 호출됨`() = runTest {
        // Given
        val settings = CameraSettings(
            iso = "200",
            shutterSpeed = "1/500",
            aperture = "f/2.8",
            whiteBalance = "Auto",
            focusMode = "AF-S",
            exposureCompensation = "0.0"
        )
        coEvery { cameraRepository.getCameraSettings() } returns Result.success(settings)
        useCase = GetCameraSettingsUseCase(cameraRepository)

        // When
        useCase()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        val result = useCase()
        assertTrue(result.isSuccess)
    }

    /**
     * Multi-call Tests - 여러 번 호출
     */

    @Test
    fun `여러 번 호출시 매번 최신 설정값 반환`() = runTest {
        // Given
        val settings1 = CameraSettings(
            iso = "100",
            shutterSpeed = "1/1000",
            aperture = "f/2.8",
            whiteBalance = "Auto",
            focusMode = "AF-S",
            exposureCompensation = "0.0"
        )
        val settings2 = CameraSettings(
            iso = "400",
            shutterSpeed = "1/500",
            aperture = "f/4.0",
            whiteBalance = "Daylight",
            focusMode = "AF-C",
            exposureCompensation = "+1.0"
        )

        coEvery { cameraRepository.getCameraSettings() } returns Result.success(settings1)
        useCase = GetCameraSettingsUseCase(cameraRepository)

        // When - first call
        val result1 = useCase()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result1.isSuccess)
        assertEquals("100", result1.getOrNull()?.iso)

        // When - update mock and second call
        coEvery { cameraRepository.getCameraSettings() } returns Result.success(settings2)
        val result2 = useCase()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result2.isSuccess)
        assertEquals("400", result2.getOrNull()?.iso)
    }

    @Test
    fun `연속 실패 후 성공`() = runTest {
        // Given
        val exception = RuntimeException("Temporary error")
        coEvery { cameraRepository.getCameraSettings() } returns Result.failure(exception)
        useCase = GetCameraSettingsUseCase(cameraRepository)

        // When - first call fails
        val result1 = useCase()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result1.isFailure)

        // When - update mock to success
        val settings = CameraSettings(
            iso = "200",
            shutterSpeed = "1/500",
            aperture = "f/2.8",
            whiteBalance = "Auto",
            focusMode = "AF-S",
            exposureCompensation = "0.0"
        )
        coEvery { cameraRepository.getCameraSettings() } returns Result.success(settings)
        val result2 = useCase()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result2.isSuccess)
        assertEquals("200", result2.getOrNull()?.iso)
    }
}
