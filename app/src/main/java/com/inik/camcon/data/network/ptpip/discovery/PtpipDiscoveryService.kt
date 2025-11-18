package com.inik.camcon.data.network.ptpip.discovery

import android.content.Context
import android.content.SharedPreferences
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.inik.camcon.data.constants.PtpipConstants
import com.inik.camcon.data.network.ptpip.wifi.WifiNetworkHelper
import com.inik.camcon.domain.model.PtpipCamera
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * PTPIP 카메라 발견 서비스 (최적화된 mDNS 검색)
 *
 * 검색 전략:
 * 1. 캐시된 IP 직접 시도 (0.5-1초) - 가장 빠름
 * 2. AP 모드 감지 및 처리
 * 3. mDNS 멀티캐스트 검색 (2-5초)
 * 4. 기본 IP 주소들 시도
 */
@Singleton
class PtpipDiscoveryService @Inject constructor(
    private val context: Context,
    private val wifiHelper: WifiNetworkHelper
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    // SharedPreferences for caching last known camera IP
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "camera_discovery_cache",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val TAG = "PtpipDiscoveryService"
        private const val PREF_LAST_CAMERA_IP = "last_camera_ip"
        private const val PREF_LAST_CAMERA_NAME = "last_camera_name"
        private const val PREF_LAST_SUCCESS_TIME = "last_success_time"
        private const val CACHE_VALID_DURATION_MS = 24 * 60 * 60 * 1000L // 24시간
    }

    /**
     * PTPIP 지원 카메라 검색 (최적화된 빠른 검색)
     */
    suspend fun discoverCameras(forceApMode: Boolean = false): List<PtpipCamera> =
        withContext(Dispatchers.IO) {
            val cameras = mutableListOf<PtpipCamera>()

            Log.i(TAG, "========================================")
            Log.i(TAG, "=== 카메라 검색 시작 (최적화 모드) ===")
            Log.i(TAG, "========================================")

            try {
                // 1단계: 캐시된 IP로 빠른 시도 (STA 모드에서만) - 디버깅용 비활성화
                if (!forceApMode && !wifiHelper.isConnectedToCameraAP()) {
                    Log.d(TAG, "1단계: 캐시된 IP 확인 시도...")
                    val cachedIP = prefs.getString(PREF_LAST_CAMERA_IP, null)
                    val cachedName = prefs.getString(PREF_LAST_CAMERA_NAME, null)

                    if (cachedIP != null && cachedName != null) {
                        Log.d(TAG, "캐시된 IP 정보: $cachedIP ($cachedName)")
                        Log.w(TAG, "⚠️ 캐시 무시하고 mDNS 검색 진행 (디버깅 모드)")
                    } else {
                        Log.d(TAG, "캐시된 IP 정보 없음")
                    }
                }

                // 2단계: AP 모드 처리
                if (forceApMode || wifiHelper.isConnectedToCameraAP()) {
                    Log.d(TAG, "2단계: AP 모드 처리 - 게이트웨이 IP 직접 연결 시도")

                    val gatewayIP = wifiHelper.detectCameraIPInAPMode()
                    if (gatewayIP != null) {
                        Log.i(TAG, "게이트웨이 IP 발견: $gatewayIP")

                        if (testPtpipConnection(gatewayIP, 15740)) {
                            Log.i(TAG, "✅ AP 모드: 게이트웨이 PTP-IP 연결 성공!")
                            val networkName = wifiHelper.getCurrentSSID() ?: "카메라 AP"

                            val apCamera = PtpipCamera(
                                ipAddress = gatewayIP,
                                port = 15740,
                                name = "$networkName (AP모드)",
                                isOnline = true
                            )
                            cameras.add(apCamera)

                            // 캐시 저장
                            saveCachedIP(gatewayIP, apCamera.name)
                            return@withContext cameras
                        }
                    }

                    // 게이트웨이 실패 시 기본 IP들 시도
                    for (ip in PtpipConstants.DEFAULT_CAMERA_IPS) {
                        if (testPtpipConnection(ip, 15740)) {
                            Log.i(TAG, "✅ 기본 IP PTP-IP 연결 성공: $ip")
                            val networkName = wifiHelper.getCurrentSSID() ?: "카메라 AP"

                            val apCamera = PtpipCamera(
                                ipAddress = ip,
                                port = 15740,
                                name = "$networkName (AP모드 - $ip)",
                                isOnline = true
                            )
                            cameras.add(apCamera)
                            saveCachedIP(ip, apCamera.name)
                            break
                        }
                    }

                    if (cameras.isEmpty()) {
                        Log.w(TAG, "❌ AP 모드에서 PTP-IP 연결 가능한 카메라를 찾을 수 없음")
                    }

                    return@withContext cameras
                }

                // 3단계: STA 모드에서 mDNS 검색
                Log.d(TAG, "3단계: STA 모드 - mDNS를 사용한 카메라 자동 검색")
                Log.d(TAG, "mDNS 서비스 검색 시작...")

                // mDNS로 여러 PTP 서비스 타입 동시 검색 (각 서비스가 개별 타임아웃을 가짐)
                val discoveredServices = discoverPtpServicesMultiType()

                Log.d(TAG, "mDNS 검색 완료: ${discoveredServices.size}개 서비스 발견")

                // 발견된 서비스들을 PtpipCamera 객체로 변환
                for (serviceInfo in discoveredServices) {
                    try {
                        val hostAddress = serviceInfo.host?.hostAddress
                        val port = serviceInfo.port
                        val serviceName = serviceInfo.serviceName

                        if (hostAddress != null && port > 0) {
                            Log.d(TAG, "서비스 정보: $serviceName ($hostAddress:$port)")

                            val cameraName = extractCameraName(serviceName, hostAddress)

                            val camera = PtpipCamera(
                                ipAddress = hostAddress,
                                port = port,
                                name = cameraName,
                                isOnline = true
                            )
                            cameras.add(camera)

                            // 첫 번째 발견된 카메라를 캐시에 저장
                            if (cameras.size == 1) {
                                saveCachedIP(hostAddress, cameraName)
                            }

                            Log.i(TAG, "카메라 발견: $cameraName ($hostAddress:$port)")
                        } else {
                            Log.w(TAG, "유효하지 않은 서비스 정보: $serviceName")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "서비스 정보 처리 중 오류: ${e.message}")
                    }
                }

                // 4단계: mDNS 실패 시 기본 IP들 시도
                if (cameras.isEmpty()) {
                    Log.d(TAG, "4단계: mDNS 실패 - 기본 IP 주소들 시도")
                    for (ip in PtpipConstants.DEFAULT_CAMERA_IPS) {
                        if (testPtpipConnection(ip, 15740)) {
                            Log.i(TAG, "✅ 기본 IP PTP-IP 연결 성공: $ip")

                            val camera = PtpipCamera(
                                ipAddress = ip,
                                port = 15740,
                                name = "PTPIP Camera ($ip)",
                                isOnline = true
                            )
                            cameras.add(camera)
                            saveCachedIP(ip, camera.name)
                            break
                        }
                    }
                }

                Log.i(TAG, "========================================")
                Log.i(TAG, "카메라 검색 완료: ${cameras.size}개 발견")
                Log.i(TAG, "========================================")

                if (cameras.isNotEmpty()) {
                    cameras.forEachIndexed { index, camera ->
                        Log.i(
                            TAG,
                            "  ${index + 1}. ${camera.name} (${camera.ipAddress}:${camera.port})"
                        )
                    }
                } else {
                    Log.w(TAG, "카메라를 찾을 수 없습니다")
                }

            } catch (e: Exception) {
                Log.e(TAG, "카메라 검색 중 오류", e)
            }

            cameras
        }

    /**
     * 캐시된 IP로 빠른 연결 시도
     */
    private suspend fun tryCachedIP(): PtpipCamera? = withContext(Dispatchers.IO) {
        val cachedIP = prefs.getString(PREF_LAST_CAMERA_IP, null)
        val cachedName = prefs.getString(PREF_LAST_CAMERA_NAME, null)
        val lastSuccessTime = prefs.getLong(PREF_LAST_SUCCESS_TIME, 0)

        if (cachedIP == null || cachedName == null) {
            Log.d(TAG, "캐시된 IP 정보 없음")
            return@withContext null
        }

        // 캐시 유효 기간 확인
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSuccessTime > CACHE_VALID_DURATION_MS) {
            Log.d(TAG, "캐시된 IP 정보가 너무 오래됨 (${(currentTime - lastSuccessTime) / 1000 / 60}분 경과)")
            return@withContext null
        }

        Log.d(TAG, "캐시된 IP 시도: $cachedIP ($cachedName)")

        // 짧은 타임아웃으로 빠르게 시도
        val success = withTimeoutOrNull(PtpipConstants.CACHED_IP_TIMEOUT) {
            testPtpipConnection(cachedIP, 15740)
        } ?: false

        if (success) {
            Log.i(TAG, "✅ 캐시된 IP 연결 성공!")
            PtpipCamera(
                ipAddress = cachedIP,
                port = 15740,
                name = "$cachedName (캐시)",
                isOnline = true
            )
        } else {
            Log.d(TAG, "캐시된 IP 연결 실패")
            null
        }
    }

    /**
     * 캐시에 IP 저장
     */
    private fun saveCachedIP(ipAddress: String, cameraName: String) {
        prefs.edit().apply {
            putString(PREF_LAST_CAMERA_IP, ipAddress)
            putString(PREF_LAST_CAMERA_NAME, cameraName)
            putLong(PREF_LAST_SUCCESS_TIME, System.currentTimeMillis())
            apply()
        }
        Log.d(TAG, "캐시 저장: $ipAddress ($cameraName)")
    }

    /**
     * 여러 mDNS 서비스 타입 동시 검색
     */
    private suspend fun discoverPtpServicesMultiType(): List<NsdServiceInfo> =
        withContext(Dispatchers.IO) {
            val allServices = mutableListOf<NsdServiceInfo>()

            // 모든 서비스 타입을 동시에 검색
            val searchJobs = PtpipConstants.SERVICE_TYPES.map { serviceType ->
                async {
                    try {
                        Log.d(TAG, "mDNS 검색 시작: $serviceType")
                        val services = discoverPtpServices(serviceType, timeoutMs = 5000L)
                        Log.d(TAG, "$serviceType 반환 결과: ${services.size}개")
                        services
                    } catch (e: Exception) {
                        Log.w(TAG, "mDNS 검색 실패: $serviceType - ${e.message}")
                        emptyList<NsdServiceInfo>()
                    }
                }
            }

            // 모든 검색 결과를 기다림
            val results = searchJobs.awaitAll()
            results.forEachIndexed { index, services ->
                Log.d(TAG, "결과[${index}] 추가: ${services.size}개 서비스")
                allServices.addAll(services)
            }

            Log.d(TAG, "모든 mDNS 검색 완료: 총 ${allServices.size}개 서비스 발견")

            // 중복 제거 (같은 IP:Port 조합)
            val uniqueServices = allServices.distinctBy {
                "${it.host?.hostAddress}:${it.port}"
            }

            Log.d(TAG, "중복 제거 후: ${uniqueServices.size}개 서비스")
            uniqueServices
        }

    /**
     * mDNS를 사용하여 특정 PTP 서비스 검색
     */
    private suspend fun discoverPtpServices(
        serviceType: String,
        timeoutMs: Long = 5000L
    ): List<NsdServiceInfo> {
        return withTimeoutOrNull(timeoutMs) {
            discoverPtpServicesInternal(serviceType)
        } ?: emptyList()
    }

    /**
     * mDNS 서비스 검색 내부 구현
     */
    private suspend fun discoverPtpServicesInternal(serviceType: String): List<NsdServiceInfo> {
        return suspendCancellableCoroutine { continuation ->
            val discoveredServices = mutableListOf<NsdServiceInfo>()
            val resolvedServices = mutableSetOf<String>()
            var servicesFound = 0
            var servicesResolved = 0
            var isResumed = false

            // 발견된 서비스가 있으면 바로 반환하는 헬퍼 함수
            fun tryResumeWithServices() {
                synchronized(discoveredServices) {
                    if (!isResumed && continuation.isActive) {
                        // 모든 서비스를 resolve했거나, resolve 성공한 서비스가 있으면 반환
                        if ((servicesResolved >= servicesFound && servicesFound > 0) ||
                            discoveredServices.isNotEmpty()
                        ) {
                            isResumed = true
                            val resultList = discoveredServices.toList()
                            Log.d(
                                TAG,
                                "$serviceType 검색 완료: ${resultList.size}개 발견 (servicesFound=$servicesFound, servicesResolved=$servicesResolved)"
                            )
                            continuation.resume(resultList)
                        }
                    }
                }
            }

            val resolveListener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "서비스 resolve 실패: ${serviceInfo.serviceName}, 에러코드: $errorCode")
                    synchronized(discoveredServices) {
                        servicesResolved++
                        tryResumeWithServices()
                    }
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    Log.d(TAG, "서비스 resolve 성공: ${serviceInfo.serviceName}")
                    Log.d(TAG, "  - Host: ${serviceInfo.host}")
                    Log.d(TAG, "  - Port: ${serviceInfo.port}")

                    synchronized(discoveredServices) {
                        discoveredServices.add(serviceInfo)
                        servicesResolved++
                        tryResumeWithServices()
                    }
                }
            }

            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {
                    Log.d(TAG, "mDNS 검색 시작됨: $regType")
                }

                override fun onServiceFound(service: NsdServiceInfo) {
                    Log.d(TAG, "서비스 발견: ${service.serviceName}")
                    synchronized(discoveredServices) {
                        val serviceKey = "${service.serviceName}:${service.serviceType}"
                        if (!resolvedServices.contains(serviceKey)) {
                            resolvedServices.add(serviceKey)
                            servicesFound++
                            try {
                                nsdManager.resolveService(service, resolveListener)
                            } catch (e: Exception) {
                                Log.w(TAG, "서비스 resolve 요청 실패: ${e.message}")
                                servicesResolved++
                                tryResumeWithServices()
                            }
                        }
                    }
                }

                override fun onServiceLost(service: NsdServiceInfo) {
                    Log.d(TAG, "서비스 손실: ${service.serviceName}")
                }

                override fun onDiscoveryStopped(serviceType: String) {
                    Log.d(TAG, "mDNS 검색 중지됨: $serviceType")
                    // 검색 중지 시 발견된 서비스가 있으면 반환
                    synchronized(discoveredServices) {
                        if (!isResumed && continuation.isActive) {
                            isResumed = true
                            Log.d(TAG, "$serviceType 검색 중지: ${discoveredServices.size}개 발견")
                            continuation.resume(discoveredServices.toList())
                        }
                    }
                }

                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.e(TAG, "mDNS 검색 시작 실패: $serviceType, 에러코드: $errorCode")
                    synchronized(discoveredServices) {
                        if (!isResumed && continuation.isActive) {
                            isResumed = true
                            continuation.resume(emptyList())
                        }
                    }
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.e(TAG, "mDNS 검색 중지 실패: $serviceType, 에러코드: $errorCode")
                }
            }

            // 검색 시작
            try {
                nsdManager.discoverServices(
                    serviceType,
                    NsdManager.PROTOCOL_DNS_SD,
                    listener
                )
                discoveryListener = listener
            } catch (e: Exception) {
                Log.e(TAG, "mDNS 검색 시작 중 오류", e)
                synchronized(discoveredServices) {
                    if (!isResumed && continuation.isActive) {
                        isResumed = true
                        continuation.resume(emptyList())
                    }
                }
                return@suspendCancellableCoroutine
            }

            // 취소 시 정리 작업 (타임아웃 포함)
            continuation.invokeOnCancellation {
                try {
                    nsdManager.stopServiceDiscovery(listener)
                } catch (e: Exception) {
                    Log.w(TAG, "mDNS 검색 정리 중 오류: ${e.message}")
                }
            }
        }
    }

    /**
     * 서비스 이름에서 카메라 이름 추출
     */
    private fun extractCameraName(serviceName: String, ipAddress: String): String {
        return when {
            serviceName.contains("Z_8", ignoreCase = true) -> {
                val match = Regex("Z_8[_\\s]*([0-9]+)").find(serviceName)
                val serialNumber = match?.groupValues?.get(1) ?: ""
                "Nikon Z8${if (serialNumber.isNotEmpty()) " ($serialNumber)" else ""}"
            }

            serviceName.contains("NIKON", ignoreCase = true) -> {
                val modelMatch = Regex("NIKON[_\\s]*([A-Z]+[_\\s]*\\d+)", RegexOption.IGNORE_CASE)
                    .find(serviceName)?.groupValues?.get(1)?.replace("_", " ")
                "Nikon ${modelMatch ?: "Camera"}"
            }

            serviceName.contains("CANON", ignoreCase = true) -> {
                val modelMatch = Regex("CANON[_\\s]*([A-Z]+[_\\s]*\\d+)", RegexOption.IGNORE_CASE)
                    .find(serviceName)?.groupValues?.get(1)?.replace("_", " ")
                "Canon ${modelMatch ?: "Camera"}"
            }

            serviceName.contains("SONY", ignoreCase = true) -> "Sony Camera"
            serviceName.contains("FUJI", ignoreCase = true) -> "Fujifilm Camera"
            serviceName.contains("PANASONIC", ignoreCase = true) -> "Panasonic Camera"
            serviceName.contains("OLYMPUS", ignoreCase = true) -> "Olympus Camera"
            serviceName.contains("LEICA", ignoreCase = true) -> "Leica Camera"
            else -> "PTPIP Camera ($ipAddress)"
        }
    }

    /**
     * PTP-IP 연결 테스트
     */
    private suspend fun testPtpipConnection(ipAddress: String, port: Int): Boolean {
        return try {
            Log.d(TAG, "PTP-IP 연결 테스트: $ipAddress:$port")

            val socket = java.net.Socket()
            socket.soTimeout = 3000 // 3초 타임아웃
            socket.connect(java.net.InetSocketAddress(ipAddress, port), 3000)

            // PTP-IP Init Command Request 생성 및 전송
            val initPacket = createPtpipInitRequest()
            socket.getOutputStream().write(initPacket)
            socket.getOutputStream().flush()

            // 응답 대기
            val response = ByteArray(1024)
            val bytesRead = socket.getInputStream().read(response)

            // PTP-IP ACK 응답 확인
            if (bytesRead >= 8) {
                val buffer =
                    java.nio.ByteBuffer.wrap(response).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                buffer.position(4) // length 필드 건너뛰기
                val responseType = buffer.int

                if (responseType == PtpipConstants.PTPIP_INIT_COMMAND_ACK) {
                    Log.d(TAG, "✅ PTP-IP 연결 성공: $ipAddress")
                    socket.close() // 테스트 후 소켓 닫기
                    return true
                } else {
                    Log.d(TAG, "❌ PTP-IP 응답 타입 불일치: $responseType")
                }
            } else {
                Log.d(TAG, "❌ PTP-IP 응답 길이 부족: $bytesRead bytes")
            }

            socket.close()
            false
        } catch (e: Exception) {
            Log.d(TAG, "❌ PTP-IP 연결 실패: $ipAddress - ${e.message}")
            false
        }
    }

    /**
     * PTP-IP Init Command Request 패킷 생성
     */
    private fun createPtpipInitRequest(): ByteArray {
        // PTP-IP 표준 GUID
        val commandGuid = byteArrayOf(
            0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
            0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10
        )

        // 호스트 이름 (UTF-16LE)
        val hostName = "Android Camera Controller"
        val hostNameBytes = hostName.toByteArray(Charsets.UTF_16LE)
        val nullTerminator = byteArrayOf(0x00, 0x00)

        // 패킷 크기 계산
        val totalLength = 4 + 4 + 16 + hostNameBytes.size + nullTerminator.size + 4
        val buffer =
            java.nio.ByteBuffer.allocate(totalLength).order(java.nio.ByteOrder.LITTLE_ENDIAN)

        // 패킷 구성
        buffer.putInt(totalLength) // Length
        buffer.putInt(PtpipConstants.PTPIP_INIT_COMMAND_REQUEST) // Type
        buffer.put(commandGuid) // Connection Number GUID
        buffer.put(hostNameBytes) // Friendly Name
        buffer.put(nullTerminator) // Null terminator
        buffer.putInt(0x00010001) // Protocol Version

        return buffer.array()
    }

    /**
     * 발견 중지
     */
    fun stopDiscovery() {
        try {
            discoveryListener?.let { nsdManager.stopServiceDiscovery(it) }
            discoveryListener = null
        } catch (e: Exception) {
            Log.w(TAG, "mDNS 검색 중지 중 오류: ${e.message}")
        }
    }

    /**
     * 캐시 초기화 (디버그용)
     */
    fun clearCache() {
        prefs.edit().clear().apply()
        Log.d(TAG, "캐시 초기화 완료")
    }
}
