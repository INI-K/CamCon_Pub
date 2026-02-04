package com.inik.camcon.presentation.ui.screens.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.inik.camcon.R
import com.inik.camcon.domain.model.ShootingMode
import com.inik.camcon.presentation.theme.Background
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

/**
 * 단순화된 촬영 컨트롤 컴포넌트
 */
@Composable
fun CaptureControls(
    uiState: CameraUiState,
    viewModel: CameraViewModel,
    onShowTimelapseDialog: () -> Unit,
    isVertical: Boolean,
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
                viewModel = viewModel,
                onShowTimelapseDialog = onShowTimelapseDialog
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
                viewModel = viewModel,
                onShowTimelapseDialog = onShowTimelapseDialog
            )
        }
    }
}

@Composable
private fun CaptureControlsContent(
    uiState: CameraUiState,
    viewModel: CameraViewModel,
    onShowTimelapseDialog: () -> Unit
) {
    // 갤러리 버튼
    Surface(
        color = SurfaceElevated,
        shape = CircleShape,
        modifier = Modifier.size(52.dp)
    ) {
        IconButton(
            onClick = { /* Navigate to gallery */ },
            modifier = Modifier.size(52.dp)
        ) {
            Icon(
                Icons.Default.PhotoLibrary,
                contentDescription = stringResource(R.string.gallery),
                tint = TextPrimary,
                modifier = Modifier.size(26.dp)
            )
        }
    }

    // 메인 촬영 버튼
    val scale by animateFloatAsState(
        targetValue = if (uiState.isCapturing) 0.95f else 1f,
        animationSpec = tween(150),
        label = "capture_button_scale"
    )

    val isEnabled = uiState.isConnected && !uiState.isCapturing

    Box(
        modifier = Modifier
            .size(76.dp)
            .scale(scale)
            .shadow(
                elevation = if (isEnabled) 16.dp else 0.dp,
                shape = CircleShape,
                ambientColor = Primary.copy(alpha = 0.3f),
                spotColor = Primary.copy(alpha = 0.5f)
            )
            .clip(CircleShape)
            .border(
                width = 3.dp,
                color = if (isEnabled) Primary else TextMuted,
                shape = CircleShape
            )
            .background(
                color = if (isEnabled) Primary else TextMuted.copy(alpha = 0.3f)
            )
            .clickable(
                enabled = isEnabled
            ) {
                when (uiState.shootingMode) {
                    ShootingMode.TIMELAPSE -> onShowTimelapseDialog()
                    else -> viewModel.capturePhoto()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (uiState.isCapturing) {
            CircularProgressIndicator(
                color = TextPrimary,
                modifier = Modifier.size(40.dp),
                strokeWidth = 3.dp
            )
        }
    }

    // 포커스 버튼
    Surface(
        color = if (uiState.isConnected) SurfaceElevated else SurfaceElevated.copy(alpha = 0.5f),
        shape = CircleShape,
        modifier = Modifier.size(52.dp)
    ) {
        IconButton(
            onClick = { viewModel.performAutoFocus() },
            enabled = uiState.isConnected && !uiState.isFocusing,
            modifier = Modifier.size(52.dp)
        ) {
            if (uiState.isFocusing) {
                CircularProgressIndicator(
                    color = Primary,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    Icons.Default.CenterFocusStrong,
                    contentDescription = stringResource(R.string.focus),
                    tint = if (uiState.isConnected) TextPrimary else TextMuted,
                    modifier = Modifier.size(26.dp)
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
                    viewModel = hiltViewModel(),
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
                    viewModel = hiltViewModel(),
                    onShowTimelapseDialog = { }
                )
            }
        }
    }
}
