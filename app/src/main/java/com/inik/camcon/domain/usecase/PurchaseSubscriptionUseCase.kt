package com.inik.camcon.domain.usecase

import com.inik.camcon.domain.model.SubscriptionProduct
import com.inik.camcon.domain.repository.SubscriptionRepository
import javax.inject.Inject

/**
 * 구독 구매 UseCase
 */
class PurchaseSubscriptionUseCase @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository
) {

    /**
     * 사용 가능한 구독 상품 목록 조회
     */
    suspend fun getAvailableSubscriptions(): List<SubscriptionProduct> {
        return subscriptionRepository.getAvailableSubscriptions()
    }

    /**
     * 구독 구매 시작
     */
    suspend fun purchaseSubscription(productId: String): Boolean {
        return subscriptionRepository.purchaseSubscription(productId)
    }

    /**
     * 구독 복원
     */
    suspend fun restoreSubscription(): Boolean {
        return subscriptionRepository.restoreSubscription()
    }
}