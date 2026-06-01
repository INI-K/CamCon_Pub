package com.inik.camcon.domain.usecase

import app.cash.turbine.test
import com.inik.camcon.domain.model.Subscription
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.repository.AppSettingsRepository
import com.inik.camcon.domain.repository.SubscriptionRepository
import com.inik.camcon.domain.util.Logger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GetSubscriptionUseCaseTest {

    private lateinit var useCase: GetSubscriptionUseCase
    private lateinit var subscriptionRepository: SubscriptionRepository
    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var logger: Logger

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        subscriptionRepository = mockk()
        appSettingsRepository = mockk(relaxed = true)
        // seedFromCache()가 first()에서 멈추지 않도록 캐시 티어 기본 stub.
        every { appSettingsRepository.subscriptionTierEnum } returns flowOf(SubscriptionTier.FREE)
        logger = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `초기 상태는 FREE 티어`() = runTest {
        // Given
        coEvery { subscriptionRepository.getUserSubscription() } returns flowOf(
            Subscription(tier = SubscriptionTier.FREE)
        )

        // When
        useCase = GetSubscriptionUseCase(subscriptionRepository, appSettingsRepository, logger, this)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        val result = useCase().value
        assertEquals(SubscriptionTier.FREE, result.tier)
    }

    @Test
    fun `getSubscriptionTier는 현재 티어를 Flow로 반환`() = runTest {
        // Given
        val proSubscription = Subscription(tier = SubscriptionTier.PRO)
        coEvery { subscriptionRepository.getUserSubscription() } returns flowOf(proSubscription)

        useCase = GetSubscriptionUseCase(subscriptionRepository, appSettingsRepository, logger, this)
        testDispatcher.scheduler.advanceUntilIdle()

        // When & Then
        useCase.getSubscriptionTier().test {
            val tier = awaitItem()
            assertEquals(SubscriptionTier.PRO, tier)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `tier가 null이면 FREE를 반환`() = runTest {
        // Given
        val nullTierSubscription = Subscription(tier = null)
        coEvery { subscriptionRepository.getUserSubscription() } returns flowOf(nullTierSubscription)

        useCase = GetSubscriptionUseCase(subscriptionRepository, appSettingsRepository, logger, this)
        testDispatcher.scheduler.advanceUntilIdle()

        // When & Then
        useCase.getSubscriptionTier().test {
            val tier = awaitItem()
            assertEquals(SubscriptionTier.FREE, tier)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `refreshSubscription with forceSync true면 syncSubscriptionStatus 호출`() = runTest {
        // Given
        coEvery { subscriptionRepository.getUserSubscription() } returns flowOf(
            Subscription(tier = SubscriptionTier.FREE)
        )
        coEvery { subscriptionRepository.syncSubscriptionStatus() } returns Unit

        useCase = GetSubscriptionUseCase(subscriptionRepository, appSettingsRepository, logger, this)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        useCase.refreshSubscription(forceSync = true)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify { subscriptionRepository.syncSubscriptionStatus() }
    }

    @Test
    fun `ADMIN 티어 구독 정보 반환`() = runTest {
        // Given
        val adminSubscription = Subscription(tier = SubscriptionTier.ADMIN, isActive = true)
        coEvery { subscriptionRepository.getUserSubscription() } returns flowOf(adminSubscription)

        useCase = GetSubscriptionUseCase(subscriptionRepository, appSettingsRepository, logger, this)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        val result = useCase().value

        // Then
        assertEquals(SubscriptionTier.ADMIN, result.tier)
        assertEquals(true, result.isActive)
    }

    @Test
    fun `syncSubscriptionStatus 호출시 구독 상태 동기화`() = runTest {
        // Given
        coEvery { subscriptionRepository.getUserSubscription() } returns flowOf(
            Subscription(tier = SubscriptionTier.FREE)
        )
        coEvery { subscriptionRepository.syncSubscriptionStatus() } returns Unit

        useCase = GetSubscriptionUseCase(subscriptionRepository, appSettingsRepository, logger, this)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        useCase.syncSubscriptionStatus()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify { subscriptionRepository.syncSubscriptionStatus() }
    }

    @Test
    fun `캐시가 PRO면 Firestore 갱신 전 초기값이 PRO`() = runTest {
        // Given: 캐시 티어는 PRO, Firestore 조회는 끝나지 않는 Flow(아직 갱신 전 상황 모사)
        every { appSettingsRepository.subscriptionTierEnum } returns flowOf(SubscriptionTier.PRO)
        coEvery { subscriptionRepository.getUserSubscription() } returns emptyFlow()

        // When
        useCase = GetSubscriptionUseCase(subscriptionRepository, appSettingsRepository, logger, this)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: Firestore가 값을 주지 않았으므로 seed된 PRO가 유지
        val result = useCase().value
        assertEquals(SubscriptionTier.PRO, result.tier)
    }

    @Test
    fun `캐시 PRO여도 Firestore가 FREE면 최종 FREE로 수렴`() = runTest {
        // Given: 캐시는 PRO지만 Firestore 실값은 FREE
        every { appSettingsRepository.subscriptionTierEnum } returns flowOf(SubscriptionTier.PRO)
        coEvery { subscriptionRepository.getUserSubscription() } returns flowOf(
            Subscription(tier = SubscriptionTier.FREE)
        )

        // When
        useCase = GetSubscriptionUseCase(subscriptionRepository, appSettingsRepository, logger, this)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: refresh가 seed를 덮어써 최종 FREE
        val result = useCase().value
        assertEquals(SubscriptionTier.FREE, result.tier)
    }
}
