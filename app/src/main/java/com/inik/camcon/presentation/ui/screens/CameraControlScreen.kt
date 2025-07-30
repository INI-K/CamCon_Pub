package com.inik.camcon.presentation.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.ColorSpace
import android.media.ExifInterface
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
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
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
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
import coil.size.Scale
import com.inik.camcon.R
import com.inik.camcon.domain.model.Camera
import com.inik.camcon.domain.model.CameraSettings
import com.inik.camcon.domain.model.CapturedPhoto
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.ui.screens.components.CameraPreviewArea
import com.inik.camcon.presentation.ui.screens.components.CaptureControls
import com.inik.camcon.presentation.ui.screens.components.LoadingOverlay
import com.inik.camcon.presentation.ui.screens.components.ShootingModeSelector
import com.inik.camcon.presentation.ui.screens.components.TopControlsBar
import com.inik.camcon.presentation.ui.screens.components.UsbInitializationOverlay
import com.inik.camcon.presentation.ui.screens.dialogs.CameraConnectionHelpDialog
import com.inik.camcon.presentation.ui.screens.dialogs.TimelapseSettingsDialog
import com.inik.camcon.presentation.viewmodel.AppSettingsViewModel
import com.inik.camcon.presentation.viewmodel.CameraUiState
import com.inik.camcon.presentation.viewmodel.CameraViewModel
import kotlinx.coroutines.launch
import java.io.File

/**
 * 메인 카메라 컨트롤 스크린 - 컴포넌트들로 분리됨
 * 분리된 컴포넌트들을 조합하여 화면을 구성
 */
@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
fun CameraControlScreen(
    viewModel: CameraViewModel,
    appSettingsViewModel: AppSettingsViewModel = hiltViewModel(),
    onFullscreenChange: (Boolean) -> Unit = {}
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // UI 상태들을 선별적으로 수집
    val uiState by viewModel.uiState.collectAsState()
    val cameraFeed by viewModel.cameraFeed.collectAsState()

    // 설정 상태들을 collectAsState로 개별 수집하되 리컴포지션 최적화
    val isCameraControlsEnabled by appSettingsViewModel.isCameraControlsEnabled.collectAsState()
    val isLiveViewEnabled by appSettingsViewModel.isLiveViewEnabled.collectAsState()
    val isAutoStartEventListener by appSettingsViewModel.isAutoStartEventListenerEnabled.collectAsState()
    val isShowPreviewInCapture by appSettingsViewModel.isShowLatestPhotoWhenDisabled.collectAsState()

    // 다이얼로그 상태들
    var showFolderSelectionDialog by remember { mutableStateOf(false) }
    var showSaveFormatSelectionDialog by remember { mutableStateOf(false) }
    var showConnectionHelpDialog by remember { mutableStateOf(false) }

    // 앱 재시작 다이얼로그 - uiState의 showRestartDialog를 observe
    val showAppRestartDialog = uiState.showRestartDialog

    // 설정들을 묶은 객체를 remember로 캐싱하여 리컴포지션 최적화
    val appSettings = remember(
        isCameraControlsEnabled,
        isLiveViewEnabled,
        isAutoStartEventListener,
        isShowPreviewInCapture
    ) {
        AppSettings(
            isCameraControlsEnabled = isCameraControlsEnabled,
            isLiveViewEnabled = isLiveViewEnabled,
            isAutoStartEventListener = isAutoStartEventListener,
            isShowPreviewInCapture = isShowPreviewInCapture
        )
    }

    // 라이프사이클 관리 (통합된 버전) - 의존성 최적화
    DisposableEffect(lifecycleOwner, isAutoStartEventListener) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.setTabSwitchFlag(true)
                    if (viewModel.uiState.value.isLiveViewActive) {
                        viewModel.stopLiveView()
                    }
                    // 이벤트 리스너는 중지하지 않음 - 탭 전환 중에도 유지
                }
                Lifecycle.Event.ON_RESUME -> {
                    val isReturningFromOtherTab = viewModel.getAndClearTabSwitchFlag()
                    // 이벤트 리스너 자동 시작 로직을 제거 - 네이티브 초기화 완료 후에 처리됨
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 네이티브 카메라 연결 상태와 초기화 상태를 모니터링하여 이벤트 리스너 자동 시작
    LaunchedEffect(
        uiState.isNativeCameraConnected,
        uiState.isInitializing,
        isAutoStartEventListener
    ) {
        // 네이티브 카메라가 연결되고, 초기화가 완료되었고, 자동 시작이 활성화되어 있고, 이벤트 리스너가 비활성화되어 있을 때만 시작
        if (uiState.isNativeCameraConnected &&
            !uiState.isInitializing &&
            isAutoStartEventListener &&
            !uiState.isEventListenerActive
        ) {
            Log.d("CameraControl", "네이티브 카메라 초기화 완료 - 이벤트 리스너 자동 시작")
            viewModel.startEventListener()
        }
    }

    // 상태 변화들을 remember로 캐싱하여 불필요한 리컴포지션 방지
    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val bottomSheetState = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden)
    var showTimelapseDialog by remember { mutableStateOf(false) }

    // UI 상태 변경 로깅을 하나로 통합하고 필요한 것만 로깅
    LaunchedEffect(uiState.isConnected, uiState.isLiveViewActive, uiState.capturedPhotos.size) {
        // 로깅 최소화 - 필요시에만 활성화
        // Log.d("CameraControl", "상태 변경 - 연결: ${uiState.isConnected}, 라이브뷰: ${uiState.isLiveViewActive}, 사진: ${uiState.capturedPhotos.size}")
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
            if (isFullscreen && appSettings.isCameraControlsEnabled) {
                FullscreenCameraLayout(
                    uiState = uiState,
                    cameraFeed = cameraFeed,
                    viewModel = viewModel,
                    onExitFullscreen = {
                        isFullscreen = false
                        onFullscreenChange(false)
                    },
                    isLiveViewEnabled = appSettings.isLiveViewEnabled
                )
            } else {
                PortraitCameraLayout(
                    uiState = uiState,
                    cameraFeed = cameraFeed,
                    viewModel = viewModel,
                    scope = scope,
                    bottomSheetState = bottomSheetState,
                    onShowTimelapseDialog = { showTimelapseDialog = true },
                    onEnterFullscreen = {
                        isFullscreen = true
                        onFullscreenChange(true)
                    },
                    appSettings = appSettings
                )
            }
        }

        if (uiState.isUsbInitializing) {
            UsbInitializationOverlay(
                message = uiState.usbInitializationMessage ?: "USB 카메라 초기화 중..."
            )
        }
    }

    if (showTimelapseDialog) {
        TimelapseSettingsDialog(
            onConfirm = { interval, shots ->
                viewModel.startTimelapse(interval, shots)
                showTimelapseDialog = false
            },
            onDismiss = { showTimelapseDialog = false }
        )
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            when {
                error.contains("Could not find the requested device") -> {
                    showConnectionHelpDialog = true
                }
            }
        }
    }

    if (showConnectionHelpDialog) {
        CameraConnectionHelpDialog(
            onDismiss = { showConnectionHelpDialog = false },
            onRetry = {
                showConnectionHelpDialog = false
                viewModel.refreshUsbDevices()
            }
        )
    }

    if (showAppRestartDialog) {
        val context = LocalContext.current
        AppRestartDialog(
            onDismiss = { viewModel.dismissRestartDialog() },
            onRestart = {
                viewModel.dismissRestartDialog()
                // 액티비티 재시작 로직
                (context as? Activity)?.recreate()
            }
        )
    }
}

/**
 * 앱 재시작을 요구하는 AlertDialog
 */
@Composable
private fun AppRestartDialog(
    onDismiss: () -> Unit,
    onRestart: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("앱 재시작 필요")
        },
        text = {
            Text("USB 장치 연결이 제대로 해제되지 않았거나 시스템 오류(-52)가 발생했습니다.\n\n앱을 재시작해야 정상적으로 사용할 수 있습니다.\n\n지금 앱을 재시작하시겠습니까?")
        },
        confirmButton = {
            Button(onClick = onRestart) {
                Text("앱 재시작")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

@Stable
private data class AppSettings(
    val isCameraControlsEnabled: Boolean,
    val isLiveViewEnabled: Boolean,
    val isAutoStartEventListener: Boolean,
    val isShowPreviewInCapture: Boolean
)

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
    appSettings: AppSettings
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        (context as? Activity)?.let { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                activity.window.setDecorFitsSystemWindows(true)
                activity.window.insetsController?.show(WindowInsets.Type.systemBars())
            } else {
                activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
    }

    val recentPhotos = remember(uiState.capturedPhotos.size) {
        if (uiState.capturedPhotos.isNotEmpty()) {
            uiState.capturedPhotos.takeLast(10).reversed()
        } else {
            emptyList()
        }
    }

    val canEnterFullscreen = remember(uiState.isLiveViewActive, uiState.capturedPhotos.size) {
        uiState.isLiveViewActive || uiState.capturedPhotos.isNotEmpty()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        TopControlsBar(
            uiState = uiState,
            cameraFeed = cameraFeed,
            onSettingsClick = { scope.launch { bottomSheetState.show() } }
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black)
                .combinedClickable(
                    onClick = { /* 단일 클릭 처리 */ },
                    onDoubleClick = {
                        if (canEnterFullscreen) {
                            onEnterFullscreen()
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (appSettings.isCameraControlsEnabled && appSettings.isLiveViewEnabled) {
                CameraPreviewArea(
                    uiState = uiState,
                    cameraFeed = cameraFeed,
                    viewModel = viewModel
                )
            } else {
                AnimatedPhotoSwitcher(
                    capturedPhotos = uiState.capturedPhotos
                )
            }

            if (canEnterFullscreen) {
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

        Card(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = Color.Black.copy(alpha = 0.9f),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            Column {
                if (appSettings.isCameraControlsEnabled && appSettings.isLiveViewEnabled) {
                    ShootingModeSelector(
                        uiState = uiState,
                        onModeSelected = { mode -> viewModel.setShootingMode(mode) },
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }

                if (appSettings.isCameraControlsEnabled && appSettings.isLiveViewEnabled) {
                    CaptureControls(
                        uiState = uiState,
                        viewModel = viewModel,
                        onShowTimelapseDialog = onShowTimelapseDialog,
                        isVertical = false
                    )
                }

                if (recentPhotos.isNotEmpty()) {
                    Text(
                        "수신된 사진 (${uiState.capturedPhotos.size}개)",
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    RecentCapturesRow(
                        photos = recentPhotos,
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

    // 전체화면 모드 설정 - 한 번만 실행
    LaunchedEffect(Unit) {
        (context as? Activity)?.let { activity ->
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

    // EXIF 설정을 remember로 캐싱
    val exifSettings = remember(uiState.capturedPhotos.lastOrNull()?.filePath) {
        uiState.capturedPhotos.lastOrNull()?.let { photo ->
            readExifMetadata(photo.filePath)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .combinedClickable(
                onClick = { /* 전체화면 단일 클릭 */ },
                onDoubleClick = onExitFullscreen
            )
    ) {
        // 메인 라이브뷰 또는 사진 뷰 영역
        if (isLiveViewEnabled && uiState.isLiveViewActive) {
            // 라이브뷰 모드
            CameraPreviewArea(
                uiState = uiState,
                cameraFeed = cameraFeed,
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AnimatedPhotoSwitcher(
                capturedPhotos = uiState.capturedPhotos,
                modifier = Modifier.fillMaxSize(),
                emptyTextColor = Color.White
            )
        }

        // 우측 컨트롤 패널 - 라이브뷰가 활성화되어 있을 때만 표시
        if (isLiveViewEnabled && uiState.isLiveViewActive) {
            FullscreenControlPanel(
                uiState = uiState,
                viewModel = viewModel,
                onShowTimelapseDialog = { showTimelapseDialog = true },
                onExitFullscreen = onExitFullscreen,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(16.dp)
            )
        } else if (uiState.capturedPhotos.isNotEmpty()) {
            // 사진 뷰 모드에서는 종료 버튼만 표시
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
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
            }
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

        // 전역 로딩 상태 - 분리된 컴포넌트 사용
        if (uiState.isCapturing) {
            LoadingOverlay("촬영 중...")
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
 * 간단한 최근 촬영 사진 로우 - 부드러운 이미지 로딩 최적화
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
        items(
            items = photos,
            key = { photo -> photo.id } // key 추가로 리컴포지션 최적화
        ) { photo ->
            RecentCaptureItem(photo = photo)
        }
    }
}

/**
 * 개별 사진 아이템 - 리컴포지션 최적화를 위해 분리
 */
@Composable
private fun RecentCaptureItem(
    photo: CapturedPhoto
) {
    // 파일 크기 텍스트를 remember로 캐싱
    val sizeText = remember(photo.size) {
        when {
            photo.size > 1024 * 1024 -> "${photo.size / (1024 * 1024)}MB"
            photo.size > 1024 -> "${photo.size / 1024}KB"
            else -> "${photo.size}B"
        }
    }

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
                        .crossfade(180)
                        .memoryCacheKey(photo.id + "_thumb")
                        .scale(Scale.FIT)
                        .apply {
                            // sRGB 색공간 설정
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                colorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                            }
                        }
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
                        .crossfade(180)
                        .memoryCacheKey(photo.id + "_full")
                        .scale(Scale.FIT)
                        .apply {
                            // sRGB 색공간 설정
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                colorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                            }
                        }
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

/**
 * 사진 변경시 fadeIn/fadeOut 애니메이션으로 부드럽게 전환 + Coil 옵션 최적화
 */
@Composable
private fun AnimatedPhotoSwitcher(
    capturedPhotos: List<CapturedPhoto>,
    modifier: Modifier = Modifier,
    emptyTextColor: Color = Color.Gray
) {
    // 최신 사진을 remember로 캐싱하여 리컴포지션 최적화
    val latestPhoto = remember(capturedPhotos.size) {
        capturedPhotos.lastOrNull()
    }

    Box(
        modifier = modifier
    ) {
        // 사진이 있을 때 애니메이션 표시
        AnimatedVisibility(
            visible = latestPhoto != null,
            enter = fadeIn(animationSpec = tween(350)),
            exit = fadeOut(animationSpec = tween(350))
        ) {
            latestPhoto?.let { photo ->
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(photo.filePath)
                        .crossfade(200)
                        .memoryCacheKey(photo.id + "_main")
                        .scale(Scale.FIT)
                        .apply {
                            // sRGB 색공간 설정
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                colorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                            }
                        }
                        .build(),
                    contentDescription = "사진",
                    modifier = Modifier
                        .matchParentSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
        // 사진이 없을 때 애니메이션 표시
        AnimatedVisibility(
            visible = latestPhoto == null,
            enter = fadeIn(animationSpec = tween(350)),
            exit = fadeOut(animationSpec = tween(350))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Photo,
                    contentDescription = "사진 없음",
                    modifier = Modifier.size(64.dp),
                    tint = emptyTextColor
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "수신된 사진이 없습니다",
                    color = emptyTextColor,
                    textAlign = TextAlign.Center
                )
                Text(
                    "카메라에서 사진을 촬영하면 여기에 표시됩니다",
                    color = emptyTextColor.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
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

/**
 * 사진 파일에서 EXIF 메타데이터를 읽어서 CameraSettings 객체로 변환
 */
private fun readExifMetadata(filePath: String): CameraSettings? {
    return try {
        val file = File(filePath)
        if (!file.exists()) return null

        val exif = ExifInterface(filePath)

        // ISO 값 읽기
        val iso = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS) ?: "AUTO"

        // 조리개 값 읽기
        val aperture = exif.getAttribute(ExifInterface.TAG_F_NUMBER)?.let { fNumber ->
            try {
                val parts = fNumber.split("/")
                if (parts.size == 2) {
                    val numerator = parts[0].toDouble()
                    val denominator = parts[1].toDouble()
                    String.format("%.1f", numerator / denominator)
                } else {
                    fNumber
                }
            } catch (e: Exception) {
                fNumber
            }
        } ?: "AUTO"

        // 셔터 속도 읽기
        val shutterSpeed = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.let { exposureTime ->
            try {
                val speed = exposureTime.toDouble()

                if (speed >= 1.0) {
                    "${speed.toInt()}s"
                } else {
                    val denominator = (1.0 / speed).toInt()
                    "1/$denominator"
                }
            } catch (e: Exception) {
                Log.e("CameraControl", "셔터 속도 파싱 실패: $exposureTime")
                exposureTime
            }
        } ?: "AUTO"

        // 화이트 밸런스 읽기
        val whiteBalance = when (exif.getAttribute(ExifInterface.TAG_WHITE_BALANCE)) {
            "0" -> "자동"
            "1" -> "수동"
            else -> "자동"
        }

        // 초점 모드 읽기 (기본값)
        val focusMode = "자동"

        // 노출 보정 읽기
        val exposureCompensation = exif.getAttribute(ExifInterface.TAG_EXPOSURE_BIAS_VALUE) ?: "0"

        CameraSettings(
            iso = iso,
            shutterSpeed = shutterSpeed,
            aperture = aperture,
            whiteBalance = whiteBalance,
            focusMode = focusMode,
            exposureCompensation = exposureCompensation
        )
    } catch (e: Exception) {
        Log.e("CameraControl", "EXIF 메타데이터 읽기 실패: ${e.message}")
        null
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
