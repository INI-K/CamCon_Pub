package com.inik.camcon.presentation.viewmodel.state

import android.util.Log
import com.inik.camcon.domain.model.PtpTimeoutException
import com.inik.camcon.domain.model.PtpSessionState
import com.inik.camcon.domain.manager.CameraStateObserver
import com.inik.camcon.domain.model.CameraAbilitiesInfo
import com.inik.camcon.domain.model.CameraCapabilities
import com.inik.camcon.domain.model.CameraSettings
import com.inik.camcon.domain.model.CapturedPhoto
import com.inik.camcon.domain.model.LiveViewFrame
import com.inik.camcon.domain.model.ShootingMode
import com.inik.camcon.presentation.viewmodel.CameraUiState
import com.inik.camcon.presentation.viewmodel.RawFileRestriction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 카메라 UI 상태 관리를 담당하는 클래스
 *
 * liveViewFrame은 초당 수십 회 업데이트되므로 별도 StateFlow로 분리하여
 * 다른 상태 변경이 라이브뷰 영역을 recompose하지 않도록 한다.
 */
@Singleton
class CameraUiStateManager @Inject constructor() : CameraStateObserver {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    /** 라이브뷰 프레임 — 초당 수십 회 업데이트되므로 uiState와 분리 */
    private val _liveViewFrame = MutableStateFlow<LiveViewFrame?>(null)
    val liveViewFrame: StateFlow<LiveViewFrame?> = _liveViewFrame.asStateFlow()

    companion object {
        private const val TAG = "카메라UI상태관리자"
    }

    // ── Connection ──

    /**
     * 연결 상태 업데이트
     */
    fun updateConnectionState(isConnected: Boolean, errorMessage: String? = null) {
        _uiState.update {
            it.copy(
                connection = it.connection.copy(isConnected = isConnected),
                error = when {
                    isConnected -> null
                    errorMessage != null -> errorMessage
                    else -> it.error
                }
            )
        }
        Log.d(TAG, "연결 상태 업데이트: $isConnected, 에러: $errorMessage")
    }

    /**
     * 네이티브 카메라 연결 상태 업데이트
     */
    fun updateNativeCameraConnection(isConnected: Boolean) {
        _uiState.update {
            it.copy(
                connection = it.connection.copy(
                    isNativeCameraConnected = isConnected,
                    isConnected = isConnected
                )
            )
        }
        Log.d(TAG, "네이티브 카메라 연결 상태 업데이트: $isConnected")
    }

    /**
     * USB 디바이스 상태 업데이트
     */
    fun updateUsbDeviceState(deviceCount: Int, hasPermission: Boolean) {
        _uiState.update {
            it.copy(
                connection = it.connection.copy(
                    usbDeviceCount = deviceCount,
                    hasUsbPermission = hasPermission,
                    isUsbInitializing = if (hasPermission) false else it.connection.isUsbInitializing,
                    usbInitializationMessage = if (hasPermission) null else it.connection.usbInitializationMessage
                ),
                error = when {
                    deviceCount == 0 && !it.connection.isConnected -> "USB 카메라가 감지되지 않음"
                    !hasPermission && deviceCount > 0 -> "USB 권한이 필요합니다"
                    else -> it.error
                }
            )
        }
        Log.d(TAG, "USB 디바이스 상태 업데이트: 개수=$deviceCount, 권한=$hasPermission")
    }

    // ── Settings / Capabilities ──

    /**
     * 카메라 기능 정보 업데이트
     */
    override fun updateCameraCapabilities(capabilities: CameraCapabilities?) {
        _uiState.update {
            it.copy(
                settings = it.settings.copy(cameraCapabilities = capabilities),
                error = if (capabilities == null && it.connection.isConnected)
                    "카메라 기능 정보를 가져올 수 없음" else it.error
            )
        }
        Log.d(TAG, "카메라 기능 정보 업데이트: ${capabilities?.model ?: "null"}")
    }

    /**
     * 카메라 설정 업데이트
     */
    fun updateCameraSettings(settings: CameraSettings) {
        _uiState.update {
            it.copy(settings = it.settings.copy(cameraSettings = settings))
        }
        Log.d(TAG, "카메라 설정 업데이트")
    }

    // ── Capture ──

    /**
     * 촬영된 사진 목록 업데이트
     */
    fun updateCapturedPhotos(photos: List<CapturedPhoto>) {
        _uiState.update {
            it.copy(capture = it.capture.copy(capturedPhotos = photos))
        }
        Log.d(TAG, "촬영된 사진 목록 업데이트: ${photos.size}개")
    }

    /**
     * 촬영 상태 업데이트
     */
    fun updateCapturingState(isCapturing: Boolean) {
        _uiState.update {
            it.copy(capture = it.capture.copy(isCapturing = isCapturing))
        }
    }

    /**
     * 촬영 모드 설정
     */
    fun setShootingMode(mode: ShootingMode) {
        _uiState.update {
            it.copy(capture = it.capture.copy(shootingMode = mode))
        }
        Log.d(TAG, "촬영 모드 설정: $mode")
    }

    /**
     * 초점 상태 업데이트
     */
    fun updateFocusingState(isFocusing: Boolean) {
        _uiState.update {
            it.copy(capture = it.capture.copy(isFocusing = isFocusing))
        }
    }

    // ── LiveView ──

    /**
     * 라이브뷰 상태 업데이트
     *
     * frame은 별도 StateFlow(_liveViewFrame)로 분리.
     * isActive/isLoading만 uiState에 반영한다.
     */
    fun updateLiveViewState(
        isActive: Boolean = _uiState.value.liveView.isLiveViewActive,
        isLoading: Boolean = _uiState.value.liveView.isLiveViewLoading,
        frame: LiveViewFrame? = _liveViewFrame.value
    ) {
        _uiState.update {
            it.copy(
                liveView = it.liveView.copy(
                    isLiveViewActive = isActive,
                    isLiveViewLoading = isLoading
                )
            )
        }
        // frame은 별도 StateFlow로 관리
        _liveViewFrame.value = frame
    }

    // ── Global / Misc ──

    /**
     * 이벤트 리스너 상태 업데이트
     */
    fun updateEventListenerState(isActive: Boolean) {
        _uiState.update { it.copy(isEventListenerActive = isActive) }
        Log.d(TAG, "이벤트 리스너 상태 업데이트: $isActive")
    }

    /**
     * 카메라 초기화 상태 업데이트
     */
    override fun updateCameraInitialization(isInitializing: Boolean) {
        val previousState = _uiState.value.isCameraInitializing
        _uiState.update { it.copy(isCameraInitializing = isInitializing) }
        Log.d(TAG, "카메라 초기화 상태 업데이트: $previousState -> $isInitializing")

        if (!isInitializing && previousState) {
            Log.d(TAG, " 카메라 초기화 완료! UI 블로킹 해제됨")
        } else if (isInitializing && !previousState) {
            Log.d(TAG, " 카메라 초기화 시작 - UI 블로킹 활성화")
        }
    }

    /**
     * 로딩 상태 업데이트
     */
    fun updateLoadingState(isLoading: Boolean) {
        _uiState.update { it.copy(isLoading = isLoading) }
    }

    /**
     * 초기화 상태 업데이트
     */
    fun updateInitializingState(isInitializing: Boolean) {
        _uiState.update { it.copy(isInitializing = isInitializing) }
    }

    /**
     * USB 초기화 상태 관리
     */
    fun updateUsbInitialization(isInitializing: Boolean, message: String? = null) {
        _uiState.update {
            it.copy(
                connection = it.connection.copy(
                    isUsbInitializing = isInitializing,
                    usbInitializationMessage = message
                )
            )
        }
        Log.d(TAG, "USB 초기화 상태 업데이트: $isInitializing, 메시지: $message")
    }

    // ── Error ──

    /**
     * 에러 상태 관리
     */
    fun setError(error: String?) {
        _uiState.update { it.copy(error = error) }
        if (error != null) {
            Log.e(TAG, "에러 상태 설정: $error")
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * PTP 타임아웃 상태 관리
     */
    fun handlePtpTimeout(exception: Throwable) {
        val isPtpTimeout = exception is PtpTimeoutException
        _uiState.update {
            it.copy(
                connection = it.connection.copy(isPtpTimeout = isPtpTimeout),
                error = if (isPtpTimeout) {
                    "PTP 카메라 통신이 일정 시간 동안 응답하지 않습니다. 연결을 다시 시도해주세요."
                } else {
                    exception.message
                }
            )
        }
        Log.d(TAG, "PTP 타임아웃 처리: $isPtpTimeout")
    }

    fun clearPtpTimeout() {
        _uiState.update {
            it.copy(connection = it.connection.copy(isPtpTimeout = false))
        }
        Log.d(TAG, "PTP 타임아웃 상태 초기화")
    }

    // ── USB Disconnection ──

    /**
     * USB 분리 상태 처리
     */
    fun handleUsbDisconnection() {
        _uiState.update {
            it.copy(
                connection = it.connection.copy(
                    isUsbDisconnected = true,
                    isConnected = false,
                    isNativeCameraConnected = false,
                    isPtpTimeout = false,
                    connectedCameraModel = null,       // 추가: 카메라 정보 초기화
                    connectedCameraManufacturer = null
                ),
                liveView = it.liveView.copy(
                    isLiveViewActive = false,
                    isLiveViewLoading = false
                ),
                capture = it.capture.copy(
                    isCapturing = false,
                    isFocusing = false
                ),
                settings = it.settings.copy(
                    cameraCapabilities = null
                ),
                error = "USB 카메라가 분리되었습니다.\n\n카메라를 다시 연결하려면:\n1. USB 케이블을 다시 연결하세요\n2. 화면 하단의 '새로고침' 버튼을 눌러주세요"
            )
        }
        _liveViewFrame.value = null
        Log.e(TAG, "USB 분리 상태 처리 완료")
    }

    fun clearUsbDisconnection() {
        _uiState.update {
            it.copy(connection = it.connection.copy(isUsbDisconnected = false))
        }
        Log.d(TAG, "USB 분리 상태 초기화")
    }

    // ── Dialogs ──

    /**
     * 앱 재시작 다이얼로그 관리
     */
    fun showRestartDialog(show: Boolean) {
        _uiState.update {
            it.copy(dialog = it.dialog.copy(showRestartDialog = show))
        }
    }

    /**
     * 카메라 상태 점검 다이얼로그 관리
     */
    override fun showCameraStatusCheckDialog(show: Boolean) {
        _uiState.update {
            it.copy(dialog = it.dialog.copy(showCameraStatusCheckDialog = show))
        }
        Log.d(TAG, "카메라 상태 점검 다이얼로그 상태 업데이트: $show")
    }

    // ── Connection Success / Failure ──

    /**
     * 연결 성공 시 상태 초기화
     */
    fun onConnectionSuccess() {
        _uiState.update {
            it.copy(
                connection = it.connection.copy(
                    isConnected = true,
                    isUsbInitializing = false,
                    usbInitializationMessage = null,
                    isPtpTimeout = false,
                    isUsbDisconnected = false
                ),
                error = null
            )
        }
        Log.d(TAG, "연결 성공 상태로 업데이트")
    }

    /**
     * 연결 실패 시 상태 처리
     */
    fun onConnectionFailure(error: Throwable) {
        val isPtpTimeout = error is PtpTimeoutException
        _uiState.update {
            it.copy(
                connection = it.connection.copy(
                    isConnected = false,
                    isUsbInitializing = false,
                    usbInitializationMessage = null,
                    isPtpTimeout = isPtpTimeout
                ),
                error = if (isPtpTimeout) {
                    "PTP 카메라 통신이 일정 시간 동안 응답하지 않습니다. 연결을 다시 시도해주세요."
                } else {
                    error.message
                }
            )
        }
        Log.e(TAG, "연결 실패 상태 처리: ${error.message}")
    }

    /**
     * 카메라 연결 해제 시 상태 초기화
     */
    fun onCameraDisconnected() {
        _uiState.update {
            it.copy(
                connection = it.connection.copy(
                    isConnected = false,
                    isNativeCameraConnected = false,
                    isPtpTimeout = false,
                    currentCamera = null,
                    connectedCameraModel = null,
                    connectedCameraManufacturer = null
                ),
                liveView = it.liveView.copy(
                    isLiveViewActive = false,
                    isLiveViewLoading = false
                ),
                capture = it.capture.copy(
                    isCapturing = false,
                    isFocusing = false
                ),
                settings = it.settings.copy(
                    cameraCapabilities = null
                ),
                error = null
            )
        }
        _liveViewFrame.value = null
        Log.d(TAG, "카메라 연결 해제 상태로 업데이트")
    }

    // ── RAW File Restriction ──

    /**
     * RAW 파일 촬영 제한 상태 업데이트
     */
    fun setRawFileRestriction(fileName: String, message: String) {
        val restriction = RawFileRestriction(fileName, message)
        _uiState.update {
            it.copy(dialog = it.dialog.copy(rawFileRestriction = restriction))
        }
        Log.d(TAG, "RAW 파일 제한 상태 업데이트: $fileName - $message")
    }

    fun setRawFileRestriction(restriction: RawFileRestriction?) {
        _uiState.update {
            it.copy(dialog = it.dialog.copy(rawFileRestriction = restriction))
        }
        Log.d(TAG, "RAW 파일 제한 상태 업데이트: $restriction")
    }

    /**
     * RAW 파일 촬영 제한 초기화
     */
    fun clearRawFileRestriction() {
        _uiState.update {
            it.copy(dialog = it.dialog.copy(rawFileRestriction = null))
        }
        Log.d(TAG, "RAW 파일 제한 상태 초기화")
    }

    /**
     * Issue W2: 미구현 촬영 모드 에러 메시지 설정
     */
    fun setShootingModeError(message: String?) {
        _uiState.update {
            it.copy(dialog = it.dialog.copy(shootingModeError = message))
        }
        if (message != null) {
            Log.w(TAG, "미구현 촬영 모드 에러: $message")
        }
    }

    /**
     * Issue W2: 미구현 촬영 모드 에러 메시지 초기화
     */
    fun clearShootingModeError() {
        _uiState.update {
            it.copy(dialog = it.dialog.copy(shootingModeError = null))
        }
        Log.d(TAG, "미구현 촬영 모드 에러 초기화")
    }

    // ── PTPIP ──

    /**
     * PTP 세션 상태 업데이트
     */
    override fun updatePtpSessionState(state: PtpSessionState) {
        val message = when (state) {
            PtpSessionState.DISCONNECTED -> ""
            PtpSessionState.SOCKET_CONNECTING -> "소켓 연결 중..."
            PtpSessionState.SOCKET_CONNECTED -> "소켓 연결 완료"
            PtpSessionState.OPENING -> "세션 초기화 중..."
            PtpSessionState.OPEN -> "세션 열림"
            PtpSessionState.READY -> "준비 완료"
            PtpSessionState.CLOSING -> "세션 종료 중..."
        }
        _uiState.update {
            it.copy(connection = it.connection.copy(connectionProgressMessage = message))
        }
        Log.d(TAG, "PTP 세션 상태 업데이트: $state -> $message")
    }

    /**
     * PTPIP 연결 상태 업데이트
     */
    override fun updatePtpipConnectionState(isConnected: Boolean) {
        _uiState.update {
            it.copy(
                connection = it.connection.copy(
                    isPtpipConnected = isConnected,
                    isConnected = isConnected || it.connection.isNativeCameraConnected
                )
            )
        }
        Log.d(TAG, "PTPIP 연결 상태 업데이트: isConnected=$isConnected")
    }

    /**
     * 사진 미리보기 탭 블록 상태 업데이트
     */
    fun blockPreviewTab(blocked: Boolean) {
        _uiState.update { it.copy(isPreviewTabBlocked = blocked) }
        Log.d(TAG, "사진 미리보기 탭 블록 상태 업데이트: $blocked")
    }

    // ── UI Visibility / Abilities ──

    /**
     * 카메라 기능 제한 안내 닫기
     */
    fun clearCameraFunctionLimitation() {
        _uiState.update {
            it.copy(dialog = it.dialog.copy(cameraFunctionLimitation = null))
        }
        Log.d(TAG, "카메라 기능 제한 안내 초기화")
    }

    /**
     * Nikon STA 경고 닫기
     */
    fun dismissNikonStaWarning() {
        _uiState.update {
            it.copy(dialog = it.dialog.copy(showNikonStaWarning = false))
        }
        Log.d(TAG, "Nikon STA 경고 닫기")
    }

    /**
     * 카메라 Abilities 기반 UI 업데이트
     */
    override fun updateCameraAbilities(abilities: CameraAbilitiesInfo) {
        Log.i(TAG, "=== 카메라 Abilities 기반 UI 업데이트 ===")
        Log.i(TAG, "모델: ${abilities.model}")
        Log.i(TAG, "제조사: ${abilities.getManufacturer()}")
        Log.i(TAG, "드라이버 상태: ${abilities.status}")

        _uiState.update { currentState ->
            currentState.copy(
                // 동적 UI 가시성
                uiVisibility = currentState.uiVisibility.copy(
                    showCaptureButton = abilities.supports.captureImage ||
                            abilities.supports.triggerCapture,
                    showLiveViewTab = abilities.supports.capturePreview,
                    showVideoButton = abilities.supports.captureVideo,
                    showConfigTab = abilities.supports.config,
                    showDeleteButton = abilities.supports.delete,
                    showUploadButton = abilities.supports.putFile,
                    showBulbMode = abilities.supports.captureImage,
                    showIntervalShooting = abilities.supports.captureImage &&
                            abilities.supports.triggerCapture
                ),

                // 다이얼로그 / 경고
                dialog = currentState.dialog.copy(
                    cameraFunctionLimitation = when {
                        abilities.supports.isFullyControllable() -> {
                            Log.i(TAG, " 완전한 원격 제어 가능")
                            null
                        }

                        abilities.supports.isDownloadOnly() -> {
                            Log.w(TAG, " 다운로드만 가능 (원격 제어 불가)")
                            "이 카메라는 파일 다운로드만 지원합니다\n" +
                                    "원격 촬영 및 라이브뷰는 사용할 수 없습니다\n\n" +
                                    "제조사: ${abilities.getManufacturer()}\n" +
                                    "모델: ${abilities.model}"
                        }

                        !abilities.supports.capturePreview -> {
                            Log.w(TAG, " 라이브뷰 미지원")
                            "이 카메라는 라이브뷰를 지원하지 않습니다\n" +
                                    "촬영은 가능하지만 실시간 미리보기는 불가능합니다"
                        }

                        !abilities.supports.config -> {
                            Log.w(TAG, " 설정 변경 미지원")
                            "이 카메라는 설정 변경을 지원하지 않습니다\n" +
                                    "촬영은 가능하지만 ISO, 셔터속도 등 설정은 카메라에서 직접 조정해주세요"
                        }

                        else -> null
                    },
                    showNikonStaWarning = abilities.needsStaAuthentication()
                ),

                // 연결된 카메라 정보
                connection = currentState.connection.copy(
                    connectedCameraModel = abilities.model,
                    connectedCameraManufacturer = abilities.getManufacturer()
                )
            )
        }

        Log.i(TAG, " UI 상태 업데이트 완료:")
        Log.i(TAG, "   촬영 버튼: ${abilities.supports.captureImage}")
        Log.i(TAG, "   라이브뷰 탭: ${abilities.supports.capturePreview}")
        Log.i(TAG, "   비디오 버튼: ${abilities.supports.captureVideo}")
        Log.i(TAG, "   설정 탭: ${abilities.supports.config}")
        Log.i(TAG, "   삭제 버튼: ${abilities.supports.delete}")
        Log.i(TAG, "   기능 제한: ${_uiState.value.cameraFunctionLimitation != null}")
    }
}
