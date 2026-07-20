package com.inik.camcon.data.datasource.local

import android.app.Application
import app.cash.turbine.test
import com.inik.camcon.domain.model.SubscriptionTier
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 구독 티어 flow 반응성 회귀 테스트.
 *
 * 회귀 배경: 751ca31 에서 평문 DataStore → [EncryptedAppPreferences] 이관 시
 * `subscriptionTier` 가 1회성 콜드 flow 로 바뀌어 세션 중 티어 변경(구매/강등)이
 * 열려 있는 화면(AppSettingsViewModel combine)에 재방출되지 않았다.
 * 수정: MutableStateFlow(TierHolder) 경유 — RAW 플래그(bfb80ef)와 동일 패턴.
 *
 * 검증 범위(순수 Kotlin — 자동 가능):
 *  - 최초 구독은 암호화 저장소 값을 방출 (null → enum FREE 폴백 포함)
 *  - setSubscriptionTier 직후 같은 구독에 새 값이 재방출 (핵심 회귀)
 *  - subscriptionTierEnum 파생 flow 도 함께 재방출
 *  - clearAllSettings 후 null(=FREE) 재방출
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppPreferencesDataSourceSubscriptionTierFlowTest {

    private lateinit var dataSource: AppPreferencesDataSource
    private lateinit var encryptedPrefs: EncryptedAppPreferences
    private var storedTier: String? = null

    @Before
    fun setUp() = runTest {
        val context = RuntimeEnvironment.getApplication()
        // 프로세스 단일 DataStore 인스턴스 → 레거시 키 누수 방지를 위해 선정리하되,
        // clearAllSettings 가 상태 flow 를 시드하므로 테스트 대상 인스턴스는 새로 만든다.
        AppPreferencesDataSource(context, mockk<EncryptedAppPreferences>(relaxed = true), Dispatchers.Unconfined)
            .clearAllSettings()

        storedTier = null
        encryptedPrefs = mockk(relaxed = true) {
            every { getSubscriptionTierString() } answers { storedTier }
            every { setSubscriptionTierString(any()) } answers { storedTier = firstArg() }
            every { hasSubscriptionTier() } returns true
        }
        dataSource = AppPreferencesDataSource(context, encryptedPrefs, Dispatchers.Unconfined)
    }

    @Test
    fun `최초 구독은 암호화 저장소 값을 방출`() = runTest {
        storedTier = "PRO"
        assertEquals("PRO", dataSource.subscriptionTier.first())
    }

    @Test
    fun `미설정(null) 티어는 enum FREE 로 폴백`() = runTest {
        assertEquals(SubscriptionTier.FREE, dataSource.subscriptionTierEnum.first())
    }

    @Test
    fun `setSubscriptionTier 직후 같은 구독에 새 값이 재방출된다`() = runTest {
        dataSource.subscriptionTier.test {
            assertNull("초기값(디스크 null)", awaitItem())

            dataSource.setSubscriptionTier("PRO")
            assertEquals("set(PRO) 직후 재방출돼야 한다 — 1회성 flow 회귀 감지", "PRO", awaitItem())

            dataSource.setSubscriptionTier("FREE")
            assertEquals("강등도 재방출돼야 한다", "FREE", awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `subscriptionTierEnum 파생 flow 도 재방출된다`() = runTest {
        dataSource.subscriptionTierEnum.test {
            assertEquals(SubscriptionTier.FREE, awaitItem())

            dataSource.setSubscriptionTier("ADMIN")
            assertEquals(SubscriptionTier.ADMIN, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `setter 는 암호화 저장소에 실제 기록한다`() = runTest {
        dataSource.setSubscriptionTier("BASIC")

        verify { encryptedPrefs.setSubscriptionTierString("BASIC") }
        assertEquals("BASIC", storedTier)
    }

    @Test
    fun `clearAllSettings 후 null 이 재방출된다`() = runTest {
        dataSource.subscriptionTier.test {
            assertNull(awaitItem())

            dataSource.setSubscriptionTier("PRO")
            assertEquals("PRO", awaitItem())

            dataSource.clearAllSettings()
            assertNull("초기화 후 티어는 null 로 되돌아와야 한다", awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }
}
