package com.inik.camcon.presentation.viewmodel

import com.inik.camcon.domain.model.CapturedPhoto
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 일괄 내보내기 **대상 선별** 회귀.
 *
 * 기기 저장소(MediaStore) 사진은 이미 폰 갤러리에 있어 내보낼 것이 없다. 그 판정이 무너지면
 * 같은 사진이 갤러리에 두 번 생기거나(중복), 반대로 앱 내부 사진이 빠져 사용자가 내보냈다고
 * 믿는 사진이 폰에 없게 된다. 파일 접근이 없는 순수 함수라 JVM 단독으로 건다.
 */
class SelectGalleryExportTargetsTest {

    private fun photo(id: String, path: String) = CapturedPhoto(
        id = id,
        filePath = path,
        thumbnailPath = null,
        captureTime = 0L,
        cameraModel = "Z8",
        settings = null,
        size = 100L,
        width = 0,
        height = 0
    )

    private val appPrivateA = photo("1", "/data/app/files/DCIM/CamCon/2026-08-31_100NCZ_8_Z8/A.JPG")
    private val appPrivateB = photo("2", "/data/app/files/DCIM/CamCon/2026-08-31_100NCZ_8_Z8/B.JPG")
    private val deviceStorage = photo("3", "/storage/emulated/0/DCIM/CamCon/C.JPG")

    /** 실제 판정([PhotoLibraryLocation.isInAppPrivateStorage])을 흉내 내는 최소 술어. */
    private val isAppPrivate: (String) -> Boolean = { it.startsWith("/data/app/files/") }

    @Test
    fun `앱 내부 사진만 대상이고 기기 저장소는 건너뛴 수로 집계된다`() {
        val result = selectGalleryExportTargets(
            photos = listOf(appPrivateA, appPrivateB, deviceStorage),
            selectedIds = setOf("1", "2", "3"),
            isAppPrivate = isAppPrivate
        )

        assertEquals(listOf(appPrivateA, appPrivateB), result.targets)
        assertEquals(1, result.alreadyInDeviceStorage)
    }

    @Test
    fun `선택되지 않은 사진은 대상에도 집계에도 들지 않는다`() {
        val result = selectGalleryExportTargets(
            photos = listOf(appPrivateA, appPrivateB, deviceStorage),
            selectedIds = setOf("1"),
            isAppPrivate = isAppPrivate
        )

        assertEquals(listOf(appPrivateA), result.targets)
        assertEquals(0, result.alreadyInDeviceStorage)
    }

    @Test
    fun `전부 기기 저장소면 대상이 비고 건너뛴 수만 남는다`() {
        val result = selectGalleryExportTargets(
            photos = listOf(appPrivateA, deviceStorage),
            selectedIds = setOf("3"),
            isAppPrivate = isAppPrivate
        )

        assertEquals(emptyList<CapturedPhoto>(), result.targets)
        assertEquals(1, result.alreadyInDeviceStorage)
    }

    @Test
    fun `선택이 비면 아무것도 내보내지 않는다`() {
        val result = selectGalleryExportTargets(
            photos = listOf(appPrivateA, deviceStorage),
            selectedIds = emptySet(),
            isAppPrivate = isAppPrivate
        )

        assertEquals(emptyList<CapturedPhoto>(), result.targets)
        assertEquals(0, result.alreadyInDeviceStorage)
    }

    @Test
    fun `대상 순서는 목록 순서를 그대로 따른다`() {
        val result = selectGalleryExportTargets(
            photos = listOf(appPrivateB, deviceStorage, appPrivateA),
            selectedIds = setOf("1", "2", "3"),
            isAppPrivate = isAppPrivate
        )

        // 최신순으로 정렬된 목록을 그대로 훑어야 진행 표시(3/12)가 화면 순서와 어긋나지 않는다.
        assertEquals(listOf(appPrivateB, appPrivateA), result.targets)
    }
}
