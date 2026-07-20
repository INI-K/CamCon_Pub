package com.inik.camcon.presentation.viewmodel

import android.content.Context
import app.cash.turbine.test
import com.inik.camcon.domain.manager.CameraConnectionGlobalManager
import com.inik.camcon.domain.manager.ErrorEvent
import com.inik.camcon.domain.model.CameraPhoto
import com.inik.camcon.domain.model.GlobalCameraConnectionState
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.model.UiText
import com.inik.camcon.domain.repository.AppSettingsRepository
import com.inik.camcon.domain.repository.CameraRepository
import com.inik.camcon.domain.usecase.GetSubscriptionUseCase
import com.inik.camcon.domain.usecase.ValidateFeatureAccessUseCase
import com.inik.camcon.domain.usecase.ValidateImageFormatUseCase
import com.inik.camcon.domain.usecase.ValidateImageFormatUseCase.ValidationResult
import com.inik.camcon.domain.usecase.camera.DeleteCameraFileUseCase
import com.inik.camcon.domain.usecase.camera.ResumeNativeOperationsUseCase
import com.inik.camcon.presentation.viewmodel.photo.FileTypeFilter
import com.inik.camcon.presentation.viewmodel.photo.PhotoImageManager
import com.inik.camcon.presentation.viewmodel.photo.PhotoListManager
import com.inik.camcon.presentation.viewmodel.photo.PhotoSelectionManager
import com.inik.camcon.presentation.viewmodel.state.ErrorHandlingManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [PhotoPreviewViewModel] 의 삭제 실패 통지(M14)·재시도 RAW 게이트(M15) SharedFlow 방출 검증.
 *
 * ViewModel 테스트 원칙: 구현 세부사항이 아닌 `uiEvent`(SharedFlow) 방출과
 * 협력 매니저 호출 여부만 검증한다(CLAUDE.md §5).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PhotoPreviewViewModelDeleteRetryTest {

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

        tierFlow = MutableStateFlow(SubscriptionTier.FREE)

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

    private fun cameraPhoto(path: String) = CameraPhoto(
        path = path,
        name = path.substringAfterLast('/'),
        size = 1_000L,
        date = 0L
    )

    // ── M14: 카메라 측 삭제 실패 통지 ──

    @Test
    fun `카메라 측 삭제 실패 시 uiEvent 로 ShowError 방출`() = runTest {
        // 카메라 삭제가 실패(예: Nikon AccessDenied 0x200F)를 반환하도록 스텁.
        coEvery { deleteCameraFileUseCase(any(), any()) } returns
            Result.failure(RuntimeException("0x200F"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.uiEvent.test {
            viewModel.deletePhoto(cameraPhoto("/store_00010001/DCIM/100NIKON/DSC_0001.JPG"))
            advanceUntilIdle()

            val event = awaitItem()
            assertTrue(
                "카메라 삭제 실패는 ShowError 로 통지되어야 한다",
                event is PhotoPreviewUiEvent.ShowError
            )
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `카메라 측 삭제 성공 시 ShowError 미방출`() = runTest {
        coEvery { deleteCameraFileUseCase(any(), any()) } returns Result.success(Unit)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.uiEvent.test {
            viewModel.deletePhoto(cameraPhoto("/store_00010001/DCIM/100NIKON/DSC_0001.JPG"))
            advanceUntilIdle()

            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    // ── M15: 재시도 다운로드 RAW 게이트 경유 ──

    @Test
    fun `retryDownload 는 RAW 차단 시 다운로드를 호출하지 않고 통지`() = runTest {
        every {
            validateImageFormatUseCase.resolveRawAccess(any(), any(), any())
        } returns ValidationResult(
            isSupported = false,
            restrictionMessage = UiText.Resource(com.inik.camcon.R.string.close),
            needsUpgrade = true,
            isRawFile = true
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        val raw = cameraPhoto("/store_00010001/DCIM/100NIKON/DSC_0001.NEF")

        viewModel.uiEvent.test {
            viewModel.retryDownload(raw)
            advanceUntilIdle()

            val event = awaitItem()
            assertTrue(
                "RAW 차단 재시도는 ShowError 로 통지되어야 한다",
                event is PhotoPreviewUiEvent.ShowError
            )
            cancelAndConsumeRemainingEvents()
        }

        // 형제 경로와 동일하게 게이트에서 막혀 실제 다운로드는 발사되지 않아야 한다.
        verify(exactly = 0) { photoImageManager.downloadFullImage(any(), any(), any()) }
    }

    @Test
    fun `retryDownload 는 RAW 허용 시 persistToDevice 로 다운로드 호출`() = runTest {
        every {
            validateImageFormatUseCase.resolveRawAccess(any(), any(), any())
        } returns ValidationResult(isSupported = true, isRawFile = true)
        val viewModel = createViewModel()
        advanceUntilIdle()

        val path = "/store_00010001/DCIM/100NIKON/DSC_0002.NEF"
        viewModel.retryDownload(cameraPhoto(path))
        advanceUntilIdle()

        verify(exactly = 1) {
            photoImageManager.downloadFullImage(eq(path), any(), eq(true))
        }
    }
}
