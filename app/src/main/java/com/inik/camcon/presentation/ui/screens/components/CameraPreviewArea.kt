package com.inik.camcon.presentation.ui.screens.components

// Coil imports for image loading
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UsbOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.inik.camcon.R
import com.inik.camcon.domain.model.Camera
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.viewmodel.CameraUiState
import com.inik.camcon.presentation.viewmodel.CameraViewModel

/**
 * 카메라 프리뷰 영역 - 라이브뷰와 연결 상태를 관리
 */
@Composable
fun CameraPreviewArea(
    uiState: CameraUiState,
    cameraFeed: List<Camera>,
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (uiState.isLiveViewActive && uiState.liveViewFrame != null) {
            // Display live view frame using Coil
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                uiState.liveViewFrame?.let { frame ->
                    // Use Coil's AsyncImage with a custom ImageRequest with byte array
                    val context = LocalContext.current
                    val imageRequest = remember(frame) {
                        ImageRequest.Builder(context)
                            .data(frame.data)
                            .crossfade(true)
                            .memoryCacheKey("liveViewFrame")
                            .build()
                    }
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = "Live View",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        placeholder = null,
                        error = null
                    )
                } ?: LoadingIndicator("라이브뷰 프레임 로딩 중...")

                // 라이브뷰 중지 버튼 오버레이
                Button(
                    onClick = {
                        Log.d("CameraControl", "Stop live view button clicked")
                        viewModel.stopLiveView()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color.Red.copy(alpha = 0.8f)
                    )
                ) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = "Stop Live View",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("라이브뷰 중지", color = Color.White)
                }
            }
        } else if (!uiState.isConnected) {
            CameraDisconnectedState(
                uiState = uiState,
                cameraFeed = cameraFeed,
                viewModel = viewModel
            )
        } else {
            CameraConnectedState(
                uiState = uiState,
                viewModel = viewModel
            )
        }

        // 전역 로딩 오버레이
        if (uiState.isCapturing) {
            LoadingOverlay("촬영 중...")
        }

        // 라이브뷰 로딩 오버레이
        if (uiState.isLiveViewLoading) {
            LoadingOverlay("라이브뷰 시작 중...")
        }
    }
}

/**
 * 카메라 연결 안됨 상태
 */
@Composable
fun CameraDisconnectedState(
    uiState: CameraUiState,
    cameraFeed: List<Camera>,
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.UsbOff,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            stringResource(R.string.camera_not_connected),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.connect_camera_usb),
            color = Color.Gray,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))
        CameraConnectionButtons(
            uiState = uiState,
            cameraFeed = cameraFeed,
            viewModel = viewModel
        )
    }
}

/**
 * 카메라 연결됨 상태 (라이브뷰 비활성)
 */
@Composable
fun CameraConnectedState(
    uiState: CameraUiState,
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        // 라이브뷰 지원 여부 확인
        val supportsLiveView = uiState.cameraCapabilities?.canLiveView ?: false

        if (supportsLiveView) {
            Icon(
                if (uiState.isLiveViewActive) Icons.Default.VideocamOff
                else Icons.Default.Videocam,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (uiState.isLiveViewActive) {
                        viewModel.stopLiveView()
                    } else {
                        viewModel.startLiveView()
                    }
                },
                enabled = uiState.isConnected,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (uiState.isConnected)
                        MaterialTheme.colors.primary
                    else
                        Color.Gray.copy(alpha = 0.5f),
                    disabledBackgroundColor = Color.Gray.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    if (uiState.isLiveViewActive)
                        stringResource(R.string.stop_live_view)
                    else
                        stringResource(R.string.start_live_view)
                )
            }
        } else {
            // 라이브뷰를 지원하지 않는 경우
            Icon(
                Icons.Default.VideocamOff,
                contentDescription = null,
                tint = Color.Red.copy(alpha = 0.5f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "라이브뷰 지원 안됨",
                color = Color.Gray,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "이 카메라 모델은 라이브뷰를 지원하지 않습니다",
                color = Color.Gray.copy(alpha = 0.7f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 카메라 연결 버튼들
 */
@Composable
fun CameraConnectionButtons(
    uiState: CameraUiState,
    cameraFeed: List<Camera>,
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = {
                // Try to reconnect or show camera list
                cameraFeed.firstOrNull()?.let { camera ->
                    viewModel.connectCamera(camera.id)
                } ?: run {
                    // 카메라가 없으면 강제로 연결 시도
                    viewModel.connectCamera("auto")
                }
            },
            colors = ButtonDefaults.buttonColors(
                backgroundColor = MaterialTheme.colors.primary
            )
        ) {
            Text(stringResource(R.string.retry_connection))
        }

        // USB 새로고침 버튼
        Button(
            onClick = { viewModel.refreshUsbDevices() },
            colors = ButtonDefaults.buttonColors(
                backgroundColor = MaterialTheme.colors.secondary
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("USB 새로고침")
            }
        }

        // USB 권한 요청 버튼
        if (uiState.usbDeviceCount > 0 && !uiState.hasUsbPermission) {
            Button(
                onClick = { viewModel.requestUsbPermission() },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color(0xFFFF6B35)
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("USB 권한 요청", color = Color.White)
                }
            }
        }
    }
}

@Preview(name = "Camera Preview - Connected", showBackground = true)
@Composable
private fun CameraPreviewConnectedPreview() {
    CamConTheme {
        Box(
            modifier = Modifier
                .size(300.dp)
                .background(Color.Black)
        ) {
            CameraConnectedState(
                uiState = CameraUiState(
                    isConnected = true,
                    cameraCapabilities = com.inik.camcon.domain.model.CameraCapabilities(
                        model = "Canon EOS R5",
                        canLiveView = true,
                        canCapturePhoto = true,
                        canCaptureVideo = true,
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
                viewModel = androidx.hilt.navigation.compose.hiltViewModel()
            )
        }
    }
}