package com.inik.camcon.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V2 타이포그래피 사다리 회귀 테스트.
 *
 * 2026-08-01 UI 전수 감사에서 확인된 결함을 고정한다:
 *  - Display 티어가 정의만 되고 소비되지 않아 화면마다 Hero 슬롯이 없었다(13/14 표면).
 *  - M3 alias 매핑이 붕괴해 titleSmall(12sp)이 bodySmall(13sp)보다 작은 위계 역전이 있었다.
 *  - 변동 수치가 비례폭으로 렌더돼 자릿수마다 폭이 튀었다(fontFeatureSettings 0건).
 *
 * 가이드: docs/DESIGN_SYSTEM_V2.md §2
 */
class TypographyScaleTest {

    @Test
    fun `타이포 사다리가 단조 감소한다`() {
        val ladder = listOf(
            "DisplayL" to DisplayL,
            "DisplayM" to DisplayM,
            "HeadingXL" to HeadingXL,
            "HeadingL" to HeadingL,
            "HeadingM" to HeadingM,
            "Body" to Body,
            "BodySmall" to BodySmall,
            "Caption" to Caption,
            "Micro" to Micro
        )
        ladder.zipWithNext { (aName, a), (bName, b) ->
            assertTrue(
                "$aName(${a.fontSize})는 $bName(${b.fontSize})보다 커야 한다",
                a.fontSize.value > b.fontSize.value
            )
        }
    }

    @Test
    fun `같은 크기 슬롯은 무게로 갈린다`() {
        // BodyLarge(리드 문장)와 HeadingM(카드 헤더)은 둘 다 16sp다. 크기가 아니라 무게가 위계를 만든다.
        assertEquals(HeadingM.fontSize, BodyLarge.fontSize)
        assertTrue(
            "HeadingM(${HeadingM.fontWeight})가 BodyLarge(${BodyLarge.fontWeight})보다 굵어야 한다",
            HeadingM.fontWeight!!.weight > BodyLarge.fontWeight!!.weight
        )
        // HeadingS(카드·행 제목)도 Body와 같은 14sp이며 무게로 갈린다.
        assertEquals(Body.fontSize, HeadingS.fontSize)
        assertTrue(
            "HeadingS(${HeadingS.fontWeight})가 Body(${Body.fontWeight})보다 굵어야 한다",
            HeadingS.fontWeight!!.weight > Body.fontWeight!!.weight
        )
    }

    @Test
    fun `M3 titleSmall이 bodySmall보다 크거나 같다`() {
        // 이전 매핑(titleSmall = Caption 12sp)은 제목이 본문보다 작은 위계 역전이었다.
        assertTrue(
            "titleSmall=${Typography.titleSmall.fontSize} / bodySmall=${Typography.bodySmall.fontSize}",
            Typography.titleSmall.fontSize.value >= Typography.bodySmall.fontSize.value
        )
    }

    @Test
    fun `M3 bodyLarge와 bodyMedium이 서로 다른 크기다`() {
        // 둘 다 Body(14sp)로 붕괴해 있어 크기 차가 0sp였다.
        assertTrue(
            "bodyLarge=${Typography.bodyLarge.fontSize} / bodyMedium=${Typography.bodyMedium.fontSize}",
            Typography.bodyLarge.fontSize.value > Typography.bodyMedium.fontSize.value
        )
    }

    @Test
    fun `M3 titleLarge가 titleMedium보다 크다`() {
        // titleLarge·titleMedium·headlineSmall 3단계가 전부 HeadingM(16sp)으로 붕괴해 있었다.
        assertTrue(
            "titleLarge=${Typography.titleLarge.fontSize} / titleMedium=${Typography.titleMedium.fontSize}",
            Typography.titleLarge.fontSize.value > Typography.titleMedium.fontSize.value
        )
    }

    @Test
    fun `Hero 티어는 본문의 2_5배 이상이다`() {
        listOf("DisplayL" to DisplayL, "DisplayNum" to DisplayNum, "MonoHero" to MonoHero)
            .forEach { (name, style) ->
                val ratio = style.fontSize.value / BodySmall.fontSize.value
                assertTrue(
                    "$name(${style.fontSize}) / BodySmall(${BodySmall.fontSize}) = $ratio 는 2.5 이상이어야 한다",
                    ratio >= 2.5f
                )
            }
    }

    @Test
    fun `수치 슬롯은 전부 탭형 숫자다`() {
        // 자릿수가 바뀌어도 폭이 고정돼야 계측기로 읽힌다.
        listOf(
            "MonoHero" to MonoHero,
            "MonoReadout" to MonoReadout,
            "MonoNumeric" to MonoNumeric,
            "MonoMicro" to MonoMicro,
            "DisplayNum" to DisplayNum
        ).forEach { (name, style) ->
            assertEquals("$name 에 tnum 이 적용돼야 한다", "tnum", style.fontFeatureSettings)
        }
    }

    @Test
    fun `산문 슬롯에는 tnum을 걸지 않는다`() {
        // tnum 은 수치 정렬용이다. 본문까지 걸면 비례폭 가독성이 손상된다.
        listOf("Body" to Body, "BodySmall" to BodySmall, "HeadingL" to HeadingL, "BodyLarge" to BodyLarge)
            .forEach { (name, style) ->
                assertEquals("$name 에는 fontFeatureSettings 가 없어야 한다", null, style.fontFeatureSettings)
            }
    }
}
