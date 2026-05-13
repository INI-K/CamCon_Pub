package com.inik.camcon.data.cache

import com.inik.camcon.data.cache.fake.FakeClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * [TtlLruProcessedFileCache] 단위 테스트.
 *
 * T3 5차 시나리오(`_workspace/03_tdd_scenarios.md`) 5종을 구현하되, 실제 구현
 * ([TtlLruProcessedFileCache.size] = `entries.size`, 만료 키 포함) 동작에 맞춰 assertion 조정.
 * 즉, `size()` 는 "보관 중인 모든 키 개수" 로서 만료 필터를 적용하지 않는다
 * (인터페이스 KDoc 의 "약한 보장" 정책).
 *
 * 시나리오:
 *  1. TTL 24h 경계 + 만료 후 add 재처리
 *  2. lazy expiry 가 contains 에만 적용 (add 는 적용 안 함, hot path 보호)
 *  3. LRU 1000 + TTL 24h 혼합 + access-order 갱신
 *  4. size() 경계 trio (LRU 999/1000/1001, TTL 24h-1ms/24h, sweepExpired 효과)
 *  5. ReentrantLock 동시 50 코루틴 add/contains 무경합
 */
class TtlLruProcessedFileCacheTest {

    private val ttlMillis = TimeUnit.HOURS.toMillis(24)

    // ─────────────────────────────────────────────────────────
    // Scenario 1: TTL 24h 경계 + 만료 후 add 재처리
    // ─────────────────────────────────────────────────────────

    @Test
    fun `scenario1_정확히 24h 경과 시점에 contains 는 false 반환`() {
        val clock = FakeClock(Instant.EPOCH)
        val cache = TtlLruProcessedFileCache(clock)

        assertTrue("신규 add 는 true 반환", cache.add("photo_A.jpg"))
        assertTrue(cache.contains("photo_A.jpg"))

        // TTL 정확히 24h. 구현은 `>=` 비교라 만료로 본다.
        clock.advance(Duration.ofHours(24))

        assertFalse("정확히 TTL 시점은 만료(>=)", cache.contains("photo_A.jpg"))
        // contains 가 만료 키를 제거하므로 size 0.
        assertEquals(0, cache.size())
    }

    @Test
    fun `scenario1_경계_TTL-1ms 는 미만료 contains true`() {
        val clock = FakeClock(Instant.EPOCH)
        val cache = TtlLruProcessedFileCache(clock)

        cache.add("photo_A.jpg")
        clock.advance(Duration.ofHours(24).minusMillis(1))

        assertTrue("TTL-1ms 는 미만료", cache.contains("photo_A.jpg"))
        assertEquals(1, cache.size())
    }

    @Test
    fun `scenario1_sub_만료 후 contains 거치지 않고 add 재호출 시 재처리로 true`() {
        val clock = FakeClock(Instant.EPOCH)
        val cache = TtlLruProcessedFileCache(clock)

        assertTrue(cache.add("photo_B.jpg"))
        clock.advance(Duration.ofHours(25))

        // contains 를 거치지 않는 sweep 누락 케이스: add 가 만료를 인지하고 true 반환.
        assertTrue("만료 후 add 는 재처리 신호", cache.add("photo_B.jpg"))
        assertTrue(cache.contains("photo_B.jpg"))
    }

    @Test
    fun `scenario1_sub_만료 후 contains 로 제거 직후 add 는 신규로 true`() {
        val clock = FakeClock(Instant.EPOCH)
        val cache = TtlLruProcessedFileCache(clock)

        cache.add("photo_C.jpg")
        clock.advance(Duration.ofHours(24))
        assertFalse(cache.contains("photo_C.jpg")) // 이 시점에 entries 에서 remove

        assertTrue("entries 에 없으므로 신규로 true", cache.add("photo_C.jpg"))
        assertTrue(cache.contains("photo_C.jpg"))
    }

    // ─────────────────────────────────────────────────────────
    // Scenario 2: lazy expiry 는 contains 에만 적용 (add 는 미적용)
    // ─────────────────────────────────────────────────────────

    @Test
    fun `scenario2_caseA_add 는 lazy expiry 를 발동시키지 않는다 hot path 보호`() {
        val clock = FakeClock(Instant.EPOCH)
        val cache = TtlLruProcessedFileCache(clock)

        cache.add("old_1.jpg")
        cache.add("old_2.jpg")
        cache.add("old_3.jpg")
        assertEquals(3, cache.size())

        clock.advance(Duration.ofHours(25))
        // add 는 다른 키의 만료를 정리하지 않음.
        assertTrue(cache.add("new_1.jpg"))

        // 실제 구현: size() = entries.size, 만료 필터 미적용.
        // old_1~3 (만료) + new_1 (유효) = 4 가 남아있다 (회수 시점은 contains 또는 sweepExpired).
        assertEquals(
            "add 는 만료 키를 정리하지 않으므로 entries 가 4건 잔존",
            4,
            cache.size()
        )
        assertTrue(cache.contains("new_1.jpg"))
    }

    @Test
    fun `scenario2_caseB_contains 는 조회 키만 lazy expiry`() {
        val clock = FakeClock(Instant.EPOCH)
        val cache = TtlLruProcessedFileCache(clock)

        cache.add("old_1.jpg")
        cache.add("old_2.jpg")
        cache.add("old_3.jpg")

        clock.advance(Duration.ofHours(25))

        assertFalse("old_2 는 만료", cache.contains("old_2.jpg"))
        // 조회된 키(old_2)만 entries 에서 제거. old_1/old_3 는 아직 잔존.
        assertEquals(2, cache.size())
    }

    @Test
    fun `scenario2_caseC_sweepExpired 는 모든 만료 키를 일괄 정리`() {
        val clock = FakeClock(Instant.EPOCH)
        val cache = TtlLruProcessedFileCache(clock)

        cache.add("old_1.jpg")
        cache.add("old_2.jpg")
        cache.add("old_3.jpg")

        clock.advance(Duration.ofHours(25))
        cache.sweepExpired()

        assertEquals(0, cache.size())
        assertFalse(cache.contains("old_1.jpg"))
        assertFalse(cache.contains("old_3.jpg"))
    }

    // ─────────────────────────────────────────────────────────
    // Scenario 3: LRU 1000 + TTL 24h 혼합 + access-order
    // ─────────────────────────────────────────────────────────

    @Test
    fun `scenario3_main_LRU 와 TTL 혼합 시 가장 오래된 키부터 축출되어 1000 유지`() {
        val clock = FakeClock(Instant.EPOCH)
        val cache = TtlLruProcessedFileCache(clock)

        // Phase 1: T=0, old 500 적재
        repeat(500) { i -> cache.add("old_%04d.jpg".format(i)) }
        // Phase 2: T=12h, mid 500 적재
        clock.advance(Duration.ofHours(12))
        repeat(500) { i -> cache.add("mid_%04d.jpg".format(i)) }
        assertEquals(1000, cache.size())

        // Phase 3: T=25h. old_* 는 만료, mid_* 는 미만료.
        clock.advance(Duration.ofHours(13))
        repeat(600) { i -> cache.add("new_%04d.jpg".format(i)) }

        // accessOrder=true + removeEldestEntry size>1000 로
        // 가장 오래된 키(old_* 500 + mid_0000~0099 100) 600 개가 LRU 축출됨.
        assertEquals(1000, cache.size())
        assertFalse(cache.contains("old_0000.jpg"))
        assertFalse(cache.contains("old_0499.jpg"))
        assertFalse(cache.contains("mid_0099.jpg"))
        assertTrue(cache.contains("mid_0100.jpg"))
        assertTrue(cache.contains("mid_0499.jpg"))
        assertTrue(cache.contains("new_0000.jpg"))
        assertTrue(cache.contains("new_0599.jpg"))
    }

    @Test
    fun `scenario3_sub_contains 가 access-order 를 갱신해 가장 오래된 키가 살아남는다`() {
        val clock = FakeClock(Instant.EPOCH)
        val cache = TtlLruProcessedFileCache(clock)

        // Phase 1+2: 1000 개 적재 (T=12h, 모두 미만료)
        repeat(500) { i -> cache.add("old_%04d.jpg".format(i)) }
        clock.advance(Duration.ofHours(12))
        repeat(500) { i -> cache.add("mid_%04d.jpg".format(i)) }
        assertEquals(1000, cache.size())

        // contains 호출이 accessOrder=true 의 LinkedHashMap.get 을 통해 recency 갱신.
        assertTrue(cache.contains("old_0000.jpg"))

        // 1001 번째 add 시 eldest 1 건이 축출되어야 함.
        cache.add("trigger.jpg")
        assertEquals(1000, cache.size())

        // old_0000 은 방금 contains 로 가장 최근이 되었으므로 살아남고,
        // 그 다음으로 오래된 old_0001 이 축출됨.
        assertTrue("recency 갱신된 키는 잔존", cache.contains("old_0000.jpg"))
        assertFalse("다음으로 오래된 키가 LRU 축출", cache.contains("old_0001.jpg"))
    }

    // ─────────────────────────────────────────────────────────
    // Scenario 4: size() 경계 trio
    // ─────────────────────────────────────────────────────────

    @Test
    fun `scenario4_caseA_LRU 경계 999 1000 1001`() {
        val clock = FakeClock(Instant.EPOCH)
        val cache = TtlLruProcessedFileCache(clock)

        repeat(999) { i -> cache.add("lru_%04d.jpg".format(i)) }
        assertEquals(999, cache.size())

        cache.add("lru_0999.jpg") // 1000 번째
        assertEquals(1000, cache.size())
        // 주의: 여기서 contains 를 호출하면 accessOrder=true 의 recency 가 갱신되어
        // 다음 add 의 LRU 축출 대상이 바뀐다. 사전 검증은 size 만 사용.

        cache.add("lru_1000.jpg") // 1001 번째 → eldest 축출
        assertEquals(1000, cache.size())
        // contains 의 recency 부수효과를 회피하려고 가장 마지막 키부터 역순 검증:
        // lru_1000 은 최근 추가되었으므로 잔존.
        assertTrue(cache.contains("lru_1000.jpg"))
        // lru_0000 이 축출되었어야 함. 단 위 contains 가 lru_1000 의 access-order 만 갱신했으므로
        // 가장 오래된 키는 여전히 lru_0000.
        assertFalse("가장 오래된 키 축출", cache.contains("lru_0000.jpg"))
    }

    @Test
    fun `scenario4_caseB_TTL 경계 24h-1ms 와 24h`() {
        val clock = FakeClock(Instant.EPOCH)
        val cache = TtlLruProcessedFileCache(clock)

        cache.add("ttl_a.jpg")

        clock.advance(Duration.ofHours(24).minusMillis(1))
        assertTrue("TTL-1ms 는 미만료", cache.contains("ttl_a.jpg"))
        assertEquals(1, cache.size())

        clock.advance(Duration.ofMillis(1))
        assertFalse("정확히 TTL 시점은 만료", cache.contains("ttl_a.jpg"))
        // contains 가 만료 키를 제거하므로 size 0.
        assertEquals(0, cache.size())
    }

    @Test
    fun `scenario4_caseC_sweepExpired 후 size 0 그리고 멱등`() {
        val clock = FakeClock(Instant.EPOCH)
        val cache = TtlLruProcessedFileCache(clock)

        cache.add("s1.jpg")
        cache.add("s2.jpg")
        clock.advance(Duration.ofHours(25))
        cache.sweepExpired()

        assertEquals(0, cache.size())
        assertFalse(cache.contains("s1.jpg"))
        assertFalse(cache.contains("s2.jpg"))

        // 멱등: 만료 키 없는 상태에서 sweepExpired 재호출은 no-op.
        cache.sweepExpired()
        assertEquals(0, cache.size())
    }

    // ─────────────────────────────────────────────────────────
    // Scenario 5: ReentrantLock 동시 50 코루틴 add/contains 무경합
    // ─────────────────────────────────────────────────────────

    /**
     * runTest 의 가상 디스패처는 단일 스레드 직렬화로 race 를 노출하지 못한다.
     * 진짜 멀티스레드 race 검증 위해 `runBlocking(Dispatchers.Default)` 사용.
     */
    @Test(timeout = 15_000)
    fun `scenario5_50 코루틴 동시 add contains 시 ReentrantLock 직렬화 무경합`() {
        val clock = FakeClock(Instant.EPOCH) // 시간 정지 → TTL 영향 차단
        val cache = TtlLruProcessedFileCache(clock)
        val keys = (0 until 100).map { "concurrent_%03d.jpg".format(it) }

        val addTrueCount = AtomicInteger(0)
        val addFalseCount = AtomicInteger(0)
        val exceptions = ConcurrentHashMap.newKeySet<String>()

        runBlocking(Dispatchers.Default) {
            val jobs = (0 until 50).map {
                launch {
                    try {
                        keys.forEach { key ->
                            if (cache.add(key)) addTrueCount.incrementAndGet()
                            else addFalseCount.incrementAndGet()
                            cache.contains(key)
                        }
                    } catch (t: Throwable) {
                        exceptions += "${t::class.simpleName}: ${t.message}"
                    }
                }
            }
            jobs.forEach { it.join() }
        }

        assertTrue("예외 발생: $exceptions", exceptions.isEmpty())
        assertEquals("중복 add 는 size 증가 없음", 100, cache.size())
        keys.forEach { assertTrue("$it 누락", cache.contains(it)) }

        // add 반환값 분포: 5000 호출 중 정확히 100 개만 true (각 키의 첫 add).
        // 단, 동시 add 의 경우 어느 코루틴이 첫 add 인지 race 결정이지만 "정확히 100 개" 는 결정적.
        assertEquals(100, addTrueCount.get())
        assertEquals(4900, addFalseCount.get())
    }
}
