package com.inik.camcon.presentation.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.data.network.ptpip.wifi.WifiNetworkHelper
import com.inik.camcon.domain.manager.CameraConnectionGlobalManager
import com.inik.camcon.domain.model.CameraCaptureCallback
import com.inik.camcon.domain.repository.PtpipPreferencesRepository
import com.inik.camcon.domain.repository.PtpipRepository
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.PtpipConnectionState
import com.inik.camcon.domain.model.WifiCapabilities
import com.inik.camcon.domain.model.WifiNetworkState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * PTPIP 기능을 관리하는 ViewModel
 * Wi-Fi를 통한 카메라 연결 및 설정 관리
 *
 * 위임 패턴 적용: 실제 로직은 3개 헬퍼에 위임
 * - [PtpipConnectionHelper]: Wi-Fi 연결, 카메라 연결/해제, 설정, 촬영
 * - [PtpipDiscoveryHelper]: Wi-Fi 스캔, 카메라 검색, 위치 설정
 * - [PtpipDebugHelper]: 디버그/테스트 (Phase 인증, 포트 스캔)
 *
 * PtpipRepository, PtpipPreferencesRepository 도메인 인터페이스를 통해
 * data 레이어에 접근한다 (Clean Architecture 레이어 경계 준수).
 */
@HiltViewModel
class PtpipViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val ptpipRepository: PtpipRepository,
    private val preferencesRepository: PtpipPreferencesRepository,
    private val globalManager: CameraConnectionGlobalManager,
    private val connectionHelper: PtpipConnectionHelper,
    private val discoveryHelper: PtpipDiscoveryHelper,
    private val debugHelper: PtpipDebugHelper,
    private val wifiHelper: WifiNetworkHelper
) : ViewModel() {

    companion object {
        private const val TAG = "PtpipViewModel"
    }

    // ── DataSource / GlobalManager에서 직접 노출하는 StateFlow ──

    // PTPIP 연결 상태
    val connectionState = ptpipRepository.connectionState

    // 연결 진행 메시지
    val connectionProgressMessage = ptpipRepository.connectionProgressMessage

    // 발견된 카메라 목록
    val discoveredCameras = ptpipRepository.discoveredCameras

    // 현재 연결된 카메라 정보
    val cameraInfo = ptpipRepository.cameraInfo

    // Wi-Fi 네트워크 상태
    val wifiNetworkState = ptpipRepository.wifiNetworkState

    // Wi-Fi 연결 끊어짐 알림 상태
    val connectionLostMessage = ptpipRepository.connectionLostMessage

    // 전역 연결 상태
    val globalConnectionState = globalManager.globalConnectionState
    val activeConnectionType = globalManager.activeConnectionType
    val connectionStatusMessage = globalManager.connectionStatusMessage

    // 저장된 Wi-Fi 자격 증명 목록
    val savedWifiCredentials = preferencesRepository.savedWifiCredentials

    // 저장된 Wi-Fi 자격 증명 (SSID Set으로 빠른 조회)
    val savedWifiSsids: StateFlow<Set<String>> = savedWifiCredentials
        .map { list -> list.map { it.ssid }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    // PTPIP 설정 상태
    val isPtpipEnabled = preferencesRepository.isPtpipEnabled
    val isAutoDiscoveryEnabled = preferencesRepository.isAutoDiscoveryEnabled
    val isAutoConnectEnabled = preferencesRepository.isAutoConnectEnabled
    val autoConnectNetworkConfig = preferencesRepository.autoConnectNetworkConfig
    val isWifiConnectionModeEnabled = preferencesRepository.isWifiConnectionModeEnabled
    val isAutoReconnectEnabled = preferencesRepository.isAutoReconnectEnabled
    val lastConnectedIp = preferencesRepository.lastConnectedIp
    val lastConnectedName = preferencesRepository.lastConnectedName
    val connectionTimeout = preferencesRepository.connectionTimeout
    val discoveryTimeout = preferencesRepository.discoveryTimeout
    val ptpipPort = preferencesRepository.ptpipPort

    // ── ViewModel 자체 UI 상태 ──

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _selectedCamera = MutableStateFlow<PtpipCamera?>(null)
    val selectedCamera: StateFlow<PtpipCamera?> = _selectedCamera.asStateFlow()

    // 자동 파일 다운로드 상태
    private val _autoDownloadEnabled = MutableStateFlow(false)
    val autoDownloadEnabled: StateFlow<Boolean> = _autoDownloadEnabled.asStateFlow()

    private val _lastDownloadedFile = MutableStateFlow<String?>(null)
    val lastDownloadedFile: StateFlow<String?> = _lastDownloadedFile.asStateFlow()

    // 주변 Wi-Fi SSID 목록 (스캔 결과)
    private val _nearbyWifiSSIDs = MutableStateFlow<List<String>>(emptyList())
    val nearbyWifiSSIDs: StateFlow<List<String>> = _nearbyWifiSSIDs.asStateFlow()

    // 위치 설정 요청 상태
    private val _needLocationSettings = MutableStateFlow(false)
    val needLocationSettings: StateFlow<Boolean> = _needLocationSettings.asStateFlow()

    // Wi-Fi 설정 페이지 이동 요청 상태
    private val _needWifiSettings = MutableStateFlow(false)
    val needWifiSettings: StateFlow<Boolean> = _needWifiSettings.asStateFlow()

    // 종합 상태 (PTPIP 활성화 + Wi-Fi 연결)
    val isPtpipAvailable = combine(
        isPtpipEnabled,
        isWifiConnectionModeEnabled,
        wifiNetworkState
    ) { enabled, wifiEnabled, networkState ->
        enabled && wifiEnabled && networkState.isConnected
    }

    // ── 디버그 콜백 ──

    private val debugCallback = object : PtpipDebugHelper.DebugCallback {
        override fun onConnectingChanged(connecting: Boolean) {
            _isConnecting.value = connecting
        }

        override fun onErrorChanged(message: String?) {
            _errorMessage.value = message
        }
    }

    // ── 초기화 ──

    init {
        // 자동 재연결 설정 감지 및 적용
        viewModelScope.launch {
            isAutoReconnectEnabled.collect { enabled ->
                connectionHelper.setAutoReconnectOnDataSource(enabled)
            }
        }

        // 저장된 자동 연결 설정 로드
        viewModelScope.launch {
            preferencesRepository.autoConnectNetworkConfig.collect { config ->
                if (config != null) {
                    connectionHelper.lastConnectedWifiConfig = config
                }
            }
        }

        // 전역 상태 변화 모니터링
        viewModelScope.launch {
            globalConnectionState.collect { state ->
                Log.d(TAG, "전역 상태 변화 감지: activeType=${state.activeConnectionType}")
                if (state.wifiNetworkState.isConnectedToCameraAP) {
                    Log.d(TAG, "AP 모드 연결 감지됨")
                }
            }
        }

        // 연결 끊어짐 상태 모니터링
        viewModelScope.launch {
            connectionLostMessage.collect { message ->
                if (message != null) {
                    Log.d(TAG, "연결 끊어짐 상태 감지됨: $message")
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    // 공개 API — 기존 시그니처 유지, 내부 로직은 헬퍼에 위임
    // ══════════════════════════════════════════════════════════

    // ── Wi-Fi 스캔 (DiscoveryHelper) ──

    fun scanNearbyWifiNetworks() {
        discoveryHelper.scanNearbyWifiNetworks(
            onDiscoveringChanged = { _isDiscovering.value = it },
            onErrorChanged = { _errorMessage.value = it },
            onNearbyWifiUpdated = { _nearbyWifiSSIDs.value = it },
            onNeedLocationSettings = { _needLocationSettings.value = it }
        )
    }

    fun dismissLocationSettingsDialog() {
        _needLocationSettings.value = false
    }

    fun dismissWifiSettingsDialog() {
        _needWifiSettings.value = false
    }

    fun checkLocationSettings() {
        discoveryHelper.checkLocationSettings(
            onNeedLocationSettings = { _needLocationSettings.value = it },
            onRescanRequested = { scanNearbyWifiNetworks() }
        )
    }

    // ── Wi-Fi 연결 (ConnectionHelper) ──

    fun connectToWifiSsidWithPassword(ssid: String, passphrase: String) {
        connectToWifiSsid(ssid, passphrase)
    }

    fun deleteSavedWifiCredential(ssid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            connectionHelper.deleteSavedWifiCredential(ssid)
        }
    }

    fun connectToWifiSsidWithSavedCredential(ssid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val passphrase = connectionHelper.getSavedCredentialPassphrase(ssid)
            if (passphrase != null) {
                Log.d(TAG, "저장된 비밀번호로 Wi-Fi 연결 시도: $ssid")
                connectToWifiSsid(ssid, passphrase)
            } else {
                Log.w(TAG, "저장된 비밀번호 없음: $ssid")
                _errorMessage.value = "저장된 비밀번호가 없습니다: $ssid"
            }
        }
    }

    fun connectToWifiSsid(ssid: String, passphrase: String? = null) {
        _isConnecting.value = true
        _errorMessage.value = null

        connectionHelper.connectToWifiSsid(
            ssid = ssid,
            passphrase = passphrase,
            onConnectionStateChanged = { _isConnecting.value = it },
            onErrorChanged = { _errorMessage.value = it },
            onCameraCreated = { camera -> _selectedCamera.value = camera }
        )
    }

    // ── 설정 변경 (ConnectionHelper) ──

    fun setPtpipEnabled(enabled: Boolean) {
        viewModelScope.launch {
            connectionHelper.setPtpipEnabled(enabled)
            if (!enabled) {
                disconnect()
            }
        }
    }

    fun setWifiConnectionModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            connectionHelper.setWifiConnectionModeEnabled(enabled)
        }
    }

    fun setAutoDiscoveryEnabled(enabled: Boolean) {
        viewModelScope.launch {
            connectionHelper.setAutoDiscoveryEnabled(enabled)
        }
    }

    fun setAutoConnectEnabled(enabled: Boolean) {
        viewModelScope.launch {
            connectionHelper.setAutoConnectEnabled(enabled)
        }
    }

    fun updateAutoConnectEnabled(
        enabled: Boolean,
        onResult: (Boolean, String) -> Unit,
        onRequestNotificationPermission: (() -> Unit)? = null
    ) {
        connectionHelper.updateAutoConnectEnabled(enabled, onResult, onRequestNotificationPermission)
    }

    fun setConnectionTimeout(timeout: Int) {
        viewModelScope.launch {
            connectionHelper.setConnectionTimeout(timeout)
        }
    }

    fun setDiscoveryTimeout(timeout: Int) {
        viewModelScope.launch {
            connectionHelper.setDiscoveryTimeout(timeout)
        }
    }

    fun setPtpipPort(port: Int) {
        viewModelScope.launch {
            connectionHelper.setPtpipPort(port)
        }
    }

    fun setAutoReconnectEnabled(enabled: Boolean) {
        viewModelScope.launch {
            connectionHelper.setAutoReconnectEnabled(enabled)
        }
    }

    // ── 카메라 검색 (DiscoveryHelper) ──

    fun discoverCameras(forceApMode: Boolean = false) {
        if (_isDiscovering.value) {
            Log.w(TAG, "이미 카메라 검색 중입니다")
            return
        }

        discoveryHelper.discoverCameras(
            forceApMode = forceApMode,
            onDiscoveringChanged = { _isDiscovering.value = it },
            onConnectingChanged = { _isConnecting.value = it },
            onErrorChanged = { _errorMessage.value = it },
            onCameraSelected = { camera -> _selectedCamera.value = camera }
        )
    }

    fun discoverCameras() = discoverCameras(false)
    fun discoverCamerasAp() = discoverCameras(true)
    fun discoverCamerasSta() = discoverCameras(false)

    // ── 카메라 연결/해제 (ConnectionHelper) ──

    fun connectToCamera(camera: PtpipCamera, forceApMode: Boolean = false) {
        viewModelScope.launch {
            try {
                _isConnecting.value = true
                _errorMessage.value = null
                _autoDownloadEnabled.value = false

                val success = connectionHelper.connectToCamera(camera, forceApMode)
                if (success) {
                    _errorMessage.value = null
                    _selectedCamera.value = camera
                    _autoDownloadEnabled.value = true
                } else {
                    _errorMessage.value = "카메라 연결에 실패했습니다"
                    _autoDownloadEnabled.value = false
                    _isConnecting.value = false
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = "카메라 연결 중 오류가 발생했습니다: ${e.message}"
                _autoDownloadEnabled.value = false
                _isConnecting.value = false
                Log.e(TAG, "카메라 연결 중 오류", e)
            }
        }
    }

    fun connectToCameraAp(camera: PtpipCamera) = connectToCamera(camera, true)
    fun connectToCameraSta(camera: PtpipCamera) = connectToCamera(camera, false)

    fun disconnect() {
        viewModelScope.launch {
            try {
                _autoDownloadEnabled.value = false
                _lastDownloadedFile.value = null

                connectionHelper.disconnect()

                _selectedCamera.value = null
                _errorMessage.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "카메라 연결 해제 중 오류", e)
            }
        }
    }

    // ── 촬영 (ConnectionHelper) ──

    fun capturePhoto(listener: CameraCaptureCallback? = null) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "수동 사진 촬영 시작 (사용자 요청)")

                if (connectionState.value != PtpipConnectionState.CONNECTED) {
                    _errorMessage.value = "카메라가 연결되어 있지 않습니다."
                    listener?.onCaptureFailed(-1)
                    return@launch
                }

                val captureListener = createCaptureListener(listener)
                connectionHelper.capturePhoto(captureListener)

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = "수동 촬영 중 오류가 발생했습니다: ${e.message}"
                Log.e(TAG, msg, e)
                _errorMessage.value = msg
                listener?.onCaptureFailed(-1)
            }
        }
    }

    /**
     * 촬영 리스너 생성 (자동 다운로드 여부에 따라 분기)
     */
    private fun createCaptureListener(externalListener: CameraCaptureCallback?): CameraCaptureCallback {
        return object : CameraCaptureCallback {
            override fun onFlushComplete() {
                Log.d(TAG, "수동 촬영: 플러시 완료")
                externalListener?.onFlushComplete()
            }

            override fun onPhotoCaptured(filePath: String, fileName: String) {
                if (_autoDownloadEnabled.value) {
                    Log.i(TAG, "수동 촬영: 사진 자동 저장됨 - $fileName")
                    Log.i(TAG, "저장 경로: $filePath")
                    _lastDownloadedFile.value = fileName
                } else {
                    Log.i(TAG, "수동 촬영: 성공 $fileName -> $filePath")
                }
                _errorMessage.value = null
                externalListener?.onPhotoCaptured(filePath, fileName)
            }

            override fun onPhotoDownloaded(
                filePath: String,
                fileName: String,
                imageData: ByteArray
            ) {
                Log.i(TAG, "수동 촬영: Native 다운로드 완료 - $fileName")
                Log.i(TAG, "데이터 크기: ${imageData.size / 1024}KB")
                _lastDownloadedFile.value = fileName
                _errorMessage.value = null
                if (externalListener is CameraCaptureCallback) {
                    externalListener.onPhotoDownloaded(filePath, fileName, imageData)
                }
            }

            override fun onCaptureFailed(errorCode: Int) {
                Log.e(TAG, "수동 촬영: 촬영 실패 (에러 코드: $errorCode)")
                val errorMsg = when (errorCode) {
                    -6 -> {
                        """
                        카메라가 원격 촬영을 지원하지 않습니다

                        Nikon 카메라는 Wi-Fi 연결 시 원격 촬영이 제한됩니다.

                        대안 방법:
                        1. 카메라 본체의 셔터 버튼을 눌러 촬영하세요
                        2. 촬영된 사진은 자동으로 앱에 전송됩니다
                        3. USB 케이블 연결 시 완전한 원격 제어가 가능합니다

                        일부 Nikon 카메라는 '리모트 촬영' 기능이 필요할 수 있습니다.
                        카메라 메뉴에서 Wi-Fi 설정을 확인해주세요.
                        """.trimIndent()
                    }
                    else -> "촬영에 실패했습니다 (에러 코드: $errorCode)"
                }
                _errorMessage.value = errorMsg
                externalListener?.onCaptureFailed(errorCode)
            }

            override fun onUsbDisconnected() {
                Log.w(TAG, "USB 분리 이벤트 - PTPIP는 영향받지 않음")
            }
        }
    }

    // ── 유틸리티/상태 조회 ──

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearConnectionLostMessage() {
        connectionHelper.clearConnectionLostMessage()
    }

    fun setIsConnecting(connecting: Boolean) {
        _isConnecting.value = connecting
    }

    fun selectCamera(camera: PtpipCamera) {
        _selectedCamera.value = camera
    }

    fun resetSettings() {
        viewModelScope.launch {
            connectionHelper.resetSettings()
            disconnect()
        }
    }

    fun isWifiConnected(): Boolean = connectionHelper.isWifiConnected()

    fun isStaConcurrencySupported(): Boolean = connectionHelper.isStaConcurrencySupported()

    fun getWifiCapabilities(): WifiCapabilities = connectionHelper.getWifiCapabilities()

    fun getConnectionStatusText(): String =
        connectionHelper.getConnectionStatusText(connectionState.value)

    fun getCurrentWifiNetworkState(): WifiNetworkState = wifiNetworkState.value

    fun getNetworkStatusMessage(): String =
        connectionHelper.getNetworkStatusMessage(wifiNetworkState.value)

    fun getComprehensiveStatusMessage(): String =
        connectionHelper.getComprehensiveStatusMessage(connectionState.value, wifiNetworkState.value)

    fun isApModeConnected() = connectionHelper.isApModeConnected()

    fun isStaModeConnected() = connectionHelper.isStaModeConnected()

    fun setAutoDownloadEnabled(enabled: Boolean) {
        _autoDownloadEnabled.value = enabled
        Log.d(TAG, "자동 다운로드 ${if (enabled) "활성화" else "비활성화"}")
    }

    fun clearLastDownloadedFile() {
        _lastDownloadedFile.value = null
    }

    fun getWifiHelper() = wifiHelper

    // ── 디버그/테스트 (DebugHelper) ──

    fun testBasicPtpipConnection(camera: PtpipCamera) {
        debugHelper.testBasicPtpipConnection(camera, debugCallback)
    }

    fun testNikonPhase1Authentication(camera: PtpipCamera) {
        debugHelper.testNikonPhase1Authentication(camera, debugCallback)
    }

    fun testNikonPhase2Authentication(camera: PtpipCamera) {
        debugHelper.testNikonPhase2Authentication(camera, debugCallback)
    }

    fun testNikonCommand(camera: PtpipCamera, command: String) {
        debugHelper.testNikonCommand(camera, command, debugCallback)
    }

    fun testSocketConnection(camera: PtpipCamera) {
        debugHelper.testSocketConnection(camera, debugCallback)
    }

    fun testPortScan(ipAddress: String) {
        debugHelper.testPortScan(ipAddress, debugCallback)
    }

    // ── 미사용 (스텁 유지) ──

    private fun startAutoDiscovery() {
        // 자동 검색 로직 추가
    }

    private fun stopAutoDiscovery() {
        // 자동 검색 중단 로직 추가
    }

    // ── 라이프사이클 ──

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            connectionHelper.cleanup()
        }
    }
}
