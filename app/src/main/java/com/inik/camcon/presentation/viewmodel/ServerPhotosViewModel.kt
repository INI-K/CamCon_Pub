package com.inik.camcon.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.domain.model.CapturedPhoto
import com.inik.camcon.domain.repository.CameraRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServerPhotosUiState(
    val photos: List<CapturedPhoto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ServerPhotosViewModel @Inject constructor(
    private val cameraRepository: CameraRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServerPhotosUiState())
    val uiState: StateFlow<ServerPhotosUiState> = _uiState.asStateFlow()

    init {
        observeCapturedPhotos()
    }

    private fun observeCapturedPhotos() {
        viewModelScope.launch {
            cameraRepository.getCapturedPhotos().collect { photos ->
                _uiState.value = _uiState.value.copy(photos = photos)
            }
        }
    }

    fun deletePhoto(photoId: String) {
        viewModelScope.launch {
            cameraRepository.deletePhoto(photoId)
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        error = error.message
                    )
                }
        }
    }

    fun refreshPhotos() {
        // 새로고침 로직
        observeCapturedPhotos()
    }
}