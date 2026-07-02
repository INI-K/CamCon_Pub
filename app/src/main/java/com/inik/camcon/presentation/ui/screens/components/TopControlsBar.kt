package com.inik.camcon.presentation.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.inik.camcon.R
import com.inik.camcon.domain.model.Camera
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.DividerLine
import com.inik.camcon.presentation.theme.ErrorV2
import com.inik.camcon.presentation.theme.MicroLabel
import com.inik.camcon.presentation.theme.MonoMicro
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.StrokeWidth
import com.inik.camcon.presentation.theme.Surface0
import com.inik.camcon.presentation.theme.Surface1
import com.inik.camcon.presentation.theme.Surface2
import com.inik.camcon.presentation.theme.TextPrimaryV2
import com.inik.camcon.presentation.theme.TextTertiary
import com.inik.camcon.presentation.theme.WarningV2
import com.inik.camcon.presentation.viewmodel.CameraConnectionState
import com.inik.camcon.presentation.viewmodel.CameraSettingsState
import com.inik.camcon.presentation.viewmodel.CameraUiState
import com.inik.camcon.domain.model.CameraCapabilities
import com.inik.camcon.domain.model.StorageInfo

/**
 * CINE 계기판 상단 바.
 *
 * 연결 상태 표시는 이 바에서 담당하지 않는다(중복 제거) — 상단 V2 StatusIndicator 한 곳으로 단일화.
 * 여기서는 계측기 헤더로서 모델명(MicroLabel, 라틴 대문자 관례) + 배터리/SD 무배경 헤어라인 칩(MonoMicro
 * 수치) + 설정 기어만 노출한다. 깊이는 상하 헤어라인으로만.
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
    Surface(
        color = Surface0,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            // 상단 헤어라인
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(StrokeWidth.hairline)
                    .background(DividerLine)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 모델명 칩 (MicroLabel — 라틴 대문자 관례, `.uppercase()` 미호출)
                val modelName = uiState.cameraCapabilities?.model
                    ?.takeIf { it.isNotBlank() && !it.equals("PTP/IP Camera", ignoreCase = true) }
                    ?: cameraFeed.firstOrNull()?.name
                        ?.takeIf { !it.equals("PTP/IP Camera", ignoreCase = true) }
                if (!modelName.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .border(
                                width = StrokeWidth.hairline,
                                color = DividerLine,
                                shape = RoundedCornerShape(3.dp)
                            )
                            .padding(horizontal = Spacing.sm, vertical = 5.dp)
                    ) {
                        Text(
                            text = modelName,
                            color = TextPrimaryV2,
                            style = MicroLabel
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(0.dp))
                }

                // 우측 계측기 그룹 — 배터리 / SD / 설정
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    // 배터리 헤어라인 칩 — capabilities.batteryLevel 이 있을 때만
                    uiState.cameraCapabilities?.batteryLevel?.let { level ->
                        HairlineChip {
                            Text(
                                text = stringResource(R.string.camera_status_battery_percent, level),
                                color = if (level <= 15) ErrorV2 else TextPrimaryV2,
                                style = MonoMicro
                            )
                        }
                    }

                    // SD 잔량 헤어라인 칩 — freeBytes 가 0 이상일 때. 1GB↑ → GB, 그 외 MB.
                    // freeBytes < 100MB 면 Warning 색. imagesFree 가 있으면 보조 텍스트 "여유 %d컷".
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
                            HairlineChip {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = sdText,
                                        color = if (isLow) WarningV2 else TextPrimaryV2,
                                        style = MonoMicro
                                    )
                                    info.imagesFree?.let { count ->
                                        Text(
                                            text = stringResource(
                                                R.string.camera_status_sd_images_free,
                                                count
                                            ),
                                            color = TextTertiary,
                                            style = MonoMicro
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 설정 버튼
                    Surface(
                        color = Surface2,
                        shape = CircleShape,
                        modifier = Modifier.size(40.dp)
                    ) {
                        IconButton(
                            onClick = onSettingsClick,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = stringResource(R.string.settings),
                                tint = TextPrimaryV2,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
            // 하단 헤어라인
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(StrokeWidth.hairline)
                    .background(DividerLine)
            )
        }
    }
}

/** 무배경 헤어라인 계측 칩 — 배경 없이 0.5dp 테두리만으로 구획. */
@Composable
private fun HairlineChip(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .border(
                width = StrokeWidth.hairline,
                color = DividerLine,
                shape = RoundedCornerShape(3.dp)
            )
            .padding(horizontal = Spacing.sm, vertical = 3.dp)
    ) {
        content()
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Top Controls - Connected", showBackground = true)
@Composable
private fun TopControlsConnectedPreview() {
    CamConTheme() {
        Box(modifier = Modifier.background(Surface0)) {
            TopControlsBar(
                uiState = CameraUiState(
                    connection = CameraConnectionState(isConnected = true),
                    settings = CameraSettingsState(
                        cameraCapabilities = CameraCapabilities(
                            model = "Nikon Z 8",
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
                            batteryLevel = 87
                        )
                    )
                ),
                cameraFeed = emptyList(),
                onSettingsClick = { }
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Top Controls - Disconnected", showBackground = true)
@Composable
private fun TopControlsDisconnectedPreview() {
    CamConTheme() {
        Box(modifier = Modifier.background(Surface0)) {
            TopControlsBar(
                uiState = CameraUiState(),
                cameraFeed = emptyList(),
                onSettingsClick = { }
            )
        }
    }
}
