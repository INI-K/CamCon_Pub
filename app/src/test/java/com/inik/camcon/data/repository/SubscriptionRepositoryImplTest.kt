package com.inik.camcon.data.repository

import app.cash.turbine.test
import com.android.billingclient.api.Purchase
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import com.inik.camcon.data.datasource.billing.BillingDataSource
import com.inik.camcon.domain.model.SubscriptionTier
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

/**
 * [SubscriptionRepositoryImpl] 단위 테스트 (감사 J항: 무테스트 해소).
 *
 * 대상:
 *  - getUserSubscription 폴백/파싱: 비로그인·조회실패 → 비권위 FREE, tier 파싱, 잘못된 tier → FREE
 *  - parseSubscription 만료 검사(:129): endDate 과거면 isActive 강등
 *  - syncSubscriptionStatus uid 가드(:161/:184): 비로그인 → billing 미접근
 *  - 활성 구매 처리: acknowledge + Cloud Function 검증 호출
 *
 * Firebase 는 MockK(Tasks.forResult/forException) 로 목킹한다(ConnectionReportRepositoryImplTest 패턴).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionRepositoryImplTest {

    private lateinit var billing: BillingDataSource
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var functions: FirebaseFunctions
    private lateinit var callableRef: HttpsCallableReference

    /** users/{uid}/subscriptions/current 문서 참조 (get() 스텁 대상) */
    private lateinit var currentDoc: DocumentReference

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var appScope: CoroutineScope

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        appScope = CoroutineScope(testDispatcher)

        billing = mockk(relaxed = true)
        // init 의 자동 구매 collect 가 즉시 끝나도록 빈 업데이트 스트림
        every { billing.getSubscriptionUpdates() } returns emptyFlow()

        auth = mockk()
        every { auth.currentUser } returns mockk<FirebaseUser> { every { uid } returns "uid-1" }

        callableRef = mockk()
        functions = mockk {
            every { getHttpsCallable("verifyAndRecordPurchase") } returns callableRef
        }

        // Firestore 체인: collection("users").document("uid-1").collection("subscriptions").document("current")
        currentDoc = mockk()
        val subsCollection = mockk<CollectionReference> {
            every { document("current") } returns currentDoc
        }
        val userDoc = mockk<DocumentReference> {
            every { collection("subscriptions") } returns subsCollection
        }
        val usersCollection = mockk<CollectionReference> {
            every { document("uid-1") } returns userDoc
        }
        firestore = mockk {
            every { collection("users") } returns usersCollection
        }
    }

    @After
    fun tearDown() {
        appScope.cancel()
        Dispatchers.resetMain()
    }

    private fun repo() = SubscriptionRepositoryImpl(billing, auth, firestore, functions, appScope)

    private fun snapshot(exists: Boolean, docData: Map<String, Any>?): DocumentSnapshot {
        val s = mockk<DocumentSnapshot>()
        every { s.exists() } returns exists
        every { s.data } returns docData
        return s
    }

    // === getUserSubscription 폴백 ===

    @Test
    fun `비로그인이면 비권위 FREE 를 방출하고 Firestore 를 조회하지 않는다`() = runTest {
        every { auth.currentUser } returns null

        val sub = repo().getUserSubscription().first()

        assertEquals(SubscriptionTier.FREE, sub.tier)
        assertFalse("서버가 부여한 FREE 가 아니므로 비권위", sub.isAuthoritative)
        verify(exactly = 0) { firestore.collection(any()) }
    }

    @Test
    fun `Firestore 조회 실패 시 비권위 FREE 로 폴백한다`() = runTest {
        every { currentDoc.get() } returns Tasks.forException(RuntimeException("network"))

        val sub = repo().getUserSubscription().first()

        assertEquals(SubscriptionTier.FREE, sub.tier)
        assertFalse("오프라인 강등 방지를 위해 비권위", sub.isAuthoritative)
    }

    // === parseSubscription ===

    @Test
    fun `활성 PRO 구독 문서를 권위값으로 파싱한다`() = runTest {
        val future = Date(System.currentTimeMillis() + 86_400_000L)
        every { currentDoc.get() } returns Tasks.forResult(
            snapshot(
                exists = true,
                docData = mapOf(
                    "tier" to "PRO",
                    "isActive" to true,
                    "endDate" to Timestamp(future)
                )
            )
        )

        val sub = repo().getUserSubscription().first()

        assertEquals(SubscriptionTier.PRO, sub.tier)
        assertTrue(sub.isActive)
        assertTrue("Firestore 실제 문서이므로 권위값", sub.isAuthoritative)
    }

    @Test
    fun `endDate 가 과거면 isActive=true 여도 만료로 강등한다`() = runTest {
        val past = Date(System.currentTimeMillis() - 86_400_000L)
        every { currentDoc.get() } returns Tasks.forResult(
            snapshot(
                exists = true,
                docData = mapOf(
                    "tier" to "PRO",
                    "isActive" to true,
                    "endDate" to Timestamp(past)
                )
            )
        )

        val sub = repo().getUserSubscription().first()

        assertEquals(SubscriptionTier.PRO, sub.tier)
        assertFalse("만료된 endDate 는 effectiveActive=false 로 강등", sub.isActive)
    }

    @Test
    fun `알 수 없는 tier 문자열은 FREE 로 대체한다`() = runTest {
        every { currentDoc.get() } returns Tasks.forResult(
            snapshot(
                exists = true,
                docData = mapOf("tier" to "GOLD", "isActive" to true)
            )
        )

        val sub = repo().getUserSubscription().first()

        assertEquals(SubscriptionTier.FREE, sub.tier)
    }

    // === syncSubscriptionStatus uid 가드 / 구매 처리 ===

    @Test
    fun `syncSubscriptionStatus 는 비로그인이면 billing 을 건드리지 않는다`() = runTest {
        every { auth.currentUser } returns null

        repo().syncSubscriptionStatus()

        coVerify(exactly = 0) { billing.getActiveSubscriptions() }
    }

    @Test
    fun `syncSubscriptionStatus 는 활성 구독이 없으면 CF 를 호출하지 않는다`() = runTest {
        coEvery { billing.getActiveSubscriptions() } returns emptyList()

        repo().syncSubscriptionStatus()

        verify(exactly = 0) { functions.getHttpsCallable(any()) }
    }

    @Test
    fun `syncSubscriptionStatus 는 활성 구매를 acknowledge 하고 CF 검증을 호출한다`() = runTest {
        val purchase = mockk<Purchase>(relaxed = true) {
            every { purchaseState } returns Purchase.PurchaseState.PURCHASED
            every { isAcknowledged } returns false
            every { products } returns arrayListOf("pro_monthly")
            every { purchaseToken } returns "token-abc"
            every { purchaseTime } returns 1000L
        }
        coEvery { billing.getActiveSubscriptions() } returns listOf(purchase)
        coEvery { billing.acknowledgeSubscription(purchase) } returns true
        every { callableRef.call(any()) } returns
            Tasks.forResult(mockk<HttpsCallableResult>(relaxed = true))

        repo().syncSubscriptionStatus()

        coVerify(exactly = 1) { billing.acknowledgeSubscription(purchase) }
        verify(exactly = 1) {
            callableRef.call(mapOf("purchaseToken" to "token-abc", "productId" to "pro_monthly"))
        }
    }

    @Test
    fun `검증 성공 시 subscriptionRefreshSignals 로 재조회 신호를 방출한다`() = runTest {
        val purchase = mockk<Purchase>(relaxed = true) {
            every { purchaseState } returns Purchase.PurchaseState.PURCHASED
            every { isAcknowledged } returns true
            every { products } returns arrayListOf("pro_monthly")
            every { purchaseToken } returns "token-abc"
            every { purchaseTime } returns 1000L
        }
        coEvery { billing.getActiveSubscriptions() } returns listOf(purchase)
        every { callableRef.call(any()) } returns
            Tasks.forResult(mockk<HttpsCallableResult>(relaxed = true))

        val repository = repo()
        repository.subscriptionRefreshSignals.test {
            repository.syncSubscriptionStatus()
            // 검증 성공 후 신호 1건
            awaitItem()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `이미 acknowledge 된 구매는 재-acknowledge 하지 않고 CF 만 호출한다`() = runTest {
        val purchase = mockk<Purchase>(relaxed = true) {
            every { purchaseState } returns Purchase.PurchaseState.PURCHASED
            every { isAcknowledged } returns true
            every { products } returns arrayListOf("pro_yearly")
            every { purchaseToken } returns "token-xyz"
            every { purchaseTime } returns 2000L
        }
        coEvery { billing.getActiveSubscriptions() } returns listOf(purchase)
        every { callableRef.call(any()) } returns
            Tasks.forResult(mockk<HttpsCallableResult>(relaxed = true))

        repo().syncSubscriptionStatus()

        coVerify(exactly = 0) { billing.acknowledgeSubscription(any()) }
        verify(exactly = 1) {
            callableRef.call(mapOf("purchaseToken" to "token-xyz", "productId" to "pro_yearly"))
        }
    }
}
