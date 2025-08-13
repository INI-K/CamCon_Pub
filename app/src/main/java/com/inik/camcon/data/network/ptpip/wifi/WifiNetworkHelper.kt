package com.inik.camcon.data.network.ptpip.wifi

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.inik.camcon.domain.model.WifiCapabilities
import com.inik.camcon.domain.model.WifiNetworkState
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import android.location.LocationManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wi-Fi 네트워크 관련 헬퍼 클래스
 * 네트워크 연결 상태 확인 및 Wi-Fi 기능 관리
 *
 * ## Activity에서 권한 요청 예제:
 *
 * ```kotlin
 * class YourActivity : AppCompatActivity() {
 *     private val wifiNetworkHelper by lazy {
 *         (application as YourApp).appComponent.wifiNetworkHelper
 *     }
 *
 *     private val permissionLauncher = registerForActivityResult(
 *         ActivityResultContracts.RequestMultiplePermissions()
 *     ) { permissions ->
 *         val allGranted = permissions.all { it.value }
 *         if (allGranted) {
 *             // 권한 허용됨 - Wi-Fi 스캔 실행
 *             performWifiScan()
 *         } else {
 *             // 권한 거부됨 - 사용자에게 안내
 *             handlePermissionDenied(permissions)
 *         }
 *     }
 *
 *     private fun requestWifiScanPermissions() {
 *         val status = wifiNetworkHelper.analyzeWifiScanPermissionStatus()
 *
 *         if (status.canScan) {
 *             // 이미 모든 권한이 있음
 *             performWifiScan()
 *             return
 *         }
 *
 *         val missingPermissions = status.missingPermissions
 *         if (missingPermissions.isEmpty()) {
 *             // 권한은 있지만 Wi-Fi나 위치 서비스가 꺼져있음
 *             handleSystemSettingsNeeded(status)
 *             return
 *         }
 *
 *         // 권한 요청 전 설명 표시 (선택적)
 *         val shouldShowRationale = missingPermissions.any { permission ->
 *             wifiNetworkHelper.shouldShowPermissionRationale(this, permission)
 *         }
 *
 *         if (shouldShowRationale) {
 *             showPermissionRationaleDialog(missingPermissions)
 *         } else {
 *             // 바로 권한 요청
 *             permissionLauncher.launch(missingPermissions.toTypedArray())
 *         }
 *     }
 *
 *     private fun showPermissionRationaleDialog(permissions: List<String>) {
 *         AlertDialog.Builder(this)
 *             .setTitle("권한 필요")
 *             .setMessage(wifiNetworkHelper.getPermissionRationaleMessage())
 *             .setPositiveButton("허용") { _, _ ->
 *                 permissionLauncher.launch(permissions.toTypedArray())
 *             }
 *             .setNegativeButton("취소", null)
 *             .show()
 *     }
 *
 *     private fun handlePermissionDenied(permissions: Map<String, Boolean>) {
 *         val permanentlyDenied = permissions.filter { !it.value }
 *             .keys.any { permission ->
 *                 !wifiNetworkHelper.shouldShowPermissionRationale(this, permission)
 *             }
 *
 *         if (permanentlyDenied) {
 *             // 설정으로 이동 안내
 *             AlertDialog.Builder(this)
 *                 .setTitle("권한 설정 필요")
 *                 .setMessage("Wi-Fi 스캔을 위해 설정에서 권한을 허용해주세요.")
 *                 .setPositiveButton("설정으로 이동") { _, _ ->
 *                     startActivity(wifiNetworkHelper.createAppSettingsIntent())
 *                 }
 *                 .setNegativeButton("취소", null)
 *                 .show()
 *         }
 *     }
 *
 *     private fun performWifiScan() {
 *         lifecycleScope.launch {
 *             val ssids = wifiNetworkHelper.scanNearbyWifiSSIDs()
 *             // 결과 처리
 *         }
 *     }
 * }
 * ```
 */
@Singleton
class WifiNetworkHelper @Inject constructor(
    private val context: Context
) {
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    companion object {
        private const val TAG = "WifiNetworkHelper"

        // AP모드에서 일반적으로 사용되는 카메라 IP 주소들
        private val COMMON_CAMERA_AP_IPS = listOf(
            "192.168.1.1",
            "192.168.0.1",
            "192.168.10.1",
            "192.168.100.1",
            "10.0.0.1",
            "172.16.0.1"
        )

        // AP모드 감지를 위한 SSID 패턴들
        private val CAMERA_AP_PATTERNS = listOf(
            "CANON",
            "NIKON",
            "SONY",
            "FUJIFILM",
            "OLYMPUS",
            "PANASONIC",
            "PENTAX",
            "LEICA"
        )
    }

    /**
     * Wi-Fi 네트워크 상태 변화를 실시간으로 감지하는 Flow
     */
    val networkStateFlow: Flow<WifiNetworkState> = callbackFlow {
        var lastState: WifiNetworkState? = null

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val state = getCurrentNetworkState()
                if (state != lastState) {
                    lastState = state
                    trySend(state)
                }
            }

            override fun onLost(network: Network) {
                val state = getCurrentNetworkState()
                if (state != lastState) {
                    lastState = state
                    trySend(state)
                }
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    val state = getCurrentNetworkState()
                    val stateChanged = state != lastState
                    val apModeChanged =
                        state.isConnectedToCameraAP != lastState?.isConnectedToCameraAP

                    if (stateChanged || apModeChanged) {
                        lastState = state
                        trySend(state)
                        clearCache()
                    }
                }
            }

            override fun onLinkPropertiesChanged(
                network: Network,
                linkProperties: android.net.LinkProperties
            ) {
                val state = getCurrentNetworkState()
                if (state.isConnectedToCameraAP != lastState?.isConnectedToCameraAP) {
                    lastState = state
                    trySend(state)
                    clearCache()
                }
            }
        }

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        connectivityManager.registerNetworkCallback(request, networkCallback)

        // 초기 상태 전송
        val initialState = getCurrentNetworkState()
        lastState = initialState
        trySend(initialState)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    /**
     * 현재 네트워크 상태 가져오기
     */
    private fun getCurrentNetworkState(): WifiNetworkState {
        val isConnected = isWifiConnected()
        val isAP = isConnectedToCameraAP()
        val ssid = getCurrentSSID()
        val cameraIP = if (isAP) detectCameraIPInAPMode() else null

        return WifiNetworkState(
            isConnected = isConnected,
            isConnectedToCameraAP = isAP,
            ssid = ssid,
            detectedCameraIP = cameraIP
        )
    }

    /**
     * 현재 Wi-Fi 연결 상태 확인
     */
    fun isWifiConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    // 카메라 AP 연결 상태 캐싱
    private var cachedIsConnectedToCameraAP: Boolean? = null
    private var lastCheckedSSID: String? = null

    /**
     * 현재 연결된 네트워크가 카메라 AP인지 확인
     */
    fun isConnectedToCameraAP(): Boolean {
        if (!isWifiConnected()) {
            cachedIsConnectedToCameraAP = false
            lastCheckedSSID = null
            return false
        }

        val ssid = getCurrentSSID()

        // 같은 SSID에 대해서는 캐시된 결과 반환
        if (ssid == lastCheckedSSID && cachedIsConnectedToCameraAP != null) {
            return cachedIsConnectedToCameraAP!!
        }

        if (ssid == null) {
            cachedIsConnectedToCameraAP = false
            lastCheckedSSID = null
            return false
        }

        // 카메라 AP 패턴 매칭
        val isMatch = CAMERA_AP_PATTERNS.any { pattern ->
            ssid.contains(pattern, ignoreCase = true)
        }

        // 결과 캐싱
        cachedIsConnectedToCameraAP = isMatch
        lastCheckedSSID = ssid

        return isMatch
    }

    /**
     * 현재 연결된 SSID 가져오기
     */
    fun getCurrentSSID(): String? {
        return if (isWifiConnected()) {
            wifiManager.connectionInfo?.ssid?.removeSurrounding("\"")
        } else null
    }

    /**
     * AP모드에서 카메라 IP 주소 감지
     */
    fun detectCameraIPInAPMode(): String? {
        if (!isConnectedToCameraAP()) return null

        val connectionInfo = wifiManager.connectionInfo
        val gatewayIp = connectionInfo?.let {
            val dhcpInfo = wifiManager.dhcpInfo
            dhcpInfo?.gateway
        }

        // 게이트웨이 IP가 있으면 그것이 카메라 IP일 가능성이 높음
        gatewayIp?.let { gateway ->
            val ip = String.format(
                "%d.%d.%d.%d",
                gateway and 0xff,
                gateway shr 8 and 0xff,
                gateway shr 16 and 0xff,
                gateway shr 24 and 0xff
            )
            return ip
        }

        // 게이트웨이를 찾을 수 없으면 일반적인 카메라 IP 반환
        return COMMON_CAMERA_AP_IPS.firstOrNull()
    }

    /**
     * 지정된 IP가 카메라 AP 범위에 있는지 확인
     */
    fun isValidCameraAPIP(ipAddress: String): Boolean {
        return COMMON_CAMERA_AP_IPS.contains(ipAddress) ||
                ipAddress.matches(Regex("^192\\.168\\.[0-9]{1,3}\\.[0-9]{1,3}$"))
    }

    /**
     * AP모드에서 사용 가능한 카메라 IP 찾기 (libgphoto2 바로 초기화)
     */
    suspend fun findAvailableCameraIP(): String? {
        if (!isConnectedToCameraAP()) return null

        Log.d(TAG, "AP모드에서 사용 가능한 카메라 IP 검색 시작")

        // 1. 게이트웨이 IP 우선 확인
        val gatewayIP = detectCameraIPInAPMode()
        if (gatewayIP != null) {
            Log.d(TAG, "✅ 게이트웨이 IP 발견: $gatewayIP")
            return if (testTcpPort(gatewayIP, 15740)) gatewayIP else null
        }

        // 2. DHCP 정보에서 서버 IP 확인
        try {
            val dhcpInfo = wifiManager.dhcpInfo
            if (dhcpInfo != null) {
                val serverIp = dhcpInfo.serverAddress
                if (serverIp != 0) {
                    val serverIpStr = String.format(
                        "%d.%d.%d.%d",
                        serverIp and 0xff,
                        serverIp shr 8 and 0xff,
                        serverIp shr 16 and 0xff,
                        serverIp shr 24 and 0xff
                    )
                    Log.d(TAG, "✅ DHCP 서버 IP 발견: $serverIpStr")
                    return if (testTcpPort(serverIpStr, 15740)) serverIpStr else null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "DHCP 정보 읽기 실패: ${e.message}")
        }

        // 3. 네트워크 정보에서 IP 범위 추정
        try {
            val connectionInfo = wifiManager.connectionInfo
            if (connectionInfo != null) {
                val ipAddress = connectionInfo.ipAddress
                if (ipAddress != 0) {
                    val myIpStr = String.format(
                        "%d.%d.%d.%d",
                        ipAddress and 0xff,
                        ipAddress shr 8 and 0xff,
                        ipAddress shr 16 and 0xff,
                        ipAddress shr 24 and 0xff
                    )
                    Log.d(TAG, "내 IP 주소: $myIpStr")

                    // 같은 서브넷의 .1 주소 시도
                    val networkBase = myIpStr.substringBeforeLast(".")
                    val guessedCameraIP = "$networkBase.1"
                    Log.d(TAG, "✅ 추정 카메라 IP: $guessedCameraIP")
                    return if (testTcpPort(guessedCameraIP, 15740)) guessedCameraIP else null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "네트워크 정보 읽기 실패: ${e.message}")
        }

        // 4. 기본 카메라 IP 반환 (가능 시 TCP 확인)
        val firstIP = COMMON_CAMERA_AP_IPS.firstOrNull()
        if (firstIP != null) {
            Log.d(TAG, "✅ 기본 카메라 IP 후보: $firstIP")
            return if (testTcpPort(firstIP, 15740)) firstIP else null
        }

        Log.w(TAG, "❌ 사용 가능한 카메라 IP를 찾을 수 없음")
        return null
    }

    /**
     * 순수 TCP 포트 접속 확인 (PTP/IP 핸드셰이크 미수행)
     * - SO_LINGER 비활성, 짧은 타임아웃 적용, 성공 즉시 close
     */
    private suspend fun testTcpPort(ipAddress: String, port: Int, timeoutMs: Int = 1500): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "TCP 포트 확인: $ipAddress:$port")
                val socket = java.net.Socket()
                // SO_LINGER 비활성화
                try {
                    socket.setSoLinger(false, 0)
                } catch (_: Exception) {
                }
                socket.soTimeout = timeoutMs
                socket.connect(java.net.InetSocketAddress(ipAddress, port), timeoutMs)
                // 성공 즉시 종료
                try {
                    socket.close()
                } catch (_: Exception) {
                }
                // 소켓 정리 후 짧은 지연으로 NIC 상태 안정화
                delay(150)
                true
            } catch (e: Exception) {
                Log.d(TAG, "TCP 포트 확인 실패: $ipAddress:$port - ${e.message}")
                false
            }
        }
    }

    /**
     * PTP/IP 초기화 시도로 카메라 연결 테스트
     */
    private suspend fun testPtpipConnection(ipAddress: String, port: Int = 15740): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                Log.d(TAG, "PTP/IP 초기화 테스트 시작: $ipAddress:$port")

                val socket = java.net.Socket()
                socket.soTimeout = 3000
                socket.connect(java.net.InetSocketAddress(ipAddress, port), 3000)

                // PTP/IP Init Command Request 전송
                val initPacket = createInitCommandRequest()
                socket.getOutputStream().write(initPacket)
                socket.getOutputStream().flush()

                // ACK 응답 대기
                val response = ByteArray(1024)
                val bytesRead = socket.getInputStream().read(response)

                socket.close()

                // 응답 확인
                if (bytesRead >= 8) {
                    val buffer =
                        java.nio.ByteBuffer.wrap(response).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    buffer.position(4)
                    val responseType = buffer.int

                    if (responseType == 0x00000002) { // PTPIP_INIT_COMMAND_ACK
                        Log.d(TAG, "✅ PTP/IP 초기화 성공: $ipAddress")
                        return@withContext true
                    }
                }

                Log.d(TAG, "❌ PTP/IP 초기화 실패: $ipAddress - 잘못된 응답")
                false
            }
        } catch (e: Exception) {
            Log.d(TAG, "❌ PTP/IP 초기화 실패: $ipAddress - ${e.message}")
            false
        }
    }

    /**
     * PTP/IP Init Command Request 패킷 생성
     */
    private fun createInitCommandRequest(): ByteArray {
        val commandGuid = byteArrayOf(
            0xd5.toByte(), 0xb4.toByte(), 0x6b.toByte(), 0xcb.toByte(),
            0xd6.toByte(), 0x2a.toByte(), 0x4d.toByte(), 0xbb.toByte(),
            0xb0.toByte(), 0x97.toByte(), 0x87.toByte(), 0x20.toByte(),
            0xcf.toByte(), 0x83.toByte(), 0xe0.toByte(), 0x84.toByte()
        )

        val hostNameBytes = byteArrayOf(
            0x41, 0x00, 0x6e, 0x00, 0x64, 0x00, 0x72, 0x00,
            0x6f, 0x00, 0x69, 0x00, 0x64, 0x00, 0x20, 0x00,
            0x44, 0x00, 0x65, 0x00, 0x76, 0x00, 0x69, 0x00,
            0x63, 0x00, 0x65, 0x00
        )
        val nullTerminator = byteArrayOf(0x00, 0x00)

        val totalLength = 4 + 4 + 16 + hostNameBytes.size + nullTerminator.size + 4
        val buffer =
            java.nio.ByteBuffer.allocate(totalLength).order(java.nio.ByteOrder.LITTLE_ENDIAN)

        buffer.putInt(totalLength)
        buffer.putInt(0x00000001) // PTPIP_INIT_COMMAND_REQUEST
        buffer.put(commandGuid)
        buffer.put(hostNameBytes)
        buffer.put(nullTerminator)
        buffer.putInt(0x00010001)

        return buffer.array()
    }

    /**
     * 단순 네트워크 연결 테스트 (레거시 - 사용 안 함)
     */
    @Deprecated("PTP/IP 초기화로 대체됨")
    suspend fun testNetworkConnection(ipAddress: String, port: Int = 15740): Boolean {
        return testPtpipConnection(ipAddress, port)
    }

    /**
     * 주변 Wi‑Fi 네트워크 SSID 스캔 (Android 10+ 최적화 버전)
     * - Android 10+에서 스캔 빈도 제한 있음
     * - Android 13+에서는 `NEARBY_WIFI_DEVICES` 권한 필요
     */
    suspend fun scanNearbyWifiSSIDs(): List<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "=== Wi-Fi 스캔 시작 (Android ${Build.VERSION.SDK_INT}) ===")

            // 스캔 환경 진단 실행
            logWifiScanDiagnosis()

            // 통합 권한 체크
            val permissionStatus = analyzeWifiScanPermissionStatus()

            Log.d(TAG, "권한 상태 분석:")
            Log.d(TAG, "  - 위치 권한: ${permissionStatus.hasFineLocationPermission}")
            Log.d(TAG, "  - 근처 Wi-Fi 장치 권한: ${permissionStatus.hasNearbyWifiDevicesPermission}")
            Log.d(TAG, "  - Wi-Fi 활성화: ${permissionStatus.isWifiEnabled}")
            Log.d(TAG, "  - 위치 서비스 활성화: ${permissionStatus.isLocationEnabled}")
            Log.d(TAG, "  - 스캔 가능: ${permissionStatus.canScan}")

            if (permissionStatus.missingPermissions.isNotEmpty()) {
                Log.e(TAG, "❌ 부족한 권한: ${permissionStatus.missingPermissions}")
                return@withContext emptyList()
            }

            if (!permissionStatus.canScan) {
                Log.e(TAG, "❌ Wi-Fi 스캔 조건이 충족되지 않음")
                return@withContext emptyList()
            }

            val results = mutableListOf<String>()

            // 방법 1: 실제 스캔 시도 (유일한 방법)
            Log.d(TAG, "방법 1: 실제 Wi-Fi 스캔 시도...")
            val scanResults = performConditionalScan()
            if (scanResults.isNotEmpty()) {
                results.addAll(scanResults)
                Log.d(TAG, "✅ 실제 스캔에서 ${scanResults.size}개 발견")
            }

            // 방법 2: Android 13+ 전용 실험적 스캔
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && results.isEmpty()) {
                Log.d(TAG, "방법 2: Android 13+ 실험적 스캔 시도...")
                val android13Results = experimentalScanForAndroid13()
                android13Results.forEach { ssid ->
                    if (!results.contains(ssid)) {
                        results.add(ssid)
                        Log.d(TAG, "✅ Android 13+ 실험적 스캔에서 추가: '$ssid'")
                    }
                }
            }

            // 방법 3: 현재 연결된 네트워크 정보만 (스캔 실패 시에만)
            if (results.isEmpty()) {
                Log.d(TAG, "방법 3: 현재 연결된 네트워크 정보 확인...")
                val currentNetwork = getCurrentNetworkSSID()
                if (currentNetwork != null) {
                    results.add(currentNetwork)
                    Log.d(TAG, "✅ 현재 네트워크 추가: '$currentNetwork'")
                }
            }

            // 최종 결과 정리
            val finalResults = results.distinct()

            Log.d(TAG, "=== Wi-Fi 스캔 완료 ===")
            Log.d(TAG, "최종 SSID 수: ${finalResults.size}")
            finalResults.forEachIndexed { index, ssid ->
                Log.d(TAG, "  ${index + 1}. '$ssid'")
            }

            return@withContext finalResults

        } catch (e: Exception) {
            Log.e(TAG, "Wi‑Fi 스캔 중 오류", e)
            return@withContext emptyList()
        }
    }

    /**
     * 현재 연결된 네트워크 SSID 추출 (보안 정책 우회)
     */
    private fun getCurrentNetworkSSID(): String? {
        return try {
            val connectionInfo = wifiManager.connectionInfo
            val rawSSID = connectionInfo?.ssid

            Log.d(TAG, "현재 네트워크 정보:")
            Log.d(TAG, "  - Raw SSID: '$rawSSID'")
            Log.d(TAG, "  - BSSID: ${connectionInfo?.bssid}")
            Log.d(TAG, "  - 신호 강도: ${connectionInfo?.rssi}dBm")
            Log.d(TAG, "  - 네트워크 ID: ${connectionInfo?.networkId}")

            when {
                rawSSID == null -> {
                    Log.d(TAG, "  ❌ SSID가 null")
                    null
                }
                rawSSID == "<unknown ssid>" -> {
                    Log.d(TAG, "  ⚠️ unknown SSID - 보안 정책으로 숨겨짐")
                    // BSSID를 기반으로 추정 시도
                    val bssid = connectionInfo?.bssid
                    if (bssid != null && bssid != "02:00:00:00:00:00") {
                        val estimatedSSID = "WiFi_${bssid.takeLast(5).replace(":", "")}"
                        Log.d(TAG, "  💡 BSSID 기반 추정: '$estimatedSSID'")
                        estimatedSSID
                    } else {
                        Log.d(TAG, "  ❌ BSSID도 유효하지 않음")
                        null
                    }
                }

                rawSSID.startsWith("\"") && rawSSID.endsWith("\"") -> {
                    val cleaned = rawSSID.removeSurrounding("\"")
                    Log.d(TAG, "  ✅ 정리된 SSID: '$cleaned'")
                    cleaned
                }

                else -> {
                    Log.d(TAG, "  ✅ 현재 SSID: '$rawSSID'")
                    rawSSID
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "현재 네트워크 SSID 조회 실패: ${e.message}")
            null
        }
    }

    /**
     * Android 10+ 제한적 스캔 시도 (캐시 사용 안함)
     */
    private suspend fun tryLimitedScan(): List<String> {
        return try {
            Log.d(TAG, "Android 10+ 제한적 스캔 시도...")

            // 1. 포그라운드 상태 확인
            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val runningProcesses = activityManager.runningAppProcesses
            val isInForeground = runningProcesses?.any {
                it.processName == context.packageName &&
                        it.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            } ?: false

            Log.d(TAG, "포그라운드 상태: $isInForeground")

            // 2. 여러 번 스캔 시도
            var scanRequested = false
            for (attempt in 0 until 3) {
                try {
                    Log.d(TAG, "스캔 시도 ${attempt + 1}/3")
                    @Suppress("DEPRECATION")
                    scanRequested = wifiManager.startScan()
                    Log.d(TAG, "스캔 요청 결과: $scanRequested")

                    if (scanRequested) {
                        break
                    } else if (attempt < 2) {
                        delay(1000)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "스캔 시도 ${attempt + 1} 실패: ${e.message}")
                    if (attempt < 2) delay(1000)
                }
            }

            if (!scanRequested) {
                Log.w(TAG, "❌ 모든 스캔 시도 실패 (빈도 제한)")
                return emptyList()
            }

            // 3. 실제 스캔 결과 직접 읽기 (캐시 안 함)
            delay(3000) // 스캔 완료 대기

            return try {
                val scanResults = wifiManager.scanResults
                Log.d(TAG, "스캔 결과: ${scanResults?.size ?: 0}개")

                scanResults?.mapNotNull { result ->
                    val ssid = result.SSID
                    when {
                        ssid.isNullOrBlank() -> null
                        ssid == "<unknown ssid>" -> null
                        ssid.startsWith("\"") && ssid.endsWith("\"") -> {
                            ssid.removeSurrounding("\"")
                        }

                        else -> ssid
                    }
                }?.distinct() ?: emptyList()
            } catch (e: SecurityException) {
                Log.e(TAG, "스캔 결과 읽기 권한 오류: ${e.message}")
                emptyList()
            }

        } catch (e: Exception) {
            Log.e(TAG, "제한적 스캔 실패: ${e.message}")
            emptyList()
        }
    }

    /**
     * Android 9 이하 레거시 스캔
     */
    private suspend fun performLegacyScan(): List<String> {
        return try {
            Log.d(TAG, "레거시 스캔 수행...")

            @Suppress("DEPRECATION")
            val scanRequested = wifiManager.startScan()
            Log.d(TAG, "레거시 스캔 요청: $scanRequested")

            if (scanRequested) {
                // 스캔 완료 대기 후 직접 결과 읽기
                for (attempt in 0 until 5) {
                    delay(1000)

                    try {
                        val scanResults = wifiManager.scanResults
                        val results = scanResults?.mapNotNull { result ->
                            val ssid = result.SSID
                            when {
                                ssid.isNullOrBlank() -> null
                                ssid == "<unknown ssid>" -> null
                                ssid.startsWith("\"") && ssid.endsWith("\"") -> {
                                    ssid.removeSurrounding("\"")
                                }

                                else -> ssid
                            }
                        }?.distinct() ?: emptyList()

                        if (results.isNotEmpty()) {
                            Log.d(TAG, "레거시 스캔 성공 (시도 ${attempt + 1}): ${results.size}개")
                            return results
                        }
                    } catch (e: SecurityException) {
                        Log.e(TAG, "레거시 스캔 결과 읽기 권한 오류: ${e.message}")
                        return emptyList()
                    }
                }
            }

            Log.w(TAG, "레거시 스캔에서 결과 없음")
            emptyList()

        } catch (e: Exception) {
            Log.e(TAG, "레거시 스캔 실패: ${e.message}")
            emptyList()
        }
    }

    /**
     * WifiNetworkSpecifier로 로컬 전용 연결 요청
     * - 시스템 승인이 필요하며, 포그라운드에서 호출되어야 함
     * - 카메라 AP 등 인터넷 없는 네트워크는 INTERNET capability 제거 권장
     * - Android 10+ 필요, 권한 부족 시 수동 연결 안내
     */
    fun requestConnectionWithSpecifier(
        ssid: String,
        passphrase: String? = null,
        requireNoInternet: Boolean = true,
        bindProcess: Boolean = true,
        onResult: (Boolean) -> Unit,
        onError: ((String) -> Unit)? = null
    ) {
        try {
            // Android 10 미만에서는 지원되지 않음
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val message =
                    "Android 10 미만에서는 자동 Wi-Fi 연결이 지원되지 않습니다.\n수동으로 Wi-Fi 설정에서 '$ssid'에 연결해주세요."
                onError?.invoke(message) ?: Log.w(TAG, message)
                onResult(false)
                return
            }

            // 연결 전 해당 SSID가 스캔 결과에 있는지 확인
            Log.d(TAG, "WifiNetworkSpecifier 연결 전 SSID 확인: $ssid")
            val availableSSIDs = try {
                wifiManager.scanResults?.map { it.SSID?.removeSurrounding("\"") } ?: emptyList()
            } catch (e: SecurityException) {
                Log.w(TAG, "스캔 결과 확인 권한 부족: ${e.message}")
                emptyList()
            }

            Log.d(TAG, "현재 스캔 결과에서 발견된 SSID: ${availableSSIDs.size}개")
            availableSSIDs.forEach { Log.d(TAG, "  - $it") }

            val isSSIDAvailable = availableSSIDs.any { it == ssid || it?.contains(ssid) == true }
            if (!isSSIDAvailable && availableSSIDs.isNotEmpty()) {
                val message = "선택한 Wi-Fi '$ssid'가 현재 검색되지 않습니다.\n\n" +
                        "다음을 확인해주세요:\n" +
                        "1. 카메라 Wi-Fi가 켜져있는지 확인\n" +
                        "2. 카메라가 AP 모드로 설정되어 있는지 확인\n" +
                        "3. 거리가 너무 멀지 않은지 확인\n\n" +
                        "또는 Wi-Fi 설정에서 수동으로 연결해보세요."
                Log.w(TAG, "SSID '$ssid'가 스캔 결과에 없음")
                onError?.invoke(message)
                onResult(false)
                return
            }

            // 보안 타입 확인
            val securityType = getWifiSecurityType(ssid)
            val requiresPassword = requiresPassword(ssid)
            Log.d(TAG, "SSID '$ssid' 보안 정보:")
            Log.d(TAG, "  - 보안 타입: ${securityType ?: "알 수 없음"}")
            Log.d(TAG, "  - 패스워드 필요: $requiresPassword")
            Log.d(TAG, "  - 제공된 패스워드: ${if (passphrase.isNullOrEmpty()) "없음" else "있음"}")

            // 패스워드가 필요한데 제공되지 않은 경우 사용자에게 알림
            if (requiresPassword && passphrase.isNullOrEmpty()) {
                val message = "선택한 Wi-Fi '$ssid'는 패스워드가 필요합니다.\n\n" +
                        "보안 타입: ${securityType ?: "WPA/WPA2"}\n\n" +
                        "카메라의 Wi-Fi 패스워드를 확인하고 다시 시도하거나,\n" +
                        "시스템 Wi-Fi 설정에서 수동으로 연결해주세요."
                Log.w(TAG, "SSID '$ssid'는 패스워드가 필요하지만 제공되지 않음")
                onError?.invoke(message)
                onResult(false)
                return
            }

            val builder = WifiNetworkSpecifier.Builder()
                .setSsid(ssid)

            if (!passphrase.isNullOrEmpty()) {
                // WPA2 기본, 필요 시 WPA3로 변경 가능
                builder.setWpa2Passphrase(passphrase)
            }

            val specifier = builder.build()

            val requestBuilder = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(specifier)

            if (requireNoInternet) {
                requestBuilder.removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }

            val request = requestBuilder.build()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.i(TAG, "WifiNetworkSpecifier 연결 성공: $ssid")
                    Log.i(TAG, "네트워크 정보:")
                    Log.i(TAG, "  - Network ID: $network")
                    Log.i(TAG, "  - SSID: $ssid")
                    Log.i(TAG, "  - 프로세스 바인딩: $bindProcess")

                    if (bindProcess) {
                        try {
                            @Suppress("DEPRECATION")
                            ConnectivityManager.setProcessDefaultNetwork(network)
                            connectivityManager.bindProcessToNetwork(network)
                            Log.d(TAG, "네트워크 바인딩 성공")
                        } catch (e: Exception) {
                            Log.w(TAG, "네트워크 바인딩 실패: ${e.message}")
                        }
                    }
                    onResult(true)
                }

                override fun onUnavailable() {
                    Log.w(TAG, "WifiNetworkSpecifier 연결 불가: $ssid")
                    Log.w(TAG, "연결 실패 상세 정보:")
                    Log.w(TAG, "  - SSID: $ssid")
                    Log.w(TAG, "  - 패스워드 제공됨: ${!passphrase.isNullOrEmpty()}")
                    Log.w(TAG, "  - 보안 타입: ${getWifiSecurityType(ssid)}")
                    Log.w(TAG, "  - 인터넷 제외: $requireNoInternet")

                    val message = "자동 연결에 실패했습니다.\n\n" +
                            "가능한 원인:\n" +
                            "1. Wi-Fi '$ssid'가 범위를 벗어남\n" +
                            "2. 패스워드가 잘못되었거나 변경됨\n" +
                            "3. 카메라가 다른 기기와 연결 중\n" +
                            "4. 카메라 Wi-Fi가 일시적으로 꺼짐\n\n" +
                            "해결 방법:\n" +
                            "- 카메라 Wi-Fi 상태 확인 후 재시도\n" +
                            "- 시스템 Wi-Fi 설정에서 '$ssid'에 수동 연결\n" +
                            "- 카메라와의 거리를 가깝게 한 후 재시도"
                    Log.e(TAG, "사용자에게 표시할 오류 메시지: $message")
                    onError?.invoke(message)
                    onResult(false)
                }

                override fun onLost(network: Network) {
                    Log.w(TAG, "WifiNetworkSpecifier 연결 손실: $ssid")
                }
            }

            connectivityManager.requestNetwork(request, callback)
            Log.d(TAG, "WifiNetworkSpecifier 요청 전송: $ssid")

        } catch (e: SecurityException) {
            val message = when {
                e.message?.contains("CHANGE_NETWORK_STATE") == true -> {
                    "앱에 네트워크 변경 권한이 없습니다.\n\n수동 연결 방법:\n1. Wi-Fi 설정 열기\n2. '$ssid' 네트워크 선택\n3. 연결 완료 후 앱으로 돌아오기"
                }

                e.message?.contains("WRITE_SETTINGS") == true -> {
                    "시스템 설정 변경 권한이 필요합니다.\n\nWi-Fi 설정에서 '$ssid'에 수동으로 연결해주세요."
                }

                else -> {
                    "자동 Wi-Fi 연결 권한이 부족합니다.\n\n수동으로 Wi-Fi 설정에서 '$ssid'에 연결해주세요.\n\n오류: ${e.message}"
                }
            }

            Log.e(TAG, "WifiNetworkSpecifier 권한 오류: ${e.message}")
            onError?.invoke(message)
            onResult(false)
        } catch (e: Exception) {
            val message =
                "Wi-Fi 연결 요청 중 오류가 발생했습니다.\n\n수동으로 Wi-Fi 설정에서 '$ssid'에 연결해주세요.\n\n오류: ${e.message}"
            Log.e(TAG, "WifiNetworkSpecifier 요청 중 오류", e)
            onError?.invoke(message)
            onResult(false)
        }
    }

    /**
     * Wi-Fi STA 동시 연결 지원 여부 확인 (Android 9+ 필요)
     */
    fun isStaConcurrencySupported(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                wifiManager.isStaConcurrencyForLocalOnlyConnectionsSupported()
            } catch (e: Exception) {
                false
            }
        } else {
            false
        }
    }

    /**
     * Wi-Fi 기능 상세 정보 가져오기
     */
    fun getWifiCapabilities(): WifiCapabilities {
        val isConnected = isWifiConnected()
        val isStaConcurrencySupported = isStaConcurrencySupported()
        val isConnectedToCameraAP = isConnectedToCameraAP()
        val connectionInfo = if (isConnected) wifiManager.connectionInfo else null

        return WifiCapabilities(
            isConnected = isConnected,
            isStaConcurrencySupported = isStaConcurrencySupported,
            isConnectedToCameraAP = isConnectedToCameraAP,
            networkName = connectionInfo?.ssid?.removeSurrounding("\""),
            linkSpeed = connectionInfo?.linkSpeed,
            frequency = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                connectionInfo?.frequency
            } else null,
            ipAddress = connectionInfo?.ipAddress,
            macAddress = connectionInfo?.macAddress,
            detectedCameraIP = if (isConnectedToCameraAP) detectCameraIPInAPMode() else null
        )
    }

    /**
     * 구성된 네트워크 SSID 목록 가져오기 (Android 10+ 대안)
     */
    private fun getConfiguredNetworkSSIDs(): List<String> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ 에서는 제한적 접근만 가능
                Log.d(TAG, "Android 10+: 구성된 네트워크 정보 제한됨")
                emptyList()
            } else {
                // Android 9 이하에서만 사용 가능 - 권한 체크
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                if (!hasPermission) {
                    Log.d(TAG, "구성된 네트워크 조회 권한 없음")
                    return emptyList()
                }

                @Suppress("DEPRECATION")
                val configuredNetworks = wifiManager.configuredNetworks
                configuredNetworks?.mapNotNull { config ->
                    config.SSID?.removeSurrounding("\"")?.takeIf { it.isNotBlank() }
                } ?: emptyList()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "구성된 네트워크 조회 권한 오류: ${e.message}")
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "구성된 네트워크 조회 실패: ${e.message}")
            emptyList()
        }
    }

    /**
     * 조건부 스캔 수행 (캐시 사용 안함, 실제 스캔만)
     */
    private suspend fun performConditionalScan(): List<String> {
        return try {
            Log.d(TAG, "조건부 스캔 수행 시작...")

            val results = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+에서도 먼저 시도
                Log.d(TAG, "Android 10+ 스캔 시도 중...")
                tryLimitedScan()
            } else {
                // Android 9 이하는 기존 방식
                performLegacyScan()
            }

            Log.d(TAG, "조건부 스캔 완료: ${results.size}개 발견")
            results
        } catch (e: Exception) {
            Log.e(TAG, "조건부 스캔 실패: ${e.message}")
            emptyList()
        }
    }

    /**
     * 저장된 네트워크 SSID 목록 가져오기 (Android 11+ API)
     */
    private fun getSavedNetworkSSIDs(): List<String> {
        return try {
            // Android 11+ 에서도 제한이 심해서 실제로는 사용하기 어려움
            Log.d(TAG, "저장된 네트워크 API는 시스템 앱에만 제한됨")
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "저장된 네트워크 조회 실패: ${e.message}")
            emptyList()
        }
    }

    /**
     * Wi‑Fi가 켜져 있는지 확인
     */
    fun isWifiEnabled(): Boolean = try {
        wifiManager.isWifiEnabled
    } catch (_: Exception) {
        false
    }

    /**
     * 위치 서비스가 켜져 있는지 확인 (스캔에 필요)
     */
    fun isLocationEnabled(): Boolean {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                lm.isLocationEnabled
            } else {
                val gps = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                val net = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                gps || net
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 위치 설정 상태 확인 요청 (Play Services SettingsClient)
     * - 성공: 위치 설정이 이미 만족됨
     * - 실패: `ResolvableApiException` 이면 UI 계층에서 동의 다이얼로그를 띄워 해결 가능
     */
    fun checkLocationSettingsForScan(): Task<LocationSettingsResponse> {
        val client = LocationServices.getSettingsClient(context)
        val request = LocationSettingsRequest.Builder()
            .addLocationRequest(
                LocationRequest.create().apply {
                    interval = 0L
                    fastestInterval = 0L
                    priority = LocationRequest.PRIORITY_LOW_POWER
                }
            )
            .build()
        return client.checkLocationSettings(request)
    }

    /**
     * 위치 설정 동의 다이얼로그 표시 (UI 계층에서 호출)
     * - Activity Result API 또는 onActivityResult에서 결과 처리 필요
     */
    fun startLocationSettingsResolution(
        activity: Activity,
        exception: ResolvableApiException,
        requestCode: Int = 1001
    ) {
        try {
            exception.startResolutionForResult(activity, requestCode)
        } catch (t: Throwable) {
            Log.w(TAG, "위치 설정 해결 다이얼로그 호출 실패: ${t.message}")
        }
    }

    /**
     * 위치 설정 확인 후, 미충족이고 해결 가능하면 다이얼로그를 즉시 표시
     * - 이미 만족: onAlreadySatisfied 콜백 호출
     * - 미해결/비해결: Resolvable이면 다이얼로그 표시, 아니면 onNonResolvable 콜백 전달
     */
    fun checkAndRequestLocationSettings(
        activity: Activity,
        requestCode: Int = 1001,
        onAlreadySatisfied: (() -> Unit)? = null,
        onNonResolvable: ((Throwable) -> Unit)? = null
    ) {
        checkLocationSettingsForScan()
            .addOnSuccessListener { _ ->
                onAlreadySatisfied?.invoke()
            }
            .addOnFailureListener { e ->
                if (e is ResolvableApiException) {
                    startLocationSettingsResolution(activity, e, requestCode)
                } else {
                    Log.w(TAG, "위치 설정 확인 실패(해결 불가): ${e.message}")
                    onNonResolvable?.invoke(e)
                }
            }
    }

    /**
     * 네트워크 상태 변화 시 캐시 초기화
     */
    private fun clearCache() {
        cachedIsConnectedToCameraAP = null
        lastCheckedSSID = null
    }

    /**
     * Wi-Fi 스캔 환경 진단 (디버깅용)
     */
    fun diagnoseWifiScanEnvironment(): Map<String, Any> {
        val diagnosis = mutableMapOf<String, Any>()

        try {
            // 기본 정보
            diagnosis["android_version"] = Build.VERSION.SDK_INT
            diagnosis["device_model"] = "${Build.MANUFACTURER} ${Build.MODEL}"

            // 권한 상태
            diagnosis["wifi_enabled"] = wifiManager.isWifiEnabled
            diagnosis["location_enabled"] = isLocationEnabled()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                diagnosis["nearby_wifi_devices_permission"] = ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.NEARBY_WIFI_DEVICES
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }

            diagnosis["fine_location_permission"] = ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            diagnosis["coarse_location_permission"] = ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            // 네트워크 정보
            diagnosis["wifi_connected"] = isWifiConnected()
            diagnosis["current_ssid"] = getCurrentSSID() ?: "null"

            // 스캔 캐시 정보
            try {
                val scanResults = wifiManager.scanResults
                diagnosis["cached_scan_count"] = scanResults?.size ?: 0
                diagnosis["cached_scan_age"] = if (scanResults?.isNotEmpty() == true) {
                    System.currentTimeMillis() - scanResults[0].timestamp / 1000
                } else 0
            } catch (e: SecurityException) {
                diagnosis["cached_scan_error"] = "SecurityException: ${e.message}"
            }

            // 앱 상태
            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val runningProcesses = activityManager.runningAppProcesses
            diagnosis["app_foreground"] = runningProcesses?.any {
                it.processName == context.packageName &&
                        it.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            } ?: false

        } catch (e: Exception) {
            diagnosis["diagnosis_error"] = e.message ?: "Unknown error"
        }

        return diagnosis
    }

    /**
     * 스캔 결과 상세 로그 출력
     */
    fun logWifiScanDiagnosis() {
        val diagnosis = diagnoseWifiScanEnvironment()

        Log.d(TAG, "=== Wi-Fi 스캔 환경 진단 ===")
        diagnosis.forEach { (key, value) ->
            Log.d(TAG, "  $key: $value")
        }
        Log.d(TAG, "=== 진단 완료 ===")
    }

    /**
     * Android 13+ 전용 실험적 스캔 (NEARBY_WIFI_DEVICES만 사용)
     */
    suspend fun experimentalScanForAndroid13(): List<String> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Log.d(TAG, "Android 13 미만에서는 실행되지 않음")
            return@withContext emptyList()
        }

        try {
            Log.d(TAG, "=== Android 13+ 실험적 스캔 시작 ===")

            // NEARBY_WIFI_DEVICES 권한만 확인
            val hasNearbyWifiDevices = ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.NEARBY_WIFI_DEVICES
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            Log.d(TAG, "NEARBY_WIFI_DEVICES 권한: $hasNearbyWifiDevices")

            if (!hasNearbyWifiDevices) {
                Log.e(TAG, "❌ NEARBY_WIFI_DEVICES 권한이 없음")
                return@withContext emptyList()
            }

            // 강제 스캔 시도 (위치 권한 무시)
            Log.d(TAG, "강제 스캔 시도 중...")

            var scanSuccess = false
            try {
                @Suppress("DEPRECATION")
                scanSuccess = wifiManager.startScan()
                Log.d(TAG, "startScan() 결과: $scanSuccess")
            } catch (e: SecurityException) {
                Log.e(TAG, "스캔 권한 오류: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "스캔 요청 실패: ${e.message}")
            }

            // 스캔 결과 확인
            delay(3000) // 3초 대기

            val results = mutableListOf<String>()

            try {
                val scanResults = wifiManager.scanResults
                Log.d(TAG, "스캔 결과 개수: ${scanResults?.size ?: 0}")

                scanResults?.forEach { result ->
                    Log.d(
                        TAG,
                        "  발견: SSID='${result.SSID}', BSSID=${result.BSSID}, Level=${result.level}dBm"
                    )

                    if (!result.SSID.isNullOrBlank() && result.SSID != "<unknown ssid>") {
                        val cleanSSID = result.SSID.removeSurrounding("\"")
                        if (!results.contains(cleanSSID)) {
                            results.add(cleanSSID)
                        }
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "스캔 결과 읽기 권한 오류: ${e.message}")
                return@withContext emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "스캔 결과 읽기 실패: ${e.message}")
                return@withContext emptyList()
            }

            Log.d(TAG, "=== 실험적 스캔 완료: ${results.size}개 발견 ===")
            return@withContext results

        } catch (e: Exception) {
            Log.e(TAG, "실험적 스캔 중 오류", e)
            return@withContext emptyList()
        }
    }

    /**
     * Wi-Fi 스캔에 필요한 권한들을 확인
     */
    fun getRequiredWifiScanPermissions(): List<String> {
        val permissions = mutableListOf<String>()

        // 기본 위치 권한 (모든 Android 버전)
        permissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION)

        // Android 13+ 근처 Wi-Fi 장치 권한
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        return permissions
    }

    /**
     * Wi-Fi 스캔을 위한 모든 권한이 허용되었는지 확인
     */
    fun hasAllWifiScanPermissions(): Boolean {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val hasNearbyWifiDevices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.NEARBY_WIFI_DEVICES
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true // Android 13 미만에서는 필요 없음

        return hasFineLocation && hasNearbyWifiDevices
    }

    /**
     * 부족한 Wi-Fi 스캔 권한 목록 반환
     */
    fun getMissingWifiScanPermissions(): List<String> {
        val missing = mutableListOf<String>()

        // ACCESS_FINE_LOCATION 확인
        if (ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            missing.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }

        // Android 13+ NEARBY_WIFI_DEVICES 확인
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.NEARBY_WIFI_DEVICES
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                missing.add(android.Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }

        return missing
    }

    /**
     * Wi-Fi 스캔 권한 상태를 상세히 분석
     */
    fun analyzeWifiScanPermissionStatus(): WifiScanPermissionStatus {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val hasNearbyWifiDevices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.NEARBY_WIFI_DEVICES
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true

        val isWifiEnabled = wifiManager.isWifiEnabled
        val isLocationEnabled = isLocationEnabled()

        return WifiScanPermissionStatus(
            hasFineLocationPermission = hasFineLocation,
            hasNearbyWifiDevicesPermission = hasNearbyWifiDevices,
            isWifiEnabled = isWifiEnabled,
            isLocationEnabled = isLocationEnabled,
            canScan = hasFineLocation && hasNearbyWifiDevices && isWifiEnabled && isLocationEnabled,
            androidVersion = Build.VERSION.SDK_INT,
            missingPermissions = getMissingWifiScanPermissions()
        )
    }

    /**
     * Wi-Fi 스캔 권한 요청을 위한 설명 메시지 생성
     */
    fun getPermissionRationaleMessage(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            "카메라와 Wi-Fi 연결을 위해 다음 권한이 필요합니다:\n\n" +
                    "• 위치 권한: 주변 Wi-Fi 네트워크를 검색하기 위해\n" +
                    "• 근처 Wi-Fi 장치 권한: Wi-Fi 네트워크에 연결하기 위해\n\n" +
                    "이 권한들은 오직 카메라 연결 목적으로만 사용됩니다."
        } else {
            "카메라와 Wi-Fi 연결을 위해 위치 권한이 필요합니다.\n\n" +
                    "이 권한은 주변 Wi-Fi 네트워크 검색을 위해서만 사용되며, " +
                    "실제 위치 정보는 수집하지 않습니다."
        }
    }

    /**
     * 권한별 설명 메시지
     */
    fun getPermissionDescription(permission: String): String {
        return when (permission) {
            android.Manifest.permission.ACCESS_FINE_LOCATION -> {
                "위치 권한 (필수)\n주변 Wi-Fi 네트워크를 검색하기 위해 필요합니다."
            }

            android.Manifest.permission.NEARBY_WIFI_DEVICES -> {
                "근처 Wi-Fi 장치 권한 (Android 13+)\nWi-Fi 네트워크 연결을 위해 필요합니다."
            }

            else -> "알 수 없는 권한: $permission"
        }
    }

    /**
     * 권한이 영구적으로 거부되었는지 확인 (shouldShowRequestPermissionRationale 사용)
     */
    fun shouldShowPermissionRationale(activity: Activity, permission: String): Boolean {
        return androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            permission
        )
    }

    /**
     * 설정 앱으로 이동하는 Intent 생성
     */
    fun createAppSettingsIntent(): Intent {
        return Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Wi-Fi 설정 화면으로 이동하는 Intent 생성
     */
    fun createWifiSettingsIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                Intent(android.provider.Settings.Panel.ACTION_WIFI)
            } catch (e: Exception) {
                Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
            }
        } else {
            Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
        }.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * 특정 SSID의 보안 타입 확인
     */
    fun getWifiSecurityType(ssid: String): String? {
        return try {
            val scanResults = wifiManager.scanResults
            val targetNetwork = scanResults?.find {
                it.SSID?.removeSurrounding("\"") == ssid
            }

            if (targetNetwork != null) {
                val capabilities = targetNetwork.capabilities
                Log.d(TAG, "SSID '$ssid' 보안 정보: $capabilities")

                when {
                    capabilities.contains("WPA3") -> "WPA3"
                    capabilities.contains("WPA2") -> "WPA2"
                    capabilities.contains("WPA") -> "WPA"
                    capabilities.contains("WEP") -> "WEP"
                    else -> "OPEN"
                }
            } else {
                Log.w(TAG, "SSID '$ssid'를 스캔 결과에서 찾을 수 없음")
                null
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Wi-Fi 보안 타입 확인 권한 오류: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Wi-Fi 보안 타입 확인 실패: ${e.message}")
            null
        }
    }

    /**
     * SSID가 패스워드를 요구하는지 확인
     */
    fun requiresPassword(ssid: String): Boolean {
        val securityType = getWifiSecurityType(ssid)
        return securityType != null && securityType != "OPEN"
    }
}

data class WifiScanPermissionStatus(
    val hasFineLocationPermission: Boolean,
    val hasNearbyWifiDevicesPermission: Boolean,
    val isWifiEnabled: Boolean,
    val isLocationEnabled: Boolean,
    val canScan: Boolean,
    val androidVersion: Int,
    val missingPermissions: List<String>
)