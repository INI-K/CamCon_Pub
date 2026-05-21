package com.inik.camcon.data.datasource.ptpip

import android.content.Context
import android.util.Log
import com.inik.camcon.CameraNative
import com.inik.camcon.data.datasource.nativesource.CameraCaptureListener
import com.inik.camcon.data.network.ptpip.authentication.NikonAuthenticationService
import com.inik.camcon.data.network.ptpip.connection.PtpipConnectionManager
import com.inik.camcon.data.network.ptpip.discovery.PtpipDiscoveryService
import com.inik.camcon.data.network.ptpip.wifi.WifiNetworkHelper
import com.inik.camcon.domain.model.CameraEndpoint
import com.inik.camcon.domain.model.ConnectionMethod
import com.inik.camcon.domain.model.EndpointSource
import com.inik.camcon.domain.model.NikonConnectionMode
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.PtpipCameraInfo
import com.inik.camcon.domain.model.PtpipConnectionState
import com.inik.camcon.domain.model.WifiCapabilities
import com.inik.camcon.domain.model.WifiNetworkState
import com.inik.camcon.domain.model.toEndpoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PTP/IP 데이터소스.
 *
 * Phase 2 재배선:
 * - 연결 분기를 [ConnectionMethod]로 일급화 (AP / STA_ROUTER / STA_PHONE_HOTSPOT)
 * - STA 분기는 [CameraNative.initCameraWithPtpip] 단일 진입점 사용 — cpp 측 fallback
 *   `performNikonStaAuthentication`이 자동 실행되어 진짜 PTP/IP 인증 시퀀스가 트리거된다.
 * - false-success 제거: native 반환값이 음수(NotImplemented 포함)면 ERROR 상태로 즉시 전환.
 */
@Singleton
class PtpipDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val discoveryService: PtpipDiscoveryService,
    private val connectionManager: PtpipConnectionManager,
    private val nikonAuthService: NikonAuthenticationService,
    private val wifiHelper: WifiNetworkHelper,
) {
    private var connectedCamera: PtpipCamera? = null
    private var lastConnectedCamera: PtpipCamera? = null
    private var lastConnectionMethod: ConnectionMethod? = null
    private var isAutoReconnectEnabled = false
    private var networkMonitoringJob: Job? = null
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionState = MutableStateFlow(PtpipConnectionState.DISCONNECTED)
    val connectionState: StateFlow<PtpipConnectionState> = _connectionState.asStateFlow()

    private val _discoveredCameras = MutableStateFlow<List<PtpipCamera>>(emptyList())
    val discoveredCameras: StateFlow<List<PtpipCamera>> = _discoveredCameras.asStateFlow()

    private val _discoveredEndpoints = MutableStateFlow<List<CameraEndpoint>>(emptyList())
    val discoveredEndpoints: StateFlow<List<CameraEndpoint>> = _discoveredEndpoints.asStateFlow()

    private val _cameraInfo = MutableStateFlow<PtpipCameraInfo?>(null)
    val cameraInfo: StateFlow<PtpipCameraInfo?> = _cameraInfo.asStateFlow()

    private val _wifiNetworkState = MutableStateFlow(
        WifiNetworkState(
            isConnected = false,
            isConnectedToCameraAP = false,
            ssid = null,
            detectedCameraIP = null,
        )
    )
    val wifiNetworkState: StateFlow<WifiNetworkState> = _wifiNetworkState.asStateFlow()

    private val _activeConnectionMethod = MutableStateFlow<ConnectionMethod?>(null)
    val activeConnectionMethod: StateFlow<ConnectionMethod?> = _activeConnectionMethod.asStateFlow()

    companion object {
        private const val TAG = "PtpipDataSource"
        private const val RECONNECT_DELAY_MS = 3000L
        private const val NATIVE_ERR_NOT_IMPLEMENTED = -999
    }

    init {
        startNetworkMonitoring()
        runCatching { CameraNative.setLogLevel(CameraNative.GP_LOG_ALL) }
            .onFailure { Log.w(TAG, "libgphoto2 로그 레벨 설정 실패", it) }
    }

    private fun startNetworkMonitoring() {
        networkMonitoringJob = wifiHelper.networkStateFlow
            .onEach { state ->
                _wifiNetworkState.value = state
                if (isAutoReconnectEnabled) handleNetworkStateChange(state)
            }
            .launchIn(coroutineScope)
    }

    private fun handleNetworkStateChange(state: WifiNetworkState) {
        coroutineScope.launch {
            val current = _connectionState.value
            when {
                !state.isConnected && current == PtpipConnectionState.CONNECTED -> {
                    Log.i(TAG, "Wi-Fi 해제 — 연결 종료")
                    _connectionState.value = PtpipConnectionState.DISCONNECTED
                    connectedCamera = null
                }
                state.isConnected &&
                    lastConnectedCamera != null &&
                    current == PtpipConnectionState.DISCONNECTED -> {
                    Log.i(TAG, "Wi-Fi 재연결 — 자동 재접속 시도")
                    delay(RECONNECT_DELAY_MS)
                    val last = lastConnectedCamera ?: return@launch
                    val method = lastConnectionMethod ?: ConnectionMethod.STA_ROUTER
                    val target = if (state.isConnectedToCameraAP && state.detectedCameraIP != null) {
                        last.copy(ipAddress = state.detectedCameraIP)
                    } else last
                    attemptAutoReconnect(target, method)
                }
            }
        }
    }

    private suspend fun attemptAutoReconnect(camera: PtpipCamera, method: ConnectionMethod) {
        if (_connectionState.value == PtpipConnectionState.CONNECTING) return
        Log.i(TAG, "자동 재연결 시도 ${camera.name} (${method})")
        if (!connectToCamera(camera, method)) {
            if (isAutoReconnectEnabled) {
                delay(5000)
                if (_connectionState.value == PtpipConnectionState.ERROR && isAutoReconnectEnabled) {
                    attemptAutoReconnect(camera, method)
                }
            }
        }
    }

    fun setAutoReconnectEnabled(enabled: Boolean) {
        isAutoReconnectEnabled = enabled
    }

    fun cleanup() {
        networkMonitoringJob?.cancel()
        networkMonitoringJob = null
    }

    // ── 검색 ────────────────────────────────────────────────────────────────

    /**
     * 카메라 검색.
     *
     * @param method 사용자가 선택한 연결 방식. null이면 환경에서 추론.
     */
    suspend fun discoverCameras(method: ConnectionMethod? = null): List<PtpipCamera> {
        if (!wifiHelper.isWifiConnected() && !wifiHelper.isHotspotEnabled()) {
            Log.w(TAG, "Wi-Fi 미연결 + 핫스팟 비활성 — 검색 중단")
            _discoveredCameras.value = emptyList()
            _discoveredEndpoints.value = emptyList()
            return emptyList()
        }
        val endpoints = discoveryService.discoverCameras(method)
        _discoveredEndpoints.value = endpoints
        val cameras = endpoints.map { it.toPtpipCamera() }
        _discoveredCameras.value = cameras
        return cameras
    }

    /**
     * 사용자가 직접 입력한 IP/포트로 후보 추가.
     */
    fun addManualCamera(ipAddress: String, name: String, port: Int = 15740): PtpipCamera {
        val endpoint = CameraEndpoint(
            ipAddress = ipAddress,
            port = port,
            name = name.ifBlank { "Manual ($ipAddress)" },
            source = EndpointSource.MANUAL_INPUT,
        )
        _discoveredEndpoints.value = (_discoveredEndpoints.value + endpoint)
            .distinctBy { it.ipAddress }
        val camera = endpoint.toPtpipCamera()
        _discoveredCameras.value = (_discoveredCameras.value + camera)
            .distinctBy { it.ipAddress }
        return camera
    }

    // ── 연결 (3-way 분기) ──────────────────────────────────────────────────

    /**
     * 호환용 시그니처. method를 환경에서 추정.
     */
    suspend fun connectToCamera(camera: PtpipCamera): Boolean =
        connectToCamera(camera, inferMethod())

    suspend fun connectToCamera(
        camera: PtpipCamera,
        method: ConnectionMethod,
    ): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "연결 시작 method=$method camera=${camera.name}@${camera.ipAddress}")
        _connectionState.value = PtpipConnectionState.CONNECTING
        _activeConnectionMethod.value = method

        disconnect()

        if (!wifiHelper.isWifiConnected() && !wifiHelper.isHotspotEnabled()) {
            Log.e(TAG, "Wi-Fi/핫스팟 모두 비활성 — 중단")
            _connectionState.value = PtpipConnectionState.ERROR
            return@withContext false
        }

        val libDir = context.applicationInfo.nativeLibraryDir
        val nativeResult: String = try {
            when (method) {
                ConnectionMethod.AP ->
                    CameraNative.initCameraForAPMode(camera.ipAddress, camera.port, libDir)
                ConnectionMethod.STA_ROUTER,
                ConnectionMethod.STA_PHONE_HOTSPOT ->
                    CameraNative.initCameraWithPtpip(camera.ipAddress, camera.port, libDir)
            }
        } catch (e: Exception) {
            Log.e(TAG, "native 초기화 예외", e)
            _connectionState.value = PtpipConnectionState.ERROR
            return@withContext false
        }

        if (!nativeResult.isSuccess()) {
            Log.e(TAG, "native 초기화 실패: $nativeResult")
            _connectionState.value = PtpipConnectionState.ERROR
            return@withContext false
        }

        // 성공
        connectedCamera = camera
        lastConnectedCamera = camera
        lastConnectionMethod = method
        _connectionState.value = PtpipConnectionState.CONNECTED
        _cameraInfo.value = PtpipCameraInfo(
            manufacturer = "Unknown",
            model = camera.name,
            version = "n/a",
            serialNumber = camera.ipAddress,
        )

        startAutomaticFileReceiving(camera)
        return@withContext true
    }

    private fun String.isSuccess(): Boolean {
        if (equals("OK", ignoreCase = true)) return true
        if (equals("GP_OK", ignoreCase = true)) return true
        if (contains("success", ignoreCase = true)) return true
        if (equals("ERR_NOT_IMPLEMENTED")) return false
        return false
    }

    private fun inferMethod(): ConnectionMethod = when {
        wifiHelper.isHotspotEnabled() -> ConnectionMethod.STA_PHONE_HOTSPOT
        wifiHelper.isConnectedToCameraAP() -> ConnectionMethod.AP
        else -> ConnectionMethod.STA_ROUTER
    }

    /**
     * 레거시 코드 호환 — 진짜 PTP GetDeviceInfo가 아닌 환경 기반 추정.
     */
    @Deprecated("Use ConnectionMethod directly via connectToCamera(camera, method).")
    suspend fun detectNikonConnectionMode(camera: PtpipCamera): NikonConnectionMode =
        withContext(Dispatchers.IO) {
            when (inferMethod()) {
                ConnectionMethod.AP -> NikonConnectionMode.AP_MODE
                ConnectionMethod.STA_ROUTER,
                ConnectionMethod.STA_PHONE_HOTSPOT -> NikonConnectionMode.STA_MODE
            }
        }

    // ── 파일 수신 / 촬영 ───────────────────────────────────────────────────

    private fun startAutomaticFileReceiving(camera: PtpipCamera) {
        coroutineScope.launch {
            val listener = object : CameraCaptureListener {
                override fun onFlushComplete() = Unit
                override fun onPhotoCaptured(filePath: String, fileName: String) {
                    Log.i(TAG, "외부 촬영 파일 수신: $fileName")
                    handleAutomaticDownload(filePath, fileName)
                }
                override fun onCaptureFailed(errorCode: Int) {
                    Log.e(TAG, "외부 촬영 수신 실패 code=$errorCode")
                }
                override fun onUsbDisconnected() = Unit
            }
            runCatching { CameraNative.listenCameraEvents(listener) }
                .onFailure { Log.e(TAG, "파일 수신 리스너 시작 실패", it) }
        }
    }

    private fun handleAutomaticDownload(filePath: String, fileName: String) {
        coroutineScope.launch {
            val file = java.io.File(filePath)
            if (file.exists()) {
                Log.i(TAG, "다운로드 완료 $fileName (${file.length() / 1024}KB)")
            } else {
                Log.w(TAG, "다운로드 파일 누락 $filePath")
            }
        }
    }

    suspend fun capturePhoto(
        callback: CameraCaptureListener? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        if (connectedCamera == null) return@withContext false
        try {
            if (callback == null) {
                CameraNative.capturePhoto() >= 0
            } else {
                CameraNative.capturePhotoAsync(callback, "")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "촬영 중 오류", e)
            false
        }
    }

    private fun stopAutomaticFileReceiving() {
        runCatching { CameraNative.stopListenCameraEvents() }
            .onFailure { Log.w(TAG, "이벤트 리스너 중지 실패", it) }
    }

    suspend fun disconnect(keepSession: Boolean = false) = withContext(Dispatchers.IO) {
        try {
            stopAutomaticFileReceiving()
            discoveryService.stopDiscovery()
            if (!keepSession) {
                withContext(Dispatchers.Default) {
                    runCatching { CameraNative.closeCamera() }
                }
            }
            connectionManager.closeConnections(!keepSession)
            if (!keepSession) {
                connectedCamera = null
                lastConnectedCamera = null
                lastConnectionMethod = null
                _connectionState.value = PtpipConnectionState.DISCONNECTED
                _cameraInfo.value = null
                _activeConnectionMethod.value = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "연결 해제 중 오류", e)
        }
    }

    suspend fun disconnectForGphoto2(keepSession: Boolean = false) = withContext(Dispatchers.IO) {
        disconnect(keepSession)
    }

    suspend fun temporaryDisconnect(keepSession: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        val wasConnected = _connectionState.value == PtpipConnectionState.CONNECTED
        if (wasConnected) {
            disconnect(keepSession)
            true
        } else false
    }

    suspend fun reconnectAfterTemporary(camera: PtpipCamera): Boolean = withContext(Dispatchers.IO) {
        delay(2000)
        val method = lastConnectionMethod ?: inferMethod()
        connectToCamera(camera, method)
    }

    // ── 외부 노출 헬퍼 ─────────────────────────────────────────────────────

    fun isExternalAccessible(): Boolean = _connectionState.value == PtpipConnectionState.DISCONNECTED
    fun isWifiConnected(): Boolean = wifiHelper.isWifiConnected()
    fun isHotspotEnabled(): Boolean = wifiHelper.isHotspotEnabled()
    fun isStaConcurrencySupported(): Boolean = wifiHelper.isStaConcurrencySupported()
    fun getWifiCapabilities(): WifiCapabilities = wifiHelper.getWifiCapabilities()
    fun getCurrentWifiNetworkState(): WifiNetworkState = _wifiNetworkState.value
    fun getConnectionManager() = connectionManager
    fun getNikonAuthService() = nikonAuthService

    /** 마지막 연결된 카메라의 endpoint 표현 (자동 재접속 등의 용도). */
    fun lastConnected(): CameraEndpoint? = lastConnectedCamera?.toEndpoint(EndpointSource.SAVED)
}
