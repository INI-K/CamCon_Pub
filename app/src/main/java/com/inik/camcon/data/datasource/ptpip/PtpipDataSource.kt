package com.inik.camcon.data.datasource.ptpip

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContentValues
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.inik.camcon.CameraNative
import com.inik.camcon.EventListenerStopCallback
import com.inik.camcon.data.datasource.nativesource.CameraCaptureListener
import com.inik.camcon.data.network.ptpip.authentication.NikonAuthenticationService
import com.inik.camcon.data.network.ptpip.connection.PtpipConnectionManager
import com.inik.camcon.data.network.ptpip.discovery.PtpipDiscoveryService
import com.inik.camcon.data.network.ptpip.wifi.WifiNetworkHelper
import com.inik.camcon.data.repository.managers.CameraEventManager
import com.inik.camcon.data.repository.managers.PhotoDownloadManager
import com.inik.camcon.data.service.AutoConnectManager
import com.inik.camcon.data.service.AutoConnectTaskRunner
import com.inik.camcon.domain.model.CameraAbilitiesInfo
import com.inik.camcon.domain.model.CameraSupports
import com.inik.camcon.domain.model.NikonConnectionMode
import com.inik.camcon.domain.model.PtpDeviceInfo
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.PtpipCameraInfo
import com.inik.camcon.domain.model.PtpipConnectionState
import com.inik.camcon.domain.model.WifiCapabilities
import com.inik.camcon.domain.model.WifiNetworkState
import com.inik.camcon.di.ApplicationScope
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PTPIP (Picture Transfer Protocol over IP) 데이터 소스
 * 
 * 리팩토링된 버전 - 각 기능을 별도 서비스로 분리하여 단일 책임 원칙 준수
 * - PtpipDiscoveryService: mDNS 카메라 검색
 * - PtpipConnectionManager: 연결 관리 
 * - NikonAuthenticationService: 니콘 STA 인증
 * - WifiNetworkHelper: Wi-Fi 네트워크 관리
 */
@Singleton
class PtpipDataSource @Inject constructor(
    private val context: Context,
    private val discoveryService: PtpipDiscoveryService,
    private val connectionManager: PtpipConnectionManager,
    private val nikonAuthService: NikonAuthenticationService,
    private val wifiHelper: WifiNetworkHelper,
    private val cameraEventManager: CameraEventManager,
    private val cameraStateObserver: com.inik.camcon.domain.manager.CameraStateObserver,
    private val photoDownloadManager: com.inik.camcon.data.repository.managers.PhotoDownloadManager,
    private val autoConnectManager: AutoConnectManager,
    private val autoConnectTaskRunnerProvider: Lazy<AutoConnectTaskRunner>,
    @ApplicationScope private val coroutineScope: CoroutineScope
) {
    @Volatile private var connectedCamera: PtpipCamera? = null
    @Volatile private var lastConnectedCamera: PtpipCamera? = null
    @Volatile private var isAutoReconnectEnabled = false
    private var networkMonitoringJob: Job? = null
    // 초기 플러시 완료 플래그
    @Volatile private var isInitialFlushCompleted = false
    private val reconnectMutex = Mutex()

    // Repository 콜백 저장용 - Singleton이므로 일반 참조 사용 (메모리 누수 방지 불필요)
    private var onPhotoCapturedCallback: ((String, String) -> Unit)? = null
    private var onPhotoDownloadedCallback: ((String, String, ByteArray) -> Unit)? = null
    private var onConnectionLostCallback: (() -> Unit)? = null // Wi-Fi 연결 끊어짐 알림용

    // StateFlow for UI observation
    private val _connectionState = MutableStateFlow(PtpipConnectionState.DISCONNECTED)
    val connectionState: StateFlow<PtpipConnectionState> = _connectionState.asStateFlow()

    // 추가: 연결 진행 메시지 상태 추가
    private val _connectionProgressMessage = MutableStateFlow("")
    val connectionProgressMessage: StateFlow<String> = _connectionProgressMessage.asStateFlow()

    // Wi-Fi 연결 끊어짐 알림 상태 추가
    private val _connectionLostMessage = MutableStateFlow<String?>(null)
    val connectionLostMessage: StateFlow<String?> = _connectionLostMessage.asStateFlow()

    private val _discoveredCameras = MutableStateFlow<List<PtpipCamera>>(emptyList())
    val discoveredCameras: StateFlow<List<PtpipCamera>> = _discoveredCameras.asStateFlow()

    private val _cameraInfo = MutableStateFlow<PtpipCameraInfo?>(null)
    val cameraInfo: StateFlow<PtpipCameraInfo?> = _cameraInfo.asStateFlow()

    private val _wifiNetworkState = MutableStateFlow(WifiNetworkState(false, false, null, null))
    val wifiNetworkState: StateFlow<WifiNetworkState> = _wifiNetworkState.asStateFlow()

    // AP 모드 강제 여부 (탭 선택 등으로 AP 모드를 명시적으로 사용하는 경우)
    private val _isApModeForced = MutableStateFlow(false)
    val isApModeForced: StateFlow<Boolean> = _isApModeForced.asStateFlow()

    private var lastAutoConnectBroadcastSsid: String? = null
    private var lastAutoConnectBroadcastBssid: String? = null

    companion object {
        private const val TAG = "PtpipDataSource"
        private const val RECONNECT_DELAY_MS = 3000L
        private const val DUP_WINDOW_MS = 1500L
        const val ACTION_PHOTO_SAVED = "com.inik.camcon.action.PHOTO_SAVED"
        const val EXTRA_URI = "uri"
        const val EXTRA_FILE_NAME = "fileName"
    }

    /**
     * 라이브러리가 로드되었는지 확인합니다.
     * 라이브러리는 이제 init 블록에서 자동으로 로드되므로 확인만 수행합니다.
     */
    private fun ensureLibrariesLoaded() {
        if (!CameraNative.isLibrariesLoaded()) {
            Log.e(TAG, "네이티브 라이브러리가 로딩되지 않았습니다. 앱을 재시작해주세요.")
            throw IllegalStateException("네이티브 라이브러리가 로딩되지 않았습니다. 앱을 재시작해주세요.")
        }
    }

    init {
        startNetworkMonitoring()
        registerAutoConnectReceiver()

        // libgphoto2 디버그 로그 활성화
        try {
            // GP_LOG_ALL = 3 (모든 로그 레벨 활성화)
            CameraNative.setLogLevel(0)
            Log.d(TAG, "libgphoto2 로그 레벨을 GP_LOG_ALL로 설정 완료")
        } catch (e: Exception) {
            Log.e(TAG, "libgphoto2 로그 레벨 설정 실패", e)

            // 대체 방법으로 개별 로그 활성화
            try {
                CameraNative.enableDebugLogging(true)
                CameraNative.enableVerboseLogging(true)
                Log.d(TAG, "libgphoto2 개별 로그 활성화 완료")
            } catch (e2: Exception) {
                Log.e(TAG, "libgphoto2 개별 로그 활성화 실패", e2)
            }
        }
    }

    /**
     * 네트워크 상태 모니터링 시작
     */
    private fun startNetworkMonitoring() {
        networkMonitoringJob = wifiHelper.networkStateFlow
            .onEach { networkState ->
                Log.d(TAG, "네트워크 상태 변화: $networkState")
                _wifiNetworkState.value = networkState
                maybeTriggerAutoConnect(networkState)
                
                if (isAutoReconnectEnabled) {
                    handleNetworkStateChange(networkState)
                }
            }
            .launchIn(coroutineScope)
    }

    /**
     * 네트워크 상태 변화 처리
     */
    private fun handleNetworkStateChange(networkState: WifiNetworkState) {
        coroutineScope.launch(Dispatchers.IO) {
            val currentState = _connectionState.value
            
            when {
                // Wi-Fi 연결 해제됨
                !networkState.isConnected -> {
                    if (currentState == PtpipConnectionState.CONNECTED) {
                        Log.i(TAG, "Wi-Fi 연결 해제됨 - 카메라 연결 해제")
                        _connectionState.value = PtpipConnectionState.DISCONNECTED
                        connectedCamera = null
                        _connectionLostMessage.value = "Wi-Fi 연결이 끊어졌습니다. 다시 연결해 주세요."
                        onConnectionLostCallback?.invoke()
                    }
                }

                // Wi-Fi 연결되고 자동 재연결이 활성화되어 있으며 이전에 연결된 카메라가 있는 경우
                networkState.isConnected && isAutoReconnectEnabled &&
                        lastConnectedCamera != null && currentState == PtpipConnectionState.DISCONNECTED -> {
                    Log.i(TAG, "Wi-Fi 연결됨 - 이전 카메라 자동 재연결 시도")
                    delay(RECONNECT_DELAY_MS)

                    // 연결 해제 상태에서만 재연결 시도
                    if (_connectionState.value == PtpipConnectionState.DISCONNECTED) {
                        // race condition 방지를 위해 로컬 변수로 저장
                        val lastCamera = lastConnectedCamera
                        if (lastCamera != null) {
                            // AP 모드에서 카메라 IP 업데이트
                            val cameraToConnect =
                                if (networkState.isConnectedToCameraAP && networkState.detectedCameraIP != null) {
                                    lastCamera.copy(ipAddress = networkState.detectedCameraIP)
                                } else {
                                    lastCamera
                                }

                            attemptAutoReconnect(cameraToConnect)
                        } else {
                            Log.w(TAG, "자동 재연결 중 lastConnectedCamera가 null로 변경됨")
                        }
                    }
                }

                // 이미 연결된 상태에서 카메라 IP가 변경된 경우 (AP 모드에서)
                networkState.isConnected && networkState.isConnectedToCameraAP && 
                networkState.detectedCameraIP != null &&
                        connectedCamera?.ipAddress != networkState.detectedCameraIP &&
                        currentState == PtpipConnectionState.CONNECTED -> {
                    Log.i(TAG, "AP 모드에서 카메라 IP 변경 감지 - 재연결 시도")
                    val currentCamera = connectedCamera
                    if (currentCamera != null) {
                        val updatedCamera =
                            currentCamera.copy(ipAddress = networkState.detectedCameraIP)
                        if (isAutoReconnectEnabled) {
                            attemptAutoReconnect(updatedCamera)
                        }
                    }
                }
            }
        }
    }

    private fun maybeTriggerAutoConnect(networkState: WifiNetworkState) {
        if (!networkState.isConnected) {
            resetAutoConnectBroadcastState()
            return
        }

        if (networkState.ssid.isNullOrBlank()) {
            resetAutoConnectBroadcastState()
            return
        }

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val isAutoConnectEnabled = autoConnectManager.isEnabled()
                if (!isAutoConnectEnabled) {
                    Log.d(TAG, "자동 연결 비활성화 상태 - 브로드캐스트 건너뜀")
                    resetAutoConnectBroadcastState()
                    return@launch
                }

                val storedConfig = autoConnectManager.getStoredConfig()
                if (storedConfig == null) {
                    Log.d(TAG, "자동 연결 설정이 없어 브로드캐스트 건너뜀")
                    resetAutoConnectBroadcastState()
                    return@launch
                }

                val ssidMatches = networkState.ssid.equals(storedConfig.ssid, ignoreCase = true)
                val currentBssid = wifiHelper.getCurrentBssid()
                val bssidMatches = storedConfig.bssid.isNullOrBlank() ||
                        currentBssid.equals(storedConfig.bssid, ignoreCase = true)

                if (!ssidMatches || !bssidMatches) {
                    Log.d(TAG, "자동 연결 대상 SSID/BSSID 불일치 - 브로드캐스트 생략")
                    resetAutoConnectBroadcastState()
                    return@launch
                }

                val alreadySentForSsid = lastAutoConnectBroadcastSsid.equals(
                    networkState.ssid,
                    ignoreCase = true
                )
                val alreadySentForBssid = when {
                    currentBssid == null -> storedConfig.bssid.isNullOrBlank() &&
                            lastAutoConnectBroadcastBssid.isNullOrBlank()

                    else -> currentBssid.equals(lastAutoConnectBroadcastBssid, ignoreCase = true)
                }

                if (alreadySentForSsid && alreadySentForBssid) {
                    Log.d(TAG, "같은 SSID/BSSID에 대해 이미 자동 연결 브로드캐스트 발송됨")
                    return@launch
                }

                Log.i(TAG, "네트워크 상태 감지 기반 자동 연결 브로드캐스트 발송: ${networkState.ssid}")
                wifiHelper.sendAutoConnectBroadcast(storedConfig.ssid)
                lastAutoConnectBroadcastSsid = networkState.ssid
                lastAutoConnectBroadcastBssid = currentBssid ?: storedConfig.bssid
            } catch (error: Exception) {
                Log.e(TAG, "자동 연결 브로드캐스트 발송 중 오류", error)
            }
        }
    }

    private fun resetAutoConnectBroadcastState() {
        if (lastAutoConnectBroadcastSsid != null || lastAutoConnectBroadcastBssid != null) {
            Log.d(TAG, "자동 연결 브로드캐스트 상태 초기화")
        }
        lastAutoConnectBroadcastSsid = null
        lastAutoConnectBroadcastBssid = null
    }

    /**
     * 자동 재연결 시도 (최대 횟수 제한 루프)
     */
    private suspend fun attemptAutoReconnect(camera: PtpipCamera) {
        // 동시 재연결 방지
        if (!reconnectMutex.tryLock()) {
            Log.d(TAG, "이미 재연결 시도 중이므로 무시")
            return
        }
        try {
            // 이미 연결 시도 중이면 무시
            if (_connectionState.value == PtpipConnectionState.CONNECTING) {
                Log.d(TAG, "이미 연결 시도 중이므로 자동 재연결 무시")
                return
            }

            val maxAttempts = 5
            var attempts = 0
            while (attempts < maxAttempts) {
                try {
                    Log.i(TAG, "자동 재연결 시도 ${attempts + 1}/$maxAttempts: ${camera.name} (${camera.ipAddress})")
                    _connectionState.value = PtpipConnectionState.CONNECTING
                    _connectionProgressMessage.value = "카메라에 연결 중..."

                    if (connectToCamera(camera)) {
                        Log.i(TAG, "자동 재연결 성공")
                        return
                    }

                    Log.w(TAG, "자동 재연결 실패 (시도 ${attempts + 1}/$maxAttempts)")
                    _connectionState.value = PtpipConnectionState.ERROR
                    _connectionProgressMessage.value = ""
                } catch (e: Exception) {
                    Log.w(TAG, "Reconnect attempt ${attempts + 1}/$maxAttempts failed", e)
                    _connectionState.value = PtpipConnectionState.ERROR
                    _connectionProgressMessage.value = ""
                }

                attempts++

                // 마지막 시도가 아니고 자동 재연결이 여전히 활성화되어 있으면 대기
                if (attempts < maxAttempts && isAutoReconnectEnabled) {
                    delay(5000)
                    // 자동 재연결이 비활성화되었거나 이미 연결되었으면 중단
                    if (!isAutoReconnectEnabled || _connectionState.value == PtpipConnectionState.CONNECTED) {
                        return
                    }
                }
            }
            Log.e(TAG, "Auto-reconnect failed after $maxAttempts attempts")
        } finally {
            reconnectMutex.unlock()
        }
    }

    /**
     * 자동 재연결 활성화/비활성화
     */
    fun setAutoReconnectEnabled(enabled: Boolean) {
        isAutoReconnectEnabled = enabled
        Log.d(TAG, "자동 재연결 ${if (enabled) "활성화" else "비활성화"}")
    }

    /**
     * 리소스 정리
     */
    fun cleanup() {
        networkMonitoringJob?.cancel()
        networkMonitoringJob = null

        // 카메라 연결 해제
        coroutineScope.launch(Dispatchers.IO) {
            try {
                disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "cleanup 중 disconnect 실패: ${e.message}")
            }
        }

        // 콜백 초기화
        onPhotoCapturedCallback = null
        onPhotoDownloadedCallback = null
        onConnectionLostCallback = null

        if (autoConnectReceiverRegistered) {
            try {
                context.unregisterReceiver(autoConnectReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "BroadcastReceiver 해제 실패: ${e.message}")
            }
            autoConnectReceiverRegistered = false
        }

        Log.d(TAG, "리소스 정리 완료 (콜백 포함)")
    }

    /**
     * mDNS를 사용하여 PTPIP 지원 카메라 검색
     */
    suspend fun discoverCameras(forceApMode: Boolean): List<PtpipCamera> {
        return try {
            Log.d(TAG, "카메라 검색 시작")

            // 연결 시도와 검색이 겹치지 않도록 직렬화: 연결 중이면 기존 목록 유지 반환
            if (_connectionState.value == PtpipConnectionState.CONNECTING) {
                Log.d(TAG, "연결 진행 중 - 검색 건너뜀 (직렬화 보호)")
                return _discoveredCameras.value
            }

            // Wi-Fi 연결 상태 확인
            if (!wifiHelper.isWifiConnected()) {
                Log.w(TAG, "Wi-Fi 네트워크에 연결되어 있지 않음")
                return emptyList()
            }

            // AP모드인지 확인하고 직접 IP 사용
            if (wifiHelper.isConnectedToCameraAP()) {
                Log.d(TAG, "AP모드 감지: libgphoto2 기반 카메라 IP 검색 시작")
                val cameraIP = wifiHelper.findAvailableCameraIP()
                if (cameraIP != null) {
                    Log.i(TAG, "✅ AP모드: libgphoto2로 검증된 카메라 IP $cameraIP 발견")
                    val networkName = wifiHelper.getCurrentSSID() ?: "카메라 AP"
                    val apCamera = PtpipCamera(
                        ipAddress = cameraIP,
                        port = 15740, // 표준 PTP/IP 포트
                        name = "$networkName (AP모드)",
                        isOnline = true
                    )
                    _discoveredCameras.value = listOf(apCamera)
                    return listOf(apCamera)
                } else {
                    Log.w(TAG, "❌ AP모드이지만 libgphoto2로 연결 가능한 카메라 IP를 찾을 수 없음")
                    // 빈 리스트 반환하여 사용자에게 상황을 알림
                    _discoveredCameras.value = emptyList()
                    return emptyList()
                }
            }

            // STA모드에서는 mDNS 검색 사용
            Log.d(TAG, "STA모드 또는 일반 네트워크: mDNS 검색 시작")
            val cameras = discoveryService.discoverCameras(forceApMode)
            _discoveredCameras.value = cameras
            cameras
        } catch (e: Exception) {
            Log.e(TAG, "카메라 검색 중 오류", e)
            emptyList()
        }
    }

    // 호환성용 무파라미터 래퍼
    suspend fun discoverCameras(): List<PtpipCamera> = discoverCameras(false)

    /**
     * 니콘 카메라 연결 모드 감지 (AP/STA/UNKNOWN)
     */
    suspend fun detectNikonConnectionMode(camera: PtpipCamera): NikonConnectionMode = 
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "니콘 카메라 연결 모드 감지 시작: ${camera.name}")

                // 기본 연결 시도 - AP 모드는 즉시 연결 가능
                if (connectionManager.establishConnection(camera)) {
                    val deviceInfo = connectionManager.getDeviceInfo()
                    connectionManager.closeConnections()
                    
                    if (deviceInfo?.manufacturer?.contains("Nikon", ignoreCase = true) == true) {
                        Log.d(TAG, "AP 모드 감지 (즉시 연결 성공)")
                        return@withContext NikonConnectionMode.AP_MODE
                    }
                }

                // AP 모드 실패 시 STA 모드로 판단
                Log.d(TAG, "STA 모드 감지 (기본 연결 실패)")
                return@withContext NikonConnectionMode.STA_MODE

            } catch (e: Exception) {
                Log.e(TAG, "니콘 카메라 모드 감지 중 오류", e)
                return@withContext NikonConnectionMode.UNKNOWN
            }
        }

    /**
     * 스마트 카메라 연결 (libgphoto2 API 기반)
     * Activity 생명주기와 독립적으로 실행됩니다
     */
    suspend fun connectToCamera(camera: PtpipCamera, forceApMode: Boolean = false): Boolean =
        // Activity와 독립적인 coroutineScope 사용
        coroutineScope.async(Dispatchers.IO) {
        try {
            Log.i(TAG, "============================================")
            Log.i(TAG, "=== 스마트 카메라 연결 시작 (독립 스코프) ===")
            Log.i(TAG, "카메라: ${camera.name}")
            Log.i(TAG, "IP: ${camera.ipAddress}:${camera.port}")
            Log.i(TAG, "============================================")

            // 연결 시작 시 상태를 CONNECTING으로 변경
            _connectionState.value = PtpipConnectionState.CONNECTING
            _connectionProgressMessage.value = "카메라 연결 시작..."
            Log.d(TAG, "PTPIP 연결 상태 변경: CONNECTING")

            // AP 모드 강제 플래그 설정
            _isApModeForced.value = forceApMode

            // 라이브러리가 로드되지 않은 경우 로드
            ensureLibrariesLoaded()

            // 이전 연결 정리
            if (_connectionState.value == PtpipConnectionState.CONNECTED &&
                connectedCamera != null && connectedCamera != camera
            ) {
                Log.d(TAG, "다른 카메라 연결됨 - 기존 연결 해제")
                disconnect()
            }

            // Wi-Fi 연결 확인 생략 (네트워크 바인딩 상태에서는 불필요)
            Log.d(TAG, "네트워크 바인딩 상태에서 연결 시도")

            // =========================
            // Step 0: Nikon 카메라 사전 인증 (STA 모드)
            // =========================
            // mDNS 서비스 이름으로 Nikon 카메라 감지
            // Z_6_... , D_850_... , Nikon_Z8 등의 형식
            val isNikonCamera = camera.name.contains("Nikon", ignoreCase = true) ||
                    camera.name.startsWith("Z_", ignoreCase = true) ||
                    camera.name.startsWith("D_", ignoreCase = true)

            if (isNikonCamera && !forceApMode) {
                Log.i(TAG, "=== Nikon 카메라 감지 (${camera.name}) - STA 인증 시도 ===")
                _connectionProgressMessage.value = "Nikon 카메라 인증 중..."

                // STA 인증 시도
                if (!nikonAuthService.performStaAuthentication(camera)) {
                    Log.w(TAG, "⚠️ Nikon STA 인증 실패 - AP 모드로 계속 진행")
                    // 인증 실패해도 계속 진행 (AP 모드일 수 있음)
                } else {
                    Log.i(TAG, "✅ Nikon STA 인증 성공 - 카메라에서 '연결 허용' 승인됨")
                }
            }

            // =========================
            // Step 1: libgphoto2로 연결
            // =========================
            // 플러그인 디렉토리 사용 (USB와 동일한 방식 - 버전 디렉토리까지 포함)
            val gphoto2BaseDir = context.getDir("gphoto2_plugins", Context.MODE_PRIVATE)
            val gphoto2VersionDir = java.io.File(gphoto2BaseDir, "libgphoto2/2.5.33.1")
            val portVersionDir = java.io.File(gphoto2BaseDir, "libgphoto2_port/0.12.2")

            // 버전 디렉토리를 콜론(:)으로 구분하여 전달
            val pluginDir = "${portVersionDir.absolutePath}:${gphoto2VersionDir.absolutePath}"
            Log.i(TAG, "플러그인 디렉토리: $pluginDir")

            _connectionProgressMessage.value = "카메라 연결 중..."

            // 환경 변수 설정 (중요!)
            Log.i(TAG, "=== libgphoto2 환경 변수 설정 ===")
            try {
                val envSetupResult = CameraNative.setupEnvironmentPaths(pluginDir)
                if (envSetupResult) {
                    Log.i(TAG, "✅ 환경 변수 설정 완료")
                } else {
                    Log.w(TAG, "⚠️ 환경 변수 설정 실패 (계속 진행)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ 환경 변수 설정 중 오류 (계속 진행): ${e.message}")
            }

            val initResult = if (forceApMode) {
                Log.i(TAG, "=== AP 모드 강제: libgphoto2 초기화 ===")
                CameraNative.initCameraForAPMode(camera.ipAddress, camera.port, pluginDir)
            } else {
                Log.i(TAG, "=== 표준 모드: libgphoto2 초기화 ===")
                CameraNative.initCameraWithPtpip(camera.ipAddress, camera.port, pluginDir)
            }

            val initOk = (initResult == "OK" || initResult == "GP_OK" ||
                    initResult?.contains("Success", ignoreCase = true) == true)

            if (!initOk) {
                Log.e(TAG, "❌ libgphoto2 초기화 실패: $initResult")
                _connectionState.value = PtpipConnectionState.ERROR
                _connectionProgressMessage.value = "연결 실패: $initResult"
                return@async false
            }

            Log.i(TAG, "✅ libgphoto2 초기화 성공")

            // =========================
            // Step 2: 카메라 기능 조회 
            // =========================
            _connectionProgressMessage.value = "카메라 정보 확인 중..."

            val abilitiesJson = try {
                CameraNative.getCameraAbilities()
            } catch (e: Exception) {
                Log.e(TAG, "Abilities 조회 중 오류", e)
                null
            }

            val deviceInfoJson = try {
                CameraNative.getCameraDeviceInfo()
            } catch (e: Exception) {
                Log.e(TAG, "DeviceInfo 조회 중 오류", e)
                null
            }

            if (abilitiesJson == null || deviceInfoJson == null) {
                Log.w(TAG, "⚠️ 카메라 정보 조회 실패 (하지만 연결은 성공)")
                // 정보 없어도 계속 진행
            } else {
                val abilities = parseAbilities(abilitiesJson)
                val deviceInfo = parseDeviceInfo(deviceInfoJson)

                Log.i(TAG, "📸 연결된 카메라 정보:")
                Log.i(TAG, "   제조사: ${deviceInfo.manufacturer}")
                Log.i(TAG, "   모델: ${deviceInfo.model}")
                Log.i(TAG, "   드라이버: ${abilities.status}")
                Log.i(TAG, "   지원 기능:")
                Log.i(TAG, "     - 원격 촬영: ${abilities.supports.captureImage}")
                Log.i(TAG, "     - 라이브뷰: ${abilities.supports.capturePreview}")
                Log.i(TAG, "     - 설정 변경: ${abilities.supports.config}")
                Log.i(TAG, "     - 트리거: ${abilities.supports.triggerCapture}")

                // 상태 저장
                storeAbilities(abilities, deviceInfo)

                // DeviceInfo에서 제조사 정보 로그 출력 (검증용)
                Log.i(TAG, "=== 제조사: ${deviceInfo.manufacturer} (DeviceInfo 확인) ===")

                // 기능 제한 경고
                if (abilities.supports.isDownloadOnly()) {
                    Log.w(TAG, "⚠️ 이 카메라는 다운로드만 지원합니다")
                    Log.w(TAG, "   원격 촬영 및 라이브뷰는 지원되지 않습니다")
                    _connectionProgressMessage.value = "연결 완료 (기능 제한)"
                }
            }

            // =========================
            // Step 4: 이벤트 리스너 시작
            // =========================
            _connectionProgressMessage.value = "이벤트 리스너 시작 중..."
            startAutomaticFileReceiving(camera)

            // =========================
            // Step 5: 연결 정보 저장 (상태는 플러시 완료 후 변경)
            // =========================
            connectedCamera = camera
            lastConnectedCamera = camera

            Log.i(TAG, "🎉 카메라 연결 설정 완료! 초기 플러시 대기 중...")
            return@async true

        } catch (e: Exception) {
            Log.e(TAG, "카메라 연결 중 오류", e)
            _connectionState.value = PtpipConnectionState.ERROR
            _connectionProgressMessage.value = "연결 오류: ${e.message}"
            return@async false
        }
        }.await() // async의 결과를 기다림

    /**
     * Abilities JSON 파싱
     */
    private fun parseAbilities(json: String): CameraAbilitiesInfo {
        try {
            val obj = org.json.JSONObject(json)
            val supportsObj = obj.getJSONObject("supports")

            return CameraAbilitiesInfo(
                model = obj.getString("model"),
                status = obj.getString("status"),
                portType = obj.getInt("port_type"),
                usbVendor = obj.getString("usb_vendor"),
                usbProduct = obj.getString("usb_product"),
                usbClass = obj.getInt("usb_class"),
                operations = obj.getInt("operations"),
                fileOperations = obj.getInt("file_operations"),
                folderOperations = obj.getInt("folder_operations"),
                supports = CameraSupports(
                    captureImage = supportsObj.getBoolean("capture_image"),
                    captureVideo = supportsObj.getBoolean("capture_video"),
                    captureAudio = supportsObj.getBoolean("capture_audio"),
                    capturePreview = supportsObj.getBoolean("capture_preview"),
                    triggerCapture = supportsObj.getBoolean("trigger_capture"),
                    config = supportsObj.getBoolean("config"),
                    delete = supportsObj.getBoolean("delete"),
                    preview = supportsObj.getBoolean("preview"),
                    raw = supportsObj.getBoolean("raw"),
                    audio = supportsObj.getBoolean("audio"),
                    exif = supportsObj.getBoolean("exif"),
                    deleteAll = supportsObj.getBoolean("delete_all"),
                    putFile = supportsObj.getBoolean("put_file"),
                    makeDir = supportsObj.getBoolean("make_dir"),
                    removeDir = supportsObj.getBoolean("remove_dir")
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Abilities 파싱 실패", e)
            throw e
        }
    }

    /**
     * DeviceInfo JSON 파싱
     */
    private fun parseDeviceInfo(json: String): PtpDeviceInfo {
        try {
            val obj = org.json.JSONObject(json)
            return PtpDeviceInfo(
                manufacturer = obj.getString("manufacturer"),
                model = obj.getString("model"),
                version = obj.getString("version"),
                serialNumber = obj.getString("serial_number")
            )
        } catch (e: Exception) {
            Log.e(TAG, "DeviceInfo 파싱 실패", e)
            throw e
        }
    }

    /**
     * Nikon 연결 모드 감지 (간소화)
     */
    private suspend fun detectNikonMode(camera: PtpipCamera): NikonConnectionMode {
        // PtpipConnectionManager를 통한 빠른 연결 테스트
        if (connectionManager.establishConnection(camera)) {
            val deviceInfo = connectionManager.getDeviceInfo()
            connectionManager.closeConnections()
            delay(300)

            if (deviceInfo != null) {
                // 즉시 연결 성공 = AP 모드
                return NikonConnectionMode.AP_MODE
            }
        }

        // 연결 실패 = STA 모드 (인증 필요)
        return NikonConnectionMode.STA_MODE
    }

    /**
     * Abilities 및 DeviceInfo 저장
     */
    private var currentAbilities: CameraAbilitiesInfo? = null
    private var currentDeviceInfo: PtpDeviceInfo? = null

    private fun storeAbilities(abilities: CameraAbilitiesInfo, deviceInfo: PtpDeviceInfo) {
        currentAbilities = abilities
        currentDeviceInfo = deviceInfo

        // 기존 PtpipCameraInfo도 업데이트
        _cameraInfo.value = PtpipCameraInfo(
            manufacturer = deviceInfo.manufacturer,
            model = deviceInfo.model,
            version = deviceInfo.version,
            serialNumber = deviceInfo.serialNumber
        )

        // UI 상태 업데이트 (PTPIP도 동일하게)
        try {
            cameraStateObserver.updateCameraAbilities(abilities)
            Log.i(TAG, "✅ PTPIP 연결 - UI 상태 업데이트 완료")
        } catch (e: Exception) {
            Log.e(TAG, "UI 상태 업데이트 실패", e)
        }
    }

    /**
     * 현재 카메라 Abilities 조회
     */
    fun getCurrentAbilities(): CameraAbilitiesInfo? = currentAbilities

    /**
     * 현재 카메라 DeviceInfo 조회
     */
    fun getCurrentDeviceInfo(): PtpDeviceInfo? = currentDeviceInfo

    /**
     * 특정 기능 지원 여부 빠른 확인
     */
    fun supportsOperation(operation: String): Boolean {
        return try {
            CameraNative.supportsOperation(operation)
        } catch (e: Exception) {
            Log.e(TAG, "기능 지원 확인 실패: $operation", e)
            false
        }
    }

    // ... existing code ...

    /**
     * AP 모드 연결 성공 시 이벤트 리스너 시작 (CameraEventManager 활용)
     */
    private suspend fun startAutomaticFileReceiving(camera: PtpipCamera) {
        Log.i(TAG, "PTPIP AP 모드 이벤트 리스너 시작: ${camera.name}")
        com.inik.camcon.utils.LogcatManager.d(TAG, "=== PTPIP 자동 파일 수신 시작 ===")
        com.inik.camcon.utils.LogcatManager.d(TAG, "카메라: ${camera.name}")
        com.inik.camcon.utils.LogcatManager.d(
            TAG,
            "Repository 콜백 설정 상태: ${onPhotoCapturedCallback != null}"
        )

        // 초기 플러시 플래그 초기화
        isInitialFlushCompleted = false

        try {
            // 파일 목록 조회 생략 - 사진이 많으면 스캔 중 연결 끊김 방지
            Log.i(TAG, "=== PTPIP 연결 후 파일 목록 조회 생략 (성능 최적화) ===")
            com.inik.camcon.utils.LogcatManager.d(TAG, "⚡ 파일 목록 조회 건너뜀 - 이벤트 리스너만 시작")

            // 이벤트 리스너 시작
            _connectionProgressMessage.value = "이벤트 리스너 시작 중..."
            com.inik.camcon.utils.LogcatManager.d(TAG, "🎧 CameraEventManager를 통한 이벤트 리스너 시작")

            // CameraEventManager를 통해 PTPIP 이벤트 리스너 시작
            val result = cameraEventManager.startCameraEventListener(
                isConnected = true,
                isInitializing = false,
                saveDirectory = photoDownloadManager.getSaveDirectory(),
                onPhotoCaptured = { filePath, fileName ->
                    com.inik.camcon.utils.LogcatManager.d(
                        TAG,
                        "🎯 PTPIP onPhotoCaptured 콜백 호출됨: $fileName"
                    )
                    // Repository 콜백 호출 제거
                    com.inik.camcon.utils.LogcatManager.d(TAG, "📋 파일 감지 알림만 처리: $fileName")

                    // 네이티브 처리 완료 알림만 로그로 기록
                    handleAutomaticDownload(filePath, fileName)
                },
                onPhotoDownloaded = { filePath, fileName, imageData ->
                    com.inik.camcon.utils.LogcatManager.d(
                        TAG,
                        "✅ 네이티브에서 완전한 다운로드 및 저장 처리 완료: $fileName"
                    )
                    // com.inik.camcon.utils.LogcatManager.d(TAG, "📁 카메라 내부 경로: $filePath")

                    // 백그라운드 알림 업데이트 - 파일 전송 중
                    try {
                        com.inik.camcon.data.service.AutoConnectForegroundService.updateNotification(
                            context,
                            "파일 전송 중",
                            "저장 중: $fileName"
                        )
                    } catch (e: Exception) {
                        // 알림 업데이트 실패해도 무시 (파일 처리는 계속)
                    }

                    // ByteArray를 MediaStore에 저장하고 실제 안드로이드 경로 얻기
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val savedPhoto = photoDownloadManager.handleNativePhotoDownload(
                                filePath = filePath,
                                fileName = fileName,
                                imageData = imageData,
                                cameraCapabilities = null, // 카메라 정보는 옵션
                                cameraSettings = null
                            )

                            if (savedPhoto != null) {
                                val realPath = savedPhoto.filePath
                                com.inik.camcon.utils.LogcatManager.d(
                                    TAG,
                                    "📁 실제 저장된 파일 경로: $realPath"
                                )

                                // 파일 저장 완료 후 알림 업데이트
                                try {
                                    com.inik.camcon.data.service.AutoConnectForegroundService.updateNotification(
                                        context,
                                        "카메라 연결 완료",
                                        "이벤트 리스너가 준비되었습니다"
                                    )
                                } catch (e: Exception) {
                                    // 알림 업데이트 실패해도 무시
                                }

                                // Repository 콜백 호출 (안드로이드 저장소 경로)
                                onPhotoDownloadedCallback?.invoke(realPath, fileName, imageData)
                            } else {
                                com.inik.camcon.utils.LogcatManager.e(
                                    TAG,
                                    "❌ MediaStore 저장 실패: $fileName"
                                )
                            }
                        } catch (e: Exception) {
                            com.inik.camcon.utils.LogcatManager.e(
                                TAG,
                                "MediaStore 저장 중 오류: ${e.message}",
                                e
                            )
                        }
                    }
                },
                onFlushComplete = {
                    Log.d(TAG, "PTPIP AP 모드 플러시 완료")
                    com.inik.camcon.utils.LogcatManager.d(TAG, "✅ PTPIP 플러시 완료")

                    // 초기 플러시가 아직 완료되지 않은 경우에만 상태 변경
                    if (!isInitialFlushCompleted) {
                        isInitialFlushCompleted = true
                        com.inik.camcon.utils.LogcatManager.d(
                            TAG,
                            "✅ 초기 플러시 완료 콜백 호출 성공 - UI 블록 해제"
                        )

                        // 초기 플러시 완료 시 연결 완료 상태로 변경
                        _connectionState.value = PtpipConnectionState.CONNECTED
                        _connectionProgressMessage.value = "연결 완료!"
                        Log.d(TAG, "PTPIP 연결 상태 변경: CONNECTED")

                        // 백그라운드 알림 업데이트 (앱이 백그라운드일 때)
                        try {
                            com.inik.camcon.data.service.AutoConnectForegroundService.updateNotification(
                                context,
                                "카메라 연결 완료",
                                "이벤트 리스너가 준비되었습니다"
                            )
                            Log.d(TAG, "✅ 백그라운드 알림 업데이트 완료")
                        } catch (e: Exception) {
                            Log.w(TAG, "백그라운드 알림 업데이트 실패 (무시): ${e.message}")
                        }

                        // 네트워크 바인딩은 유지 (카메라 연결 유지)
                        // Firebase는 getCellularNetwork()를 통해 별도로 셀룰러 데이터 사용 가능
                        Log.i(TAG, "✅ 카메라 Wi-Fi 바인딩 유지 - Firebase는 셀룰러 데이터 사용 가능")
                    } else {
                        Log.d(TAG, "중복 플러시 완료 콜백 무시 (이미 초기 플러시 완료됨)")
                    }
                },
                onCaptureFailed = { errorCode ->
                    Log.e(TAG, "PTPIP AP 모드 촬영 실패: $errorCode")
                    com.inik.camcon.utils.LogcatManager.e(TAG, "❌ PTPIP 촬영 실패: $errorCode")
                },
                connectionType = CameraEventManager.ConnectionType.PTPIP
            )

            if (result.isSuccess) {
                Log.i(TAG, "✅ PTPIP AP 모드 이벤트 리스너 시작 성공")
                com.inik.camcon.utils.LogcatManager.d(TAG, "🎉 PTPIP 이벤트 리스너 시작 성공!")
                _connectionProgressMessage.value = "초기화 완료 중..."
            } else {
                Log.e(
                    TAG,
                    "❌ PTPIP AP 모드 이벤트 리스너 시작 실패: ${result.exceptionOrNull()?.message}"
                )
                com.inik.camcon.utils.LogcatManager.e(
                    TAG,
                    "❌ PTPIP 이벤트 리스너 시작 실패: ${result.exceptionOrNull()?.message}"
                )
                _connectionProgressMessage.value = "이벤트 리스너 오류"
            }
        } catch (e: Exception) {
            Log.e(TAG, "PTPIP AP 모드 이벤트 리스너 시작 중 오류", e)
            com.inik.camcon.utils.LogcatManager.e(TAG, "❌ PTPIP 이벤트 리스너 시작 중 예외: ${e.message}", e)
            _connectionProgressMessage.value = "설정 오류"
            // 폴백: 기존 방식 사용
            startFileReceiveListenerFallback(camera)
        }
    }

    /**
     * 기본 저장 디렉토리 가져오기
     */
    private fun getDefaultSaveDirectory(): String {
        return photoDownloadManager.getSaveDirectory()
    }

    /**
     * 기존 방식의 파일 수신 리스너 (폴백용)
     */
    private fun startFileReceiveListenerFallback(camera: PtpipCamera) {
        Log.i(TAG, "기존 방식 파일 수신 리스너 시작: ${camera.name}")

        try {
            // 파일 수신 전용 리스너 (촬영 명령 없음)
            val fileReceiveListener = object : CameraCaptureListener {
                override fun onFlushComplete() {
                    Log.d(TAG, "파일 수신: 플러시 완료")
                }

                override fun onPhotoCaptured(filePath: String, fileName: String) {
                    Log.i(TAG, "파일 수신: 외부 촬영 파일 자동 다운로드 완료 - $fileName")
                    Log.i(TAG, "파일 경로: $filePath")
                    handleAutomaticDownload(filePath, fileName)
                }

                override fun onPhotoDownloaded(
                    filePath: String,
                    fileName: String,
                    imageData: ByteArray
                ) {
                    Log.i(TAG, "파일 수신: Native 직접 다운로드 완료 - $fileName")
                    Log.i(TAG, "데이터 크기: ${imageData.size / 1024}KB")
                    handleAutomaticDownload(filePath, fileName)
                }

                override fun onCaptureFailed(errorCode: Int) {
                    Log.e(TAG, "파일 수신: 수신 실패 (에러 코드: $errorCode)")
                }

                override fun onUsbDisconnected() {
                    Log.w(TAG, "USB 분리 이벤트 - PTPIP는 영향받지 않음 (Wi-Fi 연결)")
                    // PTPIP 연결에서는 USB 분리 이벤트가 관련없으므로 무시
                }
            }

            // 기존 방식으로 파일 수신 리스너 시작
            CameraNative.listenCameraEvents(fileReceiveListener)
            Log.i(TAG, "기존 방식 파일 수신 리스너 시작됨")

        } catch (e: Exception) {
            Log.e(TAG, "기존 방식 파일 수신 리스너 시작 실패", e)
        }
    }

    /**
     * 사진 촬영 (수동 촬영 명령 - 사용자 요청 시에만 실행)
     */
    suspend fun capturePhoto(
        callback: CameraCaptureListener? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "수동 사진 촬영 시작 (사용자 요청)")

            // 현재 연결된 카메라 정보 확인
            val camera = connectedCamera
            if (camera == null) {
                Log.e(TAG, "수동 촬영 실패: 연결된 카메라 없음")
                return@withContext false
            }

            // callback이 없으면 동기 방식으로 처리
            if (callback == null) {
                // 동기 방식 - 기존 코드
                try {
                    val result = CameraNative.capturePhoto()
                    Log.d(TAG, "수동 동기 촬영 결과: $result")
                    return@withContext result >= 0
                } catch (e: Exception) {
                    Log.e(TAG, "수동 동기 촬영 중 오류", e)
                    return@withContext false
                }
            }

            // 비동기 방식 - callback 있을 때 (수동 촬영 명령)
            try {
                CameraNative.capturePhotoAsync(callback, "") // 사용자 요청 시에만 촬영
                Log.d(TAG, "수동 비동기 촬영 요청 완료")
                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "수동 비동기 촬영 중 오류", e)
                return@withContext false
            }

        } catch (e: Exception) {
            Log.e(TAG, "수동 사진 촬영 중 오류", e)
            false
        }
    }

    /**
     * 자동 파일 수신 중지
     */
    private suspend fun stopAutomaticFileReceiving() {
        try {
            Log.d(TAG, "PTPIP 자동 파일 수신 중지")

            // CameraEventManager를 통해 이벤트 리스너 중지
            val result = cameraEventManager.stopCameraEventListener()
            if (result.isSuccess) {
                Log.d(TAG, "✅ CameraEventManager 이벤트 리스너 중지 성공")
            } else {
                Log.w(
                    TAG,
                    "❌ CameraEventManager 이벤트 리스너 중지 실패: ${result.exceptionOrNull()?.message}"
                )
            }

            // CameraEventManager 정리 대기
            delay(200)

            // 네이티브 이벤트 리스너 중지 (콜백으로 대기)
            try {
                Log.d(TAG, "네이티브 이벤트 리스너 중지 요청...")

                // 타임아웃 설정 (최대 5초)
                val stopped = kotlinx.coroutines.withTimeoutOrNull(5000) {
                    kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
                        try {
                            // 비동기 버전 시도
                            CameraNative.stopListenCameraEventsAsync(object :
                                EventListenerStopCallback {
                                override fun onStopped() {
                                    Log.d(TAG, "✅ 네이티브 이벤트 리스너 스레드 종료 완료 (비동기)")
                                    if (continuation.isActive) {
                                        continuation.resume(Unit) {}
                                    }
                                }
                            })
                        } catch (e: UnsatisfiedLinkError) {
                            // 네이티브 메서드가 아직 구현되지 않은 경우
                            Log.w(TAG, "stopListenCameraEventsAsync 미구현 - 동기 방식 사용")
                            try {
                                CameraNative.stopListenCameraEvents()
                                // 고정 대기 시간 사용 (동기 방식)
                                coroutineScope.launch(Dispatchers.IO) {
                                    delay(1000) // 1초 대기
                                    if (continuation.isActive) {
                                        continuation.resume(Unit) {}
                                    }
                                }
                            } catch (e2: Exception) {
                                if (continuation.isActive) {
                                    continuation.resume(Unit) {}
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "네이티브 리스너 중지 중 예외: ${e.message}")
                            if (continuation.isActive) {
                                continuation.resume(Unit) {}
                            }
                        }
                    }
                }

                if (stopped != null) {
                    Log.d(TAG, "✅ 네이티브 이벤트 리스너 종료 대기 완료")
                } else {
                    Log.w(TAG, "⚠️ 네이티브 이벤트 리스너 종료 타임아웃 (5초)")
                }

            } catch (e: Exception) {
                Log.w(TAG, "네이티브 이벤트 리스너 중지 중 예외: ${e.message}")
            }

            // 추가 안전 대기 (이벤트 리스너가 완전히 종료될 때까지)
            Log.d(TAG, "이벤트 리스너 완전 종료 대기 중...")
            delay(1000) // 1초 추가 대기

        } catch (e: Exception) {
            Log.e(TAG, "자동 파일 수신 중지 중 오류", e)
        }
    }

    /**
     * 카메라 연결 해제
     */
    suspend fun disconnect(keepSession: Boolean = false) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "카메라 연결 해제 시작 (keepSession: $keepSession)")

            // 자동 파일 수신 중지 (내부에서 완전한 대기 처리됨)
            stopAutomaticFileReceiving()

            // Discovery 중지
            // discoveryService.stopDiscovery() // 카메라 목록 유지를 위해 주석 처리

            // libgphoto2 연결 해제를 백그라운드 스레드에서 실행
            if (!keepSession) {
                withContext(Dispatchers.Default) {
                    try {
                        Log.d(TAG, "libgphoto2 연결 해제 중...")
                        // 추가 안전 대기: 네이티브 스레드가 완전히 멈출 때까지
                        delay(300)
                        CameraNative.closeCamera()
                        Log.d(TAG, "libgphoto2 연결 해제 완료")
                    } catch (e: Exception) {
                        Log.w(TAG, "libgphoto2 연결 해제 중 오류: ${e.message}")
                    }
                }
            } else {
                Log.d(TAG, "libgphoto2 연결 해제 무시 (세션 유지)")
            }

            // PTPIP 연결 해제
            connectionManager.closeConnections(!keepSession)

            // 상태 초기화
            if (!keepSession) {
                connectedCamera = null
                lastConnectedCamera = null
                _connectionState.value = PtpipConnectionState.DISCONNECTED
                _cameraInfo.value = null
                // 연결 해제 시 AP 강제 표시 해제
                _isApModeForced.value = false
                _connectionProgressMessage.value = ""
                isInitialFlushCompleted = false
                Log.d(TAG, "카메라 연결 해제 완료")
            } else {
                _connectionState.value = PtpipConnectionState.CONNECTED
                Log.d(TAG, "카메라 연결 유지 (세션 유지)")
            }

        } catch (e: Exception) {
            Log.e(TAG, "카메라 연결 해제 중 오류", e)
        }
    }

    /**
     * 자동 다운로드된 파일 처리 - 네이티브에서 모든 처리 완료됨
     */
    private fun handleAutomaticDownload(filePath: String, fileName: String) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "네이티브 파일 처리 완료 알림: $fileName")
                // 중복 처리 방지: 최근 처리 맵에서 윈도우 내 동일 파일 무시
                val now = System.currentTimeMillis()
                if (!recentProcessingGuard.tryMark(filePath, now)) {
                    Log.d(TAG, "중복 파일 처리 이벤트 무시: $fileName")
                    return@launch
                }

                // 파일 정보만 로그 출력 - 네이티브에서 모든 처리(다운로드, 리사이즈, 저장)가 완료됨
                val ext = fileName.substringAfterLast('.', "").lowercase()
                Log.d(TAG, "   파일 확장자: $ext")

                if (ext in listOf("jpg", "jpeg", "png", "cr2", "nef", "arw", "dng")) {
                    Log.d(TAG, "✅ 이미지 파일 - 네이티브에서 처리 완료됨")

                    // Repository에 촬영 완료 알림 - 네이티브가 이미 저장 완료함
                    Log.d(TAG, "🔔 Repository에 촬영 완료 알림: $fileName")
                    onPhotoCapturedCallback?.invoke(filePath, fileName)
                } else {
                    Log.d(TAG, "❌ 지원하지 않는 파일 형식: $ext")
                }

                Log.i(TAG, "✅ 파일 처리 완료 알림: $fileName (네이티브 처리 완료)")

            } catch (e: Exception) {
                Log.e(TAG, "파일 처리 완료 알림 중 오류", e)
            }
        }
    }

    // 최근 처리 중복 방지 가드
    private val recentProcessingGuard = object {
        private val map = ConcurrentHashMap<String, Long>()
        fun tryMark(key: String, now: Long): Boolean {
            // 오래된 항목 정리 (100개 이상이면)
            if (map.size > 100) {
                val expiredKeys = map.entries
                    .filter { now - it.value > DUP_WINDOW_MS * 10 }
                    .map { it.key }
                expiredKeys.forEach { map.remove(it) }
            }
            val last = map[key]
            return if (last == null || now - last > DUP_WINDOW_MS) {
                map[key] = now
                true
            } else {
                false
            }
        }
    }

    /**
     * gphoto2 접근을 위한 연결 해제
     */
    suspend fun disconnectForGphoto2(keepSession: Boolean = false) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "gphoto2 호환 모드: 연결 해제 시작 (keepSession: $keepSession)")

            // 니콘 카메라 특별 처리
            if (connectedCamera?.name?.contains("Nikon", ignoreCase = true) == true) {
                if (!keepSession) {
                    Log.d(TAG, "니콘 카메라 세션 종료")
                    connectionManager.closeSession()
                    kotlinx.coroutines.delay(2000)
                } else {
                    Log.d(TAG, "니콘 카메라 세션 유지 모드")
                }
            }

            // 일반 연결 해제 (세션 유지 여부 전달)
            disconnect(keepSession)
            kotlinx.coroutines.delay(100)

            Log.d(TAG, "gphoto2 호환 모드: 연결 해제 완료")
        } catch (e: Exception) {
            Log.e(TAG, "gphoto2 호환 모드 연결 해제 중 오류", e)
        }
    }

    /**
     * 임시 연결 해제
     */
    suspend fun temporaryDisconnect(keepSession: Boolean = true): Boolean =
        withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "임시 연결 해제 시작 (keepSession: $keepSession)")

            val currentCamera = connectedCamera
            val wasConnected = _connectionState.value == PtpipConnectionState.CONNECTED

            if (wasConnected && currentCamera != null) {
                disconnectForGphoto2(keepSession)
                return@withContext true
            }

            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "임시 연결 해제 중 오류", e)
            return@withContext false
        }
    }

    /**
     * 임시 해제 후 재연결
     */
    suspend fun reconnectAfterTemporary(camera: PtpipCamera): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "임시 해제 후 재연결 시작")
            kotlinx.coroutines.delay(2000)
            return@withContext connectToCamera(camera)
        } catch (e: Exception) {
            Log.e(TAG, "임시 해제 후 재연결 중 오류", e)
            return@withContext false
        }
    }

    /**
     * 연결 상태 확인 (외부 접근 가능 여부)
     */
    fun isExternalAccessible(): Boolean {
        return _connectionState.value == PtpipConnectionState.DISCONNECTED
    }

    /**
     * Wi-Fi 연결 상태 확인
     */
    fun isWifiConnected(): Boolean = wifiHelper.isWifiConnected()

    /**
     * Wi-Fi 활성화 여부 확인
     */
    fun isWifiEnabled(): Boolean = wifiHelper.isWifiEnabled()

    /**
     * 위치 서비스 활성화 여부 확인
     */
    fun isLocationEnabled(): Boolean = wifiHelper.isLocationEnabled()

    /**
     * Wi-Fi STA 동시 연결 지원 여부 확인
     */
    fun isStaConcurrencySupported(): Boolean = wifiHelper.isStaConcurrencySupported()

    /**
     * Wi-Fi 기능 상세 정보 가져오기
     */
    fun getWifiCapabilities(): WifiCapabilities = wifiHelper.getWifiCapabilities()

    /**
     * 현재 Wi-Fi 네트워크 상태 가져오기
     */
    fun getCurrentWifiNetworkState(): WifiNetworkState {
        return _wifiNetworkState.value
    }

    /**
     * 디버그용: ConnectionManager 접근
     */
    fun getConnectionManager() = connectionManager

    /**
     * 디버그용: NikonAuthenticationService 접근
     */
    fun getNikonAuthService() = nikonAuthService

    /**
     * 디버그용: WifiNetworkHelper 접근
     */
    fun getWifiHelper() = wifiHelper


    /**
     * WifiNetworkSpecifier 연결 요청
     */
    fun requestWifiSpecifierConnection(
        ssid: String,
        passphrase: String? = null,
        onResult: (Boolean) -> Unit,
        onError: ((String) -> Unit)? = null
    ) {
        wifiHelper.requestConnectionWithSpecifier(
            ssid = ssid,
            passphrase = passphrase,
            requireNoInternet = true,
            bindProcess = true,
            onResult = onResult,
            onError = onError
        )
    }

    /**
     * Repository 콜백 설정 (외부 셔터 감지 시 호출)
     */
    fun setPhotoCapturedCallback(callback: (String, String) -> Unit) {
        onPhotoCapturedCallback = callback
        Log.d(TAG, "PTPIP 파일 촬영 콜백 설정 완료")
    }

    fun setPhotoDownloadedCallback(callback: (String, String, ByteArray) -> Unit) {
        onPhotoDownloadedCallback = callback
        Log.d(TAG, "PTPIP 파일 다운로드 콜백 설정 완료")
    }

    /**
     * Wi-Fi 연결 끊어짐 알림 콜백 설정
     */
    fun setConnectionLostCallback(callback: () -> Unit) {
        onConnectionLostCallback = callback
        Log.d(TAG, "PTPIP 연결 끊어짐 콜백 설정 완료")
    }

    /**
     * 연결 끊어짐 메시지 클리어
     */
    fun clearConnectionLostMessage() {
        _connectionLostMessage.value = null
    }

    /**
     * 주변 Wi‑Fi SSID 스캔
     */
    suspend fun scanNearbyWifiSSIDs(): List<String> {
        return try {
            wifiHelper.scanNearbyWifiSSIDs()
        } catch (e: Exception) {
            Log.e(TAG, "Wi‑Fi 스캔 위임 중 오류", e)
            emptyList()
        }
    }

    private val autoConnectReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == WifiNetworkHelper.ACTION_AUTO_CONNECT_TRIGGER) {
                val targetSsid = intent.getStringExtra(WifiNetworkHelper.EXTRA_AUTO_CONNECT_SSID)
                    ?: return

                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        autoConnectTaskRunnerProvider.get().handlePostConnection(targetSsid)
                    } catch (e: Exception) {
                        Log.e(TAG, "자동 연결 처리 중 오류", e)
                    }
                }
            }
        }
    }

    private var autoConnectReceiverRegistered = false

    private fun registerAutoConnectReceiver() {
        if (autoConnectReceiverRegistered) return
        val filter = IntentFilter(WifiNetworkHelper.ACTION_AUTO_CONNECT_TRIGGER)
        context.registerReceiver(autoConnectReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        autoConnectReceiverRegistered = true
    }
}