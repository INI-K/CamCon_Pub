package com.inik.camcon.data.datasource.ptpip

import android.util.Log
import com.inik.camcon.data.network.ptpip.connection.PtpipConnectionManager
import com.inik.camcon.data.network.ptpip.discovery.DiscoveryBudget
import com.inik.camcon.data.network.ptpip.discovery.PtpipDiscoveryService
import com.inik.camcon.data.network.ptpip.discovery.SubnetSweepDiscoverySource
import com.inik.camcon.data.network.ptpip.wifi.WifiNetworkHelper
import com.inik.camcon.domain.model.CameraDiscoverySource
import com.inik.camcon.domain.model.NikonConnectionMode
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.utils.LogMask
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * PTP/IP 카메라 디스커버리 조율 협력자 (PtpipDataSource에서 분리).
 *
 * mDNS/AP 검색, 수동 IP 입력·수동 카메라 등록, 발견 목록 상태([discoveredCameras])를 소유한다.
 * 후보 목록의 **권위는 이 클래스의 `_discoveredCameras` 단일 지점**이다(다른 목록을 만들지 않는다).
 *
 * 세션 점유 중 검색 스킵 판정은 [discoveryBlockedProvider]로 읽는다(연결 엔진은 파사드가 소유 —
 * 상태 공유는 읽기 전용 람다로 최소화). 조건식의 정의는 `PtpipDataSource.isDiscoveryBlocked()`
 * 한 곳에만 있으며 여기서 복제하지 않는다.
 */
internal class PtpipDiscoveryCoordinator(
    private val wifiHelper: WifiNetworkHelper,
    private val discoveryService: PtpipDiscoveryService,
    private val connectionManager: PtpipConnectionManager,
    private val discoveryBlockedProvider: () -> Boolean,
    private val ioDispatcher: CoroutineDispatcher
) {
    private companion object {
        private const val TAG = "PtpipDataSource"

        /**
         * 검색 결과로 **덮어쓰지 않는** 후보 출처.
         *
         * - MANUAL_INPUT: 사용자가 방금 입력한 IP를 검색 1회가 조용히 지우면 안 된다.
         * - SUBNET_SCAN: 스윕을 쓰는 상황이 바로 "mDNS 0건"인 상황이라, 배경 폴링(4초 tick)이
         *   0건을 발표하는 순간 사용자가 방금 찾은 목록이 통째로 사라진다("찾았다가 사라짐").
         */
        val PRESERVED_SOURCES = setOf(
            CameraDiscoverySource.MANUAL_INPUT,
            CameraDiscoverySource.SUBNET_SCAN
        )
    }

    private val _discoveredCameras = MutableStateFlow<List<PtpipCamera>>(emptyList())
    val discoveredCameras: StateFlow<List<PtpipCamera>> = _discoveredCameras.asStateFlow()

    /**
     * 검색 single-flight 게이트.
     *
     * ⚠️ 없으면 이 웨이브가 고친 "후보 1대로 접힘"이 그대로 재현된다. 전경 검색(사용자 탭)과
     * 배경 폴링([com.inik.camcon.data.service.WifiMonitoringService], 4초 tick)이 동시에
     * [PtpipDiscoveryService.discoverCameras]를 호출하면 각자의 **호출 로컬 스냅샷**을
     * `_discoveredCameras`에 덮어쓴다. 배경 예산은 기지 IP 캐시 히트 시 후보 1건만 담아 즉시
     * 반환하므로, 배경 tick이 전경보다 늦게 끝나면 목록이 1건으로 고정돼 두 번째 카메라를
     * 선택할 수 없게 된다. 상태는 검색 중에도 DISCONNECTED라 `discoveryBlockedProvider()`로는
     * 막히지 않는다.
     */
    private val discoveryMutex = Mutex()

    /**
     * 서브넷 스윕(최후 폴백). 이미 주입된 의존만으로 내부 구성한다 —
     * `PtpipDataSource` 생성자를 늘리면 기존 테스트가 전부 깨진다.
     */
    private val subnetSweepSource = SubnetSweepDiscoverySource(wifiHelper, ioDispatcher)

    /**
     * 후보 목록 갱신 단일 지점.
     *
     * 사용자가 직접 등록한 후보(MANUAL_INPUT)는 검색 결과에 없어도 목록에서 지우지 않는다.
     * 목록이 선택 UI의 표면이 된 이상, 검색 1회가 사용자가 방금 입력한 IP를 조용히 없애면 안 된다.
     */
    private fun publishDiscovered(cameras: List<PtpipCamera>) {
        val preserved = _discoveredCameras.value.filter { existing ->
            existing.discoverySource in PRESERVED_SOURCES &&
                cameras.none { it.ipAddress == existing.ipAddress }
        }
        _discoveredCameras.value = cameras + preserved
    }

    // 사용자가 직접 입력한 카메라 IP. 폰 핫스팟 모드의 mDNS 폴백용.
    private val _manualIp = MutableStateFlow("")
    val manualIp: StateFlow<String> = _manualIp.asStateFlow()

    /**
     * 사용자 입력 IP 갱신. UI/ViewModel에서 호출.
     *
     * 빈 문자열은 초기화 신호로 그대로 받는다. 그 외에는 사설망/link-local 화이트리스트만 허용한다.
     * 위반 시 상태를 갱신하지 않고 경고만 남긴다 (UI는 기존 입력 유지).
     */
    fun setManualIp(ip: String) {
        if (ip.isBlank()) {
            _manualIp.value = ""
            return
        }
        if (!com.inik.camcon.data.network.ptpip.IpAddressValidator.isAllowedCameraIp(ip)) {
            Log.w(TAG, "setManualIp 거부: 허용되지 않은 IP 형식/대역 - ${LogMask.id(ip)}")
            return
        }
        _manualIp.value = ip
    }

    /**
     * 사용자가 입력한 IP를 카메라 후보로 등록한다.
     *
     * ⚠️ 동일 IP 후보가 이미 목록에 있으면 그 후보의 `name`/`vendorVerdict`/`discoveredServiceType`/
     * `discoverySource`를 **보존**하고 포트만 사용자 입력으로 갱신한다. 과거 filterNot+append 방식은
     * mDNS로 발견한 정보를 통째로 버려 NIKON verdict가 유실됐고(→ STA 인증 생략 → InitFail 0x1),
     * 그 경로가 실제 첫 페어링 파손 원인이 될 수 있었다.
     *
     * 기존 후보가 없을 때만 새 항목을 만든다. 이때 `name`은 **IP 그대로** 담는다
     * ("Manual (ip)" 라벨을 name에 넣으면 게이트 입력이 오염된다 — 표시는 displayName/UI 폴백 담당).
     *
     * IP는 사설망/link-local만 허용. 화이트리스트 외에는 `IllegalArgumentException`을 던진다.
     *
     * @param name 외부 계약 유지용. 의도적으로 사용하지 않는다 — 표시 라벨을 `name`에 쓰면
     *   Nikon 게이트 입력이 오염되고, `displayName`에 쓰면 i18n 되지 않은 문자열이 UI에 노출된다.
     */
    @Suppress("UNUSED_PARAMETER")
    fun addManualCamera(ipAddress: String, name: String, port: Int): PtpipCamera {
        require(
            com.inik.camcon.data.network.ptpip.IpAddressValidator.isAllowedCameraIp(ipAddress)
        ) {
            "허용되지 않은 카메라 IP: ${ipAddress.take(45)} (사설망/link-local만 허용)"
        }
        val safePort = if (port > 0) port else 15740
        val existing = _discoveredCameras.value.firstOrNull { it.ipAddress == ipAddress }
        val cam = existing?.copy(port = safePort, isOnline = true)
            ?: PtpipCamera(
                ipAddress = ipAddress,
                port = safePort,
                name = ipAddress,
                isOnline = true,
                displayName = null,
                discoverySource = CameraDiscoverySource.MANUAL_INPUT
            )
        _discoveredCameras.value =
            _discoveredCameras.value.filterNot { it.ipAddress == cam.ipAddress } + cam
        return cam
    }

    /**
     * mDNS를 사용하여 PTPIP 지원 카메라 검색 (사용자 주도 예산).
     */
    suspend fun discoverCameras(forceApMode: Boolean): List<PtpipCamera> =
        discoverCameras(forceApMode, DiscoveryBudget.UserInitiated)

    // 호환성용 무파라미터 래퍼
    suspend fun discoverCameras(): List<PtpipCamera> = discoverCameras(false)

    /**
     * mDNS를 사용하여 PTPIP 지원 카메라 검색 (예산 지정).
     */
    suspend fun discoverCameras(
        forceApMode: Boolean,
        budget: DiscoveryBudget
    ): List<PtpipCamera> {
        // 배경 폴링은 전경 검색을 기다리지 않는다 — 기다렸다가 뒤늦게 덮어쓰는 것이 문제의 원인이다.
        // 사용자 주도 검색은 진행 중인 배경 검색(예산 1.5s)이 끝나면 이어서 실행한다.
        if (budget.allowEarlyConfirmOnKnownIp) {
            if (!discoveryMutex.tryLock()) {
                Log.d(TAG, "검색이 이미 진행 중 - 배경 호출 건너뜀 (single-flight)")
                return _discoveredCameras.value
            }
            return try {
                discoverCamerasLocked(forceApMode, budget)
            } finally {
                discoveryMutex.unlock()
            }
        }
        return discoveryMutex.withLock { discoverCamerasLocked(forceApMode, budget) }
    }

    private suspend fun discoverCamerasLocked(
        forceApMode: Boolean,
        budget: DiscoveryBudget
    ): List<PtpipCamera> {
        return try {
            Log.d(TAG, "카메라 검색 시작 (budget=$budget)")

            // 세션 점유 중(CONNECTING/CONNECTED/무선수신)에는 검색·프로브를 전면 스킵한다.
            // 카메라는 PTP/IP 세션 1개만 허용하므로 점유 중 프로브는 자기 카메라를 '미개방'으로
            // 오판하고, 고아 소켓이 남으면 새 TCP가 -7/End-of-stream으로 거부된다.
            // 반환값은 기존 목록 — emptyList를 돌려주면 UI 목록이 소실된다.
            if (discoveryBlockedProvider()) {
                Log.d(TAG, "세션 점유 중 - 검색 건너뜀 (직렬화 보호)")
                return _discoveredCameras.value
            }

            // Wi-Fi 연결 상태 확인. 단 폰 핫스팟(STA_PHONE_HOTSPOT) 모드에선 폰이 SoftAP라
            // 클라이언트 연결이 없는 게 정상이므로, 핫스팟이 켜져 있으면 mDNS 검색을 진행한다.
            if (!wifiHelper.isWifiConnected() && !wifiHelper.isHotspotEnabled()) {
                Log.w(TAG, "Wi-Fi 네트워크에 연결되어 있지 않음 (핫스팟도 꺼짐)")
                return emptyList()
            }

            // AP모드인지 확인하고 직접 IP 사용
            if (wifiHelper.isConnectedToCameraAP()) {
                Log.d(TAG, "AP모드 감지: libgphoto2 기반 카메라 IP 검색 시작")
                val cameraIP = wifiHelper.findAvailableCameraIP()
                if (cameraIP != null) {
                    Log.i(TAG, "AP모드: libgphoto2로 검증된 카메라 IP ${LogMask.id(cameraIP)} 발견")
                    val label = "${wifiHelper.getCurrentSSID() ?: "카메라 AP"} (AP모드)"
                    val apCamera = PtpipCamera(
                        ipAddress = cameraIP,
                        port = 15740, // 표준 PTP/IP 포트
                        name = label,
                        isOnline = true,
                        displayName = label,
                        discoverySource = CameraDiscoverySource.AP_GATEWAY
                    )
                    publishDiscovered(listOf(apCamera))
                    return listOf(apCamera)
                } else {
                    Log.w(TAG, "❌ AP모드이지만 libgphoto2로 연결 가능한 카메라 IP를 찾을 수 없음")
                    // 빈 리스트 반환하여 사용자에게 상황을 알림
                    publishDiscovered(emptyList())
                    return emptyList()
                }
            }

            // STA모드에서는 mDNS 검색 사용. 후보가 잡히는 즉시 목록에 반영한다(증분 방출).
            Log.d(TAG, "STA모드 또는 일반 네트워크: mDNS 검색 시작")
            val cameras = discoveryService.discoverCameras(forceApMode, budget) { snapshot ->
                publishDiscovered(snapshot)
            }
            publishDiscovered(cameras)
            cameras
        } catch (ce: kotlinx.coroutines.CancellationException) {
            // 협력 취소는 반드시 전파 — 삼키면 취소가 "검색 결과 0건"으로 위장되고,
            // 한 레이어 아래(PtpipDiscoveryService)에서 rethrow한 의미론이 여기서 무력화된다.
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "카메라 검색 중 오류", e)
            emptyList()
        }
    }

    /**
     * 서브넷 스윕 실행 가능 여부(= 프리픽스를 얻을 수 있는가). UI 버튼 노출 조건.
     *
     * 폰 핫스팟에서 프리픽스를 못 얻으면 스윕은 아무 일도 하지 않으므로, 버튼 자체를 내보내지 않는다.
     */
    fun isSubnetSweepAvailable(): Boolean = subnetSweepSource.isAvailable()

    /**
     * 서브넷 스윕 결과를 기존 후보 목록에 **병합**한다(덮어쓰지 않는다).
     *
     * mDNS/SSDP로 이미 잡힌 후보의 이름·verdict를 스윕 결과(신호 없음)가 지우면
     * Nikon STA 인증 게이트가 뒤집힌다 — 병합은 `mergeCandidates`의 출처 우선순위 규칙에 맡긴다.
     * 검색과 같은 single-flight 게이트를 공유해 목록 덮어쓰기 경합을 막는다.
     */
    suspend fun sweepSubnet(budgetMs: Long = SubnetSweepDiscoverySource.DEFAULT_BUDGET_MS):
        List<PtpipCamera> {
        if (discoveryBlockedProvider()) {
            Log.d(TAG, "세션 점유 중 - 서브넷 스윕 건너뜀")
            return _discoveredCameras.value
        }
        return discoveryMutex.withLock {
            val found = subnetSweepSource.sweep(budgetMs)
            val merged = PtpipDiscoveryService.mergeCandidates(_discoveredCameras.value, found)
            _discoveredCameras.value = merged
            Log.i(TAG, "서브넷 스윕 병합 완료: 신규 ${found.size}건 → 총 ${merged.size}건")
            merged
        }
    }

    /** 같은 IP로 새 TCP를 열기까지 남은 대기 시간(ms). */
    fun probeCooldownRemainingMs(ip: String): Long =
        discoveryService.probeCooldownRemainingMs(ip)

    /**
     * 같은 IP 재-TCP ≥1s 규약(airnef 문서화)에 맞춰 남은 간격만큼 대기한다.
     * 검색 직후 연결 진입 시 1회 호출한다.
     */
    suspend fun awaitProbeCooldown(ip: String) {
        val remaining = probeCooldownRemainingMs(ip)
        if (remaining > 0L) {
            Log.d(TAG, "프로브 쿨다운 대기 ${remaining}ms: ${LogMask.id(ip)}")
            delay(remaining)
        }
    }

    /**
     * 니콘 카메라 연결 모드 감지 (AP/STA/UNKNOWN)
     */
    suspend fun detectNikonConnectionMode(camera: PtpipCamera): NikonConnectionMode =
        withContext(ioDispatcher) {
            try {
                Log.d(TAG, "니콘 카메라 연결 모드 감지 시작: ${LogMask.serial(camera.name)}")

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
}
