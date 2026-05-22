package com.inik.camcon.data.network.ptpip.discovery

import com.inik.camcon.domain.model.ConnectionMethod
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.WifiNetworkState

/**
 * 폰 핫스팟 STA 모드에서 mDNS가 광고를 받지 못할 때 사용자 수동 IP 입력으로
 * 폴백할지 결정하는 순수 함수.
 *
 * - mDNS가 카메라를 발견하면 항상 mDNS 결과를 우선한다.
 * - mDNS가 비었고 사용자가 STA_PHONE_HOTSPOT 탭에서 IP를 입력했다면 수동 폴백.
 * - 그 외 (STA_ROUTER 등)에서는 수동 폴백을 적용하지 않는다.
 */
object HotspotDiscoveryFallback {

    sealed class Decision {
        data class UseMdns(val cameras: List<PtpipCamera>) : Decision()
        data class UseManual(val ip: String) : Decision()
        object NoCandidate : Decision()
    }

    /**
     * @param wifiState 현재 시그니처에서는 사용하지 않으나 향후 게이트웨이 기반 폴백 확장 시
     *                  callers 변경 없이 활용 가능하도록 파라미터 유지.
     */
    @Suppress("UNUSED_PARAMETER")
    fun decide(
        method: ConnectionMethod,
        mdnsResults: List<PtpipCamera>,
        wifiState: WifiNetworkState,
        userIp: String,
    ): Decision {
        if (mdnsResults.isNotEmpty()) return Decision.UseMdns(mdnsResults)
        val trimmed = userIp.trim()
        if (method == ConnectionMethod.STA_PHONE_HOTSPOT && trimmed.isNotBlank()) {
            return Decision.UseManual(trimmed)
        }
        return Decision.NoCandidate
    }
}
