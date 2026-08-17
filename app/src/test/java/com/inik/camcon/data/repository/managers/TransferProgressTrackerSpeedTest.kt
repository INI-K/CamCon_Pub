package com.inik.camcon.data.repository.managers

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * [TransferProgressTracker] 처리율(B/s) 산출·EMA·idle 리셋 검증 (리뷰 MEDIUM 반영).
 *
 * 고정/전진 가능한 [MutableClock] 을 주 생성자로 주입해 경과시간을 결정적으로 제어한다.
 * 검증은 전부 [TransferProgressTracker.state] StateFlow 방출값 기준(내부 구현 미검증).
 */
class TransferProgressTrackerSpeedTest {

    private class MutableClock(private var nowMs: Long) : Clock() {
        fun advance(ms: Long) {
            nowMs += ms
        }

        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = Instant.ofEpochMilli(nowMs)
    }

    @Test
    fun `첫 파일 처리율은 EMA 시드로 그대로 노출된다`() {
        val clock = MutableClock(0L)
        val tracker = TransferProgressTracker(clock)

        tracker.markDownloading("a.jpg")
        clock.advance(1_000L)
        tracker.markProcessing("a.jpg", 1_048_576L) // 1MB / 1s

        assertEquals(1_048_576L, tracker.state.value.speedBytesPerSec)
    }

    @Test
    fun `두번째 파일부터 EMA(0_4 최근, 0_6 과거)로 완만화된다`() {
        val clock = MutableClock(0L)
        val tracker = TransferProgressTracker(clock)

        tracker.markDownloading("a.jpg")
        clock.advance(1_000L)
        tracker.markProcessing("a.jpg", 1_048_576L) // 1MB/s 시드

        tracker.markDownloading("b.jpg")
        clock.advance(1_000L)
        tracker.markProcessing("b.jpg", 2_097_152L) // 순간 2MB/s

        // 0.4*2097152 + 0.6*1048576 = 1468006.4 → toLong = 1468006
        assertEquals(1_468_006L, tracker.state.value.speedBytesPerSec)
    }

    @Test
    fun `큐가 완전히 비면 처리율이 0 으로 리셋된다`() {
        val clock = MutableClock(0L)
        val tracker = TransferProgressTracker(clock)

        tracker.markDownloading("a.jpg")
        clock.advance(1_000L)
        tracker.markProcessing("a.jpg", 1_048_576L)
        tracker.markDone("a.jpg")

        assertEquals(0L, tracker.state.value.speedBytesPerSec)
    }

    @Test
    fun `markDownloading 없이 markProcessing 만 오면 처리율을 갱신하지 않는다`() {
        val clock = MutableClock(0L)
        val tracker = TransferProgressTracker(clock)

        tracker.markProcessing("direct.jpg", 5_000_000L)

        assertEquals(0L, tracker.state.value.speedBytesPerSec)
    }

    @Test
    fun `바이트 수가 0 이하면 처리율을 갱신하지 않는다`() {
        val clock = MutableClock(0L)
        val tracker = TransferProgressTracker(clock)

        tracker.markDownloading("a.jpg")
        clock.advance(1_000L)
        tracker.markProcessing("a.jpg", -1L)

        assertEquals(0L, tracker.state.value.speedBytesPerSec)
    }

    @Test
    fun `동일 시각 완료는 경과 1ms 로 보정해 0 나누기를 방지한다`() {
        val clock = MutableClock(0L)
        val tracker = TransferProgressTracker(clock)

        tracker.markDownloading("a.jpg")
        tracker.markProcessing("a.jpg", 1_000L) // 경과 0ms → 1ms 보정 → 1_000_000 B/s

        assertEquals(1_000_000L, tracker.state.value.speedBytesPerSec)
    }
}
