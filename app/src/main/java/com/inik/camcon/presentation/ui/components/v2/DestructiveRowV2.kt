package com.inik.camcon.presentation.ui.components.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.clickable
import com.inik.camcon.presentation.theme.BodySmall
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.ErrorV2
import com.inik.camcon.presentation.theme.HeadingM
import com.inik.camcon.presentation.theme.IconSize
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.Surface1
import com.inik.camcon.presentation.theme.TextSecondaryV2
import com.inik.camcon.presentation.theme.TouchTarget

/**
 * CamCon V2 — DestructiveRowV2
 *
 * 계정 삭제·전체 초기화 등 파괴적 작업용 행. [RowItem] 구조를 따르되 아이콘과 라벨을
 * [ErrorV2] 톤으로 강조해 다른 일반 설정 행과 시각적으로 구분한다.
 * 행 자체는 [Surface1] 배경(섹션 컨테이너) 위에 놓이는 것을 가정한다.
 *
 * 접근성(WCAG 2.2):
 * - SC 2.5.8 Target Size: 최소 높이 [TouchTarget.lg](48dp).
 * - SC 4.1.2 Name/Role/Value: [Role.Button] 부여, label+subtitle 결합 contentDescription.
 * - SC 1.4.11 Non-text Contrast: ErrorV2 (#D9534F) 는 Surface1(#1A1A1A) 위에서 4.5:1 이상.
 */
@Composable
fun DestructiveRowV2(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    val mergedDescription = if (subtitle != null) "$title, $subtitle" else title
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = mergedDescription
                role = Role.Button
            }
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = TouchTarget.lg)
            .padding(horizontal = Spacing.base, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ErrorV2,
            modifier = Modifier.size(IconSize.md)
        )
        Column(
            modifier = Modifier
        ) {
            Text(
                text = title,
                style = HeadingM,
                color = ErrorV2
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    text = subtitle,
                    style = BodySmall,
                    color = TextSecondaryV2
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A1A1A)
@Composable
private fun DestructiveRowV2Preview() {
    CamConTheme {
        Box(
            modifier = Modifier
                .background(Surface1)
                .padding(vertical = Spacing.sm)
        ) {
            DestructiveRowV2(
                icon = Icons.Default.DeleteForever,
                title = "Delete account",
                subtitle = "Permanently remove your account and data",
                onClick = {}
            )
        }
    }
}
