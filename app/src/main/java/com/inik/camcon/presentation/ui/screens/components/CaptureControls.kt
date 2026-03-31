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
import com.inik.camcon.presentation.viewmodel.CameraUiState
import com.inik.camcon.presentation.viewmodel.CameraViewModel
import com.inik.camcon.data.datasource.local.ThemeMode
import com.inik.camcon.presentation.theme.OnPrimary

/**
 * 단순화된 촬영 컨트롤 컴포넌트
 */
@Composable
fun CaptureControls(
    uiState: CameraUiState,
    viewModel: CameraViewModel,
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
                uiState = uiState,
                onCapture = { viewModel.capturePhoto() },
                onAutoFocus = { viewModel.performAutoFocus() },
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
                uiState = uiState,
                onCapture = { viewModel.capturePhoto() },
                onAutoFocus = { viewModel.performAutoFocus() },
                onShowTimelapseDialog = onShowTimelapseDialog,
                onGalleryClick = onGalleryClick
            )
        }
    }
}

@Composable
private fun CaptureControlsContent(
    uiState: CameraUiState,
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
        targetValue = if (uiState.isCapturing) 0.93f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "capture_button_scale"
    )

    val isEnabled = uiState.isConnected && !uiState.isCapturing

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
                .clickable(enabled = isEnabled) {
                    when (uiState.shootingMode) {
                        ShootingMode.TIMELAPSE -> onShowTimelapseDialog()
                        else -> onCapture()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (uiState.isCapturing) {
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
        color = if (uiState.isConnected) SurfaceElevated else SurfaceElevated.copy(alpha = 0.5f),
        shape = CircleShape,
        border = BorderStroke(
            1.dp,
            if (uiState.isConnected) Border else TextMuted.copy(alpha = 0.1f)
        ),
        modifier = Modifier.size(52.dp)
    ) {
        IconButton(
            onClick = onAutoFocus,
            enabled = uiState.isConnected && !uiState.isFocusing,
            modifier = Modifier.size(52.dp)
        ) {
            if (uiState.isFocusing) {
                CircularProgressIndicator(
                    color = Primary,
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    Icons.Default.CenterFocusStrong,
                    contentDescription = stringResource(R.string.focus),
                    tint = if (uiState.isConnected) TextSecondary else TextMuted,
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
                    uiState = CameraUiState(
                        isConnected = true,
                        isCapturing = false,
                        isFocusing = false,
                        shootingMode = ShootingMode.SINGLE
                    ),
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
                    uiState = CameraUiState(
                        isConnected = true,
                        isCapturing = true,
                        isFocusing = false,
                        shootingMode = ShootingMode.BURST
                    ),
                    onCapture = {},
                    onAutoFocus = {},
                    onShowTimelapseDialog = { }
                )
            }
        }
    }
}
