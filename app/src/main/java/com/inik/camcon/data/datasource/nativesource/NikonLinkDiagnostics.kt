package com.inik.camcon.data.datasource.nativesource

import android.util.Log
import com.inik.camcon.CameraNative
import com.inik.camcon.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 연결 직후 1회, 카메라가 스스로 보고하는 **링크 사실**을 로그로 남긴다.
 *
 * 전송 속도 문제를 진단할 때 지금까지는 TCPINFO(무선)와 체감 속도만 있었고,
 * "USB2 케이블이라 느린 건지 카메라/케이블 문제인지"를 가릴 근거가 없었다.
 * 니콘은 이 두 가지를 프로퍼티로 직접 노출한다(전 기종 공통, 세대 드리프트 없음):
 *
 *  - `USBSpeed` 0xD10C (§6.5.15.1) — Get 전용, 1=High-Speed(USB2), 2=Super-Speed(USB3)
 *  - `ConnectionPath` 0xD12E (§6.5.15.2) — Get 전용, 0=USB, 2=내장 Wi-Fi, 3=유선 LAN
 *    (구세대 문서는 1=Wireless Transmitter 도 정의)
 *
 * libgphoto2 는 명명 위젯이 없는 벤더 프로퍼티를 "other" 섹션에 **소문자 4자리 hex 이름**으로
 * 노출하므로(config.c 의 `sprintf(buf,"%04x", propid)`) 별도 패치 없이 그대로 읽는다.
 *
 * 진단 전용이라 실패해도 연결 흐름은 그대로 진행된다 — 어떤 판단도 이 결과에 의존하지 않는다.
 * 다만 실패를 삼키지는 않는다: 조회에 실패한 사유까지 로그로 남겨야 진단이 성립한다.
 */
@Singleton
class NikonLinkDiagnostics @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private companion object {
        const val TAG = "니콘링크진단"
        const val PROP_USB_SPEED = "d10c"
        const val PROP_CONNECTION_PATH = "d12e"

        /**
         * 제조사 문자열. `getCameraDeviceInfo()` 는 내부적으로 `gp_camera_get_summary` 를 태워
         * Nikon PTP/IP 에서 ~6초가 걸리므로 쓰지 않는다(PtpipDataSource 주석 참조).
         * 이 위젯은 PTP DeviceInfo 캐시 기반이라 즉시 반환된다.
         */
        const val PROP_MANUFACTURER = "manufacturer"

        /**
         * §6.5.15.1. libgphoto2 의 열거 테이블(`ptp.c:8155`)은 USB3 이전 세대라
         * `0=USB 1.1 / 1=USB 2.0` 까지만 정의한다 — 값 1 의 해석은 양쪽이 일치한다.
         * generic 위젯은 FormFlag 가 열거형이 아니면 원시 숫자를 그대로 주므로 여기서 라벨링한다.
         */
        val USB_SPEED_LABELS = mapOf(
            "1" to "High-Speed/USB2",
            "2" to "Super-Speed/USB3"
        )

        /** §6.5.15.2. 구세대 문서는 `1=Wireless Transmitter`(WT-x 별매 유닛)도 정의한다. */
        val CONNECTION_PATH_LABELS = mapOf(
            "0" to "USB",
            "1" to "무선송신기",
            "2" to "내장Wi-Fi",
            "3" to "유선LAN"
        )
    }

    /**
     * 연결 성공 직후 세션당 1회 호출. **니콘이 아니면 와이어를 한 번도 건드리지 않고 즉시 빠진다.**
     *
     * 값 라벨은 libgphoto2 가 열거형 문자열로 주는 것을 그대로 쓰되, 조회에 실패하면 그 사유를 남긴다.
     */
    suspend fun logLinkFacts() = withContext(ioDispatcher) {
        // 벤더 게이트 — 니콘이 아니면 두 프로퍼티를 아예 읽지 않는다.
        // 0xD10C/0xD12E 는 캐논·후지·올림푸스가 전혀 다른 뜻으로 쓰는 코드이고
        // generic 위젯 이름에는 벤더가 없으므로, 게이트가 없으면 남의 값을
        // USBSpeed 라고 적어 남기게 된다. 세션당 1회 호출이라 캐시는 두지 않는다.
        val manufacturer = readProp(PROP_MANUFACTURER)
        if (!manufacturer.contains("nikon", ignoreCase = true)) return@withContext

        // 진단은 **항상** 남긴다. 조회 실패까지 조용히 삼키면 "속도가 왜 안 뜨지"에
        // 답할 근거가 사라진다 — 실패 사유 자체가 진단 결과다.
        //
        // ⚠️ USBSpeed 는 무선 세션에서도 값이 나온다(Z8 Wi-Fi 실측: 1). 활성 링크의 실측치가
        // 아니라 USB 인터페이스의 보고값이므로, **케이블 연결 시에만** 의미를 갖는다.
        // 무선 실효 속도는 이 프로퍼티가 답하지 못한다(전송 바이트/시간으로 따로 재야 한다).
        val speed = readProp(PROP_USB_SPEED)
        val path = readProp(PROP_CONNECTION_PATH)
        Log.i(
            TAG,
            "LINK: USBSpeed=${label(speed, USB_SPEED_LABELS)} " +
                "ConnectionPath=${label(path, CONNECTION_PATH_LABELS)}"
        )
    }

    /** 원문 값을 잃지 않도록 라벨과 원값을 함께 남긴다 — 모르는 값이 와도 진단이 끊기지 않는다. */
    private fun label(raw: String, labels: Map<String, String>): String =
        labels[raw.trim()]?.let { "$it($raw)" } ?: raw

    /**
     * `getConfigString`은 값을 그대로 주지 않고 `"성공: <값>"` / `"실패: <사유>"` 로 감싸 돌려준다
     * (camera_config.cpp). null 은 절대 오지 않으므로 접두사로 성패를 가른다.
     *
     * 실패 시 사유를 그대로 반환한다 — 0xD10C 는 이름 그대로 USB 전용이라 Wi-Fi 세션에서는
     * DeviceProps 에 안 실리는 게 정상이고, 그 경우 위젯 탐색 실패로 나타난다.
     */
    private fun readProp(name: String): String =
        runCatching { CameraNative.getConfigString(name) }
            .getOrElse { "실패: ${it.message}" }
            ?.let { raw ->
                if (raw.startsWith("성공")) raw.removePrefix("성공: ") else "미노출($raw)"
            } ?: "미노출(null)"
}
