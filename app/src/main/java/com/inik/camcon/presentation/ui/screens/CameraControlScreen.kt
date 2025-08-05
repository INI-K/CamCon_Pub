package com.inik.camcon.presentation.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.ColorSpace
import android.media.ExifInterface
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.filled.RotateRight
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
import androidx.compose.ui.draw.rotate
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
import com.inik.camcon.domain.model.CameraPhoto
import com.inik.camcon.domain.model.CameraSettings
import com.inik.camcon.domain.model.CapturedPhoto
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.ui.screens.components.CameraPreviewArea
import com.inik.camcon.presentation.ui.screens.components.CaptureControls
import com.inik.camcon.presentation.ui.screens.components.FullScreenPhotoViewer
import com.inik.camcon.presentation.ui.screens.components.LoadingOverlay
import com.inik.camcon.presentation.ui.screens.components.ShootingModeSelector
import com.inik.camcon.presentation.ui.screens.components.TopControlsBar
import com.inik.camcon.presentation.ui.screens.components.UsbInitializationOverlay
import com.inik.camcon.presentation.ui.screens.dialogs.CameraConnectionHelpDialog
import com.inik.camcon.presentation.ui.screens.dialogs.TimelapseSettingsDialog
import com.inik.camcon.presentation.viewmodel.AppSettingsViewModel
import com.inik.camcon.presentation.viewmodel.CameraUiState
import com.inik.camcon.presentation.viewmodel.CameraViewModel
import com.inik.camcon.presentation.viewmodel.RawFileRestriction
import kotlinx.coroutines.launch
import org.json.JSONObject
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
    val context = LocalContext.current

    // Activity를 ViewModel에 설정
    LaunchedEffect(context) {
        (context as? Activity)?.let { activity ->
            viewModel.setActivity(activity)
        }
    }

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

    // FullScreenPhotoViewer 상태들
    var showFullScreenViewer by remember { mutableStateOf(false) }
    var selectedPhoto by remember { mutableStateOf<CapturedPhoto?>(null) }

    // 앱 재시작 다이얼로그 - uiState의 showRestartDialog를 observe
    val showAppRestartDialog = uiState.showRestartDialog

    // FullScreenPhotoViewer가 열린 상태에서 뒤로가기 처리
    BackHandler(enabled = showFullScreenViewer) {
        showFullScreenViewer = false
        selectedPhoto = null
    }

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
            if (isFullscreen && (appSettings.isCameraControlsEnabled || uiState.capturedPhotos.isNotEmpty())) {
                Log.d(
                    "CameraControl",
                    "🌟 전체화면 모드 렌더링 - isFullscreen=$isFullscreen, isCameraControlsEnabled=${appSettings.isCameraControlsEnabled}, capturedPhotos=${uiState.capturedPhotos.size}"
                )
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
                Log.d(
                    "CameraControl",
                    "📱 포트레이트 모드 렌더링 - isFullscreen=$isFullscreen, isCameraControlsEnabled=${appSettings.isCameraControlsEnabled}, capturedPhotos=${uiState.capturedPhotos.size}"
                )
                PortraitCameraLayout(
                    uiState = uiState,
                    cameraFeed = cameraFeed,
                    viewModel = viewModel,
                    scope = scope,
                    bottomSheetState = bottomSheetState,
                    onShowTimelapseDialog = { showTimelapseDialog = true },
                    onEnterFullscreen = {
                        Log.d("CameraControl", "🌟 onEnterFullscreen 호출됨 - 전체화면 모드로 전환")
                        isFullscreen = true
                        onFullscreenChange(true)
                        Log.d("CameraControl", "🌟 전체화면 상태 설정 완료: isFullscreen=$isFullscreen")
                    },
                    appSettings = appSettings,
                    onPhotoClick = { photo ->
                        selectedPhoto = photo
                        showFullScreenViewer = true
                    }
                )
            }
        }

        if (uiState.isUsbInitializing) {
            UsbInitializationOverlay(
                message = uiState.usbInitializationMessage ?: "USB 카메라 초기화 중..."
            )
        }

        // RAW 파일 제한 알림 표시
        uiState.rawFileRestriction?.let { restriction ->
            RawFileRestrictionNotification(
                restriction = restriction,
                onDismiss = { viewModel.clearRawFileRestriction() }
            )
        }

        // FullScreenPhotoViewer 표시
        if (showFullScreenViewer && selectedPhoto != null) {
            FullScreenPhotoViewer(
                photo = selectedPhoto!!.toCameraPhoto(),
                onDismiss = {
                    showFullScreenViewer = false
                    selectedPhoto = null
                },
                onPhotoChanged = { /* 단일 사진이므로 변경 없음 */ },
                thumbnailData = selectedPhoto!!.getThumbnailData(),
                fullImageData = selectedPhoto!!.getImageData(),
                isDownloadingFullImage = false,
                onDownload = { /* 이미 다운로드됨, 아무 동작 안함 */ },
                viewModel = null, // PhotoPreviewViewModel 없이 사용
                hideDownloadButton = true // 다운로드 버튼 숨김
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
    appSettings: AppSettings,
    onPhotoClick: (CapturedPhoto) -> Unit = {}
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

    LaunchedEffect(appSettings) {
        Log.d(
            "CameraControl",
            "AppSettings - isCameraControlsEnabled: ${appSettings.isCameraControlsEnabled}"
        )
        Log.d("CameraControl", "AppSettings - isLiveViewEnabled: ${appSettings.isLiveViewEnabled}")
        Log.d(
            "CameraControl",
            "AppSettings - isAutoStartEventListener: ${appSettings.isAutoStartEventListener}"
        )
        Log.d(
            "CameraControl",
            "AppSettings - isShowPreviewInCapture: ${appSettings.isShowPreviewInCapture}"
        )
        Log.d(
            "CameraControl",
            "조건 확인: ${appSettings.isCameraControlsEnabled && appSettings.isLiveViewEnabled}"
        )
    }

    val recentPhotos = remember(uiState.capturedPhotos.size) {
        if (uiState.capturedPhotos.isNotEmpty()) {
            uiState.capturedPhotos.takeLast(10).reversed()
        } else {
            emptyList()
        }
    }

    val canEnterFullscreen = remember(uiState.isLiveViewActive, uiState.capturedPhotos.size) {
        val result = uiState.isLiveViewActive || uiState.capturedPhotos.isNotEmpty()
        Log.d(
            "CameraControl",
            "🔍 canEnterFullscreen 계산: isLiveViewActive=${uiState.isLiveViewActive}, capturedPhotos.size=${uiState.capturedPhotos.size}, result=$result"
        )
        result
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
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (appSettings.isCameraControlsEnabled && appSettings.isLiveViewEnabled) {
                CameraPreviewArea(
                    uiState = uiState,
                    cameraFeed = cameraFeed,
                    viewModel = viewModel,
                    onDoubleClick = {
                        if (canEnterFullscreen) {
                            onEnterFullscreen()
                        }
                    }
                )
            } else {
                Log.d("CameraControl", "사진 표시 모드 - 수신된 사진 개수: ${uiState.capturedPhotos.size}")
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .combinedClickable(
                            onClick = {
                                Log.d("CameraControl", "수신 사진 영역 단일 클릭")
                            },
                            onDoubleClick = {
                                Log.d(
                                    "CameraControl",
                                    "수신 사진 영역 더블클릭 감지! canEnterFullscreen=$canEnterFullscreen"
                                )
                                if (canEnterFullscreen) {
                                    Log.d("CameraControl", "전체화면 모드로 전환 시도")
                                    onEnterFullscreen()
                                } else {
                                    Log.w("CameraControl", "전체화면 진입 불가 - 조건 미충족")
                                }
                            }
                        )
                ) {
                    AnimatedPhotoSwitcher(
                        capturedPhotos = uiState.capturedPhotos,
                        modifier = Modifier.fillMaxSize(),
                        emptyTextColor = Color.White,
                        isRotated = false,
                        onDoubleClick = {
                            if (canEnterFullscreen) {
                                onEnterFullscreen()
                            }
                        }
                    )
                }
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
                        onPhotoClick = onPhotoClick,
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
    var isRotated by remember { mutableStateOf(false) }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 메인 라이브뷰 또는 사진 뷰 영역
        if (isLiveViewEnabled && uiState.isLiveViewActive) {
            // 라이브뷰 모드
            CameraPreviewArea(
                uiState = uiState,
                cameraFeed = cameraFeed,
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize(),
                onDoubleClick = onExitFullscreen
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .combinedClickable(
                        onClick = { /* 단일 클릭 처리 */ },
                        onDoubleClick = onExitFullscreen
                    )
            ) {
                AnimatedPhotoSwitcher(
                    capturedPhotos = uiState.capturedPhotos,
                    modifier = Modifier.fillMaxSize(),
                    emptyTextColor = Color.White,
                    isRotated = isRotated,
                    onDoubleClick = onExitFullscreen
                )
            }
        }

        // 우측 컨트롤 패널 - 라이브뷰가 활성화되어 있을 때만 표시
        if (isLiveViewEnabled && uiState.isLiveViewActive) {
            FullscreenControlPanel(
                uiState = uiState,
                viewModel = viewModel,
                onShowTimelapseDialog = { showTimelapseDialog = true },
                onExitFullscreen = onExitFullscreen,
                onRotate = { isRotated = !isRotated },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(16.dp)
            )
        } else if (uiState.capturedPhotos.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = CircleShape
                ) {
                    IconButton(
                        onClick = { isRotated = !isRotated },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.DarkGray.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.RotateRight,
                            contentDescription = "180도 회전",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = CircleShape
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
    onRotate: (() -> Unit)? = null, // 180도 회전 콜백 추가 (기본값 null)
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

            // 180도 회전 버튼
            IconButton(
                onClick = { onRotate?.invoke() },
                enabled = onRotate != null,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.DarkGray.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(
                    Icons.Default.RotateRight,
                    contentDescription = "180도 회전",
                    tint = if (onRotate != null) Color.White else Color.LightGray,
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
    onPhotoClick: (CapturedPhoto) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 새로운 사진이 추가될 때마다 첫 번째 아이템으로 스크롤
    LaunchedEffect(photos.size) {
        if (photos.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(0)
            }
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = photos,
            key = { photo -> photo.id } // key 추가로 리컴포지션 최적화
        ) { photo ->
            RecentCaptureItem(
                photo = photo,
                onClick = { onPhotoClick(photo) }
            )
        }
    }
}

/**
 * 개별 사진 아이템 - 리컴포지션 최적화를 위해 분리
 */
@Composable
private fun RecentCaptureItem(
    photo: CapturedPhoto,
    onClick: () -> Unit = {}
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
        modifier = Modifier
            .size(100.dp)
            .clickable { onClick() },
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
                        .allowHardware(false) // EXIF 처리를 위해 하드웨어 가속 비활성화
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
                        .allowHardware(false) // EXIF 처리를 위해 하드웨어 가속 비활성화
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
    emptyTextColor: Color = Color.Gray,
    isRotated: Boolean = false,
    onDoubleClick: (() -> Unit)? = null
) {
    val latestPhoto = remember(capturedPhotos.size) {
        capturedPhotos.lastOrNull()
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
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
                        .allowHardware(false) // EXIF 처리를 위해 하드웨어 가속 비활성화
                        .listener(
                            onStart = { request ->
                                Log.d("CameraPhoto", "수신된 사진 로딩 시작: ${photo.filePath}")
                            },
                            onSuccess = { request, result ->
                                Log.d("CameraPhoto", "수신된 사진 로딩 성공: ${photo.filePath}")

                                // EXIF 정보 확인
                                try {
                                    val exif =
                                        androidx.exifinterface.media.ExifInterface(photo.filePath)
                                    val orientation = exif.getAttributeInt(
                                        androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                                        androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                                    )

                                    val rotationText = when (orientation) {
                                        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> "90도 (270도로 수정 적용)"
                                        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> "180도 (회전하지 않음)"
                                        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> "270도 (90도로 수정 적용)"
                                        else -> "없음"
                                    }

                                    Log.d("EXIF_RECEIVED_PHOTO", "=== 수신 사진 EXIF 정보 ===")
                                    Log.d("EXIF_RECEIVED_PHOTO", "파일: ${photo.filePath}")
                                    Log.d("EXIF_RECEIVED_PHOTO", "EXIF Orientation: $orientation")
                                    Log.d("EXIF_RECEIVED_PHOTO", "회전 정보: $rotationText")
                                    Log.d(
                                        "EXIF_RECEIVED_PHOTO",
                                        "Coil이 자동 회전 처리: ${orientation != androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL}"
                                    )
                                } catch (e: Exception) {
                                    Log.e("EXIF_RECEIVED_PHOTO", "EXIF 정보 확인 실패: ${e.message}", e)
                                }
                            },
                            onError = { request, error ->
                                Log.e(
                                    "CameraPhoto",
                                    "수신된 사진 로딩 실패: ${photo.filePath}",
                                    error.throwable
                                )
                            }
                        )
                        .apply {
                            // sRGB 색공간 설정
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                colorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                            }
                        }
                        .build(),
                    contentDescription = "사진",
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (isRotated) Modifier.rotate(180f) else Modifier)
                        .combinedClickable(
                            onClick = {
                                Log.d("CameraControl", "수신 사진 이미지 단일 클릭")
                            },
                            onDoubleClick = {
                                Log.d("CameraControl", "🔥 수신 사진 이미지에서 더블클릭 감지!")
                                Log.d(
                                    "CameraControl",
                                    "🔍 onDoubleClick 콜백 호출 시도 - 콜백 존재 여부: ${onDoubleClick != null}"
                                )
                                onDoubleClick?.invoke()
                                Log.d("CameraControl", "✅ onDoubleClick 콜백 호출 완료")
                            }
                        ),
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

// CapturedPhoto를 CameraPhoto로 변환하는 확장 함수
private fun CapturedPhoto.toCameraPhoto(): CameraPhoto {
    return CameraPhoto(
        path = this.filePath,
        name = this.filePath.substringAfterLast("/"),
        size = this.size,
        date = this.captureTime, // 캡처 시간 전달
        width = this.width,
        height = this.height,
        thumbnailPath = this.thumbnailPath
    )
}

// CapturedPhoto에서 썸네일 데이터를 가져오는 확장 함수
private fun CapturedPhoto.getThumbnailData(): ByteArray? {
    return try {
        this.thumbnailPath?.let { File(it).readBytes() }
    } catch (e: Exception) {
        null
    }
}

// CapturedPhoto에서 이미지 데이터를 가져오는 확장 함수  
private fun CapturedPhoto.getImageData(): ByteArray? {
    return try {
        File(this.filePath).readBytes()
    } catch (e: Exception) {
        null
    }
}

// CapturedPhoto에서 EXIF 정보를 JSON 형태로 읽어오는 확장 함수
private fun CapturedPhoto.getExifData(): String? {
    return try {
        val file = File(this.filePath)
        if (!file.exists()) return null

        val exif = ExifInterface(this.filePath)
        val exifMap = mutableMapOf<String, Any>()

        // 기본 이미지 정보
        exifMap["width"] = this.width
        exifMap["height"] = this.height
        exifMap["file_size"] = this.size
        exifMap["capture_time"] = this.captureTime

        // 카메라 정보
        exif.getAttribute(ExifInterface.TAG_MAKE)?.let { exifMap["make"] = it }
        exif.getAttribute(ExifInterface.TAG_MODEL)?.let { exifMap["model"] = it }

        // 촬영 설정 (CapturedPhoto에 있는 settings 활용)
        this.settings?.let { settings ->
            exifMap["iso"] = settings.iso
            exifMap["aperture"] = settings.aperture
            exifMap["shutter_speed"] = settings.shutterSpeed
            exifMap["white_balance"] = settings.whiteBalance
            exifMap["focus_mode"] = settings.focusMode
            exifMap["exposure_compensation"] = settings.exposureCompensation
        }

        // EXIF에서 추가 정보 읽기
        exif.getAttribute(ExifInterface.TAG_F_NUMBER)?.let { exifMap["f_number"] = it }
        exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.let { exifMap["exposure_time"] = it }
        exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)?.let { exifMap["focal_length"] = it }
        exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)?.let { exif_iso ->
            if (!exifMap.containsKey("iso") || exifMap["iso"] == "AUTO") {
                exifMap["iso"] = exif_iso
            }
        }

        // 기타 정보
        val orientation =
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        exifMap["orientation"] = orientation

        exif.getAttribute(ExifInterface.TAG_WHITE_BALANCE)?.let { wb ->
            val whiteBalanceText = when (wb) {
                "0" -> "자동"
                "1" -> "수동"
                else -> "자동"
            }
            exifMap["white_balance_exif"] = whiteBalanceText
        }

        exif.getAttribute(ExifInterface.TAG_FLASH)?.let { exifMap["flash"] = it }
        exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?.let { exifMap["date_time_original"] = it }

        // GPS 정보
        val latLong = floatArrayOf(0f, 0f)
        if (exif.getLatLong(latLong)) {
            exifMap["gps_latitude"] = latLong[0]
            exifMap["gps_longitude"] = latLong[1]
        }

        // JSON 문자열로 변환
        val jsonObject = JSONObject()
        exifMap.forEach { (key, value) ->
            jsonObject.put(key, value)
        }

        jsonObject.toString()
    } catch (e: Exception) {
        Log.e("CameraControl", "EXIF 정보 읽기 실패: ${e.message}", e)
        null
    }
}

// ... existing code ...

/**
 * RAW 파일 제한 알림 컴포넌트 (슬라이드 인/아웃 + 페이드)
 */
@Composable
private fun RawFileRestrictionNotification(
    restriction: RawFileRestriction,
    onDismiss: () -> Unit
) {
    // 5초 후 자동으로 사라지게 하기
    LaunchedEffect(restriction.timestamp) {
        kotlinx.coroutines.delay(5000L)
        onDismiss()
    }

    // 화면 상단에 표시
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(
            initialOffsetY = { -80 }
        ) + fadeIn(animationSpec = tween(260)),
        exit = slideOutVertically(
            targetOffsetY = { -80 }
        ) + fadeOut(animationSpec = tween(260))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 36.dp, start = 16.dp, end = 16.dp)
        ) {
            Card(
                backgroundColor = Color(0xFFFF6B6B),
                shape = RoundedCornerShape(12.dp),
                elevation = 8.dp,
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Photo,
                            contentDescription = "RAW 알림",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "RAW 파일 제한",
                            color = Color.White,
                            fontSize = 16.sp,
                            style = MaterialTheme.typography.h6
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "닫기",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${restriction.fileName}",
                        color = Color.White,
                        fontSize = 14.sp,
                        style = MaterialTheme.typography.body2
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = restriction.message,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        style = MaterialTheme.typography.body2
                    )
                }
            }
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
