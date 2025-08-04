package com.inik.camcon.domain.usecase

import android.util.Log
import com.inik.camcon.domain.model.ImageFormat
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.utils.SubscriptionUtils
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * 이미지 포맷 검증 UseCase
 */
class ValidateImageFormatUseCase @Inject constructor(
    private val getSubscriptionUseCase: GetSubscriptionUseCase
) {

    companion object {
        private const val TAG = "ValidateImageFormat"
    }

    /**
     * 파일 경로의 이미지 포맷이 현재 구독에서 지원되는지 확인
     */
    suspend fun isFormatSupported(filePath: String): Boolean {
        val extension = filePath.substringAfterLast('.', "")
        val format = SubscriptionUtils.getImageFormatFromExtension(extension)
            ?: return false

        // 현재 사용자의 구독 티어 가져오기
        val currentTier = getSubscriptionUseCase.getSubscriptionTier().first()
        Log.d(TAG, "🎯 이미지 포맷 검증 - 파일: $filePath, 포맷: $format, 사용자 티어: $currentTier")

        val isSupported = SubscriptionUtils.isFormatSupported(format, currentTier)
        Log.i(TAG, "📋 포맷 지원 여부: $format → ${if (isSupported) "지원됨" else "미지원"} (티어: $currentTier)")

        return isSupported
    }

    /**
     * 특정 구독 등급에서 포맷이 지원되는지 확인
     */
    fun isFormatSupportedForTier(format: ImageFormat, tier: SubscriptionTier): Boolean {
        Log.d(TAG, "🔍 특정 티어 포맷 지원 확인 - 포맷: $format, 티어: $tier")
        return SubscriptionUtils.isFormatSupported(format, tier)
    }

    /**
     * 현재 구독에서 지원되는 모든 포맷 반환
     */
    suspend fun getSupportedFormats(): List<ImageFormat> {
        val currentTier = getSubscriptionUseCase.getSubscriptionTier().first()
        val supportedFormats = SubscriptionUtils.getSupportedImageFormats(currentTier)
        Log.i(TAG, "📋 현재 지원되는 포맷 목록 (티어: $currentTier): ${supportedFormats.joinToString(", ")}")
        return supportedFormats
    }
}