package com.inik.camcon.presentation.ui.components.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inik.camcon.presentation.theme.Accent
import com.inik.camcon.presentation.theme.Body
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.ErrorV2
import com.inik.camcon.presentation.theme.IconSize
import com.inik.camcon.presentation.theme.Info
import com.inik.camcon.presentation.theme.Radius
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.SuccessV2
import com.inik.camcon.presentation.theme.Surface1
import com.inik.camcon.presentation.theme.Surface3
import com.inik.camcon.presentation.theme.TextPrimaryV2

/**
 * V2 토스트 — Surface3 배경 + 좌측 4dp 컬러 bar(kind 매핑).
 *
 * Kind → bar 색상:
 * - Idle      → Info
 * - Connecting→ Accent
 * - Connected → SuccessV2
 * - Error     → ErrorV2
 */
@Composable
fun ToastV2(
    message: String,
    kind: StatusKind = StatusKind.Idle,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    maxLines: Int = Int.MAX_VALUE
) {
    val barColor: Color = when (kind) {
        StatusKind.Idle -> Info
        StatusKind.Searching -> Accent
        StatusKind.Connecting -> Accent
        StatusKind.Connected -> SuccessV2
        StatusKind.Error -> ErrorV2
    }

    Surface(
        modifier = modifier.clip(RoundedCornerShape(Radius.sm)),
        color = Surface3,
        shape = RoundedCornerShape(Radius.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 좌측 컬러 bar (4dp 폭, 행 전체 높이)
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(barColor)
            )
            Row(
                modifier = Modifier.padding(Spacing.base),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = barColor,
                        modifier = Modifier.size(IconSize.md)
                    )
                }
                Text(
                    text = message,
                    style = Body,
                    color = TextPrimaryV2,
                    maxLines = maxLines,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Preview(name = "ToastV2 – Kinds", showBackground = true, backgroundColor = 0xFF1A1A1A)
@Composable
private fun ToastV2KindsPreview() {
    CamConTheme {
        Surface(color = Surface1) {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                ToastV2(message = "촬영 모드 변경됨", kind = StatusKind.Idle)
                ToastV2(message = "카메라에 연결 중…", kind = StatusKind.Connecting)
                ToastV2(message = "Sony A7R V 연결 성공", kind = StatusKind.Connected)
                ToastV2(message = "USB 권한이 거부되었습니다", kind = StatusKind.Error)
            }
        }
    }
}

@Preview(name = "ToastV2 – With Icon", showBackground = true, backgroundColor = 0xFF1A1A1A)
@Composable
private fun ToastV2WithIconPreview() {
    CamConTheme {
        Surface(color = Surface1) {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                ToastV2(
                    message = "사진이 저장되었습니다",
                    kind = StatusKind.Connected,
                    leadingIcon = Icons.Outlined.CheckCircle
                )
                ToastV2(
                    message = "메모리 카드 가득 참",
                    kind = StatusKind.Error,
                    leadingIcon = Icons.Outlined.WarningAmber
                )
                ToastV2(
                    message = "백그라운드 동기화 중",
                    kind = StatusKind.Idle,
                    leadingIcon = Icons.Outlined.Info
                )
            }
        }
    }
}
