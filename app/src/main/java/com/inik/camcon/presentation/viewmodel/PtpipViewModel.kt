package com.inik.camcon.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.data.datasource.local.PtpipPreferencesDataSource
import com.inik.camcon.data.datasource.nativesource.CameraCaptureListener
import com.inik.camcon.data.datasource.ptpip.PtpipDataSource
import com.inik.camcon.domain.manager.CameraConnectionGlobalManager
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.PtpipConnectionState
import com.inik.camcon.domain.model.WifiCapabilities
import com.inik.camcon.domain.model.WifiNetworkState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * PTPIP 기능을 관리하는 ViewModel
 * Wi-Fi를 통한 카메라 연결 및 설정 관리
 */
@HiltViewModel
class PtpipViewModel @Inject constructor(
    private val ptpipDataSource: PtpipDataSource,
    private val preferencesDataSource: PtpipPreferencesDataSource,
    private val globalManager: CameraConnectionGlobalManager
) : ViewModel() {

    companion object {
        private const val TAG = "PtpipViewModel"
    }

    // PTPIP 연결 상태
    val connectionState = ptpipDataSource.connectionState

    // 연결 진행 메시지 추가
    val connectionProgressMessage = ptpipDataSource.connectionProgressMessage

    // 발견된 카메라 목록
    val discoveredCameras = ptpipDataSource.discoveredCameras

    // 현재 연결된 카메라 정보
    val cameraInfo = ptpipDataSource.cameraInfo

    // Wi-Fi 네트워크 상태
    val wifiNetworkState = ptpipDataSource.wifiNetworkState

    // Wi-Fi 연결 끊어짐 알림 상태 추가
    val connectionLostMessage = ptpipDataSource.connectionLostMessage

    // 전역 연결 상태 (새로 추가)
    val globalConnectionState = globalManager.globalConnectionState
    val activeConnectionType = globalManager.activeConnectionType
    val connectionStatusMessage = globalManager.connectionStatusMessage

    // PTPIP 설정 상태
    val isPtpipEnabled = preferencesDataSource.isPtpipEnabled
    val isAutoDiscoveryEnabled = preferencesDataSource.isAutoDiscoveryEnabled
    val isAutoConnectEnabled = preferencesDataSource.isAutoConnectEnabled
    val isWifiConnectionModeEnabled = preferencesDataSource.isWifiConnectionModeEnabled
    val isAutoReconnectEnabled = preferencesDataSource.isAutoReconnectEnabled
    val lastConnectedIp = preferencesDataSource.lastConnectedIp
    val lastConnectedName = preferencesDataSource.lastConnectedName
    val connectionTimeout = preferencesDataSource.connectionTimeout
    val discoveryTimeout = preferencesDataSource.discoveryTimeout
    val ptpipPort = preferencesDataSource.ptpipPort

    // UI 상태
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

    // 주변 Wi‑Fi SSID 목록 (스캔 결과)
    private val _nearbyWifiSSIDs = MutableStateFlow<List<String>>(emptyList())
    val nearbyWifiSSIDs: StateFlow<List<String>> = _nearbyWifiSSIDs.asStateFlow()

    // 위치 설정 요청 상태
    private val _needLocationSettings = MutableStateFlow(false)
    val needLocationSettings: StateFlow<Boolean> = _needLocationSettings.asStateFlow()

    // Wi-Fi 설정 페이지 이동 요청 상태 추가
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

    init {
        // 자동 재연결 설정 감지 및 적용
        viewModelScope.launch {
            isAutoReconnectEnabled.collect { enabled ->
                ptpipDataSource.setAutoReconnectEnabled(enabled)
            }
        }

        // 전역 상태 변화 모니터링
        viewModelScope.launch {
            globalConnectionState.collect { state ->
                Log.d(TAG, "전역 상태 변화 감지: activeType=${state.activeConnectionType}")
                
                // AP 모드 연결 상태 변화 시 추가 처리
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
                    // 연결 끊어짐 상태에 대한 추가 처리
                }
            }
        }
    }

    /**
     * 주변 Wi‑Fi 스캔 (SSID 리스트)
     */
    fun scanNearbyWifiNetworks() {
        Log.d(TAG, "scanNearbyWifiNetworks 메서드 호출됨")

        viewModelScope.launch {
            try {
                Log.d(TAG, "Wi-Fi 스캔 시작 - 사전 점검 진행")

                // 사전 점검: Wi‑Fi 활성화 상태
                val wifiEnabled = ptpipDataSource.isWifiEnabled()
                Log.d(TAG, "Wi-Fi 활성화 상태: $wifiEnabled")

                if (!wifiEnabled) {
                    Log.w(TAG, "Wi‑Fi가 꺼져 있음")
                    _errorMessage.value = "Wi‑Fi가 꺼져 있습니다. Wi‑Fi를 켜주세요."
                    return@launch
                }

                // 위치 서비스 확인 - 우선 시도해보고 실패하면 설정 요청
                val locationEnabled = ptpipDataSource.isLocationEnabled()
                Log.d(TAG, "위치 서비스 활성화 상태: $locationEnabled")

                if (!locationEnabled) {
                    Log.w(TAG, "위치 서비스가 꺼져 있음 - Google Play Services 설정 확인 시도")
                    _needLocationSettings.value = true
                    _errorMessage.value = "Wi-Fi 스캔을 위해 위치 서비스가 필요합니다."
                    return@launch
                }

                Log.d(TAG, "사전 점검 완료 - Wi-Fi 스캔 시작")
                _isDiscovering.value = true
                _errorMessage.value = null

                val ssids = ptpipDataSource.scanNearbyWifiSSIDs()
                Log.d(TAG, "Wi-Fi 스캔 결과: ${ssids.size}개 SSID 발견")

                ssids.forEach { ssid ->
                    Log.d(TAG, "  발견된 SSID: $ssid")
                }

                _nearbyWifiSSIDs.value = ssids

                if (ssids.isEmpty()) {
                    Log.i(TAG, "주변에 Wi-Fi 네트워크가 없음")
                    // 단순히 결과가 없는 것으로 처리 (제한 다이얼로그 표시하지 않음)
                } else {
                    Log.i(TAG, "Wi-Fi 스캔 성공: ${ssids.size}개 발견")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Wi-Fi 스캔 중 오류 발생", e)
                _nearbyWifiSSIDs.value = emptyList()
                _errorMessage.value = "주변 Wi‑Fi 스캔 중 오류: ${e.message}"
            } finally {
                _isDiscovering.value = false
                Log.d(TAG, "Wi-Fi 스캔 작업 완료")
            }
        }
    }

    /**
     * 위치 설정 다이얼로그 해제
     */
    fun dismissLocationSettingsDialog() {
        _needLocationSettings.value = false
    }

    /**
     * Wi-Fi 설정 다이얼로그 해제
     */
    fun dismissWifiSettingsDialog() {
        _needWifiSettings.value = false
    }

    /**
     * Google Play Services를 통한 위치 설정 확인
     */
    fun checkLocationSettings() {
        Log.d(TAG, "Google Play Services 위치 설정 확인 시작")
        // WifiNetworkHelper의 checkLocationSettingsForScan() 사용
        ptpipDataSource.getWifiHelper().checkLocationSettingsForScan()
            .addOnSuccessListener {
                Log.d(TAG, "위치 설정 확인 성공 - Wi-Fi 스캔 재시도")
                _needLocationSettings.value = false
                scanNearbyWifiNetworks()
            }
            .addOnFailureListener { exception ->
                Log.w(TAG, "위치 설정 확인 실패: ${exception.message}")
                // ResolvableApiException인 경우 UI에서 처리하도록 상태 유지
                _needLocationSettings.value = true
            }
    }

    /**
     * WifiNetworkSpecifier로 SSID 연결 요청 (패스워드 포함)
     */
    fun connectToWifiSsidWithPassword(ssid: String, passphrase: String) {
        Log.d(TAG, "패스워드와 함께 Wi-Fi 연결 시도: $ssid")
        connectToWifiSsid(ssid, passphrase)
    }

    /**
     * WifiNetworkSpecifier로 SSID 연결 요청
     */
    fun connectToWifiSsid(ssid: String, passphrase: String? = null) {
        Log.d(TAG, "Wi-Fi 연결 시작: ssid='$ssid', 패스워드 제공=${!passphrase.isNullOrEmpty()}")

        // 연결 시도 시작 즉시 로딩 상태 활성화
        _isConnecting.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                ptpipDataSource.requestWifiSpecifierConnection(
                    ssid = ssid,
                    passphrase = passphrase,
                    onResult = { success: Boolean ->
                        Log.d(TAG, "WifiNetworkSpecifier 결과: success=$success")
                        if (!success) {
                            val errorMsg = "Wi‑Fi 자동 연결 실패: $ssid"
                            Log.e(TAG, errorMsg)
                            _errorMessage.value = errorMsg
                            _isConnecting.value = false // 실패 시 로딩 해제
                        } else {
                            Log.i(TAG, "Wi-Fi 연결 성공: $ssid - 카메라 정보 직접 생성")
                            _errorMessage.value = null // 기존 오류 메시지 클리어

                            // 연결 성공 시 카메라 정보 바로 생성 (검색 생략)
                            // 이 시점에서는 _isConnecting을 유지하여 로딩 계속 표시
                            createCameraFromConnectedWifi(ssid)
                        }
                    },
                    onError = { errorMsg: String ->
                        Log.e(TAG, "WifiNetworkSpecifier 상세 오류: $errorMsg")
                        _errorMessage.value = errorMsg
                        _isConnecting.value = false // 오류 시 로딩 해제
                    }
                )
            } catch (e: Exception) {
                val errorMsg = "Wi‑Fi 연결 요청 중 예외 발생: ${e.message}"
                Log.e(TAG, errorMsg, e)
                _errorMessage.value = errorMsg
                _isConnecting.value = false // 예외 시 로딩 해제
            }
        }
    }

    private fun createCameraFromConnectedWifi(ssid: String) {
        viewModelScope.launch {
            try {
                // 연결 안정화를 위한 대기
                Log.d(TAG, "Wi-Fi 연결 안정화 대기 중...")
                delay(1000)

                // 연결된 Wi-Fi의 SSID를 사용하여 카메라 정보 생성
                val wifiHelper = ptpipDataSource.getWifiHelper()
                val cameraIP = wifiHelper.detectCameraIPFromCurrentNetwork() ?: "192.168.1.1"
                val currentPortValue = 15740 // 기본 PTP/IP 포트

                val camera = PtpipCamera(
                    name = "$ssid (연결됨)",
                    ipAddress = cameraIP,
                    port = currentPortValue,
                    isOnline = true
                )

                Log.i(
                    TAG,
                    "Wi-Fi 연결 성공 후 카메라 정보 생성: ${camera.name} (${camera.ipAddress}:${camera.port})"
                )

                // 카메라 정보를 선택
                _selectedCamera.value = camera

                // 연결된 Wi-Fi에서 검색하지 말고 바로 연결 시도
                Log.i(TAG, "Wi-Fi 연결 성공 후 카메라 연결 시도")

                val connectionSuccess = ptpipDataSource.connectToCamera(camera, forceApMode = true)
                if (!connectionSuccess) {
                    Log.e(TAG, "카메라 연결 실패 - 네트워크 상태 재확인")
                    _errorMessage.value = "카메라 연결에 실패했습니다.\n네트워크 상태를 확인하고 다시 시도해주세요."
                    _isConnecting.value = false // 연결 실패 시 로딩 해제
                } else {
                    _isConnecting.value = false // 연결 성공 시 로딩 해제
                }

            } catch (e: Exception) {
                Log.e(TAG, "Wi-Fi 연결 후 카메라 연결 과정에서 오류", e)
                _errorMessage.value = "카메라 연결 과정에서 오류가 발생했습니다: ${e.message}"
                _isConnecting.value = false // 예외 시 로딩 해제
            }
        }
    }

    /**
     * PTPIP 기능 활성화/비활성화
     */
    fun setPtpipEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesDataSource.setPtpipEnabled(enabled)
            if (!enabled) {
                disconnect()
            }
        }
    }

    /**
     * Wi-Fi 연결 활성화/비활성화
     */
    fun setWifiConnectionModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesDataSource.setWifiConnectionModeEnabled(enabled)
        }
    }

    /**
     * 자동 카메라 검색 활성화/비활성화
     */
    fun setAutoDiscoveryEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesDataSource.setAutoDiscoveryEnabled(enabled)
        }
    }

    /**
     * 자동 연결 활성화/비활성화
     */
    fun setAutoConnectEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesDataSource.setAutoConnectEnabled(enabled)
        }
    }

    /**
     * 연결 타임아웃 설정
     */
    fun setConnectionTimeout(timeout: Int) {
        viewModelScope.launch {
            preferencesDataSource.setConnectionTimeout(timeout)
        }
    }

    /**
     * 카메라 검색 타임아웃 설정
     */
    fun setDiscoveryTimeout(timeout: Int) {
        viewModelScope.launch {
            preferencesDataSource.setDiscoveryTimeout(timeout)
        }
    }

    /**
     * PTPIP 포트 설정
     */
    fun setPtpipPort(port: Int) {
        viewModelScope.launch {
            preferencesDataSource.setPtpipPort(port)
        }
    }

    /**
     * Wi-Fi 네트워크에서 PTPIP 카메라 검색
     */
    fun discoverCameras(forceApMode: Boolean = false) {
        if (_isDiscovering.value) {
            Log.w(TAG, "이미 카메라 검색 중입니다")
            return
        }

        Log.i(TAG, "사용자가 카메라 검색을 요청했습니다")

        viewModelScope.launch {
            try {
                _isDiscovering.value = true
                _errorMessage.value = null

                // Wi-Fi 연결 상태 확인
                if (!ptpipDataSource.isWifiConnected()) {
                    val errorMsg = "Wi-Fi가 연결되어 있지 않습니다. Wi-Fi를 켜고 네트워크에 연결해주세요."
                    Log.w(TAG, errorMsg)
                    _errorMessage.value = errorMsg
                    return@launch
                }

                // 네트워크 상태 확인
                val networkState = ptpipDataSource.getCurrentWifiNetworkState()
                if (networkState.isConnectedToCameraAP) {
                    Log.i(TAG, "✅ AP 모드 연결 감지됨: ${networkState.ssid}")
                    Log.i(TAG, "카메라 IP: ${networkState.detectedCameraIP}")
                } else {
                    Log.i(TAG, "STA 모드 또는 일반 네트워크 연결")
                }

                Log.i(TAG, "Wi-Fi 연결 확인됨, 카메라 검색 시작...")
                val cameras = ptpipDataSource.discoverCameras(forceApMode)

                Log.i(TAG, "카메라 검색 완료: ${cameras.size}개 발견")

                if (cameras.isEmpty()) {
                    val errorMsg = if (networkState.isConnectedToCameraAP) {
                        "카메라 AP에 연결되어 있지만 카메라를 찾을 수 없습니다.\n" +
                                "카메라의 Wi-Fi 설정을 확인하고 다시 시도해주세요."
                    } else {
                        "PTPIP 지원 카메라를 찾을 수 없습니다. 같은 네트워크에 카메라가 연결되어 있는지 확인해주세요."
                    }
                    Log.w(TAG, errorMsg)
                    _errorMessage.value = errorMsg
                } else {
                    Log.i(TAG, "✅ 카메라 검색 성공:")
                    cameras.forEachIndexed { index, camera ->
                        Log.i(
                            TAG,
                            "  ${index + 1}. ${camera.name} (${camera.ipAddress}:${camera.port})"
                        )
                    }
                    _errorMessage.value = null
                }

            } catch (e: Exception) {
                val errorMsg = "카메라 검색 중 오류가 발생했습니다: ${e.message}"
                Log.e(TAG, errorMsg, e)
                _errorMessage.value = errorMsg
            } finally {
                _isDiscovering.value = false
                Log.d(TAG, "카메라 검색 작업 완료")
            }
        }
    }

    // 호환성용 무파라미터 오버로드 유지
    fun discoverCameras() = discoverCameras(false)

    // AP/STA 전용 헬퍼 함수 추가
    fun discoverCamerasAp() = discoverCameras(true)
    fun discoverCamerasSta() = discoverCameras(false)

    /**
     * 카메라 연결 (AP/STA 모드 지원)
     */
    fun connectToCamera(camera: PtpipCamera, forceApMode: Boolean = false) {
        viewModelScope.launch {
            try {
                _isConnecting.value = true
                _errorMessage.value = null
                _autoDownloadEnabled.value = false // 연결 시도 중에는 비활성화

                val success = ptpipDataSource.connectToCamera(camera, forceApMode)
                if (success) {
                    _errorMessage.value = null
                    _selectedCamera.value = camera

                    // 연결 성공 시 자동 다운로드 활성화 (파일 수신 전용)
                    _autoDownloadEnabled.value = true

                    // 연결 모드에 따른 메시지 설정
                    if (globalManager.isApModeConnected()) {
                        Log.i(TAG, "AP 모드 연결 완료 - 파일 수신 리스너 활성화 (자동 촬영 없음)")
                    } else {
                        Log.i(TAG, "카메라 연결 완료 - 파일 수신 리스너 활성화 (자동 촬영 없음)")
                    }
                    // 연결 성공 시 마지막 연결 정보 저장
                    preferencesDataSource.saveLastConnectedCamera(camera.ipAddress, camera.name)

                    // 연결 완료 후 로딩 해제는 PtpipDataSource의 상태 변화에 따라 처리됨
                } else {
                    _errorMessage.value = "카메라 연결에 실패했습니다"
                    _autoDownloadEnabled.value = false
                    _isConnecting.value = false // 실패 시 로딩 해제
                }
            } catch (e: Exception) {
                _errorMessage.value = "카메라 연결 중 오류가 발생했습니다: ${e.message}"
                _autoDownloadEnabled.value = false
                _isConnecting.value = false // 예외 시 로딩 해제
                Log.e(TAG, "카메라 연결 중 오류", e)
            }
        }
    }

    fun connectToCameraAp(camera: PtpipCamera) = connectToCamera(camera, true)

    fun connectToCameraSta(camera: PtpipCamera) = connectToCamera(camera, false)

    /**
     * 카메라 연결 해제
     */
    fun disconnect() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "카메라 연결 해제 시작")

                // 자동 다운로드 비활성화
                _autoDownloadEnabled.value = false
                _lastDownloadedFile.value = null

                ptpipDataSource.disconnect()

                _selectedCamera.value = null
                _errorMessage.value = null

                // Wi-Fi 퍼포먼스 락 해제
                val wifiHelper = ptpipDataSource.getWifiHelper()
                wifiHelper.releaseWifiLock()

                Log.d(TAG, "카메라 연결 해제 완료")
            } catch (e: Exception) {
                Log.e(TAG, "카메라 연결 해제 중 오류", e)
            }
        }
    }

    /**
     * 수동 사진 촬영 (사용자 요청 시에만 실행)
     */
    fun capturePhoto(listener: CameraCaptureListener? = null) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "수동 사진 촬영 시작 (사용자 요청)")

                if (connectionState.value != PtpipConnectionState.CONNECTED) {
                    _errorMessage.value = "카메라가 연결되어 있지 않습니다."
                    listener?.onCaptureFailed(-1)
                    return@launch
                }

                // 자동 다운로드가 활성화된 경우 리스너 생성
                val captureListener = if (_autoDownloadEnabled.value) {
                    object : CameraCaptureListener {
                        override fun onFlushComplete() {
                            Log.d(TAG, "수동 촬영: 플러시 완료")
                            listener?.onFlushComplete()
                        }

                        override fun onPhotoCaptured(filePath: String, fileName: String) {
                            Log.i(TAG, "수동 촬영: 사진 자동 저장됨 - $fileName")
                            Log.i(TAG, "저장 경로: $filePath")

                            // UI 상태 업데이트
                            _lastDownloadedFile.value = fileName
                            _errorMessage.value = null

                            // 원래 리스너도 호출
                            listener?.onPhotoCaptured(filePath, fileName)
                        }

                        override fun onPhotoDownloaded(
                            filePath: String,
                            fileName: String,
                            imageData: ByteArray
                        ) {
                            Log.i(TAG, "수동 촬영: Native 다운로드 완료 - $fileName")
                            Log.i(TAG, "데이터 크기: ${imageData.size / 1024}KB")

                            // UI 상태 업데이트
                            _lastDownloadedFile.value = fileName
                            _errorMessage.value = null

                            // 원래 리스너도 호출 (있다면)
                            if (listener is CameraCaptureListener) {
                                listener.onPhotoDownloaded(filePath, fileName, imageData)
                            }
                        }

                        override fun onCaptureFailed(errorCode: Int) {
                            Log.e(TAG, "수동 촬영: 촬영 실패 (에러 코드: $errorCode)")
                            _errorMessage.value = "촬영에 실패했습니다 (에러 코드: $errorCode)"
                            listener?.onCaptureFailed(errorCode)
                        }

                        override fun onUsbDisconnected() {
                            Log.w(TAG, "USB 분리 이벤트 - PTPIP는 영향받지 않음")
                            // PTPIP 연결에서는 USB 분리 이벤트가 관련없으므로 무시
                        }
                    }
                } else {
                    // 자동 다운로드 비활성화 시 기본 리스너 사용
                    object : CameraCaptureListener {
                        override fun onFlushComplete() {
                            Log.d(TAG, "수동 촬영: 플러시 완료")
                            listener?.onFlushComplete()
                        }

                        override fun onPhotoCaptured(filePath: String, fileName: String) {
                            Log.i(TAG, "수동 촬영: 성공 $fileName -> $filePath")
                            _errorMessage.value = null
                            listener?.onPhotoCaptured(filePath, fileName)
                        }

                        override fun onPhotoDownloaded(
                            filePath: String,
                            fileName: String,
                            imageData: ByteArray
                        ) {
                            Log.i(TAG, "수동 촬영: Native 다운로드 완료 (자동 다운로드 비활성화) - $fileName")
                            Log.i(TAG, "데이터 크기: ${imageData.size / 1024}KB")

                            // 원래 리스너도 호출 (있다면)
                            if (listener is CameraCaptureListener) {
                                listener.onPhotoDownloaded(filePath, fileName, imageData)
                            }
                        }

                        override fun onCaptureFailed(errorCode: Int) {
                            Log.e(TAG, "수동 촬영: 실패 에러 코드 $errorCode")
                            _errorMessage.value = "촬영에 실패했습니다 (에러 코드: $errorCode)"
                            listener?.onCaptureFailed(errorCode)
                        }

                        override fun onUsbDisconnected() {
                            Log.w(TAG, "USB 분리 이벤트 - PTPIP는 영향받지 않음")
                            // PTPIP 연결에서는 USB 분리 이벤트가 관련없으므로 무시
                        }
                    }
                }

                // 수동 촬영 명령 실행
                ptpipDataSource.capturePhoto(captureListener)
                Log.d(TAG, "수동 촬영 명령 전송 완료")

            } catch (e: Exception) {
                val msg = "수동 촬영 중 오류가 발생했습니다: ${e.message}"
                Log.e(TAG, msg, e)
                _errorMessage.value = msg
                listener?.onCaptureFailed(-1)
            }
        }
    }

    /**
     * 에러 메시지 클리어
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * 연결 끊어짐 메시지 클리어
     */
    fun clearConnectionLostMessage() {
        ptpipDataSource.clearConnectionLostMessage()
    }

    /**
     * 연결 중 상태 설정
     */
    fun setIsConnecting(connecting: Boolean) {
        _isConnecting.value = connecting
    }

    /**
     * 카메라 선택
     */
    fun selectCamera(camera: PtpipCamera) {
        _selectedCamera.value = camera
    }

    /**
     * PTPIP 설정 초기화
     */
    fun resetSettings() {
        viewModelScope.launch {
            preferencesDataSource.clearAllSettings()
            disconnect()
        }
    }

    /**
     * Wi-Fi 연결 상태 확인
     */
    fun isWifiConnected(): Boolean {
        return ptpipDataSource.isWifiConnected()
    }

    /**
     * Wi-Fi STA 동시 연결 지원 여부 확인
     */
    fun isStaConcurrencySupported(): Boolean {
        return ptpipDataSource.isStaConcurrencySupported()
    }

    /**
     * Wi-Fi 기능 상세 정보 가져오기
     */
    fun getWifiCapabilities(): WifiCapabilities {
        return ptpipDataSource.getWifiCapabilities()
    }

    /**
     * 현재 연결 상태 문자열 반환
     */
    fun getConnectionStatusText(): String {
        return when (connectionState.value) {
            PtpipConnectionState.DISCONNECTED -> "연결 안됨"
            PtpipConnectionState.CONNECTING -> "연결 중..."
            PtpipConnectionState.CONNECTED -> "연결됨"
            PtpipConnectionState.ERROR -> "연결 오류"
        }
    }

    /**
     * 1단계: 기본 PTPIP 연결 테스트
     */
    fun testBasicPtpipConnection(camera: PtpipCamera) {
        Log.i(TAG, "=== 디버그: 기본 PTPIP 연결 테스트 시작 ===")
        
        viewModelScope.launch {
            try {
                _isConnecting.value = true
                _errorMessage.value = null

                // 연결 전 잠시 대기 (카메라 안정화)
                delay(1000)

                // PtpipConnectionManager를 통한 기본 연결 테스트
                val connectionManager = ptpipDataSource.getConnectionManager()
                val success = connectionManager.establishConnection(camera)
                
                if (success) {
                    Log.i(TAG, "✅ 기본 PTPIP 연결 성공")

                    // 연결 후 잠시 대기 (카메라와의 통신 안정화)
                    delay(500)

                    // 디바이스 정보 가져오기
                    val deviceInfo = connectionManager.getDeviceInfo()
                    if (deviceInfo != null) {
                        Log.i(TAG, "✅ 디바이스 정보 획득 성공: ${deviceInfo.manufacturer} ${deviceInfo.model}")
                        _errorMessage.value = "기본 연결 성공: ${deviceInfo.manufacturer} ${deviceInfo.model}"

                        // 정보 획득 후 잠시 대기 (카메라에게 처리 시간 제공)
                        delay(1000)
                    } else {
                        Log.w(TAG, "⚠️ 디바이스 정보 획득 실패")
                        _errorMessage.value = "기본 연결 성공하지만 디바이스 정보 없음"
                    }

                    // ⚠️ 중요: 연결 해제하지 않음 (카메라 Wi-Fi 종료 방지)
                    // 실제 사용에서는 니콘 카메라 확인 후 STA 모드로 전환하므로
                    // 연결을 유지해야 함
                    Log.d(TAG, "✅ 연결 유지 (카메라 Wi-Fi 종료 방지)")
                } else {
                    Log.e(TAG, "❌ 기본 PTPIP 연결 실패")
                    _errorMessage.value = "기본 PTPIP 연결 실패"
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "기본 연결 테스트 중 오류: ${e.message}")
                _errorMessage.value = "기본 연결 테스트 오류: ${e.message}"
            } finally {
                _isConnecting.value = false
            }
        }
    }

    /**
     * 2단계: 니콘 Phase 1 인증 테스트
     */
    fun testNikonPhase1Authentication(camera: PtpipCamera) {
        Log.i(TAG, "=== 디버그: 니콘 Phase 1 인증 테스트 시작 ===")
        
        viewModelScope.launch {
            try {
                _isConnecting.value = true
                _errorMessage.value = null
                
                // NikonAuthenticationService를 통한 Phase 1 테스트
                val authService = ptpipDataSource.getNikonAuthService()
                val success = authService.testPhase1Authentication(camera)
                
                if (success) {
                    Log.i(TAG, "✅ 니콘 Phase 1 인증 성공")
                    _errorMessage.value = "Phase 1 인증 성공"
                } else {
                    Log.e(TAG, "❌ 니콘 Phase 1 인증 실패")
                    _errorMessage.value = "Phase 1 인증 실패"
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Phase 1 테스트 중 오류: ${e.message}")
                _errorMessage.value = "Phase 1 테스트 오류: ${e.message}"
            } finally {
                _isConnecting.value = false
            }
        }
    }

    /**
     * 3단계: 니콘 Phase 2 인증 테스트
     */
    fun testNikonPhase2Authentication(camera: PtpipCamera) {
        Log.i(TAG, "=== 디버그: 니콘 Phase 2 인증 테스트 시작 ===")
        
        viewModelScope.launch {
            try {
                _isConnecting.value = true
                _errorMessage.value = null
                
                // NikonAuthenticationService를 통한 Phase 2 테스트
                val authService = ptpipDataSource.getNikonAuthService()
                val success = authService.testPhase2Authentication(camera)
                
                if (success) {
                    Log.i(TAG, "✅ 니콘 Phase 2 인증 성공")
                    _errorMessage.value = "Phase 2 인증 성공"
                } else {
                    Log.e(TAG, "❌ 니콘 Phase 2 인증 실패")
                    _errorMessage.value = "Phase 2 인증 실패"
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Phase 2 테스트 중 오류: ${e.message}")
                _errorMessage.value = "Phase 2 테스트 오류: ${e.message}"
            } finally {
                _isConnecting.value = false
            }
        }
    }

    /**
     * 4단계: 개별 니콘 명령 테스트
     */
    fun testNikonCommand(camera: PtpipCamera, command: String) {
        Log.i(TAG, "=== 디버그: 니콘 명령 테스트 시작 ($command) ===")
        
        viewModelScope.launch {
            try {
                _isConnecting.value = true
                _errorMessage.value = null
                
                val authService = ptpipDataSource.getNikonAuthService()
                val success = when (command) {
                    "0x952b" -> authService.testNikon952bCommand(camera)
                    "0x935a" -> authService.testNikon935aCommand(camera)
                    "GetDeviceInfo" -> authService.testGetDeviceInfo(camera)
                    "OpenSession" -> authService.testOpenSession(camera)
                    else -> false
                }
                
                if (success) {
                    Log.i(TAG, "✅ 니콘 명령 ($command) 성공")
                    _errorMessage.value = "$command 명령 성공"
                } else {
                    Log.e(TAG, "❌ 니콘 명령 ($command) 실패")
                    _errorMessage.value = "$command 명령 실패"
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "명령 테스트 중 오류: ${e.message}")
                _errorMessage.value = "명령 테스트 오류: ${e.message}"
            } finally {
                _isConnecting.value = false
            }
        }
    }

    /**
     * 5단계: 소켓 연결 테스트
     */
    fun testSocketConnection(camera: PtpipCamera) {
        Log.i(TAG, "=== 디버그: 소켓 연결 테스트 시작 ===")
        
        viewModelScope.launch {
            try {
                _isConnecting.value = true
                _errorMessage.value = null
                
                val authService = ptpipDataSource.getNikonAuthService()
                val success = authService.testSocketConnection(camera)
                
                if (success) {
                    Log.i(TAG, "✅ 소켓 연결 성공")
                    _errorMessage.value = "소켓 연결 성공"
                } else {
                    Log.e(TAG, "❌ 소켓 연결 실패")
                    _errorMessage.value = "소켓 연결 실패"
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "소켓 연결 테스트 중 오류: ${e.message}")
                _errorMessage.value = "소켓 연결 테스트 오류: ${e.message}"
            } finally {
                _isConnecting.value = false
            }
        }
    }

    /**
     * 6단계: 포트 스캔 테스트
     */
    fun testPortScan(ipAddress: String) {
        Log.i(TAG, "=== 디버그: 포트 스캔 테스트 시작 ===")
        
        viewModelScope.launch {
            try {
                _isConnecting.value = true
                _errorMessage.value = null
                
                val authService = ptpipDataSource.getNikonAuthService()
                val openPorts = authService.scanPorts(ipAddress)
                
                if (openPorts.isNotEmpty()) {
                    Log.i(TAG, "✅ 열린 포트 발견: ${openPorts.joinToString(", ")}")
                    _errorMessage.value = "열린 포트: ${openPorts.joinToString(", ")}"
                } else {
                    Log.w(TAG, "⚠️ 열린 포트 없음")
                    _errorMessage.value = "열린 포트 없음"
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "포트 스캔 테스트 중 오류: ${e.message}")
                _errorMessage.value = "포트 스캔 테스트 오류: ${e.message}"
            } finally {
                _isConnecting.value = false
            }
        }
    }

    /**
     * 자동 재연결 활성화/비활성화
     */
    fun setAutoReconnectEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesDataSource.setAutoReconnectEnabled(enabled)
        }
    }

    /**
     * 현재 Wi-Fi 네트워크 상태 확인
     */
    fun getCurrentWifiNetworkState(): WifiNetworkState {
        return wifiNetworkState.value
    }

    /**
     * 네트워크 상태 기반 상태 메시지 반환
     */
    fun getNetworkStatusMessage(): String {
        val networkState = wifiNetworkState.value
        return when {
            !networkState.isConnected -> "Wi-Fi 연결 안됨"
            networkState.isConnectedToCameraAP -> "카메라 AP 연결됨 (${networkState.ssid})"
            networkState.ssid != null -> "일반 Wi-Fi 연결됨 (${networkState.ssid})"
            else -> "네트워크 연결됨"
        }
    }

    /**
     * 카메라 연결 상태와 네트워크 상태를 종합한 상태 메시지
     */
    fun getComprehensiveStatusMessage(): String {
        val connectionState = connectionState.value
        val networkState = wifiNetworkState.value

        return when {
            !networkState.isConnected -> "Wi-Fi 연결 필요"
            connectionState == PtpipConnectionState.CONNECTED -> "카메라 연결됨"
            connectionState == PtpipConnectionState.CONNECTING -> "카메라 연결 중..."
            connectionState == PtpipConnectionState.ERROR -> "카메라 연결 오류"
            networkState.isConnectedToCameraAP -> "카메라 AP 연결됨 - 카메라 검색 가능"
            else -> "카메라 연결 안됨"
        }
    }

    /**
     * AP 모드 연결 여부 확인
     */
    fun isApModeConnected() = globalManager.isApModeConnected()

    /**
     * STA 모드 연결 여부 확인
     */
    fun isStaModeConnected() = globalManager.isStaModeConnected()

    /**
     * 자동 검색 시작
     */
    private fun startAutoDiscovery() {
        // 자동 검색 로직 추가
    }

    /**
     * 자동 검색 중단
     */
    private fun stopAutoDiscovery() {
        // 자동 검색 중단 로직 추가
    }

    /**
     * 자동 다운로드 활성화/비활성화
     */
    fun setAutoDownloadEnabled(enabled: Boolean) {
        _autoDownloadEnabled.value = enabled
        Log.d(TAG, "자동 다운로드 ${if (enabled) "활성화" else "비활성화"}")
    }

    /**
     * 마지막 다운로드 파일 정보 초기화
     */
    fun clearLastDownloadedFile() {
        _lastDownloadedFile.value = null
    }

    /**
     * 디버그용: WifiNetworkHelper 접근
     */
    fun getWifiHelper() = ptpipDataSource.getWifiHelper()

    override fun onCleared() {
        super.onCleared()
        // ViewModel이 소멸될 때 연결 정리
        viewModelScope.launch {
            ptpipDataSource.disconnect()
            ptpipDataSource.cleanup()
            globalManager.cleanup()

            // Wi-Fi 락이 남아있으면 해제
            try {
                val wifiHelper = ptpipDataSource.getWifiHelper()
                if (wifiHelper.isWifiLockHeld()) {
                    wifiHelper.releaseWifiLock()
                    Log.d(TAG, "ViewModel 소멸 시 Wi-Fi 락 해제")
                }
            } catch (e: Exception) {
                Log.e(TAG, "ViewModel 소멸 시 Wi-Fi 락 해제 실패: ${e.message}")
            }
        }
    }
}