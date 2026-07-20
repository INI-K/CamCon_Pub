package com.inik.camcon.domain.model

/**
 * 익명 카메라 연결 검증 집계에 보고할 연결 방식.
 * [wire]는 CF/서버가 기대하는 소문자 문자열 표현이다.
 */
enum class ConnectionReportMethod(val wire: String) {
    USB("usb"),
    WIFI("wifi")
}
