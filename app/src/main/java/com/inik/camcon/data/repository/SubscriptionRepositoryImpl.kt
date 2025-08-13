package com.inik.camcon.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.inik.camcon.data.datasource.billing.BillingDataSource
import com.inik.camcon.domain.model.Subscription
import com.inik.camcon.domain.model.SubscriptionProduct
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.repository.SubscriptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubscriptionRepositoryImpl @Inject constructor(
    private val billingDataSource: BillingDataSource,
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : SubscriptionRepository {

    private companion object {
        const val USERS_COLLECTION = "users"
        const val SUBSCRIPTIONS_COLLECTION = "subscriptions"
    }

    override fun getUserSubscription(): Flow<Subscription> = flow {
        try {
            val userId = firebaseAuth.currentUser?.uid
            if (userId == null) {
                emit(Subscription(tier = SubscriptionTier.FREE))
                return@flow
            }

            // Firebase에서 구독 정보 조회
            val subscriptionDoc = firestore
                .collection(USERS_COLLECTION)
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

                val startDate = subscriptionDoc.getDate("startDate")
                val endDate = subscriptionDoc.getDate("endDate")
                val autoRenew = subscriptionDoc.getBoolean("autoRenew") ?: false
                val isActive = subscriptionDoc.getBoolean("isActive") ?: false

                emit(
                    Subscription(
                        tier = tier,
                        startDate = startDate,
                        endDate = endDate,
                        autoRenew = autoRenew,
                        isActive = isActive && (endDate?.after(Date()) ?: false)
                    )
                )
            } else {
                // Google Play Billing에서 활성 구독 확인
                val activeSubscriptions = billingDataSource.getActiveSubscriptions()
                if (activeSubscriptions.isNotEmpty()) {
                    // 활성 구독이 있으면 Firebase에 동기화
                    syncSubscriptionStatus()
                    // 재귀 호출로 업데이트된 정보 반환
                    getUserSubscription().collect { emit(it) }
                } else {
                    emit(Subscription(tier = SubscriptionTier.FREE))
                }
            }
        } catch (e: Exception) {
            emit(Subscription(tier = SubscriptionTier.FREE))
        }
    }

    override suspend fun getAvailableSubscriptions(): List<SubscriptionProduct> {
        return try {
            billingDataSource.getSubscriptionProducts()
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun purchaseSubscription(productId: String): Boolean {
        return try {
            billingDataSource.launchBillingFlow(productId)
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun syncSubscriptionStatus() {
        try {
            val userId = firebaseAuth.currentUser?.uid ?: return
            val activeSubscriptions = billingDataSource.getActiveSubscriptions()

            if (activeSubscriptions.isNotEmpty()) {
                val latestPurchase = activeSubscriptions.maxByOrNull { it.purchaseTime }
                if (latestPurchase != null) {
                    // 구독 상품 ID로부터 등급 결정
                    val tier = when {
                        latestPurchase.products.any { it.contains("admin") } -> SubscriptionTier.ADMIN
                        latestPurchase.products.any { it.contains("referrer") } -> SubscriptionTier.REFERRER
                        latestPurchase.products.any { it.contains("basic") } -> SubscriptionTier.BASIC
                        latestPurchase.products.any { it.contains("pro") } -> SubscriptionTier.PRO
                        else -> SubscriptionTier.FREE
                    }

                    // Firebase에 구독 정보 저장
                    val subscriptionData = mapOf(
                        "tier" to tier.name,
                        "startDate" to Date(latestPurchase.purchaseTime),
                        "endDate" to null, // 구독 만료일은 별도 계산 필요
                        "autoRenew" to latestPurchase.isAutoRenewing,
                        "isActive" to true,
                        "purchaseToken" to latestPurchase.purchaseToken,
                        "lastUpdated" to Date()
                    )

                    firestore
                        .collection(USERS_COLLECTION)
                        .document(userId)
                        .collection(SUBSCRIPTIONS_COLLECTION)
                        .document("current")
                        .set(subscriptionData)
                        .await()

                    // 구독 확인
                    billingDataSource.acknowledgeSubscription(latestPurchase)
                }
            } else {
                // 활성 구독이 없으면 FREE로 설정
                val subscriptionData = mapOf(
                    "tier" to SubscriptionTier.FREE.name,
                    "isActive" to false,
                    "lastUpdated" to Date()
                )

                firestore
                    .collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(SUBSCRIPTIONS_COLLECTION)
                    .document("current")
                    .set(subscriptionData)
                    .await()
            }
        } catch (e: Exception) {
            // 로그 처리
        }
    }

    override suspend fun cancelSubscription(): Boolean {
        // Google Play에서는 앱에서 직접 구독을 취소할 수 없음
        return false
    }

    override suspend fun restoreSubscription(): Boolean {
        return try {
            syncSubscriptionStatus()
            true
        } catch (e: Exception) {
            false
        }
    }
}