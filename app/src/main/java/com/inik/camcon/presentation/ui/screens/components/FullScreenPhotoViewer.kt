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
            .withHiddenStatusBar(true) // 상태바 숨기기
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
                                        imageViewRefs[newPhoto.path]?.let { imageView ->
                                            fullImageCache[newPhoto.path]?.let { imageData ->
                                                try {
                                                    val bitmap = BitmapFactory.decodeByteArray(
                                                        imageData,
                                                        0,
                                                        imageData.size
                                                    )
                                                    if (bitmap != null && !bitmap.isRecycled) {
                                                        imageView.setImageBitmap(bitmap)
                                                        imageView.scaleType =
                                                            ImageView.ScaleType.FIT_CENTER
                                                        highQualityUpdated.add(newPhoto.path)
                                                        Log.d(
                                                            "StfalconViewer",
                                                            "✅ 수동 고화질 적용: ${newPhoto.name}"
                                                        )
                                                    } else {
                                                        Log.d(
                                                            "StfalconViewer",
                                                            "🚫 Bitmap null or recycled"
                                                        )
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

                            // 약간의 지연 후 고화질 적용 (setCurrentPosition 완료 대기)
                            CoroutineScope(Dispatchers.Main).launch {
                                delay(200) // 위치 변경 완료 대기

                                imageViewRefs[newPhoto.path]?.let { imageView ->
                                    fullImageCache[newPhoto.path]?.let { imageData ->
                                        try {
                                            val bitmap = BitmapFactory.decodeByteArray(
                                                imageData,
                                                0,
                                                imageData.size
                                            )
                                            if (bitmap != null && !bitmap.isRecycled) {
                                                imageView.setImageBitmap(bitmap)
                                                imageView.scaleType = ImageView.ScaleType.FIT_CENTER
                                                highQualityUpdated.add(newPhoto.path)
                                                Log.d(
                                                    "StfalconViewer",
                                                    "✅ 썸네일 클릭 후 고화질 적용 성공: ${newPhoto.name}"
                                                )
                                            } else {
                                                Log.d("StfalconViewer", "🚫 Bitmap null or recycled")
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
                                    } else {
                                        Log.d("StfalconViewer", "🚫 Bitmap recycled")
                                    }
                                }
                            } else {
                                Log.d("StfalconViewer", "🚫 Bitmap null")
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
        // 1. 고화질 이미지가 있으면 우선 표시
        if (fullImageData != null) {
            val fullCacheKey = "${photo.path}_full"
            var fullBitmap = bitmapCache[fullCacheKey]

            if (fullBitmap == null) {
                fullBitmap = BitmapFactory.decodeByteArray(fullImageData, 0, fullImageData.size)
                if (fullBitmap != null) {
                    bitmapCache[fullCacheKey] = fullBitmap
                }
            }

            if (fullBitmap != null && !fullBitmap.isRecycled) {
                imageView.setImageBitmap(fullBitmap)
                imageView.scaleType = ImageView.ScaleType.FIT_CENTER
                highQualityUpdated.add(photo.path) // 고화질로 업데이트됨을 표시
                Log.d("StfalconViewer", "🖼️ 고화질 이미지 표시: ${photo.name}")

                // ImageView 참조 저장
                imageViewRefs[photo.path] = imageView
                return
            }
        }

        // 2. 고화질이 없으면 썸네일 표시 (기존 로직)
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

class ThumbnailAdapter(
    private val photos: List<CameraPhoto>,
    private val thumbnailCache: Map<String, ByteArray>,
    private val viewModel: PhotoPreviewViewModel?,
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<ThumbnailAdapter.ViewHolder>() {

    private var selectedPosition = 0
    private var lastClickTime = 0L
    private val clickDebounceTime = 300L // 300ms 디바운스

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

        if (thumbnailData != null) {
            val bitmap = BitmapFactory.decodeByteArray(thumbnailData, 0, thumbnailData.size)
            holder.imageView.setImageBitmap(bitmap)
        } else {
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