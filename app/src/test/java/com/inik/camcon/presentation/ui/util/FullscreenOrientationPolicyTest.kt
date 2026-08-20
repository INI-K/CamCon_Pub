package com.inik.camcon.presentation.ui.util

import com.inik.camcon.presentation.ui.util.FullscreenOrientation.LANDSCAPE
import com.inik.camcon.presentation.ui.util.FullscreenOrientation.PORTRAIT
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 전체화면 방향 결정 규칙 테스트.
 *
 * 안드로이드 의존이 없는 순수 함수라 JVM 에서 그대로 돈다
 * (`presentation/util/FocusTapMapperTest` 와 동일한 패턴).
 *
 * 이 규칙이 지켜야 하는 것 3가지:
 * 1. 라이브뷰는 비율과 무관하게 가로 (프레임이 가로다)
 * 2. 사진은 비율을 따른다 (세로컷이 가로 화면에서 폭의 34%만 쓰던 문제)
 * 3. 판정 불가 구간에서 방향이 흔들리지 않는다 (컷마다 화면이 튀는 것 방지)
 */
class FullscreenOrientationPolicyTest {

    @Test
    fun `라이브뷰는 세로 비율이어도 가로다`() {
        assertEquals(
            LANDSCAPE,
            FullscreenOrientationPolicy.resolve(
                isLiveView = true, photoAspectRatio = 0.66f, previous = PORTRAIT
            )
        )
    }

    @Test
    fun `세로 사진은 세로로 전환된다`() {
        // 2:3 세로컷
        assertEquals(
            PORTRAIT,
            FullscreenOrientationPolicy.resolve(
                isLiveView = false, photoAspectRatio = 2f / 3f, previous = LANDSCAPE
            )
        )
    }

    @Test
    fun `가로 사진은 가로로 전환된다`() {
        // 3:2 가로컷
        assertEquals(
            LANDSCAPE,
            FullscreenOrientationPolicy.resolve(
                isLiveView = false, photoAspectRatio = 1.5f, previous = PORTRAIT
            )
        )
    }

    @Test
    fun `비율을 아직 모르면 직전 방향을 유지한다`() {
        // 진입 직후 디코딩 전. 여기서 기본값으로 튀면 화면이 한 번 회전했다 돌아온다.
        assertEquals(
            LANDSCAPE,
            FullscreenOrientationPolicy.resolve(false, null, LANDSCAPE)
        )
        assertEquals(
            PORTRAIT,
            FullscreenOrientationPolicy.resolve(false, null, PORTRAIT)
        )
    }

    @Test
    fun `정사각에 가까우면 직전 방향을 유지한다`() {
        // 데드밴드 안쪽(1/1.02 ~ 1.02)
        assertEquals(PORTRAIT, FullscreenOrientationPolicy.resolve(false, 1.01f, PORTRAIT))
        assertEquals(LANDSCAPE, FullscreenOrientationPolicy.resolve(false, 0.99f, LANDSCAPE))
        assertEquals(PORTRAIT, FullscreenOrientationPolicy.resolve(false, 1.0f, PORTRAIT))
        assertEquals(LANDSCAPE, FullscreenOrientationPolicy.resolve(false, 1.0f, LANDSCAPE))
    }

    @Test
    fun `데드밴드를 벗어나면 즉시 전환한다`() {
        assertEquals(LANDSCAPE, FullscreenOrientationPolicy.resolve(false, 1.03f, PORTRAIT))
        assertEquals(PORTRAIT, FullscreenOrientationPolicy.resolve(false, 0.97f, LANDSCAPE))
    }

    @Test
    fun `잘못된 비율값은 방향을 흔들지 않는다`() {
        // CapturedPhoto.width/height 가 0 하드코딩인 경로가 있어 0 이 흘러들 수 있다.
        for (bad in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(
                "비율 $bad 는 판정 불가로 취급해야 한다",
                LANDSCAPE,
                FullscreenOrientationPolicy.resolve(false, bad, LANDSCAPE)
            )
            assertEquals(
                "비율 $bad 는 판정 불가로 취급해야 한다",
                PORTRAIT,
                FullscreenOrientationPolicy.resolve(false, bad, PORTRAIT)
            )
        }
    }

    @Test
    fun `데드밴드 안에서 값이 오가도 전환이 일어나지 않는다`() {
        // 회귀 방어: 1.0 기준 단순 비교였다면 아래 순열에서 매번 방향이 뒤집힌다.
        var current = LANDSCAPE
        for (ratio in listOf(1.015f, 0.985f, 1.015f, 0.99f, 1.01f)) {
            current = FullscreenOrientationPolicy.resolve(false, ratio, current)
        }
        assertEquals("데드밴드 안 값만 들어오면 최초 방향이 유지되어야 한다", LANDSCAPE, current)
    }

    @Test
    fun `라이브뷰가 꺼지면 마지막 사진 비율을 따른다`() {
        // 라이브뷰 종료 직후 수신 사진 표시로 넘어가는 경로
        val afterLiveView = FullscreenOrientationPolicy.resolve(
            isLiveView = false, photoAspectRatio = 0.75f, previous = LANDSCAPE
        )
        assertEquals(PORTRAIT, afterLiveView)
    }
}
