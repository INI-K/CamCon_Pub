package com.inik.camcon.presentation.ui.components.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inik.camcon.R
import com.inik.camcon.domain.model.TransferQueueState
import com.inik.camcon.presentation.theme.Accent
import com.inik.camcon.presentation.theme.AccentMuted
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.Caption
import com.inik.camcon.presentation.theme.IconSize
import com.inik.camcon.presentation.theme.Radius
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.Surface1
import com.inik.camcon.presentation.theme.TextSecondaryV2

/**
 * 다운로드/처리 진행 카운트 배지 (요구 E7).
 *
 * V2 StatusBar 우측에 배치되며 진행 중인 전송이 있을 때만 표시된다.
 *  - 다운로드 묶음: 카운트 > 0 일 때만 다운로드 아이콘 + 숫자
 *  - 처리 묶음: 카운트 > 0 일 때만 처리(Sync) 아이콘 + 숫자
 *
 * 접근성(WCAG 2.2 SC 4.1.3 Status Messages):
 *  - [LiveRegionMode.Polite] 으로 TalkBack 이 카운트 변화를 자동 announce.
 *  - contentDescription 은 로컬라이즈된 "다운로드 N장 / 처리 N장" 전체 문구.
 *
 * 색/타이포는 theme 토큰만 사용(하드코딩 금지).
 */
@Composable
fun TransferProgressBadge(
    queue: TransferQueueState,
    modifier: Modifier = Modifier
) {
    // 진행 중이 아니면 아무것도 그리지 않는다.
    if (!queue.isActive) return

    val downloadingText = stringResource(R.string.camera_control_downloading_count, queue.downloading)
    val processingText = stringResource(R.string.camera_control_processing_count, queue.processing)

    // 접근성 전체 문구 — 활성 묶음만 이어 붙인다.
    val announce = buildList {
        if (queue.downloading > 0) add(downloadingText)
        if (queue.processing > 0) add(processingText)
    }.joinToString(", ")

    Row(
        modifier = modifier
            .background(AccentMuted, RoundedCornerShape(Radius.sm))
            .padding(horizontal = Spacing.md, vertical = 4.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = announce
                liveRegion = LiveRegionMode.Polite
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        if (queue.downloading > 0) {
            CountCluster(
                icon = Icons.Default.Download,
                count = queue.downloading
            )
        }
        if (queue.processing > 0) {
            CountCluster(
                icon = Icons.Default.Sync,
                count = queue.processing
            )
        }
    }
}

@Composable
private fun CountCluster(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Icon(
            imageVector = icon,
            // 데코레이션 — 의미는 부모 Row 의 contentDescription 이 담당.
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(IconSize.sm)
        )
        Text(
            text = count.toString(),
            style = Caption,
            color = TextSecondaryV2
        )
    }
}

@Preview(name = "TransferProgressBadge – Both", showBackground = true, backgroundColor = 0xFF1A1A1A)
@Composable
private fun TransferProgressBadgeBothPreview() {
    CamConTheme {
        Surface(color = Surface1) {
            Box(modifier = Modifier.padding(Spacing.lg)) {
                TransferProgressBadge(
                    queue = TransferQueueState(downloading = 2, processing = 1)
                )
            }
        }
    }
}

@Preview(name = "TransferProgressBadge – Processing only", showBackground = true, backgroundColor = 0xFF1A1A1A)
@Composable
private fun TransferProgressBadgeProcessingPreview() {
    CamConTheme {
        Surface(color = Surface1) {
            Box(modifier = Modifier.padding(Spacing.lg)) {
                TransferProgressBadge(
                    queue = TransferQueueState(downloading = 0, processing = 3)
                )
            }
        }
    }
}
