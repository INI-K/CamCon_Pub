package com.inik.camcon.presentation.ui.screens.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Chip
import androidx.compose.material.ChipDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inik.camcon.R
import com.inik.camcon.domain.model.ShootingMode
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.viewmodel.CameraUiState

/**
 * 촬영 모드를 선택하는 컴포넌트
 */
@Composable
fun ShootingModeSelector(
    uiState: CameraUiState,
    onModeSelected: (ShootingMode) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp)
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = contentPadding
    ) {
        items(ShootingMode.values()) { mode ->
            val isEnabled = when (mode) {
                ShootingMode.SINGLE -> uiState.isConnected
                ShootingMode.BURST -> uiState.isConnected && (uiState.cameraCapabilities?.supportsBurstMode
                    ?: false)

                ShootingMode.TIMELAPSE -> uiState.isConnected && (uiState.cameraCapabilities?.supportsTimelapse
                    ?: false)

                ShootingMode.BULB -> uiState.isConnected && (uiState.cameraCapabilities?.supportsBulbMode
                    ?: false)

                ShootingMode.HDR_BRACKET -> uiState.isConnected && (uiState.cameraCapabilities?.supportsBracketing
                    ?: false)
            }

            ShootingModeChip(
                mode = mode,
                isSelected = uiState.shootingMode == mode,
                isEnabled = isEnabled,
                onClick = { if (isEnabled) onModeSelected(mode) }
            )
        }
    }
}

/**
 * 개별 촬영 모드 칩
 */
@Composable
fun ShootingModeChip(
    mode: ShootingMode,
    isSelected: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayName = when (mode) {
        ShootingMode.SINGLE -> stringResource(R.string.single_shot)
        ShootingMode.BURST -> stringResource(R.string.burst_mode)
        ShootingMode.TIMELAPSE -> stringResource(R.string.timelapse)
        ShootingMode.BULB -> stringResource(R.string.bulb_mode)
        ShootingMode.HDR_BRACKET -> stringResource(R.string.hdr_bracket)
    }

    Chip(
        onClick = onClick,
        enabled = isEnabled,
        modifier = modifier,
        colors = ChipDefaults.chipColors(
            backgroundColor = if (isSelected && isEnabled) MaterialTheme.colors.primary
            else if (isEnabled) Color.Gray.copy(alpha = 0.3f)
            else Color.Gray.copy(alpha = 0.1f),
            contentColor = if (isEnabled) Color.White else Color.Gray,
            disabledBackgroundColor = Color.Gray.copy(alpha = 0.1f),
            disabledContentColor = Color.Gray
        )
    ) {
        Text(displayName, fontSize = 14.sp)
    }
}

@Preview(name = "Shooting Mode Selector", showBackground = true)
@Composable
private fun ShootingModeSelectorPreview() {
    CamConTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Connected Camera - All Features:")
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

            Text("Limited Features Camera:")
            ShootingModeSelector(
                uiState = CameraUiState(
                    isConnected = true,
                    shootingMode = ShootingMode.SINGLE,
                    cameraCapabilities = com.inik.camcon.domain.model.CameraCapabilities(
                        model = "Basic Camera",
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

            Text("Disconnected:")
            ShootingModeSelector(
                uiState = CameraUiState(
                    isConnected = false,
                    shootingMode = ShootingMode.SINGLE
                ),
                onModeSelected = { }
            )
        }
    }
}