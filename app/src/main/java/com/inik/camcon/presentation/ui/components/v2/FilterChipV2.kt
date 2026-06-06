package com.inik.camcon.presentation.ui.components.v2

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inik.camcon.presentation.theme.Accent
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.Caption
import com.inik.camcon.presentation.theme.DividerLine
import com.inik.camcon.presentation.theme.IconSize
import com.inik.camcon.presentation.theme.OnAccent
import com.inik.camcon.presentation.theme.Radius
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.Surface1
import com.inik.camcon.presentation.theme.TextSecondaryV2

/**
 * CamCon V2 — FilterChipV2
 *
 * 토글 가능한 필터 칩. 28dp 높이, Radius.sm(4dp).
 * - 선택 안 됨: 1px DividerLine border, 투명 배경, TextSecondaryV2.
 * - 선택됨: Accent 배경, OnAccent 텍스트, border 없음.
 * - 비활성: alpha 0.4, 클릭 무효.
 *
 * 디자인 가이드: docs/DESIGN_SYSTEM_V2.md §7 Chip 계열.
 */
@Composable
fun FilterChipV2(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(Radius.sm)
    val bg = if (selected) Accent else Color.Transparent
    val contentColor = if (selected) OnAccent else TextSecondaryV2

    // WCAG 2.2 SC 4.1.2 — `selected` 시맨틱으로 토글 상태를 announce.
    val baseModifier = modifier
        .height(28.dp)
        .alpha(if (enabled) 1f else 0.4f)
        .clip(shape)
        .background(bg, shape)
        .semantics {
            role = Role.Button
            this.selected = selected
        }
        .let { m ->
            if (selected) m else m.border(1.dp, DividerLine, shape)
        }
        .let { m ->
            if (enabled) m.clickable(onClick = onClick) else m
        }
        .padding(horizontal = Spacing.sm)

    Row(
        modifier = baseModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.height(IconSize.sm)
            )
        }
        Text(
            text = text,
            style = Caption,
            color = contentColor
        )
    }
}

// ---------------- Previews ----------------

@Preview(name = "FilterChipV2 / states row", showBackground = true, backgroundColor = 0xFF1A1A1A)
@Composable
private fun FilterChipV2StatesPreview() {
    CamConTheme {
        Box(
            modifier = Modifier
                .background(Surface1)
                .padding(Spacing.base)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FilterChipV2(text = "All", selected = true, onClick = {})
                FilterChipV2(text = "RAW", selected = false, onClick = {})
                FilterChipV2(text = "JPG", selected = false, onClick = {})
            }
        }
    }
}

@Preview(name = "FilterChipV2 / icon + disabled", showBackground = true, backgroundColor = 0xFF1A1A1A)
@Composable
private fun FilterChipV2DisabledPreview() {
    CamConTheme {
        Box(
            modifier = Modifier
                .background(Surface1)
                .padding(Spacing.base)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FilterChipV2(
                    text = "Burst",
                    selected = true,
                    onClick = {},
                    leadingIcon = Icons.Filled.Bolt
                )
                FilterChipV2(
                    text = "Locked",
                    selected = false,
                    onClick = {},
                    leadingIcon = Icons.Filled.Check,
                    enabled = false
                )
            }
        }
    }
}
