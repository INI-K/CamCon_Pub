package com.inik.camcon.domain.model

/**
 * 지원되는 이미지 포맷
 */
enum class ImageFormat(val extension: String, val mimeType: String) {
    JPG("jpg", "image/jpeg"),
    JPEG("jpeg", "image/jpeg"),
}