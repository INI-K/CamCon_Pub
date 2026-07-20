package com.inik.camcon.domain.usecase.auth

import com.inik.camcon.domain.model.ReferralCode
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.repository.AuthRepository
import com.inik.camcon.domain.usecase.GetSubscriptionUseCase
import com.inik.camcon.domain.util.Logger
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

/**
 * [AdminUserManagementUseCase.buildReferralCodeStatistics] 순수 함수 검증.
 *
 * 이미 로드된 목록에서 카운트/사용률/티어분포를 재스캔 없이 파생하는지 확인한다.
 * (getReferralCodeStatistics·loadReferralCodes 가 이 함수로 위임하도록 추출됨)
 */
class AdminUserManagementUseCaseTest {

    private lateinit var useCase: AdminUserManagementUseCase

    @Before
    fun setup() {
        // buildReferralCodeStatistics 는 순수 함수라 협력자를 건드리지 않으므로 relaxed mock 으로 충분
        val authRepository = mockk<AuthRepository>(relaxed = true)
        val getSubscriptionUseCase = mockk<GetSubscriptionUseCase>(relaxed = true)
        val logger = mockk<Logger>(relaxed = true)
        useCase = AdminUserManagementUseCase(authRepository, getSubscriptionUseCase, logger)
    }

    private fun code(name: String, used: Boolean, tier: SubscriptionTier?) = ReferralCode(
        code = name,
        isUsed = used,
        createdAt = Date(0),
        createdBy = "admin",
        tier = tier
    )

    @Test
    fun `혼합 목록의 카운트_사용률_티어분포를 정확히 파생한다`() {
        val codes = listOf(
            code("c1", used = true, tier = SubscriptionTier.PRO),
            code("c2", used = false, tier = SubscriptionTier.PRO),
            code("c3", used = true, tier = SubscriptionTier.ADMIN),
            code("c4", used = true, tier = null),
            code("c5", used = false, tier = SubscriptionTier.ADMIN)
        )

        val stats = useCase.buildReferralCodeStatistics(codes)

        assertEquals(5, stats["totalCodes"])
        assertEquals(2, stats["availableCodes"])
        assertEquals(3, stats["usedCodes"])
        assertEquals(60, stats["usageRate"]) // 3/5 * 100

        @Suppress("UNCHECKED_CAST")
        val tierDistribution = stats["tierDistribution"] as Map<String, Int>
        assertEquals(2, tierDistribution["PRO"])
        assertEquals(2, tierDistribution["ADMIN"])
        assertEquals(1, tierDistribution["추천인만"]) // tier == null → "추천인만"
        assertTrue(stats["lastUpdated"] is Long)
    }

    @Test
    fun `사용률은 정수로 절삭된다`() {
        val codes = listOf(
            code("c1", used = true, tier = SubscriptionTier.PRO),
            code("c2", used = false, tier = SubscriptionTier.PRO),
            code("c3", used = false, tier = SubscriptionTier.PRO)
        )

        val stats = useCase.buildReferralCodeStatistics(codes)

        assertEquals(3, stats["totalCodes"])
        assertEquals(1, stats["usedCodes"])
        assertEquals(33, stats["usageRate"]) // 33.33... → 33 절삭
    }

    @Test
    fun `빈 목록이면 사용률 0과 빈 분포를 반환한다`() {
        val stats = useCase.buildReferralCodeStatistics(emptyList())

        assertEquals(0, stats["totalCodes"])
        assertEquals(0, stats["availableCodes"])
        assertEquals(0, stats["usedCodes"])
        assertEquals(0, stats["usageRate"])

        @Suppress("UNCHECKED_CAST")
        val tierDistribution = stats["tierDistribution"] as Map<String, Int>
        assertTrue(tierDistribution.isEmpty())
    }
}
