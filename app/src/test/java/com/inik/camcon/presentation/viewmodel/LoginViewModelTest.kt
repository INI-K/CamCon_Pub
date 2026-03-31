package com.inik.camcon.presentation.viewmodel

import app.cash.turbine.test
import com.inik.camcon.domain.model.User
import com.inik.camcon.domain.usecase.auth.SignInWithGoogleUseCase
import com.inik.camcon.domain.usecase.auth.UserReferralUseCase
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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

    private val testDispatcher = StandardTestDispatcher()
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

        // When
        viewModel.signInWithGoogle("test-id-token")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertTrue(state.isLoggedIn)
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.uiEvent.test {
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

        // When
        viewModel.signInWithGoogle("test-id-token")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertFalse(state.isLoggedIn)
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.uiEvent.test {
            val event = awaitItem()
            assertTrue(event is LoginUiEvent.ShowError)
            assertTrue((event as LoginUiEvent.ShowError).message.contains(errorMessage))
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

        // When
        viewModel.signInWithGoogle("test-id-token", referralCode)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.isLoggedIn)
            cancelAndIgnoreRemainingEvents()
        }

        // ShowReferralMessage and NavigateToHome should be emitted
        viewModel.uiEvent.test {
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
            assertTrue(state.isLoggedIn) // Still logged in even with invalid referral
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearError should be deprecated and do nothing`() = runTest {
        // Given
        viewModel = createViewModel()

        // When - this method should not throw and should do nothing
        @Suppress("DEPRECATION")
        viewModel.clearError()

        // Then - no state change expected (method is deprecated and empty)
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
