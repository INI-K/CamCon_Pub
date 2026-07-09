package com.inik.camcon.presentation.viewmodel

import app.cash.turbine.test
import com.inik.camcon.domain.model.ReferralCode
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.usecase.auth.AdminUserManagementUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Date

/**
 * [AdminReferralCodeViewModel] 방출/위임 검증.
 *
 * 회귀 목적: loadReferralCodes()가 전체 목록을 1회만 스캔(getAllReferralCodes)하고
 * available/used 분리와 통계 파생을 모두 메모리에서 처리하는지(Firestore read ≈4N→N 절감)를
 * StateFlow 방출과 UseCase 호출 계약으로 확인한다. 구현 세부가 아닌 방출/위임만 검증한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdminReferralCodeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var adminUserManagementUseCase: AdminUserManagementUseCase
    private lateinit var viewModel: AdminReferralCodeViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        adminUserManagementUseCase = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun code(name: String, used: Boolean, tier: SubscriptionTier?) = ReferralCode(
        code = name,
        isUsed = used,
        createdAt = Date(0),
        createdBy = "admin",
        tier = tier
    )

    @Test
    fun `loadReferralCodes는 getAllReferralCodes 1회로 available_used를 분리하고 통계를 위임한다`() =
        runTest {
            // Given: 사용/미사용이 섞인 목록
            val available1 = code("A", used = false, tier = SubscriptionTier.PRO)
            val used1 = code("B", used = true, tier = SubscriptionTier.PRO)
            val available2 = code("C", used = false, tier = null)
            val mixed = listOf(available1, used1, available2)
            val sentinelStats: Map<String, Any> = mapOf("totalCodes" to 3, "sentinel" to true)

            coEvery { adminUserManagementUseCase.isAdmin() } returns true
            coEvery { adminUserManagementUseCase.getAllReferralCodes() } returns
                Result.success(mixed)
            every { adminUserManagementUseCase.buildReferralCodeStatistics(mixed) } returns
                sentinelStats

            // When: init → checkAdminStatus → loadReferralCodes 가 즉시 실행됨
            viewModel = AdminReferralCodeViewModel(adminUserManagementUseCase)

            // Then: StateFlow 방출 검증 (available=!isUsed, used=isUsed, 통계=위임 결과)
            viewModel.uiState.test {
                val state = awaitItem()
                assertEquals(true, state.isAdmin)
                assertEquals(mixed, state.allCodes)
                assertEquals(listOf(available1, available2), state.availableCodes)
                assertEquals(listOf(used1), state.usedCodes)
                assertEquals(sentinelStats, state.statistics)
                cancelAndIgnoreRemainingEvents()
            }

            // 전체 목록은 정확히 1회만 스캔
            coVerify(exactly = 1) { adminUserManagementUseCase.getAllReferralCodes() }
            // 별도 available/used 스캔은 일어나지 않는다 (read 절감 계약)
            coVerify(exactly = 0) { adminUserManagementUseCase.getAvailableReferralCodes() }
            coVerify(exactly = 0) { adminUserManagementUseCase.getUsedReferralCodes() }
            // 통계는 buildReferralCodeStatistics(전체 목록)로 위임
            verify(exactly = 1) { adminUserManagementUseCase.buildReferralCodeStatistics(mixed) }
        }
}
