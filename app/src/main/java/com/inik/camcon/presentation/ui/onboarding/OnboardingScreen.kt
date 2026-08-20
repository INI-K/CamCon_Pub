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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inik.camcon.R
import com.inik.camcon.presentation.theme.Accent
import com.inik.camcon.presentation.theme.AccentMuted
import com.inik.camcon.presentation.theme.BodySmall
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.DisplayL
import com.inik.camcon.presentation.theme.MonoNumeric
import com.inik.camcon.presentation.theme.Radius
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.Surface0
import com.inik.camcon.presentation.theme.Surface3
import com.inik.camcon.presentation.theme.TextPrimaryV2
import com.inik.camcon.presentation.theme.TextSecondaryV2
import com.inik.camcon.presentation.theme.TextTertiary
import com.inik.camcon.presentation.ui.components.v2.PrimaryButton
import com.inik.camcon.presentation.ui.components.v2.SecondaryButton
import kotlinx.coroutines.launch

/** Hero 타이틀 읽기 폭 상한 — Medium/Expanded 폭에서 제목이 끝까지 늘어나지 않게 끊는다. */
private val TitleMeasureMax = 420.dp

/** 본문 읽기 폭 상한(대략 60자 기준). */
private val BodyMeasureMax = 380.dp

/** 하단 CTA 폭 — 전폭 대신 폭을 끊어 좌측 앵커 리듬을 만든다. */
private val CtaWidthMin = 200.dp
private val CtaWidthMax = 360.dp

/** 페이지 인디케이터 — 점(dot) 대신 필름 스트립 톤의 세그먼트 트랙. */
private val IndicatorTrackHeight = 3.dp
private val IndicatorSegmentActive = 24.dp
private val IndicatorSegmentIdle = 10.dp

/**
 * 스텝 마크(원형 앰버 배경 + 아이콘) 치수.
 * Hero가 타이틀로 옮겨졌으므로 마크는 초점을 넘기고 한 단 축소한다.
 * (하단 앵커 구성에서 소형 단말 세로 여유를 확보하는 목적도 겸한다.)
 */
private val StepMarkSize = 120.dp
private val StepMarkIconSize = 56.dp

/**
 * 첫 사용자 온보딩 3-스텝 페이저.
 *
 * - 스텝 1: USB OTG 연결 안내 (CameraAlt 아이콘)
 * - 스텝 2: Wi-Fi PTP-IP 연결 안내 (Wifi 아이콘)
 * - 스텝 3: 권한 안내 (Lock 아이콘)
 *
 * 다크 테마 고정, V2 토큰만 사용. 앱 셸의 유일한 서사 표면이므로 스텝 타이틀이
 * [DisplayL] Hero 슬롯을 갖고, 그 위에 [MonoNumeric] 스텝 카운터가 eyebrow로 붙는다.
 * 좌측 앵커 + 하단 정렬 에디토리얼 구성(중앙 정렬은 빈 화면/스플래시 전용).
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
                OnboardingPage(
                    step = steps[page],
                    stepIndex = page,
                    stepCount = steps.size
                )
            }

            // 인디케이터 — 좌측 앵커 세그먼트 트랙(타이틀·CTA와 같은 좌측 라인)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(steps.size) { index ->
                    val active = index == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .height(IndicatorTrackHeight)
                            .width(if (active) IndicatorSegmentActive else IndicatorSegmentIdle)
                            .clip(RoundedCornerShape(Radius.sm))
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
                modifier = Modifier.widthIn(min = CtaWidthMin, max = CtaWidthMax)
            )
        }
    }
}

@Composable
private fun OnboardingPage(
    step: OnboardingStep,
    stepIndex: Int,
    stepCount: Int
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Bottom
    ) {
        // 시각 강조 — 원형 AccentMuted 배경 + 토큰화된 아이콘
        Box(
            modifier = Modifier
                .size(StepMarkSize)
                .clip(CircleShape)
                .background(AccentMuted),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = step.icon,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(StepMarkIconSize)
            )
        }

        Spacer(Modifier.height(Spacing.xl))

        // eyebrow — 탭형 숫자 스텝 카운터. Hero 아래 단계로 내려간 보조 슬롯.
        Text(
            text = stringResource(R.string.onboarding_step_counter, stepIndex + 1, stepCount),
            style = MonoNumeric,
            color = TextTertiary
        )

        Spacer(Modifier.height(Spacing.sm))

        // Hero — 이 표면의 유일한 최대 타이포 슬롯(34sp) : 본문 13sp 대비 2.6배.
        Text(
            text = stringResource(step.titleRes),
            style = DisplayL,
            color = TextPrimaryV2,
            modifier = Modifier.widthIn(max = TitleMeasureMax)
        )

        Spacer(Modifier.height(Spacing.md))

        Text(
            text = stringResource(step.descriptionRes),
            style = BodySmall,
            color = TextSecondaryV2,
            modifier = Modifier.widthIn(max = BodyMeasureMax)
        )

        Spacer(Modifier.height(Spacing.lg))
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingScreenPreview() {
    CamConTheme {
        OnboardingScreen(onFinish = {})
    }
}
