package com.inik.camcon.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.inik.camcon.domain.model.ReferralCode
import com.inik.camcon.domain.model.ReferralRedeemException
import com.inik.camcon.domain.model.ReferralRedeemReason
import com.inik.camcon.domain.model.Subscription
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.model.User
import com.inik.camcon.domain.repository.AuthRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import java.io.IOException
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
        private const val REDEEM_REFERRAL_FUNCTION = "redeemReferralCode"
        private const val BOOTSTRAP_FUNCTION = "ensureUserBootstrap"

        /**
         * PII(UID/식별자) 마스킹: 앞 4글자만 노출 + ***.
         * 로그/분석 도구에 식별자 평문이 흘러가지 않도록 사용한다.
         */
        private fun maskId(value: String?): String {
            if (value.isNullOrBlank()) return "<blank>"
            return if (value.length <= 4) "***" else value.take(4) + "***"
        }
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

            Log.i(TAG, "Firebase Auth 로그인 성공: ${maskId(firebaseUser.uid)}")

            // Firestore에서 사용자 정보 조회
            var user = getUserById(firebaseUser.uid)

            // 신규 사용자인 경우 Firestore에 사용자 정보 생성
            if (user == null) {
                Log.i(TAG, "신규 사용자, Firestore에 정보 생성: ${maskId(firebaseUser.uid)}")

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

                // 기본 FREE 구독 문서는 서버 권위(ensureUserBootstrap CF)로 생성 — rules가 클라 쓰기를 금지.
                // CF 미배포/일시 실패여도 로그인은 진행(구독 문서 부재 시 FREE 폴백이 이미 동작).
                try {
                    functions.getHttpsCallable(BOOTSTRAP_FUNCTION).call().await()
                } catch (e: Exception) {
                    Log.w(TAG, "기본 구독 부트스트랩 실패(로그인은 계속): ${maskId(firebaseUser.uid)}", e)
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

                // 구독 문서가 아직 없으면(과거 CF 미배포 시기 첫 로그인 등) 멱등 부트스트랩 재시도.
                // 폴백 FREE(startDate=null)와 실제 문서(FREE 포함, startDate 존재)를 startDate로 구분한다.
                if (user.subscription.startDate == null) {
                    try {
                        functions.getHttpsCallable(BOOTSTRAP_FUNCTION).call().await()
                    } catch (e: Exception) {
                        Log.w(TAG, "구독 부트스트랩 재시도 실패(로그인은 계속): ${maskId(firebaseUser.uid)}", e)
                    }
                }

                user = user.copy(lastLoginAt = Date())
                // displayName은 사용자 PII에 해당하므로 식별자 마스킹만 노출
                Log.i(TAG, "기존 사용자 로그인: ${maskId(firebaseUser.uid)}")
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
            Log.e(TAG, "사용자 조회 실패: ${maskId(userId)}", e)
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

            Log.i(TAG, "사용자 정보 업데이트 성공: ${maskId(user.id)}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "사용자 정보 업데이트 실패: ${maskId(user.id)}", e)
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

            Log.i(TAG, "사용자 티어 업데이트 성공: ${maskId(userId)} → $tier")
            true
        } catch (e: Exception) {
            Log.e(TAG, "사용자 티어 업데이트 실패: ${maskId(userId)}", e)
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

            Log.i(TAG, "사용자 비활성화 성공: ${maskId(userId)}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "사용자 비활성화 실패: ${maskId(userId)}", e)
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

            Log.i(TAG, "사용자 재활성화 성공: ${maskId(userId)}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "사용자 재활성화 실패: ${maskId(userId)}", e)
            false
        }
    }

    override suspend fun getUsersByTier(tier: SubscriptionTier): List<User> {
        return try {
            // 구독 정보에서 특정 티어의 사용자들을 찾기
            val usersSnapshot = firestore.collection(USERS_COLLECTION).get().await()

            // 사용자별 구독 조회를 직렬 await(N+1)하지 않고 병렬로 수행해 라운드트립을 단축
            coroutineScope {
                usersSnapshot.documents.map { userDoc ->
                    async {
                        val subscriptionDoc = firestore.collection(USERS_COLLECTION)
                            .document(userDoc.id)
                            .collection(SUBSCRIPTIONS_COLLECTION)
                            .document("current")
                            .get()
                            .await()

                        if (subscriptionDoc.exists() &&
                            subscriptionDoc.getString("tier") == tier.name
                        ) {
                            userDoc.data?.let { data -> mapDocumentToUser(userDoc.id, data) }
                        } else {
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
            }
        } catch (e: Exception) {
            Log.e(TAG, "티어별 사용자 조회 실패: $tier", e)
            emptyList()
        }
    }

    override suspend fun searchUsers(query: String): List<User> {
        // query 값 자체가 사용자 검색어(이름 일부)이므로 평문 노출 금지
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
            // 검색어 자체가 PII일 수 있으므로 길이만 로그
            Log.e(TAG, "사용자 검색 실패 (queryLen=${query.length})", e)
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
            Log.e(TAG, "추천 통계 조회 실패: ${maskId(userId)}", e)
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

            // 추천 코드는 비식별 토큰이지만 user 식별자는 마스킹
            Log.i(TAG, "추천 코드 생성 성공: ${maskId(userId)} → $referralCode")
            referralCode
        } catch (e: Exception) {
            Log.e(TAG, "추천 코드 생성 실패: ${maskId(userId)}", e)
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

    /**
     * 추천코드 적용 — Cloud Function(redeemReferralCode)에 위임.
     * 서버가 referral_codes 검증·소비 및 subscriptions 티어부여를 단일 트랜잭션으로 처리한다.
     * 클라이언트 직접 Firestore 접근은 firestore.rules(write=false)로 차단되므로 이 경로만 유효하다.
     */
    override suspend fun redeemReferralCode(code: String): Result<SubscriptionTier?> {
        // 관리자 코드는 대문자 — 소문자 입력을 관용해 정규화 후 전송(서버도 동일 정규화).
        val normalized = code.trim().uppercase()
        return try {
            val payload = mapOf("code" to normalized)
            val result = functions
                .getHttpsCallable(REDEEM_REFERRAL_FUNCTION)
                .call(payload)
                .await()

            @Suppress("UNCHECKED_CAST")
            val data = result.data as? Map<String, Any?>
            val tierName = data?.get("tier") as? String
            val tier = tierName?.let {
                try {
                    SubscriptionTier.valueOf(it)
                } catch (e: IllegalArgumentException) {
                    null
                }
            }

            Log.i(TAG, "추천코드 적용 성공: ${maskId(normalized)} → ${tier?.name ?: "(no tier)"}")
            Result.success(tier)
        } catch (e: FirebaseFunctionsException) {
            // 서버 HttpsError 코드를 도메인 사유로 매핑해 상위(UseCase/ViewModel)가 화면 메시지를 고르게 한다.
            val reason = when (e.code) {
                FirebaseFunctionsException.Code.NOT_FOUND -> ReferralRedeemReason.NOT_FOUND
                FirebaseFunctionsException.Code.ALREADY_EXISTS -> ReferralRedeemReason.ALREADY_USED
                FirebaseFunctionsException.Code.PERMISSION_DENIED -> ReferralRedeemReason.SELF_REFERRAL
                FirebaseFunctionsException.Code.FAILED_PRECONDITION -> ReferralRedeemReason.NOT_GRANTABLE
                FirebaseFunctionsException.Code.UNAUTHENTICATED -> ReferralRedeemReason.UNAUTHENTICATED
                FirebaseFunctionsException.Code.UNAVAILABLE,
                FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> ReferralRedeemReason.NETWORK
                else -> ReferralRedeemReason.UNKNOWN
            }
            Log.w(TAG, "추천코드 적용 거부: ${maskId(normalized)} (code=${e.code}, reason=$reason)", e)
            Result.failure(ReferralRedeemException(reason, e))
        } catch (e: IOException) {
            Log.e(TAG, "추천코드 적용 네트워크 오류: ${maskId(normalized)}", e)
            Result.failure(ReferralRedeemException(ReferralRedeemReason.NETWORK, e))
        } catch (e: Exception) {
            Log.e(TAG, "추천코드 적용 실패: ${maskId(normalized)}", e)
            Result.failure(ReferralRedeemException(ReferralRedeemReason.UNKNOWN, e))
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
                Log.w(TAG, "구독 정보 조회 실패: ${maskId(userId)}", e)
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
            Log.e(TAG, "문서 매핑 실패: ${maskId(userId)}", e)
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
