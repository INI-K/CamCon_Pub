package com.inik.camcon.domain.usecase.auth

import android.util.Log
import com.inik.camcon.domain.repository.AuthRepository
import com.inik.camcon.domain.usecase.GetSubscriptionUseCase
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * 사용자 추천 시스템 UseCase (미리 생성된 코드 기반)
 */
class UserReferralUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val getSubscriptionUseCase: GetSubscriptionUseCase
) {

    companion object {
        private const val TAG = "UserReferral"
    }

    /**
     * 추천 코드 검증 (로그인 시 사용)
     */
    suspend fun validateReferralCode(code: String): Result<Boolean> {
        return try {
            if (code.isBlank()) {
                return Result.failure(Exception("추천 코드를 입력해주세요"))
            }

            val referralCode = authRepository.validateReferralCode(code)
            if (referralCode == null) {
                Log.w(TAG, "존재하지 않는 추천 코드: $code")
                return Result.failure(Exception("존재하지 않는 추천 코드입니다"))
            }

            if (referralCode.isUsed) {
                Log.w(TAG, "이미 사용된 추천 코드: $code")
                return Result.failure(Exception("이미 사용된 추천 코드입니다"))
            }

            Log.i(TAG, "추천 코드 검증 성공: $code")
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "추천 코드 검증 실패: $code", e)
            Result.failure(e)
        }
    }

    /**
     * 추천 코드 사용 처리 (회원가입 완료 후)
     */
    suspend fun useReferralCode(code: String): Result<Boolean> {
        return try {
            val currentUser = authRepository.getCurrentUser().first()
            if (currentUser == null) {
                return Result.failure(Exception("로그인이 필요합니다"))
            }

            // 이미 추천 코드를 사용한 경우 체크
            if (!currentUser.referralCode.isNullOrEmpty()) {
                return Result.failure(Exception("이미 추천 코드를 사용하셨습니다"))
            }

            // 추천 코드 검증
            val validationResult = validateReferralCode(code)
            if (!validationResult.isSuccess) {
                return validationResult
            }

            val referralCode = authRepository.validateReferralCode(code)!!

            // 추천 코드 사용 처리
            val useSuccess = authRepository.useReferralCode(code, currentUser.id)
            if (!useSuccess) {
                return Result.failure(Exception("추천 코드 사용 처리에 실패했습니다"))
            }

            // 사용자 정보에 사용한 추천 코드 저장
            val userUpdateSuccess = authRepository.updateUserReferralCode(currentUser.id, code)
            if (!userUpdateSuccess) {
                Log.w(TAG, "사용자 추천 코드 정보 업데이트 실패: ${currentUser.id}")
            }

            // 사용자에게 티어 부여 (코드에 티어가 설정되어 있으면)
            referralCode.tier?.let { tier ->
                val tierUpdateSuccess = authRepository.updateUserTier(currentUser.id, tier)
                if (tierUpdateSuccess) {
                    Log.i(TAG, "추천 코드로 티어 부여: ${currentUser.id} → $tier")
                } else {
                    Log.w(TAG, "티어 부여 실패: ${currentUser.id} → $tier")
                }
            }

            Log.i(TAG, "추천 코드 사용 완료: $code by ${currentUser.id}")
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "추천 코드 사용 실패: $code", e)
            Result.failure(e)
        }
    }

    /**
     * 내 추천 통계 조회 (추천받은 사람들)
     */
    suspend fun getMyReferralStats(): Result<Map<String, Any>> {
        return try {
            val currentUser = authRepository.getCurrentUser().first()
            if (currentUser == null) {
                return Result.failure(Exception("로그인이 필요합니다"))
            }

            val stats = authRepository.getReferralStats(currentUser.id)
            Log.i(TAG, "추천 통계 조회 성공: ${currentUser.id}")
            Result.success(stats)
        } catch (e: Exception) {
            Log.e(TAG, "추천 통계 조회 실패", e)
            Result.failure(e)
        }
    }

    /**
     * 추천인 티어 자격 확인 (더 이상 사용하지 않음)
     */
    @Deprecated("미리 생성된 추천 코드 시스템으로 변경됨")
    suspend fun checkReferrerEligibility(): Result<Boolean> {
        return Result.failure(Exception("더 이상 지원하지 않는 기능입니다"))
    }

    /**
     * 추천인 티어 신청 (더 이상 사용하지 않음)
     */
    @Deprecated("미리 생성된 추천 코드 시스템으로 변경됨")
    suspend fun applyForReferrerTier(): Result<Boolean> {
        return Result.failure(Exception("더 이상 지원하지 않는 기능입니다"))
    }

    /**
     * 현재 사용자가 사용한 추천 코드 조회
     */
    suspend fun getUsedReferralCode(): Result<String?> {
        return try {
            val currentUser = authRepository.getCurrentUser().first()
            if (currentUser == null) {
                return Result.failure(Exception("로그인이 필요합니다"))
            }

            // User 모델의 referralCode 필드에서 사용한 추천 코드 조회
            Result.success(currentUser.referralCode)
        } catch (e: Exception) {
            Log.e(TAG, "사용한 추천 코드 조회 실패", e)
            Result.failure(e)
        }
    }

    /**
     * 추천인 티어 혜택 정보
     */
    fun getReferrerBenefits(): List<String> {
        return listOf(
            "모든 RAW 파일 형식 지원",
            "고급 필터 및 편집 기능",
            "배치 처리 기능",
            "우선 고객 지원",
            "베타 기능 우선 체험",
            "추천 보상 프로그램 참여"
        )
    }

    /**
     * 추천 코드 사용 방법 안내
     */
    fun getReferralCodeUsageGuide(): Map<String, String> {
        return mapOf(
            "title" to "추천 코드 사용 방법",
            "step1" to "회원가입 시 추천인 코드를 입력하세요",
            "step2" to "추천 코드는 한 번만 사용 가능합니다",
            "step3" to "코드에 따라 특별 혜택이 제공될 수 있습니다",
            "note" to "추천 코드는 관리자에게 문의하여 받으실 수 있습니다"
        )
    }
}