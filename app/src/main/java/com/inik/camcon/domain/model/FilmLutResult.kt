package com.inik.camcon.domain.model

/**
 * 필름 LUT 적용·저장 결과.
 */
data class FilmLutResult(
    val outputPath: String,
    val width: Int,
    val height: Int,
    val lutId: String
)
