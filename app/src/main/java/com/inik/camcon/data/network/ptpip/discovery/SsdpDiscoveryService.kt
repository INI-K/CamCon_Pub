package com.inik.camcon.data.network.ptpip.discovery

import android.util.Log
import com.inik.camcon.di.IoDispatcher
import com.inik.camcon.domain.model.CameraDiscoverySource
import com.inik.camcon.domain.model.CameraVendor
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.utils.LogMask
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.net.Socket
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SSDP(UPnP M-SEARCH) 기반 카메라 발견 서비스.
 *
 * mDNS에 광고하지 않는 제조사(Canon SSDP/UPnP, Sony 구형 SSDP, Panasonic UPnP)를
 * 연결 전에 판별하기 위한 경로다. 239.255.255.250:1900으로 ST별 M-SEARCH를 1회씩 송신하고,
 * 같은 소켓으로 유니캐스트 응답만 수집한다(멀티캐스트 수신 아님 → MulticastLock 불필요).
 *
 * 판별은 [CameraVendorClassifier.classifySsdp] 단일 지점을 사용하며, UNKNOWN 응답은
 * 공유기/TV 등 잡음이므로 버린다. 설계 근거:
 * docs/superpowers/specs/2026-07-06-multivendor-camera-discovery-design.md §2.4
 */
@Singleton
class SsdpDiscoveryService @Inject constructor(
    // 기본값 금지: @Inject 생성자에 default 인자가 있으면 Kotlin이 생성자를 2개 만들어
    // Hilt가 "only one injected constructor"로 거부한다. @IoDispatcher 바인딩으로 주입.
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    /**
     * 지정 로컬 주소에 바인딩한 UDP 소켓. 주소가 없거나 바인딩이 실패하면 와일드카드로 폴백한다
     * (바인딩 실패로 검색 자체를 죽이지는 않는다).
     */
    private fun createBoundSocket(bindAddress: String?): DatagramSocket {
        val local = bindAddress?.takeIf { it.isNotBlank() } ?: return DatagramSocket()
        return runCatching {
            DatagramSocket(InetSocketAddress(InetAddress.getByName(local), 0)).also {
                Log.d(TAG, "SSDP 소켓 바인딩: ${LogMask.id(local)}")
            }
        }.getOrElse {
            Log.w(TAG, "SSDP 소켓 바인딩 실패 - 와일드카드 폴백: ${it.message}")
            DatagramSocket()
        }
    }

    companion object {
        private const val TAG = "SsdpDiscoveryService"

        private const val SSDP_ADDRESS = "239.255.255.250"
        private const val SSDP_PORT = 1900

        /** PTP/IP 표준 포트 — SSDP 응답은 제어 포트를 알려주지 않으므로 고정 사용 */
        private const val PTPIP_PORT = 15740

        private const val RECV_BUFFER_SIZE = 2048

        /** UPnP 기기 설명 XML 조회 타임아웃. 발견 경로라 짧게 잡는다. */
        private const val DESC_TIMEOUT_MS = 1500

        /** 설명 XML 읽기 상한(바이트). 실측 응답은 1KB 남짓이다. */
        private const val DESC_MAX_BYTES = 16 * 1024

        /** `<friendlyName>ILCE-7C</friendlyName>` 에서 모델명만 뽑는다. */
        private val FRIENDLY_NAME_REGEX =
            Regex("<friendlyName>(.*?)</friendlyName>", RegexOption.IGNORE_CASE)

        /**
         * 타깃 ST 6종. UPnP 규격상 기기는 M-SEARCH로 물어본 ST만 에코하므로,
         * [CameraVendorClassifier.classifySsdp]가 아는 URN은 전부 여기서 직접 검색해야
         * 도달 가능하다(rootdevice 응답에는 서비스 URN이 실리지 않음).
         * - Canon 스마트폰 모드 / EOS Utility(WFT) / CCAPI(신형 R시리즈)
         * - Sony 구형 ScalarWebAPI / MtpNullService(A7s 실측)
         * - upnp:rootdevice (범용 — 공유기/TV도 응답하므로 vendor UNKNOWN 필터 필수)
         */
        internal val SEARCH_TARGETS = listOf(
            "urn:schemas-canon-com:service:ICPO-SmartPhoneEOSSystemService:1",
            "urn:schemas-canon-com:service:ICPO-WFTEOSSystemService:1",
            "urn:schemas-canon-com:service:ICPO-CameraControlAPIService:1",
            "urn:schemas-sony-com:service:ScalarWebAPI:1",
            "urn:microsoft-com:service:MtpNullService:1",
            "upnp:rootdevice",
            // 기기가 광고하는 **모든** ST 를 되돌려받는 와일드카드(UPnP 1.1 §1.3.2).
            // 소니 SDK 문서가 UDP 1900 을 사용 포트로 명시하는데(방화벽 목록: TCP 80/8080/22/
            // 64321/15740, UDP 1900) 위 URN 5종으로는 실기 응답이 0건이었다 → 우리가 모르는
            // ST 로 광고 중일 가능성이 크다. 정체를 알아야 이름 기반 발견이 가능해진다.
            // 잡음(공유기·TV)은 UNKNOWN 필터가 후보에서 걸러내고 덤프 로그에만 남는다.
            "ssdp:all"
        )

        /**
         * SSDP 응답/알림 텍스트를 헤더 맵으로 파싱한다(순수 함수 — 테스트 대상).
         *
         * - 첫 줄(status line: `HTTP/1.1 200 OK` 또는 `NOTIFY * HTTP/1.1`)은 건너뛴다.
         * - 헤더명은 대문자로 정규화, `:` 첫 등장 기준으로 name/value 분리, 값은 trim.
         * - CRLF/LF 혼용 허용, 빈 줄·콜론 없는 줄은 무시.
         */
        internal fun parseSsdpHeaders(text: String): Map<String, String> {
            val headers = LinkedHashMap<String, String>()
            val lines = text.split("\r\n", "\n")
            for ((index, raw) in lines.withIndex()) {
                val line = raw.trimEnd('\r')
                if (index == 0) continue // status/request line
                if (line.isBlank()) continue
                val colon = line.indexOf(':')
                if (colon <= 0) continue
                val name = line.substring(0, colon).trim().uppercase()
                val value = line.substring(colon + 1).trim()
                headers[name] = value
            }
            return headers
        }
    }

    /**
     * SSDP로 카메라를 검색한다.
     *
     * @param timeoutMs 응답 수신 대기 시간(ms). 이 시간 동안 유니캐스트 응답을 수집한다.
     * @return 판별된(vendor != UNKNOWN) 카메라 목록. 같은 IP는 confidenceRank 높은 하나만.
     */
    suspend fun discover(
        timeoutMs: Long = 3000L,
        bindAddress: String? = null
    ): List<PtpipCamera> =
        withContext(ioDispatcher) {
            // IP -> 현재까지 채택된 카메라 (더 높은 confidence 응답이 오면 교체)
            val byIp = LinkedHashMap<String, PtpipCamera>()
            // IP -> UPnP 기기 설명 XML URL(SSDP LOCATION 헤더). 기종명 조회에 쓴다.
            val locationByIp = HashMap<String, String>()
            try {
                // ⚠️ 무바인딩 `DatagramSocket()`은 와일드카드라 OS가 **기본 경로**로 송신한다.
                // 핫스팟만 켜고 셀룰러가 default route면 239.255.255.250이 셀룰러로 나가고
                // 카메라가 있는 softAP 세그먼트에는 M-SEARCH가 도달하지 않는다 →
                // "카메라는 제대로 설정했는데 앱이 못 잡음"의 확정 원인.
                // softAP 로컬 IP에 명시 바인딩해 반드시 그 인터페이스로 나가게 한다.
                createBoundSocket(bindAddress).use { socket ->
                    val group = InetAddress.getByName(SSDP_ADDRESS)
                    for (st in SEARCH_TARGETS) {
                        val payload = buildMSearch(st).toByteArray(Charsets.US_ASCII)
                        try {
                            socket.send(
                                DatagramPacket(
                                    payload,
                                    payload.size,
                                    InetSocketAddress(group, SSDP_PORT)
                                )
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "M-SEARCH 송신 실패: st=$st - ${e.message}")
                        }
                    }

                    // 같은 소켓으로 timeoutMs 동안 유니캐스트 응답 수신
                    val deadline = System.currentTimeMillis() + timeoutMs
                    val buffer = ByteArray(RECV_BUFFER_SIZE)
                    while (true) {
                        val remaining = deadline - System.currentTimeMillis()
                        if (remaining <= 0) break
                        socket.soTimeout = remaining.toInt().coerceAtLeast(1)
                        val packet = DatagramPacket(buffer, buffer.size)
                        try {
                            socket.receive(packet)
                        } catch (e: SocketTimeoutException) {
                            break
                        }
                        val sourceIp = packet.address?.hostAddress ?: continue
                        val text = String(packet.data, 0, packet.length, Charsets.US_ASCII)
                        handleResponse(sourceIp, text, byIp, locationByIp)
                    }
                }
            } catch (e: Exception) {
                // 소켓 생성/치명적 오류: 그 시점까지 수집분을 반환한다(빈 리스트 강제 반환 금지).
                Log.w(TAG, "SSDP 검색 중 오류 - ${e.message}")
            }

            // 기종명 보강. SERVER 헤더는 전 기종 공통 문자열이라("UPnP/1.0 SonyImagingDevice/1.0")
            // 어느 카메라인지 구분되지 않는다. UPnP 기기 설명 XML 의 friendlyName 이 실제 모델명이다
            // (실측 2026-08-20: ILCE-7C). 판별된 후보에만 요청하므로 보통 1~2회에 그친다.
            byIp.entries.map { (ip, camera) ->
                val model = locationByIp[ip]?.let { fetchFriendlyName(it) } ?: return@map camera
                Log.i(TAG, "UPnP 기종명 조회: ${LogMask.id(ip)} -> $model")
                camera.copy(name = model, displayName = model)
            }
        }

    /**
     * UPnP 기기 설명 XML 에서 `<friendlyName>` 을 읽는다. 실패하면 null(호출자는 기존 라벨 유지).
     *
     * 같은 LAN 의 발견된 기기에만, 짧은 타임아웃으로, 응답 앞부분만 읽는다. 판별이 끝난 카메라
     * 후보에만 호출하므로 공유기·TV 로는 나가지 않는다.
     */
    private fun fetchFriendlyName(location: String): String? = runCatching {
        val uri = URI(location)
        val host = uri.host ?: return@runCatching null
        val port = if (uri.port > 0) uri.port else 80
        val path = uri.rawPath?.takeIf { it.isNotEmpty() }?.let {
            if (uri.rawQuery != null) "$it?${uri.rawQuery}" else it
        } ?: "/"

        // ⚠️ HttpURLConnection 을 쓰면 안 된다. 플랫폼 HTTP 스택은 네트워크 보안 정책을 타는데
        // 카메라 주소는 DHCP 라 평문 허용 목록에 미리 못 넣는다(실측: "Cleartext HTTP traffic to
        // 192.168.137.9 not permitted"). 정책을 넓히면 앱 전체의 평문 금지가 느슨해지므로,
        // PTP/IP 와 마찬가지로 원시 소켓으로 최소한의 GET 만 보낸다.
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), DESC_TIMEOUT_MS)
            socket.soTimeout = DESC_TIMEOUT_MS
            val request = "GET $path HTTP/1.0\r\nHost: $host:$port\r\nConnection: close\r\n\r\n"
            socket.getOutputStream().apply {
                write(request.toByteArray(Charsets.US_ASCII))
                flush()
            }
            // 설명 XML 은 1KB 남짓이다. 상한을 두어 비정상적으로 큰 응답을 통째로 읽지 않는다.
            val buffer = ByteArray(DESC_MAX_BYTES)
            var total = 0
            val input = socket.getInputStream()
            while (total < buffer.size) {
                val n = input.read(buffer, total, buffer.size - total)
                if (n <= 0) break
                total += n
            }
            val body = String(buffer, 0, total, Charsets.UTF_8)
            FRIENDLY_NAME_REGEX.find(body)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
        }
    }.getOrElse {
        Log.d(TAG, "UPnP 설명 XML 조회 실패 - ${it.message}")
        null
    }

    /** 단일 응답을 파싱·판별해 byIp에 병합한다(confidence 높은 쪽 유지). */
    private fun handleResponse(
        sourceIp: String,
        text: String,
        byIp: MutableMap<String, PtpipCamera>,
        locationByIp: MutableMap<String, String>
    ) {
        val headers = parseSsdpHeaders(text)
        val st = headers["ST"] ?: headers["NT"]
        val usn = headers["USN"]
        val server = headers["SERVER"]

        // 같은 카메라가 ST 별로 여러 번 답하므로 LOCATION 은 처음 것만 기억한다.
        headers["LOCATION"]?.takeIf { it.isNotBlank() }
            ?.let { locationByIp.putIfAbsent(sourceIp, it) }

        // ⚠️ 덤프는 **분류보다 먼저** 남긴다. 예전엔 UNKNOWN 필터 뒤에 있어서 우리가 아는 URN 만
        // 기록됐고, 그 결과 "응답이 0건"인지 "응답은 왔는데 못 알아본 것"인지 구분할 수 없었다.
        // 제조사별 실광고 실태를 모으는 것이 이 로그의 목적이므로 mDNS 쪽(VENDOR_MDNS_DUMP)과
        // 동일하게 전부 남긴다. ST/USN/SERVER 는 PII 가 아니고 IP 만 마스킹한다.
        Log.i(
            TAG,
            "VENDOR_SSDP_DUMP ip=${LogMask.id(sourceIp)} st=$st usn=$usn server=$server"
        )

        val verdict = CameraVendorClassifier.classifySsdp(st, usn, server)
        if (verdict.vendor == CameraVendor.UNKNOWN) {
            // upnp:rootdevice / ssdp:all 응답의 공유기·TV 잡음 — 후보로는 올리지 않는다.
            return
        }

        val name = server ?: summarizeSt(st)
        val camera = PtpipCamera(
            ipAddress = sourceIp,
            port = PTPIP_PORT,
            name = name,
            isOnline = true,
            discoveredServiceType = null,
            vendorVerdict = verdict,
            // SSDP verdict는 이미 CANON/SONY/PANASONIC로 채워지므로 Nikon 게이트에 영향 없음.
            displayName = name,
            discoverySource = CameraDiscoverySource.SSDP
        )

        val existing = byIp[sourceIp]
        if (existing == null ||
            CameraVendorClassifier.confidenceRank(verdict) >
            CameraVendorClassifier.confidenceRank(existing.vendorVerdict)
        ) {
            byIp[sourceIp] = camera
            Log.i(
                TAG,
                "카메라 발견(SSDP): ${verdict.vendor}/${verdict.confidence} " +
                    "(${LogMask.id(sourceIp)}:$PTPIP_PORT)"
            )
        }
    }

    private fun buildMSearch(st: String): String =
        "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "MX: 1\r\n" +
            "ST: $st\r\n" +
            "\r\n"

    /** SERVER 헤더가 없을 때 이름 대체: ST의 URN 요약. */
    private fun summarizeSt(st: String?): String {
        if (st.isNullOrBlank()) return "SSDP Camera"
        // urn:schemas-canon-com:service:ICPO-...:1 → 마지막 유의미 세그먼트
        val service = st.substringAfterLast(":service:", "").substringBeforeLast(":")
        return if (service.isNotBlank()) service else st
    }
}
