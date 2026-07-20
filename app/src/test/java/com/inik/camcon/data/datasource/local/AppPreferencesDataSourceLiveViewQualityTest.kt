package com.inik.camcon.data.datasource.local

import android.app.Application
import app.cash.turbine.test
import com.inik.camcon.domain.model.LiveViewQuality
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 라이브뷰 화질 설정 영속 단위 테스트.
 *
 * 설계서: `.claude/_workspace/liveview_quality_design.md` §11
 * 대상: [AppPreferencesDataSource.liveViewQuality] Flow getter + [AppPreferencesDataSource.setLiveViewQuality] setter.
 *
 * 검증 범위(순수 Kotlin / DataStore 경로 — 자동 가능):
 *  - 미설정 시 기본값 = QUALITY
 *  - set(SPEED/BALANCED) → Flow 가 해당 값을 방출 (Turbine)
 *  - int↔enum 라운드트립(SPEED/BALANCED/QUALITY)
 *  - 미지 정수(예 99) 가 저장돼 있을 때 [LiveViewQuality.fromValue] 폴백 → BALANCED
 *
 * 구현 메모:
 *  - `liveViewQuality`/`setLiveViewQuality` 는 평문 `appDataStore` 만 사용하므로
 *    [EncryptedAppPreferences] 협력자는 동작에 무관 → relaxed mock 으로 대체.
 *  - DataStore 는 파일 기반이라 Robolectric Application Context 로 실제 저장/읽기를 검증한다.
 *    top-level `appDataStore`(name="app_settings") 는 프로세스 단일 인스턴스라 테스트 간
 *    상태가 누수될 수 있어 @Before 에서 clearAllSettings() 로 초기화한다.
 *
 * 단위 테스트 불가 경로(명시): 저장된 화질 정수가 JNI(`CameraNative.setLiveViewQuality`) →
 * `camera_liveview.cpp` `g_liveViewQuality` atomic → `enableLiveView` 의 `liveviewsize`
 * choice 분기로 흐르는 실제 적용은 USB/실기기·camlib 영역. 본 테스트는 DataStore 영속만 검증한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppPreferencesDataSourceLiveViewQualityTest {

    private lateinit var dataSource: AppPreferencesDataSource

    @Before
    fun setUp() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val encryptedPrefs = mockk<EncryptedAppPreferences>(relaxed = true)
        dataSource = AppPreferencesDataSource(context, encryptedPrefs, Dispatchers.Unconfined)
        // 프로세스 단일 DataStore 인스턴스 → 테스트 간 누수 방지.
        dataSource.clearAllSettings()
    }

    @Test
    fun `미설정 시 기본값은 QUALITY`() = runTest {
        assertEquals(
            "live_view_quality 키 미설정이면 QUALITY 가 기본값이어야 한다",
            LiveViewQuality.QUALITY,
            dataSource.liveViewQuality.first()
        )
    }

    @Test
    fun `setLiveViewQuality(BALANCED) 호출 시 Flow 가 BALANCED 를 방출`() = runTest {
        dataSource.liveViewQuality.test {
            // 초기값(미설정 기본값) = QUALITY
            assertEquals(LiveViewQuality.QUALITY, awaitItem())

            dataSource.setLiveViewQuality(LiveViewQuality.BALANCED)

            assertEquals(
                "set 직후 Flow 는 BALANCED 를 방출해야 한다",
                LiveViewQuality.BALANCED,
                awaitItem()
            )
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `setLiveViewQuality(SPEED) 호출 시 Flow 가 SPEED 를 방출`() = runTest {
        dataSource.liveViewQuality.test {
            assertEquals(LiveViewQuality.QUALITY, awaitItem())

            dataSource.setLiveViewQuality(LiveViewQuality.SPEED)

            assertEquals(LiveViewQuality.SPEED, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `int-enum 라운드트립 - SPEED`() = runTest {
        dataSource.setLiveViewQuality(LiveViewQuality.SPEED)
        assertEquals(LiveViewQuality.SPEED, dataSource.liveViewQuality.first())
    }

    @Test
    fun `int-enum 라운드트립 - BALANCED`() = runTest {
        dataSource.setLiveViewQuality(LiveViewQuality.BALANCED)
        assertEquals(LiveViewQuality.BALANCED, dataSource.liveViewQuality.first())
    }

    @Test
    fun `int-enum 라운드트립 - QUALITY`() = runTest {
        dataSource.setLiveViewQuality(LiveViewQuality.QUALITY)
        assertEquals(LiveViewQuality.QUALITY, dataSource.liveViewQuality.first())
    }

    /**
     * 미지 정수(예 99)가 디스크에 들어 있어도(저장 포맷 변경/손상) fromValue 폴백으로
     * 크래시 없이 BALANCED 로 안전하게 수렴하는지 — 견고성 보장.
     *
     * `appDataStore` delegate 는 main 소스 private 이라 테스트에서 같은 파일명으로 재선언하면
     * "multiple DataStores active for the same file" 충돌이 난다. 따라서 getter 가
     * 의존하는 [LiveViewQuality.fromValue] 폴백을 직접 검증해 DataSource getter
     * (`preferences[LIVE_VIEW_QUALITY] ?: BALANCED.value` → `fromValue`)의 견고성을 뒷받침한다.
     */
    @Test
    fun `미지 정수는 fromValue 폴백으로 BALANCED`() {
        assertEquals(
            "알 수 없는 정수(99)는 BALANCED 로 폴백되어야 한다",
            LiveViewQuality.BALANCED,
            LiveViewQuality.fromValue(99)
        )
    }

    @Test
    fun `음수 정수는 fromValue 폴백으로 BALANCED`() {
        assertEquals(LiveViewQuality.BALANCED, LiveViewQuality.fromValue(-1))
    }

    @Test
    fun `정상 정수는 fromValue 로 대응 enum`() {
        assertEquals(LiveViewQuality.SPEED, LiveViewQuality.fromValue(0))
        assertEquals(LiveViewQuality.BALANCED, LiveViewQuality.fromValue(1))
        assertEquals(LiveViewQuality.QUALITY, LiveViewQuality.fromValue(2))
    }
}
