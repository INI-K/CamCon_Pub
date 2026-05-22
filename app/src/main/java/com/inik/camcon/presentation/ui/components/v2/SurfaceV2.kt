package com.inik.camcon.presentation.ui.components.v2

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.DividerLine
import com.inik.camcon.presentation.theme.Radius
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.StrokeWidth
import com.inik.camcon.presentation.theme.Surface0
import com.inik.camcon.presentation.theme.Surface1
import com.inik.camcon.presentation.theme.Surface2
import com.inik.camcon.presentation.theme.Surface3
import com.inik.camcon.presentation.theme.Surface4

/**
 * V2 디자인 시스템 — 기본 Surface.
 *
 * Editorial pro tool 톤. flat (elevation 0). 5단계 surface tier 선택,
 * 옵션 1px [DividerLine] 보더.
 *
 * @param tier 0..4 → [Surface0]..[Surface4]. 범위 외는 [Surface1]로 클램프.
 * @param border true 시 [DividerLine] 1px 테두리.
 */
@Composable
fun SurfaceV2(
    modifier: Modifier = Modifier,
    tier: Int = 1,
    border: Boolean = false,
    shape: Shape = RoundedCornerShape(Radius.md),
    content: @Composable () -> Unit
) {
    val background: Color = when (tier) {
        0 -> Surface0
        1 -> Surface1
        2 -> Surface2
        3 -> Surface3
        4 -> Surface4
        else -> Surface1
    }
    val borderStroke: BorderStroke? = if (border) BorderStroke(StrokeWidth.thin, DividerLine) else null

    Surface(
        modifier = modifier,
        color = background,
        contentColor = androidx.compose.material3.LocalContentColor.current,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        shape = shape,
        border = borderStroke,
        content = content
    )
}

@Preview(name = "SurfaceV2 tier=1", showBackground = false)
@Composable
private fun SurfaceV2Preview() {
    CamConTheme {
        SurfaceV2(tier = 1, border = true) {
            Box(Modifier.padding(Spacing.lg))
        }
    }
}

@Preview(name = "SurfaceV2 tier=3 no border")
@Composable
private fun SurfaceV2HighTierPreview() {
    CamConTheme {
        SurfaceV2(tier = 3) {
            Box(Modifier.padding(Spacing.lg))
        }
    }
}
