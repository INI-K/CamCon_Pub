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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
    CaptureControlsCore(
        uiState = uiState,
        onCaptureClick = {
            when (uiState.shootingMode) {
                ShootingMode.TIMELAPSE -> onShowTimelapseDialog()
                else -> viewModel.capturePhoto()
            }
        },
        onFocusClick = { viewModel.performAutoFocus() },
        onGalleryClick = { },
        isVertical = isVertical,
        modifier = modifier
    )
}

@Composable
private fun CaptureControlsContent(
    uiState: CameraUiState,
    onCaptureClick: () -> Unit,
    onFocusClick: () -> Unit,
    onGalleryClick: () -> Unit
) {
    // 갤러리 버튼
    IconButton(
        onClick = onGalleryClick,
        modifier = Modifier
            .size(50.dp)
            .background(Color(0x66384456), CircleShape)
    ) {
        Icon(
            Icons.Default.PhotoLibrary,
            contentDescription = stringResource(R.string.gallery),
            tint = Color(0xFFF2D8BE),
            modifier = Modifier.size(30.dp)
        )
    }

    // 메인 촬영 버튼
    val ringColor = if (uiState.isConnected) Color(0xFFFFD8B3) else Color(0xFF8A8A8A)
    val coreColor = if (uiState.isConnected) Color(0xFFCB5B15) else Color(0xFF5E636B)
    Box(
        modifier = Modifier
            .size(84.dp)
            .clip(CircleShape)
            .border(3.dp, ringColor, CircleShape)
            .clickable(
                enabled = uiState.isConnected && !uiState.isCapturing
            ) { onCaptureClick() },
        contentAlignment = Alignment.Center
    ) {
        if (uiState.isCapturing) {
            CircularProgressIndicator(
                color = ringColor,
                modifier = Modifier.size(62.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(66.dp)
                    .clip(CircleShape)
                    .background(coreColor)
            )
        }
    }

    // 포커스 버튼
    IconButton(
        onClick = onFocusClick,
        enabled = uiState.isConnected && !uiState.isFocusing,
        modifier = Modifier
            .size(50.dp)
            .background(Color(0x66384456), CircleShape)
    ) {
        if (uiState.isFocusing) {
            CircularProgressIndicator(
                color = Color(0xFFFFD8B3),
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                Icons.Default.CenterFocusStrong,
                contentDescription = stringResource(R.string.focus),
                tint = if (uiState.isConnected) Color(0xFFFFD8B3) else Color.Gray,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
@Composable
private fun CaptureControlsCore(
    uiState: CameraUiState,
    onCaptureClick: () -> Unit,
    onFocusClick: () -> Unit,
    onGalleryClick: () -> Unit,
    isVertical: Boolean,
    modifier: Modifier = Modifier
) {
    val shellModifier = modifier
        .shadow(10.dp, RoundedCornerShape(24.dp))
        .clip(RoundedCornerShape(24.dp))
    if (isVertical) {
        Surface(
            modifier = shellModifier,
            color = Color.Transparent
        ) {
            Column(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xCC151C2A),
                                Color(0xCC1C2333),
                                Color(0xCC161D2C)
                            )
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CaptureControlsContent(
                    uiState = uiState,
                    onCaptureClick = onCaptureClick,
                    onFocusClick = onFocusClick,
                    onGalleryClick = onGalleryClick
                )
            }
        }
    } else {
        Surface(
            modifier = shellModifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            color = Color.Transparent
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xCC151C2A),
                                Color(0xCC1C2333),
                                Color(0xCC161D2C)
                            )
                        )
                    )
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CaptureControlsContent(
                    uiState = uiState,
                    onCaptureClick = onCaptureClick,
                    onFocusClick = onFocusClick,
                    onGalleryClick = onGalleryClick
                )
            }
        }
    }
}

@Preview(name = "Capture Controls - Portrait", showBackground = true)
@Composable
private fun CaptureControlsPortraitPreview() {
    CamConTheme {
        Column(
            modifier = Modifier
                .background(Color(0xFF121722))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 기본 상태
            CaptureControlsCore(
                uiState = CameraUiState(
                    isConnected = true,
                    isCapturing = false,
                    isFocusing = false,
                    shootingMode = ShootingMode.SINGLE
                ),
                onCaptureClick = { },
                onFocusClick = { },
                onGalleryClick = { },
                isVertical = false
            )

            // 촬영 중 상태
            CaptureControlsCore(
                uiState = CameraUiState(
                    isConnected = true,
                    isCapturing = true,
                    isFocusing = false,
                    shootingMode = ShootingMode.BURST
                ),
                onCaptureClick = { },
                onFocusClick = { },
                onGalleryClick = { },
                isVertical = true
            )
        }
    }
}