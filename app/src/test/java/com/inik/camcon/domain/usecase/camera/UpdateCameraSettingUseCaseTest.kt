package com.inik.camcon.domain.usecase.camera

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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateCameraSettingUseCaseTest {

    private lateinit var useCase: UpdateCameraSettingUseCase
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
     * Happy Path - ISO 설정
     */

    @Test
    fun `ISO 설정값 변경 성공`() = runTest {
        // Given
        coEvery { cameraRepository.updateCameraSetting("iso", "400") } returns Result.success(true)
        useCase = UpdateCameraSettingUseCase(cameraRepository)

        // When
        val result = useCase("iso", "400")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() == true)
    }

    @Test
    fun `ISO를 최소값 100으로 설정`() = runTest {
        // Given
        coEvery { cameraRepository.updateCameraSetting("iso", "100") } returns Result.success(true)
        useCase = UpdateCameraSettingUseCase(cameraRepository)

        // When
        val result = useCase("iso", "100")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isSuccess)
    }

    @Test
    fun `ISO를 최대값 6400으로 설정`() = runTest {
        // Given
        coEvery { cameraRepository.updateCameraSetting("iso", "6400") } returns Result.success(true)
        useCase = UpdateCameraSettingUseCase(cameraRepository)

        // When
        val result = useCase("iso", "6400")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isSuccess)
    }

    /**
     * Happy Path - Shutter Speed 설정
     */

    @Test
    fun `셔터 스피드 설정값 변경 성공`() = runTest {
        // Given
        coEvery { cameraRepository.updateCameraSetting("shutterSpeed", "1/500") } returns Result.success(true)
        useCase = UpdateCameraSettingUseCase(cameraRepository)

        // When
        val result = useCase("shutterSpeed", "1/500")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() == true)
    }

    @Test
    fun `셔터 스피드 - Bulb 모드`() = runTest {
        // Given
        coEvery { cameraRepository.updateCameraSetting("shutterSpeed", "Bulb") } returns Result.success(true)
        useCase = UpdateCameraSettingUseCase(cameraRepository)

        // When
        val result = useCase("shutterSpeed", "Bulb")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isSuccess)
    }

    /**
     * Happy Path - Aperture 설정
     */

    @Test
    fun `조리개 설정값 변경 성공`() = runTest {
        // Given
        coEvery { cameraRepository.updateCameraSetting("aperture", "f/4.0") } returns Result.success(true)
        useCase = UpdateCameraSettingUseCase(cameraRepository)

        // When
        val result = useCase("aperture", "f/4.0")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isSuccess)
    }

    @Test
    fun `조리개 - 최소값 f/1.0`() = runTest {
        // Given
        coEvery { cameraRepository.updateCameraSetting("aperture", "f/1.0") } returns Result.success(true)
        useCase = UpdateCameraSettingUseCase(cameraRepository)

        // When
        val result = useCase("aperture", "f/1.0")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isSuccess)
    }

    /**
     * Happy Path - White Balance 설정
     */

    @Test
    fun `화이트 밸런스 설정값 변경 성공`() = runTest {
        // Given
        coEvery { cameraRepository.updateCameraSetting("whiteBalance", "Daylight") } returns Result.success(true)
        useCase = UpdateCameraSettingUseCase(cameraRepository)

        // When
        val result = useCase("whiteBalance", "Daylight")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isSuccess)
    }

    @Test
    fun `화이트 밸런스 - Auto 모드`() = runTest {
        // Given
        coEvery { cameraRepository.updateCameraSetting("whiteBalance", "Auto") } returns Result.success(true)
        useCase = UpdateCameraSettingUseCase(cameraRepository)

        // When
        val result = useCase("whiteBalance", "Auto")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isSuccess)
    }

    /**
     * Happy Path - Focus Mode 설정
     */

    @Test
    fun `초점 모드 설정값 변경 성공 - AF-S`() = runTest {
        // Given
        coEvery { cameraRepository.updateCameraSetting("focusMode", "AF-S") } returns Result.success(true)
        useCase = UpdateCameraSettingUseCase(cameraRepository)

        // When
        val result = useCase("focusMode", "AF-S")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isSuccess)
    }

    @Test
    fun `초점 모드 설정값 변경 성공 - AF-C`() = runTest {
        // Given
        coEvery { cameraRepository.updateCameraSetting("focusMode", "AF-C") } returns Result.success(true)
        useCase = UpdateCameraSettingUseCase(cameraRepository)

        // When
        val result = useCase("focusMode", "AF-C")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isSuccess)
    }

    @Test
    fun `초점 모드 설정값 변경 성공 - MF(Manual)`() = runTest {
        // Given
        coEvery { cameraRepository.updateCameraSetting("focusMode", "MF") } returns Result.success(true)
        useCase = UpdateCameraSettingUseCase(cameraRepository)

        // When
        val result = useCase("focusMode", "MF")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isSuccess)
    }

    /**
     * Happy Path - Exposure Compensation 설정
     */

    @Test
    fun `노출 보정값 설정 성공`() = runTest {
        // Given
        coEvery { cameraRepository.updateCameraSetting("exposureCompensation", "+1.0") } returns Result.success(true)
        useCase = UpdateCameraSettingUseCase(cameraRepository)

        // When
        val result = useCase("exposureCompensation", "+1.0")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isSuccess)
    }

    @Test
    fun `노출 보정값 - 음수값`() = runTest {
        // Given
        coEvery { cameraRepository.updateCameraSetting("exposureCompensation", "-2.0") } returns Result.success(true)
        useCase = UpdateCameraSettingUseCase(cameraRepository)

        // When
        val result = useCase("exposureCompensation", "-2.0")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isSuccess)
    }

    @Test
    fun `노출 보정값 - 0.0`() = runTest {
        // Given
        coEvery { cameraRepository.updateCameraSetting("exposureCompensation", "0.0") } returns Result.success(true)
        useCase = UpdateCameraSettingUseCase(cameraRepository)

        // When
        val result = useCase("exposureCompensation", "0.0")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isSuccess)
    }

    /**
     * Error Cases - 카메라 연결 관련 실패
     */

    @Test
    fun `카메라 설정 변경 실패 - 카메라 연결 안됨`() = runTest {
        // Given
        val exception = IllegalStateException("Camera not connected")
        coEvery { cameraRepository.updateCameraSetting(any(), any()) } returns Result.failure(exception)
        useCase = UpdateCameraSettingUseCase(cameraRepository)

        // When
        val result = useCase("iso", "400")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isFailure)
        assertEquals("Camera not connected", result.exceptionOrNull()?.message)
    }

    @Test
    fun `카메라 설정 변경 실패 - 타임아웃`() = runTest {
        // Given
        val exception = RuntimeException("Camera communication timeout")
        coEvery { cameraRepository.updateCameraSetting(any(), any()) } returns Result.failure(exception)
        useCase = UpdateCameraSettingUseCase(cameraRepository)

        // When
        val result = useCase("iso", "800")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("timeout") == true)
    }

    @Test
    fun `카메라 설정 변경 실패 - 지원하지 않는 값`() = runTest {
        // Given
        val exception = IllegalArgumentException("Unsupported camera setting value: invalidValue")
        coEvery { cameraRepository.updateCameraSetting("iso", "invalidValue") } returns Result.failure(exception)
        useCase = UpdateCameraSettingUseCase(cameraRepository)

        // When
        val result = useCase("iso", "invalidValue")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Unsupported") == true)
    }

    @Test
    fun `카메라 설정 변경 실패 - 일반 예외`() = runTest {
        // Given
        val exception = Exception("Unknown camera error")
        coEvery { cameraRepository.updateCameraSetting(any(), any()) } returns Result.failure(exception)
        useCase = UpdateCameraSettingUseCase(cameraRepository)

        // When
        val result = useCase("aperture", "f/2.8")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isFailure)
        assertFalse(result.isSuccess)
    }

    /**
     * Edge Cases - Empty values
     */

    @Test
    fun `빈 key 값으로 설정 시도`() = runTest {
        // Given
        val exception = IllegalArgumentException("Key cannot be empty")
        coEvery { cameraRepository.updateCameraSetting("", "400") } returns Result.failure(exception)
        useCase = UpdateCameraSettingUseCase(cameraRepository)

        // When
        val result = useCase("", "400")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isFailure)
    }

    @Test
    fun `빈 value 값으로 설정 시도`() = runTest {
        // Given
        val exception = IllegalArgumentException("Value cannot be empty")
        coEvery { cameraRepository.updateCameraSetting("iso", "") } returns Result.failure(exception)
        useCase = UpdateCameraSettingUseCase(cameraRepository)

        // When
        val result = useCase("iso", "")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isFailure)
    }

    /**
     * Behavior Tests - 여러 설정 변경
     */

    @Test
    fun `여러 설정값을 순차적으로 변경 - ISO 후 Aperture`() = runTest {
        // Given
        coEvery { cameraRepository.updateCameraSetting("iso", "400") } returns Result.success(true)
        coEvery { cameraRepository.updateCameraSetting("aperture", "f/4.0") } returns Result.success(true)
        useCase = UpdateCameraSettingUseCase(cameraRepository)

        // When - Change ISO
        val result1 = useCase("iso", "400")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result1.isSuccess)

        // When - Change Aperture
        val result2 = useCase("aperture", "f/4.0")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result2.isSuccess)
    }

    @Test
    fun `같은 설정을 여러 번 변경`() = runTest {
        // Given
        coEvery { cameraRepository.updateCameraSetting("iso", "100") } returns Result.success(true)
        coEvery { cameraRepository.updateCameraSetting("iso", "200") } returns Result.success(true)
        coEvery { cameraRepository.updateCameraSetting("iso", "400") } returns Result.success(true)
        useCase = UpdateCameraSettingUseCase(cameraRepository)

        // When & Then
        assertTrue(useCase("iso", "100").isSuccess)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(useCase("iso", "200").isSuccess)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(useCase("iso", "400").isSuccess)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `설정 변경 실패 후 다시 성공`() = runTest {
        // Given
        val exception = RuntimeException("Temporary camera error")
        coEvery { cameraRepository.updateCameraSetting("iso", "400") } returns Result.failure(exception)
        useCase = UpdateCameraSettingUseCase(cameraRepository)

        // When - First call fails
        val result1 = useCase("iso", "400")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result1.isFailure)

        // When - Update mock and retry
        coEvery { cameraRepository.updateCameraSetting("iso", "400") } returns Result.success(true)
        val result2 = useCase("iso", "400")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result2.isSuccess)
    }

    /**
     * Return Value Verification
     */

    @Test
    fun `설정 변경 성공시 true 반환 확인`() = runTest {
        // Given
        coEvery { cameraRepository.updateCameraSetting(any(), any()) } returns Result.success(true)
        useCase = UpdateCameraSettingUseCase(cameraRepository)

        // When
        val result = useCase("whiteBalance", "Tungsten")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isSuccess)
        val value = result.getOrNull()
        assertEquals(true, value)
    }
}
