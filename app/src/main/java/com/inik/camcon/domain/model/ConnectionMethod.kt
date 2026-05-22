package com.inik.camcon.domain.model

/**
 * Wi-Fi PTP/IP 연결 방식 (사용자가 선택한 시나리오 축).
 *
 * - [AP]: 카메라가 Wi-Fi 핫스팟(AP)을 발생시키고 폰이 클라이언트로 접속.
 * - [STA_ROUTER]: 카메라/폰이 동일한 공유기에 접속한 상태에서 PTP/IP 통신.
 * - [STA_PHONE_HOTSPOT]: 폰이 핫스팟 역할을 하고 카메라가 폰의 핫스팟에 STA로 접속.
 *   네트워크 토폴로지는 STA_ROUTER와 동일하지만 게이트웨이가 폰 자기 자신.
 *
 * [NikonConnectionMode]와 직교축이다 — Nikon enum은 "어떤 네트워크 토폴로지인가"를
 * 표현하고 [ConnectionMethod]는 "사용자가 어떤 시나리오를 선택했는가"를 표현한다.
 */
enum class ConnectionMethod {
    AP,
    STA_ROUTER,
    STA_PHONE_HOTSPOT;

    val isSta: Boolean get() = this == STA_ROUTER || this == STA_PHONE_HOTSPOT
}

/**
 * [ConnectionMethod]를 [NikonConnectionMode]로 매핑한다.
 *
 * STA_PHONE_HOTSPOT도 토폴로지상 STA이므로 pairing-code 인증 흐름은 STA_MODE와 동일하다.
 */
fun ConnectionMethod.toNikonConnectionMode(): NikonConnectionMode = when (this) {
    ConnectionMethod.AP -> NikonConnectionMode.AP_MODE
    ConnectionMethod.STA_ROUTER,
    ConnectionMethod.STA_PHONE_HOTSPOT -> NikonConnectionMode.STA_MODE
}

/**
 * 기존 `connectToCamera(camera, forceApMode: Boolean)` 시그니처와의 매핑.
 * AP만 forceApMode=true, 나머지는 false.
 */
fun ConnectionMethod.toForceApMode(): Boolean = this == ConnectionMethod.AP
