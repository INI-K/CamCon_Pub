package com.inik.camcon.presentation.ui.components.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.HeadingL
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.TextPrimaryV2
import com.inik.camcon.presentation.theme.TextSecondaryV2

/**
 * V2 디자인 시스템 — 섹션 컨테이너.
 *
 * 헤더(HeadingL, 좌측) + 옵션 trailing 액션(우측) + [Spacing.lg] 갭 후 content.
 * 카메라 설정 / 사진 목록 / 연결 모드 그룹 등 큰 영역 구분에 사용.
 */
@Composable
fun Section(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = HeadingL,
                color = TextPrimaryV2
            )
            if (trailing != null) {
                trailing()
            }
        }
        Spacer(Modifier.height(Spacing.lg))
        content()
    }
}

@Preview(name = "Section basic")
@Composable
private fun SectionPreview() {
    CamConTheme {
        SurfaceV2(tier = 0) {
            Section(
                title = "Camera Settings",
                modifier = Modifier.padding(Spacing.lg)
            ) {
                Text("ISO / Shutter / Aperture", color = TextSecondaryV2)
            }
        }
    }
}

@Preview(name = "Section with trailing")
@Composable
private fun SectionWithTrailingPreview() {
    CamConTheme {
        SurfaceV2(tier = 0) {
            Section(
                title = "Recent Photos",
                modifier = Modifier.padding(Spacing.lg),
                trailing = {
                    Text("See all", color = TextSecondaryV2)
                }
            ) {
                Text("Grid", color = TextSecondaryV2)
            }
        }
    }
}
