package com.inik.camcon.data.repository.managers

import android.util.Log
import com.inik.camcon.data.datasource.ptpip.PtpipDataSource
import com.inik.camcon.data.datasource.usb.UsbCameraManager
import com.inik.camcon.domain.manager.CameraConnectionGlobalManager
import com.inik.camcon.domain.model.CameraConnectionType
import com.inik.camcon.domain.model.GlobalCameraConnectionState
import com.inik.camcon.domain.model.PtpipConnectionState
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.WifiNetworkState
import com.inik.camcon.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@Singleton
class CameraConnectionGlobalManagerImpl @Inject constructor(
    private val ptpipDataSource: PtpipDataSource,
    private val usbCameraManager: UsbCameraManager,
    private val cameraConnectionManager: CameraConnectionManager,
    @ApplicationScope private val appScope: CoroutineScope
) : CameraConnectionGlobalManager {

    companion object {
        private const val TAG = "CameraConnectionGlobalManager"
    }

    // 앱 scope의 자식 scope — cancelChildren해도 앱 scope에 영향 없음
    private var managerScope = createManagerScope()

    private fun createManagerScope(): CoroutineScope =
        CoroutineScope(appScope.coroutineContext + SupervisorJob(appScope.coroutineContext.job))

    private val _globalConnectionState = MutableStateFlow(GlobalCameraConnectionState())
    override val globalConnectionState: StateFlow<GlobalCameraConnectionState> =
        _globalConnectionState.asStateFlow()

    private val _activeConnectionType = MutableStateFlow<CameraConnectionType?>(null)
    override val activeConnectionType: StateFlow<CameraConnectionType?> =
        _activeConnectionType.asStateFlow()

    private val _connectionStatusMessage = MutableStateFlow("연결 안됨")
    override val connectionStatusMessage: StateFlow<String> =
        _connectionStatusMessage.asStateFlow()

    init {
        startGlobalStateMonitoring()
    }

    private fun startGlobalStateMonitoring() {
        usbCameraManager.isNativeCameraConnected
            .onEach { Log.d(TAG, "USB 카메라 연결 상태 변경: $it"); updateGlobalState() }
            .launchIn(managerScope)

        ptpipDataSource.connectionState
            .onEach { Log.d(TAG, "PTPIP 연결 상태 변경: $it"); updateGlobalState() }
            .launchIn(managerScope)

        ptpipDataSource.wifiNetworkState
            .onEach { Log.d(TAG, "WiFi 네트워크 상태 변경: $it"); updateGlobalState() }
            .launchIn(managerScope)

        ptpipDataSource.isApModeForced
            .onEach { Log.d(TAG, "AP 모드 강제 플래그 변경: $it"); updateGlobalState() }
            .launchIn(managerScope)

        ptpipDataSource.discoveredCameras
            .onEach { Log.d(TAG, "발견된 카메라 목록 변경: ${it.size}개"); updateGlobalState() }
            .launchIn(managerScope)
    }

    private fun updateGlobalState() {
        val usbConnected = usbCameraManager.isNativeCameraConnected.value
        val ptpipState = ptpipDataSource.connectionState.value
        val wifiState = ptpipDataSource.wifiNetworkState.value
        val discoveredCameras = ptpipDataSource.discoveredCameras.value
        val isApForced = ptpipDataSource.isApModeForced.value

        val activeConnection = when {
            usbConnected -> CameraConnectionType.USB
            ptpipState == PtpipConnectionState.CONNECTED -> {
                if (isApForced || isApConnection(wifiState)) {
                    CameraConnectionType.AP_MODE
                } else {
                    CameraConnectionType.STA_MODE
                }
            }

            else -> null
        }

        if (activeConnection == CameraConnectionType.AP_MODE) {
            managerScope.launch {
                try {
                    cameraConnectionManager.updatePtpipConnectionStatus(true)
                    Log.d(TAG, "AP 모드 연결 감지: CameraConnectionManager를 통해 PTPIP 상태 업데이트")
                } catch (error: Exception) {
                    Log.e(TAG, "AP 모드 PTPIP 상태 업데이트 실패", error)
                }
            }
        } else if (ptpipState != PtpipConnectionState.CONNECTED) {
            managerScope.launch {
                try {
                    cameraConnectionManager.updatePtpipConnectionStatus(false)
                    Log.d(TAG, "PTPIP 연결 해제 상태 업데이트")
                } catch (error: Exception) {
                    Log.e(TAG, "PTPIP 연결 해제 상태 업데이트 실패", error)
                }
            }
        }

        if (!usbConnected && ptpipState != PtpipConnectionState.CONNECTED) {
            performDisconnectionCleanup()
        }

        val statusMessage = generateStatusMessage(
            usbConnected = usbConnected,
            ptpipState = ptpipState,
            wifiState = wifiState,
            discoveredCameras = discoveredCameras,
            isApForced = isApForced
        )

        val currentState = _globalConnectionState.value
        val newState = GlobalCameraConnectionState(
            isUsbConnected = usbConnected,
            ptpipConnectionState = ptpipState,
            wifiNetworkState = wifiState,
            discoveredCameras = discoveredCameras,
            activeConnectionType = activeConnection,
            isAnyConnectionActive = usbConnected || ptpipState == PtpipConnectionState.CONNECTED
        )

        if (
            currentState != newState ||
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

    private fun generateStatusMessage(
        usbConnected: Boolean,
        ptpipState: PtpipConnectionState,
        wifiState: WifiNetworkState,
        discoveredCameras: List<PtpipCamera>,
        isApForced: Boolean
    ): String {
        return when {
            usbConnected -> "USB 카메라 연결됨"
            ptpipState == PtpipConnectionState.CONNECTED -> {
                if (isApForced || isApConnection(wifiState)) {
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

    private fun isApConnection(wifiState: WifiNetworkState): Boolean {
        val ipPrefix = wifiState.detectedCameraIP
        return wifiState.isConnectedToCameraAP ||
                ipPrefix?.startsWith("192.168.1.") == true ||
                ipPrefix?.startsWith("192.168.4.") == true
    }

    override fun getCurrentActiveConnectionType(): CameraConnectionType? {
        return activeConnectionType.value
    }

    override fun isConnectionTypeActive(type: CameraConnectionType): Boolean {
        return activeConnectionType.value == type
    }

    override fun isAnyCameraConnected(): Boolean {
        return globalConnectionState.value.isAnyConnectionActive
    }

    override fun isApModeConnected(): Boolean {
        return globalConnectionState.value.wifiNetworkState.isConnectedToCameraAP
    }

    override fun isStaModeConnected(): Boolean {
        val state = globalConnectionState.value
        return state.ptpipConnectionState == PtpipConnectionState.CONNECTED &&
                !state.wifiNetworkState.isConnectedToCameraAP
    }

    override fun isUsbConnected(): Boolean {
        return globalConnectionState.value.isUsbConnected
    }

    override fun setPtpipPhotoCapturedCallback(callback: (String, String) -> Unit) {
        ptpipDataSource.setPhotoCapturedCallback(callback)
        Log.d(TAG, "PTPIP 외부 셔터 감지 콜백 설정 완료")
    }

    private fun performDisconnectionCleanup() {
        Log.d(TAG, "연결 해제 시 정리 수행")

        managerScope.launch {
            try {
                try {
                    cameraConnectionManager.updatePtpipConnectionStatus(false)
                    Log.d(TAG, "카메라 연결 매니저 PTPIP 상태 정리 완료")
                } catch (error: Exception) {
                    Log.e(TAG, "카메라 연결 매니저 정리 실패", error)
                }

                Log.d(TAG, "전역 카메라 연결 상태 정리 완료")
            } catch (error: Exception) {
                Log.e(TAG, "연결 해제 정리 중 오류", error)
            }
        }
    }

    override fun cleanup() {
        Log.d(TAG, "CameraConnectionGlobalManagerImpl 리소스 정리 시작")
        managerScope.coroutineContext.job.cancel()
        managerScope = createManagerScope()
        // 정리 후 모니터링 재시작 (Singleton이므로 앱 수명 동안 유지되어야 함)
        startGlobalStateMonitoring()
    }
}
