package com.inik.camcon.domain.model

/**
 * per-photo 필름 편집 상태 한 묶음.
 *
 * @param lutId 적용할 필름 LUT id([FilmLut.id], assets 상대 경로). 빈 문자열 = LUT 미적용(조정만).
 * @param intensity LUT 적용 강도 0(원본)~1(완전 적용).
 * @param adjustments LUT 위에 얹는 색 조정 8종([FilmAdjustments]). 기본 = 중립.
 */
data class FilmEdit(
    val lutId: String,
    val intensity: Float = 1f,
    val adjustments: FilmAdjustments = FilmAdjustments()
) {
    /** LUT 강도가 0이거나 미적용이고, 조정도 전부 중립이면 편집이 없는 상태. */
    val isNeutral: Boolean
        get() = (lutId.isEmpty() || intensity <= FilmAdjustments.NEUTRAL_EPS) && adjustments.isNeutral
}
