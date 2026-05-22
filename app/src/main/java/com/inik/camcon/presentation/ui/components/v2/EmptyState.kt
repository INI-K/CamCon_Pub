package com.inik.camcon.presentation.ui.components.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inik.camcon.presentation.theme.BodySmall
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.HeadingM
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.Surface1
import com.inik.camcon.presentation.theme.TextPrimaryV2
import com.inik.camcon.presentation.theme.TextSecondaryV2
import com.inik.camcon.presentation.theme.TextTertiary

/**
 * V2 Empty 상태 — 중앙 정렬 Column.
 *
 * 레이아웃: 32dp icon(TextTertiary) → Spacing.md → HeadingM
 *  → (옵션) Spacing.sm + BodySmall description
 *  → (옵션) Spacing.lg + action(예: PrimaryButton)
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String? = null,
    action: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        Text(
            text = title,
            style = HeadingM,
            color = TextPrimaryV2,
            textAlign = TextAlign.Center
        )
        if (description != null) {
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = description,
                style = BodySmall,
                color = TextSecondaryV2,
                textAlign = TextAlign.Center
            )
        }
        if (action != null) {
            Spacer(modifier = Modifier.height(Spacing.lg))
            action()
        }
    }
}

@Preview(name = "EmptyState – No Camera", showBackground = true, backgroundColor = 0xFF1A1A1A)
@Composable
private fun EmptyStateNoCameraPreview() {
    CamConTheme {
        Surface(color = Surface1) {
            EmptyState(
                icon = Icons.Outlined.CameraAlt,
                title = "연결된 카메라가 없습니다",
                description = "USB로 카메라를 연결하거나 Wi-Fi PTP/IP로 페어링하세요."
            )
        }
    }
}

@Preview(name = "EmptyState – With Action", showBackground = true, backgroundColor = 0xFF1A1A1A)
@Composable
private fun EmptyStateWithActionPreview() {
    CamConTheme {
        Surface(color = Surface1) {
            EmptyState(
                icon = Icons.Outlined.PhotoLibrary,
                title = "사진이 없습니다",
                description = "카메라에서 촬영을 시작하면 여기에 자동으로 표시됩니다.",
                action = {
                    androidx.compose.material3.TextButton(onClick = {}) {
                        Text(text = "촬영 시작", color = TextPrimaryV2)
                    }
                }
            )
        }
    }
}
