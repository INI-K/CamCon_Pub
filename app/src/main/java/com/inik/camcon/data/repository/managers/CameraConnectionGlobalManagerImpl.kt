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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
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
    // val로 고정해 재할당 race/가시성 문제를 차단한다. 정리 시 scope 자체를 버리지 않고 자식만 취소한다.
    private val managerScope: CoroutineScope =
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

    // 마지막으로 CameraConnectionManager에 푸시한 PTPIP 연결 상태.
    // 초기화 시 5개 Flow 초깃값이 한꺼번에 emit되며 동일한 false를 수~십수 회 재방출하던
    // 코루틴 launch 자체를 막기 위한 dedup (수신부 멱등 가드와 이중 안전장치).
    // 여러 Flow 컬렉터가 updateGlobalState를 호출할 수 있어 가시성 보장 위해 @Volatile.
    @Volatile
    private var lastPushedPtpipConnected: Boolean? = null

    init {
        startGlobalStateMonitoring()
    }

    private fun startGlobalStateMonitoring() {
        // 각 소스의 per-emit "상태 변경" 로그는 제거 — 변경 결과는 updateGlobalState가
        // 단일 "전역 상태 업데이트" 로그로 요약한다.
        usbCameraManager.isNativeCameraConnected
            .onEach { updateGlobalState() }
            .launchIn(managerScope)

        ptpipDataSource.connectionState
            .onEach { updateGlobalState() }
            .launchIn(managerScope)

        ptpipDataSource.wifiNetworkState
            .onEach { updateGlobalState() }
            .launchIn(managerScope)

        ptpipDataSource.isApModeForced
            .onEach { updateGlobalState() }
            .launchIn(managerScope)

        ptpipDataSource.discoveredCameras
            .onEach { updateGlobalState() }
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

        // AP 연결이면 true, PTPIP 미연결이면 false를 단 한 번만 푸시한다.
        // (STA 연결은 연결 흐름에서 직접 갱신하므로 여기선 null=무시)
        val ptpipStatusToPush: Boolean? = when {
            activeConnection == CameraConnectionType.AP_MODE -> true
            ptpipState != PtpipConnectionState.CONNECTED -> false
            else -> null
        }
        if (ptpipStatusToPush != null && ptpipStatusToPush != lastPushedPtpipConnected) {
            lastPushedPtpipConnected = ptpipStatusToPush
            managerScope.launch {
                try {
                    cameraConnectionManager.updatePtpipConnectionStatus(ptpipStatusToPush)
                    Log.d(TAG, "PTPIP 상태 업데이트: $ptpipStatusToPush")
                } catch (e: CancellationException) {
                    throw e
                } catch (error: Exception) {
                    Log.e(TAG, "PTPIP 상태 업데이트 실패", error)
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

            // statusMessage에는 Wi-Fi SSID가 포함될 수 있어 연결 종류만 로깅한다.
            Log.d(TAG, "전역 상태 업데이트: activeConnection=$activeConnection")
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
        // PTPIP false 상태 반영은 updateGlobalState의 dedup된 단일 경로가 담당한다.
        // (과거 여기서 중복 updatePtpipConnectionStatus(false)를 매 emit마다 launch하던 것을 제거)
        Log.d(TAG, "연결 해제 시 정리 수행")
    }

    override fun cleanup() {
        Log.d(TAG, "CameraConnectionGlobalManagerImpl 리소스 정리 시작")
        // scope 자체는 유지하고 자식 코루틴만 취소 — SupervisorJob은 활성 상태로 남아 재구독 가능하다.
        // (var 재할당 시 다른 스레드의 launch가 stale 참조를 보던 race를 제거)
        managerScope.coroutineContext.job.cancelChildren()
        // dedup 상태를 리셋해 재모니터링 첫 updateGlobalState가 실제 상태를 무조건 1회 재푸시하도록 한다.
        // (dedup var와 StateFlow가 어긋난 채 남는 two-sources-of-truth 취약성 제거)
        lastPushedPtpipConnected = null
        // 정리 후 모니터링 재시작 (Singleton이므로 앱 수명 동안 유지되어야 함)
        startGlobalStateMonitoring()
    }
}
