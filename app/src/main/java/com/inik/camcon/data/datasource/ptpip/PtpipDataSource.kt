package com.inik.camcon.data.datasource.ptpip

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.inik.camcon.CameraNative
import com.inik.camcon.EventListenerStopCallback
import com.inik.camcon.R
import com.inik.camcon.data.datasource.nativesource.CameraCaptureListener
import com.inik.camcon.data.network.ptpip.authentication.NikonAuthenticationService
import com.inik.camcon.data.network.ptpip.connection.PtpipConnectionManager
import com.inik.camcon.data.network.ptpip.discovery.CameraVendorClassifier
import com.inik.camcon.data.network.ptpip.discovery.PtpipDiscoveryService
import com.inik.camcon.data.network.ptpip.wifi.WifiNetworkHelper
import com.inik.camcon.data.repository.managers.CameraEventManager
import com.inik.camcon.data.repository.managers.PhotoDownloadManager
import com.inik.camcon.data.service.AutoConnectManager
import com.inik.camcon.data.service.AutoConnectTaskRunner
import com.inik.camcon.di.ApplicationScope
import com.inik.camcon.di.IoDispatcher
import com.inik.camcon.domain.model.CameraAbilitiesInfo
import com.inik.camcon.domain.model.CameraSupports
import com.inik.camcon.domain.model.ConnectionMethod
import com.inik.camcon.domain.model.NikonConnectionMode
import com.inik.camcon.domain.model.PtpDeviceInfo
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.PtpipCameraInfo
import com.inik.camcon.domain.model.PtpipConnectionPhase
import com.inik.camcon.domain.model.PtpipConnectionState
import com.inik.camcon.domain.model.UiText
import com.inik.camcon.domain.model.WifiCapabilities
import com.inik.camcon.domain.model.WifiNetworkState
import com.inik.camcon.domain.model.toForceApMode
import com.inik.camcon.utils.LogMask
import com.inik.camcon.utils.LogcatManager
import dagger.Lazy
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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
    private val ptpipPreferencesDataSource: com.inik.camcon.data.datasource.local.PtpipPreferencesDataSource,
    private val tetherService: com.inik.camcon.data.network.ptpip.PtpipTetherService,
    private val nativeCameraDataSource: com.inik.camcon.data.datasource.nativesource.NativeCameraDataSource,
    private val libgphoto2PluginInstaller: com.inik.camcon.data.datasource.Libgphoto2PluginInstaller,
    @ApplicationScope private val coroutineScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    @Volatile private var connectedCamera: PtpipCamera? = null
    @Volatile private var lastConnectedCamera: PtpipCamera? = null
    @Volatile private var isAutoReconnectEnabled = false
    private var networkMonitoringJob: Job? = null
    // 초기 플러시 완료 플래그
    @Volatile private var isInitialFlushCompleted = false
    private val reconnectMutex = Mutex()
    private val connectionStateMutex = Mutex()

    // Repository 콜백 저장용 - Singleton이므로 일반 참조 사용 (메모리 누수 방지 불필요)
    private var onPhotoCapturedCallback: ((String, String) -> Unit)? = null
    private var onConnectionLostCallback: (() -> Unit)? = null // Wi-Fi 연결 끊어짐 알림용

    // 사진 다운로드 이벤트 스트림 — 수집자는 싱글톤 CameraCaptureRepositoryImpl.
    // 콜백 슬롯과 달리 화면 cleanup()이 배선을 끊을 수 없다(수신사진 리스트 유실 방지).
    // 버퍼 초과 시 emit이 suspend되어 발행측(다운로드 파이프라인)에 자연 백프레셔가 걸린다.
    private val _photoEvents = MutableSharedFlow<PtpipPhotoEvent>(extraBufferCapacity = 16)
    val photoEvents: SharedFlow<PtpipPhotoEvent> = _photoEvents.asSharedFlow()

    // UI 관찰용 StateFlow
    private val _connectionState = MutableStateFlow(PtpipConnectionState.DISCONNECTED)
    val connectionState: StateFlow<PtpipConnectionState> = _connectionState.asStateFlow()

    /**
     * 한 번이라도 연결에 성공한 직전 카메라(연결 성공 시 세팅, 앱 재시작 시 DataStore에서 복원).
     * 없으면 null. STA 핫스팟 자동 연결 폴링이 "기억된 카메라만 자동 연결" 게이트로 사용한다.
     */
    fun getLastConnectedCamera(): PtpipCamera? = lastConnectedCamera

    // 추가: 연결 진행 메시지 상태 — 외부 API는 String 유지 (UI 단순화).
    // i18n: 내부에서 setProgress(UiText)로 resource를 즉시 resolve해 저장한다.
    private val _connectionProgressMessage = MutableStateFlow("")
    val connectionProgressMessage: StateFlow<String> = _connectionProgressMessage.asStateFlow()

    /** UiText 기반 진행 메시지 설정 — Context.getString으로 즉시 resolve. */
    private fun setProgress(text: UiText) {
        _connectionProgressMessage.value = when (text) {
            is UiText.Empty -> ""
            is UiText.Raw -> text.value
            is UiText.Resource -> if (text.args.isEmpty()) {
                context.getString(text.resId)
            } else {
                context.getString(text.resId, *text.args.toTypedArray())
            }
        }
    }

    // 연결 단계 (mDNS 검색부터 세션 준비까지의 세부 단계)
    private val _connectionPhase = MutableStateFlow(PtpipConnectionPhase.IDLE)
    val connectionPhase: StateFlow<PtpipConnectionPhase> = _connectionPhase.asStateFlow()

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

    // 활성화된 사용자 시나리오 (AP / STA_ROUTER / STA_PHONE_HOTSPOT). null=미선택.
    private val _activeConnectionMethod = MutableStateFlow<ConnectionMethod?>(null)
    val activeConnectionMethod: StateFlow<ConnectionMethod?> = _activeConnectionMethod.asStateFlow()

    // 사용자가 직접 입력한 카메라 IP. 폰 핫스팟 모드의 mDNS 폴백용.
    private val _manualIp = MutableStateFlow("")
    val manualIp: StateFlow<String> = _manualIp.asStateFlow()

    /** 사용자 시나리오 선택. UI/ViewModel에서 호출. */
    fun setActiveConnectionMethod(method: ConnectionMethod) {
        _activeConnectionMethod.value = method
    }

    /**
     * 사용자 입력 IP 갱신. UI/ViewModel에서 호출.
     *
     * 빈 문자열은 초기화 신호로 그대로 받는다. 그 외에는 사설망/link-local 화이트리스트만 허용한다.
     * 위반 시 상태를 갱신하지 않고 경고만 남긴다 (UI는 기존 입력 유지).
     */
    fun setManualIp(ip: String) {
        if (ip.isBlank()) {
            _manualIp.value = ""
            return
        }
        if (!com.inik.camcon.data.network.ptpip.IpAddressValidator.isAllowedCameraIp(ip)) {
            Log.w(TAG, "setManualIp 거부: 허용되지 않은 IP 형식/대역 - ${LogMask.id(ip)}")
            return
        }
        _manualIp.value = ip
    }

    /**
     * 사용자가 입력한 IP를 카메라 후보로 등록한다.
     * 동일 IP가 이미 있으면 사용자 입력 정보(이름/포트)로 갱신한다.
     * `distinctBy`는 첫 occurrence를 유지하므로 사용자가 mDNS로 발견된 카메라의
     * 이름/포트를 수동 입력으로 덮어쓸 수 없는 문제가 있어 명시적 filterNot+append로 처리.
     *
     * IP는 사설망/link-local만 허용. 화이트리스트 외에는 `IllegalArgumentException`을 던진다.
     */
    fun addManualCamera(ipAddress: String, name: String, port: Int): PtpipCamera {
        require(
            com.inik.camcon.data.network.ptpip.IpAddressValidator.isAllowedCameraIp(ipAddress)
        ) {
            "허용되지 않은 카메라 IP: ${ipAddress.take(45)} (사설망/link-local만 허용)"
        }
        val safeName = name.ifBlank { "Manual ($ipAddress)" }
        val safePort = if (port > 0) port else 15740
        val cam = PtpipCamera(ipAddress, safePort, safeName, isOnline = true)
        _discoveredCameras.value =
            _discoveredCameras.value.filterNot { it.ipAddress == cam.ipAddress } + cam
        return cam
    }

    /**
     * 현재 환경을 기반으로 사용자 시나리오를 추정한다.
     * UI 선택이 없을 때 폴백으로 사용.
     */
    fun inferMethod(): ConnectionMethod = when {
        wifiHelper.isHotspotEnabled() -> ConnectionMethod.STA_PHONE_HOTSPOT
        wifiHelper.isConnectedToCameraAP() -> ConnectionMethod.AP
        else -> ConnectionMethod.STA_ROUTER
    }

    /**
     * `ConnectionMethod`를 인자로 받는 오버로드.
     * 기존 `connectToCamera(camera, forceApMode)` 시그니처에 위임하고
     * 결과와 무관하게 `activeConnectionMethod`를 갱신한다.
     */
    suspend fun connectToCamera(camera: PtpipCamera, method: ConnectionMethod): Boolean {
        val result = connectToCamera(camera, forceApMode = method.toForceApMode())
        // 본체에서 inferMethod() 기반으로 갱신했을 수 있으므로 사용자 의도(method)로 덮어쓴다.
        _activeConnectionMethod.value = method
        return result
    }

    private var lastAutoConnectBroadcastSsid: String? = null
    private var lastAutoConnectBroadcastBssid: String? = null

    // ===== PTP/IP 네이티브 설정 관리 =====

    /**
     * PTP/IP 설정 초기화 (GUID 재생성 강제)
     */
    fun clearPtpipSettings(): Boolean {
        val result = CameraNative.clearPtpipSettings()
        LogcatManager.d(TAG, "PTP/IP 설정 초기화: $result")
        return result
    }

    /**
     * PTP/IP GUID만 초기화
     */
    fun resetPtpipGuid(): Boolean {
        val result = CameraNative.resetPtpipGuid()
        LogcatManager.d(TAG, "PTP/IP GUID 초기화: $result")
        return result
    }

    /**
     * PTP/IP 연결에서 받은 카메라 정보를 libgphoto2에 전달
     */
    fun setCameraInfoFromPtpip(
        manufacturer: String,
        model: String,
        version: String,
        serial: String
    ): Int {
        val result = CameraNative.setCameraInfoFromPtpip(manufacturer, model, version, serial)
        LogcatManager.d(TAG, "PTP/IP 카메라 정보 설정: result=$result (manufacturer=$manufacturer, model=$model)")
        return result
    }

    /**
     * STA 모드 세션 유지
     */
    fun maintainSessionForStaMode(): Int {
        val result = CameraNative.maintainSessionForStaMode()
        LogcatManager.d(TAG, "STA 모드 세션 유지: $result")
        return result
    }

    /**
     * 세션 유지 기반 카메라 초기화 (STA 모드용)
     */
    fun initCameraWithSessionMaintenance(ipAddress: String, port: Int, libDir: String): Int {
        val result = CameraNative.initCameraWithSessionMaintenance(ipAddress, port, libDir)
        LogcatManager.d(TAG, "세션 유지 카메라 초기화: result=$result (ip=${LogMask.id(ipAddress)}, port=$port)")
        return result
    }

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
        restoreLastConnectedCamera()
        // 네이티브 로그 정책은 NativeCameraDataSource(파일 로그 단일 소유자)가 UseCase 체인으로 관리한다.
        // @Singleton 데이터소스가 생성 시점에 전역 로그 상태를 건드리면 안 된다 — 과거 여기서
        // setLogLevel(0)을 "GP_LOG_ALL"이라 거짓 주석하며 호출했고, 그 catch가 enableDebugLogging(true)+
        // enableVerboseLogging(true)로 g_logcatVerbose를 켜 logcat 스톰을 되살렸다. 그래서 제거한다.
    }

    /**
     * 앱 재시작 후 DataStore에서 마지막 연결 카메라 정보를 비동기로 복원한다.
     * 이게 없으면 자동 재연결이 활성화돼 있어도 `lastConnectedCamera == null`이라
     * `handleNetworkStateChange`의 자동 재연결 분기가 통과되지 않는다.
     *
     * port는 DataStore에 저장돼 있지 않으므로 표준 PTP/IP 포트(15740)로 채우고,
     * isOnline은 검증 전이므로 false로 둔다(자동 재연결 시 실제 연결로 검증).
     */
    private fun restoreLastConnectedCamera() {
        coroutineScope.launch(ioDispatcher) {
            try {
                val info = ptpipPreferencesDataSource.getLastConnectedCameraInfo()
                if (info != null && lastConnectedCamera == null) {
                    val (ip, name) = info
                    lastConnectedCamera = PtpipCamera(
                        ipAddress = ip,
                        port = 15740,
                        name = name ?: "Last camera",
                        isOnline = false
                    )
                    Log.d(TAG, "마지막 연결 카메라 복원: ip=${LogMask.id(ip)}, name=${LogMask.serial(name)}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "마지막 연결 카메라 복원 실패: ${e.message}")
            }
        }
    }

    /**
     * 네트워크 상태 모니터링 시작
     */
    private fun startNetworkMonitoring() {
        networkMonitoringJob = wifiHelper.networkStateFlow
            .onEach { networkState ->
                Log.d(
                    TAG,
                    "네트워크 상태 변화: connected=${networkState.isConnected}, ap=${networkState.isConnectedToCameraAP}, " +
                        "ssid=${LogMask.ssid(networkState.ssid)}, ip=${LogMask.id(networkState.detectedCameraIP)}"
                )
                _wifiNetworkState.value = networkState
                maybeTriggerAutoConnect(networkState)
                
                if (isAutoReconnectEnabled) {
                    handleNetworkStateChange(networkState)
                }
            }
            .launchIn(coroutineScope)
    }

    /**
     * 네트워크 상태를 즉시 재평가해 [wifiNetworkState]에 반영한다.
     * 핫스팟을 OS 설정에서 켜고 돌아온 직후처럼 NetworkCallback이 발화하지 않는 경우의 UI 갱신용.
     */
    suspend fun refreshWifiNetworkState() {
        val snapshot = kotlinx.coroutines.withContext(ioDispatcher) {
            wifiHelper.getNetworkStateSnapshot()
        }
        _wifiNetworkState.value = snapshot
    }

    /**
     * 네트워크 상태 변화 처리
     */
    private fun handleNetworkStateChange(networkState: WifiNetworkState) {
        coroutineScope.launch(ioDispatcher) {
            // 1차: 짧은 잠금으로 상태 전이/재연결 필요 여부만 판단.
            // (이전에는 delay(3초)까지 잠금 안에서 수행해 connectToCamera/disconnect 등
            //  connectionStateMutex 를 쓰는 모든 API가 그동안 블로킹됐다.)
            var shouldScheduleReconnect = false
            connectionStateMutex.withLock {
                val currentState = _connectionState.value

                when {
                    // Wi-Fi 연결 해제됨
                    !networkState.isConnected -> {
                        if (currentState == PtpipConnectionState.CONNECTED) {
                            Log.i(TAG, "Wi-Fi 연결 해제됨 - 카메라 연결 해제")
                            _connectionState.value = PtpipConnectionState.DISCONNECTED
                            connectedCamera = null
                            _connectionLostMessage.value =
                                context.getString(R.string.progress_wifi_disconnected)
                            onConnectionLostCallback?.invoke()
                        }
                    }

                    // Wi-Fi 연결되고 자동 재연결이 활성화되어 있으며 이전에 연결된 카메라가 있는 경우
                    networkState.isConnected && isAutoReconnectEnabled &&
                            lastConnectedCamera != null && currentState == PtpipConnectionState.DISCONNECTED -> {
                        Log.i(TAG, "Wi-Fi 연결됨 - 이전 카메라 자동 재연결 예약")
                        shouldScheduleReconnect = true
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

            // 2차: 안정화 대기는 잠금 밖에서 수행하고, 재연결 자체는 다시 잠금 안에서
            // 상태를 재확인한 뒤 시도한다 (연결 시도 직렬화는 유지).
            if (shouldScheduleReconnect) {
                delay(RECONNECT_DELAY_MS)
                connectionStateMutex.withLock {
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

        coroutineScope.launch(ioDispatcher) {
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

                Log.i(TAG, "네트워크 상태 감지 기반 자동 연결 브로드캐스트 발송: ${LogMask.ssid(networkState.ssid)}")
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
                    Log.i(TAG, "자동 재연결 시도 ${attempts + 1}/$maxAttempts: ${LogMask.serial(camera.name)} (${LogMask.id(camera.ipAddress)})")
                    _connectionState.value = PtpipConnectionState.CONNECTING
                    setProgress(UiText.Resource(R.string.progress_ptpip_connecting))

                    // 호출 경로(handleNetworkStateChange)가 이미 connectionStateMutex를 보유하므로
                    // 공개 connectToCamera(재획득) 대신 internal을 직접 호출한다 (재진입 데드락 방지).
                    if (connectToCameraInternal(camera, forceApMode = false)) {
                        Log.i(TAG, "자동 재연결 성공")
                        return
                    }

                    Log.w(TAG, "자동 재연결 실패 (시도 ${attempts + 1}/$maxAttempts)")
                    _connectionState.value = PtpipConnectionState.ERROR
                    setProgress(UiText.Empty)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Reconnect attempt ${attempts + 1}/$maxAttempts failed", e)
                    _connectionState.value = PtpipConnectionState.ERROR
                    setProgress(UiText.Empty)
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
        coroutineScope.launch(ioDispatcher) {
            try {
                disconnect(keepSession = false)
            } catch (e: Exception) {
                Log.w(TAG, "cleanup 중 disconnect 실패: ${e.message}")
            }
        }

        // 물리 셔터 리스너 중지 (누수 방지)
        stopShutterListening()

        // 콜백 초기화 (사진 다운로드 이벤트는 SharedFlow라 정리 대상 아님 — 수집자는 싱글톤 Repository)
        onPhotoCapturedCallback = null
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

            // Wi-Fi 연결 상태 확인. 단 폰 핫스팟(STA_PHONE_HOTSPOT) 모드에선 폰이 SoftAP라
            // 클라이언트 연결이 없는 게 정상이므로, 핫스팟이 켜져 있으면 mDNS 검색을 진행한다.
            if (!wifiHelper.isWifiConnected() && !wifiHelper.isHotspotEnabled()) {
                Log.w(TAG, "Wi-Fi 네트워크에 연결되어 있지 않음 (핫스팟도 꺼짐)")
                return emptyList()
            }

            // AP모드인지 확인하고 직접 IP 사용
            if (wifiHelper.isConnectedToCameraAP()) {
                Log.d(TAG, "AP모드 감지: libgphoto2 기반 카메라 IP 검색 시작")
                val cameraIP = wifiHelper.findAvailableCameraIP()
                if (cameraIP != null) {
                    Log.i(TAG, "AP모드: libgphoto2로 검증된 카메라 IP ${LogMask.id(cameraIP)} 발견")
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
        withContext(ioDispatcher) {
            try {
                Log.d(TAG, "니콘 카메라 연결 모드 감지 시작: ${LogMask.serial(camera.name)}")

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
     *
     * 외부 호출용 — connectionStateMutex 직렬화. 이미 mutex를 보유한 경로
     * (handleNetworkStateChange → attemptAutoReconnect)에서는 connectToCameraInternal을
     * 직접 호출해야 한다. kotlinx Mutex는 비재진입이므로 락 보유 중 재획득 시 데드락된다.
     */
    // ===== Nikon STA 페어링 GUID 수명 관리 =====
    // 카메라는 (GUID, client_name) 쌍에 승인/거부를 기록한다. 한 번 거부(InitFail 0x1)된 GUID는
    // 전원재기동·네트워크 변경과 무관하게 영구 거부되므로, 그 GUID를 계속 들이밀면 영영 연결 불가다.
    // → InitFail 0x1 감지 시 '새 랜덤 GUID'(=카메라가 모르는 새 기기)로 바꿔 재시도하면 hollow 수락→
    //   in-session 0x952b/0x935a 승인(본체 "연결 허용")으로 신규 등록된다. 승인된 GUID는 저장해 재사용.
    private val staPairingPrefs: android.content.SharedPreferences by lazy {
        context.getSharedPreferences("ptpip_sta_pairing", Context.MODE_PRIVATE)
    }

    private fun storedStaGuid(): String? = staPairingPrefs.getString("guid", null)

    private fun saveStaGuid(guid: String) {
        staPairingPrefs.edit().putString("guid", guid).apply()
    }

    /** libgphoto2 ptp2_ip/guid 에 주입할 GUID: 이전에 페어링된 것이 있으면 우선, 없으면 기본 페어링 GUID. */
    private fun staGuidToInject(): String =
        storedStaGuid() ?: NikonAuthenticationService.STA_PAIRING_GUID

    /** 콜론-hex 16바이트 랜덤 GUID 생성 (STA_PAIRING_GUID 와 동일 포맷). */
    private fun generateFreshStaGuid(): String {
        val b = ByteArray(16)
        java.security.SecureRandom().nextBytes(b)
        return b.joinToString(":") { "%02x".format(it) }
    }

    /**
     * 직전 init 의 libgphoto2 로그(파일)에서 PTP/IP InitFail 사유를 읽는다.
     * 0 = InitFail 없음(또는 미상), 1 = rejected/denied(거부), 2 = busy/in-use.
     * (latest 로그는 매 init 마다 덮어쓰므로 직전 시도의 결과를 반영한다.)
     */
    private fun lastPtpipInitFailReason(): Int = runCatching {
        val f = java.io.File(context.filesDir, "libgphoto2_ptpip_latest.txt")
        if (!f.exists()) return 0
        val len = f.length()
        val text = if (len <= 512_000L) {
            f.readText()
        } else {
            f.inputStream().use { ins ->
                ins.skip(len - 256_000L)
                ins.readBytes().toString(Charsets.UTF_8)
            }
        }
        when {
            text.contains("PTPIP InitFail reason=0x00000001") -> 1
            text.contains("PTPIP InitFail reason=0x00000002") -> 2
            else -> 0
        }
    }.getOrDefault(0)

    suspend fun connectToCamera(camera: PtpipCamera, forceApMode: Boolean): Boolean =
        connectionStateMutex.withLock {
            connectToCameraInternal(camera, forceApMode)
        }

    /**
     * 카메라 연결 내부 구현 (이미 connectionStateMutex 보유 상태에서 호출)
     */
    private suspend fun connectToCameraInternal(
        camera: PtpipCamera,
        forceApMode: Boolean
    ): Boolean =
        withContext(ioDispatcher) {
        try {
            Log.i(TAG, "스마트 카메라 연결 시작: ${LogMask.serial(camera.name)} (${LogMask.id(camera.ipAddress)}:${camera.port})")

            // 멱등 가드: 이미 동일 카메라(IP·포트 기준)에 연결돼 있으면 재연결하지 않는다.
            // 검색→자동선택→연결 경로가 중복 호출될 때(예: 검색 버튼 재탭) connectionStateMutex로
            // 직렬화된 두 번째 연결이 살아있는 PTP/IP 세션을 끊고 재연결하면서
            // ptp_ptpip_generic_read "End of stream"가 폭주하던 문제를 막는다.
            val alreadyConnectedCamera = connectedCamera
            if (_connectionState.value == PtpipConnectionState.CONNECTED &&
                alreadyConnectedCamera?.ipAddress == camera.ipAddress &&
                alreadyConnectedCamera.port == camera.port
            ) {
                Log.i(TAG, "이미 동일 카메라에 연결됨 - 재연결 건너뜀 (멱등): ${LogMask.serial(camera.name)}")
                return@withContext true
            }

            // 연결 시작 시 상태를 CONNECTING으로 변경
            _connectionState.value = PtpipConnectionState.CONNECTING
            setProgress(UiText.Resource(R.string.progress_ptpip_connection_start))
            Log.d(TAG, "PTPIP 연결 상태 변경: CONNECTING")

            // AP 모드 강제 플래그 설정
            _isApModeForced.value = forceApMode
            // 직접 호출 경로(ConnectionHelper / DiscoveryHelper / AutoConnectTaskRunner / connectManualCamera 등)
            // 에서도 activeConnectionMethod가 stale되지 않도록 본체에서 일관 갱신한다.
            // 신규 오버로드(connectToCamera(camera, method))는 이 호출 후 명시 method로 덮어쓰므로 사용자 의도 보존.
            _activeConnectionMethod.value =
                if (forceApMode) ConnectionMethod.AP else inferMethod()

            // 라이브러리가 로드되지 않은 경우 로드
            ensureLibrariesLoaded()

            // 이전 연결 정리 (로컬 변수 스냅샷 패턴)
            val currentCamera = connectedCamera
            if (_connectionState.value == PtpipConnectionState.CONNECTED &&
                currentCamera != null && currentCamera != camera
            ) {
                Log.d(TAG, "다른 카메라 연결됨 - 기존 연결 해제")
                disconnectInternal()
            }

            // Wi-Fi 연결 확인 생략 (네트워크 바인딩 상태에서는 불필요)
            Log.d(TAG, "네트워크 바인딩 상태에서 연결 시도")

            // =========================
            // Step 0: Nikon STA 인증 (0x952b/0x935a) — libgphoto2 연결 전 필수
            // =========================
            // 제조사 판별 단일 지점(CameraVendorClassifier) — 발견 시 verdict 우선,
            // 캐시/수동 IP 경로는 이름·타입 폴백. 오판(false) 시 STA 인증을 건너뛰어
            // 첫 페어링이 InitFail 0x1로 파손되므로 놓치는 쪽 비용이 크다.
            val isNikonCamera = CameraVendorClassifier.isLikelyNikon(camera)

            // ⚠️ Nikon은 세션마다 0x935a 승인을 거쳐야 카메라가 전체 PTP opcode를 노출한다.
            // 승인 전엔 DeviceInfo에 0x952b/0x935a만 보이고 모든 실제 명령이 0x2005(Not Supported)로 거부된다.
            // (GUID 기억만으로 InitCommand는 통과해 "연결됨"처럼 보이지만 실제 동작은 전부 실패 → 빈 껍데기 연결.
            //  따라서 반드시 선인증 후 같은 GUID를 libgphoto2에 주입한다.)
            // libgphoto2에 페어링 GUID 주입. Nikon STA 연결 승인(0x952b/0x935a)은 이제 libgphoto2
            // camera_init 내부에서 hollow(미승인) 연결일 때 '같은 세션'으로 자동 수행한다(공식 Wireless
            // Transmitter Utility와 동일, teardown 없음 — WTU 와이어 캡처로 검증). 그래서 여기선 GUID만
            // 주입하고 별도 Phase1 선인증/모델분기는 하지 않는다. (과거 별도-소켓 Phase1은 단일 PTP/IP
            // 세션을 점유·오염시켜 Z8을 -7로, Z6를 hollow로 깨뜨렸다 → in-session 승인으로 대체.)
            if (isNikonCamera && !forceApMode) {
                // libgphoto2 연결과 '동일한' 고정 페어링 GUID 주입(performStaAuthentication도 같은 GUID 사용 —
                // 인증으로 등록된 GUID와 연결 GUID가 반드시 일치해야 한다).
                runCatching {
                    CameraNative.setPtpipGuid(NikonAuthenticationService.STA_PAIRING_GUID)
                }.onFailure { Log.w(TAG, "⚠️ libgphoto2 GUID 주입 실패", it) }

                // [복원] 코드-자동전송 STA 페어링(별도 소켓: 0x952b → 0x935a → 코드 필요 시 0x935b 자동전송).
                // 카메라가 "연결 허용/페어링 코드"를 띄우면 앱이 읽어 자동 전송해 GUID를 등록한다. 등록돼야
                // 아래 libgphoto2 연결이 InitFail(0x1) 없이 붙는다. (e499a2f가 이 호출을 제거 → 미등록 GUID로
                // 바로 연결해 0x1 회귀 + 카메라에 코드도 안 뜨게 됐다. 사용자 보고로 복원.)
                Log.i(TAG, "=== Nikon STA 인증 시도 (${camera.name}) ===")
                setProgress(UiText.Resource(R.string.progress_ptpip_authenticating_nikon))
                runCatching {
                    if (nikonAuthService.performStaAuthentication(camera)) {
                        Log.i(TAG, "✅ Nikon STA 인증 성공 - 카메라 승인/등록됨")
                    } else {
                        Log.w(TAG, "⚠️ Nikon STA 인증 실패 - 그래도 연결 시도 진행")
                    }
                }.onFailure { Log.w(TAG, "⚠️ Nikon STA 인증 중 예외 - 연결 시도 계속", it) }
            }

            // =========================
            // Step 1: libgphoto2로 연결
            // =========================
            // 콜드 스타트/업데이트 직후 플러그인(camlib) 추출이 아직 끝나지 않았을 수 있으므로 완료를 대기한다.
            // (H26 회귀 방지: USB 경로와 달리 PTP-IP는 자체 재추출 가드가 없어 추출 미완 시 드라이버 로드 실패.
            //  CamCon.onCreate의 finally가 항상 complete 하므로 await는 멈추지 않으나, 안전망으로 타임아웃을 둔다.)
            kotlinx.coroutines.withTimeoutOrNull(8_000) {
                (context.applicationContext as? com.inik.camcon.CamCon)?.pluginExtractionDeferred?.await()
            }

            // [A1] PTP/IP 자가 재추출 가드 — 콜드 스타트/AAB split 추출 실패로 플러그인(iolib/camlib)
            // 디렉토리가 비어 있으면 여기서 재추출한다. USB(UsbConnectionManager)와 동일 로직을
            // Libgphoto2PluginInstaller 로 공유한다(중복 복붙 제거). 이미 있으면 멱등 통과.
            val pluginDir = libgphoto2PluginInstaller.ensurePluginDirs()
            if (com.inik.camcon.BuildConfig.DEBUG) {
                Log.i(TAG, "플러그인 디렉토리: $pluginDir")
            }

            // [A2] 재추출 후에도 iolib/camlib 가 없으면 = 플러그인/설치 오류(승인/핸드셰이크 문제 아님).
            // 이 상태로 아래 폴링 루프에 진입하면 libgphoto2 가 "No iolibs"(-4)로 실패해 무한 재시도하고,
            // 화면엔 "카메라에서 연결 허용을 누르세요"(승인 대기)로 오안내된다. 즉시 실패로 종료해
            // 정확한 플러그인/설치 오류 안내를 노출한다.
            if (!libgphoto2PluginInstaller.arePluginsPresent()) {
                Log.e(TAG, "❌ libgphoto2 플러그인 부재(iolib/camlib 없음) - 무한 재시도 대신 즉시 실패")
                _connectionState.value = PtpipConnectionState.ERROR
                setProgress(UiText.Resource(R.string.progress_ptpip_plugin_error))
                return@withContext false
            }

            setProgress(UiText.Resource(R.string.progress_ptpip_connecting))

            // 환경 변수 설정 (중요!)
            try {
                val envSetupResult = CameraNative.setupEnvironmentPaths(pluginDir)
                if (envSetupResult) {
                    Log.i(TAG, "환경 변수 설정 완료")
                } else {
                    Log.w(TAG, "환경 변수 설정 실패 (계속 진행)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "환경 변수 설정 중 오류 (계속 진행): ${e.message}")
            }

            // libgphoto2가 설정(GUID 등)을 영속화하는 $HOME/.config/gphoto 디렉토리 보장.
            // (없으면 save_settings가 "Can't open settings file for writing"으로 실패해 GUID가 디스크에 안 남음)
            runCatching { java.io.File(context.filesDir, ".config/gphoto").mkdirs() }

            // libgphoto2 초기화 1회 실행 람다 (forceApMode/표준 분기). 폴백 재시도에서 재사용.
            val runLibGphotoInit: () -> String? = {
                if (forceApMode) {
                    Log.i(TAG, "AP 모드 강제: libgphoto2 초기화")
                    CameraNative.initCameraForAPMode(camera.ipAddress, camera.port, pluginDir)
                } else {
                    Log.i(TAG, "표준 모드: libgphoto2 초기화")
                    // init 구간에만 GP_LOG_DATA로 올리고, 끝나면 파일 로그 baseline 레벨로 복귀한다.
                    // (과거 finally가 무조건 setLogLevel(0)이라, Splash/설정이 켠 영구 디버그 파일이
                    //  Wi-Fi 연결마다 ERROR로 떨어지는 버그가 있었다 → NativeCameraDataSource baseline 복원.)
                    // 파일: files/libgphoto2_ptpip_latest.txt (연결마다 덮어씀)
                    val gphotoLogPath =
                        "${context.filesDir.absolutePath}/libgphoto2_ptpip_latest.txt"
                    runCatching { CameraNative.startLogFile(gphotoLogPath) }
                    runCatching { CameraNative.setLogLevel(CameraNative.GP_LOG_DATA) }
                    try {
                        CameraNative.initCameraWithPtpip(camera.ipAddress, camera.port, pluginDir)
                    } finally {
                        runCatching { CameraNative.setLogLevel(nativeCameraDataSource.currentFileLevel()) }
                    }
                }
            }
            fun isInitOk(r: String?): Boolean =
                r == "OK" || r == "GP_OK" || r?.contains("Success", ignoreCase = true) == true

            // [2.5.34] desc 기반 HEALTHY 프로브 제거: Z8/Z9 가 GetDevicePropDesc 를 0x2005/0x200a 로 막아
            // config-read(전체 트리·get_single_config) 프로브가 healthy 연결도 거짓 음성을 내, 성공한 연결을
            // 닫고 재init(-7) 시키던 STA 회귀의 원인이었다. init 성공 자체를 ready 로 본다(아래 ready=initOk 참고).

            // 연결 준비 판정 = init 성공 + (Nikon STA면) 카메라가 전체 opcode 노출(=0x935a 승인 전환 완료).
            // ⚠️ 고정 delay 제거: 승인 전환은 비동기·시간 가변(핫스팟 RTT/펌웨어 의존)이라 "기다릴 시간"을
            // 못 정한다(너무 짧으면 미승인 연결=실패, 너무 길면 느림). 유일한 관측 신호는 "실제 op가 동작하는가"
            // 이므로 짧은 간격으로 재init+프로브를 폴링해 준비되는 즉시 진행하고, 안전망으로만 타임아웃을 둔다.
            // (콜백/이벤트 신호는 불가 — 0x935a가 libgphoto2 이벤트 큐에 안 잡힘. 워크플로 조사 결론.)
            val requireHealthy = isNikonCamera && !forceApMode
            // 최초 페어링: 카메라가 화면에 "연결 허용?"을 띄우고 사용자가 본체에서 OK를 눌러야
            // GUID 등록이 끝난다(공식 WTU도 동일 — 0x952b 후 ~7s 무신호=사용자 확인, z8cap1 검증).
            // 누르기 전엔 카메라가 새 TCP를 -7로 거부하므로, 사용자가 카메라까지 가서 누를 시간을
            // 확보하려 승인 대기 경로(requireHealthy)는 데드라인을 60s로 둔다. 이미 등록된 GUID는
            // 첫 init이 풀로 떠 즉시 통과(대기·안내 없음).
            val readyDeadlineMs = android.os.SystemClock.elapsedRealtime() +
                (if (requireHealthy) 60_000L else 20_000L)
            // 첫 backoff를 300→800ms로 상향(A-3): close 직후 너무 빨리 재init하면 Nikon이
            // "TCP만 수락하고 응답 안 함" 상태에 빠진다(airnef 문서화). ≥1s 간격 권고에 맞춤.
            var backoffMs = 800L
            var directRetries = 0
            // 직접 연결. Nikon STA 승인(0x952b/0x935a)은 libgphoto2 camera_init 내부에서 hollow일 때
            // '같은 세션'으로 자동 수행되므로(공식 WTU와 동일), init이 끝나면 healthy(풀 opcode)로 올라온다.
            // 여기선 init + healthy 프로브만 하고, 일시적 핸드셰이크 실패(-7)나 승인 직후 일시 hollow면 backoff 재시도.
            var initResult = runLibGphotoInit()
            var initOk = isInitOk(initResult)
            // ready = init 성공만으로 판정. libgphoto2 2.5.34 에서 Z8/Z9 는 GetDevicePropDesc 를 0x2005/0x200a
            // 로 막아(synth-dpd 우회는 전체 _get_config 경로에서만 동작) batterylevel/get_single_config 기반
            // healthy 프로브가 healthy 연결도 거짓 음성 → 성공한 연결을 close+재init(-7) 하던 STA 회귀의 원인.
            // 미승인 첫-페어링은 camera_init 이 -7 로 실패하므로(=init OK 면 승인·healthy 확정), 아래 재시도 루프가
            // 승인 대기를 그대로 처리한다. 따라서 desc 기반 프로브를 ready 게이트에서 제거한다.
            var ready = initOk
            while (!ready && android.os.SystemClock.elapsedRealtime() < readyDeadlineMs) {
                runCatching { CameraNative.closeCamera() }
                directRetries++
                // 최초 페어링이면 승인 전까지 카메라가 -7로 거부한다. 일시 핸드셰이크 블립(1회)은
                // 넘기고 2번째 재시도부터 "카메라 본체에서 연결 허용을 누르세요"를 안내한다.
                // >=2로 매 폴링마다 재확정해, 중간에 다른 진행 메시지가 끼어도 안내가 유지되게 한다
                // (StateFlow는 동일값이면 재방출 안 하므로 비용 없음). 이미 등록된 카메라는 첫 시도에
                // 붙어 이 루프에 도달하지 않으므로 오안내가 나지 않는다.
                if (requireHealthy && directRetries >= 2) {
                    setProgress(UiText.Resource(R.string.progress_ptpip_camera_confirm))
                }
                Log.i(TAG, "연결 준비 폴링 — 재시도 $directRetries (initOk=$initOk): $initResult")
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(1500L)
                initResult = runLibGphotoInit()
                initOk = isInitOk(initResult)
                ready = initOk
            }
            if (ready && requireHealthy) {
                Log.i(TAG, "✅ Nikon 승인 확인됨 - 카메라가 전체 opcode 노출")
            }
            initOk = ready

            if (!initOk) {
                Log.e(TAG, "❌ libgphoto2 초기화 실패: $initResult")
                _connectionState.value = PtpipConnectionState.ERROR
                setProgress(UiText.Resource( R.string.progress_ptpip_failed, listOf(initResult.orEmpty()) ))
                return@withContext false
            }

            Log.i(TAG, "✅ libgphoto2 초기화 성공")

            // =========================
            // Step 2: 카메라 기능 조회
            // =========================
            setProgress(UiText.Resource(R.string.progress_ptpip_checking_info))

            val abilitiesJson = try {
                CameraNative.getCameraAbilities()
            } catch (e: Exception) {
                Log.e(TAG, "Abilities 조회 중 오류", e)
                null
            }

            // ⚠️ getCameraDeviceInfo()는 내부적으로 gp_camera_get_summary(전체 요약)를 호출해
            // Nikon PTP/IP에서 ~6초가 걸린다(스토리지·다수 device property를 PTP로 일일이 조회).
            // 정작 필요한 manufacturer/model/version/serial 4개는 PTP DeviceInfo 캐시 기반
            // status 위젯이라 get_single_config(getConfigString)로 즉시 반환되므로 그걸 쓴다.
            // (무거운 summary 경로는 USB(NativeCameraDataSource)에서만 유지)
            fun deviceField(key: String): String =
                runCatching { CameraNative.getConfigString(key) }.getOrNull()
                    ?.takeIf { it.startsWith("성공") }
                    ?.removePrefix("성공: ") ?: ""
            val deviceInfo = PtpDeviceInfo(
                manufacturer = deviceField("manufacturer"),
                model = deviceField("cameramodel"),
                version = deviceField("deviceversion"),
                serialNumber = deviceField("serialnumber")
            )

            if (abilitiesJson == null) {
                Log.w(TAG, "⚠️ 카메라 정보 조회 실패 (하지만 연결은 성공)")
                // 정보 없어도 계속 진행
            } else {
                val abilities = parseAbilities(abilitiesJson)

                Log.i(
                    TAG,
                    "연결된 카메라 정보: 제조사=${deviceInfo.manufacturer}, 모델=${deviceInfo.model}, 드라이버=${abilities.status}, " +
                        "촬영=${abilities.supports.captureImage}, 라이브뷰=${abilities.supports.capturePreview}, " +
                        "설정=${abilities.supports.config}, 트리거=${abilities.supports.triggerCapture}"
                )

                // 상태 저장
                storeAbilities(abilities, deviceInfo)

                // 기능 제한 경고
                if (abilities.supports.isDownloadOnly()) {
                    Log.w(TAG, "이 카메라는 다운로드만 지원합니다 (원격 촬영·라이브뷰 미지원)")
                    setProgress(UiText.Resource(R.string.progress_ptpip_complete_limited))
                }
            }

            // =========================
            // Step 4: 이벤트 리스너 시작 (connectedCamera 설정 전에 시작)
            // =========================
            setProgress(UiText.Resource(R.string.progress_ptpip_listener_start))
            startAutomaticFileReceiving(camera)

            // =========================
            // Step 5: 연결 정보 저장 (이벤트 리스너 준비 완료 후)
            // =========================
            connectedCamera = camera
            lastConnectedCamera = camera
            // 직전 성공 카메라 영속 저장 — 모든 연결 경로(수동 STA 검색/자동/폴링)가 여기로 수렴하므로
            // 이 지점에서 저장해야 "마지막 연결된 카메라에 자동 연결"(auto_connect) 토글이 대상 카메라를
            // 인식한다. (Helper.connectToCamera 경로만 저장하면 검색→연결 경로는 누락됨.)
            runCatching {
                ptpipPreferencesDataSource.saveLastConnectedCamera(camera.ipAddress, camera.name)
            }.onFailure { Log.w(TAG, "직전 카메라 영속 저장 실패", it) }

            // 연결 완료 전이 안전망 (H4): 정상 경로는 onFlushComplete 콜백이 _connectionState를
            // CONNECTED로 올린다(startAutomaticFileReceiving 내부에서 최대 10초 대기). 그러나 특정
            // 카메라/펌웨어/타이밍에서 플러시 완료 콜백이 끝내 오지 않으면 CONNECTING에 영구 고착되어
            // (촬영해도 사진이 안 들어오는 무증상 실패) UI가 '연결 중'에서 멈춘다.
            // 여기 도달했다는 것은 init·healthy 프로브·이벤트 리스너 시작이 모두 성공했다는 뜻이므로,
            // 콜백이 미수신(여전히 CONNECTING)이면 안전하게 CONNECTED로 폴백 전이한다.
            if (_connectionState.value == PtpipConnectionState.CONNECTING) {
                Log.w(TAG, "⚠️ onFlushComplete 콜백 미수신 - CONNECTED 안전 폴백 전이 (H4)")
                isInitialFlushCompleted = true
                _connectionState.value = PtpipConnectionState.CONNECTED
                setProgress(UiText.Resource(R.string.progress_ptpip_complete))
            }

            Log.i(TAG, "🎉 카메라 연결 설정 완료!")
            return@withContext true

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "카메라 연결 중 오류", e)
            _connectionState.value = PtpipConnectionState.ERROR
            setProgress(UiText.Resource( R.string.progress_ptpip_error, listOf(e.message.orEmpty()) ))
            return@withContext false
        }
        } // withContext

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
     * Nikon 연결 모드 감지 (간소화)
     */
    private suspend fun detectNikonMode(camera: PtpipCamera): NikonConnectionMode {
        // PtpipConnectionManager를 통한 빠른 연결 테스트
        if (connectionManager.establishConnection(camera)) {
            val deviceInfo = connectionManager.getDeviceInfo()
            connectionManager.closeConnections()

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

    /**
     * Wi-Fi(PTPIP) 이벤트 리스너 재시작 — 연결은 살아있는데 리스너만 꺼진 경우 복구용.
     *
     * BackgroundSyncService 감독 루프·미리보기 탭 이탈 등에서 호출되는
     * CameraRepository.startCameraEventListener()는 USB 전용 경로라 Wi-Fi 리스너를
     * 되살릴 수 없었다. PTPIP은 자체 onPhotoDownloaded 저장 콜백(MediaStore 경로)이
     * 필요하므로 여기서 startAutomaticFileReceiving을 재사용해 복구한다.
     *
     * @return 호출 후 리스너가 실행 중이면 true.
     */
    suspend fun restartEventListenerIfNeeded(): Result<Boolean> {
        val camera = connectedCamera
        if (camera == null || _connectionState.value != PtpipConnectionState.CONNECTED) {
            return Result.failure(Exception("PTPIP 카메라가 연결되지 않음"))
        }
        if (cameraEventManager.isRunning()) {
            return Result.success(true)
        }
        Log.i(TAG, "PTPIP 이벤트 리스너 재시작 시도: ${LogMask.serial(camera.name)}")
        startAutomaticFileReceiving(camera)
        return Result.success(cameraEventManager.isRunning())
    }

    /**
     * AP 모드 연결 성공 시 이벤트 리스너 시작 (CameraEventManager 활용)
     */
    private suspend fun startAutomaticFileReceiving(camera: PtpipCamera) {
        Log.i(TAG, "PTPIP AP 모드 이벤트 리스너 시작: ${LogMask.serial(camera.name)}")
        com.inik.camcon.utils.LogcatManager.d(
            TAG,
            "Repository 콜백 설정 상태: ${onPhotoCapturedCallback != null}"
        )

        // 초기 플러시 플래그 초기화
        isInitialFlushCompleted = false

        // 이벤트 리스너 준비 완료 대기용
        val listenerReady = CompletableDeferred<Boolean>()

        try {
            // 파일 목록 조회 생략 - 사진이 많으면 스캔 중 연결 끊김 방지
            Log.i(TAG, "PTPIP 연결 후 파일 목록 조회 생략 (성능 최적화) - 이벤트 리스너만 시작")

            // 이벤트 리스너 시작
            setProgress(UiText.Resource(R.string.progress_ptpip_listener_start))

            // 비자발적 끊김(카메라 OFF/소켓 death) 통지 슬롯 배선 — 리스너 시작 직전에 연결.
            // 네이티브 이벤트 루프가 비정상 종료하면 '상태만' DISCONNECTED로 내려 폴링이 재연결한다.
            cameraEventManager.onPtpipConnectionLostCallback = { notifyInvoluntaryPtpipDisconnect() }

            // CameraEventManager를 통해 PTPIP 이벤트 리스너 시작
            val result = cameraEventManager.startCameraEventListener(
                isConnected = true,
                isInitializing = false,
                saveDirectory = photoDownloadManager.getSaveDirectory(),
                onPhotoCaptured = { filePath, fileName ->
                    com.inik.camcon.utils.LogcatManager.d(
                        TAG,
                        "PTPIP onPhotoCaptured 파일 감지: $fileName"
                    )
                    // 네이티브 처리 완료 알림만 로그로 기록
                    handleAutomaticDownload(filePath, fileName)
                },
                onPhotoDownloaded = { filePath, fileName, imageData ->
                    com.inik.camcon.utils.LogcatManager.d(
                        TAG,
                        "✅ 네이티브에서 완전한 다운로드 및 저장 처리 완료: $fileName"
                    )

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
                    coroutineScope.launch(ioDispatcher) {
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
                                    "실제 저장된 파일: ${LogMask.path(realPath)}"
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

                                // Repository로 이벤트 발행 (안드로이드 저장소 경로)
                                _photoEvents.emit(
                                    PtpipPhotoEvent.Downloaded(realPath, fileName, imageData)
                                )
                            } else {
                                com.inik.camcon.utils.LogcatManager.e(
                                    TAG,
                                    "❌ MediaStore 저장 실패: $fileName"
                                )
                                // 무음 유실 방지 — 실패를 Repository에 전파해
                                // UI placeholder 제거 + dedup 해제(재시도 허용)
                                _photoEvents.emit(PtpipPhotoEvent.DownloadFailed(fileName))
                            }
                        } catch (e: Exception) {
                            com.inik.camcon.utils.LogcatManager.e(
                                TAG,
                                "MediaStore 저장 중 오류: ${e.message}",
                                e
                            )
                            _photoEvents.emit(PtpipPhotoEvent.DownloadFailed(fileName))
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
                        setProgress(UiText.Resource(R.string.progress_ptpip_complete))
                        Log.d(TAG, "PTPIP 연결 상태 변경: CONNECTED")

                        // 이벤트 리스너 준비 완료 신호
                        listenerReady.complete(true)

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
                setProgress(UiText.Resource(R.string.progress_ptpip_finalizing))
            } else {
                Log.e(
                    TAG,
                    "❌ PTPIP AP 모드 이벤트 리스너 시작 실패: ${result.exceptionOrNull()?.message}"
                )
                com.inik.camcon.utils.LogcatManager.e(
                    TAG,
                    "❌ PTPIP 이벤트 리스너 시작 실패: ${result.exceptionOrNull()?.message}"
                )
                setProgress(UiText.Resource(R.string.progress_ptpip_listener_error))
                listenerReady.complete(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "PTPIP AP 모드 이벤트 리스너 시작 중 오류", e)
            com.inik.camcon.utils.LogcatManager.e(TAG, "❌ PTPIP 이벤트 리스너 시작 중 예외: ${e.message}", e)
            setProgress(UiText.Resource(R.string.progress_ptpip_setup_error))
            listenerReady.complete(false)
            // 폴백: 기존 방식 사용
            startFileReceiveListenerFallback(camera)
        }

        // 이벤트 리스너 준비 완료 대기 (최대 10초)
        val ready = withTimeoutOrNull(10_000) { listenerReady.await() }
        if (ready == null) {
            Log.w(TAG, "이벤트 리스너 준비 완료 대기 타임아웃 (10초) - 연결은 유지")
        } else if (!ready) {
            Log.w(TAG, "이벤트 리스너 준비 실패 - 연결은 유지")
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
        Log.i(TAG, "기존 방식 파일 수신 리스너 시작: ${LogMask.serial(camera.name)}")

        try {
            // 파일 수신 전용 리스너 (촬영 명령 없음)
            val fileReceiveListener = object : CameraCaptureListener {
                override fun onFlushComplete() {
                    Log.d(TAG, "파일 수신: 플러시 완료")
                }

                override fun onPhotoCaptured(filePath: String, fileName: String) {
                    Log.i(TAG, "파일 수신: 외부 촬영 파일 자동 다운로드 완료 - $fileName (${LogMask.path(filePath)})")
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
     * PTP/IP를 통해 사진 촬영
     */
    suspend fun capturePhoto(withAF: Boolean): String? = withContext(ioDispatcher) {
        return@withContext try {
            Log.d(TAG, "PTP/IP 사진 촬영 시작 (AF: $withAF)")

            // 현재 연결된 카메라 정보 확인
            val camera = connectedCamera
            if (camera == null) {
                Log.e(TAG, "촬영 실패: 연결된 카메라 없음")
                return@withContext null
            }

            // PTP/IP 카메라로 사진 촬영
            try {
                val result = CameraNative.capturePhoto()
                Log.d(TAG, "PTP/IP 촬영 결과: $result")
                if (result >= 0) {
                    result.toString() // 성공: 결과 코드를 문자열로 반환
                } else {
                    null // 실패: null 반환
                }
            } catch (e: Exception) {
                Log.e(TAG, "PTP/IP 촬영 중 오류", e)
                null
            }

        } catch (e: Exception) {
            Log.e(TAG, "PTP/IP 사진 촬영 중 오류", e)
            null
        }
    }

    /**
     * 자동 파일 수신 중지
     */
    private suspend fun stopAutomaticFileReceiving() {
        try {
            Log.d(TAG, "PTPIP 자동 파일 수신 중지")

            // 비자발적 끊김 통지 슬롯 해제 — 정상 stop 경로에서 중복/지연 통지 방지.
            cameraEventManager.onPtpipConnectionLostCallback = null

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
                                coroutineScope.launch(ioDispatcher) {
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

            // 이벤트 리스너 종료 확인
            Log.d(TAG, "이벤트 리스너 완전 종료 대기 중...")
            delay(100)

        } catch (e: Exception) {
            Log.e(TAG, "자동 파일 수신 중지 중 오류", e)
        }
    }

    /**
     * 카메라 연결 해제 (외부 호출용 — connectionStateMutex 직렬화)
     */
    suspend fun disconnect(keepSession: Boolean) {
        connectionStateMutex.withLock {
            disconnectInternal(keepSession)
        }
    }

    /**
     * 카메라 연결 해제 내부 구현 (이미 mutex 보유 상태에서 호출)
     */
    private suspend fun disconnectInternal(keepSession: Boolean = false) = withContext(ioDispatcher) {
        try {
            Log.d(TAG, "카메라 연결 해제 시작 (keepSession: $keepSession)")

            // 소유권 가드: 단일 전역 네이티브 카메라 핸들(CameraNative)을 USB와 Wi-Fi PTP/IP가
            // 공유한다. USB 전용 세션에선 PtpipDataSource가 아무것도 소유하지 않는다(USB가 소유).
            // 이때 Wi-Fi 측 teardown이 호출되면(설정/PTPIP Activity 파괴 → PtpipViewModel.onCleared
            // → connectionHelper.cleanup) 아래에서 stopAutomaticFileReceiving로 상시 이벤트 리스너를
            // 죽이고 closeCamera로 USB 카메라까지 끊어버린다(물리셔터 수신 영구 중단 + 재시작 실패).
            // 살아있는 Wi-Fi 세션을 실제로 소유할 때만(연결 완료=CONNECTED 또는 connectedCamera 보유)
            // teardown을 수행한다. 연결 실패로 남는 ERROR/연결중 CONNECTING 상태는 소유로 보지 않는다
            // (USB 보호 우선 — ERROR는 자동으로 DISCONNECTED로 리셋되지 않으므로 != DISCONNECTED는 부적합).
            val ptpipOwnsSession =
                connectedCamera != null || _connectionState.value == PtpipConnectionState.CONNECTED
            if (!ptpipOwnsSession) {
                Log.d(TAG, "PTP/IP 세션 미소유(USB 소유 또는 미연결) - Wi-Fi teardown 생략 (keepSession=$keepSession)")
                return@withContext
            }

            // 물리 셔터 무선 수신 리스너 중지 (H3): tetherService.listenForNewShots가 단일 PTP/IP
            // 세션의 소켓을 점유하므로, disconnect 시 이 Job을 취소하지 않으면 고아 소켓이 살아남아
            // 재연결 시 카메라가 새 TCP를 -7/End-of-stream으로 거부한다(앱 재시작 전까지 재연결 불가).
            stopShutterListening()

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
                // 사용자 시나리오도 리셋 — 다음 연결의 inferMethod() 결과 보존.
                _activeConnectionMethod.value = null
                setProgress(UiText.Empty)
                isInitialFlushCompleted = false
                Log.d(TAG, "카메라 연결 해제 완료")
            } else {
                _connectionState.value = PtpipConnectionState.CONNECTED
                Log.d(TAG, "카메라 연결 유지 (세션 유지)")
            }

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "카메라 연결 해제 중 오류", e)
        }
    }

    /**
     * 자동 다운로드된 파일 처리 - 네이티브에서 모든 처리 완료됨
     */
    private fun handleAutomaticDownload(filePath: String, fileName: String) {
        coroutineScope.launch(ioDispatcher) {
            try {
                Log.d(TAG, "네이티브 파일 처리 완료 알림: $fileName")
                // 중복 처리 방지: 최근 처리 맵에서 윈도우 내 동일 파일 무시
                val now = System.currentTimeMillis()
                if (!recentProcessingGuard.tryMark(filePath, now)) {
                    Log.d(TAG, "중복 파일 처리 이벤트 무시: $fileName")
                    return@launch
                }

                // 네이티브에서 모든 처리(다운로드, 리사이즈, 저장)가 완료됨
                val ext = fileName.substringAfterLast('.', "").lowercase()

                if (ext in listOf("jpg", "jpeg", "png", "cr2", "nef", "arw", "dng")) {
                    // Repository에 촬영 완료 알림 - 네이티브가 이미 저장 완료함
                    Log.d(TAG, "Repository에 촬영 완료 알림: $fileName (ext=$ext)")
                    onPhotoCapturedCallback?.invoke(filePath, fileName)
                } else {
                    Log.d(TAG, "지원하지 않는 파일 형식: $ext")
                }

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
    suspend fun disconnectForGphoto2(keepSession: Boolean) {
        withContext(ioDispatcher) {
            try {
                Log.d(TAG, "gphoto2 호환 모드: 연결 해제 시작 (keepSession: $keepSession)")

                // 니콘 카메라 특별 처리 — 연결 게이트와 동일한 단일 판별기 사용
                if (connectedCamera?.let { CameraVendorClassifier.isLikelyNikon(it) } == true) {
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
    }

    /**
     * 임시 연결 해제
     */
    suspend fun temporaryDisconnect(keepSession: Boolean): Boolean =
        withContext(ioDispatcher) {
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
    suspend fun reconnectAfterTemporary(camera: PtpipCamera): Boolean = withContext(ioDispatcher) {
        try {
            Log.d(TAG, "임시 해제 후 재연결 시작")
            kotlinx.coroutines.delay(2000)
            return@withContext connectToCamera(camera, forceApMode = false)
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

    // ── 물리 셔터 무선 수신 모드 (제조사 자동 판별: 니콘=vendor 잠금우회, 그 외=표준 PTP) ──
    // 단일 PTP/IP 세션 제약상 libgphoto2(원격촬영/라이브뷰)와 공존 불가. 이 모드는 libgphoto2
    // 연결을 끊은 뒤(호출부 책임) 단독 세션으로 카드 핸들을 폴링해 새로 찍힌 컷만 풀해상도로
    // 받는다. 받은 bytes는 기존 PhotoDownloadManager(RAW게이팅·FREE축소·EXIF·MediaStore) →
    // photoEvents 스트림으로 흘려보내 capturedPhotos/미리보기에 동일하게 등장시킨다.
    private var shutterListenerJob: Job? = null

    val isShutterListening: Boolean
        get() = shutterListenerJob?.isActive == true

    fun startShutterListening(camera: PtpipCamera) {
        if (shutterListenerJob?.isActive == true) {
            Log.w(TAG, "물리 셔터 리스너가 이미 실행 중")
            return
        }
        Log.i(TAG, "물리 셔터 리스너 시작 요청: ${LogMask.serial(camera.name)}")
        shutterListenerJob = coroutineScope.launch(ioDispatcher) {
            tetherService.listenForNewShots(camera) { fileName, bytes ->
                try {
                    val saved = photoDownloadManager.handleNativePhotoDownload(
                        filePath = fileName,
                        fileName = fileName,
                        imageData = bytes,
                        cameraCapabilities = null,
                        cameraSettings = null
                    )
                    if (saved != null) {
                        Log.i(TAG, "새 컷 수신·저장: ${LogMask.path(saved.filePath)}")
                        _photoEvents.emit(
                            PtpipPhotoEvent.Downloaded(saved.filePath, fileName, bytes)
                        )
                    } else {
                        Log.w(TAG, "새 컷 저장 차단/실패(게이팅 등): $fileName")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "새 컷 처리 오류: ${e.message}")
                }
            }
        }
    }

    fun stopShutterListening() {
        shutterListenerJob?.cancel()
        shutterListenerJob = null
        Log.i(TAG, "물리 셔터 리스너 중지")
    }

    /**
     * Wi-Fi 연결 끊어짐 알림 콜백 설정
     */
    fun setConnectionLostCallback(callback: () -> Unit) {
        onConnectionLostCallback = callback
        Log.d(TAG, "PTPIP 연결 끊어짐 콜백 설정 완료")
    }

    /**
     * 네이티브 이벤트 루프가 '비자발적으로' 죽었을 때만 호출된다(사용자 disconnect와 구분됨).
     * 공유 네이티브 핸들(closeCamera)·리스너 stop을 하지 않고 '연결 상태만' DISCONNECTED로 내려
     * WifiMonitoringService 폴링(=='DISCONNECTED')이 재연결을 이어받게 한다.
     */
    fun notifyInvoluntaryPtpipDisconnect() {
        coroutineScope.launch(ioDispatcher) {
            val transitioned = connectionStateMutex.withLock {
                // 이미 내려갔거나 재연결이 진행 중이면 무시(멱등·경합 방지).
                if (_connectionState.value != PtpipConnectionState.CONNECTED) {
                    Log.d(TAG, "비자발적 끊김 통지 무시 — 이미 CONNECTED 아님(${_connectionState.value})")
                    return@withLock false
                }
                Log.w(TAG, "PTP/IP 비자발적 끊김 — 상태 DISCONNECTED 전이(핸들 보존)")
                connectedCamera = null
                _connectionState.value = PtpipConnectionState.DISCONNECTED
                _connectionLostMessage.value = context.getString(R.string.progress_wifi_disconnected)
                isInitialFlushCompleted = false
                // 재연결 시 stale 리스너 플래그로 조기 리턴하지 않도록 Kotlin 측 실행 상태를 정합화
                // (네이티브 stop 미호출 — 공유 핸들 불침해). M1/R5 가드.
                cameraEventManager.resetListenerStateAfterNativeDeath()
                // lastConnectedCamera는 '유지'한다 — 폴링/자동재연결이 이걸로 대상 카메라를 찾는다.
                // (disconnectInternal의 명시적 사용자 해제와 달리 여기선 null로 만들지 않는다.)
                true
            }
            if (!transitioned) return@launch

            // [재연결 액터] WifiMonitoringService 폴링은 auto_connect(기본 OFF)+자기 핫스팟
            // 전제라 STA(외부 AP·폰 핫스팟)에선 영영 안 돈다 — 여기서 직접 재연결을 예약한다.
            // in-memory 플래그는 Ptpip 화면을 안 거친 프로세스에서 false 고착이라 DataStore 직독.
            val autoReconnect = try {
                ptpipPreferencesDataSource.isAutoReconnectEnabled.first()
            } catch (e: Exception) {
                false
            }
            if (!autoReconnect) {
                Log.d(TAG, "자동 재연결 비활성(설정) — 재연결 예약 생략")
                return@launch
            }
            // attemptAutoReconnect의 재시도 대기 조건이 in-memory 플래그를 보므로 동기화.
            isAutoReconnectEnabled = true

            // 리스너 정리(IO 홉) 완료 대기: 옛 리스너 스레드 생존 중 재연결하면 한 핸들
            // 이중 펌프·closeCamera 경합(UAF)이 난다. Kotlin 플래그 false 확인 + 여유 2초.
            val listenerStopped = withTimeoutOrNull(12_000L) {
                cameraEventManager.isEventListenerActive.first { !it }
                true
            } ?: false
            if (!listenerStopped) {
                Log.w(TAG, "리스너 정지 대기 초과 — 자동 재연결 포기(수동 연결 필요)")
                return@launch
            }
            delay(2_000L)

            // USB 교차모드 가드 — 그 사이 USB가 공유 핸들을 잡았으면 건드리지 않는다(C3 계열).
            if (cameraEventManager.isUsbCameraActive()) {
                Log.w(TAG, "USB 카메라 활성 — PTP/IP 자동 재연결 생략")
                return@launch
            }

            connectionStateMutex.withLock {
                // 발화 시점 재독 — 명시적 disconnect(lastConnectedCamera=null)·수동 연결과의
                // 경합은 락 안에서 조건 재확인으로 차단한다.
                val target = lastConnectedCamera
                if (_connectionState.value != PtpipConnectionState.DISCONNECTED || target == null) {
                    Log.d(TAG, "자동 재연결 조건 소멸(상태=${_connectionState.value}) — 생략")
                    return@withLock
                }
                Log.i(TAG, "비자발적 끊김 자동 재연결 시작: ${LogMask.serial(target.name)}")
                attemptAutoReconnect(target)
            }
        }
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

                coroutineScope.launch(ioDispatcher) {
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