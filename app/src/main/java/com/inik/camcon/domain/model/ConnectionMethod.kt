package com.inik.camcon.domain.model

/**
 * Wi-Fi PTP/IP 연결 방식.
 *
 * - [AP]: 카메라가 Wi-Fi 핫스팟(AP)을 발생시키고 폰이 클라이언트로 접속.
 * - [STA_ROUTER]: 카메라/폰이 동일한 공유기에 접속한 상태에서 PTP/IP 통신.
 * - [STA_PHONE_HOTSPOT]: 폰이 핫스팟 역할을 하고 카메라가 폰의 핫스팟에 STA로 접속.
 *   (네트워크 토폴로지상 STA_ROUTER와 동일하지만 게이트웨이가 폰 자기 자신.)
 */
enum class ConnectionMethod {
    AP,
    STA_ROUTER,
    STA_PHONE_HOTSPOT;

    val isSta: Boolean get() = this == STA_ROUTER || this == STA_PHONE_HOTSPOT
}
