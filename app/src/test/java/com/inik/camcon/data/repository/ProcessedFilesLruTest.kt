package com.inik.camcon.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit

/**
 * CameraRepositoryImpl의 LRU processedFiles 동작 테스트.
 *
 * 실제 CameraRepositoryImpl는 JNI에 강하게 결합되어 직접 인스턴스화가 불가능하므로,
 * 동일한 패턴(synchronized + LinkedHashMap LRU)을 별도로 생성하여 검증한다.
 */
class ProcessedFilesLruTest {

    // CameraRepositoryImpl과 동일한 패턴으로 LRU Set 생성
    private lateinit var processedFiles: MutableSet<String>

    @Before
    fun setUp() {
        processedFiles = Collections.synchronizedSet(
            Collections.newSetFromMap(object : LinkedHashMap<String, Boolean>(16, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
                    return size > 1000
                }
            })
        )
    }

    // --- LRU 크기 제한 검증 ---

    @Test
    fun `1000개까지 정상적으로 추가됨`() {
        for (i in 1..1000) {
            processedFiles.add("file_$i")
        }
        assertEquals(1000, processedFiles.size)
    }

    @Test
    fun `1001번째 추가시 eldest entry가 제거됨`() {
        // Given: 1000개 항목 추가
        for (i in 1..1000) {
            processedFiles.add("file_$i")
        }
        assertTrue(processedFiles.contains("file_1"))

        // When: 1001번째 항목 추가
        processedFiles.add("file_1001")

        // Then: 크기는 1000으로 유지, eldest(file_1) 제거
        assertEquals(1000, processedFiles.size)
        assertFalse("eldest entry가 제거되어야 함", processedFiles.contains("file_1"))
        assertTrue(processedFiles.contains("file_1001"))
    }

    @Test
    fun `1500개 추가시 크기는 1000으로 유지됨`() {
        for (i in 1..1500) {
            processedFiles.add("file_$i")
        }
        assertEquals(1000, processedFiles.size)

        // 처음 500개는 제거되어야 함
        for (i in 1..500) {
            assertFalse("file_$i 이 존재하면 안됨", processedFiles.contains("file_$i"))
        }
        // 나중 1000개는 존재해야 함
        for (i in 501..1500) {
            assertTrue("file_$i 이 존재해야 함", processedFiles.contains("file_$i"))
        }
    }

    @Test
    fun `중복 추가시 크기 변화 없음`() {
        processedFiles.add("file_A")
        processedFiles.add("file_A")
        processedFiles.add("file_A")

        assertEquals(1, processedFiles.size)
    }

    @Test
    fun `add 반환값으로 중복 여부 확인`() {
        // Given: 첫 추가는 true
        assertTrue(processedFiles.add("file_key"))

        // When/Then: 동일 키 재추가는 false
        assertFalse(processedFiles.add("file_key"))
    }

    // --- LRU 접근 순서 검증 (accessOrder=true) ---

    @Test
    fun `접근된 항목은 LRU에서 최신으로 유지됨`() {
        // Given: 1000개 추가
        for (i in 1..1000) {
            processedFiles.add("file_$i")
        }

        // When: file_1을 접근 (contains는 Set에서는 access로 카운트되지 않지만,
        // add(기존)는 접근으로 카운트됨 - LinkedHashMap의 accessOrder=true)
        // LinkedHashMap의 accessOrder=true에서 get()이 호출되면 최신으로 이동
        // Set.contains() -> Map.containsKey() -> access order 업데이트 안됨
        // Set.add() -> Map.put() -> 기존 키면 accessOrder 업데이트 됨

        // file_1을 다시 add해서 접근 순서를 갱신
        processedFiles.add("file_1")

        // 1001번째 추가
        processedFiles.add("file_1001")

        // Then: file_1은 최근 접근으로 살아있고, file_2가 제거됨
        assertTrue("접근된 file_1은 유지되어야 함", processedFiles.contains("file_1"))
        assertFalse("최초 제거 대상 file_2가 제거되어야 함", processedFiles.contains("file_2"))
    }

    // --- 멀티스레드 안전성 검증 ---

    @Test
    fun `멀티스레드에서 동시 추가시 데이터 손실 없음`() {
        val threadCount = 10
        val itemsPerThread = 100
        val barrier = CyclicBarrier(threadCount)
        val latch = CountDownLatch(threadCount)

        val threads = (0 until threadCount).map { threadIndex ->
            Thread {
                barrier.await() // 모든 스레드가 동시에 시작
                for (i in 0 until itemsPerThread) {
                    processedFiles.add("thread_${threadIndex}_item_$i")
                }
                latch.countDown()
            }
        }

        threads.forEach { it.start() }
        assertTrue("모든 스레드 완료 대기", latch.await(10, TimeUnit.SECONDS))

        // 총 1000개 = 10 threads * 100 items (LRU 한도 이내)
        assertEquals(1000, processedFiles.size)
    }

    @Test
    fun `멀티스레드에서 LRU 한도 초과시 크기 1000 유지`() {
        val threadCount = 5
        val itemsPerThread = 500 // 총 2500개
        val barrier = CyclicBarrier(threadCount)
        val latch = CountDownLatch(threadCount)

        val threads = (0 until threadCount).map { threadIndex ->
            Thread {
                barrier.await()
                for (i in 0 until itemsPerThread) {
                    processedFiles.add("thread_${threadIndex}_item_$i")
                }
                latch.countDown()
            }
        }

        threads.forEach { it.start() }
        assertTrue("모든 스레드 완료 대기", latch.await(10, TimeUnit.SECONDS))

        // LRU 한도에 의해 1000 이하로 유지
        assertTrue("크기가 1000 이하여야 함", processedFiles.size <= 1000)
    }

    @Test
    fun `멀티스레드에서 동시 add와 contains 혼합시 예외 없음`() {
        // 사전에 500개 추가
        for (i in 0 until 500) {
            processedFiles.add("pre_$i")
        }

        val threadCount = 8
        val latch = CountDownLatch(threadCount)
        val barrier = CyclicBarrier(threadCount)
        val exceptions = Collections.synchronizedList(mutableListOf<Exception>())

        val threads = (0 until threadCount).map { threadIndex ->
            Thread {
                try {
                    barrier.await()
                    for (i in 0 until 200) {
                        if (threadIndex % 2 == 0) {
                            processedFiles.add("thread_${threadIndex}_$i")
                        } else {
                            // contains (읽기) + add (쓰기) 혼합
                            processedFiles.contains("pre_${i % 500}")
                            processedFiles.add("thread_${threadIndex}_$i")
                        }
                    }
                } catch (e: Exception) {
                    exceptions.add(e)
                } finally {
                    latch.countDown()
                }
            }
        }

        threads.forEach { it.start() }
        assertTrue("모든 스레드 완료 대기", latch.await(10, TimeUnit.SECONDS))

        assertTrue("예외가 발생하면 안됨: $exceptions", exceptions.isEmpty())
        assertTrue("크기가 1000 이하여야 함", processedFiles.size <= 1000)
    }

    // --- shouldProcessFile 패턴 검증 ---

    @Test
    fun `shouldProcessFile 패턴 - 새 파일키는 true, 동일 키는 false`() {
        // CameraRepositoryImpl.shouldProcessFile 패턴 재현
        fun shouldProcessFile(fileKey: String): Boolean = processedFiles.add(fileKey)

        val fileKey = "/store/DCIM/photo.jpg|photo.jpg|1234567"

        // 첫 호출: 처리해야 함
        assertTrue(shouldProcessFile(fileKey))

        // 두 번째 호출: 중복이므로 건너뛰어야 함
        assertFalse(shouldProcessFile(fileKey))
    }

    // --- clear 동작 ---

    @Test
    fun `clear 후 모든 항목 제거됨`() {
        for (i in 1..500) {
            processedFiles.add("file_$i")
        }
        assertEquals(500, processedFiles.size)

        processedFiles.clear()

        assertEquals(0, processedFiles.size)
    }
}
