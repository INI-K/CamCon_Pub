package com.inik.camcon.presentation.viewmodel

import app.cash.turbine.test
import com.inik.camcon.CameraNative
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MockCameraViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var cameraNative: CameraNative

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        cameraNative = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `initial state should be default values`() = runTest {
        // Given - mock 설정
        every { cameraNative.isLibrariesLoaded() } returns false

        // When
        val viewModel = MockCameraViewModel(cameraNative)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isEnabled)
            assertEquals(0, state.imageCount)
            assertEquals(500, state.delayMs)
            assertFalse(state.autoCapture)
            assertEquals(3000, state.autoCaptureInterval)
            assertTrue(state.images.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `enableMockCamera should update state on success`() = runTest {
        // Given
        every { cameraNative.isLibrariesLoaded() } returns true
        every { cameraNative.getMockCameraInfo() } returns createMockCameraInfoJson()
        every { cameraNative.enableMockCamera(true) } returns true

        val viewModel = MockCameraViewModel(cameraNative)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.enableMockCamera(true)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.isEnabled)
            assertEquals("Mock Camera 활성화됨", state.successMessage)
            assertFalse(state.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `enableMockCamera should set error on failure`() = runTest {
        // Given
        every { cameraNative.isLibrariesLoaded() } returns true
        every { cameraNative.getMockCameraInfo() } returns createMockCameraInfoJson()
        every { cameraNative.enableMockCamera(true) } returns false

        val viewModel = MockCameraViewModel(cameraNative)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.enableMockCamera(true)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Mock Camera 설정 실패", state.error)
            assertFalse(state.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setDelay should update delay on success`() = runTest {
        // Given
        every { cameraNative.isLibrariesLoaded() } returns true
        every { cameraNative.getMockCameraInfo() } returns createMockCameraInfoJson()
        every { cameraNative.setMockCameraDelay(1000) } returns true

        val viewModel = MockCameraViewModel(cameraNative)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.setDelay(1000)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(1000, state.delayMs)
            assertEquals("딜레이 1000ms로 설정", state.successMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearError should remove error message`() = runTest {
        // Given
        every { cameraNative.isLibrariesLoaded() } returns false

        val viewModel = MockCameraViewModel(cameraNative)
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify error is set (from init failing to load libraries)
        viewModel.uiState.test {
            val errorState = awaitItem()
            assertEquals("네이티브 라이브러리 로딩 실패", errorState.error)
            cancelAndIgnoreRemainingEvents()
        }

        // When
        viewModel.clearError()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertNull(state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setAutoCapture should update autoCapture state on success`() = runTest {
        // Given
        every { cameraNative.isLibrariesLoaded() } returns true
        every { cameraNative.getMockCameraInfo() } returns createMockCameraInfoJson()
        every { cameraNative.setMockCameraAutoCapture(true, 5000) } returns true

        val viewModel = MockCameraViewModel(cameraNative)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.setAutoCapture(true, 5000)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.autoCapture)
            assertEquals(5000, state.autoCaptureInterval)
            assertEquals("자동 캡처 활성화 (5000ms 간격)", state.successMessage)
            assertFalse(state.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setMockCameraModel should update camera model on success`() = runTest {
        // Given
        every { cameraNative.isLibrariesLoaded() } returns true
        every { cameraNative.getMockCameraInfo() } returns createMockCameraInfoJson()
        every { cameraNative.setMockCameraModel("Canon", "Canon EOS R5") } returns true

        val viewModel = MockCameraViewModel(cameraNative)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.setMockCameraModel("Canon", "Canon EOS R5")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Canon", state.manufacturer)
            assertEquals("Canon EOS R5", state.cameraModel)
            assertEquals("카메라 모델 설정: Canon Canon EOS R5", state.successMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createMockCameraInfoJson(): String {
        val json = JSONObject().apply {
            put("enabled", true)
            put("imageCount", 5)
            put("delayMs", 500)
            put("autoCapture", false)
            put("autoCaptureInterval", 3000)
            put("cameraModel", "Test Camera")
            put("manufacturer", "Test Manufacturer")
            put("images", JSONArray().apply {
                put("/test/image1.jpg")
                put("/test/image2.jpg")
            })
        }
        return json.toString()
    }
}
