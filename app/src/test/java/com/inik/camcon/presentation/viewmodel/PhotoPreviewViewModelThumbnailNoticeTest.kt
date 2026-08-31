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
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [PhotoPreviewViewModel.showThumbnailLimitNotice] StateFlow 방출 검증.
 *
 * 고정하는 계약은 셋이다.
 *
 * 1. **판정 신호는 실동작 하나뿐이다.** 카메라가 돌려준 썸네일이 JPEG 서명을 만족하지 못했을
 *    때만 안내한다([PhotoImageManager.thumbnailUnsupported]). 기종명·오퍼레이션 광고·설명 XML 은
 *    쓰지 않는다 — 같은 세대 안에 깨진 바디(a7M5)와 멀쩡한 바디(a7M4)가 섞여 있어 그런 신호로
 *    판정하면 멀쩡한 카메라에 오탐이 난다.
 * 2. **정상 카메라에서는 뜨지 않는다.**
 * 3. **세션당 한 번만 뜬다.** 사용자가 확인해 닫은 뒤 다시 띄우지 않는다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PhotoPreviewViewModelThumbnailNoticeTest {

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

    /** 썸네일 실동작 판정을 테스트가 직접 구동한다. */
    private lateinit var thumbnailUnsupportedFlow: MutableStateFlow<Boolean>

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

        thumbnailUnsupportedFlow = MutableStateFlow(false)

        every { getSubscriptionUseCase.getSubscriptionTier() } returns
            MutableStateFlow(SubscriptionTier.PRO)
        every { cameraRepository.isPtpipConnected() } returns flowOf(true)
        every { cameraRepository.isInitializing() } returns flowOf(false)
        every { appSettingsRepository.isRawFileDownloadEnabled } returns flowOf(true)
        every { globalManager.globalConnectionState } returns
            MutableStateFlow(GlobalCameraConnectionState())
        every { errorHandlingManager.errorEvent } returns MutableSharedFlow<ErrorEvent>()
        every { photoImageManager.downloadResult } returns
            MutableSharedFlow<PhotoImageManager.DownloadResult>()
        every { photoImageManager.thumbnailUnsupported } returns thumbnailUnsupportedFlow
        every { photoListManager.filteredPhotos } returns
            MutableStateFlow(emptyList<CameraPhoto>())
        every { photoListManager.currentFilter } returns MutableStateFlow(FileTypeFilter.JPG)
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
        deleteCameraFileUseCase = deleteCameraFileUseCase,
        nikonApplicationModeManager = mockk(relaxed = true),
        ptpipEventKeepAlive = mockk(relaxed = true)
    )

    @Test
    fun `썸네일이 JPEG 이 아니면 안내를 방출한다`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showThumbnailLimitNotice.test {
            assertFalse("판정 전에는 뜨지 않는다", awaitItem())

            thumbnailUnsupportedFlow.value = true
            advanceUntilIdle()

            assertTrue("실제로 못 쓰는 것을 확인했으므로 알려야 한다", expectMostRecentItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `썸네일이 정상인 카메라에서는 안내하지 않는다`() = runTest {
        // a7M4 처럼 신형 콘텐츠 API 를 지원하면서도 GetThumb 이 멀쩡한 바디가 여기 해당한다.
        // 광고나 세대 신호로 판정했다면 이 카메라에 오탐이 났을 자리다.
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showThumbnailLimitNotice.test {
            assertFalse(awaitItem())

            // 썸네일을 여러 장 정상 수신해도 판정 flow 는 false 그대로다.
            advanceUntilIdle()

            expectNoEvents()
            assertFalse("정상 카메라에 안내를 띄우면 거짓말이 된다", viewModel.showThumbnailLimitNotice.value)
        }
    }

    @Test
    fun `사용자가 확인하면 안내가 닫힌다`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        thumbnailUnsupportedFlow.value = true
        advanceUntilIdle()

        viewModel.showThumbnailLimitNotice.test {
            assertTrue(awaitItem())

            viewModel.dismissThumbnailLimitNotice()

            assertFalse(awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `한 세션에서 판정이 되풀이돼도 안내는 한 번만 뜬다`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        thumbnailUnsupportedFlow.value = true
        advanceUntilIdle()
        assertTrue(viewModel.showThumbnailLimitNotice.value)

        viewModel.dismissThumbnailLimitNotice()
        assertFalse(viewModel.showThumbnailLimitNotice.value)

        // 새 세션 판정이 풀렸다가 다시 미지원으로 확정되는 흐름을 흉내낸다.
        thumbnailUnsupportedFlow.value = false
        advanceUntilIdle()
        thumbnailUnsupportedFlow.value = true
        advanceUntilIdle()

        assertFalse("같은 세션에서 되풀이하면 방해가 된다", viewModel.showThumbnailLimitNotice.value)
    }
}
