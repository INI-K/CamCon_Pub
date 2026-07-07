package com.inik.camcon.presentation.viewmodel

import app.cash.turbine.test
import com.inik.camcon.R
import com.inik.camcon.domain.model.ReferralRedeemException
import com.inik.camcon.domain.model.ReferralRedeemReason
import com.inik.camcon.domain.model.Subscription
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.model.UiText
import com.inik.camcon.domain.usecase.GetSubscriptionUseCase
import com.inik.camcon.domain.usecase.auth.UserReferralUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [ReferralRedeemViewModel] 방출 검증.
 * 구현 세부가 아닌 StateFlow(uiState) / SharedFlow(uiEvent) 방출과
 * 성공 시 구독 새로고침 호출 계약을 확인한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReferralRedeemViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var userReferralUseCase: UserReferralUseCase
    private lateinit var getSubscriptionUseCase: GetSubscriptionUseCase
    private lateinit var viewModel: ReferralRedeemViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        userReferralUseCase = mockk()
        getSubscriptionUseCase = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createViewModel(): ReferralRedeemViewModel {
        return ReferralRedeemViewModel(userReferralUseCase, getSubscriptionUseCase)
    }

    @Test
    fun `redeem 성공 시 Success 방출 후 구독을 새로고침한다`() = runTest {
        // Given
        coEvery { userReferralUseCase.useReferralCode("CODE123") } returns Result.success(true)
        every { getSubscriptionUseCase.invoke() } returns
            MutableStateFlow(Subscription(tier = SubscriptionTier.REFERRER))

        viewModel = createViewModel()

        // When/Then - SharedFlow(replay=0) 이므로 redeem 전에 먼저 수집한다
        viewModel.uiEvent.test {
            viewModel.redeem("CODE123")
            val event = awaitItem()
            assertTrue(event is ReferralRedeemEvent.Success)
            assertEquals(
                UiText.Resource(R.string.settings_referral_redeem_success),
                (event as ReferralRedeemEvent.Success).message
            )
            cancelAndIgnoreRemainingEvents()
        }

        // 성공 경로는 재로그인 없이 티어 반영을 위해 구독을 새로고침·영속화한다
        coVerify(exactly = 1) { getSubscriptionUseCase.refreshSubscription(false) }
        coVerify(exactly = 1) { getSubscriptionUseCase.persistSubscriptionTier(SubscriptionTier.REFERRER) }
    }

    @Test
    fun `redeem 실패(NOT_FOUND) 시 해당 Error 방출하고 구독을 새로고침하지 않는다`() = runTest {
        // Given
        coEvery { userReferralUseCase.useReferralCode("NOPE") } returns
            Result.failure(ReferralRedeemException(ReferralRedeemReason.NOT_FOUND))

        viewModel = createViewModel()

        // When/Then
        viewModel.uiEvent.test {
            viewModel.redeem("NOPE")
            val event = awaitItem()
            assertTrue(event is ReferralRedeemEvent.Error)
            assertEquals(
                UiText.Resource(R.string.referral_error_not_found),
                (event as ReferralRedeemEvent.Error).message
            )
            cancelAndIgnoreRemainingEvents()
        }

        // 실패 경로에서는 구독 새로고침이 일어나지 않아야 한다
        coVerify(exactly = 0) { getSubscriptionUseCase.refreshSubscription(any()) }
        coVerify(exactly = 0) { getSubscriptionUseCase.persistSubscriptionTier(any()) }
    }

    @Test
    fun `redeem 진행 중 isLoading이 true 후 false 로 방출된다`() = runTest {
        // Given - useReferralCode 를 지연시켜 로딩 중간 상태를 관찰한다
        coEvery { userReferralUseCase.useReferralCode(any()) } coAnswers {
            delay(50)
            Result.success(true)
        }
        every { getSubscriptionUseCase.invoke() } returns
            MutableStateFlow(Subscription(tier = SubscriptionTier.REFERRER))

        viewModel = createViewModel()

        // When/Then
        viewModel.uiState.test {
            assertFalse(awaitItem().isLoading)            // 초기값 false
            viewModel.redeem("CODE123")
            assertTrue(awaitItem().isLoading)             // 시작 시 true
            testDispatcher.scheduler.advanceUntilIdle()   // 지연 해제 → 완료
            assertFalse(awaitItem().isLoading)            // 완료 후 false
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `redeem 실패(NETWORK) 시 네트워크 메시지를 방출한다`() = runTest {
        // Given
        coEvery { userReferralUseCase.useReferralCode("CODE123") } returns
            Result.failure(ReferralRedeemException(ReferralRedeemReason.NETWORK))

        viewModel = createViewModel()

        // When/Then
        viewModel.uiEvent.test {
            viewModel.redeem("CODE123")
            val event = awaitItem()
            assertTrue(event is ReferralRedeemEvent.Error)
            assertEquals(
                UiText.Resource(R.string.referral_error_network),
                (event as ReferralRedeemEvent.Error).message
            )
            cancelAndIgnoreRemainingEvents()
        }
    }
}
