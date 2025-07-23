package com.inik.camcon.presentation.ui.screens.components

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
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
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    // 전체 사진들의 썸네일 캐시를 미리 받아서 사용
    thumbnailCache: Map<String, ByteArray> = emptyMap()
) {
    val context = LocalContext.current

    // ViewModel의 상태 관찰 - 안정화 처리
    val uiState by viewModel?.uiState?.collectAsState() ?: remember {
        mutableStateOf(com.inik.camcon.presentation.viewmodel.PhotoPreviewUiState())
    }

    // 현재 사진 인덱스 찾기 - 안정화된 상태에서 가져오기
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

    // 썸네일 어댑터 참조 (동기화를 위해)
    var thumbnailAdapter: ThumbnailAdapter? by remember { mutableStateOf(null) }
    var thumbnailRecyclerView: RecyclerView? by remember { mutableStateOf(null) }

    // 이미지 로딩 성능 개선을 위한 비트맵 캐시
    val bitmapCache = remember { mutableMapOf<String, Bitmap>() }

    // 현재 보이는 사진들의 ImageView 참조를 저장 (실시간 업데이트용)
    val imageViewRefs = remember { mutableMapOf<String, ImageView>() }

    // 고화질 업데이트가 완료된 사진들을 추적 (중복 방지)
    val highQualityUpdated = remember { mutableSetOf<String>() }

    // 뷰어 초기화를 한 번만 수행하도록 개선
    LaunchedEffect(Unit) { // photo.path 대신 Unit 사용으로 한 번만 실행
        // 이전 뷰어가 있으면 먼저 닫기
        currentViewer?.dismiss()
        
        // 이미지 뷰어 생성 및 실행
        val viewer = StfalconImageViewer.Builder<CameraPhoto>(
            context,
            uiState.photos
        ) { imageView, cameraPhoto ->
            // 캐시에서 이미지 데이터를 가져오기 (ViewModel보다 전달받은 캐시 우선)
            val photoThumbnail =
                thumbnailCache[cameraPhoto.path] ?: viewModel?.getThumbnail(cameraPhoto.path)
            val photoFullImage = fullImageCache[cameraPhoto.path]

            // 현재 보이는 사진과 인접한 사진만 로그 출력 (성능 개선)
            val photoIndex = uiState.photos.indexOf(cameraPhoto)
            val isCurrentOrAdjacent = kotlin.math.abs(photoIndex - currentPhotoIndex) <= 2

            if (isCurrentOrAdjacent) {
                Log.d("StfalconViewer", "📸 이미지 로더 호출: ${cameraPhoto.name}")
                Log.d("StfalconViewer", "  - 썸네일: ${photoThumbnail?.size ?: 0} bytes (캐시에서)")
                Log.d("StfalconViewer", "  - 고화질: ${photoFullImage?.size ?: 0} bytes")
            }

            // 최적화된 이미지 로딩 (비트맵 캐시 활용)
            loadImageIntoView(
                imageView,
                cameraPhoto,
                photoFullImage,
                photoThumbnail,
                bitmapCache,
                imageViewRefs,
                highQualityUpdated
            )

            // 현재 보이는 사진과 바로 인접한 사진만 고화질 다운로드 (범위 축소)
            if (isCurrentOrAdjacent && photoFullImage == null && !loadingPhotos.contains(cameraPhoto.path)) {
                loadingPhotos.add(cameraPhoto.path)
                Log.d("StfalconViewer", "🔄 고화질 이미지 다운로드 시작: ${cameraPhoto.path}")
                viewModel?.downloadFullImage(cameraPhoto.path)
            }
        }
            .withStartPosition(currentPhotoIndex) // 시작 위치 설정
            .withBackgroundColor(0xFF121212.toInt()) // 다크 테마 배경색 설정 (상태바와 어우러지도록)
            .withHiddenStatusBar(false) // 상태바를 숨기지 않고 투명하게 처리
            .allowSwipeToDismiss(true) // 스와이프로 닫기 허용
            .allowZooming(true) // 줌 허용
            .withOverlayView(
                run {
                    val layout = LayoutInflater.from(context)
                        .inflate(R.layout.photo_viewer_overlay, null) as ViewGroup
                    val recyclerView = layout.findViewById<RecyclerView>(R.id.recycler_view)
                    val infoButton = layout.findViewById<View>(R.id.info_button)
                    
                    recyclerView.layoutManager =
                        LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                    val adapter = ThumbnailAdapter(viewModel, thumbnailCache) { position ->
                            // 썸네일 클릭 시 뷰어를 해당 위치로 이동
                            Log.d("StfalconViewer", "🖱️ 썸네일 클릭: 인덱스 $position 로 이동")

                            // StfalconImageViewer의 setCurrentPosition을 사용하여 프로그래밍 방식으로 위치 변경
                            val viewer = currentViewer
                            Log.d("StfalconViewer", "현재 뷰어 상태: ${viewer != null}")

                            if (viewer != null) {
                                Log.d("StfalconViewer", "setCurrentPosition 호출 전: 위치 $position")
                                viewer.setCurrentPosition(position)
                                Log.d("StfalconViewer", "setCurrentPosition 호출 완료: 위치 $position")

                                // setCurrentPosition이 withImageChangeListener를 트리거하는지 확인
                                Log.d("StfalconViewer", "withImageChangeListener 트리거 대기 중...")

                                // setCurrentPosition 후 약간의 지연을 두고 이미지 변경 리스너 로직 수동 실행
                                CoroutineScope(Dispatchers.Main).launch {
                                    delay(100) // setCurrentPosition 완료 대기

                                    // 이미지 변경 리스너와 동일한 로직 수동 실행
                                    if (position in uiState.photos.indices) {
                                        Log.d("StfalconViewer", "🔄 수동 이미지 변경 리스너 실행: 위치 $position")

                                        // 고화질 이미지가 캐시에 있으면 즉시 적용
                                        val newPhoto = uiState.photos[position]
                                        if (fullImageCache[newPhoto.path] != null) {
                                            // 백그라운드에서 비트맵 디코딩
                                            CoroutineScope(Dispatchers.IO).launch {
                                                fullImageCache[newPhoto.path]?.let { imageData ->
                                                    try {
                                                        val bitmap = decodeBitmapWithExifRotation(
                                                            imageData,
                                                            newPhoto
                                                        )

                                                        // 메인 스레드에서 UI 업데이트
                                                        CoroutineScope(Dispatchers.Main).launch {
                                                            imageViewRefs[newPhoto.path]?.let { imageView ->
                                                                imageView.setImageBitmap(bitmap)
                                                                imageView.scaleType =
                                                                    ImageView.ScaleType.FIT_CENTER
                                                                highQualityUpdated.add(newPhoto.path)
                                                                Log.d(
                                                                    "StfalconViewer",
                                                                    "✅ 수동 고화질 적용: ${newPhoto.name}"
                                                                )
                                                            }
                                                        }
                                                    } catch (e: Exception) {
                                                        Log.e(
                                                            "StfalconViewer",
                                                            "❌ 수동 고화질 적용 오류: ${newPhoto.path}",
                                                            e
                                                        )
                                                    }
                                                }
                                            }
                                        } else {
                                            Log.d(
                                                "StfalconViewer",
                                                "⚠️ 고화질 캐시 없음, 다운로드 대기: ${newPhoto.name}"
                                            )
                                        }
                                    }
                                }
                            } else {
                                Log.e("StfalconViewer", "❌ 뷰어 참조를 찾을 수 없음")
                            }

                        val newPhoto = uiState.photos[position]
                            onPhotoChanged(newPhoto)

                            // 썸네일 갤러리 업데이트
                            thumbnailAdapter?.setSelectedPosition(position)
                            thumbnailRecyclerView?.scrollToPosition(position)

                            // 위치 변경 후 해당 사진의 고화질 이미지 다운로드 트리거
                            if (fullImageCache[newPhoto.path] == null && !loadingPhotos.contains(
                                    newPhoto.path
                                )
                            ) {
                                loadingPhotos.add(newPhoto.path)
                                Log.d("StfalconViewer", "🔄 썸네일 클릭 후 고화질 다운로드: ${newPhoto.path}")
                                viewModel?.downloadFullImage(newPhoto.path)
                            } else if (fullImageCache[newPhoto.path] != null) {
                                // 이미 고화질이 캐시에 있으면 즉시 적용
                                Log.d("StfalconViewer", "💾 캐시된 고화질 즉시 적용: ${newPhoto.name}")

                                // 백그라운드에서 비트맵 디코딩
                                CoroutineScope(Dispatchers.IO).launch {
                                    fullImageCache[newPhoto.path]?.let { imageData ->
                                        try {
                                            val bitmap = decodeBitmapWithExifRotation(
                                                imageData,
                                                newPhoto
                                            )
                                            if (bitmap != null && !bitmap.isRecycled) {
                                                // 메인 스레드에서 UI 업데이트
                                                CoroutineScope(Dispatchers.Main).launch {
                                                    imageViewRefs[newPhoto.path]?.let { imageView ->
                                                        imageView.setImageBitmap(bitmap)
                                                        imageView.scaleType =
                                                            ImageView.ScaleType.FIT_CENTER
                                                        highQualityUpdated.add(newPhoto.path)
                                                        Log.d(
                                                            "StfalconViewer",
                                                            "✅ 썸네일 클릭 후 고화질 적용 성공: ${newPhoto.name}"
                                                        )
                                                    }
                                                }
                                            } else {
                                                Log.d(
                                                    "StfalconViewer",
                                                    "🚫 Bitmap null or recycled"
                                                )
                                            }
                                        } catch (e: Exception) {
                                            Log.e(
                                                "StfalconViewer",
                                                "❌ 썸네일 클릭 후 고화질 적용 오류: ${newPhoto.path}",
                                                e
                                            )
                                        }
                                    }
                                }
                            }

                        // 인접 사진들도 미리 로드
                        CoroutineScope(Dispatchers.IO).launch {
                            delay(500) // 현재 사진 로드 완료 후
                            preloadAdjacentPhotosMinimal(
                                position,
                                uiState.photos,
                                fullImageCache,
                                viewModel,
                                loadingPhotos
                            )
                        }

                        Log.d("StfalconViewer", "✅ 뷰어 위치 변경 및 고화질 로드 트리거 완료: $position")
                    }

                    recyclerView.adapter = adapter
                    thumbnailAdapter = adapter
                    thumbnailRecyclerView = recyclerView
                    // 초기 선택 위치 설정
                    adapter.setSelectedPosition(currentPhotoIndex)
                    recyclerView.scrollToPosition(currentPhotoIndex)

                    // 무한 스크롤 리스너 추가
                    recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                            super.onScrolled(recyclerView, dx, dy)

                            val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                            val lastVisibleItemPosition =
                                layoutManager.findLastVisibleItemPosition()

                            // 썸네일 스크롤이 끝에 가까워지면 다음 페이지 로드 트리거
                            if (lastVisibleItemPosition >= 0 && uiState.photos.isNotEmpty()
                            ) {
                                Log.d(
                                    "StfalconViewer",
                                    "썸네일 스크롤: 마지막 보이는 인덱스=$lastVisibleItemPosition, 총 썸네일=${uiState.photos.size}개"
                                )

                                // ViewModel의 onPhotoIndexReached를 호출하여 다음 페이지 로드
                                viewModel?.onPhotoIndexReached(lastVisibleItemPosition)
                            }
                        }

                        override fun onScrollStateChanged(
                            recyclerView: RecyclerView,
                            newState: Int
                        ) {
                            super.onScrollStateChanged(recyclerView, newState)

                            // 스크롤이 정지했을 때 최종 위치 확인
                            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                                val layoutManager =
                                    recyclerView.layoutManager as LinearLayoutManager
                                val lastVisibleItemPosition =
                                    layoutManager.findLastVisibleItemPosition()

                                Log.d(
                                    "StfalconViewer",
                                    "썸네일 스크롤 정지: 마지막 위치=$lastVisibleItemPosition"
                                )

                                // 스크롤 정지 시에도 다음 페이지 로드 체크
                                viewModel?.onPhotoIndexReached(lastVisibleItemPosition)
                            }
                        }
                    })

                    // 사진 정보 버튼 클릭 리스너 설정
                    infoButton.setOnClickListener {
                        Log.d("StfalconViewer", "📋 사진 정보 버튼 클릭: ${photo.name}")
                        // 사진 정보 다이얼로그 표시
                        showPhotoInfoDialog(context, photo, viewModel)
                    }
                    
                    layout
                }
            )
            .withImageChangeListener { pos ->
                Log.d("StfalconViewer", "🎯 withImageChangeListener 호출됨: 위치 $pos")

                if (pos in uiState.photos.indices) {
                    val newPhoto = uiState.photos[pos]

                    Log.d("StfalconViewer", "📸 사진 변경됨: 인덱스 $pos → ${newPhoto.name}")

                    // UI 상태 즉시 업데이트 (메인 스레드에서 빠르게)
                    onPhotoChanged(newPhoto)

                    // 썸네일 갤러리 동기화
                    thumbnailAdapter?.setSelectedPosition(pos)
                    thumbnailRecyclerView?.scrollToPosition(pos)
                    Log.d("StfalconViewer", "🔄 썸네일 갤러리 동기화: 인덱스 $pos")

                    // 백그라운드에서 최소한의 미리 로딩만 수행 (성능 향상)
                    CoroutineScope(Dispatchers.IO).launch {
                        // 현재 사진만 우선 다운로드
                        if (fullImageCache[newPhoto.path] == null && !loadingPhotos.contains(newPhoto.path)) {
                            loadingPhotos.add(newPhoto.path)
                            Log.d("StfalconViewer", "🔄 현재 사진 우선 다운로드: ${newPhoto.path}")
                            viewModel?.downloadFullImage(newPhoto.path)
                        }

                        // 인접 사진은 더 긴 지연 후 다운로드 (슬라이딩 전환 완전 완료 대기)
                        delay(800) // 800ms 지연 - 전환 애니메이션 완료 대기
                        preloadAdjacentPhotosMinimal(
                            pos,
                            uiState.photos,
                            fullImageCache,
                            viewModel,
                            loadingPhotos
                        )
                    }
                }
            }
            .withDismissListener {
                // 뷰어 닫기 시 콜백
                Log.d("StfalconViewer", "❌ 뷰어 닫힘 - 정상적인 종료")
                loadingPhotos.clear() // 로딩 상태 초기화
                bitmapCache.clear() // 비트맵 캐시 정리
                imageViewRefs.clear() // ImageView 참조 정리
                highQualityUpdated.clear() // 고화질 업데이트 상태 정리
                thumbnailAdapter?.clearBitmapCache() // ThumbnailAdapter의 비트맵 캐시도 정리
                currentViewer = null
                onDismiss()
            }

        // 뷰어 표시
        val actualViewer = viewer.show()
        currentViewer = actualViewer
        Log.d("StfalconViewer", "뷰어 참조 저장 완료: ${currentViewer != null}")
        Log.d("StfalconViewer", "뷰어 표시 완료")
    }

    // 고화질 이미지 캐시가 업데이트되면 실시간으로 고화질 교체 - 디바운싱 추가
    LaunchedEffect(fullImageCache.keys) {
        // 캐시 업데이트를 디바운싱하여 안정화
        delay(200)

        fullImageCache.forEach { (photoPath, imageData) ->
            // 이미 고화질로 업데이트된 사진은 건너뛰기
            if (!highQualityUpdated.contains(photoPath)) {
                imageViewRefs[photoPath]?.let { imageView ->
                    Log.d("StfalconViewer", "🔄 실시간 고화질 교체 시작: $photoPath")

                    val cacheKey = "${photoPath}_full"
                    if (!bitmapCache.containsKey(cacheKey)) {
                        // 백그라운드에서 비트맵 디코딩
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val bitmap = decodeBitmapWithExifRotation(
                                    imageData,
                                    uiState.photos.find { it.path == photoPath })

                                // 메인 스레드에서 UI 업데이트 (전환 완료 후)
                                CoroutineScope(Dispatchers.Main).launch {
                                    delay(100) // 전환 완료 대기 시간 단축 (500ms → 100ms)

                                    if (bitmap != null && !bitmap.isRecycled) {
                                        imageView.setImageBitmap(bitmap)
                                        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
                                        highQualityUpdated.add(photoPath) // 중복 방지
                                        Log.d(
                                            "StfalconViewer",
                                            "✅ 실시간 고화질 교체 성공: ${photoPath.substringAfterLast("/")}"
                                        )
                                    } else {
                                        Log.d("StfalconViewer", "🚫 Bitmap recycled")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("StfalconViewer", "❌ 실시간 고화질 처리 오류: $photoPath", e)
                            }
                        }
                    }
                }
            }
        }
    }

    // ThumbnailAdapter에 고화질 캐시 업데이트를 전달 - 디바운싱 추가
    LaunchedEffect(fullImageCache.keys) {
        delay(150) // 캐시 업데이트 디바운싱
        thumbnailAdapter?.updateFullImageCache(fullImageCache)
    }

    // ViewModel의 photos 리스트 변경 감지하여 ThumbnailAdapter 업데이트 - 안정화
    LaunchedEffect(uiState.photos.size) {
        Log.d("StfalconViewer", "사진 리스트 크기 변경 감지: ${uiState.photos.size}개")

        // 상태 변경을 지연시켜 안정화
        delay(100)

        // ThumbnailAdapter에 새 사진 추가 알림
        thumbnailAdapter?.let { adapter ->
            adapter.refreshPhotos() // 새로고침
        }
    }

    // Compose가 dispose될 때 뷰어 정리
    DisposableEffect(Unit) {
        onDispose {
            Log.d("StfalconViewer", "🧹 Compose dispose - 뷰어 정리")
            currentViewer?.dismiss()
            currentViewer = null
            loadingPhotos.clear()
            bitmapCache.clear() // 비트맵 캐시도 정리
            imageViewRefs.clear() // ImageView 참조도 정리
            highQualityUpdated.clear() // 고화질 업데이트 상태도 정리
            thumbnailAdapter?.clearBitmapCache() // ThumbnailAdapter의 비트맵 캐시도 정리
            onDismiss()
        }
    }
}

/**
 * 최적화된 인접 사진 미리 로드 함수 - 중복 방지 및 제한적 로드
 */
private fun preloadAdjacentPhotosOptimized(
    currentPosition: Int,
    photos: List<CameraPhoto>,
    fullImageCache: Map<String, ByteArray>,
    viewModel: PhotoPreviewViewModel?,
    loadingPhotos: MutableSet<String>
) {
    val preloadRange = 1 // 앞뒤 1장씩만 미리 로드

    // 현재 사진 기준으로 앞뒤 1장씩만 체크
    val indicesToPreload = listOf(currentPosition - 1, currentPosition + 1)
        .filter { it in photos.indices && it != currentPosition }

    for (i in indicesToPreload) {
        val adjacentPhoto = photos[i]

        if (fullImageCache[adjacentPhoto.path] == null && !loadingPhotos.contains(adjacentPhoto.path)) {
            loadingPhotos.add(adjacentPhoto.path)
            viewModel?.downloadFullImage(adjacentPhoto.path)
        } else {
            Log.d("StfalconViewer", "⏭️ 인접 사진 미리 로드 건너뛰기: ${adjacentPhoto.name} (이미 캐시되거나 로딩 중)")
        }
    }
}

/**
 * ImageView에 이미지 데이터를 로드하는 함수
 * 고화질 이미지가 있으면 우선 표시, 없으면 썸네일 표시
 * 비트맵 디코딩은 백그라운드에서 처리하여 메인 스레드 차단 방지
 * EXIF 방향 정보를 고려한 회전 처리 추가
 */
private fun loadImageIntoView(
    imageView: ImageView,
    photo: CameraPhoto,
    fullImageData: ByteArray?,
    thumbnailData: ByteArray?,
    bitmapCache: MutableMap<String, Bitmap>,
    imageViewRefs: MutableMap<String, ImageView>,
    highQualityUpdated: MutableSet<String>
) {
    // ImageView 참조 저장 (실시간 고화질 업데이트용)
    imageViewRefs[photo.path] = imageView

    // 백그라운드에서 이미지 디코딩 처리
    CoroutineScope(Dispatchers.IO).launch {
        try {
            var selectedBitmap: Bitmap? = null
            var isHighQuality = false

            // 1. 고화질 이미지가 있으면 우선 처리
            if (fullImageData != null) {
                val fullCacheKey = "${photo.path}_full"
                var fullBitmap = bitmapCache[fullCacheKey]

                if (fullBitmap == null) {
                    fullBitmap = decodeBitmapWithExifRotation(fullImageData, photo)
                    if (fullBitmap != null) {
                        bitmapCache[fullCacheKey] = fullBitmap
                    }
                }

                if (fullBitmap != null && !fullBitmap.isRecycled) {
                    selectedBitmap = fullBitmap
                    isHighQuality = true
                    Log.d("StfalconViewer", "🖼️ 고화질 이미지 준비 완료 (회전 적용): ${photo.name}")
                }
            }

            // 2. 고화질이 없으면 썸네일 처리
            if (selectedBitmap == null && thumbnailData != null) {
                val thumbnailCacheKey = "${photo.path}_thumbnail"
                var thumbnailBitmap = bitmapCache[thumbnailCacheKey]

                if (thumbnailBitmap == null) {
                    thumbnailBitmap = decodeBitmapWithExifRotation(thumbnailData, photo)
                    if (thumbnailBitmap != null) {
                        bitmapCache[thumbnailCacheKey] = thumbnailBitmap
                    }
                }

                if (thumbnailBitmap != null && !thumbnailBitmap.isRecycled) {
                    selectedBitmap = thumbnailBitmap
                    Log.d("StfalconViewer", "📱 썸네일 준비 완료 (회전 적용): ${photo.name}")
                }
            }

            // 3. 메인 스레드에서 UI 업데이트 (비트맵이 준비된 후)
            CoroutineScope(Dispatchers.Main).launch {
                if (selectedBitmap != null && !selectedBitmap.isRecycled) {
                    imageView.setImageBitmap(selectedBitmap)
                    imageView.scaleType = ImageView.ScaleType.FIT_CENTER

                    if (isHighQuality) {
                        highQualityUpdated.add(photo.path)
                        Log.d("StfalconViewer", "✅ 고화질 이미지 표시 완료: ${photo.name}")
                    } else {
                        Log.d("StfalconViewer", "✅ 썸네일 표시 완료: ${photo.name}")
                    }
                } else {
                    // 플레이스홀더 설정
                    Log.w("StfalconViewer", "⚠️ 이미지 없음, 플레이스홀더 표시: ${photo.name}")
                    setPlaceholderImage(imageView)
                }
            }

        } catch (e: Exception) {
            Log.e("StfalconViewer", "❌ 이미지 로딩 에러: ${photo.name}", e)
            CoroutineScope(Dispatchers.Main).launch {
                setPlaceholderImage(imageView)
            }
        }
    }
}

/**
 * ByteArray에서 EXIF 방향 정보를 고려하여 비트맵을 디코딩하는 함수 (개선된 버전)
 */
private fun decodeBitmapWithExifRotation(
    imageData: ByteArray,
    photo: CameraPhoto? = null
): Bitmap? {
    return try {
        Log.d("StfalconViewer", "=== EXIF 디코딩 시작: ${photo?.name ?: "unknown"} ===")
        Log.d("StfalconViewer", "imageData size: ${imageData.size} bytes")
        Log.d("StfalconViewer", "photo.path: ${photo?.path}")

        // 1. 기본 비트맵 디코딩
        val originalBitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
            ?: return null

        Log.d("StfalconViewer", "원본 비트맵 크기: ${originalBitmap.width}x${originalBitmap.height}")

        // 2. EXIF 방향 정보 읽기 (원본 파일 우선, 실패 시 바이트 스트림)
        val orientation = try {
            // 원본 파일이 있고 존재하는 경우 파일에서 직접 읽기
            if (!photo?.path.isNullOrEmpty() && java.io.File(photo?.path ?: "").exists()) {
                Log.d("StfalconViewer", "원본 파일에서 EXIF 읽기 시도: ${photo!!.path}")
                val exif = androidx.exifinterface.media.ExifInterface(photo.path)
                val orientation = exif.getAttributeInt(
                    androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                )
                Log.d("StfalconViewer", "파일 EXIF 읽기 성공: orientation = $orientation")
                orientation
            } else {
                Log.d("StfalconViewer", "바이트 스트림에서 EXIF 읽기 시도")
                Log.d(
                    "StfalconViewer",
                    "파일 존재 여부: ${
                        if (photo?.path != null) java.io.File(photo.path)
                            .exists() else "path is null"
                    }"
                )

                // 원본 파일이 없으면 바이트 스트림에서 읽기
                val exif = androidx.exifinterface.media.ExifInterface(imageData.inputStream())
                val orientation = exif.getAttributeInt(
                    androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                )
                Log.d("StfalconViewer", "바이트 스트림 EXIF 읽기 성공: orientation = $orientation")
                orientation
            }
        } catch (e: Exception) {
            Log.e("StfalconViewer", "EXIF 읽기 실패: ${e.message}", e)
            androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
        }

        Log.d("StfalconViewer", "최종 EXIF Orientation: $orientation (${photo?.name ?: "unknown"})")

        // 3. 방향에 따른 회전 적용
        when (orientation) {
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> {
                Log.d("StfalconViewer", "90도 회전 적용: ${photo?.name}")
                val matrix = android.graphics.Matrix()
                matrix.postRotate(90f)
                Bitmap.createBitmap(
                    originalBitmap,
                    0,
                    0,
                    originalBitmap.width,
                    originalBitmap.height,
                    matrix,
                    true
                )
            }

            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> {
                Log.d("StfalconViewer", "180도 회전 적용: ${photo?.name}")
                val matrix = android.graphics.Matrix()
                matrix.postRotate(180f)
                Bitmap.createBitmap(
                    originalBitmap,
                    0,
                    0,
                    originalBitmap.width,
                    originalBitmap.height,
                    matrix,
                    true
                )
            }

            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> {
                Log.d("StfalconViewer", "270도 회전 적용: ${photo?.name}")
                val matrix = android.graphics.Matrix()
                matrix.postRotate(270f)
                Bitmap.createBitmap(
                    originalBitmap,
                    0,
                    0,
                    originalBitmap.width,
                    originalBitmap.height,
                    matrix,
                    true
                )
            }

            else -> {
                Log.d("StfalconViewer", "회전 없음: ${photo?.name} (orientation: $orientation)")
                originalBitmap
            }
        }
    } catch (e: Exception) {
        Log.e("StfalconViewer", "EXIF 회전 처리 완전 실패: ${photo?.name}", e)
        // EXIF 처리 실패 시 원본 디코딩 시도
        try {
            BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
        } catch (ex: Exception) {
            Log.e("StfalconViewer", "❌ 비트맵 디코딩 완전 실패", ex)
            null
        }
    }
}

/**
 * 인접 사진 미리 로드 함수 - 최소한의 로딩
 */
private fun preloadAdjacentPhotosMinimal(
    currentPosition: Int,
    photos: List<CameraPhoto>,
    fullImageCache: Map<String, ByteArray>,
    viewModel: PhotoPreviewViewModel?,
    loadingPhotos: MutableSet<String>
) {
    val preloadRange = 1 // 앞뒤 1장씩만 미리 로드

    // 현재 사진의 바로 앞뒤 사진만 체크
    val indicesToPreload = listOf(currentPosition - 1, currentPosition + 1)
        .filter { it in photos.indices }

    for (index in indicesToPreload) {
        val adjacentPhoto = photos[index]

        if (fullImageCache[adjacentPhoto.path] == null && !loadingPhotos.contains(adjacentPhoto.path)) {
            loadingPhotos.add(adjacentPhoto.path)
            viewModel?.downloadFullImage(adjacentPhoto.path)
        }
    }
}

/**
 * 플레이스홀더 이미지 설정
 */
private fun setPlaceholderImage(imageView: ImageView) {
    // 기본 플레이스홀더 설정
    imageView.setImageResource(android.R.drawable.ic_menu_gallery)
    imageView.scaleType = ImageView.ScaleType.CENTER
}

class ThumbnailAdapter(
    private val viewModel: PhotoPreviewViewModel?,
    private val thumbnailCache: Map<String, ByteArray>,
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<ThumbnailAdapter.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_PHOTO = 0
        private const val VIEW_TYPE_LOADING = 1
    }

    private var selectedPosition = 0
    private var lastClickTime = 0L
    private val clickDebounceTime = 300L // 300ms 디바운스

    // 고화질 캐시에 접근하기 위한 참조 추가
    private var fullImageCache: Map<String, ByteArray> = emptyMap()

    // 비트맵 캐시 추가 (메모리 효율성을 위해)
    private val bitmapCache = mutableMapOf<String, Bitmap>()

    // 사진 리스트를 캐시하여 빠른 접근과 안정성 확보
    private var cachedPhotos: List<CameraPhoto> = emptyList()
    private var lastUpdateTime = 0L
    private val updateThrottleTime = 50L // 50ms 스로틀

    // 로딩 상태 추가
    private var isLoading = false
    private var hasNextPage = true

    // ViewModel에서 안전하게 photos 리스트 가져오기
    private val photos: List<CameraPhoto>
        get() {
            val currentTime = System.currentTimeMillis()
            // 스로틀링: 너무 자주 업데이트하지 않도록 제한
            if (currentTime - lastUpdateTime > updateThrottleTime) {
                val newPhotos = viewModel?.uiState?.value?.photos ?: emptyList()
                if (newPhotos != cachedPhotos) {
                    cachedPhotos = newPhotos
                    lastUpdateTime = currentTime
                    
                    // UI 상태도 동시에 업데이트
                    val uiState = viewModel?.uiState?.value
                    isLoading = uiState?.isLoading == true || uiState?.isLoadingMore == true
                    hasNextPage = uiState?.hasNextPage == true
                }
            }
            return cachedPhotos
        }

    /**
     * 사진 리스트 강제 업데이트 (외부에서 호출)
     */
    fun refreshPhotos() {
        val oldSize = cachedPhotos.size
        val oldIsLoading = isLoading

        cachedPhotos = viewModel?.uiState?.value?.photos ?: emptyList()
        val uiState = viewModel?.uiState?.value
        isLoading = uiState?.isLoading == true || uiState?.isLoadingMore == true
        hasNextPage = uiState?.hasNextPage == true
        lastUpdateTime = System.currentTimeMillis()

        Log.d(
            "ThumbnailAdapter",
            "사진 리스트 새로고침: $oldSize → ${cachedPhotos.size}, 로딩: $oldIsLoading → $isLoading"
        )

        // 크기가 변경되거나 로딩 상태가 변경된 경우 업데이트
        if (oldSize != cachedPhotos.size || oldIsLoading != isLoading) {
            notifyDataSetChanged()
        }
    }

    fun updateFullImageCache(cache: Map<String, ByteArray>) {
        val oldCache = fullImageCache
        fullImageCache = cache

        Log.d("ThumbnailAdapter", "=== 고화질 캐시 업데이트 ===")
        Log.d("ThumbnailAdapter", "이전 캐시 크기: ${oldCache.size}, 새 캐시 크기: ${cache.size}")
        Log.d("ThumbnailAdapter", "캐시된 사진들: ${cache.keys.map { it.substringAfterLast("/") }}")

        // 새로 추가된 고화질 이미지만 개별적으로 업데이트 (깜빡임 방지)
        if (cache.size > oldCache.size) {
            val newItems = cache.keys - oldCache.keys
            Log.d("ThumbnailAdapter", "새로운 고화질 데이터 감지: ${newItems.map { it.substringAfterLast("/") }}")
            
            // 새로 추가된 아이템들의 위치를 찾아서 개별 업데이트
            newItems.forEach { photoPath ->
                val position = photos.indexOfFirst { it.path == photoPath }
                if (position != -1) {
                    Log.d("ThumbnailAdapter", "개별 아이템 업데이트: 위치 $position, 파일: ${photoPath.substringAfterLast("/")}")
                    notifyItemChanged(position)
                }
            }
        }
    }

    /**
     * 리스트 크기가 변경되었을 때 호출 (새 사진 추가 시)
     */
    fun notifyPhotosUpdated(oldSize: Int, newSize: Int) {
        when {
            newSize > oldSize -> {
                // 새 아이템 추가됨
                Log.d("ThumbnailAdapter", "새 사진 추가: $oldSize → $newSize")
                notifyItemRangeInserted(oldSize, newSize - oldSize)
            }

            newSize < oldSize -> {
                // 아이템 제거됨
                Log.d("ThumbnailAdapter", "사진 제거: $oldSize → $newSize")
                notifyItemRangeRemoved(newSize, oldSize - newSize)
            }

            else -> {
                // 크기는 같지만 내용이 변경됨
                Log.d("ThumbnailAdapter", "사진 리스트 내용 변경")
                notifyDataSetChanged()
            }
        }
    }

    /**
     * 비트맵 캐시 정리 (메모리 누수 방지)
     */
    fun clearBitmapCache() {
        bitmapCache.values.forEach { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        bitmapCache.clear()
        Log.d("ThumbnailAdapter", "비트맵 캐시 정리 완료")
    }

    fun setSelectedPosition(position: Int) {
        val previousPosition = selectedPosition
        selectedPosition = position
        notifyItemChanged(previousPosition)
        notifyItemChanged(selectedPosition)
    }

    override fun getItemViewType(position: Int): Int {
        return if (position < photos.size) VIEW_TYPE_PHOTO else VIEW_TYPE_LOADING
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return when (viewType) {
            VIEW_TYPE_PHOTO -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.thumbnail_item, parent, false)
                ViewHolder(view, VIEW_TYPE_PHOTO)
            }

            VIEW_TYPE_LOADING -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.thumbnail_loading_item, parent, false)
                ViewHolder(view, VIEW_TYPE_LOADING)
            }

            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when (holder.viewType) {
            VIEW_TYPE_PHOTO -> bindPhotoItem(holder, position)
            VIEW_TYPE_LOADING -> bindLoadingItem(holder)
        }
    }

    override fun getItemCount(): Int {
        val photoCount = photos.size
        return if (isLoading && photoCount > 0 && hasNextPage) {
            photoCount + 1 // 로딩 아이템 추가
        } else {
            photoCount
        }
    }

    private fun bindPhotoItem(holder: ViewHolder, position: Int) {
        // 실시간으로 ViewModel에서 photos 가져오기
        val currentPhotos = photos
        if (position >= currentPhotos.size) {
            Log.w("ThumbnailAdapter", "잘못된 position: $position, 총 사진: ${currentPhotos.size}")
            return
        }

        val photo = currentPhotos[position]
        val thumbnailData = thumbnailCache[photo.path] ?: viewModel?.getThumbnail(photo.path)
        val fullImageData = fullImageCache[photo.path]

        // 비트맵 캐시 키 생성
        val thumbnailCacheKey = "${photo.path}_thumbnail"
        val highQualityCacheKey = "${photo.path}_highquality"
        
        // 이미 처리된 고화질 비트맵이 있는지 확인
        val hasProcessedHighQuality = bitmapCache.containsKey(highQualityCacheKey)
        val hasProcessedThumbnail = bitmapCache.containsKey(thumbnailCacheKey)

        // 로그는 새로 처리되는 경우에만 출력
        if (!hasProcessedHighQuality && !hasProcessedThumbnail) {
            Log.d("ThumbnailAdapter", "=== 새 썸네일 처리: ${photo.name} (position: $position) ===")
            Log.d("ThumbnailAdapter", "thumbnailData: ${thumbnailData?.size ?: 0} bytes")
            Log.d("ThumbnailAdapter", "fullImageData: ${fullImageData?.size ?: 0} bytes")
        }

        // EXIF 처리를 위해 고화질 데이터 우선 사용
        if (thumbnailData != null) {
            // 고화질 버전이 있고 아직 처리되지 않은 경우에만 새로 처리
            if (fullImageData != null && !hasProcessedHighQuality) {
                Log.d("ThumbnailAdapter", "고화질 데이터로 새 비트맵 생성: ${photo.name}")
                
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val bitmap = decodeThumbnailWithFullImageExif(
                            thumbnailData,
                            fullImageData,
                            photo
                        )
                        
                        if (bitmap != null && !bitmap.isRecycled) {
                            // 고화질 버전으로 캐시에 저장
                            bitmapCache[highQualityCacheKey] = bitmap
                            // 기존 썸네일 캐시가 있으면 제거 (고화질로 대체)
                            bitmapCache.remove(thumbnailCacheKey)
                            
                            CoroutineScope(Dispatchers.Main).launch {
                                holder.imageView?.setImageBitmap(bitmap)
                                holder.imageView?.scaleType = ImageView.ScaleType.CENTER_CROP
                                Log.d("ThumbnailAdapter", "고화질 썸네일 적용 완료: ${photo.name}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ThumbnailAdapter", "고화질 썸네일 처리 에러: ${photo.name}", e)
                    }
                }
            }
            // 고화질이 없고 기본 썸네일도 처리되지 않은 경우에만 처리
            else if (fullImageData == null && !hasProcessedThumbnail) {
                Log.d("ThumbnailAdapter", "기본 썸네일로 새 비트맵 생성: ${photo.name}")
                
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val bitmap = decodeBitmapWithExifRotation(thumbnailData, photo)
                        
                        if (bitmap != null && !bitmap.isRecycled) {
                            bitmapCache[thumbnailCacheKey] = bitmap
                            
                            CoroutineScope(Dispatchers.Main).launch {
                                holder.imageView?.setImageBitmap(bitmap)
                                holder.imageView?.scaleType = ImageView.ScaleType.CENTER_CROP
                                Log.d("ThumbnailAdapter", "기본 썸네일 적용 완료: ${photo.name}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ThumbnailAdapter", "기본 썸네일 처리 에러: ${photo.name}", e)
                    }
                }
            }
            // 이미 처리된 비트맵이 있으면 캐시에서 재사용
            else {
                val cachedBitmap = bitmapCache[highQualityCacheKey] ?: bitmapCache[thumbnailCacheKey]
                if (cachedBitmap != null && !cachedBitmap.isRecycled) {
                    holder.imageView?.setImageBitmap(cachedBitmap)
                    holder.imageView?.scaleType = ImageView.ScaleType.CENTER_CROP
                } else {
                    // 캐시된 비트맵이 recycled된 경우 플레이스홀더 표시
                    holder.imageView?.setImageResource(android.R.drawable.ic_menu_gallery)
                }
            }
        } else {
            // 썸네일 데이터가 없으면 플레이스홀더 표서
            holder.imageView?.setImageResource(android.R.drawable.ic_menu_gallery)
        }

        // 현재 선택된 썸네일 강조표시
        if (position == selectedPosition) {
            holder.imageView?.setBackgroundResource(R.drawable.thumbnail_selected_background)
            holder.imageView?.alpha = 1.0f
        } else {
            holder.imageView?.setBackgroundResource(R.drawable.thumbnail_background)
            holder.imageView?.alpha = 0.7f
        }

        holder.itemView.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime < clickDebounceTime) {
                Log.d("ThumbnailAdapter", "빠른 클릭 무시: ${photo.name}")
                return@setOnClickListener
            }
            lastClickTime = currentTime

            Log.d("ThumbnailAdapter", "썸네일 클릭: ${photo.name} (position: $position)")
            onClick(position)
        }
    }

    private fun bindLoadingItem(holder: ViewHolder) {
        // 로딩 아이템은 별도 처리 불필요 (XML 레이아웃에서 처리)
        Log.d("ThumbnailAdapter", "로딩 아이템 표시")
    }

    class ViewHolder(itemView: View, val viewType: Int) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView? = if (viewType == VIEW_TYPE_PHOTO) {
            itemView.findViewById(R.id.image_view)
        } else {
            null
        }
    }
}

/**
 * ThumbnailAdapter의 onBindViewHolder에서 사용하는 함수
 * 고화질 이미지의 EXIF 정보를 썸네일에 적용
 */
private fun decodeThumbnailWithFullImageExif(
    thumbnailData: ByteArray,
    fullImageData: ByteArray,
    photo: CameraPhoto
): Bitmap? {
    // 고화질 이미지에서 EXIF 읽기
    val fullExif = try {
        // 원본 파일이 있고 존재하는 경우 파일에서 직접 읽기
        val exif = androidx.exifinterface.media.ExifInterface(fullImageData.inputStream())
        exif.getAttributeInt(
            androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
            androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
        )
    } catch (e: Exception) {
        Log.e("StfalconViewer", "고화질 EXIF 읽기 실패: ${photo.name}", e)
        androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
    }

    // 썸네일 이미지 디코딩
    val thumbnailBitmap = decodeBitmapWithExifRotation(thumbnailData, photo)

    // 고화질 이미지의 EXIF 정보를 썸네일에 적용
    return when (fullExif) {
        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> {
            val matrix = android.graphics.Matrix()
            matrix.postRotate(90f)
            Bitmap.createBitmap(
                thumbnailBitmap!!,
                0,
                0,
                thumbnailBitmap.width,
                thumbnailBitmap.height,
                matrix,
                true
            )
        }

        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> {
            val matrix = android.graphics.Matrix()
            matrix.postRotate(180f)
            Bitmap.createBitmap(
                thumbnailBitmap!!,
                0,
                0,
                thumbnailBitmap.width,
                thumbnailBitmap.height,
                matrix,
                true
            )
        }

        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> {
            val matrix = android.graphics.Matrix()
            matrix.postRotate(270f)
            Bitmap.createBitmap(
                thumbnailBitmap!!,
                0,
                0,
                thumbnailBitmap.width,
                thumbnailBitmap.height,
                matrix,
                true
            )
        }

        else -> thumbnailBitmap
    }
}

/**
 * 사진 정보 다이얼로그를 표시하는 함수
 */
private fun showPhotoInfoDialog(
    context: Context,
    photo: CameraPhoto,
    viewModel: PhotoPreviewViewModel?
) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            // EXIF 정보 가져오기
            val exifInfo = viewModel?.getCameraPhotoExif(photo.path)
            
            withContext(Dispatchers.Main) {
                val dialogView = LayoutInflater.from(context)
                    .inflate(R.layout.dialog_photo_info, null)
                
                // 기본 정보 설정
                dialogView.findViewById<TextView>(R.id.tv_photo_name).text = photo.name
                dialogView.findViewById<TextView>(R.id.tv_photo_path).text = photo.path
                dialogView.findViewById<TextView>(R.id.tv_photo_size).text = 
                    "${photo.size} bytes (${String.format("%.2f MB", photo.size / 1024.0 / 1024.0)})"
                
                // 수정 시간 포맷팅
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val formattedDate = try {
                    dateFormat.format(Date(photo.date * 1000L))
                } catch (e: Exception) {
                    "알 수 없음"
                }
                dialogView.findViewById<TextView>(R.id.tv_photo_date).text = formattedDate
                
                // EXIF 정보 파싱 및 표시
                val exifContainer = dialogView.findViewById<LinearLayout>(R.id.exif_container)
                if (!exifInfo.isNullOrEmpty() && exifInfo != "{}") {
                    try {
                        // 간단한 JSON 파싱 (org.json 사용 없이)
                        parseAndDisplayExif(exifInfo, exifContainer, context)
                    } catch (e: Exception) {
                        Log.e("PhotoInfo", "EXIF 파싱 오류", e)
                        addExifItem(exifContainer, context, "EXIF 정보", "파싱 오류")
                    }
                } else {
                    addExifItem(exifContainer, context, "EXIF 정보", "없음")
                }
                
                AlertDialog.Builder(context)
                    .setTitle("사진 정보")
                    .setView(dialogView)
                    .setPositiveButton("확인") { dialog, _ -> dialog.dismiss() }
                    .show()
            }
        } catch (e: Exception) {
            Log.e("PhotoInfo", "사진 정보 로드 오류", e)
            withContext(Dispatchers.Main) {
                AlertDialog.Builder(context)
                    .setTitle("오류")
                    .setMessage("사진 정보를 불러올 수 없습니다.")
                    .setPositiveButton("확인") { dialog, _ -> dialog.dismiss() }
                    .show()
            }
        }
    }
}

/**
 * EXIF 정보를 파싱하여 표시하는 함수
 */
private fun parseAndDisplayExif(
    exifJson: String,
    container: LinearLayout,
    context: Context
) {
    // 간단한 JSON 파싱
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
    
    if (entries.isNotEmpty()) {
        entries["width"]?.let { width ->
            addExifItem(container, context, "너비", "${width}px")
        }
        entries["height"]?.let { height ->
            addExifItem(container, context, "높이", "${height}px")
        }
        entries["orientation"]?.let { orientation ->
            val orientationText = when (orientation) {
                "1" -> "정상 (0°)"
                "3" -> "180° 회전"
                "6" -> "시계방향 90° 회전"
                "8" -> "반시계방향 90° 회전"
                else -> "알 수 없음 ($orientation)"
            }
            addExifItem(container, context, "방향", orientationText)
        }
    } else {
        addExifItem(container, context, "EXIF 정보", "없음")
    }
}

/**
 * EXIF 항목을 추가하는 헬퍼 함수
 */
private fun addExifItem(
    container: LinearLayout,
    context: Context,
    label: String,
    value: String
) {
    val itemView = LayoutInflater.from(context)
        .inflate(R.layout.item_photo_info, container, false)
    
    itemView.findViewById<TextView>(R.id.tv_info_label).text = label
    itemView.findViewById<TextView>(R.id.tv_info_value).text = value
    
    container.addView(itemView)
}