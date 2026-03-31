package com.inik.camcon.domain.model.focus

data class FocusConfig(
    val mode: String,
    val availableModes: List<String> = emptyList()
)

data class FocusArea(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)
