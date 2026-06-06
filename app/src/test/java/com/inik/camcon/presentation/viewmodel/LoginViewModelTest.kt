package com.inik.camcon.presentation.viewmodel

import app.cash.turbine.test
import com.inik.camcon.R
import com.inik.camcon.domain.model.UiText
import com.inik.camcon.domain.model.User
import com.inik.camcon.domain.usecase.auth.SignInWithGoogleUseCase
import com.inik.camcon.domain.usecase.auth.UserReferralUseCase
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var signInWithGoogleUseCase: SignInWithGoogleUseCase
    private lateinit var userReferralUseCase: UserReferralUseCase
    private lateinit var viewModel: LoginViewModel

    private val testUser = User(
        id = "test-uid",
        email = "test@example.com",
        displayName = "Test User",
        photoUrl = null
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        signInWithGoogleUseCase = mockk()
        userReferralUseCase = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createViewModel(): LoginViewModel {
        return LoginViewModel(signInWithGoogleUseCase, userReferralUseCase)
    }

    @Test
    fun `initial state should have default values`() = runTest {
        // When
        viewModel = createViewModel()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertFalse(state.isLoggedIn)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `signInWithGoogle should show loading state`() = runTest {
        // Given
        coEvery { signInWithGoogleUseCase(any()) } coAnswers {
            kotlinx.coroutines.delay(1000)
            Result.success(testUser)
        }

        viewModel = createViewModel()

        // When
        viewModel.signInWithGoogle("test-id-token")

        // Then
        viewModel.uiState.test {
            val loadingState = awaitItem()
            assertTrue(loadingState.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `signInWithGoogle success should emit NavigateToHome event`() = runTest {
        // Given
        coEvery { signInWithGoogleUseCase("test-id-token") } returns Result.success(testUser)

        viewModel = createViewModel()

        // When/Then - 로그인 전에 uiEvent를 먼저 수집한다 (SharedFlow replay=0)
        viewModel.uiEvent.test {
            viewModel.signInWithGoogle("test-id-token")
            val event = awaitItem()
            assertTrue(event is LoginUiEvent.NavigateToHome)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `signInWithGoogle failure should emit ShowError event`() = runTest {
        // Given
        val errorMessage = "Authentication failed"
        coEvery { signInWithGoogleUseCase("test-id-token") } returns Result.failure(
            Exception(errorMessage)
        )

        viewModel = createViewModel()

        // When/Then - 로그인 전에 uiEvent를 먼저 수집한다 (SharedFlow replay=0)
        viewModel.uiEvent.test {
            viewModel.signInWithGoogle("test-id-token")
            val event = awaitItem()
            assertTrue(event is LoginUiEvent.ShowError)
            // 에러 메시지는 raw 문자열 대신 분류된 UiText(인증 에러)로 노출된다 (raw는 logcat 한정)
            assertEquals(
                UiText.Resource(R.string.login_error_auth),
                (event as LoginUiEvent.ShowError).message
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `signInWithGoogle with referral code should process referral`() = runTest {
        // Given
        val referralCode = "TEST123"
        coEvery { signInWithGoogleUseCase("test-id-token") } returns Result.success(testUser)
        coEvery { userReferralUseCase.useReferralCode(referralCode) } returns Result.success(true)

        viewModel = createViewModel()

        // When/Then - 로그인 전에 uiEvent를 먼저 수집한다 (SharedFlow replay=0)
        viewModel.uiEvent.test {
            viewModel.signInWithGoogle("test-id-token", referralCode)
            val event1 = awaitItem()
            assertTrue(event1 is LoginUiEvent.ShowReferralMessage)
            val event2 = awaitItem()
            assertTrue(event2 is LoginUiEvent.NavigateToHome)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `signInWithGoogle with invalid referral code should still login`() = runTest {
        // Given
        val referralCode = "INVALID"
        coEvery { signInWithGoogleUseCase("test-id-token") } returns Result.success(testUser)
        coEvery { userReferralUseCase.useReferralCode(referralCode) } returns Result.success(false)

        viewModel = createViewModel()

        // When
        viewModel.signInWithGoogle("test-id-token", referralCode)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.isLoggedIn) // 잘못된 추천 코드여도 로그인은 유지된다
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearError should be deprecated and do nothing`() = runTest {
        // Given
        viewModel = createViewModel()

        // When - 이 메서드는 예외를 던지지 않고 아무 동작도 하지 않아야 한다
        @Suppress("DEPRECATION")
        viewModel.clearError()

        // Then - 상태 변화 없음 예상 (deprecated 되었고 비어 있는 메서드)
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
