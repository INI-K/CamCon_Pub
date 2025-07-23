package com.inik.camcon.presentation.ui.screens.components

import android.graphics.Bitmap
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.inik.camcon.R
import com.inik.camcon.domain.model.CameraPhoto
import com.inik.camcon.presentation.viewmodel.PhotoPreviewViewModel
import com.stfalcon.imageviewer.StfalconImageViewer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * StfalconImageViewer를 사용한 전체화면 사진 뷰어
 * 핀치 줌, 스와이프 전환, 스와이프 투 디스미스 기능 제공
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
    
    // 로드 중인 사진들을 추적하여 중복 로드 방지
    val loadingPhotos = remember { mutableSetOf<String>() }
    
    // 현재 표시 중인 뷰어 인스턴스 추적
    var currentViewer: StfalconImageViewer<CameraPhoto>? by remember { mutableStateOf(null) }

    // 썸네일 어댑터 참조
    var thumbnailAdapter: PhotoViewerThumbnailAdapter? by remember { mutableStateOf(null) }
    var thumbnailRecyclerView: RecyclerView? by remember { mutableStateOf(null) }

    // 이미지 로딩 성능 개선을 위한 비트맵 캐시
    val bitmapCache = remember { mutableMapOf<String, Bitmap>() }

    // 현재 보이는 사진들의 ImageView 참조를 저장 (실시간 업데이트용)
    val imageViewRefs = remember { mutableMapOf<String, android.widget.ImageView>() }

    // 고화질 업데이트가 완료된 사진들을 추적 (중복 방지)
    val highQualityUpdated = remember { mutableSetOf<String>() }

    // 뷰어 초기화
    LaunchedEffect(Unit) {
        // 이전 뷰어가 있으면 먼저 닫기
        currentViewer?.dismiss()
        
        // 이미지 뷰어 생성 및 실행
        val viewer = createImageViewer(
            context = context,
            photos = uiState.photos,
            currentPhotoIndex = currentPhotoIndex,
            thumbnailCache = thumbnailCache,
            viewModel = viewModel,
            fullImageCache = fullImageCache,
            bitmapCache = bitmapCache,
            imageViewRefs = imageViewRefs,
            highQualityUpdated = highQualityUpdated,
            loadingPhotos = loadingPhotos,
            onPhotoChanged = onPhotoChanged,
            onThumbnailAdapterCreated = { adapter, recyclerView ->
                thumbnailAdapter = adapter
                thumbnailRecyclerView = recyclerView
            },
            onViewerDismiss = {
                Log.d("StfalconViewer", "뷰어 닫힘 - 정상적인 종료")
                loadingPhotos.clear()
                bitmapCache.clear()
                imageViewRefs.clear()
                highQualityUpdated.clear()
                thumbnailAdapter?.clearBitmapCache()
                currentViewer = null
                onDismiss()
            }
        )

        // 뷰어 표시
        val actualViewer = viewer.show()
        currentViewer = actualViewer
        Log.d("StfalconViewer", "뷰어 표시 완료")
    }

    // 고화질 이미지 캐시가 업데이트되면 실시간으로 고화질 교체
    LaunchedEffect(fullImageCache.keys) {
        delay(200) // 캐시 업데이트 디바운싱

        fullImageCache.forEach { (photoPath, imageData) ->
            if (!highQualityUpdated.contains(photoPath)) {
                imageViewRefs[photoPath]?.let { imageView ->
                    Log.d("StfalconViewer", "실시간 고화질 교체 시작: $photoPath")

                    val cacheKey = "${photoPath}_full"
                    if (!bitmapCache.containsKey(cacheKey)) {
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val bitmap = ImageProcessingUtils.decodeBitmapWithExifRotation(
                                    imageData,
                                    uiState.photos.find { it.path == photoPath }
                                )

                                CoroutineScope(Dispatchers.Main).launch {
                                    delay(100)

                                    if (bitmap != null && !bitmap.isRecycled) {
                                        imageView.setImageBitmap(bitmap)
                                        imageView.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                                        highQualityUpdated.add(photoPath)
                                        Log.d("StfalconViewer", "실시간 고화질 교체 성공")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("StfalconViewer", "실시간 고화질 처리 오류: $photoPath", e)
                            }
                        }
                    }
                }
            }
        }
    }

    // ThumbnailAdapter에 고화질 캐시 업데이트를 전달
    LaunchedEffect(fullImageCache.keys) {
        delay(150) // 캐시 업데이트 디바운싱
        thumbnailAdapter?.updateFullImageCache(fullImageCache)
    }

    // ViewModel의 photos 리스트 변경 감지하여 ThumbnailAdapter 업데이트
    LaunchedEffect(uiState.photos.size) {
        Log.d("StfalconViewer", "사진 리스트 크기 변경 감지: ${uiState.photos.size}개")
        delay(100) // 상태 변경 안정화

        thumbnailAdapter?.refreshPhotos()
    }

    // Compose가 dispose될 때 뷰어 정리
    DisposableEffect(Unit) {
        onDispose {
            Log.d("StfalconViewer", "Compose dispose - 뷰어 정리")
            currentViewer?.dismiss()
            currentViewer = null
            loadingPhotos.clear()
            bitmapCache.clear()
            imageViewRefs.clear()
            highQualityUpdated.clear()
            thumbnailAdapter?.clearBitmapCache()
            onDismiss()
        }
    }
}

/**
 * StfalconImageViewer 생성 함수
 */
private fun createImageViewer(
    context: android.content.Context,
    photos: List<CameraPhoto>,
    currentPhotoIndex: Int,
    thumbnailCache: Map<String, ByteArray>,
    viewModel: PhotoPreviewViewModel?,
    fullImageCache: Map<String, ByteArray>,
    bitmapCache: MutableMap<String, Bitmap>,
    imageViewRefs: MutableMap<String, android.widget.ImageView>,
    highQualityUpdated: MutableSet<String>,
    loadingPhotos: MutableSet<String>,
    onPhotoChanged: (CameraPhoto) -> Unit,
    onThumbnailAdapterCreated: (PhotoViewerThumbnailAdapter, RecyclerView) -> Unit,
    onViewerDismiss: () -> Unit
): StfalconImageViewer.Builder<CameraPhoto> {
    
    return StfalconImageViewer.Builder<CameraPhoto>(context, photos) { imageView, cameraPhoto ->
        // 캐시에서 이미지 데이터를 가져오기
        val photoThumbnail = thumbnailCache[cameraPhoto.path] ?: viewModel?.getThumbnail(cameraPhoto.path)
        val photoFullImage = fullImageCache[cameraPhoto.path]

        // 현재 보이는 사진과 인접한 사진만 로그 출력 (성능 개선)
        val photoIndex = photos.indexOf(cameraPhoto)
        val isCurrentOrAdjacent = kotlin.math.abs(photoIndex - currentPhotoIndex) <= 2

        if (isCurrentOrAdjacent) {
            Log.d("StfalconViewer", "이미지 로더 호출: ${cameraPhoto.name}")
        }

        // 이미지 로딩
        ImageProcessingUtils.loadImageIntoView(
            imageView,
            cameraPhoto,
            photoFullImage,
            photoThumbnail,
            bitmapCache,
            imageViewRefs,
            highQualityUpdated
        )

        // 인접한 사진만 고화질 다운로드
        if (isCurrentOrAdjacent && photoFullImage == null && !loadingPhotos.contains(cameraPhoto.path)) {
            loadingPhotos.add(cameraPhoto.path)
            Log.d("StfalconViewer", "고화질 이미지 다운로드 시작: ${cameraPhoto.path}")
            viewModel?.downloadFullImage(cameraPhoto.path)
        }
    }
    .withStartPosition(currentPhotoIndex)
    .withBackgroundColor(0xFF121212.toInt())
    .withHiddenStatusBar(false)
    .allowSwipeToDismiss(true)
    .allowZooming(true)
    .withOverlayView(
        createOverlayView(
            context,
            viewModel,
            thumbnailCache,
            photos,
            currentPhotoIndex,
            fullImageCache,
            loadingPhotos,
            imageViewRefs,
            highQualityUpdated,
            onPhotoChanged,
            onThumbnailAdapterCreated
        )
    )
    .withImageChangeListener { pos ->
        Log.d("StfalconViewer", "withImageChangeListener 호출됨: 위치 $pos")

        if (pos in photos.indices) {
            val newPhoto = photos[pos]
            Log.d("StfalconViewer", "사진 변경됨: 인덱스 $pos → ${newPhoto.name}")

            // UI 상태 즉시 업데이트
            onPhotoChanged(newPhoto)

            // 백그라운드에서 최소한의 미리 로딩만 수행
            CoroutineScope(Dispatchers.IO).launch {
                // 현재 사진만 우선 다운로드
                if (fullImageCache[newPhoto.path] == null && !loadingPhotos.contains(newPhoto.path)) {
                    loadingPhotos.add(newPhoto.path)
                    Log.d("StfalconViewer", "현재 사진 우선 다운로드: ${newPhoto.path}")
                    viewModel?.downloadFullImage(newPhoto.path)
                }

                // 인접 사진은 더 긴 지연 후 다운로드
                delay(800)
                ImageProcessingUtils.preloadAdjacentPhotosMinimal(
                    pos,
                    photos,
                    fullImageCache,
                    viewModel,
                    loadingPhotos
                )
            }
        }
    }
    .withDismissListener(onViewerDismiss)
}

/**
 * 오버레이 뷰 생성 함수
 */
private fun createOverlayView(
    context: android.content.Context,
    viewModel: PhotoPreviewViewModel?,
    thumbnailCache: Map<String, ByteArray>,
    photos: List<CameraPhoto>,
    currentPhotoIndex: Int,
    fullImageCache: Map<String, ByteArray>,
    loadingPhotos: MutableSet<String>,
    imageViewRefs: MutableMap<String, android.widget.ImageView>,
    highQualityUpdated: MutableSet<String>,
    onPhotoChanged: (CameraPhoto) -> Unit,
    onThumbnailAdapterCreated: (PhotoViewerThumbnailAdapter, RecyclerView) -> Unit
): ViewGroup {
    val layout = LayoutInflater.from(context)
        .inflate(R.layout.photo_viewer_overlay, null) as ViewGroup
    val recyclerView = layout.findViewById<RecyclerView>(R.id.recycler_view)
    val infoButton = layout.findViewById<android.view.View>(R.id.info_button)
    
    // 썸네일 갤러리 설정
    recyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
    val adapter = PhotoViewerThumbnailAdapter(viewModel, thumbnailCache) { position ->
        handleThumbnailClick(
            position,
            photos,
            fullImageCache,
            loadingPhotos,
            imageViewRefs,
            highQualityUpdated,
            viewModel,
            onPhotoChanged
        )
    }

    recyclerView.adapter = adapter
    onThumbnailAdapterCreated(adapter, recyclerView)
    
    // 초기 선택 위치 설정
    adapter.setSelectedPosition(currentPhotoIndex)
    recyclerView.scrollToPosition(currentPhotoIndex)

    // 무한 스크롤 리스너 추가
    setupInfiniteScrollListener(recyclerView, viewModel)

    // 사진 정보 버튼 클릭 리스너 설정
    infoButton.setOnClickListener {
        if (photos.isNotEmpty()) {
            val currentPhoto = photos.getOrNull(currentPhotoIndex) ?: photos.first()
            Log.d("StfalconViewer", "사진 정보 버튼 클릭: ${currentPhoto.name}")
            PhotoInfoDialog.showPhotoInfoDialog(context, currentPhoto, viewModel)
        }
    }
    
    return layout
}

/**
 * 썸네일 클릭 처리 함수
 */
private fun handleThumbnailClick(
    position: Int,
    photos: List<CameraPhoto>,
    fullImageCache: Map<String, ByteArray>,
    loadingPhotos: MutableSet<String>,
    imageViewRefs: MutableMap<String, android.widget.ImageView>,
    highQualityUpdated: MutableSet<String>,
    viewModel: PhotoPreviewViewModel?,
    onPhotoChanged: (CameraPhoto) -> Unit
) {
    if (position !in photos.indices) return

    val newPhoto = photos[position]
    Log.d("StfalconViewer", "썸네일 클릭: 인덱스 $position → ${newPhoto.name}")

    onPhotoChanged(newPhoto)

    // 고화질 이미지 다운로드 또는 적용
    if (fullImageCache[newPhoto.path] == null && !loadingPhotos.contains(newPhoto.path)) {
        loadingPhotos.add(newPhoto.path)
        Log.d("StfalconViewer", "썸네일 클릭 후 고화질 다운로드: ${newPhoto.path}")
        viewModel?.downloadFullImage(newPhoto.path)
    } else if (fullImageCache[newPhoto.path] != null) {
        // 이미 고화질이 캐시에 있으면 즉시 적용
        Log.d("StfalconViewer", "캐시된 고화질 즉시 적용: ${newPhoto.name}")
        
        CoroutineScope(Dispatchers.IO).launch {
            fullImageCache[newPhoto.path]?.let { imageData ->
                try {
                    val bitmap = ImageProcessingUtils.decodeBitmapWithExifRotation(imageData, newPhoto)
                    
                    CoroutineScope(Dispatchers.Main).launch {
                        imageViewRefs[newPhoto.path]?.let { imageView ->
                            if (bitmap != null && !bitmap.isRecycled) {
                                imageView.setImageBitmap(bitmap)
                                imageView.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                                highQualityUpdated.add(newPhoto.path)
                                Log.d("StfalconViewer", "썸네일 클릭 후 고화질 적용 성공")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("StfalconViewer", "썸네일 클릭 후 고화질 적용 오류", e)
                }
            }
        }
    }

    // 인접 사진들 미리 로드
    CoroutineScope(Dispatchers.IO).launch {
        delay(500)
        ImageProcessingUtils.preloadAdjacentPhotosMinimal(
            position,
            photos,
            fullImageCache,
            viewModel,
            loadingPhotos
        )
    }
}

/**
 * 무한 스크롤 리스너 설정
 */
private fun setupInfiniteScrollListener(
    recyclerView: RecyclerView,
    viewModel: PhotoPreviewViewModel?
) {
    recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)

            val layoutManager = recyclerView.layoutManager as LinearLayoutManager
            val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()

            if (lastVisibleItemPosition >= 0) {
                viewModel?.onPhotoIndexReached(lastVisibleItemPosition)
            }
        }

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            super.onScrollStateChanged(recyclerView, newState)

            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()
                viewModel?.onPhotoIndexReached(lastVisibleItemPosition)
            }
        }
    })
}