package com.inik.camcon.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.domain.model.CameraPhoto
import com.inik.camcon.domain.repository.CameraRepository
import com.inik.camcon.domain.usecase.camera.GetCameraPhotosUseCase
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
    val selectedPhoto: CameraPhoto? = null
)

@HiltViewModel
class PhotoPreviewViewModel @Inject constructor(
    private val cameraRepository: CameraRepository,
    private val getCameraPhotosUseCase: GetCameraPhotosUseCase,
    private val photoCaptureEventManager: PhotoCaptureEventManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PhotoPreviewUiState())
    val uiState: StateFlow<PhotoPreviewUiState> = _uiState.asStateFlow()

    init {
        loadCameraPhotos()
        observePhotoCaptureEvents()
    }

    private fun observePhotoCaptureEvents() {
        photoCaptureEventManager.photoCaptureEvent
            .onEach {
                // 사진이 촬영되면 목록을 자동으로 새로고침
                loadCameraPhotos()
            }
            .launchIn(viewModelScope)
    }

    fun loadCameraPhotos() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                getCameraPhotosUseCase()
                    .onSuccess { photos ->
                        _uiState.value = _uiState.value.copy(
                            photos = photos,
                            isLoading = false
                        )
                    }
                    .onFailure { error ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = error.message
                        )
                    }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
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
}
