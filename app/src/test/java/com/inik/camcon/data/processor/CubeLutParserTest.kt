package com.inik.camcon.data.processor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CubeLutParser] 와 [sampleTrilinear] 순수 로직 검증.
 * Android 의존성이 없으므로 일반 JUnit 으로 실행된다.
 */
class CubeLutParserTest {

    /** size=2 항등(identity) LUT. .cube 순서(R 가장 빠름 → G → B). */
    private val identity2 = """
        TITLE "identity"
        # comment line
        DOMAIN_MIN 0.0 0.0 0.0
        DOMAIN_MAX 1.0 1.0 1.0
        LUT_3D_SIZE 2
        0 0 0
        1 0 0
        0 1 0
        1 1 0
        0 0 1
        1 0 1
        0 1 1
        1 1 1
    """.trimIndent()

    @Test
    fun `parses size and full data of a valid 3D LUT`() {
        val lut = CubeLutParser.parse(identity2)
        assertNotNull(lut)
        requireNotNull(lut)
        assertEquals(2, lut.size)
        assertEquals(2 * 2 * 2 * 3, lut.data.size)
        // 첫 격자점 (0,0,0) = 0, 마지막 (1,1,1) = 1
        assertEquals(0f, lut.data[0], 1e-6f)
        assertEquals(1f, lut.data[lut.data.size - 1], 1e-6f)
    }

    @Test
    fun `skips comment TITLE and DOMAIN lines`() {
        val lut = CubeLutParser.parse(identity2)
        requireNotNull(lut)
        // (1,0,0) 격자점의 R 채널 = 1 (인덱스 ((0*2+0)*2+1)*3 = 3)
        assertEquals(1f, lut.data[3], 1e-6f)
    }

    @Test
    fun `returns null when LUT_3D_SIZE is missing`() {
        val text = "TITLE \"x\"\n0 0 0\n1 1 1"
        assertNull(CubeLutParser.parse(text))
    }

    @Test
    fun `returns null when data is insufficient for declared size`() {
        val text = "LUT_3D_SIZE 2\n0 0 0\n1 0 0" // 8개 필요하지만 2개만
        assertNull(CubeLutParser.parse(text))
    }

    @Test
    fun `rejects LUT with non-unit DOMAIN to avoid silent wrong colors`() {
        val text = """
            LUT_3D_SIZE 2
            DOMAIN_MIN 0.0 0.0 0.0
            DOMAIN_MAX 100.0 100.0 100.0
            0 0 0
            1 0 0
            0 1 0
            1 1 0
            0 0 1
            1 0 1
            0 1 1
            1 1 1
        """.trimIndent()
        assertNull(CubeLutParser.parse(text))
    }

    @Test
    fun `accepts explicit unit DOMAIN`() {
        // identity2 already declares DOMAIN 0..1 — confirm the guard does not reject valid files
        assertNotNull(CubeLutParser.parse(identity2))
    }

    @Test
    fun `identity LUT trilinear sampling returns the input color`() {
        val lut = CubeLutParser.parse(identity2)
        requireNotNull(lut)
        val out = FloatArray(3)

        lut.sampleTrilinear(0f, 0f, 0f, out)
        assertEquals(0f, out[0], 1e-5f); assertEquals(0f, out[1], 1e-5f); assertEquals(0f, out[2], 1e-5f)

        lut.sampleTrilinear(1f, 1f, 1f, out)
        assertEquals(1f, out[0], 1e-5f); assertEquals(1f, out[1], 1e-5f); assertEquals(1f, out[2], 1e-5f)

        // 선형 보간이므로 항등 LUT 에서 임의 중간값도 입력과 동일해야 한다
        lut.sampleTrilinear(0.5f, 0.25f, 0.75f, out)
        assertEquals(0.5f, out[0], 1e-5f); assertEquals(0.25f, out[1], 1e-5f); assertEquals(0.75f, out[2], 1e-5f)
    }

    @Test
    fun `sampling clamps out-of-range input into the unit cube`() {
        val lut = CubeLutParser.parse(identity2)
        requireNotNull(lut)
        val out = FloatArray(3)
        lut.sampleTrilinear(-1f, 2f, 0.5f, out)
        assertTrue(out[0] in 0f..1f && out[1] in 0f..1f && out[2] in 0f..1f)
        assertEquals(0f, out[0], 1e-5f)  // -1 → 0
        assertEquals(1f, out[1], 1e-5f)  // 2 → 1
    }
}
