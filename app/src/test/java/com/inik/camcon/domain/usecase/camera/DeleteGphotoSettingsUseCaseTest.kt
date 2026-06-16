package com.inik.camcon.domain.usecase.camera

import com.inik.camcon.domain.repository.CameraRepository
import io.mockk.coEvery
import io.mockk.coVerify
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
class DeleteGphotoSettingsUseCaseTest {

    private lateinit var useCase: DeleteGphotoSettingsUseCase
    private lateinit var cameraRepository: CameraRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        cameraRepository = mockk()
        useCase = DeleteGphotoSettingsUseCase(cameraRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Happy Path - 정상 삭제
     */

    @Test
    fun `설정 삭제 성공 시 Result success로 래핑된 문자열 반환`() = runTest {
        // Given
        coEvery { cameraRepository.deleteGphotoSettings() } returns "deleted"
        useCase = DeleteGphotoSettingsUseCase(cameraRepository)

        // When
        val result = useCase()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isSuccess)
        assertEquals("deleted", result.getOrNull())
    }

    /**
     * Error Cases - repository 예외를 runCatching이 Result.failure로 포착
     */

    @Test
    fun `repository 예외 발생 시 throw하지 않고 Result failure로 포착`() = runTest {
        // Given
        val exception = RuntimeException("file access denied")
        coEvery { cameraRepository.deleteGphotoSettings() } throws exception
        useCase = DeleteGphotoSettingsUseCase(cameraRepository)

        // When
        val result = useCase()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isFailure)
        assertFalse(result.isSuccess)
        assertEquals("file access denied", result.exceptionOrNull()?.message)
    }

    @Test
    fun `JNI 레이어 예외도 Result failure로 전파`() = runTest {
        // Given
        val exception = IllegalStateException("native delete failed")
        coEvery { cameraRepository.deleteGphotoSettings() } throws exception
        useCase = DeleteGphotoSettingsUseCase(cameraRepository)

        // When
        val result = useCase()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    /**
     * Repository Interaction Tests
     */

    @Test
    fun `invoke 호출 시 repository의 deleteGphotoSettings 호출됨`() = runTest {
        // Given
        coEvery { cameraRepository.deleteGphotoSettings() } returns "ok"
        useCase = DeleteGphotoSettingsUseCase(cameraRepository)

        // When
        useCase()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { cameraRepository.deleteGphotoSettings() }
    }
}
