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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    // StateFlow for UI observation
    private val _connectionState = MutableStateFlow(PtpipConnectionState.DISCONNECTED)
    val connectionState: StateFlow<PtpipConnectionState> = _connectionState.asStateFlow()

    private val _discoveredCameras = MutableStateFlow<List<PtpipCamera>>(emptyList())
    val discoveredCameras: StateFlow<List<PtpipCamera>> = _discoveredCameras.asStateFlow()

    private val _cameraInfo = MutableStateFlow<PtpipCameraInfo?>(null)
    val cameraInfo: StateFlow<PtpipCameraInfo?> = _cameraInfo.asStateFlow()

    companion object {
        private const val TAG = "PtpipDataSource"
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
                CameraNative.initCameraWithPtpip(camera.ipAddress, camera.port, libDir)
            } catch (e: Exception) {
                Log.w(TAG, "libgphoto2 초기화 실패: ${e.message}")
                null
            }

            if (ptpipResult == "OK" || ptpipResult == "GP_OK" || 
                ptpipResult?.contains("Success", ignoreCase = true) == true) {
                Log.i(TAG, "✅ AP 모드 감지: libgphoto2 연결 성공!")
                _connectionState.value = PtpipConnectionState.CONNECTED
                connectedCamera = camera
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

                // 중요: 니콘 카메라의 경우 연결을 유지하고 STA 인증으로 넘어감
                // 연결을 해제하면 카메라가 Wi-Fi를 종료할 수 있음
                Log.d(TAG, "니콘 카메라 감지 - 기존 연결 유지하며 STA 인증 준비")

                // 부드러운 연결 해제 (세션만 종료, 소켓은 유지)
                try {
                    connectionManager.closeSession()
                    Log.d(TAG, "기존 세션 종료 완료")
                } catch (e: Exception) {
                    Log.w(TAG, "세션 종료 중 오류 (무시): ${e.message}")
                }

                // 카메라 안정화 대기 (짧게)
                kotlinx.coroutines.delay(1000)

                // 소켓 연결 해제 (카메라 Wi-Fi 종료 방지를 위해 부드럽게)
                connectionManager.closeConnections()

                // 추가 안정화 대기
                kotlinx.coroutines.delay(2000)

                // 니콘 STA 인증 수행
                if (nikonAuthService.performStaAuthentication(camera)) {
                    Log.i(TAG, "✅ 니콘 STA 모드 인증 성공!")
                    _connectionState.value = PtpipConnectionState.CONNECTED
                    connectedCamera = camera
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
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "카메라 연결 해제 시작")

            // Discovery 중지
            discoveryService.stopDiscovery()

            // libgphoto2 연결 해제
            try {
                CameraNative.closeCamera()
            } catch (e: Exception) {
                Log.w(TAG, "libgphoto2 연결 해제 중 오류: ${e.message}")
            }

            // PTPIP 연결 해제
            connectionManager.closeConnections()

            // 상태 초기화
            connectedCamera = null
            _connectionState.value = PtpipConnectionState.DISCONNECTED
            _cameraInfo.value = null

            Log.d(TAG, "카메라 연결 해제 완료")
        } catch (e: Exception) {
            Log.e(TAG, "카메라 연결 해제 중 오류", e)
        }
    }

    /**
     * gphoto2 접근을 위한 연결 해제
     */
    suspend fun disconnectForGphoto2() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "gphoto2 호환 모드: 연결 해제 시작")

            // 니콘 카메라 특별 처리
            if (connectedCamera?.name?.contains("Nikon", ignoreCase = true) == true) {
                Log.d(TAG, "니콘 카메라 세션 종료")
                connectionManager.closeSession()
                kotlinx.coroutines.delay(2000)
            }

            // 일반 연결 해제
            disconnect()
            kotlinx.coroutines.delay(1000)

            Log.d(TAG, "gphoto2 호환 모드: 연결 해제 완료")
        } catch (e: Exception) {
            Log.e(TAG, "gphoto2 호환 모드 연결 해제 중 오류", e)
        }
    }

    /**
     * 임시 연결 해제
     */
    suspend fun temporaryDisconnect(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "임시 연결 해제 시작")

            val currentCamera = connectedCamera
            val wasConnected = _connectionState.value == PtpipConnectionState.CONNECTED

            if (wasConnected && currentCamera != null) {
                disconnectForGphoto2()
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
            val result = CameraNative.capturePhoto()
            Log.d(TAG, "사진 촬영 결과: $result")
            result >= 0
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

/**
 * PTPIP 연결 상태
 */
enum class PtpipConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

/**
 * 니콘 카메라 연결 모드 (AP/STA/UNKNOWN)
 */
enum class NikonConnectionMode {
    AP_MODE,
    STA_MODE,
    UNKNOWN
}

/**
 * PTPIP 카메라 정보
 */
data class PtpipCamera(
    val ipAddress: String,
    val port: Int,
    val name: String,
    val isOnline: Boolean
)

/**
 * PTPIP 카메라 디바이스 정보
 */
data class PtpipCameraInfo(
    val manufacturer: String,
    val model: String,
    val version: String,
    val serialNumber: String
)

/**
 * Wi-Fi 기능 정보
 */
data class WifiCapabilities(
    val isConnected: Boolean,
    val isStaConcurrencySupported: Boolean,
    val networkName: String?,
    val linkSpeed: Int?,
    val frequency: Int?,
    val ipAddress: Int?,
    val macAddress: String?
)