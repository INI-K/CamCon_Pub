package com.inik.camcon.presentation.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inik.camcon.R
import com.inik.camcon.presentation.theme.Accent
import com.inik.camcon.presentation.theme.AccentMuted
import com.inik.camcon.presentation.theme.Body
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.HeadingL
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.Surface0
import com.inik.camcon.presentation.theme.Surface3
import com.inik.camcon.presentation.theme.TextPrimaryV2
import com.inik.camcon.presentation.theme.TextSecondaryV2
import com.inik.camcon.presentation.ui.components.v2.PrimaryButton
import com.inik.camcon.presentation.ui.components.v2.SecondaryButton
import kotlinx.coroutines.launch

/**
 * 첫 사용자 온보딩 3-스텝 페이저.
 *
 * - 스텝 1: USB OTG 연결 안내 (CameraAlt 아이콘)
 * - 스텝 2: Wi-Fi PTP-IP 연결 안내 (Wifi 아이콘)
 * - 스텝 3: 권한 안내 (Lock 아이콘)
 *
 * 다크 테마 고정, V2 토큰만 사용. 페이지 인디케이터는 단순 dot row.
 * 마지막 페이지에서 [PrimaryButton] "시작하기" 노출, 그 외에는 "다음".
 * 어느 단계에서나 우상단 [SecondaryButton] "건너뛰기" 가능.
 *
 * 표시 정책: `AppPreferencesDataSource.isOnboardingCompleted` 가 false 인 신규 사용자에게
 * MainActivity 진입 시 1회 표시되며, [onFinish] 콜백에서 `setOnboardingCompleted(true)` 후
 * MainScreen 으로 전환되도록 호출자가 책임진다.
 */
private data class OnboardingStep(
    val icon: ImageVector,
    val titleRes: Int,
    val descriptionRes: Int
)

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit
) {
    val steps = listOf(
        OnboardingStep(
            icon = Icons.Filled.CameraAlt,
            titleRes = R.string.onboarding_step1_title,
            descriptionRes = R.string.onboarding_step1_description
        ),
        OnboardingStep(
            icon = Icons.Filled.Wifi,
            titleRes = R.string.onboarding_step2_title,
            descriptionRes = R.string.onboarding_step2_description
        ),
        OnboardingStep(
            icon = Icons.Filled.Lock,
            titleRes = R.string.onboarding_step3_title,
            descriptionRes = R.string.onboarding_step3_description
        )
    )

    val pagerState = rememberPagerState(initialPage = 0) { steps.size }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface0)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.xl, vertical = Spacing.lg)
        ) {
            // 상단: Skip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                SecondaryButton(
                    text = stringResource(R.string.onboarding_skip),
                    onClick = onFinish
                )
            }

            Spacer(Modifier.height(Spacing.lg))

            // 페이지 본문
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                OnboardingPage(step = steps[page])
            }

            // 인디케이터
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.lg),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(steps.size) { index ->
                    val active = index == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (active) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(if (active) Accent else Surface3)
                    )
                }
            }

            // 하단 버튼
            val isLast = pagerState.currentPage == steps.size - 1
            PrimaryButton(
                text = stringResource(
                    if (isLast) R.string.onboarding_start else R.string.onboarding_next
                ),
                onClick = {
                    if (isLast) {
                        onFinish()
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun OnboardingPage(step: OnboardingStep) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 시각 강조 — 원형 AccentMuted 배경 + 토큰화된 아이콘
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(AccentMuted),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = step.icon,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(72.dp)
            )
        }

        Spacer(Modifier.height(Spacing.xl))

        Text(
            text = stringResource(step.titleRes),
            style = HeadingL,
            color = TextPrimaryV2,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(Spacing.md))

        Text(
            text = stringResource(step.descriptionRes),
            style = Body,
            color = TextSecondaryV2,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingScreenPreview() {
    CamConTheme {
        OnboardingScreen(onFinish = {})
    }
}
