package com.inik.camcon.domain.model

/**
 * 사용자 구독 등급을 나타내는 enum
 */
enum class SubscriptionTier {
    FREE,       // JPG/JPEG만 지원 (RAW 파일 사용 불가)
    BASIC,      // JPG/JPEG만 지원 (RAW 파일 사용 불가)
    PRO,        // JPG/JPEG + RAW 파일 지원
    REFERRER,   // 추천인 티어 - 모든 권한 (RAW 파일 포함)
    ADMIN       // 관리자 티어 - 모든 권한 (RAW 파일 포함)
}