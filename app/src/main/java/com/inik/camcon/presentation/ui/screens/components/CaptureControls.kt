package com.inik.camcon.presentation.ui.screens.components

import android.media.MediaActionSound
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inik.camcon.R
import com.inik.camcon.domain.model.ShootingMode
import com.inik.camcon.presentation.theme.Background
import com.inik.camcon.presentation.theme.Border
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.Error
import com.inik.camcon.presentation.theme.Primary
import com.inik.camcon.presentation.theme.PrimaryDark
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.SurfaceElevated
import com.inik.camcon.presentation.theme.TextMuted
import com.inik.camcon.presentation.theme.TextPrimary
import com.inik.camcon.presentation.theme.TextSecondary
import com.inik.camcon.presentation.viewmodel.CameraCaptureState
import com.inik.camcon.presentation.viewmodel.CameraUiState
import com.inik.camcon.domain.model.ThemeMode
import com.inik.camcon.presentation.theme.OnPrimary

/**
 * 단순화된 촬영 컨트롤 컴포넌트 — state+callback 패턴
 *
 * @param captureState 촬영 관련 sub-state
 * @param isConnected 카메라 연결 여부
 * @param onCapture 촬영 콜백
 * @param onAutoFocus AF 콜백
 * @param onShowTimelapseDialog 타임랩스 다이얼로그 콜백
 * @param isVertical 세로 레이아웃 여부
 * @param onGalleryClick 갤러리 클릭 콜백
 * @param isLiveViewActive 라이브뷰 활성 여부. false면 트리거 캡처 라벨을 함께 표시한다.
 * @param isShutterSoundEnabled 셔터 사운드 토글 (기본 true)
 * @param isTimelapseRunning 타임랩스가 진행 중인지. true면 메인 버튼을 중지 버튼으로 렌더한다.
 * @param onStopTimelapse 타임랩스 중지 콜백
 */
@Composable
fun CaptureControls(
    captureState: CameraCaptureState,
    isConnected: Boolean,
    onCapture: () -> Unit,
    onAutoFocus: () -> Unit,
    onShowTimelapseDialog: () -> Unit,
    isVertical: Boolean,
    onGalleryClick: () -> Unit = {},
    isLiveViewActive: Boolean = true,
    isShutterSoundEnabled: Boolean = true,
    isTimelapseRunning: Boolean = false,
    onStopTimelapse: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (isVertical) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            CaptureControlsContent(
                captureState = captureState,
                isConnected = isConnected,
                onCapture = onCapture,
                onAutoFocus = onAutoFocus,
                onShowTimelapseDialog = onShowTimelapseDialog,
                onGalleryClick = onGalleryClick,
                isLiveViewActive = isLiveViewActive,
                isShutterSoundEnabled = isShutterSoundEnabled,
                isTimelapseRunning = isTimelapseRunning,
                onStopTimelapse = onStopTimelapse
            )
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!isLiveViewActive) {
                Text(
                    text = stringResource(R.string.capture_no_liveview_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CaptureControlsContent(
                    captureState = captureState,
                    isConnected = isConnected,
                    onCapture = onCapture,
                    onAutoFocus = onAutoFocus,
                    onShowTimelapseDialog = onShowTimelapseDialog,
                    onGalleryClick = onGalleryClick,
                    isLiveViewActive = isLiveViewActive,
                    isShutterSoundEnabled = isShutterSoundEnabled,
                    isTimelapseRunning = isTimelapseRunning,
                    onStopTimelapse = onStopTimelapse
                )
            }
        }
    }
}

@Composable
private fun CaptureControlsContent(
    captureState: CameraCaptureState,
    isConnected: Boolean,
    onCapture: () -> Unit,
    onAutoFocus: () -> Unit,
    onShowTimelapseDialog: () -> Unit,
    onGalleryClick: () -> Unit = {},
    isLiveViewActive: Boolean = true,
    isShutterSoundEnabled: Boolean = true,
    isTimelapseRunning: Boolean = false,
    onStopTimelapse: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current

    // 셔터음 — 호스트 컴포저블의 라이프사이클을 따라 load/release
    val shutterSound = remember {
        MediaActionSound().apply { load(MediaActionSound.SHUTTER_CLICK) }
    }
    DisposableEffect(Unit) {
        onDispose {
            try {
                shutterSound.release()
            } catch (e: Exception) {
                // best effort — 일부 OEM에서 release 중 예외 가능
            }
        }
    }

    // 갤러리 버튼
    Surface(
        color = SurfaceElevated,
        shape = CircleShape,
        border = BorderStroke(1.dp, Border),
        modifier = Modifier.size(52.dp)
    ) {
        IconButton(
            onClick = onGalleryClick,
            modifier = Modifier.size(52.dp)
        ) {
            Icon(
                Icons.Default.PhotoLibrary,
                contentDescription = stringResource(R.string.gallery),
                tint = TextSecondary,
                modifier = Modifier.size(24.dp)
            )
        }
    }

    // 메인 촬영 버튼 — DSLR 이중 링 셔터 스타일
    val scale by animateFloatAsState(
        targetValue = if (captureState.isCapturing) 0.93f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "capture_button_scale"
    )

    // 타임랩스 진행 중이면 메인 버튼을 활성 중지 버튼으로 전환한다.
    val isStopMode = isTimelapseRunning &&
            captureState.shootingMode == ShootingMode.TIMELAPSE &&
            captureState.isCapturing
    val isEnabled = if (isStopMode) {
        // 중지 모드에서는 진행 중이어도 항상 누를 수 있어야 한다.
        isConnected
    } else {
        isConnected && !captureState.isCapturing
    }

    val captureCd = stringResource(R.string.capture_shutter_cd)
    val stopTimelapseCd = stringResource(R.string.capture_stop_timelapse)
    val buttonCd = if (isStopMode) stopTimelapseCd else captureCd

    // 바깥 장식 링
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(88.dp)
            .scale(scale)
            .border(
                width = 1.5.dp,
                color = when {
                    isStopMode -> Error.copy(alpha = 0.4f)
                    isEnabled -> Primary.copy(alpha = 0.3f)
                    else -> TextMuted.copy(alpha = 0.12f)
                },
                shape = CircleShape
            )
    ) {
        // 안쪽 버튼
        Box(
            modifier = Modifier
                .size(72.dp)
                .shadow(
                    elevation = if (isEnabled) 20.dp else 0.dp,
                    shape = CircleShape,
                    ambientColor = (if (isStopMode) Error else Primary).copy(alpha = 0.4f),
                    spotColor = (if (isStopMode) Error else Primary).copy(alpha = 0.6f)
                )
                .clip(CircleShape)
                .background(
                    color = when {
                        isStopMode -> Error
                        isEnabled -> Primary
                        else -> TextMuted.copy(alpha = 0.25f)
                    }
                )
                .semantics {
                    role = Role.Button
                    contentDescription = buttonCd
                }
                .clickable(enabled = isEnabled) {
                    // H1: 햅틱·셔터음
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (isStopMode) {
                        // 타임랩스 중지 — 셔터음 없이 즉시 중지
                        onStopTimelapse()
                        return@clickable
                    }
                    if (isShutterSoundEnabled) {
                        try {
                            shutterSound.play(MediaActionSound.SHUTTER_CLICK)
                        } catch (e: Exception) {
                            // ignore — 사운드 실패가 캡처를 막아서는 안 됨
                        }
                    }
                    when (captureState.shootingMode) {
                        ShootingMode.TIMELAPSE -> onShowTimelapseDialog()
                        else -> onCapture()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (isStopMode) {
                // 중지 아이콘 표시 (스피너 대신)
                Icon(
                    Icons.Default.Stop,
                    contentDescription = null,
                    tint = OnPrimary,
                    modifier = Modifier.size(36.dp)
                )
            } else if (captureState.isCapturing) {
                CircularProgressIndicator(
                    color = OnPrimary,
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 2.5.dp
                )
            }
        }
    }

    // 포커스 버튼
    Surface(
        color = if (isConnected) SurfaceElevated else SurfaceElevated.copy(alpha = 0.5f),
        shape = CircleShape,
        border = BorderStroke(
            1.dp,
            if (isConnected) Border else TextMuted.copy(alpha = 0.1f)
        ),
        modifier = Modifier.size(52.dp)
    ) {
        IconButton(
            onClick = onAutoFocus,
            enabled = isConnected && !captureState.isFocusing,
            modifier = Modifier.size(52.dp)
        ) {
            if (captureState.isFocusing) {
                CircularProgressIndicator(
                    color = Primary,
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    Icons.Default.CenterFocusStrong,
                    contentDescription = stringResource(R.string.focus),
                    tint = if (isConnected) TextSecondary else TextMuted,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Preview(name = "Capture Controls", showBackground = true)
@Composable
private fun CaptureControlsPreview() {
    CamConTheme() {
        Column(
            modifier = Modifier
                .background(Background)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // 연결됨
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CaptureControlsContent(
                    captureState = CameraCaptureState(
                        isCapturing = false,
                        isFocusing = false,
                        shootingMode = ShootingMode.SINGLE
                    ),
                    isConnected = true,
                    onCapture = {},
                    onAutoFocus = {},
                    onShowTimelapseDialog = { }
                )
            }

            // 촬영 중
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CaptureControlsContent(
                    captureState = CameraCaptureState(
                        isCapturing = true,
                        isFocusing = false,
                        shootingMode = ShootingMode.BURST
                    ),
                    isConnected = true,
                    onCapture = {},
                    onAutoFocus = {},
                    onShowTimelapseDialog = { }
                )
            }
        }
    }
}
