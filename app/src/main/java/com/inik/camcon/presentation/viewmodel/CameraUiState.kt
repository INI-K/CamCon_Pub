package com.inik.camcon.presentation.viewmodel

import androidx.compose.runtime.Stable
import com.inik.camcon.domain.model.Camera
import com.inik.camcon.domain.model.CameraCapabilities
import com.inik.camcon.domain.model.CameraSettings
import com.inik.camcon.domain.model.CapturedPhoto
import com.inik.camcon.domain.model.LiveViewFrame
import com.inik.camcon.domain.model.ShootingMode
import com.inik.camcon.domain.model.TransferQueueState

/**
 * 카메라 연결 관련 상태
 */
@Stable
data class CameraConnectionState(
    val isConnected: Boolean = false,
    val isNativeCameraConnected: Boolean = false,
    val isPtpipConnected: Boolean = false,
    val currentCamera: Camera? = null,
    val usbDeviceCount: Int = 0,
    val hasUsbPermission: Boolean = false,
    val isUsbInitializing: Boolean = false,
    val usbInitializationMessage: String? = null,
    val isUsbDisconnected: Boolean = false,
    val isPtpTimeout: Boolean = false,
    val connectionProgressMessage: String = "",
    val connectedCameraModel: String? = null,
    val connectedCameraManufacturer: String? = null
)

/**
 * 라이브뷰 관련 상태
 * liveViewFrame은 초당 수십 회 업데이트되므로 별도 StateFlow로 분리 권장
 */
@Stable
data class CameraLiveViewState(
    val isLiveViewActive: Boolean = false,
    val isLiveViewLoading: Boolean = false
)

/**
 * 촬영 관련 상태
 */
@Stable
data class CameraCaptureState(
    val isCapturing: Boolean = false,
    val isFocusing: Boolean = false,
    val shootingMode: ShootingMode = ShootingMode.SINGLE,
    val capturedPhotos: List<CapturedPhoto> = emptyList(),
    val transferQueue: TransferQueueState = TransferQueueState()
)

/**
 * 카메라 설정/능력 관련 상태
 */
@Stable
data class CameraSettingsState(
    val cameraSettings: CameraSettings? = null,
    val cameraCapabilities: CameraCapabilities? = null
)

/**
 * 동적 UI 가시성 제어 (카메라 Abilities 기반)
 */
@Stable
data class CameraUiVisibility(
    val showCaptureButton: Boolean = true,
    val showLiveViewTab: Boolean = true,
    val showVideoButton: Boolean = false,
    val showConfigTab: Boolean = true,
    val showDeleteButton: Boolean = true,
    val showUploadButton: Boolean = false,
    val showBulbMode: Boolean = false,
    val showIntervalShooting: Boolean = true
)

/**
 * 다이얼로그/알림 관련 상태
 */
@Stable
data class CameraDialogState(
    val showRestartDialog: Boolean = false,
    val showCameraStatusCheckDialog: Boolean = false,
    val showNikonStaWarning: Boolean = false,
    val cameraFunctionLimitation: String? = null,
    val rawFileRestriction: RawFileRestriction? = null,
    val shootingModeError: String? = null
)

/**
 * 카메라 UI 상태를 나타내는 데이터 클래스 — sub-state 기반
 *
 * liveViewFrame은 초당 수십 회 업데이트되므로 별도 StateFlow로 분리.
 * CameraViewModel.liveViewFrame: StateFlow<LiveViewFrame?> 을 사용한다.
 */
data class CameraUiState(
    val connection: CameraConnectionState = CameraConnectionState(),
    val liveView: CameraLiveViewState = CameraLiveViewState(),
    val capture: CameraCaptureState = CameraCaptureState(),
    val settings: CameraSettingsState = CameraSettingsState(),
    val uiVisibility: CameraUiVisibility = CameraUiVisibility(),
    val dialog: CameraDialogState = CameraDialogState(),

    // 글로벌 상태 (특정 sub-state에 넣기 어려운 것들)
    val error: String? = null,
    val isLoading: Boolean = false,
    val isInitializing: Boolean = false,
    val isCameraInitializing: Boolean = false,
    val isEventListenerActive: Boolean = false
) {
    // === 하위 호환 편의 프로퍼티 ===
    // 기존 코드에서 uiState.isConnected 패턴으로 접근하던 곳을 지원
    inline val isConnected: Boolean get() = connection.isConnected
    inline val isNativeCameraConnected: Boolean get() = connection.isNativeCameraConnected
    inline val isPtpipConnected: Boolean get() = connection.isPtpipConnected
    inline val currentCamera: Camera? get() = connection.currentCamera
    inline val usbDeviceCount: Int get() = connection.usbDeviceCount
    inline val hasUsbPermission: Boolean get() = connection.hasUsbPermission
    inline val isUsbInitializing: Boolean get() = connection.isUsbInitializing
    inline val usbInitializationMessage: String? get() = connection.usbInitializationMessage
    inline val isUsbDisconnected: Boolean get() = connection.isUsbDisconnected
    inline val isPtpTimeout: Boolean get() = connection.isPtpTimeout
    inline val connectionProgressMessage: String get() = connection.connectionProgressMessage
    inline val connectedCameraModel: String? get() = connection.connectedCameraModel
    inline val connectedCameraManufacturer: String? get() = connection.connectedCameraManufacturer

    inline val isLiveViewActive: Boolean get() = liveView.isLiveViewActive
    inline val isLiveViewLoading: Boolean get() = liveView.isLiveViewLoading

    inline val isCapturing: Boolean get() = capture.isCapturing
    inline val isFocusing: Boolean get() = capture.isFocusing
    inline val shootingMode: ShootingMode get() = capture.shootingMode
    inline val capturedPhotos: List<CapturedPhoto> get() = capture.capturedPhotos
    inline val transferQueue: TransferQueueState get() = capture.transferQueue

    inline val cameraSettings: CameraSettings? get() = settings.cameraSettings
    inline val cameraCapabilities: CameraCapabilities? get() = settings.cameraCapabilities

    inline val showCaptureButton: Boolean get() = uiVisibility.showCaptureButton
    inline val showLiveViewTab: Boolean get() = uiVisibility.showLiveViewTab
    inline val showVideoButton: Boolean get() = uiVisibility.showVideoButton
    inline val showConfigTab: Boolean get() = uiVisibility.showConfigTab
    inline val showDeleteButton: Boolean get() = uiVisibility.showDeleteButton
    inline val showUploadButton: Boolean get() = uiVisibility.showUploadButton
    inline val showBulbMode: Boolean get() = uiVisibility.showBulbMode
    inline val showIntervalShooting: Boolean get() = uiVisibility.showIntervalShooting

    inline val showRestartDialog: Boolean get() = dialog.showRestartDialog
    inline val showCameraStatusCheckDialog: Boolean get() = dialog.showCameraStatusCheckDialog
    inline val showNikonStaWarning: Boolean get() = dialog.showNikonStaWarning
    inline val cameraFunctionLimitation: String? get() = dialog.cameraFunctionLimitation
    inline val rawFileRestriction: RawFileRestriction? get() = dialog.rawFileRestriction

    /**
     * UI가 완전히 활성화 가능한지 (모든 기능 지원)
     */
    fun isFullyFunctional(): Boolean {
        return showCaptureButton && showLiveViewTab && showConfigTab &&
                cameraFunctionLimitation == null
    }

    /**
     * 제한적 기능만 가능한지
     */
    fun hasLimitedFunctionality(): Boolean {
        return cameraFunctionLimitation != null
    }
}

/**
 * RAW 파일 제한 알림 정보
 */
data class RawFileRestriction(
    val fileName: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)
