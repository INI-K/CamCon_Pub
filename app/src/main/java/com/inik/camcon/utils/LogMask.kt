package com.inik.camcon.utils

import java.io.File

/**
 * 로그에 식별자·경로·네트워크 정보의 평문이 흘러가지 않도록 마스킹한다.
 *
 * release 빌드에서는 ProGuard 가 android.util.Log 호출을 제거하지만, DEBUG 빌드 logcat·
 * 버그리포트·화면공유 경로로의 PII 유출을 막기 위해 사용한다. 기존 maskToken/maskId/maskUserId
 * 와 동일한 규약(앞 일부만 노출 + 마스킹)을 따른다.
 */
object LogMask {

    private const val BLANK = "<blank>"

    /** 카메라 시리얼번호: 앞 2 + 뒤 2 만 노출 */
    fun serial(value: String?): String {
        if (value.isNullOrBlank()) return BLANK
        val v = value.trim()
        return if (v.length <= 4) "****" else "${v.take(2)}***${v.takeLast(2)}"
    }

    /** Wi-Fi SSID: 앞 3자 + 길이만 노출 */
    fun ssid(value: String?): String {
        if (value.isNullOrBlank()) return BLANK
        val v = value.trim().removeSurrounding("\"")
        return if (v.length <= 3) "***" else "${v.take(3)}***(${v.length}자)"
    }

    /** BSSID(AP MAC): OUI(앞 8자)만 노출 */
    fun bssid(value: String?): String {
        if (value.isNullOrBlank()) return BLANK
        val v = value.trim()
        return if (v.length <= 8) "**:**:**" else "${v.take(8)}:**:**:**"
    }

    /** 파일 절대경로: 파일명만 노출 */
    fun path(value: String?): String {
        if (value.isNullOrBlank()) return BLANK
        return File(value).name
    }

    /** PTP/IP GUID 등 안정 식별자: 앞 4 + 길이만 노출 */
    fun guid(value: String?): String {
        if (value.isNullOrBlank()) return BLANK
        val v = value.trim()
        return if (v.length <= 4) "****" else "${v.take(4)}****(${v.length}자)"
    }

    /** 일반 식별자(uid 등): 앞 4 + 마스킹 */
    fun id(value: String?): String {
        if (value.isNullOrBlank()) return BLANK
        return if (value.length <= 4) "***" else value.take(4) + "***"
    }
}
