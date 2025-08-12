package com.inik.camcon.data.network.ptpip.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.inik.camcon.domain.model.WifiCapabilities
import com.inik.camcon.domain.model.WifiNetworkState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
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
            return gatewayIP
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
                    return serverIpStr
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
                    return guessedCameraIP
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "네트워크 정보 읽기 실패: ${e.message}")
        }

        // 4. 기본 카메라 IP 반환
        val firstIP = COMMON_CAMERA_AP_IPS.firstOrNull()
        if (firstIP != null) {
            Log.d(TAG, "✅ 기본 카메라 IP 반환: $firstIP")
            return firstIP
        }

        Log.w(TAG, "❌ 사용 가능한 카메라 IP를 찾을 수 없음")
        return null
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
     * 네트워크 상태 변화 시 캐시 초기화
     */
    private fun clearCache() {
        cachedIsConnectedToCameraAP = null
        lastCheckedSSID = null
    }
}