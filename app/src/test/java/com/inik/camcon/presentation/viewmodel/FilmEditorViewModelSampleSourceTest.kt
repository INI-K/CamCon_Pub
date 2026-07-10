package com.inik.camcon.presentation.viewmodel

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.inik.camcon.domain.model.FilmLut
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.repository.AppSettingsRepository
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * [FilmEditorViewModel.ensureSampleSourceIfNeeded] (번들 샘플 자동 로드) 단위 테스트.
 *
 * select-only 진입(설정/카메라 '기본 필름 선택')에서 대상 사진이 없을 때 assets 의 번들 샘플
 * ([SAMPLE_ASSET]) 을 cache 로 복사해 소스로 세팅하는 계약을 검증한다.
 *
 * Robolectric 이 필요한 이유(기존 [FilmEditorViewModelTest] 는 순수 JVM 이라 이 경로를 제외):
 *  - [FilmEditorViewModel] 의 `copySampleToCache` 가 `context.assets.open(...)` / `context.cacheDir`
 *    를 실제로 사용한다(merged assets 접근 필요).
 *  - 이어지는 `setSourceImage` 가 `BitmapFactory.decodeFile` 로 디코딩한다(ShadowBitmapFactory 필요).
 * → `ServerPhotosViewModelOwnMediaTest` 와 동일한 Robolectric 컨벤션(sdk=34, application=Application::class)
 *   을 따르고, 기존 순수 JVM 테스트 파일은 건드리지 않는다.
 *
 * 협력자: [FilmLutUseCase]/[FilmFavoritesUseCase]/[FilmEditProcessor]/[AppSettingsRepository] =
 * relaxed mock(GPU init·썸네일 정리 no-op). 티어는 PRO 고정(게이팅과 무관).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FilmEditorViewModelSampleSourceTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var context: Context
    private lateinit var filmLutUseCase: FilmLutUseCase
    private lateinit var filmFavoritesUseCase: FilmFavoritesUseCase
    private lateinit var filmEditProcessor: FilmEditProcessor
    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var observeEffectiveTierUseCase: ObserveEffectiveTierUseCase
    private val validateFeatureAccessUseCase = ValidateFeatureAccessUseCase()

    private val favoritesFlow = MutableStateFlow<Set<String>>(emptySet())
    private val effectiveTierFlow = MutableStateFlow(SubscriptionTier.PRO)

    private val catalog = listOf(
        FilmLut(id = "bw/tri-x.cube", name = "Kodak Tri-X 400", category = "Bw", assetPath = "bw/tri-x.cube")
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()

        filmLutUseCase = mockk(relaxed = true)
        filmFavoritesUseCase = mockk(relaxed = true)
        filmEditProcessor = mockk(relaxed = true)
        appSettingsRepository = mockk(relaxed = true)
        observeEffectiveTierUseCase = mockk()

        coEvery { filmLutUseCase.getAvailableLuts() } returns catalog
        every { filmFavoritesUseCase.favorites() } returns favoritesFlow
        every { observeEffectiveTierUseCase() } returns effectiveTierFlow

        // 캐시 파일 개수 단정이 이전 테스트 산출물에 오염되지 않도록 샘플 캐시 디렉터리를 비운다.
        sampleCacheDir().deleteRecursively()
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
            appSettingsRepository = appSettingsRepository,
            context = context,
            ioDispatcher = testDispatcher
        )

    /** copySampleToCache 가 쓰는 캐시 디렉터리(VM 내부 상수와 동일 경로). */
    private fun sampleCacheDir(): File = File(context.cacheDir, "film_editor_images")

    private fun sampleCacheFile(): File = File(sampleCacheDir(), SAMPLE_ASSET)

    /** private `_sourcePath` 를 직접 조작한다(멱등 가드 우회로 dedup 층을 노출하기 위함). */
    @Suppress("UNCHECKED_CAST")
    private fun setSourcePath(vm: FilmEditorViewModel, value: String?) {
        val field = FilmEditorViewModel::class.java.getDeclaredField("_sourcePath")
        field.isAccessible = true
        (field.get(vm) as MutableStateFlow<String?>).value = value
    }

    @Test
    fun `소스 미설정 상태에서 호출하면 샘플이 소스로 설정된다`() = runTest {
        val vm = createViewModel()
        assertNull("초기엔 소스가 없어야 함", vm.sourcePath.value)

        vm.ensureSampleSourceIfNeeded()

        val path = vm.sourcePath.value
        assertNotNull("샘플이 소스로 설정되어야 함", path)
        assertTrue(
            "소스 경로는 번들 샘플 파일이어야 함: $path",
            path!!.endsWith(SAMPLE_ASSET)
        )
        // 실제로 assets → cache 복사가 일어났는지(파일 존재 + 내용 있음).
        val cached = File(path)
        assertTrue("샘플이 cache 로 복사되어야 함", cached.exists() && cached.length() > 0)

        // 파생 상태 sourceId = "path@mtime" 도 방출되어야 그리드가 뜬다.
        val sid = vm.sourceId.value
        assertNotNull("sourceId 가 파생되어야 함", sid)
        assertTrue("sourceId 는 경로@mtime 형식이어야 함: $sid", sid!!.startsWith("$path@"))
    }

    @Test
    fun `이미 소스가 있으면 멱등하게 no-op 이다`() = runTest {
        val vm = createViewModel()
        // 사용자 대상 사진이 이미 지정된 상태를 모사(실사진 편집 진입).
        val existing = "/tmp/existing_source.jpg"
        setSourcePath(vm, existing)

        vm.ensureSampleSourceIfNeeded()

        // 소스 교체 없음.
        assertEquals("기존 소스가 샘플로 교체되면 안 됨", existing, vm.sourcePath.value)
        // 조기 반환이므로 assets → cache 복사도 일어나지 않아야 함.
        assertFalse(
            "멱등 no-op 은 샘플 캐시 파일을 만들지 않아야 함",
            sampleCacheFile().exists()
        )
    }

    @Test
    fun `중복 호출해도 캐시 파일이 중복 생성되지 않는다`() = runTest {
        val vm = createViewModel()

        vm.ensureSampleSourceIfNeeded()
        val firstPath = vm.sourcePath.value
        assertNotNull(firstPath)
        assertEquals(
            "첫 호출 후 샘플 캐시 파일은 정확히 1개",
            1,
            sampleCacheDir().listFiles()?.size ?: 0
        )

        // 멱등 가드를 우회해 copySampleToCache 재진입을 강제(세션당 1회지만 dedup 층 검증용).
        setSourcePath(vm, null)
        vm.ensureSampleSourceIfNeeded()

        assertEquals(
            "재복사 없이 기존 파일 재사용 → 캐시 파일은 여전히 1개",
            1,
            sampleCacheDir().listFiles()?.size ?: 0
        )
        assertEquals(
            "재진입해도 동일 캐시 경로를 재사용해야 함",
            firstPath,
            vm.sourcePath.value
        )
    }

    private companion object {
        /** VM 내부 SAMPLE_ASSET_NAME 과 동일해야 함(private 상수 미러). */
        const val SAMPLE_ASSET = "film_sample.webp"
    }
}
