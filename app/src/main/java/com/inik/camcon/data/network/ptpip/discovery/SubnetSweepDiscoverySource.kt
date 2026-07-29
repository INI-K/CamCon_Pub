package com.inik.camcon.data.network.ptpip.discovery

import android.os.SystemClock
import android.util.Log
import com.inik.camcon.data.network.ptpip.wifi.WifiNetworkHelper
import com.inik.camcon.domain.model.CameraDiscoverySource
import com.inik.camcon.domain.model.CameraProtocol
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.utils.LogMask
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel

/** 스윕 대상 서브넷(로컬 IPv4 + 프리픽스 길이). */
internal data class SweepTarget(
    val localIp: String,
    val prefixLength: Int
)

/**
 * 서브넷 TCP 스윕으로 **카메라 제어 포트가 열린 엔드포인트를 제조사 불문 한 번에** 찾는다.
 *
 * 제조사별 광고 프로토콜(Nikon=mDNS, Canon/Sony=SSDP, Fuji=자체 UDP)을 각각 구현하는 대신
 * [CameraProtocol.SWEEP_PORTS]를 호스트마다 훑는다 — 제조사를 몰라도, 광고를 하지 않아도,
 * 멀티캐스트가 차단된 망에서도 발견이 성립한다. 이것이 "한 번에 모든 카메라 검색"의 실질적 수단이다.
 *
 * ⚠️ **발견 ≠ 연결.** 후지 포크(55740)는 발견되지만 현재 연결되지 않는다
 * ([CameraProtocol.PTPIP_FUJI] KDoc 참조).
 *
 * 자동 연결 대상에서는 영구 제외한다. 그 이유:
 * - 결과에 이름·제조사 신호가 전혀 없다. Nikon STA 인증 게이트는 이름/서비스타입을 입력으로 쓰므로
 *   스윕 후보로 **첫 페어링을 시도하면 인증이 생략돼 InitFail 0x1로 깨진다**. 그래서 스윕 후보는
 *   자동 연결 대상에서 영구 제외하고(UI 격리 섹션), 이미 페어링을 마친 카메라의 재발견에 쓴다.
 * - 공용망에서는 타인의 카메라를 노출·접촉한다.
 * - Android의 로컬 네트워크 접근 제한(Android 17 / targetSdk 37의 `ACCESS_LOCAL_NETWORK`)이
 *   가장 먼저 겨냥하는 행위다. 그때는 TCP가 EPERM이 아니라 **timeout으로 조용히 실패**한다.
 *
 * 프로브 규약: 순수 TCP connect + 즉시 close. PTP 핸드셰이크 바이트를 절대 보내지 않는다
 * (InitCommandRequest를 보내고 abrupt close 하면 Nikon이 세션을 잠근다 — 실증된 사고).
 * 모든 채널은 취소·예외 경로에서도 `finally` + [NonCancellable]로 닫는다. 고아 소켓이 남으면
 * 카메라가 새 TCP를 -7/End-of-stream으로 거부해 앱 재시작까지 연결 불가가 된다.
 */
internal class SubnetSweepDiscoverySource(
    private val wifiHelper: WifiNetworkHelper,
    private val ioDispatcher: CoroutineDispatcher
) {
    companion object {
        private const val TAG = "PtpipSubnetSweep"

        /**
         * 스윕 총 예산.
         *
         * 호스트 × 포트 매트릭스라 대상이 포트 수만큼 늘어난다(/24 × 2포트 = 506). 열린 포트는
         * LAN에서 수 ms에 응답하고 닫힌 포트는 RST로 즉시 실패하므로, 실제 소요를 지배하는 것은
         * **응답이 없는(드롭되는) 주소의 타임아웃**이다. 사용자가 명시적으로 누르는 액션이므로
         * 검색(3초)보다는 여유를 두되, 셔터 기회를 놓칠 만큼 길게 잡지 않는다.
         */
        const val DEFAULT_BUDGET_MS = 2_500L

        /**
         * 동시 in-flight connect 수. /24 253개를 한 번에 열면 fd·Wi-Fi 큐가 터진다.
         * 배치로 나눠 열고 완료분만큼 다시 채운다.
         */
        private const val MAX_IN_FLIGHT = 64

        /**
         * `Selector.select` 1회 대기 상한. `select`는 취소 불가 블로킹이므로 조각내서
         * 루프마다 코루틴 취소를 확인한다.
         */
        private const val SELECT_SLICE_MS = 150L

        /** 스윕을 허용하는 프리픽스 범위. /24보다 넓으면 호스트 수가 폭발하고, /31~/32는 대상이 없다. */
        private const val MIN_PREFIX = 24
        private const val MAX_PREFIX = 30

        /**
         * 스윕 대상 호스트 목록. 네트워크 주소·브로드캐스트·자기 자신을 제외한다.
         *
         * 프리픽스가 허용 범위를 벗어나면 빈 목록을 반환한다(= 스윕 거부).
         */
        internal fun hostsOf(target: SweepTarget): List<String> {
            if (target.prefixLength !in MIN_PREFIX..MAX_PREFIX) return emptyList()
            val local = ipv4ToInt(target.localIp) ?: return emptyList()
            val mask = (-1 shl (32 - target.prefixLength))
            val network = local and mask
            val broadcast = network or mask.inv()
            if (broadcast - network < 2) return emptyList()
            return ((network + 1) until broadcast)
                .filter { it != local }
                .map { intToIpv4(it) }
        }

        internal fun ipv4ToInt(ip: String): Int? {
            val parts = ip.split('.')
            if (parts.size != 4) return null
            var result = 0
            for (part in parts) {
                val octet = part.toIntOrNull() ?: return null
                if (octet !in 0..255) return null
                result = (result shl 8) or octet
            }
            return result
        }

        internal fun intToIpv4(value: Int): String =
            "${(value ushr 24) and 0xFF}.${(value ushr 16) and 0xFF}." +
                "${(value ushr 8) and 0xFF}.${value and 0xFF}"
    }

    /**
     * 스윕 대상 판정. 얻지 못하면 null이고 이때 UI는 스윕 버튼을 노출하지 않아야 한다
     * (누르면 아무 일도 안 하는 버튼을 주력 모드에 내보내면 안 된다).
     *
     * 경로 2개:
     * 1. `NetworkInterface` 열거 — 폰 핫스팟(SoftAP)은 `Network` 객체로 등록되지 않아
     *    `activeNetwork`/`LinkProperties` 경로로는 프리픽스를 영영 얻을 수 없다. `ap0`/`swlan0`가
     *    주소와 프리픽스를 동시에 준다.
     * 2. `LinkProperties`(= `WifiNetworkState.subnetPrefix`) — 공유기 클라이언트 모드에서
     *    인터페이스 프리픽스가 비정상일 때의 보정값.
     */
    internal fun resolveTarget(): SweepTarget? {
        val fromInterface = wifiHelper.localIpv4Prefix()
        if (fromInterface != null && fromInterface.second in MIN_PREFIX..MAX_PREFIX) {
            return SweepTarget(fromInterface.first, fromInterface.second)
        }
        val localIp = fromInterface?.first ?: return null
        val statePrefix = runCatching { wifiHelper.getNetworkStateSnapshot().subnetPrefix }
            .getOrNull()
        if (statePrefix != null && statePrefix in MIN_PREFIX..MAX_PREFIX) {
            return SweepTarget(localIp, statePrefix)
        }
        Log.d(TAG, "스윕 대상 프리픽스를 얻지 못함 (interface=$fromInterface, state=$statePrefix)")
        return null
    }

    /** 스윕 실행 가능 여부. UI 버튼 노출 조건. */
    fun isAvailable(): Boolean =
        resolveTarget()?.let { hostsOf(it).isNotEmpty() } ?: false

    /**
     * 서브넷을 훑어 PTP/IP 포트가 열린 호스트를 후보로 만든다.
     *
     * ⚠️ `name`에는 IP를 그대로 담는다. 표시용 라벨을 `name`에 넣으면 Nikon 게이트 입력이 오염된다
     * (표시는 `displayName`/UI 폴백 담당).
     */
    suspend fun sweep(budgetMs: Long = DEFAULT_BUDGET_MS): List<PtpipCamera> =
        withContext(ioDispatcher) {
            val target = resolveTarget()
            if (target == null) {
                Log.i(TAG, "스윕 거부: 서브넷 프리픽스 미확인")
                return@withContext emptyList()
            }
            val hosts = hostsOf(target)
            if (hosts.isEmpty()) {
                Log.i(TAG, "스윕 거부: 대상 호스트 없음 (prefix=/${target.prefixLength})")
                return@withContext emptyList()
            }
            // 호스트 × 포트 전수. 제조사별 광고 프로토콜을 각각 구현하지 않고 **카메라 전용 포트가
            // 열려 있는지**만 보므로, 제조사를 몰라도 한 번의 스윕으로 전부 찾는다.
            val targets = hosts.flatMap { host ->
                CameraProtocol.SWEEP_PORTS.map { port -> host to port }
            }
            Log.i(
                TAG,
                "서브넷 스윕 시작: 호스트 ${hosts.size} × 포트 ${CameraProtocol.SWEEP_PORTS} " +
                    "= ${targets.size}개 (prefix=/${target.prefixLength}, budget=${budgetMs}ms)"
            )
            sweepEndpoints(targets, budgetMs).map { (ip, port) ->
                PtpipCamera(
                    ipAddress = ip,
                    port = port,
                    name = ip,
                    isOnline = true,
                    displayName = null,
                    discoverySource = CameraDiscoverySource.SUBNET_SCAN
                )
            }
        }

    private suspend fun sweepEndpoints(
        endpoints: List<Pair<String, Int>>,
        budgetMs: Long
    ): List<Pair<String, Int>> {
        val found = mutableListOf<Pair<String, Int>>()
        val selector = runCatching { Selector.open() }.getOrElse {
            Log.w(TAG, "Selector 생성 실패 - 스윕 중단: ${it.message}")
            return emptyList()
        }
        val opened = mutableListOf<SocketChannel>()
        try {
            val deadline = SystemClock.elapsedRealtime() + budgetMs
            var next = 0
            var pending = 0
            while (currentCoroutineContext().isActive) {
                val remaining = deadline - SystemClock.elapsedRealtime()
                if (remaining <= 0L) break
                if (next >= endpoints.size && pending == 0) break

                while (pending < MAX_IN_FLIGHT && next < endpoints.size) {
                    val endpoint = endpoints[next++]
                    val channel = runCatching { SocketChannel.open() }.getOrNull() ?: break
                    opened += channel
                    val registered = runCatching {
                        channel.configureBlocking(false)
                        if (channel.connect(InetSocketAddress(endpoint.first, endpoint.second))) {
                            // 즉시 연결(루프백·ARP 캐시) — 프로브 성립, 바이트 전송 없이 닫는다.
                            found += endpoint
                            channel.close()
                            false
                        } else {
                            channel.register(selector, SelectionKey.OP_CONNECT, endpoint)
                            true
                        }
                    }.getOrElse {
                        runCatching { channel.close() }
                        false
                    }
                    if (registered) pending++
                }

                val slice = minOf(SELECT_SLICE_MS, deadline - SystemClock.elapsedRealtime())
                if (slice <= 0L) break
                val ready = runCatching { selector.select(slice) }.getOrElse { 0 }
                if (ready == 0) continue

                val keys = selector.selectedKeys().iterator()
                while (keys.hasNext()) {
                    val key = keys.next()
                    keys.remove()
                    pending--
                    val channel = key.channel() as SocketChannel
                    @Suppress("UNCHECKED_CAST")
                    val endpoint = key.attachment() as? Pair<String, Int>
                    val connected = runCatching { channel.finishConnect() }.getOrDefault(false)
                    if (connected && endpoint != null) {
                        found += endpoint
                        Log.i(
                            TAG,
                            "스윕 발견: ${LogMask.id(endpoint.first)}:${endpoint.second} " +
                                "(${CameraProtocol.ofPort(endpoint.second)})"
                        )
                    }
                    key.cancel()
                    runCatching { channel.close() }
                }
            }
        } finally {
            // 취소돼도 반드시 닫는다 — 고아 소켓은 카메라를 앱 재시작까지 연결 불가로 만든다.
            withContext(NonCancellable) {
                opened.forEach { runCatching { it.close() } }
                runCatching { selector.close() }
            }
        }
        Log.i(TAG, "서브넷 스윕 완료: ${found.size}개 응답")
        return found
    }
}
