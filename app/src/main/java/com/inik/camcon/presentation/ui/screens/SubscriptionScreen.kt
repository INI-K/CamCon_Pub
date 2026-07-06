package com.inik.camcon.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inik.camcon.R
import com.inik.camcon.domain.model.SubscriptionProduct
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.presentation.theme.Accent
import com.inik.camcon.presentation.theme.BodySmall
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.Caption
import com.inik.camcon.presentation.theme.HeadingL
import com.inik.camcon.presentation.theme.HeadingM
import com.inik.camcon.presentation.theme.IconSize
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.SuccessV2
import com.inik.camcon.presentation.theme.Surface0
import com.inik.camcon.presentation.theme.TextPrimaryV2
import com.inik.camcon.presentation.theme.TextSecondaryV2
import com.inik.camcon.presentation.theme.TextTertiary
import com.inik.camcon.presentation.ui.components.v2.CardV2
import com.inik.camcon.presentation.ui.components.v2.ChipV2
import com.inik.camcon.presentation.ui.components.v2.PrimaryButton
import com.inik.camcon.presentation.ui.components.v2.SecondaryButton
import com.inik.camcon.presentation.viewmodel.SubscriptionUiState
import com.inik.camcon.presentation.viewmodel.SubscriptionViewModel

/**
 * 페이월 폴백 productId — Google Play Billing 상품 조회가 비어 있을 때
 * (개발 빌드 / Play Console 미등록 / 빌링 미가용) 사용하는 정적 SKU.
 *
 * 이 값들은 Play Console에 등록한 구독 상품 ID와 동일해야 하며,
 * data 레이어 `BillingDataSourceImpl`의 동명 상수와 의도적으로 일치시킨다.
 * (presentation→data 의존을 만들지 않기 위해 상수만 로컬에 둔다.)
 */
private const val PRO_MONTHLY_PRODUCT_ID = "camcon_pro_monthly"

/**
 * 페이월에 노출할 유료 티어 카드 정의.
 * 각 티어의 혜택 stringRes 목록과 폴백 productId를 함께 보관한다.
 */
private data class PaywallTier(
    val tier: SubscriptionTier,
    val titleRes: Int,
    val benefitsRes: List<Int>,
    val fallbackProductId: String?
)

private val freeTier = PaywallTier(
    tier = SubscriptionTier.FREE,
    titleRes = R.string.subscription_tier_free,
    benefitsRes = listOf(
        R.string.subscription_benefit_jpg,
        R.string.subscription_benefit_basic_control,
        R.string.subscription_benefit_free_resolution
    ),
    fallbackProductId = null
)

private val proTier = PaywallTier(
    tier = SubscriptionTier.PRO,
    titleRes = R.string.subscription_tier_pro,
    benefitsRes = listOf(
        R.string.subscription_benefit_raw,
        R.string.subscription_benefit_advanced_control,
        R.string.subscription_benefit_raw_download
    ),
    fallbackProductId = PRO_MONTHLY_PRODUCT_ID
)

private val paywallTiers = listOf(freeTier, proTier)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    viewModel: SubscriptionViewModel = hiltViewModel(),
    onBackClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SubscriptionScreenContent(
        uiState = uiState,
        onBackClick = onBackClick,
        onPurchase = viewModel::purchase,
        onRestore = viewModel::restore,
        onErrorConsumed = viewModel::consumeError,
        onPurchaseSuccessConsumed = viewModel::consumePurchaseSuccess
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubscriptionScreenContent(
    uiState: SubscriptionUiState,
    onBackClick: () -> Unit,
    onPurchase: (String) -> Unit,
    onRestore: () -> Unit,
    onErrorConsumed: () -> Unit,
    onPurchaseSuccessConsumed: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    val purchaseStartedMessage = stringResource(R.string.subscription_snackbar_purchase_started)
    val genericErrorMessage = stringResource(R.string.subscription_snackbar_error)

    LaunchedEffect(uiState.error) {
        val error = uiState.error
        if (error != null) {
            snackbarHostState.showSnackbar(error.ifBlank { genericErrorMessage })
            onErrorConsumed()
        }
    }

    LaunchedEffect(uiState.purchaseSuccess) {
        if (uiState.purchaseSuccess) {
            snackbarHostState.showSnackbar(purchaseStartedMessage)
            onPurchaseSuccessConsumed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = {
                    Text(
                        text = stringResource(R.string.subscription_title),
                        style = HeadingL,
                        color = TextPrimaryV2
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.subscription_back),
                            tint = TextPrimaryV2
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface0,
                    titleContentColor = TextPrimaryV2,
                    navigationIconContentColor = TextPrimaryV2
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Surface0,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Surface0)
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.base, vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                CurrentTierBanner(currentTier = uiState.currentTier)

                Text(
                    text = stringResource(R.string.subscription_choose_plan),
                    style = HeadingM,
                    color = TextSecondaryV2
                )

                paywallTiers.forEach { paywallTier ->
                    TierCard(
                        paywallTier = paywallTier,
                        currentTier = uiState.currentTier,
                        product = findProduct(uiState.products, paywallTier.tier),
                        purchaseInProgress = uiState.purchaseInProgress,
                        onPurchase = onPurchase
                    )
                }

                SecondaryButton(
                    text = stringResource(R.string.subscription_restore),
                    onClick = onRestore,
                    enabled = !uiState.purchaseInProgress,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = stringResource(R.string.subscription_disclaimer),
                    style = Caption,
                    color = TextTertiary
                )

                Spacer(Modifier.height(Spacing.lg))
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Accent)
                }
            }
        }
    }
}

/** [products]에서 해당 [tier]에 매칭되는 첫 상품을 찾는다. 없으면 null. */
private fun findProduct(
    products: List<SubscriptionProduct>,
    tier: SubscriptionTier
): SubscriptionProduct? = products.firstOrNull { it.tier == tier }

@Composable
private fun CurrentTierBanner(currentTier: SubscriptionTier) {
    CardV2(
        modifier = Modifier.fillMaxWidth(),
        border = true
    ) {
        Column(modifier = Modifier.padding(Spacing.base)) {
            Text(
                text = stringResource(R.string.subscription_current_plan),
                style = Caption,
                color = TextTertiary
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = tierLabel(currentTier),
                style = HeadingL,
                color = Accent
            )
        }
    }
}

@Composable
private fun TierCard(
    paywallTier: PaywallTier,
    currentTier: SubscriptionTier,
    product: SubscriptionProduct?,
    purchaseInProgress: Boolean,
    onPurchase: (String) -> Unit
) {
    val isCurrent = currentTier == paywallTier.tier
    val isFree = paywallTier.tier == SubscriptionTier.FREE
    // 결제에 사용할 productId: Billing 상품이 있으면 그 ID, 없으면 폴백 SKU.
    val productId = product?.productId ?: paywallTier.fallbackProductId

    CardV2(
        modifier = Modifier.fillMaxWidth(),
        border = true
    ) {
        Column(modifier = Modifier.padding(Spacing.base)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(paywallTier.titleRes),
                    style = HeadingM,
                    color = TextPrimaryV2
                )
                if (isCurrent) {
                    ChipV2(text = stringResource(R.string.subscription_badge_current))
                } else if (product != null && product.price.isNotBlank()) {
                    Text(
                        text = product.price,
                        style = HeadingM,
                        color = Accent
                    )
                }
            }

            Spacer(Modifier.height(Spacing.md))

            paywallTier.benefitsRes.forEach { benefitRes ->
                BenefitRow(text = stringResource(benefitRes))
            }

            if (!isFree) {
                Spacer(Modifier.height(Spacing.md))
                PrimaryButton(
                    text = if (isCurrent) {
                        stringResource(R.string.subscription_badge_current)
                    } else {
                        stringResource(R.string.subscription_upgrade)
                    },
                    onClick = { productId?.let(onPurchase) },
                    enabled = !isCurrent && productId != null,
                    isLoading = purchaseInProgress,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun BenefitRow(text: String) {
    Row(
        modifier = Modifier.padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = SuccessV2,
            modifier = Modifier.size(IconSize.sm)
        )
        Text(
            text = text,
            style = BodySmall,
            color = TextSecondaryV2
        )
    }
}

@Composable
private fun tierLabel(tier: SubscriptionTier): String = stringResource(
    when (tier) {
        SubscriptionTier.FREE -> R.string.subscription_tier_free
        SubscriptionTier.BASIC -> R.string.subscription_tier_basic
        SubscriptionTier.PRO -> R.string.subscription_tier_pro
        SubscriptionTier.REFERRER -> R.string.subscription_tier_referrer
        SubscriptionTier.ADMIN -> R.string.subscription_tier_admin
    }
)

@Preview(name = "SubscriptionScreen – FREE", showBackground = true, backgroundColor = 0xFF0E0E0E)
@Composable
private fun SubscriptionScreenFreePreview() {
    CamConTheme {
        SubscriptionScreenContent(
            uiState = SubscriptionUiState(
                currentTier = SubscriptionTier.FREE,
                isLoading = false,
                billingUnavailable = true
            ),
            onBackClick = {},
            onPurchase = {},
            onRestore = {},
            onErrorConsumed = {},
            onPurchaseSuccessConsumed = {}
        )
    }
}

@Preview(name = "SubscriptionScreen – PRO", showBackground = true, backgroundColor = 0xFF0E0E0E)
@Composable
private fun SubscriptionScreenProPreview() {
    CamConTheme {
        SubscriptionScreenContent(
            uiState = SubscriptionUiState(
                currentTier = SubscriptionTier.PRO,
                isLoading = false,
                billingUnavailable = true
            ),
            onBackClick = {},
            onPurchase = {},
            onRestore = {},
            onErrorConsumed = {},
            onPurchaseSuccessConsumed = {}
        )
    }
}
