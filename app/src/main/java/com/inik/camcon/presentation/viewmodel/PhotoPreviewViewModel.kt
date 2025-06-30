package com.inik.camcon.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.domain.model.CameraPhoto
import com.inik.camcon.domain.repository.CameraRepository
import com.inik.camcon.domain.usecase.camera.GetCameraPhotosPagedUseCase
import com.inik.camcon.domain.usecase.camera.GetCameraPhotosUseCase
import com.inik.camcon.domain.usecase.camera.GetCameraThumbnailUseCase
import com.inik.camcon.domain.usecase.camera.PhotoCaptureEventManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PhotoPreviewUiState(
    val photos: List<CameraPhoto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedPhoto: CameraPhoto? = null,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val hasNextPage: Boolean = false,
    val isLoadingMore: Boolean = false,
    val thumbnailCache: Map<String, ByteArray> = emptyMap()
)

@HiltViewModel
class PhotoPreviewViewModel @Inject constructor(
    private val cameraRepository: CameraRepository,
    private val getCameraPhotosPagedUseCase: GetCameraPhotosPagedUseCase,
    private val getCameraThumbnailUseCase: GetCameraThumbnailUseCase,
    private val getCameraPhotosUseCase: GetCameraPhotosUseCase,
    private val photoCaptureEventManager: PhotoCaptureEventManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PhotoPreviewUiState())
    val uiState: StateFlow<PhotoPreviewUiState> = _uiState.asStateFlow()

    init {
        loadInitialPhotos()
        observePhotoCaptureEvents()
    }

    private fun observePhotoCaptureEvents() {
        photoCaptureEventManager.photoCaptureEvent
            .onEach {
                // 사진이 촬영되면 목록을 자동으로 새로고침
                loadInitialPhotos()
            }
            .launchIn(viewModelScope)
    }

    fun loadInitialPhotos() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                photos = emptyList(),
                currentPage = 0
            )

            getCameraPhotosPagedUseCase(page = 0, pageSize = 20).fold(
                onSuccess = { paginatedPhotos ->
                    _uiState.value = _uiState.value.copy(
                        photos = paginatedPhotos.photos,
                        isLoading = false,
                        currentPage = paginatedPhotos.currentPage,
                        totalPages = paginatedPhotos.totalPages,
                        hasNextPage = paginatedPhotos.hasNext
                    )

                    // 첫 번째 페이지의 썸네일 로드 시작
                    loadThumbnailsForCurrentPage()
                },
                onFailure = { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = exception.message ?: "사진을 불러오는데 실패했습니다"
                    )
                }
            )
        }
    }

    fun loadNextPage() {
        if (_uiState.value.isLoadingMore || !_uiState.value.hasNextPage) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)

            val nextPage = _uiState.value.currentPage + 1
            getCameraPhotosPagedUseCase(page = nextPage, pageSize = 20).fold(
                onSuccess = { paginatedPhotos ->
                    val currentPhotos = _uiState.value.photos
                    val newPhotos = currentPhotos + paginatedPhotos.photos

                    _uiState.value = _uiState.value.copy(
                        photos = newPhotos,
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

    fun selectPhoto(photo: CameraPhoto?) {
        _uiState.value = _uiState.value.copy(selectedPhoto = photo)
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

    fun getThumbnail(photoPath: String): ByteArray? {
        return _uiState.value.thumbnailCache[photoPath]
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}