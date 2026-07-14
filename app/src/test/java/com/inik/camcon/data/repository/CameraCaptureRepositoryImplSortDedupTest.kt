package com.inik.camcon.data.repository

import android.content.Context
import com.inik.camcon.data.cache.CacheSweeper
import com.inik.camcon.data.cache.TtlLruProcessedFileCache
import com.inik.camcon.data.datasource.nativesource.NativeCameraDataSource
import com.inik.camcon.data.datasource.ptpip.PtpipDataSource
import com.inik.camcon.data.datasource.usb.UsbCameraManager
import com.inik.camcon.data.repository.managers.CameraConnectionManager
import com.inik.camcon.data.repository.managers.CameraEventManager
import com.inik.camcon.data.repository.managers.PhotoDownloadManager
import com.inik.camcon.data.repository.managers.TransferProgressTracker
import com.inik.camcon.domain.model.CapturedPhoto
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * 정렬/중복 회귀 테스트 (사진 순서/중복 fix).
 *
 * CameraCaptureRepositoryImpl.updateDownloadedPhoto 의 정렬·dedup 안전망을 검증한다.
 * 해당 메서드는 private 이므로 @VisibleForTesting seedDownloadedPhotoForTest 진입점으로
 * 동일 경로를 통과시킨다(프로덕션 동작 변경 없음 — 진입점 노출만, seamsAdded 참조).
 *
 * 검증 대상(요구):
 *  - captureTime 역순으로 시드해도 StateFlow 는 captureTime 오름차순(꼬리=최신)으로 정렬.
 *  - 동일 fileName(경로 상위 디렉터리만 다름) 2건 → 1건만 유지(Z8 듀얼슬롯 중복 제거).
 *  - "X.NEF" + "X.JPG" → 확장자가 달라 별개 컷으로 2건 유지(RAW+JPG).
 */
class CameraCaptureRepositoryImplSortDedupTest {

    private lateinit var captureRepo: CameraCaptureRepositoryImpl

    @Before
    fun setUp() {
        val context = mockk<Context>(relaxed = true)
        val nativeDataSource = mockk<NativeCameraDataSource>(relaxed = true)
        val ptpipDataSource = mockk<PtpipDataSource>(relaxed = true)
        val usbCameraManager = mockk<UsbCameraManager>(relaxed = true)
        val connectionManager = mockk<CameraConnectionManager>(relaxed = true)
        val eventManager = mockk<CameraEventManager>(relaxed = true)
        val downloadManager = mockk<PhotoDownloadManager>(relaxed = true)
        val transferProgressTracker = mockk<TransferProgressTracker>(relaxed = true)
        val cacheSweeper = mockk<CacheSweeper>(relaxed = true)
        val scope = mockk<CoroutineScope>(relaxed = true)
        val ioDispatcher: CoroutineDispatcher = StandardTestDispatcher()

        // updateDownloadedPhoto 의 dedup 은 capturedPhotos StateFlow 기반이라
        // processedFileCache 는 영향 없음(별개 키 경로). 실 캐시 주입으로 안전.
        val processedFileCache = TtlLruProcessedFileCache(
            clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
        )

        captureRepo = CameraCaptureRepositoryImpl(
            context = context,
            nativeDataSource = nativeDataSource,
            ptpipDataSource = ptpipDataSource,
            usbCameraManager = usbCameraManager,
            connectionManager = connectionManager,
            eventManager = eventManager,
            downloadManager = downloadManager,
            transferProgressTracker = transferProgressTracker,
            errorNotifier = mockk(relaxed = true),
            processedFileCache = processedFileCache,
            cacheSweeper = cacheSweeper,
            scope = scope,
            ioDispatcher = ioDispatcher
        )
    }

    @Test
    fun `captureTime 역순 시드 시 StateFlow 는 captureTime 오름차순으로 정렬`() = runTest {
        // Arrange & Act: captureTime 이 늦은 순(역순)으로 먼저 시드
        captureRepo.seedDownloadedPhotoForTest(photo(name = "C.JPG", captureTime = 3000))
        captureRepo.seedDownloadedPhotoForTest(photo(name = "A.JPG", captureTime = 1000))
        captureRepo.seedDownloadedPhotoForTest(photo(name = "B.JPG", captureTime = 2000))

        // Assert: 오름차순(꼬리=최신). UI 가 takeLast/lastOrNull 로 최신을 꼬리에서 읽음.
        val photos = captureRepo.getCapturedPhotos().first()
        assertEquals(listOf(1000L, 2000L, 3000L), photos.map { it.captureTime })
        assertEquals("A.JPG", basename(photos.first().filePath))
        assertEquals("C.JPG", basename(photos.last().filePath))
    }

    @Test
    fun `동일 fileName 이 경로만 다르게 2건 시드되면 1건만 유지`() = runTest {
        // Arrange & Act: 듀얼슬롯(store_00010001/00020001)이 같은 fileName 을 두 번 내려보낸 상황
        captureRepo.seedDownloadedPhotoForTest(
            photo(filePath = "/store_00010001/DSC_0001.JPG", captureTime = 1000)
        )
        captureRepo.seedDownloadedPhotoForTest(
            photo(filePath = "/store_00020001/DSC_0001.JPG", captureTime = 1000)
        )

        // Assert: basename(파일명) 기준 dedup → 1건
        val photos = captureRepo.getCapturedPhotos().first()
        assertEquals(1, photos.size)
        assertEquals("DSC_0001.JPG", basename(photos.first().filePath))
    }

    @Test
    fun `RAW 와 JPG 는 확장자가 달라 2건 모두 유지`() = runTest {
        // Arrange & Act: 동일 컷의 RAW + JPG 두 포맷
        captureRepo.seedDownloadedPhotoForTest(
            photo(filePath = "/store/X.NEF", captureTime = 1000)
        )
        captureRepo.seedDownloadedPhotoForTest(
            photo(filePath = "/store/X.JPG", captureTime = 1000)
        )

        // Assert: 확장자가 달라 별개 컷 → 2건. 동률 captureTime 은 확장자(JPG<NEF) 순 안정 정렬.
        val photos = captureRepo.getCapturedPhotos().first()
        assertEquals(2, photos.size)
        assertEquals(setOf("X.NEF", "X.JPG"), photos.map { basename(it.filePath) }.toSet())
    }

    @Test
    fun `동일 captureTime 은 basename 그다음 확장자 순으로 안정 정렬`() = runTest {
        // Arrange & Act: captureTime 동률 — basename 우선, 동일 basename 은 확장자 순
        captureRepo.seedDownloadedPhotoForTest(photo(filePath = "/s/B.JPG", captureTime = 1000))
        captureRepo.seedDownloadedPhotoForTest(photo(filePath = "/s/A.NEF", captureTime = 1000))
        captureRepo.seedDownloadedPhotoForTest(photo(filePath = "/s/A.JPG", captureTime = 1000))

        // Assert: (A,JPG) < (A,NEF) < (B,JPG)
        val photos = captureRepo.getCapturedPhotos().first()
        assertEquals(
            listOf("A.JPG", "A.NEF", "B.JPG"),
            photos.map { basename(it.filePath) }
        )
    }

    // --- Helpers ---

    private fun basename(filePath: String): String = filePath.substringAfterLast("/")

    private fun photo(
        name: String? = null,
        filePath: String = "/store/${name ?: "UNNAMED.JPG"}",
        captureTime: Long
    ): CapturedPhoto = CapturedPhoto(
        id = UUID.randomUUID().toString(),
        filePath = filePath,
        thumbnailPath = null,
        captureTime = captureTime,
        cameraModel = "TestCam",
        settings = null,
        size = 100,
        width = 0,
        height = 0,
        isDownloading = false,
        downloadCompleteTime = captureTime
    )
}
