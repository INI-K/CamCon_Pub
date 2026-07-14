package com.inik.camcon.domain.usecase

import app.cash.turbine.test
import com.inik.camcon.domain.model.Subscription
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.repository.AppSettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * item A 회귀: 유효 티어 병합은 서버 권위 판정이 있으면 그 판정이 강등·승격 모두 최종이어야 한다.
 * 만료/환불된 유료 티어가 로컬 pref 캐시(PRO)로 PRO 기능(미리보기 탭·전체 LUT)을 계속 열어두면 안 된다.
 * 반대로 서버 권위값이 아직 없을 때(초기·오프라인·비로그인 폴백)는 기존 오프라인 보호(pref 우선)를 유지한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveEffectiveTierUseCaseTest {

    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var getSubscriptionUseCase: GetSubscriptionUseCase
    private lateinit var useCase: ObserveEffectiveTierUseCase

    @Before
    fun setUp() {
        appSettingsRepository = mockk()
        getSubscriptionUseCase = mockk()
    }

    private fun givenPrefTier(tier: SubscriptionTier) {
        every { appSettingsRepository.subscriptionTierEnum } returns flowOf(tier)
    }

    private fun givenServerSubscription(subscription: Subscription) {
        every { getSubscriptionUseCase.invoke() } returns MutableStateFlow(subscription)
    }

    @Test
    fun `권위 만료 PRO(isActive=false)는 pref PRO를 이기고 FREE로 강등`() = runTest {
        givenPrefTier(SubscriptionTier.PRO)
        givenServerSubscription(
            Subscription(tier = SubscriptionTier.PRO, isActive = false, isAuthoritative = true)
        )
        useCase = ObserveEffectiveTierUseCase(appSettingsRepository, getSubscriptionUseCase)

        assertEquals(SubscriptionTier.FREE, useCase().first())
    }

    @Test
    fun `권위 FREE(환불)는 pref PRO를 이기고 FREE로 강등`() = runTest {
        givenPrefTier(SubscriptionTier.PRO)
        givenServerSubscription(
            Subscription(tier = SubscriptionTier.FREE, isActive = true, isAuthoritative = true)
        )
        useCase = ObserveEffectiveTierUseCase(appSettingsRepository, getSubscriptionUseCase)

        assertEquals(SubscriptionTier.FREE, useCase().first())
    }

    @Test
    fun `비권위 FREE는 pref PRO를 강등하지 않는다 (오프라인 보호)`() = runTest {
        givenPrefTier(SubscriptionTier.PRO)
        givenServerSubscription(
            Subscription(tier = SubscriptionTier.FREE, isActive = true, isAuthoritative = false)
        )
        useCase = ObserveEffectiveTierUseCase(appSettingsRepository, getSubscriptionUseCase)

        assertEquals(SubscriptionTier.PRO, useCase().first())
    }

    @Test
    fun `권위 PRO는 pref FREE여도 PRO`() = runTest {
        givenPrefTier(SubscriptionTier.FREE)
        givenServerSubscription(
            Subscription(tier = SubscriptionTier.PRO, isActive = true, isAuthoritative = true)
        )
        useCase = ObserveEffectiveTierUseCase(appSettingsRepository, getSubscriptionUseCase)

        assertEquals(SubscriptionTier.PRO, useCase().first())
    }

    @Test
    fun `초기 비권위 기본값에서는 pref PRO가 유지된다 (H1 잠금 플래시 방지)`() = runTest {
        givenPrefTier(SubscriptionTier.PRO)
        // GetSubscriptionUseCase 초기 기본값과 동일: 서버 조회 전 비권위 FREE
        givenServerSubscription(
            Subscription(tier = SubscriptionTier.FREE, isAuthoritative = false)
        )
        useCase = ObserveEffectiveTierUseCase(appSettingsRepository, getSubscriptionUseCase)

        assertEquals(SubscriptionTier.PRO, useCase().first())
    }

    @Test
    fun `비권위 seed PRO는 유지되다가 권위 FREE 도착 시 강등된다`() = runTest {
        givenPrefTier(SubscriptionTier.PRO)
        val serverFlow = MutableStateFlow(
            // 캐시 seed(비권위) PRO
            Subscription(tier = SubscriptionTier.PRO, isActive = true, isAuthoritative = false)
        )
        every { getSubscriptionUseCase.invoke() } returns serverFlow
        useCase = ObserveEffectiveTierUseCase(appSettingsRepository, getSubscriptionUseCase)

        useCase().test {
            // 비권위 seed → pref 우선으로 PRO 유지(플래시 없음)
            assertEquals(SubscriptionTier.PRO, awaitItem())
            // 서버 권위 환불/만료 판정 도착
            serverFlow.value =
                Subscription(tier = SubscriptionTier.FREE, isActive = true, isAuthoritative = true)
            // 권위 판정이 pref 캐시를 이기고 강등
            assertEquals(SubscriptionTier.FREE, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }
}
