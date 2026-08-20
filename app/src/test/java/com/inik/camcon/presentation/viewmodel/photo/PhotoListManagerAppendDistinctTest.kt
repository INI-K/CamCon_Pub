package com.inik.camcon.presentation.viewmodel.photo

import com.inik.camcon.domain.model.CameraPhoto
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 페이지 누적 시 경로 중복 제거 회귀 테스트.
 *
 * 네이티브 페이징은 최신 우선 역순 수집이라 페이지 사이에 새 사진이 들어오면 창이 밀려
 * 같은 파일이 두 페이지에 걸쳐 나온다(Z8 실측 2026-08-19 16:56 KAY_3030.JPG). 중복이
 * 그대로 흘러가면 PhotoPreviewScreen 의 `key = { photo -> photo.path }` 계약이 깨져
 * Compose 가 IllegalArgumentException 으로 앱을 죽인다(FATAL). 그 재발 방지.
 */
class PhotoListManagerAppendDistinctTest {

    private fun photo(name: String) = CameraPhoto(
        path = "/store_00020001/DCIM/100NCZ_8/$name",
        name = name,
        size = 7_000_000L,
        date = 0L
    )

    private fun manager() = PhotoListManager(
        context = mockk(relaxed = true),
        getCameraPhotosPagedUseCase = mockk(relaxed = true),
        validateImageFormatUseCase = mockk(relaxed = true),
        errorHandlingManager = mockk(relaxed = true),
        appScope = CoroutineScope(Dispatchers.Unconfined)
    )

    @Test
    fun `페이지 경계 중복은 제거되고 순서는 보존된다`() {
        val page0 = listOf(photo("KAY_3032.JPG"), photo("KAY_3031.JPG"), photo("KAY_3030.JPG"))
        // 역순 수집 중 새 사진이 들어와 창이 밀린 상황: KAY_3030 이 다음 페이지에 다시 등장
        val page1 = listOf(photo("KAY_3030.JPG"), photo("KAY_3029.JPG"))

        val merged = manager().appendDistinct(page0, page1)

        assertEquals(
            listOf("KAY_3032.JPG", "KAY_3031.JPG", "KAY_3030.JPG", "KAY_3029.JPG"),
            merged.map { it.name }
        )
        assertEquals(merged.size, merged.map { it.path }.distinct().size)
    }

    @Test
    fun `수신 페이지 내부의 중복도 제거된다`() {
        val merged = manager().appendDistinct(
            emptyList(),
            listOf(photo("KAY_1.JPG"), photo("KAY_1.JPG"), photo("KAY_2.JPG"))
        )

        assertEquals(listOf("KAY_1.JPG", "KAY_2.JPG"), merged.map { it.name })
    }

    @Test
    fun `전부 중복이면 기존 목록을 그대로 유지한다`() {
        val current = listOf(photo("KAY_1.JPG"), photo("KAY_2.JPG"))

        val merged = manager().appendDistinct(current, listOf(photo("KAY_2.JPG")))

        assertEquals(current, merged)
    }

    @Test
    fun `빈 페이지 수신은 기존 목록에 영향을 주지 않는다`() {
        val current = listOf(photo("KAY_1.JPG"))

        assertEquals(current, manager().appendDistinct(current, emptyList()))
    }

    @Test
    fun `중복이 없으면 전부 이어붙인다`() {
        val merged = manager().appendDistinct(
            listOf(photo("KAY_1.JPG")),
            listOf(photo("KAY_2.JPG"), photo("KAY_3.JPG"))
        )

        assertEquals(3, merged.size)
        assertEquals(listOf("KAY_1.JPG", "KAY_2.JPG", "KAY_3.JPG"), merged.map { it.name })
    }
}
