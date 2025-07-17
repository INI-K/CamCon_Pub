package com.inik.camcon.presentation.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Photo
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.inik.camcon.R
import com.inik.camcon.domain.model.Camera
import com.inik.camcon.domain.model.CameraSettings
import com.inik.camcon.domain.model.CapturedPhoto
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.ui.screens.components.CameraPreviewArea
import com.inik.camcon.presentation.ui.screens.components.CameraSettingsOverlay
import com.inik.camcon.presentation.ui.screens.components.CaptureControls
import com.inik.camcon.presentation.ui.screens.components.LoadingOverlay
import com.inik.camcon.presentation.ui.screens.components.ShootingModeSelector
import com.inik.camcon.presentation.ui.screens.components.TopControlsBar
import com.inik.camcon.presentation.ui.screens.dialogs.CameraConnectionHelpDialog
import com.inik.camcon.presentation.ui.screens.dialogs.TimelapseSettingsDialog
import com.inik.camcon.presentation.viewmodel.AppSettingsViewModel
import com.inik.camcon.presentation.viewmodel.CameraUiState
import com.inik.camcon.presentation.viewmodel.CameraViewModel
import kotlinx.coroutines.launch

/**
 * 메인 카메라 컨트롤 스크린 - 컴포넌트들로 분리됨
 * 분리된 컴포넌트들을 조합하여 화면을 구성
 */
@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
fun CameraControlScreen(
    viewModel: CameraViewModel = hiltViewModel(),
    appSettingsViewModel: AppSettingsViewModel = hiltViewModel()
) {
    var showConnectionHelpDialog by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // 설정 상태
    val isCameraControlsEnabled by appSettingsViewModel.isCameraControlsEnabled.collectAsState()
    val isLiveViewEnabled by appSettingsViewModel.isLiveViewEnabled.collectAsState()
    val isAutoStartEventListener by appSettingsViewModel.isAutoStartEventListenerEnabled.collectAsState()
    val isShowLatestPhotoWhenDisabled by appSettingsViewModel.isShowLatestPhotoWhenDisabled.collectAsState()

    // 라이프사이클 관리
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    // 카메라 제어 탭 진입 시 자동 이벤트 리스너 시작
                    if (isAutoStartEventListener && !viewModel.uiState.value.isEventListenerActive) {
                        Log.d("CameraControl", "자동 이벤트 리스너 시작")
                        viewModel.startEventListener()
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    if (viewModel.uiState.value.isLiveViewActive) {
                        viewModel.stopLiveView()
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    // 필요시 연결 해제: viewModel.disconnectCamera()
                }
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

    var showTimelapseDialog by remember { mutableStateOf(false) }
    var isFullscreen by rememberSaveable { mutableStateOf(false) }

    // UI 상태 변경 로깅
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
        if (isFullscreen && isCameraControlsEnabled) {
            FullscreenCameraLayout(
                uiState = uiState,
                cameraFeed = cameraFeed,
                viewModel = viewModel,
                onExitFullscreen = { isFullscreen = false },
                isLiveViewEnabled = isLiveViewEnabled
            )
        } else {
            PortraitCameraLayout(
                uiState = uiState,
                cameraFeed = cameraFeed,
                viewModel = viewModel,
                scope = scope,
                bottomSheetState = bottomSheetState,
                onShowTimelapseDialog = { showTimelapseDialog = true },
                onEnterFullscreen = { isFullscreen = true },
                isCameraControlsEnabled = isCameraControlsEnabled,
                isLiveViewEnabled = isLiveViewEnabled,
                isShowLatestPhotoWhenDisabled = isShowLatestPhotoWhenDisabled
            )
        }
    }

    // 타임랩스 설정 다이얼로그
    if (showTimelapseDialog) {
        TimelapseSettingsDialog(
            onConfirm = { interval, shots ->
                viewModel.startTimelapse(interval, shots)
                showTimelapseDialog = false
            },
            onDismiss = { showTimelapseDialog = false }
        )
    }

    // 에러 처리
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            when {
                error.contains("Could not find the requested device") ||
                        error.contains("-52") -> {
                    showConnectionHelpDialog = true
                }
                // PTP 타임아웃 자동 처리 제거 - 기본값 사용
            }
        }
    }

    // 연결 도움말 다이얼로그
    if (showConnectionHelpDialog) {
        CameraConnectionHelpDialog(
            onDismiss = { showConnectionHelpDialog = false },
            onRetry = {
                showConnectionHelpDialog = false
                viewModel.refreshUsbDevices()
            }
        )
    }
}

/**
 * 포트레이트 모드 레이아웃 - 분리된 컴포넌트들 사용
 */
@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
private fun PortraitCameraLayout(
    uiState: CameraUiState,
    cameraFeed: List<Camera>,
    viewModel: CameraViewModel,
    scope: kotlinx.coroutines.CoroutineScope,
    bottomSheetState: ModalBottomSheetState,
    onShowTimelapseDialog: () -> Unit,
    onEnterFullscreen: () -> Unit,
    isCameraControlsEnabled: Boolean,
    isLiveViewEnabled: Boolean,
    isShowLatestPhotoWhenDisabled: Boolean
) {
    val context = LocalContext.current

    // 포트레이트 모드 설정
    LaunchedEffect(Unit) {
        (context as? Activity)?.let { activity ->
            Log.d("PortraitCameraLayout", "화면 방향 PORTRAIT로 설정")
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
        // 상단 컨트롤 바 - 분리된 컴포넌트 사용
        TopControlsBar(
            uiState = uiState,
            cameraFeed = cameraFeed,
            onSettingsClick = { scope.launch { bottomSheetState.show() } }
        )

        // 라이브뷰/프리뷰 영역
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black)
                .combinedClickable(
                    onClick = {
                        Log.d("CameraControl", "=== 단일 클릭 감지 ===")
                    },
                    onDoubleClick = {
                        Log.d("CameraControl", "=== 더블 클릭 감지 ===")
                        if (uiState.isLiveViewActive) {
                            Log.d("CameraControl", "전체화면 모드로 진입")
                            onEnterFullscreen()
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            // 카메라 프리뷰 영역 - 분리된 컴포넌트 사용
            if (isCameraControlsEnabled && isLiveViewEnabled) {
                CameraPreviewArea(
                    uiState = uiState,
                    cameraFeed = cameraFeed,
                    viewModel = viewModel
                )
            } else if (!isCameraControlsEnabled && isShowLatestPhotoWhenDisabled) {
                // 최신 사진 표시
                uiState.capturedPhotos.lastOrNull()?.let { latestPhoto ->
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(latestPhoto.filePath)
                            .crossfade(true)
                            .build(),
                        contentDescription = "최신 사진",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } ?: run {
                    // 촬영된 사진이 없을 때 안내 메시지
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Photo,
                            contentDescription = "사진 없음",
                            modifier = Modifier.size(64.dp),
                            tint = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "촬영된 사진이 없습니다",
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "카메라 셔터 버튼을 눌러 사진을 촬영하세요",
                            color = Color.Gray.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            } else {
                // 라이브뷰도 비활성화된 경우 안내 메시지
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "카메라",
                        modifier = Modifier.size(64.dp),
                        tint = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "카메라 컨트롤이 비활성화됨",
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "설정에서 활성화할 수 있습니다",
                        color = Color.Gray.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            // 카메라 설정 오버레이 - 분리된 컴포넌트 사용
            CameraSettingsOverlay(
                settings = uiState.cameraSettings,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            // 전체화면 안내 텍스트
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

        // 하단 컨트롤
        Card(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = Color.Black.copy(alpha = 0.9f),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            Column {
                // 촬영 모드 선택 - 분리된 컴포넌트 사용
                ShootingModeSelector(
                    uiState = uiState,
                    onModeSelected = { mode -> viewModel.setShootingMode(mode) },
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                // 촬영 컨트롤 - 분리된 컴포넌트 사용
                if (isCameraControlsEnabled) {
                    CaptureControls(
                        uiState = uiState,
                        viewModel = viewModel,
                        onShowTimelapseDialog = onShowTimelapseDialog,
                        isVertical = false
                    )
                }

                // 최근 촬영 사진들 - 여기는 간소화된 버전 유지
                if (uiState.capturedPhotos.isNotEmpty()) {
                    Text(
                        "수신된 사진 (${uiState.capturedPhotos.size}개)",
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    RecentCapturesRow(
                        photos = uiState.capturedPhotos.takeLast(10), // 최근 10개
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * 전체화면 모드 레이아웃 - 분리된 컴포넌트들 사용
 */
@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
private fun FullscreenCameraLayout(
    uiState: CameraUiState,
    cameraFeed: List<Camera>,
    viewModel: CameraViewModel,
    onExitFullscreen: () -> Unit,
    isLiveViewEnabled: Boolean
) {
    val context = LocalContext.current
    var showTimelapseDialog by remember { mutableStateOf(false) }

    // 전체화면 모드 설정
    LaunchedEffect(Unit) {
        (context as? Activity)?.let { activity ->
            Log.d("FullscreenCameraLayout", "전체화면 모드 설정")
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
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
                    Log.d("CameraControl", "전체화면 단일 클릭")
                },
                onDoubleClick = {
                    Log.d("CameraControl", "전체화면 더블 클릭 - 종료")
                    onExitFullscreen()
                }
            )
    ) {
        // 메인 라이브뷰 영역 - 분리된 컴포넌트 사용
        if (isLiveViewEnabled) {
            CameraPreviewArea(
                uiState = uiState,
                cameraFeed = cameraFeed,
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 상단 카메라 설정 오버레이 - 분리된 컴포넌트 사용
        CameraSettingsOverlay(
            settings = uiState.cameraSettings,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        )

        // 우측 컨트롤 패널 - 분리된 컴포넌트들로 구성
        FullscreenControlPanel(
            uiState = uiState,
            viewModel = viewModel,
            onShowTimelapseDialog = { showTimelapseDialog = true },
            onExitFullscreen = onExitFullscreen,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(16.dp)
        )

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

        // 전역 로딩 상태 - 분리된 컴포넌트 사용
        if (uiState.isCapturing) {
            LoadingOverlay("촬영 중...")
        }
    }

    // 타임랩스 설정 다이얼로그 - 분리된 컴포넌트 사용
    if (showTimelapseDialog) {
        TimelapseSettingsDialog(
            onConfirm = { interval, shots ->
                viewModel.startTimelapse(interval, shots)
                showTimelapseDialog = false
            },
            onDismiss = { showTimelapseDialog = false }
        )
    }
}

/**
 * 전체화면 컨트롤 패널 - 분리된 컴포넌트들 조합
 */
@Composable
private fun FullscreenControlPanel(
    uiState: CameraUiState,
    viewModel: CameraViewModel,
    onShowTimelapseDialog: () -> Unit,
    onExitFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Black.copy(alpha = 0.7f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 종료 버튼
            IconButton(
                onClick = onExitFullscreen,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Red.copy(alpha = 0.3f), CircleShape)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "전체화면 종료",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // 촬영 모드 선택 (세로) - 분리된 컴포넌트 사용
            ShootingModeSelector(
                uiState = uiState,
                onModeSelected = { mode -> viewModel.setShootingMode(mode) },
            )

            // 메인 촬영 버튼 - 분리된 컴포넌트 사용
            CaptureControls(
                uiState = uiState,
                viewModel = viewModel,
                onShowTimelapseDialog = onShowTimelapseDialog,
                isVertical = true
            )
        }
    }
}

/**
 * 간단한 최근 촬영 사진 로우
 */
@Composable
private fun RecentCapturesRow(
    photos: List<CapturedPhoto>,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(photos) { photo ->
            Card(
                modifier = Modifier.size(100.dp),
                shape = RoundedCornerShape(12.dp),
                elevation = 4.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.DarkGray),
                    contentAlignment = Alignment.Center
                ) {
                    // 실제 이미지가 있으면 표출
                    photo.thumbnailPath?.let { thumbnailPath ->
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(thumbnailPath)
                                .crossfade(true)
                                .build(),
                            contentDescription = "촬영된 사진",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } ?: run {
                        // 썸네일이 없으면 원본 이미지 시도
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(photo.filePath)
                                .crossfade(true)
                                .build(),
                            contentDescription = "촬영된 사진",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    // 다운로드 상태 표시
                    if (photo.isDownloading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "다운로드 중...",
                                color = Color.White,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // 파일 크기 표시 (하단)
                    if (photo.size > 0) {
                        val sizeText = when {
                            photo.size > 1024 * 1024 -> "${photo.size / (1024 * 1024)}MB"
                            photo.size > 1024 -> "${photo.size / 1024}KB"
                            else -> "${photo.size}B"
                        }
                        Text(
                            sizeText,
                            color = Color.White,
                            fontSize = 10.sp,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .background(
                                    Color.Black.copy(alpha = 0.7f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(4.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 카메라 설정 시트 - 분리된 컴포넌트 사용
 */
@Composable
private fun CameraSettingsSheet(
    settings: CameraSettings?,
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
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.close)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Settings would be dynamically loaded based on camera capabilities
        settings?.let {
            Text("ISO: ${it.iso}")
            Text("셔터 속도: ${it.shutterSpeed}")
            Text("조리개: ${it.aperture}")
            Text("화이트밸런스: ${it.whiteBalance}")
        } ?: run {
            Text("카메라 설정을 로드할 수 없습니다", color = Color.Gray)
        }
    }
}

// 프리뷰는 간소화
@Preview(name = "Camera Control Screen", showBackground = true)
@Composable
private fun CameraControlScreenPreview() {
    CamConTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "카메라 컨트롤 스크린",
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview(name = "Camera Settings Sheet", showBackground = true)
@Composable
private fun CameraSettingsSheetPreview() {
    CamConTheme {
        CameraSettingsSheet(
            settings = CameraSettings(
                iso = "400",
                shutterSpeed = "1/125",
                aperture = "f/2.8",
                whiteBalance = "자동",
                focusMode = "자동",
                exposureCompensation = "0"
            ),
            onSettingChange = { _, _ -> },
            onClose = { }
        )
    }
}

@Preview(name = "Recent Captures Row", showBackground = true)
@Composable
private fun RecentCapturesRowPreview() {
    CamConTheme {
        RecentCapturesRow(
            photos = listOf(
                CapturedPhoto(
                    id = "1",
                    filePath = "/path/to/test1.jpg",
                    thumbnailPath = "/path/to/thumb1.jpg",
                    captureTime = System.currentTimeMillis(),
                    cameraModel = "Canon EOS R5",
                    settings = null,
                    size = 1024 * 1024,
                    width = 1920,
                    height = 1080
                ),
                CapturedPhoto(
                    id = "2",
                    filePath = "/path/to/test2.jpg",
                    thumbnailPath = "/path/to/thumb2.jpg",
                    captureTime = System.currentTimeMillis(),
                    cameraModel = "Canon EOS R5",
                    settings = null,
                    size = 1024 * 1024,
                    width = 1920,
                    height = 1080
                ),
                CapturedPhoto(
                    id = "3",
                    filePath = "/path/to/test3.jpg",
                    thumbnailPath = "/path/to/thumb3.jpg",
                    captureTime = System.currentTimeMillis(),
                    cameraModel = "Canon EOS R5",
                    settings = null,
                    size = 1024 * 1024,
                    width = 1920,
                    height = 1080
                )
            ),
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(name = "Fullscreen Control Panel", showBackground = true)
@Composable
private fun FullscreenControlPanelPreview() {
    CamConTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "전체화면 컨트롤 패널",
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
    }
}
