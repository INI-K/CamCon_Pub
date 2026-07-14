package com.inik.camcon.presentation.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * V2 텍스트/서피스 토큰의 WCAG 대비 회귀 테스트(순수 JVM).
 *
 * 디자인 감사에서 [TextTertiary] 를 `0xFF7E848E` → `0xFF8F95A0` 로 올려 Surface4 대비 AA(4.5:1)를
 * 통과시켰다(design_fix_workorder.md §확정설계결정 2). 이 계약을 코드로 고정한다:
 *
 *  (a) [TextTertiary] vs Surface0~Surface4 전부 ≥ 4.5:1 (본문 최소 대비 AA).
 *  (b) [TextSecondaryV2]/[TextPrimaryV2] vs Surface4(최연) ≥ 4.5:1.
 *  (c) 3단계 텍스트 위계 보존: L(Tertiary) < L(Secondary) < L(Primary).
 *
 * 상대휘도·대비비는 실제 Compose 토큰의 sRGB 성분([Color.red]/[green]/[blue], 순수 계산)에서
 * 직접 산출한다 — Color.kt 의 상수가 회귀하면 이 테스트가 깨진다.
 */
class ColorContrastTest {

    /** WCAG AA 본문 최소 대비. */
    private val aaBody = 4.5

    private val surfaces = listOf(
        "Surface0" to Surface0,
        "Surface1" to Surface1,
        "Surface2" to Surface2,
        "Surface3" to Surface3,
        "Surface4" to Surface4
    )

    // ---- WCAG 2.1 상대휘도·대비비 ----

    private fun linearize(channel: Float): Double {
        val c = channel.toDouble()
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(color: Color): Double =
        0.2126 * linearize(color.red) +
                0.7152 * linearize(color.green) +
                0.0722 * linearize(color.blue)

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    @Test
    fun `TextTertiary는 Surface0부터 Surface4까지 전부 AA(4_5) 이상`() {
        surfaces.forEach { (name, surface) ->
            val ratio = contrast(TextTertiary, surface)
            assertTrue(
                "TextTertiary vs $name 대비 $ratio 는 ${aaBody}:1 이상이어야 함",
                ratio >= aaBody
            )
        }
    }

    @Test
    fun `TextSecondaryV2와 TextPrimaryV2는 최연 서피스(Surface4)에서도 AA 이상`() {
        val secondary = contrast(TextSecondaryV2, Surface4)
        val primary = contrast(TextPrimaryV2, Surface4)
        assertTrue(
            "TextSecondaryV2 vs Surface4 대비 $secondary 는 ${aaBody}:1 이상이어야 함",
            secondary >= aaBody
        )
        assertTrue(
            "TextPrimaryV2 vs Surface4 대비 $primary 는 ${aaBody}:1 이상이어야 함",
            primary >= aaBody
        )
    }

    @Test
    fun `텍스트 3단계 휘도 위계가 보존된다`() {
        val tertiary = luminance(TextTertiary)
        val secondary = luminance(TextSecondaryV2)
        val primary = luminance(TextPrimaryV2)
        assertTrue(
            "위계 붕괴: L(Tertiary)=$tertiary < L(Secondary)=$secondary 이어야 함",
            tertiary < secondary
        )
        assertTrue(
            "위계 붕괴: L(Secondary)=$secondary < L(Primary)=$primary 이어야 함",
            secondary < primary
        )
    }
}
