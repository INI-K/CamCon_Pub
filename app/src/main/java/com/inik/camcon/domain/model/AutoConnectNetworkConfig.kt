package com.inik.camcon.domain.model

/**
 * 자동 Wi-Fi 연결을 위해 저장되는 카메라 네트워크 설정 정보
 */
data class AutoConnectNetworkConfig(
    val ssid: String,
    val passphrase: String?,
    val securityType: String?,
    val isHidden: Boolean,
    val bssid: String? = null,
    val lastUpdatedEpochMillis: Long = System.currentTimeMillis()
)
