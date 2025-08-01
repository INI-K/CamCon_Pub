package com.inik.camcon.domain.usecase

import com.inik.camcon.domain.model.Subscription
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * 사용자 구독 정보를 가져오는 UseCase
 */
class GetSubscriptionUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {

    /**
     * 현재 사용자의 구독 정보를 Flow로 반환
     */
    operator fun invoke(): Flow<Subscription> {
        return authRepository.getCurrentUser().map { user ->
            user?.subscription ?: Subscription(tier = SubscriptionTier.FREE)
        }
    }

    /**
     * 현재 사용자의 구독 등급만 반환
     */
    fun getSubscriptionTier(): Flow<SubscriptionTier> {
        return invoke().map { it.tier }
    }
}