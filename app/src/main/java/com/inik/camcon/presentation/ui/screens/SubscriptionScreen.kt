package com.inik.camcon.presentation.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Lock
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inik.camcon.R
import com.inik.camcon.domain.model.SubscriptionProduct
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.presentation.theme.Accent
import com.inik.camcon.presentation.theme.BodySmall
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.Caption
import com.inik.camcon.presentation.theme.DividerLine
import com.inik.camcon.presentation.theme.HeadingL
import com.inik.camcon.presentation.theme.HeadingM
import com.inik.camcon.presentation.theme.IconSize
import com.inik.camcon.presentation.theme.MicroLabel
import com.inik.camcon.presentation.theme.Radius
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.StrokeWidth
import com.inik.camcon.presentation.theme.SuccessV2
import com.inik.camcon.presentation.theme.Surface0
import com.inik.camcon.presentation.theme.TextPrimaryV2
import com.inik.camcon.presentation.theme.TextSecondaryV2
import com.inik.camcon.presentation.theme.TextTertiary
import com.inik.camcon.presentation.ui.components.v2.CardV2
import com.inik.camcon.presentation.ui.components.v2.ChipV2
import com.inik.camcon.presentation.ui.components.v2.PrimaryButton
import com.inik.camcon.presentation.ui.components.v2.SecondaryButton
import com.inik.camcon.presentation.ui.components.v2.SkeletonLoader
import com.inik.camcon.presentation.viewmodel.FilmLookSample
import com.inik.camcon.presentation.viewmodel.SubscriptionUiState
import com.inik.camcon.presentation.viewmodel.SubscriptionViewModel
import kotlinx.coroutines.launch

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
    val filmLookSamples by viewModel.filmLookSamples.collectAsStateWithLifecycle()
    val filmLookCount by viewModel.filmLookCount.collectAsStateWithLifecycle()
    SubscriptionScreenContent(
        uiState = uiState,
        filmLookSamples = filmLookSamples,
        filmLookCount = filmLookCount,
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
    onPurchaseSuccessConsumed: () -> Unit,
    filmLookSamples: List<FilmLookSample> = emptyList(),
    filmLookCount: Int = 0
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val purchaseStartedMessage = stringResource(R.string.subscription_snackbar_purchase_started)
    val genericErrorMessage = stringResource(R.string.subscription_snackbar_error)
    val comingSoonMessage = stringResource(R.string.subscription_coming_soon)

    // TODO(billing): 결제 미지원 — 업그레이드/구매 복원은 "추후 지원" 안내만 표시한다.
    //  결제 배선 복구 시 이 콜백 대신 onPurchase(productId) / onRestore() 를 다시 호출한다.
    val onComingSoon: () -> Unit = {
        coroutineScope.launch { snackbarHostState.showSnackbar(comingSoonMessage) }
    }

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
                        onUpgrade = onComingSoon,
                        // 필름 룩 시각 증거는 PRO 카드에만(무료 카드는 스트립 없음).
                        filmLookSamples = if (paywallTier.tier == SubscriptionTier.PRO) {
                            filmLookSamples
                        } else {
                            emptyList()
                        },
                        filmLookCount = filmLookCount
                    )
                }

                SecondaryButton(
                    text = stringResource(R.string.subscription_restore),
                    onClick = onComingSoon, // TODO(billing): 복구 시 onRestore 로 되돌린다.
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
    onUpgrade: () -> Unit,
    filmLookSamples: List<FilmLookSample> = emptyList(),
    filmLookCount: Int = 0
) {
    val isCurrent = currentTier == paywallTier.tier
    val isFree = paywallTier.tier == SubscriptionTier.FREE

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

            // 텍스트 체크리스트에 시각 증거 추가 — 필름 룩 썸네일 스트립(PRO 카드 전용).
            if (filmLookSamples.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.md))
                FilmLookStrip(samples = filmLookSamples, count = filmLookCount)
            }

            if (!isFree) {
                Spacer(Modifier.height(Spacing.md))
                // TODO(billing): 결제 미지원 — 탭 시 "추후 지원" 안내만 표시.
                //  복구 시 onClick 을 { (product?.productId ?: paywallTier.fallbackProductId)?.let(onPurchase) },
                //  enabled 를 !isCurrent && productId != null 로 되돌린다.
                PrimaryButton(
                    text = if (isCurrent) {
                        stringResource(R.string.subscription_badge_current)
                    } else {
                        stringResource(R.string.subscription_upgrade)
                    },
                    onClick = onUpgrade,
                    enabled = !isCurrent,
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

/**
 * 필름 룩 시각 증거 스트립 — 번들 샘플에 대표 LUT 을 적용한 썸네일 가로 스크롤 + "N가지 필름 룩" 카운터.
 * [count] 는 카탈로그 size 그대로(하드코딩 금지). 잠긴(PRO 전용) 표본은 자물쇠 오버레이.
 */
@Composable
private fun FilmLookStrip(samples: List<FilmLookSample>, count: Int) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (count > 0) {
            Text(
                text = stringResource(R.string.subscription_film_look_count, count),
                style = MicroLabel,
                color = Accent
            )
            Spacer(Modifier.height(Spacing.sm))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            samples.forEach { sample ->
                FilmLookChip(sample = sample)
            }
        }
    }
}

@Composable
private fun FilmLookChip(sample: FilmLookSample) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(Radius.sm))
            .border(
                BorderStroke(StrokeWidth.hairline, DividerLine),
                RoundedCornerShape(Radius.sm)
            )
    ) {
        val bmp = sample.thumbnail
        if (bmp != null && !bmp.isRecycled) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = sample.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            SkeletonLoader(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(Radius.sm),
                announceLoading = false
            )
        }

        // 잠긴(PRO 전용) 필름은 프리뷰를 가라앉히고 앰버 자물쇠 배지로 표시(룩 자체는 보이게).
        if (sample.locked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Surface0.copy(alpha = 0.45f))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(3.dp)
                    .size(18.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(Surface0.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = stringResource(R.string.fs_lut_locked_badge_cd),
                    tint = Accent,
                    modifier = Modifier.size(IconSize.xs)
                )
            }
        }
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
