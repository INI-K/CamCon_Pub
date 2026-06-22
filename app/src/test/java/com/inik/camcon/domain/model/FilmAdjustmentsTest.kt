package com.inik.camcon.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FilmAdjustments] 순수 로직 검증(기본값/clamp/isNeutral).
 * Android 의존성이 없으므로 일반 JUnit 으로 실행된다.
 */
class FilmAdjustmentsTest {

    @Test
    fun `기본 인스턴스는 모든 필드가 중립(0f)`() {
        val a = FilmAdjustments()
        assertEquals(0f, a.exposure, 0f)
        assertEquals(0f, a.temperature, 0f)
        assertEquals(0f, a.contrast, 0f)
        assertEquals(0f, a.shadows, 0f)
        assertEquals(0f, a.highlights, 0f)
        assertEquals(0f, a.saturation, 0f)
        assertEquals(0f, a.grain, 0f)
        assertEquals(0f, a.chromaticAberration, 0f)
    }

    @Test
    fun `기본 인스턴스는 isNeutral true`() {
        assertTrue(FilmAdjustments().isNeutral)
        assertTrue(FilmAdjustments.NEUTRAL_ADJUSTMENTS.isNeutral)
    }

    @Test
    fun `미세 부동소수 오차는 여전히 중립으로 판정`() {
        val a = FilmAdjustments(exposure = FilmAdjustments.NEUTRAL_EPS / 2f)
        assertTrue("eps 미만 변화는 중립이어야 한다", a.isNeutral)
    }

    @Test
    fun `한 필드라도 유의미하게 바뀌면 isNeutral false`() {
        assertFalse(FilmAdjustments(exposure = 1f).isNeutral)
        assertFalse(FilmAdjustments(temperature = -50f).isNeutral)
        assertFalse(FilmAdjustments(saturation = 100f).isNeutral)
        assertFalse(FilmAdjustments(grain = 0.5f).isNeutral)
        assertFalse(FilmAdjustments(chromaticAberration = 0.3f).isNeutral)
    }

    @Test
    fun `normalized 는 양방향 필드를 범위로 clamp`() {
        val a = FilmAdjustments(
            exposure = 10f,
            temperature = 999f,
            contrast = -999f,
            shadows = 500f,
            highlights = -500f,
            saturation = 12345f
        ).normalized()

        assertEquals(FilmAdjustments.EXPOSURE_MAX, a.exposure, 0f)
        assertEquals(FilmAdjustments.TEMPERATURE_MAX, a.temperature, 0f)
        assertEquals(FilmAdjustments.BIPOLAR_MIN, a.contrast, 0f)
        assertEquals(FilmAdjustments.BIPOLAR_MAX, a.shadows, 0f)
        assertEquals(FilmAdjustments.BIPOLAR_MIN, a.highlights, 0f)
        assertEquals(FilmAdjustments.BIPOLAR_MAX, a.saturation, 0f)
    }

    @Test
    fun `normalized 는 단방향 필드를 0_1 로 clamp`() {
        val a = FilmAdjustments(grain = 5f, chromaticAberration = -3f).normalized()
        assertEquals(FilmAdjustments.UNIPOLAR_MAX, a.grain, 0f)
        assertEquals(FilmAdjustments.UNIPOLAR_MIN, a.chromaticAberration, 0f)
    }

    @Test
    fun `normalized 는 음수 노출도 하한으로 clamp`() {
        val a = FilmAdjustments(exposure = -10f).normalized()
        assertEquals(FilmAdjustments.EXPOSURE_MIN, a.exposure, 0f)
    }

    @Test
    fun `범위 내 값은 normalized 가 보존`() {
        val a = FilmAdjustments(
            exposure = 1f,
            temperature = 30f,
            contrast = -40f,
            shadows = 20f,
            highlights = -10f,
            saturation = 50f,
            grain = 0.4f,
            chromaticAberration = 0.2f
        )
        assertEquals(a, a.normalized())
    }

    @Test
    fun `중립 인스턴스는 normalized 후에도 중립`() {
        assertTrue(FilmAdjustments().normalized().isNeutral)
    }
}
