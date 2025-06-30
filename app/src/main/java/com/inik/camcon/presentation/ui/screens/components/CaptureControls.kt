package com.inik.camcon.presentation.ui.screens.components

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
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inik.camcon.R
import com.inik.camcon.domain.model.ShootingMode
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.viewmodel.CameraUiState
import com.inik.camcon.presentation.viewmodel.CameraViewModel

/**
 * 촬영 관련 컨트롤을 표시하는 컴포넌트
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
        // Vertical layout for landscape mode
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CaptureControlsContent(
                uiState = uiState,
                viewModel = viewModel,
                onShowTimelapseDialog = onShowTimelapseDialog
            )
        }
    } else {
        // Horizontal layout for portrait mode
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
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
    // Photo Gallery Button
    IconButton(
        onClick = { /* Navigate to gallery */ },
        modifier = Modifier.size(48.dp)
    ) {
        Icon(
            Icons.Default.PhotoLibrary,
            contentDescription = stringResource(R.string.gallery),
            tint = Color.White,
            modifier = Modifier.size(32.dp)
        )
    }

    // Main Capture Button
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .border(
                3.dp,
                if (uiState.isConnected) Color.White else Color.Gray,
                CircleShape
            )
            .clickable(
                enabled = uiState.isConnected && !uiState.isCapturing
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
                color = Color.White,
                modifier = Modifier.size(60.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(
                        if (uiState.isConnected) Color.White else Color.Gray
                    )
            )
        }
    }

    // Focus Button
    IconButton(
        onClick = {
            viewModel.performAutoFocus()
        },
        enabled = uiState.isConnected && !uiState.isFocusing,
        modifier = Modifier.size(48.dp)
    ) {
        if (uiState.isFocusing) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                Icons.Default.CenterFocusStrong,
                contentDescription = stringResource(R.string.focus),
                tint = if (uiState.isConnected) Color.White else Color.Gray,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Preview(name = "Capture Controls - Portrait", showBackground = true)
@Composable
private fun CaptureControlsPortraitPreview() {
    CamConTheme {
        Column(
            modifier = Modifier
                .background(Color.Black)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Idle State
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

            // Capturing State
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