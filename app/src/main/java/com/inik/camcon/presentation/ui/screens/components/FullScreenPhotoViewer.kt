package com.inik.camcon.presentation.ui.screens.components

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.inik.camcon.domain.model.CameraPhoto
import com.inik.camcon.presentation.viewmodel.PhotoPreviewViewModel
import kotlinx.coroutines.delay

/**
 * SmartToolFactory Compose-Image를 사용한 전체화면 사진 뷰어
 * 고급 줌/팬 제스처, 스와이프 네비게이션, 썸네일 지원
 */
@Composable
fun FullScreenPhotoViewer(
    photo: CameraPhoto,
    onDismiss: () -> Unit,
    onPhotoChanged: (CameraPhoto) -> Unit,
    thumbnailData: ByteArray?,
    fullImageData: ByteArray?,
    isDownloadingFullImage: Boolean = false,
    onDownload: () -> Unit,
    viewModel: PhotoPreviewViewModel? = null,
    thumbnailCache: Map<String, ByteArray> = emptyMap()
) {
    val context = LocalContext.current

    // ViewModel의 상태 관찰
    val uiState by viewModel?.uiState?.collectAsState() ?: remember {
        mutableStateOf(com.inik.camcon.presentation.viewmodel.PhotoPreviewUiState())
    }

    // 현재 사진 인덱스 찾기
    val currentPhotoIndex = remember(photo.path, uiState.photos) {
        uiState.photos.indexOfFirst { it.path == photo.path }.takeIf { it >= 0 } ?: 0
    }

    // ViewModel의 캐시 상태 관찰
    val fullImageCache by viewModel?.fullImageCache?.collectAsState() ?: remember { 
        mutableStateOf(emptyMap<String, ByteArray>()) 
    }

    // Pager 상태 - 스와이프 네비게이션용
    val pagerState = rememberPagerState(
        initialPage = currentPhotoIndex,
        pageCount = { uiState.photos.size }
    )

    // 페이지 변경 감지
    LaunchedEffect(pagerState.currentPage) {
        val newPhoto = uiState.photos.getOrNull(pagerState.currentPage)
        if (newPhoto != null && newPhoto.path != photo.path) {
            onPhotoChanged(newPhoto)
        }
    }

    // 외부에서 photo가 변경되면 pager도 동기화
    LaunchedEffect(currentPhotoIndex) {
        if (pagerState.currentPage != currentPhotoIndex) {
            pagerState.animateScrollToPage(currentPhotoIndex)
        }
    }

    // 현재 페이지 사진의 고화질 다운로드
    LaunchedEffect(pagerState.currentPage) {
        val currentPhoto = uiState.photos.getOrNull(pagerState.currentPage)
        if (currentPhoto != null && viewModel != null) {
            val hasFullImage = fullImageCache.containsKey(currentPhoto.path)
            if (!hasFullImage) {
                Log.d("ImageViewer", "현재 사진 고화질 다운로드: ${currentPhoto.name}")
                viewModel.downloadFullImage(currentPhoto.path)
            }
        }
    }

    // 전체화면 배경
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 메인 이미지 페이저 (스와이프 네비게이션)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            val pagePhoto = uiState.photos.getOrNull(pageIndex)
            if (pagePhoto != null) {
                val imageData = fullImageCache[pagePhoto.path] ?: thumbnailCache[pagePhoto.path]

                SmartZoomableImageViewer(
                    imageData = imageData,
                    photo = pagePhoto,
                    onDismiss = onDismiss,
                    context = context
                )
            }
        }

        // 상단 컨트롤 바
        TopControlBar(
            photo = uiState.photos.getOrNull(pagerState.currentPage) ?: photo,
            onClose = onDismiss,
            onInfoClick = {
                val currentPhoto = uiState.photos.getOrNull(pagerState.currentPage) ?: photo
                PhotoInfoDialog.showPhotoInfoDialog(context, currentPhoto, viewModel)
            },
            modifier = Modifier.align(Alignment.TopStart)
        )

        // 하단 썸네일 리스트
        BottomThumbnailStrip(
            photos = uiState.photos,
            currentPhotoIndex = pagerState.currentPage,
            thumbnailCache = thumbnailCache,
            viewModel = viewModel,
            onPhotoSelected = { selectedPhoto ->
                val newIndex = uiState.photos.indexOfFirst { it.path == selectedPhoto.path }
                if (newIndex >= 0) {
                    onPhotoChanged(selectedPhoto)
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/**
 * 기본 이미지 뷰어 (라이브러리 로딩 문제 해결 후 개선 예정)
 */
@Composable
private fun SmartZoomableImageViewer(
    imageData: ByteArray?,
    photo: CameraPhoto,
    onDismiss: () -> Unit,
    context: android.content.Context
) {
    if (imageData != null) {
        // 이미지 비트맵 생성
        val bitmap = remember(imageData) {
            BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
        }

        if (bitmap != null) {
            // 기본 Image 사용 (임시)
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = photo.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onDismiss() }
            )
        } else {
            // 바이트 배열 디코딩 실패 시 Coil 사용
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageData)
                    .crossfade(true)
                    .build(),
                contentDescription = photo.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onDismiss() }
            )
        }
    } else {
        // 이미지 데이터가 없을 때 플레이스홀더
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = Color.White,
                strokeWidth = 3.dp
            )
        }
    }
}

/**
 * 상단 컨트롤 바
 */
@Composable
private fun TopControlBar(
    photo: CameraPhoto,
    onClose: () -> Unit,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 닫기 버튼
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .background(
                    Color.Black.copy(alpha = 0.6f),
                    RoundedCornerShape(20.dp)
                )
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "닫기",
                tint = Color.White
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // 정보 버튼
        IconButton(
            onClick = onInfoClick,
            modifier = Modifier
                .background(
                    Color.Black.copy(alpha = 0.6f),
                    RoundedCornerShape(20.dp)
                )
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = "정보",
                tint = Color.White
            )
        }
    }
}

/**
 * 하단 썸네일 스트립
 */
@Composable
private fun BottomThumbnailStrip(
    photos: List<CameraPhoto>,
    currentPhotoIndex: Int,
    thumbnailCache: Map<String, ByteArray>,
    viewModel: PhotoPreviewViewModel?,
    onPhotoSelected: (CameraPhoto) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // 현재 사진이 변경되면 썸네일 리스트를 해당 위치로 스크롤
    LaunchedEffect(currentPhotoIndex) {
        if (currentPhotoIndex >= 0 && currentPhotoIndex < photos.size) {
            delay(100) // 약간의 지연으로 부드러운 스크롤
            listState.animateScrollToItem(
                index = currentPhotoIndex,
                scrollOffset = -200 // 선택된 아이템이 화면 중앙에 오도록 조정
            )
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        itemsIndexed(photos) { index, photo ->
            ThumbnailItem(
                photo = photo,
                isSelected = index == currentPhotoIndex,
                thumbnailData = thumbnailCache[photo.path] ?: viewModel?.getThumbnail(photo.path),
                onClick = { onPhotoSelected(photo) }
            )
        }
    }
}

/**
 * 개별 썸네일 아이템
 */
@Composable
private fun ThumbnailItem(
    photo: CameraPhoto,
    isSelected: Boolean,
    thumbnailData: ByteArray?,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) Color.White else Color.Gray.copy(alpha = 0.3f)
            )
            .clickable { onClick() }
            .padding(if (isSelected) 2.dp else 0.dp)
    ) {
        if (thumbnailData != null) {
            val bitmap = remember(thumbnailData) {
                BitmapFactory.decodeByteArray(thumbnailData, 0, thumbnailData.size)
            }

            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = photo.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(if (isSelected) 6.dp else 8.dp))
                )
            } else {
                // 바이트 배열 디코딩 실패 시 Coil 사용
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(thumbnailData)
                        .crossfade(true)
                        .build(),
                    contentDescription = photo.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(if (isSelected) 6.dp else 8.dp))
                )
            }
        } else {
            // 썸네일이 없을 때 플레이스홀더
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Gray.copy(alpha = 0.5f))
                    .clip(RoundedCornerShape(if (isSelected) 6.dp else 8.dp)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            }
        }
    }
}