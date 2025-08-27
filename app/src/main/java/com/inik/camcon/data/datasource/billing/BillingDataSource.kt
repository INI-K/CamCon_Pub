package com.inik.camcon.data.datasource.billing

import com.android.billingclient.api.Purchase
import com.inik.camcon.domain.model.SubscriptionProduct
import com.inik.camcon.domain.model.SubscriptionTier
import kotlinx.coroutines.flow.Flow

interface BillingDataSource {
    /**
     * 구독 상품 목록 조회
     */
    suspend fun getSubscriptionProducts(): List<SubscriptionProduct>

    /**
     * 구독 구매 시작
     */
    suspend fun launchBillingFlow(productId: String): Boolean

    /**
     * 활성 구독 정보 조회
     */
    suspend fun getActiveSubscriptions(): List<Purchase>

    /**
     * 구독 상태 실시간 감시
     */
    fun getSubscriptionUpdates(): Flow<List<Purchase>>

    /**
     * 구독 확인 및 소비
     */
    suspend fun acknowledgeSubscription(purchase: Purchase): Boolean

    /**
     * 구독 취소
     */
    suspend fun cancelSubscription(purchaseToken: String): Boolean

    /**
     * Firebase에서 구독 상태 동기화
     */
    suspend fun syncSubscriptionWithFirebase(tier: SubscriptionTier, purchaseToken: String)
}