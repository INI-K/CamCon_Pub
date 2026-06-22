package com.inik.camcon.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FilmEdit] 순수 로직 검증(기본값/isNeutral 조합).
 */
class FilmEditTest {

    @Test
    fun `기본 강도는 1f 조정은 중립`() {
        val edit = FilmEdit(lutId = "a.cube")
        assertEquals(1f, edit.intensity, 0f)
        assertTrue(edit.adjustments.isNeutral)
    }

    @Test
    fun `LUT 적용+강도 1 이면 isNeutral false`() {
        assertFalse(FilmEdit(lutId = "a.cube", intensity = 1f).isNeutral)
    }

    @Test
    fun `lutId 빈 문자열이고 조정 중립이면 isNeutral true`() {
        assertTrue(FilmEdit(lutId = "", intensity = 1f).isNeutral)
    }

    @Test
    fun `강도 0 이고 조정 중립이면 isNeutral true`() {
        assertTrue(FilmEdit(lutId = "a.cube", intensity = 0f).isNeutral)
    }

    @Test
    fun `LUT 없어도 조정이 있으면 isNeutral false`() {
        val edit = FilmEdit(
            lutId = "",
            intensity = 1f,
            adjustments = FilmAdjustments(saturation = 50f)
        )
        assertFalse(edit.isNeutral)
    }
}
