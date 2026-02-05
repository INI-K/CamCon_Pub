package com.inik.camcon.presentation.ui.screens.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inik.camcon.R
import com.inik.camcon.domain.model.ShootingMode
import com.inik.camcon.presentation.theme.Background
import com.inik.camcon.presentation.theme.Border
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.Primary
import com.inik.camcon.presentation.theme.Surface
import com.inik.camcon.presentation.theme.SurfaceElevated
import com.inik.camcon.presentation.theme.TextMuted
import com.inik.camcon.presentation.theme.TextPrimary
import com.inik.camcon.presentation.viewmodel.CameraUiState
import com.inik.camcon.data.datasource.local.ThemeMode

/**
 * 단순화된 촬영 모드 선택
 */
@Composable
fun ShootingModeSelector(
    uiState: CameraUiState,
    onModeSelected: (ShootingMode) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp)
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = contentPadding
    ) {
        items(
            items = ShootingMode.entries.toTypedArray(),
            key = { mode -> mode.name }
        ) { mode ->
            val isEnabled = when (mode) {
                ShootingMode.SINGLE -> uiState.isConnected
                ShootingMode.BURST -> uiState.isConnected &&
                        (uiState.cameraCapabilities?.supportsBurstMode ?: false)
                ShootingMode.TIMELAPSE -> uiState.isConnected &&
                        (uiState.cameraCapabilities?.supportsTimelapse ?: false)
                ShootingMode.BULB -> uiState.isConnected &&
                        (uiState.cameraCapabilities?.supportsBulbMode ?: false)
                ShootingMode.HDR_BRACKET -> uiState.isConnected &&
                        (uiState.cameraCapabilities?.supportsBracketing ?: false)
            }

            ModeButton(
                mode = mode,
                isSelected = uiState.shootingMode == mode,
                isEnabled = isEnabled,
                onClick = { if (isEnabled) onModeSelected(mode) }
            )
        }
    }
}

@Composable
private fun ModeButton(
    mode: ShootingMode,
    isSelected: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = when (mode) {
        ShootingMode.SINGLE -> "단일"
        ShootingMode.BURST -> "연사"
        ShootingMode.TIMELAPSE -> "타임랩스"
        ShootingMode.BULB -> "벌브"
        ShootingMode.HDR_BRACKET -> "HDR"
    }

    Button(
        onClick = onClick,
        enabled = isEnabled,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = when {
                !isEnabled -> Surface.copy(alpha = 0.5f)
                isSelected -> Primary.copy(alpha = 0.15f)
                else -> SurfaceElevated
            },
            contentColor = when {
                !isEnabled -> TextMuted
                isSelected -> Primary
                else -> TextPrimary
            }
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = when {
                !isEnabled -> TextMuted.copy(alpha = 0.3f)
                isSelected -> Primary.copy(alpha = 0.5f)
                else -> Border
            }
        ),
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@Preview(name = "Shooting Mode Selector", showBackground = true)
@Composable
private fun ShootingModeSelectorPreview() {
    CamConTheme(themeMode = ThemeMode.DARK) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .background(Background),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 연결됨
            ShootingModeSelector(
                uiState = CameraUiState(
                    isConnected = true,
                    shootingMode = ShootingMode.BURST,
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
                onModeSelected = { }
            )

            // 기본 카메라
            ShootingModeSelector(
                uiState = CameraUiState(
                    isConnected = true,
                    shootingMode = ShootingMode.SINGLE,
                    cameraCapabilities = com.inik.camcon.domain.model.CameraCapabilities(
                        model = "Basic",
                        canCapturePhoto = true,
                        canCaptureVideo = false,
                        canLiveView = false,
                        canTriggerCapture = true,
                        supportsAutofocus = true,
                        supportsManualFocus = false,
                        supportsFocusPoint = false,
                        supportsBurstMode = false,
                        supportsTimelapse = false,
                        supportsBracketing = false,
                        supportsBulbMode = false,
                        canDownloadFiles = true,
                        canDeleteFiles = false,
                        canPreviewFiles = false,
                        availableIsoSettings = emptyList(),
                        availableShutterSpeeds = emptyList(),
                        availableApertures = emptyList(),
                        availableWhiteBalanceSettings = emptyList(),
                        supportsRemoteControl = true,
                        supportsConfigChange = false,
                        batteryLevel = 60
                    )
                ),
                onModeSelected = { }
            )
        }
    }
}
