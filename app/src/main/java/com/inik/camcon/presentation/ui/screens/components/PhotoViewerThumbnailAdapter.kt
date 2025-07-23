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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 전체화면 사진 뷰어의 썸네일 갤러리용 어댑터
 * 로딩 상태와 무한 스크롤을 지원하는 RecyclerView 어댑터입니다.
 */
class PhotoViewerThumbnailAdapter(
    private val viewModel: PhotoPreviewViewModel?,
    private val thumbnailCache: Map<String, ByteArray>,
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<PhotoViewerThumbnailAdapter.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_PHOTO = 0
        private const val VIEW_TYPE_LOADING = 1
        private const val CLICK_DEBOUNCE_TIME = 300L // 300ms 디바운스
        private const val UPDATE_THROTTLE_TIME = 50L // 50ms 스로틀
    }

    private var selectedPosition = 0
    private var lastClickTime = 0L

    // 고화질 캐시에 접근하기 위한 참조
    private var fullImageCache: Map<String, ByteArray> = emptyMap()

    // 비트맵 캐시 (메모리 효율성을 위해)
    private val bitmapCache = mutableMapOf<String, Bitmap>()

    // 사진 리스트를 캐시하여 빠른 접근과 안정성 확보
    private var cachedPhotos: List<CameraPhoto> = emptyList()
    private var lastUpdateTime = 0L

    // 로딩 상태
    private var isLoading = false
    private var hasNextPage = true

    // ViewModel에서 안전하게 photos 리스트 가져오기
    private val photos: List<CameraPhoto>
        get() {
            val currentTime = System.currentTimeMillis()
            // 스로틀링: 너무 자주 업데이트하지 않도록 제한
            if (currentTime - lastUpdateTime > UPDATE_THROTTLE_TIME) {
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
        val oldHasNextPage = hasNextPage

        // ViewModel에서 최신 상태 가져오기
        val uiState = viewModel?.uiState?.value
        val newPhotos = uiState?.photos ?: emptyList()
        val newIsLoading = uiState?.isLoading == true || uiState?.isLoadingMore == true
        val newHasNextPage = uiState?.hasNextPage == true

        // 상태 업데이트
        cachedPhotos = newPhotos
        isLoading = newIsLoading
        hasNextPage = newHasNextPage
        lastUpdateTime = System.currentTimeMillis()

        Log.d(
            "ThumbnailAdapter",
            "사진 리스트 새로고침: $oldSize → ${cachedPhotos.size}, 로딩: $oldIsLoading → $isLoading, hasNextPage: $oldHasNextPage → $hasNextPage"
        )

        // 변경사항에 따른 적절한 notify 호출
        handleDataSetChanges(oldSize, oldIsLoading, oldHasNextPage)
    }

    /**
     * 데이터셋 변경에 따른 적절한 notify 처리
     */
    private fun handleDataSetChanges(
        oldSize: Int,
        oldIsLoading: Boolean,
        oldHasNextPage: Boolean
    ) {
        when {
            // 사진이 새로 추가된 경우
            cachedPhotos.size > oldSize -> {
                Log.d("ThumbnailAdapter", "새 사진 추가됨: ${oldSize} → ${cachedPhotos.size}")
                if (oldIsLoading && !isLoading) {
                    // 로딩이 완료되고 새 사진이 추가됨 - 로딩 아이템을 사진으로 교체
                    notifyItemChanged(oldSize)
                    if (cachedPhotos.size - oldSize > 1) {
                        notifyItemRangeInserted(oldSize + 1, cachedPhotos.size - oldSize - 1)
                    }
                } else {
                    // 단순히 새 사진 추가
                    notifyItemRangeInserted(oldSize, cachedPhotos.size - oldSize)
                }

                // 새 로딩 아이템이 필요한 경우
                if (isLoading && hasNextPage && (!oldIsLoading || !oldHasNextPage)) {
                    notifyItemInserted(cachedPhotos.size)
                }
            }

            // 로딩 상태만 변경된 경우
            cachedPhotos.size == oldSize && (isLoading != oldIsLoading || hasNextPage != oldHasNextPage) -> {
                handleLoadingStateChange(oldIsLoading, oldHasNextPage, oldSize)
            }

            // 사진이 제거된 경우 (드문 경우)
            cachedPhotos.size < oldSize -> {
                Log.d("ThumbnailAdapter", "사진 제거됨: $oldSize → ${cachedPhotos.size}")
                notifyItemRangeRemoved(cachedPhotos.size, oldSize - cachedPhotos.size)
            }

            // 전체 데이터셋이 변경된 경우
            else -> {
                Log.d("ThumbnailAdapter", "전체 데이터셋 변경")
                notifyDataSetChanged()
            }
        }
    }

    /**
     * 로딩 상태 변경 처리
     */
    private fun handleLoadingStateChange(
        oldIsLoading: Boolean,
        oldHasNextPage: Boolean,
        oldSize: Int
    ) {
        if (isLoading && hasNextPage && cachedPhotos.size > 0) {
            if (!oldIsLoading || !oldHasNextPage) {
                Log.d("ThumbnailAdapter", "로딩 아이템 추가")
                notifyItemInserted(cachedPhotos.size)
            }
        } else if (!isLoading || !hasNextPage) {
            if (oldIsLoading && oldHasNextPage) {
                Log.d("ThumbnailAdapter", "로딩 아이템 제거")
                notifyItemRemoved(oldSize)
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
                val position = photos.indexOfFirst { it.path == photoPath }
                if (position != -1) {
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
        val shouldShowLoading = isLoading && photoCount > 0 && hasNextPage
        return if (shouldShowLoading) photoCount + 1 else photoCount
    }

    /**
     * 사진 아이템 바인딩
     */
    private fun bindPhotoItem(holder: ViewHolder, position: Int) {
        val currentPhotos = photos
        if (position >= currentPhotos.size) {
            Log.w("ThumbnailAdapter", "잘못된 position: $position, 총 사진: ${currentPhotos.size}")
            return
        }

        val photo = currentPhotos[position]

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
        Log.d(
            "ThumbnailAdapter",
            "로딩 썸네일 표시 - 총 사진: ${photos.size}개, isLoading: $isLoading, hasNextPage: $hasNextPage"
        )
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