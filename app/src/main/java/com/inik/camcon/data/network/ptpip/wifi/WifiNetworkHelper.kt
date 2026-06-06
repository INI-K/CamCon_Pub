package com.inik.camcon.data.network.ptpip.wifi

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.MacAddress
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.inik.camcon.di.IoDispatcher
import com.inik.camcon.domain.model.AutoConnectNetworkConfig
import com.inik.camcon.domain.model.WifiCapabilities
import com.inik.camcon.domain.model.WifiNetworkState
import com.inik.camcon.domain.model.WifiScanPermissionStatus
import com.inik.camcon.domain.repository.WifiCapabilityProvider
import com.inik.camcon.utils.LogMask
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import android.location.LocationManager
import android.net.wifi.WifiNetworkSuggestion
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
    private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : WifiCapabilityProvider {
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // @Singleton(앱 생명주기) 범위의 managed scope.
    // NetworkCallback 콜백 스레드를 블로킹하지 않도록 안정화 지연·로깅 작업을 오프로드한다.
    private val helperScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    // Wi-Fi 퍼포먼스 락 관리
    private var wifiLock: WifiManager.WifiLock? = null

    /**
     * High performance Wi-Fi lock 획득
     */
    fun acquireWifiLock(tag: String = "CamConWifiLock"): Boolean {
        synchronized(this) {
            if (wifiLock?.isHeld == true) {
                Log.d(TAG, "이미 Wi-Fi 락이 획득됨")
                return true
            }
            return try {
                wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, tag)
                wifiLock?.setReferenceCounted(false)
                wifiLock?.acquire()
                Log.d(TAG, "✅ Wi-Fi high performance 락 획득")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Wi-Fi 락 획득 실패: ${e.message}")
                false
            }
        }
    }

    /**
     * Wi-Fi 락 해제
     */
    fun releaseWifiLock() {
        synchronized(this) {
            try {
                wifiLock?.let {
                    if (it.isHeld) {
                        it.release()
                        Log.d(TAG, "✅ Wi-Fi 락 해제")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Wi-Fi 락 해제 실패: ${e.message}")
            } finally {
                wifiLock = null
            }
        }
    }

    /**
     * Wi-Fi 락 현재 상태 확인
     */
    fun isWifiLockHeld(): Boolean {
        return wifiLock?.isHeld == true
    }

    /**
     * 현재 Wi-Fi 연결의 주파수 대역 확인 (2.4GHz/5GHz)
     */
    @Suppress("DEPRECATION")
    fun getCurrentWifiFrequencyInfo(): WifiFrequencyInfo? {
        return try {
            Log.d(TAG, "Wi-Fi 주파수 정보 조회 시작...")

            val connectionInfo = wifiManager.connectionInfo
            if (connectionInfo == null) {
                Log.w(TAG, "❌ WifiManager.connectionInfo가 null")
                return null
            }

            Log.d(TAG, "ConnectionInfo 획득 성공: networkId=${connectionInfo.networkId}, bssid=${LogMask.bssid(connectionInfo.bssid)}")

            // 연결 정보가 있으면 Wi-Fi에 연결된 것으로 간주
            val rawSSID = connectionInfo.ssid
            val ssid = rawSSID?.removeSurrounding("\"")

            if (ssid.isNullOrEmpty() || ssid == "<unknown ssid>") {
                Log.w(TAG, "SSID 정보가 유효하지 않음: ${LogMask.ssid(ssid)}")
                return null
            }

            // API 21+ 에서 주파수 정보 사용 가능
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val frequency = connectionInfo.frequency
                val linkSpeed = connectionInfo.linkSpeed
                val rssi = connectionInfo.rssi

                if (frequency > 0) {
                    val band = when {
                        frequency in 2412..2484 -> "2.4GHz"
                        frequency in 5170..5825 -> "5GHz"
                        frequency in 5925..7125 -> "6GHz" // Wi-Fi 6E
                        else -> "Unknown"
                    }

                    Log.d(TAG, "Wi-Fi 주파수 조회 성공: ssid=${LogMask.ssid(ssid)}, ${frequency}MHz($band), ${linkSpeed}Mbps, ${rssi}dBm")

                    return WifiFrequencyInfo(
                        frequency = frequency,
                        band = band,
                        linkSpeed = linkSpeed,
                        rssi = rssi,
                        ssid = ssid
                    )
                } else {
                    Log.w(TAG, "❌ 주파수 정보가 유효하지 않음: ${frequency}MHz")
                }
            } else {
                Log.w(TAG, "❌ 주파수 정보는 Android 5.0+ 에서만 사용 가능 (현재: API ${Build.VERSION.SDK_INT})")
            }

            return null
        } catch (e: Exception) {
            Log.e(TAG, "❌ Wi-Fi 주파수 정보 조회 실패: ${e.message}", e)
            return null
        }
    }

    /**
     * 특정 SSID의 주파수 정보를 스캔 결과에서 조회
     */
    fun getSSIDFrequencyInfo(ssid: String): WifiFrequencyInfo? {
        return try {
            val scanResults = wifiManager.scanResults
            val targetNetwork = scanResults?.find {
                it.SSID?.removeSurrounding("\"") == ssid
            }

            if (targetNetwork != null) {
                val frequency = targetNetwork.frequency
                val band = when {
                    frequency in 2412..2484 -> "2.4GHz"
                    frequency in 5170..5825 -> "5GHz"
                    frequency in 5925..7125 -> "6GHz"
                    else -> "Unknown"
                }

                Log.d(TAG, "SSID '${LogMask.ssid(ssid)}' 주파수: ${frequency}MHz($band), ${targetNetwork.level}dBm")

                return WifiFrequencyInfo(
                    frequency = frequency,
                    band = band,
                    linkSpeed = null, // 스캔 결과에서는 링크 속도 정보 없음
                    rssi = targetNetwork.level,
                    ssid = ssid
                )
            } else {
                Log.w(TAG, "SSID '${LogMask.ssid(ssid)}'를 스캔 결과에서 찾을 수 없음")
                return null
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Wi-Fi 주파수 정보 조회 권한 오류: ${e.message}")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Wi-Fi 주파수 정보 조회 실패: ${e.message}")
            return null
        }
    }

    companion object {
        private const val TAG = "WifiNetworkHelper"
        const val ACTION_AUTO_CONNECT_TRIGGER = "com.inik.camcon.action.AUTO_CONNECT_TRIGGER"
        const val ACTION_AUTO_CONNECT_SUCCESS = "com.inik.camcon.action.AUTO_CONNECT_SUCCESS"
        const val EXTRA_AUTO_CONNECT_SSID = "extra_auto_connect_ssid"
        const val EXTRA_CAMERA_IP = "extra_camera_ip"
        const val WIFI_AUTO_CONNECT_PERMISSION = "com.inik.camcon.permission.WIFI_AUTO_CONNECT"

        data class SuggestionResult(
            val success: Boolean,
            val message: String
        )

        // AP모드에서 일반적으로 사용되는 카메라 IP 주소들
        private val COMMON_CAMERA_AP_IPS = listOf(
            "192.168.1.1",
            "192.168.0.1",
            "192.168.10.1",
            "192.168.100.1",
            "10.0.0.1",
            "172.16.0.1"
        )

        // AP모드 감지를 위한 SSID 패턴들 — brand + 모델 prefix 통합 리스트.
        // 인스턴스 isCameraNetwork와 companion isCameraApSsid가 동일 소스를 사용하도록 단일 진실 지점.
        internal val CAMERA_AP_PATTERNS = listOf(
            // Brand
            "CANON", "Canon",
            "NIKON", "Nikon",
            "SONY", "Sony",
            "FUJIFILM", "Fujifilm", "FUJI",
            "OLYMPUS", "Olympus",
            "PANASONIC", "Panasonic", "Lumix", "LUMIX",
            "PENTAX", "Pentax", "RICOH", "Ricoh",
            "LEICA", "Leica",
            "HASSELBLAD", "Hasselblad",
            // Canon 모델
            "EOS", "PowerShot", "IXUS", "VIXIA",
            // Nikon 모델
            "COOLPIX", "Z_5", "Z_6", "Z_7", "Z8", "Z9", "Z30", "Z50", "Zfc",
            "D3500", "D5600", "D7500", "D780", "D850", "D500",
            // Sony 모델
            "Alpha", "A7R", "A7S", "A7C", "A7IV", "A9",
            // Fujifilm 모델
            "X-T4", "X-T5", "X-T30", "X-T50", "X-PRO3", "X-E4", "X-S10", "X-S20",
            "GFX50", "GFX100",
            // Olympus 모델
            "OM-D", "E-M1", "E-M5", "E-M10",
            // Panasonic 모델
            "GH5", "GH6"
        )

        /**
         * 카메라 AP/Wi-Fi Direct 광고로 추정되는 SSID 패턴 매칭.
         * [CAMERA_AP_PATTERNS] 단일 소스를 사용하므로 인스턴스 isCameraNetwork와 발산하지 않는다.
         * "AndroidHotspot…" / "Starbucks…" 같은 generic SSID는 명시적으로 제외한다.
         */
        fun isCameraApSsid(ssid: String?): Boolean {
            if (ssid.isNullOrBlank()) return false
            val direct = ssid.startsWith("DIRECT-", ignoreCase = true)
            val byPattern = CAMERA_AP_PATTERNS.any { ssid.contains(it, ignoreCase = true) }
            val isGenericHotspot = ssid.startsWith("Android", ignoreCase = true) && !byPattern
            if (isGenericHotspot) return false
            return direct || byPattern
        }
    }

    /**
     * 폰의 Wi-Fi 핫스팟(테더링) 활성 여부.
     *
     * targetSdk 30+에서 hidden API `isWifiApEnabled` reflection은 blocklist로 차단되어
     * 대부분 false로 폴백된다(신뢰 불가). 따라서 먼저 비차단 경로인 NetworkInterface 열거로
     * AP/softAP 인터페이스(ap0/swlan0/softap…)를 탐지하고, 못 찾으면 구형 기기용 reflection을
     * 폴백으로 시도한다.
     */
    fun isHotspotEnabled(): Boolean {
        if (detectHotspotByInterfaces()) return true
        return runCatching {
            val m = wifiManager.javaClass.getDeclaredMethod("isWifiApEnabled")
            m.isAccessible = true
            (m.invoke(wifiManager) as? Boolean) ?: false
        }.getOrElse { false }
    }

    /**
     * 폰이 AP(softAP) 역할을 하면 ap0/swlan0/softap 같은 별도 인터페이스가 IPv4를 갖고 up 상태가 된다.
     * hidden API를 쓰지 않으므로 targetSdk 36에서도 차단되지 않는다.
     */
    private fun detectHotspotByInterfaces(): Boolean = runCatching {
        val interfaces = java.net.NetworkInterface.getNetworkInterfaces() ?: return false
        for (nif in interfaces) {
            val name = nif.name?.lowercase() ?: continue
            // ap0 / swlan0 / softap0 처럼 prefix 뒤 숫자가 오는 softAP 인터페이스만 매칭.
            // "apcli0"(일부 라우터 클라이언트 모드) 같은 무관 인터페이스 오탐 방지.
            val isApName = name.matches(Regex("^(ap|swlan|softap)\\d.*"))
            if (!isApName) continue
            if (!runCatching { nif.isUp }.getOrDefault(false)) continue
            val hasIpv4 = nif.inetAddresses.asSequence().any {
                it is java.net.Inet4Address && !it.isLoopbackAddress
            }
            if (hasIpv4) return true
        }
        false
    }.getOrDefault(false)

    /**
     * 현재 네트워크 상태 스냅샷(외부 강제 갱신 트리거용).
     * 핫스팟을 OS 설정에서 켜고 돌아온 직후처럼 NetworkCallback이 발화하지 않는 경우 UI 갱신에 사용한다.
     */
    fun getNetworkStateSnapshot(): WifiNetworkState = getCurrentNetworkState()

    /**
     * 현재 네트워크의 기본 게이트웨이 IPv4 추출.
     */
    fun extractGatewayIp(linkProps: android.net.LinkProperties?): String? = linkProps?.routes
        ?.asSequence()
        ?.filter { it.isDefaultRoute }
        ?.mapNotNull { it.gateway }
        ?.filterIsInstance<java.net.Inet4Address>()
        ?.map { it.hostAddress }
        ?.firstOrNull()

    /**
     * 현재 네트워크의 IPv4 서브넷 prefix 길이 추출.
     */
    fun extractSubnetPrefix(linkProps: android.net.LinkProperties?): Int? = linkProps?.linkAddresses
        ?.asSequence()
        ?.filter { it.address is java.net.Inet4Address }
        ?.map(android.net.LinkAddress::getPrefixLength)
        ?.firstOrNull()

    /**
     * 지정한 SSID에 대해 자동 연결 제안 트리거 브로드캐스트를 전송한다. (API 26+ 용)
     */
    fun sendAutoConnectBroadcast(ssid: String) {
        val intent = Intent(ACTION_AUTO_CONNECT_TRIGGER).apply {
            putExtra(EXTRA_AUTO_CONNECT_SSID, ssid)
            setPackage(context.packageName)
        }

        Log.d(TAG, "자동 연결 브로드캐스트 전송 요청: ssid=${LogMask.ssid(ssid)}")
        context.sendBroadcast(intent, WIFI_AUTO_CONNECT_PERMISSION)
    }

    /**
     * 자동 연결 성공 시 브로드캐스트 전송
     */
    fun sendAutoConnectSuccessBroadcast(ssid: String, cameraIp: String) {
        val intent = Intent(ACTION_AUTO_CONNECT_SUCCESS).apply {
            putExtra(EXTRA_AUTO_CONNECT_SSID, ssid)
            putExtra(EXTRA_CAMERA_IP, cameraIp)
            setPackage(context.packageName)
        }

        Log.d(TAG, "자동 연결 성공 브로드캐스트 전송: ssid=${LogMask.ssid(ssid)}, ip=${LogMask.id(cameraIp)}")
        context.sendBroadcast(intent, WIFI_AUTO_CONNECT_PERMISSION)
    }

    /**
     * 카메라 관련 네트워크만 필터링
     */
    private fun filterCameraNetworks(ssidList: List<String>): List<String> {
        val cameraNetworks = ssidList.filter { ssid ->
            isCameraNetwork(ssid)
        }

        Log.d(TAG, "카메라 네트워크 필터링 결과: ${cameraNetworks.size}/${ssidList.size}개가 카메라 관련")

        return cameraNetworks
    }

    /**
     * SSID가 카메라 관련 네트워크인지 판단.
     * companion [CAMERA_AP_PATTERNS]를 단일 소스로 사용하고, 인스턴스 한정 추가 패턴
     * (액션캠/드론/구식 모델 등 origin 풍부 리스트)을 합쳐 매칭한다.
     */
    private fun isCameraNetwork(ssid: String): Boolean {
        // 인스턴스 전용 확장 패턴 — companion에서는 강한 시그널만 유지하기 위해 분리.
        val instanceExtras = listOf(
            "GoPro", "GOPRO", "Hero",
            "DJI", "Dji", "Mavic", "Phantom", "Inspire", "Mini",
            "Insta360", "INSTA360",
            "FX30", "FX3", "RX100", "RX10", "ZV-1", "ZV-E10",
            "X-A7", "X-H1", "X-H2",
            "PEN", "E-PL", "E-P7",
            "GX9", "G9", "G100", "FZ1000", "LX100",
            "GODOX", "Godox", "Flashpoint",
            "Osmo", "OSMO", "Pocket", "Action", "Mobile",
            "SIGMA", "fp", "TAMRON"
        )

        val merged = CAMERA_AP_PATTERNS + instanceExtras
        return merged.any { pattern ->
            ssid.contains(pattern, ignoreCase = true)
        }
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
                // 핫스팟 on/off·게이트웨이 변화도 통과시키도록 전체 상태 변화 기준으로 비교한다.
                // (distinctUntilChanged가 최종 중복을 제거하므로 안전)
                val state = getCurrentNetworkState()
                if (state != lastState) {
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
    }.distinctUntilChanged().flowOn(ioDispatcher)

    /**
     * 현재 네트워크 상태 가져오기.
     * 폰 핫스팟 STA 모드 지원을 위해 LinkProperties 기반 게이트웨이/서브넷 + 핫스팟 활성 상태를 함께 채운다.
     */
    private fun getCurrentNetworkState(): WifiNetworkState {
        val isConnected = isWifiConnected()
        val isAP = isConnectedToCameraAP()
        val ssid = getCurrentSSID()
        val cameraIP = if (isAP) detectCameraIPInAPMode() else null

        val activeNetwork = runCatching { connectivityManager.activeNetwork }.getOrNull()
        val linkProps = activeNetwork?.let {
            runCatching { connectivityManager.getLinkProperties(it) }.getOrNull()
        }
        val gatewayIp = extractGatewayIp(linkProps)
        val subnetPrefix = extractSubnetPrefix(linkProps)
        val hotspotEnabled = isHotspotEnabled()

        return WifiNetworkState(
            isConnected = isConnected,
            isConnectedToCameraAP = isAP,
            ssid = ssid,
            detectedCameraIP = cameraIP,
            gatewayIp = gatewayIp,
            subnetPrefix = subnetPrefix,
            isHotspotEnabled = hotspotEnabled,
        )
    }

    /**
     * 현재 WiFi 네트워크에 프로세스 바인딩
     * 자동 연결 등에서 카메라 통신을 위해 사용
     */
    fun bindToCurrentNetwork(): Boolean {
        try {
            // 1. activeNetwork 확인
            val activeNetwork = connectivityManager.activeNetwork
            if (activeNetwork != null) {
                val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                    Log.d(TAG, "현재 WiFi 네트워크에 바인딩: $activeNetwork")
                    return connectivityManager.bindProcessToNetwork(activeNetwork)
                }
            }

            // 2. allNetworks에서 WiFi 찾기
            val allNetworks = connectivityManager.allNetworks
            for (network in allNetworks) {
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                    Log.d(TAG, "WiFi 네트워크 발견 및 바인딩: $network")
                    return connectivityManager.bindProcessToNetwork(network)
                }
            }

            Log.w(TAG, "바인딩할 WiFi 네트워크를 찾을 수 없음")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "네트워크 바인딩 중 오류", e)
            return false
        }
    }

    /**
     * 네트워크 바인딩 해제
     */
    fun unbindFromCurrentNetwork() {
        try {
            connectivityManager.bindProcessToNetwork(null)
            Log.d(TAG, "네트워크 바인딩 해제 완료")
        } catch (e: Exception) {
            Log.e(TAG, "네트워크 바인딩 해제 중 오류", e)
        }
    }

    /**
     * 현재 Wi-Fi 연결 상태 확인
     * 인터넷이 없는 WiFi(카메라 AP)도 감지합니다.
     */
    fun isWifiConnected(): Boolean {
        // 1. activeNetwork 확인 (인터넷이 있는 네트워크)
        val network = connectivityManager.activeNetwork
        if (network != null) {
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
//                Log.d(TAG, "WiFi 연결됨 (activeNetwork)")
                return true
            }
        }

        // 2. activeNetwork가 없거나 WiFi가 아닌 경우, allNetworks에서 WiFi 찾기
        // (인터넷 없는 WiFi는 activeNetwork에 포함되지 않을 수 있음)
        try {
            val allNetworks = connectivityManager.allNetworks
            for (net in allNetworks) {
                val cap = connectivityManager.getNetworkCapabilities(net)
                if (cap?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
//                    Log.d(TAG, "WiFi 연결됨 (allNetworks): $net")
                    return true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "allNetworks 확인 실패: ${e.message}")
        }

        // 3. WifiManager의 connectionInfo 확인 (최우선)
        // networkId가 -1이어도 SSID가 유효하면 연결된 것으로 판단
        try {
            val connectionInfo = wifiManager.connectionInfo
            if (connectionInfo != null) {
                val ssid = connectionInfo.ssid
                val networkId = connectionInfo.networkId

//                Log.d(TAG, "WifiManager connectionInfo 확인:")
//                Log.d(TAG, "  - SSID: $ssid")
//                Log.d(TAG, "  - NetworkId: $networkId")
//                Log.d(TAG, "  - BSSID: ${connectionInfo.bssid}")

                // SSID가 유효하면 연결된 것으로 판단 (networkId 무시)
                if (ssid != null && ssid != "<unknown ssid>" && ssid != "\"<unknown ssid>\"") {
//                    Log.d(TAG, "WiFi 연결됨 (connectionInfo - SSID 기반)")
                    return true
                }

                // SSID는 못 가져오지만 networkId가 유효한 경우
                if (networkId != -1) {
//                    Log.d(TAG, "WiFi 연결됨 (connectionInfo - NetworkId 기반)")
                    return true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "connectionInfo 확인 실패: ${e.message}")
        }

//        Log.d(TAG, "WiFi 연결 안 됨")
        return false
    }

    // 카메라 AP 연결 상태 캐싱
    // networkStateFlow 콜백(메인 Looper)과 PtpipDataSource 등 호출 스레드(IO)가 동시에
    // 읽고/쓰므로 cacheLock으로 접근을 직렬화한다. (clearCache·캐시 읽기/쓰기 모두 보호)
    private val cacheLock = Any()
    private var cachedIsConnectedToCameraAP: Boolean? = null
    private var lastCheckedSSID: String? = null

    /**
     * 현재 연결된 네트워크가 카메라 AP인지 확인
     */
    fun isConnectedToCameraAP(): Boolean {
        if (!isWifiConnected()) {
            synchronized(cacheLock) {
                cachedIsConnectedToCameraAP = false
                lastCheckedSSID = null
            }
            return false
        }

        // 폰 핫스팟이 켜져 있으면 폰 자신이 AP다 — 카메라 AP의 클라이언트일 수 없다.
        // STA+AP 동시 동작 시 활성 네트워크 게이트웨이가 폰/공유기(예: 192.168.0.1)라
        // <unknown ssid> 게이트웨이 패턴 폴백이 이를 카메라 AP로 오탐하던 것을 차단한다.
        // 이 오탐이 discoverCameras를 AP 경로로 몰아 게이트웨이만 찍고 실패시키던 원인.
        if (isHotspotEnabled()) {
            synchronized(cacheLock) {
                cachedIsConnectedToCameraAP = false
                lastCheckedSSID = null
            }
            return false
        }

        val ssid = getCurrentSSID()

        // 같은 SSID에 대해서는 캐시된 결과 반환 (락 안에서 스냅샷하여 check-then-use NPE 방지)
        synchronized(cacheLock) {
            val cached = cachedIsConnectedToCameraAP
            if (ssid == lastCheckedSSID && cached != null) {
                return cached
            }
        }

        // SSID를 가져올 수 없는 경우 (<unknown ssid>)
        // 하지만 WiFi는 연결되어 있으므로 추가 확인
        if (ssid == null || ssid == "<unknown ssid>") {
            // 게이트웨이 IP가 카메라 IP 패턴인지 확인 (무한 재귀 방지)
            try {
                val connectionInfo = wifiManager.connectionInfo
                val dhcpInfo = wifiManager.dhcpInfo
                val gatewayIP = dhcpInfo?.gateway

                if (gatewayIP != null && gatewayIP != 0) {
                    val gatewayIpStr = String.format(
                        "%d.%d.%d.%d",
                        gatewayIP and 0xff,
                        gatewayIP shr 8 and 0xff,
                        gatewayIP shr 16 and 0xff,
                        gatewayIP shr 24 and 0xff
                    )
                    if (isValidCameraAPIP(gatewayIpStr)) {
                        Log.d(TAG, "SSID를 알 수 없지만 게이트웨이가 카메라 IP 패턴: ${LogMask.id(gatewayIpStr)}")
                        synchronized(cacheLock) {
                            cachedIsConnectedToCameraAP = true
                            lastCheckedSSID = ssid
                        }
                        return true
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "게이트웨이 IP 확인 실패: ${e.message}")
            }

            synchronized(cacheLock) {
                cachedIsConnectedToCameraAP = false
                lastCheckedSSID = null
            }
            return false
        }

        // 카메라 AP 패턴 매칭 (Nikon Z_ 모델 대응)
        val isMatch = CAMERA_AP_PATTERNS.any { pattern ->
            ssid.contains(pattern, ignoreCase = true)
        } || ssid.startsWith("Z_", ignoreCase = true) // Nikon 카메라 패턴 추가

        // 결과 캐싱
        synchronized(cacheLock) {
            cachedIsConnectedToCameraAP = isMatch
            lastCheckedSSID = ssid
        }

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
     * Network 객체로부터 SSID 추출 (Android 보안 정책 우회)
     * WiFi Suggestion으로 연결된 네트워크는 이 방법으로만 SSID를 가져올 수 있습니다.
     */
    fun getSSIDFromNetwork(network: Network): String? {
        return try {
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
            if (networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                val transportInfo = networkCapabilities.transportInfo
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && transportInfo is android.net.wifi.WifiInfo) {
                    val rawSSID = transportInfo.ssid
                    val ssid = rawSSID?.trim('"')

                    // <unknown ssid>가 아니고 유효한 경우에만 반환
                    if (!ssid.isNullOrEmpty() && ssid != "<unknown ssid>") {
                        Log.d(TAG, "Network에서 SSID 추출 성공: ${LogMask.ssid(ssid)}")
                        return ssid
                    } else {
                        Log.w(TAG, "Network에서 SSID가 <unknown ssid> 또는 null: ${LogMask.ssid(rawSSID)}")
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Network에서 SSID 추출 실패: ${e.message}")
            null
        }
    }

    /**
     * 현재 연결된 네트워크의 BSSID 반환 (유효하지 않은 기본값은 제거)
     */
    fun getCurrentBssid(): String? {
        return try {
            if (!isWifiConnected()) {
                return null
            }
            val bssid = wifiManager.connectionInfo?.bssid
            if (bssid.isNullOrBlank() || bssid == "02:00:00:00:00:00") {
                null
            } else {
                bssid.lowercase()
            }
        } catch (error: SecurityException) {
            Log.w(TAG, "BSSID 조회 권한 부족: ${error.message}")
            null
        } catch (error: Exception) {
            Log.w(TAG, "BSSID 조회 실패: ${error.message}")
            null
        }
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
            Log.d(TAG, "게이트웨이 IP 발견: ${LogMask.id(gatewayIP)}")
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
                    Log.d(TAG, "DHCP 서버 IP 발견: ${LogMask.id(serverIpStr)}")
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
                    Log.d(TAG, "내 IP 주소: ${LogMask.id(myIpStr)}")

                    // 같은 서브넷의 .1 주소 시도
                    val networkBase = myIpStr.substringBeforeLast(".")
                    val guessedCameraIP = "$networkBase.1"
                    Log.d(TAG, "추정 카메라 IP: ${LogMask.id(guessedCameraIP)}")
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
        return withContext(ioDispatcher) {
            try {
                Log.d(TAG, "TCP 포트 확인: ${LogMask.id(ipAddress)}:$port")
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
                Log.d(TAG, "TCP 포트 확인 실패: ${LogMask.id(ipAddress)}:$port - ${e.message}")
                false
            }
        }
    }

    /**
     * PTP/IP 초기화 시도로 카메라 연결 테스트
     */
    private suspend fun testPtpipConnection(ipAddress: String, port: Int = 15740): Boolean {
        return try {
            withContext(ioDispatcher) {
                Log.d(TAG, "PTP/IP 초기화 테스트 시작: ${LogMask.id(ipAddress)}:$port")

                java.net.Socket().use { socket ->
                    socket.soTimeout = 3000
                    socket.connect(java.net.InetSocketAddress(ipAddress, port), 3000)

                    // PTP/IP Init Command Request 전송
                    val initPacket = createInitCommandRequest()
                    socket.getOutputStream().write(initPacket)
                    socket.getOutputStream().flush()

                    // ACK 응답 대기
                    val response = ByteArray(1024)
                    val bytesRead = socket.getInputStream().read(response)

                    // 응답 확인
                    if (bytesRead >= 8) {
                        val buffer =
                            java.nio.ByteBuffer.wrap(response)
                                .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                        buffer.position(4)
                        val responseType = buffer.int

                        if (responseType == 0x00000002) { // PTPIP_INIT_COMMAND_ACK
                            Log.d(TAG, "PTP/IP 초기화 성공: ${LogMask.id(ipAddress)}")
                            return@withContext true
                        }
                    }

                    Log.d(TAG, "PTP/IP 초기화 실패: ${LogMask.id(ipAddress)} - 잘못된 응답")
                    false
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "PTP/IP 초기화 실패: ${LogMask.id(ipAddress)} - ${e.message}")
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
     * - 카메라 관련 네트워크만 필터링하여 반환
     */
    suspend fun scanNearbyWifiSSIDs(): List<String> = withContext(ioDispatcher) {
        try {
            Log.d(TAG, "Wi-Fi 스캔 시작 (Android ${Build.VERSION.SDK_INT})")

            // 스캔 환경 진단 실행
            logWifiScanDiagnosis()

            // 통합 권한 체크
            val permissionStatus = analyzeWifiScanPermissionStatus()

            Log.d(TAG, "권한 상태 분석: 위치=${permissionStatus.hasFineLocationPermission}, 근처Wi-Fi=${permissionStatus.hasNearbyWifiDevicesPermission}, Wi-Fi활성=${permissionStatus.isWifiEnabled}, 위치서비스=${permissionStatus.isLocationEnabled}, 스캔가능=${permissionStatus.canScan}")

            if (permissionStatus.missingPermissions.isNotEmpty()) {
                Log.e(TAG, "부족한 권한: ${permissionStatus.missingPermissions}")
                return@withContext emptyList()
            }

            if (!permissionStatus.canScan) {
                Log.w(TAG, "Wi-Fi 스캔 조건이 충족되지 않음")
                return@withContext emptyList()
            }

            val results = mutableListOf<String>()

            // 방법 1: 실제 스캔 시도 (유일한 방법)
            val scanResults = performConditionalScan()
            if (scanResults.isNotEmpty()) {
                results.addAll(scanResults)
                Log.d(TAG, "실제 스캔에서 ${scanResults.size}개 발견")
            }

            // 방법 2: Android 13+ 전용 실험적 스캔
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && results.isEmpty()) {
                val android13Results = experimentalScanForAndroid13()
                android13Results.forEach { ssid ->
                    if (!results.contains(ssid)) {
                        results.add(ssid)
                    }
                }
            }

            // 방법 3: 현재 연결된 네트워크 정보만 (스캔 실패 시에만)
            if (results.isEmpty()) {
                val currentNetwork = getCurrentNetworkSSID()
                if (currentNetwork != null) {
                    results.add(currentNetwork)
                }
            }

            // 카메라 관련 네트워크만 필터링
            val filteredResults = filterCameraNetworks(results.distinct())

            Log.d(TAG, "Wi-Fi 스캔 완료: 전체 ${results.distinct().size}개, 카메라 관련 ${filteredResults.size}개")

            return@withContext filteredResults

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

            Log.d(TAG, "현재 네트워크 정보: ssid=${LogMask.ssid(rawSSID)}, bssid=${LogMask.bssid(connectionInfo?.bssid)}, ${connectionInfo?.rssi}dBm, networkId=${connectionInfo?.networkId}")

            when {
                rawSSID == null -> {
                    null
                }
                rawSSID == "<unknown ssid>" -> {
                    // BSSID를 기반으로 추정 시도
                    val bssid = connectionInfo?.bssid
                    if (bssid != null && bssid != "02:00:00:00:00:00") {
                        val estimatedSSID = "WiFi_${bssid.takeLast(5).replace(":", "")}"
                        Log.d(TAG, "unknown SSID - BSSID 기반 추정 사용")
                        estimatedSSID
                    } else {
                        null
                    }
                }

                rawSSID.startsWith("\"") && rawSSID.endsWith("\"") -> {
                    rawSSID.removeSurrounding("\"")
                }

                else -> {
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

    private var currentNetwork: Network? = null // 현재 바인딩된 네트워크 추적용

    // requestConnectionWithSpecifier로 등록한 NetworkCallback 추적용.
    // onAvailable 성공분은 바인딩 유지를 위해 unbindNetwork 시점까지 살려두고,
    // onUnavailable/타임아웃 등 실패분은 즉시 이 필드로 해제한다.
    @Volatile
    private var specifierNetworkCallback: ConnectivityManager.NetworkCallback? = null

    /**
     * requestConnectionWithSpecifier로 등록한 콜백을 한 번만 해제한다.
     */
    private fun unregisterSpecifierCallback() {
        val callback = specifierNetworkCallback ?: return
        specifierNetworkCallback = null
        try {
            connectivityManager.unregisterNetworkCallback(callback)
            Log.d(TAG, "Specifier NetworkCallback 해제됨")
        } catch (e: Exception) {
            Log.w(TAG, "Specifier NetworkCallback 해제 실패: ${e.message}")
        }
    }

    /**
     * 현재 바인딩된 카메라 네트워크 가져오기
     */
    fun getCurrentCameraNetwork(): Network? = currentNetwork

    /**
     * 네트워크 바인딩 해제
     */
    fun unbindNetwork() {
        try {
            connectivityManager.bindProcessToNetwork(null)
            currentNetwork = null
            Log.i(TAG, "네트워크 바인딩 해제됨")
        } catch (e: Exception) {
            Log.e(TAG, "네트워크 바인딩 해제 실패", e)
        }
        // 성공 경로에서 살려둔 콜백을 바인딩 해제 시점에 해제 (누수 방지)
        unregisterSpecifierCallback()
    }

    /**
     * 셀룰러 네트워크 객체 가져오기 (Firebase 등에서 사용)
     * bindProcessToNetwork로 Wi-Fi에 바인딩된 상태에서도
     * 특정 요청을 셀룰러로 보낼 수 있도록 합니다
     */
    fun getCellularNetwork(): Network? {
        try {
            val networks = connectivityManager.allNetworks
            for (network in networks) {
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                if (capabilities != null &&
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                ) {
                    Log.i(TAG, "✅ 셀룰러 네트워크 발견: $network")
                    return network
                }
            }
            Log.w(TAG, "셀룰러 네트워크를 찾을 수 없음")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "셀룰러 네트워크 조회 실패", e)
            return null
        }
    }

    /**
     * 현재 바인딩된 네트워크 반환
     */
    fun getBoundNetwork(): Network? = currentNetwork

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

            // WiFi가 켜져 있는지 확인
            if (!wifiManager.isWifiEnabled) {
                Log.w(TAG, "WiFi가 꺼져 있음 - 사용자에게 안내")
                val message = "WiFi가 꺼져 있습니다.\n\nWiFi를 켜고 다시 시도해주세요."
                onError?.invoke(message)
                onResult(false)
                return
            }

            // 현재 연결 상태 확인 및 로깅
            val currentSSID = getCurrentSSID()
            val currentlyConnected = isWifiConnected()
            Log.d(TAG, "현재 WiFi 상태: 활성화=${wifiManager.isWifiEnabled}, 연결됨=$currentlyConnected, ssid=${LogMask.ssid(currentSSID)}")

            // 이미 목표 네트워크에 연결되어 있는지 확인
            if (currentSSID == ssid) {
                Log.i(TAG, "이미 '${LogMask.ssid(ssid)}'에 연결되어 있음 - 즉시 성공 처리")
                onResult(true)
                return
            }

            // 연결 전 해당 SSID가 스캔 결과에 있는지 확인
            val availableSSIDs = try {
                wifiManager.scanResults?.map { it.SSID?.removeSurrounding("\"") } ?: emptyList()
            } catch (e: SecurityException) {
                Log.w(TAG, "스캔 결과 확인 권한 없음")
                emptyList()
            }

            Log.d(TAG, "WifiNetworkSpecifier 연결 전 SSID 확인: ${LogMask.ssid(ssid)}, 스캔 발견 ${availableSSIDs.size}개")

            val isSSIDAvailable = availableSSIDs.any { it == ssid || it?.contains(ssid) == true }
            if (!isSSIDAvailable && availableSSIDs.isNotEmpty()) {
                val message = "선택한 Wi-Fi '$ssid'가 현재 검색되지 않습니다.\n\n" +
                        "다음을 확인해주세요:\n" +
                        "1. 카메라 Wi-Fi가 켜져있는지 확인\n" +
                        "2. 카메라가 AP 모드로 설정되어 있는지 확인\n" +
                        "3. 거리가 너무 멀지 않은지 확인\n\n" +
                        "또는 Wi-Fi 설정에서 수동으로 연결해보세요."
                Log.w(TAG, "SSID '${LogMask.ssid(ssid)}'가 스캔 결과에 없음")
                onError?.invoke(message)
                onResult(false)
                return
            }

            // 보안 타입 확인
            val securityType = getWifiSecurityType(ssid)
            val requiresPassword = requiresPassword(ssid)
            Log.d(TAG, "SSID '${LogMask.ssid(ssid)}' 보안: ${securityType ?: "알 수 없음"}, 패스워드필요=$requiresPassword, 제공됨=${!passphrase.isNullOrEmpty()}")

            // 패스워드가 필요한데 제공되지 않은 경우 사용자에게 알림
            if (requiresPassword && passphrase.isNullOrEmpty()) {
                val message = "선택한 Wi-Fi '$ssid'는 패스워드가 필요합니다.\n\n" +
                        "보안 타입: ${securityType ?: "WPA/WPA2"}\n\n" +
                        "카메라의 Wi-Fi 패스워드를 확인하고 다시 시도하거나,\n" +
                        "시스템 Wi-Fi 설정에서 '$ssid'에 수동 연결해주세요."
                Log.w(TAG, "SSID '${LogMask.ssid(ssid)}'는 패스워드가 필요하지만 제공되지 않음")
                onError?.invoke(message)
                onResult(false)
                return
            }

            Log.d(TAG, "WifiNetworkSpecifier 연결 시도: ssid=${LogMask.ssid(ssid)}, 보안=${securityType ?: "OPEN"}, 패스워드길이=${passphrase?.length ?: 0}, 인터넷제외=$requireNoInternet")

            // BSSID 가져오기 (더 정확한 네트워크 지정)
            val targetBssid = try {
                wifiManager.scanResults?.find {
                    it.SSID?.removeSurrounding("\"") == ssid
                }?.BSSID
            } catch (e: Exception) {
                null
            }

            if (targetBssid != null) {
                Log.d(TAG, "  - Target BSSID: ${LogMask.bssid(targetBssid)} (더 정확한 네트워크 지정)")
            }

            val builder = WifiNetworkSpecifier.Builder()
                .setSsid(ssid)

            // BSSID가 있으면 추가 (더 정확한 연결)
            if (targetBssid != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val macAddress = MacAddress.fromString(targetBssid)
                    builder.setBssid(macAddress)
                    Log.d(TAG, "  - BSSID 지정 완료: ${LogMask.bssid(targetBssid)}")
                } catch (e: Exception) {
                    Log.w(TAG, "  - BSSID 지정 실패: ${e.message}")
                }
            }

            if (!passphrase.isNullOrEmpty()) {
                // 보안 타입에 따라 다른 메서드 사용
                when {
                    securityType?.contains("WPA3", ignoreCase = true) == true &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                        // WPA3-SAE 보안 사용 (WPA3의 기본 모드)
                        builder.setWpa3Passphrase(passphrase)
                        Log.d(TAG, "  - WPA3 패스워드 설정")
                    }

                    securityType?.contains("SAE", ignoreCase = true) == true &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                        // SAE (Simultaneous Authentication of Equals) = WPA3
                        builder.setWpa3Passphrase(passphrase)
                        Log.d(TAG, "  - WPA3-SAE 패스워드 설정")
                    }

                    securityType?.contains("WPA2", ignoreCase = true) == true -> {
                        builder.setWpa2Passphrase(passphrase)
                        Log.d(TAG, "  - WPA2 패스워드 설정")
                    }

                    securityType?.contains("WPA", ignoreCase = true) == true -> {
                        // WPA2/WPA3 혼합 모드인 경우 WPA2로 연결 시도
                        builder.setWpa2Passphrase(passphrase)
                        Log.d(TAG, "  - WPA/WPA2 패스워드 설정")
                    }

                    else -> {
                        // 보안 타입을 알 수 없는 경우 WPA2로 시도 (가장 일반적)
                        builder.setWpa2Passphrase(passphrase)
                        Log.d(TAG, "  - 기본 WPA2 패스워드 설정")
                    }
                }
            }

            val specifier = builder.build()

            val requestBuilder = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(specifier)

            if (requireNoInternet) {
                requestBuilder.removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                Log.d(TAG, "  - 인터넷 검증 제외")
            }

            val request = requestBuilder.build()

            // 타임아웃 설정 (30초)
            val timeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
            var isCallbackInvoked = false

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (isCallbackInvoked) return
                    isCallbackInvoked = true
                    timeoutHandler.removeCallbacksAndMessages(null)

                    Log.i(TAG, "WifiNetworkSpecifier 연결 성공: ssid=${LogMask.ssid(ssid)}, network=$network, 바인딩=$bindProcess")

                    if (bindProcess) {
                        try {
                            // 프로세스를 카메라 Wi-Fi에 바인딩
                            // 이렇게 하면 libgphoto2가 카메라에 연결할 수 있습니다
                            connectivityManager.bindProcessToNetwork(network)
                            currentNetwork = network
                            Log.d(TAG, "✅ 네트워크 바인딩 성공")

                            // Wi-Fi 퍼포먼스 락 적용
                            if (acquireWifiLock("PTP_IP_HighPerf")) {
                                Log.i(TAG, "🚀 Wi-Fi 퍼포먼스 락 활성화 - PTP/IP 최적화")
                            }

                            // 안정화 지연 + 주파수 정보 로깅은 콜백 스레드(메인 Looper) 블로킹을
                            // 피하기 위해 managed scope로 오프로드한다. onResult는 즉시 호출된다.
                            helperScope.launch {
                                // 연결 안정화를 위한 짧은 지연
                                delay(800)

                                // 네트워크 상태 재확인
                                val capabilities =
                                    connectivityManager.getNetworkCapabilities(network)
                                if (capabilities != null) {
                                    Log.d(
                                        TAG,
                                        "네트워크 기능 확인: WiFi=${
                                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                                        }"
                                    )
                                }

                                // 연결된 Wi-Fi 주파수 정보 출력 (추가 지연 후)
                                delay(500) // Wi-Fi 정보 완전 설정 대기
                                val freqInfo = getCurrentWifiFrequencyInfo()
                                if (freqInfo != null) {
                                    Log.i(TAG, "현재 Wi-Fi 주파수: ssid=${LogMask.ssid(freqInfo.ssid)}, ${freqInfo.frequency}MHz(${freqInfo.band}), ${freqInfo.linkSpeed}Mbps, ${freqInfo.rssi}dBm")
                                } else {
                                    Log.w(TAG, "Wi-Fi 주파수 정보를 가져올 수 없음")
                                }
                            }

                        } catch (e: Exception) {
                            Log.w(TAG, "네트워크 바인딩 실패: ${e.message}")
                        }
                    }
                    onResult(true)
                }

                override fun onUnavailable() {
                    if (isCallbackInvoked) return
                    isCallbackInvoked = true
                    timeoutHandler.removeCallbacksAndMessages(null)

                    val detectedSecurity = getWifiSecurityType(ssid)
                    Log.w(TAG, "WifiNetworkSpecifier 연결 불가: ssid=${LogMask.ssid(ssid)}, 패스워드제공=${!passphrase.isNullOrEmpty()}, 보안=$detectedSecurity, 인터넷제외=$requireNoInternet")

                    val message = buildString {
                        appendLine("자동 연결에 실패했습니다.")
                        appendLine()
                        appendLine("가능한 원인:")
                        appendLine("1. Wi-Fi '$ssid'가 범위를 벗어남")
                        appendLine("2. 패스워드가 잘못되었거나 변경됨")

                        // 패스워드 힌트 제공
                        if (!passphrase.isNullOrEmpty()) {
                            appendLine("   (입력한 패스워드 길이: ${passphrase.length}자)")
                        }

                        appendLine("3. 카메라가 이미 다른 기기와 연결 중")
                        appendLine("   → 카메라 Wi-Fi를 끄고 다시 켜보세요")
                        appendLine("4. 카메라 Wi-Fi가 일시적으로 꺼짐")
                        appendLine()
                        appendLine("해결 방법:")
                        appendLine("• 카메라 Wi-Fi 상태 확인 후 재시도")
                        appendLine("• 시스템 Wi-Fi 설정에서 '$ssid'에 수동 연결")
                        appendLine("• 카메라와의 거리를 가깝게 한 후 재시도")
                        appendLine("• 카메라의 Wi-Fi 패스워드를 다시 확인")
                    }

                    onError?.invoke(message)
                    onResult(false)
                    // 실패 경로: 콜백을 즉시 해제해 누수 방지
                    unregisterSpecifierCallback()
                }

                override fun onLost(network: Network) {
                    Log.w(TAG, "WifiNetworkSpecifier 연결 손실: ssid=${LogMask.ssid(ssid)} (Network: $network)")
                    // 연결 손실 시 재연결 시도하지 않고 로그만 남김
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                }
            }

            // 45초 타임아웃 설정 (카메라 연결은 시간이 더 걸릴 수 있음)
            timeoutHandler.postDelayed({
                if (!isCallbackInvoked) {
                    isCallbackInvoked = true
                    Log.w(TAG, "⏱️ WifiNetworkSpecifier 연결 타임아웃 (45초)")

                    val message = buildString {
                        appendLine("연결 시도가 45초 동안 응답이 없어 종료되었습니다.")
                        appendLine()
                        appendLine("이 문제는 다음 원인일 수 있습니다:")
                        appendLine("1. Android 시스템이 '$ssid'에 연결을 허용하지 않음")
                        appendLine("2. 카메라 WiFi 보안 설정이 Android와 호환되지 않음")
                        appendLine("3. 카메라가 이미 다른 기기와 연결 중")
                        appendLine()
                        appendLine("해결 방법:")
                        appendLine("• 시스템 WiFi 설정에서 '$ssid'에 수동으로 연결해보세요")
                        appendLine("• 카메라 WiFi를 끄고 다시 켜보세요")
                        appendLine("• 카메라 WiFi 보안 설정을 WPA2로 변경해보세요")
                        appendLine("• 스마트폰을 재시작해보세요")
                    }

                    onError?.invoke(message)
                    onResult(false)

                    // 타임아웃 경로: 멤버 필드를 통해 일관 해제
                    unregisterSpecifierCallback()
                }
            }, 45000) // 45초

            // 이전 호출에서 살아남은 콜백이 있으면 먼저 해제 (반복 자동 연결 누수 방지)
            unregisterSpecifierCallback()
            connectivityManager.requestNetwork(request, callback)
            // requestNetwork 성공 후에만 추적 필드에 저장 (미등록 콜백 해제 시도 방지)
            specifierNetworkCallback = callback
            Log.d(TAG, "WifiNetworkSpecifier 요청 전송 완료")

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
     * WifiNetworkSuggestion 지원 여부 확인
     */
    fun isNetworkSuggestionSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }

    /**
     * WifiNetworkSuggestion 등록
     */
    fun registerNetworkSuggestion(config: AutoConnectNetworkConfig): SuggestionResult {
        if (!isNetworkSuggestionSupported()) {
            return SuggestionResult(
                success = false,
                message = "Android 10 이상에서만 자동 Wi-Fi 연결을 지원합니다."
            )
        }

        return try {
            Log.d(TAG, "WiFi Suggestion 등록 시작: ssid=${LogMask.ssid(config.ssid)}, hidden=${config.isHidden}, bssid=${LogMask.bssid(config.bssid)}, security=${config.securityType}, 패스워드제공=${!config.passphrase.isNullOrEmpty()}")

            val suggestion = buildWifiNetworkSuggestion(config) ?: return SuggestionResult(
                success = false,
                message = "지원되지 않는 보안 방식으로 인해 자동 연결을 설정할 수 없습니다."
            )

            val status = wifiManager.addNetworkSuggestions(listOf(suggestion))
            Log.d(TAG, "addNetworkSuggestions 결과 코드: $status")

            when (status) {
                WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS -> {
                    Log.d(TAG, "WiFi Suggestion 등록 성공")
                    SuggestionResult(true, "카메라 Wi-Fi 자동 연결이 설정되었습니다.")
                }

                WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_EXCEEDS_MAX_PER_APP -> {
                    Log.w(TAG, "등록 실패: 최대 개수 초과")
                    SuggestionResult(false, "자동 연결 가능한 네트워크 수를 초과했습니다. 다른 제안을 제거한 뒤 다시 시도해주세요.")
                }

                WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE -> {
                    Log.w(TAG, "중복: 이미 등록된 suggestion")
                    SuggestionResult(true, "이미 카메라 자동 연결이 등록되어 있습니다.")
                }

                WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_INTERNAL -> {
                    Log.e(TAG, "등록 실패: 시스템 내부 오류")
                    SuggestionResult(false, "시스템 내부 오류로 자동 연결을 등록하지 못했습니다.")
                }

                WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_APP_DISALLOWED -> {
                    Log.e(TAG, "등록 실패: 앱 권한 거부")
                    SuggestionResult(false, "시스템에서 자동 연결 권한을 거부했습니다. 설정 앱에서 권한을 다시 허용해주세요.")
                }

                WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_NOT_ALLOWED -> {
                    Log.e(TAG, "등록 실패: 현재 상태에서 등록 불가")
                    SuggestionResult(false, "현재 상태에서는 자동 연결 제안을 추가할 수 없습니다. 잠시 후 다시 시도해주세요.")
                }

                else -> {
                    Log.e(TAG, "등록 실패: 알 수 없는 오류 (코드: $status)")
                    SuggestionResult(false, "알 수 없는 이유로 자동 연결 등록에 실패했습니다. (코드: $status)")
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "등록 실패: 권한 부족", e)
            SuggestionResult(false, "자동 연결 등록에 필요한 권한이 부족합니다.")
        } catch (e: Exception) {
            Log.e(TAG, "WifiNetworkSuggestion 등록 실패", e)
            SuggestionResult(false, "자동 연결 등록 중 오류가 발생했습니다: ${e.message}")
        }
    }

    /**
     * WifiNetworkSuggestion 제거
     */
    fun removeNetworkSuggestion(config: AutoConnectNetworkConfig): SuggestionResult {
        if (!isNetworkSuggestionSupported()) {
            return SuggestionResult(false, "Android 10 이상에서만 자동 연결을 지원합니다.")
        }

        return try {
            Log.d(
                TAG,
                "자동 연결 제안 제거 시도: ssid=${LogMask.ssid(config.ssid)}, bssid=${LogMask.bssid(config.bssid)}, security=${config.securityType}"
            )
            val suggestion = buildWifiNetworkSuggestion(config) ?: return SuggestionResult(
                true,
                "자동 연결 정보가 없어도 이미 해제되었습니다."
            )
            val status = wifiManager.removeNetworkSuggestions(listOf(suggestion))
            Log.d(TAG, "removeNetworkSuggestions 결과 코드: $status")
            when (status) {
                WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS -> SuggestionResult(
                    true,
                    "카메라 자동 연결이 해제되었습니다."
                )

                WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_INTERNAL -> SuggestionResult(
                    false,
                    "시스템 오류로 자동 연결 해제에 실패했습니다."
                )

                else -> SuggestionResult(false, "자동 연결 해제 중 알 수 없는 오류가 발생했습니다. (코드: $status)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "WifiNetworkSuggestion 제거 실패", e)
            SuggestionResult(false, "자동 연결 해제 중 오류가 발생했습니다: ${e.message}")
        }
    }

    /**
     * 현재 등록된 제안 모두 제거
     */
    fun clearAllNetworkSuggestions(): SuggestionResult {
        if (!isNetworkSuggestionSupported()) {
            return SuggestionResult(false, "Android 10 이상에서만 자동 연결을 지원합니다.")
        }

        return try {
            Log.d(TAG, "모든 자동 연결 제안 초기화 시도")
            val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val suggestions = wifiManager.networkSuggestions
                Log.d(TAG, "현재 등록된 제안 수: ${suggestions.size}")
                wifiManager.removeNetworkSuggestions(suggestions)
            } else {
                wifiManager.removeNetworkSuggestions(emptyList())
            }
            Log.d(TAG, "clearAllNetworkSuggestions 결과 코드: $status")
            when (status) {
                WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS -> SuggestionResult(
                    true,
                    "자동 연결 제안을 모두 초기화했습니다."
                )

                else -> SuggestionResult(false, "자동 연결 초기화 중 오류가 발생했습니다. (코드: $status)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "WifiNetworkSuggestion 전체 초기화 실패", e)
            SuggestionResult(false, "자동 연결 초기화 중 오류가 발생했습니다: ${e.message}")
        }
    }

    private fun buildWifiNetworkSuggestion(config: AutoConnectNetworkConfig): WifiNetworkSuggestion? {
        val builder = WifiNetworkSuggestion.Builder()
            .setSsid(config.ssid)
            .setIsHiddenSsid(config.isHidden)
            .setIsAppInteractionRequired(false)  // false로 변경: 사용자 상호작용 없이 자동 연결
            .setPriority(Int.MAX_VALUE)  // 최고 우선순위로 자동 연결 유도

        // 브로드캐스트 트리거: 사용자 상호작용 없이 연결되도록 명시적으로 설정 (API 29+)
        try {
            val interactionMethod = builder.javaClass.getMethod(
                "setIsUserInteractionRequired",
                Boolean::class.javaPrimitiveType
            )
            interactionMethod.invoke(builder, false)
            Log.d(TAG, "setIsUserInteractionRequired(false) 적용됨 (브로드캐스트 트리거 조건)")
        } catch (e: Exception) {
            Log.w(TAG, "setIsUserInteractionRequired 메서드 적용 실패: ${e.message}")
        }

        if (!config.bssid.isNullOrBlank() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val macAddress = MacAddress.fromString(config.bssid)
                builder.setBssid(macAddress)
            } catch (error: IllegalArgumentException) {
                Log.w(TAG, "잘못된 BSSID 형식으로 인해 설정할 수 없습니다: ${LogMask.bssid(config.bssid)}")
            }
        }

        // 보안 설정 먼저 적용
        if (!applySecurityToSuggestion(builder, config)) {
            return null
        }

        // Android 버전별 추가 설정 및 브로드캐스트 트리거 관련 로깅
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                // Android 13+ (API 33)
                try {
                    builder.setIsMetered(false)
                    builder.setCredentialSharedWithUser(true)
                    // Android 13+에서는 POST_CONNECTION 브로드캐스트가 제한적일 수 있음
                    Log.d(TAG, "Android 13+ (TIRAMISU): Credential 공유 + non-metered 설정 완료")
                    Log.d(TAG, "⚠️ Android 13+에서는 시스템 브로드캐스트가 제한될 수 있습니다")
                } catch (error: Throwable) {
                    Log.w(TAG, "Android 13+ 설정 중 예외 발생: ${error.message}")
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                // Android 12/12L (API 31-32)
                try {
                    builder.setIsMetered(false)
                    builder.setCredentialSharedWithUser(true)
                    Log.d(TAG, "Android 12+ (S): Credential 공유 + non-metered 설정 완료")
                } catch (error: Throwable) {
                    Log.w(TAG, "Android 12+ 설정 중 예외 발생: ${error.message}")
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                // Android 11 (API 30)
                try {
                    builder.setIsMetered(false)
                    builder.setCredentialSharedWithUser(true)
                    Log.d(TAG, "Android 11 (R): Credential 공유 + non-metered 설정 완료")
                } catch (error: Throwable) {
                    Log.w(TAG, "Android 11 설정 중 예외 발생: ${error.message}")
                }
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                // Android 10 (API 29)
                try {
                    val method = builder.javaClass.getMethod(
                        "setIsInternetRequired",
                        Boolean::class.javaPrimitiveType
                    )
                    method.invoke(builder, false)
                    Log.d(TAG, "Android 10 (Q): 인터넷 검증 제외 플래그 설정 완료")
                } catch (error: NoSuchMethodException) {
                    Log.w(TAG, "setIsInternetRequired 메서드를 사용할 수 없습니다: ${error.message}")
                } catch (error: Throwable) {
                    Log.w(TAG, "Android 10 설정 중 예외 발생: ${error.message}")
                }
            }

            else -> {
                Log.w(
                    TAG,
                    "Android 10 미만에서는 WiFi Suggestion이 지원되지 않습니다 (현재: API ${Build.VERSION.SDK_INT})"
                )
                return null
            }
        }

        return try {
            val suggestion = builder.build()
            Log.d(TAG, "WifiNetworkSuggestion 빌드 성공: ssid=${LogMask.ssid(config.ssid)}, priority=${Int.MAX_VALUE}, API=${Build.VERSION.SDK_INT}")
            suggestion
        } catch (e: IllegalStateException) {
            Log.e(TAG, "❌ WifiNetworkSuggestion 빌드 실패: ${e.message}", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "❌ WifiNetworkSuggestion 빌드 중 예외: ${e.message}", e)
            null
        }
    }

    private fun applySecurityToSuggestion(
        builder: WifiNetworkSuggestion.Builder,
        config: AutoConnectNetworkConfig
    ): Boolean {
        return when {
            config.passphrase.isNullOrEmpty() -> {
                // 개방형 네트워크
                builder.setIsEnhancedOpen(false)
                Log.d(TAG, "WifiSuggestion: 개방형 네트워크 설정")
                true
            }

            config.securityType?.contains("WPA3", ignoreCase = true) == true ||
                    config.securityType?.contains("SAE", ignoreCase = true) == true -> {
                // WPA3 또는 WPA3-SAE 보안
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    builder.setWpa3Passphrase(config.passphrase)
                    Log.d(TAG, "WifiSuggestion: WPA3-SAE 패스워드 설정")
                    true
                } else {
                    Log.w(TAG, "WPA3는 Android 10 이상에서만 지원됨")
                    // Android 10 미만에서는 WPA2로 폴백
                    builder.setWpa2Passphrase(config.passphrase)
                    Log.d(TAG, "WifiSuggestion: WPA2로 폴백")
                    true
                }
            }

            config.securityType?.contains("WPA2", ignoreCase = true) == true -> {
                // WPA2 보안
                builder.setWpa2Passphrase(config.passphrase)
                Log.d(TAG, "WifiSuggestion: WPA2 패스워드 설정")
                true
            }

            config.securityType?.contains("WPA", ignoreCase = true) == true -> {
                // WPA 또는 WPA/WPA2 혼합 모드
                builder.setWpa2Passphrase(config.passphrase)
                Log.d(TAG, "WifiSuggestion: WPA/WPA2 패스워드 설정")
                true
            }

            else -> {
                // 보안 타입을 알 수 없는 경우 WPA2로 시도
                Log.w(TAG, "알 수 없는 보안 타입: ${config.securityType}, WPA2로 시도")
                builder.setWpa2Passphrase(config.passphrase)
                true
            }
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
        synchronized(cacheLock) {
            cachedIsConnectedToCameraAP = null
            lastCheckedSSID = null
        }
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
            diagnosis["current_ssid"] = LogMask.ssid(getCurrentSSID())

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

        Log.d(TAG, "Wi-Fi 스캔 환경 진단: $diagnosis")
    }

    /**
     * Android 13+ 전용 실험적 스캔 (NEARBY_WIFI_DEVICES만 사용)
     */
    suspend fun experimentalScanForAndroid13(): List<String> = withContext(ioDispatcher) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Log.d(TAG, "Android 13 미만에서는 실행되지 않음")
            return@withContext emptyList()
        }

        try {
            Log.d(TAG, "Android 13+ 실험적 스캔 시작")

            // NEARBY_WIFI_DEVICES 권한만 확인
            val hasNearbyWifiDevices = ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.NEARBY_WIFI_DEVICES
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (!hasNearbyWifiDevices) {
                Log.w(TAG, "NEARBY_WIFI_DEVICES 권한이 없음")
                return@withContext emptyList()
            }

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

            Log.d(TAG, "실험적 스캔 완료: ${results.size}개 발견")
            return@withContext results

        } catch (e: Exception) {
            Log.e(TAG, "실험적 스캔 중 오류", e)
            return@withContext emptyList()
        }
    }

    /**
     * Wi-Fi 스캔에 필요한 권한들을 확인
     */
    override fun getRequiredWifiScanPermissions(): List<String> {
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
    override fun analyzeWifiScanPermissionStatus(): WifiScanPermissionStatus {
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
    override fun getPermissionRationaleMessage(): String {
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
     * WPA2/WPA3 혼합 모드인 경우 WPA2를 반환 (호환성 우선)
     */
    fun getWifiSecurityType(ssid: String): String? {
        return try {
            val scanResults = wifiManager.scanResults
            val targetNetwork = scanResults?.find {
                it.SSID?.removeSurrounding("\"") == ssid
            }

            if (targetNetwork != null) {
                val capabilities = targetNetwork.capabilities
                Log.d(TAG, "SSID '${LogMask.ssid(ssid)}' 보안 정보: $capabilities")

                // WPA2/WPA3 혼합 모드 감지
                val hasWPA2 = capabilities.contains("WPA2", ignoreCase = true) ||
                        capabilities.contains("PSK", ignoreCase = true)
                val hasWPA3 = capabilities.contains("WPA3", ignoreCase = true) ||
                        capabilities.contains("SAE", ignoreCase = true)

                // 우선순위: WPA2/WPA3 혼합 > WPA2 > WPA3 > WPA > WEP > OPEN
                // 혼합 모드인 경우 호환성을 위해 WPA2로 연결
                when {
                    hasWPA2 && hasWPA3 -> {
                        Log.d(TAG, "WPA2/WPA3 혼합 모드 감지 - WPA2로 연결 시도 (호환성 우선)")
                        "WPA2"
                    }

                    capabilities.contains("WPA2", ignoreCase = true) -> "WPA2"
                    capabilities.contains("WPA3", ignoreCase = true) -> "WPA3"
                    capabilities.contains("SAE", ignoreCase = true) -> "WPA3-SAE"
                    capabilities.contains("WPA", ignoreCase = true) -> "WPA"
                    capabilities.contains("WEP", ignoreCase = true) -> "WEP"
                    else -> "OPEN"
                }
            } else {
                Log.w(TAG, "SSID '${LogMask.ssid(ssid)}'를 스캔 결과에서 찾을 수 없음")
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

    /**
     * 현재 연결된 네트워크에서 카메라 IP 즉시 감지
     */
    fun detectCameraIPFromCurrentNetwork(): String? {
        return try {
            Log.d(TAG, "현재 네트워크에서 카메라 IP 감지 시작")

            // 1. 게이트웨이 IP 확인
            val dhcpInfo = wifiManager.dhcpInfo
            if (dhcpInfo != null) {
                val gatewayIP = dhcpInfo.gateway
                if (gatewayIP != 0) {
                    val gatewayIpStr = String.format(
                        "%d.%d.%d.%d",
                        gatewayIP and 0xff,
                        gatewayIP shr 8 and 0xff,
                        gatewayIP shr 16 and 0xff,
                        gatewayIP shr 24 and 0xff
                    )
                    Log.d(TAG, "게이트웨이 IP 감지: ${LogMask.id(gatewayIpStr)}")
                    return gatewayIpStr
                }
            }

            // 2. 연결 정보에서 IP 추출
            val connectionInfo = wifiManager.connectionInfo
            if (connectionInfo != null) {
                val ipAddress = connectionInfo.ipAddress
                if (ipAddress != 0) {
                    val networkBase = String.format(
                        "%d.%d.%d",
                        ipAddress and 0xff,
                        ipAddress shr 8 and 0xff,
                        ipAddress shr 16 and 0xff
                    )
                    val cameraIP = "$networkBase.1"
                    Log.d(TAG, "추정 카메라 IP: ${LogMask.id(cameraIP)}")
                    return cameraIP
                }
            }

            // 3. 기본값 반환
            Log.d(TAG, "기본 카메라 IP 사용: 192.168.1.1")
            return "192.168.1.1"

        } catch (e: Exception) {
            Log.e(TAG, "카메라 IP 감지 실패: ${e.message}")
            return "192.168.1.1"
        }
    }
}

/**
 * Wi-Fi 주파수 정보 데이터 모델
 */
data class WifiFrequencyInfo(
    val frequency: Int,
    val band: String,
    val linkSpeed: Int?,
    val rssi: Int?,
    val ssid: String?
)