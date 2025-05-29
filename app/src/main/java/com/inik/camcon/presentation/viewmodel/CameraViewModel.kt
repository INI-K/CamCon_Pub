package com.inik.camcon.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.data.datasource.camera.CameraDatabaseManager
import com.inik.camcon.data.datasource.camera.SupportedCamera
import com.inik.camcon.data.datasource.usb.UsbCameraManager
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
    val error: String? = null,
    val usbDeviceCount: Int = 0,
    val hasUsbPermission: Boolean = false,
    val supportedCamera: SupportedCamera? = null,
    val supportedFeatures: List<String> = emptyList()
)

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val cameraRepository: CameraRepository,
    private val getCameraFeedUseCase: GetCameraFeedUseCase,
    private val startTimelapseUseCase: StartTimelapseUseCase,
    private val usbCameraManager: UsbCameraManager,
    private val cameraDatabaseManager: CameraDatabaseManager
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
        observeUsbDevices()
        initializeCameraDatabase()
    }

    private fun initializeCameraDatabase() {
        viewModelScope.launch {
            try {
                cameraDatabaseManager.initializeDatabase()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "카메라 데이터베이스 로드 실패: ${e.message}")
                }
            }
        }
    }

    private fun observeCameraConnection() {
        viewModelScope.launch {
            cameraRepository.isCameraConnected().collect { isConnected ->
                _uiState.update { it.copy(isConnected = isConnected) }
                if (isConnected) {
                    loadCameraSettings()
                    checkCameraSupport()
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

    private fun observeUsbDevices() {
        viewModelScope.launch {
            usbCameraManager.connectedDevices.collect { devices ->
                _uiState.update {
                    it.copy(
                        usbDeviceCount = devices.size,
                        error = if (devices.isEmpty()) "USB 카메라가 감지되지 않음" else null
                    )
                }
            }
        }

        viewModelScope.launch {
            usbCameraManager.hasUsbPermission.collect { hasPermission ->
                _uiState.update {
                    it.copy(
                        hasUsbPermission = hasPermission,
                        error = if (!hasPermission && _uiState.value.usbDeviceCount > 0)
                            "USB 권한이 필요합니다" else _uiState.value.error
                    )
                }
            }
        }
    }

    private suspend fun loadCameraSettings() {
        cameraRepository.getCameraSettings()
            .onSuccess { settings ->
                _uiState.update { it.copy(cameraSettings = settings) }
            }
            .onFailure { error ->
                _uiState.update { it.copy(error = "카메라 설정 로드 실패: ${error.message ?: "알 수 없는 오류"}") }
            }
    }

    private suspend fun checkCameraSupport() {
        val currentCamera = _uiState.value.currentCamera
        if (currentCamera != null) {
            val supportedCamera = cameraDatabaseManager.findSupportedCamera(
                vendor = extractVendor(currentCamera.name),
                model = extractModel(currentCamera.name)
            )

            val features = supportedCamera?.features ?: emptyList()

            _uiState.update {
                it.copy(
                    supportedCamera = supportedCamera,
                    supportedFeatures = features
                )
            }
        }
    }

    private fun extractVendor(cameraName: String): String {
        return when {
            cameraName.contains("Canon", ignoreCase = true) -> "Canon"
            cameraName.contains("Nikon", ignoreCase = true) -> "Nikon"
            cameraName.contains("Sony", ignoreCase = true) -> "Sony"
            cameraName.contains("Fuji", ignoreCase = true) -> "Fujifilm"
            cameraName.contains("Panasonic", ignoreCase = true) -> "Panasonic"
            else -> "Unknown"
        }
    }

    private fun extractModel(cameraName: String): String {
        val vendor = extractVendor(cameraName)
        return cameraName.substringAfter(vendor).trim()
    }

    fun connectCamera(cameraId: String) {
        viewModelScope.launch {
            cameraRepository.connectCamera(cameraId)
                .onFailure { error ->
                    _uiState.update { it.copy(error = "카메라 연결 실패: ${error.message ?: "알 수 없는 오류"}") }
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
                    _uiState.update { it.copy(error = "사진 촬영 실패: ${error.message ?: "알 수 없는 오류"}") }
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
                            error = "라이브 뷰 시작 실패: ${error.message ?: "알 수 없는 오류"}"
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
                            error = "타임랩스 시작 실패: ${error.message ?: "알 수 없는 오류"}"
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
                    _uiState.update { it.copy(error = "카메라 설정 업데이트 실패: ${error.message ?: "알 수 없는 오류"}") }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
