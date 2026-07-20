package com.inik.camcon.data.cache

import android.util.Log
import com.inik.camcon.di.CacheModule
import com.inik.camcon.domain.cache.ProcessedFileCache
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * `CacheModule.provideCacheSweeper` 의 **실제 프로덕션 sweep 루프**를 구동해 검증한다.
 *
 * 이전 버전은 sweep 루프를 테스트 파일 안에 사본(`launchSweepLoop`)으로 복제해 검증했는데,
 * 프로덕션이 바뀌어도 사본이 그대로면 회귀를 놓치는 **미러 표류** 문제가 있었다. 본 재작성은
 * `di.CacheModule.provideCacheSweeper(cache, scope)` 를 직접 호출해 실제 `while(isActive){ delay; runCatching{ sweepExpired() } }`
 * 루프를 `TestScope` 가상 시계 위에서 돌린다. 따라서 프로덕션 루프의 주기·예외 회복·취소 종료를 그대로 검증한다.
 *
 * 검증 대상은 **관찰 가능한 행위**(sweepExpired 호출 횟수) 뿐이다 — 내부 Job 핸들에 의존하지 않는다.
 *
 * sweep 주기(1h)는 `CacheModule` 이 문서로 보장하는 계약이므로 `TimeUnit.HOURS.toMillis(1)` 로 고정 검증한다.
 * 프로덕션이 주기를 바꾸면 본 테스트가 깨져 계약 변경을 강제로 드러낸다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CacheSweeperTest {

    private val sweepIntervalMillis: Long = TimeUnit.HOURS.toMillis(1)

    @Before
    fun setUp() {
        // provideCacheSweeper 의 실패 경로가 LogcatManager.w → android.util.Log 를 호출하므로 정적 목킹.
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    /**
     * 실제 프로덕션 sweeper 를 `testScheduler` 위에서 시작한다.
     * 반환한 scope 는 테스트 종료 전 반드시 취소해 무한 루프 코루틴 누수를 막는다.
     */
    private fun TestScope.startRealSweeper(
        cache: ProcessedFileCache,
    ): Pair<CacheSweeper, CoroutineScope> {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val sweeper = CacheModule.provideCacheSweeper(cache, scope)
        return sweeper to scope
    }

    @Test
    fun `초기 시작 직후에는 sweepExpired가 즉시 호출되지 않는다`() = runTest {
        val cache = mockk<ProcessedFileCache>(relaxed = true)
        val (_, scope) = startRealSweeper(cache)

        // 1ms 만 진행 — 첫 1h delay 가 풀리기 전.
        testScheduler.advanceTimeBy(1L)
        testScheduler.runCurrent()

        verify(exactly = 0) { cache.sweepExpired() }

        scope.cancel()
    }

    @Test
    fun `정확히 1h 경과 후 sweepExpired가 처음 호출된다`() = runTest {
        val cache = mockk<ProcessedFileCache>(relaxed = true)
        val (_, scope) = startRealSweeper(cache)

        // 1h - 1ms: 아직 호출 전.
        testScheduler.advanceTimeBy(sweepIntervalMillis - 1L)
        testScheduler.runCurrent()
        verify(exactly = 0) { cache.sweepExpired() }

        // 추가 1ms → 정확히 1h 경과 → 첫 호출.
        testScheduler.advanceTimeBy(1L)
        testScheduler.runCurrent()
        verify(exactly = 1) { cache.sweepExpired() }

        scope.cancel()
    }

    @Test
    fun `5h 경과 시 sweepExpired가 정확히 5회 호출된다(주기성)`() = runTest {
        val cache = mockk<ProcessedFileCache>(relaxed = true)
        val (_, scope) = startRealSweeper(cache)

        testScheduler.advanceTimeBy(sweepIntervalMillis * 5)
        testScheduler.runCurrent()

        verify(exactly = 5) { cache.sweepExpired() }

        scope.cancel()
    }

    @Test
    fun `sweepExpired 예외 발생 시 다음 cycle이 계속 호출된다(runCatching 회복)`() = runTest {
        val cache = mockk<ProcessedFileCache>()
        var callCount = 0
        // 첫 호출은 예외, 이후 정상. 프로덕션 runCatching 이 삼키고 루프가 살아남아야 한다.
        every { cache.sweepExpired() } answers {
            callCount++
            if (callCount == 1) throw IllegalStateException("의도된 첫 cycle 실패")
        }
        val (_, scope) = startRealSweeper(cache)

        // 첫 cycle(1h) — 예외 발생.
        testScheduler.advanceTimeBy(sweepIntervalMillis)
        testScheduler.runCurrent()
        assertEquals("첫 호출 후 카운트", 1, callCount)

        // 두 번째 cycle(총 2h) — 루프가 죽지 않았다면 다시 호출된다.
        testScheduler.advanceTimeBy(sweepIntervalMillis)
        testScheduler.runCurrent()
        assertEquals("두 번째 호출 후 카운트(루프 생존)", 2, callCount)

        scope.cancel()
    }

    @Test
    fun `scope 취소 후에는 추가 sweepExpired 호출이 없다(메모리 누수 방어)`() = runTest {
        val cache = mockk<ProcessedFileCache>(relaxed = true)
        val (_, scope) = startRealSweeper(cache)

        testScheduler.advanceTimeBy(sweepIntervalMillis * 2)
        testScheduler.runCurrent()
        verify(exactly = 2) { cache.sweepExpired() }

        scope.cancel()

        // 취소 이후 100h 진행해도 추가 호출 없어야 한다.
        testScheduler.advanceTimeBy(sweepIntervalMillis * 100)
        testScheduler.runCurrent()
        verify(exactly = 2) { cache.sweepExpired() }
    }

    @Test
    fun `CacheSweeper cancel이 sweep 루프를 멈춘다`() = runTest {
        val cache = mockk<ProcessedFileCache>(relaxed = true)
        val (sweeper, scope) = startRealSweeper(cache)

        testScheduler.advanceTimeBy(sweepIntervalMillis)
        testScheduler.runCurrent()
        verify(exactly = 1) { cache.sweepExpired() }

        // 래퍼의 cancel 이 보관 중인 Job 을 취소해 이후 sweep 이 멈춰야 한다.
        sweeper.cancel()
        testScheduler.advanceTimeBy(sweepIntervalMillis * 10)
        testScheduler.runCurrent()
        verify(exactly = 1) { cache.sweepExpired() }

        scope.cancel()
    }
}
