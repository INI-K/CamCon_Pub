package com.inik.camcon.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.data.datasource.usb.UsbCameraManager
import com.inik.camcon.domain.model.Camera
import com.inik.camcon.domain.model.CameraCapabilities
import com.inik.camcon.domain.model.CameraSettings
import com.inik.camcon.domain.model.CapturedPhoto
import com.inik.camcon.domain.model.LiveViewFrame
import com.inik.camcon.domain.model.ShootingMode
import com.inik.camcon.domain.model.TimelapseSettings
import com.inik.camcon.domain.repository.CameraRepository
import com.inik.camcon.domain.usecase.GetCameraFeedUseCase
import com.inik.camcon.domain.usecase.camera.CapturePhotoUseCase
import com.inik.camcon.domain.usecase.camera.ConnectCameraUseCase
import com.inik.camcon.domain.usecase.camera.DisconnectCameraUseCase
import com.inik.camcon.domain.usecase.camera.GetCameraCapabilitiesUseCase
import com.inik.camcon.domain.usecase.camera.GetCameraPhotosUseCase
import com.inik.camcon.domain.usecase.camera.GetCameraSettingsUseCase
import com.inik.camcon.domain.usecase.camera.PerformAutoFocusUseCase
import com.inik.camcon.domain.usecase.camera.StartLiveViewUseCase
import com.inik.camcon.domain.usecase.camera.StartTimelapseUseCase
import com.inik.camcon.domain.usecase.camera.StopLiveViewUseCase
import com.inik.camcon.domain.usecase.camera.UpdateCameraSettingUseCase
import com.inik.camcon.domain.usecase.usb.RefreshUsbDevicesUseCase
import com.inik.camcon.domain.usecase.usb.RequestUsbPermissionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val cameraCapabilities: CameraCapabilities? = null,
    val isNativeCameraConnected: Boolean = false,
    val isLoading: Boolean = false,
    val isFocusing: Boolean = false,
    val isInitializing: Boolean = false,
    val isLiveViewLoading: Boolean = false
)

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val cameraRepository: CameraRepository,
    private val getCameraFeedUseCase: GetCameraFeedUseCase,
    private val connectCameraUseCase: ConnectCameraUseCase,
    private val disconnectCameraUseCase: DisconnectCameraUseCase,
    private val capturePhotoUseCase: CapturePhotoUseCase,
    private val startLiveViewUseCase: StartLiveViewUseCase,
    private val stopLiveViewUseCase: StopLiveViewUseCase,
    private val performAutoFocusUseCase: PerformAutoFocusUseCase,
    private val getCameraSettingsUseCase: GetCameraSettingsUseCase,
    private val updateCameraSettingUseCase: UpdateCameraSettingUseCase,
    private val getCameraCapabilitiesUseCase: GetCameraCapabilitiesUseCase,
    private val getCameraPhotosUseCase: GetCameraPhotosUseCase,
    private val refreshUsbDevicesUseCase: RefreshUsbDevicesUseCase,
    private val requestUsbPermissionUseCase: RequestUsbPermissionUseCase,
    private val startTimelapseUseCase: StartTimelapseUseCase,
    private val usbCameraManager: UsbCameraManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    val cameraFeed: StateFlow<List<Camera>> = getCameraFeedUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var liveViewJob: Job? = null
    private var timelapseJob: Job? = null
    private var initializationJob: Job? = null

    init {
        observeDataSources()
        initializeCameraDatabase()
    }

    private fun observeDataSources() {
        observeCameraConnection()
        observeCapturedPhotos()
        observeUsbDevices()
        observeCameraCapabilities()
    }

    private fun initializeCameraDatabase() {
        if (initializationJob?.isActive == true) return

        initializationJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.update { it.copy(isInitializing = true) }

                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isInitializing = false) }
                }
            } catch (e: Exception) {
                Log.e("CameraViewModel", "카메라 데이터베이스 로드 실패", e)
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isInitializing = false,
                            error = "카메라 데이터베이스 로드 실패: ${e.message}"
                        )
                    }
                }
            }
        }
    }

    private fun observeCameraConnection() {
        cameraRepository.isCameraConnected()
            .onEach { isConnected ->
                _uiState.update {
                    it.copy(
                        isConnected = isConnected,
                        error = if (isConnected) null else it.error // 연결되면 에러 메시지 제거
                    )
                }
                if (isConnected) {
                    loadCameraSettingsAsync()
                }
            }
            .catch { e ->
                Log.e("CameraViewModel", "카메라 연결 상태 관찰 중 오류", e)
                _uiState.update { it.copy(error = "연결 상태 확인 실패: ${e.message}") }
            }
            .launchIn(viewModelScope)
    }

    private fun observeCapturedPhotos() {
        cameraRepository.getCapturedPhotos()
            .onEach { photos ->
                _uiState.update { it.copy(capturedPhotos = photos) }
            }
            .catch { e ->
                Log.e("CameraViewModel", "촬영된 사진 목록 관찰 중 오류", e)
            }
            .launchIn(viewModelScope)
    }

    private fun observeUsbDevices() {
        usbCameraManager.connectedDevices
            .onEach { devices ->
                _uiState.update {
                    it.copy(
                        usbDeviceCount = devices.size,
                        error = if (devices.isEmpty() && !it.isConnected)
                            "USB 카메라가 감지되지 않음" else null
                    )
                }
            }
            .launchIn(viewModelScope)

        usbCameraManager.hasUsbPermission
            .onEach { hasPermission ->
                _uiState.update {
                    it.copy(
                        hasUsbPermission = hasPermission,
                        error = if (!hasPermission && _uiState.value.usbDeviceCount > 0)
                            "USB 권한이 필요합니다" else _uiState.value.error
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeCameraCapabilities() {
        usbCameraManager.cameraCapabilities
            .onEach { capabilities ->
                _uiState.update {
                    it.copy(
                        cameraCapabilities = capabilities,
                        error = if (capabilities == null && it.isConnected)
                            "카메라 기능 정보를 가져올 수 없음" else it.error
                    )
                }
            }
            .launchIn(viewModelScope)

        usbCameraManager.isNativeCameraConnected
            .onEach { isConnected ->
                Log.d("CameraViewModel", "네이티브 카메라 연결 상태 변경: $isConnected")

                _uiState.update {
                    it.copy(
                        isNativeCameraConnected = isConnected,
                        isConnected = isConnected // 네이티브 연결 상태를 기본 연결 상태로도 반영
                    )
                }

                if (isConnected) {
                    // 네이티브 카메라가 연결되면 자동으로 CameraRepository의 connectCamera 호출
                    Log.d("CameraViewModel", "네이티브 카메라 연결됨 - 자동으로 카메라 연결 시작")
                    autoConnectCamera()
                } else {
                    Log.d("CameraViewModel", "네이티브 카메라 연결 해제됨")
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * 네이티브 카메라가 연결되었을 때 자동으로 CameraRepository에 연결
     */
    private fun autoConnectCamera() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d("CameraViewModel", "자동 카메라 연결 시작")

                // USB로 연결된 카메라를 자동으로 연결
                connectCameraUseCase("auto")
                    .onSuccess {
                        Log.d("CameraViewModel", "자동 카메라 연결 성공 - 이벤트 리스너 활성화됨")
                        withContext(Dispatchers.Main) {
                            _uiState.update {
                                it.copy(
                                    isConnected = true,
                                    error = null
                                )
                            }
                        }

                        // 카메라 capabilities 가져오기
                        loadCameraCapabilitiesAsync()
                        // 카메라 설정과 지원 확인도 수행
                        loadCameraSettingsAsync()
                    }
                    .onFailure { error ->
                        Log.e("CameraViewModel", "자동 카메라 연결 실패", error)
                        withContext(Dispatchers.Main) {
                            _uiState.update {
                                it.copy(error = "자동 카메라 연결 실패: ${error.message}")
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.e("CameraViewModel", "자동 카메라 연결 중 예외 발생", e)
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(error = "자동 카메라 연결 실패: ${e.message}")
                    }
                }
            }
        }
    }

    private fun loadCameraSettingsAsync() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                getCameraSettingsUseCase()
                    .onSuccess { settings ->
                        withContext(Dispatchers.Main) {
                            _uiState.update { it.copy(cameraSettings = settings) }
                        }
                    }
                    .onFailure { error ->
                        Log.e("CameraViewModel", "카메라 설정 로드 실패", error)
                        withContext(Dispatchers.Main) {
                            _uiState.update {
                                it.copy(error = "카메라 설정 로드 실패: ${error.message ?: "알 수 없는 오류"}")
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.e("CameraViewModel", "카메라 설정 로드 중 예외 발생", e)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(error = "카메라 설정 로드 실패: ${e.message}") }
                }
            }
        }
    }

    fun connectCamera(cameraId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }

                connectCameraUseCase(cameraId)
                    .onSuccess {
                        Log.d("CameraViewModel", "카메라 연결 성공")
                        withContext(Dispatchers.Main) {
                            _uiState.update { it.copy(isConnected = true) }
                        }

                        // 카메라 capabilities 가져오기
                        loadCameraCapabilitiesAsync()
                        // 카메라 설정과 지원 확인도 수행
                        loadCameraSettingsAsync()
                    }
                    .onFailure { error ->
                        Log.e("CameraViewModel", "카메라 연결 실패", error)
                        withContext(Dispatchers.Main) {
                            _uiState.update {
                                it.copy(
                                    isConnected = false,
                                    error = error.message
                                )
                            }
                        }
                    }

                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                Log.e("CameraViewModel", "카메라 연결 중 예외 발생", e)
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isConnected = false,
                            error = "카메라 연결 실패: ${e.message}"
                        )
                    }
                }
            }
        }
    }

    fun refreshUsbDevices() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // USB 디바이스 목록 새로고침
                val devices = refreshUsbDevicesUseCase()
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            usbDeviceCount = devices.size,
                            error = if (devices.isEmpty()) "USB 카메라가 감지되지 않음" else null
                        )
                    }
                }

                // 디바이스가 발견되면 권한 요청
                devices.firstOrNull()?.let { device ->
                    if (!usbCameraManager.hasUsbPermission.value) {
                        withContext(Dispatchers.Main) {
                            requestUsbPermissionUseCase(device)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("CameraViewModel", "USB 디바이스 새로고침 실패", e)
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(error = "USB 디바이스 확인 실패: ${e.message}")
                    }
                }
            }
        }
    }

    fun requestUsbPermission() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val devices = refreshUsbDevicesUseCase()
                if (devices.isNotEmpty()) {
                    val device = devices.first()
                    withContext(Dispatchers.Main) {
                        requestUsbPermissionUseCase(device)
                        _uiState.update {
                            it.copy(error = "USB 권한을 요청했습니다. 대화상자에서 승인해주세요.")
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(error = "USB 카메라가 감지되지 않았습니다")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("CameraViewModel", "USB 권한 요청 실패", e)
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(error = "USB 권한 요청 실패: ${e.message}")
                    }
                }
            }
        }
    }

    fun capturePhoto() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d("CameraViewModel", "=== 사진 촬영 요청 시작 ===")
                Log.d("CameraViewModel", "현재 UI 상태: isConnected=${_uiState.value.isConnected}")
                Log.d("CameraViewModel", "현재 UI 상태: isCapturing=${_uiState.value.isCapturing}")
                Log.d("CameraViewModel", "촬영 모드: ${_uiState.value.shootingMode}")

                _uiState.update { it.copy(isCapturing = true, error = null) }

                capturePhotoUseCase(_uiState.value.shootingMode)
                    .onSuccess { photo ->
                        // Photo will be added to the list via observeCapturedPhotos
                        Log.d("CameraViewModel", "✓ 사진 촬영 성공: ${photo.filePath}")
                        Log.d("CameraViewModel", "파일 크기: ${photo.size} bytes")
                    }
                    .onFailure { error ->
                        Log.e("CameraViewModel", "❌ 사진 촬영 실패", error)
                        withContext(Dispatchers.Main) {
                            _uiState.update {
                                it.copy(error = "사진 촬영 실패: ${error.message ?: "알 수 없는 오류"}")
                            }
                        }
                    }

                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isCapturing = false) }
                }
                Log.d("CameraViewModel", "=== 사진 촬영 요청 완료 ===")
            } catch (e: Exception) {
                Log.e("CameraViewModel", "❌ 사진 촬영 중 예외 발생", e)
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isCapturing = false,
                            error = "사진 촬영 실패: ${e.message}"
                        )
                    }
                }
            }
        }
    }

    fun setShootingMode(mode: ShootingMode) {
        _uiState.update { it.copy(shootingMode = mode) }
    }

    fun startLiveView() {
        if (_uiState.value.isLiveViewActive || liveViewJob?.isActive == true) return

        liveViewJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                // 라이브뷰 지원 여부 확인
                val capabilities = _uiState.value.cameraCapabilities
                if (capabilities != null && !capabilities.canLiveView) {
                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(error = "이 카메라는 라이브뷰를 지원하지 않습니다.")
                        }
                    }
                    return@launch
                }

                Log.d("CameraViewModel", "라이브뷰 시작 시도")
                _uiState.update { it.copy(isLiveViewLoading = true) }

                startLiveViewUseCase()
                    .catch { error ->
                        Log.e("CameraViewModel", "라이브뷰 오류", error)
                        withContext(Dispatchers.Main) {
                            _uiState.update {
                                it.copy(
                                    isLiveViewActive = false,
                                    isLiveViewLoading = false,
                                    error = error.message
                                )
                            }
                        }
                    }
                    .collect { frame ->
                        withContext(Dispatchers.Main) {
                            _uiState.update {
                                it.copy(
                                    isLiveViewActive = true,
                                    liveViewFrame = frame,
                                    isLiveViewLoading = false
                                )
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.e("CameraViewModel", "라이브뷰 시작 중 예외 발생", e)
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isLiveViewActive = false,
                            liveViewFrame = null,
                            isLiveViewLoading = false,
                            error = "라이브뷰 시작 실패: ${e.message}"
                        )
                    }
                }
            }
        }
    }

    fun stopLiveView() {
        liveViewJob?.cancel()
        liveViewJob = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                stopLiveViewUseCase()
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isLiveViewActive = false,
                            liveViewFrame = null,
                            isLiveViewLoading = false
                        )
                    }
                }
                Log.d("CameraViewModel", "라이브뷰 중지 성공. PC 모드 종료를 위해 disconnectCamera 호출.")
                disconnectCamera()
            } catch (e: Exception) {
                Log.e("CameraViewModel", "라이브뷰 중지 중 예외 발생", e)
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isLiveViewActive = false,
                            liveViewFrame = null,
                            isLiveViewLoading = false,
                            error = "라이브뷰 중지 실패: ${e.message}"
                        )
                    }
                }
                Log.w("CameraViewModel", "라이브뷰 중지 실패했으나, 카메라 연결 해제 시도")
                disconnectCamera()
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

        timelapseJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.update {
                    it.copy(
                        isCapturing = true,
                        shootingMode = ShootingMode.TIMELAPSE
                    )
                }

                startTimelapseUseCase(settings)
                    .catch { error ->
                        Log.e("CameraViewModel", "타임랩스 실행 중 오류", error)
                        withContext(Dispatchers.Main) {
                            _uiState.update {
                                it.copy(
                                    isCapturing = false,
                                    error = "타임랩스 시작 실패: ${error.message ?: "알 수 없는 오류"}"
                                )
                            }
                        }
                    }
                    .collect { photo ->
                        // Photos will be added via observeCapturedPhotos
                        Log.d("CameraViewModel", "타임랩스 사진 촬영: ${photo.filePath}")
                    }

                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isCapturing = false) }
                }
            } catch (e: Exception) {
                Log.e("CameraViewModel", "타임랩스 중 예외 발생", e)
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isCapturing = false,
                            error = "타임랩스 실패: ${e.message}"
                        )
                    }
                }
            }
        }
    }

    fun stopTimelapse() {
        timelapseJob?.cancel()
        timelapseJob = null
        _uiState.update { it.copy(isCapturing = false) }
    }

    fun updateCameraSetting(key: String, value: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                updateCameraSettingUseCase(key, value)
                    .onSuccess {
                        loadCameraSettingsAsync()
                    }
                    .onFailure { error ->
                        Log.e("CameraViewModel", "카메라 설정 업데이트 실패", error)
                        withContext(Dispatchers.Main) {
                            _uiState.update {
                                it.copy(error = "카메라 설정 업데이트 실패: ${error.message ?: "알 수 없는 오류"}")
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.e("CameraViewModel", "카메라 설정 업데이트 중 예외 발생", e)
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(error = "카메라 설정 업데이트 실패: ${e.message}")
                    }
                }
            }
        }
    }

    fun performAutoFocus() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.update { it.copy(isFocusing = true) }

                performAutoFocusUseCase()
                    .onSuccess {
                        // 포커싱 성공 피드백 (잠시 표시 후 사라짐)
                        withContext(Dispatchers.Main) {
                            _uiState.update { it.copy(isFocusing = false) }

                            // 성공 메시지를 잠시 표시
                            _uiState.update { it.copy(error = "초점 맞춤 완료") }
                        }

                        delay(1000)

                        withContext(Dispatchers.Main) {
                            if (_uiState.value.error == "초점 맞춤 완료") {
                                _uiState.update { it.copy(error = null) }
                            }
                        }
                    }
                    .onFailure { error ->
                        Log.e("CameraViewModel", "자동초점 실패", error)
                        withContext(Dispatchers.Main) {
                            _uiState.update {
                                it.copy(
                                    isFocusing = false,
                                    error = "자동초점 실패: ${error.message ?: "알 수 없는 오류"}"
                                )
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.e("CameraViewModel", "자동초점 중 예외 발생", e)
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isFocusing = false,
                            error = "자동초점 실패: ${e.message}"
                        )
                    }
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun refreshCameraCapabilities() {
        usbCameraManager.refreshCameraCapabilities()
    }

    fun disconnectCamera() {
        Log.d("CameraViewModel", "disconnectCamera 호출됨. PC 모드 종료 시도.")
        liveViewJob?.cancel()
        timelapseJob?.cancel()
        initializationJob?.cancel()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                disconnectCameraUseCase()
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isConnected = false,
                            isNativeCameraConnected = false,
                            cameraCapabilities = null,
                            currentCamera = null,
                            error = null,
                            isLiveViewActive = false,
                            liveViewFrame = null,
                            isLiveViewLoading = false,
                            isCapturing = false,
                            isFocusing = false
                        )
                    }
                }
                Log.i("CameraViewModel", "카메라 연결 해제 성공 (UseCase). PC 모드 종료됨.")
            } catch (e: Exception) {
                Log.e("CameraViewModel", "카메라 연결 해제 실패", e)
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(error = "카메라 연결 해제 실패: ${e.message}")
                    }
                }
            }
        }
    }

    private fun loadCameraCapabilitiesAsync() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                getCameraCapabilitiesUseCase()
                    .onSuccess { capabilities ->
                        withContext(Dispatchers.Main) {
                            _uiState.update {
                                it.copy(cameraCapabilities = capabilities)
                            }
                        }
                    }
                    .onFailure { error ->
                        Log.e("CameraViewModel", "Failed to load camera capabilities", error)
                    }
            } catch (e: Exception) {
                Log.e("CameraViewModel", "카메라 기능 로드 중 예외 발생", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // ViewModel이 해제될 때 모든 작업 정리
        liveViewJob?.cancel()
        timelapseJob?.cancel()
        initializationJob?.cancel()

        // USB 매니저 정리
        try {
            usbCameraManager.cleanup()
        } catch (e: Exception) {
            Log.w("CameraViewModel", "USB 매니저 정리 중 오류", e)
        }
    }
}
