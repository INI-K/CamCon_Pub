package com.inik.camcon.presentation.viewmodel

import android.content.Context
import app.cash.turbine.test
import com.inik.camcon.domain.repository.FilmEditProcessor
import com.inik.camcon.domain.model.FilmAdjustments
import com.inik.camcon.domain.model.FilmEdit
import com.inik.camcon.domain.model.FilmLut
import com.inik.camcon.domain.model.SubscriptionTier
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * [FilmEditorViewModel] 순수 로직(visibleLuts 파생) 단위 테스트.
 *
 * 설계서: `docs/superpowers/specs/2026-06-22-film-simulation-editor-design.md` §4.3/§9.
 * 검증 범위(자동 가능): 카테고리/검색/즐겨찾기 조합 → [FilmEditorViewModel.visibleLuts] 방출.
 * 제외: 비트맵 디코딩/GPU/썸네일(실기기 필요) — [setSourceImage]/[requestThumbnail] 미호출.
 *
 * 협력자: [FilmLutUseCase]/[FilmFavoritesUseCase]/[FilmEditProcessor] = relaxed mock.
 * GPU init(initializeGPU) 은 relaxed mock 으로 no-op.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FilmEditorViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var filmLutUseCase: FilmLutUseCase
    private lateinit var filmFavoritesUseCase: FilmFavoritesUseCase
    private lateinit var filmEditProcessor: FilmEditProcessor
    private lateinit var context: Context

    private lateinit var observeEffectiveTierUseCase: ObserveEffectiveTierUseCase
    private val validateFeatureAccessUseCase = ValidateFeatureAccessUseCase()

    private val favoritesFlow = MutableStateFlow<Set<String>>(emptySet())
    /** 게이팅 테스트가 유효 티어를 제어하는 축. 기본 PRO(전체 허용) — 기존 필터 테스트에 영향 없음. */
    private val effectiveTierFlow = MutableStateFlow(SubscriptionTier.PRO)

    private val freeLutId = ValidateFeatureAccessUseCase.FREE_FILM_LUT_IDS.first()
    private val paidLutId = "neg/portra.cube" // 카탈로그엔 있지만 무료셋엔 없는 id

    private val catalog = listOf(
        FilmLut(id = "bw/tri-x.cube", name = "Kodak Tri-X 400", category = "Bw", assetPath = "bw/tri-x.cube"),
        FilmLut(id = "bw/hp5.cube", name = "Ilford HP5", category = "Bw", assetPath = "bw/hp5.cube"),
        FilmLut(id = "neg/portra.cube", name = "Kodak Portra 400", category = "Negative New", assetPath = "neg/portra.cube"),
        FilmLut(id = "neg/gold.cube", name = "Kodak Gold 200", category = "Negative New", assetPath = "neg/gold.cube"),
        FilmLut(id = "print/fuji.cube", name = "Fuji Crystal", category = "Print", assetPath = "print/fuji.cube")
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        filmLutUseCase = mockk(relaxed = true)
        filmFavoritesUseCase = mockk(relaxed = true)
        filmEditProcessor = mockk(relaxed = true)
        context = mockk(relaxed = true)

        observeEffectiveTierUseCase = mockk()

        coEvery { filmLutUseCase.getAvailableLuts() } returns catalog
        every { filmFavoritesUseCase.favorites() } returns favoritesFlow
        every { observeEffectiveTierUseCase() } returns effectiveTierFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

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

    @Test
    fun `default category ALL emits full catalog`() = runTest {
        val vm = createViewModel()
        vm.visibleLuts.test {
            assertEquals(catalog, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `category filter narrows to that category`() = runTest {
        val vm = createViewModel()
        vm.setCategory("Bw")
        vm.visibleLuts.test {
            val visible = awaitItem()
            assertEquals(listOf("bw/tri-x.cube", "bw/hp5.cube"), visible.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `search filters by name case-insensitive across categories`() = runTest {
        val vm = createViewModel()
        vm.setSearch("kodak")
        vm.visibleLuts.test {
            val visible = awaitItem()
            assertEquals(
                listOf("Kodak Tri-X 400", "Kodak Portra 400", "Kodak Gold 200"),
                visible.map { it.name }
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `category and search combine`() = runTest {
        val vm = createViewModel()
        vm.setCategory("Negative New")
        vm.setSearch("gold")
        vm.visibleLuts.test {
            val visible = awaitItem()
            assertEquals(listOf("neg/gold.cube"), visible.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `favorites category shows only favorited luts`() = runTest {
        favoritesFlow.value = setOf("bw/hp5.cube", "print/fuji.cube")
        val vm = createViewModel()
        vm.setCategory(FilmEditorViewModel.CATEGORY_FAVORITES)
        vm.visibleLuts.test {
            val visible = awaitItem()
            assertEquals(listOf("bw/hp5.cube", "print/fuji.cube"), visible.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `favorites category with search applies both`() = runTest {
        favoritesFlow.value = setOf("bw/tri-x.cube", "neg/portra.cube")
        val vm = createViewModel()
        vm.setCategory(FilmEditorViewModel.CATEGORY_FAVORITES)
        vm.setSearch("portra")
        vm.visibleLuts.test {
            val visible = awaitItem()
            assertEquals(listOf("neg/portra.cube"), visible.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `favorites category empty when nothing favorited`() = runTest {
        val vm = createViewModel()
        vm.setCategory(FilmEditorViewModel.CATEGORY_FAVORITES)
        vm.visibleLuts.test {
            assertEquals(emptyList<FilmLut>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `blank search keeps full catalog under ALL`() = runTest {
        val vm = createViewModel()
        vm.setSearch("   ")
        vm.visibleLuts.test {
            assertEquals(catalog.size, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selectLut updates selectedLutId`() = runTest {
        val vm = createViewModel()
        vm.selectLut("neg/portra.cube")
        assertEquals("neg/portra.cube", vm.selectedLutId.value)
    }

    // ---- Phase 3: 편집 상태 / clamp / 내보내기 ----

    @Test
    fun `setIntensity clamps to 0_1 range`() = runTest {
        val vm = createViewModel()
        vm.setIntensity(2f)
        assertEquals(1f, vm.filmEdit.value.intensity, 1e-4f)
        vm.setIntensity(-0.5f)
        assertEquals(0f, vm.filmEdit.value.intensity, 1e-4f)
        vm.setIntensity(0.4f)
        assertEquals(0.4f, vm.filmEdit.value.intensity, 1e-4f)
    }

    @Test
    fun `setAdjustment clamps each field to its range`() = runTest {
        val vm = createViewModel()
        // 양방향(±100), 노출(±2), 단방향(0..1) 모두 범위 밖 입력.
        vm.setAdjustment(
            exposure = 99f,            // -> 2 (EXPOSURE_MAX)
            temperature = -999f,       // -> -100 (TEMPERATURE_MIN)
            contrast = 500f,           // -> 100 (BIPOLAR_MAX)
            saturation = -500f,        // -> -100 (BIPOLAR_MIN)
            grain = 5f,                // -> 1 (UNIPOLAR_MAX)
            chromaticAberration = -3f  // -> 0 (UNIPOLAR_MIN)
        )
        val a = vm.filmEdit.value.adjustments
        assertEquals(FilmAdjustments.EXPOSURE_MAX, a.exposure, 1e-4f)
        assertEquals(FilmAdjustments.TEMPERATURE_MIN, a.temperature, 1e-4f)
        assertEquals(FilmAdjustments.BIPOLAR_MAX, a.contrast, 1e-4f)
        assertEquals(FilmAdjustments.BIPOLAR_MIN, a.saturation, 1e-4f)
        assertEquals(FilmAdjustments.UNIPOLAR_MAX, a.grain, 1e-4f)
        assertEquals(FilmAdjustments.UNIPOLAR_MIN, a.chromaticAberration, 1e-4f)
    }

    @Test
    fun `setAdjustment only updates passed fields`() = runTest {
        val vm = createViewModel()
        vm.setAdjustment(contrast = 50f)
        vm.setAdjustment(saturation = -20f)
        val a = vm.filmEdit.value.adjustments
        assertEquals(50f, a.contrast, 1e-4f)
        assertEquals(-20f, a.saturation, 1e-4f)
        assertEquals(FilmAdjustments.NEUTRAL, a.exposure, 1e-4f)
    }

    @Test
    fun `resetAdjustments returns to neutral keeping lut and intensity`() = runTest {
        val vm = createViewModel()
        vm.selectLut("bw/tri-x.cube")
        vm.setIntensity(0.6f)
        vm.setAdjustment(contrast = 40f, grain = 0.5f)
        vm.resetAdjustments()
        val edit = vm.filmEdit.value
        assertTrue(edit.adjustments.isNeutral)
        assertEquals("bw/tri-x.cube", edit.lutId)
        assertEquals(0.6f, edit.intensity, 1e-4f)
    }

    @Test
    fun `export emits FAIL when no source set`() = runTest {
        val vm = createViewModel()
        vm.message.test {
            assertEquals(null, awaitItem())
            vm.export()
            assertEquals(FilmEditorViewModel.MESSAGE_FAIL, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `export emits FAIL when applyEditToTemp returns null`() = runTest {
        coEvery { filmLutUseCase.applyEditToTemp(any(), any(), any()) } returns null
        val vm = createViewModel()
        setSourceViaReflection(vm, "/tmp/whatever.jpg")
        vm.message.test {
            assertEquals(null, awaitItem())
            vm.export()
            assertEquals(FilmEditorViewModel.MESSAGE_FAIL, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `export emits OK when temp produced and saved to external files`() = runTest {
        // 임시 결과 파일(applyEditToTemp 반환) + 외부 Pictures 디렉터리(getExternalFilesDir) 준비.
        val tmpResult = File.createTempFile("film_export_src", ".jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }
        val externalDir = File(System.getProperty("java.io.tmpdir"), "camcon_ext_${System.nanoTime()}")
            .apply { mkdirs(); deleteOnExit() }
        every { context.getExternalFilesDir(any()) } returns externalDir
        // contentResolver.insert 는 relaxed mock 에서 null → saveToExternalFiles 폴백 경로로 성공.
        coEvery { filmLutUseCase.applyEditToTemp(any(), any(), any()) } returns tmpResult.absolutePath

        val vm = createViewModel()
        setSourceViaReflection(vm, "/tmp/source.jpg")
        vm.message.test {
            assertEquals(null, awaitItem())
            vm.export()
            assertEquals(FilmEditorViewModel.MESSAGE_OK, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- 티어 게이팅(무료 시그니처 필름 + PRO 전체) ----

    @Test
    fun `PRO 티어면 lockedLutIds 는 emptySet (배지 플래시 없음)`() = runTest {
        effectiveTierFlow.value = SubscriptionTier.PRO
        val vm = createViewModel()
        vm.lockedLutIds.test {
            assertEquals(emptySet<String>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `FREE 티어면 lockedLutIds 는 카탈로그에서 무료셋을 뺀 집합`() = runTest {
        effectiveTierFlow.value = SubscriptionTier.FREE
        val vm = createViewModel()
        vm.lockedLutIds.test {
            // 초기값 emptySet → FREE 병합 후 '카탈로그 − 무료셋'. 이 카탈로그엔 무료 id 가 없어 전부 잠김.
            var latest = awaitItem()
            while (latest.isEmpty()) latest = awaitItem()
            assertEquals(catalog.map { it.id }.toSet(), latest)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selectLutGated FREE + 잠긴 id → lutLockNotice 방출 + 선택 불변`() = runTest {
        effectiveTierFlow.value = SubscriptionTier.FREE
        val vm = createViewModel()
        vm.lutLockNotice.test {
            vm.selectLutGated(paidLutId)
            awaitItem() // Unit 방출 확인
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("", vm.selectedLutId.value)
        assertEquals("", vm.filmEdit.value.lutId)
    }

    @Test
    fun `selectLutGated FREE + 무료 id → lutSelectionAccepted 방출 + 선택 갱신`() = runTest {
        effectiveTierFlow.value = SubscriptionTier.FREE
        val vm = createViewModel()
        vm.lutSelectionAccepted.test {
            vm.selectLutGated(freeLutId)
            assertEquals(freeLutId, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(freeLutId, vm.selectedLutId.value)
    }

    @Test
    fun `selectLutGated PRO + 잠긴 id 도 허용 → accepted 방출`() = runTest {
        effectiveTierFlow.value = SubscriptionTier.PRO
        val vm = createViewModel()
        vm.lutSelectionAccepted.test {
            vm.selectLutGated(paidLutId)
            assertEquals(paidLutId, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(paidLutId, vm.selectedLutId.value)
    }

    /** export 는 _sourcePath 가 있어야 동작한다. setSourceImage 는 비트맵 디코딩을 타므로,
     *  단위테스트에선 private _sourcePath 만 리플렉션으로 주입한다(디코딩/GPU 우회). */
    private fun setSourceViaReflection(vm: FilmEditorViewModel, path: String) {
        val field = FilmEditorViewModel::class.java.getDeclaredField("_sourcePath")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(vm) as MutableStateFlow<String?>
        flow.value = path
    }
}
