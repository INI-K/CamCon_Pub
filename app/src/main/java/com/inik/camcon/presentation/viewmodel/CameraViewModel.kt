package com.inik.camcon.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.CameraNative
import com.inik.camcon.NativeErrorCallback
import com.inik.camcon.data.datasource.usb.UsbCameraManager
import com.inik.camcon.data.repository.managers.PtpTimeoutException
import com.inik.camcon.domain.model.Camera
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

    // PTP 타임아웃 상태 관리
    private val _isPtpTimeout = MutableStateFlow(false)
    val isPtpTimeout: StateFlow<Boolean> = _isPtpTimeout.asStateFlow()

    val cameraFeed: StateFlow<List<Camera>> = getCameraFeedUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var liveViewJob: Job? = null
    private var timelapseJob: Job? = null
    private var initializationJob: Job? = null

    // 탭 전환 감지를 위한 플래그
    private var isTabSwitching = false

    // 자동 연결 중복 실행 방지를 위한 플래그
    private var isAutoConnecting = false

    // 앱 재개 중인지 확인하는 플래그 추가
    private var isAppResuming = false

    // ViewModel 초기화 중복 방지 플래그
    private var isViewModelInitialized = false

    init {
        // 중복 초기화 방지
        if (!isViewModelInitialized) {
            // 앱 재개 상태를 가장 먼저 설정
            isAppResuming = true
            Log.d("CameraViewModel", "ViewModel 초기화 - 앱 재개 상태 설정")

            observeDataSources()
            initializeCameraDatabase()

            cameraRepository.setPhotoPreviewMode(false)
            Log.d("CameraViewModel", "제어 탭 진입 - 사진 미리보기 모드 비활성화")

            // 3초 후 앱 재개 상태 해제
            viewModelScope.launch {
                delay(3000)
                isAppResuming = false
                Log.d("CameraViewModel", "앱 재개 상태 해제")
            }

            // 네이티브 에러 콜백 등록
            registerNativeErrorCallback()

            // USB 분리 콜백 설정
            setupUsbDisconnectionCallback()

            // 초기화 완료 플래그 설정
            isViewModelInitialized = true
        } else {
            Log.d("CameraViewModel", "ViewModel 이미 초기화됨 - 중복 초기화 방지")
        }
    }

    /**
     * 네이티브 에러 콜백 등록
     */
    private fun registerNativeErrorCallback() {
        try {
            CameraNative.setErrorCallback(object : NativeErrorCallback {
                override fun onNativeError(errorCode: Int, errorMessage: String) {
                    Log.e(
                        "CameraViewModel",
                        "네이티브 에러 감지: 코드=$errorCode, 메시지=$errorMessage"
                    )

                    // -10 타임아웃 에러 처리
                    if (errorCode == -10) {
                        viewModelScope.launch(Dispatchers.Main) {
                            _uiState.update {
                                it.copy(
                                    error = "USB 포트 타임아웃 에러 발생 (-10): $errorMessage"
                                )
                            }
                        }
                    }
                    // -52 USB 카메라 감지 실패 에러 처리 (앱 재시작 필요)
                    else if (errorCode == -52) {
                        viewModelScope.launch(Dispatchers.Main) {
                            _uiState.update {
                                it.copy(
                                    showRestartDialog = true,
                                    error = "USB 카메라 감지 실패 (-52): $errorMessage"
                                )
                            }
                        }
                    }
                    // -35 USB 포트 쓰기 실패 에러 처리
                    else if (errorCode == -35) {
                        viewModelScope.launch(Dispatchers.Main) {
                            _uiState.update {
                                it.copy(
                                    error = "USB 포트 쓰기 실패 (-35): $errorMessage\n\nUSB 케이블을 확인하거나 카메라를 재연결하세요."
                                )
                            }
                        }
                    }
                    // 필요시 다른 에러 코드도 처리 가능
                }
            })
            Log.d("CameraViewModel", "네이티브 에러 콜백 등록 완료")
        } catch (e: Exception) {
            Log.e("CameraViewModel", "네이티브 에러 콜백 등록 실패", e)
        }
    }

    /**
     * 네이티브 에러 콜백 해제
     */
    private fun unregisterNativeErrorCallback() {
        try {
            CameraNative.setErrorCallback(null)
            Log.d("CameraViewModel", "네이티브 에러 콜백 해제 완료")
        } catch (e: Exception) {
            Log.e("CameraViewModel", "네이티브 에러 콜백 해제 실패", e)
        }
    }

    /**
     * USB 분리 콜백 설정
     */
    private fun setupUsbDisconnectionCallback() {
        try {
            usbCameraManager.setUsbDisconnectionCallback {
                handleUsbDisconnection()
            }
            Log.d("CameraViewModel", "USB 분리 콜백 설정 완료")
        } catch (e: Exception) {
            Log.e("CameraViewModel", "USB 분리 콜백 설정 실패", e)
        }
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
                        error = if (isConnected) null else it.error
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
                            "USB 권한이 필요합니다" else _uiState.value.error,
                        // 권한이 승인되면 초기화 상태 해제
                        isUsbInitializing = if (hasPermission) false else it.isUsbInitializing,
                        usbInitializationMessage = if (hasPermission) null else it.usbInitializationMessage
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

                // 앱 재개 중이고 연결 해제 이벤트인 경우 무시
                if (isAppResuming && !isConnected) {
                    Log.d("CameraViewModel", "앱 재개 중 연결 해제 이벤트 무시")
                    return@onEach
                }

                _uiState.update {
                    it.copy(
                        isNativeCameraConnected = isConnected,
                        isConnected = isConnected
                    )
                }

                // 중복 실행 방지 로직 강화 - 연결된 경우만 자동 연결 시도
                when {
                    isConnected && !isAutoConnecting && !isAppResuming -> {
                        Log.d("CameraViewModel", "네이티브 카메라 연결됨 - 자동으로 카메라 연결 시작")
                        autoConnectCamera()
                    }

                    isConnected && isAutoConnecting -> {
                        Log.d("CameraViewModel", "자동 연결이 이미 진행 중이므로 중복 실행 방지")
                    }

                    !isConnected -> {
                        Log.d("CameraViewModel", "네이티브 카메라 연결 해제됨")
                        isAutoConnecting = false
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeEventListenerState() {
        cameraRepository.isEventListenerActive()
            .onEach { isActive ->
                Log.d("CameraViewModel", "이벤트 리스너 상태 변경: $isActive")
                _uiState.update { it.copy(isEventListenerActive = isActive) }
            }
            .catch { e ->
                Log.e("CameraViewModel", "이벤트 리스너 상태 관찰 중 오류", e)
            }
            .launchIn(viewModelScope)
    }

    private fun observeCameraInitialization() {
        cameraRepository.isInitializing()
            .onEach { isInitializing ->
                Log.d("CameraViewModel", "카메라 초기화 상태 변경: $isInitializing")
                _uiState.update { it.copy(isCameraInitializing = isInitializing) }
            }
            .catch { e ->
                Log.e("CameraViewModel", "카메라 초기화 상태 관찰 중 오류", e)
            }
            .launchIn(viewModelScope)
    }

    /**
     * 네이티브 카메라가 연결되었을 때 자동으로 CameraRepository에 연결
     */
    private fun autoConnectCamera() {
        // 이미 연결이 진행 중이면 즉시 반환
        if (isAutoConnecting) {
            Log.d("CameraViewModel", "자동 카메라 연결이 이미 진행 중입니다 - 중복 실행 방지")
            return
        }

        isAutoConnecting = true // 플래그를 함수 시작 시점에 설정

        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d("CameraViewModel", "자동 카메라 연결 시작")

                // USB 초기화 시작
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isUsbInitializing = true,
                            usbInitializationMessage = "USB 카메라 초기화 중..."
                        )
                    }
                }

                connectCameraUseCase("auto")
                    .onSuccess {
                        Log.d("CameraViewModel", "자동 카메라 연결 성공 - 이벤트 리스너 활성화됨")
                        withContext(Dispatchers.Main) {
                            _uiState.update {
                                it.copy(
                                    isConnected = true,
                                    error = null,
                                    isUsbInitializing = false,
                                    usbInitializationMessage = null,
                                    isPtpTimeout = false
                                )
                            }
                        }

                        loadCameraCapabilitiesAsync()
                        loadCameraSettingsAsync()
                    }
                    .onFailure { error ->
                        Log.e("CameraViewModel", "자동 카메라 연결 실패", error)
                        withContext(Dispatchers.Main) {
                            val isPtpTimeout = error is PtpTimeoutException
                            if (isPtpTimeout) {
                                _uiState.update {
                                    it.copy(
                                        error = if (isPtpTimeout) "PTP 카메라 통신이 일정 시간 동안 응답하지 않습니다. 연결을 다시 시도해주세요."
                                        else "자동 카메라 연결 실패: ${error.message}",
                                        isUsbInitializing = false,
                                        usbInitializationMessage = null,
                                        isPtpTimeout = isPtpTimeout
                                    )
                                }
                            } else {
                                _uiState.update {
                                    it.copy(
                                        error = "자동 카메라 연결 실패: ${error.message}",
                                        isUsbInitializing = false,
                                        usbInitializationMessage = null
                                    )
                                }
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.e("CameraViewModel", "자동 카메라 연결 중 예외 발생", e)
                withContext(Dispatchers.Main) {
                    val isPtpTimeout = e is PtpTimeoutException
                    if (isPtpTimeout) {
                        _uiState.update {
                            it.copy(
                                error = if (isPtpTimeout) "PTP 카메라 통신이 일정 시간 동안 응답하지 않습니다. 연결을 다시 시도해주세요."
                                else "자동 카메라 연결 실패: ${e.message}",
                                isUsbInitializing = false,
                                usbInitializationMessage = null,
                                isPtpTimeout = isPtpTimeout
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                error = "자동 카메라 연결 실패: ${e.message}",
                                isUsbInitializing = false,
                                usbInitializationMessage = null
                            )
                        }
                    }
                }
            } finally {
                // 성공/실패와 관계없이 플래그를 해제
                isAutoConnecting = false
                Log.d("CameraViewModel", "자동 카메라 연결 완료 - 플래그 해제")
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

    /**
     * USB 초기화 시작
     */
    fun startUsbInitialization(message: String = "USB 카메라 초기화 중...") {
        _uiState.update {
            it.copy(
                isUsbInitializing = true,
                usbInitializationMessage = message
            )
        }
    }

    /**
     * USB 초기화 완료
     */
    fun completeUsbInitialization() {
        _uiState.update {
            it.copy(
                isUsbInitializing = false,
                usbInitializationMessage = null
            )
        }
    }

    /**
     * USB 초기화 상태 업데이트
     */
    fun updateUsbInitializationMessage(message: String) {
        _uiState.update {
            it.copy(usbInitializationMessage = message)
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
                            _uiState.update { it.copy(isConnected = true, isPtpTimeout = false) }
                        }

                        loadCameraCapabilitiesAsync()
                        loadCameraSettingsAsync()
                    }
                    .onFailure { error ->
                        Log.e("CameraViewModel", "카메라 연결 실패", error)
                        withContext(Dispatchers.Main) {
                            val isPtpTimeout = error is PtpTimeoutException
                            if (isPtpTimeout) {
                                _uiState.update {
                                    it.copy(
                                        isConnected = false,
                                        error = if (isPtpTimeout) "PTP 카메라 통신이 일정 시간 동안 응답하지 않습니다. 연결을 다시 시도해주세요."
                                        else error.message,
                                        isPtpTimeout = isPtpTimeout
                                    )
                                }
                            } else {
                                _uiState.update {
                                    it.copy(
                                        isConnected = false,
                                        error = error.message
                                    )
                                }
                            }
                        }
                    }

                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                Log.e("CameraViewModel", "카메라 연결 중 예외 발생", e)
                withContext(Dispatchers.Main) {
                    val isPtpTimeout = e is PtpTimeoutException
                    if (isPtpTimeout) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isConnected = false,
                                error = if (isPtpTimeout) "PTP 카메라 통신이 일정 시간 동안 응답하지 않습니다. 연결을 다시 시도해주세요."
                                else "카메라 연결 실패: ${e.message}",
                                isPtpTimeout = isPtpTimeout
                            )
                        }
                    } else {
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
    }

    fun refreshUsbDevices() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val devices = refreshUsbDevicesUseCase()
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            usbDeviceCount = devices.size,
                            error = if (devices.isEmpty()) "USB 카메라가 감지되지 않음" else null
                        )
                    }
                }

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
                // USB 초기화 시작
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isUsbInitializing = true,
                            usbInitializationMessage = "USB 권한 요청 중..."
                        )
                    }
                }

                val devices = refreshUsbDevicesUseCase()
                if (devices.isNotEmpty()) {
                    val device = devices.first()
                    withContext(Dispatchers.Main) {
                        requestUsbPermissionUseCase(device)
                        _uiState.update {
                            it.copy(
                                error = "USB 권한을 요청했습니다. 대화상자에서 승인해주세요.",
                                usbInitializationMessage = "USB 권한 대기 중..."
                            )
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(
                                error = "USB 카메라가 감지되지 않았습니다",
                                isUsbInitializing = false,
                                usbInitializationMessage = null
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("CameraViewModel", "USB 권한 요청 실패", e)
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            error = "USB 권한 요청 실패: ${e.message}",
                            isUsbInitializing = false,
                            usbInitializationMessage = null
                        )
                    }
                }
            }
        }
    }

    fun capturePhoto() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d("CameraViewModel", "=== 사진 촬영 요청 시작 ===")

                _uiState.update { it.copy(isCapturing = true, error = null) }

                capturePhotoUseCase(_uiState.value.shootingMode)
                    .onSuccess { photo ->
                        Log.d("CameraViewModel", "✓ 사진 촬영 성공: ${photo.filePath}")
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
        if (_uiState.value.isLiveViewActive || liveViewJob?.isActive == true) {
            Log.d("CameraViewModel", "라이브뷰가 이미 활성화되어 있거나 시작 중입니다")
            return
        }

        Log.d("CameraViewModel", "=== 라이브뷰 시작 요청 ===")
        Log.d("CameraViewModel", "카메라 연결 상태: ${_uiState.value.isConnected}")
        Log.d("CameraViewModel", "네이티브 카메라 연결: ${_uiState.value.isNativeCameraConnected}")
        Log.d("CameraViewModel", "카메라 기능 정보: ${_uiState.value.cameraCapabilities}")

        liveViewJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val capabilities = _uiState.value.cameraCapabilities
                Log.d("CameraViewModel", "카메라 라이브뷰 지원 여부: ${capabilities?.canLiveView}")

                if (capabilities != null && !capabilities.canLiveView) {
                    Log.w("CameraViewModel", "카메라가 라이브뷰를 지원하지 않습니다: ${capabilities.model}")
                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(error = "이 카메라는 라이브뷰를 지원하지 않습니다.")
                        }
                    }
                    return@launch
                }

                // 연결 상태 재확인
                if (!_uiState.value.isConnected) {
                    Log.e("CameraViewModel", "카메라가 연결되지 않은 상태에서 라이브뷰 시작 불가")
                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(error = "카메라가 연결되지 않았습니다. 먼저 카메라를 연결해주세요.")
                        }
                    }
                    return@launch
                }

                Log.d("CameraViewModel", "라이브뷰 시작 - 로딩 상태 설정")
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isLiveViewLoading = true, error = null) }
                }

                Log.d("CameraViewModel", "StartLiveViewUseCase 호출")
                startLiveViewUseCase()
                    .catch { error ->
                        Log.e("CameraViewModel", "라이브뷰 Flow 오류", error)
                        withContext(Dispatchers.Main) {
                            _uiState.update {
                                it.copy(
                                    isLiveViewActive = false,
                                    isLiveViewLoading = false,
                                    error = "라이브뷰 시작 실패: ${error.message}"
                                )
                            }
                        }
                    }
                    .collect { frame ->
                        Log.d("CameraViewModel", "라이브뷰 프레임 수신: 크기=${frame.data.size} bytes")
                        withContext(Dispatchers.Main) {
                            _uiState.update {
                                it.copy(
                                    isLiveViewActive = true,
                                    liveViewFrame = frame,
                                    isLiveViewLoading = false,
                                    error = null
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
                Log.d("CameraViewModel", "라이브뷰 중지 성공 - 카메라 연결은 유지됨")
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
                        withContext(Dispatchers.Main) {
                            _uiState.update { it.copy(isFocusing = false) }
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
        Log.d("CameraViewModel", "disconnectCamera 호출됨")
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
                            isFocusing = false,
                            isPtpTimeout = false
                        )
                    }
                }
                Log.i("CameraViewModel", "카메라 연결 해제 성공")
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
                        Log.e("CameraViewModel", "카메라 기능 로드 실패", error)
                    }
            } catch (e: Exception) {
                Log.e("CameraViewModel", "카메라 기능 로드 중 예외 발생", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        liveViewJob?.cancel()
        timelapseJob?.cancel()
        initializationJob?.cancel()

        // onCleared에서 네이티브 에러 콜백을 항상 해제합니다.
        CameraNative.setErrorCallback(null)

        try {
            usbCameraManager.cleanup()
        } catch (e: Exception) {
            Log.w("CameraViewModel", "USB 매니저 정리 중 오류", e)
        }
    }

    /**
     * 이벤트 리스너 시작
     */
    fun startEventListener() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d("CameraViewModel", "이벤트 리스너 시작 요청")
                cameraRepository.startCameraEventListener()
                    .onSuccess {
                        Log.d("CameraViewModel", "이벤트 리스너 시작 성공")
                    }
                    .onFailure { error ->
                        Log.e("CameraViewModel", "이벤트 리스너 시작 실패", error)
                        withContext(Dispatchers.Main) {
                            _uiState.update {
                                it.copy(error = "이벤트 리스너 시작 실패: ${error.message}")
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.e("CameraViewModel", "이벤트 리스너 시작 중 예외 발생", e)
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(error = "이벤트 리스너 시작 실패: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * 이벤트 리스너 중지
     */
    fun stopEventListener(onComplete: (() -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d("CameraViewModel", "이벤트 리스너 중지 요청")
                cameraRepository.stopCameraEventListener()
                    .onSuccess {
                        Log.d("CameraViewModel", "이벤트 리스너 중지 성공")
                        onComplete?.invoke()
                    }
                    .onFailure { error ->
                        Log.e("CameraViewModel", "이벤트 리스너 중지 실패", error)
                        withContext(Dispatchers.Main) {
                            _uiState.update {
                                it.copy(error = "이벤트 리스너 중지 실패: ${error.message}")
                            }
                        }
                        // 실패해도 콜백 호출
                        onComplete?.invoke()
                    }
            } catch (e: Exception) {
                Log.e("CameraViewModel", "이벤트 리스너 중지 중 예외 발생", e)
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(error = "이벤트 리스너 중지 실패: ${e.message}")
                    }
                }
                // 예외 발생해도 콜백 호출
                onComplete?.invoke()
            }
        }
    }

    /**
     * 탭 전환 플래그 설정
     */
    fun setTabSwitchFlag(isReturning: Boolean) {
        Log.d("CameraViewModel", "탭 전환 플래그 설정: $isReturning")
        isTabSwitching = isReturning
    }

    /**
     * 탭 전환 플래그 확인 후 초기화
     */
    fun getAndClearTabSwitchFlag(): Boolean {
        val wasReturning = isTabSwitching
        isTabSwitching = false
        Log.d("CameraViewModel", "탭 전환 플래그 확인 및 초기화: $wasReturning -> false")
        return wasReturning
    }

    /**
     * PTP 타임아웃 상태 초기화
     */
    fun clearPtpTimeout() {
        _uiState.update { it.copy(isPtpTimeout = false) }
        Log.d("CameraViewModel", "PTP 타임아웃 상태 초기화")
    }

    /**
     * USB 분리 상태 처리
     */
    fun handleUsbDisconnection() {
        Log.e("CameraViewModel", "USB 디바이스 분리 처리")
        _uiState.update {
            it.copy(
                isUsbDisconnected = true,
                isConnected = false,
                isNativeCameraConnected = false,
                isLiveViewActive = false,
                liveViewFrame = null,
                error = "USB 디바이스가 분리되었습니다. 카메라를 다시 연결해주세요."
            )
        }

        // 진행 중인 작업들 중단
        liveViewJob?.cancel()
        timelapseJob?.cancel()
    }

    /**
     * USB 분리 상태 초기화
     */
    fun clearUsbDisconnection() {
        _uiState.update { it.copy(isUsbDisconnected = false) }
        Log.d("CameraViewModel", "USB 분리 상태 초기화")
    }

    /**
     * 앱 재시작을 위한 액티비티 재시작 요청
     */
    fun restartApp() {
        Log.d("CameraViewModel", "앱 재시작 요청")
        // Activity에서 처리되어야 함
    }

    /**
     * 앱 재시작 다이얼로그 닫기
     */
    fun dismissRestartDialog() {
        _uiState.update {
            it.copy(showRestartDialog = false)
        }
    }

    /**
     * 데이터 소스 관찰
     */
    private fun observeDataSources() {
        observeCameraConnection()
        observeCapturedPhotos()
        observeUsbDevices()
        observeCameraCapabilities()
        observeEventListenerState()
        observeCameraInitialization()
    }
}
