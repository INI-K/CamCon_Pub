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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
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
import com.inik.camcon.presentation.viewmodel.CameraUiState
import com.inik.camcon.data.datasource.local.ThemeMode

/**
 * 단순화된 상단 컨트롤 바
 */
@Composable
fun TopControlsBar(
    uiState: CameraUiState,
    cameraFeed: List<Camera>,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                // 상태 인디케이터
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (uiState.isConnected) Success else Error
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
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
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
    CamConTheme(themeMode = ThemeMode.DARK) {
        Box(modifier = Modifier.background(Background)) {
            TopControlsBar(
                uiState = CameraUiState(
                    isConnected = true,
                    cameraCapabilities = com.inik.camcon.domain.model.CameraCapabilities(
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
    CamConTheme(themeMode = ThemeMode.DARK) {
        Box(modifier = Modifier.background(Background)) {
            TopControlsBar(
                uiState = CameraUiState(isConnected = false),
                cameraFeed = emptyList(),
                onSettingsClick = { }
            )
        }
    }
}
