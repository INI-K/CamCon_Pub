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
import com.inik.camcon.domain.manager.ErrorNotifier
import com.inik.camcon.domain.manager.ErrorSeverity
import com.inik.camcon.domain.manager.ErrorType
import com.inik.camcon.domain.model.CapturedPhoto
import io.mockk.mockk
import io.mockk.verify
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
 * 테더링 저장 실패 → 사용자 통지 회귀 테스트 (M1 보완).
 *
 * 감사 #14의 본질 결함은 "저장 실패(디스크 풀 등) 시 배지만 조용히 사라지고 사용자 통지가 전무"였다.
 * 모든 실패 경로(네이티브 큐 실패·처리 예외·저장 null·PTPIP 실패·onDownloadFailed 콜백)가 수렴하는
 * [CameraCaptureRepositoryImpl.updatePhotoDownloadFailed] 가 (1) 실패 항목 제거와 (2) errorNotifier.emitError
 * 통지를 함께 수행하는지 검증한다. private 이므로 @VisibleForTesting markPhotoDownloadFailedForTest 진입점 사용.
 *
 * 핵심: 실패 시나리오에선 사진이 _capturedPhotos 에 등록된 적이 없어 제거 대상이 0건이어도, 통지는 반드시
 * 방출되어야 한다(그게 결함의 본질). ErrorNotifier 는 기존 UI 에러 채널(errorEvent→setError)로 노출된다.
 */
class CameraCaptureRepositoryImplDownloadFailedTest {

    private lateinit var captureRepo: CameraCaptureRepositoryImpl
    private lateinit var errorNotifier: ErrorNotifier

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
        errorNotifier = mockk(relaxed = true)

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
            errorNotifier = errorNotifier,
            processedFileCache = processedFileCache,
            cacheSweeper = cacheSweeper,
            scope = scope,
            ioDispatcher = ioDispatcher
        )
    }

    @Test
    fun `저장 실패 시 STORAGE-HIGH 통지를 파일명과 함께 방출한다`() = runTest {
        // Act: 저장 실패(파일명은 basename 으로 전달)
        captureRepo.markPhotoDownloadFailedForTest("KY6_0035.JPG")

        // Assert: 기존 UI 에러 채널로 유실을 알린다 — 메시지에 어떤 컷인지 식별 가능한 파일명 포함
        verify {
            errorNotifier.emitError(
                ErrorType.STORAGE,
                match { it.contains("KY6_0035.JPG") },
                any(),
                ErrorSeverity.HIGH
            )
        }
    }

    @Test
    fun `실패 사진이 목록에 없어도(제거 0건) 통지는 방출된다`() = runTest {
        // Arrange: 실패 경로의 실제 프로덕션 상태 — 실패한 컷은 _capturedPhotos 에 등록된 적이 없다.
        captureRepo.seedDownloadedPhotoForTest(
            photo(filePath = "/store/OTHER.JPG", captureTime = 1000)
        )

        // Act: 목록에 없는 파일의 저장 실패
        captureRepo.markPhotoDownloadFailedForTest("MISSING.JPG")

        // Assert: 제거 대상이 0건이어도(무관 항목 유지) 사용자 통지는 반드시 방출 — 이게 결함의 본질.
        verify {
            errorNotifier.emitError(
                ErrorType.STORAGE,
                match { it.contains("MISSING.JPG") },
                any(),
                ErrorSeverity.HIGH
            )
        }
        val photos = captureRepo.getCapturedPhotos().first()
        assertEquals(1, photos.size)
        assertEquals("OTHER.JPG", photos.first().filePath.substringAfterLast("/"))
    }

    @Test
    fun `등록된 실패 항목은 basename 키로 제거되며 통지도 방출된다`() = runTest {
        // Arrange: 전체 로컬 경로로 등록(등록 키 = basename), 서로 다른 두 컷
        captureRepo.seedDownloadedPhotoForTest(
            photo(filePath = "/storage/emulated/0/DCIM/A.JPG", captureTime = 1000)
        )
        captureRepo.seedDownloadedPhotoForTest(
            photo(filePath = "/storage/emulated/0/DCIM/B.JPG", captureTime = 2000)
        )

        // Act: A 만 실패
        captureRepo.markPhotoDownloadFailedForTest("A.JPG")

        // Assert: (1) basename 키 정합으로 A 만 제거, B 유지
        val photos = captureRepo.getCapturedPhotos().first()
        assertEquals(1, photos.size)
        assertEquals("B.JPG", photos.first().filePath.substringAfterLast("/"))
        // (2) 통지 방출
        verify {
            errorNotifier.emitError(
                ErrorType.STORAGE,
                match { it.contains("A.JPG") },
                any(),
                ErrorSeverity.HIGH
            )
        }
    }

    private fun photo(filePath: String, captureTime: Long): CapturedPhoto = CapturedPhoto(
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
