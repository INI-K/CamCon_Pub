package com.inik.camcon.domain.model

import com.google.gson.annotations.SerializedName
import java.util.Date

/**
 * 사용자 구독 정보
 */
data class Subscription(
    @SerializedName("tier")
    val tier: SubscriptionTier = SubscriptionTier.FREE,

    @SerializedName("start_date")
    val startDate: Date? = null,

    @SerializedName("end_date")
    val endDate: Date? = null,

    @SerializedName("auto_renew")
    val autoRenew: Boolean = false,

    @SerializedName("is_active")
    val isActive: Boolean = true
)