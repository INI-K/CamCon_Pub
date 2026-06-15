package com.inik.camcon.domain.model

/**
 * 필름 시뮬레이션 LUT 메타데이터(YahiaAngelo/Film-Luts 카탈로그 기반, MIT).
 *
 * @param id 안정 키. assets 상대 경로(.cube)를 그대로 사용한다. 설정에 저장되는 selectedFilmLutId 값.
 * @param name 표시 이름. 예: "Kodak Tri-X 400".
 * @param category 표시 카테고리. 예: "Bw", "Negative New".
 * @param assetPath AssetManager 경로. 예: "luts/bw/kodak_tri-x_400.cube".
 */
data class FilmLut(
    val id: String,
    val name: String,
    val category: String,
    val assetPath: String
)
