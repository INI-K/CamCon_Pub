package com.inik.camcon.domain.usecase

import com.inik.camcon.domain.model.ImageFormat
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.utils.SubscriptionUtils
import javax.inject.Inject

/**
 * 이미지 포맷 검증 UseCase
 */
class ValidateImageFormatUseCase @Inject constructor(
    private val getSubscriptionUseCase: GetSubscriptionUseCase
) {

    /**
     * 파일 경로의 이미지 포맷이 현재 구독에서 지원되는지 확인
     */
    suspend fun isFormatSupported(filePath: String): Boolean {
        val extension = filePath.substringAfterLast('.', "")
        val format = SubscriptionUtils.getImageFormatFromExtension(extension)
            ?: return false

        val subscription = getSubscriptionUseCase.invoke()
        var currentTier = SubscriptionTier.FREE

        subscription.collect { sub ->
            currentTier = sub.tier
            return@collect
        }

        return SubscriptionUtils.isFormatSupported(format, currentTier)
    }

    /**
     * 특정 구독 등급에서 포맷이 지원되는지 확인
     */
    fun isFormatSupportedForTier(format: ImageFormat, tier: SubscriptionTier): Boolean {
        return SubscriptionUtils.isFormatSupported(format, tier)
    }

    /**
     * 현재 구독에서 지원되는 모든 포맷 반환
     */
    suspend fun getSupportedFormats(): List<ImageFormat> {
        val subscription = getSubscriptionUseCase.invoke()
        var currentTier = SubscriptionTier.FREE

        subscription.collect { sub ->
            currentTier = sub.tier
            return@collect
        }

        return SubscriptionUtils.getSupportedImageFormats(currentTier)
    }
}