package com.inik.camcon.presentation.ui.screens.components

// Coil imports for image loading
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inik.camcon.R
import com.inik.camcon.domain.model.Camera
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.viewmodel.CameraUiState
import com.inik.camcon.presentation.viewmodel.CameraViewModel

/**
 * 카메라 프리뷰 영역 - 라이브뷰와 연결 상태를 관리
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CameraPreviewArea(
    uiState: CameraUiState,
    cameraFeed: List<Camera>,
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier,
    onDoubleClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .combinedClickable(
                onClick = { /* 단일 클릭 처리 */ },
                onDoubleClick = { onDoubleClick?.invoke() }
            )
    ) {
        if (uiState.isLiveViewActive && uiState.liveViewFrame != null) {
            // Display live view frame using Android Bitmap
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                uiState.liveViewFrame?.let { frame ->
                    // 바이트 배열을 비트맵으로 직접 디코딩
                    val bitmap = remember(frame.timestamp) {
                        try {
                            Log.d("CameraPreview", "바이트 배열을 비트맵으로 디코딩 시도: ${frame.data.size} bytes")
                            BitmapFactory.decodeByteArray(frame.data, 0, frame.data.size)
                        } catch (e: Exception) {
                            Log.e("CameraPreview", "비트맵 디코딩 실패", e)
                            null
                        }
                    }

                    bitmap?.let {
                        Log.d("CameraPreview", "비트맵 디코딩 성공: ${it.width}x${it.height}")
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "Live View",
                            modifier = Modifier
                                .fillMaxSize()
                                .combinedClickable(
                                    onClick = { /* 단일 클릭 처리 */ },
                                    onDoubleClick = {
                                        Log.d("CameraPreview", "라이브뷰 이미지 더블클릭 감지")
                                        onDoubleClick?.invoke()
                                    }
                                ),
                            contentScale = ContentScale.Fit
                        )
                    } ?: run {
                        Log.w("CameraPreview", "비트맵 디코딩 실패 - LoadingOverlay 표시")
                        LoadingOverlay(stringResource(R.string.processing_liveview_frame))
                    }
                } ?: LoadingOverlay(stringResource(R.string.loading_liveview_frame))

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
                    Text(stringResource(R.string.stop_live_view), color = Color.White)
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
            LoadingOverlay(stringResource(R.string.capturing_photo))
        }

        // 라이브뷰 로딩 오버레이
        if (uiState.isLiveViewLoading) {
            LoadingOverlay(stringResource(R.string.starting_liveview))
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
                stringResource(R.string.liveview_not_supported),
                color = Color.Gray,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.liveview_not_supported_detail),
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
                Text(stringResource(R.string.refresh_usb))
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
                    Text(stringResource(R.string.request_usb_permission), color = Color.White)
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