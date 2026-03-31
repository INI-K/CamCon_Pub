package com.inik.camcon.data.datasource.usb

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest

/**
 * UsbConnectionManager의 AtomicBoolean + Mutex 동시 초기화 방지 패턴 테스트.
 *
 * UsbConnectionManager는 Android Context와 JNI(CameraNative)에 강하게 결합되어
 * 직접 인스턴스화가 불가능하므로, 동일한 패턴을 별도로 구현하여 검증한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UsbConnectionManagerAtomicTest {

    // --- AtomicBoolean 기본 동작 ---

    @Test
    fun `AtomicBoolean 초기값은 false`() {
        val isInitializing = AtomicBoolean(false)
        assertFalse(isInitializing.get())
    }

    @Test
    fun `AtomicBoolean set과 get 동작`() {
        val isInitializing = AtomicBoolean(false)

        isInitializing.set(true)
        assertTrue(isInitializing.get())

        isInitializing.set(false)
        assertFalse(isInitializing.get())
    }

    // --- compareAndSet 패턴 검증 (isHandlingDisconnection) ---

    @Test
    fun `compareAndSet으로 중복 처리 방지`() {
        val isHandling = AtomicBoolean(false)

        // 첫 번째 호출: false -> true 성공
        assertTrue(isHandling.compareAndSet(false, true))
        assertTrue(isHandling.get())

        // 두 번째 호출: false -> true 실패 (이미 true)
        assertFalse(isHandling.compareAndSet(false, true))
    }

    // --- 초기화 가드 패턴 검증 ---

    @Test
    fun `isInitializingNativeCamera get()이 true이면 초기화 건너뛰기`() {
        val isInitializing = AtomicBoolean(false)
        var initCount = 0

        // UsbConnectionManager.connectToCamera() 패턴 재현
        fun connectToCamera() {
            if (isInitializing.get()) {
                return // 중복 방지
            }
            isInitializing.set(true)
            try {
                initCount++
            } finally {
                isInitializing.set(false)
            }
        }

        // 첫 연결: 정상 진행
        connectToCamera()
        assertEquals(1, initCount)

        // isInitializing을 수동으로 true로 설정하면 건너뜀
        isInitializing.set(true)
        connectToCamera()
        assertEquals(1, initCount) // 증가하지 않음
    }

    // --- Mutex + AtomicBoolean 조합 패턴 ---

    @Test
    fun `Mutex withLock 내부에서 AtomicBoolean 사용 패턴`() = runTest {
        val mutex = Mutex()
        val isInitializing = AtomicBoolean(false)
        var lastInitializedFd = -1
        var initCount = 0

        // UsbConnectionManager.initializeNativeCamera() 패턴 재현
        suspend fun initializeNativeCamera(fd: Int, isConnected: Boolean) {
            mutex.withLock {
                // 중복 FD 방지
                if (fd == lastInitializedFd && isConnected) return@withLock
                // 초기화 진행 중 체크
                if (isInitializing.get()) return@withLock
                // 이미 연결 체크
                if (isConnected && lastInitializedFd != -1) return@withLock

                isInitializing.set(true)
                lastInitializedFd = fd
                try {
                    initCount++
                } finally {
                    isInitializing.set(false)
                }
            }
        }

        // 첫 초기화: 성공
        initializeNativeCamera(100, false)
        assertEquals(1, initCount)
        assertEquals(100, lastInitializedFd)

        // 동일 FD + 연결됨: 건너뜀
        initializeNativeCamera(100, true)
        assertEquals(1, initCount)

        // 다른 FD + 연결되지 않음: 진행
        initializeNativeCamera(200, false)
        assertEquals(2, initCount)
    }

    // --- 멀티스레드에서 AtomicBoolean 동시 접근 ---

    @Test
    fun `멀티스레드에서 AtomicBoolean compareAndSet은 하나만 성공`() {
        val isHandling = AtomicBoolean(false)
        val threadCount = 10
        val barrier = CyclicBarrier(threadCount)
        val latch = CountDownLatch(threadCount)
        val successCount = java.util.concurrent.atomic.AtomicInteger(0)

        val threads = (0 until threadCount).map {
            Thread {
                barrier.await()
                if (isHandling.compareAndSet(false, true)) {
                    successCount.incrementAndGet()
                }
                latch.countDown()
            }
        }

        threads.forEach { it.start() }
        assertTrue(latch.await(5, TimeUnit.SECONDS))

        // 정확히 1개의 스레드만 성공
        assertEquals(1, successCount.get())
    }

    @Test
    fun `멀티스레드에서 AtomicBoolean set-get 일관성 유지`() {
        val isInitializing = AtomicBoolean(false)
        val threadCount = 100
        val latch = CountDownLatch(threadCount)
        val barrier = CyclicBarrier(threadCount)
        val errors = java.util.Collections.synchronizedList(mutableListOf<String>())

        val threads = (0 until threadCount).map { i ->
            Thread {
                try {
                    barrier.await()
                    for (j in 0 until 1000) {
                        isInitializing.set(true)
                        val afterSet = isInitializing.get()
                        // set(true) 직후에는 true여야 함 (다른 스레드가 set(false) 할 수 있지만
                        // AtomicBoolean 자체의 일관성은 보장됨)
                        isInitializing.set(false)
                    }
                } catch (e: Exception) {
                    errors.add("Thread $i: ${e.message}")
                } finally {
                    latch.countDown()
                }
            }
        }

        threads.forEach { it.start() }
        assertTrue(latch.await(10, TimeUnit.SECONDS))
        assertTrue("예외가 발생하면 안됨: $errors", errors.isEmpty())
        // 모든 스레드 종료 후 최종 상태는 false
        assertFalse(isInitializing.get())
    }

    // --- finally에서 isInitializing reset 보장 ---

    @Test
    fun `초기화 중 예외 발생시 finally에서 isInitializing이 false로 리셋`() {
        val isInitializing = AtomicBoolean(false)

        // UsbConnectionManager.initializeNativeCamera() finally 패턴
        try {
            isInitializing.set(true)
            assertTrue(isInitializing.get())
            throw RuntimeException("초기화 실패")
        } catch (e: Exception) {
            // 예외 처리
        } finally {
            isInitializing.set(false)
        }

        assertFalse("예외 후에도 isInitializing이 false여야 함", isInitializing.get())
    }
}
