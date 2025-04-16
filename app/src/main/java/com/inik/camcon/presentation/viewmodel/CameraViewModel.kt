package com.inik.camcon.presentation.viewmodel


import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.inik.camcon.domain.model.Camera
import com.inik.camcon.domain.usecase.CapturePhotoUseCase
import com.inik.camcon.domain.usecase.GetCameraFeedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val getCameraFeedUseCase: GetCameraFeedUseCase,
    private val capturePhotoUseCase: CapturePhotoUseCase
) : ViewModel() {

    private val _cameraFeed = MutableStateFlow<List<Camera>>(emptyList())
    val cameraFeed: StateFlow<List<Camera>> = _cameraFeed

    init {
        viewModelScope.launch {
            val data = getCameraFeedUseCase()
            _cameraFeed.value = data
        }
    }

    fun capturePhoto() = viewModelScope.launch {
        capturePhotoUseCase()
    }
}