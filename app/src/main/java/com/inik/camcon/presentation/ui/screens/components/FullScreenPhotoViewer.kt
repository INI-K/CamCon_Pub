package com.inik.camcon.presentation.ui.screens.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
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
    photos: List<CameraPhoto>,
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
    
    // 현재 사진 인덱스 찾기
    val currentPhotoIndex = photos.indexOfFirst { it.path == photo.path }
    
    // ViewModel의 캐시 상태 관찰
    val fullImageCache by viewModel?.fullImageCache?.collectAsState() ?: remember { 
        mutableStateOf(emptyMap<String, ByteArray>()) 
    }
    
    // 로드 중인 사진들을 추적하여 중복 로드 방지
    val loadingPhotos = remember { mutableSetOf<String>() }
    
    // 현재 표시 중인 뷰어 인스턴스 추적
    var currentViewer: Any? by remember { mutableStateOf(null) }

    // 이미지 로딩 성능 개선을 위한 비트맵 캐시
    val bitmapCache = remember { mutableMapOf<String, Bitmap>() }

    // 현재 보이는 사진들의 ImageView 참조를 저장 (실시간 업데이트용)
    val imageViewRefs = remember { mutableMapOf<String, ImageView>() }

    // 고화질 업데이트가 완료된 사진들을 추적 (중복 방지)
    val highQualityUpdated = remember { mutableSetOf<String>() }

    // 뷰어 초기화를 한 번만 수행하도록 개선
    LaunchedEffect(Unit) { // photo.path 대신 Unit 사용으로 한 번만 실행
        // 이전 뷰어가 있으면 먼저 닫기
        (currentViewer as? com.stfalcon.imageviewer.StfalconImageViewer<*>)?.dismiss()
        
        // 이미지 뷰어 생성 및 실행
        val viewer = StfalconImageViewer.Builder<CameraPhoto>(
            context,
            photos
        ) { imageView, cameraPhoto ->
            // 캐시에서 이미지 데이터를 가져오기 (ViewModel보다 전달받은 캐시 우선)
            val photoThumbnail =
                thumbnailCache[cameraPhoto.path] ?: viewModel?.getThumbnail(cameraPhoto.path)
            val photoFullImage = fullImageCache[cameraPhoto.path]

            // 현재 보이는 사진과 인접한 사진만 로그 출력 (성능 개선)
            val photoIndex = photos.indexOf(cameraPhoto)
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
            .withHiddenStatusBar(true) // 상태바 숨기기
            .allowSwipeToDismiss(true) // 스와이프로 닫기 허용
            .allowZooming(true) // 줌 허용
            .withImageChangeListener { position ->
                // 사진 변경 시 콜백 - 성능 최적화
                if (position in photos.indices) {
                    val newPhoto = photos[position]

                    Log.d("StfalconViewer", "📸 사진 변경됨: 인덱스 $position → ${newPhoto.name}")

                    // UI 상태 즉시 업데이트 (메인 스레드에서 빠르게)
                    onPhotoChanged(newPhoto)

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
                            position,
                            photos,
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
                currentViewer = null
                onDismiss()
            }

        // 뷰어 표시
        currentViewer = viewer
        viewer.show()
    }

    // 고화질 이미지 캐시가 업데이트되면 실시간으로 고화질 교체
    LaunchedEffect(fullImageCache) {
        fullImageCache.forEach { (photoPath, imageData) ->
            // 이미 고화질로 업데이트된 사진은 건너뛰기
            if (!highQualityUpdated.contains(photoPath)) {
                imageViewRefs[photoPath]?.let { imageView ->
                    Log.d("StfalconViewer", "🔄 실시간 고화질 교체 시작: $photoPath")

                    val cacheKey = "${photoPath}_full"
                    if (!bitmapCache.containsKey(cacheKey)) {
                        try {
                            val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
                            if (bitmap != null && !bitmap.isRecycled) {
                                bitmapCache[cacheKey] = bitmap
                                highQualityUpdated.add(photoPath) // 중복 방지

                                // 메인 스레드에서 UI 업데이트 (전환 완료 후)
                                CoroutineScope(Dispatchers.Main).launch {
                                    delay(500) // 전환 완료 대기

                                    if (!bitmap.isRecycled) {
                                        imageView.setImageBitmap(bitmap)
                                        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
                                        Log.d(
                                            "StfalconViewer",
                                            "✅ 실시간 고화질 교체 성공: ${photoPath.substringAfterLast("/")}"
                                        )
                                    }
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

    // Compose가 dispose될 때 뷰어 정리
    DisposableEffect(Unit) {
        onDispose {
            Log.d("StfalconViewer", "🧹 Compose dispose - 뷰어 정리")
            (currentViewer as? com.stfalcon.imageviewer.StfalconImageViewer<*>)?.dismiss()
            currentViewer = null
            loadingPhotos.clear()
            bitmapCache.clear() // 비트맵 캐시도 정리
            imageViewRefs.clear() // ImageView 참조도 정리
            highQualityUpdated.clear() // 고화질 업데이트 상태도 정리
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
 * 썸네일만 즉시 표시하고 고화질은 실시간 업데이트로 처리
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
    try {
        // 1. 썸네일이 있으면 즉시 표시 (빠른 반응성)
        if (thumbnailData != null) {
            val thumbnailCacheKey = "${photo.path}_thumbnail"
            var thumbnailBitmap = bitmapCache[thumbnailCacheKey]

            if (thumbnailBitmap == null) {
                thumbnailBitmap =
                    BitmapFactory.decodeByteArray(thumbnailData, 0, thumbnailData.size)
                if (thumbnailBitmap != null) {
                    bitmapCache[thumbnailCacheKey] = thumbnailBitmap
                }
            }

            if (thumbnailBitmap != null) {
                imageView.setImageBitmap(thumbnailBitmap)
                imageView.scaleType = ImageView.ScaleType.FIT_CENTER
                Log.d("StfalconViewer", "📱 썸네일 표시: ${photo.name}")
            }
        } else {
            // 썸네일이 없으면 플레이스홀더
            Log.w("StfalconViewer", "⚠️ 썸네일 없음: ${photo.name}")
            setPlaceholderImage(imageView)
        }

        // ImageView 참조 저장 (실시간 고화질 업데이트용)
        imageViewRefs[photo.path] = imageView

    } catch (e: Exception) {
        Log.e("StfalconViewer", "❌ 이미지 로딩 에러: ${photo.name}", e)
        setPlaceholderImage(imageView)
    }
}

/**
 * 인접 사진 미리 로딩 최적화 함수 - 최소한의 로딩
 */
private fun preloadAdjacentPhotosMinimal(
    currentPosition: Int,
    photos: List<CameraPhoto>,
    fullImageCache: Map<String, ByteArray>,
    viewModel: PhotoPreviewViewModel?,
    loadingPhotos: MutableSet<String>
) {
    // 현재 사진의 바로 앞뒤 사진만 체크
    val adjacentIndices = listOf(currentPosition - 1, currentPosition + 1)
        .filter { it in photos.indices }

    for (index in adjacentIndices) {
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