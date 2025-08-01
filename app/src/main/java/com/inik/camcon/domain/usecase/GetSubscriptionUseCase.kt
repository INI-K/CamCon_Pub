package com.inik.camcon.domain.usecase

import com.inik.camcon.domain.model.Subscription
import com.inik.camcon.domain.model.SubscriptionTier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * 사용자 구독 정보를 가져오는 UseCase
 * 현재는 모든 사용자가 FREE 티어 사용
 */
class GetSubscriptionUseCase @Inject constructor() {

    /**
     * 현재 사용자의 구독 정보를 Flow로 반환
     * 현재는 항상 FREE 티어 반환
     */
    operator fun invoke(): Flow<Subscription> {
        return flowOf(Subscription(tier = SubscriptionTier.FREE))
    }

    /**
     * 현재 사용자의 구독 등급만 반환
     */
    fun getSubscriptionTier(): Flow<SubscriptionTier> {
        return invoke().map { it.tier }
    }

    /**
     * 구독 상태 동기화 (현재는 아무 작업 안함)
     */
    suspend fun syncSubscriptionStatus() {
        // 현재는 Firebase가 구현되지 않아 아무 작업 안함
    }
}