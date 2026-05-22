package com.inik.camcon.presentation.ui.screens.components

import com.inik.camcon.domain.model.WifiNetworkState

/**
 * 폰 핫스팟 STA 모드 화면에서 보여줄 상태 요약.
 *
 * Compose 외부에서 사용 가능한 순수 데이터 모델이며, [fromWifiState]를 통해
 * `WifiNetworkState`로부터 결정된다. UI는 본 객체를 읽어 라벨/버튼 활성을 표현한다.
 */
data class HotspotStaContentState(
    val status: HotspotStatus,
    val ssidLabel: String?,
    val gatewayLabel: String?,
    val canDiscover: Boolean,
) {
    enum class HotspotStatus { ENABLED, DISABLED }

    companion object {
        fun fromWifiState(wifi: WifiNetworkState): HotspotStaContentState {
            val enabled = wifi.isHotspotEnabled
            return HotspotStaContentState(
                status = if (enabled) HotspotStatus.ENABLED else HotspotStatus.DISABLED,
                ssidLabel = if (enabled) wifi.ssid else null,
                gatewayLabel = if (enabled) wifi.gatewayIp else null,
                canDiscover = enabled,
            )
        }
    }
}
