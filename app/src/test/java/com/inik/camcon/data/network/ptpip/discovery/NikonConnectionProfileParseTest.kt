package com.inik.camcon.data.network.ptpip.discovery

import com.inik.camcon.domain.model.NikonConnectionProfile
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * mDNS TXT `apps` → 연결 프로파일 매핑 회귀 테스트.
 *
 * 이 매핑이 틀리면 연결 화면이 "바디 조작 가능/불가"를 거꾸로 안내한다. 값은 Z8 실기 덤프에서
 * 그대로 가져왔다(2026-08-19, VENDOR_MDNS_DUMP):
 *   apps=$DSC  → [컴퓨터에 연결 · 카메라 컨트롤] 세션 중 본체 재생(▶) 잠김
 *   apps=WT3T  → [컴퓨터에 연결 · 사진 전송]     본체 조작 자유 + 촬영물 수신 동작
 *
 * `$DSC` 의 `$` 는 Kotlin 문자열 템플릿 시작 문자라 이스케이프가 필요하다 —
 * 이걸 놓치면 조용히 빈 문자열과 비교하게 되므로 테스트로 못박는다.
 */
class NikonConnectionProfileParseTest {

    private fun txt(vararg pairs: Pair<String, String?>): Map<String, ByteArray?> =
        pairs.associate { (k, v) -> k to v?.toByteArray(Charsets.UTF_8) }

    @Test
    fun `카메라 컨트롤 모드는 DSC 로 광고된다`() {
        assertEquals(
            NikonConnectionProfile.CAMERA_CONTROL,
            parseNikonConnectionProfile(txt("apps" to "\$DSC", "vid" to "A"))
        )
    }

    @Test
    fun `사진 전송 모드는 WT 접두로 광고된다`() {
        assertEquals(
            NikonConnectionProfile.IMAGE_TRANSFER,
            parseNikonConnectionProfile(txt("apps" to "WT3T"))
        )
        // WT-7 등 다른 무선 송신기 변형도 같은 성격으로 본다(정확한 집합은 미확정).
        assertEquals(
            NikonConnectionProfile.IMAGE_TRANSFER,
            parseNikonConnectionProfile(txt("apps" to "WT7"))
        )
    }

    @Test
    fun `apps 가 없거나 모르는 값이면 UNKNOWN 이다`() {
        assertEquals(
            NikonConnectionProfile.UNKNOWN,
            parseNikonConnectionProfile(txt("vid" to "A", "pid" to "451"))
        )
        assertEquals(
            NikonConnectionProfile.UNKNOWN,
            parseNikonConnectionProfile(txt("apps" to "SOMETHING"))
        )
        assertEquals(
            NikonConnectionProfile.UNKNOWN,
            parseNikonConnectionProfile(emptyMap())
        )
    }

    @Test
    fun `값이 null 이거나 공백이어도 터지지 않는다`() {
        assertEquals(
            NikonConnectionProfile.UNKNOWN,
            parseNikonConnectionProfile(txt("apps" to null))
        )
        assertEquals(
            NikonConnectionProfile.UNKNOWN,
            parseNikonConnectionProfile(txt("apps" to "   "))
        )
    }

    @Test
    fun `키 대소문자와 값 앞뒤 공백은 무시한다`() {
        assertEquals(
            NikonConnectionProfile.CAMERA_CONTROL,
            parseNikonConnectionProfile(txt("APPS" to " \$DSC "))
        )
        assertEquals(
            NikonConnectionProfile.IMAGE_TRANSFER,
            parseNikonConnectionProfile(txt("Apps" to "wt3t"))
        )
    }
}
