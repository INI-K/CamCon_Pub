package com.inik.camcon.domain.model.mock

data class MockCameraInfo(
    val isEnabled: Boolean,
    val model: String,
    val imageCount: Int,
    val delayMs: Int,
    val details: String
)

data class MockCameraConfig(
    val manufacturer: String,
    val model: String
)
