package com.inik.camcon.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.inik.camcon.domain.model.ReferralCode
import com.inik.camcon.domain.model.Subscription
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.model.User
import com.inik.camcon.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : AuthRepository {

    companion object {
        private const val TAG = "AuthRepository"
        private const val USERS_COLLECTION = "users"
        private const val SUBSCRIPTIONS_COLLECTION = "subscriptions"
        private const val REFERRALS_COLLECTION = "referrals"
        private const val REFERRAL_CODES_COLLECTION = "referral_codes"
    }

    override suspend fun signInWithGoogle(idToken: String): Result<User> {
        return try {
            Log.d(TAG, "Firebase Auth 로그인 시작, idToken: ${idToken.take(20)}...")

            // Google ID Token으로 Firebase 인증 크리덴셜 생성
            val credential =
                com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)

            // Firebase Auth로 로그인
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val firebaseUser = authResult.user ?: throw Exception("Firebase 사용자 정보가 null입니다")

            Log.i(TAG, "Firebase Auth 로그인 성공: ${firebaseUser.uid}")

            // Firestore에서 사용자 정보 조회
            var user = getUserById(firebaseUser.uid)

            // 신규 사용자인 경우 Firestore에 사용자 정보 생성
            if (user == null) {
                Log.i(TAG, "신규 사용자, Firestore에 정보 생성: ${firebaseUser.uid}")

                val newUserData = mapOf(
                    "email" to (firebaseUser.email ?: ""),
                    "displayName" to (firebaseUser.displayName ?: ""),
                    "photoUrl" to firebaseUser.photoUrl?.toString(),
                    "createdAt" to Date(),
                    "lastLoginAt" to Date(),
                    "isActive" to true,
                    "referralCode" to null,
                    "referredBy" to null,
                    "totalReferrals" to 0,
                    "deviceInfo" to null,  // 필요시 별도로 수집
                    "appVersion" to null   // 필요시 별도로 수집
                )

                firestore.collection(USERS_COLLECTION)
                    .document(firebaseUser.uid)
                    .set(newUserData)
                    .await()

                // 기본 구독 정보 생성 (FREE 티어)
                val defaultSubscription = mapOf(
                    "tier" to SubscriptionTier.FREE.name,
                    "isActive" to true,
                    "startDate" to Date(),
                    "endDate" to null,
                    "updatedAt" to Date(),
                    "updatedBy" to "system"
                )

                firestore.collection(USERS_COLLECTION)
                    .document(firebaseUser.uid)
                    .collection(SUBSCRIPTIONS_COLLECTION)
                    .document("current")
                    .set(defaultSubscription)
                    .await()

                // User 객체 생성
                user = User(
                    id = firebaseUser.uid,
                    email = firebaseUser.email ?: "",
                    displayName = firebaseUser.displayName ?: "",
                    photoUrl = firebaseUser.photoUrl?.toString(),
                    subscription = Subscription(tier = SubscriptionTier.FREE),
                    createdAt = Date(),
                    lastLoginAt = Date(),
                    isActive = true,
                    referralCode = null,
                    referredBy = null,
                    totalReferrals = 0,
                    deviceInfo = null,
                    appVersion = null
                )
            } else {
                // 기존 사용자인 경우 마지막 로그인 시간 업데이트
                firestore.collection(USERS_COLLECTION)
                    .document(firebaseUser.uid)
                    .update("lastLoginAt", Date())
                    .await()

                user = user.copy(lastLoginAt = Date())
                Log.i(TAG, "기존 사용자 로그인: ${user.displayName}")
            }

            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Google 로그인 실패", e)
            Result.failure(e)
        }
    }

    override suspend fun signOut() {
        firebaseAuth.signOut()
    }

    override fun getCurrentUser(): Flow<User?> = flow {
        val firebaseUser = firebaseAuth.currentUser
        if (firebaseUser != null) {
            val user = getUserById(firebaseUser.uid)
            emit(user)
        } else {
            emit(null)
        }
    }

    override suspend fun getUserById(userId: String): User? {
        return try {
            val doc = firestore.collection(USERS_COLLECTION)
                .document(userId)
                .get()
                .await()

            if (doc.exists()) {
                val data = doc.data ?: return null
                mapDocumentToUser(userId, data)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "사용자 조회 실패: $userId", e)
            null
        }
    }

    override suspend fun getAllUsers(): List<User> {
        return try {
            val snapshot = firestore.collection(USERS_COLLECTION)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                doc.data?.let { data ->
                    mapDocumentToUser(doc.id, data)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "전체 사용자 조회 실패", e)
            emptyList()
        }
    }

    override suspend fun updateUser(user: User): Boolean {
        return try {
            val userMap = mapOf(
                "email" to user.email,
                "displayName" to user.displayName,
                "photoUrl" to user.photoUrl,
                "lastLoginAt" to (user.lastLoginAt ?: Date()),
                "isActive" to user.isActive,
                "referralCode" to user.referralCode,
                "referredBy" to user.referredBy,
                "totalReferrals" to user.totalReferrals,
                "deviceInfo" to user.deviceInfo,
                "appVersion" to user.appVersion,
                "updatedAt" to Date()
            )

            firestore.collection(USERS_COLLECTION)
                .document(user.id)
                .update(userMap)
                .await()

            Log.i(TAG, "사용자 정보 업데이트 성공: ${user.id}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "사용자 정보 업데이트 실패: ${user.id}", e)
            false
        }
    }

    override suspend fun updateUserTier(userId: String, tier: SubscriptionTier): Boolean {
        return try {
            // 사용자의 구독 정보 업데이트
            val subscriptionData = mapOf(
                "tier" to tier.name,
                "isActive" to true,
                "startDate" to Date(),
                "endDate" to null,
                "updatedAt" to Date(),
                "updatedBy" to (firebaseAuth.currentUser?.uid ?: "system")
            )

            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(SUBSCRIPTIONS_COLLECTION)
                .document("current")
                .set(subscriptionData)
                .await()

            Log.i(TAG, "사용자 티어 업데이트 성공: $userId → $tier")
            true
        } catch (e: Exception) {
            Log.e(TAG, "사용자 티어 업데이트 실패: $userId", e)
            false
        }
    }

    override suspend fun deactivateUser(userId: String): Boolean {
        return try {
            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .update(
                    mapOf(
                        "isActive" to false,
                        "deactivatedAt" to Date(),
                        "deactivatedBy" to (firebaseAuth.currentUser?.uid ?: "system")
                    )
                )
                .await()

            Log.i(TAG, "사용자 비활성화 성공: $userId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "사용자 비활성화 실패: $userId", e)
            false
        }
    }

    override suspend fun reactivateUser(userId: String): Boolean {
        return try {
            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .update(
                    mapOf(
                        "isActive" to true,
                        "reactivatedAt" to Date(),
                        "reactivatedBy" to (firebaseAuth.currentUser?.uid ?: "system")
                    )
                )
                .await()

            Log.i(TAG, "사용자 재활성화 성공: $userId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "사용자 재활성화 실패: $userId", e)
            false
        }
    }

    override suspend fun getUsersByTier(tier: SubscriptionTier): List<User> {
        return try {
            // 구독 정보에서 특정 티어의 사용자들을 찾기
            val users = mutableListOf<User>()
            val usersSnapshot = firestore.collection(USERS_COLLECTION).get().await()

            for (userDoc in usersSnapshot.documents) {
                val subscriptionDoc = firestore.collection(USERS_COLLECTION)
                    .document(userDoc.id)
                    .collection(SUBSCRIPTIONS_COLLECTION)
                    .document("current")
                    .get()
                    .await()

                if (subscriptionDoc.exists()) {
                    val tierString = subscriptionDoc.getString("tier")
                    if (tierString == tier.name) {
                        userDoc.data?.let { data ->
                            mapDocumentToUser(userDoc.id, data)?.let { user ->
                                users.add(user)
                            }
                        }
                    }
                }
            }

            users
        } catch (e: Exception) {
            Log.e(TAG, "티어별 사용자 조회 실패: $tier", e)
            emptyList()
        }
    }

    override suspend fun searchUsers(query: String): List<User> {
        return try {
            val snapshot = firestore.collection(USERS_COLLECTION)
                .orderBy("displayName")
                .startAt(query)
                .endAt(query + "\uf8ff")
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                doc.data?.let { data ->
                    mapDocumentToUser(doc.id, data)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "사용자 검색 실패: $query", e)
            emptyList()
        }
    }

    override suspend fun getReferralStats(userId: String): Map<String, Any> {
        return try {
            val referralsSnapshot = firestore.collection(REFERRALS_COLLECTION)
                .whereEqualTo("referrerId", userId)
                .get()
                .await()

            val totalReferrals = referralsSnapshot.size()
            val activeReferrals = referralsSnapshot.documents.count { doc ->
                doc.getBoolean("isActive") == true
            }

            mapOf(
                "totalReferrals" to totalReferrals,
                "activeReferrals" to activeReferrals,
                "referralCode" to (getUserById(userId)?.referralCode ?: ""),
                "lastUpdated" to Date()
            )
        } catch (e: Exception) {
            Log.e(TAG, "추천 통계 조회 실패: $userId", e)
            emptyMap()
        }
    }

    override suspend fun generateReferralCode(userId: String): String? {
        return try {
            val referralCode = "REF${UUID.randomUUID().toString().take(8).uppercase()}"

            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .update(
                    mapOf(
                        "referralCode" to referralCode,
                        "referralCodeGeneratedAt" to Date()
                    )
                )
                .await()

            Log.i(TAG, "추천 코드 생성 성공: $userId → $referralCode")
            referralCode
        } catch (e: Exception) {
            Log.e(TAG, "추천 코드 생성 실패: $userId", e)
            null
        }
    }

    override suspend fun isUserLoggedIn(): Boolean {
        return try {
            val user = firebaseAuth.currentUser
            user != null
        } catch (e: Exception) {
            Log.e(TAG, "로그인 상태 확인 실패", e)
            false
        }
    }

    override suspend fun updateUserReferralCode(userId: String, referralCode: String): Boolean {
        return try {
            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .update(
                    mapOf(
                        "referralCode" to referralCode,
                        "referralCodeUsedAt" to Date(),
                        "updatedAt" to Date()
                    )
                )
                .await()

            Log.i(TAG, "사용자 추천 코드 정보 업데이트 성공: $userId → $referralCode")
            true
        } catch (e: Exception) {
            Log.e(TAG, "사용자 추천 코드 정보 업데이트 실패: $userId", e)
            false
        }
    }

    /**
     * Firestore 문서를 User 객체로 매핑
     */
    private fun mapDocumentToUser(userId: String, data: Map<String, Any>): User? {
        return try {
            User(
                id = userId,
                email = data["email"] as? String ?: "",
                displayName = data["displayName"] as? String ?: "",
                photoUrl = data["photoUrl"] as? String,
                subscription = Subscription(), // 별도로 구독 정보 조회 필요
                createdAt = data["createdAt"] as? Date,
                lastLoginAt = data["lastLoginAt"] as? Date,
                isActive = data["isActive"] as? Boolean ?: true,
                referralCode = data["referralCode"] as? String,
                referredBy = data["referredBy"] as? String,
                totalReferrals = (data["totalReferrals"] as? Long)?.toInt() ?: 0,
                deviceInfo = data["deviceInfo"] as? String,
                appVersion = data["appVersion"] as? String
            )
        } catch (e: Exception) {
            Log.e(TAG, "문서 매핑 실패: $userId", e)
            null
        }
    }

    // 추천인 코드 관리 메서드들
    override suspend fun createReferralCode(
        code: String,
        tier: SubscriptionTier?,
        description: String?
    ): Boolean {
        return try {
            val referralCode = ReferralCode(
                code = code,
                isUsed = false,
                usedBy = null,
                usedAt = null,
                createdAt = Date(),
                createdBy = firebaseAuth.currentUser?.uid ?: "system",
                tier = tier,
                description = description
            )

            val data = mapOf(
                "isUsed" to referralCode.isUsed,
                "usedBy" to referralCode.usedBy,
                "usedAt" to referralCode.usedAt,
                "createdAt" to referralCode.createdAt,
                "createdBy" to referralCode.createdBy,
                "tier" to referralCode.tier?.name,
                "description" to referralCode.description
            )

            firestore.collection(REFERRAL_CODES_COLLECTION)
                .document(code)
                .set(data)
                .await()

            Log.i(TAG, "추천 코드 생성 성공: $code")
            true
        } catch (e: Exception) {
            Log.e(TAG, "추천 코드 생성 실패: $code", e)
            false
        }
    }

    override suspend fun getAllReferralCodes(): List<ReferralCode> {
        return try {
            val snapshot = firestore.collection(REFERRAL_CODES_COLLECTION)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                mapDocumentToReferralCode(doc.id, doc.data)
            }
        } catch (e: Exception) {
            Log.e(TAG, "전체 추천 코드 조회 실패", e)
            emptyList()
        }
    }

    override suspend fun getAvailableReferralCodes(): List<ReferralCode> {
        return try {
            val snapshot = firestore.collection(REFERRAL_CODES_COLLECTION)
                .whereEqualTo("isUsed", false)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                mapDocumentToReferralCode(doc.id, doc.data)
            }
        } catch (e: Exception) {
            Log.e(TAG, "사용 가능한 추천 코드 조회 실패", e)
            emptyList()
        }
    }

    override suspend fun getUsedReferralCodes(): List<ReferralCode> {
        return try {
            val snapshot = firestore.collection(REFERRAL_CODES_COLLECTION)
                .whereEqualTo("isUsed", true)
                .orderBy("usedAt", Query.Direction.DESCENDING)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                mapDocumentToReferralCode(doc.id, doc.data)
            }
        } catch (e: Exception) {
            Log.e(TAG, "사용된 추천 코드 조회 실패", e)
            emptyList()
        }
    }

    override suspend fun validateReferralCode(code: String): ReferralCode? {
        return try {
            val doc = firestore.collection(REFERRAL_CODES_COLLECTION)
                .document(code)
                .get()
                .await()

            if (doc.exists() && doc.data != null) {
                mapDocumentToReferralCode(doc.id, doc.data)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "추천 코드 검증 실패: $code", e)
            null
        }
    }

    override suspend fun useReferralCode(code: String, userId: String): Boolean {
        return try {
            val updateData = mapOf(
                "isUsed" to true,
                "usedBy" to userId,
                "usedAt" to Date()
            )

            firestore.collection(REFERRAL_CODES_COLLECTION)
                .document(code)
                .update(updateData)
                .await()

            Log.i(TAG, "추천 코드 사용 처리 성공: $code by $userId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "추천 코드 사용 처리 실패: $code", e)
            false
        }
    }

    override suspend fun deleteReferralCode(code: String): Boolean {
        return try {
            firestore.collection(REFERRAL_CODES_COLLECTION)
                .document(code)
                .delete()
                .await()

            Log.i(TAG, "추천 코드 삭제 성공: $code")
            true
        } catch (e: Exception) {
            Log.e(TAG, "추천 코드 삭제 실패: $code", e)
            false
        }
    }

    /**
     * Firestore 문서를 ReferralCode 객체로 매핑
     */
    private fun mapDocumentToReferralCode(code: String, data: Map<String, Any>?): ReferralCode? {
        return try {
            if (data == null) return null

            val tierString = data["tier"] as? String
            val tier = tierString?.let {
                try {
                    SubscriptionTier.valueOf(it)
                } catch (e: IllegalArgumentException) {
                    null
                }
            }

            ReferralCode(
                code = code,
                isUsed = data["isUsed"] as? Boolean ?: false,
                usedBy = data["usedBy"] as? String,
                usedAt = data["usedAt"] as? Date,
                createdAt = data["createdAt"] as? Date ?: Date(),
                createdBy = data["createdBy"] as? String ?: "",
                tier = tier,
                description = data["description"] as? String
            )
        } catch (e: Exception) {
            Log.e(TAG, "ReferralCode 문서 매핑 실패: $code", e)
            null
        }
    }
}
