package com.inik.camcon.data.cache

import com.inik.camcon.domain.cache.ProcessedFileCache
import java.time.Clock
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.withLock

/**
 * [ProcessedFileCache] 의 LRU + TTL 구현.
 *
 * - **LRU**: `LinkedHashMap(accessOrder = true)` + `removeEldestEntry { size > maxSize }` 로 access-order 기반 eviction.
 * - **TTL**: 키마다 등록 시각([Clock.millis]) 을 저장하고, [contains] 호출 시 만료 키를 즉시 remove (lazy on read).
 *   추가로 `CacheSweeper` 가 1h 주기로 [sweepExpired] 호출 (eager).
 * - **동기화**: JDK [ReentrantLock] + `withLock`. JNI event thread / main / 임의 스레드 어디서든 안전.
 * - **시간 소스**: [Clock] 주입. 운영은 `Clock.systemUTC()`, 테스트는 `Clock.fixed/offset` 또는 FakeClock.
 *
 * dedup 목적이라 wall clock 점프(NTP/사용자 변경) 영향은 실용상 무시 가능.
 */
@Singleton
class TtlLruProcessedFileCache @Inject constructor(
    private val clock: Clock,
) : ProcessedFileCache {

    private val maxSize = MAX_SIZE
    private val ttlMillis = TTL_MILLIS

    private val entries = object : LinkedHashMap<String, Long>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean =
            size > maxSize
    }

    private val lock = ReentrantLock()

    override fun add(key: String): Boolean = lock.withLock {
        val now = clock.millis()
        val previous = entries.put(key, now)
        // 신규 키이거나, 이전 등록이 TTL 을 초과한 만료 키였다면 "새 처리"로 본다.
        previous == null || (now - previous) >= ttlMillis
    }

    override fun contains(key: String): Boolean = lock.withLock {
        val ts = entries[key] ?: return@withLock false
        val now = clock.millis()
        if (now - ts >= ttlMillis) {
            entries.remove(key)
            false
        } else {
            true
        }
    }

    override fun size(): Int = lock.withLock { entries.size }

    override fun remove(key: String): Boolean = lock.withLock { entries.remove(key) != null }

    override fun sweepExpired() {
        lock.withLock {
            val now = clock.millis()
            val iterator = entries.entries.iterator()
            while (iterator.hasNext()) {
                if (now - iterator.next().value >= ttlMillis) {
                    iterator.remove()
                }
            }
        }
    }

    override fun clear() {
        lock.withLock { entries.clear() }
    }

    companion object {
        const val MAX_SIZE = 1000
        val TTL_MILLIS: Long = TimeUnit.HOURS.toMillis(24)
    }
}
