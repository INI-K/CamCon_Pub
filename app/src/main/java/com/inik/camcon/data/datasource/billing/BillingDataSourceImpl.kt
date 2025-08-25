package com.inik.camcon.data.datasource.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.google.firebase.firestore.FirebaseFirestore
import com.inik.camcon.domain.model.SubscriptionProduct
import com.inik.camcon.domain.model.SubscriptionTier
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class BillingDataSourceImpl @Inject constructor(
    private val context: Context,
    private val firestore: FirebaseFirestore
) : BillingDataSource, PurchasesUpdatedListener {

    private var billingClient: BillingClient? = null
    private val purchaseUpdateFlow = callbackFlow<List<Purchase>> {
        close()
        awaitClose()
    }

    companion object {
        // Google Play Console에서 설정할 구독 상품 ID들
        const val BASIC_MONTHLY_PRODUCT_ID = "camcon_basic_monthly"
        const val BASIC_YEARLY_PRODUCT_ID = "camcon_basic_yearly"
        const val PRO_MONTHLY_PRODUCT_ID = "camcon_pro_monthly"
        const val PRO_YEARLY_PRODUCT_ID = "camcon_pro_yearly"
    }

    init {
        initializeBillingClient()
    }

    private fun initializeBillingClient() {
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases()
            .build()
    }

    private suspend fun ensureBillingClientReady(): Boolean {
        return suspendCancellableCoroutine { continuation ->
            billingClient?.let { client ->
                if (client.isReady) {
                    continuation.resume(true)
                    return@suspendCancellableCoroutine
                }

                client.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(billingResult: BillingResult) {
                        continuation.resume(billingResult.responseCode == BillingClient.BillingResponseCode.OK)
                    }

                    override fun onBillingServiceDisconnected() {
                        continuation.resume(false)
                    }
                })
            } ?: continuation.resume(false)
        }
    }

    override suspend fun getSubscriptionProducts(): List<SubscriptionProduct> {
        if (!ensureBillingClientReady()) return emptyList()

        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(BASIC_MONTHLY_PRODUCT_ID)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(BASIC_YEARLY_PRODUCT_ID)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRO_MONTHLY_PRODUCT_ID)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRO_YEARLY_PRODUCT_ID)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        return suspendCancellableCoroutine { continuation ->
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build()

            billingClient?.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    val subscriptionProducts = productDetailsList.map { productDetails ->
                        mapToSubscriptionProduct(productDetails)
                    }
                    continuation.resume(subscriptionProducts)
                } else {
                    continuation.resume(emptyList())
                }
            } ?: continuation.resume(emptyList())
        }
    }

    private fun mapToSubscriptionProduct(productDetails: ProductDetails): SubscriptionProduct {
        val subscriptionOfferDetails = productDetails.subscriptionOfferDetails?.firstOrNull()
        val pricingPhase = subscriptionOfferDetails?.pricingPhases?.pricingPhaseList?.firstOrNull()

        val tier = when (productDetails.productId) {
            BASIC_MONTHLY_PRODUCT_ID, BASIC_YEARLY_PRODUCT_ID -> SubscriptionTier.BASIC
            PRO_MONTHLY_PRODUCT_ID, PRO_YEARLY_PRODUCT_ID -> SubscriptionTier.PRO
            else -> SubscriptionTier.FREE
        }

        return SubscriptionProduct(
            productId = productDetails.productId,
            tier = tier,
            title = productDetails.title,
            description = productDetails.description,
            price = pricingPhase?.formattedPrice ?: "",
            currencyCode = pricingPhase?.priceCurrencyCode ?: "",
            priceAmountMicros = pricingPhase?.priceAmountMicros ?: 0L,
            billingPeriod = pricingPhase?.billingPeriod ?: ""
        )
    }

    override suspend fun launchBillingFlow(productId: String): Boolean {
        if (!ensureBillingClientReady()) return false

        val productDetails = getProductDetails(productId) ?: return false
        val activity = context as? Activity ?: return false

        val subscriptionOfferDetails = productDetails.subscriptionOfferDetails?.firstOrNull()
            ?: return false

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(subscriptionOfferDetails.offerToken)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        val billingResult = billingClient?.launchBillingFlow(activity, billingFlowParams)
        return billingResult?.responseCode == BillingClient.BillingResponseCode.OK
    }

    private suspend fun getProductDetails(productId: String): ProductDetails? {
        if (!ensureBillingClientReady()) return null

        return suspendCancellableCoroutine { continuation ->
            val productList = listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(productId)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            )

            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build()

            billingClient?.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    continuation.resume(productDetailsList.firstOrNull())
                } else {
                    continuation.resume(null)
                }
            } ?: continuation.resume(null)
        }
    }

    override suspend fun getActiveSubscriptions(): List<Purchase> {
        if (!ensureBillingClientReady()) return emptyList()

        return suspendCancellableCoroutine { continuation ->
            billingClient?.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            ) { billingResult, purchases ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    continuation.resume(purchases)
                } else {
                    continuation.resume(emptyList())
                }
            } ?: continuation.resume(emptyList())
        }
    }

    override fun getSubscriptionUpdates(): Flow<List<Purchase>> = purchaseUpdateFlow

    override suspend fun acknowledgeSubscription(purchase: Purchase): Boolean {
        if (!ensureBillingClientReady()) return false

        return suspendCancellableCoroutine { continuation ->
            val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

            billingClient?.acknowledgePurchase(acknowledgeParams) { billingResult ->
                continuation.resume(billingResult.responseCode == BillingClient.BillingResponseCode.OK)
            } ?: continuation.resume(false)
        }
    }

    override suspend fun cancelSubscription(purchaseToken: String): Boolean {
        // Google Play Billing에서는 앱에서 직접 구독을 취소할 수 없음
        // 사용자가 Google Play Store에서 직접 취소해야 함
        return false
    }

    override suspend fun syncSubscriptionWithFirebase(
        tier: SubscriptionTier,
        purchaseToken: String
    ) {
        // Firebase에 구독 정보 저장
        // 실제 구현에서는 사용자 ID와 함께 저장해야 함
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            // 구매 업데이트 처리
            for (purchase in purchases) {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    // 구독 확인 및 Firebase 동기화
                }
            }
        }
    }
}