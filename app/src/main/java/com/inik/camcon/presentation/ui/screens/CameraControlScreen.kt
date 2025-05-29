package com.inik.camcon.presentation.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Chip
import androidx.compose.material.ChipDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UsbOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.inik.camcon.R
import com.inik.camcon.domain.model.ShootingMode
import com.inik.camcon.presentation.viewmodel.CameraViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
fun CameraControlScreen(
    viewModel: CameraViewModel = hiltViewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> viewModel.stopLiveView()
                Lifecycle.Event.ON_STOP -> viewModel.disconnectCamera()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
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

    // Log UI state changes
    LaunchedEffect(uiState.isLiveViewActive) {
        Log.d("CameraControl", "라이브뷰 상태 변경: ${uiState.isLiveViewActive}")
    }

    // Log UI state changes
    LaunchedEffect(uiState.isLiveViewActive) {
        Log.d("CameraControl", "라이브뷰 상태 변경: ${uiState.isLiveViewActive}")
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
            // Simple test content
            Text(
                "CLICK TEST AREA",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
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

@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
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
    val context = LocalContext.current
    // Portrait 모드에서는 시스템 UI 항상 보이도록 설정
    SideEffect {
        (context as? Activity)?.let { activity ->
            Log.d("PortraitCameraLayout", "화면 방향 PORTRAIT로 설정 및 시스템 UI 복원")
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                activity.window.setDecorFitsSystemWindows(true)
                activity.window.insetsController?.show(WindowInsets.Type.systemBars())
            } else {
                @Suppress("DEPRECATION")
                activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
    }
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
                .combinedClickable(
                    onClick = {
                        Log.d("CameraControl", "=== 단일 클릭 감지 ===")
                        Log.d("CameraControl", "라이브뷰 활성: ${uiState.isLiveViewActive}")
                    },
                    onDoubleClick = {
                        Log.d("CameraControl", "=== 더블 클릭 감지 ===")
                        Log.d("CameraControl", "라이브뷰 활성: ${uiState.isLiveViewActive}")
                        if (uiState.isLiveViewActive) {
                            Log.d("CameraControl", "전체화면 모드로 진입 시작")
                            onEnterFullscreen()
                            Log.d("CameraControl", "전체화면 모드로 진입 완료")
                        } else {
                            Log.d("CameraControl", "더블 클릭 무시 - 라이브뷰 비활성")
                        }
                    }
                ),
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

            // 전체화면 모드 안내 텍스트 (라이브뷰 활성화 시에만 표시)
            if (uiState.isLiveViewActive) {
                Text(
                    "더블클릭으로 전체화면",
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .background(
                            Color.Black.copy(alpha = 0.5f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 12.sp
                )
            }
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

@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
fun FullscreenCameraView(
    uiState: com.inik.camcon.presentation.viewmodel.CameraUiState,
    cameraFeed: List<com.inik.camcon.domain.model.Camera>,
    viewModel: CameraViewModel,
    onExitFullscreen: () -> Unit
) {
    val context = LocalContext.current
    // Fullscreen 모드에서 시스템 UI 숨기기
    // SideEffect를 사용하여 Composable이 화면에 표시될 때마다 실행되도록 함
    SideEffect {
        (context as? Activity)?.let { activity ->
            Log.d("FullscreenCameraView", "화면 방향 LANDSCAPE로 설정 및 시스템 UI 숨김")
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                activity.window.setDecorFitsSystemWindows(false)
                activity.window.insetsController?.let { controller ->
                    controller.hide(WindowInsets.Type.systemBars())
                    controller.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                activity.window.decorView.systemUiVisibility = (
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                or View.SYSTEM_UI_FLAG_FULLSCREEN
                        )
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .combinedClickable(
                onClick = {
                    Log.d("CameraControl", "Single click in fullscreen")
                },
                onDoubleClick = {
                    Log.d("CameraControl", "Double click in fullscreen - exiting")
                    onExitFullscreen()
                }
            )
    ) {
        CameraPreviewArea(
            uiState = uiState,
            cameraFeed = cameraFeed,
            viewModel = viewModel
        )

        // 전체화면 종료 버튼 오버레이 (상단 우측)
        IconButton(
            onClick = {
                Log.d("CameraControl", "Exit fullscreen button clicked")
                onExitFullscreen()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .background(
                    Color.Black.copy(alpha = 0.6f),
                    CircleShape
                )
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "전체화면 종료",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        // 하단 안내 텍스트
        Text(
            "더블클릭으로 종료",
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .background(
                    Color.Black.copy(alpha = 0.5f),
                    RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 14.sp
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

// Preview Composables for development
@Preview(name = "Camera Control - Portrait", showBackground = true)
@Composable
fun CameraControlScreenPreview() {
    MaterialTheme {
        Surface {
            // Mock data for preview
            val mockUiState = com.inik.camcon.presentation.viewmodel.CameraUiState(
                isConnected = true,
                isLiveViewActive = false,
                isCapturing = false,
                shootingMode = ShootingMode.SINGLE,
                cameraSettings = com.inik.camcon.domain.model.CameraSettings(
                    iso = "400",
                    shutterSpeed = "1/60",
                    aperture = "5.6",
                    whiteBalance = "Auto",
                    focusMode = "AF-S",
                    exposureCompensation = "0"
                ),
                cameraCapabilities = com.inik.camcon.domain.model.CameraCapabilities(
                    model = "Canon EOS R5",
                    canCapturePhoto = true,
                    canCaptureVideo = true,
                    canLiveView = true,
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
                    availableIsoSettings = listOf("100", "200", "400", "800", "1600", "3200"),
                    availableShutterSpeeds = listOf(
                        "1/1000",
                        "1/500",
                        "1/250",
                        "1/125",
                        "1/60",
                        "1/30"
                    ),
                    availableApertures = listOf("1.4", "2.0", "2.8", "4.0", "5.6", "8.0"),
                    availableWhiteBalanceSettings = listOf(
                        "Auto",
                        "Daylight",
                        "Cloudy",
                        "Tungsten",
                        "Fluorescent"
                    ),
                    supportsRemoteControl = true,
                    supportsConfigChange = true,
                    batteryLevel = 85
                ),
                capturedPhotos = emptyList(),
                usbDeviceCount = 1,
                hasUsbPermission = true,
                supportedFeatures = listOf("라이브뷰", "원격촬영", "타임랩스"),
                error = null,
                liveViewFrame = null,
                supportedCamera = null
            )

            // Create mock ViewModel (would need to create a proper mock)
            Box(modifier = Modifier.fillMaxSize()) {
                PortraitCameraLayoutPreview(mockUiState)
            }
        }
    }
}

@Preview(name = "Camera Control - Landscape", showBackground = true, widthDp = 800, heightDp = 400)
@Composable
fun CameraControlLandscapePreview() {
    MaterialTheme {
        Surface {
            val mockUiState = com.inik.camcon.presentation.viewmodel.CameraUiState(
                isConnected = true,
                isLiveViewActive = true,
                isCapturing = false,
                shootingMode = ShootingMode.SINGLE,
                cameraSettings = com.inik.camcon.domain.model.CameraSettings(
                    iso = "400",
                    shutterSpeed = "1/60",
                    aperture = "5.6",
                    whiteBalance = "Auto",
                    focusMode = "AF-S",
                    exposureCompensation = "0"
                ),
                cameraCapabilities = com.inik.camcon.domain.model.CameraCapabilities(
                    model = "Canon EOS R5",
                    canCapturePhoto = true,
                    canCaptureVideo = true,
                    canLiveView = true,
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
                    availableIsoSettings = listOf("100", "200", "400", "800", "1600", "3200"),
                    availableShutterSpeeds = listOf(
                        "1/1000",
                        "1/500",
                        "1/250",
                        "1/125",
                        "1/60",
                        "1/30"
                    ),
                    availableApertures = listOf("1.4", "2.0", "2.8", "4.0", "5.6", "8.0"),
                    availableWhiteBalanceSettings = listOf(
                        "Auto",
                        "Daylight",
                        "Cloudy",
                        "Tungsten",
                        "Fluorescent"
                    ),
                    supportsRemoteControl = true,
                    supportsConfigChange = true,
                    batteryLevel = 85
                ),
                capturedPhotos = emptyList(),
                usbDeviceCount = 1,
                hasUsbPermission = true,
                supportedFeatures = listOf("라이브뷰", "원격촬영", "타임랩스"),
                error = null,
                liveViewFrame = null,
                supportedCamera = null
            )

            FullscreenCameraViewPreview(mockUiState)
        }
    }
}

@Preview(name = "Disconnected State", showBackground = true)
@Composable
fun CameraDisconnectedPreview() {
    MaterialTheme {
        Surface {
            val mockUiState = com.inik.camcon.presentation.viewmodel.CameraUiState(
                isConnected = false,
                isLiveViewActive = false,
                isCapturing = false,
                shootingMode = ShootingMode.SINGLE,
                cameraSettings = null,
                cameraCapabilities = null,
                capturedPhotos = emptyList(),
                usbDeviceCount = 0,
                hasUsbPermission = false,
                supportedFeatures = emptyList(),
                error = null,
                liveViewFrame = null,
                supportedCamera = null
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                CameraPreviewAreaPreview(mockUiState, emptyList())
            }
        }
    }
}

// Preview helper composables (without ViewModel dependency)
@Composable
fun PortraitCameraLayoutPreview(
    uiState: com.inik.camcon.presentation.viewmodel.CameraUiState
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Top Controls Bar
        TopControlsBarPreview(uiState)

        // Live View / Preview Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CameraPreviewAreaPreview(uiState, emptyList())
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
                            onClick = { }
                        )
                    }
                }

                // Capture Controls
                CaptureControlsPreview(uiState, false)
            }
        }
    }
}

@Composable
fun FullscreenCameraViewPreview(
    uiState: com.inik.camcon.presentation.viewmodel.CameraUiState
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (uiState.isLiveViewActive) {
            // Mock live view content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "라이브뷰 전체화면 모드\n(더블클릭으로 나가기)",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    fontSize = 18.sp
                )
            }

            // 상단 우측 종료 버튼
            IconButton(
                onClick = { },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(
                        Color.Black.copy(alpha = 0.5f),
                        CircleShape
                    )
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "전체화면 종료",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // 하단 중앙 안내 텍스트
            Text(
                "더블클릭으로 나가기",
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .background(
                        Color.Black.copy(alpha = 0.5f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                fontSize = 14.sp
            )

            // 카메라 설정 정보 오버레이 (상단 좌측)
            uiState.cameraSettings?.let { settings ->
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .background(
                            Color.Black.copy(alpha = 0.6f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    CameraSettingChip("ISO ${settings.iso}")
                    Spacer(modifier = Modifier.width(8.dp))
                    CameraSettingChip(settings.shutterSpeed)
                    Spacer(modifier = Modifier.width(8.dp))
                    CameraSettingChip("f/${settings.aperture}")
                }
            }
        } else {
            CameraPreviewAreaPreview(uiState, emptyList())
        }
    }
}

@Composable
fun TopControlsBarPreview(
    uiState: com.inik.camcon.presentation.viewmodel.CameraUiState
) {
    Surface(
        color = Color.Black.copy(alpha = 0.7f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Camera Connection Status
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
                        uiState.cameraCapabilities?.model ?: "카메라 연결됨"
                    } else {
                        "카메라 연결 안됨"
                    },
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Settings Button
            IconButton(onClick = { }) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "설정",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun CameraPreviewAreaPreview(
    uiState: com.inik.camcon.presentation.viewmodel.CameraUiState,
    cameraFeed: List<com.inik.camcon.domain.model.Camera>
) {
    if (uiState.isLiveViewActive) {
        // Mock live view display
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "라이브뷰 화면\n(더블클릭으로 전체화면)",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp
                )
            }

            // Stop button overlay
            Button(
                onClick = { },
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
                "카메라가 연결되지 않음",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "USB로 카메라를 연결해주세요",
                color = Color.Gray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    } else {
        // Connected but not live view
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Videocam,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.primary
                )
            ) {
                Text("라이브뷰 시작")
            }
        }
    }
}

@Composable
fun CaptureControlsPreview(
    uiState: com.inik.camcon.presentation.viewmodel.CameraUiState,
    isVertical: Boolean
) {
    if (isVertical) {
        // Vertical layout for landscape mode
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CaptureControlsContentPreview(uiState)
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
            CaptureControlsContentPreview(uiState)
        }
    }
}

@Composable
fun CaptureControlsContentPreview(
    uiState: com.inik.camcon.presentation.viewmodel.CameraUiState
) {
    // Photo Gallery Button
    IconButton(
        onClick = { },
        modifier = Modifier.size(48.dp)
    ) {
        Icon(
            Icons.Default.PhotoLibrary,
            contentDescription = "갤러리",
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
            ),
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
        onClick = { },
        enabled = uiState.isConnected,
        modifier = Modifier.size(48.dp)
    ) {
        Icon(
            Icons.Default.CenterFocusStrong,
            contentDescription = "포커스",
            tint = if (uiState.isConnected) Color.White else Color.Gray,
            modifier = Modifier.size(32.dp)
        )
    }
}
