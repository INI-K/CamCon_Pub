package com.inik.camcon.presentation.ui.components.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inik.camcon.presentation.theme.Accent
import com.inik.camcon.presentation.theme.BodySmall
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.StrokeWidth
import com.inik.camcon.presentation.theme.Surface1
import com.inik.camcon.presentation.theme.Surface3
import com.inik.camcon.presentation.theme.TextSecondaryV2

/**
 * V2 선형 프로그레스 바 — 1dp(thin) height, Surface3 트랙 + Accent fill.
 *
 * - progress == null  → indeterminate. 폭 30%의 인디케이터가 좌→우 슬라이드 (1500ms infinite restart).
 * - progress in 0..1  → determinate. fill 비율로 표시. 1f 초과/0f 미만은 coerce.
 */
@Composable
fun ProgressBarV2(
    progress: Float? = null,
    modifier: Modifier = Modifier,
    color: Color = Accent
) {
    val height = StrokeWidth.thin
    val trackShape = RoundedCornerShape(height / 2)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(trackShape)
            .background(Surface3)
    ) {
        if (progress == null) {
            IndeterminateFill(color = color)
        } else {
            val clamped = progress.coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = clamped)
                    .fillMaxHeight()
                    .background(color)
            )
        }
    }
}

/** 인디케이터가 차지하는 트랙 폭 비율. */
private const val BAR_FRACTION = 0.3f

@Composable
private fun IndeterminateFill(color: Color) {
    val transition = rememberInfiniteTransition(label = "progressIndeterminate")
    // `by` 위임 대신 State 로 받는다. 값을 graphicsLayer 람다 안에서 읽어야
    // Composition 이 아니라 Layer 단계에서만 무효화된다(표시되는 내내 60fps 재구성 방지).
    val translate = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "translate"
    )

    // BoxWithConstraints 를 쓰지 않는다 — 자식의 '구성'이 측정값에 의존하지 않으므로
    // 서브컴포지션 기계장치가 통째로 낭비였다. 폭은 비율로, 이동은 레이어 변환으로 처리한다.
    // 부모(ProgressBarV2 트랙)가 clip 하므로 트랙 밖으로 삐져나가지 않는다.
    Box(
        modifier = Modifier
            .fillMaxWidth(BAR_FRACTION)
            .fillMaxHeight()
            .graphicsLayer {
                // size 는 이 막대 자신의 픽셀 크기다. 부모 폭 = size.width / BAR_FRACTION.
                val parentWidth = size.width / BAR_FRACTION
                translationX = -size.width + (parentWidth + size.width) * translate.value
            }
            .background(color)
    )
}

@Preview(name = "ProgressBarV2 – Determinate", showBackground = true, backgroundColor = 0xFF1A1A1A)
@Composable
private fun ProgressBarV2DeterminatePreview() {
    CamConTheme {
        Surface(color = Surface1) {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Text("다운로드 25%", style = BodySmall, color = TextSecondaryV2)
                ProgressBarV2(progress = 0.25f)
                Text("다운로드 60%", style = BodySmall, color = TextSecondaryV2)
                ProgressBarV2(progress = 0.6f)
                Text("다운로드 100%", style = BodySmall, color = TextSecondaryV2)
                ProgressBarV2(progress = 1.0f)
            }
        }
    }
}

@Preview(name = "ProgressBarV2 – Indeterminate", showBackground = true, backgroundColor = 0xFF1A1A1A)
@Composable
private fun ProgressBarV2IndeterminatePreview() {
    CamConTheme {
        Surface(color = Surface1) {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Text("카메라 검색 중…", style = BodySmall, color = TextSecondaryV2)
                ProgressBarV2(progress = null)
            }
        }
    }
}
