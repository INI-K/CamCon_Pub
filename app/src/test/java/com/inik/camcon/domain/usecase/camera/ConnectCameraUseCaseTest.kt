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
class ConnectCameraUseCaseTest {

    private lateinit var useCase: ConnectCameraUseCase
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
     * Happy Path Tests - USB/Wi-Fi 연결 성공
     */

    @Test
    fun `USB 카메라 연결 성공 - 유효한 ID 제공`() = runTest {
        // Given
        val cameraId = "usb://camera_001"
        coEvery { cameraRepository.connectCamera(cameraId) } returns Result.success(true)
        useCase = ConnectCameraUseCase(cameraRepository)

        // When
        val result = useCase(cameraId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() == true)
    }

    @Test
    fun `Wi-Fi PTPIP 카메라 연결 성공 - IP 주소 기반 ID`() = runTest {
        // Given
        val cameraId = "ptpip://192.168.1.100:15740"
        coEvery { cameraRepository.connectCamera(cameraId) } returns Result.success(true)
        useCase = ConnectCameraUseCase(cameraRepository)

        // When
        val result = useCase(cameraId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() == true)
    }

    @Test
    fun `다양한 카메라 모델 연결 성공`() = runTest {
        // Given
        val cameraIds = listOf(
            "usb://canon_eos_r5",
            "usb://nikon_z9",
            "ptpip://192.168.1.50:15740",
            "usb://sony_a7riv"
        )
        cameraIds.forEach { id ->
            coEvery { cameraRepository.connectCamera(id) } returns Result.success(true)
        }
        useCase = ConnectCameraUseCase(cameraRepository)

        // When & Then
        cameraIds.forEach { id ->
            val result = useCase(id)
            testDispatcher.scheduler.advanceUntilIdle()
            assertTrue("ID: $id 연결 실패", result.isSuccess)
        }
    }

    /**
     * Edge Cases - 경계값, 특수 입력
     */

    @Test
    fun `빈 문자열 ID로 연결 시도 - 에러 발생`() = runTest {
        // Given
        val cameraId = ""
        val exception = IllegalArgumentException("Camera ID cannot be empty")
        coEvery { cameraRepository.connectCamera(cameraId) } returns Result.failure(exception)
        useCase = ConnectCameraUseCase(cameraRepository)

        // When
        val result = useCase(cameraId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isFailure)
        assertEquals("Camera ID cannot be empty", result.exceptionOrNull()?.message)
    }

    @Test
    fun `매우 긴 ID로 연결 시도 - 성공`() = runTest {
        // Given
        val longId = "usb://camera_" + "a".repeat(200)
        coEvery { cameraRepository.connectCamera(longId) } returns Result.success(true)
        useCase = ConnectCameraUseCase(cameraRepository)

        // When
        val result = useCase(longId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isSuccess)
    }

    @Test
    fun `특수 문자 포함 ID - 성공`() = runTest {
        // Given
        val specialId = "usb://camera-001_v2.5"
        coEvery { cameraRepository.connectCamera(specialId) } returns Result.success(true)
        useCase = ConnectCameraUseCase(cameraRepository)

        // When
        val result = useCase(specialId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isSuccess)
    }

    /**
     * Error Cases - 연결 실패 시나리오
     */

    @Test
    fun `카메라 연결 실패 - 장치 찾을 수 없음`() = runTest {
        // Given
        val cameraId = "usb://unknown_device"
        val exception = RuntimeException("Camera device not found")
        coEvery { cameraRepository.connectCamera(cameraId) } returns Result.failure(exception)
        useCase = ConnectCameraUseCase(cameraRepository)

        // When
        val result = useCase(cameraId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("not found") == true)
    }

    @Test
    fun `카메라 연결 실패 - 이미 연결된 상태`() = runTest {
        // Given
        val cameraId = "usb://camera_001"
        val exception = IllegalStateException("Camera already connected")
        coEvery { cameraRepository.connectCamera(cameraId) } returns Result.failure(exception)
        useCase = ConnectCameraUseCase(cameraRepository)

        // When
        val result = useCase(cameraId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("already connected") == true)
    }

    @Test
    fun `카메라 연결 실패 - 권한 없음`() = runTest {
        // Given
        val cameraId = "usb://camera_001"
        val exception = SecurityException("Permission denied for camera access")
        coEvery { cameraRepository.connectCamera(cameraId) } returns Result.failure(exception)
        useCase = ConnectCameraUseCase(cameraRepository)

        // When
        val result = useCase(cameraId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }

    @Test
    fun `카메라 연결 실패 - 타임아웃`() = runTest {
        // Given
        val cameraId = "ptpip://192.168.1.100:15740"
        val exception = RuntimeException("Connection timeout: camera did not respond within 30 seconds")
        coEvery { cameraRepository.connectCamera(cameraId) } returns Result.failure(exception)
        useCase = ConnectCameraUseCase(cameraRepository)

        // When
        val result = useCase(cameraId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("timeout") == true)
    }

    @Test
    fun `카메라 연결 실패 - USB 드라이버 미설치`() = runTest {
        // Given
        val cameraId = "usb://camera_001"
        val exception = RuntimeException("Camera driver not loaded")
        coEvery { cameraRepository.connectCamera(cameraId) } returns Result.failure(exception)
        useCase = ConnectCameraUseCase(cameraRepository)

        // When
        val result = useCase(cameraId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isFailure)
        assertFalse(result.isSuccess)
    }

    @Test
    fun `카메라 연결 실패 - 네트워크 연결 불가 (Wi-Fi)`() = runTest {
        // Given
        val cameraId = "ptpip://192.168.1.100:15740"
        val exception = RuntimeException("Network unreachable")
        coEvery { cameraRepository.connectCamera(cameraId) } returns Result.failure(exception)
        useCase = ConnectCameraUseCase(cameraRepository)

        // When
        val result = useCase(cameraId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Network") == true)
    }

    /**
     * Multiple Call Tests - 연속 호출, 재연결, 중복 연결
     */

    @Test
    fun `순차적 여러 카메라 연결 - 각각 성공`() = runTest {
        // Given
        val camera1Id = "usb://camera_001"
        val camera2Id = "usb://camera_002"
        coEvery { cameraRepository.connectCamera(camera1Id) } returns Result.success(true)
        coEvery { cameraRepository.connectCamera(camera2Id) } returns Result.success(true)
        useCase = ConnectCameraUseCase(cameraRepository)

        // When
        val result1 = useCase(camera1Id)
        testDispatcher.scheduler.advanceUntilIdle()

        val result2 = useCase(camera2Id)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result1.isSuccess)
        assertTrue(result2.isSuccess)
    }

    @Test
    fun `같은 카메라 재연결 - 첫 번째 실패 후 성공`() = runTest {
        // Given
        val cameraId = "usb://camera_001"
        val failureException = RuntimeException("Initial connection failed")
        coEvery { cameraRepository.connectCamera(cameraId) } returns Result.failure(failureException)
        useCase = ConnectCameraUseCase(cameraRepository)

        // When - first call fails
        val result1 = useCase(cameraId)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(result1.isFailure)

        // When - update mock to success and try again
        coEvery { cameraRepository.connectCamera(cameraId) } returns Result.success(true)
        val result2 = useCase(cameraId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result2.isSuccess)
        assertTrue(result2.getOrNull() == true)
    }

    @Test
    fun `중복 연결 시도 - 모두 성공 반환`() = runTest {
        // Given
        val cameraId = "usb://camera_001"
        coEvery { cameraRepository.connectCamera(cameraId) } returns Result.success(true)
        useCase = ConnectCameraUseCase(cameraRepository)

        // When - call 3 times
        val result1 = useCase(cameraId)
        testDispatcher.scheduler.advanceUntilIdle()

        val result2 = useCase(cameraId)
        testDispatcher.scheduler.advanceUntilIdle()

        val result3 = useCase(cameraId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result1.isSuccess)
        assertTrue(result2.isSuccess)
        assertTrue(result3.isSuccess)
    }

    /**
     * Repository Interaction Tests - Repository 호출 검증
     */

    @Test
    fun `invoke 호출 시 repository의 connectCamera 호출됨`() = runTest {
        // Given
        val cameraId = "usb://camera_001"
        coEvery { cameraRepository.connectCamera(cameraId) } returns Result.success(true)
        useCase = ConnectCameraUseCase(cameraRepository)

        // When
        useCase(cameraId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - repository method was called (verified by mockk behavior)
        val result = useCase(cameraId)
        assertTrue(result.isSuccess)
    }
}
