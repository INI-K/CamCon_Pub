package com.inik.camcon.data.datasource.ptpip

import android.content.Context
import android.util.Log
import com.inik.camcon.CameraNative
import com.inik.camcon.data.datasource.nativesource.CameraCaptureListener
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

    /**
     * 자동 재연결 시도
     */
    private suspend fun attemptAutoReconnect(camera: PtpipCamera) {
        try {
            // 이미 연결 시도 중이면 무시
            if (_connectionState.value == PtpipConnectionState.CONNECTING) {
                Log.d(TAG, "이미 연결 시도 중이므로 자동 재연결 무시")
                return
            }

            Log.i(TAG, "자동 재연결 시도: ${camera.name} (${camera.ipAddress})")
            _connectionState.value = PtpipConnectionState.CONNECTING
            
            if (connectToCamera(camera)) {
                Log.i(TAG, "✅ 자동 재연결 성공")
            } else {
                Log.w(TAG, "❌ 자동 재연결 실패")
                _connectionState.value = PtpipConnectionState.ERROR

                // 자동 재연결 활성화 상태에서만 재시도
                if (isAutoReconnectEnabled) {
                    delay(5000) // 5초 후 다시 시도
                    // 여전히 오류 상태이고 자동 재연결이 활성화되어 있으면 재시도
                    if (_connectionState.value == PtpipConnectionState.ERROR && isAutoReconnectEnabled) {
                        Log.i(TAG, "자동 재연결 재시도")
                        attemptAutoReconnect(camera)
                    }
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

                // AP 모드 성공 시 파일 수신 리스너 시작
                startAutomaticFileReceiving(camera)

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

                    // STA 모드에서도 파일 수신 리스너 시작
                    startAutomaticFileReceiving(camera)

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

                // 다른 카메라에서도 파일 수신 리스너 시작
                startAutomaticFileReceiving(camera)

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
     * AP 모드 연결 성공 시 파일 수신 리스너만 시작 (자동 촬영 없음)
     */
    private fun startAutomaticFileReceiving(camera: PtpipCamera) {
        Log.i(TAG, "파일 수신 리스너 시작: ${camera.name} (자동 촬영 없음)")

        coroutineScope.launch {
            try {
                // 파일 수신 전용 리스너 (촬영 명령 없음)
                val fileReceiveListener = object : CameraCaptureListener {
                    override fun onFlushComplete() {
                        Log.d(TAG, "파일 수신: 플러시 완료")
                    }

                    override fun onPhotoCaptured(filePath: String, fileName: String) {
                        Log.i(TAG, "파일 수신: 외부 촬영 파일 자동 다운로드 완료 - $fileName")
                        Log.i(TAG, "파일 경로: $filePath")

                        // 추가 처리 로직 (예: 썸네일 생성, 메타데이터 추출 등)
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

                // 파일 수신 리스너만 시작 (촬영 명령 실행 없음)
                startFileReceiveListener(fileReceiveListener)
                Log.i(TAG, "파일 수신 리스너 시작됨 - 외부 촬영 파일 자동 다운로드 대기 중")

            } catch (e: Exception) {
                Log.e(TAG, "파일 수신 리스너 시작 중 오류", e)
            }
        }
    }

    /**
     * 파일 수신 전용 리스너 시작 (촬영 명령 없음)
     */
    private fun startFileReceiveListener(listener: CameraCaptureListener) {
        try {
            // 카메라 이벤트 리스너 시작 (파일 수신 전용)
            CameraNative.listenCameraEvents(listener)
            Log.i(TAG, "파일 수신 전용 리스너 활성화됨")
        } catch (e: Exception) {
            Log.e(TAG, "파일 수신 리스너 시작 실패", e)
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
    private fun stopAutomaticFileReceiving() {
        try {
            Log.d(TAG, "자동 파일 수신 중지")
            CameraNative.stopListenCameraEvents()
            Log.d(TAG, "카메라 이벤트 리스너 중지됨")
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

            // 자동 파일 수신 중지
            stopAutomaticFileReceiving()

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
     * 자동 다운로드된 파일 처리
     */
    private fun handleAutomaticDownload(filePath: String, fileName: String) {
        coroutineScope.launch {
            try {
                Log.d(TAG, "자동 다운로드 파일 처리 시작: $fileName")

                // 파일 정보 확인
                val fileInfo = java.io.File(filePath)
                if (fileInfo.exists()) {
                    val fileSize = fileInfo.length()
                    Log.i(TAG, "자동 다운로드 완료: $fileName (크기: ${fileSize / 1024}KB)")

                    // 파일 타입 확인
                    val fileExtension = fileName.substringAfterLast(".", "").lowercase()
                    Log.d(TAG, "파일 타입: $fileExtension")

                    // 이미지 파일인 경우 썸네일 생성 시도
                    if (fileExtension in listOf("jpg", "jpeg", "png", "cr2", "nef", "arw", "dng")) {
                        Log.d(TAG, "이미지 파일 감지 - 썸네일 생성 시도")
                        // 썸네일 생성 로직은 추후 구현
                    }

                    // 파일 다운로드 성공 알림
                    Log.i(TAG, "✅ 자동 파일 다운로드 성공: $fileName")

                } else {
                    Log.w(TAG, "❌ 자동 다운로드 파일이 존재하지 않음: $filePath")
                }

            } catch (e: Exception) {
                Log.e(TAG, "자동 다운로드 파일 처리 중 오류", e)
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
}