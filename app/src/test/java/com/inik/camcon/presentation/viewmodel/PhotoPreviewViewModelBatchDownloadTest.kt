package com.inik.camcon.presentation.viewmodel

import android.content.Context
import app.cash.turbine.test
import com.inik.camcon.domain.manager.CameraConnectionGlobalManager
import com.inik.camcon.domain.manager.ErrorEvent
import com.inik.camcon.domain.model.CameraPhoto
import com.inik.camcon.domain.model.GlobalCameraConnectionState
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.repository.AppSettingsRepository
import com.inik.camcon.domain.repository.CameraRepository
import com.inik.camcon.domain.usecase.GetSubscriptionUseCase
import com.inik.camcon.domain.usecase.ValidateFeatureAccessUseCase
import com.inik.camcon.domain.usecase.ValidateImageFormatUseCase
import com.inik.camcon.domain.usecase.camera.DeleteCameraFileUseCase
import com.inik.camcon.domain.usecase.camera.ResumeNativeOperationsUseCase
import com.inik.camcon.presentation.viewmodel.photo.FileTypeFilter
import com.inik.camcon.presentation.viewmodel.photo.PhotoImageManager
import com.inik.camcon.presentation.viewmodel.photo.PhotoListManager
import com.inik.camcon.presentation.viewmodel.photo.PhotoSelectionManager
import com.inik.camcon.presentation.viewmodel.state.ErrorHandlingManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * [PhotoPreviewViewModel.downloadSelectedPhotos] 의 유계 동시성·진행 집계·부분 실패 방출 검증.
 *
 * 감사 확정: 일괄 다운로드가 전건 동시 발사되어 후순위 항목이 JNI 60초 대기(제출 시점 기산)로
 * 구조적 타임아웃하던 문제의 수정. 세마포어로 동시성을 상한(3)으로 제한하고, n/m 진행을 집계하며,
 * 실패 항목이 개별 식별·집계되는지 StateFlow/SharedFlow 방출로 확인한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PhotoPreviewViewModelBatchDownloadTest {

    // 구현부 companion 의 MAX_CONCURRENT_DOWNLOADS 와 일치해야 한다(private 이라 하드코딩).
    private val expectedConcurrencyCap = 3

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var context: Context
    private lateinit var cameraRepository: CameraRepository
    private lateinit var globalManager: CameraConnectionGlobalManager
    private lateinit var getSubscriptionUseCase: GetSubscriptionUseCase
    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var validateImageFormatUseCase: ValidateImageFormatUseCase
    private lateinit var photoListManager: PhotoListManager
    private lateinit var photoImageManager: PhotoImageManager
    private lateinit var photoSelectionManager: PhotoSelectionManager
    private lateinit var errorHandlingManager: ErrorHandlingManager
    private lateinit var resumeNativeOperationsUseCase: ResumeNativeOperationsUseCase
    private lateinit var deleteCameraFileUseCase: DeleteCameraFileUseCase

    private val validateFeatureAccessUseCase = ValidateFeatureAccessUseCase()

    private lateinit var tierFlow: MutableStateFlow<SubscriptionTier>

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = mockk(relaxed = true)
        cameraRepository = mockk(relaxed = true)
        globalManager = mockk(relaxed = true)
        getSubscriptionUseCase = mockk(relaxed = true)
        appSettingsRepository = mockk(relaxed = true)
        validateImageFormatUseCase = mockk(relaxed = true)
        photoListManager = mockk(relaxed = true)
        photoImageManager = mockk(relaxed = true)
        photoSelectionManager = mockk(relaxed = true)
        errorHandlingManager = mockk(relaxed = true)
        resumeNativeOperationsUseCase = mockk(relaxed = true)
        deleteCameraFileUseCase = mockk(relaxed = true)

        tierFlow = MutableStateFlow(SubscriptionTier.PRO)

        every { getSubscriptionUseCase.getSubscriptionTier() } returns tierFlow
        every { cameraRepository.isPtpipConnected() } returns flowOf(false)
        every { cameraRepository.isInitializing() } returns flowOf(false)
        every { appSettingsRepository.isRawFileDownloadEnabled } returns flowOf(true)
        every { globalManager.globalConnectionState } returns
            MutableStateFlow(GlobalCameraConnectionState())
        every { errorHandlingManager.errorEvent } returns MutableSharedFlow<ErrorEvent>()
        every { photoImageManager.downloadResult } returns
            MutableSharedFlow<PhotoImageManager.DownloadResult>()
        every { photoListManager.filteredPhotos } returns
            MutableStateFlow(emptyList<CameraPhoto>())
        every { photoListManager.currentFilter } returns
            MutableStateFlow(FileTypeFilter.JPG)

        // RAW 게이팅(동기)은 전부 통과시켜 eligiblePaths = 선택 전량이 되게 한다.
        every {
            validateImageFormatUseCase.resolveRawAccess(any(), any(), any())
        } returns ValidateImageFormatUseCase.ValidationResult(isSupported = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createViewModel(): PhotoPreviewViewModel = PhotoPreviewViewModel(
        context = context,
        cameraRepository = cameraRepository,
        globalManager = globalManager,
        getSubscriptionUseCase = getSubscriptionUseCase,
        appSettingsRepository = appSettingsRepository,
        validateImageFormatUseCase = validateImageFormatUseCase,
        validateFeatureAccessUseCase = validateFeatureAccessUseCase,
        photoListManager = photoListManager,
        photoImageManager = photoImageManager,
        photoSelectionManager = photoSelectionManager,
        errorHandlingManager = errorHandlingManager,
        resumeNativeOperationsUseCase = resumeNativeOperationsUseCase,
        deleteCameraFileUseCase = deleteCameraFileUseCase
    )

    private fun paths(n: Int): List<String> =
        (1..n).map { "/store_00010001/DCIM/100NIKON/DSC_%04d.JPG".format(it) }

    @Test
    fun `일괄 다운로드는 동시성 상한을 넘지 않는다`() = runTest(testDispatcher) {
        val selected = paths(10)
        every { photoSelectionManager.getSelectedPaths() } returns selected.toSet()

        val inFlight = AtomicInteger(0)
        val maxInFlight = AtomicInteger(0)
        coEvery { photoImageManager.downloadAndPersist(any(), any()) } coAnswers {
            val now = inFlight.incrementAndGet()
            maxInFlight.updateAndGet { m -> maxOf(m, now) }
            delay(100) // 가상 시간 — 동시 점유 구간 형성
            inFlight.decrementAndGet()
            true
        }

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.downloadSelectedPhotos()
        advanceUntilIdle()

        assertEquals(
            "10건이라도 동시 실행은 상한으로 제한되어야 한다",
            expectedConcurrencyCap,
            maxInFlight.get()
        )
        // 전건이 처리됐는지(집계 완주) 확인.
        coVerify(exactly = 10) { photoImageManager.downloadAndPersist(any(), any()) }
    }

    @Test
    fun `진행 집계가 n에서 m으로 증가하고 완료 시 리셋된다`() = runTest(testDispatcher) {
        val selected = paths(3) // 상한(3) 이하라 3건 모두 즉시 점유
        every { photoSelectionManager.getSelectedPaths() } returns selected.toSet()

        val gates = selected.associateWith { CompletableDeferred<Boolean>() }
        coEvery { photoImageManager.downloadAndPersist(any(), any()) } coAnswers {
            gates.getValue(firstArg<String>()).await()
        }

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.downloadSelectedPhotos()
        advanceUntilIdle()

        // 배치 시작: total=3, completed=0, inProgress
        var progress = viewModel.multiDownloadProgress.value
        assertTrue(progress.inProgress)
        assertEquals(3, progress.total)
        assertEquals(0, progress.completed)

        // 1건 성공 → completed=1
        gates.getValue(selected[0]).complete(true)
        advanceUntilIdle()
        progress = viewModel.multiDownloadProgress.value
        assertEquals(1, progress.completed)
        assertEquals(0, progress.failed)

        // 1건 실패 → completed=2, failed=1
        gates.getValue(selected[1]).complete(false)
        advanceUntilIdle()
        progress = viewModel.multiDownloadProgress.value
        assertEquals(2, progress.completed)
        assertEquals(1, progress.failed)

        // 마지막 1건 성공 → 배치 완료 → 진행 상태 리셋
        gates.getValue(selected[2]).complete(true)
        advanceUntilIdle()
        assertEquals(MultiDownloadProgress(), viewModel.multiDownloadProgress.value)
    }

    @Test
    fun `부분 실패 시 partial 에러 이벤트를 방출하고 마지막 실패를 재시도 대상으로 보존한다`() =
        runTest(testDispatcher) {
            val selected = paths(4)
            every { photoSelectionManager.getSelectedPaths() } returns selected.toSet()

            // p2, p4 실패.
            coEvery { photoImageManager.downloadAndPersist(any(), any()) } coAnswers {
                val path = firstArg<String>()
                path != selected[1] && path != selected[3]
            }

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.uiEvent.test {
                viewModel.downloadSelectedPhotos()
                advanceUntilIdle()

                val event = awaitItem()
                assertTrue(
                    "부분 실패는 재시도 유도용 에러 토스트로 방출",
                    event is PhotoPreviewUiEvent.ShowError
                )
                cancelAndConsumeRemainingEvents()
            }

            // 실패 항목이 개별 식별되어 재시도 대상으로 보존.
            assertTrue(viewModel.lastFailedDownload.value != null)
        }

    @Test
    fun `전건 성공 시 완료 안내 이벤트를 방출한다`() = runTest(testDispatcher) {
        val selected = paths(5)
        every { photoSelectionManager.getSelectedPaths() } returns selected.toSet()
        coEvery { photoImageManager.downloadAndPersist(any(), any()) } returns true

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.uiEvent.test {
            viewModel.downloadSelectedPhotos()
            advanceUntilIdle()

            val event = awaitItem()
            assertTrue(
                "전건 성공은 안내(Info) 토스트로 방출",
                event is PhotoPreviewUiEvent.ShowInfo
            )
            cancelAndConsumeRemainingEvents()
        }
        coVerify(exactly = 5) { photoImageManager.downloadAndPersist(any(), any()) }
    }
}
