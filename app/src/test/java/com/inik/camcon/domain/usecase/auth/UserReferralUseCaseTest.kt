package com.inik.camcon.domain.usecase.auth

import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.repository.AuthRepository
import com.inik.camcon.domain.usecase.GetSubscriptionUseCase
import com.inik.camcon.domain.util.Logger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * useReferralCode가 Cloud Function(redeemReferralCode) 결과를 UI 계약(Result<Boolean>)으로
 * 올바르게 매핑하는지 검증한다. 검증·소비·티어부여는 서버가 수행하므로 이 UseCase는
 * 단일 CF 호출의 성공/실패 전파만 책임진다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UserReferralUseCaseTest {

    private lateinit var useCase: UserReferralUseCase
    private lateinit var authRepository: AuthRepository
    private lateinit var getSubscriptionUseCase: GetSubscriptionUseCase
    private lateinit var logger: Logger

    @Before
    fun setUp() {
        authRepository = mockk()
        getSubscriptionUseCase = mockk(relaxed = true)
        logger = mockk(relaxed = true)
        useCase = UserReferralUseCase(authRepository, getSubscriptionUseCase, logger)
    }

    @Test
    fun `CF 성공이면 success(true) 반환`() = runTest {
        // Given
        coEvery { authRepository.redeemReferralCode("VALID123") } returns
            Result.success(SubscriptionTier.PRO)

        // When
        val result = useCase.useReferralCode("VALID123")

        // Then
        assertTrue(result.isSuccess)
        assertEquals(true, result.getOrNull())
        coVerify { authRepository.redeemReferralCode("VALID123") }
    }

    @Test
    fun `티어 없는 코드도 CF 성공이면 success(true) 반환`() = runTest {
        // Given: 추천인만 있고 티어가 없는 코드(서버가 tier=null 반환)
        coEvery { authRepository.redeemReferralCode("REFONLY") } returns
            Result.success(null)

        // When
        val result = useCase.useReferralCode("REFONLY")

        // Then
        assertTrue(result.isSuccess)
        assertEquals(true, result.getOrNull())
    }

    @Test
    fun `CF 실패면 failure 전파`() = runTest {
        // Given: 이미 사용/미존재 등 서버 거부(HttpsError → 예외)
        val cause = IllegalStateException("already-exists")
        coEvery { authRepository.redeemReferralCode("USED") } returns
            Result.failure(cause)

        // When
        val result = useCase.useReferralCode("USED")

        // Then
        assertTrue(result.isFailure)
        assertEquals(cause, result.exceptionOrNull())
    }

    @Test
    fun `빈 코드는 CF 호출 없이 failure 반환`() = runTest {
        // When
        val result = useCase.useReferralCode("   ")

        // Then
        assertTrue(result.isFailure)
        coVerify(exactly = 0) { authRepository.redeemReferralCode(any()) }
    }

    @Test
    fun `redeem 예외가 던져져도 failure로 감싸 반환`() = runTest {
        // Given: repository가 Result 대신 예외를 던지는 극단 경우
        coEvery { authRepository.redeemReferralCode("BOOM") } throws RuntimeException("network")

        // When
        val result = useCase.useReferralCode("BOOM")

        // Then
        assertTrue(result.isFailure)
        assertFalse(result.isSuccess)
    }
}
