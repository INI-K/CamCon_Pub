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
 * [PhotoPreviewViewModel] 의 `uiState.canAccessRawFormats` StateFlow 방출 검증.
 *
 * 구독 티어가 바뀔 때 미리보기 탭/전체포맷 접근 플래그가 올바르게 방출되는지 확인한다.
 * 게이팅 판정은 [ValidateFeatureAccessUseCase] 단일 지점에 위임되므로(CLAUDE.md §2),
 * 이 UseCase 는 무의존 순수 함수라 **실제 인스턴스**를 사용하고, 티어→boolean 매핑은
 * `isPhotoPreviewAllowed`(PRO/REFERRER/ADMIN 만 true)의 실제 로직을 그대로 관통시킨다.
 *
 * ViewModel 테스트 원칙: 구현 세부사항이 아닌 StateFlow 방출만 검증(Turbine).
 * 구독 티어는 [getSubscriptionUseCase] 의 콜드 flow 를 MutableStateFlow 로 구동해 전이시킨다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PhotoPreviewViewModelCanAccessRawTest {

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

    // 게이팅 단일 지점 — 무의존 순수 함수라 실제 인스턴스로 실제 티어 매핑을 관통.
    private val validateFeatureAccessUseCase = ValidateFeatureAccessUseCase()

    // 구독 티어 구동용. getSubscriptionTier() 가 이 flow 를 반환하도록 스텁.
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

        // init 에서 collect/first 되는 flow 만 명시 스텁(relaxed mock 의 flow 방출 불확정성 회피).
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

    @Test
    fun `FREE 티어는 canAccessRawFormats false 를 방출`() = runTest {
        tierFlow.value = SubscriptionTier.FREE
        val viewModel = createViewModel()

        viewModel.uiState.test {
            // 초기 방출(옵저버 실행 전 기본값)
            assertFalse(awaitItem().canAccessRawFormats)

            // 티어 옵저버 구동
            advanceUntilIdle()
            assertFalse(expectMostRecentItem().canAccessRawFormats)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `PRO REFERRER ADMIN 은 true, FREE BASIC 은 false 를 방출`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            // 초기 상태 소비 후 FREE 옵저버 구동
            awaitItem()
            advanceUntilIdle()
            assertFalse("FREE 는 접근 불가", expectMostRecentItem().canAccessRawFormats)

            tierFlow.value = SubscriptionTier.PRO
            advanceUntilIdle()
            assertTrue("PRO 는 접근 가능", expectMostRecentItem().canAccessRawFormats)

            tierFlow.value = SubscriptionTier.REFERRER
            advanceUntilIdle()
            assertTrue("REFERRER 는 접근 가능", expectMostRecentItem().canAccessRawFormats)

            tierFlow.value = SubscriptionTier.ADMIN
            advanceUntilIdle()
            assertTrue("ADMIN 은 접근 가능", expectMostRecentItem().canAccessRawFormats)

            tierFlow.value = SubscriptionTier.BASIC
            advanceUntilIdle()
            assertFalse("BASIC 은 접근 불가", expectMostRecentItem().canAccessRawFormats)

            cancelAndConsumeRemainingEvents()
        }
    }
}
