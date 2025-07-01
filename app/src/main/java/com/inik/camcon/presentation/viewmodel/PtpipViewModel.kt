package com.inik.camcon.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.data.datasource.local.PtpipPreferencesDataSource
import com.inik.camcon.data.datasource.ptpip.PtpipCamera
import com.inik.camcon.data.datasource.ptpip.PtpipConnectionState
import com.inik.camcon.data.datasource.ptpip.PtpipDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val preferencesDataSource: PtpipPreferencesDataSource
) : ViewModel() {

    // PTPIP 연결 상태
    val connectionState = ptpipDataSource.connectionState

    // 발견된 카메라 목록
    val discoveredCameras = ptpipDataSource.discoveredCameras

    // 현재 연결된 카메라 정보
    val cameraInfo = ptpipDataSource.cameraInfo

    // PTPIP 설정 상태
    val isPtpipEnabled = preferencesDataSource.isPtpipEnabled
    val isAutoDiscoveryEnabled = preferencesDataSource.isAutoDiscoveryEnabled
    val isAutoConnectEnabled = preferencesDataSource.isAutoConnectEnabled
    val isWifiStaModeEnabled = preferencesDataSource.isWifiStaModeEnabled
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

    // 종합 상태 (PTPIP 활성화 + Wi-Fi 연결)
    val isPtpipAvailable = combine(
        isPtpipEnabled,
        isWifiStaModeEnabled
    ) { enabled, wifiEnabled ->
        enabled && wifiEnabled && ptpipDataSource.isWifiConnected()
    }

    init {
        // 자동 연결 설정이 활성화된 경우 마지막 연결 카메라로 자동 연결 시도
        viewModelScope.launch {
            combine(
                isAutoConnectEnabled,
                lastConnectedIp,
                lastConnectedName
            ) { autoConnect, ip, name ->
                if (autoConnect && ip != null && name != null) {
                    val camera = PtpipCamera(
                        ipAddress = ip,
                        port = 15740,
                        name = name,
                        isOnline = true
                    )
                    connectToCamera(camera)
                }
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
     * Wi-Fi STA 모드 활성화/비활성화
     */
    fun setWifiStaModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesDataSource.setWifiStaModeEnabled(enabled)
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
    fun discoverCameras() {
        if (_isDiscovering.value) return

        viewModelScope.launch {
            try {
                _isDiscovering.value = true
                _errorMessage.value = null

                if (!ptpipDataSource.isWifiConnected()) {
                    _errorMessage.value = "Wi-Fi에 연결되어 있지 않습니다."
                    return@launch
                }

                val cameras = ptpipDataSource.discoverCameras()
                if (cameras.isEmpty()) {
                    _errorMessage.value = "PTPIP 지원 카메라를 찾을 수 없습니다."
                }

            } catch (e: Exception) {
                _errorMessage.value = "카메라 검색 중 오류가 발생했습니다: ${e.message}"
            } finally {
                _isDiscovering.value = false
            }
        }
    }

    /**
     * 특정 카메라에 연결
     */
    fun connectToCamera(camera: PtpipCamera) {
        if (_isConnecting.value) return

        viewModelScope.launch {
            try {
                _isConnecting.value = true
                _errorMessage.value = null
                _selectedCamera.value = camera

                val success = ptpipDataSource.connectToCamera(camera)
                if (success) {
                    // 연결 성공 시 마지막 연결 정보 저장
                    preferencesDataSource.saveLastConnectedCamera(camera.ipAddress, camera.name)
                } else {
                    _errorMessage.value = "카메라 연결에 실패했습니다."
                    _selectedCamera.value = null
                }

            } catch (e: Exception) {
                _errorMessage.value = "연결 중 오류가 발생했습니다: ${e.message}"
                _selectedCamera.value = null
            } finally {
                _isConnecting.value = false
            }
        }
    }

    /**
     * 카메라 연결 해제
     */
    fun disconnect() {
        viewModelScope.launch {
            try {
                ptpipDataSource.disconnect()
                _selectedCamera.value = null
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "연결 해제 중 오류가 발생했습니다: ${e.message}"
            }
        }
    }

    /**
     * 원격 촬영 실행
     */
    fun capturePhoto() {
        viewModelScope.launch {
            try {
                if (connectionState.value != PtpipConnectionState.CONNECTED) {
                    _errorMessage.value = "카메라가 연결되어 있지 않습니다."
                    return@launch
                }

                val success = ptpipDataSource.capturePhoto()
                if (!success) {
                    _errorMessage.value = "촬영에 실패했습니다."
                }

            } catch (e: Exception) {
                _errorMessage.value = "촬영 중 오류가 발생했습니다: ${e.message}"
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

    override fun onCleared() {
        super.onCleared()
        // ViewModel이 소멸될 때 연결 정리
        viewModelScope.launch {
            ptpipDataSource.disconnect()
        }
    }
}