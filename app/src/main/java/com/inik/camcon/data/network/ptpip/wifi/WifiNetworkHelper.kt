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

            // 권한 상태 확인 및 로그
            val hasLocationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.checkSelfPermission(android.Manifest.permission.NEARBY_WIFI_DEVICES) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
            }

            Log.d(TAG, "권한 상태 체크:")
            Log.d(TAG, "  - Android 버전: ${Build.VERSION.SDK_INT}")
            Log.d(
                TAG,
                "  - 필요 권한: ${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) "NEARBY_WIFI_DEVICES" else "ACCESS_FINE_LOCATION"}"
            )
            Log.d(TAG, "  - 권한 보유: $hasLocationPermission")

            if (!hasLocationPermission) {
                Log.e(TAG, "❌ Wi-Fi 스캔 권한이 없음")
                return@withContext emptyList()
            }

            // Android 10+ 제한 상황에서 대안적 방법들 시도
            val results = mutableListOf<String>()

            // 방법 1: 기존 캐시된 스캔 결과 활용 (가장 안정적)
            Log.d(TAG, "방법 1: 캐시된 스캔 결과 확인...")
            val cachedResults = getCachedScanResults()
            if (cachedResults.isNotEmpty()) {
                results.addAll(cachedResults)
                Log.d(TAG, "✅ 캐시에서 ${cachedResults.size}개 발견")
            }

            // 방법 2: 연결된 네트워크 정보에서 추출 (현재 네트워크)
            Log.d(TAG, "방법 2: 현재 연결된 네트워크 정보 확인...")
            val currentNetwork = getCurrentNetworkSSID()
            if (currentNetwork != null && !results.contains(currentNetwork)) {
                results.add(currentNetwork)
                Log.d(TAG, "✅ 현재 네트워크 추가: '$currentNetwork'")
            }

            // 방법 3: 시스템 제한 우회를 위한 조건부 스캔 시도
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Log.d(TAG, "방법 3: Android 10+ 조건부 스캔 시도...")
                val scanResults = tryLimitedScan()
                scanResults.forEach { ssid ->
                    if (!results.contains(ssid)) {
                        results.add(ssid)
                        Log.d(TAG, "✅ 제한적 스캔에서 추가: '$ssid'")
                    }
                }
            } else {
                // Android 9 이하는 기존 방식 사용
                Log.d(TAG, "방법 3: 레거시 스캔 (Android 9 이하)...")
                val legacyResults = performLegacyScan()
                legacyResults.forEach { ssid ->
                    if (!results.contains(ssid)) {
                        results.add(ssid)
                        Log.d(TAG, "✅ 레거시 스캔에서 추가: '$ssid'")
                    }
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
     * 캐시된 스캔 결과 가져오기
     */
    private fun getCachedScanResults(): List<String> {
        return try {
            val scanResults = wifiManager.scanResults
            Log.d(TAG, "캐시된 스캔 결과: ${scanResults.size}개")

            val ssids = scanResults.mapNotNull { result ->
                val ssid = result.SSID
                Log.d(
                    TAG,
                    "  - Raw SSID: '$ssid', BSSID: ${result.BSSID}, Level: ${result.level}dBm"
                )

                when {
                    ssid.isBlank() -> {
                        Log.d(TAG, "    ❌ 빈 SSID 제외")
                        null
                    }

                    ssid == "<unknown ssid>" -> {
                        Log.d(TAG, "    ❌ unknown SSID 제외")
                        null
                    }

                    ssid.startsWith("\"") && ssid.endsWith("\"") -> {
                        val cleaned = ssid.removeSurrounding("\"")
                        Log.d(TAG, "    ✅ 따옴표 제거: '$cleaned'")
                        cleaned
                    }

                    else -> {
                        Log.d(TAG, "    ✅ 정상 SSID: '$ssid'")
                        ssid
                    }
                }
            }.distinct()

            Log.d(TAG, "정리된 SSID: ${ssids.size}개")
            ssids
        } catch (e: SecurityException) {
            Log.e(TAG, "캐시된 스캔 결과 조회 실패 (권한): ${e.message}")
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "캐시된 스캔 결과 조회 실패: ${e.message}")
            emptyList()
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
     * Android 10+ 제한적 스캔 시도
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

            if (!isInForeground) {
                Log.w(TAG, "❌ 앱이 포그라운드에 있지 않음 - 스캔 제한됨")
                return emptyList()
            }

            // 2. 신중한 스캔 요청 (실패할 가능성 높음)
            var scanRequested = false
            try {
                @Suppress("DEPRECATION")
                scanRequested = wifiManager.startScan()
                Log.d(TAG, "제한적 스캔 요청 결과: $scanRequested")
            } catch (e: Exception) {
                Log.w(TAG, "제한적 스캔 요청 실패: ${e.message}")
            }

            if (!scanRequested) {
                Log.w(TAG, "❌ 시스템이 스캔 요청을 거부함 (빈도 제한)")
                return emptyList()
            }

            // 3. 결과 대기 (짧게)
            delay(2000)
            return getCachedScanResults()

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
                // 스캔 완료 대기
                repeat(5) { attempt ->
                    delay(1000)
                    val results = getCachedScanResults()
                    if (results.isNotEmpty()) {
                        Log.d(TAG, "레거시 스캔 성공 (시도 ${attempt + 1}): ${results.size}개")
                        return results
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
     */
    fun requestConnectionWithSpecifier(
        ssid: String,
        passphrase: String? = null,
        requireNoInternet: Boolean = true,
        bindProcess: Boolean = true,
        onResult: (Boolean) -> Unit
    ) {
        try {
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
                    if (bindProcess) {
                        try {
                            @Suppress("DEPRECATION")
                            ConnectivityManager.setProcessDefaultNetwork(network)
                            connectivityManager.bindProcessToNetwork(network)
                        } catch (e: Exception) {
                            Log.w(TAG, "네트워크 바인딩 실패: ${e.message}")
                        }
                    }
                    onResult(true)
                }

                override fun onUnavailable() {
                    Log.w(TAG, "WifiNetworkSpecifier 연결 불가: $ssid")
                    onResult(false)
                }

                override fun onLost(network: Network) {
                    Log.w(TAG, "WifiNetworkSpecifier 연결 손실: $ssid")
                }
            }

            connectivityManager.requestNetwork(request, callback)
            Log.d(TAG, "WifiNetworkSpecifier 요청 전송: $ssid")
        } catch (e: Exception) {
            Log.e(TAG, "WifiNetworkSpecifier 요청 중 오류", e)
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
}