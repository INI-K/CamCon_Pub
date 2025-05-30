package com.inik.camcon.domain.model

data class CameraPhoto(
    val path: String,
    val name: String,
    val size: Long,
    val date: Long,
    val width: Int = 0,
    val height: Int = 0,
    val thumbnailPath: String? = null
)