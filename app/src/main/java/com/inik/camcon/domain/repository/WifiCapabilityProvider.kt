package com.inik.camcon.domain.repository

import com.inik.camcon.domain.model.WifiScanPermissionStatus

/**
 * Wi-Fi 스캔 권한/전제조건 조회를 위한 도메인 추상화.
 *
 * Presentation 계층이 data 구현체(WifiNetworkHelper)에 직접 의존하지 않고
 * 권한 상태를 조회할 수 있도록 framework-독립 메서드만 노출한다.
 * android.content.Intent / GMS Task 등 framework 타입을 반환하는 메서드는
 * 도메인 경계를 깨므로 포함하지 않으며, 그러한 처리는 UI 계층이
 * Context/LocationServices로 직접 수행한다.
 */
interface WifiCapabilityProvider {
    /** Wi-Fi 스캔 권한 및 전제조건을 종합 분석한 상태를 반환. */
    fun analyzeWifiScanPermissionStatus(): WifiScanPermissionStatus

    /** Wi-Fi 스캔에 필요한 권한 목록(Android 버전 반영)을 반환. */
    fun getRequiredWifiScanPermissions(): List<String>

    /** 권한 요청 사유를 설명하는 사용자 안내 메시지를 반환. */
    fun getPermissionRationaleMessage(): String
}
