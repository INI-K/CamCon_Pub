package com.inik.camcon.presentation.ui.components.v2

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.inik.camcon.R
import com.inik.camcon.presentation.theme.ButtonText
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.DividerLine
import com.inik.camcon.presentation.theme.IconSize
import com.inik.camcon.presentation.theme.Radius
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.StrokeWidth
import com.inik.camcon.presentation.theme.TextDisabled
import com.inik.camcon.presentation.theme.TextPrimaryV2
import com.inik.camcon.presentation.theme.TouchTarget

/**
 * V2 디자인 시스템 — Secondary Button.
 *
 * 1px [DividerLine] 보더 / Transparent 배경 / [TextPrimaryV2] 텍스트 /
 * 4dp([Radius.sm]) radius / [TouchTarget.min](44dp) 높이.
 * 보조 액션 (취소, 재시도, 모드 전환) 용도.
 *
 * 접근성(WCAG 2.2 SC 4.1.2, SC 2.5.8):
 * - isLoading=true 일 때 stateDescription 으로 진행 상태 발화.
 * - 높이는 [TouchTarget.min](44dp) 으로 보장.
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    val effectivelyEnabled = enabled && !isLoading
    val borderColor = if (effectivelyEnabled) DividerLine else DividerLine.copy(alpha = 0.5f)
    val loadingLabel = stringResource(R.string.cd_button_loading)
    val a11y = if (isLoading) {
        Modifier.semantics { stateDescription = loadingLabel }
    } else {
        Modifier
    }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .then(a11y)
            .defaultMinSize(minHeight = TouchTarget.min)
            .height(TouchTarget.min),
        enabled = effectivelyEnabled,
        shape = RoundedCornerShape(Radius.sm),
        border = BorderStroke(StrokeWidth.thin, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = TextPrimaryV2,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = TextDisabled
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
                    color = TextPrimaryV2
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = text,
                    style = ButtonText,
                    color = TextPrimaryV2.copy(alpha = 0.6f)
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

@Preview(name = "SecondaryButton default")
@Composable
private fun SecondaryButtonPreview() {
    CamConTheme {
        SurfaceV2(tier = 0) {
            SecondaryButton(
                text = "Cancel",
                onClick = {},
                modifier = Modifier.padding(Spacing.lg)
            )
        }
    }
}

@Preview(name = "SecondaryButton states")
@Composable
private fun SecondaryButtonStatesPreview() {
    CamConTheme {
        SurfaceV2(tier = 0) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                SecondaryButton(text = "Retry", onClick = {}, leadingIcon = Icons.Filled.Refresh)
                SecondaryButton(text = "Loading…", onClick = {}, isLoading = true)
                SecondaryButton(text = "Disabled", onClick = {}, enabled = false)
            }
        }
    }
}
