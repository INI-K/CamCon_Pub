package com.inik.camcon.presentation.viewmodel

import android.content.Context
import app.cash.turbine.test
import com.inik.camcon.domain.model.LiveViewQuality
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.repository.AppSettingsRepository
import com.inik.camcon.domain.usecase.ColorTransferUseCase
import com.inik.camcon.domain.usecase.GetSubscriptionUseCase
import com.inik.camcon.domain.usecase.camera.ReadNativeLogUseCase
import com.inik.camcon.domain.usecase.camera.StartNativeLogUseCase
import com.inik.camcon.domain.usecase.camera.StopNativeLogUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * 라이브뷰 화질 ViewModel 노출/세터 단위 테스트.
 *
 * 설계서: `.claude/_workspace/liveview_quality_design.md` §11
 * 대상: [AppSettingsViewModel.liveViewQuality] StateFlow + [AppSettingsViewModel.setLiveViewQuality].
 *
 * ViewModel 테스트 원칙(프로젝트 규약): 구현 세부가 아니라 **StateFlow 방출**을 검증한다.
 *  - 초기값 = QUALITY (stateIn initialValue)
 *  - repository.liveViewQuality 가 다른 값을 방출하면 StateFlow 도 그 값으로 수렴
 *  - setLiveViewQuality(q) 호출 → repository.setLiveViewQuality(q) 위임 (coVerify)
 *
 * 협력자 처리:
 *  - appSettingsRepository: relaxed mock. ViewModel 생성자가 다수 Flow 프로퍼티를 즉시 stateIn 하므로
 *    relaxed 로 두고, 본 테스트가 보는 `liveViewQuality` 만 MutableStateFlow 로 명시 stub 해
 *    set→방출 연동을 모사한다.
 *  - getSubscriptionUseCase.getSubscriptionTier(): isLiveViewEnabled/isAdminTier 가 생성 시 참조 →
 *    FREE Flow stub.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppSettingsViewModelLiveViewQualityTest {

    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var getSubscriptionUseCase: GetSubscriptionUseCase
    private lateinit var context: Context

    private val liveViewQualityFlow = MutableStateFlow(LiveViewQuality.QUALITY)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        appSettingsRepository = mockk(relaxed = true)
        getSubscriptionUseCase = mockk(relaxed = true)
        context = mockk(relaxed = true)

        every { appSettingsRepository.liveViewQuality } returns liveViewQualityFlow
        every { getSubscriptionUseCase.getSubscriptionTier() } returns flowOf(SubscriptionTier.FREE)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): AppSettingsViewModel = AppSettingsViewModel(
        context = context,
        appSettingsRepository = appSettingsRepository,
        colorTransferUseCase = mockk<ColorTransferUseCase>(relaxed = true),
        getSubscriptionUseCase = getSubscriptionUseCase,
        startNativeLogUseCase = mockk<StartNativeLogUseCase>(relaxed = true),
        stopNativeLogUseCase = mockk<StopNativeLogUseCase>(relaxed = true),
        readNativeLogUseCase = mockk<ReadNativeLogUseCase>(relaxed = true)
    )

    @Test
    fun `liveViewQuality 초기값은 QUALITY`() = runTest {
        val viewModel = createViewModel()
        // stateIn initialValue (구독 전) = QUALITY
        assertEquals(LiveViewQuality.QUALITY, viewModel.liveViewQuality.value)
    }

    @Test
    fun `repository 가 BALANCED 를 방출하면 StateFlow 도 BALANCED 로 수렴`() = runTest {
        // upstream 을 initialValue(QUALITY)와 다른 값으로 두어 "초기값 방출 후 수렴"을 검증.
        liveViewQualityFlow.value = LiveViewQuality.BALANCED
        val viewModel = createViewModel()

        viewModel.liveViewQuality.test {
            // stateIn(WhileSubscribed): 구독 직후 initialValue(QUALITY) 먼저, 이어서 upstream(BALANCED)로 수렴.
            assertEquals(LiveViewQuality.QUALITY, awaitItem())
            assertEquals(LiveViewQuality.BALANCED, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `repository 방출 변경 시 StateFlow 가 순차 방출 - QUALITY to SPEED`() = runTest {
        val viewModel = createViewModel()

        viewModel.liveViewQuality.test {
            assertEquals(LiveViewQuality.QUALITY, awaitItem())

            liveViewQualityFlow.value = LiveViewQuality.SPEED

            assertEquals(LiveViewQuality.SPEED, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `setLiveViewQuality(QUALITY) 호출 시 repository 에 위임`() = runTest {
        coEvery { appSettingsRepository.setLiveViewQuality(any()) } returns Unit
        val viewModel = createViewModel()

        viewModel.setLiveViewQuality(LiveViewQuality.QUALITY)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { appSettingsRepository.setLiveViewQuality(LiveViewQuality.QUALITY) }
    }

    @Test
    fun `setLiveViewQuality(SPEED) 호출 시 SPEED 로 위임`() = runTest {
        coEvery { appSettingsRepository.setLiveViewQuality(any()) } returns Unit
        val viewModel = createViewModel()

        viewModel.setLiveViewQuality(LiveViewQuality.SPEED)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { appSettingsRepository.setLiveViewQuality(LiveViewQuality.SPEED) }
    }
}
