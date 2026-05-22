package com.inik.camcon.presentation.ui.components.v2

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.inik.camcon.R
import com.inik.camcon.presentation.theme.Accent
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.CameraSpec
import com.inik.camcon.presentation.theme.Caption
import com.inik.camcon.presentation.theme.ErrorV2
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.Surface1
import com.inik.camcon.presentation.theme.TextSecondaryV2
import com.inik.camcon.presentation.theme.TextTertiary

/**
 * V2 상태 인디케이터 — 8dp dot + Caption label.
 *
 * 4가지 상태:
 * - Idle: TextTertiary dot (정적)
 * - Connecting: Accent dot + 360° 회전 (1000ms linear infinite)
 * - Connected: Accent dot + 펄스 scale 1.0↔1.2 (1500ms reverse infinite)
 * - Error: ErrorV2 dot + ErrorV2 label
 *
 * 접근성(WCAG 2.2 SC 4.1.3 Status Messages):
 * - Idle 이외 상태에서 [LiveRegionMode.Polite] 적용 → 화면 포커스 변화 없이도
 *   TalkBack 이 상태 변경을 자동으로 announce.
 * - contentDescription 은 "{상태} {label}" 형태로 구성하여 dot 의미와 label 을
 *   하나의 발화로 들려준다.
 */
enum class StatusKind { Idle, Connecting, Connected, Error }

@Composable
fun StatusIndicator(
    kind: StatusKind,
    label: String,
    modifier: Modifier = Modifier
) {
    val dotColor = when (kind) {
        StatusKind.Idle -> TextTertiary
        StatusKind.Connecting -> Accent
        StatusKind.Connected -> Accent
        StatusKind.Error -> ErrorV2
    }
    val labelColor = when (kind) {
        StatusKind.Error -> ErrorV2
        else -> TextSecondaryV2
    }
    val statusLabel = when (kind) {
        StatusKind.Idle -> stringResource(R.string.cd_status_idle)
        StatusKind.Connecting -> stringResource(R.string.cd_status_connecting)
        StatusKind.Connected -> stringResource(R.string.cd_status_connected)
        StatusKind.Error -> stringResource(R.string.cd_status_error)
    }
    val announce = "$statusLabel, $label"

    Row(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                contentDescription = announce
                if (kind != StatusKind.Idle) {
                    liveRegion = LiveRegionMode.Polite
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        StatusDot(kind = kind, color = dotColor)
        Text(
            text = label,
            style = Caption,
            color = labelColor
        )
    }
}

@Composable
private fun StatusDot(kind: StatusKind, color: Color) {
    val transition = rememberInfiniteTransition(label = "statusDot")

    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    val pulse by transition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val dotModifier = when (kind) {
        StatusKind.Connecting -> Modifier.rotate(rotation)
        StatusKind.Connected -> Modifier.scale(pulse)
        else -> Modifier
    }

    Box(
        modifier = dotModifier
            .size(CameraSpec.liveviewIndicator)
            .clip(CircleShape)
            .background(color)
    )
}

@Preview(name = "StatusIndicator – All States", showBackground = true, backgroundColor = 0xFF1A1A1A)
@Composable
private fun StatusIndicatorAllStatesPreview() {
    CamConTheme {
        Surface(color = Surface1) {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                StatusIndicator(kind = StatusKind.Idle, label = "대기 중")
                StatusIndicator(kind = StatusKind.Connecting, label = "연결 중…")
                StatusIndicator(kind = StatusKind.Connected, label = "Sony A7R V 연결됨")
                StatusIndicator(kind = StatusKind.Error, label = "연결 실패")
            }
        }
    }
}

@Preview(name = "StatusIndicator – Single Connected", showBackground = true, backgroundColor = 0xFF1A1A1A)
@Composable
private fun StatusIndicatorConnectedPreview() {
    CamConTheme {
        Surface(color = Surface1) {
            Box(modifier = Modifier.padding(Spacing.lg)) {
                StatusIndicator(kind = StatusKind.Connected, label = "Live")
            }
        }
    }
}
