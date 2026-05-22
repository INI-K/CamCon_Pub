package com.inik.camcon.presentation.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inik.camcon.R
import com.inik.camcon.domain.model.Camera
import com.inik.camcon.presentation.theme.Background
import com.inik.camcon.presentation.theme.Border
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.Error
import com.inik.camcon.presentation.theme.Primary
import com.inik.camcon.presentation.theme.Success
import com.inik.camcon.presentation.theme.Surface
import com.inik.camcon.presentation.theme.SurfaceElevated
import com.inik.camcon.presentation.theme.TextMuted
import com.inik.camcon.presentation.theme.TextPrimary
import com.inik.camcon.presentation.theme.TextSecondary
import com.inik.camcon.presentation.theme.WarningV2
import com.inik.camcon.presentation.viewmodel.CameraConnectionState
import com.inik.camcon.presentation.viewmodel.CameraSettingsState
import com.inik.camcon.presentation.viewmodel.CameraUiState
import com.inik.camcon.domain.model.CameraCapabilities
import com.inik.camcon.domain.model.StorageInfo
import com.inik.camcon.domain.model.ThemeMode

/**
 * 단순화된 상단 컨트롤 바.
 *
 * @param storageInfo 카메라 첫 슬롯 스토리지 정보. null 이면 SD 칩 숨김.
 */
@Composable
fun TopControlsBar(
    uiState: CameraUiState,
    cameraFeed: List<Camera>,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    storageInfo: StorageInfo? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "status_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Surface(
        color = Surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 연결 상태 표시
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(
                        color = if (uiState.isConnected)
                            Success.copy(alpha = 0.1f)
                        else
                            Error.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = if (uiState.isConnected)
                            Success.copy(alpha = 0.3f)
                        else
                            Error.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                // 상태 인디케이터 (연결 시 펄싱)
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (uiState.isConnected) Success.copy(alpha = pulseAlpha) else Error
                        )
                )

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = if (uiState.isConnected) {
                        uiState.cameraCapabilities?.model
                            ?: cameraFeed.firstOrNull()?.name
                            ?: stringResource(R.string.camera_connected)
                    } else {
                        stringResource(R.string.camera_disconnected)
                    },
                    color = TextPrimary,
                    style = MaterialTheme.typography.labelLarge
                )

                // M8: 배터리 칩 — capabilities.batteryLevel 이 있을 때만 표시
                uiState.cameraCapabilities?.batteryLevel?.let { level ->
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                color = Surface,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = Border,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.camera_status_battery_percent, level),
                            color = if (level <= 15) Error else TextPrimary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // SD 잔량 칩 — storageInfo 가 주어지고 freeBytes 가 0 이상일 때 표시.
                // 1GB 이상 → "SD %.1fGB", 그 외 MB. freeBytes < 100MB 면 Warning 색.
                // imagesFree 가 있으면 보조 텍스트 "여유 %d컷".
                storageInfo?.let { info ->
                    if (info.freeBytes >= 0) {
                        val freeBytes = info.freeBytes
                        val isLow = freeBytes < 100L * 1024 * 1024 // 100MB
                        val mb = freeBytes / (1024.0 * 1024.0)
                        val gb = freeBytes / (1024.0 * 1024.0 * 1024.0)
                        val sdText = if (freeBytes >= 1024L * 1024 * 1024) {
                            stringResource(R.string.camera_status_sd_format_gb, gb)
                        } else {
                            stringResource(R.string.camera_status_sd_format_mb, mb.toInt())
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(
                                    color = Surface,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isLow) WarningV2.copy(alpha = 0.5f) else Border,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = sdText,
                                    color = if (isLow) WarningV2 else TextPrimary,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium
                                )
                                info.imagesFree?.let { count ->
                                    Text(
                                        text = stringResource(
                                            R.string.camera_status_sd_images_free,
                                            count
                                        ),
                                        color = TextMuted,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 설정 버튼
            Surface(
                color = SurfaceElevated,
                shape = CircleShape,
                modifier = Modifier.size(44.dp)
            ) {
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = stringResource(R.string.settings),
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Preview(name = "Top Controls - Connected", showBackground = true)
@Composable
private fun TopControlsConnectedPreview() {
    CamConTheme() {
        Box(modifier = Modifier.background(Background)) {
            TopControlsBar(
                uiState = CameraUiState(
                    connection = CameraConnectionState(isConnected = true),
                    settings = CameraSettingsState(
                        cameraCapabilities = CameraCapabilities(
                            model = "Canon EOS R5",
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
                            batteryLevel = 85
                        )
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
    CamConTheme() {
        Box(modifier = Modifier.background(Background)) {
            TopControlsBar(
                uiState = CameraUiState(),
                cameraFeed = emptyList(),
                onSettingsClick = { }
            )
        }
    }
}
