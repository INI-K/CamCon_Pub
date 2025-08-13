package com.inik.camcon.data.network.ptpip.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.inik.camcon.data.constants.PtpipConstants
import com.inik.camcon.data.network.ptpip.wifi.WifiNetworkHelper
import com.inik.camcon.domain.model.PtpipCamera
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * PTPIP 카메라 발견 서비스
 * AP 모드에서는 WifiNetworkHelper를 사용하고, 일반 모드에서는 mDNS를 사용
 */
@Singleton
class PtpipDiscoveryService @Inject constructor(
    private val context: Context,
    private val wifiHelper: WifiNetworkHelper
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    companion object {
        private const val TAG = "PtpipDiscoveryService"
    }

    /**
     * PTPIP 지원 카메라 검색 (사용자 선택 모드에 따라 처리)
     */
    suspend fun discoverCameras(forceApMode: Boolean = false): List<PtpipCamera> =
        withContext(Dispatchers.IO) {
        val cameras = mutableListOf<PtpipCamera>()

        try {
            // 사용자가 AP 모드를 선택했거나 자동 감지로 AP 모드인 경우
            if (forceApMode || wifiHelper.isConnectedToCameraAP()) {
                Log.d(TAG, "AP 모드 처리: 게이트웨이 IP에 직접 PTP-IP 연결 시도")

                // 게이트웨이 IP 찾기
                val gatewayIP = wifiHelper.detectCameraIPInAPMode()
                if (gatewayIP != null) {
                    Log.i(TAG, "게이트웨이 IP 발견: $gatewayIP")

                    // 게이트웨이에 PTP-IP 연결 시도
                    if (testPtpipConnection(gatewayIP, 15740)) {
                        Log.i(TAG, "✅ AP 모드: 게이트웨이 PTP-IP 연결 성공!")
                        val networkName = wifiHelper.getCurrentSSID() ?: "카메라 AP"

                        val apCamera = PtpipCamera(
                            ipAddress = gatewayIP,
                            port = 15740,
                            name = "$networkName (AP모드 - PTP-IP 확인됨)",
                            isOnline = true
                        )
                        cameras.add(apCamera)

                        Log.i(TAG, "✅ AP 모드 카메라 검색 완료: ${cameras.size}개 발견")
                        return@withContext cameras
                    } else {
                        Log.w(TAG, "❌ 게이트웨이 PTP-IP 연결 실패: $gatewayIP")

                        // 게이트웨이 연결 실패 시 기본 IP들 시도
                        val defaultIPs = listOf(
                            "192.168.1.1",
                            "192.168.0.1",
                            "192.168.10.1",
                            "192.168.100.1"
                        )

                        for (ip in defaultIPs) {
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
                                break
                            }
                        }

                        if (cameras.isEmpty()) {
                            Log.w(TAG, "❌ AP 모드에서 PTP-IP 연결 가능한 카메라를 찾을 수 없음")
                        }

                        return@withContext cameras
                    }
                } else {

                    // 게이트웨이 IP를 찾을 수 없어도 기본 IP들로 시도
                    val defaultIPs = listOf(
                        "192.168.1.1",
                        "192.168.0.1",
                        "192.168.10.1",
                        "192.168.100.1"
                    )

                    for (ip in defaultIPs) {
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
                            break
                        }
                    }

                    return@withContext cameras
                }
            }

            // STA 모드에서 mDNS 검색
            Log.d(TAG, "STA 모드: mDNS를 사용한 카메라 검색")
            Log.d(TAG, "mDNS 서비스 검색 시작: ${PtpipConstants.SERVICE_TYPE}")

            // mDNS로 PTP 서비스 검색
            val discoveredServices = withTimeoutOrNull(PtpipConstants.DISCOVERY_TIMEOUT) {
                discoverPtpServices()
            } ?: emptyList()

            Log.d(TAG, "mDNS 검색 완료: ${discoveredServices.size}개 서비스 발견")

            // 발견된 서비스들을 PtpipCamera 객체로 변환
            for (serviceInfo in discoveredServices) {
                try {
                    val hostAddress = serviceInfo.host?.hostAddress
                    val port = serviceInfo.port
                    val serviceName = serviceInfo.serviceName

                    if (hostAddress != null && port > 0) {
                        Log.d(TAG, "서비스 정보: $serviceName ($hostAddress:$port)")

                        // 카메라 이름 추출 및 정리
                        val cameraName = extractCameraName(serviceName, hostAddress)

                        val camera = PtpipCamera(
                            ipAddress = hostAddress,
                            port = port,
                            name = cameraName,
                            isOnline = true
                        )
                        cameras.add(camera)

                        Log.i(TAG, "카메라 발견: $cameraName ($hostAddress:$port)")
                    } else {
                        Log.w(TAG, "유효하지 않은 서비스 정보: $serviceName")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "서비스 정보 처리 중 오류: ${e.message}")
                }
            }

            Log.i(TAG, "카메라 검색 완료")
            Log.i(TAG, "- 발견된 카메라: ${cameras.size}개")

            if (cameras.isNotEmpty()) {
                Log.i(TAG, "발견된 카메라 목록:")
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
     * mDNS를 사용하여 PTP 서비스 검색
     */
    private suspend fun discoverPtpServices(): List<NsdServiceInfo> {
        return suspendCancellableCoroutine { continuation ->
            val discoveredServices = mutableListOf<NsdServiceInfo>()
            val resolvedServices = mutableSetOf<String>()
            var servicesFound = 0
            var servicesResolved = 0

            val resolveListener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "서비스 resolve 실패: ${serviceInfo.serviceName}, 에러코드: $errorCode")
                    synchronized(discoveredServices) {
                        servicesResolved++
                        if (servicesResolved >= servicesFound) {
                            continuation.resume(discoveredServices.toList())
                        }
                    }
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    Log.d(TAG, "서비스 resolve 성공: ${serviceInfo.serviceName}")
                    Log.d(TAG, "  - Host: ${serviceInfo.host}")
                    Log.d(TAG, "  - Port: ${serviceInfo.port}")

                    synchronized(discoveredServices) {
                        discoveredServices.add(serviceInfo)
                        servicesResolved++
                        if (servicesResolved >= servicesFound) {
                            continuation.resume(discoveredServices.toList())
                        }
                    }
                }
            }

            discoveryListener = object : NsdManager.DiscoveryListener {
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
                                if (servicesResolved >= servicesFound) {
                                    continuation.resume(discoveredServices.toList())
                                }
                            }
                        }
                    }
                }

                override fun onServiceLost(service: NsdServiceInfo) {
                    Log.d(TAG, "서비스 손실: ${service.serviceName}")
                }

                override fun onDiscoveryStopped(serviceType: String) {
                    Log.d(TAG, "mDNS 검색 중지됨: $serviceType")
                }

                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.e(TAG, "mDNS 검색 시작 실패: $serviceType, 에러코드: $errorCode")
                    continuation.resume(emptyList())
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.e(TAG, "mDNS 검색 중지 실패: $serviceType, 에러코드: $errorCode")
                }
            }

            // 검색 시작
            try {
                nsdManager.discoverServices(
                    PtpipConstants.SERVICE_TYPE,
                    NsdManager.PROTOCOL_DNS_SD,
                    discoveryListener
                )
            } catch (e: Exception) {
                Log.e(TAG, "mDNS 검색 시작 중 오류", e)
                continuation.resume(emptyList())
                return@suspendCancellableCoroutine
            }

            // 취소 시 정리 작업
            continuation.invokeOnCancellation {
                try {
                    discoveryListener?.let { nsdManager.stopServiceDiscovery(it) }
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
                // Nikon Z8 시리즈
                val match = Regex("Z_8[_\\s]*([0-9]+)").find(serviceName)
                val serialNumber = match?.groupValues?.get(1) ?: ""
                "Nikon Z8${if (serialNumber.isNotEmpty()) " ($serialNumber)" else ""}"
            }

            serviceName.contains("NIKON", ignoreCase = true) -> {
                // 기타 Nikon 카메라
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
            socket.soTimeout = 5000 // 5초 타임아웃
            socket.connect(java.net.InetSocketAddress(ipAddress, port), 5000)

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
                    // 성공 시에는 소켓을 열어둠 (실제 통신에서 사용하기 위해)
                    return true
                } else {
                    Log.d(TAG, "❌ PTP-IP 응답 타입 불일치: $responseType")
                }
            } else {
                Log.d(TAG, "❌ PTP-IP 응답 길이 부족: $bytesRead bytes")
            }

            // 실패 시에만 소켓 닫기
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
}