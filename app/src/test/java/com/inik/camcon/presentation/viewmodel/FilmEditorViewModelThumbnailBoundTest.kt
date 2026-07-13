package com.inik.camcon.presentation.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.repository.FilmEditProcessor
import com.inik.camcon.domain.usecase.FilmFavoritesUseCase
import com.inik.camcon.domain.usecase.FilmLutUseCase
import com.inik.camcon.domain.usecase.ObserveEffectiveTierUseCase
import com.inik.camcon.domain.usecase.ValidateFeatureAccessUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [FilmEditorViewModel._thumbnails] 무한 축적 회귀 테스트 (썸네일 맵 LRU 상한 fix).
 *
 * 컨택트 시트 296종 그리드를 끝까지 스크롤하면 requestThumbnail 이 셀마다 호출돼 VM 맵이 모든
 * 썸네일을 강참조로 누적 → 생성기(FilmThumbnailGenerator) 48MB 예산이 무력화되던 문제를 막았다.
 * 맵에 개수 상한([FilmEditorViewModel.MAX_THUMBNAILS])을 두고 초과 시 오래된 항목부터 참조를 드롭한다.
 *
 * 실 Bitmap([android.graphics.Bitmap])이 필요해 Robolectric 으로 실행한다(sdk=34 고정). Bitmap 은
 * 캐시 소유이므로 축출 시 recycle 하면 안 된다 — isRecycled 로 검증한다.
 * application=Application::class: 실제 CamCon 은 네이티브 로딩으로 UnsatisfiedLinkError → 스텁 대체.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FilmEditorViewModelThumbnailBoundTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var filmLutUseCase: FilmLutUseCase
    private lateinit var filmFavoritesUseCase: FilmFavoritesUseCase
    private lateinit var filmEditProcessor: FilmEditProcessor
    private lateinit var context: Context
    private lateinit var observeEffectiveTierUseCase: ObserveEffectiveTierUseCase
    private val validateFeatureAccessUseCase = ValidateFeatureAccessUseCase()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        filmLutUseCase = mockk(relaxed = true)
        filmFavoritesUseCase = mockk(relaxed = true)
        filmEditProcessor = mockk(relaxed = true)
        context = mockk(relaxed = true)
        observeEffectiveTierUseCase = mockk()

        coEvery { filmLutUseCase.getAvailableLuts() } returns emptyList()
        every { filmFavoritesUseCase.favorites() } returns MutableStateFlow(emptySet())
        every { observeEffectiveTierUseCase() } returns MutableStateFlow(SubscriptionTier.PRO)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `썸네일 맵은 상한을 넘지 않고 오래된 항목부터 축출된다`() = runTest {
        val cap = maxThumbnails()
        val created = mutableMapOf<String, Bitmap>()
        coEvery { filmEditProcessor.generateThumbnail(any(), any(), any()) } answers {
            val lutId = thirdArg<String>()
            newBitmap().also { created[lutId] = it }
        }

        val vm = createViewModel()
        injectSource(vm)

        val total = cap + 3
        for (i in 0 until total) vm.requestThumbnail("lut_$i")

        val map = vm.thumbnails.value
        assertEquals("맵 크기는 상한으로 묶여야 함", cap, map.size)
        assertFalse("가장 오래된 항목은 축출됨", map.containsKey("lut_0"))
        assertFalse(map.containsKey("lut_1"))
        assertFalse(map.containsKey("lut_2"))
        assertTrue("최신 항목은 유지", map.containsKey("lut_${total - 1}"))
        assertTrue("상한 이내 최근 항목 유지", map.containsKey("lut_3"))
        // 축출은 참조만 드롭 — 캐시 소유 비트맵을 recycle 하면 안 됨.
        assertFalse("축출 시 recycle 하면 안 됨", created["lut_0"]!!.isRecycled)
    }

    @Test
    fun `상한 이내면 모든 썸네일이 유지된다`() = runTest {
        coEvery { filmEditProcessor.generateThumbnail(any(), any(), any()) } answers { newBitmap() }

        val vm = createViewModel()
        injectSource(vm)

        val n = maxThumbnails() // 정확히 상한만큼
        for (i in 0 until n) vm.requestThumbnail("lut_$i")

        assertEquals(n, vm.thumbnails.value.size)
    }

    // --- Helpers ---

    private fun createViewModel(): FilmEditorViewModel =
        FilmEditorViewModel(
            filmLutUseCase = filmLutUseCase,
            filmFavoritesUseCase = filmFavoritesUseCase,
            filmEditProcessor = filmEditProcessor,
            observeEffectiveTierUseCase = observeEffectiveTierUseCase,
            validateFeatureAccessUseCase = validateFeatureAccessUseCase,
            appSettingsRepository = mockk(relaxed = true),
            context = context,
            ioDispatcher = testDispatcher
        )

    private fun newBitmap(): Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

    /** requestThumbnail 진입 가드(thumbSource != null, sourceId != null)를 리플렉션으로 충족. */
    private fun injectSource(vm: FilmEditorViewModel) {
        FilmEditorViewModel::class.java.getDeclaredField("thumbSource").apply {
            isAccessible = true
            set(vm, newBitmap())
        }
        val sidField = FilmEditorViewModel::class.java.getDeclaredField("_sourceId").apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        (sidField.get(vm) as MutableStateFlow<String?>).value = "sid"
    }

    private fun maxThumbnails(): Int =
        FilmEditorViewModel::class.java.getDeclaredField("MAX_THUMBNAILS")
            .apply { isAccessible = true }
            .getInt(null)
}
