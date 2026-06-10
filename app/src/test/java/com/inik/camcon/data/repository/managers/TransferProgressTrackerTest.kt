package com.inik.camcon.data.repository.managers

import com.inik.camcon.domain.model.TransferQueueState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * TransferProgressTracker 단위 테스트 (요구 E2: 다운로드/처리 진행 카운트).
 *
 * [TransferProgressTracker] 는 @Inject 생성자만 가질 뿐 의존성이 없어 직접 생성한다.
 * 안드로이드 프레임워크(Log 등)에 의존하지 않는 순수 로직이므로 JVM 단위 테스트로 충분하다.
 *
 * StateFlow.value 스냅샷을 직접 검증한다(요구 E: capture.transferQueue UDF 경로의 소스).
 */
class TransferProgressTrackerTest {

    private lateinit var tracker: TransferProgressTracker

    @Before
    fun setUp() {
        tracker = TransferProgressTracker()
    }

    // --- 단계 전이 ---

    @Test
    fun `markDownloading 시 downloading 카운트가 1 증가하고 isActive true`() {
        // Arrange: 초기 상태는 빈 큐
        assertEquals(TransferQueueState(), tracker.state.value)

        // Act
        tracker.markDownloading("KAY_1200.NEF")

        // Assert
        val state = tracker.state.value
        assertEquals(1, state.downloading)
        assertEquals(0, state.processing)
        assertEquals("KAY_1200.NEF", state.currentFileName)
        assertTrue(state.isActive)
    }

    @Test
    fun `markDownloading 후 markProcessing 시 downloading 0 processing 1 로 전이`() {
        // Arrange: 다운로드 시작
        tracker.markDownloading("KAY_1200.NEF")
        assertEquals(1, tracker.state.value.downloading)

        // Act: 같은 파일이 후처리 단계로 전이
        tracker.markProcessing("KAY_1200.NEF")

        // Assert: 같은 키이므로 downloading 에서 processing 으로 이동(합계 유지)
        val state = tracker.state.value
        assertEquals(0, state.downloading)
        assertEquals(1, state.processing)
        assertEquals("KAY_1200.NEF", state.currentFileName)
        assertEquals(1, state.total)
    }

    @Test
    fun `markProcessing 후 markDone 시 큐에서 제거되고 isActive false`() {
        // Arrange: 다운로드 → 처리 단계
        tracker.markDownloading("KAY_1200.NEF")
        tracker.markProcessing("KAY_1200.NEF")
        assertEquals(1, tracker.state.value.processing)

        // Act: 처리 완료
        tracker.markDone("KAY_1200.NEF")

        // Assert: 빈 큐로 복귀
        val state = tracker.state.value
        assertEquals(0, state.downloading)
        assertEquals(0, state.processing)
        assertNull(state.currentFileName)
        assertFalse(state.isActive)
    }

    // --- 듀얼슬롯 중복 방지 ---

    @Test
    fun `동일 fileName 으로 markDownloading 두 번 호출해도 downloading 은 1`() {
        // Arrange & Act: 듀얼슬롯이 같은 fileName 을 두 번 내려보내는 상황
        tracker.markDownloading("DSC_0001.JPG")
        tracker.markDownloading("DSC_0001.JPG")

        // Assert: 같은 키라 카운트가 부풀지 않음(요구 E2 듀얼슬롯 방어)
        val state = tracker.state.value
        assertEquals(1, state.downloading)
        assertEquals(1, state.total)
    }

    // --- RAW + JPG 동시 ---

    @Test
    fun `RAW 와 JPG 는 확장자가 달라 별개 키로 합계 2 집계`() {
        // Arrange & Act: 동일 컷의 RAW + JPG 두 포맷
        tracker.markDownloading("KAY_1200.NEF")
        tracker.markDownloading("KAY_1200.JPG")

        // Assert: 확장자가 달라 별개 키 → downloading 2
        val state = tracker.state.value
        assertEquals(2, state.downloading)
        assertEquals(2, state.total)
        assertTrue(state.isActive)
    }

    // --- 안전성 ---

    @Test
    fun `markDone 으로 없는 키 제거 시 no-op 으로 안전`() {
        // Arrange: 한 건 다운로드 중
        tracker.markDownloading("KAY_1200.NEF")

        // Act: 큐에 없는 키 제거
        tracker.markDone("UNKNOWN.JPG")

        // Assert: 기존 항목은 그대로 유지
        val state = tracker.state.value
        assertEquals(1, state.downloading)
        assertEquals("KAY_1200.NEF", state.currentFileName)
    }

    @Test
    fun `clear 시 TransferQueueState 기본값으로 방출되고 isActive false`() {
        // Arrange: 다운로드 + 처리 혼재 상태
        tracker.markDownloading("A.NEF")
        tracker.markProcessing("B.JPG")
        assertTrue(tracker.state.value.isActive)

        // Act: 연결 해제/리스너 정지 시점
        tracker.clear()

        // Assert: 기본 상태(isActive=false) 방출
        assertEquals(TransferQueueState(), tracker.state.value)
        assertFalse(tracker.state.value.isActive)
    }

    @Test
    fun `currentFileName 은 PROCESSING 을 DOWNLOADING 보다 우선`() {
        // Arrange & Act: 다운로드 항목이 뒤에 등장해도 처리 항목이 우선
        tracker.markProcessing("PROCESSING.JPG")
        tracker.markDownloading("DOWNLOADING.NEF")

        // Assert: 마지막 PROCESSING 우선(recompute 규칙)
        val state = tracker.state.value
        assertEquals("PROCESSING.JPG", state.currentFileName)
        assertEquals(1, state.downloading)
        assertEquals(1, state.processing)
    }
}
