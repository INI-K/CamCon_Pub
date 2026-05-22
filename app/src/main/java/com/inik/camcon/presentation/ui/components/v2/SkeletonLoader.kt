package com.inik.camcon.presentation.ui.components.v2

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inik.camcon.presentation.theme.Accent
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.Radius
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.Surface1
import com.inik.camcon.presentation.theme.Surface2
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * V2 스켈레톤 로더 — Surface2 베이스 + Accent@10% 그라데이션 shimmer.
 *
 * shimmer 좌→우 슬라이드 1200ms infinite restart.
 * width/height는 호출부 Modifier로 제어.
 */
@Composable
fun SkeletonLoader(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Radius.md)
) {
    var widthPx by remember { mutableStateOf(0f) }

    val transition = rememberInfiniteTransition(label = "skeletonShimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "translate"
    )

    val shimmerColor = Accent.copy(alpha = 0.10f)
    val gradientWidth = if (widthPx > 0f) widthPx else 1f
    val startX = -gradientWidth + (gradientWidth * 2f * translate)

    val brush = Brush.linearGradient(
        colors = listOf(
            Color.Transparent,
            shimmerColor,
            Color.Transparent
        ),
        start = androidx.compose.ui.geometry.Offset(startX, 0f),
        end = androidx.compose.ui.geometry.Offset(startX + gradientWidth, 0f)
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(Surface2)
            .onGloballyPositioned { coords ->
                widthPx = coords.size.width.toFloat()
            }
            .background(brush)
    )
}

@Preview(name = "SkeletonLoader – Card Variants", showBackground = true, backgroundColor = 0xFF1A1A1A)
@Composable
private fun SkeletonLoaderCardPreview() {
    CamConTheme {
        Surface(color = Surface1) {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                SkeletonLoader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )
                SkeletonLoader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                )
                SkeletonLoader(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(14.dp)
                )
            }
        }
    }
}

@Preview(name = "SkeletonLoader – Avatar Row", showBackground = true, backgroundColor = 0xFF1A1A1A)
@Composable
private fun SkeletonLoaderAvatarPreview() {
    CamConTheme {
        Surface(color = Surface1) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.padding(Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                SkeletonLoader(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    SkeletonLoader(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(16.dp)
                    )
                    SkeletonLoader(
                        modifier = Modifier
                            .fillMaxWidth(0.4f)
                            .height(12.dp)
                    )
                }
            }
        }
    }
}
