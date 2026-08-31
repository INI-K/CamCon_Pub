package com.inik.camcon.presentation.ui.screens

// 멀티 선택 기능: 필요한 import
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.inik.camcon.R
import com.inik.camcon.presentation.theme.Accent
import com.inik.camcon.presentation.theme.BodySmall
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.CameraSpec
import com.inik.camcon.presentation.theme.Caption
import com.inik.camcon.presentation.theme.DisplayNum
import com.inik.camcon.presentation.theme.HeadingM
import com.inik.camcon.presentation.theme.LocalWindowSizeClass
import com.inik.camcon.presentation.theme.MicroLabel
import com.inik.camcon.presentation.theme.MonoNumeric
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.TextPrimaryV2
import com.inik.camcon.presentation.theme.TextSecondaryV2
import com.inik.camcon.presentation.theme.TextTertiary
import com.inik.camcon.presentation.viewmodel.photo.CardBrowseState
import com.inik.camcon.presentation.viewmodel.photo.CardBrowseError
import com.inik.camcon.presentation.ui.components.v2.AppDialog
import com.inik.camcon.presentation.ui.components.v2.EmptyState
import com.inik.camcon.presentation.ui.components.v2.FilterChipV2
import com.inik.camcon.presentation.ui.components.v2.IconButtonV2
import com.inik.camcon.presentation.ui.components.v2.PrimaryButton
import com.inik.camcon.presentation.ui.components.v2.ProgressBarV2
import com.inik.camcon.presentation.ui.components.v2.SecondaryButton
import com.inik.camcon.presentation.ui.components.v2.SkeletonLoader
import com.inik.camcon.presentation.ui.components.v2.StatusIndicator
import com.inik.camcon.presentation.ui.components.v2.StatusKind
import com.inik.camcon.presentation.ui.components.v2.SurfaceV2
import com.inik.camcon.presentation.ui.components.v2.ToastV2
import com.inik.camcon.presentation.ui.screens.components.FeaturedPhotoThumbnail
import com.inik.camcon.presentation.ui.screens.components.FluidPhotoThumbnail
import com.inik.camcon.presentation.ui.screens.components.FullScreenPhotoViewer
import com.inik.camcon.presentation.ui.screens.components.UsbInitializationOverlay
import com.inik.camcon.presentation.viewmodel.CameraViewModel
import com.inik.camcon.presentation.viewmodel.PhotoPreviewUiEvent
import com.inik.camcon.presentation.viewmodel.PhotoPreviewViewModel
import com.inik.camcon.presentation.viewmodel.photo.FileTypeFilter
import com.inik.camcon.utils.LogcatManager
import kotlinx.coroutines.delay
import java.io.File

/**
 * 카메라에서 촬영한 사진들을 미리보기로 보여주는 메인 화면 (V2 디자인 시스템).
 *
 * 구조:
 *  - SurfaceV2 tier=0 외곽
 *  - Column: StatusBar(연결 상태) → Header(사진 수 Hero/필터/새로고침) → 그리드/빈/오류 상태 → BottomActionBar(다중 선택)
 *  - 오버레이: UsbInitializationOverlay, FullScreenPhotoViewer, ToastV2(에러)
 *
 * 보존:
 *  - PhotoPreviewViewModel / CameraViewModel API 무변경
 *  - StaggeredGrid 분기(Compact=2 / Medium=3 / Expanded=4)
 *  - 다중 선택, 풀스크린, PullToRefresh, 필터(All/JPG/RAW), 빈/로딩/오류 상태, i18n
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoPreviewScreen(
    viewModel: PhotoPreviewViewModel = hiltViewModel(),
    cameraViewModel: CameraViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val isStorageUnsupported by viewModel.isStorageUnsupported.collectAsStateWithLifecycle()
    val cardBrowseState by viewModel.cardBrowseState.collectAsStateWithLifecycle()
    val cardBrowseError by viewModel.cardBrowseError.collectAsStateWithLifecycle()
    val showThumbnailLimitNotice by viewModel.showThumbnailLimitNotice.collectAsStateWithLifecycle()
    val isLoadingPhotos by viewModel.isLoadingPhotos.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMorePhotos.collectAsStateWithLifecycle()
    val hasNextPage by viewModel.hasNextPage.collectAsStateWithLifecycle()
    val currentFilter by viewModel.currentFilter.collectAsStateWithLifecycle()
    val currentPage by viewModel.currentPage.collectAsStateWithLifecycle()
    val totalPages by viewModel.totalPages.collectAsStateWithLifecycle()
    val isMultiSelectMode by viewModel.isMultiSelectMode.collectAsStateWithLifecycle()
    val selectedPhotos by viewModel.selectedPhotos.collectAsStateWithLifecycle()
    val multiDownloadProgress by viewModel.multiDownloadProgress.collectAsStateWithLifecycle()
    val isPtpipConnected by cameraViewModel.isPtpipConnected.collectAsStateWithLifecycle()
    val cameraCapabilities by cameraViewModel.cameraCapabilities.collectAsStateWithLifecycle()

    val pullToRefreshState = rememberPullToRefreshState()

    // 멀티 선택 모드에서 뒤로가기 처리
    BackHandler(enabled = isMultiSelectMode) {
        viewModel.exitMultiSelectMode()
    }

    DisposableEffect(Unit) {
        LogcatManager.d("PhotoPreviewScreen", "사진 미리보기 탭 진입 - 이벤트 리스너 관리 시작")
        // 카드 탐색 구간 진입 통지 — 니콘 앱 모드 해제는 진입마다 필요하다(재진입 포함).
        viewModel.onTabEnter()
        onDispose {
            LogcatManager.d("PhotoPreviewScreen", "사진 미리보기 탭 이탈 - 이벤트 리스너 재시작 신호")
            viewModel.onTabExit()
        }
    }

    SurfaceV2(
        modifier = Modifier.fillMaxSize(),
        tier = 0
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
        ) {
            // === StatusBar: 연결 상태 표시 ===
            StatusBarRow(
                isPtpipConnected = isPtpipConnected,
                isUsbConnected = uiState.isConnected
            )

            // === Header (Hero readout) ===
            if (isMultiSelectMode) {
                MultiSelectHeader(selectedCount = selectedPhotos.size)
            } else {
                PhotoListHeader(
                    photoCount = photos.size,
                    currentPage = currentPage,
                    totalPages = totalPages,
                    currentFilter = currentFilter,
                    canAccessRaw = uiState.canAccessRawFormats,
                    onFilterChange = { viewModel.changeFileTypeFilter(it) },
                    onRefresh = { viewModel.loadCameraPhotos() },
                    onForceLoadNext = { viewModel.forceLoadNextPage() }
                )
            }

            // === Content with PullToRefresh ===
            PullToRefreshBox(
                isRefreshing = isLoadingPhotos,
                onRefresh = {
                    LogcatManager.d("PhotoPreviewScreen", "Pull to refresh 트리거")
                    viewModel.loadCameraPhotos()
                },
                state = pullToRefreshState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    uiState.isInitializing -> {
                        UsbInitializationOverlay(
                            message = stringResource(R.string.photo_preview_event_initializing),
                            progress = null
                        )
                    }

                    !uiState.isConnected && !isPtpipConnected -> CameraDisconnectedState()
                    isLoadingPhotos && photos.isEmpty() -> PhotoSkeletonGrid()
                    // 카드 탐색 미지원 세션(Sony PC리모트) — 일반 빈 상태 대신 이유+해결 경로 상시 안내.
                    // 카드 보기로 전환했는데 카드가 비어 있는 경우도 여기로 온다. 그때
                    // isStorageUnsupported 는 이미 풀려 있으므로 전환 상태를 함께 본다.
                    photos.isEmpty() &&
                            (isStorageUnsupported || cardBrowseState != CardBrowseState.IDLE) ->
                        CardBrowsingUnsupportedState(
                            cardBrowseState = cardBrowseState,
                            cardBrowseError = cardBrowseError,
                            onEnterCardBrowse = viewModel::enterCardBrowse,
                            onExitCardBrowse = viewModel::exitCardBrowse
                        )

                    photos.isEmpty() -> EmptyPhotosV2()
                    else -> Column(modifier = Modifier.fillMaxSize()) {
                        // 카드 보기 중에는 그 사실과 빠져나갈 길을 그리드 위에 항상 띄운다.
                        // 이게 없으면 사진이 뜬 순간 CardBrowsingUnsupportedState 가 사라져
                        // 촬영 모드로 돌아갈 방법이 화면에서 없어진다.
                        if (cardBrowseState != CardBrowseState.IDLE) {
                            CardBrowseBanner(
                                cardBrowseState = cardBrowseState,
                                onExitCardBrowse = viewModel::exitCardBrowse
                            )
                        }
                        PhotoGrid(
                            photos = photos,
                            isLoadingMore = isLoadingMore,
                            hasNextPage = hasNextPage,
                            isMultiSelectMode = isMultiSelectMode,
                            selectedPhotos = selectedPhotos,
                            viewModel = viewModel
                        )
                    }
                }
            }

            // === 다중선택 다운로드 진행 표시 (필수1) ===
            if (multiDownloadProgress.inProgress) {
                MultiDownloadProgressRow(
                    completed = multiDownloadProgress.completed,
                    total = multiDownloadProgress.total
                )
            }

            // === Bottom Action Bar (다중 선택 모드 시) ===
            if (isMultiSelectMode) {
                MultiSelectBottomBar(
                    hasSelection = selectedPhotos.isNotEmpty() &&
                            !multiDownloadProgress.inProgress,
                    onSelectAll = { viewModel.selectAllPhotos() },
                    onDeselectAll = { viewModel.deselectAllPhotos() },
                    onDownload = { viewModel.downloadSelectedPhotos() },
                    onCancel = { viewModel.exitMultiSelectMode() }
                )
            }
        }
    }

    // === 썸네일 제한 안내 ===
    // 2025년 신형 소니는 GetThumb 을 광고하면서도 실제로는 지원하지 않아 미리보기가 비어 보인다.
    // 고장으로 오해하지 않도록 카드 보기에 들어갈 때 세션당 한 번 알린다.
    if (showThumbnailLimitNotice) {
        AppDialog(
            onDismissRequest = { viewModel.dismissThumbnailLimitNotice() },
            title = { Text(stringResource(R.string.preview_thumbnail_unsupported_title)) },
            text = { Text(stringResource(R.string.preview_thumbnail_unsupported_desc)) },
            confirmButton = {
                PrimaryButton(
                    text = stringResource(R.string.ok),
                    onClick = { viewModel.dismissThumbnailLimitNotice() }
                )
            }
        )
    }

    // === FullScreen Viewer 오버레이 ===
    uiState.selectedPhoto?.let { photo ->
        val fullImageCache by viewModel.fullImageCache.collectAsStateWithLifecycle()
        // StateFlow 구독 — 일반 함수(getThumbnail)로 읽으면 캐시가 채워져도
        // recomposition이 없어 썸네일이 영영 placeholder로 남는다(2026-07-03 실측).
        val thumbnailCache by viewModel.thumbnailCache.collectAsStateWithLifecycle()
        val downloadingImages by viewModel.downloadingImages.collectAsStateWithLifecycle()

        LaunchedEffect(photo.path) {
            val isLocalFile = File(photo.path).exists()
            if (isLocalFile) {
                LogcatManager.d("PhotoPreviewScreen", "로컬 파일이므로 다운로드 건너뛰기: ${photo.name}")
                return@LaunchedEffect
            }

            LogcatManager.d(
                "PhotoPreviewScreen",
                "ImageViewer 진입 - 최적화된 다운로드: ${photo.name}"
            )

            if (!downloadingImages.contains(photo.path) && !fullImageCache.containsKey(photo.path)) {
                viewModel.quickPreloadCurrentImage(photo)

                var waitCount = 0
                while (!fullImageCache.containsKey(photo.path) &&
                    downloadingImages.contains(photo.path) &&
                    waitCount < 20
                ) {
                    delay(100)
                    waitCount++
                }

                delay(1000)
                viewModel.preloadAdjacentImages(photo, photos)
            }
        }

        // 로컬 사진 목록 판정은 photos 리스트 identity 가 바뀔 때만(페이지 로드/새로고침) 1회 계산한다.
        // 이전에는 캐시(fullImageCache 등) 갱신마다 뷰어 블록이 recompose 되며 매번 photos 전체를
        // File.exists() 스캔(메인스레드 stat N회)했다. remember(photos) 로 그 재스캔을 제거한다.
        val localPhotos = remember(photos) {
            if (photos.any { File(it.path).exists() }) photos else null
        }

        // H7-A — 삭제 액션 게이팅: 카메라 capability + 구독 티어
        val canDelete = (cameraCapabilities?.canDeleteFiles == true) &&
                uiState.canAccessRawFormats

        val viewerContext = androidx.compose.ui.platform.LocalContext.current

        FullScreenPhotoViewer(
            photo = photo,
            onDismiss = {
                LogcatManager.d("PhotoPreviewScreen", "ImageViewer 닫힘")
                viewModel.selectPhoto(null)
            },
            onPhotoChanged = { newPhoto ->
                if (newPhoto.path != photo.path) {
                    LogcatManager.d(
                        "PhotoPreviewScreen",
                        "ImageViewer - 사진 변경: ${photo.name} → ${newPhoto.name}"
                    )
                    viewModel.selectPhoto(newPhoto)
                }
            },
            thumbnailData = thumbnailCache[photo.path],
            fullImageData = fullImageCache[photo.path],
            isDownloadingFullImage = downloadingImages.contains(photo.path),
            onDownload = {
                // 명시적 다운로드 버튼 — 성공/실패 토스트 + FREE 고지(필수1/4).
                // RAW 게이팅은 ValidateImageFormatUseCase 단일 지점.
                viewModel.downloadPhotoExplicit(photo)
            },
            viewModel = viewModel,
            localPhotos = localPhotos,
            onDeleteRequest = if (canDelete) {
                { target -> viewModel.deletePhoto(target) }
            } else null,
            onFilmEdit = { target ->
                // own-media(API29+)는 uri 로만 접근 가능 → uri 우선, 없으면 기존 파일경로.
                val uri = target.uri
                if (uri != null) {
                    com.inik.camcon.presentation.ui.FilmEditorActivity.startForPhoto(
                        viewerContext, android.net.Uri.parse(uri)
                    )
                } else {
                    com.inik.camcon.presentation.ui.FilmEditorActivity.startForPhoto(
                        viewerContext, target.path
                    )
                }
            },
            isRawFile = viewModel::isRawFile
        )

        BackHandler(enabled = !isMultiSelectMode) {
            viewModel.selectPhoto(null)
        }
    }

    // === Toast / 안내 오버레이 (SharedFlow 수집) ===
    val context = androidx.compose.ui.platform.LocalContext.current
    var showError by remember { mutableStateOf<String?>(null) }
    var showInfo by remember { mutableStateOf<String?>(null) }
    var showFreeNotice by remember { mutableStateOf<String?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.uiEvent.collect { event ->
                when (event) {
                    is PhotoPreviewUiEvent.ShowError -> showError = event.message
                    is PhotoPreviewUiEvent.ShowInfo -> showInfo = event.message
                    is PhotoPreviewUiEvent.ShowFreeTierNotice -> showFreeNotice = event.message
                }
            }
        }
    }

    // 성공/안내 토스트는 일정 시간 후 자동 사라짐.
    LaunchedEffect(showInfo) {
        if (showInfo != null) {
            delay(2500)
            showInfo = null
        }
    }
    LaunchedEffect(showFreeNotice) {
        if (showFreeNotice != null) {
            delay(5000)
            showFreeNotice = null
        }
    }

    val lastFailedDownload by viewModel.lastFailedDownload.collectAsStateWithLifecycle()

    // 우선순위: 에러(재시도) > FREE 안내(업그레이드) > 정보 토스트.
    when {
        showError != null -> ErrorToastOverlay(
            error = showError!!,
            onRetry = {
                showError = null
                // M12 — 단일 사진 다운로드 실패면 그 사진만 재시도, 아니면 전체 새로고침
                if (lastFailedDownload != null) {
                    viewModel.retryDownload(lastFailedDownload)
                } else {
                    viewModel.loadCameraPhotos()
                }
            },
            onDismiss = { showError = null }
        )

        showFreeNotice != null -> FreeTierNoticeOverlay(
            message = showFreeNotice!!,
            onUpgrade = {
                showFreeNotice = null
                com.inik.camcon.presentation.ui.SubscriptionActivity.start(context)
            },
            onDismiss = { showFreeNotice = null }
        )

        showInfo != null -> InfoToastOverlay(message = showInfo!!)
    }
}

/* ----------------- Helpers / Components ----------------- */

/**
 * Hero 수치(34sp) 옆 eyebrow 라벨을 광학 베이스라인에 맞추는 인셋.
 * Compose 에는 Row 안의 서로 다른 폰트 크기를 베이스라인 정렬해 주는 토큰이 없어 고정 인셋으로 맞춘다.
 */
private val HeroBaselineInset = 6.dp

/** 장문 토스트가 Expanded(태블릿 4열) 폭에서 한 줄로 늘어지지 않도록 하는 가독 폭 상한. */
private val ReadableTextMaxWidth = 480.dp

/**
 * 상단 32dp StatusBar — 연결 상태 표시 (USB / PTPIP / 미연결).
 */
@Composable
private fun StatusBarRow(
    isPtpipConnected: Boolean,
    isUsbConnected: Boolean
) {
    val wifiLabel = stringResource(R.string.photo_preview_wifi_connected)
    val notConnectedLabel = stringResource(R.string.photo_preview_camera_not_connected)
    val (kind, label) = when {
        isPtpipConnected -> StatusKind.Connected to wifiLabel
        isUsbConnected -> StatusKind.Connected to "USB"
        else -> StatusKind.Idle to notConnectedLabel
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CameraSpec.statusBarHeight)
            .padding(horizontal = Spacing.base),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusIndicator(kind = kind, label = label)
    }
}

/**
 * 메인 헤더 — Hero readout(사진 개수) + eyebrow + 필터/새로고침.
 *
 * 이 화면의 존재 이유는 "카메라에 사진이 몇 장 있고 지금 몇 페이지를 보고 있는가"다.
 * 따라서 photoCount 를 [DisplayNum](34sp tnum) 히어로로 올리고, 기존 최대 슬롯이던
 * Section 제목(HeadingL 20sp)은 [MicroLabel] eyebrow 로 강등한다.
 */
@Composable
private fun PhotoListHeader(
    photoCount: Int,
    currentPage: Int,
    totalPages: Int,
    currentFilter: FileTypeFilter,
    canAccessRaw: Boolean,
    onFilterChange: (FileTypeFilter) -> Unit,
    onRefresh: () -> Unit,
    onForceLoadNext: () -> Unit
) {
    var lastClickTime by remember { mutableStateOf(0L) }

    // 스크린리더에는 히어로 분해 조각이 아니라 로컬라이즈된 문장을 그대로 읽힌다.
    val countText = pluralStringResource(
        R.plurals.gallery_v2_photo_count,
        photoCount,
        photoCount
    )
    val countA11yText = if (totalPages > 0) {
        stringResource(
            R.string.gallery_v2_photo_count_with_page,
            countText,
            currentPage + 1,
            totalPages
        )
    } else {
        countText
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.base, vertical = Spacing.sm)
    ) {
        // eyebrow(강등된 제목) + 새로고침
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.photo_grid_v3_eyebrow),
                style = MicroLabel,
                color = TextTertiary
            )
            Spacer(Modifier.weight(1f))
            IconButtonV2(
                icon = Icons.Default.Refresh,
                contentDescription = stringResource(R.string.cd_refresh),
                onClick = {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastClickTime < 1000) {
                        LogcatManager.d("PhotoPreviewScreen", "더블클릭 감지 - 강제 로딩 테스트")
                        onForceLoadNext()
                    } else {
                        onRefresh()
                    }
                    lastClickTime = currentTime
                }
            )
        }

        // === Hero readout: 사진 개수(34sp tnum) + 페이지(mono) ===
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) { contentDescription = countA11yText },
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = photoCount.toString(),
                style = DisplayNum,
                color = TextPrimaryV2
            )
            Spacer(Modifier.width(Spacing.xs))
            Text(
                text = stringResource(R.string.photo_grid_v3_photos_unit),
                style = MicroLabel,
                color = TextTertiary,
                modifier = Modifier.padding(bottom = HeroBaselineInset)
            )
            Spacer(Modifier.weight(1f))
            if (totalPages > 0) {
                Text(
                    text = stringResource(R.string.photo_grid_v3_page_label),
                    style = MicroLabel,
                    color = TextTertiary,
                    modifier = Modifier.padding(bottom = HeroBaselineInset)
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = "${currentPage + 1}/$totalPages",
                    style = MonoNumeric,
                    color = TextSecondaryV2,
                    modifier = Modifier.padding(bottom = HeroBaselineInset)
                )
            }
        }

        Spacer(Modifier.height(Spacing.md))

        // 필터 라벨 + Chip 행
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                text = stringResource(R.string.photo_preview_filter),
                style = BodySmall,
                color = TextSecondaryV2
            )
            FilterChipV2(
                text = "ALL",
                selected = currentFilter == FileTypeFilter.ALL,
                onClick = { onFilterChange(FileTypeFilter.ALL) }
            )
            FilterChipV2(
                text = "RAW",
                selected = currentFilter == FileTypeFilter.RAW,
                onClick = {
                    // RAW 게이팅은 ViewModel 측 ValidateImageFormatUseCase 단일 지점에서 처리됨.
                    onFilterChange(FileTypeFilter.RAW)
                },
                leadingIcon = if (!canAccessRaw) Icons.Default.Lock else null
            )
            FilterChipV2(
                text = "JPG",
                selected = currentFilter == FileTypeFilter.JPG,
                onClick = { onFilterChange(FileTypeFilter.JPG) }
            )
        }
    }
}

/**
 * 멀티 선택 모드 헤더 — 선택 개수를 Accent 히어로로.
 *
 * Section 을 쓰지 않는다. Section 은 제목 뒤에 항상 [Spacing.lg] 를 넣는데
 * 여기 content 는 비어 있어 헤더-그리드 사이에 죽은 여백만 남았다.
 */
@Composable
private fun MultiSelectHeader(selectedCount: Int) {
    val a11yText = stringResource(R.string.photo_preview_selected_count, selectedCount)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.base, vertical = Spacing.sm)
            .semantics(mergeDescendants = true) { contentDescription = a11yText },
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = selectedCount.toString(),
            style = DisplayNum,
            color = Accent
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            text = stringResource(R.string.photo_grid_v3_selected_unit),
            style = MicroLabel,
            color = TextTertiary,
            modifier = Modifier.padding(bottom = HeroBaselineInset)
        )
    }
}

/**
 * 멀티 선택 모드 — 하단 액션 바.
 * Primary(다운로드) + Secondary(전체 선택/해제/취소).
 */
@Composable
private fun MultiSelectBottomBar(
    hasSelection: Boolean,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit
) {
    SurfaceV2(tier = 1, border = true, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.base, vertical = Spacing.md)
        ) {
            // 전체 선택 / 해제 / 취소 (Secondary 라인)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                SecondaryButton(
                    text = stringResource(R.string.server_photos_select_all),
                    onClick = onSelectAll,
                    leadingIcon = Icons.Default.SelectAll,
                    modifier = Modifier.weight(1f)
                )
                SecondaryButton(
                    text = stringResource(R.string.server_photos_deselect_all),
                    onClick = onDeselectAll,
                    leadingIcon = Icons.Default.Close,
                    modifier = Modifier.weight(1f)
                )
                SecondaryButton(
                    text = stringResource(R.string.cancel),
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            // 다운로드 (Primary)
            PrimaryButton(
                text = stringResource(R.string.fullscreen_viewer_download),
                onClick = onDownload,
                leadingIcon = Icons.Default.Download,
                enabled = hasSelection,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 카메라 미연결 상태 (V2 EmptyState).
 */
@Composable
private fun CameraDisconnectedState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        EmptyState(
            icon = Icons.Outlined.CameraAlt,
            title = stringResource(R.string.photo_preview_camera_not_connected),
            description = stringResource(R.string.photo_preview_connect_usb)
        )
    }
}

/**
 * 카드 탐색 미지원 세션 안내 (Sony PC리모트: 원격 중 카드 접근이 펌웨어 설계상 불가).
 * 토스트는 놓치기 쉬워 빈 화면 자리에 이유와 해결 경로(USB+MTP)를 상시 표시한다.
 */
@Composable
private fun CardBrowsingUnsupportedState(
    cardBrowseState: CardBrowseState,
    cardBrowseError: CardBrowseError?,
    onEnterCardBrowse: () -> Unit,
    onExitCardBrowse: () -> Unit
) {
    // 자동 진입은 하지 않는다 — 들어가면 촬영·라이브뷰가 멈추므로 사용자가 알고 골라야 한다.
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = Spacing.lg)
        ) {
            EmptyState(
                icon = Icons.Outlined.PhotoLibrary,
                title = stringResource(R.string.preview_card_browse_unsupported_title),
                description = stringResource(
                    when (cardBrowseState) {
                        CardBrowseState.STUCK -> R.string.preview_card_browse_stuck
                        CardBrowseState.ENTERING -> R.string.preview_card_browse_entering
                        CardBrowseState.LEAVING -> R.string.preview_card_browse_leaving
                        CardBrowseState.ACTIVE -> R.string.preview_card_browse_active
                        CardBrowseState.IDLE ->
                            if (cardBrowseError == CardBrowseError.ENTER_FAILED) {
                                R.string.preview_card_browse_enter_failed
                            } else {
                                R.string.preview_card_browse_available_desc
                            }
                    }
                )
            )
            Spacer(modifier = Modifier.height(Spacing.lg))
            when (cardBrowseState) {
                // 전환 중에는 조작을 막는다. 커맨드 큐를 점유하는 구간이다.
                CardBrowseState.ENTERING, CardBrowseState.LEAVING -> Unit

                CardBrowseState.ACTIVE -> SecondaryButton(
                    text = stringResource(R.string.preview_card_browse_exit),
                    onClick = onExitCardBrowse
                )

                // 갇힌 상태에서 조용히 IDLE로 돌리지 않는다. 촬영이 계속 막혀 있으므로
                // 사용자가 재시도하거나 카메라를 껐다 켜야 한다는 것을 알아야 한다.
                CardBrowseState.STUCK -> PrimaryButton(
                    text = stringResource(R.string.preview_card_browse_retry_exit),
                    onClick = onExitCardBrowse
                )

                CardBrowseState.IDLE -> PrimaryButton(
                    text = stringResource(R.string.preview_card_browse_enter),
                    onClick = onEnterCardBrowse
                )
            }
        }
    }
}

/**
 * 카드 보기 중임을 알리고 빠져나갈 길을 항상 제공하는 배너.
 *
 * 사진이 뜨고 나면 [CardBrowsingUnsupportedState] 는 그려지지 않는다. 이 배너가 없으면
 * 카드 보기가 켜져 있다는 사실도, 촬영 모드로 돌아가는 버튼도 화면에서 사라진다.
 */
@Composable
private fun CardBrowseBanner(
    cardBrowseState: CardBrowseState,
    onExitCardBrowse: () -> Unit
) {
    SurfaceV2(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        tier = 2
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(
                    when (cardBrowseState) {
                        CardBrowseState.LEAVING -> R.string.preview_card_browse_leaving
                        CardBrowseState.STUCK -> R.string.preview_card_browse_stuck
                        else -> R.string.preview_card_browse_active
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            // 전환 중(LEAVING)에는 버튼을 내린다. 커맨드 큐를 점유하는 구간이라 조작을 막는다.
            if (cardBrowseState == CardBrowseState.ACTIVE || cardBrowseState == CardBrowseState.STUCK) {
                Spacer(modifier = Modifier.width(Spacing.sm))
                SecondaryButton(
                    text = stringResource(R.string.preview_card_browse_exit),
                    onClick = onExitCardBrowse
                )
            }
        }
    }
}

/**
 * 사진 0건 (V2 EmptyState).
 */
@Composable
private fun EmptyPhotosV2() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        EmptyState(
            icon = Icons.Outlined.PhotoLibrary,
            title = stringResource(R.string.no_photos),
            description = stringResource(R.string.connect_camera_and_capture)
        )
    }
}

/**
 * 로딩 — V2 SkeletonLoader 그리드 + ProgressBar(indeterminate).
 */
@Composable
private fun PhotoSkeletonGrid() {
    val widthSizeClass = LocalWindowSizeClass.current.widthSizeClass
    val cols = when (widthSizeClass) {
        WindowWidthSizeClass.Expanded -> 4
        WindowWidthSizeClass.Medium -> 3
        else -> 2
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.base)
    ) {
        ProgressBarV2(progress = null)
        Spacer(Modifier.height(Spacing.md))
        // Featured (전체 너비)
        SkeletonLoader(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        )
        Spacer(Modifier.height(Spacing.sm))
        // 그리드 행
        repeat(3) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                repeat(cols) {
                    SkeletonLoader(
                        modifier = Modifier
                            .weight(1f)
                            .height(120.dp)
                    )
                }
            }
            Spacer(Modifier.height(Spacing.sm))
        }
    }
}

/**
 * 사진 그리드 — StaggeredGrid 분기 보존(Compact=2 / Medium=3 / Expanded=4).
 */
@Composable
private fun PhotoGrid(
    photos: List<com.inik.camcon.domain.model.CameraPhoto>,
    isLoadingMore: Boolean,
    hasNextPage: Boolean,
    isMultiSelectMode: Boolean,
    selectedPhotos: Set<String>,
    viewModel: PhotoPreviewViewModel
) {
    val lazyGridState = rememberLazyStaggeredGridState()
    val fullImageCache by viewModel.fullImageCache.collectAsStateWithLifecycle()
    // StateFlow 구독 — getThumbnail 함수 호출로는 캐시 갱신 시 recomposition이 없다.
    val thumbnailCache by viewModel.thumbnailCache.collectAsStateWithLifecycle()

    val widthSizeClass = LocalWindowSizeClass.current.widthSizeClass
    val gridColumns = when (widthSizeClass) {
        WindowWidthSizeClass.Expanded -> 4
        WindowWidthSizeClass.Medium -> 3
        else -> 2
    }

    // 무한 스크롤
    LaunchedEffect(lazyGridState) {
        snapshotFlow {
            val layoutInfo = lazyGridState.layoutInfo
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            visibleItemsInfo.lastOrNull()?.index ?: -1
        }
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex >= 0 && photos.isNotEmpty()) {
                    viewModel.onPhotoIndexReached(lastVisibleIndex)
                }
            }
    }

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(gridColumns),
        state = lazyGridState,
        contentPadding = PaddingValues(Spacing.base),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalItemSpacing = Spacing.sm,
        modifier = Modifier.fillMaxSize()
    ) {
        // Featured 첫 사진 (멀티선택 모드 아닐 때만)
        photos.firstOrNull()?.let { firstPhoto ->
            if (!isMultiSelectMode) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    FeaturedPhotoThumbnail(
                        photo = firstPhoto,
                        thumbnailData = thumbnailCache[firstPhoto.path],
                        fullImageData = fullImageCache[firstPhoto.path],
                        onClick = { viewModel.selectPhoto(firstPhoto) }
                    )
                }
            }
        }

        // 나머지
        items(
            items = if (isMultiSelectMode) photos else photos.drop(1),
            key = { photo -> photo.path },
            contentType = { "photo_thumbnail" }
        ) { photo ->
            FluidPhotoThumbnail(
                photo = photo,
                thumbnailData = thumbnailCache[photo.path],
                fullImageData = fullImageCache[photo.path],
                onClick = {
                    if (isMultiSelectMode) {
                        viewModel.togglePhotoSelection(photo.path)
                    } else {
                        viewModel.selectPhoto(photo)
                    }
                },
                onLongClick = {
                    if (!isMultiSelectMode) {
                        viewModel.startMultiSelectMode(photo.path)
                    }
                },
                isSelected = selectedPhotos.contains(photo.path),
                isMultiSelectMode = isMultiSelectMode
            )
        }

        if (isLoadingMore && photos.isNotEmpty()) {
            item(span = StaggeredGridItemSpan.FullLine) {
                LoadMoreIndicatorV2()
            }
        } else if (!hasNextPage && photos.isNotEmpty() && !isLoadingMore) {
            item(span = StaggeredGridItemSpan.FullLine) {
                EndOfListMessage(photoCount = photos.size)
            }
        }
    }
}

/**
 * 추가 로딩 인디케이터 — V2 ProgressBar.
 */
@Composable
private fun LoadMoreIndicatorV2() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.base),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ProgressBarV2(progress = null, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = stringResource(R.string.photo_preview_loading_more),
            style = Caption,
            color = TextSecondaryV2
        )
    }
}

/**
 * 마지막 페이지 도달 메시지.
 */
@Composable
private fun EndOfListMessage(photoCount: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.base),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.photo_preview_all_loaded, photoCount),
            style = Caption,
            color = TextTertiary,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 에러 표시 — V2 Toast 오버레이 + 재시도 버튼.
 */
@Composable
private fun ErrorToastOverlay(
    error: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = ReadableTextMaxWidth)
                .fillMaxWidth()
                .padding(Spacing.base),
            horizontalAlignment = Alignment.End
        ) {
            ToastV2(
                message = error,
                kind = StatusKind.Error,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SecondaryButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss
                )
                PrimaryButton(
                    text = stringResource(R.string.server_photos_retry),
                    onClick = onRetry
                )
            }
        }
    }
}

/**
 * 다중선택 다운로드 진행 표시 (필수1) — "n/m 다운로드 중" + indeterminate 바.
 */
@Composable
private fun MultiDownloadProgressRow(
    completed: Int,
    total: Int
) {
    SurfaceV2(tier = 1, border = true, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.base, vertical = Spacing.sm)
        ) {
            // 라벨과 수치를 분리한다. 완료 수가 9→10 으로 넘어갈 때 비례폭 텍스트는
            // 폭이 흔들리므로 수치만 MonoNumeric(tnum) 으로 우측 고정한다.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.photo_grid_v3_downloading_label),
                    style = Caption,
                    color = TextSecondaryV2
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "$completed/$total",
                    style = MonoNumeric,
                    color = TextPrimaryV2
                )
            }
            Spacer(Modifier.height(Spacing.xs))
            ProgressBarV2(progress = null, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * 정보/성공 토스트 오버레이 (필수1) — 하단, 재시도 버튼 없음.
 */
@Composable
private fun InfoToastOverlay(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        ToastV2(
            message = message,
            kind = StatusKind.Connected,
            modifier = Modifier
                .widthIn(max = ReadableTextMaxWidth)
                .fillMaxWidth()
                .padding(Spacing.base)
        )
    }
}

/**
 * FREE 티어 2000px 축소 사전 고지 오버레이 (필수4) — 업그레이드 CTA + 닫기.
 */
@Composable
private fun FreeTierNoticeOverlay(
    message: String,
    onUpgrade: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = ReadableTextMaxWidth)
                .fillMaxWidth()
                .padding(Spacing.base),
            horizontalAlignment = Alignment.End
        ) {
            ToastV2(
                message = message,
                kind = StatusKind.Idle,
                leadingIcon = Icons.Default.Lock,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SecondaryButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss
                )
                PrimaryButton(
                    text = stringResource(R.string.gallery_v2_upgrade),
                    onClick = onUpgrade
                )
            }
        }
    }
}

/* ----------------- Previews ----------------- */

@Preview(showBackground = true, backgroundColor = 0xFF1A1A1A)
@Composable
private fun PhotoListHeader_NoPhotos_Preview() {
    CamConTheme {
        SurfaceV2(tier = 0) {
            PhotoListHeader(
                photoCount = 0,
                currentPage = 0,
                totalPages = 0,
                currentFilter = FileTypeFilter.JPG,
                canAccessRaw = false,
                onFilterChange = {},
                onRefresh = {},
                onForceLoadNext = {}
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A1A1A)
@Composable
private fun PhotoListHeader_WithPhotos_Preview() {
    CamConTheme {
        SurfaceV2(tier = 0) {
            PhotoListHeader(
                photoCount = 247,
                currentPage = 1,
                totalPages = 13,
                currentFilter = FileTypeFilter.RAW,
                canAccessRaw = false,
                onFilterChange = {},
                onRefresh = {},
                onForceLoadNext = {}
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A1A1A)
@Composable
private fun MultiSelectHeaderPreview() {
    CamConTheme {
        SurfaceV2(tier = 0) {
            MultiSelectHeader(selectedCount = 18)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A1A1A)
@Composable
private fun MultiSelectBottomBarPreview() {
    CamConTheme {
        SurfaceV2(tier = 0) {
            MultiSelectBottomBar(
                hasSelection = true,
                onSelectAll = {},
                onDeselectAll = {},
                onDownload = {},
                onCancel = {}
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A1A1A)
@Composable
private fun SkeletonGridPreview() {
    CamConTheme {
        SurfaceV2(tier = 0) {
            PhotoSkeletonGrid()
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A1A1A)
@Composable
private fun ErrorToastOverlayPreview() {
    CamConTheme {
        SurfaceV2(tier = 0) {
            ErrorToastOverlay(
                error = "PTP timeout while listing /store_00010001/DCIM/103ND850 (0x2019)",
                onRetry = {},
                onDismiss = {}
            )
        }
    }
}
