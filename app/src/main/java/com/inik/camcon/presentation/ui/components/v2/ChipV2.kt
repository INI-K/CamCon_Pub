package com.inik.camcon.presentation.ui.components.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.Caption
import com.inik.camcon.presentation.theme.IconSize
import com.inik.camcon.presentation.theme.Radius
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.Surface1
import com.inik.camcon.presentation.theme.Surface2
import com.inik.camcon.presentation.theme.TextSecondaryV2

/**
 * CamCon V2 — ChipV2
 *
 * 정적 라벨 또는 가벼운 액션용 칩. 28dp 높이, Radius.sm(4dp), Surface2 배경.
 * onClick=null이면 ripple 없이 정적 라벨로 동작한다.
 *
 * 디자인 가이드: docs/DESIGN_SYSTEM_V2.md §7 Chip 계열.
 */
@Composable
fun ChipV2(
    text: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(Radius.sm)
    val base = modifier
        .height(28.dp)
        .clip(shape)
        .background(Surface2, shape)

    // WCAG 2.2 SC 4.1.2 — onClick 이 있을 때 Role.Button 명시.
    val container = if (onClick != null) {
        base
            .semantics { role = Role.Button }
            .clickable(onClick = onClick)
    } else {
        base
    }

    Row(
        modifier = container.padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = TextSecondaryV2,
                modifier = Modifier.height(IconSize.sm)
            )
        }
        Text(
            text = text,
            style = Caption,
            color = TextSecondaryV2
        )
    }
}

// ---------------- Previews ----------------

@Preview(name = "ChipV2 / static label", showBackground = true, backgroundColor = 0xFF1A1A1A)
@Composable
private fun ChipV2StaticPreview() {
    CamConTheme {
        Box(
            modifier = Modifier
                .background(Surface1)
                .padding(Spacing.base)
        ) {
            ChipV2(text = "RAW + JPG")
        }
    }
}

@Preview(name = "ChipV2 / clickable + icon", showBackground = true, backgroundColor = 0xFF1A1A1A)
@Composable
private fun ChipV2ClickablePreview() {
    CamConTheme {
        Box(
            modifier = Modifier
                .background(Surface1)
                .padding(Spacing.base)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ChipV2(
                    text = "Filter",
                    leadingIcon = Icons.Filled.FilterAlt,
                    onClick = {}
                )
                ChipV2(
                    text = "Favorite",
                    leadingIcon = Icons.Filled.Star,
                    onClick = {}
                )
            }
        }
    }
}
