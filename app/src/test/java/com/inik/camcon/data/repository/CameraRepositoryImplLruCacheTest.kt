package com.inik.camcon.data.repository

import android.content.Context
import com.inik.camcon.data.datasource.nativesource.NativeCameraDataSource
import com.inik.camcon.data.datasource.ptpip.PtpipDataSource
import com.inik.camcon.data.datasource.local.AppPreferencesDataSource
import com.inik.camcon.data.datasource.usb.UsbCameraManager
import com.inik.camcon.data.repository.managers.CameraConnectionManager
import com.inik.camcon.data.repository.managers.CameraEventManager
import com.inik.camcon.data.repository.managers.PhotoDownloadManager
import com.inik.camcon.domain.manager.CameraStateObserver
import com.inik.camcon.domain.manager.ErrorHandlingManager
import com.inik.camcon.domain.repository.ColorTransferRepository
import com.inik.camcon.domain.usecase.GetSubscriptionUseCase
import com.inik.camcon.domain.usecase.camera.PhotoCaptureEventManager
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * LRU 캐시 회귀 테스트: processedFiles LinkedHashMap이 정확히 1000개 항목 제한을 준수하는지 검증
 * Issue C5: processedFiles OOM 위험 (타임랩스 장기 사용)
 *
 * 테스트 내용:
 * - 1000개 항목까지 추가하면 캐시가 정상 작동
 * - 1001번째 항목 추가 시 가장 오래된 항목이 자동 제거됨 (LRU 정책)
 * - 타임랩스 같은 장기 촬영에서 메모리 누수 방지 확인
 *
 * Repository init 블록 try-catch 적용으로 모의 객체들의 초기화 실패 방지됨 (2026-04-22 fix)
 */
class CameraRepositoryImplLruCacheTest {

    private lateinit var repository: CameraRepositoryImpl

    @Before
    fun setUp() {
        // Mock 모든 의존성
        val context = mockk<Context>()
        val nativeDataSource = mockk<NativeCameraDataSource>(relaxed = true)
        val ptpipDataSource = mockk<PtpipDataSource>(relaxed = true)
        val usbCameraManager = mockk<UsbCameraManager>(relaxed = true)
        val photoCaptureEventManager = mockk<PhotoCaptureEventManager>(relaxed = true)
        val appPreferencesDataSource = mockk<AppPreferencesDataSource>(relaxed = true)
        val colorTransferRepository = mockk<ColorTransferRepository>(relaxed = true)
        val connectionManager = mockk<CameraConnectionManager>(relaxed = true)
        val eventManager = mockk<CameraEventManager>(relaxed = true)
        val downloadManager = mockk<PhotoDownloadManager>(relaxed = true)
        val cameraStateObserver = mockk<CameraStateObserver>(relaxed = true)
        val getSubscriptionUseCase = mockk<GetSubscriptionUseCase>(relaxed = true)
        val errorHandlingManager = mockk<ErrorHandlingManager>(relaxed = true)
        val scope = mockk<CoroutineScope>(relaxed = true)
        val ioDispatcher: CoroutineDispatcher = StandardTestDispatcher()

        // Repository 초기화 (생성자 순서와 정확히 일치)
        repository = CameraRepositoryImpl(
            context = context,
            nativeDataSource = nativeDataSource,
            ptpipDataSource = ptpipDataSource,
            usbCameraManager = usbCameraManager,
            photoCaptureEventManager = photoCaptureEventManager,
            appPreferencesDataSource = appPreferencesDataSource,
            colorTransferRepository = colorTransferRepository,
            connectionManager = connectionManager,
            eventManager = eventManager,
            downloadManager = downloadManager,
            cameraStateObserver = cameraStateObserver,
            getSubscriptionUseCase = getSubscriptionUseCase,
            errorHandlingManager = errorHandlingManager,
            scope = scope,
            ioDispatcher = ioDispatcher
        )
    }

    /**
     * Test 1: 캐시가 정상적으로 항목을 추가하고 1000개 제한을 준수하는가?
     */
    @Test
    fun cacheRespects1000ItemLimitOnOverflow() = runTest {
        // 1001개의 고유 파일 경로 생성 및 처리
        val filePaths = (0..1000).map { i -> "photo_${String.format("%04d", i)}.jpg" }

        filePaths.forEach { filePath ->
            repository.markFileAsProcessed(filePath)
        }

        // 캐시 크기가 1000을 초과하지 않는지 확인
        val cacheSize = repository.getProcessedFilesCount()
        assertTrue("캐시 크기($cacheSize)가 1000을 초과함", cacheSize <= 1000)
        assertEquals("정확히 1000개 항목이 캐시되어야 함", 1000, cacheSize)
    }

    /**
     * Test 2: LRU 정책 — 가장 오래된 항목이 제거되는가?
     */
    @Test
    fun oldestItemRemovedWhenCacheExceeds1000Items() = runTest {
        // 처음 1000개 추가
        (0..999).forEach { i ->
            val filePath = "photo_${String.format("%04d", i)}.jpg"
            repository.markFileAsProcessed(filePath)
        }

        // 가장 오래된 항목의 경로 저장
        val oldestFilePath = "photo_0000.jpg"

        // 1001번째 항목 추가 (새로운 항목)
        repository.markFileAsProcessed("photo_1000.jpg")

        // 가장 오래된 항목이 제거되었는지 확인
        val isOldestRemoved = !repository.isFileProcessed(oldestFilePath)
        assertTrue("가장 오래운 항목($oldestFilePath)이 제거되어야 함", isOldestRemoved)

        // 새로운 항목이 추가되었는지 확인
        val isNewItemPresent = repository.isFileProcessed("photo_1000.jpg")
        assertTrue("새로운 항목(photo_1000.jpg)이 캐시에 있어야 함", isNewItemPresent)
    }

    /**
     * Test 3: 타임랩스 시뮬레이션 — 지속적인 추가 시에도 메모리 안전성
     */
    @Test
    fun timelapseScenarioWith5000SequentialAddsDoesNotExceedCacheLimit() = runTest {
        // 타임랩스처럼 5000개의 사진을 순차적으로 처리 (메모리 누수 시나리오)
        repeat(5000) { i ->
            val filePath = "timelapse_frame_${String.format("%05d", i)}.jpg"
            repository.markFileAsProcessed(filePath)
        }

        // 최종 캐시 크기가 여전히 1000 이하인지 확인
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

        // 같은 파일을 10번 추가
        repeat(10) {
            repository.markFileAsProcessed(filePath)
        }

        // 캐시 크기가 1이어야 함 (중복 제거)
        val cacheSize = repository.getProcessedFilesCount()
        assertEquals("중복된 파일은 한 번만 저장되어야 함", 1, cacheSize)
    }

    /**
     * Test 5: 경계 케이스 — 정확히 1000개 추가 후 상태
     */
    @Test
    fun cacheStateIsValidAtExactly1000Items() = runTest {
        // 정확히 1000개 추가
        (0..999).forEach { i ->
            val filePath = "photo_${String.format("%04d", i)}.jpg"
            repository.markFileAsProcessed(filePath)
        }

        // 캐시 크기 확인
        val cacheSize = repository.getProcessedFilesCount()
        assertEquals("1000개 추가 후 캐시 크기는 1000이어야 함", 1000, cacheSize)

        // 마지막 항목이 존재해야 함
        val lastItemPath = "photo_0999.jpg"
        assertTrue("마지막 항목이 캐시에 있어야 함", repository.isFileProcessed(lastItemPath))
    }
}
