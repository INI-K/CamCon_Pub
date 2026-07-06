package com.inik.camcon.data.network.ptpip.discovery

import android.util.Log
import com.inik.camcon.di.IoDispatcher
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
    companion object {
        private const val TAG = "SsdpDiscoveryService"

        private const val SSDP_ADDRESS = "239.255.255.250"
        private const val SSDP_PORT = 1900

        /** PTP/IP 표준 포트 — SSDP 응답은 제어 포트를 알려주지 않으므로 고정 사용 */
        private const val PTPIP_PORT = 15740

        private const val RECV_BUFFER_SIZE = 2048

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
            "upnp:rootdevice"
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
    suspend fun discover(timeoutMs: Long = 3000L): List<PtpipCamera> =
        withContext(ioDispatcher) {
            // IP -> 현재까지 채택된 카메라 (더 높은 confidence 응답이 오면 교체)
            val byIp = LinkedHashMap<String, PtpipCamera>()
            try {
                DatagramSocket().use { socket ->
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
                        handleResponse(sourceIp, text, byIp)
                    }
                }
            } catch (e: Exception) {
                // 소켓 생성/치명적 오류: 그 시점까지 수집분을 반환한다(빈 리스트 강제 반환 금지).
                Log.w(TAG, "SSDP 검색 중 오류 - ${e.message}")
            }

            byIp.values.toList()
        }

    /** 단일 응답을 파싱·판별해 byIp에 병합한다(confidence 높은 쪽 유지). */
    private fun handleResponse(
        sourceIp: String,
        text: String,
        byIp: MutableMap<String, PtpipCamera>
    ) {
        val headers = parseSsdpHeaders(text)
        val st = headers["ST"] ?: headers["NT"]
        val usn = headers["USN"]
        val server = headers["SERVER"]

        val verdict = CameraVendorClassifier.classifySsdp(st, usn, server)
        if (verdict.vendor == CameraVendor.UNKNOWN) {
            // upnp:rootdevice 응답의 공유기/TV 잡음 — 반드시 버린다.
            return
        }

        // 실측 확보용: ST/USN/SERVER는 PII 아님, IP만 마스킹.
        Log.i(
            TAG,
            "VENDOR_SSDP_DUMP ip=${LogMask.id(sourceIp)} st=$st usn=$usn server=$server"
        )

        val name = server ?: summarizeSt(st)
        val camera = PtpipCamera(
            ipAddress = sourceIp,
            port = PTPIP_PORT,
            name = name,
            isOnline = true,
            discoveredServiceType = null,
            vendorVerdict = verdict
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
