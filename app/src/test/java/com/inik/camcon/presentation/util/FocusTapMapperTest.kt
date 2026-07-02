package com.inik.camcon.presentation.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * mapTapToCameraPoint 순수 함수 검증 — ContentScale.Fit 레터박스 + 180° 회전 + 범위 거부 + 반올림.
 * (JNI/USB 경로는 실기기 필요 — CLAUDE.md §5. 매핑 수학만 단위테스트한다.)
 */
class FocusTapMapperTest {

    @Test
    fun `동일 종횡비 중앙 탭은 중앙으로`() {
        // box == bmp (레터박스 없음)
        val p = mapTapToCameraPoint(500f, 250f, 1000, 500, 1000, 500, rotated = false)
        assertEquals(CameraFocusPoint(500, 250), p)
    }

    @Test
    fun `가로 필러박스 오프셋 보정`() {
        // box 2000x500, bmp 1000x500(2:1) -> scale=1, offX=500
        // tapX=750 -> ix=(750-500)/1=250
        val p = mapTapToCameraPoint(750f, 250f, 2000, 500, 1000, 500, rotated = false)
        assertEquals(CameraFocusPoint(250, 250), p)
    }

    @Test
    fun `레터박스 여백 탭은 null`() {
        // 위와 같은 레이아웃에서 좌측 여백(x=100 < offX=500) 탭
        assertNull(mapTapToCameraPoint(100f, 250f, 2000, 500, 1000, 500, rotated = false))
    }

    @Test
    fun `180도 회전은 좌표를 반전`() {
        // 좌상단 탭(0,0)이 회전 시 우하단(bmpW,bmpH)으로
        val p = mapTapToCameraPoint(0f, 0f, 1000, 500, 1000, 500, rotated = true)
        assertEquals(CameraFocusPoint(1000, 500), p)
    }

    @Test
    fun `세로 레터박스(상하 여백) 보정`() {
        // box 1000x1000, bmp 1000x500(2:1) -> scale=1, offY=250
        // tapY=600 -> iy=(600-250)/1=350
        val p = mapTapToCameraPoint(500f, 600f, 1000, 1000, 1000, 500, rotated = false)
        assertEquals(CameraFocusPoint(500, 350), p)
    }

    @Test
    fun `다운스케일된 표시에서 비율 환산`() {
        // box 500x250, bmp 1000x500 -> scale=0.5, off=0
        // tap(250,125) -> ix=250/0.5=500, iy=125/0.5=250
        val p = mapTapToCameraPoint(250f, 125f, 500, 250, 1000, 500, rotated = false)
        assertEquals(CameraFocusPoint(500, 250), p)
    }

    @Test
    fun `잘못된 치수는 null`() {
        assertNull(mapTapToCameraPoint(10f, 10f, 0, 100, 100, 100, rotated = false))
        assertNull(mapTapToCameraPoint(10f, 10f, 100, 100, 0, 100, rotated = false))
    }

    // computeFittedRect — 그리드/오버레이가 그려질 실제 영상 영역(ContentScale.Fit) 검증.

    @Test
    fun `동일 종횡비는 컨테이너 전체가 표시 영역`() {
        // box == 콘텐츠 비율 -> 여백 없음, rect == 컨테이너
        val r = computeFittedRect(1000, 500, 1000, 500)!!
        assertEquals(0f, r.left, 0.001f)
        assertEquals(0f, r.top, 0.001f)
        assertEquals(1000f, r.width, 0.001f)
        assertEquals(500f, r.height, 0.001f)
    }

    @Test
    fun `가로 필러박스는 좌우 여백을 만든다`() {
        // box 2000x500, 콘텐츠 1000x500(2:1) -> scale=1, 표시폭 1000, 좌우 각 500 여백
        val r = computeFittedRect(2000, 500, 1000, 500)!!
        assertEquals(500f, r.left, 0.001f)
        assertEquals(0f, r.top, 0.001f)
        assertEquals(1000f, r.width, 0.001f)
        assertEquals(500f, r.height, 0.001f)
        assertEquals(1500f, r.right, 0.001f)
    }

    @Test
    fun `세로 레터박스는 상하 여백을 만든다`() {
        // box 1000x1000, 콘텐츠 1000x500(2:1) -> scale=1, 표시높이 500, 상하 각 250 여백
        val r = computeFittedRect(1000, 1000, 1000, 500)!!
        assertEquals(0f, r.left, 0.001f)
        assertEquals(250f, r.top, 0.001f)
        assertEquals(1000f, r.width, 0.001f)
        assertEquals(500f, r.height, 0.001f)
        assertEquals(750f, r.bottom, 0.001f)
    }

    @Test
    fun `computeFittedRect 잘못된 치수는 null`() {
        assertNull(computeFittedRect(0, 100, 100, 100))
        assertNull(computeFittedRect(100, 100, 100, 0))
    }
}
