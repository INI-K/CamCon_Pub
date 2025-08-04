package com.inik.camcon.utils

import android.util.Log
import com.inik.camcon.domain.model.ImageFormat
import com.inik.camcon.domain.model.SubscriptionTier

/**
 * 구독 기능 관련 유틸리티
 */
object SubscriptionUtils {

    private const val TAG = "SubscriptionUtils"

    /**
     * 구독 등급에 따라 지원되는 이미지 포맷 반환
     */
    fun getSupportedImageFormats(tier: SubscriptionTier): List<ImageFormat> {
        val formats = when (tier) {
            SubscriptionTier.FREE -> listOf(ImageFormat.JPG, ImageFormat.JPEG)
            SubscriptionTier.BASIC -> listOf(
                ImageFormat.JPG,
                ImageFormat.JPEG,
                ImageFormat.PNG
            )

            SubscriptionTier.PRO -> ImageFormat.values().toList()
        }
        Log.d(TAG, "티어별 지원 포맷 - $tier: ${formats.joinToString(", ")}")
        return formats
    }

    /**
     * 특정 이미지 포맷이 구독 등급에서 지원되는지 확인
     */
    fun isFormatSupported(format: ImageFormat, tier: SubscriptionTier): Boolean {
        val supported = getSupportedImageFormats(tier).contains(format)
        Log.d(TAG, "포맷 지원 확인 - $format: ${if (supported) "지원됨" else "미지원"} (티어: $tier)")
        return supported
    }

    /**
     * 파일 확장자로부터 이미지 포맷 추출
     */
    fun getImageFormatFromExtension(extension: String): ImageFormat? {
        val format = ImageFormat.values().find {
            it.extension.equals(extension, ignoreCase = true)
        }
        Log.d(TAG, "확장자 → 포맷 변환: $extension → ${format ?: "지원되지 않는 포맷"}")
        return format
    }

    /**
     * 구독 등급에 따른 기능 제한 확인
     */
    fun canUseFeature(tier: SubscriptionTier, feature: String): Boolean {
        val canUse = when (feature) {
            "raw_processing" -> tier == SubscriptionTier.PRO
            "png_export" -> tier != SubscriptionTier.FREE
            "webp_export" -> tier == SubscriptionTier.PRO
            "advanced_filters" -> tier == SubscriptionTier.PRO
            "batch_processing" -> tier != SubscriptionTier.FREE
            else -> true // 기본 기능은 모든 등급에서 사용 가능
        }
        Log.i(TAG, "기능 사용 권한 확인 - $feature: ${if (canUse) "사용 가능" else "제한됨"} (티어: $tier)")
        return canUse
    }
}