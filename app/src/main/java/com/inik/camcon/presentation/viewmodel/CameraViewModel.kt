package com.inik.camcon.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.CameraNative
import com.inik.camcon.NativeErrorCallback
import com.inik.camcon.data.datasource.usb.UsbCameraManager
import com.inik.camcon.domain.model.Camera
import com.inik.camcon.domain.model.ShootingMode
import com.inik.camcon.domain.model.TimelapseSettings
import com.inik.camcon.domain.repository.CameraRepository
import com.inik.camcon.domain.usecase.GetCameraFeedUseCase
import com.inik.camcon.domain.usecase.camera.CapturePhotoUseCase
import com.inik.camcon.domain.usecase.camera.ConnectCameraUseCase
import com.inik.camcon.domain.usecase.camera.DisconnectCameraUseCase
import com.inik.camcon.domain.usecase.camera.GetCameraCapabilitiesUseCase
import com.inik.camcon.domain.usecase.camera.GetCameraSettingsUseCase
import com.inik.camcon.domain.usecase.camera.PerformAutoFocusUseCase
import com.inik.camcon.domain.usecase.camera.StartLiveViewUseCase
import com.inik.camcon.domain.usecase.camera.StartTimelapseUseCase
import com.inik.camcon.domain.usecase.camera.StopLiveViewUseCase
import com.inik.camcon.domain.usecase.camera.UpdateCameraSettingUseCase
import com.inik.camcon.domain.usecase.usb.RefreshUsbDevicesUseCase
import com.inik.camcon.domain.usecase.usb.RequestUsbPermissionUseCase
import com.inik.camcon.presentation.viewmodel.state.CameraUiStateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 카메라 기능을 위한 ViewModel
 * MVVM 패턴에 따라 UI 상태와 비즈니스 로직을 분리하여 관리
 */
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
    private val startTimelapseUseCase: StartTimelapseUseCase,
    private val refreshUsbDevicesUseCase: RefreshUsbDevicesUseCase,
    private val requestUsbPermissionUseCase: RequestUsbPermissionUseCase,
    private val usbCameraManager: UsbCameraManager,
    private val uiStateManager: CameraUiStateManager
) : ViewModel() {

    // UI 상태는 StateManager에 위임
    val uiState: StateFlow<CameraUiState> = uiStateManager.uiState

    val cameraFeed: StateFlow<List<Camera>> = getCameraFeedUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // 작업 관리
    private var liveViewJob: Job? = null
    private var timelapseJob: Job? = null
    private var initializationJob: Job? = null

    // 상태 플래그
    private var isTabSwitching = false
    private var isAutoConnecting = false
    private var isAppResuming = false
    private var isViewModelInitialized = false

    companion object {
        private const val TAG = "카메라뷰모델"
    }

    init {
        initializeViewModel()
    }

    private fun initializeViewModel() {
        if (!isViewModelInitialized) {
            isAppResuming = true
            Log.d(TAG, "ViewModel 초기화 시작")

            setupObservers()
            initializeCameraRepository()
            registerNativeErrorCallback()
            setupUsbDisconnectionCallback()

            // 3초 후 앱 재개 상태 해제
            viewModelScope.launch {
                delay(3000)
                isAppResuming = false
                Log.d(TAG, "앱 재개 상태 해제")
            }

            isViewModelInitialized = true
        }
    }

    private fun setupObservers() {
        observeCameraConnection()
        observeCapturedPhotos()
        observeUsbDevices()
        observeCameraCapabilities()
        observeEventListenerState()
        observeCameraInitialization()
    }

    private fun initializeCameraRepository() {
        if (initializationJob?.isActive == true) return

        initializationJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                uiStateManager.updateInitializingState(true)
                cameraRepository.setPhotoPreviewMode(false)
                uiStateManager.updateInitializingState(false)
            } catch (e: Exception) {
                Log.e(TAG, "카메라 리포지토리 초기화 실패", e)
                uiStateManager.setError("카메라 초기화 실패: ${e.message}")
                uiStateManager.updateInitializingState(false)
            }
        }
    }

    private fun registerNativeErrorCallback() {
        try {
            CameraNative.setErrorCallback(object : NativeErrorCallback {
                override fun onNativeError(errorCode: Int, errorMessage: String) {
                    handleNativeError(errorCode, errorMessage)
                }
            })
            Log.d(TAG, "네이티브 에러 콜백 등록 완료")
        } catch (e: Exception) {
            Log.e(TAG, "네이티브 에러 콜백 등록 실패", e)
        }
    }

    private fun handleNativeError(errorCode: Int, errorMessage: String) {
        Log.e(TAG, "네이티브 에러 감지: 코드=$errorCode, 메시지=$errorMessage")

        val error = when (errorCode) {
            -10 -> "USB 포트 타임아웃 에러 발생 (-10): $errorMessage"
            -52 -> {
                uiStateManager.showRestartDialog(true)
                "USB 카메라 감지 실패 (-52): $errorMessage"
            }

            -35 -> "USB 포트 쓰기 실패 (-35): $errorMessage\n\nUSB 케이블을 확인하거나 카메라를 재연결하세요."
            else -> "알 수 없는 네이티브 에러 ($errorCode): $errorMessage"
        }

        uiStateManager.setError(error)
    }

    private fun setupUsbDisconnectionCallback() {
        try {
            usbCameraManager.setUsbDisconnectionCallback {
                handleUsbDisconnection()
            }
            Log.d(TAG, "USB 분리 콜백 설정 완료")
        } catch (e: Exception) {
            Log.e(TAG, "USB 분리 콜백 설정 실패", e)
        }
    }

    // MARK: - Observers

    private fun observeCameraConnection() {
        cameraRepository.isCameraConnected()
            .onEach { isConnected ->
                uiStateManager.updateConnectionState(isConnected)
                if (isConnected) {
                    loadCameraSettingsAsync()
                }
            }
            .catch { e ->
                Log.e(TAG, "카메라 연결 상태 관찰 중 오류", e)
                uiStateManager.setError("연결 상태 확인 실패: ${e.message}")
            }
            .launchIn(viewModelScope)
    }

    private fun observeCapturedPhotos() {
        cameraRepository.getCapturedPhotos()
            .onEach { photos ->
                uiStateManager.updateCapturedPhotos(photos)
            }
            .catch { e ->
                Log.e(TAG, "촬영된 사진 목록 관찰 중 오류", e)
            }
            .launchIn(viewModelScope)
    }

    private fun observeUsbDevices() {
        usbCameraManager.connectedDevices
            .onEach { devices ->
                uiStateManager.updateUsbDeviceState(devices.size, uiState.value.hasUsbPermission)
            }
            .launchIn(viewModelScope)

        usbCameraManager.hasUsbPermission
            .onEach { hasPermission ->
                uiStateManager.updateUsbDeviceState(uiState.value.usbDeviceCount, hasPermission)
            }
            .launchIn(viewModelScope)
    }

    private fun observeCameraCapabilities() {
        usbCameraManager.cameraCapabilities
            .onEach { capabilities ->
                uiStateManager.updateCameraCapabilities(capabilities)
            }
            .launchIn(viewModelScope)

        usbCameraManager.isNativeCameraConnected
            .onEach { isConnected ->
                Log.d(TAG, "네이티브 카메라 연결 상태 변경: $isConnected")

                if (isAppResuming && !isConnected) {
                    Log.d(TAG, "앱 재개 중 연결 해제 이벤트 무시")
                    return@onEach
                }

                uiStateManager.updateNativeCameraConnection(isConnected)

                when {
                    isConnected && !isAutoConnecting && !isAppResuming -> {
                        Log.d(TAG, "네이티브 카메라 연결됨 - 자동 연결 시작")
                        autoConnectCamera()
                    }
                    !isConnected -> {
                        Log.d(TAG, "네이티브 카메라 연결 해제됨")
                        isAutoConnecting = false
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeEventListenerState() {
        cameraRepository.isEventListenerActive()
            .onEach { isActive ->
                uiStateManager.updateEventListenerState(isActive)
            }
            .catch { e ->
                Log.e(TAG, "이벤트 리스너 상태 관찰 중 오류", e)
            }
            .launchIn(viewModelScope)
    }

    private fun observeCameraInitialization() {
        cameraRepository.isInitializing()
            .onEach { isInitializing ->
                uiStateManager.updateCameraInitialization(isInitializing)
            }
            .catch { e ->
                Log.e(TAG, "카메라 초기화 상태 관찰 중 오류", e)
            }
            .launchIn(viewModelScope)
    }

    // MARK: - Connection Management

    private fun autoConnectCamera() {
        if (isAutoConnecting) {
            Log.d(TAG, "자동 카메라 연결이 이미 진행 중")
            return
        }

        isAutoConnecting = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "자동 카메라 연결 시작")
                uiStateManager.updateUsbInitialization(true, "USB 카메라 초기화 중...")

                connectCameraUseCase("auto")
                    .onSuccess {
                        Log.d(TAG, "자동 카메라 연결 성공")
                        uiStateManager.onConnectionSuccess()
                        loadCameraCapabilitiesAsync()
                        loadCameraSettingsAsync()
                    }
                    .onFailure { error ->
                        Log.e(TAG, "자동 카메라 연결 실패", error)
                        uiStateManager.onConnectionFailure(error)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "자동 카메라 연결 중 예외 발생", e)
                uiStateManager.onConnectionFailure(e)
            } finally {
                isAutoConnecting = false
                Log.d(TAG, "자동 카메라 연결 완료")
            }
        }
    }

    // MARK: - Public Methods

    fun connectCamera(cameraId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                uiStateManager.updateLoadingState(true)
                uiStateManager.clearError()

                connectCameraUseCase(cameraId)
                    .onSuccess {
                        Log.d(TAG, "카메라 연결 성공")
                        uiStateManager.onConnectionSuccess()
                        loadCameraCapabilitiesAsync()
                        loadCameraSettingsAsync()
                    }
                    .onFailure { error ->
                        Log.e(TAG, "카메라 연결 실패", error)
                        uiStateManager.onConnectionFailure(error)
                    }

                uiStateManager.updateLoadingState(false)
            } catch (e: Exception) {
                Log.e(TAG, "카메라 연결 중 예외 발생", e)
                uiStateManager.updateLoadingState(false)
                uiStateManager.onConnectionFailure(e)
            }
        }
    }

    fun disconnectCamera() {
        Log.d(TAG, "카메라 연결 해제 요청")
        liveViewJob?.cancel()
        timelapseJob?.cancel()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                disconnectCameraUseCase()
                uiStateManager.onCameraDisconnected()
                Log.i(TAG, "카메라 연결 해제 성공")
            } catch (e: Exception) {
                Log.e(TAG, "카메라 연결 해제 실패", e)
                uiStateManager.setError("카메라 연결 해제 실패: ${e.message}")
            }
        }
    }

    fun refreshUsbDevices() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val devices = refreshUsbDevicesUseCase()
                uiStateManager.updateUsbDeviceState(devices.size, uiState.value.hasUsbPermission)

                devices.firstOrNull()?.let { device ->
                    if (!usbCameraManager.hasUsbPermission.value) {
                        requestUsbPermissionUseCase(device)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "USB 디바이스 새로고침 실패", e)
                uiStateManager.setError("USB 디바이스 확인 실패: ${e.message}")
            }
        }
    }

    fun requestUsbPermission() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                uiStateManager.updateUsbInitialization(true, "USB 권한 요청 중...")

                val devices = refreshUsbDevicesUseCase()
                if (devices.isNotEmpty()) {
                    val device = devices.first()
                    requestUsbPermissionUseCase(device)
                    uiStateManager.setError("USB 권한을 요청했습니다. 대화상자에서 승인해주세요.")
                    uiStateManager.updateUsbInitialization(false, "USB 권한 대기 중...")
                } else {
                    uiStateManager.setError("USB 카메라가 감지되지 않았습니다")
                    uiStateManager.updateUsbInitialization(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "USB 권한 요청 실패", e)
                uiStateManager.setError("USB 권한 요청 실패: ${e.message}")
                uiStateManager.updateUsbInitialization(false)
            }
        }
    }

    // MARK: - Camera Operations

    fun capturePhoto() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "사진 촬영 요청 시작")
                uiStateManager.updateCapturingState(true)
                uiStateManager.clearError()

                capturePhotoUseCase(uiState.value.shootingMode)
                    .onSuccess { photo ->
                        Log.d(TAG, "사진 촬영 성공: ${photo.filePath}")
                    }
                    .onFailure { error ->
                        Log.e(TAG, "사진 촬영 실패", error)
                        uiStateManager.setError("사진 촬영 실패: ${error.message ?: "알 수 없는 오류"}")
                    }

                uiStateManager.updateCapturingState(false)
            } catch (e: Exception) {
                Log.e(TAG, "사진 촬영 중 예외 발생", e)
                uiStateManager.updateCapturingState(false)
                uiStateManager.setError("사진 촬영 실패: ${e.message}")
            }
        }
    }

    fun setShootingMode(mode: ShootingMode) {
        uiStateManager.setShootingMode(mode)
    }

    fun startLiveView() {
        if (uiState.value.isLiveViewActive || liveViewJob?.isActive == true) {
            Log.d(TAG, "라이브뷰가 이미 활성화되어 있음")
            return
        }

        Log.d(TAG, "라이브뷰 시작 요청")
        liveViewJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val capabilities = uiState.value.cameraCapabilities
                if (capabilities != null && !capabilities.canLiveView) {
                    Log.w(TAG, "카메라가 라이브뷰를 지원하지 않음")
                    uiStateManager.setError("이 카메라는 라이브뷰를 지원하지 않습니다.")
                    return@launch
                }

                if (!uiState.value.isConnected) {
                    Log.e(TAG, "카메라가 연결되지 않은 상태")
                    uiStateManager.setError("카메라가 연결되지 않았습니다. 먼저 카메라를 연결해주세요.")
                    return@launch
                }

                uiStateManager.updateLiveViewState(isLoading = true)
                uiStateManager.clearError()

                startLiveViewUseCase()
                    .catch { error ->
                        Log.e(TAG, "라이브뷰 Flow 오류", error)
                        uiStateManager.updateLiveViewState(isActive = false, isLoading = false)
                        uiStateManager.setError("라이브뷰 시작 실패: ${error.message}")
                    }
                    .collect { frame ->
                        Log.d(TAG, "라이브뷰 프레임 수신: ${frame.data.size} bytes")
                        uiStateManager.updateLiveViewState(
                            isActive = true,
                            isLoading = false,
                            frame = frame
                        )
                        uiStateManager.clearError()
                    }
            } catch (e: Exception) {
                Log.e(TAG, "라이브뷰 시작 중 예외 발생", e)
                uiStateManager.updateLiveViewState(
                    isActive = false,
                    isLoading = false,
                    frame = null
                )
                uiStateManager.setError("라이브뷰 시작 실패: ${e.message}")
            }
        }
    }

    fun stopLiveView() {
        liveViewJob?.cancel()
        liveViewJob = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                stopLiveViewUseCase()
                uiStateManager.updateLiveViewState(
                    isActive = false,
                    isLoading = false,
                    frame = null
                )
                Log.d(TAG, "라이브뷰 중지 성공")
            } catch (e: Exception) {
                Log.e(TAG, "라이브뷰 중지 중 예외 발생", e)
                uiStateManager.updateLiveViewState(
                    isActive = false,
                    isLoading = false,
                    frame = null
                )
                uiStateManager.setError("라이브뷰 중지 실패: ${e.message}")
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
                uiStateManager.updateCapturingState(true)
                uiStateManager.setShootingMode(ShootingMode.TIMELAPSE)

                startTimelapseUseCase(settings)
                    .catch { error ->
                        Log.e(TAG, "타임랩스 실행 중 오류", error)
                        uiStateManager.updateCapturingState(false)
                        uiStateManager.setError("타임랩스 시작 실패: ${error.message ?: "알 수 없는 오류"}")
                    }
                    .collect { photo ->
                        Log.d(TAG, "타임랩스 사진 촬영: ${photo.filePath}")
                    }

                uiStateManager.updateCapturingState(false)
            } catch (e: Exception) {
                Log.e(TAG, "타임랩스 중 예외 발생", e)
                uiStateManager.updateCapturingState(false)
                uiStateManager.setError("타임랩스 실패: ${e.message}")
            }
        }
    }

    fun stopTimelapse() {
        timelapseJob?.cancel()
        timelapseJob = null
        uiStateManager.updateCapturingState(false)
    }

    fun updateCameraSetting(key: String, value: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                updateCameraSettingUseCase(key, value)
                    .onSuccess {
                        loadCameraSettingsAsync()
                    }
                    .onFailure { error ->
                        Log.e(TAG, "카메라 설정 업데이트 실패", error)
                        uiStateManager.setError("카메라 설정 업데이트 실패: ${error.message ?: "알 수 없는 오류"}")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "카메라 설정 업데이트 중 예외 발생", e)
                uiStateManager.setError("카메라 설정 업데이트 실패: ${e.message}")
            }
        }
    }

    fun performAutoFocus() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                uiStateManager.updateFocusingState(true)

                performAutoFocusUseCase()
                    .onSuccess {
                        uiStateManager.updateFocusingState(false)
                        uiStateManager.setError("초점 맞춤 완료")

                        delay(1000)
                        if (uiState.value.error == "초점 맞춤 완료") {
                            uiStateManager.clearError()
                        }
                    }
                    .onFailure { error ->
                        Log.e(TAG, "자동초점 실패", error)
                        uiStateManager.updateFocusingState(false)
                        uiStateManager.setError("자동초점 실패: ${error.message ?: "알 수 없는 오류"}")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "자동초점 중 예외 발생", e)
                uiStateManager.updateFocusingState(false)
                uiStateManager.setError("자동초점 실패: ${e.message}")
            }
        }
    }

    // MARK: - Settings Loading

    private fun loadCameraSettingsAsync() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                getCameraSettingsUseCase()
                    .onSuccess { settings ->
                        uiStateManager.updateCameraSettings(settings)
                    }
                    .onFailure { error ->
                        Log.e(TAG, "카메라 설정 로드 실패", error)
                        uiStateManager.setError("카메라 설정 로드 실패: ${error.message ?: "알 수 없는 오류"}")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "카메라 설정 로드 중 예외 발생", e)
                uiStateManager.setError("카메라 설정 로드 실패: ${e.message}")
            }
        }
    }

    private fun loadCameraCapabilitiesAsync() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                getCameraCapabilitiesUseCase()
                    .onSuccess { capabilities ->
                        uiStateManager.updateCameraCapabilities(capabilities)
                    }
                    .onFailure { error ->
                        Log.e(TAG, "카메라 기능 로드 실패", error)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "카메라 기능 로드 중 예외 발생", e)
            }
        }
    }

    // MARK: - Event Listener Management

    fun startEventListener() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                cameraRepository.startCameraEventListener()
                    .onSuccess {
                        Log.d(TAG, "이벤트 리스너 시작 성공")
                    }
                    .onFailure { error ->
                        Log.e(TAG, "이벤트 리스너 시작 실패", error)
                        uiStateManager.setError("이벤트 리스너 시작 실패: ${error.message}")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "이벤트 리스너 시작 중 예외 발생", e)
                uiStateManager.setError("이벤트 리스너 시작 실패: ${e.message}")
            }
        }
    }

    fun stopEventListener(onComplete: (() -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                cameraRepository.stopCameraEventListener()
                    .onSuccess {
                        Log.d(TAG, "이벤트 리스너 중지 성공")
                        onComplete?.invoke()
                    }
                    .onFailure { error ->
                        Log.e(TAG, "이벤트 리스너 중지 실패", error)
                        uiStateManager.setError("이벤트 리스너 중지 실패: ${error.message}")
                        onComplete?.invoke() // 실패해도 콜백 호출
                    }
            } catch (e: Exception) {
                Log.e(TAG, "이벤트 리스너 중지 중 예외 발생", e)
                uiStateManager.setError("이벤트 리스너 중지 실패: ${e.message}")
                onComplete?.invoke() // 예외 발생해도 콜백 호출
            }
        }
    }

    // MARK: - State Management Methods

    fun clearError() = uiStateManager.clearError()
    fun clearPtpTimeout() = uiStateManager.clearPtpTimeout()
    fun clearUsbDisconnection() = uiStateManager.clearUsbDisconnection()
    fun dismissRestartDialog() = uiStateManager.showRestartDialog(false)

    fun setTabSwitchFlag(isReturning: Boolean) {
        Log.d(TAG, "탭 전환 플래그 설정: $isReturning")
        isTabSwitching = isReturning
    }

    fun getAndClearTabSwitchFlag(): Boolean {
        val wasReturning = isTabSwitching
        isTabSwitching = false
        Log.d(TAG, "탭 전환 플래그 확인 및 초기화: $wasReturning -> false")
        return wasReturning
    }

    fun refreshCameraCapabilities() {
        usbCameraManager.refreshCameraCapabilities()
    }

    private fun handleUsbDisconnection() {
        Log.e(TAG, "USB 디바이스 분리 처리")

        // 진행 중인 작업들 즉시 중단
        liveViewJob?.cancel()
        timelapseJob?.cancel()

        uiStateManager.handleUsbDisconnection()
    }

    override fun onCleared() {
        super.onCleared()
        liveViewJob?.cancel()
        timelapseJob?.cancel()
        initializationJob?.cancel()

        // 네이티브 에러 콜백 해제
        CameraNative.setErrorCallback(null)

        try {
            usbCameraManager.cleanup()
        } catch (e: Exception) {
            Log.w(TAG, "USB 매니저 정리 중 오류", e)
        }
    }
}










