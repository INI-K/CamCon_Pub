package com.inik.camcon.presentation.ui.screens.components

import com.inik.camcon.domain.model.LiveViewQuality
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 인-LV 화질 순환 헬퍼 [LiveViewQuality.next] 의 순환 불변식 단위 테스트.
 *
 * 설계서 §10 (camcon-tdd-tester 위임): `next()` 순환 SPEED→BALANCED→QUALITY→SPEED 라운드트립.
 *
 * 순수 함수라 협력자/디스패처 없이 검증한다. 아이콘([LiveViewQuality.icon])/라벨([LiveViewQuality.shortLabelRes])은
 * Compose ImageVector / @StringRes 의존이라 단위테스트 가치가 낮아 제외(렌더는 계측/수동 검증 영역).
 */
class LiveViewQualityIconsTest {

    @Test
    fun `next - SPEED 의 다음은 BALANCED`() {
        assertEquals(LiveViewQuality.BALANCED, LiveViewQuality.SPEED.next())
    }

    @Test
    fun `next - BALANCED 의 다음은 QUALITY`() {
        assertEquals(LiveViewQuality.QUALITY, LiveViewQuality.BALANCED.next())
    }

    @Test
    fun `next - QUALITY 의 다음은 SPEED (순환 닫힘)`() {
        assertEquals(LiveViewQuality.SPEED, LiveViewQuality.QUALITY.next())
    }

    @Test
    fun `next - 세 번 순환하면 시작값으로 라운드트립`() {
        val start = LiveViewQuality.SPEED
        val afterThree = start.next().next().next()
        assertEquals(start, afterThree)
    }

    @Test
    fun `next - 모든 시작값에서 3회 순환 라운드트립`() {
        LiveViewQuality.values().forEach { start ->
            assertEquals(
                "$start 에서 3회 순환 시 자기 자신으로 복귀해야 함",
                start,
                start.next().next().next()
            )
        }
    }

    @Test
    fun `next - 연속 순환 전체 시퀀스 검증`() {
        // SPEED→BALANCED→QUALITY→SPEED→BALANCED ... 순서 고정
        val seq = generateSequence(LiveViewQuality.SPEED) { it.next() }.take(7).toList()
        assertEquals(
            listOf(
                LiveViewQuality.SPEED,
                LiveViewQuality.BALANCED,
                LiveViewQuality.QUALITY,
                LiveViewQuality.SPEED,
                LiveViewQuality.BALANCED,
                LiveViewQuality.QUALITY,
                LiveViewQuality.SPEED
            ),
            seq
        )
    }
}
