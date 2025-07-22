package com.inik.camcon.presentation.ui.screens

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Snackbar
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inik.camcon.R
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.ui.screens.components.EmptyPhotoState
import com.inik.camcon.presentation.ui.screens.components.FullScreenPhotoViewer
import com.inik.camcon.presentation.ui.screens.components.PhotoThumbnail
import com.inik.camcon.presentation.viewmodel.FileTypeFilter
import com.inik.camcon.presentation.viewmodel.PhotoPreviewViewModel
import kotlinx.coroutines.delay

/**
 * 카메라에서 촬영한 사진들을 미리보기로 보여주는 메인 화면
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun PhotoPreviewScreen(
    viewModel: PhotoPreviewViewModel = hiltViewModel()
) {
    Log.d("PhotoPreviewScreen", "=== PhotoPreviewScreen 컴포저블 시작 ===")

    val uiState by viewModel.uiState.collectAsState()

    Log.d("PhotoPreviewScreen", "현재 UI 상태:")
    Log.d("PhotoPreviewScreen", "  - isConnected: ${uiState.isConnected}")
    Log.d("PhotoPreviewScreen", "  - isLoading: ${uiState.isLoading}")
    Log.d("PhotoPreviewScreen", "  - photos.size: ${uiState.photos.size}")
    Log.d("PhotoPreviewScreen", "  - error: ${uiState.error}")

    val pullRefreshState = rememberPullRefreshState(
        refreshing = uiState.isLoading,
        onRefresh = {
            Log.d("PhotoPreviewScreen", "Pull to refresh 트리거")
            viewModel.loadCameraPhotos()
        }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp) // 상단 마진 추가
        ) {
            // 상단 타이틀 영역 (모던한 디자인)
            ModernHeader(
                photoCount = uiState.photos.size,
                currentPage = uiState.currentPage,
                totalPages = uiState.totalPages,
                onRefresh = { viewModel.loadCameraPhotos() },
                fileTypeFilter = uiState.fileTypeFilter,
                onFilterChange = { filter -> viewModel.changeFileTypeFilter(filter) }
            )

            // 메인 콘텐츠
            when {
                !uiState.isConnected -> {
                    CameraDisconnectedState()
                }

                uiState.isLoading && uiState.photos.isEmpty() -> {
                    LoadingIndicator()
                }

                uiState.photos.isEmpty() -> {
                    EmptyPhotoState()
                }

                else -> {
                    PhotoGrid(
                        uiState = uiState,
                        viewModel = viewModel
                    )
                }
            }
        }

        // Pull to refresh 인디케이터
        PullRefreshIndicator(
            refreshing = uiState.isLoading,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = MaterialTheme.colors.surface,
            contentColor = MaterialTheme.colors.primary
        )
    }

    // 전체화면 사진 뷰어
    uiState.selectedPhoto?.let { photo ->
        // fullImageCache와 downloadingImages 상태 관찰
        val fullImageCache by viewModel.fullImageCache.collectAsState()
        val downloadingImages by viewModel.downloadingImages.collectAsState()

        // 선택된 사진의 실제 파일 다운로드 시작 (한 번만 실행)
        LaunchedEffect(photo.path) {
            android.util.Log.d(
                "PhotoPreviewScreen",
                "StfalconImageViewer 진입 - 최적화된 다운로드: ${photo.name}"
            )

            // 우선 현재 사진만 빠르게 다운로드 (슬라이딩 성능 우선)
            viewModel.quickPreloadCurrentImage(photo)

            // 200ms 후에 인접 사진들 백그라운드 다운로드 (지연 시간 증가)
            delay(200)
            viewModel.preloadAdjacentImages(photo, uiState.photos)
        }

        // StfalconImageViewer 호출
        FullScreenPhotoViewer(
            photo = photo,
            photos = uiState.photos,
            onDismiss = {
                android.util.Log.d("PhotoPreviewScreen", "❌ StfalconImageViewer 닫힘")
                viewModel.selectPhoto(null)
            },
            onPhotoChanged = { newPhoto ->
                // 같은 사진이면 호출하지 않음 (중복 방지)
                if (newPhoto.path != photo.path) {
                    android.util.Log.d(
                        "PhotoPreviewScreen",
                        "📸 StfalconImageViewer - 사진 변경: ${photo.name} → ${newPhoto.name}"
                    )
                    viewModel.selectPhoto(newPhoto)

                    // 즉시 현재 사진만 빠르게 다운로드 (슬라이딩 성능 우선)
                    viewModel.quickPreloadCurrentImage(newPhoto)
                }
            },
            thumbnailData = viewModel.getThumbnail(photo.path),
            fullImageData = fullImageCache[photo.path], // 실시간으로 업데이트되는 실제 파일 데이터
            isDownloadingFullImage = downloadingImages.contains(photo.path),
            onDownload = { viewModel.downloadPhoto(photo) },
            viewModel = viewModel, // ViewModel 전달
            thumbnailCache = uiState.thumbnailCache // 전체 썸네일 캐시 전달
        )

        BackHandler {
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
                style = MaterialTheme.typography.h2,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                text = "카메라가 연결되지 않았습니다",
                style = MaterialTheme.typography.h6,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "USB 케이블을 연결하고 카메라를 켜주세요",
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
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
    onFilterChange: (FileTypeFilter) -> Unit
) {
    Column {
        // 첫 번째 행: 제목과 새로고침 버튼
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.camera_photo_list),
                    color = MaterialTheme.colors.onPrimary,
                    style = MaterialTheme.typography.h6
                )
                if (photoCount > 0) {
                    Text(
                        text = "${photoCount}장의 사진" +
                                if (totalPages > 0) " (페이지 ${currentPage + 1}/${totalPages})" else "",
                        color = MaterialTheme.colors.onPrimary.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.caption
                    )
                }
            }

            IconButton(
                onClick = onRefresh
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "새로고침",
                    tint = MaterialTheme.colors.onPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 두 번째 행: 파일 타입 필터 버튼들
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "필터:",
                color = MaterialTheme.colors.onPrimary.copy(alpha = 0.8f),
                style = MaterialTheme.typography.body2,
                modifier = Modifier.padding(end = 8.dp)
            )

            TextButton(
                onClick = { onFilterChange(FileTypeFilter.ALL) },
                enabled = fileTypeFilter != FileTypeFilter.ALL
            ) {
                Text(
                    text = "ALL",
                    color = if (fileTypeFilter == FileTypeFilter.ALL) MaterialTheme.colors.secondary else MaterialTheme.colors.onPrimary.copy(
                        alpha = 0.7f
                    ),
                    style = MaterialTheme.typography.button
                )
            }

            TextButton(
                onClick = { onFilterChange(FileTypeFilter.RAW) },
                enabled = fileTypeFilter != FileTypeFilter.RAW
            ) {
                Text(
                    text = "RAW",
                    color = if (fileTypeFilter == FileTypeFilter.RAW) MaterialTheme.colors.secondary else MaterialTheme.colors.onPrimary.copy(
                        alpha = 0.7f
                    ),
                    style = MaterialTheme.typography.button
                )
            }

            TextButton(
                onClick = { onFilterChange(FileTypeFilter.JPG) },
                enabled = fileTypeFilter != FileTypeFilter.JPG
            ) {
                Text(
                    text = "JPG",
                    color = if (fileTypeFilter == FileTypeFilter.JPG) MaterialTheme.colors.secondary else MaterialTheme.colors.onPrimary.copy(
                        alpha = 0.7f
                    ),
                    style = MaterialTheme.typography.button
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
                color = MaterialTheme.colors.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "카메라에서 사진을 불러오는 중...",
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
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
    viewModel: PhotoPreviewViewModel
) {
    val lazyGridState = rememberLazyGridState()

    // 무한 스크롤 구현 - 푸터 감지 개선
    LaunchedEffect(lazyGridState) {
        snapshotFlow {
            val layoutInfo = lazyGridState.layoutInfo
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            val lastVisibleItemIndex = visibleItemsInfo.lastOrNull()?.index ?: -1
            val totalItemsCount = uiState.photos.size

            // 스크롤 상태 정보를 더 상세하게 로깅
            lastVisibleItemIndex
        }
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex >= 0 && uiState.photos.isNotEmpty()) {
                    Log.d(
                        "PhotoPreviewScreen",
                        "스크롤 감지: 마지막 보이는 인덱스=$lastVisibleIndex, 총 사진=${uiState.photos.size}개"
                    )
                    viewModel.onPhotoIndexReached(lastVisibleIndex)
                }
            }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = lazyGridState,
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(uiState.photos) { photo ->
            PhotoThumbnail(
                photo = photo,
                onClick = { viewModel.selectPhoto(photo) },
                thumbnailData = viewModel.getThumbnail(photo.path)
            )
        }

        // 더 로딩 중일 때 로딩 인디케이터 표시 (프리로딩은 백그라운드이므로 표시하지 않음)
        // 마지막 페이지일 때 완료 메시지
        if (!uiState.hasNextPage && uiState.photos.isNotEmpty()) {
            item(span = { GridItemSpan(3) }) {
                EndOfListMessage(photoCount = uiState.photos.size)
            }
        }
    }
}

/**
 * 더 많은 사진을 로딩 중일 때 표시되는 인디케이터
 */
@Composable
private fun LoadMoreIndicator() {
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
                color = MaterialTheme.colors.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "더 많은 사진 불러오는 중...",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
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
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
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
            backgroundColor = MaterialTheme.colors.error,
            action = {
                TextButton(onClick = onRetry) {
                    Text(
                        text = "재시도",
                        color = MaterialTheme.colors.onError
                    )
                }
            }
        ) {
            Text(
                text = error,
                color = MaterialTheme.colors.onError
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
    CamConTheme {
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
    CamConTheme {
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
    CamConTheme {
        LoadingIndicator()
    }
}

@Preview(showBackground = true)
@Composable
private fun LoadMoreIndicatorPreview() {
    CamConTheme {
        LoadMoreIndicator()
    }
}

@Preview(showBackground = true)
@Composable
private fun EndOfListMessagePreview() {
    CamConTheme {
        EndOfListMessage(photoCount = 42)
    }
}

@Preview(showBackground = true)
@Composable
private fun ErrorSnackbarPreview() {
    CamConTheme {
        ErrorSnackbar(
            error = "사진을 불러오는 중 오류가 발생했습니다.",
            onRetry = {}
        )
    }
}