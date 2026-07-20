package com.inik.camcon.data.repository.managers

import com.inik.camcon.utils.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [PhotoDownloadManager.computeFreeTierTargetSize] 단위 테스트 (FREE 2000px 리사이즈 게이팅).
 *
 * FREE 티어는 장축 [Constants.ImageProcessing.FREE_TIER_MAX_DIMENSION](=2000px)로 축소 저장한다.
 * 실제 픽셀 리사이즈(BitmapFactory)는 자동 테스트 불가 경로이므로, 게이팅의 핵심인 '리사이즈 필요
 * 여부 + 목표 치수' 순수 판정만 고정한다. data/presentation 두 경로가 이 상수를 공유하므로
 * 값 드리프트도 함께 방지한다(감사 MAJOR: 2000px 이중 구현 통합).
 */
class PhotoDownloadManagerFreeTierResizeTest {

    private val limit = Constants.ImageProcessing.FREE_TIER_MAX_DIMENSION

    @Test
    fun `게이팅 상수는 2000px`() {
        assertEquals(2000, limit)
    }

    @Test
    fun `상한 이하 이미지는 null (리사이즈 불필요)`() {
        assertNull(PhotoDownloadManager.computeFreeTierTargetSize(limit, limit))
        assertNull(PhotoDownloadManager.computeFreeTierTargetSize(1600, 1200))
        assertNull(PhotoDownloadManager.computeFreeTierTargetSize(2000, 1000))
    }

    @Test
    fun `가로 장축 초과는 장축을 2000으로 축소하고 비율 유지`() {
        // 6000x4000 (3:2) → 장축 6000 을 2000 으로. scale=1/3 → 2000x1333
        val (w, h) = PhotoDownloadManager.computeFreeTierTargetSize(6000, 4000)!!
        assertEquals(2000, w)
        assertEquals(1333, h)
    }

    @Test
    fun `세로 장축 초과도 장축을 2000으로 축소`() {
        // 4000x6000 → scale=1/3 → 1333x2000
        val (w, h) = PhotoDownloadManager.computeFreeTierTargetSize(4000, 6000)!!
        assertEquals(1333, w)
        assertEquals(2000, h)
    }

    @Test
    fun `장축이 상한보다 1px 크면 리사이즈 대상`() {
        val target = PhotoDownloadManager.computeFreeTierTargetSize(limit + 1, 1000)
        // 리사이즈 대상이며 장축은 상한 이하로 축소된다.
        assertEquals(limit, target!!.first)
    }

    @Test
    fun `유효하지 않은 치수는 null`() {
        assertNull(PhotoDownloadManager.computeFreeTierTargetSize(0, 0))
        assertNull(PhotoDownloadManager.computeFreeTierTargetSize(-1, 100))
        assertNull(PhotoDownloadManager.computeFreeTierTargetSize(100, 0))
    }
}
