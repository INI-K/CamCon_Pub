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
     * 포맷 검증 결과
     */
    data class ValidationResult(
        val isSupported: Boolean,
        val restrictionMessage: String? = null,
        val needsUpgrade: Boolean = false,
        val isRawFile: Boolean = false,
        val manufacturer: String? = null
    )

    /**
     * 파일 경로의 이미지 포맷이 현재 구독에서 지원되는지 확인
     */
    suspend fun isFormatSupported(filePath: String): Boolean {
        // RAW 파일 확인
        if (SubscriptionUtils.isRawFile(filePath)) {
            val currentTier = getSubscriptionUseCase.getSubscriptionTier().first()
            val isSupported = currentTier == SubscriptionTier.PRO ||
                    currentTier == SubscriptionTier.ADMIN ||
                    currentTier == SubscriptionTier.REFERRER
            Log.d(TAG, "🎯 RAW 파일 검증 - 파일: $filePath, 사용자 티어: $currentTier, 지원: $isSupported")
            return isSupported
        }

        // 일반 포맷 확인
        val extension = filePath.substringAfterLast('.', "")
        val format = SubscriptionUtils.getImageFormatFromExtension(extension)
            ?: return false

        val currentTier = getSubscriptionUseCase.getSubscriptionTier().first()
        val isSupported = SubscriptionUtils.isFormatSupported(format, currentTier)
        Log.i(TAG, "📋 포맷 지원 여부: $format → ${if (isSupported) "지원됨" else "미지원"} (티어: $currentTier)")

        return isSupported
    }

    /**
     * 상세한 포맷 검증 수행
     */
    suspend fun validateFormat(filePath: String): ValidationResult {
        val currentTier = getSubscriptionUseCase.getSubscriptionTier().first()

        // RAW 파일 검증
        if (SubscriptionUtils.isRawFile(filePath)) {
            val manufacturer = SubscriptionUtils.getRawFileManufacturer(filePath)
            Log.d(TAG, "🔍 RAW 파일 검증 - 파일: $filePath, 제조사: $manufacturer, 티어: $currentTier")

            if (currentTier == SubscriptionTier.PRO || currentTier == SubscriptionTier.ADMIN || currentTier == SubscriptionTier.REFERRER) {
                return ValidationResult(
                    isSupported = true,
                    isRawFile = true,
                    manufacturer = manufacturer
                )
            }

            val message = when (currentTier) {
                SubscriptionTier.FREE -> SubscriptionUtils.getRawRestrictionMessage()
                SubscriptionTier.BASIC -> SubscriptionUtils.getRawRestrictionMessageForBasic()
                else -> null
            }

            Log.w(TAG, "🚫 RAW 파일 접근 제한 - 티어: $currentTier, 제조사: $manufacturer")
            return ValidationResult(
                isSupported = false,
                restrictionMessage = message,
                needsUpgrade = true,
                isRawFile = true,
                manufacturer = manufacturer
            )
        }

        // 일반 포맷 검증
        val extension = filePath.substringAfterLast('.', "")
        val format = SubscriptionUtils.getImageFormatFromExtension(extension)
            ?: return ValidationResult(false, "지원되지 않는 파일 형식입니다.")

        val isSupported = SubscriptionUtils.isFormatSupported(format, currentTier)

        Log.d(TAG, "🔍 상세 포맷 검증 - 파일: $filePath, 포맷: $format, 티어: $currentTier, 지원: $isSupported")

        if (isSupported) {
            return ValidationResult(true)
        }

        // 기타 포맷 제한 처리
        val restrictionMessage = "현재 구독에서는 지원되지 않는 파일 형식입니다."

        return ValidationResult(
            isSupported = false,
            restrictionMessage = restrictionMessage,
            needsUpgrade = true
        )
    }

    /**
     * RAW 파일 접근 시도 검증 (특화된 메서드)
     */
    suspend fun validateRawFileAccess(filePath: String): ValidationResult {
        if (!SubscriptionUtils.isRawFile(filePath)) {
            return ValidationResult(true) // RAW 파일이 아니면 통과
        }

        val currentTier = getSubscriptionUseCase.getSubscriptionTier().first()
        val manufacturer = SubscriptionUtils.getRawFileManufacturer(filePath)
        Log.d(TAG, "🎯 RAW 파일 접근 검증 - 파일: $filePath, 제조사: $manufacturer, 티어: $currentTier")

        return when (currentTier) {
            SubscriptionTier.PRO, SubscriptionTier.ADMIN, SubscriptionTier.REFERRER -> {
                Log.i(TAG, "✅ RAW 파일 접근 허용 - 사용자 (제조사: $manufacturer)")
                ValidationResult(
                    isSupported = true,
                    isRawFile = true,
                    manufacturer = manufacturer
                )
            }
            SubscriptionTier.FREE -> {
                Log.w(TAG, "🚫 RAW 파일 접근 차단 - FREE 사용자 (제조사: $manufacturer)")
                ValidationResult(
                    isSupported = false,
                    restrictionMessage = SubscriptionUtils.getRawRestrictionMessage(),
                    needsUpgrade = true,
                    isRawFile = true,
                    manufacturer = manufacturer
                )
            }
            SubscriptionTier.BASIC -> {
                Log.w(TAG, "🚫 RAW 파일 접근 차단 - BASIC 사용자 (제조사: $manufacturer)")
                ValidationResult(
                    isSupported = false,
                    restrictionMessage = SubscriptionUtils.getRawRestrictionMessageForBasic(),
                    needsUpgrade = true,
                    isRawFile = true,
                    manufacturer = manufacturer
                )
            }
        }
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

    /**
     * 업그레이드 안내 메시지 가져오기
     */
    suspend fun getUpgradeMessage(): String {
        val currentTier = getSubscriptionUseCase.getSubscriptionTier().first()
        return SubscriptionUtils.getUpgradeMessage(currentTier)
    }

    /**
     * 지원되는 RAW 확장자 목록 반환
     */
    fun getSupportedRawExtensions(): Set<String> {
        return SubscriptionUtils.getSupportedRawExtensions()
    }
}