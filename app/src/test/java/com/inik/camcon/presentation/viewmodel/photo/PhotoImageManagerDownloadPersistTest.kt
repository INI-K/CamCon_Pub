package com.inik.camcon.presentation.viewmodel.photo

import com.inik.camcon.domain.model.SubscriptionTier
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [PhotoImageManager] 의 다운로드→기기 저장 배선 검증.
 *
 * 감사 확정: 갤러리 다운로드가 인메모리 캐시에만 적재되고 MediaStore 저장이 없던 문제의 수정.
 * - (a) 명시적 다운로드 성공 시 저장 포트([GalleryDownloadStore.save]) 가 호출되는가.
 * - (b) FREE 티어 저장이 [ValidateImageFormatUseCase] 단일 지점을 경유하는가(게이팅 우회 차단).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PhotoImageManagerDownloadPersistTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private lateinit var cameraRepository: CameraRepository
    private lateinit var getCameraThumbnailUseCase: GetCameraThumbnailUseCase
    private lateinit var downloadCameraPhotoUseCase: DownloadCameraPhotoUseCase
    private lateinit var getCameraPhotoExifJsonUseCase: GetCameraPhotoExifJsonUseCase
    private lateinit var validateImageFormatUseCase: ValidateImageFormatUseCase
    private lateinit var galleryDownloadStore: GalleryDownloadStore

    private lateinit var manager: PhotoImageManager

    private val jpgPath = "/store_00010001/DCIM/100NIKON/DSC_0001.JPG"

    @Before
    fun setUp() {
        cameraRepository = mockk(relaxed = true)
        getCameraThumbnailUseCase = mockk(relaxed = true)
        downloadCameraPhotoUseCase = mockk(relaxed = true)
        getCameraPhotoExifJsonUseCase = mockk(relaxed = true)
        validateImageFormatUseCase = mockk(relaxed = true)
        galleryDownloadStore = mockk(relaxed = true)

        manager = PhotoImageManager(
            cameraRepository = cameraRepository,
            getCameraThumbnailUseCase = getCameraThumbnailUseCase,
            downloadCameraPhotoUseCase = downloadCameraPhotoUseCase,
            getCameraPhotoExifJsonUseCase = getCameraPhotoExifJsonUseCase,
            validateImageFormatUseCase = validateImageFormatUseCase,
            galleryDownloadStore = galleryDownloadStore,
            appScope = CoroutineScope(dispatcher),
            ioDispatcher = dispatcher
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `PRO 다운로드 성공 시 원본 바이트를 저장 포트로 넘긴다`() = runTest(dispatcher) {
        val bytes = byteArrayOf(1, 2, 3, 4)
        coEvery { downloadCameraPhotoUseCase(jpgPath) } returns bytes
        coEvery { validateImageFormatUseCase.validateFormat(any()) } returns
            ValidateImageFormatUseCase.ValidationResult(isSupported = true)
        coEvery { galleryDownloadStore.save(any(), any()) } returns "/DCIM/CamCon/100NIKON/DSC_0001.JPG"

        val result = manager.downloadAndPersist(jpgPath, SubscriptionTier.PRO)

        assertTrue("저장까지 성공하면 true", result)
        // 저장 포트가 원본 경로·바이트로 호출되어야 한다(실제 기기 저장 배선).
        coVerify(exactly = 1) { galleryDownloadStore.save(jpgPath, bytes) }
    }

    @Test
    fun `FREE 저장은 ValidateImageFormatUseCase 게이팅 단일 지점을 경유한다`() = runTest(dispatcher) {
        val bytes = byteArrayOf(9, 8, 7)
        coEvery { downloadCameraPhotoUseCase(jpgPath) } returns bytes
        coEvery { validateImageFormatUseCase.validateFormat(any()) } returns
            ValidateImageFormatUseCase.ValidationResult(isSupported = true)
        coEvery { galleryDownloadStore.save(any(), any()) } returns "/DCIM/CamCon/100NIKON/DSC_0001.JPG"

        val result = manager.downloadAndPersist(jpgPath, SubscriptionTier.FREE)

        assertTrue(result)
        // 게이팅 단일 지점 경유 확인 — 저장 전에 포맷 검증이 반드시 호출된다.
        coVerify(atLeast = 1) { validateImageFormatUseCase.validateFormat(match { it.endsWith(".JPG") }) }
        coVerify(exactly = 1) { galleryDownloadStore.save(eq(jpgPath), any()) }
    }

    @Test
    fun `게이팅이 미지원으로 판정하면 기기 저장을 하지 않는다`() = runTest(dispatcher) {
        val bytes = byteArrayOf(5, 5, 5)
        coEvery { downloadCameraPhotoUseCase(jpgPath) } returns bytes
        // 저장 진입부 게이팅이 미지원(RAW·업그레이드 필요)으로 차단하는 경우를 시뮬레이션.
        coEvery { validateImageFormatUseCase.validateFormat(any()) } returns
            ValidateImageFormatUseCase.ValidationResult(
                isSupported = false,
                needsUpgrade = true,
                isRawFile = true
            )

        val result = manager.downloadAndPersist(jpgPath, SubscriptionTier.PRO)

        assertFalse("게이팅 차단 시 저장 실패로 통지", result)
        coVerify(exactly = 0) { galleryDownloadStore.save(any(), any()) }
    }

    @Test
    fun `빈 다운로드 데이터면 저장하지 않고 실패로 통지한다`() = runTest(dispatcher) {
        coEvery { downloadCameraPhotoUseCase(jpgPath) } returns ByteArray(0)

        val result = manager.downloadAndPersist(jpgPath, SubscriptionTier.PRO)

        assertFalse(result)
        coVerify(exactly = 0) { galleryDownloadStore.save(any(), any()) }
    }
}
