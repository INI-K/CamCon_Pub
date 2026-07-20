package com.inik.camcon.data.repository.managers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [PhotoDownloadManager.sanitizeCameraFileName] 단위 테스트.
 *
 * JNI 콜백이 넘긴 카메라 파일명은 신뢰할 수 없어 "../" 등이 섞이면 앱 사설 저장소 내부/외부
 * 파일을 덮어쓸 수 있다(경로 traversal). 순수 basename 정제 로직만 JVM 단위 테스트로 고정한다.
 * 실제 동작(File.name 기반 basename + blank/./../ISO제어문자 거부)에 맞춰 검증한다.
 *
 * 주의: 정제는 File.name 으로 basename 만 취하므로, 원시 문자열의 실제 경로 구분자('/')만
 * 제거한다. URL 인코딩("..%2F")은 구분자가 아니므로 정제 대상이 아니다(원시 문자열 기준).
 */
class PhotoDownloadManagerSanitizeFileNameTest {

    private val nul = 0.toChar()   // NUL (ISO control)
    private val lf = 10.toChar()   // 개행 LF (ISO control)
    private val tab = 9.toChar()   // 탭 HT (ISO control)

    @Test
    fun `정상 파일명은 그대로 반환`() {
        assertEquals("KY6_0035.JPG", PhotoDownloadManager.sanitizeCameraFileName("KY6_0035.JPG"))
    }

    @Test
    fun `서브폴더가 앞에 붙으면 basename 만 반환`() {
        assertEquals(
            "KY6_0035.JPG",
            PhotoDownloadManager.sanitizeCameraFileName("105KAY_1/KY6_0035.JPG")
        )
    }

    @Test
    fun `카메라 풀경로도 basename 만 반환`() {
        assertEquals(
            "KY6_0035.JPG",
            PhotoDownloadManager.sanitizeCameraFileName("/store_00010001/DCIM/105KAY_1/KY6_0035.JPG")
        )
    }

    @Test
    fun `상위경로 traversal 이 섞여도 안전한 basename 으로 축약`() {
        // 실제 경로 구분자('/')가 제거되어 traversal 이 무력화된 안전한 basename 만 남는다.
        assertEquals(
            "x.jpg",
            PhotoDownloadManager.sanitizeCameraFileName("../../shared_prefs/x.jpg")
        )
    }

    @Test
    fun `절대경로는 basename 만 반환`() {
        assertEquals(
            "evil.jpg",
            PhotoDownloadManager.sanitizeCameraFileName("/data/data/com.inik.camcon/evil.jpg")
        )
    }

    @Test
    fun `basename 이 상위경로 토큰이면 null`() {
        // File("foo/..").name == ".." → 유효하지 않은 것으로 거부.
        assertNull(PhotoDownloadManager.sanitizeCameraFileName("foo/.."))
    }

    @Test
    fun `상위경로 토큰 단독은 null`() {
        assertNull(PhotoDownloadManager.sanitizeCameraFileName(".."))
    }

    @Test
    fun `현재경로 토큰 단독은 null`() {
        assertNull(PhotoDownloadManager.sanitizeCameraFileName("."))
    }

    @Test
    fun `빈 문자열은 null`() {
        assertNull(PhotoDownloadManager.sanitizeCameraFileName(""))
    }

    @Test
    fun `공백만 있는 문자열은 null`() {
        assertNull(PhotoDownloadManager.sanitizeCameraFileName("   "))
    }

    @Test
    fun `개행 제어문자를 포함하면 null`() {
        assertNull(PhotoDownloadManager.sanitizeCameraFileName("a" + lf + "b.jpg"))
    }

    @Test
    fun `탭 제어문자를 포함하면 null`() {
        assertNull(PhotoDownloadManager.sanitizeCameraFileName("a" + tab + "b.jpg"))
    }

    @Test
    fun `NUL 제어문자를 포함하면 null`() {
        assertNull(PhotoDownloadManager.sanitizeCameraFileName("a" + nul + "b.jpg"))
    }

    @Test
    fun `일반 공백은 제어문자가 아니므로 보존된다`() {
        // 실제 동작 문서화: ASCII 공백(0x20)은 ISO 제어문자가 아니므로 정제되지 않고 그대로 남는다.
        assertEquals(
            "my photo.jpg",
            PhotoDownloadManager.sanitizeCameraFileName("my photo.jpg")
        )
    }
}
