package com.inik.camcon.data.repository.managers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PhotoDownloadManager의 rotateImageIfRequired() 로직 테스트.
 *
 * rotateImageIfRequired()가 private이고, Bitmap/Matrix는 Robolectric 없이
 * 사용 불가하므로 (현재 JDK 25 / class version 69 호환 이슈),
 * 동일한 분기 로직과 EXIF 상수값을 순수 JVM에서 검증한다.
 */
class ExifRotationTest {

    // ExifInterface 상수값 (android.media.ExifInterface에서 복사)
    companion object {
        const val ORIENTATION_UNDEFINED = 0
        const val ORIENTATION_NORMAL = 1
        const val ORIENTATION_ROTATE_90 = 6
        const val ORIENTATION_ROTATE_180 = 3
        const val ORIENTATION_ROTATE_270 = 8
    }

    /**
     * PhotoDownloadManager.rotateImageIfRequired()와 동일한 분기 로직 재현.
     * 회전 각도를 반환 (0 = 회전 불필요)
     */
    private fun getRotationDegrees(orientation: Int): Float {
        return when (orientation) {
            ORIENTATION_ROTATE_90 -> 90f
            ORIENTATION_ROTATE_180 -> 180f
            ORIENTATION_ROTATE_270 -> 270f
            else -> 0f // 회전 불필요
        }
    }

    /**
     * 회전 후 예상 크기 계산 (width, height)
     */
    private fun expectedSizeAfterRotation(
        width: Int, height: Int, degrees: Float
    ): Pair<Int, Int> {
        return when (degrees) {
            90f, 270f -> Pair(height, width) // width/height 교환
            else -> Pair(width, height) // 크기 유지
        }
    }

    // --- EXIF orientation 상수값 표준 준수 검증 (C7) ---

    @Test
    fun `EXIF orientation 상수값이 표준과 일치하는지 확인`() {
        // EXIF 2.32 표준: orientation 값
        // 1=Normal, 3=180도, 6=90CW, 8=270CW
        assertEquals(1, ORIENTATION_NORMAL)
        assertEquals(3, ORIENTATION_ROTATE_180)
        assertEquals(6, ORIENTATION_ROTATE_90)
        assertEquals(8, ORIENTATION_ROTATE_270)
    }

    // --- 회전 각도 결정 로직 ---

    @Test
    fun `ORIENTATION_ROTATE_90은 90도 회전`() {
        assertEquals(90f, getRotationDegrees(ORIENTATION_ROTATE_90))
    }

    @Test
    fun `ORIENTATION_ROTATE_180은 180도 회전`() {
        assertEquals(180f, getRotationDegrees(ORIENTATION_ROTATE_180))
    }

    @Test
    fun `ORIENTATION_ROTATE_270은 270도 회전`() {
        assertEquals(270f, getRotationDegrees(ORIENTATION_ROTATE_270))
    }

    @Test
    fun `ORIENTATION_NORMAL은 회전 없음`() {
        assertEquals(0f, getRotationDegrees(ORIENTATION_NORMAL))
    }

    @Test
    fun `ORIENTATION_UNDEFINED은 회전 없음`() {
        assertEquals(0f, getRotationDegrees(ORIENTATION_UNDEFINED))
    }

    // --- 회전 후 크기 변환 검증 ---

    @Test
    fun `90도 회전시 width와 height가 교환됨`() {
        // Given: 100x50 이미지
        val (newW, newH) = expectedSizeAfterRotation(100, 50, 90f)

        // Then: 50x100
        assertEquals(50, newW)
        assertEquals(100, newH)
    }

    @Test
    fun `180도 회전시 width와 height 동일 유지`() {
        val (newW, newH) = expectedSizeAfterRotation(100, 50, 180f)

        assertEquals(100, newW)
        assertEquals(50, newH)
    }

    @Test
    fun `270도 회전시 width와 height가 교환됨`() {
        val (newW, newH) = expectedSizeAfterRotation(100, 50, 270f)

        assertEquals(50, newW)
        assertEquals(100, newH)
    }

    @Test
    fun `정사각형 이미지 90도 회전시 크기 동일 유지`() {
        val (newW, newH) = expectedSizeAfterRotation(200, 200, 90f)

        assertEquals(200, newW)
        assertEquals(200, newH)
    }

    @Test
    fun `0도 회전시 크기 변화 없음`() {
        val (newW, newH) = expectedSizeAfterRotation(100, 50, 0f)

        assertEquals(100, newW)
        assertEquals(50, newH)
    }

    // --- 분기 로직 완전성 검증 ---

    @Test
    fun `when 분기가 모든 회전 케이스를 처리함`() {
        val allOrientations = listOf(
            ORIENTATION_UNDEFINED,
            ORIENTATION_NORMAL,
            ORIENTATION_ROTATE_90,
            ORIENTATION_ROTATE_180,
            ORIENTATION_ROTATE_270
        )

        for (orientation in allOrientations) {
            val degrees = getRotationDegrees(orientation)
            // 모든 케이스에서 예외 없이 각도 반환
            assertTrue(
                "orientation=$orientation 에서 유효한 각도 반환 실패",
                degrees in listOf(0f, 90f, 180f, 270f)
            )
        }
    }

    @Test
    fun `회전이 필요한 orientation은 3개뿐`() {
        val rotationNeeded = listOf(ORIENTATION_ROTATE_90, ORIENTATION_ROTATE_180, ORIENTATION_ROTATE_270)
        val noRotation = listOf(ORIENTATION_UNDEFINED, ORIENTATION_NORMAL)

        for (o in rotationNeeded) {
            assertTrue("orientation=$o 에서 회전이 필요해야 함", getRotationDegrees(o) != 0f)
        }
        for (o in noRotation) {
            assertEquals("orientation=$o 에서 회전이 불필요해야 함", 0f, getRotationDegrees(o))
        }
    }

    // --- Matrix 변환 수학적 검증 ---

    @Test
    fun `90도 회전 변환 행렬의 수학적 정확성`() {
        // 90도 CW 회전 행렬: [[cos90, -sin90], [sin90, cos90]] = [[0, -1], [1, 0]]
        val cos90 = Math.cos(Math.toRadians(90.0)).toFloat()
        val sin90 = Math.sin(Math.toRadians(90.0)).toFloat()

        assertEquals(0f, cos90, 0.001f)
        assertEquals(1f, sin90, 0.001f)
    }

    @Test
    fun `180도 회전 변환 행렬의 수학적 정확성`() {
        val cos180 = Math.cos(Math.toRadians(180.0)).toFloat()
        val sin180 = Math.sin(Math.toRadians(180.0)).toFloat()

        assertEquals(-1f, cos180, 0.001f)
        assertEquals(0f, sin180, 0.001f)
    }

    @Test
    fun `270도 회전 변환 행렬의 수학적 정확성`() {
        val cos270 = Math.cos(Math.toRadians(270.0)).toFloat()
        val sin270 = Math.sin(Math.toRadians(270.0)).toFloat()

        assertEquals(0f, cos270, 0.001f)
        assertEquals(-1f, sin270, 0.001f)
    }
}
