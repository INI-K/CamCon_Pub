package com.inik.camcon.presentation.ui.components.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inik.camcon.presentation.theme.Accent
import com.inik.camcon.presentation.theme.BodySmall
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.HeadingM
import com.inik.camcon.presentation.theme.IconSize
import com.inik.camcon.presentation.theme.OnAccent
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.Surface1
import com.inik.camcon.presentation.theme.Surface3
import com.inik.camcon.presentation.theme.TextPrimaryV2
import com.inik.camcon.presentation.theme.TextSecondaryV2
import com.inik.camcon.presentation.theme.TouchTarget

/**
 * CamCon V2 — RowItem
 *
 * Settings 화면 표준 행. 최소 높이 [TouchTarget.lg](48dp), 좌우 패딩 14dp(Spacing.base).
 * leadingIcon(IconSize.md, TextSecondaryV2) + Column(label HeadingM, description BodySmall) + trailing.
 * 프로필 사진 등 ImageVector로 표현 불가한 경우 `leadingContent` 슬롯 사용(leadingIcon보다 우선).
 * onClick=null이면 정적 행, !=null이면 ripple. enabled=false면 alpha 0.4 + 클릭 무효.
 *
 * 접근성(WCAG 2.2):
 * - SC 2.5.8 Target Size: 최소 높이 [TouchTarget.lg](48dp) 보장.
 * - SC 4.1.2 Name/Role/Value: onClick이 있을 때 [Role.Button] 설정.
 * - SC 1.3.1 Info & Relationships: label + description 을 단일 contentDescription 으로 묶어
 *   TalkBack 이 한 번에 읽도록 한다.
 * - leadingContent 슬롯(이미지/아바타 등)은 [leadingContentDescription] 으로 의미 부여,
 *   null 이면 장식 요소로 간주하고 [clearAndSetSemantics] 로 시맨틱 트리에서 제거.
 *
 * 디자인 가이드: docs/DESIGN_SYSTEM_V2.md §8 Row/ListItem.
 */
@Composable
fun RowItem(
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    leadingIcon: ImageVector? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    leadingContentDescription: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    val mergedDescription = if (description != null) "$label, $description" else label
    val container = modifier
        .fillMaxWidth()
        .alpha(if (enabled) 1f else 0.4f)
        .semantics(mergeDescendants = true) {
            contentDescription = mergedDescription
            if (onClick != null) {
                role = Role.Button
            }
        }
        .let { m ->
            if (enabled && onClick != null) m.clickable(onClick = onClick) else m
        }
        .defaultMinSize(minHeight = TouchTarget.lg)
        .padding(horizontal = Spacing.base, vertical = Spacing.sm)

    Row(
        modifier = container,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        when {
            leadingContent != null -> {
                val cd = leadingContentDescription
                Box(
                    modifier = if (cd != null) {
                        Modifier.semantics {
                            contentDescription = cd
                        }
                    } else {
                        // 장식 요소 — TalkBack 트리에서 제외.
                        Modifier.clearAndSetSemantics { }
                    }
                ) {
                    leadingContent()
                }
            }

            leadingIcon != null -> Icon(
                imageVector = leadingIcon,
                // 이미 행 전체 contentDescription 에 라벨이 포함되므로 장식으로 처리.
                contentDescription = null,
                tint = TextSecondaryV2,
                modifier = Modifier.size(IconSize.md)
            )
        }
        Column(
            modifier = Modifier
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = HeadingM,
                color = TextPrimaryV2
            )
            if (description != null) {
                Text(
                    text = description,
                    style = BodySmall,
                    color = TextSecondaryV2
                )
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}

// ---------------- Previews ----------------

@Preview(name = "RowItem / switch + description", showBackground = true, backgroundColor = 0xFF1A1A1A)
@Composable
private fun RowItemSwitchPreview() {
    CamConTheme {
        Box(
            modifier = Modifier
                .background(Surface1)
                .padding(vertical = Spacing.sm)
        ) {
            RowItem(
                label = "Push notifications",
                description = "Receive alerts for new captures",
                leadingIcon = Icons.Filled.Notifications,
                trailing = {
                    Switch(
                        checked = true,
                        onCheckedChange = {},
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = OnAccent,
                            checkedTrackColor = Accent,
                            uncheckedThumbColor = TextSecondaryV2,
                            uncheckedTrackColor = Surface3
                        )
                    )
                },
                onClick = {}
            )
        }
    }
}

@Preview(name = "RowItem / chevron + disabled", showBackground = true, backgroundColor = 0xFF1A1A1A)
@Composable
private fun RowItemChevronPreview() {
    CamConTheme {
        Box(
            modifier = Modifier
                .background(Surface1)
                .padding(vertical = Spacing.sm)
        ) {
            Column {
                RowItem(
                    label = "Wi-Fi connection",
                    description = "Tap to configure PTP-IP",
                    leadingIcon = Icons.Filled.Wifi,
                    trailing = {
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = TextSecondaryV2,
                            modifier = Modifier.size(IconSize.md)
                        )
                    },
                    onClick = {}
                )
                RowItem(
                    label = "Cloud sync (Pro)",
                    description = "Subscribe to enable",
                    leadingIcon = Icons.Filled.Wifi,
                    trailing = {
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = TextSecondaryV2,
                            modifier = Modifier.size(IconSize.md)
                        )
                    },
                    onClick = {},
                    enabled = false
                )
            }
        }
    }
}
