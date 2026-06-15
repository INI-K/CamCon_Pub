package com.inik.camcon.presentation.ui.screens.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inik.camcon.R
import com.inik.camcon.domain.model.ShootingMode
import com.inik.camcon.presentation.theme.Accent
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.DividerLine
import com.inik.camcon.presentation.theme.OnAccent
import com.inik.camcon.presentation.theme.Radius
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.StrokeWidth
import com.inik.camcon.presentation.theme.Surface0
import com.inik.camcon.presentation.theme.Surface1
import com.inik.camcon.presentation.theme.Surface2
import com.inik.camcon.presentation.theme.TextPrimaryV2
import com.inik.camcon.presentation.theme.TextTertiary
import com.inik.camcon.domain.model.CameraCapabilities
import com.inik.camcon.presentation.viewmodel.CameraCaptureState
import com.inik.camcon.domain.model.ThemeMode

/**
 * 앱이 실제로 구현한 촬영 모드. 여기에 없는 모드는 카메라가 지원해도 UI에서 비활성화된다.
 * (BURST/TIMELAPSE/BULB/HDR는 미구현 — CameraCaptureRepositoryImpl에서 throw)
 */
private val APP_IMPLEMENTED_SHOOTING_MODES = setOf(ShootingMode.SINGLE)

/**
 * 촬영 모드 선택 -- state+callback 패턴
 *
 * @param captureState 촬영 sub-state (현재 모드 포함)
 * @param isConnected 카메라 연결 여부
 * @param cameraCapabilities 카메라 능력 (모드 활성화 판단)
 * @param onModeSelected 모드 선택 콜백 (지원 모드만 트리거)
 * @param onUnsupportedModeClick 미지원 모드 탭 콜백 — M6에서 Snackbar 안내 트리거
 */
@Composable
fun ShootingModeSelector(
    captureState: CameraCaptureState,
    isConnected: Boolean,
    cameraCapabilities: CameraCapabilities?,
    onModeSelected: (ShootingMode) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp),
    onUnsupportedModeClick: (ShootingMode) -> Unit = {}
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
            // 카메라가 지원하더라도 앱이 아직 구현하지 않은 모드는 비활성화한다.
            // (BURST/TIMELAPSE/BULB/HDR는 현재 CameraCaptureRepositoryImpl에서
            //  UnsupportedShootingModeException을 던지므로 선택 시 항상 실패 — 허위 기능 노출 방지)
            val isCameraCapable = when (mode) {
                ShootingMode.SINGLE -> true
                ShootingMode.BURST -> cameraCapabilities?.supportsBurstMode ?: false
                ShootingMode.TIMELAPSE -> cameraCapabilities?.supportsTimelapse ?: false
                ShootingMode.BULB -> cameraCapabilities?.supportsBulbMode ?: false
                ShootingMode.HDR_BRACKET -> cameraCapabilities?.supportsBracketing ?: false
            }
            val isSupported = isCameraCapable && mode in APP_IMPLEMENTED_SHOOTING_MODES
            val isEnabled = isConnected && isSupported

            ModeButton(
                mode = mode,
                isSelected = captureState.shootingMode == mode,
                isEnabled = isEnabled,
                onClick = {
                    when {
                        isEnabled -> onModeSelected(mode)
                        // M6: 연결됐는데 미지원 모드를 탭하면 안내. 미연결 시는 조용히 무시.
                        isConnected && !isSupported -> onUnsupportedModeClick(mode)
                        else -> Unit
                    }
                }
            )
        }
    }
}

/**
 * V2 토큰 기반 모드 칩. `enabled=false`인 상태에서도 탭 자체는 통과시켜
 * 호출측 콜백을 트리거하도록 한다(시각만 disabled).
 */
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

    val containerColor = when {
        isSelected -> Accent
        isEnabled -> Surface2
        else -> Surface1.copy(alpha = 0.4f)
    }
    val contentColor = when {
        isSelected -> OnAccent
        isEnabled -> TextPrimaryV2
        else -> TextTertiary
    }
    val borderColor = when {
        isSelected -> Accent
        isEnabled -> DividerLine
        else -> TextTertiary.copy(alpha = 0.2f)
    }

    androidx.compose.material3.Surface(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.sm))
            .clickable(onClick = onClick),
        color = containerColor,
        shape = RoundedCornerShape(Radius.sm),
        border = if (!isSelected) BorderStroke(StrokeWidth.thin, borderColor) else null
    ) {
        Box(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = contentColor
            )
        }
    }
}

@Preview(name = "Shooting Mode Selector", showBackground = true)
@Composable
private fun ShootingModeSelectorPreview() {
    CamConTheme() {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .background(Surface0),
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
