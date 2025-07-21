package com.inik.camcon.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.domain.manager.CameraConnectionGlobalManager
import com.inik.camcon.domain.model.CameraPhoto
import com.inik.camcon.domain.repository.CameraRepository
import com.inik.camcon.domain.usecase.camera.GetCameraPhotosPagedUseCase
import com.inik.camcon.domain.usecase.camera.GetCameraThumbnailUseCase
import com.inik.camcon.domain.usecase.camera.PhotoCaptureEventManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PhotoPreviewUiState(
    val photos: List<CameraPhoto> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val selectedPhoto: CameraPhoto? = null,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val hasNextPage: Boolean = false,
    val thumbnailCache: Map<String, ByteArray> = emptyMap(),
    val isConnected: Boolean = false,
    val isInitialized: Boolean = false,
    val fileTypeFilter: FileTypeFilter = FileTypeFilter.JPG,
    val allPhotos: List<CameraPhoto> = emptyList()
)

enum class FileTypeFilter {
    ALL,
    JPG,
    RAW
}

@HiltViewModel
class PhotoPreviewViewModel @Inject constructor(
    private val cameraRepository: CameraRepository,
    private val getCameraPhotosPagedUseCase: GetCameraPhotosPagedUseCase,
    private val getCameraThumbnailUseCase: GetCameraThumbnailUseCase,
    private val photoCaptureEventManager: PhotoCaptureEventManager,
    private val globalManager: CameraConnectionGlobalManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PhotoPreviewUiState())
    val uiState: StateFlow<PhotoPreviewUiState> = _uiState.asStateFlow()

    // 실제 파일 다운로드 캐시 (고해상도 이미지용)
    private val _fullImageCache = MutableStateFlow<Map<String, ByteArray>>(emptyMap())
    val fullImageCache: StateFlow<Map<String, ByteArray>> = _fullImageCache.asStateFlow()

    // 실제 파일 다운로드 상태 관리
    private val _downloadingImages = MutableStateFlow<Set<String>>(emptySet())
    val downloadingImages: StateFlow<Set<String>> = _downloadingImages.asStateFlow()

    // 프리로딩 상태 추적
    private val _prefetchedPage = MutableStateFlow(0)

    companion object {
        private const val TAG = "PhotoPreviewViewModel"
        private const val PREFETCH_PAGE_SIZE = 50
        private const val PREFETCH_THRESHOLD = 30
    }

    init {
        android.util.Log.d(TAG, "=== PhotoPreviewViewModel 초기화 시작 ===")
        loadInitialPhotos()
        observePhotoCaptureEvents()
        observeCameraConnection()
        android.util.Log.d(TAG, "=== PhotoPreviewViewModel 초기화 완료 ===")
    }

    private fun observeCameraConnection() {
        android.util.Log.d(TAG, "=== observeCameraConnection 시작 ===")
        viewModelScope.launch {
            globalManager.globalConnectionState.collect { connectionState ->
                val isConnected = connectionState.isAnyConnectionActive
                android.util.Log.d(TAG, "전역 카메라 연결 상태 변경: $isConnected")
                android.util.Log.d(TAG, "  - USB 연결: ${connectionState.isUsbConnected}")
                android.util.Log.d(TAG, "  - PTPIP 연결: ${connectionState.ptpipConnectionState}")
                android.util.Log.d(TAG, "  - 활성 연결 타입: ${connectionState.activeConnectionType}")

                val previousConnected = _uiState.value.isConnected
                _uiState.value = _uiState.value.copy(isConnected = isConnected)

                if (isConnected && !previousConnected) {
                    android.util.Log.d(TAG, "카메라 연결됨 - 자동으로 사진 목록 불러오기")
                    loadInitialPhotos()
                } else if (!isConnected && previousConnected) {
                    android.util.Log.d(TAG, "카메라 연결 해제됨 - 에러 상태 설정")
                    _uiState.value = _uiState.value.copy(
                        error = "카메라 연결이 해제되었습니다",
                        isInitialized = false
                    )
                }
            }
        }
    }

    private suspend fun checkCameraInitialization() {
        // 카메라 초기화 상태 확인 (Repository에 함수 추가 필요)
        // 임시로 연결 상태와 동일하게 처리
        _uiState.value = _uiState.value.copy(isInitialized = _uiState.value.isConnected)
    }

    private fun observePhotoCaptureEvents() {
        // 사진 촬영 이벤트 자동 새로고침 비활성화
        // (카메라 제어 화면에서 이벤트 리스너가 중지되는 문제 방지)
        /*
        photoCaptureEventManager.photoCaptureEvent
            .onEach {
                // 사진이 촬영되면 목록을 자동으로 새로고침
                loadInitialPhotos()
            }
            .launchIn(viewModelScope)
        */

        // 수동 새로고침만 허용하도록 변경
        android.util.Log.d("PhotoPreviewViewModel", "사진 촬영 이벤트 자동 새로고침 비활성화 - 수동 새로고침만 허용")
    }

    fun loadInitialPhotos() {
        android.util.Log.d(TAG, "=== loadInitialPhotos 호출 ===")
        viewModelScope.launch {
            android.util.Log.d(TAG, "loadInitialPhotos 코루틴 시작")
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                currentPage = 0,
                allPhotos = emptyList()
            )
            android.util.Log.d(TAG, "UI 상태 업데이트: isLoading=true")

            // 카메라 연결 상태 확인
            val isConnected = _uiState.value.isConnected
            android.util.Log.d(TAG, "현재 카메라 연결 상태: $isConnected")

            if (!isConnected) {
                android.util.Log.w(TAG, "카메라가 연결되지 않음 - 에러 상태로 설정")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "카메라가 연결되지 않았습니다. 카메라를 연결해주세요.",
                    photos = emptyList()
                )
                return@launch
            }

            android.util.Log.d(TAG, "getCameraPhotosPagedUseCase 호출 시작")
            getCameraPhotosPagedUseCase(page = 0, pageSize = PREFETCH_PAGE_SIZE).fold(
                onSuccess = { paginatedPhotos ->
                    android.util.Log.d(TAG, "사진 목록 불러오기 성공: ${paginatedPhotos.photos.size}개")
                    _uiState.value = _uiState.value.copy(
                        allPhotos = paginatedPhotos.photos,
                        photos = filterPhotos(
                            paginatedPhotos.photos,
                            _uiState.value.fileTypeFilter
                        ),
                        isLoading = false,
                        currentPage = paginatedPhotos.currentPage,
                        totalPages = paginatedPhotos.totalPages,
                        hasNextPage = paginatedPhotos.hasNext
                    )

                    // 첫 번째 페이지의 썸네일 로드 시작
                    android.util.Log.d(TAG, "썸네일 로드 시작")
                    loadThumbnailsForCurrentPage()
                },
                onFailure = { exception ->
                    android.util.Log.e(TAG, "사진 목록 불러오기 실패", exception)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = exception.message ?: "사진을 불러오는데 실패했습니다"
                    )
                }
            )
            android.util.Log.d(TAG, "loadInitialPhotos 코루틴 완료")
        }
    }

    fun loadNextPage() {
        if (_uiState.value.isLoadingMore || !_uiState.value.hasNextPage) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)

            val nextPage = _uiState.value.currentPage + 1
            getCameraPhotosPagedUseCase(page = nextPage, pageSize = PREFETCH_PAGE_SIZE).fold(
                onSuccess = { paginatedPhotos ->
                    val currentPhotos = _uiState.value.allPhotos
                    val newPhotos = currentPhotos + paginatedPhotos.photos

                    _uiState.value = _uiState.value.copy(
                        allPhotos = newPhotos,
                        photos = filterPhotos(newPhotos, _uiState.value.fileTypeFilter),
                        isLoadingMore = false,
                        currentPage = paginatedPhotos.currentPage,
                        totalPages = paginatedPhotos.totalPages,
                        hasNextPage = paginatedPhotos.hasNext
                    )

                    // 새로 로드된 사진들의 썸네일 로드
                    loadThumbnailsForNewPhotos(paginatedPhotos.photos)
                },
                onFailure = { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        error = exception.message ?: "추가 사진을 불러오는데 실패했습니다"
                    )
                }
            )
        }
    }

    fun loadCameraPhotos() {
        loadInitialPhotos()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun downloadPhoto(photo: CameraPhoto) {
        viewModelScope.launch {
            try {
                cameraRepository.downloadPhotoFromCamera(photo.path)
                    .onSuccess {
                        // 다운로드 성공
                    }
                    .onFailure { error ->
                        _uiState.value = _uiState.value.copy(
                            error = error.message
                        )
                    }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message
                )
            }
        }
    }

    private fun loadThumbnailsForCurrentPage() {
        viewModelScope.launch {
            val photos = _uiState.value.photos
            loadThumbnailsForNewPhotos(photos)
        }
    }

    private fun loadThumbnailsForNewPhotos(photos: List<CameraPhoto>) {
        viewModelScope.launch {
            val currentCache = _uiState.value.thumbnailCache.toMutableMap()

            photos.forEach { photo ->
                if (!currentCache.containsKey(photo.path)) {
                    // 썸네일이 캐시에 없으면 로드
                    getCameraThumbnailUseCase(photo.path).fold(
                        onSuccess = { thumbnailData ->
                            currentCache[photo.path] = thumbnailData
                            _uiState.value = _uiState.value.copy(
                                thumbnailCache = currentCache.toMap()
                            )
                        },
                        onFailure = { exception ->
                            // 썸네일 로드 실패는 로그만 남기고 UI에는 영향 주지 않음
                            // Log.w("PhotoPreviewViewModel", "썸네일 로드 실패: ${photo.path}", exception)
                        }
                    )
                }
            }
        }
    }

    /**
     * 전체화면 뷰어용 실제 파일 다운로드
     */
    fun downloadFullImage(photoPath: String) {
        android.util.Log.d(TAG, "=== downloadFullImage 호출: $photoPath ===")

        // 이미 캐시에 있는지 확인
        if (_fullImageCache.value.containsKey(photoPath)) {
            android.util.Log.d(TAG, "이미 캐시에 있음, 다운로드 생략")
            return
        }

        viewModelScope.launch {
            try {
                android.util.Log.d(TAG, "실제 파일 다운로드 시작: $photoPath")

                // 네이티브 코드에서 직접 다운로드
                val imageData = com.inik.camcon.CameraNative.downloadCameraPhoto(photoPath)

                if (imageData != null && imageData.isNotEmpty()) {
                    val currentCache = _fullImageCache.value.toMutableMap()
                    currentCache[photoPath] = imageData
                    _fullImageCache.value = currentCache

                    android.util.Log.d(TAG, "실제 파일 다운로드 성공: ${imageData.size} bytes")
                } else {
                    android.util.Log.e(TAG, "실제 파일 다운로드 실패: 데이터가 비어있음")
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "실제 파일 다운로드 중 예외", e)
            }
        }
    }

    /**
     * 전체화면 뷰어용 실제 파일 데이터 가져오기
     */
    fun getFullImage(photoPath: String): ByteArray? {
        return _fullImageCache.value[photoPath]
    }

    /**
     * 특정 사진이 다운로드 중인지 확인
     */
    fun isDownloadingFullImage(photoPath: String): Boolean {
        return _downloadingImages.value.contains(photoPath)
    }

    fun getThumbnail(photoPath: String): ByteArray? {
        return _uiState.value.thumbnailCache[photoPath]
    }

    fun selectPhoto(photo: CameraPhoto?) {
        _uiState.value = _uiState.value.copy(selectedPhoto = photo)
    }

    /**
     * 프리로딩: 사용자가 특정 인덱스에 도달했을 때 호출
     * 30번째 사진에 도달하면 다음 50장을 백그라운드에서 미리 로드
     */
    fun onPhotoIndexReached(currentIndex: Int) {
        val photos = _uiState.value.photos
        val currentPage = _uiState.value.currentPage
        val shouldPrefetch = currentIndex >= PREFETCH_THRESHOLD &&
                !_uiState.value.isLoadingMore &&
                _uiState.value.hasNextPage &&
                _prefetchedPage.value <= currentPage // 아직 프리로드하지 않은 페이지만

        if (shouldPrefetch) {
            android.util.Log.d(TAG, "프리로드 트리거: 현재 인덱스 $currentIndex, 임계값 $PREFETCH_THRESHOLD 도달")
            prefetchNextPage()
            _prefetchedPage.value = currentPage + 1 // Update prefetched page
        }
    }

    /**
     * 백그라운드에서 다음 페이지를 미리 로드 (사용자가 느끼지 못하게)
     */
    private fun prefetchNextPage() {
        if (_uiState.value.isLoadingMore || !_uiState.value.hasNextPage) {
            android.util.Log.d(TAG, "프리로드 건너뛰기: 이미 로딩 중이거나 다음 페이지가 없음")
            return
        }

        viewModelScope.launch {
            android.util.Log.d(TAG, "백그라운드 프리로드 시작")
            _uiState.value = _uiState.value.copy(isLoadingMore = true)

            val nextPage = _uiState.value.currentPage + 1
            getCameraPhotosPagedUseCase(page = nextPage, pageSize = PREFETCH_PAGE_SIZE).fold(
                onSuccess = { paginatedPhotos ->
                    val currentPhotos = _uiState.value.allPhotos
                    val newPhotos = currentPhotos + paginatedPhotos.photos

                    _uiState.value = _uiState.value.copy(
                        allPhotos = newPhotos,
                        photos = filterPhotos(newPhotos, _uiState.value.fileTypeFilter),
                        isLoadingMore = false,
                        currentPage = paginatedPhotos.currentPage,
                        totalPages = paginatedPhotos.totalPages,
                        hasNextPage = paginatedPhotos.hasNext
                    )

                    android.util.Log.d(
                        TAG,
                        "백그라운드 프리로드 완료: 추가된 사진 ${paginatedPhotos.photos.size}개, 총 ${newPhotos.size}개"
                    )

                    // 새로 로드된 사진들의 썸네일을 백그라운드에서 로드
                    loadThumbnailsForNewPhotos(paginatedPhotos.photos)
                },
                onFailure = { exception ->
                    android.util.Log.e(TAG, "백그라운드 프리로드 실패", exception)
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        error = exception.message ?: "추가 사진을 불러오는데 실패했습니다"
                    )
                }
            )
        }
    }

    private fun filterPhotos(
        photos: List<CameraPhoto>,
        filter: FileTypeFilter = _uiState.value.fileTypeFilter
    ): List<CameraPhoto> {
        android.util.Log.d(TAG, "filterPhotos 호출: 필터=$filter, 전체사진=${photos.size}개")

        val filtered = when (filter) {
            FileTypeFilter.ALL -> photos
            FileTypeFilter.JPG -> photos.filter {
                val isJpg = it.path.endsWith(".jpg", true) || it.path.endsWith(".jpeg", true)
                android.util.Log.v(TAG, "JPG 필터 확인: ${it.path} -> $isJpg")
                isJpg
            }
            FileTypeFilter.RAW -> photos.filter {
                val isRaw = it.path.endsWith(".arw", true) ||
                        it.path.endsWith(".cr2", true) ||
                        it.path.endsWith(".nef", true) ||
                        it.path.endsWith(".dng", true)
                android.util.Log.v(TAG, "RAW 필터 확인: ${it.path} -> $isRaw")
                isRaw
            }
        }

        android.util.Log.d(TAG, "필터링 결과: ${filtered.size}개")
        return filtered
    }

    /**
     * 파일 타입 필터 변경
     */
    fun changeFileTypeFilter(filter: FileTypeFilter) {
        android.util.Log.d(TAG, "파일 타입 필터 변경: ${_uiState.value.fileTypeFilter} -> $filter")

        val filteredPhotos = filterPhotos(_uiState.value.allPhotos, filter)

        _uiState.value = _uiState.value.copy(
            fileTypeFilter = filter,
            photos = filteredPhotos
        )

        _prefetchedPage.value = 0 // 필터 변경 시 프리로드 페이지 리셋

        android.util.Log.d(
            TAG,
            "필터링 완료: 전체 ${_uiState.value.allPhotos.size}개 -> 필터링된 ${filteredPhotos.size}개"
        )
    }
}