package com.inik.camcon.data.network.ptpip.discovery

import com.inik.camcon.domain.model.CameraVendor
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.VendorConfidence
import com.inik.camcon.domain.model.VendorVerdict

/**
 * 카메라 제조사 판별 단일 지점.
 *
 * 조사 근거(2026-07-06, docs/superpowers/specs/2026-07-06-multivendor-camera-discovery-design.md):
 * - mDNS(_ptp._tcp)로 광고하는 제조사는 사실상 니콘뿐. Canon/Sony(구형)/Panasonic은 SSDP/UPnP.
 * - 니콘 연결 전 확정 신호는 `_nikon._tcp` 서비스 타입(있으면 확정, 없어도 배제 불가)과
 *   서비스 이름 패턴(`Nikon_*`, `Z_6_5000784`, `D_850_*`)뿐. TXT 레코드는 실측 근거 없음.
 * - UNKNOWN은 "해당 제조사 아님"이 아니다 — 기기명을 바꾼 니콘은 이름 신호가 사라지므로
 *   연결 후 PTP DeviceInfo(manufacturer)로만 확정 가능하다.
 */
object CameraVendorClassifier {

    /** 니콘 mDNS 서비스 이름 프리픽스: Z_6_5000784 / D_850_... (실기 관측 패턴) */
    private val NIKON_NAME_PREFIX = Regex("^[ZD]_", RegexOption.IGNORE_CASE)

    /**
     * mDNS 발견 신호로 제조사 판별 (연결 전).
     */
    fun classifyMdns(serviceName: String, serviceType: String?): VendorVerdict {
        // 니콘 전용 서비스 타입 — 있으면 확정. 없다고 니콘을 배제하지는 않는다.
        if (serviceType?.contains("_nikon._tcp") == true) {
            return VendorVerdict(CameraVendor.NIKON, VendorConfidence.CONFIRMED)
        }
        if (serviceName.contains("nikon", ignoreCase = true)) {
            return VendorVerdict(CameraVendor.NIKON, VendorConfidence.CONFIRMED)
        }
        if (NIKON_NAME_PREFIX.containsMatchIn(serviceName)) {
            return VendorVerdict(CameraVendor.NIKON, VendorConfidence.LIKELY)
        }
        return VendorVerdict.unknown()
    }

    /**
     * SSDP(M-SEARCH 응답) 헤더로 제조사 판별 (연결 전).
     *
     * 실측 URN:
     * - Canon: urn:schemas-canon-com:service:ICPO-SmartPhoneEOSSystemService:1 (스마트폰 모드),
     *   ICPO-WFTEOSSystemService:1 (EOS Utility/WFT), ICPO-CameraControlAPIService:1 (CCAPI)
     * - Sony 구형: urn:schemas-sony-com:service:ScalarWebAPI:1
     * - Sony(A7s 실측): urn:microsoft-com:service:MtpNullService:1 — 범용 MTP 가능성이 있어 LIKELY
     */
    fun classifySsdp(st: String?, usn: String?, server: String?): VendorVerdict {
        val urn = "${st.orEmpty()} ${usn.orEmpty()}"
        if (urn.contains("schemas-canon-com", ignoreCase = true)) {
            return VendorVerdict(CameraVendor.CANON, VendorConfidence.CONFIRMED)
        }
        if (urn.contains("schemas-sony-com:service:ScalarWebAPI", ignoreCase = true)) {
            return VendorVerdict(CameraVendor.SONY, VendorConfidence.CONFIRMED)
        }
        if (urn.contains("microsoft-com:service:MtpNullService", ignoreCase = true)) {
            return VendorVerdict(CameraVendor.SONY, VendorConfidence.LIKELY)
        }
        if (server?.contains("panasonic", ignoreCase = true) == true ||
            usn?.contains("panasonic", ignoreCase = true) == true
        ) {
            return VendorVerdict(CameraVendor.PANASONIC, VendorConfidence.LIKELY)
        }
        return VendorVerdict.unknown()
    }

    /**
     * 연결 후 PTP DeviceInfo manufacturer 문자열로 제조사 확정 — 유일한 100% 신호.
     * (예: "Nikon Corporation", "Canon Inc.")
     */
    fun confirmFromDeviceInfo(manufacturer: String?): CameraVendor {
        val m = manufacturer ?: return CameraVendor.UNKNOWN
        return when {
            m.contains("nikon", ignoreCase = true) -> CameraVendor.NIKON
            m.contains("canon", ignoreCase = true) -> CameraVendor.CANON
            m.contains("sony", ignoreCase = true) -> CameraVendor.SONY
            m.contains("fuji", ignoreCase = true) -> CameraVendor.FUJIFILM
            m.contains("panasonic", ignoreCase = true) -> CameraVendor.PANASONIC
            else -> CameraVendor.UNKNOWN
        }
    }

    /**
     * 니콘 전용 연결 경로(STA 페어링 GUID 주입 + 승인 인증) 진입 여부.
     * CONFIRMED/LIKELY 모두 니콘 경로를 탄다 — 오판 시 첫 페어링이 InitFail 0x1로
     * 파손되므로(STA 인증 미수행) 놓치는 쪽이 잘못 태우는 쪽보다 비용이 크다.
     */
    fun isLikelyNikon(camera: PtpipCamera): Boolean {
        if (camera.vendorVerdict.vendor == CameraVendor.NIKON) return true
        // 캐시/수동 IP 등 verdict 미산정 경로 폴백: 이름·타입으로 재판별
        return classifyMdns(camera.name, camera.discoveredServiceType).vendor == CameraVendor.NIKON
    }

    /**
     * 같은 IP:Port 중복 제거 시 어느 발견 항목을 남길지 결정하는 우선순위.
     * 제조사 특정 신호(_nikon._tcp 등)가 표준 _ptp._tcp에 밀려 유실되지 않도록 한다.
     */
    fun confidenceRank(verdict: VendorVerdict): Int = when (verdict.confidence) {
        VendorConfidence.CONFIRMED -> 2
        VendorConfidence.LIKELY -> 1
        VendorConfidence.UNKNOWN -> 0
    }
}
