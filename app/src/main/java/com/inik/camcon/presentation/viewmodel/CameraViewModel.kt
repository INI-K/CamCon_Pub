package com.inik.camcon.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.domain.model.*
import com.inik.camcon.domain.repository.CameraRepository
import com.inik.camcon.domain.usecase.GetCameraFeedUseCase
import com.inik.camcon.domain.usecase.camera.StartTimelapseUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CameraUiState(
    val isConnected: Boolean = false,
    val currentCamera: Camera? = null,
    val cameraSettings: CameraSettings? = null,
    val isCapturing: Boolean = false,
    val capturedPhotos: List<CapturedPhoto> = emptyList(),
    val liveViewFrame: LiveViewFrame? = null,
    val isLiveViewActive: Boolean = false,
    val shootingMode: ShootingMode = ShootingMode.SINGLE,
    val error: String? = null
)

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val cameraRepository: CameraRepository,
    private val getCameraFeedUseCase: GetCameraFeedUseCase,
    private val startTimelapseUseCase: StartTimelapseUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    val cameraFeed: StateFlow<List<Camera>> = getCameraFeedUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var liveViewJob: kotlinx.coroutines.Job? = null
    private var timelapseJob: kotlinx.coroutines.Job? = null

    init {
        observeCameraConnection()
        observeCapturedPhotos()
    }

    private fun observeCameraConnection() {
        viewModelScope.launch {
            cameraRepository.isCameraConnected().collect { isConnected ->
                _uiState.update { it.copy(isConnected = isConnected) }
                if (isConnected) {
                    loadCameraSettings()
                }
            }
        }
    }

    private fun observeCapturedPhotos() {
        viewModelScope.launch {
            cameraRepository.getCapturedPhotos().collect { photos ->
                _uiState.update { it.copy(capturedPhotos = photos) }
            }
        }
    }

    private suspend fun loadCameraSettings() {
        cameraRepository.getCameraSettings()
            .onSuccess { settings ->
                _uiState.update { it.copy(cameraSettings = settings) }
            }
            .onFailure { error ->
                _uiState.update { it.copy(error = error.message) }
            }
    }

    fun connectCamera(cameraId: String) {
        viewModelScope.launch {
            cameraRepository.connectCamera(cameraId)
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message) }
                }
        }
    }

    fun capturePhoto() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCapturing = true, error = null) }

            cameraRepository.capturePhoto(_uiState.value.shootingMode)
                .onSuccess { photo ->
                    // Photo will be added to the list via observeCapturedPhotos
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message) }
                }

            _uiState.update { it.copy(isCapturing = false) }
        }
    }

    fun setShootingMode(mode: ShootingMode) {
        _uiState.update { it.copy(shootingMode = mode) }
    }

    fun startLiveView() {
        if (_uiState.value.isLiveViewActive) return

        liveViewJob = viewModelScope.launch {
            _uiState.update { it.copy(isLiveViewActive = true) }

            cameraRepository.startLiveView()
                .catch { error ->
                    _uiState.update {
                        it.copy(
                            isLiveViewActive = false,
                            error = error.message
                        )
                    }
                }
                .collect { frame ->
                    _uiState.update { it.copy(liveViewFrame = frame) }
                }
        }
    }

    fun stopLiveView() {
        liveViewJob?.cancel()
        liveViewJob = null

        viewModelScope.launch {
            cameraRepository.stopLiveView()
            _uiState.update {
                it.copy(
                    isLiveViewActive = false,
                    liveViewFrame = null
                )
            }
        }
    }

    fun startTimelapse(interval: Int, totalShots: Int) {
        if (timelapseJob?.isActive == true) return

        val settings = TimelapseSettings(
            interval = interval,
            totalShots = totalShots,
            duration = (interval * totalShots) / 60
        )

        timelapseJob = viewModelScope.launch {
            _uiState.update { it.copy(isCapturing = true, shootingMode = ShootingMode.TIMELAPSE) }

            startTimelapseUseCase(settings)
                .catch { error ->
                    _uiState.update {
                        it.copy(
                            isCapturing = false,
                            error = error.message
                        )
                    }
                }
                .collect { photo ->
                    // Photos will be added via observeCapturedPhotos
                }

            _uiState.update { it.copy(isCapturing = false) }
        }
    }

    fun stopTimelapse() {
        timelapseJob?.cancel()
        timelapseJob = null
        _uiState.update { it.copy(isCapturing = false) }
    }

    fun updateCameraSetting(key: String, value: String) {
        viewModelScope.launch {
            cameraRepository.updateCameraSetting(key, value)
                .onSuccess {
                    loadCameraSettings()
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message) }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
