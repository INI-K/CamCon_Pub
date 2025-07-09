package com.inik.camcon.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.data.datasource.local.PtpipPreferencesDataSource
import com.inik.camcon.data.datasource.ptpip.PtpipDataSource
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.PtpipConnectionState
import com.inik.camcon.domain.model.WifiCapabilities
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
    private val preferencesDataSource: PtpipPreferencesDataSource
) : ViewModel() {

    companion object {
        private const val TAG = "PtpipViewModel"
    }

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
    val isWifiConnectionModeEnabled = preferencesDataSource.isWifiConnectionModeEnabled
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
        isWifiConnectionModeEnabled
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
    fun discoverCameras() {
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

                Log.i(TAG, "Wi-Fi 연결 확인됨, 카메라 검색 시작...")
                val cameras = ptpipDataSource.discoverCameras()

                if (cameras.isEmpty()) {
                    val errorMsg = "PTPIP 지원 카메라를 찾을 수 없습니다. 같은 네트워크에 카메라가 연결되어 있는지 확인해주세요."
                    Log.w(TAG, errorMsg)
                    _errorMessage.value = errorMsg
                } else {
                    Log.i(TAG, "검색 완료: " + cameras.size + "개 카메라 발견")
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

    /**
     * 특정 카메라에 연결
     */
    fun connectToCamera(camera: PtpipCamera) {
        Log.i(TAG, "카메라 연결 요청: ${camera.name} (${camera.ipAddress}:${camera.port})")

        if (_isConnecting.value) {
            Log.w(TAG, "이미 연결 시도 중입니다")
            return
        }

        Log.d(TAG, "연결 시작 - _isConnecting을 true로 설정")

        viewModelScope.launch {
            try {
                _isConnecting.value = true
                _errorMessage.value = null
                _selectedCamera.value = camera

                Log.d(TAG, "PTPIP 데이터소스 연결 시도 시작")
                val success = ptpipDataSource.connectToCamera(camera)

                Log.d(TAG, "연결 결과: ${if (success) "성공" else "실패"}")

                if (success) {
                    Log.i(TAG, "카메라 연결 성공")
                    // 연결 성공 시 마지막 연결 정보 저장
                    preferencesDataSource.saveLastConnectedCamera(camera.ipAddress, camera.name)
                } else {
                    Log.w(TAG, "카메라 연결 실패")
                    _errorMessage.value = "카메라 연결에 실패했습니다."
                    _selectedCamera.value = null
                }

            } catch (e: Exception) {
                Log.e(TAG, "연결 중 예외 발생", e)
                _errorMessage.value = "연결 중 오류가 발생했습니다: ${e.message}"
                _selectedCamera.value = null
            } finally {
                Log.d(TAG, "연결 시도 완료 - _isConnecting을 false로 설정")
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

    override fun onCleared() {
        super.onCleared()
        // ViewModel이 소멸될 때 연결 정리
        viewModelScope.launch {
            ptpipDataSource.disconnect()
        }
    }
}