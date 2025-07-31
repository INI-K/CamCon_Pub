package com.inik.camcon.presentation.ui.screens.components

// --- BottomSheet 관련 임포트 ---
// --------------------------
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
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
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * 현재 보여지는 사진을 공유
 */
private fun shareCurrentPhoto(
    context: android.content.Context,
    photo: CameraPhoto,
    viewModel: PhotoPreviewViewModel?,
    fullImageData: ByteArray? = null,
    thumbnailData: ByteArray? = null
) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            // 이미지 데이터 가져오기 우선순위:
            // 1. viewModel의 고화질 이미지
            // 2. 직접 전달받은 fullImageData
            // 3. viewModel의 썸네일
            // 4. 직접 전달받은 thumbnailData
            val imageData = when {
                viewModel != null -> {
                    val fullImage = viewModel.fullImageCache.value[photo.path]
                    val thumbnail = viewModel.uiState.value.thumbnailCache[photo.path]
                    fullImage ?: thumbnail
                }

                fullImageData != null -> fullImageData
                thumbnailData != null -> thumbnailData
                else -> null
            }

            if (imageData != null && imageData.isNotEmpty()) {
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

                        Log.d("PhotoShare", "사진 공유 시작: ${tempFile.name} (${imageData.size} bytes)")
                    } catch (e: Exception) {
                        Log.e("PhotoShare", "공유 인텐트 실행 실패", e)
                        Toast.makeText(context, "사진 공유에 실패했습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "공유할 이미지 데이터가 없습니다.", Toast.LENGTH_SHORT).show()
                    Log.w(
                        "PhotoShare",
                        "공유할 이미지 데이터 없음: viewModel=${viewModel != null}, fullImageData=${fullImageData != null}, thumbnailData=${thumbnailData != null}"
                    )
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
    hideDownloadButton: Boolean = false // 다운로드 버튼 숨김 옵션 추가
) {
    val context = LocalContext.current

    // 사진 정보 바텀시트 관련 상태
    val showPhotoInfoSheet = remember { mutableStateOf(false) }
    val modalBottomSheetState = androidx.compose.material3.rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    val scope = rememberCoroutineScope()

    // ViewModel의 상태 관찰
    val uiState by viewModel?.uiState?.collectAsState() ?: remember {
        mutableStateOf(
            com.inik.camcon.presentation.viewmodel.PhotoPreviewUiState(
                photos = listOf(photo), // 단일 사진만 포함
                thumbnailCache = thumbnailData?.let { mapOf(photo.path to it) } ?: emptyMap()
            )
        )
    }

    // ViewModel의 썸네일 캐시 직접 사용 (성능 최적화)
    val sharedThumbnailCache = uiState.thumbnailCache

    // 현재 사진 인덱스 찾기 (단일 사진인 경우 항상 0)
    val currentPhotoIndex = if (viewModel != null) {
        remember(photo.path, uiState.photos) {
            uiState.photos.indexOfFirst { it.path == photo.path }.takeIf { it >= 0 } ?: 0
        }
    } else {
        0 // 단일 사진인 경우 항상 0
    }

    // ViewModel의 캐시 상태 관찰
    val fullImageCache by viewModel?.fullImageCache?.collectAsState() ?: remember {
        mutableStateOf(
            fullImageData?.let { mapOf(photo.path to it) } ?: emptyMap()
        )
    }

    // Pager 상태 - 스와이프 네비게이션용 (단일 사진인 경우 비활성화)
    val pagerState = rememberPagerState(
        initialPage = currentPhotoIndex,
        pageCount = { if (viewModel != null) uiState.photos.size else 1 }
    )

    // 페이지 변경 감지 (PhotoPreviewViewModel이 있을 때만)
    if (viewModel != null) {
        LaunchedEffect(pagerState.currentPage) {
            val newPhoto = uiState.photos.getOrNull(pagerState.currentPage)
            if (newPhoto != null && newPhoto.path != photo.path) {
                Log.d(
                    "FullScreenPhotoViewer",
                    "Pager 페이지 변경 성공: ${photo.name} → ${newPhoto.name} (페이지: ${pagerState.currentPage})"
                )
                onPhotoChanged(newPhoto)
                // 페이지네이션 체크: 뷰어에서도 페이지 로딩 트리거
                viewModel.onPhotoIndexReached(pagerState.currentPage)
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
            if (currentPhoto != null) {
                val hasFullImage = fullImageCache.containsKey(currentPhoto.path)
                val isDownloading = viewModel.isDownloadingFullImage(currentPhoto.path)

                if (!hasFullImage && !isDownloading) {
                    Log.d("ImageViewer", "현재 사진 고화질 다운로드: ${currentPhoto.name}")
                    viewModel.downloadFullImage(currentPhoto.path)
                }
            }
        }
    }

    // 전체화면 배경
    // 바텀시트 내부 컨텐츠
    val currentPhotoForSheet = uiState.photos.getOrNull(pagerState.currentPage) ?: photo

    @OptIn(ExperimentalMaterial3Api::class)
    if (showPhotoInfoSheet.value) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = {
                showPhotoInfoSheet.value = false
                scope.launch { modalBottomSheetState.hide() }
            },
            sheetState = modalBottomSheetState
        ) {
            PhotoInfoBottomSheetContent(
                photo = currentPhotoForSheet,
                viewModel = viewModel,
                onDismiss = {
                    showPhotoInfoSheet.value = false
                    scope.launch { modalBottomSheetState.hide() }
                }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 메인 이미지 페이저 (스와이프 네비게이션) - PhotoPreviewViewModel이 있을 때만 활성화
        if (viewModel != null) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { pageIndex ->
                val pagePhoto = uiState.photos.getOrNull(pageIndex)
                if (pagePhoto != null) {
                    GalleryStyleImage(
                        fullImageData = fullImageCache[pagePhoto.path],
                        thumbnailData = sharedThumbnailCache[pagePhoto.path],
                        photo = pagePhoto,
                        onDismiss = onDismiss,
                        context = context
                    )
                }
            }
        } else {
            // 단일 사진 표시
            GalleryStyleImage(
                fullImageData = fullImageData,
                thumbnailData = thumbnailData,
                photo = photo,
                onDismiss = onDismiss,
                context = context
            )
        }

        // 상단 컨트롤 바
        TopControlBar(
            photo = if (viewModel != null) (uiState.photos.getOrNull(pagerState.currentPage)
                ?: photo) else photo,
            onClose = onDismiss,
            onInfoClick = {
                val currentPhoto =
                    if (viewModel != null) (uiState.photos.getOrNull(pagerState.currentPage)
                        ?: photo) else photo
                Log.d("FullScreenPhotoViewer", "정보 버튼 클릭됨: ${currentPhoto.name}")
                try {
                    // 기존 AlertDialog/PhotoInfoDialog.showPhotoInfoDialog 대신 바텀시트로 상태 변경
                    showPhotoInfoSheet.value = true
                    scope.launch {
                        modalBottomSheetState.show()
                    }
                    Log.d("FullScreenPhotoViewer", "PhotoInfo 바텀시트 호출 성공")
                } catch (e: Exception) {
                    Log.e("FullScreenPhotoViewer", "PhotoInfoDialog 바텀시트 호출 실패", e)
                }
            },
            onDownloadClick = if (hideDownloadButton) null else onDownload,
            onShareClick = {
                val currentPhoto =
                    if (viewModel != null) (uiState.photos.getOrNull(pagerState.currentPage)
                        ?: photo) else photo
                shareCurrentPhoto(context, currentPhoto, viewModel, fullImageData, thumbnailData)
            },
            modifier = Modifier.align(Alignment.TopStart)
        )

        // 하단 썸네일 리스트 (PhotoPreviewViewModel이 있을 때만 표시)
        if (viewModel != null && uiState.photos.size > 1) {
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
    LaunchedEffect(modalBottomSheetState.currentValue) {
        if (!modalBottomSheetState.isVisible) {
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
                // EXIF 회전을 실제로 적용 (remember로 캐싱) - remember 키 최적화
                val (rotatedBitmap, isPortrait) = remember(
                    fullImageData?.contentHashCode(),
                    photo.path
                ) {
                    processImageWithOptimization(fullImageData, photo.name, "full")
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

                    // 하드웨어 가속 및 성능 최적화를 위한 ImageViewer 설정
                    ImageViewer(state = imageViewerState) {
                        Image(
                            bitmap = rotatedBitmap.asImageBitmap(),
                            contentDescription = photo.name,
                            contentScale = contentScale,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    // 하드웨어 가속 활성화
                                    compositingStrategy = CompositingStrategy.Offscreen
                                )
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
                    // EXIF 회전을 실제로 적용 (remember로 캐싱) - remember 키 최적화
                    val (rotatedBitmap, isPortrait) = remember(
                        thumbnailData.contentHashCode(),
                        photo.path
                    ) {
                        processImageWithOptimization(thumbnailData, photo.name, "thumbnail")
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
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(
                                        // 하드웨어 가속 활성화
                                        compositingStrategy = CompositingStrategy.Offscreen
                                    )
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
 * 이미지 처리 최적화 함수 - 성능 향상을 위한 비트맵 처리
 */
private fun processImageWithOptimization(
    imageData: ByteArray?,
    photoName: String,
    imageType: String
): Pair<android.graphics.Bitmap?, Boolean> {
    if (imageData == null) return Pair(null, false)

    var bitmap: android.graphics.Bitmap? = null
    var rotationDegrees = 0

    try {
        // BitmapFactory.Options로 메모리 최적화
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        // 이미지 크기 먼저 확인
        BitmapFactory.decodeByteArray(imageData, 0, imageData.size, options)
        val originalWidth = options.outWidth
        val originalHeight = options.outHeight

        // 메모리 절약을 위한 샘플링 (4K 이상 이미지는 다운스케일)
        val maxDimension = 4096 // 4K 해상도 제한
        val sampleSize = calculateInSampleSize(originalWidth, originalHeight, maxDimension)

        // 실제 디코딩
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
            inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
            inDither = false
            inTempStorage = ByteArray(16 * 1024) // 16KB 버퍼
        }

        bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size, decodeOptions)

        // EXIF 회전 정보 읽기
        val exif = ExifInterface(ByteArrayInputStream(imageData))
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
            "EXIF_ROTATE_OPTIMIZED",
            "$imageType 사진: $photoName, 원본: ${originalWidth}x${originalHeight}, " +
                    "샘플링: $sampleSize, EXIF Orientation: $orientation, 회전: $rotationDegrees"
        )

    } catch (e: Exception) {
        Log.e("EXIF_ROTATE_OPTIMIZED", "$imageType EXIF 읽기/비트맵 생성 실패: ${e.message}")
        return Pair(null, false)
    }

    // 회전 적용 (필요한 경우만)
    val finalBitmap = if (bitmap != null && rotationDegrees != 0) {
        try {
            val matrix = Matrix().apply {
                postRotate(rotationDegrees.toFloat())
            }

            val rotatedBitmap = android.graphics.Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )

            // 원본 비트맵 메모리 해제
            if (rotatedBitmap != bitmap) {
                bitmap.recycle()
            }

            rotatedBitmap
        } catch (e: Exception) {
            Log.e("EXIF_ROTATE_OPTIMIZED", "$imageType 비트맵 회전 실패: ${e.message}")
            bitmap
        }
    } else {
        bitmap
    }

    // 세로/가로 판별
    val isPortrait = finalBitmap?.let { it.height > it.width } ?: false

    finalBitmap?.let { bmp ->
        Log.d(
            "EXIF_ROTATE_OPTIMIZED",
            "$imageType 최종 비트맵 크기(회전적용후): ${bmp.width}x${bmp.height}, isPortrait: $isPortrait"
        )
    }

    return Pair(finalBitmap, isPortrait)
}

/**
 * 적절한 샘플링 크기 계산 - 메모리 최적화
 */
private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
    var inSampleSize = 1

    if (height > maxDimension || width > maxDimension) {
        val halfHeight = height / 2
        val halfWidth = width / 2

        // 원하는 크기보다 작아질 때까지 반으로 나누기
        while ((halfHeight / inSampleSize) >= maxDimension && (halfWidth / inSampleSize) >= maxDimension) {
            inSampleSize *= 2
        }
    }

    return inSampleSize
}

/**
 * 상단 컨트롤 바
 */
@Composable
private fun TopControlBar(
    photo: CameraPhoto,
    onClose: () -> Unit,
    onInfoClick: () -> Unit,
    onDownloadClick: (() -> Unit)?, // nullable로 변경
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

        // 다운로드 버튼 (조건부 표시)
        if (onDownloadClick != null) {
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
    val scope = rememberCoroutineScope()

    LaunchedEffect(photo.path) {
        withContext(Dispatchers.IO) {
            try {
                Log.d("PhotoInfoDialog", "EXIF 정보 가져오기 시작: ${photo.path}")

                val info = if (viewModel != null) {
                    // PhotoPreviewViewModel이 있으면 캐시에서 가져오기
                    viewModel.getCameraPhotoExif(photo.path)
                } else {
                    // PhotoPreviewViewModel이 없으면 파일에서 직접 읽기
                    readExifFromFile(photo.path)
                }

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
                    val formattedDate = remember(exifInfo.value, isLoading.value) {
                        if (!isLoading.value && !exifInfo.value.isNullOrEmpty()) {
                            try {
                                val exifEntries = parseExifInfo(exifInfo.value!!)
                                val dateTimeOriginal = exifEntries["date_time_original"]

                                if (dateTimeOriginal != null) {
                                    Log.d("PhotoInfoDialog", "EXIF 날짜 원본: $dateTimeOriginal")
                                    // EXIF 날짜 형식: "2025:07:28 19:22:53"
                                    val exifFormat =
                                        SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault())
                                    val displayFormat =
                                        SimpleDateFormat("yyyy년 M월 d일 a h:mm", Locale.KOREAN)

                                    try {
                                        val parsedDate = exifFormat.parse(dateTimeOriginal)
                                        if (parsedDate != null) {
                                            val result = displayFormat.format(parsedDate)
                                            Log.d("PhotoInfoDialog", "EXIF 날짜 파싱 성공: $result")
                                            result
                                        } else {
                                            Log.w("PhotoInfoDialog", "EXIF 날짜 파싱 실패, 기본값 사용")
                                            "촬영 날짜 알 수 없음"
                                        }
                                    } catch (e: Exception) {
                                        Log.e(
                                            "PhotoInfoDialog",
                                            "EXIF 날짜 파싱 예외: $dateTimeOriginal",
                                            e
                                        )
                                        "촬영 날짜 알 수 없음"
                                    }
                                } else {
                                    Log.d("PhotoInfoDialog", "EXIF에 date_time_original 없음, 기본값 사용")
                                    SimpleDateFormat("yyyy년 M월 d일 a h:mm", Locale.KOREAN)
                                        .format(Date(photo.date))
                                }
                            } catch (e: Exception) {
                                Log.w("PhotoInfoDialog", "EXIF 정보 파싱 실패", e)
                                SimpleDateFormat("yyyy년 M월 d일 a h:mm", Locale.KOREAN)
                                    .format(Date(photo.date))
                            }
                        } else {
                            if (isLoading.value) {
                                "날짜 정보 불러오는 중..."
                            } else {
                                SimpleDateFormat("yyyy년 M월 d일 a h:mm", Locale.KOREAN)
                                    .format(Date(photo.date))
                            }
                        }
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

// 파일에서 직접 EXIF 정보를 읽는 함수
private fun readExifFromFile(filePath: String): String? {
    return try {
        val file = java.io.File(filePath)
        if (!file.exists()) return null

        val exif = androidx.exifinterface.media.ExifInterface(filePath)
        val exifMap = mutableMapOf<String, Any>()

        // 기본 이미지 정보
        exifMap["width"] = exif.getAttributeInt(
            androidx.exifinterface.media.ExifInterface.TAG_IMAGE_WIDTH, 0
        )
        exifMap["height"] = exif.getAttributeInt(
            androidx.exifinterface.media.ExifInterface.TAG_IMAGE_LENGTH, 0
        )
        exifMap["file_size"] = file.length()

        // 카메라 정보
        exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_MAKE)?.let {
            exifMap["make"] = it
        }
        exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_MODEL)?.let {
            exifMap["model"] = it
        }

        // 촬영 설정
        exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_F_NUMBER)?.let {
            exifMap["f_number"] = it
        }
        exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_EXPOSURE_TIME)?.let {
            exifMap["exposure_time"] = it
        }
        exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_FOCAL_LENGTH)?.let {
            exifMap["focal_length"] = it
        }
        exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
            ?.let {
                exifMap["iso"] = it
            }

        // 기타 정보
        val orientation = exif.getAttributeInt(
            androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
            androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
        )
        exifMap["orientation"] = orientation

        exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_WHITE_BALANCE)?.let {
            exifMap["white_balance"] = it
        }
        exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_FLASH)?.let {
            exifMap["flash"] = it
        }
        exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL)?.let {
            exifMap["date_time_original"] = it
        }

        // GPS 정보
        val latLong = floatArrayOf(0f, 0f)
        if (exif.getLatLong(latLong)) {
            exifMap["gps_latitude"] = latLong[0]
            exifMap["gps_longitude"] = latLong[1]
        }

        // JSON 문자열로 변환
        val jsonObject = org.json.JSONObject()
        exifMap.forEach { (key, value) ->
            jsonObject.put(key, value)
        }

        jsonObject.toString()
    } catch (e: Exception) {
        Log.e("PhotoInfoDialog", "파일에서 EXIF 읽기 실패: ${e.message}", e)
        null
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
        // 분수 형태 처리 ("400/10" -> 40.0)
        if (focalLength.contains("/")) {
            val parts = focalLength.split("/")
            if (parts.size == 2) {
                val numerator = parts[0].toDoubleOrNull()
                val denominator = parts[1].toDoubleOrNull()
                if (numerator != null && denominator != null && denominator != 0.0) {
                    val result = numerator / denominator
                    return if (result == result.toInt().toDouble()) {
                        "${result.toInt()}mm"
                    } else {
                        "${String.format("%.1f", result)}mm"
                    }
                }
            }
        }

        // 일반 숫자 형태 처리
        val focal = focalLength.toDoubleOrNull()
        if (focal != null) {
            if (focal == focal.toInt().toDouble()) {
                "${focal.toInt()}mm"
            } else {
                "${String.format("%.1f", focal)}mm"
            }
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