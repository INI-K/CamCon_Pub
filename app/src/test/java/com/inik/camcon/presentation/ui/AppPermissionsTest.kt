package com.inik.camcon.presentation.ui

import android.Manifest
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AppPermissions] 순수 함수 회귀.
 *
 * - 요청 목록: SDK 분기 + 미허용 필터
 * - 영구 거부 목록: 거부 && 재요청 억제 필터
 */
class AppPermissionsTest {

    @Test
    fun `API33+ 미허용 미디어_알림 권한만 요청 목록에 담긴다`() {
        val granted = setOf(Manifest.permission.READ_MEDIA_IMAGES)

        val result = AppPermissions.storagePermissionsToRequest(Build.VERSION_CODES.TIRAMISU) {
            it in granted
        }

        assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS), result)
    }

    @Test
    fun `API32 이하는 저장소 권한을 요청하지 않는다`() {
        val result = AppPermissions.storagePermissionsToRequest(Build.VERSION_CODES.S) { false }

        assertTrue(result.isEmpty())
    }

    @Test
    fun `모두 허용이면 요청 목록은 비어 있다`() {
        val result = AppPermissions.storagePermissionsToRequest(Build.VERSION_CODES.TIRAMISU) { true }

        assertTrue(result.isEmpty())
    }

    @Test
    fun `영구 거부된 권한만 골라낸다`() {
        val results = mapOf(
            Manifest.permission.READ_MEDIA_IMAGES to false,   // 거부 + 영구
            Manifest.permission.POST_NOTIFICATIONS to false,  // 거부 + 재요청 가능
            "android.permission.GRANTED" to true              // 승인
        )
        val permanent = setOf(Manifest.permission.READ_MEDIA_IMAGES)

        val result = AppPermissions.permanentlyDeniedPermissions(results) { it in permanent }

        assertEquals(listOf(Manifest.permission.READ_MEDIA_IMAGES), result)
    }

    @Test
    fun `거부가 모두 재요청 가능하면 영구 거부 목록은 비어 있다`() {
        val results = mapOf(Manifest.permission.READ_MEDIA_IMAGES to false)

        val result = AppPermissions.permanentlyDeniedPermissions(results) { false }

        assertTrue(result.isEmpty())
    }
}
