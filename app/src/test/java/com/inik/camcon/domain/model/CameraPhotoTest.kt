package com.inik.camcon.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [CameraPhoto] 의 신규 [CameraPhoto.uri] 필드 하위호환 회귀.
 *
 * 사진권한 정책 변경으로 MediaStore content URI 를 뷰어까지 관통시키기 위해 `uri: String? = null`
 * 필드가 추가됐다. 기존 생성부(카메라 캡처분·PTP 다운로드 등)는 `uri` 를 넘기지 않으므로,
 * 기본값이 계속 null 로 유지돼 기존 File 경로 흐름이 깨지지 않아야 한다.
 */
class CameraPhotoTest {

    @Test
    fun `uri 를 생략하면 기본값 null 하위호환`() {
        val photo = CameraPhoto(
            path = "/storage/emulated/0/DCIM/CamCon/IMG_0001.jpg",
            name = "IMG_0001.jpg",
            size = 1024L,
            date = 1_700_000_000_000L
        )

        assertNull(photo.uri)
    }

    @Test
    fun `uri 를 넘기면 그대로 보존된다`() {
        val uri = "content://media/external/images/media/42"
        val photo = CameraPhoto(
            path = "/storage/emulated/0/DCIM/CamCon/IMG_0002.jpg",
            name = "IMG_0002.jpg",
            size = 2048L,
            date = 1_700_000_000_001L,
            uri = uri
        )

        assertEquals(uri, photo.uri)
    }
}
