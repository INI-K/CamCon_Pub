package com.inik.camcon.domain.model

import java.util.Date

/**
 * 미리 생성된 추천인 코드 모델
 */
data class ReferralCode(
    val code: String,
    val isUsed: Boolean = false,
    val usedBy: String? = null,      // 사용한 사용자 ID
    val usedAt: Date? = null,        // 사용 날짜
    val createdAt: Date,
    val createdBy: String,           // 생성한 관리자 ID
    val tier: SubscriptionTier? = null, // 코드 사용 시 부여될 티어 (null이면 추천인만)
    val description: String? = null  // 코드 설명
)