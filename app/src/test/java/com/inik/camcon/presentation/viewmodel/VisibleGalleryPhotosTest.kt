package com.inik.camcon.presentation.viewmodel

import com.inik.camcon.domain.model.CapturedPhoto
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * "좋아요만 보기" 파생 목록 회귀.
 *
 * 이 목록이 **다중 선택·전체 선택·일괄 내보내기의 기준**이다. 필터를 켠 채 전체 선택을 눌렀는데
 * 화면 밖 사진까지 잡히면 사용자가 고르지 않은 사진이 갤러리로 나간다 — 컬링 흐름에서 가장
 * 위험한 어긋남이라 순수 함수로 떼어 고정한다.
 */
class VisibleGalleryPhotosTest {

    private fun photo(id: String, path: String) = CapturedPhoto(
        id = id,
        filePath = path,
        thumbnailPath = null,
        captureTime = id.toLong(),
        cameraModel = "Z8",
        settings = null,
        size = 100L,
        width = 0,
        height = 0
    )

    private val a = photo("1", "/data/app/A.JPG")
    private val b = photo("2", "/data/app/B.JPG")
    private val c = photo("3", "/data/app/C.JPG")
    private val all = listOf(a, b, c)

    @Test
    fun `필터가 꺼져 있으면 목록을 그대로 돌려준다`() {
        assertEquals(
            all,
            visibleGalleryPhotos(all, favorites = setOf(a.filePath), showFavoritesOnly = false)
        )
    }

    @Test
    fun `필터를 켜면 좋아요한 사진만 남는다`() {
        assertEquals(
            listOf(a, c),
            visibleGalleryPhotos(
                all,
                favorites = setOf(a.filePath, c.filePath),
                showFavoritesOnly = true
            )
        )
    }

    @Test
    fun `좋아요가 없으면 필터 결과는 비어 있다`() {
        assertEquals(
            emptyList<CapturedPhoto>(),
            visibleGalleryPhotos(all, favorites = emptySet(), showFavoritesOnly = true)
        )
    }

    @Test
    fun `목록에 없는 좋아요 경로는 아무 일도 하지 않는다`() {
        // 다른 폴더의 좋아요가 이 폴더 목록에 끼어들면 안 된다.
        assertEquals(
            listOf(b),
            visibleGalleryPhotos(
                all,
                favorites = setOf(b.filePath, "/data/app/other/Z.JPG"),
                showFavoritesOnly = true
            )
        )
    }

    @Test
    fun `필터는 원본 순서를 그대로 유지한다`() {
        // 최신순으로 정렬된 목록을 걸러내기만 한다 — 순서가 바뀌면 그리드가 뒤섞인다.
        assertEquals(
            listOf(a, b, c),
            visibleGalleryPhotos(
                all,
                favorites = setOf(c.filePath, a.filePath, b.filePath),
                showFavoritesOnly = true
            )
        )
    }
}
