package com.inik.camcon.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
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
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions
) : AuthRepository {

    companion object {
        private const val TAG = "AuthRepository"
        private const val USERS_COLLECTION = "users"
        private const val SUBSCRIPTIONS_COLLECTION = "subscriptions"
        private const val REFERRALS_COLLECTION = "referrals"
        private const val REFERRAL_CODES_COLLECTION = "referral_codes"

        // 클라이언트는 subscriptions/referral_codes에 직접 쓸 수 없다(firestore.rules: write if false).
        // 권한 필요한 쓰기는 모두 Cloud Function 경유. (SubscriptionRepositoryImpl과 동일 보안 모델)
        private const val REDEEM_REFERRAL_FUNCTION = "redeemReferralCode"
        private const val ADMIN_SET_TIER_FUNCTION = "adminSetUserTier"
        private const val ADMIN_CREATE_REFERRAL_FUNCTION = "adminCreateReferralCode"
        private const val ADMIN_DELETE_REFERRAL_FUNCTION = "adminDeleteReferralCode"
        private const val ENSURE_SUBSCRIPTION_FUNCTION = "ensureUserSubscription"
    }

    override suspend fun signInWithGoogle(idToken: String): Result<User> {
        return try {
            Log.d(TAG, "Firebase Auth 로그인 시작")

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

                // 기본 구독 정보(FREE) 생성은 Cloud Function에 위임한다.
                // subscriptions는 클라이언트 직접 쓰기가 rules로 차단되므로(write if false)
                // 서버 Admin SDK가 멱등하게 FREE 문서를 생성한다. 실패해도 조회 경로가 FREE로 폴백하므로
                // 로그인 자체는 막지 않는다(다음 호출 시 재시도됨).
                runCatching {
                    functions.getHttpsCallable(ENSURE_SUBSCRIPTION_FUNCTION).call().await()
                }.onFailure {
                    Log.e(TAG, "FREE 구독 문서 생성 위임 실패 — FREE 폴백, 다음 호출 시 재시도", it)
                }

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

            val users = mutableListOf<User>()
            for (doc in snapshot.documents) {
                doc.data?.let { data ->
                    mapDocumentToUser(doc.id, data)?.let { user ->
                        users.add(user)
                    }
                }
            }
            users
        } catch (e: Exception) {
            Log.e(TAG, "전체 사용자 조회 실패", e)
            emptyList()
        }
    }

    override suspend fun updateUser(user: User): Boolean {
        return try {
            // 보호 필드(tier/updatedAt/updatedBy)는 클라이언트가 변경할 수 없다(firestore.rules:
            // protectedFieldsNotChanged). updatedAt을 포함하면 affectedKeys에 잡혀 PERMISSION_DENIED.
            // 서버 타임스탬프가 필요하면 Cloud Function으로 이전해야 한다.
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
                "appVersion" to user.appVersion
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
            // subscriptions는 클라이언트 직접 쓰기가 rules로 차단된다(write if false).
            // 관리자 티어 변경은 Cloud Function(adminSetUserTier)이 호출자의 ADMIN 권한을 서버에서
            // 검증한 뒤 Admin SDK로 기록한다. 호출자가 ADMIN이 아니면 CF가 PERMISSION_DENIED를 던진다.
            val payload = mapOf(
                "userId" to userId,
                "tier" to tier.name
            )
            functions.getHttpsCallable(ADMIN_SET_TIER_FUNCTION).call(payload).await()

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

            val users = mutableListOf<User>()
            for (doc in snapshot.documents) {
                doc.data?.let { data ->
                    mapDocumentToUser(doc.id, data)?.let { user ->
                        users.add(user)
                    }
                }
            }
            users
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
            // 보호 필드(updatedAt)는 제거한다 — 포함 시 protectedFieldsNotChanged 위반으로
            // PERMISSION_DENIED가 발생한다. referralCode/referralCodeUsedAt은 비보호 필드라 본인 쓰기 허용.
            // (추천 코드 소비 자체는 redeemReferralCode CF가 사용자 문서에 referralCode를 함께 기록한다.)
            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .update(
                    mapOf(
                        "referralCode" to referralCode,
                        "referralCodeUsedAt" to Date()
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
    private suspend fun mapDocumentToUser(userId: String, data: Map<String, Any>): User? {
        return try {
            // 구독 정보 조회
            val subscription = try {
                val subscriptionDoc = firestore.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(SUBSCRIPTIONS_COLLECTION)
                    .document("current")
                    .get()
                    .await()

                if (subscriptionDoc.exists()) {
                    val tierString = subscriptionDoc.getString("tier") ?: "FREE"
                    val tier = try {
                        SubscriptionTier.valueOf(tierString)
                    } catch (e: IllegalArgumentException) {
                        SubscriptionTier.FREE
                    }

                    Subscription(
                        tier = tier,
                        isActive = subscriptionDoc.getBoolean("isActive") ?: false,
                        startDate = subscriptionDoc.getDate("startDate"),
                        endDate = subscriptionDoc.getDate("endDate")
                    )
                } else {
                    Subscription(tier = SubscriptionTier.FREE)
                }
            } catch (e: Exception) {
                Log.w(TAG, "구독 정보 조회 실패: $userId", e)
                Subscription(tier = SubscriptionTier.FREE)
            }

            User(
                id = userId,
                email = data["email"] as? String ?: "",
                displayName = data["displayName"] as? String ?: "",
                photoUrl = data["photoUrl"] as? String,
                subscription = subscription,
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
            // referral_codes는 클라이언트 직접 쓰기가 rules로 차단된다(write if false).
            // Cloud Function(adminCreateReferralCode)이 호출자의 ADMIN 권한을 서버에서 검증한 뒤
            // Admin SDK로 생성한다. 호출자가 ADMIN이 아니면 CF가 PERMISSION_DENIED를 던진다.
            val payload = mapOf(
                "code" to code,
                "tier" to tier?.name,
                "description" to description
            )
            functions.getHttpsCallable(ADMIN_CREATE_REFERRAL_FUNCTION).call(payload).await()

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
            // referral_codes 소비와 그에 따른 티어 부여는 클라이언트가 직접 할 수 없다(write if false).
            // Cloud Function(redeemReferralCode)이 트랜잭션으로 코드 유효성·중복(1인1회)을 검증하고,
            // 코드에 설정된 티어를 Admin SDK로 부여한다. 호출자(request.auth.uid)에게만 적용되므로
            // userId 파라미터는 사용하지 않는다(서버가 인증 주체로 강제). 실패는 false로 호출자에 전달.
            val payload = mapOf("code" to code)
            functions.getHttpsCallable(REDEEM_REFERRAL_FUNCTION).call(payload).await()

            Log.i(TAG, "추천 코드 사용 처리 성공: $code by $userId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "추천 코드 사용 처리 실패: $code", e)
            false
        }
    }

    override suspend fun deleteReferralCode(code: String): Boolean {
        return try {
            // referral_codes는 클라이언트 직접 쓰기가 rules로 차단된다(write if false).
            // Cloud Function(adminDeleteReferralCode)이 호출자의 ADMIN 권한을 서버에서 검증한 뒤 삭제한다.
            val payload = mapOf("code" to code)
            functions.getHttpsCallable(ADMIN_DELETE_REFERRAL_FUNCTION).call(payload).await()

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
