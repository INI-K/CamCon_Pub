package com.inik.camcon.presentation.ui.screens.components

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
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inik.camcon.R
import com.inik.camcon.domain.model.ShootingMode
import com.inik.camcon.presentation.theme.Background
import com.inik.camcon.presentation.theme.Border
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.Primary
import com.inik.camcon.presentation.theme.PrimaryDark
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
                onGalleryClick = onGalleryClick
            )
        }
    } else {
        Row(
            modifier = modifier
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
                onGalleryClick = onGalleryClick
            )
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
    onGalleryClick: () -> Unit = {}
) {
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

    val isEnabled = isConnected && !captureState.isCapturing

    // 바깥 장식 링
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(88.dp)
            .scale(scale)
            .border(
                width = 1.5.dp,
                color = if (isEnabled) Primary.copy(alpha = 0.3f) else TextMuted.copy(alpha = 0.12f),
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
                    ambientColor = Primary.copy(alpha = 0.4f),
                    spotColor = Primary.copy(alpha = 0.6f)
                )
                .clip(CircleShape)
                .background(
                    color = if (isEnabled) Primary else TextMuted.copy(alpha = 0.25f)
                )
                .semantics {
                    role = Role.Button
                    contentDescription = "촬영"
                }
                .clickable(enabled = isEnabled) {
                    when (captureState.shootingMode) {
                        ShootingMode.TIMELAPSE -> onShowTimelapseDialog()
                        else -> onCapture()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (captureState.isCapturing) {
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
    CamConTheme(themeMode = ThemeMode.DARK) {
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
