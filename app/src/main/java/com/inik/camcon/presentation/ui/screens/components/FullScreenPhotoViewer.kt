package com.inik.camcon.presentation.ui.screens.components

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
    
    Log.d("StfalconViewer", "=== StfalconImageViewer 시작 ===")
    Log.d("StfalconViewer", "사진: ${photo.name}, 인덱스: $currentPhotoIndex")
    Log.d("StfalconViewer", "전체 사진 수: ${photos.size}")
    Log.d("StfalconViewer", "미리 로드된 썸네일: ${thumbnailCache.size}개")
    
    LaunchedEffect(photo.path) {
        Log.d("StfalconViewer", "🚀 StfalconImageViewer 실행")
        
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
            
            // 로딩 최적화: 현재 보이는 사진과 인접한 사진만 로그 출력
            val isCurrentOrAdjacent = kotlin.math.abs(photos.indexOf(cameraPhoto) - currentPhotoIndex) <= 1
            if (isCurrentOrAdjacent) {
                Log.d("StfalconViewer", "📸 이미지 로더 호출: ${cameraPhoto.name}")
                Log.d("StfalconViewer", "  - 썸네일: ${photoThumbnail?.size ?: 0} bytes (캐시에서 가져옴)")
                Log.d("StfalconViewer", "  - 고화질: ${photoFullImage?.size ?: 0} bytes")
            }
            
            loadImageIntoView(imageView, cameraPhoto, photoFullImage, photoThumbnail)
            
            // 현재 보이는 사진과 바로 인접한 사진만 고화질 다운로드
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
                // 사진 변경 시 콜백 - 디바운싱 적용
                Log.d("StfalconViewer", "📸 사진 변경됨: 인덱스 $position")
                if (position in photos.indices) {
                    val newPhoto = photos[position]
                    
                    // UI 상태 즉시 업데이트 (매끄러운 전환을 위해)
                    onPhotoChanged(newPhoto)
                    
                    // 백그라운드에서 고화질 이미지 로드
                    CoroutineScope(Dispatchers.IO).launch {
                        if (fullImageCache[newPhoto.path] == null && !loadingPhotos.contains(newPhoto.path)) {
                            loadingPhotos.add(newPhoto.path)
                            Log.d("StfalconViewer", "🔄 새 사진 고화질 이미지 다운로드: ${newPhoto.path}")
                            viewModel?.downloadFullImage(newPhoto.path)
                        }
                        
                        // 인접 사진들을 백그라운드에서 미리 로드
                        preloadAdjacentPhotosOptimized(position, photos, fullImageCache, viewModel, loadingPhotos)
                    }
                }
            }
            .withDismissListener {
                // 뷰어 닫기 시 콜백
                Log.d("StfalconViewer", "❌ 뷰어 닫힘 - 정상적인 종료")
                loadingPhotos.clear() // 로딩 상태 초기화
                currentViewer = null
                onDismiss()
            }

        // 뷰어 표시
        currentViewer = viewer
        viewer.show()
    }
    
    // Compose가 dispose될 때 뷰어 정리
    DisposableEffect(Unit) {
        onDispose {
            Log.d("StfalconViewer", "🧹 Compose dispose - 뷰어 정리")
            (currentViewer as? com.stfalcon.imageviewer.StfalconImageViewer<*>)?.dismiss()
            currentViewer = null
            loadingPhotos.clear()
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

    Log.d("StfalconViewer", "🔄 인접 사진 미리 로드 체크: 인덱스 $indicesToPreload")

    for (i in indicesToPreload) {
        val adjacentPhoto = photos[i]

        // 이미 캐시에 있거나 로딩 중이면 건너뛰기
        if (fullImageCache[adjacentPhoto.path] == null && !loadingPhotos.contains(adjacentPhoto.path)) {
            loadingPhotos.add(adjacentPhoto.path)
            Log.d("StfalconViewer", "🔄 인접 사진 미리 로드 시작: ${adjacentPhoto.name} (인덱스 $i)")
            viewModel?.downloadFullImage(adjacentPhoto.path)
        } else {
            Log.d("StfalconViewer", "⏭️ 인접 사진 미리 로드 건너뛰기: ${adjacentPhoto.name} (이미 캐시되거나 로딩 중)")
        }
    }
}

/**
 * ImageView에 이미지 데이터를 로드하는 함수
 */
private fun loadImageIntoView(
    imageView: ImageView,
    photo: CameraPhoto,
    fullImageData: ByteArray?,
    thumbnailData: ByteArray?
) {
    try {
        // 고해상도 이미지가 있으면 우선 사용, 없으면 썸네일 사용
        val imageData = fullImageData ?: thumbnailData

        if (imageData != null) {
            val imageType = if (fullImageData != null) "고화질" else "썸네일"
            Log.d("StfalconViewer", "🖼️ $imageType 이미지 로딩: ${photo.name} (${imageData.size} bytes)")

            val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap)
                imageView.scaleType = ImageView.ScaleType.FIT_CENTER
                Log.d("StfalconViewer", "✅ $imageType 이미지 로딩 성공: ${bitmap.width}x${bitmap.height}")
            } else {
                Log.e("StfalconViewer", "❌ 비트맵 디코딩 실패: ${photo.name}")
                setPlaceholderImage(imageView)
            }
        } else {
            Log.w("StfalconViewer", "⚠️ 이미지 데이터 없음: ${photo.name}")
            setPlaceholderImage(imageView)
        }
    } catch (e: Exception) {
        Log.e("StfalconViewer", "❌ 이미지 로딩 에러: ${photo.name}", e)
        setPlaceholderImage(imageView)
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