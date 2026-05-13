package com.inik.camcon.data.cache

import com.inik.camcon.domain.cache.ProcessedFileCache
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * `CacheSweeper` 가 `@ApplicationScope` 에서 1h 주기로 [ProcessedFileCache.sweepExpired] 를 호출하고,
 * scope 취소 시 정상 종료되며, 예외 발생 시 다음 cycle 을 계속하는지 검증.
 *
 * **테스트 대상의 위치 주의**: 실제 sweep 코루틴은 `di/CacheModule.provideCacheSweeper` 의
 * `scope.launch { while (isActive) { delay; runCatching { ... } } }` 블록에 있다 (`CacheSweeper` 클래스 자체는
 * [Job] 보관 래퍼). Hilt provider 본문은 단위 테스트가 어려우므로, 본 테스트는 **같은 패턴**을
 * `TestScope` + `StandardTestDispatcher` 환경에서 재현하여 패턴이 기대대로 동작하는지를 검증한다.
 * `CacheModule` 가 본 패턴을 그대로 따르고 있으므로 회귀 방어선 가치 충분.
 *
 * `runTest` 의 `advanceTimeBy` / `runCurrent` 로 가상 시계를 직접 진행시킨다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CacheSweeperTest {

    private val sweepIntervalMillis: Long = TimeUnit.HOURS.toMillis(1)

    /**
     * Hilt provider 의 sweep 루프를 그대로 재현. 본문은 `CacheModule.provideCacheSweeper` 와 1:1 매칭.
     */
    private fun launchSweepLoop(
        scope: TestScope,
        cache: ProcessedFileCache,
        onFailure: ((Throwable) -> Unit)? = null,
    ): kotlinx.coroutines.Job {
        return scope.launch {
            while (isActive) {
                delay(sweepIntervalMillis)
                runCatching { cache.sweepExpired() }
                    .onFailure { throwable -> onFailure?.invoke(throwable) }
            }
        }
    }

    @Test
    fun `초기 launch 직후에는 sweepExpired 가 즉시 호출되지 않는다`() = runTest {
        val cache = mockk<ProcessedFileCache>(relaxed = true)

        // 자식 Job 만 보관·cancel. coroutineContext[Job] 은 runTest 자체의 Job 이라 cancel 금지.
        val job = launchSweepLoop(this, cache)
        // 시간을 1ms 만 진행. 1h delay 가 풀리기 전까지 호출 없어야 함.
        testScheduler.advanceTimeBy(1L)

        verify(exactly = 0) { cache.sweepExpired() }

        job.cancelAndJoin()
    }

    @Test
    fun `정확히 1h 경과 후 sweepExpired 가 처음 호출된다`() = runTest {
        val cache = mockk<ProcessedFileCache>(relaxed = true)

        val job = launchSweepLoop(this, cache)

        // 1h - 1ms: 아직 호출 전
        testScheduler.advanceTimeBy(sweepIntervalMillis - 1L)
        verify(exactly = 0) { cache.sweepExpired() }

        // 추가 1ms: 정확히 1h 경과 → 첫 호출 발생
        testScheduler.advanceTimeBy(1L)
        testScheduler.runCurrent()
        verify(exactly = 1) { cache.sweepExpired() }

        job.cancelAndJoin()
    }

    @Test
    fun `5h 경과 시 sweepExpired 가 정확히 5회 호출된다 (주기성 검증)`() = runTest {
        val cache = mockk<ProcessedFileCache>(relaxed = true)

        val job = launchSweepLoop(this, cache)
        testScheduler.advanceTimeBy(sweepIntervalMillis * 5)
        testScheduler.runCurrent()

        verify(exactly = 5) { cache.sweepExpired() }

        job.cancelAndJoin()
    }

    @Test
    fun `scope cancel 시 sweep 코루틴 정상 종료 (메모리 누수 방어)`() = runTest {
        val cache = mockk<ProcessedFileCache>(relaxed = true)

        val job = launchSweepLoop(this, cache)
        testScheduler.advanceTimeBy(sweepIntervalMillis * 2)
        testScheduler.runCurrent()
        assertTrue("cancel 전 job 은 active", job.isActive)

        job.cancelAndJoin()
        assertFalse("cancel 후 job 은 비활성", job.isActive)
        assertTrue("cancel 후 job 은 종료 상태", job.isCompleted || job.isCancelled)
    }

    @Test
    fun `sweepExpired 예외 발생 시 다음 cycle 이 계속 호출된다 (runCatching 회복 검증)`() = runTest {
        val cache = mockk<ProcessedFileCache>()
        val capturedFailures = mutableListOf<Throwable>()
        var callCount = 0

        // 첫 호출은 예외, 이후 정상 동작.
        every { cache.sweepExpired() } answers {
            callCount++
            if (callCount == 1) throw IllegalStateException("의도된 첫 cycle 실패")
            // 두 번째 호출 이후는 Unit 반환 (정상)
        }

        val job = launchSweepLoop(this, cache, onFailure = { capturedFailures += it })

        // 첫 cycle (1h) — 예외 발생
        testScheduler.advanceTimeBy(sweepIntervalMillis)
        testScheduler.runCurrent()
        assertEquals("첫 호출 후 카운트", 1, callCount)
        assertEquals("예외 1 건 캡처", 1, capturedFailures.size)
        assertTrue("캡처된 예외 메시지", capturedFailures[0].message?.contains("의도된 첫 cycle 실패") == true)

        // 두 번째 cycle (총 2h) — 정상 호출 (loop 가 죽지 않았음을 검증)
        testScheduler.advanceTimeBy(sweepIntervalMillis)
        testScheduler.runCurrent()
        assertEquals("두 번째 호출 후 카운트", 2, callCount)
        // 두 번째 호출은 예외 없음.
        assertEquals("예외 카운트 변동 없음", 1, capturedFailures.size)

        job.cancelAndJoin()
    }

    @Test
    fun `취소 후에는 추가 sweepExpired 호출이 발생하지 않는다`() = runTest {
        val cache = mockk<ProcessedFileCache>(relaxed = true)

        val job = launchSweepLoop(this, cache)
        // 2h 진행
        testScheduler.advanceTimeBy(sweepIntervalMillis * 2)
        testScheduler.runCurrent()
        verify(exactly = 2) { cache.sweepExpired() }

        job.cancelAndJoin()

        // cancel 이후 100h 진행해도 추가 호출 없어야 함.
        testScheduler.advanceTimeBy(sweepIntervalMillis * 100)
        testScheduler.runCurrent()
        verify(exactly = 2) { cache.sweepExpired() }
    }

    @Test
    fun `CacheSweeper 래퍼의 cancel 이 보관 중인 Job 을 취소한다`() = runTest {
        val cache = mockk<ProcessedFileCache>(relaxed = true)

        val job = launchSweepLoop(this, cache)
        val sweeper = CacheSweeper(job)

        testScheduler.advanceTimeBy(sweepIntervalMillis)
        testScheduler.runCurrent()
        assertTrue("cancel 전 active", job.isActive)

        sweeper.cancel()
        // cancel 은 비동기이므로 join 으로 보장.
        job.join()

        assertFalse("CacheSweeper.cancel 후 비활성", job.isActive)
        assertTrue("CacheSweeper.cancel 후 종료", job.isCancelled)
    }
}
