package com.inik.camcon.presentation.ui.screens

// Multi-select feature: Required imports
import android.util.Log
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inik.camcon.R
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.ui.screens.components.EmptyPhotoState
import com.inik.camcon.presentation.ui.screens.components.FluidPhotoThumbnail
import com.inik.camcon.presentation.ui.screens.components.FeaturedPhotoThumbnail
import com.inik.camcon.presentation.ui.screens.components.FullScreenPhotoViewer
import com.inik.camcon.presentation.ui.screens.components.UsbInitializationOverlay
import com.inik.camcon.presentation.viewmodel.PhotoPreviewViewModel
import com.inik.camcon.presentation.viewmodel.CameraViewModel
import com.inik.camcon.presentation.viewmodel.photo.FileTypeFilter
import com.inik.camcon.data.datasource.local.ThemeMode
import kotlinx.coroutines.delay
import java.io.File

/**
 * 카메라에서 촬영한 사진들을 미리보기로 보여주는 메인 화면
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun PhotoPreviewScreen(
    viewModel: PhotoPreviewViewModel = hiltViewModel(),
    cameraViewModel: CameraViewModel = hiltViewModel()
) {
    Log.d("PhotoPreviewScreen", "=== PhotoPreviewScreen 컴포저블 시작 ===")

    val uiState by viewModel.uiState.collectAsState()
    val photos by viewModel.photos.collectAsState()
    val isLoadingPhotos by viewModel.isLoadingPhotos.collectAsState()
    val isLoadingMore by viewModel.isLoadingMorePhotos.collectAsState()
    val hasNextPage by viewModel.hasNextPage.collectAsState()
    val currentFilter by viewModel.currentFilter.collectAsState()
    val currentPage by viewModel.currentPage.collectAsState()
    val totalPages by viewModel.totalPages.collectAsState()
    val isMultiSelectMode by viewModel.isMultiSelectMode.collectAsState()
    val selectedPhotos by viewModel.selectedPhotos.collectAsState()
    val isPtpipConnected by cameraViewModel.isPtpipConnected.collectAsState()

    Log.d("PhotoPreviewScreen", "현재 UI 상태:")
    Log.d("PhotoPreviewScreen", "  - isConnected: ${uiState.isConnected}")
    Log.d("PhotoPreviewScreen", "  - isLoading: ${isLoadingPhotos}")
    Log.d("PhotoPreviewScreen", "  - photos.size: ${photos.size}")
    Log.d("PhotoPreviewScreen", "  - error: ${uiState.error}")

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isLoadingPhotos,
        onRefresh = {
            Log.d("PhotoPreviewScreen", "Pull to refresh 트리거")
            viewModel.loadCameraPhotos()
        }
    )

    // 멀티 선택 모드에서 뒤로가기 처리
    BackHandler(enabled = isMultiSelectMode) {
        viewModel.exitMultiSelectMode()
    }

    DisposableEffect(Unit) {
        Log.d("PhotoPreviewScreen", "📸 사진 미리보기 탭 진입 - 이벤트 리스너 관리 시작")

        onDispose {
            Log.d("PhotoPreviewScreen", "📸 사진 미리보기 탭 이탈 - 이벤트 리스너 재시작 신호")

            // ViewModel에 탭 이탈을 알려서 이벤트 리스너를 재시작하도록 함
            viewModel.onTabExit()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
            .padding(horizontal = 16.dp) // 좌우 마진 추가
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 24.dp) // 상단 마진 증가 (16dp → 24dp)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
        ) {
            // 상단 타이틀 영역 (모던한 디자인)
            if (isMultiSelectMode) {
                MultiSelectActionBar(
                    selectedCount = selectedPhotos.size,
                    onSelectAll = { viewModel.selectAllPhotos() },
                    onDeselectAll = { viewModel.deselectAllPhotos() },
                    onDownload = { viewModel.downloadSelectedPhotos() },
                    onCancel = { viewModel.exitMultiSelectMode() }
                )
            } else {
                ModernHeader(
                    photoCount = photos.size,
                    currentPage = currentPage,
                    totalPages = totalPages,
                    onRefresh = { viewModel.loadCameraPhotos() },
                    fileTypeFilter = currentFilter,
                    onFilterChange = { filter -> viewModel.changeFileTypeFilter(filter) },
                    viewModel = viewModel
                )
            }

            // 카메라 이벤트 초기화 블록 오버레이 표시
            if (uiState.isInitializing) {
                UsbInitializationOverlay(
                    message = "카메라 이벤트 초기화 중...",
                    progress = null
                )
                return@Column // UI 상호작용 완전 차단 (오버레이만 보임)
            }

            // 메인 콘텐츠
            when {
                isPtpipConnected -> {
                    // PTPIP 연결 시 사진 미리보기 차단
                    PtpipBlockOverlay()
                }

                !uiState.isConnected -> {
                    CameraDisconnectedState()
                }

                isLoadingPhotos && photos.isEmpty() -> {
                    LoadingIndicator()
                }

                photos.isEmpty() -> {
                    EmptyPhotoState()
                }

                else -> {
                    PhotoGrid(
                        uiState = uiState,
                        photos = photos,
                        isLoadingMore = isLoadingMore,
                        hasNextPage = hasNextPage,
                        isMultiSelectMode = isMultiSelectMode,
                        selectedPhotos = selectedPhotos.toSet(), // Change here
                        viewModel = viewModel
                    )
                }
            }
        }

        // Pull to refresh 인디케이터 - 정상 동작 복원
        PullRefreshIndicator(
            refreshing = isLoadingPhotos,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        )
    }

    // 전체화면 사진 뷰어
    uiState.selectedPhoto?.let { photo ->
        // fullImageCache와 downloadingImages 상태 관찰
        val fullImageCache by viewModel.fullImageCache.collectAsState()
        val downloadingImages by viewModel.downloadingImages.collectAsState()

        // 선택된 사진의 실제 파일 다운로드 시작 (한 번만 실행, photo.path가 변경될 때만)
        LaunchedEffect(photo.path) {
            // 로컬 파일인지 확인
            val isLocalFile = File(photo.path).exists()

            if (isLocalFile) {
                Log.d("PhotoPreviewScreen", "로컬 파일이므로 다운로드 건너뛰기: ${photo.name}")
                return@LaunchedEffect
            }

            Log.d(
                "PhotoPreviewScreen",
                "ImageViewer 진입 - 최적화된 다운로드: ${photo.name}"
            )

            // 현재 사진이 이미 다운로드 중이거나 캐시에 있으면 건너뛰기
            if (!downloadingImages.contains(photo.path) && !fullImageCache.containsKey(photo.path)) {
                // 우선 현재 사진만 빠르게 다운로드
                viewModel.quickPreloadCurrentImage(photo)

                // 현재 사진 다운로드 완료 대기 (최대 2초)
                var waitCount = 0
                while (!fullImageCache.containsKey(photo.path) &&
                    downloadingImages.contains(photo.path) &&
                    waitCount < 20
                ) {
                    delay(100)
                    waitCount++
                }

                // 인접 사진들 백그라운드 다운로드 (1초 후)
                delay(1000)
                viewModel.preloadAdjacentImages(photo, photos)
            }
        }

        // ImageViewer 호출
        FullScreenPhotoViewer(
            photo = photo,
            onDismiss = {
                Log.d("PhotoPreviewScreen", "❌ ImageViewer 닫힘")
                viewModel.selectPhoto(null)
            },
            onPhotoChanged = { newPhoto ->
                // 같은 사진이면 호출하지 않음 (중복 방지)
                if (newPhoto.path != photo.path) {
                    Log.d(
                        "PhotoPreviewScreen",
                        "📸 ImageViewer - 사진 변경: ${photo.name} → ${newPhoto.name}"
                    )
                    viewModel.selectPhoto(newPhoto)
                }
            },
            thumbnailData = viewModel.getThumbnail(photo.path),
            fullImageData = fullImageCache[photo.path], // 실시간으로 업데이트되는 실제 파일 데이터
            isDownloadingFullImage = downloadingImages.contains(photo.path),
            onDownload = { 
                // RAW 파일 접근 권한 체크
                if (com.inik.camcon.utils.SubscriptionUtils.isRawFile(photo.path)) {
                    val tier = uiState.currentTier
                    val canAccess = tier == com.inik.camcon.domain.model.SubscriptionTier.PRO || 
                                   tier == com.inik.camcon.domain.model.SubscriptionTier.REFERRER || 
                                   tier == com.inik.camcon.domain.model.SubscriptionTier.ADMIN
                    
                    if (!canAccess) {
                        // RAW 파일 다운로드 제한 메시지 표시
                        val message = when (tier) {
                            com.inik.camcon.domain.model.SubscriptionTier.FREE -> 
                                "RAW 파일 다운로드는 준비중입니다.\nJPG 파일만 다운로드하실 수 있습니다."
                            com.inik.camcon.domain.model.SubscriptionTier.BASIC -> 
                                "RAW 파일 다운로드는 PRO 구독에서만 가능합니다.\nPRO로 업그레이드해주세요!"
                            else -> "RAW 파일을 다운로드할 수 없습니다."
                        }
                        // 에러 메시지 표시 (ViewModel을 통해)
                        viewModel.clearError() // 기존 에러 클리어 후
                        // ViewModel에서 직접 에러 상태 설정은 불가하므로, 대신 다운로드 시도로 처리
                        // viewModel.downloadPhoto에서 이미 RAW 제한 로직이 있음
                    }
                }
                viewModel.downloadPhoto(photo) 
            },
            viewModel = viewModel, // ViewModel을 통해 썸네일 캐시 공유
            localPhotos = if (photos.any { File(it.path).exists() }) photos else null // 로컬 사진인 경우 목록 전달
        )

        BackHandler(enabled = !isMultiSelectMode) {
            viewModel.selectPhoto(null)
        }
    }

    // 에러 메시지
    uiState.error?.let { error ->
        ErrorSnackbar(
            error = error,
            onRetry = {
                viewModel.clearError()
                viewModel.loadCameraPhotos()
            }
        )
    }
}

/**
 * 카메라 연결이 끊어진 상태를 표시하는 컴포넌트
 */
@Composable
private fun CameraDisconnectedState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "📷",
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                text = "카메라가 연결되지 않았습니다",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "USB 케이블을 연결하고 카메라를 켜주세요",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 모던한 디자인의 상단 타이틀 컴포넌트
 */
@Composable
private fun ModernHeader(
    photoCount: Int,
    currentPage: Int,
    totalPages: Int,
    onRefresh: () -> Unit,
    fileTypeFilter: FileTypeFilter,
    onFilterChange: (FileTypeFilter) -> Unit,
    viewModel: PhotoPreviewViewModel? = null
) {
    var lastClickTime by remember { mutableStateOf(0L) }

    // 사용자 티어 정보 가져오기
    val uiState by viewModel?.uiState?.collectAsState()
        ?: remember { mutableStateOf(com.inik.camcon.presentation.viewmodel.PhotoPreviewUiState()) }
    val canAccessRaw = uiState.currentTier == com.inik.camcon.domain.model.SubscriptionTier.PRO ||
            uiState.currentTier == com.inik.camcon.domain.model.SubscriptionTier.REFERRER ||
            uiState.currentTier == com.inik.camcon.domain.model.SubscriptionTier.ADMIN

    Column {
        // 첫 번째 행: 제목 중앙 정렬, 새로고침 버튼 우측
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            // 중앙 정렬된 제목
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.camera_photo_list),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                if (photoCount > 0) {
                    Text(
                        text = "${photoCount}장의 사진" +
                                if (totalPages > 0) " (페이지 ${currentPage + 1}/${totalPages})" else "",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // 우측 새로고침 버튼
            IconButton(
                onClick = {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastClickTime < 1000) {
                        // 더블클릭 감지 - 강제 로딩 테스트
                        Log.d("PhotoPreviewScreen", "🧪 더블클릭 감지 - 강제 로딩 테스트")
                        viewModel?.forceLoadNextPage()
                    } else {
                        // 일반 새로고침
                        onRefresh()
                    }
                    lastClickTime = currentTime
                },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "새로고침",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp)) // 여백 증가 (12dp → 16dp)

        // 두 번째 행: 파일 타입 필터 버튼들 (중앙 정렬)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "필터:",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = 8.dp)
            )

            TextButton(
                onClick = { onFilterChange(FileTypeFilter.ALL) },
                enabled = fileTypeFilter != FileTypeFilter.ALL
            ) {
                Text(
                    text = "ALL",
                    color = if (fileTypeFilter == FileTypeFilter.ALL)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            TextButton(
                onClick = {
                    if (canAccessRaw) {
                        onFilterChange(FileTypeFilter.RAW)
                    } else {
                        // RAW 접근 권한 없을 때 제한 메시지 표시
                        val message = when (uiState.currentTier) {
                            com.inik.camcon.domain.model.SubscriptionTier.FREE ->
                                "RAW 파일 보기는 준비중입니다.\nJPG 파일만 확인하실 수 있습니다."

                            com.inik.camcon.domain.model.SubscriptionTier.BASIC ->
                                "RAW 파일은 PRO 구독에서만 볼 수 있습니다.\nPRO로 업그레이드해주세요!"

                            else -> "RAW 파일에 접근할 수 없습니다."
                        }
                        viewModel?.let { vm ->
                            vm.uiState.value.copy(error = message).let { newState ->
                                // ViewModel의 private 메서드이므로 직접 호출 불가
                                // 대신 RAW 필터 선택을 시도하여 ViewModel에서 에러 처리하도록 함
                                onFilterChange(FileTypeFilter.RAW)
                            }
                        }
                    }
                },
                enabled = fileTypeFilter != FileTypeFilter.RAW
            ) {
                Text(
                    text = "RAW${if (!canAccessRaw) " 🔒" else ""}",
                    color = if (fileTypeFilter == FileTypeFilter.RAW)
                        MaterialTheme.colorScheme.primary
                    else if (!canAccessRaw)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            TextButton(
                onClick = { onFilterChange(FileTypeFilter.JPG) },
                enabled = fileTypeFilter != FileTypeFilter.JPG
            ) {
                Text(
                    text = "JPG",
                    color = if (fileTypeFilter == FileTypeFilter.JPG)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

/**
 * 로딩 인디케이터
 */
@Composable
private fun LoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
                    Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "카메라에서 사진을 불러오는 중...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
    }
}

/**
 * 사진 그리드 컴포넌트
 */
@Composable
private fun PhotoGrid(
    uiState: com.inik.camcon.presentation.viewmodel.PhotoPreviewUiState,
    photos: List<com.inik.camcon.domain.model.CameraPhoto>,
    isLoadingMore: Boolean,
    hasNextPage: Boolean,
    isMultiSelectMode: Boolean,
    selectedPhotos: Set<String>,
    viewModel: PhotoPreviewViewModel
) {
    val lazyGridState = rememberLazyStaggeredGridState()
    val fullImageCache by viewModel.fullImageCache.collectAsState()

    // 무한 스크롤 구현 - 푸터 감지 개선
    LaunchedEffect(lazyGridState) {
        snapshotFlow {
            val layoutInfo = lazyGridState.layoutInfo
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            val lastVisibleItemIndex = visibleItemsInfo.lastOrNull()?.index ?: -1

            // 스크롤 상태 정보를 더 상세하게 로깅
            lastVisibleItemIndex
        }
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex >= 0 && photos.isNotEmpty()) {
                    Log.d(
                        "PhotoPreviewScreen",
                        "스크롤 감지: 마지막 보이는 인덱스=$lastVisibleIndex, 총 사진=${photos.size}개"
                    )
                    viewModel.onPhotoIndexReached(lastVisibleIndex)
                }
            }
    }

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2), // Pinterest 스타일: 2열 고정
        state = lazyGridState,
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp,
        modifier = Modifier.fillMaxSize()
    ) {
        // 첫 번째 아이템을 특별하게 표시 (전체 너비)
        photos.firstOrNull()?.let { firstPhoto ->
            if (!isMultiSelectMode) { // 멀티선택 모드가 아닐 때만 특별 표시
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

        // 나머지 아이템들 (2열 레이아웃)
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
                        // 멀티 선택 모드에서는 선택/해제
                        viewModel.togglePhotoSelection(photo.path)
                    } else {
                        // 일반 모드에서는 전체화면으로 이동
                        viewModel.selectPhoto(photo)
                    }
                },
                onLongClick = {
                    if (!isMultiSelectMode) {
                        // 멀티 선택 모드 시작
                        viewModel.startMultiSelectMode(photo.path)
                    }
                },
                isSelected = selectedPhotos.contains(photo.path),
                isMultiSelectMode = isMultiSelectMode
            )
        }

        // 로딩 상태 디버깅
        Log.d("PhotoPreviewScreen", "로딩 상태 체크:")
        Log.d("PhotoPreviewScreen", "  - isLoading: ${isLoadingMore}")
        Log.d("PhotoPreviewScreen", "  - photos.size: ${photos.size}")
        Log.d("PhotoPreviewScreen", "  - hasNextPage: ${hasNextPage}")

        // 더 로딩 중일 때 로딩 인디케이터 표시
        if ((isLoadingMore) && photos.isNotEmpty()) {
            Log.d("PhotoPreviewScreen", "LoadMoreIndicator 표시 조건 만족")
            item(span = StaggeredGridItemSpan.FullLine) {
                LoadMoreIndicator()
            }
        }

        // 마지막 페이지일 때 완료 메시지
        else if (!hasNextPage && photos.isNotEmpty() && !isLoadingMore) {
            Log.d("PhotoPreviewScreen", "EndOfListMessage 표시 조건 만족")
            item(span = StaggeredGridItemSpan.FullLine) {
                EndOfListMessage(photoCount = photos.size)
            }
        } else {
            Log.d("PhotoPreviewScreen", "로딩 인디케이터/완료 메시지 표시하지 않음")
        }
    }
}

/**
 * 더 많은 사진을 로딩 중일 때 표시되는 인디케이터
 */
@Composable
private fun LoadMoreIndicator() {
    Log.d("PhotoPreviewScreen", "🔄 LoadMoreIndicator 컴포넌트 렌더링됨")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "더 많은 사진 불러오는 중...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * 리스트 끝에 도달했을 때 표시되는 메시지
 */
@Composable
private fun EndOfListMessage(photoCount: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "모든 사진을 불러왔습니다 (총 ${photoCount}개)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 에러 상황에서 표시되는 스낵바
 */
@Composable
private fun ErrorSnackbar(
    error: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Snackbar(
            modifier = Modifier.padding(16.dp),
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            action = {
                TextButton(onClick = onRetry) {
                    Text(
                        text = "재시도",
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        ) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

/**
 * 멀티 선택 모드에서 표시되는 액션 바
 */
@Composable
private fun MultiSelectActionBar(
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // 첫 번째 행: 선택된 개수와 취소 버튼
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 선택된 개수 표시 (칩 스타일)
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "$selectedCount 개 선택됨",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            // 취소 버튼 (텍스트 버튼)
            TextButton(
                onClick = onCancel,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text(
                    text = "취소",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 두 번째 행: 액션 버튼들 (OutlinedButton with icons)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 전체 선택 버튼
            OutlinedButton(
                onClick = onSelectAll,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SelectAll,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "전체 선택",
                    style = MaterialTheme.typography.labelMedium
                )
            }

            // 전체 해제 버튼
            OutlinedButton(
                onClick = onDeselectAll,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "전체 해제",
                    style = MaterialTheme.typography.labelMedium
                )
            }

            // 다운로드 버튼 (FilledTonalButton로 강조)
            FilledTonalButton(
                onClick = onDownload,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "다운로드",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

/**
 * PTPIP 모드에서 사진 미리보기를 블록하는 오버레이
 */
@Composable
private fun PtpipBlockOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "📶",
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                text = "Wi-Fi 연결 중입니다",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "현재 카메라가 Wi-Fi로 연결되어 있어\n사진 미리보기를 사용할 수 없습니다.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "사진 미리보기를 사용하려면",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "USB 케이블 연결로 전환해주세요",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Wi-Fi 연결에서는 '카메라 제어' 탭을 이용해주세요!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
            )
        }
    }
}

/**
 * Previews
 */

@Preview(showBackground = true)
@Composable
private fun ModernHeaderPreview_NoPhotos() {
    CamConTheme(themeMode = ThemeMode.LIGHT) {
        ModernHeader(
            photoCount = 0,
            currentPage = 0,
            totalPages = 0,
            onRefresh = {},
            fileTypeFilter = FileTypeFilter.JPG,
            onFilterChange = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ModernHeaderPreview_WithPhotos() {
    CamConTheme(themeMode = ThemeMode.LIGHT) {
        ModernHeader(
            photoCount = 42,
            currentPage = 1,
            totalPages = 3,
            onRefresh = {},
            fileTypeFilter = FileTypeFilter.JPG,
            onFilterChange = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LoadingIndicatorPreview() {
    CamConTheme(themeMode = ThemeMode.LIGHT) {
        LoadingIndicator()
    }
}

@Preview(showBackground = true)
@Composable
private fun LoadMoreIndicatorPreview() {
    CamConTheme(themeMode = ThemeMode.LIGHT) {
        LoadMoreIndicator()
    }
}

@Preview(showBackground = true)
@Composable
private fun EndOfListMessagePreview() {
    CamConTheme(themeMode = ThemeMode.LIGHT) {
        EndOfListMessage(photoCount = 42)
    }
}

@Preview(showBackground = true)
@Composable
private fun ErrorSnackbarPreview() {
    CamConTheme(themeMode = ThemeMode.LIGHT) {
        ErrorSnackbar(
            error = "사진을 불러오는 중 오류가 발생했습니다.",
            onRetry = {}
        )
    }
}