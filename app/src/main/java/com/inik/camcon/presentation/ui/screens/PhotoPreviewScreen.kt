package com.inik.camcon.presentation.ui.screens

// Multi-select feature: Required imports
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
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inik.camcon.R
import com.inik.camcon.presentation.theme.Accent
import com.inik.camcon.presentation.theme.BodySmall
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.Caption
import com.inik.camcon.presentation.theme.HeadingM
import com.inik.camcon.presentation.theme.IconSize
import com.inik.camcon.presentation.theme.LocalWindowSizeClass
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.TextPrimaryV2
import com.inik.camcon.presentation.theme.TextSecondaryV2
import com.inik.camcon.presentation.theme.TextTertiary
import com.inik.camcon.presentation.ui.components.v2.EmptyState
import com.inik.camcon.presentation.ui.components.v2.FilterChipV2
import com.inik.camcon.presentation.ui.components.v2.IconButtonV2
import com.inik.camcon.presentation.ui.components.v2.PrimaryButton
import com.inik.camcon.presentation.ui.components.v2.ProgressBarV2
import com.inik.camcon.presentation.ui.components.v2.SecondaryButton
import com.inik.camcon.presentation.ui.components.v2.Section
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
 *  - Column: StatusBar(연결 상태) → Section(제목/필터/새로고침) → 그리드/빈/오류 상태 → BottomActionBar(다중 선택)
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
    val isLoadingPhotos by viewModel.isLoadingPhotos.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMorePhotos.collectAsStateWithLifecycle()
    val hasNextPage by viewModel.hasNextPage.collectAsStateWithLifecycle()
    val currentFilter by viewModel.currentFilter.collectAsStateWithLifecycle()
    val currentPage by viewModel.currentPage.collectAsStateWithLifecycle()
    val totalPages by viewModel.totalPages.collectAsStateWithLifecycle()
    val isMultiSelectMode by viewModel.isMultiSelectMode.collectAsStateWithLifecycle()
    val selectedPhotos by viewModel.selectedPhotos.collectAsStateWithLifecycle()
    val isPtpipConnected by cameraViewModel.isPtpipConnected.collectAsStateWithLifecycle()

    val pullToRefreshState = rememberPullToRefreshState()

    // 멀티 선택 모드에서 뒤로가기 처리
    BackHandler(enabled = isMultiSelectMode) {
        viewModel.exitMultiSelectMode()
    }

    DisposableEffect(Unit) {
        LogcatManager.d("PhotoPreviewScreen", "사진 미리보기 탭 진입 - 이벤트 리스너 관리 시작")
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

            // === Header (Section) ===
            if (isMultiSelectMode) {
                MultiSelectHeader(selectedCount = selectedPhotos.size)
            } else {
                PhotoListHeader(
                    photoCount = photos.size,
                    currentPage = currentPage,
                    totalPages = totalPages,
                    currentFilter = currentFilter,
                    canAccessRaw = canAccessRawTier(uiState.currentTier),
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

                    isPtpipConnected -> PtpipBlockOverlay()
                    !uiState.isConnected -> CameraDisconnectedState()
                    isLoadingPhotos && photos.isEmpty() -> PhotoSkeletonGrid()
                    photos.isEmpty() -> EmptyPhotosV2()
                    else -> PhotoGrid(
                        photos = photos,
                        isLoadingMore = isLoadingMore,
                        hasNextPage = hasNextPage,
                        isMultiSelectMode = isMultiSelectMode,
                        selectedPhotos = selectedPhotos.toSet(),
                        viewModel = viewModel
                    )
                }
            }

            // === Bottom Action Bar (다중 선택 모드 시) ===
            if (isMultiSelectMode) {
                MultiSelectBottomBar(
                    hasSelection = selectedPhotos.isNotEmpty(),
                    onSelectAll = { viewModel.selectAllPhotos() },
                    onDeselectAll = { viewModel.deselectAllPhotos() },
                    onDownload = { viewModel.downloadSelectedPhotos() },
                    onCancel = { viewModel.exitMultiSelectMode() }
                )
            }
        }
    }

    // === FullScreen Viewer 오버레이 ===
    uiState.selectedPhoto?.let { photo ->
        val fullImageCache by viewModel.fullImageCache.collectAsStateWithLifecycle()
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
            thumbnailData = viewModel.getThumbnail(photo.path),
            fullImageData = fullImageCache[photo.path],
            isDownloadingFullImage = downloadingImages.contains(photo.path),
            onDownload = {
                // RAW 게이팅은 ValidateImageFormatUseCase 단일 지점.
                viewModel.downloadPhoto(photo)
            },
            viewModel = viewModel,
            localPhotos = if (photos.any { File(it.path).exists() }) photos else null
        )

        BackHandler(enabled = !isMultiSelectMode) {
            viewModel.selectPhoto(null)
        }
    }

    // === Error Toast (SharedFlow 수집) ===
    var showError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is PhotoPreviewUiEvent.ShowError -> {
                    showError = event.message
                }
            }
        }
    }

    showError?.let { error ->
        ErrorToastOverlay(
            error = error,
            onRetry = {
                showError = null
                viewModel.loadCameraPhotos()
            },
            onDismiss = { showError = null }
        )
    }
}

/* ----------------- Helpers / Components ----------------- */

private fun canAccessRawTier(tier: com.inik.camcon.domain.model.SubscriptionTier): Boolean {
    return tier == com.inik.camcon.domain.model.SubscriptionTier.PRO ||
            tier == com.inik.camcon.domain.model.SubscriptionTier.REFERRER ||
            tier == com.inik.camcon.domain.model.SubscriptionTier.ADMIN
}

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
            .height(32.dp)
            .padding(horizontal = Spacing.base),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusIndicator(kind = kind, label = label)
    }
}

/**
 * 메인 헤더 — Section(title) + trailing(필터/카운트/새로고침).
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

    Section(
        title = stringResource(R.string.camera_photo_list),
        modifier = Modifier.padding(horizontal = Spacing.base, vertical = Spacing.sm),
        trailing = {
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
    ) {
        // 사진 개수 / 페이지 표시
        if (photoCount > 0) {
            Text(
                text = "${photoCount}장의 사진" +
                        if (totalPages > 0) " (페이지 ${currentPage + 1}/${totalPages})" else "",
                style = Caption,
                color = TextSecondaryV2
            )
            Spacer(Modifier.height(Spacing.sm))
        }

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
 * 멀티 선택 모드 헤더 — Section title에 선택 개수.
 */
@Composable
private fun MultiSelectHeader(selectedCount: Int) {
    Section(
        title = stringResource(R.string.photo_preview_selected_count, selectedCount),
        modifier = Modifier.padding(horizontal = Spacing.base, vertical = Spacing.sm)
    ) {
        Spacer(Modifier.height(Spacing.xs))
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
        // Featured (full width)
        SkeletonLoader(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        )
        Spacer(Modifier.height(Spacing.sm))
        // Grid rows
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
                        thumbnailData = viewModel.getThumbnail(firstPhoto.path),
                        fullImageCache = fullImageCache,
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
                thumbnailData = viewModel.getThumbnail(photo.path),
                fullImageCache = fullImageCache,
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
 * PTPIP 모드에서 사진 미리보기 차단 — V2 EmptyState 톤.
 */
@Composable
private fun PtpipBlockOverlay() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = null,
                modifier = Modifier.size(IconSize.xl),
                tint = Accent
            )
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = stringResource(R.string.photo_preview_wifi_connected),
                style = HeadingM,
                color = TextPrimaryV2,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = stringResource(R.string.photo_preview_wifi_block_message),
                style = BodySmall,
                color = TextSecondaryV2,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = stringResource(R.string.photo_preview_use_photo_preview),
                style = BodySmall,
                color = Accent,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = stringResource(R.string.photo_preview_switch_usb),
                style = Caption,
                color = TextSecondaryV2,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = stringResource(R.string.photo_preview_use_camera_control),
                style = Caption,
                color = Accent,
                textAlign = TextAlign.Center
            )
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
                photoCount = 42,
                currentPage = 1,
                totalPages = 3,
                currentFilter = FileTypeFilter.JPG,
                canAccessRaw = true,
                onFilterChange = {},
                onRefresh = {},
                onForceLoadNext = {}
            )
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
                error = "사진을 불러오는 중 오류가 발생했습니다.",
                onRetry = {},
                onDismiss = {}
            )
        }
    }
}
