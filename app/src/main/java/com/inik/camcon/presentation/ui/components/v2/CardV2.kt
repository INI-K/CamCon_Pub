package com.inik.camcon.presentation.ui.components.v2

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.Radius
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.TextPrimaryV2
import com.inik.camcon.presentation.theme.TextSecondaryV2

/**
 * V2 디자인 시스템 — Card.
 *
 * [SurfaceV2] tier=2, 6dp([Radius.md]) radius, elevation 0, 옵션 1px [DividerLine] border.
 * 사진 카드 / 설정 그룹 / 연결 항목 등 콘텐츠 묶음에 사용.
 */
@Composable
fun CardV2(
    modifier: Modifier = Modifier,
    border: Boolean = false,
    shape: Shape = RoundedCornerShape(Radius.md),
    content: @Composable ColumnScope.() -> Unit
) {
    SurfaceV2(
        modifier = modifier,
        tier = 2,
        border = border,
        shape = shape
    ) {
        Column(content = content)
    }
}

@Preview(name = "CardV2 no border")
@Composable
private fun CardV2Preview() {
    CamConTheme {
        SurfaceV2(tier = 0) {
            CardV2(modifier = Modifier.padding(Spacing.lg)) {
                Column(Modifier.padding(Spacing.base)) {
                    Text("Card title", color = TextPrimaryV2)
                    Text("Card body", color = TextSecondaryV2)
                }
            }
        }
    }
}

@Preview(name = "CardV2 with border")
@Composable
private fun CardV2BorderedPreview() {
    CamConTheme {
        SurfaceV2(tier = 0) {
            CardV2(
                modifier = Modifier.padding(Spacing.lg),
                border = true
            ) {
                Column(Modifier.padding(Spacing.base)) {
                    Text("Bordered card", color = TextPrimaryV2)
                }
            }
        }
    }
}
