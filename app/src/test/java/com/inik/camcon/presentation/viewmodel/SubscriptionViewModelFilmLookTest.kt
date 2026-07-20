package com.inik.camcon.presentation.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.inik.camcon.domain.model.FilmLut
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.repository.FilmEditProcessor
import com.inik.camcon.domain.usecase.FilmLutUseCase
import com.inik.camcon.domain.usecase.GetSubscriptionUseCase
import com.inik.camcon.domain.usecase.PurchaseSubscriptionUseCase
import com.inik.camcon.domain.usecase.ValidateFeatureAccessUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [SubscriptionViewModel] 페이월 필름 룩 프리뷰 StateFlow 방출 단위 테스트(순수 JVM).
 *
 * 신설 계약(loadFilmLookPreview → [SubscriptionViewModel.filmLookCount]/[filmLookSamples]) 검증:
 *  - filmLookCount = 카탈로그 size 그대로("N가지 필름 룩" 카운터, 하드코딩 금지).
 *  - filmLookSamples = FREE 시그니처 5종(locked=false) + 잠금 표본(locked=true), 최대 8 / 잠금 최대 3.
 *  - 빈 카탈로그면 두 StateFlow 모두 초기값 유지.
 *  - 샘플 이미지 디코딩 실패(폴백)해도 스켈레톤 + 카운트 유지, 크래시 없음.
 *  - 썸네일 생성 시 개별 항목 thumbnail 채움.
 *
 * 비트맵 디코딩 경로 격리(design 지시): 기본은 `returnDefaultValues=true` 로 `BitmapFactory.decodeStream`
 * 이 null 을 반환 → decodeSampleSource 가 null → 스켈레톤에서 멈추는 "에러/폴백" 경로가 자연히 노출된다.
 * 썸네일 채움 경로만 [mockkStatic] 으로 [BitmapFactory] 를 제어한다(Robolectric 신설 회피).
 *
 * 협력자: [GetSubscriptionUseCase]/[PurchaseSubscriptionUseCase]/[FilmLutUseCase]/[FilmEditProcessor]/
 * [Context] = relaxed mock. ioDispatcher = Main 과 동일 [UnconfinedTestDispatcher](동기 실행).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionViewModelFilmLookTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var getSubscriptionUseCase: GetSubscriptionUseCase
    private lateinit var purchaseSubscriptionUseCase: PurchaseSubscriptionUseCase
    private lateinit var filmLutUseCase: FilmLutUseCase
    private lateinit var filmEditProcessor: FilmEditProcessor
    private lateinit var context: Context

    private val tierFlow = MutableStateFlow(SubscriptionTier.FREE)

    /** FREE 시그니처 5종(순서 보존). */
    private val freeIds = ValidateFeatureAccessUseCase.FREE_FILM_LUT_IDS.toList()

    /** 카탈로그: FREE 5종 + 유료 4종 = 9종. */
    private val catalog: List<FilmLut> =
        freeIds.mapIndexed { i, id ->
            FilmLut(id = id, name = "Free $i", category = "Free", assetPath = id)
        } + (0 until 4).map { i ->
            FilmLut(id = "paid/lut_$i.cube", name = "Paid $i", category = "Paid", assetPath = "paid/lut_$i.cube")
        }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        getSubscriptionUseCase = mockk(relaxed = true)
        purchaseSubscriptionUseCase = mockk(relaxed = true)
        filmLutUseCase = mockk(relaxed = true)
        filmEditProcessor = mockk(relaxed = true)
        context = mockk(relaxed = true)

        every { getSubscriptionUseCase.getSubscriptionTier() } returns tierFlow
        coEvery { purchaseSubscriptionUseCase.getAvailableSubscriptions() } returns emptyList()
        coEvery { filmLutUseCase.getAvailableLuts() } returns catalog
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createViewModel(): SubscriptionViewModel =
        SubscriptionViewModel(
            getSubscriptionUseCase = getSubscriptionUseCase,
            purchaseSubscriptionUseCase = purchaseSubscriptionUseCase,
            filmLutUseCase = filmLutUseCase,
            filmEditProcessor = filmEditProcessor,
            context = context,
            ioDispatcher = testDispatcher
        )

    @Test
    fun `filmLookCount는 카탈로그 크기를 그대로 방출한다`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals("filmLookCount 는 카탈로그 size 여야 함", catalog.size, vm.filmLookCount.value)
    }

    @Test
    fun `filmLookSamples는 FREE 시그니처가 해제, 나머지가 잠금으로 방출된다`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        val samples = vm.filmLookSamples.value
        // FREE 5 + 잠금 3 = 8.
        assertEquals("표본은 FREE 5 + 잠금 3 = 8 이어야 함", 8, samples.size)

        val free = samples.filter { !it.locked }
        val locked = samples.filter { it.locked }
        assertEquals("잠금 해제 표본은 FREE 시그니처 5종", 5, free.size)
        assertEquals("잠금 표본은 3종", 3, locked.size)

        // 잠금 해제 표본 id 는 전부 FREE 셋에 속하고, 잠금 표본 id 는 전부 밖.
        assertTrue(
            "잠금 해제 표본은 모두 FREE 셋",
            free.all { it.lutId in ValidateFeatureAccessUseCase.FREE_FILM_LUT_IDS }
        )
        assertTrue(
            "잠금 표본은 모두 FREE 셋 밖",
            locked.all { it.lutId !in ValidateFeatureAccessUseCase.FREE_FILM_LUT_IDS }
        )
        // FREE 가 앞, 잠금이 뒤(스트립 순서).
        assertTrue("FREE 표본이 잠금 표본보다 앞", samples.take(5).none { it.locked })
    }

    @Test
    fun `표본은 최대 8개_잠금은 최대 3개로 캡된다`() = runTest {
        // 유료 LUT 을 대량 투입해도 캡이 걸리는지.
        val big = freeIds.mapIndexed { i, id ->
            FilmLut(id = id, name = "Free $i", category = "Free", assetPath = id)
        } + (0 until 30).map { i ->
            FilmLut(id = "paid/big_$i.cube", name = "Big $i", category = "Paid", assetPath = "paid/big_$i.cube")
        }
        coEvery { filmLutUseCase.getAvailableLuts() } returns big

        val vm = createViewModel()
        advanceUntilIdle()

        val samples = vm.filmLookSamples.value
        assertEquals("표본은 최대 8개", 8, samples.size)
        assertEquals("잠금 표본은 최대 3개", 3, samples.count { it.locked })
        assertEquals("filmLookCount 는 전체 카탈로그 size", big.size, vm.filmLookCount.value)
    }

    @Test
    fun `빈 카탈로그면 두 StateFlow가 초기값을 유지한다`() = runTest {
        coEvery { filmLutUseCase.getAvailableLuts() } returns emptyList()

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals("빈 카탈로그면 count 0", 0, vm.filmLookCount.value)
        assertTrue("빈 카탈로그면 표본 없음", vm.filmLookSamples.value.isEmpty())
    }

    @Test
    fun `샘플 디코딩 실패시 스켈레톤과 카운트는 유지되고 썸네일은 null`() = runTest {
        // BitmapFactory mock 없음 → decodeStream 이 기본값(null) → decodeSampleSource null → 스켈레톤 정지.
        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals("디코딩 실패해도 카운트는 방출", catalog.size, vm.filmLookCount.value)
        val samples = vm.filmLookSamples.value
        assertEquals("스켈레톤 표본은 유지", 8, samples.size)
        assertTrue("디코딩 실패 → 모든 썸네일 null", samples.all { it.thumbnail == null })
    }

    @Test
    fun `썸네일이 생성되면 개별 표본 항목에 채워진다`() = runTest {
        // 디코딩 소스 + 썸네일 생성이 성공하도록 BitmapFactory·generateThumbnail 을 제어.
        mockkStatic(BitmapFactory::class)
        every { BitmapFactory.decodeStream(any(), any(), any()) } answers {
            val opts = thirdArg<BitmapFactory.Options?>()
            if (opts != null && opts.inJustDecodeBounds) {
                // 바운즈 프로브: 양수 치수 설정(다운스케일 계산 통과), 데이터는 null.
                opts.outWidth = 1024
                opts.outHeight = 768
                null
            } else {
                mockk<Bitmap>(relaxed = true) // 실제 디코딩 소스.
            }
        }
        val thumb = mockk<Bitmap>(relaxed = true)
        every { thumb.isRecycled } returns false
        coEvery { filmEditProcessor.generateThumbnail(any(), any(), any()) } returns thumb

        val vm = createViewModel()
        advanceUntilIdle()

        val samples = vm.filmLookSamples.value
        assertEquals("표본 8개", 8, samples.size)
        assertTrue("생성된 썸네일이 표본에 채워져야 함", samples.all { it.thumbnail === thumb })
        // 카운트/잠금 구조는 그대로.
        assertEquals("카운트 유지", catalog.size, vm.filmLookCount.value)
        assertEquals("잠금 3종 유지", 3, samples.count { it.locked })
    }

    @Test
    fun `초기 uiState는 FREE 티어에서 로딩 완료로 수렴한다`() = runTest {
        // 필름 룩 신설이 기존 uiState 방출을 깨지 않았는지 확인(회귀 가드).
        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("티어는 관찰 Flow 값(FREE)", SubscriptionTier.FREE, state.currentTier)
        assertFalse("상품 로드 종료 후 isLoading=false", state.isLoading)
        assertNull("에러 없음", state.error)
        assertNotNull("uiState 방출됨", state)
    }
}
