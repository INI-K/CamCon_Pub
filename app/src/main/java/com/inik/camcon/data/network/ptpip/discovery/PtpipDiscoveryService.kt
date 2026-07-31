package com.inik.camcon.data.network.ptpip.discovery

import android.content.Context
import android.content.SharedPreferences
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.SystemClock
import android.util.Log
import com.inik.camcon.data.constants.PtpipConstants
import com.inik.camcon.data.datasource.local.PtpipPreferencesDataSource
import com.inik.camcon.data.network.ptpip.wifi.WifiNetworkHelper
import com.inik.camcon.di.IoDispatcher
import com.inik.camcon.BuildConfig
import com.inik.camcon.domain.model.CameraDiscoverySource
import com.inik.camcon.domain.model.CameraVendor
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.utils.LogMask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * PTPIP 카메라 발견 서비스 (다중 후보 누적 mDNS/SSDP 검색)
 *
 * 검색 전략:
 * 1. 캐시된 IP 확인 — 후보로 **편입**만 한다(과거처럼 조기 return으로 검색을 종료하지 않는다).
 *    단 배경 예산([DiscoveryBudget.allowEarlyConfirmOnKnownIp])에서 캐시 IP가 DataStore 기지 IP와
 *    같으면 즉시 반환해 4초 폴링의 응답성을 보존한다.
 * 2. AP 모드 감지 및 처리(게이트웨이 → 기본 IP)
 * 3. STA 모드: mDNS 멀티캐스트 + SSDP 병행. 예산([DiscoveryBudget.totalMs])을 소진할 때까지
 *    후보를 **누적**하고, 매 후보마다 `onPartialResult`로 스냅샷 전체를 방출한다.
 *
 * ⚠️ resolve 직렬화: API 29~32 `NsdManager`는 앱당 동시 resolve 1건만 허용하며
 * 충돌 시 `FAILURE_ALREADY_ACTIVE(3)`를 반환한다. 조기 resume을 제거하면 resolve 체류가 늘어
 * 충돌이 커지므로 [resolveGate]로 앱 전체 동시 resolve를 1건으로 묶고, 카메라 서비스 타입
 * (`_ptp`/`_ptpip`/`_nikon`)을 우선 드레인한다.
 */
@Singleton
class PtpipDiscoveryService @Inject constructor(
    private val context: Context,
    private val wifiHelper: WifiNetworkHelper,
    private val ssdpDiscoveryService: SsdpDiscoveryService,
    private val ptpipPreferencesDataSource: PtpipPreferencesDataSource,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    // 동시 mDNS 검색(서비스 타입별 listener)이 서로를 덮어쓰지 않도록
    // 단일 필드 대신 스레드세이프 Set으로 활성 listener를 관리한다.
    private val activeDiscoveryListeners =
        java.util.Collections.synchronizedSet(mutableSetOf<NsdManager.DiscoveryListener>())

    // 앱 전체 동시 resolve를 1건으로 제한(FAILURE_ALREADY_ACTIVE 회피). @Singleton이라 프로세스 전역.
    private val resolveGate = Mutex()

    // IP별 마지막 TCP 프로브 시각/결과 — 같은 IP 재-TCP ≥1s 규약(airnef 문서화) 준수용.
    private val probeHistory = ConcurrentHashMap<String, ProbeRecord>()

    // 마지막으로 알려진 카메라 IP 캐싱용 SharedPreferences
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "camera_discovery_cache",
        Context.MODE_PRIVATE
    )

    private data class ProbeRecord(val atMs: Long, val open: Boolean)

    companion object {
        private const val TAG = "PtpipDiscoveryService"
        private const val PREF_LAST_CAMERA_IP = "last_camera_ip"
        private const val PREF_LAST_CAMERA_NAME = "last_camera_name"
        private const val PREF_LAST_CAMERA_SERVICE_TYPE = "last_camera_service_type"
        private const val PREF_LAST_SUCCESS_TIME = "last_success_time"
        private const val CACHE_VALID_DURATION_MS = 24 * 60 * 60 * 1000L // 24시간

        /** 표준 PTP/IP 포트 */
        private const val PTPIP_PORT = 15740

        /** resolve 실패/타임아웃 시 재큐잉 상한(초과 시 그 서비스는 이번 검색에서 포기). */
        private const val MAX_RESOLVE_ATTEMPTS = 2

        /**
         * 카메라가 아님이 확실한 서비스 타입(프린터·네트워크 감시카메라 계열).
         *
         * 표준 PTP/IP 포트를 광고하면 이 목록에 있어도 카메라로 취급한다 —
         * 목록에서 완전히 빼는 것은 "이 타입 + 비표준 포트" 조합뿐이다.
         */
        private val NON_CAMERA_SERVICE_TYPES = listOf(
            "_ipp.",
            "_pdl-datastream.",
            "_axis-video."
        )

        /**
         * 우선 드레인 대상 서비스 타입(카메라). 프린터류(_ipp/_pdl-datastream 등)가 resolve 큐를
         * 점유해 예산 안에 카메라 resolve가 못 끝나는 것을 완화하는 유일한 수단이다.
         * `PtpipConstants.SERVICE_TYPES` 자체는 축소하지 않는다(실기 mDNS 덤프 확보 전 제거 금지).
         */
        private val PRIORITY_SERVICE_TYPES = listOf("_ptp._tcp", "_ptpip._tcp", "_nikon._tcp")

        /**
         * 후보 병합(같은 IP:port 중복 제거) 순수 함수.
         *
         * 규칙:
         * - 신규 endpoint(IP:port)는 그대로 추가한다.
         * - 기존 후보가 NIKON verdict면 타 벤더 신호로 **덮지 않는다**. 덮으면 STA 인증이 생략되어
         *   첫 페어링이 InitFail 0x1로 파손된다(기존 SSDP 병합 규칙 보존).
         * - 그 외에는 제조사 판별 신뢰도가 높은 쪽을 남기고, 동일 신뢰도면 출처 우선순위
         *   ([CameraDiscoverySource.priority] — 라이브 신호 > 캐시/추정)가 높은 쪽을 남긴다.
         */
        internal fun mergeCandidates(
            existing: List<PtpipCamera>,
            incoming: List<PtpipCamera>
        ): List<PtpipCamera> {
            if (incoming.isEmpty()) return existing
            val merged = LinkedHashMap<String, PtpipCamera>(existing.size + incoming.size)
            existing.forEach { merged[endpointKey(it)] = it }
            for (candidate in incoming) {
                val key = endpointKey(candidate)
                val previous = merged[key]
                if (previous == null) {
                    merged[key] = candidate
                    continue
                }
                if (previous.vendorVerdict.vendor == CameraVendor.NIKON &&
                    candidate.vendorVerdict.vendor != CameraVendor.NIKON
                ) {
                    continue
                }
                val previousRank = CameraVendorClassifier.confidenceRank(previous.vendorVerdict)
                val candidateRank = CameraVendorClassifier.confidenceRank(candidate.vendorVerdict)
                val promote = candidateRank > previousRank ||
                    (candidateRank == previousRank &&
                        candidate.discoverySource.priority > previous.discoverySource.priority)
                if (promote) {
                    merged[key] = candidate
                }
            }
            return merged.values.toList()
        }

        /**
         * IPv4 전용 필터.
         *
         * libgphoto2 ptpip은 AF_INET 전용이고 `ptpip:IP:PORT` 경로가 콜론 구분이라
         * IPv6 리터럴을 담을 수 없다. IPv6로 resolve된 서비스는 후보에서 제외한다.
         */
        internal fun isSupportedHost(host: InetAddress?): Boolean = host is Inet4Address

        private fun endpointKey(camera: PtpipCamera): String = "${camera.ipAddress}:${camera.port}"
    }

    /**
     * 같은 IP로의 재-TCP 최소 간격 규약(순수 함수 — 단위 테스트 대상).
     *
     * 근거: Nikon은 close 직후 너무 빨리 새 TCP를 열면 "TCP만 수락하고 응답 안 함" 상태에 빠진다
     * (airnef 문서화, `PtpipDataSource` 연결 backoff 주석과 동일 근거). ≥1s 간격을 강제한다.
     */
    internal object ProbeCooldown {
        const val MIN_INTERVAL_MS = 1_000L

        /** 직전 프로브 결과를 재사용해야 하는가(= 새 소켓을 열면 안 되는가). */
        fun shouldReuse(lastAtMs: Long, nowMs: Long): Boolean {
            if (lastAtMs <= 0L) return false
            val delta = nowMs - lastAtMs
            return delta >= 0L && delta < MIN_INTERVAL_MS
        }

        /** 다음 TCP 시도까지 남은 대기 시간(ms). */
        fun remainingMs(lastAtMs: Long, nowMs: Long): Long {
            if (lastAtMs <= 0L) return 0L
            return (MIN_INTERVAL_MS - (nowMs - lastAtMs)).coerceIn(0L, MIN_INTERVAL_MS)
        }
    }

    /**
     * PTPIP 지원 카메라 검색 (기존 1-파라미터 계약 유지 — 호출부 무변경).
     */
    suspend fun discoverCameras(forceApMode: Boolean = false): List<PtpipCamera> =
        discoverCameras(forceApMode, DiscoveryBudget.UserInitiated) {}

    /**
     * PTPIP 지원 카메라 검색 (예산 + 증분 방출).
     *
     * @param budget 검색 전체/개별 resolve 시간 예산과 기지 IP 조기 확정 허용 여부
     * @param onPartialResult 후보가 추가/승격될 때마다 **현재까지 병합된 스냅샷 전체**를 방출한다
     *        (개별 항목이 아님 — 병합·정렬 로직 중복을 막기 위함)
     */
    suspend fun discoverCameras(
        forceApMode: Boolean,
        budget: DiscoveryBudget,
        onPartialResult: (List<PtpipCamera>) -> Unit
    ): List<PtpipCamera> = withContext(ioDispatcher) {
        val publishLock = Any()
        var snapshot: List<PtpipCamera> = emptyList()

        fun publish(incoming: List<PtpipCamera>): List<PtpipCamera> = synchronized(publishLock) {
            if (incoming.isNotEmpty()) {
                val merged = mergeCandidates(snapshot, incoming)
                if (merged != snapshot) {
                    snapshot = merged
                    onPartialResult(merged)
                }
            }
            snapshot
        }

        fun currentSnapshot(): List<PtpipCamera> = synchronized(publishLock) { snapshot }

        val knownIp = runCatching {
            ptpipPreferencesDataSource.getLastConnectedCameraInfo()?.first
        }.getOrNull()?.takeIf { it.isNotBlank() }

        try {
            Log.i(TAG, "카메라 검색 시작 (budget=$budget, forceApMode=$forceApMode)")

            // 1단계: 캐시된 IP 확인 (STA 모드에서만). 조기 return 없이 후보로 편입만 한다.
            if (!forceApMode && !wifiHelper.isConnectedToCameraAP()) {
                Log.d(TAG, "1단계: 캐시된 IP 확인 시도...")
                val cachedCamera = tryCachedIP()
                if (cachedCamera != null) {
                    publish(listOf(cachedCamera))
                    if (budget.allowEarlyConfirmOnKnownIp && cachedCamera.ipAddress == knownIp) {
                        // 배경 재연결 경로: 기억된 카메라가 살아 있으면 즉시 확정해 응답성을 보존한다.
                        Log.i(TAG, "기지 IP 캐시 확정 - 배경 예산에서 즉시 반환")
                        // 조기 확정도 "캐시가 유효했다"는 실증이므로 만료 시각을 갱신한다.
                        // 갱신하지 않으면 마지막 풀 검색으로부터 24h 뒤 캐시가 만료돼
                        // 배경 재연결의 빠른 경로가 조용히 사라진다(CACHE_VALID_DURATION_MS).
                        saveDiscoveryCache(listOf(cachedCamera), knownIp)
                        return@withContext currentSnapshot()
                    }
                }
            }

            // 2단계: AP 모드 처리
            if (forceApMode || wifiHelper.isConnectedToCameraAP()) {
                publish(discoverApModeCameras())
                return@withContext currentSnapshot()
            }

            // 3단계: STA 모드에서 mDNS + SSDP 병행 검색 (예산 소진까지 누적)
            Log.d(TAG, "3단계: STA 모드 - mDNS/SSDP 병행 카메라 자동 검색")
            coroutineScope {
                // SSDP는 mDNS와 병행 실행 — mDNS에 광고하지 않는 제조사
                // (Canon SSDP/UPnP, Sony 구형, Panasonic)를 연결 전에 판별한다.
                val ssdpDeferred = async {
                    try {
                        // softAP 로컬 IP에 바인딩해 M-SEARCH가 반드시 핫스팟 세그먼트로 나가게 한다.
                        ssdpDiscoveryService.discover(
                            timeoutMs = budget.totalMs,
                            bindAddress = wifiHelper.softApIpv4Address()
                                ?: wifiHelper.localIpv4Prefix()?.first
                        )
                    } catch (ce: CancellationException) {
                        // 협력 취소는 반드시 전파 — 삼키면 취소가 빈 결과로 위장된다.
                        throw ce
                    } catch (e: Exception) {
                        Log.w(TAG, "SSDP 검색 실패: ${e.message}")
                        emptyList()
                    }
                }

                discoverMdnsCandidates(budget) { candidate ->
                    publish(listOf(candidate))
                    Log.i(
                        TAG,
                        "카메라 발견: ${LogMask.id(candidate.name)} " +
                            "(${LogMask.id(candidate.ipAddress)}:${candidate.port})"
                    )
                }

                // SSDP 결과 병합 — 같은 IP는 mergeCandidates가 NIKON verdict 보존 규칙으로 처리한다.
                val ssdpCameras = ssdpDeferred.await()
                val accepted = mutableListOf<PtpipCamera>()
                for (ssdpCamera in ssdpCameras) {
                    val alreadyListed =
                        currentSnapshot().any { it.ipAddress == ssdpCamera.ipAddress }
                    if (alreadyListed) {
                        // 이미 mDNS로 잡힌 기기 — 프로브 없이 verdict 승격만 시도한다.
                        accepted += ssdpCamera
                        continue
                    }
                    // TV/가전 잡음 차단: Sony Bravia가 ScalarWebAPI URN을, Panasonic 가전이
                    // rootdevice를 광고하므로 PTP/IP 포트(15740)가 실제로 열린 기기만 목록에 올린다.
                    // testPtpipConnection은 순수 TCP connect라 InitCommandRequest를 보내지 않아
                    // 니콘 세션락에도 안전하다.
                    if (probePtpipPort(ssdpCamera.ipAddress, ssdpCamera.port)) {
                        accepted += ssdpCamera
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
                }
                publish(accepted)
            }

            val result = currentSnapshot()
            Log.i(TAG, "카메라 검색 완료: ${result.size}개 발견")
            if (result.isEmpty()) {
                Log.w(TAG, "카메라를 찾을 수 없습니다")
            }
            saveDiscoveryCache(result, knownIp)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "카메라 검색 중 오류", e)
        }

        currentSnapshot()
    }

    /**
     * AP 모드(카메라 자체 AP) 후보 탐색: 게이트웨이 IP → 실패 시 기본 IP 목록.
     */
    private suspend fun discoverApModeCameras(): List<PtpipCamera> {
        Log.d(TAG, "2단계: AP 모드 처리 - 게이트웨이 IP 직접 연결 시도")
        val gatewayIP = wifiHelper.detectCameraIPInAPMode()
        if (gatewayIP != null) {
            Log.i(TAG, "게이트웨이 IP 발견: ${LogMask.id(gatewayIP)}")
            if (probePtpipPort(gatewayIP, PTPIP_PORT)) {
                Log.i(TAG, "AP 모드: 게이트웨이 PTP-IP 연결 성공")
                val label = "${wifiHelper.getCurrentSSID() ?: "카메라 AP"} (AP모드)"
                saveCachedIP(gatewayIP, label)
                return listOf(apCamera(gatewayIP, label))
            }
        }

        // 게이트웨이 실패 시 기본 IP들 시도
        for (ip in PtpipConstants.DEFAULT_CAMERA_IPS) {
            if (probePtpipPort(ip, PTPIP_PORT)) {
                Log.i(TAG, "기본 IP PTP-IP 연결 성공: ${LogMask.id(ip)}")
                val label = "${wifiHelper.getCurrentSSID() ?: "카메라 AP"} (AP모드 - $ip)"
                saveCachedIP(ip, label)
                return listOf(apCamera(ip, label))
            }
        }

        Log.w(TAG, "AP 모드에서 PTP-IP 연결 가능한 카메라를 찾을 수 없음")
        return emptyList()
    }

    /** AP 경로 후보 생성 — name(게이트 입력)과 displayName 모두 현행 라벨을 유지한다. */
    private fun apCamera(ipAddress: String, label: String) = PtpipCamera(
        ipAddress = ipAddress,
        port = PTPIP_PORT,
        name = label,
        isOnline = true,
        displayName = label,
        discoverySource = CameraDiscoverySource.AP_GATEWAY
    )

    /**
     * 캐시된 IP로 빠른 도달성 확인.
     *
     * ⚠️ `name`은 저장된 **원본 이름**을 그대로 담는다(과거의 `"$cachedName (캐시)"` 문자열 접합은
     * 게이트 입력을 오염시켜 Nikon 판별을 뒤집을 수 있어 제거했다). 출처 표시는
     * [CameraDiscoverySource.CACHED_IP] 배지가 담당한다.
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
            probePtpipPort(cachedIP, PTPIP_PORT)
        } ?: false

        if (success) {
            Log.i(TAG, "캐시된 IP 연결 성공")
            // 캐시된 서비스 타입으로 재판별 — 개명 니콘(_nikon._tcp로만 CONFIRMED)이
            // 캐시 경로에서 verdict를 잃고 STA 인증을 건너뛰는 비일관을 막는다.
            val cachedType = prefs.getString(PREF_LAST_CAMERA_SERVICE_TYPE, null)
            PtpipCamera(
                ipAddress = cachedIP,
                port = PTPIP_PORT,
                name = cachedName,
                isOnline = true,
                discoveredServiceType = cachedType,
                vendorVerdict = CameraVendorClassifier.classifyMdns(cachedName, cachedType),
                displayName = cachedName,
                discoverySource = CameraDiscoverySource.CACHED_IP
            )
        } else {
            Log.d(TAG, "캐시된 IP 연결 실패")
            null
        }
    }

    /**
     * 빠른 경로 캐시 갱신 정책.
     *
     * (a) DataStore 기지 IP와 일치하는 후보가 있으면 그것, 없고 (b) 후보가 1개면 그것으로 저장한다.
     * 후보 2개 이상 + 기지 일치 없음이면 **저장하지 않는다**(타인 카메라 캐시 오염 방지).
     * SharedPreferences 스키마는 변경하지 않는다.
     */
    private fun saveDiscoveryCache(cameras: List<PtpipCamera>, knownIp: String?) {
        if (cameras.isEmpty()) return
        val target = cameras.firstOrNull { knownIp != null && it.ipAddress == knownIp }
            ?: cameras.singleOrNull()
        if (target == null) {
            Log.d(TAG, "후보 ${cameras.size}개 + 기지 IP 일치 없음 - 빠른경로 캐시 미갱신")
            return
        }
        saveCachedIP(target.ipAddress, target.name, target.discoveredServiceType)
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
     * mDNS 후보 누적 검색.
     *
     * 서비스 타입 전부를 동시에 discover하되, resolve는 **직렬 큐 1건**으로 처리한다.
     * 조기 resume/stopServiceDiscovery는 없다 — [DiscoveryBudget.totalMs]를 소진할 때까지
     * 발견되는 모든 서비스를 resolve해 [onCandidate]로 흘린다.
     *
     * 모든 종료 경로(정상/타임아웃/취소)에서 `finally`가 자기 listener를 해제한다.
     * 유출된 listener는 다음 검색·연결의 mDNS 소켓을 흔들어 카메라를 죽인다.
     */
    private suspend fun discoverMdnsCandidates(
        budget: DiscoveryBudget,
        onCandidate: (PtpipCamera) -> Unit
    ) {
        val queue = ResolveQueue()
        val seenServices = java.util.Collections.synchronizedSet(mutableSetOf<String>())
        val startedListeners = mutableListOf<NsdManager.DiscoveryListener>()
        // resolve 실패/타임아웃한 서비스의 재시도 횟수. onServiceFound가 seenServices로 이미
        // 소비한 서비스는 다시 큐에 오지 않으므로, 재큐잉이 없으면 그 카메라는 이번 검색에서
        // 영구 유실된다(= 목록에 안 뜨는 실패 모드). API 29~32의 ALREADY_ACTIVE 실패가 특히 그렇다.
        val resolveAttempts = java.util.Collections.synchronizedMap(mutableMapOf<String, Int>())
        // mDNS 응답은 224.0.0.251 멀티캐스트로 온다. 락이 없으면 Wi-Fi 칩이 절전 중 그 프레임을
        // 버려서 카메라가 응답했는데도 앱에 도달하지 않는다(매니페스트 선언만 있고 취득 코드가 없었다).
        val multicastLock = wifiHelper.acquireMulticastLock("camcon-mdns")
        try {
            withTimeoutOrNull(budget.totalMs) {
                coroutineScope {
                    // 단일 소비 코루틴 + resolveGate = 앱 전체 동시 resolve 1건 보장.
                    launch {
                        while (isActive) {
                            val service = queue.take()
                            val resolved = resolveGate.withLock {
                                withTimeoutOrNull(budget.resolveTimeoutMs) {
                                    resolveService(service)
                                }
                            }
                            if (resolved == null) {
                                val key = "${service.serviceName}:${service.serviceType}"
                                val attempts = (resolveAttempts[key] ?: 0) + 1
                                resolveAttempts[key] = attempts
                                if (attempts <= MAX_RESOLVE_ATTEMPTS) {
                                    Log.d(
                                        TAG,
                                        "resolve 미완/타임아웃 - 재큐잉($attempts/$MAX_RESOLVE_ATTEMPTS): " +
                                            LogMask.id(service.serviceName)
                                    )
                                    // 우선순위 없이 뒤로 보낸다 — 아직 시도하지 않은 서비스가 먼저다.
                                    queue.offer(service, priority = false)
                                } else {
                                    Log.w(
                                        TAG,
                                        "resolve 재시도 한도 초과 - 후보 포기: " +
                                            LogMask.id(service.serviceName)
                                    )
                                }
                                continue
                            }
                            toMdnsCamera(resolved)?.let(onCandidate)
                        }
                    }

                    for (serviceType in PtpipConstants.SERVICE_TYPES) {
                        val priority = PRIORITY_SERVICE_TYPES.any { serviceType.contains(it) }
                        val listener = createDiscoveryListener(seenServices, queue, priority)
                        try {
                            nsdManager.discoverServices(
                                serviceType,
                                NsdManager.PROTOCOL_DNS_SD,
                                listener
                            )
                            startedListeners += listener
                            activeDiscoveryListeners.add(listener)
                        } catch (e: Exception) {
                            Log.e(TAG, "mDNS 검색 시작 중 오류: $serviceType - ${e.message}")
                        }
                    }
                }
            }
        } finally {
            startedListeners.forEach { stopDiscoveryListener(it) }
            // 해제 누락은 배터리를 계속 소모한다 — 취소·예외 경로에서도 반드시 놓는다.
            wifiHelper.releaseMulticastLock(multicastLock)
        }
    }

    private fun createDiscoveryListener(
        seenServices: MutableSet<String>,
        queue: ResolveQueue,
        priority: Boolean
    ): NsdManager.DiscoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {
            Log.d(TAG, "mDNS 검색 시작됨: $regType")
        }

        override fun onServiceFound(service: NsdServiceInfo) {
            Log.d(TAG, "서비스 발견: ${LogMask.id(service.serviceName)}")
            val serviceKey = "${service.serviceName}:${service.serviceType}"
            if (!seenServices.add(serviceKey)) return
            queue.offer(service, priority)
        }

        override fun onServiceLost(service: NsdServiceInfo) {
            Log.d(TAG, "서비스 손실: ${LogMask.id(service.serviceName)}")
            seenServices.remove("${service.serviceName}:${service.serviceType}")
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.d(TAG, "mDNS 검색 중지됨: $serviceType")
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "mDNS 검색 시작 실패: $serviceType, 에러코드: $errorCode")
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.w(TAG, "mDNS 검색 중지 실패: $serviceType, 에러코드: $errorCode")
        }
    }

    /**
     * 서비스 1건 resolve. 서비스마다 **새 ResolveListener 인스턴스**를 생성한다
     * (단일 객체 재사용은 동시 검색 간 콜백 혼선을 만든다).
     */
    private suspend fun resolveService(service: NsdServiceInfo): NsdServiceInfo? =
        suspendCancellableCoroutine { continuation ->
            val listener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.w(
                        TAG,
                        "서비스 resolve 실패: ${LogMask.id(serviceInfo.serviceName)}, 에러코드: $errorCode"
                    )
                    if (continuation.isActive) continuation.resume(null)
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    Log.d(
                        TAG,
                        "서비스 resolve 성공: ${LogMask.id(serviceInfo.serviceName)} " +
                            "(${LogMask.id(serviceInfo.host?.hostAddress)}:${serviceInfo.port})"
                    )
                    if (continuation.isActive) continuation.resume(serviceInfo)
                }
            }
            // 타임아웃/취소로 이 코루틴이 접히면 프레임워크 resolve 요청은 그대로 살아 있다.
            // 방치하면 API 29~32에서 다음 resolve가 FAILURE_ALREADY_ACTIVE(3)로 거부된다.
            // API 34+에는 명시 취소 API가 있어 그때만 정리한다(하위 버전은 재큐잉으로 흡수).
            continuation.invokeOnCancellation {
                if (android.os.Build.VERSION.SDK_INT >= 34) {
                    runCatching { nsdManager.stopServiceResolution(listener) }
                        .onFailure { Log.d(TAG, "resolve 취소 실패(무해): ${it.message}") }
                }
            }
            try {
                nsdManager.resolveService(service, listener)
            } catch (e: Exception) {
                Log.w(TAG, "서비스 resolve 요청 실패: ${e.message}")
                if (continuation.isActive) continuation.resume(null)
            }
        }

    /**
     * resolve된 서비스를 후보로 변환. IPv4가 아니면 제외한다.
     */
    private fun toMdnsCamera(serviceInfo: NsdServiceInfo): PtpipCamera? {
        return try {
            val host = serviceInfo.host
            val serviceName = serviceInfo.serviceName.orEmpty()
            if (!isSupportedHost(host)) {
                Log.d(
                    TAG,
                    "IPv4 아님 - 후보 제외: ${LogMask.id(serviceName)} " +
                        "(host=${host?.javaClass?.simpleName})"
                )
                return null
            }
            val hostAddress = host?.hostAddress
            val port = serviceInfo.port
            if (hostAddress.isNullOrBlank() || port <= 0) {
                Log.w(TAG, "유효하지 않은 서비스 정보: ${LogMask.id(serviceName)}")
                return null
            }

            val verdict = CameraVendorClassifier.classifyMdns(serviceName, serviceInfo.serviceType)

            // 실측 확보용 덤프: 실기별 mDNS 광고 실태(타입·TXT)를 남긴다.
            // ⚠️ TXT 값에는 기종·시리얼 계열 식별자가 실릴 수 있으므로 **디버그 빌드에서만** 값을
            // 남기고, 릴리스에서는 키 목록만 남긴다(어떤 필드가 오는지는 알아야 하되 값은 PII).
            if (BuildConfig.DEBUG) {
                val txt = serviceInfo.attributes.entries.joinToString {
                    "${it.key}=${it.value?.toString(Charsets.UTF_8).orEmpty()}"
                }
                Log.i(
                    TAG,
                    "VENDOR_MDNS_DUMP name=${LogMask.id(serviceName)} " +
                        "type=${serviceInfo.serviceType} verdict=$verdict txt={$txt}"
                )
            } else {
                Log.i(
                    TAG,
                    "VENDOR_MDNS_DUMP name=${LogMask.id(serviceName)} " +
                        "type=${serviceInfo.serviceType} verdict=$verdict " +
                        "txtKeys=${serviceInfo.attributes.keys}"
                )
            }

            // 비카메라 서비스 차단. 조기 종료를 제거해 예산 소진까지 모든 서비스를 resolve하게 됐고,
            // 검색 타입에 프린터·네트워크카메라 계열(_ipp/_pdl-datastream/_axis-video/_dpsoffer)이
            // 포함돼 있어 필터가 없으면 가정집 프린터가 목록에 "카메라"로 노출된다.
            // 프로브를 쓰지 않는 무비용 정적 판정만 한다(mDNS 무프로브 규약 유지).
            // 서비스 타입 자체는 줄이지 않는다 — 실기 덤프(VENDOR_MDNS_DUMP) 확보가 끝나기 전까지는
            // 어떤 타입이 실제로 쓰이는지 확정할 수 없다.
            if (!isPlausiblePtpipService(serviceInfo.serviceType, port)) {
                Log.d(
                    TAG,
                    "PTP/IP 서비스로 보이지 않음 - 후보 제외: ${LogMask.id(serviceName)} " +
                        "(type=${serviceInfo.serviceType}, port=$port)"
                )
                return null
            }

            PtpipCamera(
                ipAddress = hostAddress,
                port = port,
                // ⚠️ 원본 mDNS 이름을 그대로 유지 — Nikon STA 인증 게이트의 유일한 입력이다.
                name = extractCameraName(serviceName, hostAddress),
                isOnline = true,
                discoveredServiceType = serviceInfo.serviceType,
                vendorVerdict = verdict,
                displayName = null,
                discoverySource = CameraDiscoverySource.MDNS
            )
        } catch (e: Exception) {
            Log.w(TAG, "서비스 정보 처리 중 오류: ${e.message}")
            null
        }
    }

    /**
     * PTP/IP 카메라로 볼 수 있는 서비스인가 (프로브 없는 정적 판정).
     *
     * **블랙리스트 방식이다.** 화이트리스트(`_ptp`/`_ptpip`/`_nikon`만 통과)로 하면 표준 포트가 아닌
     * 곳에 광고하는 **비니콘 카메라가 탈락**한다 — 제조사별 mDNS 실광고 실태가 아직 미확정이므로
     * (`SERVICE_TYPES`의 4종은 근거 미확인) "배제 근거가 확실한 것만" 뺀다. 니콘 편향을 만들지 않는
     * 쪽이 이 앱의 목표(다제조사 지원)에 맞다.
     *
     * 예외: 프린터 계열 타입이라도 **표준 PTP/IP 포트(15740)를 광고하면 카메라로 본다** —
     * 일부 Canon이 `_ipp._tcp`를 쓴다는 기록이 있다(`PtpipConstants.SERVICE_TYPES` 주석).
     */
    private fun isPlausiblePtpipService(serviceType: String?, port: Int): Boolean {
        val type = serviceType?.lowercase().orEmpty()
        if (NON_CAMERA_SERVICE_TYPES.any { type.contains(it) }) {
            return port == PTPIP_PORT
        }
        return true
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
     * PTP/IP 포트 도달성 프로브 (IP별 ≥1s 쿨다운 적용).
     *
     * 같은 IP로 [ProbeCooldown.MIN_INTERVAL_MS] 이내 재요청이면 새 TCP를 열지 않고
     * 직전 결과를 재사용한다.
     */
    private suspend fun probePtpipPort(ipAddress: String, port: Int): Boolean {
        val now = SystemClock.elapsedRealtime()
        probeHistory[ipAddress]?.let { record ->
            if (ProbeCooldown.shouldReuse(record.atMs, now)) {
                Log.d(
                    TAG,
                    "프로브 쿨다운(<${ProbeCooldown.MIN_INTERVAL_MS}ms) - 직전 결과 재사용: " +
                        "${LogMask.id(ipAddress)}=${record.open}"
                )
                return record.open
            }
        }
        val open = testPtpipConnection(ipAddress, port)
        probeHistory[ipAddress] = ProbeRecord(SystemClock.elapsedRealtime(), open)
        return open
    }

    /**
     * 같은 IP로 새 TCP를 열기까지 남은 대기 시간(ms). 연결 진입 직전 대기용.
     */
    fun probeCooldownRemainingMs(ipAddress: String): Long =
        ProbeCooldown.remainingMs(
            probeHistory[ipAddress]?.atMs ?: 0L,
            SystemClock.elapsedRealtime()
        )

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

    private fun stopDiscoveryListener(listener: NsdManager.DiscoveryListener) {
        try {
            nsdManager.stopServiceDiscovery(listener)
        } catch (e: Exception) {
            Log.w(TAG, "mDNS 검색 중지 중 오류: ${e.message}")
        } finally {
            activeDiscoveryListeners.remove(listener)
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
        listeners.forEach { stopDiscoveryListener(it) }
    }

    /**
     * 캐시 초기화 (디버그용)
     */
    fun clearCache() {
        prefs.edit().clear().apply()
        Log.d(TAG, "캐시 초기화 완료")
    }

    /**
     * resolve 대기 큐. 카메라 서비스 타입(_ptp/_ptpip/_nikon)을 우선 드레인한다.
     *
     * 신호 채널은 "깨우기" 용도라 카운트가 정확하지 않아도 무해하다(재확인 루프로 흡수).
     * `take()`는 취소 가능하므로 예산 만료 시 소비 코루틴이 즉시 정리된다.
     */
    private class ResolveQueue {
        private val lock = Any()
        private val high = ArrayDeque<NsdServiceInfo>()
        private val low = ArrayDeque<NsdServiceInfo>()
        private val signal = Channel<Unit>(Channel.UNLIMITED)

        fun offer(service: NsdServiceInfo, priority: Boolean) {
            synchronized(lock) {
                if (priority) high.addLast(service) else low.addLast(service)
            }
            signal.trySend(Unit)
        }

        suspend fun take(): NsdServiceInfo {
            while (true) {
                val next = synchronized(lock) {
                    high.removeFirstOrNull() ?: low.removeFirstOrNull()
                }
                if (next != null) return next
                signal.receive()
            }
        }
    }
}
