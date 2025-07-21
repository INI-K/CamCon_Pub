package com.inik.camcon.presentation.ui.screens.components

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.inik.camcon.domain.model.CameraPhoto

/**
 * 사진 뷰어의 메인 이미지 콘텐츠
 * 갤러리 앱처럼 스와이프, 더블탭 줌, 핀치 줌 지원
 */
@Composable
fun PhotoViewerContent(
    photo: CameraPhoto,
    photos: List<CameraPhoto>,
    thumbnailData: ByteArray?,
    fullImageData: ByteArray?,
    isDownloadingFullImage: Boolean = false,
    currentPhotoIndex: Int,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    onScaleChange: (Float) -> Unit,
    onOffsetChange: (Float, Float) -> Unit,
    onAnimateScale: (Float) -> Unit,
    onAnimateOffset: (Float, Float) -> Unit,
    onPhotoChanged: (CameraPhoto) -> Unit
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }

    // 페이저 상태 관리
    val pagerState = rememberPagerState(
        initialPage = currentPhotoIndex,
        pageCount = { photos.size }
    )

    // 확대/축소 상태 관리
    var imageScale by remember { mutableStateOf(1f) }
    var imageOffsetX by remember { mutableStateOf(0f) }
    var imageOffsetY by remember { mutableStateOf(0f) }
    var lastTapTime by remember { mutableStateOf(0L) }

    // 사진이 변경될 때 줌 상태 초기화
    LaunchedEffect(photo.path) {
        imageScale = 1f
        imageOffsetX = 0f
        imageOffsetY = 0f
        onScaleChange(1f)
        onOffsetChange(0f, 0f)
    }

    // 페이저 상태 변경 감지
    LaunchedEffect(pagerState.currentPage) {
        Log.d("PhotoViewer", "페이저 상태 변경: ${pagerState.currentPage} (기존: $currentPhotoIndex)")
        if (pagerState.currentPage != currentPhotoIndex && pagerState.currentPage < photos.size) {
            Log.d("PhotoViewer", "새 사진으로 변경: ${photos[pagerState.currentPage].name}")
            onPhotoChanged(photos[pagerState.currentPage])
        }
    }

    // 외부에서 사진이 변경될 때 페이저 동기화
    LaunchedEffect(currentPhotoIndex) {
        Log.d(
            "PhotoViewer",
            "외부에서 사진 인덱스 변경: $currentPhotoIndex (페이저: ${pagerState.currentPage})"
        )
        if (pagerState.currentPage != currentPhotoIndex) {
            Log.d("PhotoViewer", "페이저 동기화 중...")
            pagerState.animateScrollToPage(currentPhotoIndex)
        }
    }

    Log.d(
        "PhotoViewer",
        "HorizontalPager 렌더링: 총 ${photos.size}개, 현재 페이지: ${pagerState.currentPage}, userScrollEnabled: ${imageScale <= 1.1f}"
    )

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        pageSpacing = 0.dp,
        userScrollEnabled = true // 디버깅을 위해 항상 활성화
    ) { pageIndex ->
        val pagePhoto = photos[pageIndex]
        val isCurrentPage = pageIndex == pagerState.currentPage

        Log.d("PhotoViewer", "페이지 $pageIndex 렌더링: ${pagePhoto.name}, 현재 페이지인가? $isCurrentPage")

        Box(
            modifier = Modifier
                .fillMaxSize()
            // 디버깅을 위해 제스처 처리를 일시적으로 비활성화
            /*
            .pointerInput(pageIndex, imageScale) {
                // 모든 제스처를 하나의 pointerInput으로 통합
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        if (pageIndex == pagerState.currentPage) {
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastTapTime > 300) { // 중복 방지
                                lastTapTime = currentTime
                                Log.d("PhotoViewer", "더블탭 감지! 현재 스케일: $imageScale")

                                if (imageScale > 1.5f) {
                                    // 축소
                                    imageScale = 1f
                                    imageOffsetX = 0f
                                    imageOffsetY = 0f
                                    onAnimateScale(1f)
                                    onAnimateOffset(0f, 0f)
                                } else {
                                    // 확대 (탭한 지점을 중심으로)
                                    val newScale = 2.5f
                                    imageScale = newScale

                                    // 탭한 지점을 화면 중앙으로 이동
                                    val centerX = screenWidth / 2f
                                    val centerY = screenHeight / 2f
                                    imageOffsetX =
                                        (centerX - tapOffset.x) * (newScale - 1f) / newScale
                                    imageOffsetY =
                                        (centerY - tapOffset.y) * (newScale - 1f) / newScale

                                    onAnimateScale(newScale)
                                    onAnimateOffset(imageOffsetX, imageOffsetY)
                                }
                            }
                        }
                    }
                )
            }
            .pointerInput(pageIndex, imageScale) {
                // 핀치 줌과 팬을 함께 처리 (현재 페이지에서만)
                if (pageIndex == pagerState.currentPage) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = max(1f, min(imageScale * zoom, 4f))

                        if (newScale > 1f || imageScale > 1f) {
                            imageScale = newScale

                            // 확대된 상태에서만 팬 처리
                            if (imageScale > 1f) {
                                val maxOffsetX = (screenWidth * (imageScale - 1f)) / 2f
                                val maxOffsetY = (screenHeight * (imageScale - 1f)) / 2f

                                imageOffsetX =
                                    (imageOffsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                                imageOffsetY =
                                    (imageOffsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                            } else {
                                imageOffsetX = 0f
                                imageOffsetY = 0f
                            }

                            onScaleChange(imageScale)
                            onOffsetChange(imageOffsetX, imageOffsetY)
                        }
                    }
                }
            }
            */,
            contentAlignment = Alignment.Center
        ) {
            PhotoSlide(
                photo = pagePhoto,
                thumbnailData = if (pagePhoto.path == photo.path) thumbnailData else null,
                fullImageData = if (pagePhoto.path == photo.path) fullImageData else null,
                isDownloadingFullImage = if (pagePhoto.path == photo.path) isDownloadingFullImage else false,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = if (isCurrentPage) imageScale else 1f,
                        scaleY = if (isCurrentPage) imageScale else 1f,
                        translationX = if (isCurrentPage) imageOffsetX else 0f,
                        translationY = if (isCurrentPage) imageOffsetY else 0f
                    )
            )
        }
    }
}