package com.inik.camcon.domain.usecase.auth

import android.util.Log
import com.inik.camcon.domain.model.ReferralCode
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.model.User
import com.inik.camcon.domain.repository.AuthRepository
import com.inik.camcon.domain.usecase.GetSubscriptionUseCase
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * 관리자 전용 회원 관리 UseCase
 */
class AdminUserManagementUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val getSubscriptionUseCase: GetSubscriptionUseCase
) {

    private companion object {
        private const val TAG = "AdminUserManagement"
    }

    /**
     * 관리자 권한 확인
     */
    suspend fun isAdmin(): Boolean {
        val currentTier = getSubscriptionUseCase.getSubscriptionTier().first()
        return currentTier == SubscriptionTier.ADMIN
    }

    /**
     * 전체 사용자 조회 (관리자만)
     */
    suspend fun getAllUsers(): Result<List<User>> {
        return try {
            if (!isAdmin()) {
                return Result.failure(Exception("관리자 권한이 필요합니다"))
            }

            val users = authRepository.getAllUsers()
            Log.i(TAG, "전체 사용자 조회 성공: ${users.size}명")
            Result.success(users)
        } catch (e: Exception) {
            Log.e(TAG, "전체 사용자 조회 실패", e)
            Result.failure(e)
        }
    }

    /**
     * 특정 사용자 조회
     */
    suspend fun getUserById(userId: String): Result<User?> {
        return try {
            if (!isAdmin()) {
                return Result.failure(Exception("관리자 권한이 필요합니다"))
            }

            val user = authRepository.getUserById(userId)
            Log.i(TAG, "사용자 조회: $userId")
            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "사용자 조회 실패: $userId", e)
            Result.failure(e)
        }
    }

    /**
     * 사용자 티어 변경 (관리자만)
     */
    suspend fun updateUserTier(userId: String, newTier: SubscriptionTier): Result<Boolean> {
        return try {
            if (!isAdmin()) {
                return Result.failure(Exception("관리자 권한이 필요합니다"))
            }

            val success = authRepository.updateUserTier(userId, newTier)
            if (success) {
                Log.i(TAG, "사용자 티어 변경 성공: $userId → $newTier")
            } else {
                Log.w(TAG, "사용자 티어 변경 실패: $userId")
            }
            Result.success(success)
        } catch (e: Exception) {
            Log.e(TAG, "사용자 티어 변경 실패: $userId", e)
            Result.failure(e)
        }
    }

    /**
     * 사용자 비활성화 (관리자만)
     */
    suspend fun deactivateUser(userId: String): Result<Boolean> {
        return try {
            if (!isAdmin()) {
                return Result.failure(Exception("관리자 권한이 필요합니다"))
            }

            val success = authRepository.deactivateUser(userId)
            if (success) {
                Log.i(TAG, "사용자 비활성화 성공: $userId")
            } else {
                Log.w(TAG, "사용자 비활성화 실패: $userId")
            }
            Result.success(success)
        } catch (e: Exception) {
            Log.e(TAG, "사용자 비활성화 실패: $userId", e)
            Result.failure(e)
        }
    }

    /**
     * 사용자 재활성화 (관리자만)
     */
    suspend fun reactivateUser(userId: String): Result<Boolean> {
        return try {
            if (!isAdmin()) {
                return Result.failure(Exception("관리자 권한이 필요합니다"))
            }

            val success = authRepository.reactivateUser(userId)
            if (success) {
                Log.i(TAG, "사용자 재활성화 성공: $userId")
            } else {
                Log.w(TAG, "사용자 재활성화 실패: $userId")
            }
            Result.success(success)
        } catch (e: Exception) {
            Log.e(TAG, "사용자 재활성화 실패: $userId", e)
            Result.failure(e)
        }
    }

    /**
     * 티어별 사용자 조회
     */
    suspend fun getUsersByTier(tier: SubscriptionTier): Result<List<User>> {
        return try {
            if (!isAdmin()) {
                return Result.failure(Exception("관리자 권한이 필요합니다"))
            }

            val users = authRepository.getUsersByTier(tier)
            Log.i(TAG, "티어별 사용자 조회 성공: $tier → ${users.size}명")
            Result.success(users)
        } catch (e: Exception) {
            Log.e(TAG, "티어별 사용자 조회 실패: $tier", e)
            Result.failure(e)
        }
    }

    /**
     * 사용자 검색
     */
    suspend fun searchUsers(query: String): Result<List<User>> {
        return try {
            if (!isAdmin()) {
                return Result.failure(Exception("관리자 권한이 필요합니다"))
            }

            if (query.isBlank()) {
                return Result.failure(Exception("검색어를 입력해주세요"))
            }

            val users = authRepository.searchUsers(query)
            Log.i(TAG, "사용자 검색 성공: '$query' → ${users.size}명")
            Result.success(users)
        } catch (e: Exception) {
            Log.e(TAG, "사용자 검색 실패: $query", e)
            Result.failure(e)
        }
    }

    /**
     * 추천 통계 조회
     */
    suspend fun getReferralStats(userId: String): Result<Map<String, Any>> {
        return try {
            if (!isAdmin()) {
                return Result.failure(Exception("관리자 권한이 필요합니다"))
            }

            val stats = authRepository.getReferralStats(userId)
            Log.i(TAG, "추천 통계 조회 성공: $userId")
            Result.success(stats)
        } catch (e: Exception) {
            Log.e(TAG, "추천 통계 조회 실패: $userId", e)
            Result.failure(e)
        }
    }

    /**
     * 전체 사용자 통계
     */
    suspend fun getUserStatistics(): Result<Map<String, Any>> {
        return try {
            if (!isAdmin()) {
                return Result.failure(Exception("관리자 권한이 필요합니다"))
            }

            val allUsers = authRepository.getAllUsers()
            val activeUsers = allUsers.filter { it.isActive }
            val tierCounts = SubscriptionTier.values().associateWith { tier ->
                authRepository.getUsersByTier(tier).size
            }

            val statistics = mapOf(
                "totalUsers" to allUsers.size,
                "activeUsers" to activeUsers.size,
                "inactiveUsers" to (allUsers.size - activeUsers.size),
                "tierDistribution" to tierCounts,
                "lastUpdated" to System.currentTimeMillis()
            )

            Log.i(TAG, "사용자 통계 조회 성공: 총 ${allUsers.size}명")
            Result.success(statistics)
        } catch (e: Exception) {
            Log.e(TAG, "사용자 통계 조회 실패", e)
            Result.failure(e)
        }
    }

    /**
     * 추천 코드 생성 (관리자만)
     */
    suspend fun createReferralCode(
        code: String,
        tier: SubscriptionTier?,
        description: String?
    ): Result<Boolean> {
        return try {
            if (!isAdmin()) {
                return Result.failure(Exception("관리자 권한이 필요합니다"))
            }

            if (code.isBlank()) {
                return Result.failure(Exception("추천 코드를 입력해주세요"))
            }

            val success = authRepository.createReferralCode(code, tier, description)
            if (success) {
                Log.i(TAG, "추천 코드 생성 성공: $code")
            } else {
                Log.w(TAG, "추천 코드 생성 실패: $code")
            }
            Result.success(success)
        } catch (e: Exception) {
            Log.e(TAG, "추천 코드 생성 실패: $code", e)
            Result.failure(e)
        }
    }

    /**
     * 전체 추천 코드 조회 (관리자만)
     */
    suspend fun getAllReferralCodes(): Result<List<ReferralCode>> {
        return try {
            if (!isAdmin()) {
                return Result.failure(Exception("관리자 권한이 필요합니다"))
            }

            val codes = authRepository.getAllReferralCodes()
            Log.i(TAG, "전체 추천 코드 조회 성공: ${codes.size}개")
            Result.success(codes)
        } catch (e: Exception) {
            Log.e(TAG, "전체 추천 코드 조회 실패", e)
            Result.failure(e)
        }
    }

    /**
     * 사용 가능한 추천 코드 조회 (관리자만)
     */
    suspend fun getAvailableReferralCodes(): Result<List<ReferralCode>> {
        return try {
            if (!isAdmin()) {
                return Result.failure(Exception("관리자 권한이 필요합니다"))
            }

            val codes = authRepository.getAvailableReferralCodes()
            Log.i(TAG, "사용 가능한 추천 코드 조회 성공: ${codes.size}개")
            Result.success(codes)
        } catch (e: Exception) {
            Log.e(TAG, "사용 가능한 추천 코드 조회 실패", e)
            Result.failure(e)
        }
    }

    /**
     * 사용된 추천 코드 조회 (관리자만)
     */
    suspend fun getUsedReferralCodes(): Result<List<ReferralCode>> {
        return try {
            if (!isAdmin()) {
                return Result.failure(Exception("관리자 권한이 필요합니다"))
            }

            val codes = authRepository.getUsedReferralCodes()
            Log.i(TAG, "사용된 추천 코드 조회 성공: ${codes.size}개")
            Result.success(codes)
        } catch (e: Exception) {
            Log.e(TAG, "사용된 추천 코드 조회 실패", e)
            Result.failure(e)
        }
    }

    /**
     * 추천 코드 삭제 (관리자만)
     */
    suspend fun deleteReferralCode(code: String): Result<Boolean> {
        return try {
            if (!isAdmin()) {
                return Result.failure(Exception("관리자 권한이 필요합니다"))
            }

            val success = authRepository.deleteReferralCode(code)
            if (success) {
                Log.i(TAG, "추천 코드 삭제 성공: $code")
            } else {
                Log.w(TAG, "추천 코드 삭제 실패: $code")
            }
            Result.success(success)
        } catch (e: Exception) {
            Log.e(TAG, "추천 코드 삭제 실패: $code", e)
            Result.failure(e)
        }
    }

    /**
     * 추천 코드 통계 조회 (관리자만)
     */
    suspend fun getReferralCodeStatistics(): Result<Map<String, Any>> {
        return try {
            if (!isAdmin()) {
                return Result.failure(Exception("관리자 권한이 필요합니다"))
            }

            val allCodes = authRepository.getAllReferralCodes()
            val availableCodes = allCodes.filter { !it.isUsed }
            val usedCodes = allCodes.filter { it.isUsed }

            val tierDistribution = allCodes.groupBy { it.tier?.name ?: "추천인만" }
                .mapValues { it.value.size }

            val statistics = mapOf(
                "totalCodes" to allCodes.size,
                "availableCodes" to availableCodes.size,
                "usedCodes" to usedCodes.size,
                "usageRate" to if (allCodes.isNotEmpty()) {
                    (usedCodes.size.toDouble() / allCodes.size * 100).toInt()
                } else 0,
                "tierDistribution" to tierDistribution,
                "lastUpdated" to System.currentTimeMillis()
            )

            Log.i(TAG, "추천 코드 통계 조회 성공: 총 ${allCodes.size}개")
            Result.success(statistics)
        } catch (e: Exception) {
            Log.e(TAG, "추천 코드 통계 조회 실패", e)
            Result.failure(e)
        }
    }
}