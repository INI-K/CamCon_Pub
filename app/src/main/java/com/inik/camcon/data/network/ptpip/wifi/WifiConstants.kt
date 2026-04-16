package com.inik.camcon.data.network.ptpip.wifi

/**
 * Wi-Fi 네트워크 관련 상수 정의
 */
object WifiConstants {
    // 브로드캐스트 액션
    const val ACTION_AUTO_CONNECT_TRIGGER = "com.inik.camcon.action.AUTO_CONNECT_TRIGGER"
    const val ACTION_AUTO_CONNECT_SUCCESS = "com.inik.camcon.action.AUTO_CONNECT_SUCCESS"

    // 브로드캐스트 엑스트라
    const val EXTRA_AUTO_CONNECT_SSID = "extra_auto_connect_ssid"
    const val EXTRA_CAMERA_IP = "extra_camera_ip"

    // AP모드에서 일반적으로 사용되는 카메라 IP 주소들
    val COMMON_CAMERA_AP_IPS = listOf(
        "192.168.1.1",
        "192.168.0.1",
        "192.168.10.1",
        "192.168.100.1",
        "10.0.0.1",
        "172.16.0.1"
    )

    // AP모드 감지를 위한 카메라 브랜드 SSID 패턴들
    val CAMERA_AP_PATTERNS = listOf(
        "CANON",
        "NIKON",
        "SONY",
        "FUJIFILM",
        "OLYMPUS",
        "PANASONIC",
        "PENTAX",
        "LEICA"
    )

    // 카메라 관련 네트워크 SSID 패턴 (스캔 결과 필터링용)
    val CAMERA_NETWORK_PATTERNS = listOf(
        // 주요 카메라 브랜드
        "CANON", "Canon",
        "NIKON", "Nikon",
        "SONY", "Sony",
        "FUJIFILM", "Fujifilm", "FUJI",
        "OLYMPUS", "Olympus",
        "PANASONIC", "Panasonic", "Lumix", "LUMIX",
        "PENTAX", "Pentax", "RICOH", "Ricoh",
        "LEICA", "Leica",
        "HASSELBLAD", "Hasselblad",

        // 액션카메라 및 드론
        "GoPro", "GOPRO", "Hero",
        "DJI", "Dji", "Mavic", "Phantom", "Inspire", "Mini",
        "Insta360", "INSTA360",

        // Canon 모델명
        "EOS", "PowerShot", "IXUS", "VIXIA",

        // Nikon 모델명
        "COOLPIX", "Z_5", "Z_6", "Z_7", "Z8", "Z9", "Z30", "Z50", "Zfc",
        "D3500", "D5600", "D7500", "D780", "D850", "D500",

        // Sony 모델명
        "Alpha", "FX30", "FX3", "A7R", "A7S", "A7C", "A7IV", "A9",
        "RX100", "RX10", "ZV-1", "ZV-E10",

        // Fujifilm 모델명
        "X-T4", "X-T5", "X-T30", "X-T50", "X-PRO3", "X-E4", "X-S10", "X-S20",
        "X-A7", "X-H1", "X-H2", "GFX50", "GFX100",

        // Olympus 모델명
        "OM-D", "E-M1", "E-M5", "E-M10", "PEN", "E-PL", "E-P7",

        // Panasonic 모델명
        "GH5", "GH6", "GX9", "G9", "G100", "FZ1000", "LX100",

        // 기타 카메라/영상 장비
        "GODOX", "Godox", "Flashpoint",
        "Osmo", "OSMO", "Pocket", "Action", "Mobile",
        "SIGMA", "fp", "TAMRON"
    )
}

/**
 * Wi-Fi Suggestion 등록 결과
 */
data class WifiSuggestionResult(
    val success: Boolean,
    val message: String
)
