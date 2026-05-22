package com.inik.camcon.presentation.ui.components.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.IconSize
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.TextDisabled
import com.inik.camcon.presentation.theme.TextPrimaryV2
import com.inik.camcon.presentation.theme.TouchTarget

/**
 * V2 디자인 시스템 — Icon Button.
 *
 * [TouchTarget.min] (40dp) 터치 타깃 + [IconSize.lg] (24dp) 아이콘.
 * Material 3 기본 Ripple 유지. Tint는 기본 [TextPrimaryV2].
 */
@Composable
fun IconButtonV2(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = TextPrimaryV2,
    enabled: Boolean = true
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(TouchTarget.min),
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = tint,
            disabledContentColor = TextDisabled
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(IconSize.lg)
        )
    }
}

@Preview(name = "IconButtonV2 default")
@Composable
private fun IconButtonV2Preview() {
    CamConTheme {
        SurfaceV2(tier = 0) {
            IconButtonV2(
                icon = Icons.Filled.Settings,
                contentDescription = "Settings",
                onClick = {},
                modifier = Modifier.padding(Spacing.md)
            )
        }
    }
}

@Preview(name = "IconButtonV2 states")
@Composable
private fun IconButtonV2StatesPreview() {
    CamConTheme {
        SurfaceV2(tier = 0) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.padding(Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                IconButtonV2(icon = Icons.Filled.Settings, contentDescription = "Settings", onClick = {})
                IconButtonV2(icon = Icons.Filled.Close, contentDescription = "Close", onClick = {}, enabled = false)
            }
        }
    }
}
