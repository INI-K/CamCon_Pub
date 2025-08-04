package com.inik.camcon.domain.model

import com.google.gson.annotations.SerializedName

/**
 * Google Play 구독 상품 정보
 */
data class SubscriptionProduct(
    @SerializedName("product_id")
    val productId: String,

    @SerializedName("tier")
    val tier: SubscriptionTier,

    @SerializedName("title")
    val title: String,

    @SerializedName("description")
    val description: String,

    @SerializedName("price")
    val price: String,

    @SerializedName("currency_code")
    val currencyCode: String,

    @SerializedName("price_amount_micros")
    val priceAmountMicros: Long,

    @SerializedName("billing_period")
    val billingPeriod: String // P1M (월간), P1Y (연간)
)