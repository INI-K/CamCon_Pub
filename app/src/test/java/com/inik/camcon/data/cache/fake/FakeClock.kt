package com.inik.camcon.data.cache.fake

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * 테스트용 `java.time.Clock` 픽스처. `advance(Duration)` 으로 시간을 직접 진행시킨다.
 *
 * - `Clock` 을 상속하므로 [millis] 는 Clock 기본 구현이 `instant().toEpochMilli()` 호출.
 * - 내부 `current` 는 `Clock.offset` 으로 누적 갱신.
 * - thread-safety 는 단일 스레드 가정. 동시성 테스트(Scenario 5)는 시간을 진행시키지 않으므로 안전.
 */
class FakeClock(initial: Instant = Instant.EPOCH) : Clock() {

    private var current: Clock = Clock.fixed(initial, ZoneOffset.UTC)

    override fun getZone(): ZoneId = current.zone

    override fun withZone(zone: ZoneId): Clock = current.withZone(zone)

    override fun instant(): Instant = current.instant()

    fun advance(duration: Duration) {
        current = Clock.offset(current, duration)
    }
}
