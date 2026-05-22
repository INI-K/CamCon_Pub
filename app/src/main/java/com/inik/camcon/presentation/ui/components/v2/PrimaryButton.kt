package com.inik.camcon.presentation.ui.components.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import com.inik.camcon.presentation.theme.Accent
import com.inik.camcon.presentation.theme.ButtonText
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.IconSize
import com.inik.camcon.presentation.theme.OnAccent
import com.inik.camcon.presentation.theme.Radius
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.StrokeWidth
import com.inik.camcon.presentation.theme.TouchTarget

/**
 * V2 디자인 시스템 — Primary Button.
 *
 * [Accent] 배경 / [OnAccent] 텍스트 / 4dp([Radius.sm]) radius / 40dp([TouchTarget.min]) 높이.
 * [isLoading]=true 시 텍스트 자리에 작은 [CircularProgressIndicator] 표시 + 입력 비활성.
 * [enabled]=false 시 Accent.copy(alpha=0.4) 배경.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    val effectivelyEnabled = enabled && !isLoading
    Button(
        onClick = onClick,
        modifier = modifier
            .defaultMinSize(minHeight = TouchTarget.min)
            .height(TouchTarget.min),
        enabled = effectivelyEnabled,
        shape = RoundedCornerShape(Radius.sm),
        colors = ButtonDefaults.buttonColors(
            containerColor = Accent,
            contentColor = OnAccent,
            disabledContainerColor = Accent.copy(alpha = 0.4f),
            disabledContentColor = OnAccent.copy(alpha = 0.6f)
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = Spacing.lg,
            vertical = Spacing.xs
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(IconSize.sm),
                    strokeWidth = StrokeWidth.regular,
                    color = OnAccent
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = text,
                    style = ButtonText,
                    color = OnAccent.copy(alpha = 0.6f)
                )
            } else {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        modifier = Modifier.size(IconSize.md)
                    )
                    Spacer(Modifier.width(Spacing.sm))
                }
                Text(text = text, style = ButtonText)
            }
        }
    }
}

@Preview(name = "PrimaryButton default")
@Composable
private fun PrimaryButtonPreview() {
    CamConTheme {
        SurfaceV2(tier = 0) {
            PrimaryButton(
                text = "Connect Camera",
                onClick = {},
                modifier = Modifier.padding(Spacing.lg)
            )
        }
    }
}

@Preview(name = "PrimaryButton loading + icon + disabled")
@Composable
private fun PrimaryButtonStatesPreview() {
    CamConTheme {
        SurfaceV2(tier = 0) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                PrimaryButton(text = "Capture", onClick = {}, leadingIcon = Icons.Filled.PhotoCamera)
                PrimaryButton(text = "Loading…", onClick = {}, isLoading = true)
                PrimaryButton(text = "Disabled", onClick = {}, enabled = false)
            }
        }
    }
}
