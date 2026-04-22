package com.inik.camcon.presentation.viewmodel

import app.cash.turbine.test
import com.inik.camcon.CameraNative
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.mockk
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * MockCameraViewModel instrumented test
 * androidTest로 이관됨 (JVM 단위 테스트 환경에서 native library 로드 불가)
 *
 * 원인: CameraNative.kt 싱글톤이 초기화될 때 arm64-v8a libnative-lib.so 로드 시도
 * → JVM에서는 로드 실패 → ExceptionInInitializerError
 *
 * androidTest 환경에서는 Android 컨텍스트가 있어 native library 로드 지원
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
class MockCameraViewModelTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var cameraNative: CameraNative

    @Before
    fun setup() {
        hiltRule.inject()
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
            assertFalse(state.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `enableMockCamera should not enable on failure`() = runTest {
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
