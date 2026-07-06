package com.inik.camcon.domain.usecase

import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.repository.AppSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * pref 우선 병합 유효 티어(cold flow) 단일 진입점.
 *
 * pref 티어(로컬 캐시)가 FREE 가 아니면 pref 를 우선 사용하고, FREE 면 Firebase 티어를 확인한다.
 * StateFlow 가 아닌 cold flow 로 유지해 초기값(FREE) 오염 없이 첫 방출이 곧 병합 티어가 되게 한다
 * (판정·잠금 표시가 PRO 사용자에게 잠금 플래시를 만들지 않도록 — H1 방어).
 *
 * [AppSettingsViewModel] 과 [FilmEditorViewModel] 이 공유해 병합 로직 드리프트를 막는다(동작 무변경).
 */
@Singleton
class ObserveEffectiveTierUseCase @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository,
    private val getSubscriptionUseCase: GetSubscriptionUseCase
) {
    operator fun invoke(): Flow<SubscriptionTier> = combine(
        appSettingsRepository.subscriptionTierEnum,
        getSubscriptionUseCase.getSubscriptionTier()
    ) { prefTier, firebaseTier ->
        if (prefTier != SubscriptionTier.FREE) prefTier else firebaseTier
    }
}
