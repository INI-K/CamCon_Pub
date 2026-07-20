package com.inik.camcon.presentation.viewmodel

import android.content.Context
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.repository.FilmEditProcessor
import com.inik.camcon.domain.usecase.FilmLutUseCase
import com.inik.camcon.domain.usecase.GetSubscriptionUseCase
import com.inik.camcon.domain.usecase.PurchaseSubscriptionUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * [SubscriptionViewModel.purchase] 결제 후 구독 재조회 배선 검증(item1(b)).
 *
 * 결제 시트가 정상 호출된 경우에만 forceSync 재조회를 트리거하고, 시트 미표시(false)면
 * 재조회하지 않는다. (실제 구매 완료 반영의 주 경로는 repository 검증 신호이며, 이 호출은 보조.)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionViewModelPurchaseTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var getSubscriptionUseCase: GetSubscriptionUseCase
    private lateinit var purchaseSubscriptionUseCase: PurchaseSubscriptionUseCase
    private lateinit var filmLutUseCase: FilmLutUseCase
    private lateinit var filmEditProcessor: FilmEditProcessor
    private lateinit var context: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        getSubscriptionUseCase = mockk(relaxed = true)
        purchaseSubscriptionUseCase = mockk(relaxed = true)
        filmLutUseCase = mockk(relaxed = true)
        filmEditProcessor = mockk(relaxed = true)
        context = mockk(relaxed = true)

        every { getSubscriptionUseCase.getSubscriptionTier() } returns flowOf(SubscriptionTier.FREE)
        coEvery { purchaseSubscriptionUseCase.getAvailableSubscriptions() } returns emptyList()
        coEvery { filmLutUseCase.getAvailableLuts() } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): SubscriptionViewModel =
        SubscriptionViewModel(
            getSubscriptionUseCase = getSubscriptionUseCase,
            purchaseSubscriptionUseCase = purchaseSubscriptionUseCase,
            filmLutUseCase = filmLutUseCase,
            filmEditProcessor = filmEditProcessor,
            context = context,
            ioDispatcher = testDispatcher
        )

    @Test
    fun `purchase 시트가 떠오르면 forceSync 로 구독을 재조회한다`() = runTest {
        coEvery { purchaseSubscriptionUseCase.purchaseSubscription("pro_monthly") } returns true
        val vm = createViewModel()
        advanceUntilIdle()

        vm.purchase("pro_monthly")
        advanceUntilIdle()

        coVerify(exactly = 1) { getSubscriptionUseCase.refreshSubscription(forceSync = true) }
    }

    @Test
    fun `purchase 시트 미표시(false)면 재조회하지 않는다`() = runTest {
        coEvery { purchaseSubscriptionUseCase.purchaseSubscription("pro_monthly") } returns false
        val vm = createViewModel()
        advanceUntilIdle()

        vm.purchase("pro_monthly")
        advanceUntilIdle()

        coVerify(exactly = 0) { getSubscriptionUseCase.refreshSubscription(any()) }
    }
}
