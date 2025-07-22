package com.inik.camcon.presentation.ui.screens.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
            .withBackgroundColor(0xFF121212.toInt()) // 다크 테마 배경색 설정 (상태바와 어우러지도록)
            .withHiddenStatusBar(false) // 상태바를 숨기지 않고 투명하게 처리
            .allowSwipeToDismiss(true) // 스와이프로 닫기 허용
            .allowZooming(true) // 줌 허용
            .withOverlayView(
                run {
                    val layout = LayoutInflater.from(context)
                        .inflate(R.layout.thumbnail_gallery, null) as ViewGroup
                    val recyclerView = layout.findViewById<RecyclerView>(R.id.recycler_view)
                    recyclerView.layoutManager =
                        LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                    val adapter = ThumbnailAdapter(photos, thumbnailCache, viewModel) { position ->
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
                                if (position in photos.indices) {
                                    Log.d("StfalconViewer", "🔄 수동 이미지 변경 리스너 실행: 위치 $position")

                                    // 고화질 이미지가 캐시에 있으면 즉시 적용
                                    val newPhoto = photos[position]
                                    if (fullImageCache[newPhoto.path] != null) {
                                        // 백그라운드에서 비트맵 디코딩
                                        CoroutineScope(Dispatchers.IO).launch {
                                            fullImageCache[newPhoto.path]?.let { imageData ->
                                                try {
                                                    val bitmap = decodeBitmapWithExifRotation(
                                                        imageData,
                                                        photos.find { it.path == newPhoto.path })

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

                        val newPhoto = photos[position]
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
                                            photos.find { it.path == newPhoto.path }
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
                                photos,
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
                    layout
                }
            )
            .withImageChangeListener { pos ->
                Log.d("StfalconViewer", "🎯 withImageChangeListener 호출됨: 위치 $pos")

                if (pos in photos.indices) {
                    val newPhoto = photos[pos]

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
        val actualViewer = viewer.show()
        currentViewer = actualViewer
        Log.d("StfalconViewer", "뷰어 참조 저장 완료: ${currentViewer != null}")
        Log.d("StfalconViewer", "뷰어 표시 완료")
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
                        // 백그라운드에서 비트맵 디코딩
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val bitmap = decodeBitmapWithExifRotation(
                                    imageData,
                                    photos.find { it.path == photoPath })

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

    // ThumbnailAdapter에 고화질 캐시 업데이트를 전달
    LaunchedEffect(fullImageCache) {
        thumbnailAdapter?.updateFullImageCache(fullImageCache)
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
    private val photos: List<CameraPhoto>,
    private val thumbnailCache: Map<String, ByteArray>,
    private val viewModel: PhotoPreviewViewModel?,
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<ThumbnailAdapter.ViewHolder>() {

    private var selectedPosition = 0
    private var lastClickTime = 0L
    private val clickDebounceTime = 300L // 300ms 디바운스

    // 고화질 캐시에 접근하기 위한 참조 추가
    private var fullImageCache: Map<String, ByteArray> = emptyMap()

    fun updateFullImageCache(cache: Map<String, ByteArray>) {
        val oldSize = fullImageCache.size
        fullImageCache = cache
        val newSize = fullImageCache.size

        Log.d("ThumbnailAdapter", "=== 고화질 캐시 업데이트 ===")
        Log.d("ThumbnailAdapter", "이전 캐시 크기: $oldSize, 새 캐시 크기: $newSize")
        Log.d("ThumbnailAdapter", "캐시된 사진들: ${cache.keys.map { it.substringAfterLast("/") }}")

        // 캐시가 업데이트되면 전체 어댑터를 새로고침
        if (newSize > oldSize) {
            Log.d("ThumbnailAdapter", "새로운 고화질 데이터 감지, 어댑터 새로고침")
            notifyDataSetChanged()
        }
    }

    fun setSelectedPosition(position: Int) {
        val previousPosition = selectedPosition
        selectedPosition = position
        notifyItemChanged(previousPosition)
        notifyItemChanged(selectedPosition)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.thumbnail_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val photo = photos[position]
        val thumbnailData = thumbnailCache[photo.path] ?: viewModel?.getThumbnail(photo.path)
        val fullImageData = fullImageCache[photo.path]

        Log.d("ThumbnailAdapter", "=== 썸네일 어댑터 처리 시작: ${photo.name} (position: $position) ===")
        Log.d("ThumbnailAdapter", "photo.path: ${photo.path}")
        Log.d("ThumbnailAdapter", "thumbnailData size: ${thumbnailData?.size ?: 0} bytes")
        Log.d("ThumbnailAdapter", "fullImageData size: ${fullImageData?.size ?: 0} bytes")

        // EXIF 처리를 위해 고화질 데이터 우선 사용
        if (thumbnailData != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    Log.d("ThumbnailAdapter", "비트맵 디코딩 시작: ${photo.name}")

                    // 썸네일에 EXIF 적용하기 위해 고화질 데이터에서 EXIF 읽기
                    val bitmap = if (fullImageData != null) {
                        Log.d("ThumbnailAdapter", "고화질 데이터에서 EXIF 읽어서 썸네일에 적용: ${photo.name}")
                        decodeThumbnailWithFullImageExif(thumbnailData, fullImageData, photo)
                    } else {
                        Log.d("ThumbnailAdapter", "고화질 데이터 없음, 기본 썸네일 디코딩: ${photo.name}")
                        decodeBitmapWithExifRotation(thumbnailData, photo)
                    }

                    Log.d(
                        "ThumbnailAdapter",
                        "비트맵 디코딩 완료: ${photo.name}, bitmap: ${bitmap != null}"
                    )

                    // 메인 스레드에서 UI 업데이트
                    CoroutineScope(Dispatchers.Main).launch {
                        if (bitmap != null && !bitmap.isRecycled) {
                            Log.d(
                                "ThumbnailAdapter",
                                "썸네일 비트맵 적용 성공: ${photo.name} (${bitmap.width}x${bitmap.height})"
                            )
                            holder.imageView.setImageBitmap(bitmap)
                            holder.imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                        } else {
                            Log.w("ThumbnailAdapter", "썸네일 비트맵 null 또는 recycled: ${photo.name}")
                            holder.imageView.setImageResource(android.R.drawable.ic_menu_gallery)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ThumbnailAdapter", "썸네일 EXIF 처리 에러: ${photo.name}", e)
                    // 에러 시 메인 스레드에서 플레이스홀더 설정
                    CoroutineScope(Dispatchers.Main).launch {
                        holder.imageView.setImageResource(android.R.drawable.ic_menu_gallery)
                    }
                }
            }
        } else {
            Log.w("ThumbnailAdapter", "썸네일 데이터 없음: ${photo.name}")
            // 썸네일이 없으면 즉시 플레이스홀더 설정
            holder.imageView.setImageResource(android.R.drawable.ic_menu_gallery)
        }

        // 현재 선택된 썸네일 강조표시
        if (position == selectedPosition) {
            holder.imageView.setBackgroundResource(R.drawable.thumbnail_selected_background)
            holder.imageView.alpha = 1.0f
        } else {
            holder.imageView.setBackgroundResource(R.drawable.thumbnail_background)
            holder.imageView.alpha = 0.7f
        }

        holder.itemView.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime > clickDebounceTime) {
                lastClickTime = currentTime
                Log.d("ThumbnailAdapter", "썸네일 클릭: ${photo.name} (인덱스 $position)")
                onClick(position)
            } else {
                Log.d("ThumbnailAdapter", "썸네일 클릭 무시 (디바운스): ${photo.name}")
            }
        }
    }

    override fun getItemCount(): Int {
        return photos.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.image_view)
    }
}

private fun decodeThumbnailWithFullImageExif(
    thumbnailData: ByteArray,
    fullImageData: ByteArray,
    photo: CameraPhoto
): Bitmap? {
    // 고화질 이미지에서 EXIF 읽기
    val fullExif = try {
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