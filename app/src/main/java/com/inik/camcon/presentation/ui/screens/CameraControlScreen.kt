package com.inik.camcon.presentation.ui.screens

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.ColorSpace
import android.media.ExifInterface
import com.inik.camcon.utils.LogcatManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.imePadding
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.inik.camcon.domain.model.ThemeMode
import com.inik.camcon.domain.model.Camera
import com.inik.camcon.domain.model.CameraPhoto
import com.inik.camcon.domain.model.CameraSettings
import com.inik.camcon.domain.model.CapturedPhoto
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.Background
import com.inik.camcon.presentation.theme.IconSize
import com.inik.camcon.presentation.theme.Padding
import com.inik.camcon.presentation.theme.Radius
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.StrokeWidth
import com.inik.camcon.presentation.theme.Surface
import com.inik.camcon.presentation.theme.SurfaceElevated
import com.inik.camcon.presentation.theme.Primary
import com.inik.camcon.presentation.theme.Error
import com.inik.camcon.presentation.theme.TextPrimary
import com.inik.camcon.presentation.theme.TextSecondary
import com.inik.camcon.presentation.theme.TextMuted
import com.inik.camcon.presentation.theme.TouchTarget
import com.inik.camcon.presentation.theme.Surface0
import com.inik.camcon.presentation.theme.TextSecondaryV2
import com.inik.camcon.presentation.ui.components.v2.EmptyState
import com.inik.camcon.presentation.ui.components.v2.StatusIndicator
import com.inik.camcon.presentation.ui.components.v2.StatusKind
import com.inik.camcon.presentation.ui.components.v2.ToastV2
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.WarningAmber
import com.inik.camcon.presentation.ui.screens.components.CameraPreviewArea
import com.inik.camcon.presentation.ui.screens.components.CameraSettingsControls
import com.inik.camcon.presentation.ui.screens.components.CaptureControls
import com.inik.camcon.presentation.ui.screens.components.FullScreenPhotoViewer
import com.inik.camcon.presentation.ui.screens.components.LoadingOverlay
import com.inik.camcon.presentation.ui.screens.components.ShootingModeSelector
import com.inik.camcon.presentation.ui.screens.components.TopControlsBar
import com.inik.camcon.presentation.ui.screens.components.UsbInitializationOverlay
import com.inik.camcon.presentation.ui.screens.components.UnsupportedShootingModeSnackbar
import com.inik.camcon.presentation.ui.screens.dialogs.CameraConnectionHelpDialog
import com.inik.camcon.presentation.ui.screens.dialogs.TimelapseSettingsDialog
import com.inik.camcon.presentation.ui.screens.camera.dialogs.CameraRestartDialog
import com.inik.camcon.domain.model.LiveViewFrame
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CameraControlScreen(
    viewModel: CameraViewModel,
    appSettingsViewModel: AppSettingsViewModel = hiltViewModel(),
    onFullscreenChange: (Boolean) -> Unit = {},
    onGalleryClick: () -> Unit = {}
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    // UI 상태들을 선별적으로 수집
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val liveViewFrame by viewModel.liveViewFrame.collectAsStateWithLifecycle()
    val cameraFeed by viewModel.cameraFeed.collectAsStateWithLifecycle()

    // 설정 상태들을 collectAsState로 개별 수집하되 리컴포지션 최적화
    val isCameraControlsEnabled by appSettingsViewModel.isCameraControlsEnabled.collectAsStateWithLifecycle()
    val isLiveViewEnabled by appSettingsViewModel.isLiveViewEnabled.collectAsStateWithLifecycle()
    val isAutoStartEventListener by appSettingsViewModel.isAutoStartEventListenerEnabled.collectAsStateWithLifecycle()
    val isShowPreviewInCapture by appSettingsViewModel.isShowLatestPhotoWhenDisabled.collectAsStateWithLifecycle()
    val isShutterSoundEnabled by appSettingsViewModel.isShutterSoundEnabled.collectAsStateWithLifecycle()
    val isLiveViewGridEnabled by appSettingsViewModel.isLiveViewGridEnabled.collectAsStateWithLifecycle()
    val hasSeenCaptureCoachmark by appSettingsViewModel.hasSeenCaptureCoachmark.collectAsStateWithLifecycle()
    val lastTimelapseInterval by appSettingsViewModel.lastTimelapseInterval.collectAsStateWithLifecycle()
    val lastTimelapseCount by appSettingsViewModel.lastTimelapseCount.collectAsStateWithLifecycle()

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
            // 이벤트 리스너를 여기서 중지하지 않음 - 탭 변경 시에도 계속 실행되어야 함
            // viewModel.stopEventListener() 호출 제거
        }
    }

    // 이벤트 리스너 자동 시작 상태 추적 (중복 방지) - 더 이상 필요하지 않음
    // UsbAutoConnectManager에서 자동 처리됨

    // 기존 자동 시작 로직은 UsbAutoConnectManager로 이동됨
    // 여기서는 연결 상태만 모니터링
    LaunchedEffect(uiState.isConnected, uiState.isNativeCameraConnected) {
        LogcatManager.d("CameraControl", "=== 연결 상태 모니터링 ===")
        LogcatManager.d("CameraControl", "isConnected: ${uiState.isConnected}")
        LogcatManager.d(
            "CameraControl",
            "isNativeCameraConnected: ${uiState.isNativeCameraConnected}"
        )

        if (uiState.isConnected && uiState.isNativeCameraConnected) {
            LogcatManager.d("CameraControl", "카메라 완전 연결 완료 - UsbAutoConnectManager에서 자동 처리됨")
            // 탭 전환 시에도 이벤트 리스너가 유지되도록 여기서는 별도 처리하지 않음
            // UsbAutoConnectManager에서 자동으로 이벤트 리스너를 관리함
        }
    }

    // 상태 변화들을 remember로 캐싱하여 불필요한 리컴포지션 방지
    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbarHostState = remember { SnackbarHostState() }
    var showTimelapseDialog by remember { mutableStateOf(false) }
    var showBottomSheet by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { scaffoldPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (showBottomSheet) {
                ModalBottomSheet(
                    onDismissRequest = {
                        scope.launch {
                            bottomSheetState.hide()
                            showBottomSheet = false
                        }
                    },
                    sheetState = bottomSheetState,
                    shape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl)
                ) {
                    CameraSettingsSheet(
                        settings = uiState.cameraSettings,
                        onSettingChange = { key, value ->
                            viewModel.updateCameraSetting(key, value)
                        },
                        onClose = {
                            scope.launch {
                                bottomSheetState.hide()
                                showBottomSheet = false
                            }
                        }
                    )
                }
            }
            AnimatedContent(
                targetState = isFullscreen && (appSettings.isCameraControlsEnabled || uiState.capturedPhotos.isNotEmpty()),
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                label = "fullscreen_content"
            ) { isFullscreenMode ->
                if (isFullscreenMode) {
                    LogcatManager.d(
                        "CameraControl",
                        "🌟 전체화면 모드 렌더링 - isFullscreen=$isFullscreen, isCameraControlsEnabled=${appSettings.isCameraControlsEnabled}, capturedPhotos=${uiState.capturedPhotos.size}"
                    )
                    // 전체화면 모드는 scaffoldPadding 무시 (시스템 UI 숨김)
                    FullscreenCameraLayout(
                        uiState = uiState,
                        liveViewFrame = liveViewFrame,
                        cameraFeed = cameraFeed,
                        viewModel = viewModel,
                        onExitFullscreen = {
                            isFullscreen = false
                            onFullscreenChange(false)
                        },
                        isLiveViewEnabled = appSettings.isLiveViewEnabled,
                        onGalleryClick = onGalleryClick,
                        isShutterSoundEnabled = isShutterSoundEnabled,
                        isLiveViewGridEnabled = isLiveViewGridEnabled,
                        onToggleLiveViewGrid = {
                            appSettingsViewModel.setLiveViewGridEnabled(!isLiveViewGridEnabled)
                        }
                    )
                } else {
                    // 일반 모드는 Scaffold contentPadding 적용
                    PortraitCameraLayout(
                        uiState = uiState,
                        liveViewFrame = liveViewFrame,
                        cameraFeed = cameraFeed,
                        viewModel = viewModel,
                        scope = scope,
                        bottomSheetState = bottomSheetState,
                        onShowTimelapseDialog = { showTimelapseDialog = true },
                        onEnterFullscreen = {
                            LogcatManager.d("CameraControl", "🌟 onEnterFullscreen 호출됨 - 전체화면 모드로 전환")
                            isFullscreen = true
                            onFullscreenChange(true)
                            LogcatManager.d(
                                "CameraControl",
                                "🌟 전체화면 상태 설정 완료: isFullscreen=$isFullscreen"
                            )
                        },
                        appSettings = appSettings,
                        onPhotoClick = { photo ->
                            selectedPhoto = photo
                            showFullScreenViewer = true
                        },
                        onShowBottomSheet = { showBottomSheet = true },
                        onGalleryClick = onGalleryClick,
                        contentPadding = scaffoldPadding,
                        isShutterSoundEnabled = isShutterSoundEnabled,
                        isLiveViewGridEnabled = isLiveViewGridEnabled,
                        onToggleLiveViewGrid = {
                            appSettingsViewModel.setLiveViewGridEnabled(!isLiveViewGridEnabled)
                        },
                        onUnsupportedShootingMode = { mode ->
                            viewModel.setShootingMode(mode)
                        }
                    )
                }
            }
        }
    }

    if (uiState.isUsbInitializing) {
        UsbInitializationOverlay(
            message = uiState.usbInitializationMessage ?: stringResource(R.string.camera_control_usb_initializing)
        )
    }

    // RAW 파일 제한 알림 표시
    uiState.rawFileRestriction?.let { restriction ->
        RawFileRestrictionNotification(
            restriction = restriction,
            onDismiss = { viewModel.clearRawFileRestriction() }
        )
    }

    // 지원하지 않는 촬영 모드 에러 Snackbar 표시
    UnsupportedShootingModeSnackbar(
        shootingModeError = uiState.dialog.shootingModeError,
        snackbarHostState = snackbarHostState
    )

    // FullScreenPhotoViewer 표시
    if (showFullScreenViewer) {
        selectedPhoto?.let { photo ->
            FullScreenPhotoViewer(
                photo = photo.toCameraPhoto(),
                onDismiss = {
                    showFullScreenViewer = false
                    selectedPhoto = null
                },
                onPhotoChanged = { /* 단일 사진이므로 변경 없음 */ },
                thumbnailData = photo.getThumbnailData(),
                fullImageData = photo.getImageData(),
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
                // M7: prefill 값 저장 후 시작
                appSettingsViewModel.setLastTimelapseSettings(interval, shots)
                viewModel.startTimelapse(interval, shots)
                showTimelapseDialog = false
            },
            onDismiss = { showTimelapseDialog = false },
            initialInterval = lastTimelapseInterval,
            initialCount = lastTimelapseCount
        )
    }

    // M5: 첫 실행 코치마크 — 카메라 컨트롤 활성 상태에서 1회 표시
    if (isCameraControlsEnabled && !hasSeenCaptureCoachmark) {
        com.inik.camcon.presentation.ui.screens.components.CaptureCoachmarkOverlay(
            onDismiss = { appSettingsViewModel.setHasSeenCaptureCoachmark(true) }
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
        CameraRestartDialog(
            onDismiss = { viewModel.dismissRestartDialog() },
            onRestart = {
                viewModel.dismissRestartDialog()
                // 앱을 완전히 재시작
                (context as? Activity)?.let { activity ->
                    // 현재 Activity 종료하고 새로 시작
                    val intent = activity.baseContext.packageManager
                        .getLaunchIntentForPackage(activity.baseContext.packageName)
                    intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    activity.startActivity(intent)
                    activity.finishAffinity() // 모든 Activity 스택 제거

                    // 프로세스 종료 (선택적 - 더 확실한 재시작을 원할 경우)
                    android.os.Process.killProcess(android.os.Process.myPid())
                    System.exit(0)
                }
            }
        )
    }
}

@androidx.compose.runtime.Stable
private data class AppSettings(
    val isCameraControlsEnabled: Boolean,
    val isLiveViewEnabled: Boolean,
    val isAutoStartEventListener: Boolean,
    val isShowPreviewInCapture: Boolean
)

/**
 * 포트레이트 모드 레이아웃 - 분리된 컴포넌트들 사용
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun PortraitCameraLayout(
    uiState: CameraUiState,
    liveViewFrame: LiveViewFrame?,
    cameraFeed: List<Camera>,
    viewModel: CameraViewModel,
    scope: kotlinx.coroutines.CoroutineScope,
    bottomSheetState: SheetState,
    onShowTimelapseDialog: () -> Unit,
    onEnterFullscreen: () -> Unit,
    appSettings: AppSettings,
    onPhotoClick: (CapturedPhoto) -> Unit = {},
    onShowBottomSheet: () -> Unit,
    onGalleryClick: () -> Unit = {},
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(0.dp),
    isShutterSoundEnabled: Boolean = true,
    isLiveViewGridEnabled: Boolean = false,
    onToggleLiveViewGrid: () -> Unit = {},
    onUnsupportedShootingMode: (com.inik.camcon.domain.model.ShootingMode) -> Unit = {}
) {
    val context = LocalContext.current

    // 진입 시 1회만 — 화면 진입 시 portrait 강제 + 시스템 바 표시 동작은 재실행 필요 없음
    LaunchedEffect(Unit) {
        (context as? Activity)?.let { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
                show(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    LaunchedEffect(appSettings) {
        LogcatManager.d(
            "CameraControl",
            "AppSettings - isCameraControlsEnabled: ${appSettings.isCameraControlsEnabled}"
        )
        LogcatManager.d(
            "CameraControl",
            "AppSettings - isLiveViewEnabled: ${appSettings.isLiveViewEnabled}"
        )
        LogcatManager.d(
            "CameraControl",
            "AppSettings - isAutoStartEventListener: ${appSettings.isAutoStartEventListener}"
        )
        LogcatManager.d(
            "CameraControl",
            "AppSettings - isShowPreviewInCapture: ${appSettings.isShowPreviewInCapture}"
        )
        LogcatManager.d(
            "CameraControl",
            "라이브뷰 UI 표시 조건 (카메라 컨트롤 & 라이브뷰 둘 다 활성화): ${appSettings.isCameraControlsEnabled && appSettings.isLiveViewEnabled}"
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
        LogcatManager.d(
            "CameraControl",
            "🔍 canEnterFullscreen 계산: isLiveViewActive=${uiState.isLiveViewActive}, capturedPhotos.size=${uiState.capturedPhotos.size}, result=$result"
        )
        result
    }

    // V2 StatusBar — 카메라 연결 상태 라벨
    val statusKind = when {
        uiState.isConnected && uiState.isNativeCameraConnected -> StatusKind.Connected
        uiState.isUsbInitializing -> StatusKind.Connecting
        uiState.error != null -> StatusKind.Error
        else -> StatusKind.Idle
    }
    val connectedLabel = stringResource(R.string.camera_connected)
    val connectingLabel = stringResource(R.string.connecting)
    val errorLabel = stringResource(R.string.error)
    val disconnectedLabel = stringResource(R.string.camera_disconnected)
    val statusLabel = when (statusKind) {
        StatusKind.Connected -> {
            val model = cameraFeed.firstOrNull()?.name
            if (model.isNullOrBlank()) connectedLabel else "$connectedLabel · $model"
        }
        StatusKind.Connecting -> connectingLabel
        StatusKind.Error -> errorLabel
        StatusKind.Idle -> disconnectedLabel
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(contentPadding)
            .imePadding()
    ) {
        // V2 StatusBar Row (32dp) — 연결 상태 표시 + 토스트 슬롯
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .padding(horizontal = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusIndicator(kind = statusKind, label = statusLabel)
        }

        TopControlsBar(
            uiState = uiState,
            cameraFeed = cameraFeed,
            onSettingsClick = { onShowBottomSheet() }
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Background),
            contentAlignment = Alignment.Center
        ) {
            if (appSettings.isCameraControlsEnabled && appSettings.isLiveViewEnabled) {
                // ✅ Bitmap 디코딩 수집 (IO 디스패처에서 처리됨)
                val decodedBitmap by viewModel.decodedLiveViewBitmap.collectAsStateWithLifecycle()

                CameraPreviewArea(
                    liveViewState = uiState.liveView,
                    liveViewFrame = liveViewFrame,
                    decodedBitmap = decodedBitmap,  // ✅ 새 파라미터 전달
                    connectionState = uiState.connection,
                    captureState = uiState.capture,
                    cameraCapabilities = uiState.cameraCapabilities,
                    cameraFeed = cameraFeed,
                    onStopLiveView = viewModel::stopLiveView,
                    onStartLiveView = viewModel::startLiveView,
                    onConnectCamera = viewModel::connectCamera,
                    onRefreshUsb = viewModel::refreshUsbDevices,
                    onRequestUsbPermission = viewModel::requestUsbPermission,
                    onDoubleClick = {
                        if (canEnterFullscreen) {
                            onEnterFullscreen()
                        }
                    },
                    isGridOverlayEnabled = isLiveViewGridEnabled,
                    onToggleGridOverlay = onToggleLiveViewGrid
                )
            } else {
                LogcatManager.d(
                    "CameraControl",
                    "사진 표시 모드 - 수신된 사진 개수: ${uiState.capturedPhotos.size}"
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics {
                            customActions = listOf(
                                CustomAccessibilityAction("전체화면 전환") {
                                    if (canEnterFullscreen) { onEnterFullscreen(); true } else false
                                }
                            )
                        }
                        .combinedClickable(
                            onClick = {
                                LogcatManager.d("CameraControl", "수신 사진 영역 단일 클릭")
                            },
                            onDoubleClick = {
                                LogcatManager.d(
                                    "CameraControl",
                                    "수신 사진 영역 더블클릭 감지! canEnterFullscreen=$canEnterFullscreen"
                                )
                                if (canEnterFullscreen) {
                                    LogcatManager.d("CameraControl", "전체화면 모드로 전환 시도")
                                    onEnterFullscreen()
                                } else {
                                    LogcatManager.w("CameraControl", "전체화면 진입 불가 - 조건 미충족")
                                }
                            }
                        )
                ) {
                    AnimatedPhotoSwitcher(
                        capturedPhotos = uiState.capturedPhotos,
                        modifier = Modifier.fillMaxSize(),
                        emptyTextColor = TextSecondary,
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
                // 프리미엄 힌트 배지
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(Padding.lg)
                        .background(
                            SurfaceElevated.copy(alpha = 0.8f),
                            RoundedCornerShape(Radius.lg)
                        )
                        .padding(horizontal = Padding.md, vertical = 6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.camera_control_double_click_fullscreen),
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // 프리미엄 하단 컨트롤 패널
        Surface(
            color = Surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            tonalElevation = Spacing.sm,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(vertical = Padding.sm)
            ) {
                // H2: 카메라 컨트롤이 켜져 있으면 라이브뷰 여부와 무관하게 모드 선택을 노출
                if (appSettings.isCameraControlsEnabled) {
                    ShootingModeSelector(
                        captureState = uiState.capture,
                        isConnected = uiState.isConnected,
                        cameraCapabilities = uiState.cameraCapabilities,
                        onModeSelected = { mode -> viewModel.setShootingMode(mode) },
                        onUnsupportedModeClick = onUnsupportedShootingMode,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }

                // ISO/셔터스피드/조리개 조절 컨트롤
                if (appSettings.isCameraControlsEnabled && uiState.isConnected) {
                    CameraSettingsControls(
                        currentSettings = uiState.cameraSettings,
                        capabilities = uiState.cameraCapabilities,
                        onSettingChange = { key, value ->
                            viewModel.updateCameraSetting(key, value)
                        },
                        isEnabled = uiState.isConnected && !uiState.isCapturing,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }

                // H2: 카메라 컨트롤이 활성화되어 있고 라이브뷰가 비활성/미지원이어도
                // 트리거 캡처용 셔터/AF는 항상 노출. CaptureControls 내부 라벨이
                // "라이브뷰 없이 트리거 캡처"를 표시한다.
                if (appSettings.isCameraControlsEnabled) {
                    val supportsLiveView = uiState.cameraCapabilities?.canLiveView ?: false
                    val liveViewVisuallyActive = appSettings.isLiveViewEnabled && supportsLiveView
                    CaptureControls(
                        captureState = uiState.capture,
                        isConnected = uiState.isConnected,
                        onCapture = viewModel::capturePhoto,
                        onAutoFocus = viewModel::performAutoFocus,
                        onShowTimelapseDialog = onShowTimelapseDialog,
                        isVertical = false,
                        onGalleryClick = onGalleryClick,
                        isLiveViewActive = liveViewVisuallyActive,
                        isShutterSoundEnabled = isShutterSoundEnabled
                    )
                }

                if (recentPhotos.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.camera_control_received_photos, uiState.capturedPhotos.size),
                        color = TextPrimary,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = Padding.lg, vertical = Padding.md)
                    )
                    RecentCapturesRow(
                        photos = recentPhotos,
                        onPhotoClick = onPhotoClick,
                        modifier = Modifier.padding(horizontal = Padding.lg, vertical = Padding.sm)
                    )
                }
            }
        }
    }
}

/**
 * 전체화면 모드 레이아웃 - 분리된 컴포넌트들 사용
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun FullscreenCameraLayout(
    uiState: CameraUiState,
    liveViewFrame: LiveViewFrame?,
    cameraFeed: List<Camera>,
    viewModel: CameraViewModel,
    onExitFullscreen: () -> Unit,
    isLiveViewEnabled: Boolean,
    onGalleryClick: () -> Unit = {},
    isShutterSoundEnabled: Boolean = true,
    isLiveViewGridEnabled: Boolean = false,
    onToggleLiveViewGrid: () -> Unit = {}
) {
    val context = LocalContext.current
    var showTimelapseDialog by remember { mutableStateOf(false) }
    var isRotated by remember { mutableStateOf(false) }

    // 진입 시 1회만 — 전체화면 진입 시 landscape 전환 + 시스템 바 숨김은 재실행 불필요
    LaunchedEffect(Unit) {
        (context as? Activity)?.let { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // 메인 라이브뷰 또는 사진 뷰 영역
        if (isLiveViewEnabled && uiState.isLiveViewActive) {
            // ✅ Bitmap 디코딩 수집 (IO 디스패처에서 처리됨)
            val decodedBitmap by viewModel.decodedLiveViewBitmap.collectAsStateWithLifecycle()

            // 라이브뷰 모드
            CameraPreviewArea(
                liveViewState = uiState.liveView,
                liveViewFrame = liveViewFrame,
                decodedBitmap = decodedBitmap,  // ✅ 새 파라미터 전달
                connectionState = uiState.connection,
                captureState = uiState.capture,
                cameraCapabilities = uiState.cameraCapabilities,
                cameraFeed = cameraFeed,
                onStopLiveView = viewModel::stopLiveView,
                onStartLiveView = viewModel::startLiveView,
                onConnectCamera = viewModel::connectCamera,
                onRefreshUsb = viewModel::refreshUsbDevices,
                onRequestUsbPermission = viewModel::requestUsbPermission,
                modifier = Modifier.fillMaxSize(),
                onDoubleClick = onExitFullscreen,
                isGridOverlayEnabled = isLiveViewGridEnabled,
                onToggleGridOverlay = onToggleLiveViewGrid
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics {
                        customActions = listOf(
                            CustomAccessibilityAction("전체화면 종료") {
                                onExitFullscreen(); true
                            }
                        )
                    }
                    .combinedClickable(
                        onClick = { /* 단일 클릭 처리 */ },
                        onDoubleClick = onExitFullscreen
                    )
            ) {
                AnimatedPhotoSwitcher(
                    capturedPhotos = uiState.capturedPhotos,
                    modifier = Modifier.fillMaxSize(),
                    emptyTextColor = TextSecondary,
                    isRotated = isRotated,
                    onDoubleClick = onExitFullscreen
                )
            }
        }

        // 우측 컨트롤 패널 - 라이브뷰가 활성화되어 있을 때만 표시
        if (isLiveViewEnabled && uiState.isLiveViewActive) {
            FullscreenControlPanel(
                captureState = uiState.capture,
                isConnected = uiState.isConnected,
                cameraCapabilities = uiState.cameraCapabilities,
                onCapture = viewModel::capturePhoto,
                onAutoFocus = viewModel::performAutoFocus,
                onSetShootingMode = viewModel::setShootingMode,
                onShowTimelapseDialog = { showTimelapseDialog = true },
                onExitFullscreen = onExitFullscreen,
                onRotate = { isRotated = !isRotated },
                onGalleryClick = onGalleryClick,
                isShutterSoundEnabled = isShutterSoundEnabled,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(Padding.lg)
            )
        } else if (uiState.capturedPhotos.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Padding.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Surface(
                    color = SurfaceElevated.copy(alpha = 0.9f),
                    shape = CircleShape
                ) {
                    IconButton(
                        onClick = { isRotated = !isRotated },
                        modifier = Modifier
                            .size(TouchTarget.xl)
                            .background(SurfaceElevated, CircleShape)
                    ) {
                        Icon(
                            Icons.Default.RotateRight,
                            contentDescription = stringResource(R.string.cd_rotate_180),
                            tint = TextPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Surface(
                    color = SurfaceElevated.copy(alpha = 0.9f),
                    shape = CircleShape
                ) {
                    IconButton(
                        onClick = onExitFullscreen,
                        modifier = Modifier
                            .size(TouchTarget.xl)
                            .background(Error.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.cd_exit_fullscreen),
                            tint = TextPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }

        // 하단 안내 텍스트 - 프리미엄 스타일
        Surface(
            color = SurfaceElevated.copy(alpha = 0.8f),
            shape = RoundedCornerShape(Radius.lg),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(Padding.lg)
        ) {
            Text(
                text = stringResource(R.string.camera_control_double_click_exit),
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = Padding.base, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // 전역 로딩 상태
        if (uiState.isCapturing) {
            LoadingOverlay(stringResource(R.string.camera_control_capturing))
        }
    }

    // 타임랩스 설정 다이얼로그 (전체화면 모드)
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
 * 전체화면 컨트롤 패널 -- state+callback 패턴
 */
@Composable
private fun FullscreenControlPanel(
    captureState: com.inik.camcon.presentation.viewmodel.CameraCaptureState,
    isConnected: Boolean,
    cameraCapabilities: com.inik.camcon.domain.model.CameraCapabilities?,
    onCapture: () -> Unit,
    onAutoFocus: () -> Unit,
    onSetShootingMode: (com.inik.camcon.domain.model.ShootingMode) -> Unit,
    onShowTimelapseDialog: () -> Unit,
    onExitFullscreen: () -> Unit,
    onRotate: (() -> Unit)? = null,
    onGalleryClick: () -> Unit = {},
    isShutterSoundEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SurfaceElevated.copy(alpha = 0.95f),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = Spacing.sm,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(Padding.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 종료 버튼
            Surface(
                color = Error.copy(alpha = 0.2f),
                shape = CircleShape,
                modifier = Modifier.size(TouchTarget.xl)
            ) {
                IconButton(
                    onClick = onExitFullscreen,
                    modifier = Modifier.size(TouchTarget.xl)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.cd_exit_fullscreen),
                        tint = Error,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // 180도 회전 버튼
            Surface(
                color = SurfaceElevated,
                shape = CircleShape,
                modifier = Modifier.size(TouchTarget.xl)
            ) {
                IconButton(
                    onClick = { onRotate?.invoke() },
                    enabled = onRotate != null,
                    modifier = Modifier.size(TouchTarget.xl)
                ) {
                    Icon(
                        Icons.Default.RotateRight,
                        contentDescription = stringResource(R.string.cd_rotate_180),
                        tint = if (onRotate != null) TextPrimary else TextSecondary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // 촬영 모드 선택 (세로)
            ShootingModeSelector(
                captureState = captureState,
                isConnected = isConnected,
                cameraCapabilities = cameraCapabilities,
                onModeSelected = onSetShootingMode,
            )

            // 메인 촬영 버튼
            CaptureControls(
                captureState = captureState,
                isConnected = isConnected,
                onCapture = onCapture,
                onAutoFocus = onAutoFocus,
                onShowTimelapseDialog = onShowTimelapseDialog,
                isVertical = true,
                onGalleryClick = onGalleryClick,
                isShutterSoundEnabled = isShutterSoundEnabled
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
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
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
 * 프리미엄 개별 사진 아이템
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

    Surface(
        modifier = Modifier
            .size(104.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(Radius.xl),
        color = SurfaceElevated,
        tonalElevation = Padding.xs,
        shadowElevation = Padding.xs
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // 실제 이미지가 있으면 표출
            photo.thumbnailPath?.let { thumbnailPath ->
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(thumbnailPath)
                        .crossfade(200)
                        .memoryCacheKey(photo.id + "_thumb")
                        .scale(Scale.FIT)
                        .allowHardware(false)
                        .apply {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                colorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                            }
                        }
                        .build(),
                    contentDescription = stringResource(R.string.camera_control_captured_photo),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } ?: run {
                // 썸네일이 없으면 원본 이미지 시도
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(photo.filePath)
                        .crossfade(200)
                        .memoryCacheKey(photo.id + "_full")
                        .scale(Scale.FIT)
                        .allowHardware(false)
                        .apply {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                colorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                            }
                        }
                        .build(),
                    contentDescription = stringResource(R.string.camera_control_captured_photo),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            // 다운로드 상태 표시
            if (photo.isDownloading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Background.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Primary,
                        modifier = Modifier.size(IconSize.lg),
                        strokeWidth = StrokeWidth.thick
                    )
                }
            }

            // 파일 크기 표시 (하단)
            if (photo.size > 0) {
                Surface(
                    color = Background.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(Radius.md),
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    Text(
                        text = sizeText,
                        color = TextPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
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
    emptyTextColor: Color = TextSecondary,
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
                                LogcatManager.d("CameraPhoto", "수신된 사진 로딩 시작: ${photo.filePath}")
                            },
                            onSuccess = { request, result ->
                                LogcatManager.d("CameraPhoto", "수신된 사진 로딩 성공: ${photo.filePath}")

                                // EXIF 디버그 로깅 — 릴리즈에서는 파일 I/O 자체를 건너뜀
                                if (com.inik.camcon.BuildConfig.DEBUG) {
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

                                        LogcatManager.d("EXIF_RECEIVED_PHOTO", "=== 수신 사진 EXIF 정보 ===")
                                        LogcatManager.d("EXIF_RECEIVED_PHOTO", "파일: ${photo.filePath}")
                                        LogcatManager.d("EXIF_RECEIVED_PHOTO", "EXIF Orientation: $orientation")
                                        LogcatManager.d("EXIF_RECEIVED_PHOTO", "회전 정보: $rotationText")
                                        LogcatManager.d(
                                            "EXIF_RECEIVED_PHOTO",
                                            "Coil이 자동 회전 처리: ${orientation != androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL}"
                                        )
                                    } catch (e: Exception) {
                                        LogcatManager.e("EXIF_RECEIVED_PHOTO", "EXIF 정보 확인 실패: ${e.message}", e)
                                    }
                                }
                            },
                            onError = { request, error ->
                                LogcatManager.e(
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
                    contentDescription = stringResource(R.string.camera_control_photo),
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (isRotated) Modifier.rotate(180f) else Modifier)
                        .combinedClickable(
                            onClick = {
                                LogcatManager.d("CameraControl", "수신 사진 이미지 단일 클릭")
                            },
                            onDoubleClick = {
                                LogcatManager.d("CameraControl", "🔥 수신 사진 이미지에서 더블클릭 감지!")
                                LogcatManager.d(
                                    "CameraControl",
                                    "🔍 onDoubleClick 콜백 호출 시도 - 콜백 존재 여부: ${onDoubleClick != null}"
                                )
                                onDoubleClick?.invoke()
                                LogcatManager.d("CameraControl", "✅ onDoubleClick 콜백 호출 완료")
                            }
                        ),
                    contentScale = ContentScale.Fit
                )
            }
        }
        // 사진이 없을 때 EmptyState 표시 (V2)
        AnimatedVisibility(
            visible = latestPhoto == null,
            enter = fadeIn(animationSpec = tween(350)),
            exit = fadeOut(animationSpec = tween(350))
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    icon = Icons.Outlined.PhotoLibrary,
                    title = stringResource(R.string.camera_control_no_received_photos),
                    description = stringResource(R.string.camera_control_photo_appear_hint)
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
            .padding(Padding.base)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.camera_settings),
                style = MaterialTheme.typography.titleLarge
            )
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.close)
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.base))

        // Settings would be dynamically loaded based on camera capabilities
//        settings?.let {
//            Text("ISO: ${it.iso}")
//            Text("셔터 속도: ${it.shutterSpeed}")
//            Text("조리개: ${it.aperture}")
//            Text("화이트밸런스: ${it.whiteBalance}")
//        } ?: run {
//            Text("카메라 설정을 로드할 수 없습니다", color = Color.Gray)
//        }
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
                LogcatManager.e("CameraControl", "셔터 속도 파싱 실패: $exposureTime")
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
        LogcatManager.e("CameraControl", "EXIF 메타데이터 읽기 실패: ${e.message}")
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
        LogcatManager.e("CameraControl", "EXIF 정보 읽기 실패: ${e.message}", e)
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

    // 화면 상단에 표시 — V2 ToastV2 (Error kind)
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
                .padding(top = 36.dp, start = Padding.base, end = Padding.base)
        ) {
            Box(modifier = Modifier.align(Alignment.TopCenter)) {
                ToastV2(
                    message = "${stringResource(R.string.camera_control_raw_file_restriction)} · ${restriction.fileName} — ${restriction.message}",
                    kind = StatusKind.Error,
                    leadingIcon = Icons.Outlined.WarningAmber,
                    modifier = Modifier.clickable { onDismiss() }
                )
            }
        }
    }
}

// 프리뷰는 간소화
@Preview(name = "Camera Control Screen", showBackground = true)
@Composable
private fun CameraControlScreenPreview() {
    CamConTheme() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Background),
            contentAlignment = Alignment.Center
        ) {
            Text(
                stringResource(R.string.camera_control_preview_screen),
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview(name = "Camera Settings Sheet", showBackground = true)
@Composable
private fun CameraSettingsSheetPreview() {
    CamConTheme() {
        CameraSettingsSheet(
            settings = CameraSettings(
                iso = "400",
                shutterSpeed = "1/125",
                aperture = "f/2.8",
                whiteBalance = "Auto",
                focusMode = "Auto",
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
    CamConTheme() {
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
            modifier = Modifier.padding(Padding.base)
        )
    }
}

@Preview(name = "Fullscreen Control Panel", showBackground = true)
@Composable
private fun FullscreenControlPanelPreview() {
    CamConTheme() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Background),
            contentAlignment = Alignment.Center
        ) {
            Text(
                stringResource(R.string.camera_control_fullscreen_panel),
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
        }
    }
}
