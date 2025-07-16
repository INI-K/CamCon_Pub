package com.inik.camcon.domain.manager

import android.util.Log
import com.inik.camcon.data.datasource.ptpip.PtpipDataSource
import com.inik.camcon.data.datasource.usb.UsbCameraManager
import com.inik.camcon.domain.model.CameraConnectionType
import com.inik.camcon.domain.model.GlobalCameraConnectionState
import com.inik.camcon.domain.model.PtpipConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 카메라 연결 상태를 앱 전역에서 관리하는 매니저
 * USB, AP모드, STA모드 등 모든 연결 방식의 상태를 통합 관리
 */
@Singleton
class CameraConnectionGlobalManager @Inject constructor(
    private val ptpipDataSource: PtpipDataSource,
    private val usbCameraManager: UsbCameraManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "CameraConnectionGlobalManager"
    }

    // 전역 카메라 연결 상태
    private val _globalConnectionState = MutableStateFlow(GlobalCameraConnectionState())
    val globalConnectionState: StateFlow<GlobalCameraConnectionState> =
        _globalConnectionState.asStateFlow()

    // 현재 활성 연결 타입
    private val _activeConnectionType = MutableStateFlow<CameraConnectionType?>(null)
    val activeConnectionType: StateFlow<CameraConnectionType?> = _activeConnectionType.asStateFlow()

    // 연결 상태 메시지
    private val _connectionStatusMessage = MutableStateFlow("연결 안됨")
    val connectionStatusMessage: StateFlow<String> = _connectionStatusMessage.asStateFlow()

    init {
        startGlobalStateMonitoring()
    }

    /**
     * 전역 상태 모니터링 시작
     */
    private fun startGlobalStateMonitoring() {
        // USB 카메라 연결 상태 모니터링
        usbCameraManager.isNativeCameraConnected
            .onEach { isConnected ->
                Log.d(TAG, "USB 카메라 연결 상태 변경: $isConnected")
                updateGlobalState()
            }
            .launchIn(scope)

        // PTPIP 연결 상태 모니터링
        ptpipDataSource.connectionState
            .onEach { state ->
                Log.d(TAG, "PTPIP 연결 상태 변경: $state")
                updateGlobalState()
            }
            .launchIn(scope)

        // WiFi 네트워크 상태 모니터링
        ptpipDataSource.wifiNetworkState
            .onEach { networkState ->
                Log.d(TAG, "WiFi 네트워크 상태 변경: $networkState")
                updateGlobalState()
            }
            .launchIn(scope)

        // 발견된 카메라 목록 모니터링
        ptpipDataSource.discoveredCameras
            .onEach { cameras ->
                Log.d(TAG, "발견된 카메라 목록 변경: ${cameras.size}개")
                updateGlobalState()
            }
            .launchIn(scope)
    }

    /**
     * 전역 상태 업데이트
     */
    private fun updateGlobalState() {
        val usbConnected = usbCameraManager.isNativeCameraConnected.value
        val ptpipState = ptpipDataSource.connectionState.value
        val wifiState = ptpipDataSource.wifiNetworkState.value
        val discoveredCameras = ptpipDataSource.discoveredCameras.value

        // 활성 연결 타입 결정
        val activeConnection = when {
            usbConnected -> CameraConnectionType.USB
            ptpipState == PtpipConnectionState.CONNECTED -> {
                if (wifiState.isConnectedToCameraAP) {
                    CameraConnectionType.AP_MODE
                } else {
                    CameraConnectionType.STA_MODE
                }
            }

            else -> null
        }

        // 연결 상태 메시지 생성
        val statusMessage = generateStatusMessage(
            usbConnected = usbConnected,
            ptpipState = ptpipState,
            wifiState = wifiState,
            discoveredCameras = discoveredCameras
        )

        // 상태 변경이 있을 때만 업데이트
        val currentState = _globalConnectionState.value
        val newState = GlobalCameraConnectionState(
            isUsbConnected = usbConnected,
            ptpipConnectionState = ptpipState,
            wifiNetworkState = wifiState,
            discoveredCameras = discoveredCameras,
            activeConnectionType = activeConnection,
            isAnyConnectionActive = usbConnected || ptpipState == PtpipConnectionState.CONNECTED
        )

        // 상태가 실제로 변경되었을 때만 업데이트
        if (currentState != newState ||
            _activeConnectionType.value != activeConnection ||
            _connectionStatusMessage.value != statusMessage
        ) {

            _globalConnectionState.value = newState
            _activeConnectionType.value = activeConnection
            _connectionStatusMessage.value = statusMessage

            Log.d(
                TAG,
                "전역 상태 업데이트: activeConnection=$activeConnection, statusMessage=$statusMessage"
            )
        }
    }

    /**
     * 상태 메시지 생성
     */
    private fun generateStatusMessage(
        usbConnected: Boolean,
        ptpipState: PtpipConnectionState,
        wifiState: com.inik.camcon.domain.model.WifiNetworkState,
        discoveredCameras: List<com.inik.camcon.domain.model.PtpipCamera>
    ): String {
        return when {
            usbConnected -> "USB 카메라 연결됨"
            ptpipState == PtpipConnectionState.CONNECTED -> {
                if (wifiState.isConnectedToCameraAP) {
                    "AP 모드 연결됨 (${wifiState.ssid})"
                } else {
                    "STA 모드 연결됨 (${wifiState.ssid})"
                }
            }

            ptpipState == PtpipConnectionState.CONNECTING -> "카메라 연결 중..."
            ptpipState == PtpipConnectionState.ERROR -> "카메라 연결 오류"
            wifiState.isConnectedToCameraAP -> "카메라 AP 연결됨 - 카메라 검색 가능"
            wifiState.isConnected -> "Wi-Fi 연결됨 - 카메라 검색 가능"
            else -> "연결 안됨"
        }
    }

    /**
     * 현재 활성 연결 타입 확인
     */
    fun getCurrentActiveConnectionType(): CameraConnectionType? {
        return activeConnectionType.value
    }

    /**
     * 특정 연결 타입이 활성화되어 있는지 확인
     */
    fun isConnectionTypeActive(type: CameraConnectionType): Boolean {
        return activeConnectionType.value == type
    }

    /**
     * 카메라가 연결되어 있는지 확인
     */
    fun isAnyCameraConnected(): Boolean {
        return globalConnectionState.value.isAnyConnectionActive
    }

    /**
     * AP 모드 연결 상태 확인
     */
    fun isApModeConnected(): Boolean {
        return globalConnectionState.value.wifiNetworkState.isConnectedToCameraAP
    }

    /**
     * STA 모드 연결 상태 확인
     */
    fun isStaModeConnected(): Boolean {
        val state = globalConnectionState.value
        return state.ptpipConnectionState == PtpipConnectionState.CONNECTED &&
                !state.wifiNetworkState.isConnectedToCameraAP
    }

    /**
     * USB 연결 상태 확인
     */
    fun isUsbConnected(): Boolean {
        return globalConnectionState.value.isUsbConnected
    }

    /**
     * 리소스 정리
     */
    fun cleanup() {
        Log.d(TAG, "리소스 정리 시작")
        // scope는 자동으로 정리됨
    }
}