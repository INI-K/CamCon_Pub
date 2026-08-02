package com.inik.camcon.presentation.ui.util

/**
 * 전체화면 표시 방향. `ActivityInfo` 상수 매핑은 호출부가 담당한다
 * (안드로이드 의존을 두지 않아 JVM 단위테스트가 가능하다).
 */
enum class FullscreenOrientation { LANDSCAPE, PORTRAIT }

/**
 * 전체화면 방향 결정 규칙.
 *
 * - 라이브뷰는 항상 가로다. 카메라 라이브뷰 프레임이 가로이므로 세로로 두면 크게 낭비된다.
 * - 사진은 표시 비율을 따른다. 세로컷을 가로 화면에 Fit 하면 화면 폭의 약 34%만 쓰게 된다.
 * - 비율 판정 전(null)이나 정사각 근처(데드밴드)에서는 **직전 결정을 유지한다**.
 *   그렇지 않으면 컷이 바뀔 때마다 화면이 튀고, 정사각에 가까운 사진에서 가로↔세로를 왕복한다.
 */
object FullscreenOrientationPolicy {

    /**
     * 정사각 근처 오분류 방지 폭.
     *
     * 비율은 Coil 이 다운샘플한 비트맵의 정수 치수에서 나오므로 반올림 오차가 섞인다.
     * 1.0 을 기준으로 딱 잘라 판정하면 거의 정사각인 컷에서 가로↔세로가 왕복한다.
     */
    const val DEADBAND = 1.02f

    /**
     * @param isLiveView 라이브뷰를 표시 중인가. true 면 비율과 무관하게 가로.
     * @param photoAspectRatio 표시할 사진의 width/height. 아직 모르면 null.
     * @param previous 직전에 결정된 방향. 판정 불가 구간에서 이 값을 유지한다.
     */
    fun resolve(
        isLiveView: Boolean,
        photoAspectRatio: Float?,
        previous: FullscreenOrientation
    ): FullscreenOrientation {
        if (isLiveView) return FullscreenOrientation.LANDSCAPE

        val ratio = photoAspectRatio ?: return previous
        // 0·음수·NaN·Infinity 는 비율로 성립하지 않는다. 방향을 흔들지 말고 유지한다.
        if (!ratio.isFinite() || ratio <= 0f) return previous

        return when {
            ratio > DEADBAND -> FullscreenOrientation.LANDSCAPE
            ratio < 1f / DEADBAND -> FullscreenOrientation.PORTRAIT
            else -> previous
        }
    }
}
