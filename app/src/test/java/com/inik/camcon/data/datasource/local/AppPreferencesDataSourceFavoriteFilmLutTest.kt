package com.inik.camcon.data.datasource.local

import android.app.Application
import app.cash.turbine.test
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 필름 LUT 즐겨찾기 영속(toggle) 단위 테스트.
 *
 * 설계서: `docs/superpowers/specs/2026-06-22-film-simulation-editor-design.md` §4.2/§11.1
 * 대상: [AppPreferencesDataSource.favoriteFilmLutIds] Flow getter + [AppPreferencesDataSource.toggleFavoriteFilmLut] 토글.
 *
 * 검증 범위(DataStore 영속 경로 — 자동 가능):
 *  - 미설정 시 기본값 = 빈 집합
 *  - toggle(id) → 추가, 재toggle → 제거 (Turbine 방출 확인)
 *  - 여러 id 누적/부분 제거
 *  - 빈 id 무시
 *  - read-modify-write 라운드트립
 *
 * 구현 메모(LiveViewQualityTest 와 동일):
 *  - 즐겨찾기는 평문 `appDataStore` 만 사용 → [EncryptedAppPreferences] 협력자는 무관, relaxed mock.
 *  - 프로세스 단일 DataStore 인스턴스라 @Before 에서 clearAllSettings() 로 초기화.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppPreferencesDataSourceFavoriteFilmLutTest {

    private lateinit var dataSource: AppPreferencesDataSource

    @Before
    fun setUp() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val encryptedPrefs = mockk<EncryptedAppPreferences>(relaxed = true)
        dataSource = AppPreferencesDataSource(context, encryptedPrefs)
        dataSource.clearAllSettings()
    }

    @Test
    fun `미설정 시 기본값은 빈 집합`() = runTest {
        assertEquals(emptySet<String>(), dataSource.favoriteFilmLutIds.first())
    }

    @Test
    fun `toggle 한 번이면 추가된다`() = runTest {
        dataSource.toggleFavoriteFilmLut("luts/bw/kodak_tri-x_400.cube")
        assertEquals(
            setOf("luts/bw/kodak_tri-x_400.cube"),
            dataSource.favoriteFilmLutIds.first()
        )
    }

    @Test
    fun `toggle 두 번이면 제거되어 빈 집합`() = runTest {
        val id = "luts/bw/kodak_tri-x_400.cube"
        dataSource.toggleFavoriteFilmLut(id)
        dataSource.toggleFavoriteFilmLut(id)
        assertEquals(emptySet<String>(), dataSource.favoriteFilmLutIds.first())
    }

    @Test
    fun `여러 id 를 누적 추가한다`() = runTest {
        dataSource.toggleFavoriteFilmLut("a.cube")
        dataSource.toggleFavoriteFilmLut("b.cube")
        dataSource.toggleFavoriteFilmLut("c.cube")
        assertEquals(setOf("a.cube", "b.cube", "c.cube"), dataSource.favoriteFilmLutIds.first())
    }

    @Test
    fun `중간 id 만 제거하면 나머지는 유지된다`() = runTest {
        dataSource.toggleFavoriteFilmLut("a.cube")
        dataSource.toggleFavoriteFilmLut("b.cube")
        dataSource.toggleFavoriteFilmLut("c.cube")

        dataSource.toggleFavoriteFilmLut("b.cube") // 제거

        assertEquals(setOf("a.cube", "c.cube"), dataSource.favoriteFilmLutIds.first())
    }

    @Test
    fun `빈 id 는 무시된다`() = runTest {
        dataSource.toggleFavoriteFilmLut("")
        assertEquals(emptySet<String>(), dataSource.favoriteFilmLutIds.first())
    }

    @Test
    fun `Flow 는 toggle 시 추가-제거를 순서대로 방출`() = runTest {
        val id = "luts/print/fuji.cube"
        dataSource.favoriteFilmLutIds.test {
            assertEquals(emptySet<String>(), awaitItem()) // 초기 빈 집합

            dataSource.toggleFavoriteFilmLut(id)
            assertEquals(setOf(id), awaitItem())

            dataSource.toggleFavoriteFilmLut(id)
            assertEquals(emptySet<String>(), awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `toggle 후 contains 동작 확인`() = runTest {
        val id = "x.cube"
        assertFalse(id in dataSource.favoriteFilmLutIds.first())
        dataSource.toggleFavoriteFilmLut(id)
        assertTrue(id in dataSource.favoriteFilmLutIds.first())
    }
}
