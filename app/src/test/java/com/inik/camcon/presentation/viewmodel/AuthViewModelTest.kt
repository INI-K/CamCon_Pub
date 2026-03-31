package com.inik.camcon.presentation.viewmodel

import android.content.Context
import app.cash.turbine.test
import com.inik.camcon.domain.model.User
import com.inik.camcon.domain.usecase.auth.GetCurrentUserUseCase
import com.inik.camcon.domain.usecase.auth.SignOutUseCase
import io.mockk.coEvery
import io.mockk.every
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private lateinit var viewModel: AuthViewModel
    private lateinit var signOutUseCase: SignOutUseCase
    private lateinit var getCurrentUserUseCase: GetCurrentUserUseCase
    private lateinit var context: Context

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        signOutUseCase = mockk()
        getCurrentUserUseCase = mockk()
        context = mockk(relaxed = true)

        // 기본 동작 설정
        every { getCurrentUserUseCase() } returns flowOf(null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): AuthViewModel {
        return AuthViewModel(signOutUseCase, getCurrentUserUseCase, context)
    }

    @Test
    fun `초기 상태는 isLoading false`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.isSignOutSuccess)
    }

    @Test
    fun `signOut 성공시 isSignOutSuccess true로 변경`() = runTest {
        coEvery { signOutUseCase() } returns Result.success(Unit)
        viewModel = createViewModel()

        viewModel.uiState.test {
            // 초기 상태
            val initial = awaitItem()
            assertFalse(initial.isSignOutSuccess)

            // signOut 호출
            viewModel.signOut()
            testDispatcher.scheduler.advanceUntilIdle()

            // 로딩 상태
            val loading = awaitItem()
            assertTrue(loading.isLoading)

            // 성공 상태
            val success = awaitItem()
            assertFalse(success.isLoading)
            assertTrue(success.isSignOutSuccess)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `signOut 실패시 isLoading false로 복원`() = runTest {
        val errorMessage = "로그아웃 실패"
        coEvery { signOutUseCase() } returns Result.failure(Exception(errorMessage))
        viewModel = createViewModel()

        viewModel.uiState.test {
            // 초기 상태
            awaitItem()

            // signOut 호출
            viewModel.signOut()
            testDispatcher.scheduler.advanceUntilIdle()

            // 로딩 상태
            awaitItem()

            // 실패 상태 - isLoading은 false로 복원
            val failure = awaitItem()
            assertFalse(failure.isLoading)
            assertFalse(failure.isSignOutSuccess)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `사용자 정보 변경시 currentUser 업데이트`() = runTest {
        val user = User(
            id = "test-uid",
            email = "test@example.com",
            displayName = "Test User"
        )
        every { getCurrentUserUseCase() } returns flowOf(user)

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(user, viewModel.uiState.value.currentUser)
    }
}
