package com.inik.camcon.presentation.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.inik.camcon.R
import com.inik.camcon.domain.model.ShootingMode
import com.inik.camcon.presentation.viewmodel.CameraViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun CameraControlScreen(
    viewModel: CameraViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val cameraFeed by viewModel.cameraFeed.collectAsState()
    val scope = rememberCoroutineScope()
    val bottomSheetState = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden)
    val configuration = LocalConfiguration.current
    val isLandscape =
        configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    
    var showTimelapseDialog by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var isFullscreen by rememberSaveable { mutableStateOf(false) }

    // 전체화면 모드일 때는 가로화면으로 강제 전환
    val context = LocalContext.current
    LaunchedEffect(isFullscreen) {
        if (isFullscreen) {
            (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    ModalBottomSheetLayout(
        sheetState = bottomSheetState,
        sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        sheetContent = {
            CameraSettingsSheet(
                settings = uiState.cameraSettings,
                onSettingChange = { key, value ->
                    viewModel.updateCameraSetting(key, value)
                },
                onClose = {
                    scope.launch { bottomSheetState.hide() }
                }
            )
        }
    ) {
        if (isFullscreen) {
            // 전체화면 모드
            FullscreenCameraView(
                uiState = uiState,
                cameraFeed = cameraFeed,
                viewModel = viewModel,
                onExitFullscreen = { isFullscreen = false }
            )
        } else {
            // 일반 모드 (세로 고정)
            PortraitCameraLayout(
                uiState = uiState,
                cameraFeed = cameraFeed,
                viewModel = viewModel,
                scope = scope,
                bottomSheetState = bottomSheetState,
                onShowTimelapseDialog = { showTimelapseDialog = true },
                onEnterFullscreen = { isFullscreen = true }
            )
        }
    }

    // Timelapse Settings Dialog
    if (showTimelapseDialog) {
        TimelapseSettingsDialog(
            onConfirm = { interval, shots ->
                viewModel.startTimelapse(interval, shots)
                showTimelapseDialog = false
            },
            onDismiss = { showTimelapseDialog = false }
        )
    }

    // Error handling
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            // Show error snackbar
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun LandscapeCameraLayout(
    uiState: com.inik.camcon.presentation.viewmodel.CameraUiState,
    cameraFeed: List<com.inik.camcon.domain.model.Camera>,
    viewModel: CameraViewModel,
    scope: kotlinx.coroutines.CoroutineScope,
    bottomSheetState: ModalBottomSheetState,
    onShowTimelapseDialog: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Left side - Live View Area (takes most space)
        Box(
            modifier = Modifier
                .weight(2f)
                .fillMaxHeight()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CameraPreviewArea(
                uiState = uiState,
                cameraFeed = cameraFeed,
                viewModel = viewModel
            )

            // Top Controls Overlay
            TopControlsBar(
                uiState = uiState,
                cameraFeed = cameraFeed,
                onSettingsClick = { scope.launch { bottomSheetState.show() } },
                modifier = Modifier.align(Alignment.TopCenter)
            )

            // Camera Settings Overlay
            CameraSettingsOverlay(
                settings = uiState.cameraSettings,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 60.dp)
            )
        }

        // Right side - Controls
        Surface(
            color = Color.Black.copy(alpha = 0.9f),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Shooting Mode Selector
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ShootingMode.values()) { mode ->
                        ShootingModeChip(
                            mode = mode,
                            isSelected = uiState.shootingMode == mode,
                            isEnabled = uiState.isConnected,
                            onClick = { viewModel.setShootingMode(mode) }
                        )
                    }
                }

                // Capture Controls
                CaptureControls(
                    uiState = uiState,
                    viewModel = viewModel,
                    onShowTimelapseDialog = onShowTimelapseDialog,
                    isVertical = true
                )

                // Recent Captures
                if (uiState.capturedPhotos.isNotEmpty()) {
                    RecentCapturesGrid(
                        photos = uiState.capturedPhotos,
                        isVertical = true
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun PortraitCameraLayout(
    uiState: com.inik.camcon.presentation.viewmodel.CameraUiState,
    cameraFeed: List<com.inik.camcon.domain.model.Camera>,
    viewModel: CameraViewModel,
    scope: kotlinx.coroutines.CoroutineScope,
    bottomSheetState: ModalBottomSheetState,
    onShowTimelapseDialog: () -> Unit,
    onEnterFullscreen: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Top Controls Bar
        TopControlsBar(
            uiState = uiState,
            cameraFeed = cameraFeed,
            onSettingsClick = { scope.launch { bottomSheetState.show() } }
        )

        // Live View / Preview Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black)
                .clickable {
                    if (uiState.isLiveViewActive) {
                        onEnterFullscreen()
                    }
                }
                .doubleClickHandler {
                    if (uiState.isLiveViewActive) {
                        onEnterFullscreen()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            CameraPreviewArea(
                uiState = uiState,
                cameraFeed = cameraFeed,
                viewModel = viewModel
            )

            // Camera Settings Overlay
            CameraSettingsOverlay(
                settings = uiState.cameraSettings,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        // Bottom Controls
        Card(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = Color.Black.copy(alpha = 0.9f),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            Column {
                // Shooting Mode Selector
                LazyRow(
                    modifier = Modifier.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(ShootingMode.values()) { mode ->
                        ShootingModeChip(
                            mode = mode,
                            isSelected = uiState.shootingMode == mode,
                            isEnabled = uiState.isConnected,
                            onClick = { viewModel.setShootingMode(mode) }
                        )
                    }
                }

                // Capture Controls
                CaptureControls(
                    uiState = uiState,
                    viewModel = viewModel,
                    onShowTimelapseDialog = onShowTimelapseDialog,
                    isVertical = false
                )

                // Recent Captures
                if (uiState.capturedPhotos.isNotEmpty()) {
                    RecentCapturesGrid(
                        photos = uiState.capturedPhotos,
                        isVertical = false
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun FullscreenCameraView(
    uiState: com.inik.camcon.presentation.viewmodel.CameraUiState,
    cameraFeed: List<com.inik.camcon.domain.model.Camera>,
    viewModel: CameraViewModel,
    onExitFullscreen: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onExitFullscreen() }
            .doubleClickHandler { onExitFullscreen() }
    ) {
        CameraPreviewArea(
            uiState = uiState,
            cameraFeed = cameraFeed,
            viewModel = viewModel
        )
    }
}

@Composable
fun TopControlsBar(
    uiState: com.inik.camcon.presentation.viewmodel.CameraUiState,
    cameraFeed: List<com.inik.camcon.domain.model.Camera>,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Black.copy(alpha = 0.7f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Camera Connection Status
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            if (uiState.isConnected)
                                Color.Green.copy(alpha = 0.2f)
                            else
                                Color.Red.copy(alpha = 0.2f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (uiState.isConnected) Color.Green else Color.Red
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiState.isConnected) {
                            uiState.cameraCapabilities?.model
                                ?: cameraFeed.firstOrNull()?.name
                                ?: stringResource(R.string.camera_connected)
                        } else {
                            stringResource(R.string.camera_disconnected)
                        },
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // 카메라 기능 간략 표시
                uiState.cameraCapabilities?.let { capabilities ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(start = 12.dp)
                    ) {
                        if (capabilities.canLiveView) {
                            FeatureBadge("라이브뷰", Color.Blue)
                        }
                        if (capabilities.supportsTimelapse) {
                            FeatureBadge("타임랩스", Color(0xFF9C27B0))
                        }
                        if (capabilities.supportsBurstMode) {
                            FeatureBadge("버스트", Color(0xFFFF9800))
                        }
                    }
                }
            }

            // Settings Button
            IconButton(onClick = onSettingsClick) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = stringResource(R.string.settings),
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun CameraPreviewArea(
    uiState: com.inik.camcon.presentation.viewmodel.CameraUiState,
    cameraFeed: List<com.inik.camcon.domain.model.Camera>,
    viewModel: CameraViewModel
) {
    if (uiState.isLiveViewActive && uiState.liveViewFrame != null) {
        // Display live view frame
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            uiState.liveViewFrame?.let { frame ->
                // Convert byte array to Bitmap and display
                val bitmap = remember(frame) {
                    try {
                        android.graphics.BitmapFactory.decodeByteArray(
                            frame.data,
                            0,
                            frame.data.size
                        )
                    } catch (e: Exception) {
                        null
                    }
                }

                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Live View",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } ?: run {
                    Text(
                        "라이브뷰 프레임 디코딩 실패",
                        color = Color.Red
                    )
                }
            }

            // 라이브뷰 중지 버튼 오버레이
            Button(
                onClick = { viewModel.stopLiveView() },
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
        // Camera not connected state
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
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

            // 디버그 정보 표시
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                backgroundColor = Color.DarkGray.copy(alpha = 0.8f),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        "연결 상태 확인:",
                        color = Color.Yellow,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "• 카메라 연결됨: ${uiState.isConnected}",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    Text(
                        "• 감지된 카메라 수: ${cameraFeed.size}",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    Text(
                        "• USB 디바이스 수: ${uiState.usbDeviceCount}",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    Text(
                        "• USB 권한: ${if (uiState.hasUsbPermission) "승인됨" else "대기중"}",
                        color = if (uiState.hasUsbPermission) Color.Green else Color.Yellow,
                        fontSize = 12.sp
                    )
                    if (cameraFeed.isNotEmpty()) {
                        Text(
                            "• 카메라 이름: ${cameraFeed.first().name}",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }

                    // USB 디바이스가 감지되지 않으면 추가 정보 표시
                    if (uiState.usbDeviceCount == 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "USB 확인사항:",
                            color = Color.Red,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "1. USB 케이블 연결 확인",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                        Text(
                            "2. 카메라 전원 확인",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                        Text(
                            "3. USB 모드 PTP/MTP 설정",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                        Text(
                            "4. Android 개발자 옵션에서 USB 디버깅 활성화",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }

                    // 지원 기능 표시
                    if (uiState.supportedFeatures.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "• 지원 기능:",
                            color = Color.Cyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            uiState.supportedFeatures.joinToString(", "),
                            color = Color.Green,
                            fontSize = 11.sp
                        )
                    }

                    // 카메라 기능 정보 표시
                    uiState.cameraCapabilities?.let { capabilities ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "카메라 기능 정보:",
                            color = Color.Green,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        // 기본 촬영 기능
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                CapabilityItem("사진 촬영", capabilities.canCapturePhoto)
                                CapabilityItem("동영상 촬영", capabilities.canCaptureVideo)
                                CapabilityItem("라이브뷰", capabilities.canLiveView)
                                CapabilityItem("원격 촬영", capabilities.canTriggerCapture)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                CapabilityItem("버스트 모드", capabilities.supportsBurstMode)
                                CapabilityItem("타임랩스", capabilities.supportsTimelapse)
                                CapabilityItem("브라켓팅", capabilities.supportsBracketing)
                                CapabilityItem("벌브 모드", capabilities.supportsBulbMode)
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // 초점 기능
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                CapabilityItem("자동 초점", capabilities.supportsAutofocus)
                                CapabilityItem("수동 초점", capabilities.supportsManualFocus)
                                CapabilityItem("초점 포인트", capabilities.supportsFocusPoint)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                CapabilityItem("파일 다운로드", capabilities.canDownloadFiles)
                                CapabilityItem("파일 삭제", capabilities.canDeleteFiles)
                                CapabilityItem("파일 미리보기", capabilities.canPreviewFiles)
                            }
                        }

                        // 설정 가능한 옵션들
                        if (capabilities.availableIsoSettings.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "ISO 옵션: ${
                                    capabilities.availableIsoSettings.take(5).joinToString(", ")
                                }${if (capabilities.availableIsoSettings.size > 5) "..." else ""}",
                                color = Color.Cyan,
                                fontSize = 10.sp
                            )
                        }

                        if (capabilities.availableShutterSpeeds.isNotEmpty()) {
                            Text(
                                "셔터 속도: ${
                                    capabilities.availableShutterSpeeds.take(5).joinToString(", ")
                                }${if (capabilities.availableShutterSpeeds.size > 5) "..." else ""}",
                                color = Color.Cyan,
                                fontSize = 10.sp
                            )
                        }

                        if (capabilities.availableApertures.isNotEmpty()) {
                            Text(
                                "조리개: ${
                                    capabilities.availableApertures.take(5).joinToString(", ")
                                }${if (capabilities.availableApertures.size > 5) "..." else ""}",
                                color = Color.Cyan,
                                fontSize = 10.sp
                            )
                        }

                        // 새로고침 버튼
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.refreshCameraCapabilities() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = Color.Blue.copy(alpha = 0.7f)
                            )
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("기능 정보 새로고침", color = Color.White, fontSize = 12.sp)
                        }
                    }

                    // libgphoto2 지원 여부
                    uiState.supportedCamera?.let { camera ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "✓ libgphoto2 지원됨 (${camera.driver})",
                            color = Color.Green,
                            fontSize = 11.sp
                        )
                    } ?: run {
                        if (cameraFeed.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "⚠ libgphoto2 지원 확인 중...",
                                color = Color.Yellow,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // 에러 메시지 표시
                    uiState.error?.let { error ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "• 에러: $error",
                            color = Color.Red,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
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

            // USB 새로고침 버튼 추가
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    viewModel.refreshUsbDevices()
                },
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

            // 카메라 연결 해제 버튼 추가
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    viewModel.disconnectCamera()
                },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color.Red.copy(alpha = 0.7f)
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LinkOff,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("PC 모드 완전 종료", color = Color.White, fontSize = 12.sp)
                }
            }

            // USB 디바이스가 있지만 권한이 없는 경우 권한 요청 버튼
            if (uiState.usbDeviceCount > 0 && !uiState.hasUsbPermission) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        viewModel.requestUsbPermission()
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFFFF6B35) // 주황색
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
    } else {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
        }
    }
}

@Composable
fun CameraSettingsOverlay(
    settings: com.inik.camcon.domain.model.CameraSettings?,
    modifier: Modifier = Modifier
) {
    settings?.let { settings ->
        Row(
            modifier = modifier
                .padding(16.dp)
                .background(
                    Color.Black.copy(alpha = 0.6f),
                    RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            CameraSettingChip("ISO ${settings.iso}")
            Spacer(modifier = Modifier.width(8.dp))
            CameraSettingChip(settings.shutterSpeed)
            Spacer(modifier = Modifier.width(8.dp))
            CameraSettingChip("f/${settings.aperture}")
        }
    }
}

@Composable
fun CaptureControls(
    uiState: com.inik.camcon.presentation.viewmodel.CameraUiState,
    viewModel: CameraViewModel,
    onShowTimelapseDialog: () -> Unit,
    isVertical: Boolean
) {
    if (isVertical) {
        // Vertical layout for landscape mode
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CaptureControlsContent(
                uiState = uiState,
                viewModel = viewModel,
                onShowTimelapseDialog = onShowTimelapseDialog,
                isVertical = true
            )
        }
    } else {
        // Horizontal layout for portrait mode
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CaptureControlsContent(
                uiState = uiState,
                viewModel = viewModel,
                onShowTimelapseDialog = onShowTimelapseDialog,
                isVertical = false
            )
        }
    }
}

@Composable
fun CaptureControlsContent(
    uiState: com.inik.camcon.presentation.viewmodel.CameraUiState,
    viewModel: CameraViewModel,
    onShowTimelapseDialog: () -> Unit,
    isVertical: Boolean
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
        enabled = uiState.isConnected,
        modifier = Modifier.size(48.dp)
    ) {
        Icon(
            Icons.Default.CenterFocusStrong,
            contentDescription = stringResource(R.string.focus),
            tint = if (uiState.isConnected) Color.White else Color.Gray,
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
fun RecentCapturesGrid(
    photos: List<com.inik.camcon.domain.model.CapturedPhoto>,
    isVertical: Boolean
) {
    if (isVertical) {
        // Vertical grid for landscape mode
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            photos.take(6).chunked(2).forEach { rowPhotos ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    rowPhotos.forEach { photo ->
                        Card(
                            modifier = Modifier
                                .size(60.dp)
                                .clickable { /* Open photo */ },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.DarkGray)
                            )
                        }
                    }
                }
            }
        }
    } else {
        // Horizontal list for portrait mode
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(photos.take(10)) { photo ->
                Card(
                    modifier = Modifier
                        .size(72.dp)
                        .clickable { /* Open photo */ },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.DarkGray)
                    )
                }
            }
        }
    }
}

@Composable
fun CameraSettingChip(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium
    )
}

@Composable
fun ShootingModeChip(
    mode: ShootingMode,
    isSelected: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    val displayName = when (mode) {
        ShootingMode.SINGLE -> stringResource(R.string.single_shot)
        ShootingMode.BURST -> stringResource(R.string.burst_mode)
        ShootingMode.TIMELAPSE -> stringResource(R.string.timelapse)
        ShootingMode.BULB -> stringResource(R.string.bulb_mode)
        ShootingMode.HDR_BRACKET -> stringResource(R.string.hdr_bracket)
    }
    
    Chip(
        onClick = onClick,
        enabled = isEnabled,
        colors = ChipDefaults.chipColors(
            backgroundColor = if (isSelected && isEnabled) MaterialTheme.colors.primary
            else if (isEnabled) Color.Gray.copy(alpha = 0.3f)
            else Color.Gray.copy(alpha = 0.1f),
            contentColor = if (isEnabled) Color.White else Color.Gray,
            disabledBackgroundColor = Color.Gray.copy(alpha = 0.1f),
            disabledContentColor = Color.Gray
        )
    ) {
        Text(displayName, fontSize = 14.sp)
    }
}

@Composable
fun CameraSettingsSheet(
    settings: com.inik.camcon.domain.model.CameraSettings?,
    onSettingChange: (String, String) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.camera_settings),
                style = MaterialTheme.typography.h6
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Settings would be dynamically loaded based on camera capabilities
        settings?.let {
            SettingRow(stringResource(R.string.iso), it.iso) { value ->
                onSettingChange("iso", value)
            }
            SettingRow(stringResource(R.string.shutter_speed), it.shutterSpeed) { value ->
                onSettingChange("shutterSpeed", value)
            }
            SettingRow(stringResource(R.string.aperture), it.aperture) { value ->
                onSettingChange("aperture", value)
            }
            SettingRow(stringResource(R.string.white_balance), it.whiteBalance) { value ->
                onSettingChange("whiteBalance", value)
            }
        }
    }
}

@Composable
fun SettingRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, modifier = Modifier.weight(1f))
        // In real app, this would be a dropdown or appropriate control
        Text(
            value,
            color = MaterialTheme.colors.primary,
            modifier = Modifier.clickable { /* Show options */ }
        )
    }
}

@Composable
fun TimelapseSettingsDialog(
    onConfirm: (interval: Int, shots: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var interval by remember { mutableStateOf("5") }
    var totalShots by remember { mutableStateOf("100") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.timelapse_settings)) },
        text = {
            Column {
                OutlinedTextField(
                    value = interval,
                    onValueChange = { interval = it },
                    label = { Text(stringResource(R.string.interval_seconds)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = totalShots,
                    onValueChange = { totalShots = it },
                    label = { Text(stringResource(R.string.total_shots)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        interval.toIntOrNull() ?: 5,
                        totalShots.toIntOrNull() ?: 100
                    )
                }
            ) {
                Text(stringResource(R.string.start_timelapse))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun Modifier.doubleClickHandler(onDoubleTap: () -> Unit): Modifier = composed {
    val doubleTap = remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    clickable {
        coroutineScope.launch {
            if (doubleTap.value) {
                doubleTap.value = false
                onDoubleTap()
            } else {
                doubleTap.value = true
                kotlinx.coroutines.delay(300)
                doubleTap.value = false
            }
        }
    }
}

@Composable
fun FeatureBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun CapabilityItem(
    name: String,
    isSupported: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 1.dp)
    ) {
        Icon(
            if (isSupported) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            tint = if (isSupported) Color.Green else Color.Red,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            name,
            color = if (isSupported) Color.White else Color.Gray,
            fontSize = 10.sp
        )
    }
}

