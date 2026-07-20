package com.inik.camcon.domain.usecase

import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.repository.AppSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 유효 티어(cold flow) 단일 진입점.
 *
 * 서버(Firestore) 권위 판정이 있으면 그 판정이 강등·승격 모두 최종이다 — 만료/환불 후에도 로컬
 * pref 캐시가 PRO 기능(미리보기 탭·전체 필름 LUT)을 열어두지 못하게 한다. 서버 권위값이 아직
 * 없을 때(부팅 직후 초기값·오프라인·비로그인 폴백)만 pref 캐시를 우선 사용해 PRO 사용자에게 잠금
 * 플래시가 생기지 않도록 한다(H1 방어). StateFlow 가 아닌 cold flow 로 유지해 초기값 오염 없이
 * 첫 방출이 곧 병합 티어가 되게 한다.
 *
 * [AppSettingsViewModel] 과 [FilmEditorViewModel] 이 공유해 병합 로직 드리프트를 막는다.
 */
@Singleton
class ObserveEffectiveTierUseCase @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository,
    private val getSubscriptionUseCase: GetSubscriptionUseCase
) {
    operator fun invoke(): Flow<SubscriptionTier> = combine(
        appSettingsRepository.subscriptionTierEnum,
        getSubscriptionUseCase.invoke()
    ) { prefTier, subscription ->
        // 만료/비활성 구독은 서버 판정과 동일하게 FREE 로 강등(게이팅 단일 진입값 규약).
        val serverTier = subscription.tier?.takeIf { subscription.isActive }
            ?: SubscriptionTier.FREE

        if (subscription.isAuthoritative) {
            // 서버 권위 판정: 강등·승격 모두 최종. pref 캐시가 만료된 PRO 를 유지하지 못한다.
            serverTier
        } else {
            // 비권위(초기·오프라인·비로그인 폴백): 기존 오프라인 보호 유지 — pref 우선.
            if (prefTier != SubscriptionTier.FREE) prefTier else serverTier
        }
    }
}
