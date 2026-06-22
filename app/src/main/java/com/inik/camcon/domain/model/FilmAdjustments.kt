package com.inik.camcon.domain.model

/**
 * per-photo 필름 편집의 색 조정 8종 값 객체.
 *
 * 모든 필드는 **UI 단위**(슬라이더 표시값)로 저장하며, 중립(=조정 없음) 기본값은 모두 `0f` 이다.
 * GPU 필터 파라미터로의 매핑(예: 색온도 ±100 → 켈빈, 노출 ±2 → EV)은 data 레이어의
 * `FilmAdjustmentProcessor` 가 단일 지점에서 수행한다. 도메인은 사람이 이해하는 단위만 다룬다.
 *
 * 양방향 슬라이더(중앙=0)인 6종: [exposure]/[temperature]/[contrast]/[shadows]/[highlights]/[saturation].
 * 단방향 슬라이더(0~)인 2종: [grain]/[chromaticAberration].
 *
 * @param exposure 노출. 범위 [EXPOSURE_MIN]..[EXPOSURE_MAX] (EV 스톱). 0 = 변화 없음, +2 = 2스톱 증가.
 * @param temperature 색온도. 범위 [TEMPERATURE_MIN]..[TEMPERATURE_MAX]. 음수 = 차갑게(파랑), 양수 = 따뜻하게(주황).
 * @param contrast 대비. 범위 [BIPOLAR_MIN]..[BIPOLAR_MAX]. 음수 = 평탄, 양수 = 강한 대비.
 * @param shadows 섀도. 범위 [BIPOLAR_MIN]..[BIPOLAR_MAX]. 양수 = 어두운 영역 밝게 들어올림.
 * @param highlights 하이라이트. 범위 [BIPOLAR_MIN]..[BIPOLAR_MAX]. 음수 = 밝은 영역 눌러 디테일 보존.
 * @param saturation 채도. 범위 [BIPOLAR_MIN]..[BIPOLAR_MAX]. -100 = 흑백, +100 = 진한 채도.
 * @param grain 필름 그레인 강도. 범위 [UNIPOLAR_MIN]..[UNIPOLAR_MAX]. 0 = 없음, 1 = 최대.
 * @param chromaticAberration 색수차(RGB 채널 오프셋) 강도. 범위 [UNIPOLAR_MIN]..[UNIPOLAR_MAX]. 0 = 없음.
 */
data class FilmAdjustments(
    val exposure: Float = NEUTRAL,
    val temperature: Float = NEUTRAL,
    val contrast: Float = NEUTRAL,
    val shadows: Float = NEUTRAL,
    val highlights: Float = NEUTRAL,
    val saturation: Float = NEUTRAL,
    val grain: Float = NEUTRAL,
    val chromaticAberration: Float = NEUTRAL
) {

    /** 조정이 전혀 없는 중립 상태인지 여부(미세 부동소수 오차 허용). */
    val isNeutral: Boolean
        get() = exposure.isNeutralValue() &&
                temperature.isNeutralValue() &&
                contrast.isNeutralValue() &&
                shadows.isNeutralValue() &&
                highlights.isNeutralValue() &&
                saturation.isNeutralValue() &&
                grain.isNeutralValue() &&
                chromaticAberration.isNeutralValue()

    /**
     * 각 필드를 정의된 범위로 clamp 한 사본을 반환한다.
     * 외부(슬라이더/저장 복원)에서 들어온 범위 밖 값으로부터 색 엔진을 보호한다.
     */
    fun normalized(): FilmAdjustments = FilmAdjustments(
        exposure = exposure.coerceIn(EXPOSURE_MIN, EXPOSURE_MAX),
        temperature = temperature.coerceIn(TEMPERATURE_MIN, TEMPERATURE_MAX),
        contrast = contrast.coerceIn(BIPOLAR_MIN, BIPOLAR_MAX),
        shadows = shadows.coerceIn(BIPOLAR_MIN, BIPOLAR_MAX),
        highlights = highlights.coerceIn(BIPOLAR_MIN, BIPOLAR_MAX),
        saturation = saturation.coerceIn(BIPOLAR_MIN, BIPOLAR_MAX),
        grain = grain.coerceIn(UNIPOLAR_MIN, UNIPOLAR_MAX),
        chromaticAberration = chromaticAberration.coerceIn(UNIPOLAR_MIN, UNIPOLAR_MAX)
    )

    companion object {
        /** 모든 양방향 필드의 중립값. */
        const val NEUTRAL = 0f

        /** 중립 판정 허용 오차. */
        const val NEUTRAL_EPS = 1e-4f

        // 노출(EV 스톱)
        const val EXPOSURE_MIN = -2f
        const val EXPOSURE_MAX = 2f

        // 색온도(UI 단위 ±100)
        const val TEMPERATURE_MIN = -100f
        const val TEMPERATURE_MAX = 100f

        // 양방향 조정(대비/섀도/하이라이트/채도): ±100, 중앙 0
        const val BIPOLAR_MIN = -100f
        const val BIPOLAR_MAX = 100f

        // 단방향 조정(그레인/색수차): 0..1
        const val UNIPOLAR_MIN = 0f
        const val UNIPOLAR_MAX = 1f

        /** 모든 조정이 중립인 기본 인스턴스. */
        val NEUTRAL_ADJUSTMENTS = FilmAdjustments()

        private fun Float.isNeutralValue(): Boolean = kotlin.math.abs(this) <= NEUTRAL_EPS
    }
}
