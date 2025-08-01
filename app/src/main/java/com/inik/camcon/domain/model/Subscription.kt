package com.inik.camcon.domain.model

import java.util.Date

/**
 * 사용자 구독 정보
 * 현재는 모든 사용자가 FREE 티어 사용
 */
data class Subscription(
    val tier: SubscriptionTier = SubscriptionTier.FREE,
    val startDate: Date? = null,
    val endDate: Date? = null,
    val autoRenew: Boolean = false,
    val isActive: Boolean = true
)