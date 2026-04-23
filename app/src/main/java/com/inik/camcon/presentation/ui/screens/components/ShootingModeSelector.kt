package com.inik.camcon.presentation.ui.screens.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.inik.camcon.presentation.theme.OnPrimary
import com.inik.camcon.presentation.theme.TextMuted
import com.inik.camcon.presentation.theme.TextPrimary
import com.inik.camcon.domain.model.CameraCapabilities
import com.inik.camcon.presentation.viewmodel.CameraCaptureState
import com.inik.camcon.domain.model.ThemeMode

/**
 * 촬영 모드 선택 -- state+callback 패턴
 *
 * @param captureState 촬영 sub-state (현재 모드 포함)
 * @param isConnected 카메라 연결 여부
 * @param cameraCapabilities 카메라 능력 (모드 활성화 판단)
 * @param onModeSelected 모드 선택 콜백
 */
@Composable
fun ShootingModeSelector(
    captureState: CameraCaptureState,
    isConnected: Boolean,
    cameraCapabilities: CameraCapabilities?,
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
                ShootingMode.SINGLE -> isConnected
                ShootingMode.BURST -> isConnected &&
                        (cameraCapabilities?.supportsBurstMode ?: false)
                ShootingMode.TIMELAPSE -> isConnected &&
                        (cameraCapabilities?.supportsTimelapse ?: false)
                ShootingMode.BULB -> isConnected &&
                        (cameraCapabilities?.supportsBulbMode ?: false)
                ShootingMode.HDR_BRACKET -> isConnected &&
                        (cameraCapabilities?.supportsBracketing ?: false)
            }

            ModeButton(
                mode = mode,
                isSelected = captureState.shootingMode == mode,
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
        ShootingMode.SINGLE -> stringResource(R.string.shooting_mode_single)
        ShootingMode.BURST -> stringResource(R.string.shooting_mode_burst)
        ShootingMode.TIMELAPSE -> stringResource(R.string.shooting_mode_timelapse)
        ShootingMode.BULB -> stringResource(R.string.shooting_mode_bulb)
        ShootingMode.HDR_BRACKET -> stringResource(R.string.hdr_bracket)
    }

    Button(
        onClick = onClick,
        enabled = isEnabled,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = when {
                isSelected -> Primary
                else -> SurfaceElevated
            },
            contentColor = when {
                isSelected -> OnPrimary
                else -> TextPrimary
            },
            disabledContainerColor = Surface.copy(alpha = 0.4f),
            disabledContentColor = TextMuted
        ),
        border = if (!isSelected) BorderStroke(
            width = 1.dp,
            color = if (isEnabled) Border else TextMuted.copy(alpha = 0.2f)
        ) else null,
        shape = RoundedCornerShape(50.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
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
                captureState = CameraCaptureState(shootingMode = ShootingMode.BURST),
                isConnected = true,
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
                ),
                onModeSelected = { }
            )

            // 기본 카메라
            ShootingModeSelector(
                captureState = CameraCaptureState(shootingMode = ShootingMode.SINGLE),
                isConnected = true,
                cameraCapabilities = CameraCapabilities(
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
                ),
                onModeSelected = { }
            )
        }
    }
}
