package com.inik.camcon.presentation.ui.screens

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.ColorSpace
import android.widget.Toast
import com.inik.camcon.utils.LogcatManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Scale
import com.inik.camcon.R
import com.inik.camcon.utils.resolve
import com.inik.camcon.domain.model.ThemeMode
import com.inik.camcon.domain.model.UiText
import com.inik.camcon.domain.model.Camera
import com.inik.camcon.domain.model.CameraPhoto
import com.inik.camcon.domain.model.CameraSettings
import com.inik.camcon.domain.model.CapturedPhoto
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.DisplayNum
import com.inik.camcon.presentation.theme.Elevation
import com.inik.camcon.presentation.theme.IconSize
import com.inik.camcon.presentation.theme.Padding
import com.inik.camcon.presentation.theme.Radius
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.StrokeWidth
import com.inik.camcon.presentation.theme.Surface1
import com.inik.camcon.presentation.theme.Surface2
import com.inik.camcon.presentation.theme.Accent
import com.inik.camcon.presentation.theme.DividerLine
import com.inik.camcon.presentation.theme.ErrorV2
import com.inik.camcon.presentation.theme.Micro
import com.inik.camcon.presentation.theme.MicroLabel
import com.inik.camcon.presentation.theme.MonoMicro
import com.inik.camcon.presentation.theme.MonoReadout
import com.inik.camcon.presentation.theme.Surface3
import com.inik.camcon.presentation.theme.TextDisabled
import com.inik.camcon.presentation.theme.TextPrimaryV2
import com.inik.camcon.presentation.theme.TextTertiary
import com.inik.camcon.presentation.theme.TouchTarget
import com.inik.camcon.presentation.theme.Surface0
import com.inik.camcon.presentation.theme.TextSecondaryV2
import com.inik.camcon.presentation.ui.components.v2.EmptyState
import com.inik.camcon.presentation.ui.components.v2.StatusIndicator
import com.inik.camcon.presentation.ui.components.v2.StatusKind
import com.inik.camcon.presentation.ui.components.v2.ToastV2
import com.inik.camcon.presentation.ui.components.v2.TransferProgressBadge
import com.inik.camcon.presentation.ui.util.FullscreenOrientation
import com.inik.camcon.presentation.ui.util.FullscreenOrientationPolicy
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.WarningAmber
import com.inik.camcon.presentation.ui.screens.components.CameraPreviewArea
import com.inik.camcon.presentation.ui.screens.components.CameraSettingsControls
import com.inik.camcon.presentation.ui.screens.components.LiveViewExposureStrip
import com.inik.camcon.presentation.ui.screens.components.CaptureControls
import com.inik.camcon.presentation.ui.screens.components.icon
import com.inik.camcon.presentation.ui.screens.components.next
import com.inik.camcon.presentation.ui.screens.components.shortLabelRes
import com.inik.camcon.presentation.ui.screens.components.FullScreenPhotoViewer
import com.inik.camcon.presentation.ui.screens.components.LoadingOverlay
import com.inik.camcon.presentation.ui.screens.components.ShootingModeSelector
import com.inik.camcon.presentation.ui.screens.components.TopControlsBar
import com.inik.camcon.presentation.ui.screens.components.UsbInitializationOverlay
import com.inik.camcon.presentation.ui.screens.components.UnsupportedShootingModeSnackbar
import com.inik.camcon.presentation.ui.screens.dialogs.CameraConnectionHelpDialog
import com.inik.camcon.presentation.ui.screens.dialogs.TimelapseSettingsDialog
import com.inik.camcon.presentation.ui.screens.camera.dialogs.CameraRestartDialog
import com.inik.camcon.domain.model.LiveViewQuality
import com.inik.camcon.domain.usecase.PipelineFeature
import com.inik.camcon.presentation.viewmodel.AppSettingsViewModel
import com.inik.camcon.presentation.viewmodel.CameraUiState
import com.inik.camcon.presentation.viewmodel.CameraViewModel
import com.inik.camcon.presentation.viewmodel.RawFileRestriction
import kotlinx.coroutines.launch
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
    // liveViewFrame(프레임레이트로 갱신)은 루트에서 수집하면 CameraControlScreen 전체 스코프가
    // 프레임마다 recompose 되므로, 실제 소비처인 각 레이아웃의 라이브뷰 블록에서만 수집한다.
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cameraFeed by viewModel.cameraFeed.collectAsStateWithLifecycle()

    // 설정 상태들을 collectAsState로 개별 수집하되 리컴포지션 최적화
    val isCameraControlsEnabled by appSettingsViewModel.isCameraControlsEnabled.collectAsStateWithLifecycle()
    val isLiveViewEnabled by appSettingsViewModel.isLiveViewEnabled.collectAsStateWithLifecycle()
    val isAutoStartEventListener by appSettingsViewModel.isAutoStartEventListenerEnabled.collectAsStateWithLifecycle()
    val isShowPreviewInCapture by appSettingsViewModel.isShowLatestPhotoWhenDisabled.collectAsStateWithLifecycle()
    val isShutterSoundEnabled by appSettingsViewModel.isShutterSoundEnabled.collectAsStateWithLifecycle()
    val isLiveViewGridEnabled by appSettingsViewModel.isLiveViewGridEnabled.collectAsStateWithLifecycle()
    val isHistogramEnabled by appSettingsViewModel.isHistogramEnabled.collectAsStateWithLifecycle()
    val isFocusPeakingEnabled by appSettingsViewModel.isFocusPeakingEnabled.collectAsStateWithLifecycle()
    // histogramData 는 프레임레이트로 갱신되므로 여기서 수집하지 않는다.
    // liveViewFrame 과 같은 이유로 각 레이아웃의 라이브뷰 최하위 스코프에서만 수집한다.
    val hasSeenCaptureCoachmark by appSettingsViewModel.hasSeenCaptureCoachmark.collectAsStateWithLifecycle()
    // CINE 이미지 파이프라인 패널 상태 (AppSettings 단일 소스 — 이미 존재하는 StateFlow 소비, VM 추가 없음)
    val isFilmSimulationEnabled by appSettingsViewModel.isFilmSimulationEnabled.collectAsStateWithLifecycle()
    val selectedFilmLutId by appSettingsViewModel.selectedFilmLutId.collectAsStateWithLifecycle()
    val selectedFilmLutLocked by appSettingsViewModel.selectedFilmLutLocked.collectAsStateWithLifecycle()
    val isColorTransferEnabled by appSettingsViewModel.isColorTransferEnabled.collectAsStateWithLifecycle()
    val colorTransferReferenceImagePath by appSettingsViewModel.colorTransferReferenceImagePath.collectAsStateWithLifecycle()
    val lastTimelapseInterval by appSettingsViewModel.lastTimelapseInterval.collectAsStateWithLifecycle()
    val lastTimelapseCount by appSettingsViewModel.lastTimelapseCount.collectAsStateWithLifecycle()
    // 인-LV 화질 컨트롤 현재값/콜백 — SettingsActivity와 동일 DataStore 단일 소스(자동 동기).
    // 변경은 setLiveViewQuality 단일 경유 → DataStore → observeLiveViewQuality 가 재시작까지 처리.
    val liveViewQuality by appSettingsViewModel.liveViewQuality.collectAsStateWithLifecycle()
    val onCycleLiveViewQuality: () -> Unit = {
        appSettingsViewModel.setLiveViewQuality(liveViewQuality.next())
    }

    // 필름↔색감 배타 스왑 안내 토스트 — CTA 없이 안내만(다운로드 제한 ToastV2 관례와 동일).
    val pipelineSwapColorDisabledText = stringResource(R.string.pipeline_swap_color_disabled)
    val pipelineSwapFilmDisabledText = stringResource(R.string.pipeline_swap_film_disabled)
    var pipelineSwapMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        appSettingsViewModel.pipelineSwapEvent.collect { disabled ->
            pipelineSwapMessage = when (disabled) {
                PipelineFeature.COLOR_TRANSFER -> pipelineSwapColorDisabledText
                PipelineFeature.FILM_SIMULATION -> pipelineSwapFilmDisabledText
            }
        }
    }

    // FILM SIM 칩 롱프레스 → '기본 필름 선택'(select-only) 에디터를 열고 결과를 소비한다.
    // 선택된 lutId 를 기본 필름으로 저장 + 필름 시뮬레이션 자동 적용을 켠다(선택=사용 의도) + 피드백 토스트.
    val filmDefaultSelectLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val lutId = result.data
                ?.getStringExtra(com.inik.camcon.presentation.ui.FilmEditorActivity.EXTRA_RESULT_LUT_ID)
            if (!lutId.isNullOrEmpty()) {
                appSettingsViewModel.setSelectedFilmLutId(lutId)
                appSettingsViewModel.setFilmSimulationEnabled(true)
                val name = filmLutDisplayName(lutId) ?: lutId
                Toast.makeText(
                    context,
                    context.getString(R.string.film_default_set_toast, name),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    val onLongPressFilmSim: () -> Unit = {
        filmDefaultSelectLauncher.launch(
            Intent(context, com.inik.camcon.presentation.ui.FilmEditorActivity::class.java)
                .putExtra(com.inik.camcon.presentation.ui.FilmEditorActivity.EXTRA_SELECT_ONLY, true)
        )
    }

    // 다이얼로그 상태들
    var showFolderSelectionDialog by remember { mutableStateOf(false) }
    var showSaveFormatSelectionDialog by remember { mutableStateOf(false) }
    var showConnectionHelpDialog by remember { mutableStateOf(false) }
    // 연결 도움말을 이미 표시·처리한 에러 (동일 에러 중복 표시 방지, 클리어 시 리셋)
    var handledConnectionError by remember { mutableStateOf<UiText?>(null) }

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

    // ⚠️ 이 화면은 니콘 앱 모드를 **건드리지 않는다**.
    // 촬영 화면에서는 본체 재생(▶)이 눌려야 한다는 것이 요구사항이고(사용자 결정 2026-08-20),
    // ▶ 를 여는 유일한 수단이 앱 모드 ON 이다(벤더 사양). 앱 셔터를 UI 에서 제거했으므로
    // 여기서 OFF 를 유지할 이유(카드 라우팅)도 없다. 카드 탐색 점유는 미리보기 탭만 잡는다.

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

    // 전체화면 라이브뷰에서 기기 뒤로가기를 가로채 전체화면만 해제한다(화면/앱 종료·시스템바 잔존 방지).
    BackHandler(enabled = isFullscreen) {
        isFullscreen = false
        onFullscreenChange(false)
    }
    val scope = rememberCoroutineScope()
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbarHostState = remember { SnackbarHostState() }
    var showTimelapseDialog by rememberSaveable { mutableStateOf(false) }
    var showBottomSheet by rememberSaveable { mutableStateOf(false) }

    // === 전체화면 방향 단일 소유 지점(SSOT) ===
    // requestedOrientation 을 쓰는 곳은 이 파일에서 여기 하나뿐이어야 한다.
    // 아래 AnimatedContent 의 자식들이 각자 방향을 걸면, 이탈하는 쪽이 fade 300ms 뒤에
    // dispose 되면서 새로 들어온 쪽의 설정을 덮어써 방향이 어긋난다.
    val isFullscreenActive =
        isFullscreen && (appSettings.isCameraControlsEnabled || uiState.capturedPhotos.isNotEmpty())
    val isShowingLiveView =
        isFullscreenActive && appSettings.isLiveViewEnabled && uiState.isLiveViewActive

    // 수동 180도 보정. AnimatedContent 자식 안에 있으면 전체화면을 나갔다 올 때마다 풀리므로
    // 바깥으로 올리고 rememberSaveable 로 프로세스 사망도 견디게 한다.
    var isRotated by rememberSaveable { mutableStateOf(false) }

    // 표시 비율은 Coil 이 실제로 그린 비트맵에서만 얻는다.
    // CapturedPhoto.width/height 는 생성 경로 6곳이 0 하드코딩이라 신뢰할 수 없다.
    var photoAspectRatio by remember { mutableStateOf<Float?>(null) }
    var fullscreenOrientation by remember { mutableStateOf(FullscreenOrientation.LANDSCAPE) }

    LaunchedEffect(isShowingLiveView, photoAspectRatio) {
        fullscreenOrientation = FullscreenOrientationPolicy.resolve(
            isLiveView = isShowingLiveView,
            photoAspectRatio = photoAspectRatio,
            previous = fullscreenOrientation
        )
    }

    LaunchedEffect(isFullscreenActive, fullscreenOrientation) {
        val activity = context as? Activity ?: return@LaunchedEffect
        activity.requestedOrientation = when {
            !isFullscreenActive -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            fullscreenOrientation == FullscreenOrientation.PORTRAIT ->
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            // 단방향 고정이 상하반전의 원인이었다. c4d96581 이 LANDSCAPE 를 REVERSE_LANDSCAPE 로
            // 바꿨지만 둘 다 한쪽 가로만 고정하므로, 반대로 눕히면 같은 증상이 그대로 재발했다.
            // USER_LANDSCAPE 는 양방향 가로를 허용하면서 사용자의 자동회전 잠금은 존중한다
            // (SENSOR_LANDSCAPE 는 잠금을 무시해 삼각대·케이지 사용자가 화면을 통제할 수 없다).
            else -> ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
        }
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
            if (isFullscreenActive) {
                WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                hide(WindowInsetsCompat.Type.systemBars())
            } else {
                show(WindowInsetsCompat.Type.systemBars())
            }
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    // MainActivity 가 이미 Scaffold(SubcomposeLayout) 안에서 이 화면을 NavHost 목적지로 띄운다.
    // 여기서 Scaffold 를 또 열면 SubcomposeLayout 이 2중이 되어 측정 단계마다 슬롯 컴포지션 부기 비용을 이중으로 낸다.
    // 이 Scaffold 가 쓰던 슬롯은 snackbarHost 하나뿐이고 contentWindowInsets 가 0이라
    // 넘겨주던 padding 도 항상 0이었으므로, Box + SnackbarHost 로 치환한다(동작 보존).
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface0)
    ) {
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
                targetState = isFullscreenActive,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                label = "fullscreen_content"
            ) { isFullscreenMode ->
                if (isFullscreenMode) {
//                    LogcatManager.d(
//                        "CameraControl",
//                        "🌟 전체화면 모드 렌더링 - isFullscreen=$isFullscreen, isCameraControlsEnabled=${appSettings.isCameraControlsEnabled}, capturedPhotos=${uiState.capturedPhotos.size}"
//                    )
                    // 전체화면 모드는 contentPadding 무시 (시스템 UI 숨김)
                    FullscreenCameraLayout(
                        uiState = uiState,
                        cameraFeed = cameraFeed,
                        viewModel = viewModel,
                        onExitFullscreen = {
                            isFullscreen = false
                            onFullscreenChange(false)
                        },
                        isLiveViewEnabled = appSettings.isLiveViewEnabled,
                        onGalleryClick = {
                            // 갤러리로 나가면 전체화면을 해제 — 잔류 시 탭 네비 숨김·인셋
                            // 이상 상태가 갤러리 화면까지 샌다(화면 로컬+상위 양쪽 해제).
                            isFullscreen = false
                            onFullscreenChange(false)
                            onGalleryClick()
                        },
                        isShutterSoundEnabled = isShutterSoundEnabled,
                        isLiveViewGridEnabled = isLiveViewGridEnabled,
                        onToggleLiveViewGrid = {
                            appSettingsViewModel.setLiveViewGridEnabled(!isLiveViewGridEnabled)
                        },
                        isHistogramEnabled = isHistogramEnabled,
                        onToggleHistogram = {
                            appSettingsViewModel.setHistogramEnabled(!isHistogramEnabled)
                        },
                        isFocusPeakingEnabled = isFocusPeakingEnabled,
                        onToggleFocusPeaking = {
                            appSettingsViewModel.setFocusPeakingEnabled(!isFocusPeakingEnabled)
                        },
                        liveViewQuality = liveViewQuality,
                        onCycleLiveViewQuality = onCycleLiveViewQuality,
                        isRotated = isRotated,
                        onToggleRotate = { isRotated = !isRotated },
                        onPhotoAspectResolved = { photoAspectRatio = it }
                    )
                } else {
                    // 일반 모드는 Scaffold contentPadding 적용
                    PortraitCameraLayout(
                        uiState = uiState,
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
                        contentPadding = PaddingValues(0.dp),   // 구 scaffoldPadding — insets 0 이라 항상 0이었다
                        isShutterSoundEnabled = isShutterSoundEnabled,
                        isLiveViewGridEnabled = isLiveViewGridEnabled,
                        onToggleLiveViewGrid = {
                            appSettingsViewModel.setLiveViewGridEnabled(!isLiveViewGridEnabled)
                        },
                        isHistogramEnabled = isHistogramEnabled,
                        onToggleHistogram = {
                            appSettingsViewModel.setHistogramEnabled(!isHistogramEnabled)
                        },
                        isFocusPeakingEnabled = isFocusPeakingEnabled,
                        onToggleFocusPeaking = {
                            appSettingsViewModel.setFocusPeakingEnabled(!isFocusPeakingEnabled)
                        },
                        liveViewQuality = liveViewQuality,
                        onCycleLiveViewQuality = onCycleLiveViewQuality,
                        onUnsupportedShootingMode = { mode ->
                            viewModel.setShootingMode(mode)
                        },
                        isFilmSimulationEnabled = isFilmSimulationEnabled,
                        selectedFilmLutId = selectedFilmLutId,
                        selectedFilmLutLocked = selectedFilmLutLocked,
                        onToggleFilmSimulation = {
                            // GPU 정리(releaseGpu) 호출 금지 — 전역 싱글톤 파괴 위험(memory 규약).
                            appSettingsViewModel.setFilmSimulationEnabled(!isFilmSimulationEnabled)
                        },
                        onLongPressFilmSim = onLongPressFilmSim,
                        isColorTransferEnabled = isColorTransferEnabled,
                        colorTransferReferenceImagePath = colorTransferReferenceImagePath,
                        onToggleColorTransfer = {
                            appSettingsViewModel.setColorTransferEnabled(!isColorTransferEnabled)
                        },
                        onPhotoAspectResolved = { photoAspectRatio = it }
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (uiState.isUsbInitializing) {
        UsbInitializationOverlay(
            message = uiState.usbInitializationMessage?.resolve(context)
                ?: stringResource(R.string.camera_control_usb_initializing)
        )
    }

    // RAW 파일 제한 알림 표시
    uiState.rawFileRestriction?.let { restriction ->
        RawFileRestrictionNotification(
            restriction = restriction,
            onDismiss = { viewModel.clearRawFileRestriction() }
        )
    }

    // 필름↔색감 배타 스왑 안내 표시
    pipelineSwapMessage?.let { message ->
        PipelineSwapNotification(
            message = message,
            onDismiss = { pipelineSwapMessage = null }
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
            // 원본(수 MB~수십 MB)·썸네일 바이트를 컴포지션 밖 IO 에서 1회 로드한다.
            // 이전에는 뷰어 인자에서 매 recomposition 마다 원본 파일 전체를 readBytes() 했다(라이브뷰 중엔 매 프레임).
            // FullScreenPhotoViewer 내부 fullImageCache 는 키 없는 remember 로 최초 1회만 캡처하므로,
            // 바이트가 준비된 뒤에 뷰어를 구성한다(로딩 중에는 다크 플레이스홀더).
            val imageBytes by produceState<Pair<ByteArray?, ByteArray?>?>(
                initialValue = null,
                key1 = photo.id
            ) {
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    photo.getThumbnailData() to photo.getImageData()
                }
            }
            val loaded = imageBytes
            if (loaded == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Surface0),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Accent)
                }
            } else {
                FullScreenPhotoViewer(
                    photo = photo.toCameraPhoto(),
                    onDismiss = {
                        showFullScreenViewer = false
                        selectedPhoto = null
                    },
                    onPhotoChanged = { /* 단일 사진이므로 변경 없음 */ },
                    thumbnailData = loaded.first,
                    fullImageData = loaded.second,
                    isDownloadingFullImage = false,
                    onDownload = { /* 이미 다운로드됨, 아무 동작 안함 */ },
                    viewModel = null, // PhotoPreviewViewModel 없이 사용
                    hideDownloadButton = true, // 다운로드 버튼 숨김
                    onFilmEdit = { target ->
                        // own-media(API29+)는 uri 로만 접근 가능 → uri 우선, 없으면 기존 파일경로.
                        val uri = target.uri
                        if (uri != null) {
                            com.inik.camcon.presentation.ui.FilmEditorActivity.startForPhoto(
                                context, android.net.Uri.parse(uri)
                            )
                        } else {
                            com.inik.camcon.presentation.ui.FilmEditorActivity.startForPhoto(
                                context, target.path
                            )
                        }
                    },
                    isRawFile = appSettingsViewModel::isRawFile
                )
            }
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

    // AF 성공 등 1-shot 정보 메시지를 Snackbar로 표시 (에러 채널과 분리)
    val autoFocusCompletedMsg = stringResource(R.string.autofocus_completed)
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.infoMessage.collect { info ->
                val message = when (info) {
                    com.inik.camcon.presentation.viewmodel.state.InfoMessage.AutoFocusCompleted ->
                        autoFocusCompletedMsg
                }
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    // M5: 첫 실행 코치마크 — 카메라 컨트롤 활성 상태에서 1회 표시
    if (isCameraControlsEnabled && !hasSeenCaptureCoachmark) {
        com.inik.camcon.presentation.ui.screens.components.CaptureCoachmarkOverlay(
            onDismiss = { appSettingsViewModel.setHasSeenCaptureCoachmark(true) }
        )
    }

    LaunchedEffect(uiState.error) {
        val error = uiState.error
        // error 는 UiText? — 문자열 판정/표시 전에 resolve 로 현재 로케일 문자열을 얻는다.
        val errorText = error?.resolve(context)
        val isConnectionError = errorText?.contains("Could not find the requested device") == true
        if (isConnectionError) {
            // 아직 표시·처리하지 않은 새 에러일 때만 도움말 표시 (사용자가 닫은 동일 에러는 재오픈하지 않음)
            if (handledConnectionError != error) {
                handledConnectionError = error
                showConnectionHelpDialog = true
            }
        } else {
            // 에러가 사라지거나 다른 에러로 바뀌면 도움말을 닫고 처리 기록을 리셋해 재발 시 다시 표시되도록 함
            showConnectionHelpDialog = false
            handledConnectionError = null

            // 전용 UI(연결 도움말/USB 분리/PTP 타임아웃)가 없는 일반 에러는
            // 본문을 Snackbar로 노출하고 1-shot으로 소비한다.
            if (!errorText.isNullOrBlank() &&
                !uiState.connection.isUsbDisconnected &&
                !uiState.isPtpTimeout
            ) {
                snackbarHostState.showSnackbar(errorText)
                viewModel.clearError()
            }
        }
    }

    if (showConnectionHelpDialog) {
        CameraConnectionHelpDialog(
            onDismiss = { showConnectionHelpDialog = false },
            onRetry = {
                showConnectionHelpDialog = false
                // 사용자가 재시도를 요청했으므로 처리 기록을 비워, 재연결이 다시 실패하면 도움말이 다시 표시되게 함
                handledConnectionError = null
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
    isHistogramEnabled: Boolean = false,
    onToggleHistogram: () -> Unit = {},
    isFocusPeakingEnabled: Boolean = false,
    onToggleFocusPeaking: () -> Unit = {},
    liveViewQuality: LiveViewQuality = LiveViewQuality.BALANCED,
    onCycleLiveViewQuality: () -> Unit = {},
    onUnsupportedShootingMode: (com.inik.camcon.domain.model.ShootingMode) -> Unit = {},
    // CINE 파이프라인 패널 배선 (AppSettings 상태/토글)
    isFilmSimulationEnabled: Boolean = false,
    selectedFilmLutId: String = "",
    selectedFilmLutLocked: Boolean? = null,
    onToggleFilmSimulation: () -> Unit = {},
    onLongPressFilmSim: () -> Unit = {},
    isColorTransferEnabled: Boolean = false,
    colorTransferReferenceImagePath: String? = null,
    onToggleColorTransfer: () -> Unit = {},
    onPhotoAspectResolved: (Float) -> Unit = {}
) {
    val context = LocalContext.current

    // 방향·시스템바 제어는 CameraControlScreen 의 SSOT effect 로 이관했다.
    // 여기서 다시 걸면 AnimatedContent dispose 순서에 따라 서로 덮어써 방향이 어긋난다.

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

    val canEnterFullscreen = remember(uiState.isLiveViewActive, uiState.capturedPhotos.size) {
        val result = uiState.isLiveViewActive || uiState.capturedPhotos.isNotEmpty()
        LogcatManager.d(
            "CameraControl",
            "🔍 canEnterFullscreen 계산: isLiveViewActive=${uiState.isLiveViewActive}, capturedPhotos.size=${uiState.capturedPhotos.size}, result=$result"
        )
        result
    }

    // 백그라운드 자동 검색 armed 여부(auto_connect ON && 직전 카메라 존재). UI 근사 신호.
    val isAutoSearchArmed by viewModel.isAutoSearchArmed.collectAsStateWithLifecycle()

    // V2 StatusBar — 카메라 연결 상태 라벨
    val statusKind = when {
        uiState.isConnected && (uiState.isNativeCameraConnected || uiState.isPtpipConnected) -> StatusKind.Connected
        uiState.isUsbInitializing -> StatusKind.Connecting
        uiState.error != null -> StatusKind.Error
        // 연결 안 됨 + 자동 검색 armed → 백그라운드 검색 중 표시(에러/연결시도 없을 때만).
        isAutoSearchArmed -> StatusKind.Searching
        else -> StatusKind.Idle
    }
    val connectedLabel = stringResource(R.string.camera_connected)
    val connectingLabel = stringResource(R.string.connecting)
    val errorLabel = stringResource(R.string.error)
    val disconnectedLabel = stringResource(R.string.camera_disconnected)
    val searchingLabel = stringResource(R.string.camera_searching)
    val statusLabel = when (statusKind) {
        // CINE: 모델명은 계기판 바(TopControlsBar) 한 곳에만 표시(중복 제거) — 상태열은 연결 상태만.
        StatusKind.Connected -> connectedLabel
        StatusKind.Connecting -> connectingLabel
        StatusKind.Error -> errorLabel
        StatusKind.Searching -> searchingLabel
        StatusKind.Idle -> disconnectedLabel
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface0)
            .padding(contentPadding)
            .imePadding()
    ) {
        // CINE Hero — 이 화면의 존재 이유(= 지금까지 카메라에서 넘어온 컷 수)를 최상위 타이포 슬롯으로 앵커한다.
        // 위 행은 eyebrow(연결 상태 + 전송 진행 배지), 아래 행이 Hero 카운터.
        // DisplayNum(34sp Bold, tnum 내장)이라 자릿수가 늘어도 좌우 흔들림이 없다.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusIndicator(kind = statusKind, label = statusLabel)
                Spacer(modifier = Modifier.weight(1f))
                // 다운로드/처리 진행 카운트 배지 (요구 E7). 비활성 시 내부에서 early-return 으로 미표시.
                TransferProgressBadge(queue = uiState.capture.transferQueue)
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    text = uiState.capturedPhotos.size.toString(),
                    style = DisplayNum,
                    color = TextPrimaryV2
                )
                // 단위는 로케일별 한글/CJK 가 들어오므로 MicroLabel(라틴 대문자 트래킹) 대신 Micro 를 쓴다.
                Text(
                    text = stringResource(R.string.cc_hero_shots_unit),
                    style = Micro,
                    color = TextTertiary,
                    modifier = Modifier.padding(bottom = Spacing.xs)
                )
            }
        }

        val cameraStorageInfo by viewModel.cameraStorageInfo.collectAsStateWithLifecycle()
        val exposureCompensation by viewModel.exposureCompensation.collectAsStateWithLifecycle()

        TopControlsBar(
            uiState = uiState,
            cameraFeed = cameraFeed,
            onSettingsClick = { onShowBottomSheet() },
            storageInfo = cameraStorageInfo
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Surface0),
            contentAlignment = Alignment.Center
        ) {
            if (appSettings.isCameraControlsEnabled && appSettings.isLiveViewEnabled) {
                // ✅ 프레임/Bitmap 디코딩 수집은 이 최하위 스코프에서만 (IO 디스패처에서 처리됨).
                // 프레임레이트 recomposition 을 CameraPreviewArea 서브트리로 국한한다.
                val liveViewFrame by viewModel.liveViewFrame.collectAsStateWithLifecycle()
                val decodedBitmap by viewModel.decodedLiveViewBitmap.collectAsStateWithLifecycle()
                val histogramData by viewModel.histogramData.collectAsStateWithLifecycle()

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
                    onToggleGridOverlay = onToggleLiveViewGrid,
                    histogramData = histogramData,
                    isHistogramEnabled = isHistogramEnabled,
                    onToggleHistogram = onToggleHistogram,
                    isFocusPeakingEnabled = isFocusPeakingEnabled,
                    onToggleFocusPeaking = onToggleFocusPeaking,
                    currentSettings = uiState.cameraSettings,
                    liveViewQuality = liveViewQuality,
                    onCycleLiveViewQuality = onCycleLiveViewQuality,
                    // CINE: 노출 스트립은 모니터 아래 독립 행으로 이동. 시작/중지는 좌상단 오버레이 칩.
                    showInlineExposureStrip = false
                )
            } else {
                val enterFullscreenLabel = stringResource(R.string.camera_control_enter_fullscreen)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics {
                            customActions = listOf(
                                CustomAccessibilityAction(enterFullscreenLabel) {
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
                        emptyTextColor = TextSecondaryV2,
                        isRotated = false,
                        onDoubleClick = {
                            if (canEnterFullscreen) {
                                onEnterFullscreen()
                            }
                        },
                        // 전체화면 진입 전에 비율을 미리 확보해 두면 진입 순간 가로로 한 번
                        // 튀었다 돌아오는 현상이 없다(같은 사진을 여기서도 이미 그리고 있다).
                        onAspectResolved = onPhotoAspectResolved
                    )
                }
            }

            // 상시 "더블클릭으로 전체화면" 힌트 배지 제거(CINE): 1회성 CaptureCoachmarkOverlay 로 흡수.
            // 더블클릭 제스처와 a11y 커스텀 액션("전체화면 전환")은 위에서 그대로 유지된다.
        }

        // CINE 노출 스트립 — 모니터 '아래' 독립 행(목업 순서: 모니터 → 노출 → 파이프라인).
        // 라이브뷰 활성 중에만 표시(사용자 결정 2026-08-18): LV 가 꺼지거나 끊긴 뒤에도
        // 마지막 판독값이 남아 있으면 죽은 값(스테일)을 실측처럼 보여줘 혼란만 준다.
        // 6칼럼 균등(fullWidth) + 상하 헤어라인은 LiveViewExposureStrip 내부가 그린다.
        // 판독값이 하나도 없으면(초기/미연결) 내부에서 스스로 렌더를 생략한다.
        if (uiState.isLiveViewActive) {
            uiState.cameraSettings?.let { s ->
                LiveViewExposureStrip(
                    settings = s,
                    fullWidth = true,
                    modifier = Modifier.background(Surface0)
                )
            }
        }

        // CINE 하단 = 이미지 파이프라인 패널. 셔터 버튼·촬영 모드 행은 이 화면 UI에서만 제거
        // (CaptureControls/ShootingModeSelector 코드·촬영 로직·ViewModel 경로는 그대로 보존 — 물리셔터 전제).
        // 각진 사각 Surface1 + 상단 0.5dp 헤어라인(라운드 제거).
        Surface(
            color = Surface1,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                // 상단 헤어라인 (패널 구획) — TopControlsBar 와 동일하게 DividerLine.
                // Surface0 로 칠하면 Surface0 배경 위에 그려져 선이 보이지 않는다.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(StrokeWidth.hairline)
                        .background(DividerLine)
                )

                // ISO/셔터스피드/조리개/EV 조절 컨트롤 — 셔터/모드가 아니므로 유지.
                if (appSettings.isCameraControlsEnabled && uiState.isConnected) {
                    CameraSettingsControls(
                        currentSettings = uiState.cameraSettings,
                        capabilities = uiState.cameraCapabilities,
                        onSettingChange = { key, value ->
                            viewModel.updateCameraSetting(key, value)
                        },
                        isEnabled = uiState.isConnected && !uiState.isCapturing,
                        modifier = Modifier.padding(vertical = Padding.sm),
                        exposureCompensation = exposureCompensation,
                        onExposureCompensationChange = { value ->
                            viewModel.setExposureCompensation(value)
                        }
                    )
                }

                ImagePipelinePanel(
                    isFilmSimulationEnabled = isFilmSimulationEnabled,
                    selectedFilmLutId = selectedFilmLutId,
                    selectedFilmLutLocked = selectedFilmLutLocked,
                    onToggleFilmSimulation = onToggleFilmSimulation,
                    onLongPressFilmSim = onLongPressFilmSim,
                    isColorTransferEnabled = isColorTransferEnabled,
                    colorTransferReferenceImagePath = colorTransferReferenceImagePath,
                    onToggleColorTransfer = onToggleColorTransfer,
                    latestPhoto = uiState.capturedPhotos.lastOrNull(),
                    onLatestPhotoClick = onPhotoClick
                )
            }
        }
    }
}

/**
 * CINE 이미지 파이프라인 패널 (셔터/촬영 모드 대체).
 *
 * 좌측: FILM SIM · 색감전송 토글 칩(탭=ON/OFF, 길게=상세 화면 진입).
 *   - ON = 앰버 dot + 값(LUT명/레퍼런스 파일명), OFF = TextDisabled 디밍.
 *   - **FilmLutRepository.releaseGpu() 등 GPU 정리 호출 금지**(전역 싱글톤, memory 규약) — 토글은 순수 설정 변경만.
 * 우측: 최근 수신 피드 — 마지막 수신 사진의 미니 썸네일 + 파일명(모노), 없으면 "대기 중".
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImagePipelinePanel(
    isFilmSimulationEnabled: Boolean,
    selectedFilmLutId: String,
    selectedFilmLutLocked: Boolean?,
    onToggleFilmSimulation: () -> Unit,
    onLongPressFilmSim: () -> Unit,
    isColorTransferEnabled: Boolean,
    colorTransferReferenceImagePath: String?,
    onToggleColorTransfer: () -> Unit,
    latestPhoto: CapturedPhoto?,
    onLatestPhotoClick: (CapturedPhoto) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Padding.lg, vertical = Padding.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // FILM SIM — 탭 토글 / 길게 = 필름 에디터(LUT 선택)
            val filmValue = if (isFilmSimulationEnabled) {
                filmLutDisplayName(selectedFilmLutId)
                    ?: stringResource(R.string.pipeline_none)
            } else {
                stringResource(R.string.pipeline_off)
            }
            PipelineChip(
                label = stringResource(R.string.pipeline_film_sim),
                value = filmValue,
                isOn = isFilmSimulationEnabled,
                // 필름심 ON 인데 선택 LUT 이 잠겼으면 자동 적용이 스킵되므로 잠금 표기(ON+LUT명 혼란 방지).
                isLocked = isFilmSimulationEnabled && selectedFilmLutLocked == true,
                contentDescription = stringResource(R.string.cd_toggle_film_sim),
                onTap = onToggleFilmSimulation,
                // 롱프레스 = '기본 필름 선택'(select-only). 결과 소비는 CameraControlScreen 의 런처가 담당.
                onLongPress = onLongPressFilmSim
            )

            // 색감전송 — 탭 토글 / 길게 = 색감전송 설정(레퍼런스 선택)
            val ctValue = if (isColorTransferEnabled) {
                colorTransferReferenceImagePath
                    ?.substringAfterLast('/')
                    ?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.pipeline_none)
            } else {
                stringResource(R.string.pipeline_off)
            }
            PipelineChip(
                label = stringResource(R.string.pipeline_color_transfer),
                value = ctValue,
                isOn = isColorTransferEnabled,
                contentDescription = stringResource(R.string.cd_toggle_color_transfer),
                onTap = onToggleColorTransfer,
                onLongPress = {
                    context.startActivity(
                        Intent(context, com.inik.camcon.presentation.ui.ColorTransferSettingsActivity::class.java)
                    )
                }
            )
        }

        // 우측 최근 수신 피드
        RecentReceivedFeed(
            latestPhoto = latestPhoto,
            onClick = onLatestPhotoClick
        )
    }
}

/**
 * 파이프라인 토글 칩 — 탭 = ON/OFF, 길게 = 상세 진입.
 * ON = 앰버 엣지 + 앰버 dot + 값 하단 앰버 언더라인, OFF = 디밍.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PipelineChip(
    label: String,
    value: String,
    isOn: Boolean,
    contentDescription: String,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    isLocked: Boolean = false
) {
    // 누름 상태는 ripple 대신 surface tier 승강(투명 → Surface2) + 1px 앰버 엣지로 표현한다.
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = if (isPressed) Surface2 else Color.Transparent,
                shape = RoundedCornerShape(Radius.sm)
            )
            .border(
                width = StrokeWidth.hairline,
                color = when {
                    isPressed -> Accent
                    isOn -> Accent.copy(alpha = 0.6f)
                    else -> Surface3
                },
                shape = RoundedCornerShape(Radius.sm)
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onTap,
                onLongClick = onLongPress,
                onClickLabel = contentDescription
            )
            .padding(horizontal = Padding.md, vertical = Padding.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(
                        color = if (isOn) Accent else TextDisabled,
                        shape = CircleShape
                    )
            )
            Text(
                text = label,
                style = MicroLabel,
                color = if (isOn) TextTertiary else TextDisabled
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            if (isLocked) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = stringResource(R.string.fs_selected_film_locked_hint),
                    tint = TextTertiary,
                    modifier = Modifier.size(IconSize.sm)
                )
            }
            // 값은 라벨(MicroLabel 11sp)의 1.45배로 키워 위계를 색이 아닌 크기가 전담하게 한다.
            Text(
                text = value,
                style = MonoReadout,
                color = if (isOn) TextPrimaryV2 else TextDisabled,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 우측 최근 수신 피드 — 마지막 수신 사진의 미니 썸네일 + 파일명(모노). 없으면 "대기 중". */
@Composable
private fun RecentReceivedFeed(
    latestPhoto: CapturedPhoto?,
    onClick: (CapturedPhoto) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.width(64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        if (latestPhoto == null) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .border(StrokeWidth.hairline, DividerLine, RoundedCornerShape(Radius.sm)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Photo,
                    contentDescription = null,
                    tint = TextDisabled,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = stringResource(R.string.pipeline_recent_waiting),
                style = MonoMicro,
                color = TextTertiary,
                maxLines = 1
            )
        } else {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .border(StrokeWidth.hairline, DividerLine, RoundedCornerShape(Radius.sm))
                    .clickable { onClick(latestPhoto) },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(latestPhoto.thumbnailPath ?: latestPhoto.filePath)
                        .crossfade(200)
                        .memoryCacheKey(latestPhoto.id + "_pipe")
                        .scale(Scale.FIT)
                        .allowHardware(false)
                        .build(),
                    contentDescription = stringResource(R.string.camera_control_captured_photo),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Text(
                text = latestPhoto.filePath.substringAfterLast('/'),
                style = MonoMicro,
                color = TextSecondaryV2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * selectedFilmLutId(assets 상대 경로)에서 표시용 LUT 명을 파생한다.
 * 카탈로그 suspend 조회를 피하기 위해 파일명 basename 을 사람이 읽을 수 있는 형태로 변환한다.
 * 예: "luts/negative-new/kodak_portra_400.cube" → "KODAK PORTRA 400". 비어있으면 null.
 */
private fun filmLutDisplayName(id: String): String? {
    if (id.isBlank()) return null
    return id.substringAfterLast('/')
        .removeSuffix(".cube")
        .replace('_', ' ')
        .replace('-', ' ')
        .trim()
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { it.uppercase() }
        .takeIf { it.isNotBlank() }
}

/**
 * 전체화면 모드 레이아웃 - 분리된 컴포넌트들 사용
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun FullscreenCameraLayout(
    uiState: CameraUiState,
    cameraFeed: List<Camera>,
    viewModel: CameraViewModel,
    onExitFullscreen: () -> Unit,
    isLiveViewEnabled: Boolean,
    onGalleryClick: () -> Unit = {},
    isShutterSoundEnabled: Boolean = true,
    isLiveViewGridEnabled: Boolean = false,
    onToggleLiveViewGrid: () -> Unit = {},
    isHistogramEnabled: Boolean = false,
    onToggleHistogram: () -> Unit = {},
    isFocusPeakingEnabled: Boolean = false,
    onToggleFocusPeaking: () -> Unit = {},
    liveViewQuality: LiveViewQuality = LiveViewQuality.BALANCED,
    onCycleLiveViewQuality: () -> Unit = {},
    isRotated: Boolean = false,
    onToggleRotate: () -> Unit = {},
    onPhotoAspectResolved: (Float) -> Unit = {}
) {
    val context = LocalContext.current
    var showTimelapseDialog by rememberSaveable { mutableStateOf(false) }

    // 방향·시스템바 제어와 isRotated 는 CameraControlScreen 의 SSOT 로 이관했다.
    // isRotated 를 여기 두면 전체화면을 나갔다 올 때마다 180도 보정이 풀린다.

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface0)
    ) {
        // 메인 라이브뷰 또는 사진 뷰 영역
        if (isLiveViewEnabled && uiState.isLiveViewActive) {
            // ✅ 프레임/Bitmap 디코딩 수집은 이 최하위 스코프에서만 (IO 디스패처에서 처리됨).
            // 프레임레이트 recomposition 을 CameraPreviewArea 서브트리로 국한한다.
            val liveViewFrame by viewModel.liveViewFrame.collectAsStateWithLifecycle()
            val decodedBitmap by viewModel.decodedLiveViewBitmap.collectAsStateWithLifecycle()
            val histogramData by viewModel.histogramData.collectAsStateWithLifecycle()

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
                onToggleGridOverlay = onToggleLiveViewGrid,
                histogramData = histogramData,
                isHistogramEnabled = isHistogramEnabled,
                onToggleHistogram = onToggleHistogram,
                isFocusPeakingEnabled = isFocusPeakingEnabled,
                onToggleFocusPeaking = onToggleFocusPeaking,
                currentSettings = uiState.cameraSettings,
                inlineChromeVisible = false,
                rotated = isRotated
                // 탭-투-포커스 비활성화: Nikon Z8/Z9는 AF-영역 모드를 PTP로 설정할 수 없어
                // (벤더확장 미노출) changeafarea 좌표가 무시되고 좌상단에 park된다. onTapFocus 미전달(null)
                // → 라이브뷰 단일 탭은 무동작, 더블탭=전체화면 종료는 유지.
            )
        } else {
            val exitFullscreenLabel = stringResource(R.string.camera_control_exit_fullscreen)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics {
                        customActions = listOf(
                            CustomAccessibilityAction(exitFullscreenLabel) {
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
                    emptyTextColor = TextSecondaryV2,
                    isRotated = isRotated,
                    onDoubleClick = onExitFullscreen,
                    onAspectResolved = onPhotoAspectResolved
                )
            }
        }

        // 우측 슬림 컨트롤 레일 + 하단 가로 모드 칩 - 라이브뷰 활성 시
        if (isLiveViewEnabled && uiState.isLiveViewActive) {
            FullscreenControlPanel(
                captureState = uiState.capture,
                isConnected = uiState.isConnected,
                onCapture = viewModel::capturePhoto,
                onAutoFocus = viewModel::performAutoFocus,
                onShowTimelapseDialog = { showTimelapseDialog = true },
                onExitFullscreen = onExitFullscreen,
                onStopLiveView = viewModel::stopLiveView,
                isGridEnabled = isLiveViewGridEnabled,
                onToggleGrid = onToggleLiveViewGrid,
                isHistogramEnabled = isHistogramEnabled,
                onToggleHistogram = onToggleHistogram,
                isFocusPeakingEnabled = isFocusPeakingEnabled,
                onToggleFocusPeaking = onToggleFocusPeaking,
                onRotate = onToggleRotate,
                onGalleryClick = onGalleryClick,
                isShutterSoundEnabled = isShutterSoundEnabled,
                onStopTimelapse = viewModel::stopTimelapse,
                liveViewQuality = liveViewQuality,
                onCycleLiveViewQuality = onCycleLiveViewQuality,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    // 양방향 가로를 허용하면서 노치·펀치홀이 반대편으로도 오게 됐다.
                    .displayCutoutPadding()
                    .padding(end = Spacing.md, top = Spacing.xs, bottom = Spacing.xs)
            )

            // 촬영 모드는 하단 가로 칩으로 분리 (우측 레일 폭 회피)
            ShootingModeSelector(
                captureState = uiState.capture,
                isConnected = uiState.isConnected,
                cameraCapabilities = uiState.cameraCapabilities,
                onModeSelected = viewModel::setShootingMode,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .displayCutoutPadding()
                    .padding(bottom = Padding.lg, end = 112.dp)
            )
        } else if (uiState.capturedPhotos.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .displayCutoutPadding()
                    .padding(Padding.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Surface(
                    color = Surface2.copy(alpha = 0.9f),
                    shape = CircleShape
                ) {
                    IconButton(
                        onClick = onToggleRotate,
                        modifier = Modifier
                            .size(TouchTarget.xl)
                            .background(Surface2, CircleShape)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.RotateRight,
                            contentDescription = stringResource(R.string.cd_rotate_180),
                            tint = TextPrimaryV2,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Surface(
                    color = Surface2.copy(alpha = 0.9f),
                    shape = CircleShape
                ) {
                    IconButton(
                        onClick = onExitFullscreen,
                        modifier = Modifier
                            .size(TouchTarget.xl)
                            .background(ErrorV2.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.cd_exit_fullscreen),
                            tint = TextPrimaryV2,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }

        // 더블클릭 종료 텍스트 힌트 제거: ✕ 버튼이 명시적이고 더블클릭 제스처는 유지된다.
        // (좌하단 노출 스트립과의 겹침도 함께 해소)

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
 * 전체화면 우측 통합 컨트롤 도크 -- state+callback 패턴.
 *
 * 모든 컨트롤을 하나의 세로 Column(단일 열)에 그룹 간격 리듬으로 담는다. 단일 Column이라 요소가
 * 서로 겹치는 것이 구조적으로 불가능하고, 컴팩트 사이즈로 짧은 가로 화면(≈360dp)에도 클리핑 없이
 * 들어간다(합계 ≈339dp). 각 버튼은 자체 반투명 원형 배경으로 패널 없이도 또렷하다.
 * 그룹: [종료] · [뷰 토글: 그리드/히스토그램/포커스피킹] · [캡처: 갤러리/셔터/AF] · [보조: 중지/회전].
 * 빨강은 종료 하나뿐이며, 라이브뷰 중지는 중립색이다.
 */
@Composable
private fun FullscreenControlPanel(
    captureState: com.inik.camcon.presentation.viewmodel.CameraCaptureState,
    isConnected: Boolean,
    onCapture: () -> Unit,
    onAutoFocus: () -> Unit,
    onShowTimelapseDialog: () -> Unit,
    onExitFullscreen: () -> Unit,
    onStopLiveView: () -> Unit,
    isGridEnabled: Boolean,
    onToggleGrid: () -> Unit,
    isHistogramEnabled: Boolean,
    onToggleHistogram: () -> Unit,
    isFocusPeakingEnabled: Boolean,
    onToggleFocusPeaking: () -> Unit,
    onRotate: (() -> Unit)? = null,
    onGalleryClick: () -> Unit = {},
    isShutterSoundEnabled: Boolean = true,
    onStopTimelapse: () -> Unit = {},
    liveViewQuality: LiveViewQuality = LiveViewQuality.BALANCED,
    onCycleLiveViewQuality: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 그룹 구분은 선이 아니라 간격 리듬으로 한다. 도크는 단일 surface tier 위에 떠 있어
    // 구분선 양쪽 tier 가 동일하므로 선이 정보를 만들지 못한다.
    // 그룹 간 Spacing.md(12dp) : 그룹 내부 Spacing.xs(4dp) = 3:1.
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1) 종료 — 유일한 빨강
        DockCircleButton(
            icon = Icons.Default.Close,
            contentDescription = stringResource(R.string.cd_exit_fullscreen),
            onClick = onExitFullscreen,
            background = ErrorV2.copy(alpha = 0.85f),
            size = TouchTarget.lg,
            iconSize = IconSize.lg
        )

        // 2) 뷰 토글 (가로 미니행) — 그리드 / 히스토그램 / 포커스 피킹
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            DockCircleButton(
                icon = if (isGridEnabled) Icons.Default.GridOn else Icons.Default.GridOff,
                contentDescription = stringResource(R.string.liveview_grid_toggle),
                onClick = onToggleGrid,
                background = Surface2.copy(alpha = if (isGridEnabled) 0.9f else 0.7f),
                tint = if (isGridEnabled) MaterialTheme.colorScheme.primary else TextPrimaryV2,
                size = TouchTarget.min,
                iconSize = IconSize.md
            )
            DockCircleButton(
                icon = Icons.Default.BarChart,
                contentDescription = stringResource(R.string.liveview_histogram_toggle),
                onClick = onToggleHistogram,
                background = Surface2.copy(alpha = if (isHistogramEnabled) 0.9f else 0.7f),
                tint = if (isHistogramEnabled) MaterialTheme.colorScheme.primary else TextPrimaryV2,
                size = TouchTarget.min,
                iconSize = IconSize.md
            )
            DockCircleButton(
                icon = Icons.Default.CenterFocusWeak,
                contentDescription = stringResource(R.string.liveview_focus_peaking_toggle),
                onClick = onToggleFocusPeaking,
                background = Surface2.copy(alpha = if (isFocusPeakingEnabled) 0.9f else 0.7f),
                tint = if (isFocusPeakingEnabled) MaterialTheme.colorScheme.primary else TextPrimaryV2,
                size = TouchTarget.min,
                iconSize = IconSize.md
            )
        }

        // 2-1) 화질 순환 (단독 행) — 탭 시 SPEED→BALANCED→QUALITY 순환. 현재 단계 아이콘 + accent tint.
        // 가로 폭 압박을 피하려 뷰 토글행에 합치지 않고 단독 버튼으로 둔다(단일 Column 겹침 불가).
        DockCircleButton(
            icon = liveViewQuality.icon(),
            contentDescription = stringResource(
                R.string.cd_cycle_liveview_quality,
                stringResource(liveViewQuality.shortLabelRes())
            ),
            onClick = onCycleLiveViewQuality,
            background = Surface2.copy(alpha = 0.8f),
            tint = MaterialTheme.colorScheme.primary,
            size = TouchTarget.min,
            iconSize = IconSize.md
        )

        // 3) 캡처 (컴팩트) — 갤러리 / 셔터 / AF
        CaptureControls(
            captureState = captureState,
            isConnected = isConnected,
            onCapture = onCapture,
            onAutoFocus = onAutoFocus,
            onShowTimelapseDialog = onShowTimelapseDialog,
            isVertical = true,
            compact = true,
            onGalleryClick = onGalleryClick,
            isShutterSoundEnabled = isShutterSoundEnabled,
            isTimelapseRunning = captureState.shootingMode == com.inik.camcon.domain.model.ShootingMode.TIMELAPSE && captureState.isCapturing,
            onStopTimelapse = onStopTimelapse,
            // 라이브뷰 중 앱 셔터는 노출하지 않는다(사용자 결정 2026-08-20). 이 도크는
            // 라이브뷰 활성 시에만 뜨므로 여기서 끄면 라이브뷰 구간 앱 셔터가 사라진다.
            // 촬영 로직·ViewModel 경로는 그대로 보존 — 추후 되살릴 때 이 한 줄만 되돌리면 된다.
            showShutter = false
        )

        // 4) 보조 (가로 미니행) — 라이브뷰 중지(중립색) / 180° 회전
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            DockCircleButton(
                icon = Icons.Default.Stop,
                contentDescription = stringResource(R.string.cd_stop_live_view),
                onClick = onStopLiveView,
                background = Surface2.copy(alpha = 0.85f),
                enabled = isConnected,
                size = TouchTarget.min,
                iconSize = IconSize.md
            )
            DockCircleButton(
                icon = Icons.AutoMirrored.Filled.RotateRight,
                contentDescription = stringResource(R.string.cd_rotate_180),
                onClick = { onRotate?.invoke() },
                enabled = onRotate != null,
                background = Surface2.copy(alpha = 0.85f),
                tint = if (onRotate != null) TextPrimaryV2 else TextSecondaryV2,
                size = TouchTarget.min,
                iconSize = IconSize.md
            )
        }
    }
}

/**
 * 전체화면 도크용 원형 아이콘 버튼 — 자체 반투명 배경으로 패널 없이도 또렷하다.
 */
@Composable
private fun DockCircleButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    background: Color,
    modifier: Modifier = Modifier,
    tint: Color = TextPrimaryV2,
    size: Dp = TouchTarget.lg,
    iconSize: Dp = IconSize.lg,
    enabled: Boolean = true
) {
    Surface(
        color = background,
        shape = CircleShape,
        modifier = modifier.size(size)
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(size)
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(iconSize)
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
        shape = RoundedCornerShape(Radius.md),
        color = Surface2,
        tonalElevation = Elevation.low,
        shadowElevation = Elevation.low
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
                            // RAW(NEF 등)는 Coil 이 EXIF orientation 을 안 씌워 세로컷이 눕는다 → RAW 만 방향 보정.
                            com.inik.camcon.presentation.ui.util.RawExifRotationTransformation
                                .forPathOrNull(photo.filePath)?.let { transformations(it) }
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
                        .background(Surface0.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Accent,
                        modifier = Modifier.size(IconSize.lg),
                        strokeWidth = StrokeWidth.thick
                    )
                }
            }

            // 파일 크기 표시 (하단)
            if (photo.size > 0) {
                Surface(
                    color = Surface0.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(Radius.sm),
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    // 변동 수치(파일 용량)이므로 탭형 모노 — 자릿수가 바뀌어도 배지 폭이 흔들리지 않는다.
                    Text(
                        text = sizeText,
                        color = TextPrimaryV2,
                        style = MonoMicro,
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
    emptyTextColor: Color = TextSecondaryV2,
    isRotated: Boolean = false,
    onDoubleClick: (() -> Unit)? = null,
    onAspectResolved: (Float) -> Unit = {}
) {
    // capturedPhotos 는 LRU 1000장 캡이 있어 size 가 1000에서 고정되면 remember(size) 가 최신 사진을
    // 영영 갱신하지 못한다(동결 회귀). lastOrNull() 은 O(1) 이므로 remember 없이 매 recomposition 직접 읽는다.
    val latestPhoto = capturedPhotos.lastOrNull()

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
                            // RAW(NEF 등)는 Coil 이 EXIF orientation 을 안 씌워 세로컷이 눕는다 → RAW 만 방향 보정.
                            com.inik.camcon.presentation.ui.util.RawExifRotationTransformation
                                .forPathOrNull(photo.filePath)?.let { transformations(it) }
                        }
                        .build(),
                    contentDescription = stringResource(R.string.camera_control_photo),
                    // painter.intrinsicSize 는 쓰면 안 된다. crossfade(200) 동안 CrossfadePainter 가
                    // 이전/새 이미지의 축별 max 를 돌려줘 200ms 간 정사각형에 가까운 허구 값이 나온다.
                    // result.drawable 은 JPG EXIF 자동회전과 RawExifRotationTransformation(RAW) 이
                    // 모두 적용된 '실제로 그려질' 비트맵이라 그대로 표시 비율이다.
                    onSuccess = { state ->
                        val w = state.result.drawable.intrinsicWidth
                        val h = state.result.drawable.intrinsicHeight
                        if (w > 0 && h > 0) onAspectResolved(w.toFloat() / h.toFloat())
                    },
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

        // 설정은 카메라 능력에 따라 동적으로 로드될 예정
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

/**
 * RAW 파일 제한 알림 컴포넌트 (슬라이드 인/아웃 + 페이드)
 */
@Composable
private fun RawFileRestrictionNotification(
    restriction: RawFileRestriction,
    onDismiss: () -> Unit
) {
    // 내부 visible 상태로 종료 애니메이션을 재생한 뒤 onDismiss 호출
    var visible by remember(restriction.timestamp) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 진입 시 애니메이션 트리거 + 5초 후 자동 소멸
    LaunchedEffect(restriction.timestamp) {
        visible = true
        kotlinx.coroutines.delay(5000L)
        visible = false
        kotlinx.coroutines.delay(260L) // exit 애니메이션 완료 대기
        onDismiss()
    }

    // 화면 상단에 표시 — V2 ToastV2 (Error kind)
    AnimatedVisibility(
        visible = visible,
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
                // 컴팩트 강제: 태블릿에서 풀폭으로 여러 줄 감기며 라이브뷰를 가리지 않도록
                // 폭 상한 + 2줄 말줄임. 경고 성격은 아이콘·컬러바가 전달하므로 타이틀 중복 제거.
                // 구독 업그레이드 유도는 추후 지원 — CTA 없이 안내만, 탭하면 조기 닫기.
                ToastV2(
                    message = "${restriction.fileName}: ${restriction.message}",
                    kind = StatusKind.Error,
                    leadingIcon = Icons.Outlined.WarningAmber,
                    maxLines = 2,
                    modifier = Modifier
                        .widthIn(max = 400.dp)
                        .clickable {
                            scope.launch {
                                visible = false
                                kotlinx.coroutines.delay(260L)
                                onDismiss()
                            }
                        }
                )
            }
        }
    }
}

/**
 * 필름↔색감 배타 스왑 안내 토스트 (슬라이드 인/아웃 + 페이드).
 * 다운로드 제한 토스트와 동일한 상단 오버레이 관례를 따르되, 경고가 아닌 안내이므로 Idle kind + 2초 자동 소멸.
 */
@Composable
private fun PipelineSwapNotification(
    message: String,
    onDismiss: () -> Unit
) {
    var visible by remember(message) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(message) {
        visible = true
        kotlinx.coroutines.delay(2000L)
        visible = false
        kotlinx.coroutines.delay(260L) // exit 애니메이션 완료 대기
        onDismiss()
    }

    AnimatedVisibility(
        visible = visible,
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
                    message = message,
                    kind = StatusKind.Idle,
                    maxLines = 2,
                    modifier = Modifier
                        .widthIn(max = 400.dp)
                        .clickable {
                            scope.launch {
                                visible = false
                                kotlinx.coroutines.delay(260L)
                                onDismiss()
                            }
                        }
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
                .background(Surface0),
            contentAlignment = Alignment.Center
        ) {
            Text(
                stringResource(R.string.camera_control_preview_screen),
                color = TextPrimaryV2,
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
                iso = "640",
                shutterSpeed = "1/160",
                aperture = "f/3.5",
                whiteBalance = "5600K",
                focusMode = "AF-C",
                exposureCompensation = "-1/3"
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
                    filePath = "/storage/emulated/0/CamCon/DSC_4417.NEF",
                    thumbnailPath = "/storage/emulated/0/CamCon/.thumb/DSC_4417.jpg",
                    captureTime = System.currentTimeMillis(),
                    cameraModel = "NIKON Z 8",
                    settings = null,
                    size = 51_384_912L,
                    width = 8256,
                    height = 5504
                ),
                CapturedPhoto(
                    id = "2",
                    filePath = "/storage/emulated/0/CamCon/DSC_4418.NEF",
                    thumbnailPath = "/storage/emulated/0/CamCon/.thumb/DSC_4418.jpg",
                    captureTime = System.currentTimeMillis(),
                    cameraModel = "NIKON Z 8",
                    settings = null,
                    size = 48_902_144L,
                    width = 5504,
                    height = 8256
                ),
                CapturedPhoto(
                    id = "3",
                    filePath = "/storage/emulated/0/CamCon/DSC_4421.JPG",
                    thumbnailPath = "/storage/emulated/0/CamCon/.thumb/DSC_4421.jpg",
                    captureTime = System.currentTimeMillis(),
                    cameraModel = "NIKON Z 8",
                    settings = null,
                    size = 9_137_664L,
                    width = 8256,
                    height = 5504
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
                .background(Surface0),
            contentAlignment = Alignment.Center
        ) {
            Text(
                stringResource(R.string.camera_control_fullscreen_panel),
                color = TextPrimaryV2,
                textAlign = TextAlign.Center
            )
        }
    }
}
