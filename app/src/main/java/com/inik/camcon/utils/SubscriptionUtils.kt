package com.inik.camcon.utils

import android.util.Log
import com.inik.camcon.domain.model.ImageFormat
import com.inik.camcon.domain.model.SubscriptionTier

/**
 * 구독 기능 관련 유틸리티
 */
object SubscriptionUtils {

    private const val TAG = "SubscriptionUtils"

    // 제조사별 RAW 파일 확장자 목록
    private val RAW_EXTENSIONS = setOf(
        "cr2", "cr3",           // Canon
        "nef", "nrw",           // Nikon
        "arw", "srf", "sr2",    // Sony
        "orf",                  // Olympus
        "rw2",                  // Panasonic
        "pef", "ptx",           // Pentax
        "x3f",                  // Sigma
        "raf",                  // Fujifilm
        "3fr",                  // Hasselblad
        "fff",                  // Imacon
        "dcr", "mrw",           // Minolta
        "bay",                  // Casio
        "dng",                  // Adobe Digital Negative
        "erf",                  // Epson
        "mef",                  // Mamiya
        "mos",                  // Creo Leaf
        "raw",                  // Generic RAW
        "rwl",                  // Leica
        "iiq",                  // Phase One
        "kdc",                  // Kodak
        "mdc",                  // Minolta DiMAGE
        "nco",                  // Nikon Coolpix
        "tif", "tiff"           // RAW in TIFF format (일부 카메라)
    )

    /**
     * 구독 등급에 따라 지원되는 이미지 포맷 반환
     */
    fun getSupportedImageFormats(tier: SubscriptionTier): List<ImageFormat> {
        val formats = when (tier) {
            SubscriptionTier.FREE -> listOf(ImageFormat.JPG, ImageFormat.JPEG)
            SubscriptionTier.BASIC -> listOf(ImageFormat.JPG, ImageFormat.JPEG)
            SubscriptionTier.PRO -> listOf(ImageFormat.JPG, ImageFormat.JPEG)
            SubscriptionTier.REFERRER -> listOf(ImageFormat.JPG, ImageFormat.JPEG) // 추천인도 JPG만 지원
            SubscriptionTier.ADMIN -> listOf(ImageFormat.JPG, ImageFormat.JPEG) // 관리자도 JPG만 지원
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
     * RAW 파일인지 확인 (파일 경로 기준)
     */
    fun isRawFile(filePath: String): Boolean {
        val extension = filePath.substringAfterLast('.', "").lowercase()
        val isRaw = RAW_EXTENSIONS.contains(extension)
        Log.d(TAG, "RAW 파일 확인 - $filePath (확장자: $extension): ${if (isRaw) "RAW 파일" else "일반 파일"}")
        return isRaw
    }

    /**
     * FREE 티어에서 RAW 파일 접근 시 표시할 메시지
     */
    fun getRawRestrictionMessage(): String {
        return "RAW 파일은 PRO 구독에서만 사용할 수 있습니다.\n" +
                "PRO로 업그레이드하여 RAW 파일 편집 기능을 이용해보세요!"
    }

    /**
     * BASIC 티어에서 RAW 파일 접근 시 표시할 메시지
     */
    fun getRawRestrictionMessageForBasic(): String {
        return "RAW 파일은 PRO 구독에서만 사용할 수 있습니다.\n" +
                "PRO로 업그레이드하여 고급 RAW 파일 편집 기능을 이용해보세요!"
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
        // 관리자와 추천인은 모든 기능 사용 가능
        if (tier == SubscriptionTier.ADMIN || tier == SubscriptionTier.REFERRER) {
            Log.i(TAG, "기능 사용 권한 확인 - $feature: 사용 가능 (특별 티어: $tier)")
            return true
        }

        val canUse = when (feature) {
            "raw_processing" -> tier == SubscriptionTier.PRO
            "png_export" -> false
            "webp_export" -> false
            "advanced_filters" -> tier == SubscriptionTier.PRO
            "batch_processing" -> tier != SubscriptionTier.FREE
            else -> true // 기본 기능은 모든 등급에서 사용 가능
        }
        Log.i(TAG, "기능 사용 권한 확인 - $feature: ${if (canUse) "사용 가능" else "제한됨"} (티어: $tier)")
        return canUse
    }

    /**
     * 티어별 업그레이드 혜택 메시지
     */
    fun getUpgradeMessage(currentTier: SubscriptionTier): String {
        return when (currentTier) {
            SubscriptionTier.FREE -> "BASIC으로 업그레이드: PNG 미지원\nPRO로 업그레이드: RAW 지원"
            SubscriptionTier.BASIC -> "PRO로 업그레이드: RAW 파일 편집"
            SubscriptionTier.PRO -> "이미 최고 등급을 사용 중입니다!"
            SubscriptionTier.REFERRER -> "추천인 특별 등급입니다! 모든 기능을 이용하실 수 있습니다."
            SubscriptionTier.ADMIN -> "관리자 등급입니다! 모든 기능을 이용하실 수 있습니다."
        }
    }

    /**
     * 지원되는 RAW 확장자 목록 반환
     */
    fun getSupportedRawExtensions(): Set<String> {
        return RAW_EXTENSIONS
    }

    /**
     * 파일이 특정 제조사의 RAW 파일인지 확인
     */
    fun getRawFileManufacturer(filePath: String): String? {
        val extension = filePath.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "cr2", "cr3" -> "Canon"
            "nef", "nrw" -> "Nikon"
            "arw", "srf", "sr2" -> "Sony"
            "orf" -> "Olympus"
            "rw2" -> "Panasonic"
            "pef", "ptx" -> "Pentax"
            "x3f" -> "Sigma"
            "raf" -> "Fujifilm"
            "3fr" -> "Hasselblad"
            "dng" -> "Adobe DNG"
            else -> if (RAW_EXTENSIONS.contains(extension)) "기타" else null
        }
    }
}