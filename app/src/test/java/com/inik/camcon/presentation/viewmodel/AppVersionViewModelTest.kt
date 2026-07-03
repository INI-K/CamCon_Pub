package com.inik.camcon.presentation.viewmodel

import com.inik.camcon.domain.model.AppVersionInfo
import com.inik.camcon.domain.usecase.CheckAppVersionUseCase
import com.inik.camcon.domain.usecase.StartImmediateUpdateUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppVersionViewModelTest {

    private lateinit var viewModel: AppVersionViewModel
    private lateinit var checkAppVersionUseCase: CheckAppVersionUseCase
    private lateinit var startImmediateUpdateUseCase: StartImmediateUpdateUseCase

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        checkAppVersionUseCase = mockk()
        startImmediateUpdateUseCase = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): AppVersionViewModel {
        return AppVersionViewModel(checkAppVersionUseCase, startImmediateUpdateUseCase)
    }

    private fun versionInfo(
        updateRequired: Boolean = false,
        updateAvailable: Boolean = false
    ) = AppVersionInfo(
        currentVersion = "1.0.0",
        latestVersion = "1.0.1",
        isUpdateRequired = updateRequired,
        isUpdateAvailable = updateAvailable
    )

    @Test
    fun `초기 상태는 isLoading true — 체크 완료 전 스플래시 네비게이션 방지`() = runTest {
        viewModel = createViewModel()

        // checkForUpdate() 호출 전의 idle 상태가 "체크 완료(false,false)"와 구분되지 않으면
        // 스플래시가 버전 체크 전에 네비게이션을 발화한다 (로그인 화면 2회 오픈 근본원인).
        assertTrue(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.showUpdateDialog)
    }

    @Test
    fun `checkForUpdate 성공 - 업데이트 불필요시 isLoading false, 다이얼로그 없음`() = runTest {
        coEvery { checkAppVersionUseCase() } returns Result.success(versionInfo())
        viewModel = createViewModel()

        viewModel.checkForUpdate()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.showUpdateDialog)
    }

    @Test
    fun `checkForUpdate 성공 - 업데이트 가능시 showUpdateDialog true`() = runTest {
        coEvery { checkAppVersionUseCase() } returns Result.success(
            versionInfo(updateAvailable = true)
        )
        viewModel = createViewModel()

        viewModel.checkForUpdate()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.showUpdateDialog)
    }

    @Test
    fun `checkForUpdate 실패시에도 isLoading false로 내려가 네비게이션 진행 가능`() = runTest {
        coEvery { checkAppVersionUseCase() } returns Result.failure(Exception("네트워크 오류"))
        viewModel = createViewModel()

        viewModel.checkForUpdate()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `dismissUpdateDialog는 showUpdateDialog만 false로 변경`() = runTest {
        coEvery { checkAppVersionUseCase() } returns Result.success(
            versionInfo(updateAvailable = true)
        )
        viewModel = createViewModel()

        viewModel.checkForUpdate()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showUpdateDialog)

        viewModel.dismissUpdateDialog()

        assertFalse(viewModel.uiState.value.showUpdateDialog)
    }
}
