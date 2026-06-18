package com.inik.camcon.presentation.ui.screens.components

import android.content.Context
import android.media.AudioManager
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
import androidx.compose.ui.platform.LocalContext
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
import com.inik.camcon.presentation.theme.Surface0
import com.inik.camcon.presentation.theme.DividerLine
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.ErrorV2
import com.inik.camcon.presentation.theme.Accent
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.Surface2
import com.inik.camcon.presentation.theme.TextTertiary
import com.inik.camcon.presentation.theme.TextSecondaryV2
import com.inik.camcon.presentation.viewmodel.CameraCaptureState
import com.inik.camcon.presentation.viewmodel.CameraUiState
import com.inik.camcon.domain.model.ThemeMode
import com.inik.camcon.presentation.theme.OnAccent

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
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (isVertical) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) Spacing.sm else 20.dp)
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
                onStopTimelapse = onStopTimelapse,
                compact = compact
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
                    color = TextSecondaryV2,
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
    onStopTimelapse: () -> Unit = {},
    compact: Boolean = false
) {
    val haptic = LocalHapticFeedback.current

    // compact = 전체화면 가로 도크용 축소 사이즈(세로 누적 높이를 줄여 짧은 가로 화면에서도 fit).
    val galleryAfSize = if (compact) 44.dp else 52.dp
    val sideIconSize = if (compact) 20.dp else 24.dp
    val shutterOuterSize = if (compact) 64.dp else 88.dp
    val shutterInnerSize = if (compact) 50.dp else 72.dp
    val shutterIconSize = if (compact) 28.dp else 36.dp

    // 셔터음은 기기 무음/진동 모드를 따른다(사용자가 기기를 무음으로 두면 셔터음도 무음).
    val context = LocalContext.current
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

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
        color = Surface2,
        shape = CircleShape,
        border = BorderStroke(1.dp, DividerLine),
        modifier = Modifier.size(galleryAfSize)
    ) {
        IconButton(
            onClick = onGalleryClick,
            modifier = Modifier.size(galleryAfSize)
        ) {
            Icon(
                Icons.Default.PhotoLibrary,
                contentDescription = stringResource(R.string.gallery),
                tint = TextSecondaryV2,
                modifier = Modifier.size(sideIconSize)
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
            .size(shutterOuterSize)
            .scale(scale)
            .border(
                width = 1.5.dp,
                color = when {
                    isStopMode -> ErrorV2.copy(alpha = 0.4f)
                    isEnabled -> Accent.copy(alpha = 0.3f)
                    else -> TextTertiary.copy(alpha = 0.12f)
                },
                shape = CircleShape
            )
    ) {
        // 안쪽 버튼
        Box(
            modifier = Modifier
                .size(shutterInnerSize)
                .shadow(
                    elevation = if (isEnabled) 20.dp else 0.dp,
                    shape = CircleShape,
                    ambientColor = (if (isStopMode) ErrorV2 else Accent).copy(alpha = 0.4f),
                    spotColor = (if (isStopMode) ErrorV2 else Accent).copy(alpha = 0.6f)
                )
                .clip(CircleShape)
                .background(
                    color = when {
                        isStopMode -> ErrorV2
                        isEnabled -> Accent
                        else -> TextTertiary.copy(alpha = 0.25f)
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
                    // 셔터음은 앱 설정 + 기기 ringer 모드(무음/진동이면 미재생)를 모두 따른다.
                    if (isShutterSoundEnabled &&
                        audioManager?.ringerMode == AudioManager.RINGER_MODE_NORMAL
                    ) {
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
                    tint = OnAccent,
                    modifier = Modifier.size(shutterIconSize)
                )
            } else if (captureState.isCapturing) {
                CircularProgressIndicator(
                    color = OnAccent,
                    modifier = Modifier.size(shutterIconSize),
                    strokeWidth = 2.5.dp
                )
            }
        }
    }

    // 포커스 버튼
    Surface(
        color = if (isConnected) Surface2 else Surface2.copy(alpha = 0.5f),
        shape = CircleShape,
        border = BorderStroke(
            1.dp,
            if (isConnected) DividerLine else TextTertiary.copy(alpha = 0.1f)
        ),
        modifier = Modifier.size(galleryAfSize)
    ) {
        IconButton(
            onClick = onAutoFocus,
            enabled = isConnected && !captureState.isFocusing,
            modifier = Modifier.size(galleryAfSize)
        ) {
            if (captureState.isFocusing) {
                CircularProgressIndicator(
                    color = Accent,
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    Icons.Default.CenterFocusStrong,
                    contentDescription = stringResource(R.string.focus),
                    tint = if (isConnected) TextSecondaryV2 else TextTertiary,
                    modifier = Modifier.size(sideIconSize)
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
                .background(Surface0)
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
