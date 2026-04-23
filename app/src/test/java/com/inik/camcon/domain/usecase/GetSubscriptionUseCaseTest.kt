package com.inik.camcon.domain.usecase

import app.cash.turbine.test
import com.inik.camcon.domain.model.Subscription
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.repository.SubscriptionRepository
import com.inik.camcon.domain.util.Logger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private lateinit var logger: Logger

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        subscriptionRepository = mockk()
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
        useCase = GetSubscriptionUseCase(subscriptionRepository, logger, this)
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

        useCase = GetSubscriptionUseCase(subscriptionRepository, logger, this)
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

        useCase = GetSubscriptionUseCase(subscriptionRepository, logger, this)
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

        useCase = GetSubscriptionUseCase(subscriptionRepository, logger, this)
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

        useCase = GetSubscriptionUseCase(subscriptionRepository, logger, this)
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

        useCase = GetSubscriptionUseCase(subscriptionRepository, logger, this)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        useCase.syncSubscriptionStatus()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify { subscriptionRepository.syncSubscriptionStatus() }
    }
}
