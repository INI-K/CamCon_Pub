package com.inik.camcon.data.repository

import android.content.Context
import com.inik.camcon.data.datasource.nativesource.NativeCameraDataSource
import com.inik.camcon.data.datasource.ptpip.PtpipDataSource
import com.inik.camcon.data.datasource.usb.UsbCameraManager
import com.inik.camcon.data.repository.managers.CameraConnectionManager
import com.inik.camcon.data.repository.managers.CameraEventManager
import com.inik.camcon.data.repository.managers.PhotoDownloadManager
import com.inik.camcon.domain.manager.CameraStateObserver
import com.inik.camcon.domain.repository.ColorTransferRepository
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * LRU 캐시 회귀 테스트: processedFiles LinkedHashMap이 정확히 1000개 항목 제한을 준수하는지 검증
 * Issue C5: processedFiles OOM 위험 (타임랩스 장기 사용)
 *
 * H8 분해(2026-04-23) 후: CameraRepositoryImpl은 얇은 Facade. LRU는 CameraCaptureRepositoryImpl 소유.
 * Facade가 markFileAsProcessed/isFileProcessed/getProcessedFilesCount를 captureRepo로 delegate.
 * 이 테스트는 Facade 공개 API를 통해 LRU 회귀를 검증 — 실제 Capture sub-impl을 주입하여 LRU 동작 유지.
 */
class CameraRepositoryImplLruCacheTest {

    private lateinit var repository: CameraRepositoryImpl

    @Before
    fun setUp() {
        val context = mockk<Context>(relaxed = true)
        val colorTransferRepository = mockk<ColorTransferRepository>(relaxed = true)
        val cameraStateObserver = mockk<CameraStateObserver>(relaxed = true)

        // Capture sub-impl의 의존성 mocks
        val nativeDataSource = mockk<NativeCameraDataSource>(relaxed = true)
        val ptpipDataSource = mockk<PtpipDataSource>(relaxed = true)
        val usbCameraManager = mockk<UsbCameraManager>(relaxed = true)
        val connectionManager = mockk<CameraConnectionManager>(relaxed = true)
        val eventManager = mockk<CameraEventManager>(relaxed = true)
        val downloadManager = mockk<PhotoDownloadManager>(relaxed = true)
        val scope = mockk<CoroutineScope>(relaxed = true)
        val ioDispatcher: CoroutineDispatcher = StandardTestDispatcher()

        // LRU 캐시를 보유한 실제 Capture sub-impl
        val captureRepo = CameraCaptureRepositoryImpl(
            context = context,
            nativeDataSource = nativeDataSource,
            ptpipDataSource = ptpipDataSource,
            usbCameraManager = usbCameraManager,
            connectionManager = connectionManager,
            eventManager = eventManager,
            downloadManager = downloadManager,
            scope = scope,
            ioDispatcher = ioDispatcher
        )

        val lifecycleRepo = mockk<CameraLifecycleRepositoryImpl>(relaxed = true)
        val controlRepo = mockk<CameraControlRepositoryImpl>(relaxed = true)

        repository = CameraRepositoryImpl(
            context = context,
            colorTransferRepository = colorTransferRepository,
            cameraStateObserver = cameraStateObserver,
            lifecycleRepo = lifecycleRepo,
            captureRepo = captureRepo,
            controlRepo = controlRepo
        )
    }

    /**
     * Test 1: 캐시가 정상적으로 항목을 추가하고 1000개 제한을 준수하는가?
     */
    @Test
    fun cacheRespects1000ItemLimitOnOverflow() = runTest {
        val filePaths = (0..1000).map { i -> "photo_${String.format("%04d", i)}.jpg" }

        filePaths.forEach { filePath ->
            repository.markFileAsProcessed(filePath)
        }

        val cacheSize = repository.getProcessedFilesCount()
        assertTrue("캐시 크기($cacheSize)가 1000을 초과함", cacheSize <= 1000)
        assertEquals("정확히 1000개 항목이 캐시되어야 함", 1000, cacheSize)
    }

    /**
     * Test 2: LRU 정책 — 가장 오래된 항목이 제거되는가?
     */
    @Test
    fun oldestItemRemovedWhenCacheExceeds1000Items() = runTest {
        (0..999).forEach { i ->
            val filePath = "photo_${String.format("%04d", i)}.jpg"
            repository.markFileAsProcessed(filePath)
        }

        val oldestFilePath = "photo_0000.jpg"
        repository.markFileAsProcessed("photo_1000.jpg")

        val isOldestRemoved = !repository.isFileProcessed(oldestFilePath)
        assertTrue("가장 오래운 항목($oldestFilePath)이 제거되어야 함", isOldestRemoved)

        val isNewItemPresent = repository.isFileProcessed("photo_1000.jpg")
        assertTrue("새로운 항목(photo_1000.jpg)이 캐시에 있어야 함", isNewItemPresent)
    }

    /**
     * Test 3: 타임랩스 시뮬레이션 — 지속적인 추가 시에도 메모리 안전성
     */
    @Test
    fun timelapseScenarioWith5000SequentialAddsDoesNotExceedCacheLimit() = runTest {
        repeat(5000) { i ->
            val filePath = "timelapse_frame_${String.format("%05d", i)}.jpg"
            repository.markFileAsProcessed(filePath)
        }

        val finalCacheSize = repository.getProcessedFilesCount()
        assertTrue("타임랩스 후 캐시 크기($finalCacheSize)가 1000을 초과함", finalCacheSize <= 1000)
        assertEquals("정확히 1000개만 유지되어야 함", 1000, finalCacheSize)
    }

    /**
     * Test 4: 중복 처리 방지 — 같은 파일이 다시 추가되면 카운트 증가 안함
     */
    @Test
    fun duplicateFilePathsDoNotIncreaseCacheSize() = runTest {
        val filePath = "photo_duplicate.jpg"

        repeat(10) {
            repository.markFileAsProcessed(filePath)
        }

        val cacheSize = repository.getProcessedFilesCount()
        assertEquals("중복된 파일은 한 번만 저장되어야 함", 1, cacheSize)
    }

    /**
     * Test 5: 경계 케이스 — 정확히 1000개 추가 후 상태
     */
    @Test
    fun cacheStateIsValidAtExactly1000Items() = runTest {
        (0..999).forEach { i ->
            val filePath = "photo_${String.format("%04d", i)}.jpg"
            repository.markFileAsProcessed(filePath)
        }

        val cacheSize = repository.getProcessedFilesCount()
        assertEquals("1000개 추가 후 캐시 크기는 1000이어야 함", 1000, cacheSize)

        val lastItemPath = "photo_0999.jpg"
        assertTrue("마지막 항목이 캐시에 있어야 함", repository.isFileProcessed(lastItemPath))
    }
}
