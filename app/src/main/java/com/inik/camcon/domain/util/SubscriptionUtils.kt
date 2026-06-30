package com.inik.camcon.domain.util

import com.inik.camcon.domain.model.ImageFormat
import com.inik.camcon.domain.model.SubscriptionTier

/**
 * 구독 기능 관련 유틸리티
 */
object SubscriptionUtils {

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
        "raw",                  // 일반 RAW
        "rwl",                  // Leica
        "iiq",                  // Phase One
        "kdc",                  // Kodak
        "mdc",                  // Minolta DiMAGE
        "nco",                  // Nikon Coolpix
        "tif", "tiff"           // TIFF 포맷의 RAW (일부 카메라)
    )

    /**
     * 구독 등급에 따라 지원되는 이미지 포맷 반환
     *
     * 사양(CLAUDE.md)상 FREE=JPG/JPEG, BASIC 이상=JPG/JPEG/PNG.
     * RAW 게이팅은 본 목록과 별개로 [ValidateImageFormatUseCase] 단일 지점에서 처리한다.
     */
    fun getSupportedImageFormats(tier: SubscriptionTier): List<ImageFormat> {
        val formats = when (tier) {
            SubscriptionTier.FREE -> listOf(ImageFormat.JPG, ImageFormat.JPEG)
            SubscriptionTier.BASIC -> listOf(ImageFormat.JPG, ImageFormat.JPEG, ImageFormat.PNG)
            SubscriptionTier.PRO -> listOf(ImageFormat.JPG, ImageFormat.JPEG, ImageFormat.PNG)
            SubscriptionTier.REFERRER -> listOf(ImageFormat.JPG, ImageFormat.JPEG, ImageFormat.PNG)
            SubscriptionTier.ADMIN -> listOf(ImageFormat.JPG, ImageFormat.JPEG, ImageFormat.PNG)
        }
        return formats
    }

    /**
     * 특정 이미지 포맷이 구독 등급에서 지원되는지 확인
     */
    fun isFormatSupported(format: ImageFormat, tier: SubscriptionTier): Boolean {
        val supported = getSupportedImageFormats(tier).contains(format)
        return supported
    }

    /**
     * RAW 파일인지 확인 (파일 경로 기준)
     */
    fun isRawFile(filePath: String): Boolean {
        val extension = filePath.substringAfterLast('.', "").lowercase()
        return RAW_EXTENSIONS.contains(extension)
    }

    // PR-7 (2026-05-13): RAW 차단/업그레이드 메시지는 `R.string.*` 자원으로 이동.
    // `ValidateImageFormatUseCase` 가 `@ApplicationContext` 로 자원 조회. 8개 언어 동기화는 res/values-* 에서.

    /**
     * 파일 확장자로부터 이미지 포맷 추출
     */
    fun getImageFormatFromExtension(extension: String): ImageFormat? {
        val format = ImageFormat.values().find {
            it.extension.equals(extension, ignoreCase = true)
        }
        return format
    }

    /**
     * 구독 등급에 따른 기능 제한 확인
     */
    fun canUseFeature(tier: SubscriptionTier, feature: String): Boolean {
        // 관리자와 추천인은 모든 기능 사용 가능
        if (tier == SubscriptionTier.ADMIN || tier == SubscriptionTier.REFERRER) {
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
        return canUse
    }

    // `getUpgradeMessage` 도 PR-7 (2026-05-13) 에서 `R.string.upgrade_message_*` 로 이동.

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