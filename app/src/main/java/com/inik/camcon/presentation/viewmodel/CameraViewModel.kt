package com.inik.camcon.presentation.viewmodel

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.CameraNative
import com.inik.camcon.data.datasource.local.AppPreferencesDataSource
import com.inik.camcon.domain.manager.CameraSettingsManager
import com.inik.camcon.domain.manager.ErrorHandlingManager
import com.inik.camcon.domain.model.Camera
import com.inik.camcon.domain.model.ShootingMode
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.repository.CameraRepository
import com.inik.camcon.domain.usecase.GetSubscriptionUseCase
import com.inik.camcon.presentation.viewmodel.state.CameraUiStateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 카메라 기능을 위한 ViewModel - MVVM 패턴 준수
 * 단일책임: UI 상태 관리 및 매니저들 간의 조정만 담당
 * View Layer와 Domain Layer 사이의 중재자 역할
 */
@HiltViewModel
class CameraViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cameraRepository: CameraRepository,
    private val getSubscriptionUseCase: GetSubscriptionUseCase,
    private val uiStateManager: CameraUiStateManager,

    // 매니저 의존성 주입 (단일책임원칙 적용)
    private val connectionManager: CameraConnectionManager,
    private val operationsManager: CameraOperationsManager,
    private val settingsManager: CameraSettingsManager,
    private val errorHandlingManager: ErrorHandlingManager,
    private val appPreferencesDataSource: AppPreferencesDataSource,

    // 신규 매니저 의존성 주입
    private val advancedCaptureManager: CameraAdvancedCaptureManager,
    private val focusManager: CameraFocusManager,
    private val fileManager: CameraFileManager,
    private val streamingManager: CameraStreamingManager,
    private val diagnosticsManager: CameraDiagnosticsManager
) : ViewModel() {

    companion object {
        private const val TAG = "카메라뷰모델"
    }

    // UI 상태는 StateManager에 위임
    val uiState: StateFlow<CameraUiState> = uiStateManager.uiState

    // 카메라 피드 (Repository에서 직접 가져오기 - 단순 위임 UseCase 제거)
    val cameraFeed: StateFlow<List<Camera>> = cameraRepository.getCameraFeed()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // 카메라 설정 상태 (SettingsManager에서 관리)
    val cameraSettings = settingsManager.cameraSettings
    val cameraCapabilities = settingsManager.cameraCapabilities
    val isLoadingSettings = settingsManager.isLoadingSettings
    val isUpdatingSettings = settingsManager.isUpdatingSettings

    // 연결 상태 (ConnectionManager에서 관리)
    val isAutoConnecting = connectionManager.isAutoConnecting

    // PTPIP 연결 상태 (사진 미리보기 차단용)
    val isPtpipConnected: StateFlow<Boolean> = cameraRepository.isPtpipConnected()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    // RAW 파일 제한 스타일링을 위한 현재 Activity 참조
    private var currentActivity: Activity? = null

    init {
        initializeViewModel()
        loadSubscriptionTierAtStartup()
        observeRawDownloadSetting()
    }

    /**
     * ViewModel 초기화 - 매니저들 설정
     */
    private fun initializeViewModel() {
        Log.d(TAG, "ViewModel 초기화 시작")

        // 에러 처리 시스템 초기화
        errorHandlingManager.initialize()

        // 옵저버들 설정
        setupObservers()

        // 카메라 리포지토리 초기화
        initializeCameraRepository()

        // RAW 파일 제한 콜백 등록
        registerRawLimitCallback()

        Log.d(TAG, "ViewModel 초기화 완료")
    }

    /**
     * 앱 시작 시 구독 티어 미리 로드
     */
    private fun loadSubscriptionTierAtStartup() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "🔑 앱 시작 시 구독 티어 로드 시작")

                // 로컬 캐시된 구독 티어를 먼저 적용 (Firebase 오프라인 대비)
                val cachedTier = appPreferencesDataSource.subscriptionTierEnum.first()
                val cachedTierInt = subscriptionTierToInt(cachedTier)
                CameraNative.setSubscriptionTier(cachedTierInt)
                Log.d(TAG, "✅ 앱 시작 시 구독 티어 설정 완료: $cachedTier (네이티브: $cachedTierInt)")

                // RAW 파일 다운로드 설정도 함께 로드
                val isRawDownloadEnabled = appPreferencesDataSource.isRawFileDownloadEnabled.first()
                CameraNative.setRawFileDownloadEnabled(isRawDownloadEnabled)
                Log.d(TAG, "✅ 앱 시작 시 RAW 파일 다운로드 설정 완료: $isRawDownloadEnabled")

            } catch (e: Exception) {
                Log.e(TAG, "❌ 앱 시작 시 구독 티어 로드 실패", e)
                // 실패 시 기본값(FREE) 설정
                CameraNative.setSubscriptionTier(0)
                CameraNative.setRawFileDownloadEnabled(true)
            }
        }
    }

    private fun subscriptionTierToInt(tier: SubscriptionTier): Int = when (tier) {
        SubscriptionTier.FREE -> 0
        SubscriptionTier.BASIC -> 1
        SubscriptionTier.PRO -> 2
        SubscriptionTier.REFERRER -> 2
        SubscriptionTier.ADMIN -> 2
    }

    /**
     * RAW 파일 다운로드 설정 관찰
     */
    private fun observeRawDownloadSetting() {
        appPreferencesDataSource.isRawFileDownloadEnabled
            .onEach { enabled ->
                CameraNative.setRawFileDownloadEnabled(enabled)
                Log.d(TAG, "🎯 RAW 파일 다운로드 설정 업데이트: $enabled")
            }
            .launchIn(viewModelScope)
    }

    /**
     * 옵저버들 설정 - 각 매니저의 상태를 UI에 반영
     */
    private fun setupObservers() {
        // 카메라 연결 상태 관찰
        observeCameraConnection()

        // 촬영된 사진 관찰
        observeCapturedPhotos()

        // USB 디바이스 상태 관찰 (ConnectionManager에 위임)
        connectionManager.observeUsbDevices(viewModelScope, uiStateManager)

        // 에러 이벤트 관찰
        observeErrorEvents()

        // 기타 상태들 관찰
        observeOtherStates()

        // PTPIP 연결 상태 관찰
        observePtpipConnection()
    }

    /**
     * PTPIP 연결 상태 관찰
     */
    private fun observePtpipConnection() {
        isPtpipConnected
            .onEach { isConnected ->
                uiStateManager.updatePtpipConnectionState(isConnected)
                if (isConnected) {
                    // PTPIP 연결 상태에 따라 사진 미리보기 탭을 블록합니다.
                    uiStateManager.blockPreviewTab(true)
                } else {
                    uiStateManager.blockPreviewTab(false)
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * 카메라 연결 상태 관찰
     */
    private fun observeCameraConnection() {
        cameraRepository.isCameraConnected()
            .onEach { isConnected ->
                uiStateManager.updateConnectionState(isConnected)
                if (isConnected) {
                    // 연결 시 설정 로딩을 생략하여 연결 속도 향상 (15초 단축)
                    // 설정은 사용자가 설정 탭을 열 때 로드됨
                    // settingsManager.loadCameraSettings()
                    // settingsManager.loadCameraCapabilities()

                    // Firebase에서 최신 구독 티어를 가져와 업데이트
                    // AP 모드에서는 Firebase 오프라인으로 실패할 수 있으며,
                    // 그 경우 loadSubscriptionTierAtStartup에서 설정한 로컬 캐시 값이 유지됨
                    viewModelScope.launch {
                        try {
                            getSubscriptionUseCase.getSubscriptionTier()
                                .collect { tier ->
                                    val tierInt = subscriptionTierToInt(tier)
                                    CameraNative.setSubscriptionTier(tierInt)
                                    Log.d(TAG, "🔄 구독 티어 업데이트: $tier (네이티브: $tierInt)")
                                }
                        } catch (e: Exception) {
                            Log.e(TAG, "구독 티어 업데이트 실패 (로컬 캐시 값 유지)", e)
                        }
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * 촬영된 사진 관찰
     */
    private fun observeCapturedPhotos() {
        cameraRepository.getCapturedPhotos()
            .onEach { photos ->
                uiStateManager.updateCapturedPhotos(photos)
            }
            .launchIn(viewModelScope)
    }

    /**
     * 에러 이벤트 관찰
     */
    private fun observeErrorEvents() {
        // 일반 에러 이벤트
        errorHandlingManager.errorEvent
            .onEach { errorEvent ->
                uiStateManager.setError(errorEvent.message)
                Log.e(TAG, "에러 이벤트 수신: ${errorEvent.type} - ${errorEvent.message}")
            }
            .launchIn(viewModelScope)

        // 네이티브 에러 이벤트  
        errorHandlingManager.nativeErrorEvent
            .onEach { nativeErrorEvent ->
                uiStateManager.setError(nativeErrorEvent.userFriendlyMessage)

                // 특별한 액션이 필요한 경우 처리
                when (nativeErrorEvent.actionRequired) {
                    com.inik.camcon.domain.manager.ErrorAction.RESTART_APP -> {
                        uiStateManager.showRestartDialog(true)
                    }
                    com.inik.camcon.domain.manager.ErrorAction.RECONNECT_CAMERA -> {
                        // 자동 재연결 시도
                        disconnectCamera()
                    }
                    else -> {
                        // 기본 에러 표시
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * 기타 상태들 관찰
     */
    private fun observeOtherStates() {
        // 이벤트 리스너 상태
        cameraRepository.isEventListenerActive()
            .onEach { isActive ->
                uiStateManager.updateEventListenerState(isActive)
            }
            .launchIn(viewModelScope)

        // 카메라 초기화 상태 - 시작만 감지하고 해제는 onFlushComplete에서 처리
        cameraRepository.isInitializing()
            .onEach { isInitializing ->
                // 초기화 시작만 UI에 반영하고, 해제는 onFlushComplete 콜백에서 처리
                if (isInitializing) {
                    uiStateManager.updateCameraInitialization(true)
                }
                // 카메라 초기화 완료(false)는 여기서 처리하지 않음 - onFlushComplete에서만 처리
            }
            .launchIn(viewModelScope)
    }

    /**
     * 카메라 리포지토리 초기화
     */
    private fun initializeCameraRepository() {
        viewModelScope.launch {
            try {
                uiStateManager.updateInitializingState(true)
                cameraRepository.setPhotoPreviewMode(false)
                uiStateManager.updateInitializingState(false)
            } catch (e: Exception) {
                Log.e(TAG, "카메라 리포지토리 초기화 실패", e)
                uiStateManager.updateInitializingState(false)
                errorHandlingManager.emitError(
                    com.inik.camcon.domain.manager.ErrorType.INITIALIZATION,
                    "카메라 초기화 실패: ${e.message}",
                    e,
                    com.inik.camcon.domain.manager.ErrorSeverity.HIGH
                )
            }
        }
    }

    /**
     * RAW 파일 제한 콜백 등록
     */
    private fun registerRawLimitCallback() {
        try {
            cameraRepository.setRawFileRestrictionCallback { fileName, restrictionMessage ->
                Log.d(TAG, "RAW 파일 제한: $fileName - $restrictionMessage")
                uiStateManager.setRawFileRestriction(fileName, restrictionMessage)
            }
            Log.d(TAG, "RAW 파일 제한 콜백 등록 완료")
        } catch (e: Exception) {
            Log.e(TAG, "RAW 파일 제한 콜백 등록 실패", e)
        }
    }

    // MARK: - Public Methods (UI에서 호출)

    /**
     * 현재 Activity 설정
     */
    fun setActivity(activity: Activity?) {
        currentActivity = activity
    }

    /**
     * 카메라 연결 (ConnectionManager에 위임)
     */
    fun connectCamera(cameraId: String) {
        connectionManager.connectCamera(cameraId, uiStateManager)
    }

    /**
     * 카메라 연결 해제 (ConnectionManager에 위임)
     */
    fun disconnectCamera() {
        // 진행 중인 작업들 먼저 중단
        operationsManager.cleanup()

        // 연결 해제
        connectionManager.disconnectCamera(uiStateManager)
    }

    /**
     * USB 디바이스 새로고침 (ConnectionManager에 위임)
     */
    fun refreshUsbDevices() {
        connectionManager.refreshUsbDevices(uiStateManager)
    }

    /**
     * USB 권한 요청 (ConnectionManager에 위임)
     */
    fun requestUsbPermission() {
        connectionManager.requestUsbPermission(uiStateManager)
    }

    /**
     * 사진 촬영 (OperationsManager에 위임)
     */
    fun capturePhoto() {
        operationsManager.capturePhoto(uiState.value.shootingMode, uiStateManager)
    }

    /**
     * 라이브뷰 시작 (OperationsManager에 위임)
     */
    fun startLiveView() {
        if (operationsManager.isLiveViewActive()) {
            Log.d(TAG, "라이브뷰가 이미 활성화되어 있음")
            return
        }

        operationsManager.startLiveView(
            uiState.value.isConnected,
            cameraCapabilities.value,
            uiStateManager
        )
    }

    /**
     * 라이브뷰 중지 (OperationsManager에 위임)
     */
    fun stopLiveView() {
        operationsManager.stopLiveView(uiStateManager)
    }

    /**
     * 타임랩스 시작 (OperationsManager에 위임)
     */
    fun startTimelapse(interval: Int, totalShots: Int) {
        if (operationsManager.isTimelapseActive()) return

        operationsManager.startTimelapse(interval, totalShots, uiStateManager)
    }

    /**
     * 타임랩스 중지 (OperationsManager에 위임)
     */
    fun stopTimelapse() {
        operationsManager.stopTimelapse(uiStateManager)
    }

    /**
     * 자동초점 (OperationsManager에 위임)
     */
    fun performAutoFocus() {
        operationsManager.performAutoFocus(uiStateManager)
    }

    /**
     * 촬영 모드 설정
     */
    fun setShootingMode(mode: ShootingMode) {
        uiStateManager.setShootingMode(mode)
    }

    /**
     * 카메라 설정 업데이트 (SettingsManager에 위임)
     */
    fun updateCameraSetting(key: String, value: String) {
        viewModelScope.launch {
            settingsManager.updateCameraSetting(key, value)
        }
    }

    /**
     * 카메라 파일 목록 상태 확인 및 로드
     */
    fun checkCameraStatusAndLoadFiles(onFilesLoaded: (String) -> Unit) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "카메라 파일 목록 로드 전 상태 확인")
                uiStateManager.updateLoadingState(true)
                uiStateManager.clearError()

                // 연결 상태 확인
                if (!uiState.value.isConnected || !CameraNative.isCameraConnected()) {
                    Log.w(TAG, "카메라가 연결되지 않았거나 전원이 꺼져 있음")
                    uiStateManager.updateLoadingState(false)
                    errorHandlingManager.emitError(
                        com.inik.camcon.domain.manager.ErrorType.CONNECTION,
                        "카메라 연결을 확인해주세요.\n카메라가 켜져 있고 올바르게 연결되어 있는지 확인하십시오.",
                        null,
                        com.inik.camcon.domain.manager.ErrorSeverity.MEDIUM
                    )
                    return@launch
                }

                // 초기화 상태 확인
                if (!CameraNative.isCameraInitialized()) {
                    Log.w(TAG, "네이티브 카메라가 초기화되지 않음")
                    uiStateManager.updateLoadingState(false)
                    errorHandlingManager.emitError(
                        com.inik.camcon.domain.manager.ErrorType.INITIALIZATION,
                        "카메라 초기화가 필요합니다.\n카메라를 다시 연결해주세요.",
                        null,
                        com.inik.camcon.domain.manager.ErrorSeverity.MEDIUM
                    )
                    return@launch
                }

                // 파일 목록 가져오기
                Log.d(TAG, "카메라 파일 목록 로드 시작")
                val fileList = CameraNative.getCameraFileList()

                if (fileList.isEmpty() || fileList.contains("ERROR") || fileList.contains("TIMEOUT")) {
                    Log.w(TAG, "카메라 파일 목록 로드 실패: $fileList")
                    uiStateManager.updateLoadingState(false)
                    errorHandlingManager.emitError(
                        com.inik.camcon.domain.manager.ErrorType.FILE_SYSTEM,
                        "카메라에서 파일 목록을 불러올 수 없습니다.\n카메라 상태를 확인하고 다시 시도해주세요.",
                        null,
                        com.inik.camcon.domain.manager.ErrorSeverity.MEDIUM
                    )
                    return@launch
                }

                Log.d(TAG, "카메라 파일 목록 로드 성공")
                uiStateManager.updateLoadingState(false)
                uiStateManager.clearError()
                onFilesLoaded(fileList)

            } catch (e: Exception) {
                Log.e(TAG, "카메라 파일 목록 확인 중 예외 발생", e)
                uiStateManager.updateLoadingState(false)
                errorHandlingManager.emitError(
                    com.inik.camcon.domain.manager.ErrorType.FILE_SYSTEM,
                    "카메라 상태 확인 중 오류가 발생했습니다.\n카메라 연결을 확인해주세요.",
                    e,
                    com.inik.camcon.domain.manager.ErrorSeverity.MEDIUM
                )
            }
        }
    }

    /**
     * 카메라 전원 상태 확인
     */
    fun checkCameraPowerStatus(): Boolean {
        return try {
            val isConnected = CameraNative.isCameraConnected()
            val isInitialized = CameraNative.isCameraInitialized()

            Log.d(TAG, "카메라 상태 확인 - 연결: $isConnected, 초기화: $isInitialized")

            if (!isConnected || !isInitialized) {
                errorHandlingManager.emitError(
                    com.inik.camcon.domain.manager.ErrorType.CONNECTION,
                    "카메라 전원을 확인해주세요.\n카메라가 켜져 있고 정상적으로 연결되어 있는지 확인하십시오.",
                    null,
                    com.inik.camcon.domain.manager.ErrorSeverity.MEDIUM
                )
                return false
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "카메라 전원 상태 확인 실패", e)
            errorHandlingManager.emitError(
                com.inik.camcon.domain.manager.ErrorType.CONNECTION,
                "카메라 상태를 확인할 수 없습니다.\n카메라 연결을 다시 확인해주세요.",
                e,
                com.inik.camcon.domain.manager.ErrorSeverity.MEDIUM
            )
            false
        }
    }

    /**
     * 이벤트 리스너 시작
     */
    fun startEventListener() {
        viewModelScope.launch {
            try {
                cameraRepository.startCameraEventListener()
                    .onSuccess {
                        Log.d(TAG, "이벤트 리스너 시작 성공")
                    }
                    .onFailure { error ->
                        Log.e(TAG, "이벤트 리스너 시작 실패", error)
                        errorHandlingManager.emitError(
                            com.inik.camcon.domain.manager.ErrorType.OPERATION,
                            "이벤트 리스너 시작 실패: ${error.message}",
                            error,
                            com.inik.camcon.domain.manager.ErrorSeverity.MEDIUM
                        )
                    }
            } catch (e: Exception) {
                Log.e(TAG, "이벤트 리스너 시작 중 예외 발생", e)
                errorHandlingManager.emitError(
                    com.inik.camcon.domain.manager.ErrorType.OPERATION,
                    "이벤트 리스너 시작 실패: ${e.message}",
                    e,
                    com.inik.camcon.domain.manager.ErrorSeverity.MEDIUM
                )
            }
        }
    }

    /**
     * 이벤트 리스너 중지
     */
    fun stopEventListener(onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                cameraRepository.stopCameraEventListener()
                    .onSuccess {
                        Log.d(TAG, "이벤트 리스너 중지 성공")
                        onComplete?.invoke()
                    }
                    .onFailure { error ->
                        Log.e(TAG, "이벤트 리스너 중지 실패", error)
                        errorHandlingManager.emitError(
                            com.inik.camcon.domain.manager.ErrorType.OPERATION,
                            "이벤트 리스너 중지 실패: ${error.message}",
                            error,
                            com.inik.camcon.domain.manager.ErrorSeverity.MEDIUM
                        )
                        onComplete?.invoke()
                    }
            } catch (e: Exception) {
                Log.e(TAG, "이벤트 리스너 중지 중 예외 발생", e)
                errorHandlingManager.emitError(
                    com.inik.camcon.domain.manager.ErrorType.OPERATION,
                    "이벤트 리스너 중지 실패: ${e.message}",
                    e,
                    com.inik.camcon.domain.manager.ErrorSeverity.MEDIUM
                )
                onComplete?.invoke()
            }
        }
    }

    // MARK: - State Management Methods (UiStateManager에 위임)

    fun clearError() = uiStateManager.clearError()
    fun clearPtpTimeout() = uiStateManager.clearPtpTimeout()
    fun clearUsbDisconnection() = uiStateManager.clearUsbDisconnection()
    fun clearRawFileRestriction() = uiStateManager.clearRawFileRestriction()
    fun dismissRestartDialog() = uiStateManager.showRestartDialog(false)
    fun dismissCameraStatusCheckDialog() = uiStateManager.showCameraStatusCheckDialog(false)

    /**
     * 카메라 기능 제한 안내 닫기
     */
    fun clearCameraFunctionLimitation() {
        uiStateManager.clearCameraFunctionLimitation()
        Log.d(TAG, "카메라 기능 제한 안내 닫기")
    }

    /**
     * Nikon STA 경고 닫기
     */
    fun dismissNikonStaWarning() {
        uiStateManager.dismissNikonStaWarning()
        Log.d(TAG, "Nikon STA 경고 닫기")
    }

    fun setTabSwitchFlag(isReturning: Boolean) {
        Log.d(TAG, "탭 전환 플래그 설정: $isReturning")
        // UiStateManager에서 관리하도록 변경 가능
    }

    fun getAndClearTabSwitchFlag(): Boolean {
        Log.d(TAG, "탭 전환 플래그 확인 및 초기화")
        // UiStateManager에서 관리하도록 변경 가능
        return false
    }

    fun refreshCameraCapabilities() {
        viewModelScope.launch {
            settingsManager.loadCameraCapabilities()
        }
    }

    /**
     * 카메라 설정 수동 로드 (설정 탭을 열 때 호출)
     */
    fun loadCameraSettingsManually() {
        viewModelScope.launch {
            settingsManager.loadCameraSettings()
            settingsManager.loadCameraCapabilities()
        }
    }

    // === Advanced Capture ===
    fun startBulbCapture() = advancedCaptureManager.startBulbCapture()
    fun endBulbCapture() = advancedCaptureManager.endBulbCapture()
    fun bulbCaptureWithDuration(seconds: Int) = advancedCaptureManager.bulbCaptureWithDuration(seconds)
    fun startVideoRecording() = advancedCaptureManager.startVideoRecording()
    fun stopVideoRecording() = advancedCaptureManager.stopVideoRecording()
    fun startIntervalCapture(intervalSeconds: Int, totalFrames: Int) = advancedCaptureManager.startIntervalCapture(intervalSeconds, totalFrames)
    fun stopIntervalCapture() = advancedCaptureManager.stopIntervalCapture()
    fun captureDualMode(keepRawOnCard: Boolean, downloadJpeg: Boolean) = advancedCaptureManager.captureDualMode(keepRawOnCard, downloadJpeg)
    fun triggerCapture() = advancedCaptureManager.triggerCapture()
    fun captureAudio() = advancedCaptureManager.captureAudio()
    val bulbState get() = advancedCaptureManager.bulbState
    val videoState get() = advancedCaptureManager.videoState
    val intervalStatus get() = advancedCaptureManager.intervalStatus

    // === Focus ===
    fun setAFMode(mode: String) = focusManager.setAFMode(mode)
    fun refreshAFMode() = focusManager.refreshAFMode()
    fun setAFArea(x: Int, y: Int, width: Int, height: Int) = focusManager.setAFArea(x, y, width, height)
    fun driveManualFocus(steps: Int) = focusManager.driveManualFocus(steps)
    val focusConfig get() = focusManager.focusConfig
    val isFocusDriving get() = focusManager.isFocusDriving

    // === File ===
    fun refreshStorageInfo() = fileManager.refreshStorageInfo()
    fun downloadAllRawFiles(folder: String) = fileManager.downloadAllRawFiles(folder)
    fun uploadFileToCamera(folder: String, filename: String, data: ByteArray) = fileManager.uploadFileToCamera(folder, filename, data)
    fun deleteAllFilesInFolder(folder: String) = fileManager.deleteAllFilesInFolder(folder)
    fun createFolder(parentFolder: String, folderName: String) = fileManager.createFolder(parentFolder, folderName)
    fun removeFolder(parentFolder: String, folderName: String) = fileManager.removeFolder(parentFolder, folderName)
    fun initializeCache() = fileManager.initializeCache()
    fun invalidateFileCache() = fileManager.invalidateFileCache()
    val storageInfo get() = fileManager.storageInfo

    // === Streaming ===
    fun startStreaming() = streamingManager.startStreaming()
    fun stopStreaming() = streamingManager.stopStreaming()
    fun setStreamingParameters(width: Int, height: Int, fps: Int) = streamingManager.setStreamingParameters(width, height, fps)
    val isStreaming get() = streamingManager.isStreaming
    val currentStreamFrame get() = streamingManager.currentFrame

    // === Diagnostics ===
    fun runFullDiagnostics() = diagnosticsManager.runFullDiagnostics()
    fun clearErrorHistory() = diagnosticsManager.clearErrorHistory()
    fun refreshMemoryPoolStatus() = diagnosticsManager.refreshMemoryPoolStatus()
    fun clearCameraFilePool() = diagnosticsManager.clearCameraFilePool()
    val diagnosticsReport get() = diagnosticsManager.diagnosticsReport
    val memoryPoolStatus get() = diagnosticsManager.memoryPoolStatus

    override fun onCleared() {
        super.onCleared()
        currentActivity = null

        // 매니저들 정리
        operationsManager.cleanup()
        connectionManager.cleanup()
        settingsManager.cleanup()
        errorHandlingManager.cleanup()

        // RAW 제한 콜백 해제
        cameraRepository.setRawFileRestrictionCallback(null)

        Log.d(TAG, "ViewModel 정리 완료")
    }
}
