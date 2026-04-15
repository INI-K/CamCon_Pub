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
class DisconnectCameraUseCaseTest {

    private lateinit var useCase: DisconnectCameraUseCase
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
     * Happy Path - 정상 연결 해제
     */

    @Test
    fun `카메라 연결 해제 성공`() = runTest {
        // Given
        coEvery { cameraRepository.disconnectCamera() } returns Result.success(true)
        useCase = DisconnectCameraUseCase(cameraRepository)

        // When
        val result = useCase()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() == true)
    }

    @Test
    fun `카메라 연결 해제 - 반환값 true 확인`() = runTest {
        // Given
        coEvery { cameraRepository.disconnectCamera() } returns Result.success(true)
        useCase = DisconnectCameraUseCase(cameraRepository)

        // When
        val result = useCase()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isSuccess)
        val value = result.getOrNull()
        assertEquals(true, value)
    }

    /**
     * Error Cases - 연결 해제 실패
     */

    @Test
    fun `카메라 연결 해제 실패 - 카메라 이미 연결 해제됨`() = runTest {
        // Given
        val exception = IllegalStateException("Camera already disconnected")
        coEvery { cameraRepository.disconnectCamera() } returns Result.failure(exception)
        useCase = DisconnectCameraUseCase(cameraRepository)

        // When
        val result = useCase()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isFailure)
        assertEquals("Camera already disconnected", result.exceptionOrNull()?.message)
    }

    @Test
    fun `카메라 연결 해제 실패 - USB 연결 해제 오류`() = runTest {
        // Given
        val exception = RuntimeException("Failed to disconnect USB device")
        coEvery { cameraRepository.disconnectCamera() } returns Result.failure(exception)
        useCase = DisconnectCameraUseCase(cameraRepository)

        // When
        val result = useCase()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("USB") == true)
    }

    @Test
    fun `카메라 연결 해제 실패 - 타임아웃`() = runTest {
        // Given
        val exception = RuntimeException("Camera disconnection timeout")
        coEvery { cameraRepository.disconnectCamera() } returns Result.failure(exception)
        useCase = DisconnectCameraUseCase(cameraRepository)

        // When
        val result = useCase()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("timeout") == true)
    }

    @Test
    fun `카메라 연결 해제 실패 - 일반 예외`() = runTest {
        // Given
        val exception = Exception("Unknown camera error during disconnection")
        coEvery { cameraRepository.disconnectCamera() } returns Result.failure(exception)
        useCase = DisconnectCameraUseCase(cameraRepository)

        // When
        val result = useCase()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isFailure)
        assertFalse(result.isSuccess)
    }

    /**
     * Multiple Call Tests - 여러 번 호출
     */

    @Test
    fun `연속 호출 - 첫 번째는 성공, 두 번째는 이미 연결 해제됨 오류`() = runTest {
        // Given
        coEvery { cameraRepository.disconnectCamera() } returns Result.success(true)
        useCase = DisconnectCameraUseCase(cameraRepository)

        // When - First call succeeds
        val result1 = useCase()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result1.isSuccess)

        // When - Second call fails (already disconnected)
        coEvery { cameraRepository.disconnectCamera() } returns Result.failure(
            IllegalStateException("Camera already disconnected")
        )
        val result2 = useCase()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result2.isFailure)
    }

    @Test
    fun `연속 실패 후 성공`() = runTest {
        // Given
        val exception = RuntimeException("Temporary connection error")
        coEvery { cameraRepository.disconnectCamera() } returns Result.failure(exception)
        useCase = DisconnectCameraUseCase(cameraRepository)

        // When - First call fails
        val result1 = useCase()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result1.isFailure)

        // When - Update mock to success
        coEvery { cameraRepository.disconnectCamera() } returns Result.success(true)
        val result2 = useCase()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result2.isSuccess)
    }

    /**
     * Idempotency Tests - 멱등성 검사
     */

    @Test
    fun `같은 호출을 세 번 수행 - 모두 성공`() = runTest {
        // Given
        coEvery { cameraRepository.disconnectCamera() } returns Result.success(true)
        useCase = DisconnectCameraUseCase(cameraRepository)

        // When & Then
        repeat(3) {
            val result = useCase()
            testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(result.isSuccess)
        }
    }

    /**
     * State Verification Tests
     */

    @Test
    fun `연결 해제 실패시 isSuccess false`() = runTest {
        // Given
        coEvery { cameraRepository.disconnectCamera() } returns Result.failure(
            Exception("Disconnection failed")
        )
        useCase = DisconnectCameraUseCase(cameraRepository)

        // When
        val result = useCase()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertFalse(result.isSuccess)
        assertTrue(result.isFailure)
    }

    @Test
    fun `연결 해제 성공시 isSuccess true`() = runTest {
        // Given
        coEvery { cameraRepository.disconnectCamera() } returns Result.success(true)
        useCase = DisconnectCameraUseCase(cameraRepository)

        // When
        val result = useCase()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isSuccess)
        assertFalse(result.isFailure)
    }

    /**
     * Exception Message Verification
     */

    @Test
    fun `실패 시 예외 메시지가 포함됨`() = runTest {
        // Given
        val errorMessage = "Cannot disconnect from camera: Device not found"
        val exception = RuntimeException(errorMessage)
        coEvery { cameraRepository.disconnectCamera() } returns Result.failure(exception)
        useCase = DisconnectCameraUseCase(cameraRepository)

        // When
        val result = useCase()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isFailure)
        val exceptionMessage = result.exceptionOrNull()?.message
        assertEquals(errorMessage, exceptionMessage)
    }

    /**
     * Repository Interaction Tests
     */

    @Test
    fun `invoke 호출시 repository의 disconnectCamera 호출됨`() = runTest {
        // Given
        coEvery { cameraRepository.disconnectCamera() } returns Result.success(true)
        useCase = DisconnectCameraUseCase(cameraRepository)

        // When
        val result = useCase()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isSuccess)
    }
}
