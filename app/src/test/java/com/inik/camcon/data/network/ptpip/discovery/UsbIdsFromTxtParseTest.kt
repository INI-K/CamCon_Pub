package com.inik.camcon.data.network.ptpip.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * mDNS TXT → USB VID/PID 추출 검증.
 *
 * 실측 기준값은 Z8(2026-08-20): `guid=04b00451-0000-1001-8001-3cbee134e25a`, `pid=451`.
 * 이 값이 libgphoto2 표의 `{"Nikon:Z8", 0x04b0, 0x0451}` 과 일치해 발견 단계에서
 * 기종명을 확정할 수 있다.
 */
class UsbIdsFromTxtParseTest {

    private fun txt(vararg pairs: Pair<String, String>): Map<String, ByteArray?> =
        pairs.associate { (k, v) -> k to v.toByteArray(Charsets.UTF_8) }

    @Test
    fun `GUID 앞 8자리에서 VID와 PID를 뽑는다`() {
        val ids = parseUsbIdsFromTxt(txt("guid" to "04b00451-0000-1001-8001-3cbee134e25a"))
        assertEquals(0x04b0 to 0x0451, ids)
    }

    @Test
    fun `GUID가 없으면 pid 필드를 니콘 벤더로 보정한다`() {
        // `vid=A` 는 USB 벤더 ID 가 아니므로 참조하지 않는다.
        val ids = parseUsbIdsFromTxt(txt("pid" to "451", "vid" to "A"))
        assertEquals(0x04b0 to 0x0451, ids)
    }

    @Test
    fun `GUID가 pid보다 우선한다`() {
        val ids = parseUsbIdsFromTxt(txt("guid" to "04b00450-0000-1001", "pid" to "451"))
        assertEquals(0x04b0 to 0x0450, ids)
    }

    @Test
    fun `VID가 0인 GUID는 무시하고 pid로 폴백한다`() {
        val ids = parseUsbIdsFromTxt(txt("guid" to "00000451-0000-1001", "pid" to "451"))
        assertEquals(0x04b0 to 0x0451, ids)
    }

    @Test
    fun `식별 정보가 없으면 null`() {
        assertNull(parseUsbIdsFromTxt(txt("f" to "5745", "n" to "Mv3sdfJBMPUIaHOlDtsWUt8")))
    }

    @Test
    fun `짧거나 깨진 GUID는 폴백도 없으면 null`() {
        assertNull(parseUsbIdsFromTxt(txt("guid" to "04b0")))
        assertNull(parseUsbIdsFromTxt(txt("guid" to "zzzzzzzz-0000")))
    }

    @Test
    fun `키 대소문자를 가리지 않는다`() {
        assertEquals(0x04b0 to 0x0451, parseUsbIdsFromTxt(txt("GUID" to "04b00451-0000")))
        assertEquals(0x04b0 to 0x0451, parseUsbIdsFromTxt(txt("PID" to "451")))
    }
}
