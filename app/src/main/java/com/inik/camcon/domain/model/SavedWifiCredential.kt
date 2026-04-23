package com.inik.camcon.domain.model

/**
 * Wi-Fi AP 모드에서 이전에 연결한 카메라의 Wi-Fi 인증 정보
 */
data class SavedWifiCredential(
    val ssid: String,
    val passphrase: String,
    val security: String,
    val bssid: String?,
    val lastConnectedAt: Long
)
