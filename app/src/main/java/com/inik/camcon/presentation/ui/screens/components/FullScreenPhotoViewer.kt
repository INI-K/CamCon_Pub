package com.inik.camcon.presentation.ui.screens.components

// --- BottomSheet 관련 임포트 ---
// --------------------------
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.inik.camcon.domain.model.CameraPhoto
import com.inik.camcon.presentation.viewmodel.PhotoPreviewViewModel
import com.zhangke.imageviewer.ImageViewer
import com.zhangke.imageviewer.rememberImageViewerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.abs

/**
 * 현재 보여지는 사진을 공유
 */
private fun shareCurrentPhoto(
    context: android.content.Context,
    photo: CameraPhoto,
    viewModel: PhotoPreviewViewModel?
) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            // 고화질 이미지 데이터 가져오기 (썸네일보다 우선)
            val fullImageData = viewModel?.fullImageCache?.value?.get(photo.path)
            val imageData =
                fullImageData ?: viewModel?.uiState?.value?.thumbnailCache?.get(photo.path)

            if (imageData != null) {
                // 임시 파일 생성
                val cacheDir = File(context.cacheDir, "shared_photos")
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs()
                }

                val tempFile = File(cacheDir, "share_${photo.name}")
                FileOutputStream(tempFile).use { fos ->
                    fos.write(imageData)
                }

                withContext(Dispatchers.Main) {
                    try {
                        // FileProvider를 사용하여 URI 생성
                        val fileUri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            tempFile
                        )

                        // 공유 인텐트 생성
                        val shareIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            type = "image/*"
                            putExtra(Intent.EXTRA_STREAM, fileUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }

                        val chooser = Intent.createChooser(shareIntent, "사진 공유")
                        context.startActivity(chooser)

                        Log.d("PhotoShare", "사진 공유 시작: ${tempFile.name}")
                    } catch (e: Exception) {
                        Log.e("PhotoShare", "공유 인텐트 실행 실패", e)
                        Toast.makeText(context, "사진 공유에 실패했습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "이미지 데이터를 불러오는 중입니다.", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e("PhotoShare", "사진 공유 준비 중 오류", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "사진 공유에 실패했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/**
 * 0xZhangKe ImageViewer를 사용한 전체화면 사진 뷰어
 * 고급 줌/팬 제스처, 스와이프 네비게이션, 썸네일 지원
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun FullScreenPhotoViewer(
    photo: CameraPhoto,
    onDismiss: () -> Unit,
    onPhotoChanged: (CameraPhoto) -> Unit,
    thumbnailData: ByteArray?,
    fullImageData: ByteArray?,
    isDownloadingFullImage: Boolean = false,
    onDownload: () -> Unit,
    viewModel: PhotoPreviewViewModel? = null
) {
    val context = LocalContext.current

    // 사진 정보 바텀시트 관련 상태
    val showPhotoInfoSheet = remember { mutableStateOf(false) }
    val bottomSheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
        confirmStateChange = { it != ModalBottomSheetValue.HalfExpanded }
    )
    val scope = rememberCoroutineScope()

    // ViewModel의 상태 관찰
    val uiState by viewModel?.uiState?.collectAsState() ?: remember {
        mutableStateOf(com.inik.camcon.presentation.viewmodel.PhotoPreviewUiState())
    }

    // ViewModel의 썸네일 캐시 직접 사용 (성능 최적화)
    val sharedThumbnailCache = uiState.thumbnailCache

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
            Log.d(
                "FullScreenPhotoViewer",
                "Pager 페이지 변경 성공: ${photo.name} → ${newPhoto.name} (페이지: ${pagerState.currentPage})"
            )
            onPhotoChanged(newPhoto)
            // 페이지네이션 체크: 뷰어에서도 페이지 로딩 트리거
            viewModel?.onPhotoIndexReached(pagerState.currentPage)
        } else {
            Log.d(
                "FullScreenPhotoViewer",
                "Pager 현재 페이지: ${pagerState.currentPage}, 총 ${uiState.photos.size}장"
            )
        }
    }

    // Pager 스크롤 상태 모니터링
    LaunchedEffect(pagerState) {
        snapshotFlow<Boolean> { pagerState.isScrollInProgress }.collect { isScrolling ->
            Log.d(
                "FullScreenPhotoViewer",
                "HorizontalPager 스크롤: ${if (isScrolling) "진행중" else "정지"}"
            )
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow<Float> { pagerState.currentPageOffsetFraction }.collect { offset ->
            if (abs(offset) > 0.01f) {
                Log.d("FullScreenPhotoViewer", "HorizontalPager 오프셋: $offset")
            }
        }
    }

    // 외부에서 photo가 변경되면 pager도 동기화 (애니메이션 없이 즉시 이동)
    LaunchedEffect(currentPhotoIndex) {
        if (pagerState.currentPage != currentPhotoIndex && currentPhotoIndex >= 0) {
            Log.d("FullScreenPhotoViewer", "외부 photo 변경으로 pager 동기화: index=$currentPhotoIndex")
            pagerState.scrollToPage(currentPhotoIndex)
        }
    }

    // 현재 페이지 사진의 고화질 다운로드 (중복 방지)
    LaunchedEffect(pagerState.currentPage) {
        val currentPhoto = uiState.photos.getOrNull(pagerState.currentPage)
        if (currentPhoto != null && viewModel != null) {
            val hasFullImage = fullImageCache.containsKey(currentPhoto.path)
            val isDownloading = viewModel.isDownloadingFullImage(currentPhoto.path)

            if (!hasFullImage && !isDownloading) {
                Log.d("ImageViewer", "현재 사진 고화질 다운로드: ${currentPhoto.name}")
                viewModel.downloadFullImage(currentPhoto.path)
            }
        }
    }

    // 전체화면 배경
    // 바텀시트 내부 컨텐츠
    val currentPhotoForSheet = uiState.photos.getOrNull(pagerState.currentPage) ?: photo

    ModalBottomSheetLayout(
        sheetState = bottomSheetState,
        sheetContent = {
            if (showPhotoInfoSheet.value) {
                // Surface 제거하고 직접 Column 사용
                PhotoInfoBottomSheetContent(
                    photo = currentPhotoForSheet,
                    viewModel = viewModel,
                    onDismiss = {
                        showPhotoInfoSheet.value = false
                        // 바텀시트 닫기
                        scope.launch {
                            bottomSheetState.hide()
                        }
                    }
                )
            }
        },
        sheetBackgroundColor = Color.White, // 직접 흰색 배경
        scrimColor = Color.Black.copy(alpha = 0.4f)
    ) {
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
                    val imageData =
                        fullImageCache[pagePhoto.path] ?: sharedThumbnailCache[pagePhoto.path]

                    GalleryStyleImage(
                        fullImageData = fullImageCache[pagePhoto.path],
                        thumbnailData = sharedThumbnailCache[pagePhoto.path],
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
                    Log.d("FullScreenPhotoViewer", "정보 버튼 클릭됨: ${currentPhoto.name}")
                    try {
                        // 기존 AlertDialog/PhotoInfoDialog.showPhotoInfoDialog 대신 바텀시트로 상태 변경
                        showPhotoInfoSheet.value = true
                        scope.launch {
                            bottomSheetState.show()
                        }
                        Log.d("FullScreenPhotoViewer", "PhotoInfo 바텀시트 호출 성공")
                    } catch (e: Exception) {
                        Log.e("FullScreenPhotoViewer", "PhotoInfoDialog 바텀시트 호출 실패", e)
                    }
                },
                onDownloadClick = onDownload,
                onShareClick = {
                    val currentPhoto = uiState.photos.getOrNull(pagerState.currentPage) ?: photo
                    shareCurrentPhoto(context, currentPhoto, viewModel)
                },
                modifier = Modifier.align(Alignment.TopStart)
            )

            // 하단 썸네일 리스트
            BottomThumbnailStrip(
                photos = uiState.photos,
                currentPhotoIndex = pagerState.currentPage,
                thumbnailCache = sharedThumbnailCache,
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

    // 바텀시트 상태와 showPhotoInfoSheet 동기화
    LaunchedEffect(bottomSheetState.isVisible) {
        if (!bottomSheetState.isVisible) {
            showPhotoInfoSheet.value = false
        }
    }
}

/**
 * 0xZhangKe ImageViewer를 사용한 갤러리 스타일의 이미지 뷰어
 * pagerState를 받아서(예: 스와이프 상태 상호작용 차단 등에도 활용 가능)
 */
@Composable
private fun GalleryStyleImage(
    fullImageData: ByteArray?,
    thumbnailData: ByteArray?,
    photo: CameraPhoto,
    onDismiss: () -> Unit,
    context: android.content.Context
) {
    // "고화질(full) 있으면 고화질, 없으면 썸네일 둘 중 하나로 Crossfade"
    Crossfade(
        targetState = if (fullImageData != null) "full" else "thumbnail",
        animationSpec = tween(durationMillis = 350)
    ) { which ->
        when (which) {
            "full" -> {
                // EXIF 회전을 실제로 적용 (remember로 캐싱)
                val (rotatedBitmap, isPortrait) = remember(fullImageData) {
                    var bitmap: android.graphics.Bitmap? = null
                    var rotationDegrees = 0

                    fullImageData?.let { data ->
                        try {
                            bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
                            val exif = ExifInterface(ByteArrayInputStream(data))
                            val orientation = exif.getAttributeInt(
                                ExifInterface.TAG_ORIENTATION,
                                ExifInterface.ORIENTATION_UNDEFINED
                            )
                            rotationDegrees = when (orientation) {
                                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                                else -> 0
                            }
                            Log.d(
                                "EXIF_ROTATE",
                                "full이미지 사진: ${photo.name}, EXIF Orientation: $orientation, 회전 각도: $rotationDegrees"
                            )
                        } catch (e: Exception) {
                            Log.e("EXIF_ROTATE", "EXIF 읽기/비트맵 생성 실패: ${e.message}")
                        }
                    }

                    val finalBitmap = if (bitmap != null && rotationDegrees != 0) {
                        val matrix = Matrix()
                        matrix.postRotate(rotationDegrees.toFloat())
                        try {
                            android.graphics.Bitmap.createBitmap(
                                bitmap!!, 0, 0, bitmap!!.width, bitmap!!.height, matrix, true
                            )
                        } catch (e: Exception) {
                            Log.e("EXIF_ROTATE", "비트맵 회전 실패: ${e.message}")
                            bitmap
                        }
                    } else {
                        bitmap
                    }

                    // 세로/가로 판별
                    val portrait = finalBitmap?.let { it.height > it.width } ?: false
                    finalBitmap?.let { bmp ->
                        Log.d(
                            "EXIF_ROTATE",
                            "full이미지 실제 비트맵 크기(회전적용후): ${bmp.width}x${bmp.height}, isPortrait: $portrait"
                        )
                    }

                    Pair(finalBitmap, portrait)
                }

                if (rotatedBitmap != null) {
                    val contentScale =
                        if (isPortrait) ContentScale.Fit else ContentScale.FillBounds

                    Log.d(
                        "EXIF_ROTATE",
                        "최종 결정 - 사진: ${photo.name}, isPortrait: $isPortrait, ContentScale: ${if (isPortrait) "Fit" else "FillBounds"}"
                    )

                    val imageViewerState = rememberImageViewerState(
                        minimumScale = 1.0f,
                        maximumScale = 5.0f
                    )
                    ImageViewer(state = imageViewerState) {
                        Image(
                            bitmap = rotatedBitmap.asImageBitmap(),
                            contentDescription = photo.name,
                            contentScale = contentScale,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
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
            "thumbnail" -> {
                if (thumbnailData != null) {
                    // EXIF 회전을 실제로 적용 (remember로 캐싱)
                    val (rotatedBitmap, isPortrait) = remember(thumbnailData) {
                        var bitmap: android.graphics.Bitmap? = null
                        var rotationDegrees = 0

                        thumbnailData?.let { data ->
                            try {
                                bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
                                val exif = ExifInterface(ByteArrayInputStream(data))
                                val orientation = exif.getAttributeInt(
                                    ExifInterface.TAG_ORIENTATION,
                                    ExifInterface.ORIENTATION_UNDEFINED
                                )
                                rotationDegrees = when (orientation) {
                                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                                    else -> 0
                                }
                                Log.d(
                                    "EXIF_ROTATE_THUMB",
                                    "썸네일 사진: ${photo.name}, EXIF Orientation: $orientation, 회전 각도: $rotationDegrees"
                                )
                            } catch (e: Exception) {
                                Log.e("EXIF_ROTATE_THUMB", "EXIF 읽기/비트맵 생성 실패: ${e.message}")
                            }
                        }

                        val finalBitmap = if (bitmap != null && rotationDegrees != 0) {
                            val matrix = Matrix()
                            matrix.postRotate(rotationDegrees.toFloat())
                            try {
                                android.graphics.Bitmap.createBitmap(
                                    bitmap!!, 0, 0, bitmap!!.width, bitmap!!.height, matrix, true
                                )
                            } catch (e: Exception) {
                                Log.e("EXIF_ROTATE_THUMB", "썸네일 비트맵 회전 실패: ${e.message}")
                                bitmap
                            }
                        } else {
                            bitmap
                        }

                        // 세로/가로 판별
                        val portrait = finalBitmap?.let { it.height > it.width } ?: false
                        finalBitmap?.let { bmp ->
                            Log.d(
                                "EXIF_ROTATE_THUMB",
                                "썸네일 실제 비트맵 크기(회전적용후): ${bmp.width}x${bmp.height}, isPortrait: $portrait"
                            )
                        }

                        Pair(finalBitmap, portrait)
                    }

                    if (rotatedBitmap != null) {
                        val contentScale =
                            if (isPortrait) ContentScale.Fit else ContentScale.FillBounds

                        Log.d(
                            "EXIF_ROTATE_THUMB",
                            "썸네일 최종 결정 - 사진: ${photo.name}, isPortrait: $isPortrait, ContentScale: ${if (isPortrait) "Fit" else "FillBounds"}"
                        )

                        val imageViewerState = rememberImageViewerState(
                            minimumScale = 1.0f,
                            maximumScale = 5.0f
                        )
                        ImageViewer(state = imageViewerState) {
                            Image(
                                bitmap = rotatedBitmap.asImageBitmap(),
                                contentDescription = photo.name,
                                contentScale = contentScale,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        // 둘 다 없을 시 로딩
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
                } else {
                    // 둘 다 없을 시 로딩
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
    onDownloadClick: () -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Start,
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

        // 다운로드 버튼
        IconButton(
            onClick = onDownloadClick,
            modifier = Modifier
                .background(
                    Color.Black.copy(alpha = 0.6f),
                    RoundedCornerShape(20.dp)
                )
        ) {
            Icon(
                Icons.Default.Download,
                contentDescription = "다운로드",
                tint = Color.White
            )
        }

        // 공유 버튼
        IconButton(
            onClick = onShareClick,
            modifier = Modifier
                .background(
                    Color.Black.copy(alpha = 0.6f),
                    RoundedCornerShape(20.dp)
                )
        ) {
            Icon(
                Icons.Default.Share,
                contentDescription = "공유",
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
    val uiState by viewModel?.uiState?.collectAsState() ?: remember {
        mutableStateOf(com.inik.camcon.presentation.viewmodel.PhotoPreviewUiState())
    }

    // 현재 사진이 변경되면 썸네일 리스트를 해당 위치로 스크롤
    LaunchedEffect(currentPhotoIndex) {
        if (currentPhotoIndex >= 0 && currentPhotoIndex < photos.size) {
            delay(100)
            listState.animateScrollToItem(
                index = currentPhotoIndex,
                scrollOffset = -200
            )
        }
    }

    // 스크롤 상태 감지 최적화 - 디바운싱 적용
    LaunchedEffect(listState) {
        var lastEmissionTime = 0L
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
        }.collect { lastVisibleIndex: Int? ->
            val currentTime = System.currentTimeMillis()
            lastEmissionTime = currentTime

            // 300ms 지연 후 처리 (디바운싱)
            delay(300)
            if (currentTime == lastEmissionTime) { // 마지막 이벤트인지 확인
                if (lastVisibleIndex != null && viewModel != null) {
                    val threshold = photos.size - 5
                    if (lastVisibleIndex >= threshold && uiState.hasNextPage && !uiState.isLoadingMore) {
                        Log.d(
                            "ThumbnailStrip",
                            "썸네일 스크롤에서 페이지네이션 트리거: $lastVisibleIndex >= $threshold"
                        )
                        viewModel.onPhotoIndexReached(lastVisibleIndex)
                    }
                }
            }
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
        itemsIndexed(
            items = photos,
            key = { _, photo -> photo.path }
        ) { index, photo ->
            ThumbnailItem(
                photo = photo,
                isSelected = index == currentPhotoIndex,
                thumbnailData = thumbnailCache[photo.path],
                onClick = { onPhotoSelected(photo) }
            )
        }

        // 로딩 인디케이터 아이템 추가
        if (uiState.isLoadingMore && uiState.hasNextPage) {
            item {
                LoadingThumbnailItem()
            }
        }
    }
}

/**
 * 로딩 중인 썸네일 아이템
 */
@Composable
private fun LoadingThumbnailItem() {
    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Gray.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = Color.White,
            strokeWidth = 2.dp
        )
    }
}

/**
 * 개별 썸네일 아이템 - 성능 최적화
 */
@Composable
private fun ThumbnailItem(
    photo: CameraPhoto,
    isSelected: Boolean,
    thumbnailData: ByteArray?,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val bitmap = remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    // 백그라운드에서 비트맵 디코딩
    LaunchedEffect(thumbnailData) {
        if (thumbnailData != null) {
            val decodedBitmap = withContext(Dispatchers.IO) {
                try {
                    BitmapFactory.decodeByteArray(thumbnailData, 0, thumbnailData.size)
                } catch (e: Exception) {
                    Log.e("ThumbnailItem", "비트맵 디코딩 실패: ${e.message}")
                    null
                }
            }
            bitmap.value = decodedBitmap
        } else {
            bitmap.value = null
        }
    }

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
        val currentBitmap = bitmap.value
        when {
            currentBitmap != null -> {
                Image(
                    bitmap = currentBitmap.asImageBitmap(),
                    contentDescription = photo.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(if (isSelected) 6.dp else 8.dp))
                )
            }

            thumbnailData != null -> {
                // 비트맵 디코딩 중일 때
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Gray.copy(alpha = 0.5f))
                        .clip(RoundedCornerShape(if (isSelected) 6.dp else 8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 1.5.dp
                    )
                }
            }
            else -> {
                // 썸네일 데이터가 아직 없을 때
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
}

/**
 * 사진 정보 바텀 다이얼로그 컨텐츠
 */
@Composable
private fun PhotoInfoBottomSheetContent(
    photo: CameraPhoto,
    viewModel: PhotoPreviewViewModel?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val exifInfo = remember { mutableStateOf<String?>(null) }
    val isLoading = remember { mutableStateOf(true) }

    // EXIF 정보 로드
    LaunchedEffect(photo.path) {
        withContext(Dispatchers.IO) {
            try {
                Log.d("PhotoInfoDialog", "EXIF 정보 가져오기 시작: ${photo.path}")
                val info = viewModel?.getCameraPhotoExif(photo.path)
                Log.d("PhotoInfoDialog", "EXIF 정보 가져오기 완료: $info")
                exifInfo.value = info
            } catch (e: Exception) {
                Log.e("PhotoInfoDialog", "EXIF 정보 로드 실패", e)
                exifInfo.value = null
            } finally {
                isLoading.value = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 20.dp)
            .padding(bottom = 40.dp) // 바텀 네비게이션 공간 확보
    ) {
        // 핸들 바
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 4.dp)
                    .background(
                        Color.Gray.copy(alpha = 0.3f),
                        RoundedCornerShape(2.dp)
                    )
            )
        }

        // 헤더
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "상세정보",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black
            )
        }

        // 스크롤 가능한 컨텐츠
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 날짜/시간 정보
            InfoRow(
                icon = {
                    Icon(
                        Icons.Outlined.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Color.Black
                    )
                },
                content = {
                    val dateFormat =
                        java.text.SimpleDateFormat("yyyy년 M월 d일 오후 h:mm", Locale.KOREAN)
                    val formattedDate = try {
                        dateFormat.format(java.util.Date(photo.date * 1000L))
                    } catch (e: Exception) {
                        "알 수 없음"
                    }

                    Text(
                        text = formattedDate,
                        fontSize = 16.sp,
                        color = Color.Black
                    )
                }
            )

            // 파일 정보
            InfoRow(
                icon = {
                    Icon(
                        Icons.Outlined.Image,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Color.Black
                    )
                },
                content = {
                    Column {
                        Text(
                            text = photo.name,
                            fontSize = 16.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                            color = Color.Black
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // 파일 크기와 해상도
                        val fileInfo = buildString {
                            append("${String.format("%.2f", photo.size / 1024.0 / 1024.0)}MB")

                            if (!isLoading.value && !exifInfo.value.isNullOrEmpty() && exifInfo.value != "{}") {
                                try {
                                    val exifEntries = parseExifInfo(exifInfo.value!!)
                                    val width = exifEntries["width"]
                                    val height = exifEntries["height"]
                                    if (width != null && height != null) {
                                        append("    ${width}x${height}")
                                    }
                                } catch (e: Exception) {
                                    // 무시
                                }
                            }
                        }

                        Text(
                            text = fileInfo,
                            fontSize = 14.sp,
                            color = Color.Gray
                        )

                        // 폴더 경로
                        val folderPath = photo.path.substringBeforeLast("/")
                            .replace("/storage/emulated/0", "/내장 메모리")

                        Text(
                            text = folderPath,
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }
                }
            )

            // EXIF 정보
            InfoRow(
                icon = {
                    Icon(
                        Icons.Outlined.PhotoCamera,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Color.Black
                    )
                },
                content = {
                    if (isLoading.value) {
                        Text(
                            text = "EXIF 정보 불러오는 중...",
                            fontSize = 16.sp,
                            color = Color.Gray
                        )
                    } else {
                        ExifInfoContent(exifInfo = exifInfo.value)
                    }
                }
            )
        }
    }
}

@Composable
private fun InfoRow(
    icon: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier.padding(top = 2.dp)
        ) {
            icon()
        }

        Spacer(modifier = Modifier.width(16.dp))

        Box(
            modifier = Modifier.weight(1f)
        ) {
            content()
        }
    }
}

@Composable
private fun ExifInfoContent(exifInfo: String?) {
    if (exifInfo.isNullOrEmpty() || exifInfo == "{}") {
        Text(
            text = "EXIF 정보가 없습니다",
            fontSize = 16.sp,
            color = Color.Gray
        )
    } else {
        val exifEntries = remember(exifInfo) {
            try {
                parseExifInfo(exifInfo)
            } catch (e: Exception) {
                Log.e("PhotoInfoDialog", "EXIF 파싱 오류", e)
                emptyMap()
            }
        }

        if (exifEntries.isNotEmpty()) {
            Column {
                // 카메라 모델
                val cameraModel = buildString {
                    val make = exifEntries["make"]
                    val model = exifEntries["model"]
                    when {
                        make != null && model != null -> {
                            append("$make $model")
                        }

                        make != null -> append(make)
                        model != null -> append(model)
                        else -> append("알 수 없는 카메라")
                    }
                }

                Text(
                    text = cameraModel,
                    fontSize = 16.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    color = Color.Black
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 촬영 설정
                val settings = mutableListOf<String>()
                exifEntries["f_number"]?.let { fNumber ->
                    settings.add(formatAperture(fNumber))
                }
                exifEntries["exposure_time"]?.let { exposureTime ->
                    settings.add(formatShutterSpeed(exposureTime))
                }
                exifEntries["focal_length"]?.let { focalLength ->
                    settings.add(formatFocalLength(focalLength))
                }
                exifEntries["iso"]?.let { iso ->
                    val isoValue = try {
                        val isoNumber = iso.toIntOrNull()
                        if (isoNumber != null) "ISO $isoNumber" else "ISO $iso"
                    } catch (e: Exception) {
                        "ISO $iso"
                    }
                    settings.add(isoValue)
                }

                if (settings.isNotEmpty()) {
                    Text(
                        text = settings.joinToString("    "),
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }

                // 추가 정보 (화이트밸런스, 플래시)
                val additionalInfo = mutableListOf<String>()
                exifEntries["white_balance"]?.let { whiteBalance ->
                    additionalInfo.add("화이트밸런스 ${formatWhiteBalance(whiteBalance)}")
                }
                exifEntries["flash"]?.let { flash ->
                    additionalInfo.add("${formatFlash(flash)}")
                }

                if (additionalInfo.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = additionalInfo.joinToString("    "),
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }
        } else {
            androidx.compose.material.Text(
                text = "EXIF 정보를 파싱할 수 없습니다",
                fontSize = 16.sp,
                color = Color.Gray
            )
        }
    }
}

// EXIF 파싱 및 포맷팅 함수들
private fun parseExifInfo(exifJson: String): Map<String, String> {
    val entries = mutableMapOf<String, String>()
    val cleanJson = exifJson.trim().removePrefix("{").removeSuffix("}")

    if (cleanJson.isNotEmpty()) {
        val pairs = cleanJson.split(",")
        for (pair in pairs) {
            val keyValue = pair.split(":")
            if (keyValue.size == 2) {
                val key = keyValue[0].trim().removeSurrounding("\"")
                val value = keyValue[1].trim().removeSurrounding("\"")
                entries[key] = value
            }
        }
    }
    return entries
}

private fun formatShutterSpeed(exposureTime: String): String {
    return try {
        val time = exposureTime.toDoubleOrNull()
        when {
            time == null -> exposureTime
            time >= 1.0 -> "${time.toInt()} s"
            time > 0 -> {
                val fraction = 1.0 / time
                "1/${String.format("%.0f", fraction)} s"
            }

            else -> exposureTime
        }
    } catch (e: Exception) {
        exposureTime
    }
}

private fun formatAperture(fNumber: String): String {
    return try {
        val aperture = fNumber.toDoubleOrNull()
        if (aperture != null) {
            "F${String.format("%.1f", aperture)}"
        } else {
            "F$fNumber"
        }
    } catch (e: Exception) {
        "F$fNumber"
    }
}

private fun formatFocalLength(focalLength: String): String {
    return try {
        val focal = focalLength.toDoubleOrNull()
        if (focal != null) {
            "${String.format("%.2f", focal)}mm"
        } else {
            "${focalLength}mm"
        }
    } catch (e: Exception) {
        "${focalLength}mm"
    }
}

private fun formatWhiteBalance(whiteBalance: String): String {
    return when (whiteBalance) {
        "0" -> "자동"
        "1" -> "수동"
        else -> whiteBalance
    }
}

private fun formatFlash(flash: String): String {
    return try {
        val flashValue = flash.toIntOrNull() ?: return flash
        when {
            flashValue and 0x01 == 0 -> "플래시 사용 안 함"
            flashValue and 0x01 == 1 -> "플래시 사용함"
            else -> flash
        }
    } catch (e: Exception) {
        flash
    }
}