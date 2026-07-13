package com.inik.camcon.presentation.viewmodel

import android.content.Context
import app.cash.turbine.test
import com.inik.camcon.domain.model.Subscription
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.repository.AppSettingsRepository
import com.inik.camcon.domain.usecase.ColorTransferUseCase
import com.inik.camcon.domain.usecase.GetSubscriptionUseCase
import com.inik.camcon.domain.usecase.ObserveEffectiveTierUseCase
import com.inik.camcon.domain.usecase.ValidateFeatureAccessUseCase
import com.inik.camcon.domain.usecase.ValidateImageFormatUseCase
import com.inik.camcon.domain.usecase.camera.ReadNativeLogUseCase
import com.inik.camcon.domain.usecase.camera.StartNativeLogUseCase
import com.inik.camcon.domain.usecase.camera.StopNativeLogUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 미리보기/파이프라인 게이트 ViewModel 단위 테스트 (finalDesign §6).
 *
 * 검증 원칙: 세터가 티어·상대 플래그를 읽어 배타 스왑을 결정하고, init 정합화 관찰자가
 * 비허용 티어의 '둘 다 ON' 만 정합화하는지를 **repository 위임(coVerify) + SharedFlow/StateFlow 방출**로 본다.
 *
 * 협력자:
 *  - appSettingsRepository: relaxed mock. 게이트 판정이 참조하는 티어/필름/색감 flow 만 MutableStateFlow 로 명시 stub.
 *  - getSubscriptionUseCase.getSubscriptionTier(): effectiveTierFlow 의 Firebase 축 — 여기선 FREE flow 로 두고
 *    pref(subscriptionTierEnum) 로 유효 티어를 제어한다(pref 우선 병합).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppSettingsViewModelPipelineGateTest {

    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var getSubscriptionUseCase: GetSubscriptionUseCase
    private lateinit var validateFeatureAccessUseCase: ValidateFeatureAccessUseCase
    private lateinit var context: Context

    private val prefTierFlow = MutableStateFlow(SubscriptionTier.FREE)
    private val filmEnabledFlow = MutableStateFlow(false)
    private val colorEnabledFlow = MutableStateFlow(false)
    private val selectedFilmLutIdFlow = MutableStateFlow("")

    private val paidLutId = "luts/print/kodak_2393_cuspclip.cube" // 무료셋에 없는 카탈로그 id
    private val freeLutId = ValidateFeatureAccessUseCase.FREE_FILM_LUT_IDS.first()

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        appSettingsRepository = mockk(relaxed = true)
        getSubscriptionUseCase = mockk(relaxed = true)
        context = mockk(relaxed = true)
        // 판정 로직은 실제 순수 UseCase 를 그대로 사용(mock 아님) — 게이트 규칙의 진실성을 함께 검증.
        validateFeatureAccessUseCase = ValidateFeatureAccessUseCase()

        every { appSettingsRepository.subscriptionTierEnum } returns prefTierFlow
        every { appSettingsRepository.isFilmSimulationEnabled } returns filmEnabledFlow
        every { appSettingsRepository.isColorTransferEnabled } returns colorEnabledFlow
        every { appSettingsRepository.selectedFilmLutId } returns selectedFilmLutIdFlow
        every { getSubscriptionUseCase.getSubscriptionTier() } returns flowOf(SubscriptionTier.FREE)
        // ObserveEffectiveTierUseCase 는 이제 invoke()(전체 Subscription)를 읽는다. 비권위 FREE 로 두어
        // 유효 티어를 pref(subscriptionTierEnum)로 제어하는 기존 병합(비권위=pref 우선)을 유지한다.
        every { getSubscriptionUseCase.invoke() } returns MutableStateFlow(
            Subscription(tier = SubscriptionTier.FREE, isAuthoritative = false)
        )

        coEvery { appSettingsRepository.setColorTransferEnabled(any()) } returns Unit
        coEvery { appSettingsRepository.setFilmSimulationEnabled(any()) } returns Unit
        coEvery { appSettingsRepository.setSelectedFilmLutId(any()) } returns Unit
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
        readNativeLogUseCase = mockk<ReadNativeLogUseCase>(relaxed = true),
        validateImageFormatUseCase = mockk<ValidateImageFormatUseCase>(relaxed = true),
        validateFeatureAccessUseCase = validateFeatureAccessUseCase,
        // 실제 UseCase 사용(mock 아님) — pref+Firebase 병합 로직이 리팩터 후에도 무변경임을 함께 증명.
        observeEffectiveTierUseCase = ObserveEffectiveTierUseCase(
            appSettingsRepository, getSubscriptionUseCase
        )
    )

    // ---------- 세터 배타 스왑 ----------

    @Test
    fun `FREE + 색감 ON 상태에서 필름 켜면 색감 OFF 먼저 필름 ON 나중 + COLOR_TRANSFER 이벤트`() = runTest {
        prefTierFlow.value = SubscriptionTier.FREE
        colorEnabledFlow.value = true
        val viewModel = createViewModel()

        viewModel.pipelineSwapEvent.test {
            viewModel.setFilmSimulationEnabled(true)
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(awaitItem() == com.inik.camcon.domain.usecase.PipelineFeature.COLOR_TRANSFER)
            cancelAndConsumeRemainingEvents()
        }

        coVerifyOrder {
            appSettingsRepository.setColorTransferEnabled(false)
            appSettingsRepository.setFilmSimulationEnabled(true)
        }
    }

    @Test
    fun `PRO + 색감 ON 상태에서 필름 켜도 색감 OFF 호출 없음(스왑 안함)`() = runTest {
        prefTierFlow.value = SubscriptionTier.PRO
        colorEnabledFlow.value = true
        val viewModel = createViewModel()

        viewModel.setFilmSimulationEnabled(true)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { appSettingsRepository.setColorTransferEnabled(false) }
        coVerify(exactly = 1) { appSettingsRepository.setFilmSimulationEnabled(true) }
    }

    @Test
    fun `FREE + 필름 ON 상태에서 색감 켜면 필름 OFF 먼저 색감 ON 나중 + FILM_SIMULATION 이벤트`() = runTest {
        prefTierFlow.value = SubscriptionTier.FREE
        filmEnabledFlow.value = true
        val viewModel = createViewModel()

        viewModel.pipelineSwapEvent.test {
            viewModel.setColorTransferEnabled(true)
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(awaitItem() == com.inik.camcon.domain.usecase.PipelineFeature.FILM_SIMULATION)
            cancelAndConsumeRemainingEvents()
        }

        coVerifyOrder {
            appSettingsRepository.setFilmSimulationEnabled(false)
            appSettingsRepository.setColorTransferEnabled(true)
        }
    }

    // ---------- init 은 설정을 쓰지 않는다 (시작 시 정합화 제거 회귀 가드) ----------
    // 배경: 시작 시 '둘 다 ON' 영구 정합화가 티어 첫 방출 경합에서 사용자의 색감 설정을
    // 잘못 OFF 했다(실기 '재시작하면 토글 풀림'). 불변식은 세터 스왑 + 수신 마스킹이 지키므로
    // init 은 어떤 티어·플래그 조합에서도 영구 쓰기를 해서는 안 된다.

    @Test
    fun `H1 회귀 - pref PRO + 둘 다 ON 이어도 init 이 색감 OFF 를 호출하지 않음`() = runTest {
        prefTierFlow.value = SubscriptionTier.PRO
        filmEnabledFlow.value = true
        colorEnabledFlow.value = true
        val viewModel = createViewModel()

        testDispatcher.scheduler.advanceUntilIdle()

        // 세터를 호출하지 않았으므로 init 만이 유일한 setColorTransferEnabled 호출원 후보.
        coVerify(exactly = 0) { appSettingsRepository.setColorTransferEnabled(false) }
    }

    @Test
    fun `pref FREE + 둘 다 ON 이어도 init 은 설정을 쓰지 않는다 (재시작 토글 풀림 회귀)`() = runTest {
        prefTierFlow.value = SubscriptionTier.FREE
        filmEnabledFlow.value = true
        colorEnabledFlow.value = true
        val viewModel = createViewModel()

        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { appSettingsRepository.setColorTransferEnabled(any()) }
        coVerify(exactly = 0) { appSettingsRepository.setFilmSimulationEnabled(any()) }
    }

    // ---------- photoPreviewAccess 플래시 회귀 ----------

    @Test
    fun `H1 플래시 회귀 - pref PRO 는 photoPreviewAccess 가 null 다음 true (중간 false 없음)`() = runTest {
        prefTierFlow.value = SubscriptionTier.PRO
        val viewModel = createViewModel()

        viewModel.photoPreviewAccess.test {
            // stateIn initialValue = null(미확정), 이어서 pref 병합 티어(PRO)로 true.
            assertNull(awaitItem())
            assertTrue(awaitItem() == true)
            cancelAndConsumeRemainingEvents()
        }
    }

    // ---------- 필름 LUT 잠금 표시 / 세터 심층 방어 ----------

    @Test
    fun `selectedFilmLutLocked - pref PRO 는 null 다음 false (FREE 플래시 없음)`() = runTest {
        prefTierFlow.value = SubscriptionTier.PRO
        selectedFilmLutIdFlow.value = paidLutId
        val viewModel = createViewModel()

        viewModel.selectedFilmLutLocked.test {
            assertNull(awaitItem())
            assertTrue(awaitItem() == false)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `selectedFilmLutLocked - FREE + 유료 LUT 선택이면 null 다음 true`() = runTest {
        prefTierFlow.value = SubscriptionTier.FREE
        selectedFilmLutIdFlow.value = paidLutId
        val viewModel = createViewModel()

        viewModel.selectedFilmLutLocked.test {
            assertNull(awaitItem())
            assertTrue(awaitItem() == true)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `setSelectedFilmLutId - FREE + 유료 id 는 repo 저장 미호출(심층 방어)`() = runTest {
        prefTierFlow.value = SubscriptionTier.FREE
        val viewModel = createViewModel()

        viewModel.setSelectedFilmLutId(paidLutId)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { appSettingsRepository.setSelectedFilmLutId(paidLutId) }
    }

    @Test
    fun `setSelectedFilmLutId - FREE + 무료 id 는 repo 저장 호출`() = runTest {
        prefTierFlow.value = SubscriptionTier.FREE
        val viewModel = createViewModel()

        viewModel.setSelectedFilmLutId(freeLutId)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { appSettingsRepository.setSelectedFilmLutId(freeLutId) }
    }

    @Test
    fun `setSelectedFilmLutId - PRO + 유료 id 는 repo 저장 호출`() = runTest {
        prefTierFlow.value = SubscriptionTier.PRO
        val viewModel = createViewModel()

        viewModel.setSelectedFilmLutId(paidLutId)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { appSettingsRepository.setSelectedFilmLutId(paidLutId) }
    }
}
