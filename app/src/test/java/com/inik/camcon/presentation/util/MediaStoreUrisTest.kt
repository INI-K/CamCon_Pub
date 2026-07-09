package com.inik.camcon.presentation.util

import android.app.Application
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [imageContentUriOrNull] 경계 회귀.
 *
 * `CapturedPhoto.id` → content URI 매핑은 스코프드 스토리지에서 own-media 를 관통시키는 핵심.
 * 핵심 경계는 "id 가 MediaStore _ID(Long)면 URI, UUID 폴백이면 null"이다. `ContentUris`/
 * `MediaStore` 가 Android 클래스라 Robolectric 으로 실행한다(sdk=34: Robolectric 4.14 지원 한계).
 * application=Application::class: 실제 CamCon 은 onCreate 에서 네이티브를 로드해 JVM 에서 실패.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MediaStoreUrisTest {

    @Test
    fun `숫자 _ID 는 해당 id 로 끝나는 이미지 content URI 로 매핑된다`() {
        val result = imageContentUriOrNull("123")

        requireNotNull(result)
        assertTrue("content:// 스킴이어야 함: $result", result.startsWith("content://"))
        assertTrue("images 경로여야 함: $result", result.contains("images/media"))
        assertTrue("id 로 끝나야 함: $result", result.endsWith("/123"))
    }

    @Test
    fun `UUID 폴백 id 는 null (기존 File 경로로 폴백)`() {
        assertNull(imageContentUriOrNull("550e8400-e29b-41d4-a716-446655440000"))
    }

    @Test
    fun `숫자가 아닌 id 는 null`() {
        assertNull(imageContentUriOrNull("IMG_0001"))
    }

    @Test
    fun `빈 문자열은 null`() {
        assertNull(imageContentUriOrNull(""))
    }
}
