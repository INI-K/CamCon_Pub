package com.inik.camcon.presentation.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.inik.camcon.R
import com.inik.camcon.presentation.theme.IconSize
import com.inik.camcon.presentation.theme.Radius
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.Surface1
import com.inik.camcon.presentation.theme.TextPrimaryV2
import com.inik.camcon.presentation.theme.TextSecondaryV2
import com.inik.camcon.presentation.ui.components.v2.PrimaryButton

/**
 * M5: 첫 실행 코치마크 — 촬영 화면 진입 시 1회 표시.
 *
 * 반투명 배경 + 3개 핵심 동작 안내(더블탭 전체화면, 셔터 영역, 최근 사진 더블탭).
 * 사용자가 "이해했어요" 버튼을 누르면 onDismiss 호출. 상위에서 flag 저장.
 */
@Composable
fun CaptureCoachmarkOverlay(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 360.dp)
                .padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            CoachmarkItem(
                icon = Icons.Default.ArrowUpward,
                title = stringResource(R.string.coachmark_double_tap_fullscreen)
            )
            CoachmarkItem(
                icon = Icons.Default.RadioButtonChecked,
                title = stringResource(R.string.coachmark_shutter_area)
            )
            CoachmarkItem(
                icon = Icons.Default.PhotoLibrary,
                title = stringResource(R.string.coachmark_recent_double_tap)
            )

            Spacer(modifier = Modifier.height(Spacing.sm))

            PrimaryButton(
                text = stringResource(R.string.coachmark_got_it),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CoachmarkItem(
    icon: ImageVector,
    title: String
) {
    Surface(
        color = Surface1,
        shape = RoundedCornerShape(Radius.md),
        modifier = Modifier.fillMaxWidth()
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextSecondaryV2,
                modifier = Modifier.size(IconSize.md)
            )
            Text(
                text = title,
                color = TextPrimaryV2,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Start
            )
        }
    }
}
