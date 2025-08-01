package com.inik.camcon.utils

import com.inik.camcon.domain.model.ImageFormat
import com.inik.camcon.domain.model.SubscriptionTier

/**
 * 구독 기능 관련 유틸리티
 */
object SubscriptionUtils {

    /**
     * 구독 등급에 따라 지원되는 이미지 포맷 반환
     */
    fun getSupportedImageFormats(tier: SubscriptionTier): List<ImageFormat> {
        return when (tier) {
            SubscriptionTier.FREE -> listOf(ImageFormat.JPG, ImageFormat.JPEG)
            SubscriptionTier.BASIC -> listOf(
                ImageFormat.JPG,
                ImageFormat.JPEG,
                ImageFormat.PNG
            )

            SubscriptionTier.PRO -> ImageFormat.values().toList()
        }
    }

    /**
     * 특정 이미지 포맷이 구독 등급에서 지원되는지 확인
     */
    fun isFormatSupported(format: ImageFormat, tier: SubscriptionTier): Boolean {
        return getSupportedImageFormats(tier).contains(format)
    }

    /**
     * 파일 확장자로부터 이미지 포맷 추출
     */
    fun getImageFormatFromExtension(extension: String): ImageFormat? {
        return ImageFormat.values().find {
            it.extension.equals(extension, ignoreCase = true)
        }
    }

    /**
     * 구독 등급에 따른 기능 제한 확인
     */
    fun canUseFeature(tier: SubscriptionTier, feature: String): Boolean {
        return when (feature) {
            "raw_processing" -> tier == SubscriptionTier.PRO
            "png_export" -> tier != SubscriptionTier.FREE
            "webp_export" -> tier == SubscriptionTier.PRO
            "advanced_filters" -> tier == SubscriptionTier.PRO
            "batch_processing" -> tier != SubscriptionTier.FREE
            else -> true // 기본 기능은 모든 등급에서 사용 가능
        }
    }
}