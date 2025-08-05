package com.inik.camcon.domain.repository

import com.inik.camcon.domain.model.Subscription
import com.inik.camcon.domain.model.SubscriptionProduct
import kotlinx.coroutines.flow.Flow

interface SubscriptionRepository {
    /**
     * 현재 사용자의 구독 정보 조회
     */
    fun getUserSubscription(): Flow<Subscription>

    /**
     * 사용 가능한 구독 상품 목록 조회
     */
    suspend fun getAvailableSubscriptions(): List<SubscriptionProduct>

    /**
     * 구독 구매 시작
     */
    suspend fun purchaseSubscription(productId: String): Boolean

    /**
     * 구독 상태 확인 및 동기화
     */
    suspend fun syncSubscriptionStatus()

    /**
     * 구독 취소
     */
    suspend fun cancelSubscription(): Boolean

    /**
     * 구독 복원
     */
    suspend fun restoreSubscription(): Boolean
}