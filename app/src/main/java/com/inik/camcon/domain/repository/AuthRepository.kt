package com.inik.camcon.domain.repository

import com.inik.camcon.domain.model.ReferralCode
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun signInWithGoogle(idToken: String): Result<User>
    suspend fun signOut()
    fun getCurrentUser(): Flow<User?>
    suspend fun isUserLoggedIn(): Boolean

    // 회원 관리 기능
    suspend fun getUserById(userId: String): User?
    suspend fun getAllUsers(): List<User>
    suspend fun updateUser(user: User): Boolean
    suspend fun updateUserTier(userId: String, tier: SubscriptionTier): Boolean
    suspend fun updateUserReferralCode(userId: String, referralCode: String): Boolean
    suspend fun deactivateUser(userId: String): Boolean
    suspend fun reactivateUser(userId: String): Boolean
    suspend fun getUsersByTier(tier: SubscriptionTier): List<User>
    suspend fun searchUsers(query: String): List<User>
    suspend fun getReferralStats(userId: String): Map<String, Any>
    suspend fun generateReferralCode(userId: String): String?

    // 추천인 코드 관리 (미리 생성된 코드)
    suspend fun createReferralCode(
        code: String,
        tier: SubscriptionTier?,
        description: String?
    ): Boolean

    suspend fun getAllReferralCodes(): List<ReferralCode>
    suspend fun getAvailableReferralCodes(): List<ReferralCode>
    suspend fun getUsedReferralCodes(): List<ReferralCode>
    suspend fun validateReferralCode(code: String): ReferralCode?
    suspend fun useReferralCode(code: String, userId: String): Boolean
    suspend fun deleteReferralCode(code: String): Boolean
}
