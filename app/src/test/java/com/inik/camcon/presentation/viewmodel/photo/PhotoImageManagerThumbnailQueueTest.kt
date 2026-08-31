package com.inik.camcon.presentation.viewmodel.photo

import app.cash.turbine.test
import com.inik.camcon.domain.model.CameraPhoto
import com.inik.camcon.domain.repository.CameraRepository
import com.inik.camcon.domain.usecase.ValidateImageFormatUseCase
import com.inik.camcon.domain.usecase.camera.DownloadCameraPhotoUseCase
import com.inik.camcon.domain.usecase.camera.GetCameraPhotoExifJsonUseCase
import com.inik.camcon.domain.usecase.camera.GetCameraThumbnailUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [PhotoImageManager] 썸네일 대기열 검증.
 *
 * 고정하는 계약은 셋이다.
 *
 * 1. **목록이 늘어도 처음부터 다시 받지 않는다.** 페이징은 목록을 51 → 101 → 151 로 늘리는데,
 *    예전에는 그때마다 진행 중 잡을 취소하고 0번부터 다시 시작해 앞쪽 썸네일을 반복해서 받고
 *    정작 사용자가 보는 구간이 뒤로 밀렸다. 같은 사진의 썸네일 요청은 한 번뿐이어야 한다.
 * 2. **이미 캐시에 있는 사진은 다시 요청하지 않는다.**
 * 3. **쓸 수 없는 썸네일을 주는 카메라로 판정되면 남은 요청을 중단한다.** 계속 받아 봐야
 *    디코딩 불가능한 데이터를 사진당 100KB 넘게 실어 나른다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PhotoImageManagerThumbnailQueueTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private lateinit var getCameraThumbnailUseCase: GetCameraThumbnailUseCase
    private lateinit var manager: PhotoImageManager

    /** 유효한 최소 JPEG(서명 + EOI). 서명 검사를 통과해야 정상 경로로 흐른다. */
    private val jpegBytes = byteArrayOf(
        0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
        0xFF.toByte(), 0xD9.toByte()
    )

    private fun photo(index: Int) = CameraPhoto(
        path = "/store_00010001/DCIM/100NIKON/DSC_%04d.JPG".format(index),
        name = "DSC_%04d.JPG".format(index),
        size = 1000L,
        date = 0L
    )

    @Before
    fun setUp() {
        getCameraThumbnailUseCase = mockk()
        coEvery { getCameraThumbnailUseCase(any()) } returns Result.success(jpegBytes)

        manager = PhotoImageManager(
            cameraRepository = mockk<CameraRepository>(relaxed = true),
            getCameraThumbnailUseCase = getCameraThumbnailUseCase,
            downloadCameraPhotoUseCase = mockk<DownloadCameraPhotoUseCase>(relaxed = true),
            getCameraPhotoExifJsonUseCase = mockk<GetCameraPhotoExifJsonUseCase>(relaxed = true),
            validateImageFormatUseCase = mockk<ValidateImageFormatUseCase>(relaxed = true),
            galleryDownloadStore = mockk<GalleryDownloadStore>(relaxed = true),
            appScope = CoroutineScope(dispatcher),
            ioDispatcher = dispatcher
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `목록이 늘어도 앞쪽 썸네일을 다시 요청하지 않는다`() = runTest(dispatcher) {
        val firstPage = (1..3).map { photo(it) }
        val secondPage = (1..6).map { photo(it) }   // 앞 3장은 그대로, 뒤 3장이 추가

        manager.loadThumbnailsForPhotos(firstPage)
        manager.loadThumbnailsForPhotos(secondPage)

        // 앞 3장은 정확히 한 번씩만 — 재시작했다면 두 번씩 불렸을 자리다.
        (1..3).forEach { i ->
            coVerify(exactly = 1) { getCameraThumbnailUseCase(photo(i).path) }
        }
        // 새로 들어온 3장도 한 번씩 받는다.
        (4..6).forEach { i ->
            coVerify(exactly = 1) { getCameraThumbnailUseCase(photo(i).path) }
        }
    }

    @Test
    fun `같은 목록을 다시 넘기면 아무것도 요청하지 않는다`() = runTest(dispatcher) {
        val photos = (1..3).map { photo(it) }

        manager.loadThumbnailsForPhotos(photos)
        manager.loadThumbnailsForPhotos(photos)

        photos.forEach { p ->
            coVerify(exactly = 1) { getCameraThumbnailUseCase(p.path) }
        }
    }

    @Test
    fun `받은 썸네일이 캐시 StateFlow 로 방출된다`() = runTest(dispatcher) {
        val photos = (1..2).map { photo(it) }

        manager.loadThumbnailsForPhotos(photos)

        manager.thumbnailCache.test {
            val cache = expectMostRecentItem()
            assertEquals(2, cache.size)
            assertTrue(cache.containsKey(photo(1).path))
            assertTrue(cache.containsKey(photo(2).path))
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `JPEG 이 아니면 미지원으로 방출하고 나머지를 중단한다`() = runTest(dispatcher) {
        // 소니 신형이 돌려주는, 디코딩 불가능한 바이트를 흉내낸다.
        coEvery { getCameraThumbnailUseCase(any()) } returns Result.success(byteArrayOf(0, 1, 2, 3))
        val photos = (1..5).map { photo(it) }

        manager.thumbnailUnsupported.test {
            assertFalse("판정 전에는 지원한다고 본다", awaitItem())

            manager.loadThumbnailsForPhotos(photos)

            assertTrue("첫 응답이 JPEG 이 아니면 미지원이다", expectMostRecentItem())
            cancelAndConsumeRemainingEvents()
        }

        // 첫 장에서 중단했으므로 5장을 다 받지 않는다.
        coVerify(exactly = 1) { getCameraThumbnailUseCase(photo(1).path) }
        coVerify(exactly = 0) { getCameraThumbnailUseCase(photo(5).path) }
    }

    @Test
    fun `미지원으로 판정된 뒤에는 요청 자체를 하지 않는다`() = runTest(dispatcher) {
        coEvery { getCameraThumbnailUseCase(any()) } returns Result.success(byteArrayOf(0, 1, 2, 3))
        manager.loadThumbnailsForPhotos(listOf(photo(1)))
        assertTrue(manager.thumbnailUnsupported.value)

        manager.loadThumbnailsForPhotos((10..12).map { photo(it) })

        (10..12).forEach { i ->
            coVerify(exactly = 0) { getCameraThumbnailUseCase(photo(i).path) }
        }
    }
}
