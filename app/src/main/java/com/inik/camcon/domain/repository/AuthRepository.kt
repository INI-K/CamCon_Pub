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
    suspend fun deleteReferralCode(code: String): Boolean

    /**
     * 추천코드 적용 — 검증·소비·티어부여를 서버(Cloud Function redeemReferralCode)가
     * 단일 트랜잭션으로 처리한다. 클라이언트는 referral_codes/subscriptions에 직접 쓸 수 없다
     * (firestore.rules: write=false). 성공 시 부여된 티어(코드에 티어가 없으면 null)를 반환.
     */
    suspend fun redeemReferralCode(code: String): Result<SubscriptionTier?>

    /**
     * 계정·데이터 삭제 — 서버(Cloud Function deleteAccount)가 Admin SDK로 호출자의
     * Firestore 사용자 문서·구독·레퍼럴·구매기록과 Firebase Auth 사용자까지 삭제한다.
     * 클라이언트는 users/subscriptions에 직접 쓸 수 없고 Auth 삭제도 재인증이 필요하므로 서버가 처리한다.
     * 성공 시 로컬 로그아웃까지 수행한다.
     */
    suspend fun deleteAccount(): Result<Unit>
}
