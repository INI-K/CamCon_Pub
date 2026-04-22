package com.inik.camcon.domain.usecase.camera

import app.cash.turbine.test
import com.inik.camcon.domain.model.LiveViewFrame
import com.inik.camcon.domain.repository.CameraRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StartLiveViewUseCaseTest {

    private lateinit var useCase: StartLiveViewUseCase
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
     * Helper function to create test LiveViewFrame instances
     */
    private fun createTestFrame(
        width: Int = 1920,
        height: Int = 1080,
        timestamp: Long = System.currentTimeMillis()
    ): LiveViewFrame {
        return LiveViewFrame(
            data = ByteArray(width * height * 3 / 2) { (it % 256).toByte() },
            width = width,
            height = height,
            timestamp = timestamp
        )
    }

    /**
     * Happy Path Tests - 라이브뷰 시작 성공
     */

    @Test
    fun `라이브뷰 시작 - 단일 프레임 수신`() = runTest {
        // Given
        val frame = createTestFrame()
        every { cameraRepository.startLiveView() } returns flowOf(frame)
        useCase = StartLiveViewUseCase(cameraRepository)

        // When & Then
        useCase().test {
            val receivedFrame = awaitItem()
            assertEquals(frame.width, receivedFrame.width)
            assertEquals(frame.height, receivedFrame.height)
            assertEquals(frame.timestamp, receivedFrame.timestamp)
            awaitComplete()
        }
    }

    @Test
    fun `라이브뷰 시작 - 연속 프레임 수신`() = runTest {
        // Given
        val frames = listOf(
            createTestFrame(1920, 1080, 100L),
            createTestFrame(1920, 1080, 200L),
            createTestFrame(1920, 1080, 300L)
        )
        every { cameraRepository.startLiveView() } returns flowOf(*frames.toTypedArray())
        useCase = StartLiveViewUseCase(cameraRepository)

        // When & Then
        useCase().test {
            frames.forEach { expectedFrame ->
                val receivedFrame = awaitItem()
                assertEquals(expectedFrame.width, receivedFrame.width)
                assertEquals(expectedFrame.height, receivedFrame.height)
                assertEquals(expectedFrame.timestamp, receivedFrame.timestamp)
            }
            awaitComplete()
        }
    }

    @Test
    fun `라이브뷰 시작 - 다양한 해상도 프레임 처리`() = runTest {
        // Given
        val resolutions = listOf(
            Pair(1280, 720),   // HD
            Pair(1920, 1080),  // Full HD
            Pair(2560, 1440),  // 2K
            Pair(3840, 2160)   // 4K
        )
        val frames = resolutions.mapIndexed { index, (w, h) ->
            createTestFrame(w, h, (index + 1) * 100L)
        }
        every { cameraRepository.startLiveView() } returns flowOf(*frames.toTypedArray())
        useCase = StartLiveViewUseCase(cameraRepository)

        // When & Then
        useCase().test {
            resolutions.forEach { (expectedW, expectedH) ->
                val frame = awaitItem()
                assertEquals(expectedW, frame.width)
                assertEquals(expectedH, frame.height)
            }
            awaitComplete()
        }
    }

    @Test
    fun `라이브뷰 시작 - 타임스탬프 순서 유지`() = runTest {
        // Given
        val baseTime = 1000L
        val frames = (0..9).map { i ->
            createTestFrame(1920, 1080, baseTime + (i * 33L)) // 30fps simulation
        }
        every { cameraRepository.startLiveView() } returns flowOf(*frames.toTypedArray())
        useCase = StartLiveViewUseCase(cameraRepository)

        // When & Then
        useCase().test {
            var previousTimestamp = Long.MIN_VALUE
            repeat(10) {
                val frame = awaitItem()
                assertTrue(frame.timestamp >= previousTimestamp)
                previousTimestamp = frame.timestamp
            }
            awaitComplete()
        }
    }

    @Test
    fun `라이브뷰 시작 - 데이터 무결성 검증`() = runTest {
        // Given
        val testData = ByteArray(1920 * 1080 * 3 / 2) { (it % 256).toByte() }
        val frame = LiveViewFrame(
            data = testData,
            width = 1920,
            height = 1080,
            timestamp = 123456789L
        )
        every { cameraRepository.startLiveView() } returns flowOf(frame)
        useCase = StartLiveViewUseCase(cameraRepository)

        // When & Then
        useCase().test {
            val receivedFrame = awaitItem()
            assertEquals(testData.size, receivedFrame.data.size)
            assertEquals(testData.toList(), receivedFrame.data.toList())
            awaitComplete()
        }
    }

    /**
     * Edge Cases - 경계값, 특수 입력
     */

    @Test
    fun `라이브뷰 시작 - 빈 바이트 배열`() = runTest {
        // Given
        val frame = LiveViewFrame(
            data = ByteArray(0),
            width = 1920,
            height = 1080,
            timestamp = 100L
        )
        every { cameraRepository.startLiveView() } returns flowOf(frame)
        useCase = StartLiveViewUseCase(cameraRepository)

        // When & Then
        useCase().test {
            val receivedFrame = awaitItem()
            assertEquals(0, receivedFrame.data.size)
            awaitComplete()
        }
    }

    @Test
    fun `라이브뷰 시작 - 최소 해상도 (1x1)`() = runTest {
        // Given
        val frame = createTestFrame(1, 1)
        every { cameraRepository.startLiveView() } returns flowOf(frame)
        useCase = StartLiveViewUseCase(cameraRepository)

        // When & Then
        useCase().test {
            val receivedFrame = awaitItem()
            assertEquals(1, receivedFrame.width)
            assertEquals(1, receivedFrame.height)
            awaitComplete()
        }
    }

    @Test
    fun `라이브뷰 시작 - 최대 해상도 (8K)`() = runTest {
        // Given
        val frame = createTestFrame(7680, 4320) // 8K resolution
        every { cameraRepository.startLiveView() } returns flowOf(frame)
        useCase = StartLiveViewUseCase(cameraRepository)

        // When & Then
        useCase().test {
            val receivedFrame = awaitItem()
            assertEquals(7680, receivedFrame.width)
            assertEquals(4320, receivedFrame.height)
            awaitComplete()
        }
    }

    @Test
    fun `라이브뷰 시작 - 0 타임스탐프`() = runTest {
        // Given
        val frame = createTestFrame(timestamp = 0L)
        every { cameraRepository.startLiveView() } returns flowOf(frame)
        useCase = StartLiveViewUseCase(cameraRepository)

        // When & Then
        useCase().test {
            val receivedFrame = awaitItem()
            assertEquals(0L, receivedFrame.timestamp)
            awaitComplete()
        }
    }

    @Test
    fun `라이브뷰 시작 - 음수 타임스탐프`() = runTest {
        // Given
        val frame = createTestFrame(timestamp = -1000L)
        every { cameraRepository.startLiveView() } returns flowOf(frame)
        useCase = StartLiveViewUseCase(cameraRepository)

        // When & Then
        useCase().test {
            val receivedFrame = awaitItem()
            assertEquals(-1000L, receivedFrame.timestamp)
            awaitComplete()
        }
    }

    /**
     * Error Cases - 라이브뷰 실패 시나리오
     */

    @Test
    fun `라이브뷰 시작 실패 - 카메라 연결되지 않음`() = runTest {
        // Given
        val exception = RuntimeException("Camera not connected")
        every { cameraRepository.startLiveView() } returns flow {
            throw exception
        }
        useCase = StartLiveViewUseCase(cameraRepository)

        // When & Then
        useCase().test {
            val thrownException = awaitError()
            assertTrue(thrownException.message?.contains("not connected") == true)
        }
    }

    @Test
    fun `라이브뷰 시작 실패 - 라이브뷰 미지원`() = runTest {
        // Given
        val exception = IllegalStateException("Live view not supported on this camera model")
        every { cameraRepository.startLiveView() } returns flow {
            throw exception
        }
        useCase = StartLiveViewUseCase(cameraRepository)

        // When & Then
        useCase().test {
            val thrownException = awaitError()
            assertTrue(thrownException is IllegalStateException)
        }
    }

    @Test
    fun `라이브뷰 시작 실패 - USB 연결 해제`() = runTest {
        // Given
        val exception = RuntimeException("USB connection lost")
        every { cameraRepository.startLiveView() } returns flow {
            throw exception
        }
        useCase = StartLiveViewUseCase(cameraRepository)

        // When & Then
        useCase().test {
            val thrownException = awaitError()
            assertTrue(thrownException.message?.contains("USB") == true)
        }
    }

    @Test
    fun `라이브뷰 시작 실패 - 버퍼 오버플로우`() = runTest {
        // Given
        val exception = OutOfMemoryError("Buffer overflow: frame data too large")
        every { cameraRepository.startLiveView() } returns flow {
            throw exception
        }
        useCase = StartLiveViewUseCase(cameraRepository)

        // When & Then
        useCase().test {
            val thrownException = awaitError()
            assertTrue(thrownException is OutOfMemoryError)
        }
    }

    @Test
    fun `라이브뷰 시작 실패 - 메모리 부족`() = runTest {
        // Given
        val exception = OutOfMemoryError("Not enough memory for live view buffer")
        every { cameraRepository.startLiveView() } returns flow {
            throw exception
        }
        useCase = StartLiveViewUseCase(cameraRepository)

        // When & Then
        useCase().test {
            val thrownException = awaitError()
            assertTrue(thrownException is OutOfMemoryError)
        }
    }

    @Test
    fun `라이브뷰 시작 실패 - 프레임 수신 타임아웃`() = runTest {
        // Given
        val exception = RuntimeException("Live view frame timeout: no frame received for 5 seconds")
        every { cameraRepository.startLiveView() } returns flow {
            throw exception
        }
        useCase = StartLiveViewUseCase(cameraRepository)

        // When & Then
        useCase().test {
            val thrownException = awaitError()
            assertTrue(thrownException.message?.contains("timeout") == true)
        }
    }

    /**
     * Multiple Call Tests - 연속 호출, 중복 시작
     */

    @Test
    fun `라이브뷰 순차적 시작 - 각각 독립적으로 작동`() = runTest {
        // Given
        val frame1 = createTestFrame(1920, 1080, 100L)
        val frame2 = createTestFrame(1280, 720, 200L)

        every { cameraRepository.startLiveView() } returns flowOf(frame1)
        useCase = StartLiveViewUseCase(cameraRepository)

        // When & Then - First call
        useCase().test {
            val received1 = awaitItem()
            assertEquals(1920, received1.width)
            awaitComplete()
        }

        // Update mock for second call
        every { cameraRepository.startLiveView() } returns flowOf(frame2)

        // When & Then - Second call
        useCase().test {
            val received2 = awaitItem()
            assertEquals(1280, received2.width)
            awaitComplete()
        }
    }

    @Test
    fun `라이브뷰 시작 - 구독자 취소 감지`() = runTest {
        // Given
        val frames = listOf(
            createTestFrame(1920, 1080, 100L),
            createTestFrame(1920, 1080, 200L),
            createTestFrame(1920, 1080, 300L)
        )
        every { cameraRepository.startLiveView() } returns flowOf(*frames.toTypedArray())
        useCase = StartLiveViewUseCase(cameraRepository)

        // When & Then
        useCase().test {
            val frame1 = awaitItem()
            assertEquals(100L, frame1.timestamp)

            // Cancel subscription after first frame
            cancelAndIgnoreRemainingEvents()
        }
    }
}
