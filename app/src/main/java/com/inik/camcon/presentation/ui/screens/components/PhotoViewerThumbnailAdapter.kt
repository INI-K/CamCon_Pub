package com.inik.camcon.presentation.ui.screens.components

import android.graphics.Bitmap
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
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
 * 전체화면 사진 뷰어의 썸네일 갤러리용 어댑터
 * 로딩 상태와 무한 스크롤을 지원하는 RecyclerView 어댑터입니다.
 */
class PhotoViewerThumbnailAdapter(
    private val viewModel: PhotoPreviewViewModel?,
    private val thumbnailCache: Map<String, ByteArray>,
    private val onClick: (Int) -> Unit,
    private val initialPhotos: List<CameraPhoto> = emptyList() // 뷰어와 동일한 리스트 사용
) : RecyclerView.Adapter<PhotoViewerThumbnailAdapter.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_PHOTO = 0
        private const val VIEW_TYPE_LOADING = 1
        private const val CLICK_DEBOUNCE_TIME = 300L // 300ms 디바운스
        private const val UPDATE_THROTTLE_TIME = 50L // 50ms 스로틀
    }

    private var selectedPosition = 0
    private var lastClickTime = 0L

    // StfalconImageViewer 참조 추가
    private var viewer: StfalconImageViewer<CameraPhoto>? = null

    // 고화질 캐시에 접근하기 위한 참조
    private var fullImageCache: Map<String, ByteArray> = emptyMap()

    // 비트맵 캐시 (메모리 효율성을 위해)
    private val bitmapCache = mutableMapOf<String, Bitmap>()

    // 데이터 일관성을 위한 스냅샷 저장 - 초기값을 뷰어와 동일하게 설정
    private var photosSnapshot: List<CameraPhoto> = initialPhotos
    private var isLoadingSnapshot = false
    private var hasNextPageSnapshot = true

    init {
        // 초기 데이터 로드 - 뷰어와 동일한 리스트 우선 사용
        try {
            if (initialPhotos.isNotEmpty()) {
                photosSnapshot = initialPhotos
                Log.d("ThumbnailAdapter", "뷰어와 동일한 초기 리스트 사용: ${photosSnapshot.size}개 사진")
            } else {
                val uiState = viewModel?.uiState?.value
                photosSnapshot = uiState?.photos ?: emptyList()
                Log.d("ThumbnailAdapter", "ViewModel에서 초기 데이터 로드: ${photosSnapshot.size}개 사진")
            }

            isLoadingSnapshot =
                viewModel?.uiState?.value?.isLoading == true || viewModel?.uiState?.value?.isLoadingMore == true
            hasNextPageSnapshot = viewModel?.uiState?.value?.hasNextPage == true

        } catch (e: Exception) {
            Log.e("ThumbnailAdapter", "초기 데이터 로드 실패", e)
            // 안전한 기본값으로 초기화
            photosSnapshot = initialPhotos
            isLoadingSnapshot = false
            hasNextPageSnapshot = true
        }
    }

    /**
     * 사진 리스트 강제 업데이트 (외부에서 호출)
     * 데이터 일관성을 보장하기 위해 스냅샷 방식 사용
     */
    fun refreshPhotos() {
        try {
            // 현재 상태를 스냅샷으로 저장
            val oldPhotos = photosSnapshot
            val oldIsLoading = isLoadingSnapshot
            val oldHasNextPage = hasNextPageSnapshot
            val oldItemCount = if (oldIsLoading && oldPhotos.isNotEmpty() && oldHasNextPage) {
                oldPhotos.size + 1
            } else {
                oldPhotos.size
            }

            // ViewModel에서 최신 상태 가져오기
            val uiState = viewModel?.uiState?.value
            val newPhotos = uiState?.photos ?: emptyList()
            val newIsLoading = uiState?.isLoading == true || uiState?.isLoadingMore == true
            val newHasNextPage = uiState?.hasNextPage == true

            // 스냅샷 업데이트
            photosSnapshot = newPhotos
            isLoadingSnapshot = newIsLoading
            hasNextPageSnapshot = newHasNextPage

            val newItemCount = if (newIsLoading && newPhotos.isNotEmpty() && newHasNextPage) {
                newPhotos.size + 1
            } else {
                newPhotos.size
            }

            Log.d(
                "ThumbnailAdapter",
                "데이터 새로고침: 사진 ${oldPhotos.size}→${newPhotos.size}, " +
                        "아이템 ${oldItemCount}→${newItemCount}, " +
                        "로딩 ${oldIsLoading}→${newIsLoading}, " +
                        "hasNext ${oldHasNextPage}→${newHasNextPage}"
            )

            // 안전한 데이터셋 변경 처리
            handleSafeDataSetChange(oldPhotos.size, oldItemCount, newItemCount)

        } catch (e: Exception) {
            Log.e("ThumbnailAdapter", "refreshPhotos 처리 중 오류", e)
            // 오류 발생 시 안전한 전체 새로고침
            try {
                notifyDataSetChanged()
            } catch (ex: Exception) {
                Log.e("ThumbnailAdapter", "notifyDataSetChanged도 실패", ex)
            }
        }
    }

    /**
     * 안전한 데이터셋 변경 처리
     */
    private fun handleSafeDataSetChange(
        oldPhotoCount: Int,
        oldItemCount: Int,
        newItemCount: Int
    ) {
        when {
            // 데이터가 크게 변경되었거나 복잡한 경우 전체 새로고침
            kotlin.math.abs(newItemCount - oldItemCount) > 10 || 
            photosSnapshot.size < oldPhotoCount -> {
                Log.d("ThumbnailAdapter", "전체 데이터셋 새로고침 (큰 변경 감지)")
                notifyDataSetChanged()
            }

            // 새로운 사진이 추가된 경우
            photosSnapshot.size > oldPhotoCount -> {
                val photosAdded = photosSnapshot.size - oldPhotoCount
                Log.d("ThumbnailAdapter", "새 사진 추가: $photosAdded 개")
                
                // 기존 아이템들은 그대로 두고 새 아이템만 추가
                notifyItemRangeInserted(oldPhotoCount, photosAdded)
                
                // 로딩 아이템 상태 변경
                handleLoadingItemChange(oldItemCount, newItemCount)
            }

            // 사진 개수는 같지만 로딩 상태가 변경된 경우
            photosSnapshot.size == oldPhotoCount && oldItemCount != newItemCount -> {
                Log.d("ThumbnailAdapter", "로딩 상태만 변경")
                handleLoadingItemChange(oldItemCount, newItemCount)
            }

            // 변경사항이 없는 경우
            oldItemCount == newItemCount -> {
                Log.d("ThumbnailAdapter", "변경사항 없음")
            }

            // 기타 예상치 못한 경우
            else -> {
                Log.d("ThumbnailAdapter", "예상치 못한 변경 - 전체 새로고침")
                notifyDataSetChanged()
            }
        }
    }

    /**
     * 로딩 아이템 변경 처리
     */
    private fun handleLoadingItemChange(oldItemCount: Int, newItemCount: Int) {
        when {
            newItemCount > oldItemCount -> {
                // 로딩 아이템 추가
                Log.d("ThumbnailAdapter", "로딩 아이템 추가: ${oldItemCount} → ${newItemCount}")
                notifyItemInserted(oldItemCount)
            }
            newItemCount < oldItemCount -> {
                // 로딩 아이템 제거
                Log.d("ThumbnailAdapter", "로딩 아이템 제거: ${oldItemCount} → ${newItemCount}")
                notifyItemRemoved(oldItemCount - 1)
            }
        }
    }

    /**
     * 고화질 캐시 업데이트
     */
    fun updateFullImageCache(cache: Map<String, ByteArray>) {
        val oldCache = fullImageCache
        fullImageCache = cache

        // 새로 추가된 고화질 이미지만 개별적으로 업데이트 (깜빡임 방지)
        if (cache.size > oldCache.size) {
            val newItems = cache.keys - oldCache.keys
            Log.d("ThumbnailAdapter", "새로운 고화질 데이터 감지: ${newItems.size}개")

            // 새로 추가된 아이템들의 위치를 찾아서 개별 업데이트
            newItems.forEach { photoPath ->
                val position = photosSnapshot.indexOfFirst { it.path == photoPath }
                if (position != -1 && position < photosSnapshot.size) {
                    notifyItemChanged(position)
                }
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

    /**
     * 선택된 위치 설정
     */
    fun setSelectedPosition(position: Int) {
        val previousPosition = selectedPosition
        selectedPosition = position
        
        // 유효한 범위 내에서만 업데이트
        if (previousPosition < photosSnapshot.size) {
            notifyItemChanged(previousPosition)
        }
        if (selectedPosition < photosSnapshot.size) {
            notifyItemChanged(selectedPosition)
        }
    }

    /**
     * StfalconImageViewer 참조 설정
     */
    fun setViewer(imageViewer: StfalconImageViewer<CameraPhoto>) {
        this.viewer = imageViewer
        Log.d("ThumbnailAdapter", "ImageViewer 참조 설정 완료")

        // 설정 직후 뷰어 상태 확인
        Log.d("ThumbnailAdapter", "뷰어 참조가 성공적으로 설정됨")
    }

    override fun getItemViewType(position: Int): Int {
        return if (position < photosSnapshot.size) VIEW_TYPE_PHOTO else VIEW_TYPE_LOADING
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
        val currentTime = System.currentTimeMillis()

        // 50ms 이내에는 캐시된 값 사용 (과도한 재계산 방지)
        if (currentTime - lastCountUpdate < 50 && cachedItemCount > 0) {
            return cachedItemCount
        }

        val photoCount = photosSnapshot.size
        val shouldShowLoading = isLoadingSnapshot && photoCount > 0 && hasNextPageSnapshot
        val itemCount = if (shouldShowLoading) photoCount + 1 else photoCount

        // 값이 실제로 변경되었을 때만 로그 출력
        if (itemCount != cachedItemCount) {
            Log.d(
                "ThumbnailAdapter",
                "getItemCount 변경됨 - 사진: $photoCount, 로딩표시: $shouldShowLoading, 총아이템: $itemCount"
            )
        }

        cachedItemCount = itemCount
        lastCountUpdate = currentTime

        return itemCount
    }

    // 아이템 수 캐싱 (성능 최적화)
    private var cachedItemCount = 0
    private var lastCountUpdate = 0L

    /**
     * 사진 아이템 바인딩
     */
    private fun bindPhotoItem(holder: ViewHolder, position: Int) {
        if (position >= photosSnapshot.size) {
            Log.w("ThumbnailAdapter", "잘못된 position: $position, 총 사진: ${photosSnapshot.size}")
            return
        }

        val photo = photosSnapshot[position]

        // 선택 상태 표시 (즉시 적용)
        setSelectionState(holder, position)

        // 클릭 리스너 설정 (즉시 등록)
        setClickListener(holder, photo, position)

        // 이미지 로딩
        loadThumbnailImage(holder, photo)
    }

    /**
     * 선택 상태 설정
     */
    private fun setSelectionState(holder: ViewHolder, position: Int) {
        if (position == selectedPosition) {
            holder.imageView?.setBackgroundResource(R.drawable.thumbnail_selected_background)
            holder.imageView?.alpha = 1.0f
        } else {
            holder.imageView?.setBackgroundResource(R.drawable.thumbnail_background)
            holder.imageView?.alpha = 0.7f
        }
    }

    /**
     * 클릭 리스너 설정
     */
    private fun setClickListener(holder: ViewHolder, photo: CameraPhoto, position: Int) {
        holder.itemView.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime < CLICK_DEBOUNCE_TIME) {
                Log.d("ThumbnailAdapter", "빠른 클릭 무시: ${photo.name}")
                return@setOnClickListener
            }
            lastClickTime = currentTime

            Log.d("ThumbnailAdapter", "썸네일 클릭: ${photo.name} (position: $position)")
            Log.d("ThumbnailAdapter", "현재 어댑터 리스트 크기: ${photosSnapshot.size}")

            // 뷰어 참조 상태 확인
            if (viewer == null) {
                Log.e("ThumbnailAdapter", "뷰어 참조가 null입니다!")
                // 뷰어가 없어도 UI 상태는 업데이트
                setSelectedPosition(position)
                onClick(position)
                return@setOnClickListener
            }

            // 이제 뷰어와 동일한 리스트를 사용하므로 position을 그대로 사용
            if (position < 0 || position >= photosSnapshot.size) {
                Log.e("ThumbnailAdapter", "잘못된 position: $position (총 ${photosSnapshot.size}개)")
                return@setOnClickListener
            }

            // 뷰어의 현재 위치 변경 시도 - 지연 처리
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    Log.d("ThumbnailAdapter", "뷰어 위치 변경 시도: → $position")

                    // 강제 위치 변경을 위한 트릭: 다른 위치로 먼저 이동 후 원하는 위치로 이동
                    val tempPosition = if (position == 0) {
                        // position 0이면 1로 먼저 이동
                        if (photosSnapshot.size > 1) 1 else 0
                    } else {
                        // position 0이 아니면 0으로 먼저 이동
                        0
                    }

                    // 임시 위치로 먼저 이동 (withImageChangeListener 트리거 방지를 위해 짧은 지연)
                    if (tempPosition != position && photosSnapshot.size > 1) {
                        Log.d("ThumbnailAdapter", "강제 변경을 위한 임시 이동: → $tempPosition")
                        viewer?.setCurrentPosition(tempPosition)
                        delay(50) // 매우 짧은 지연
                    }

                    // 실제 원하는 위치로 이동
                    viewer?.setCurrentPosition(position)
                    Log.d("ThumbnailAdapter", "setCurrentPosition 1차 호출 완료: $position")

                    // 100ms 후 한 번 더 시도 (뷰어가 완전히 준비되지 않을 수 있음)
                    delay(100)

                    viewer?.setCurrentPosition(position)
                    Log.d("ThumbnailAdapter", "setCurrentPosition 2차 호출 완료: $position")

                    // 추가로 200ms 후 한 번 더 시도
                    delay(200)

                    viewer?.setCurrentPosition(position)
                    Log.d("ThumbnailAdapter", "setCurrentPosition 3차 호출 완료: $position")

                } catch (e: Exception) {
                    Log.e("ThumbnailAdapter", "setCurrentPosition 호출 실패", e)
                }
            }

            // 선택 상태 업데이트
            setSelectedPosition(position)

            // 기존 onClick 콜백도 호출
            onClick(position)
        }
    }

    /**
     * 썸네일 이미지 로딩
     */
    private fun loadThumbnailImage(holder: ViewHolder, photo: CameraPhoto) {
        val thumbnailData = thumbnailCache[photo.path] ?: viewModel?.getThumbnail(photo.path)
        val fullImageData = fullImageCache[photo.path]

        val thumbnailCacheKey = "${photo.path}_thumbnail"
        val highQualityCacheKey = "${photo.path}_highquality"

        // 캐시된 비트맵이 있으면 즉시 표시
        val cachedBitmap = bitmapCache[highQualityCacheKey] ?: bitmapCache[thumbnailCacheKey]
        if (cachedBitmap != null && !cachedBitmap.isRecycled) {
            holder.imageView?.setImageBitmap(cachedBitmap)
            holder.imageView?.scaleType = ImageView.ScaleType.CENTER_CROP
            return
        }

        // 썸네일 데이터가 있는 경우 처리
        if (thumbnailData != null) {
            // 플레이스홀더 먼저 표시
            holder.imageView?.setImageResource(android.R.drawable.ic_menu_gallery)
            holder.imageView?.scaleType = ImageView.ScaleType.CENTER

            // 고화질 데이터가 있으면 고화질로, 없으면 기본 썸네일로 처리
            if (fullImageData != null) {
                loadHighQualityThumbnail(
                    holder,
                    photo,
                    thumbnailData,
                    fullImageData,
                    highQualityCacheKey
                )
            } else {
                loadBasicThumbnail(holder, photo, thumbnailData, thumbnailCacheKey)
            }
        } else {
            // 썸네일 데이터가 없으면 플레이스홀더 표시
            setPlaceholderImage(holder.imageView)
        }
    }

    /**
     * 고화질 썸네일 로딩
     */
    private fun loadHighQualityThumbnail(
        holder: ViewHolder,
        photo: CameraPhoto,
        thumbnailData: ByteArray,
        fullImageData: ByteArray,
        cacheKey: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bitmap = ImageProcessingUtils.decodeThumbnailWithFullImageExif(
                    thumbnailData,
                    fullImageData,
                    photo
                )

                if (bitmap != null && !bitmap.isRecycled) {
                    bitmapCache[cacheKey] = bitmap

                    CoroutineScope(Dispatchers.Main).launch {
                        holder.imageView?.setImageBitmap(bitmap)
                        holder.imageView?.scaleType = ImageView.ScaleType.CENTER_CROP
                        holder.imageView?.alpha = 1.0f
                    }
                } else {
                    // 고화질 처리 실패 시 기본 썸네일로 fallback
                    CoroutineScope(Dispatchers.Main).launch {
                        loadBasicThumbnail(holder, photo, thumbnailData, "${photo.path}_thumbnail")
                    }
                }
            } catch (e: Exception) {
                Log.e("ThumbnailAdapter", "고화질 썸네일 처리 에러: ${photo.name}", e)
                // 에러 발생 시 기본 썸네일로 fallback
                CoroutineScope(Dispatchers.Main).launch {
                    loadBasicThumbnail(holder, photo, thumbnailData, "${photo.path}_thumbnail")
                }
            }
        }
    }

    /**
     * 기본 썸네일 로딩
     */
    private fun loadBasicThumbnail(
        holder: ViewHolder,
        photo: CameraPhoto,
        thumbnailData: ByteArray,
        cacheKey: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bitmap = ImageProcessingUtils.decodeBitmapWithExifRotation(thumbnailData, photo)

                if (bitmap != null && !bitmap.isRecycled) {
                    bitmapCache[cacheKey] = bitmap

                    CoroutineScope(Dispatchers.Main).launch {
                        holder.imageView?.setImageBitmap(bitmap)
                        holder.imageView?.scaleType = ImageView.ScaleType.CENTER_CROP
                        holder.imageView?.alpha = 1.0f
                    }
                }
            } catch (e: Exception) {
                Log.e("ThumbnailAdapter", "기본 썸네일 처리 에러: ${photo.name}", e)
            }
        }
    }

    /**
     * 로딩 아이템 바인딩
     */
    private fun bindLoadingItem(holder: ViewHolder) {
        Log.d("ThumbnailAdapter", "로딩 아이템 바인딩 시작 - 위치: ${holder.adapterPosition}")

        // 로딩 인디케이터를 명시적으로 표시
        val progressBar =
            holder.itemView.findViewById<android.widget.ProgressBar>(android.R.id.progress)
        val textView = holder.itemView.findViewById<android.widget.TextView>(android.R.id.text1)

        // ProgressBar와 TextView가 없다면 레이아웃에서 직접 찾기
        val allProgressBars =
            holder.itemView.findViewsWithType(android.widget.ProgressBar::class.java)
        val allTextViews = holder.itemView.findViewsWithType(android.widget.TextView::class.java)

        val actualProgressBar = progressBar ?: allProgressBars.firstOrNull()
        val actualTextView = textView ?: allTextViews.firstOrNull()

        // 로딩 인디케이터 활성화
        actualProgressBar?.let { pb ->
            pb.visibility = android.view.View.VISIBLE
            pb.isIndeterminate = true
            Log.d("ThumbnailAdapter", "ProgressBar 활성화됨")
        }

        // 로딩 텍스트 표시
        actualTextView?.let { tv ->
            tv.visibility = android.view.View.VISIBLE
            tv.text = "로딩중"
            Log.d("ThumbnailAdapter", "로딩 텍스트 설정됨")
        }

        // 전체 아이템을 보이도록 설정
        holder.itemView.visibility = android.view.View.VISIBLE
        holder.itemView.alpha = 1.0f

        // 스냅샷과 실시간 상태 모두 확인
        val snapshotLoading = isLoadingSnapshot && hasNextPageSnapshot
        val realtimeState = viewModel?.uiState?.value
        val realtimeLoading =
            (realtimeState?.isLoading == true || realtimeState?.isLoadingMore == true)
                    && realtimeState?.hasNextPage == true

        Log.d(
            "ThumbnailAdapter",
            "로딩 상태 확인 - 스냅샷: $snapshotLoading, 실시간: $realtimeLoading, " +
                    "총사진: ${photosSnapshot.size}, 어댑터위치: ${holder.adapterPosition}"
        )

        // 로딩 완료 후 정리를 위한 디바운스
        holder.itemView.post {
            Log.d("ThumbnailAdapter", "로딩 아이템 UI 표시 완료 - 최종 확인")
        }
    }

    /**
     * View에서 특정 타입의 자식 뷰들을 찾는 확장 함수
     */
    private fun <T : android.view.View> android.view.View.findViewsWithType(clazz: Class<T>): List<T> {
        val result = mutableListOf<T>()
        if (clazz.isInstance(this)) {
            @Suppress("UNCHECKED_CAST")
            result.add(this as T)
        }
        if (this is ViewGroup) {
            for (i in 0 until childCount) {
                result.addAll(getChildAt(i).findViewsWithType(clazz))
            }
        }
        return result
    }

    /**
     * 플레이스홀더 이미지 설정
     */
    private fun setPlaceholderImage(imageView: ImageView?) {
        imageView?.setImageResource(android.R.drawable.ic_menu_gallery)
        imageView?.scaleType = ImageView.ScaleType.CENTER
        imageView?.alpha = 0.5f
    }

    /**
     * ViewHolder 클래스
     */
    class ViewHolder(itemView: View, val viewType: Int) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView? = if (viewType == VIEW_TYPE_PHOTO) {
            itemView.findViewById(R.id.image_view)
        } else {
            null
        }
    }
}