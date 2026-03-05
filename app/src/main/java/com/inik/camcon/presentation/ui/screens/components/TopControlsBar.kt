package com.inik.camcon.presentation.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inik.camcon.R
import com.inik.camcon.domain.model.Camera
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.viewmodel.CameraUiState

/**
 * 카메라 연결 상태와 설정 버튼을 표시하는 상단 컨트롤 바
 */
@Composable
fun TopControlsBar(
    uiState: CameraUiState,
    cameraFeed: List<Camera>,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val compactWidthPx = with(density) { 380.dp.roundToPx() }
    val isCompact = LocalWindowInfo.current.containerSize.width < compactWidthPx
    val horizontalPadding = if (isCompact) 10.dp else 12.dp
    val containerRadius = if (isCompact) 16.dp else 18.dp
    val contentPadding = if (isCompact) 12.dp else 16.dp
    val textSize = if (isCompact) 13.sp else 14.sp
    val iconSize = if (isCompact) 20.dp else 24.dp
    val settingButtonSize = if (isCompact) 40.dp else 48.dp
    val badgeSpacing = if (isCompact) 6.dp else 8.dp
    val tagHorizontal = if (isCompact) 10.dp else 12.dp
    val tagVertical = if (isCompact) 5.dp else 6.dp
    val featureScrollState = rememberScrollState()
    val connectedChipBg = Color(0x4432C28B)
    val disconnectedChipBg = Color(0x44D95B5B)
    val connectedDot = Color(0xFF59F0AE)
    val disconnectedDot = Color(0xFFFF8D8D)
    Surface(
        color = Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 8.dp)
            .shadow(10.dp, RoundedCornerShape(containerRadius))
            .clip(RoundedCornerShape(containerRadius))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xCC1A2232),
                        Color(0xCC131A27)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(containerRadius))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = contentPadding, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Camera Connection Status
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            if (uiState.isConnected)
                                connectedChipBg
                            else
                                disconnectedChipBg,
                            RoundedCornerShape(12.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (uiState.isConnected) {
                                Color(0xAA9FF4D2)
                            } else {
                                Color(0xAAF9B4B4)
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = tagHorizontal, vertical = tagVertical)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (uiState.isConnected) connectedDot else disconnectedDot
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiState.isConnected) {
                            uiState.cameraCapabilities?.model
                                ?: cameraFeed.firstOrNull()?.name
                                ?: stringResource(R.string.camera_connected)
                        } else {
                            stringResource(R.string.camera_disconnected)
                        },
                        color = Color.White,
                        fontSize = textSize,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 카메라 기능 간략 표시
                uiState.cameraCapabilities?.let { capabilities ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(badgeSpacing),
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .horizontalScroll(featureScrollState)
                    ) {
                        if (capabilities.canLiveView) {
                            FeatureBadge(
                                text = stringResource(R.string.live_view),
                                color = Color(0xFF5FA9FF),
                                compact = isCompact
                            )
                        }
                        if (capabilities.supportsTimelapse) {
                            FeatureBadge(
                                text = stringResource(R.string.time_lapse),
                                color = Color(0xFF0E8A68),
                                compact = isCompact
                            )
                        }
                        if (capabilities.supportsBurstMode) {
                            FeatureBadge(
                                text = stringResource(R.string.burst),
                                color = Color(0xFFFF9A3D),
                                compact = isCompact
                            )
                        }
                    }
                }
            }

            // Settings Button
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(settingButtonSize)
                    .background(Color(0x55374455), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0x66FFD8B1), RoundedCornerShape(12.dp))
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = stringResource(R.string.settings),
                    tint = Color(0xFFFFD6AE),
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}

@Preview(name = "Top Controls - Connected", showBackground = true)
@Composable
private fun TopControlsConnectedPreview() {
    CamConTheme {
        Surface(color = Color.Black) {
            TopControlsBar(
                uiState = CameraUiState(
                    isConnected = true,
                    cameraCapabilities = com.inik.camcon.domain.model.CameraCapabilities(
                        model = "Canon EOS 5D Mark IV",
                        canCapturePhoto = true,
                        canCaptureVideo = true,
                        canLiveView = true,
                        canTriggerCapture = true,
                        supportsAutofocus = true,
                        supportsManualFocus = true,
                        supportsFocusPoint = true,
                        supportsBurstMode = true,
                        supportsTimelapse = true,
                        supportsBracketing = true,
                        supportsBulbMode = true,
                        canDownloadFiles = true,
                        canDeleteFiles = true,
                        canPreviewFiles = true,
                        availableIsoSettings = emptyList(),
                        availableShutterSpeeds = emptyList(),
                        availableApertures = emptyList(),
                        availableWhiteBalanceSettings = emptyList(),
                        supportsRemoteControl = true,
                        supportsConfigChange = true,
                        batteryLevel = 65
                    )
                ),
                cameraFeed = emptyList(),
                onSettingsClick = { }
            )
        }
    }
}
@Preview(name = "Top Controls - Disconnected", showBackground = true)
@Composable
private fun TopControlsDisconnectedPreview() {
    CamConTheme {
        Surface(color = Color(0xFF111827)) {
            TopControlsBar(
                uiState = CameraUiState(isConnected = false),
                cameraFeed = emptyList(),
                onSettingsClick = { }
            )
        }
    }
}
@Preview(name = "Top Controls - Compact", showBackground = true, widthDp = 320)
@Composable
private fun TopControlsCompactPreview() {
    CamConTheme {
        Surface(color = Color(0xFF111827)) {
            TopControlsBar(
                uiState = CameraUiState(
                    isConnected = true,
                    cameraCapabilities = com.inik.camcon.domain.model.CameraCapabilities(
                        model = "Nikon Zf Ultra Long Camera Name",
                        canCapturePhoto = true,
                        canCaptureVideo = true,
                        canLiveView = true,
                        canTriggerCapture = true,
                        supportsAutofocus = true,
                        supportsManualFocus = true,
                        supportsFocusPoint = true,
                        supportsBurstMode = true,
                        supportsTimelapse = true,
                        supportsBracketing = false,
                        supportsBulbMode = false,
                        canDownloadFiles = true,
                        canDeleteFiles = false,
                        canPreviewFiles = true,
                        availableIsoSettings = emptyList(),
                        availableShutterSpeeds = emptyList(),
                        availableApertures = emptyList(),
                        availableWhiteBalanceSettings = emptyList(),
                        supportsRemoteControl = true,
                        supportsConfigChange = true,
                        batteryLevel = 85
                    )
                ),
                cameraFeed = emptyList(),
                onSettingsClick = { }
            )
        }
    }
}