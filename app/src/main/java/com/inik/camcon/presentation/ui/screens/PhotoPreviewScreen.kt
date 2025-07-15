package com.inik.camcon.presentation.ui.screens

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
import androidx.compose.material.TopAppBar
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
import com.inik.camcon.presentation.viewmodel.PhotoPreviewViewModel

/**
 * 카메라에서 촬영한 사진들을 미리보기로 보여주는 메인 화면
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun PhotoPreviewScreen(
    viewModel: PhotoPreviewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val pullRefreshState = rememberPullRefreshState(
        refreshing = uiState.isLoading,
        onRefresh = { viewModel.loadCameraPhotos() }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 상단 앱바
            TopAppBar(
                title = {
                    PhotoPreviewTitle(
                        photoCount = uiState.photos.size,
                        currentPage = uiState.currentPage,
                        totalPages = uiState.totalPages
                    )
                },
                backgroundColor = MaterialTheme.colors.primary,
                elevation = 4.dp,
                actions = {
                    if (uiState.photos.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.loadCameraPhotos() }
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "새로고침",
                                tint = MaterialTheme.colors.onPrimary
                            )
                        }
                    }
                }
            )

            // 메인 콘텐츠
            when {
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
        FullScreenPhotoViewer(
            photo = photo,
            photos = uiState.photos,
            onDismiss = { viewModel.selectPhoto(null) },
            onPhotoChanged = { newPhoto -> viewModel.selectPhoto(newPhoto) },
            thumbnailData = viewModel.getThumbnail(photo.path),
            onDownload = { viewModel.downloadPhoto(photo) }
        )
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
 * 상단 앱바 제목 컴포넌트
 */
@Composable
private fun PhotoPreviewTitle(
    photoCount: Int,
    currentPage: Int,
    totalPages: Int
) {
    Column {
        Text(
            text = stringResource(R.string.recent_captures),
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

    // 무한 스크롤 구현
    LaunchedEffect(lazyGridState) {
        snapshotFlow { lazyGridState.layoutInfo.visibleItemsInfo }
            .collect { visibleItems ->
                val lastVisibleItemIndex = visibleItems.lastOrNull()?.index ?: -1
                val totalItemsCount = uiState.photos.size

                // 마지막에서 5개 아이템 전에 도달하면 다음 페이지 로드
                if (lastVisibleItemIndex >= totalItemsCount - 5 &&
                    uiState.hasNextPage &&
                    !uiState.isLoadingMore
                ) {
                    viewModel.loadNextPage()
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

        // 더 로딩 중일 때 로딩 인디케이터 표시
        if (uiState.isLoadingMore) {
            item(span = { GridItemSpan(3) }) {
                LoadMoreIndicator()
            }
        }

        // 마지막 페이지일 때 완료 메시지
        if (!uiState.hasNextPage && uiState.photos.isNotEmpty() && !uiState.isLoadingMore) {
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
private fun PhotoPreviewTitlePreview_NoPhotos() {
    CamConTheme {
        PhotoPreviewTitle(
            photoCount = 0,
            currentPage = 0,
            totalPages = 0
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PhotoPreviewTitlePreview_WithPhotos() {
    CamConTheme {
        PhotoPreviewTitle(
            photoCount = 42,
            currentPage = 1,
            totalPages = 3
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