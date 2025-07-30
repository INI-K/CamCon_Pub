package com.inik.camcon.presentation.viewmodel.state

import android.util.Log
import com.inik.camcon.data.repository.managers.PtpTimeoutException
import com.inik.camcon.domain.model.CameraCapabilities
import com.inik.camcon.domain.model.CameraSettings
import com.inik.camcon.domain.model.CapturedPhoto
import com.inik.camcon.domain.model.LiveViewFrame
import com.inik.camcon.domain.model.ShootingMode
import com.inik.camcon.presentation.viewmodel.CameraUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 카메라 UI 상태 관리를 담당하는 클래스
 */
@Singleton
class CameraUiStateManager @Inject constructor() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    companion object {
        private const val TAG = "카메라UI상태관리자"
    }

    /**
     * 연결 상태 업데이트
     */
    fun updateConnectionState(isConnected: Boolean) {
        _uiState.update {
            it.copy(
                isConnected = isConnected,
                error = if (isConnected) null else it.error
            )
        }
        Log.d(TAG, "연결 상태 업데이트: $isConnected")
    }

    /**
     * 네이티브 카메라 연결 상태 업데이트
     */
    fun updateNativeCameraConnection(isConnected: Boolean) {
        _uiState.update {
            it.copy(
                isNativeCameraConnected = isConnected,
                isConnected = isConnected
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
                usbDeviceCount = deviceCount,
                hasUsbPermission = hasPermission,
                error = when {
                    deviceCount == 0 && !it.isConnected -> "USB 카메라가 감지되지 않음"
                    !hasPermission && deviceCount > 0 -> "USB 권한이 필요합니다"
                    else -> it.error
                },
                // 권한이 승인되면 초기화 상태 해제
                isUsbInitializing = if (hasPermission) false else it.isUsbInitializing,
                usbInitializationMessage = if (hasPermission) null else it.usbInitializationMessage
            )
        }
        Log.d(TAG, "USB 디바이스 상태 업데이트: 개수=$deviceCount, 권한=$hasPermission")
    }

    /**
     * 카메라 기능 정보 업데이트
     */
    fun updateCameraCapabilities(capabilities: CameraCapabilities?) {
        _uiState.update {
            it.copy(
                cameraCapabilities = capabilities,
                error = if (capabilities == null && it.isConnected)
                    "카메라 기능 정보를 가져올 수 없음" else it.error
            )
        }
        Log.d(TAG, "카메라 기능 정보 업데이트: ${capabilities?.model ?: "null"}")
    }

    /**
     * 카메라 설정 업데이트
     */
    fun updateCameraSettings(settings: CameraSettings) {
        _uiState.update { it.copy(cameraSettings = settings) }
        Log.d(TAG, "카메라 설정 업데이트")
    }

    /**
     * 촬영된 사진 목록 업데이트
     */
    fun updateCapturedPhotos(photos: List<CapturedPhoto>) {
        _uiState.update { it.copy(capturedPhotos = photos) }
        Log.d(TAG, "촬영된 사진 목록 업데이트: ${photos.size}개")
    }

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
    fun updateCameraInitialization(isInitializing: Boolean) {
        _uiState.update { it.copy(isCameraInitializing = isInitializing) }
        Log.d(TAG, "카메라 초기화 상태 업데이트: $isInitializing")
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
                isUsbInitializing = isInitializing,
                usbInitializationMessage = message
            )
        }
        Log.d(TAG, "USB 초기화 상태 업데이트: $isInitializing, 메시지: $message")
    }

    /**
     * 촬영 상태 업데이트
     */
    fun updateCapturingState(isCapturing: Boolean) {
        _uiState.update { it.copy(isCapturing = isCapturing) }
    }

    /**
     * 촬영 모드 설정
     */
    fun setShootingMode(mode: ShootingMode) {
        _uiState.update { it.copy(shootingMode = mode) }
        Log.d(TAG, "촬영 모드 설정: $mode")
    }

    /**
     * 라이브뷰 상태 업데이트
     */
    fun updateLiveViewState(
        isActive: Boolean = _uiState.value.isLiveViewActive,
        isLoading: Boolean = _uiState.value.isLiveViewLoading,
        frame: LiveViewFrame? = _uiState.value.liveViewFrame
    ) {
        _uiState.update {
            it.copy(
                isLiveViewActive = isActive,
                isLiveViewLoading = isLoading,
                liveViewFrame = frame
            )
        }
        Log.d(TAG, "라이브뷰 상태 업데이트: 활성=$isActive, 로딩=$isLoading")
    }

    /**
     * 초점 상태 업데이트
     */
    fun updateFocusingState(isFocusing: Boolean) {
        _uiState.update { it.copy(isFocusing = isFocusing) }
    }

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
        Log.d(TAG, "에러 상태 초기화")
    }

    /**
     * PTP 타임아웃 상태 관리
     */
    fun handlePtpTimeout(exception: Throwable) {
        val isPtpTimeout = exception is PtpTimeoutException
        _uiState.update {
            it.copy(
                isPtpTimeout = isPtpTimeout,
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
        _uiState.update { it.copy(isPtpTimeout = false) }
        Log.d(TAG, "PTP 타임아웃 상태 초기화")
    }

    /**
     * USB 분리 상태 처리
     */
    fun handleUsbDisconnection() {
        _uiState.update {
            it.copy(
                isUsbDisconnected = true,
                isConnected = false,
                isNativeCameraConnected = false,
                isLiveViewActive = false,
                isLiveViewLoading = false,
                liveViewFrame = null,
                isCapturing = false,
                isFocusing = false,
                isPtpTimeout = false,
                cameraCapabilities = null,
                currentCamera = null,
                error = "USB 카메라가 분리되었습니다.\n\n카메라를 다시 연결하려면:\n1. USB 케이블을 다시 연결하세요\n2. 화면 하단의 '새로고침' 버튼을 눌러주세요"
            )
        }
        Log.e(TAG, "USB 분리 상태 처리 완료")
    }

    fun clearUsbDisconnection() {
        _uiState.update { it.copy(isUsbDisconnected = false) }
        Log.d(TAG, "USB 분리 상태 초기화")
    }

    /**
     * 앱 재시작 다이얼로그 관리
     */
    fun showRestartDialog(show: Boolean) {
        _uiState.update { it.copy(showRestartDialog = show) }
    }

    /**
     * 연결 성공 시 상태 초기화
     */
    fun onConnectionSuccess() {
        _uiState.update {
            it.copy(
                isConnected = true,
                error = null,
                isUsbInitializing = false,
                usbInitializationMessage = null,
                isPtpTimeout = false,
                isUsbDisconnected = false
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
                isConnected = false,
                isUsbInitializing = false,
                usbInitializationMessage = null,
                isPtpTimeout = isPtpTimeout,
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
        Log.d(TAG, "카메라 연결 해제 상태로 업데이트")
    }
}