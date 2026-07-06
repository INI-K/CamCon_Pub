package com.inik.camcon.data.network.ptpip.discovery

import android.content.Context
import android.content.SharedPreferences
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.inik.camcon.data.constants.PtpipConstants
import com.inik.camcon.data.network.ptpip.wifi.WifiNetworkHelper
import com.inik.camcon.di.IoDispatcher
import com.inik.camcon.domain.model.CameraVendor
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.utils.LogMask
import kotlinx.coroutines.CoroutineDispatcher
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
    private val wifiHelper: WifiNetworkHelper,
    private val ssdpDiscoveryService: SsdpDiscoveryService,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    // 동시 mDNS 검색(서비스 타입별 async)이 서로의 listener를 덮어쓰지 않도록
    // 단일 필드 대신 스레드세이프 Set으로 활성 listener를 관리한다.
    private val activeDiscoveryListeners =
        java.util.Collections.synchronizedSet(mutableSetOf<NsdManager.DiscoveryListener>())

    // 마지막으로 알려진 카메라 IP 캐싱용 SharedPreferences
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "camera_discovery_cache",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val TAG = "PtpipDiscoveryService"
        private const val PREF_LAST_CAMERA_IP = "last_camera_ip"
        private const val PREF_LAST_CAMERA_NAME = "last_camera_name"
        private const val PREF_LAST_CAMERA_SERVICE_TYPE = "last_camera_service_type"
        private const val PREF_LAST_SUCCESS_TIME = "last_success_time"
        private const val CACHE_VALID_DURATION_MS = 24 * 60 * 60 * 1000L // 24시간

        // 동시 mDNS 검색(서비스 타입별 async) 시 NsdManager는 한 번에 하나의
        // resolveService만 허용하며, 충돌 시 FAILURE_ALREADY_ACTIVE(3)를 반환한다.
        // 이 경우 카운트만 올리고 누락시키지 않도록 백오프 후 재시도한다.
        private const val NSD_FAILURE_ALREADY_ACTIVE = 3
        private const val RESOLVE_RETRY_MAX = 5
        private const val RESOLVE_RETRY_DELAY_MS = 150L
    }

    // resolveService 재시도 예약용 핸들러 (서비스 타입별 검색 코루틴이 공유)
    private val resolveRetryHandler =
        android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * PTPIP 지원 카메라 검색 (최적화된 빠른 검색)
     */
    suspend fun discoverCameras(forceApMode: Boolean = false): List<PtpipCamera> =
        withContext(ioDispatcher) {
            val cameras = mutableListOf<PtpipCamera>()

            Log.i(TAG, "카메라 검색 시작 (최적화 모드)")

            try {
                // 1단계: 캐시된 IP로 빠른 시도 (STA 모드에서만, 0.5-1초)
                // 성공 시 5초+ 걸리는 mDNS 검색을 생략해 재연결을 크게 단축한다.
                // (한동안 디버깅용으로 비활성화되어 매 재연결마다 mDNS 풀스캔이 강제됐었음)
                if (!forceApMode && !wifiHelper.isConnectedToCameraAP()) {
                    Log.d(TAG, "1단계: 캐시된 IP 확인 시도...")
                    val cachedCamera = tryCachedIP()
                    if (cachedCamera != null) {
                        Log.i(TAG, "캐시된 IP 연결 성공 - mDNS 검색 생략")
                        cameras.add(cachedCamera)
                        return@withContext cameras
                    }
                }

                // 2단계: AP 모드 처리
                if (forceApMode || wifiHelper.isConnectedToCameraAP()) {
                    Log.d(TAG, "2단계: AP 모드 처리 - 게이트웨이 IP 직접 연결 시도")

                    val gatewayIP = wifiHelper.detectCameraIPInAPMode()
                    if (gatewayIP != null) {
                        Log.i(TAG, "게이트웨이 IP 발견: ${LogMask.id(gatewayIP)}")

                        if (testPtpipConnection(gatewayIP, 15740)) {
                            Log.i(TAG, "AP 모드: 게이트웨이 PTP-IP 연결 성공")
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
                            Log.i(TAG, "기본 IP PTP-IP 연결 성공: ${LogMask.id(ip)}")
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
                        Log.w(TAG, "AP 모드에서 PTP-IP 연결 가능한 카메라를 찾을 수 없음")
                    }

                    return@withContext cameras
                }

                // 3단계: STA 모드에서 mDNS + SSDP 병행 검색
                Log.d(TAG, "3단계: STA 모드 - mDNS/SSDP 병행 카메라 자동 검색")

                // SSDP는 mDNS와 병행 실행 — mDNS에 광고하지 않는 제조사
                // (Canon SSDP/UPnP, Sony 구형, Panasonic)를 연결 전에 판별한다.
                val ssdpDeferred = async {
                    try {
                        ssdpDiscoveryService.discover()
                    } catch (ce: kotlinx.coroutines.CancellationException) {
                        // 협력 취소는 반드시 전파 — 삼키면 취소가 빈 결과로 위장된다.
                        throw ce
                    } catch (e: Exception) {
                        Log.w(TAG, "SSDP 검색 실패: ${e.message}")
                        emptyList()
                    }
                }

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
                            val cameraName = extractCameraName(serviceName, hostAddress)
                            val verdict = CameraVendorClassifier.classifyMdns(
                                serviceName, serviceInfo.serviceType
                            )

                            // 실측 확보용 덤프: 실기별 mDNS 광고 실태(타입·TXT)를 남긴다.
                            // 니콘이 실제로 어떤 서비스 타입/TXT를 광고하는지 저장소에 실측이
                            // 없어(2026-07-06 조사) 이 로그로 갭을 메꾼다. 이름/IP는 마스킹.
                            val txt = serviceInfo.attributes.entries.joinToString {
                                "${it.key}=${it.value?.toString(Charsets.UTF_8).orEmpty()}"
                            }
                            Log.i(
                                TAG,
                                "VENDOR_MDNS_DUMP name=${LogMask.id(serviceName)} " +
                                    "type=${serviceInfo.serviceType} verdict=$verdict txt={$txt}"
                            )

                            val camera = PtpipCamera(
                                ipAddress = hostAddress,
                                port = port,
                                name = cameraName,
                                isOnline = true,
                                discoveredServiceType = serviceInfo.serviceType,  // mDNS 서비스 타입 저장
                                vendorVerdict = verdict
                            )
                            cameras.add(camera)
                            Log.i(TAG, "카메라 발견: ${LogMask.id(cameraName)} (${LogMask.id(hostAddress)}:$port)")

                            // 첫 번째 발견된 카메라를 캐시에 저장
                            if (cameras.size == 1) {
                                saveCachedIP(hostAddress, cameraName, serviceInfo.serviceType)
                            }
                        } else {
                            Log.w(TAG, "유효하지 않은 서비스 정보: ${LogMask.id(serviceName)}")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "서비스 정보 처리 중 오류: ${e.message}")
                    }
                }

                // SSDP 결과 병합 — 같은 IP는 mDNS 항목을 유지하되 verdict만 신뢰도 높은 쪽으로 승격
                val ssdpCameras = ssdpDeferred.await()
                for (ssdpCamera in ssdpCameras) {
                    val existingIdx = cameras.indexOfFirst { it.ipAddress == ssdpCamera.ipAddress }
                    if (existingIdx < 0) {
                        // TV/가전 잡음 차단: Sony Bravia가 ScalarWebAPI URN을, Panasonic
                        // 가전이 rootdevice를 광고하므로 PTP/IP 포트(15740)가 실제로 열린
                        // 기기만 목록에 올린다. testPtpipConnection은 순수 TCP connect라
                        // InitCommandRequest를 보내지 않아 니콘 세션락에도 안전하다.
                        if (testPtpipConnection(ssdpCamera.ipAddress, ssdpCamera.port)) {
                            cameras.add(ssdpCamera)
                            Log.i(
                                TAG,
                                "카메라 발견(SSDP): ${ssdpCamera.vendorVerdict.vendor} " +
                                    "(${LogMask.id(ssdpCamera.ipAddress)}:${ssdpCamera.port})"
                            )
                        } else {
                            Log.d(
                                TAG,
                                "SSDP 발견 기기 PTP/IP 포트 미개방 — 목록 제외: " +
                                    "${ssdpCamera.vendorVerdict.vendor} (${LogMask.id(ssdpCamera.ipAddress)})"
                            )
                        }
                    } else if (
                        cameras[existingIdx].vendorVerdict.vendor != CameraVendor.NIKON &&
                        CameraVendorClassifier.confidenceRank(ssdpCamera.vendorVerdict) >
                        CameraVendorClassifier.confidenceRank(cameras[existingIdx].vendorVerdict)
                    ) {
                        // NIKON verdict는 보존 — 타 벤더 SSDP 응답이 덮으면 STA 인증이
                        // 생략되어 첫 페어링이 InitFail 0x1로 파손된다.
                        cameras[existingIdx] =
                            cameras[existingIdx].copy(vendorVerdict = ssdpCamera.vendorVerdict)
                    }
                }

                // 4단계: mDNS 실패 시 기본 IP들 시도 - 비활성화 (시간 소모 방지)
                if (cameras.isEmpty()) {
                    Log.d(TAG, "4단계: mDNS 실패 - 기본 IP 시도 건너뜀 (시간 절약)")
                    // 기본 IP 시도 비활성화 - 불필요한 타임아웃 방지
                }

                Log.i(TAG, "카메라 검색 완료: ${cameras.size}개 발견")

                if (cameras.isEmpty()) {
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
    private suspend fun tryCachedIP(): PtpipCamera? = withContext(ioDispatcher) {
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

        Log.d(TAG, "캐시된 IP 시도: ${LogMask.id(cachedIP)} (${LogMask.id(cachedName)})")

        // 짧은 타임아웃으로 빠르게 시도
        val success = withTimeoutOrNull(PtpipConstants.CACHED_IP_TIMEOUT) {
            testPtpipConnection(cachedIP, 15740)
        } ?: false

        if (success) {
            Log.i(TAG, "캐시된 IP 연결 성공")
            // 캐시된 서비스 타입으로 재판별 — 개명 니콘(_nikon._tcp로만 CONFIRMED)이
            // 캐시 경로에서 verdict를 잃고 STA 인증을 건너뛰는 비일관을 막는다.
            val cachedType = prefs.getString(PREF_LAST_CAMERA_SERVICE_TYPE, null)
            PtpipCamera(
                ipAddress = cachedIP,
                port = 15740,
                name = "$cachedName (캐시)",
                isOnline = true,
                discoveredServiceType = cachedType,
                vendorVerdict = CameraVendorClassifier.classifyMdns(cachedName, cachedType)
            )
        } else {
            Log.d(TAG, "캐시된 IP 연결 실패")
            null
        }
    }

    /**
     * 캐시에 IP 저장
     */
    private fun saveCachedIP(ipAddress: String, cameraName: String, serviceType: String? = null) {
        prefs.edit().apply {
            putString(PREF_LAST_CAMERA_IP, ipAddress)
            putString(PREF_LAST_CAMERA_NAME, cameraName)
            putString(PREF_LAST_CAMERA_SERVICE_TYPE, serviceType)
            putLong(PREF_LAST_SUCCESS_TIME, System.currentTimeMillis())
            apply()
        }
        Log.d(TAG, "캐시 저장: ${LogMask.id(ipAddress)} (${LogMask.id(cameraName)})")
    }

    /**
     * 여러 mDNS 서비스 타입 동시 검색
     */
    private suspend fun discoverPtpServicesMultiType(): List<NsdServiceInfo> =
        withContext(ioDispatcher) {
            val allServices = mutableListOf<NsdServiceInfo>()

            // 모든 서비스 타입을 동시에 검색
            val searchJobs = PtpipConstants.SERVICE_TYPES.map { serviceType ->
                async {
                    try {
                        discoverPtpServices(serviceType, timeoutMs = 5000L)
                    } catch (e: Exception) {
                        Log.w(TAG, "mDNS 검색 실패: $serviceType - ${e.message}")
                        emptyList<NsdServiceInfo>()
                    }
                }
            }

            // 모든 검색 결과를 기다림
            val results = searchJobs.awaitAll()
            results.forEach { services ->
                allServices.addAll(services)
            }

            Log.d(TAG, "모든 mDNS 검색 완료: 총 ${allServices.size}개 서비스 발견")

            // 중복 제거 (같은 IP:Port 조합) — 제조사 특정 타입(_nikon._tcp 등)이
            // 표준 _ptp._tcp에 도착 순서로 밀려 유실되지 않도록 신뢰도 높은 쪽을 남긴다.
            val uniqueServices = allServices
                .filter { it.host?.hostAddress != null }
                .groupBy { "${it.host!!.hostAddress}:${it.port}" }
                .map { (_, group) ->
                    group.maxBy { svc ->
                        CameraVendorClassifier.confidenceRank(
                            CameraVendorClassifier.classifyMdns(
                                svc.serviceName.orEmpty(), svc.serviceType
                            )
                        )
                    }
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
            // 서비스별 resolve 재시도 횟수 (serviceName:serviceType 기준)
            val resolveRetryCounts = mutableMapOf<String, Int>()
            var servicesFound = 0
            var servicesResolved = 0
            var isResumed = false

            // 이 코루틴 전용 listener (공유 필드 대신 로컬 보관 → 동시 검색 간 간섭 제거)
            var localListener: NsdManager.DiscoveryListener? = null

            // 발견된 서비스가 있으면 바로 반환하는 헬퍼 함수
            fun tryResumeWithServices() {
                synchronized(discoveredServices) {
                    if (!isResumed && continuation.isActive) {
                        // 모든 서비스를 resolve했거나, resolve 성공한 서비스가 있으면 반환
                        if ((servicesResolved >= servicesFound && servicesFound > 0) ||
                            discoveredServices.isNotEmpty()
                        ) {
                            isResumed = true
                            // 검색 중지 (리소스 정리) — 자기 자신의 listener만 stop
                            try {
                                localListener?.let {
                                    nsdManager.stopServiceDiscovery(it)
                                    activeDiscoveryListeners.remove(it)
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "검색 완료 후 정리 중 오류: ${e.message}")
                            }
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

            // resolveService 재시도 시 동일 listener를 명시적으로 재참조하기 위해 보관
            var localResolveListener: NsdManager.ResolveListener? = null

            val resolveListener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "서비스 resolve 실패: ${LogMask.id(serviceInfo.serviceName)}, 에러코드: $errorCode")
                    // 동시 resolve 충돌(FAILURE_ALREADY_ACTIVE)이면 카운트만 올려
                    // 누락시키지 말고 백오프 후 재시도한다.
                    if (errorCode == NSD_FAILURE_ALREADY_ACTIVE) {
                        val serviceKey = "${serviceInfo.serviceName}:${serviceInfo.serviceType}"
                        val retried = synchronized(discoveredServices) {
                            if (isResumed || !continuation.isActive) {
                                false
                            } else {
                                val attempts = (resolveRetryCounts[serviceKey] ?: 0) + 1
                                if (attempts > RESOLVE_RETRY_MAX) {
                                    false
                                } else {
                                    resolveRetryCounts[serviceKey] = attempts
                                    true
                                }
                            }
                        }
                        if (retried) {
                            Log.d(TAG, "resolve 충돌, 재시도 예약: ${LogMask.id(serviceInfo.serviceName)}")
                            resolveRetryHandler.postDelayed({
                                val stillActive = synchronized(discoveredServices) {
                                    !isResumed && continuation.isActive
                                }
                                if (!stillActive) return@postDelayed
                                try {
                                    nsdManager.resolveService(serviceInfo, localResolveListener!!)
                                } catch (e: Exception) {
                                    Log.w(TAG, "resolve 재시도 요청 실패: ${e.message}")
                                    synchronized(discoveredServices) {
                                        servicesResolved++
                                        tryResumeWithServices()
                                    }
                                }
                            }, RESOLVE_RETRY_DELAY_MS)
                            return
                        }
                    }
                    synchronized(discoveredServices) {
                        servicesResolved++
                        tryResumeWithServices()
                    }
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    Log.d(
                        TAG,
                        "서비스 resolve 성공: ${LogMask.id(serviceInfo.serviceName)} (${LogMask.id(serviceInfo.host?.hostAddress)}:${serviceInfo.port})"
                    )

                    synchronized(discoveredServices) {
                        discoveredServices.add(serviceInfo)
                        servicesResolved++
                        tryResumeWithServices()
                    }
                }
            }
            localResolveListener = resolveListener

            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {
                    Log.d(TAG, "mDNS 검색 시작됨: $regType")
                }

                override fun onServiceFound(service: NsdServiceInfo) {
                    Log.d(TAG, "서비스 발견: ${LogMask.id(service.serviceName)}")
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
                    Log.d(TAG, "서비스 손실: ${LogMask.id(service.serviceName)}")
                    synchronized(discoveredServices) {
                        val serviceKey = "${service.serviceName}:${service.serviceType}"
                        resolvedServices.remove(serviceKey)
                    }
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
                    Log.w(TAG, "mDNS 검색 중지 실패: $serviceType, 에러코드: $errorCode")
                }
            }

            // 검색 시작
            try {
                localListener = listener
                activeDiscoveryListeners.add(listener)
                nsdManager.discoverServices(
                    serviceType,
                    NsdManager.PROTOCOL_DNS_SD,
                    listener
                )
            } catch (e: Exception) {
                Log.e(TAG, "mDNS 검색 시작 중 오류", e)
                activeDiscoveryListeners.remove(listener)
                synchronized(discoveredServices) {
                    if (!isResumed && continuation.isActive) {
                        isResumed = true
                        continuation.resume(emptyList())
                    }
                }
                return@suspendCancellableCoroutine
            }

            // 취소 시 정리 작업 (타임아웃 포함) — 자기 listener만 stop
            continuation.invokeOnCancellation {
                try {
                    nsdManager.stopServiceDiscovery(listener)
                } catch (e: Exception) {
                    Log.w(TAG, "mDNS 검색 정리 중 오류: ${e.message}")
                } finally {
                    activeDiscoveryListeners.remove(listener)
                }
            }
        }
    }

    /**
     * 서비스 이름에서 카메라 이름 추출
     * 원본 mDNS 이름을 그대로 유지 (Nikon STA 인증에 필요)
     */
    private fun extractCameraName(serviceName: String, ipAddress: String): String {
        // mDNS 서비스 이름을 그대로 반환 (예: Z_6_5000784)
        // Nikon 카메라 감지를 위해 원본 이름 유지
        return serviceName
    }

    /**
     * PTP-IP 연결 테스트
     */
    private suspend fun testPtpipConnection(ipAddress: String, port: Int): Boolean {
        return try {
            Log.d(TAG, "PTP-IP 연결 테스트: ${LogMask.id(ipAddress)}:$port")

            // ⚠️ InitCommandRequest를 보내지 않는다. Nikon Z8은 abrupt close(CloseSession 없이) 시
            // PTP/IP 세션을 놓아주지 않고 잠근다(단일 세션만 허용). 프로브가 InitCommandRequest→Ack로
            // 세션을 세운 뒤 소켓만 닫으면, 뒤따르는 실제 연결(Phase1/libgphoto2)이 전부 InitFail 0x1로
            // 거부된다 — 패킷 GUID/이름과 무관(랜덤 GUID 프로브도 수락됨). 그래서 포트 개방(TCP connect)
            // 확인만으로 PTP/IP 카메라 도달성을 판정한다(15740은 PTP/IP 전용 포트).
            java.net.Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress(ipAddress, port), 3000)
                Log.d(TAG, "PTP-IP 포트 개방 확인: ${LogMask.id(ipAddress)}")
                socket.isConnected
            }
        } catch (e: Exception) {
            Log.d(TAG, "PTP-IP 연결 실패: ${LogMask.id(ipAddress)} - ${e.message}")
            false
        }
    }

    /**
     * 발견 중지
     */
    fun stopDiscovery() {
        // 현재 활성화된 모든 listener를 정리 (동시 검색 전부 중지)
        val listeners = synchronized(activeDiscoveryListeners) {
            activeDiscoveryListeners.toList()
        }
        listeners.forEach { listener ->
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (e: Exception) {
                Log.w(TAG, "mDNS 검색 중지 중 오류: ${e.message}")
            } finally {
                activeDiscoveryListeners.remove(listener)
            }
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
