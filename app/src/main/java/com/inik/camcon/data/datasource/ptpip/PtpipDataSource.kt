package com.inik.camcon.data.datasource.ptpip

import android.content.Context
import android.util.Log
import com.inik.camcon.CameraNative
import com.inik.camcon.data.network.ptpip.authentication.NikonAuthenticationService
import com.inik.camcon.data.network.ptpip.connection.PtpipConnectionManager
import com.inik.camcon.data.network.ptpip.discovery.PtpipDiscoveryService
import com.inik.camcon.data.network.ptpip.wifi.WifiNetworkHelper
import com.inik.camcon.domain.model.NikonConnectionMode
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.PtpipCameraInfo
import com.inik.camcon.domain.model.PtpipConnectionState
import com.inik.camcon.domain.model.WifiCapabilities
import com.inik.camcon.domain.model.WifiNetworkState
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
    private val wifiHelper: WifiNetworkHelper
) {
    private var connectedCamera: PtpipCamera? = null
    private var lastConnectedCamera: PtpipCamera? = null
    private var isAutoReconnectEnabled = false
    private var networkMonitoringJob: Job? = null
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // StateFlow for UI observation
    private val _connectionState = MutableStateFlow(PtpipConnectionState.DISCONNECTED)
    val connectionState: StateFlow<PtpipConnectionState> = _connectionState.asStateFlow()

    private val _discoveredCameras = MutableStateFlow<List<PtpipCamera>>(emptyList())
    val discoveredCameras: StateFlow<List<PtpipCamera>> = _discoveredCameras.asStateFlow()

    private val _cameraInfo = MutableStateFlow<PtpipCameraInfo?>(null)
    val cameraInfo: StateFlow<PtpipCameraInfo?> = _cameraInfo.asStateFlow()

    private val _wifiNetworkState = MutableStateFlow(WifiNetworkState(false, false, null, null))
    val wifiNetworkState: StateFlow<WifiNetworkState> = _wifiNetworkState.asStateFlow()

    companion object {
        private const val TAG = "PtpipDataSource"
        private const val RECONNECT_DELAY_MS = 3000L
    }

    init {
        startNetworkMonitoring()
    }

    /**
     * 네트워크 상태 모니터링 시작
     */
    private fun startNetworkMonitoring() {
        networkMonitoringJob = wifiHelper.networkStateFlow
            .onEach { networkState ->
                Log.d(TAG, "네트워크 상태 변화: $networkState")
                _wifiNetworkState.value = networkState
                
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
        coroutineScope.launch {
            val currentState = _connectionState.value
            
            when {
                // Wi-Fi 연결 해제됨
                !networkState.isConnected -> {
                    if (currentState == PtpipConnectionState.CONNECTED) {
                        Log.i(TAG, "Wi-Fi 연결 해제됨 - 카메라 연결 해제")
                        _connectionState.value = PtpipConnectionState.DISCONNECTED
                        connectedCamera = null
                    }
                }
                
                // Wi-Fi 연결되고 이전에 연결된 카메라가 있는 경우
                networkState.isConnected && lastConnectedCamera != null -> {
                    if (currentState == PtpipConnectionState.DISCONNECTED) {
                        Log.i(TAG, "Wi-Fi 연결됨 - 이전 카메라 자동 재연결 시도")
                        delay(RECONNECT_DELAY_MS)
                        
                        // AP 모드에서 카메라 IP 업데이트
                        val cameraToConnect = if (networkState.isConnectedToCameraAP && networkState.detectedCameraIP != null) {
                            lastConnectedCamera!!.copy(ipAddress = networkState.detectedCameraIP)
                        } else {
                            lastConnectedCamera!!
                        }
                        
                        attemptAutoReconnect(cameraToConnect)
                    }
                }
                
                // 같은 Wi-Fi에 연결되었지만 카메라 IP가 변경된 경우
                networkState.isConnected && networkState.isConnectedToCameraAP && 
                networkState.detectedCameraIP != null && 
                connectedCamera?.ipAddress != networkState.detectedCameraIP -> {
                    Log.i(TAG, "AP 모드에서 카메라 IP 변경 감지 - 재연결 시도")
                    val updatedCamera = connectedCamera?.copy(ipAddress = networkState.detectedCameraIP)
                    if (updatedCamera != null) {
                        attemptAutoReconnect(updatedCamera)
                    }
                }
            }
        }
    }

    /**
     * 자동 재연결 시도
     */
    private suspend fun attemptAutoReconnect(camera: PtpipCamera) {
        try {
            Log.i(TAG, "자동 재연결 시도: ${camera.name} (${camera.ipAddress})")
            _connectionState.value = PtpipConnectionState.CONNECTING
            
            if (connectToCamera(camera)) {
                Log.i(TAG, "✅ 자동 재연결 성공")
            } else {
                Log.w(TAG, "❌ 자동 재연결 실패")
                _connectionState.value = PtpipConnectionState.ERROR
                delay(5000) // 5초 후 다시 시도
                if (isAutoReconnectEnabled && _connectionState.value == PtpipConnectionState.ERROR) {
                    attemptAutoReconnect(camera)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "자동 재연결 중 오류", e)
            _connectionState.value = PtpipConnectionState.ERROR
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
        Log.d(TAG, "리소스 정리 완료")
    }

    /**
     * mDNS를 사용하여 PTPIP 지원 카메라 검색
     */
    suspend fun discoverCameras(): List<PtpipCamera> {
        return try {
            Log.d(TAG, "카메라 검색 시작")
            
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
            val cameras = discoveryService.discoverCameras()
            _discoveredCameras.value = cameras
            cameras
        } catch (e: Exception) {
            Log.e(TAG, "카메라 검색 중 오류", e)
            emptyList()
        }
    }

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
     * 스마트 카메라 연결 (하이브리드 방식)
     */
    suspend fun connectToCamera(camera: PtpipCamera): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "스마트 카메라 연결 시작: ${camera.name}")
            _connectionState.value = PtpipConnectionState.CONNECTING

            // 이전 연결 정리
            disconnect()

            // Wi-Fi 연결 확인
            if (!wifiHelper.isWifiConnected()) {
                Log.e(TAG, "Wi-Fi 연결이 해제됨")
                _connectionState.value = PtpipConnectionState.ERROR
                return@withContext false
            }

            // 1단계: libgphoto2 PTP/IP 연결 시도 (AP 모드 감지)
            Log.i(TAG, "=== 1단계: libgphoto2 PTP/IP 연결 시도 ===")
            val libDir = context.applicationInfo.nativeLibraryDir
            val ptpipResult = try {
                // AP모드인지 확인
                if (wifiHelper.isConnectedToCameraAP()) {
                    Log.i(TAG, "AP모드 감지: 단순 연결 방식 사용")
                    CameraNative.initCameraForAPMode(camera.ipAddress, camera.port, libDir)
                } else {
                    Log.i(TAG, "STA모드 또는 일반 모드: 복잡한 연결 방식 사용")
                    CameraNative.initCameraWithPtpip(camera.ipAddress, camera.port, libDir)
                }
            } catch (e: Exception) {
                Log.w(TAG, "libgphoto2 초기화 실패: ${e.message}")
                null
            }

            if (ptpipResult == "OK" || ptpipResult == "GP_OK" || 
                ptpipResult?.contains("Success", ignoreCase = true) == true) {
                Log.i(TAG, "✅ AP 모드 감지: libgphoto2 연결 성공!")
                _connectionState.value = PtpipConnectionState.CONNECTED
                connectedCamera = camera
                lastConnectedCamera = camera
                return@withContext true
            }

            // 2단계: 기본 PTPIP 연결로 제조사 확인
            Log.i(TAG, "=== 2단계: 기본 PTPIP 연결로 제조사 확인 ===")
            if (!connectionManager.establishConnection(camera)) {
                Log.e(TAG, "기본 PTPIP 연결 실패")
                _connectionState.value = PtpipConnectionState.ERROR
                return@withContext false
            }

            val deviceInfo = connectionManager.getDeviceInfo()
            if (deviceInfo == null) {
                Log.e(TAG, "장치 정보를 가져올 수 없음")
                connectionManager.closeConnections()
                _connectionState.value = PtpipConnectionState.ERROR
                return@withContext false
            }

            _cameraInfo.value = deviceInfo
            Log.i(TAG, "카메라 정보 확인: ${deviceInfo.manufacturer} ${deviceInfo.model}")

            // 3단계: 니콘 카메라 STA 모드 인증
            val isNikonCamera = isNikonCamera(deviceInfo)
            Log.d(TAG, "니콘 카메라 감지 결과: $isNikonCamera")

            if (isNikonCamera) {
                Log.i(TAG, "=== 3단계: 니콘 STA 모드 인증 ===")

                // 니콘 STA 모드: 기존 PTPIP 연결을 유지하면서 libgphoto2 세션 연결
                Log.d(TAG, "니콘 STA 모드: 기존 PTPIP 연결 유지하며 libgphoto2 세션 연결")

                // 니콘 STA 인증 수행 (기존 연결 유지)
                if (nikonAuthService.performStaAuthentication(camera)) {
                    Log.i(TAG, "✅ 니콘 STA 모드 인증 성공!")

                    // 기존 PTPIP 연결을 유지하면서 libgphoto2 세션 연결
                    val libDir = context.applicationInfo.nativeLibraryDir
                    try {
                        Log.i(TAG, "=== STA 모드: 세션 유지 초기화 시작 ===")
                        val initResult = CameraNative.initCameraWithSessionMaintenance(
                            camera.ipAddress,
                            camera.port,
                            libDir
                        )
                        if (initResult >= 0) {
                            Log.i(TAG, "✅ libgphoto2 세션 유지 초기화 성공!")
                        } else {
                            Log.w(TAG, "❌ libgphoto2 세션 유지 초기화 실패: $initResult (세션 유지)")
                            // 세션 유지 실패해도 계속 진행 - 오류로 처리하지 않음
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "❌ libgphoto2 세션 유지 초기화 실패 (예외): ${e.message}")
                        // 예외가 발생해도 계속 진행 - 오류로 처리하지 않음
                    }

                    // 세션 초기화 성공 여부와 관계없이 연결 상태를 성공으로 설정
                    _connectionState.value = PtpipConnectionState.CONNECTED
                    connectedCamera = camera
                    lastConnectedCamera = camera
                    return@withContext true
                } else {
                    Log.e(TAG, "❌ 니콘 STA 모드 인증 실패")
                    _connectionState.value = PtpipConnectionState.ERROR
                    return@withContext false
                }
            } else {
                Log.i(TAG, "니콘이 아닌 카메라 - 기본 PTPIP 연결 유지")
                _connectionState.value = PtpipConnectionState.CONNECTED
                connectedCamera = camera
                lastConnectedCamera = camera

                // 니콘이 아닌 카메라의 경우 libgphoto2 세션 유지 초기화
                val libDir = context.applicationInfo.nativeLibraryDir
                try {
                    val initResult = CameraNative.initCameraWithSessionMaintenance(
                        camera.ipAddress,
                        camera.port,
                        libDir
                    )
                    if (initResult >= 0) {
                        Log.i(TAG, "✅ libgphoto2 세션 유지 초기화 성공!")
                    } else {
                        Log.w(TAG, "❌ libgphoto2 세션 유지 초기화 실패: $initResult (세션 유지)")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "❌ libgphoto2 세션 유지 초기화 실패 (예외): ${e.message}")
                }

                return@withContext true
            }

        } catch (e: Exception) {
            Log.e(TAG, "카메라 연결 중 오류", e)
            _connectionState.value = PtpipConnectionState.ERROR
            return@withContext false
        }
    }

    private fun isNikonCamera(deviceInfo: PtpipCameraInfo): Boolean {
        // 다양한 방법으로 니콘 카메라 감지
        val manufacturer = deviceInfo.manufacturer.lowercase()
        val model = deviceInfo.model.lowercase()

        // 1. 정확한 문자열 매칭
        if (manufacturer.contains("nikon") || model.contains("nikon")) {
            Log.d(TAG, "니콘 감지: 정확한 문자열 매칭")
            return true
        }

        // 2. 부분 문자열 매칭 (깨진 문자 처리)
        val nikonPatterns = listOf("ikon", "niko", "kon")
        if (nikonPatterns.any { manufacturer.contains(it) || model.contains(it) }) {
            Log.d(TAG, "니콘 감지: 부분 문자열 매칭")
            return true
        }

        // 3. 니콘 카메라 모델명 패턴 확인
        val nikonModelPatterns = listOf("z ", "d", "coolpix", "z8", "z9", "z6", "z7", "z5")
        if (nikonModelPatterns.any { model.contains(it) }) {
            Log.d(TAG, "니콘 감지: 모델명 패턴 매칭")
            return true
        }

        // 4. 바이트 패턴으로 "Nikon" 검사 (UTF-16LE에서 깨진 경우)
        val originalBytes = deviceInfo.manufacturer.toByteArray()
        val nikonBytes = "Nikon".toByteArray()

        // 홀수 인덱스 바이트만 비교 (UTF-16LE에서 ASCII 부분)
        for (i in 0 until originalBytes.size - nikonBytes.size + 1 step 2) {
            var match = true
            for (j in nikonBytes.indices) {
                if (i + j * 2 >= originalBytes.size || originalBytes[i + j * 2] != nikonBytes[j]) {
                    match = false
                    break
                }
            }
            if (match) {
                Log.d(TAG, "니콘 감지: 바이트 패턴 매칭")
                return true
            }
        }

        Log.d(TAG, "니콘 감지 실패: 제조사='$manufacturer', 모델='$model'")
        return false
    }

    /**
     * 카메라 연결 해제
     */
    suspend fun disconnect(keepSession: Boolean = false) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "카메라 연결 해제 시작 (keepSession: $keepSession)")

            // Discovery 중지
            discoveryService.stopDiscovery()

            // libgphoto2 연결 해제
            if (!keepSession) {
                try {
                    CameraNative.closeCamera()
                } catch (e: Exception) {
                    Log.w(TAG, "libgphoto2 연결 해제 중 오류: ${e.message}")
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
            kotlinx.coroutines.delay(1000)

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
     * 사진 촬영
     */
    suspend fun capturePhoto(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "사진 촬영 시작")

            // 현재 연결된 카메라 정보 확인
            val camera = connectedCamera
            if (camera == null) {
                Log.e(TAG, "사진 촬영 실패: 연결된 카메라 없음")
                return@withContext false
            }

            // PTP/IP 연결
            try {
                Log.i(TAG, "STA 모드 세션 유지 방식 시도")

                // STA 모드 세션 유지 기능 사용
                val maintainResult = CameraNative.maintainSessionForStaMode()
                if (maintainResult >= 0) {
                    Log.i(TAG, "STA 모드 세션 유지 성공")

                    // 초기화 성공 후 촬영
                    val result = CameraNative.capturePhoto()
                    Log.d(TAG, "사진 촬영 결과: $result")

                    return@withContext result >= 0
                } else {
                    Log.w(TAG, "❌ STA 모드 세션 유지 실패: $maintainResult")

                    // 세션 유지 실패해도 계속 시도
                    // 여러 번 재시도해도 실패하면 사용자에게 알림 필요

                    // 여전히 촬영 시도
                    val result = CameraNative.capturePhoto()
                    Log.d(TAG, "세션 유지 실패 후 촬영 시도 결과: $result")

                    return@withContext result >= 0
                }
            } catch (e: Exception) {
                Log.e(TAG, "PTP/IP 세션 유지 중 오류", e)
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "사진 촬영 중 오류", e)
            false
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
     * Wi-Fi STA 동시 연결 지원 여부 확인
     */
    fun isStaConcurrencySupported(): Boolean = wifiHelper.isStaConcurrencySupported()

    /**
     * Wi-Fi 기능 상세 정보 가져오기
     */
    fun getWifiCapabilities(): WifiCapabilities = wifiHelper.getWifiCapabilities()

    /**
     * 디버그용: ConnectionManager 접근
     */
    fun getConnectionManager() = connectionManager

    /**
     * 디버그용: NikonAuthenticationService 접근
     */
    fun getNikonAuthService() = nikonAuthService
}