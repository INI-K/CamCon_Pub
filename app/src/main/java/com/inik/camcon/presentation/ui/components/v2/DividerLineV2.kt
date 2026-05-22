package com.inik.camcon.presentation.ui.components.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.DividerLine
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.StrokeWidth

/**
 * V2 디자인 시스템 — 1px Divider.
 *
 * horizontal=true → 너비 채움 + [StrokeWidth.thin] 높이.
 * horizontal=false → 높이 채움 + [StrokeWidth.thin] 너비 (vertical divider).
 */
@Composable
fun DividerLineV2(
    modifier: Modifier = Modifier,
    color: Color = DividerLine,
    horizontal: Boolean = true
) {
    val sized = if (horizontal) {
        modifier
            .fillMaxWidth()
            .height(StrokeWidth.thin)
    } else {
        modifier
            .fillMaxHeight()
            .width(StrokeWidth.thin)
    }
    Box(modifier = sized.background(color))
}

@Preview(name = "DividerLineV2 horizontal")
@Composable
private fun DividerLineV2HorizontalPreview() {
    CamConTheme {
        SurfaceV2(tier = 1) {
            Box(Modifier.padding(Spacing.lg)) {
                DividerLineV2()
            }
        }
    }
}

@Preview(name = "DividerLineV2 vertical")
@Composable
private fun DividerLineV2VerticalPreview() {
    CamConTheme {
        SurfaceV2(tier = 1) {
            Box(
                Modifier
                    .padding(Spacing.lg)
                    .height(48.dp)
            ) {
                DividerLineV2(horizontal = false)
            }
        }
    }
}
