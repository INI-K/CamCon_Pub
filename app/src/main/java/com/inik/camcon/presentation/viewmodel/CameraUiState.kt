package com.inik.camcon.presentation.viewmodel

import com.inik.camcon.domain.model.Camera
import com.inik.camcon.domain.model.CameraCapabilities
import com.inik.camcon.domain.model.CameraSettings
import com.inik.camcon.domain.model.CapturedPhoto
import com.inik.camcon.domain.model.LiveViewFrame
import com.inik.camcon.domain.model.ShootingMode

/**
 * 카메라 UI 상태를 나타내는 데이터 클래스
 */
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
    val isLiveViewLoading: Boolean = false,
    val isEventListenerActive: Boolean = false,
    // USB 연결 및 초기화 중 전체 UI 블로킹을 위한 상태
    val isUsbInitializing: Boolean = false,
    val usbInitializationMessage: String? = null,
    // 카메라 이벤트 리스너 초기화 중 UI 블로킹을 위한 상태
    val isCameraInitializing: Boolean = false,
    // PTP 타임아웃 오류 상태
    val isPtpTimeout: Boolean = false,
    // 앱 재시작 다이얼로그 표시 플래그
    val showRestartDialog: Boolean = false,
    // USB 디바이스 분리 상태
    val isUsbDisconnected: Boolean = false,
    // 카메라 상태 점검 다이얼로그 표시 플래그
    val showCameraStatusCheckDialog: Boolean = false
)