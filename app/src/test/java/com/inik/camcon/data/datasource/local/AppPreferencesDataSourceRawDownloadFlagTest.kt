package com.inik.camcon.data.datasource.local

import android.app.Application
import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
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
 * RAW 파일 다운로드 플래그 반응성 회귀 테스트.
 *
 * 회귀 배경: 751ca31 에서 평문 DataStore → [EncryptedAppPreferences] 이관 시
 * `isRawFileDownloadEnabled` 가 1회성 콜드 flow(`flow { emit(...) }`)로 바뀌어
 * setter 호출 후 재방출이 없었다 → 설정 화면 토글이 눌러도 움직이지 않는 증상.
 * 수정: MutableStateFlow 경유(최초 구독 시 디스크 값 시드, setter 가 재방출).
 *
 * 검증 범위(순수 Kotlin — 자동 가능):
 *  - 최초 구독은 암호화 저장소 값을 방출
 *  - set 직후 같은 구독에 새 값이 재방출 (핵심 회귀 — 구버전은 flow 완료로 실패)
 *  - setter 가 암호화 저장소에도 실제 기록
 *  - clearAllSettings 후 기본값 true 재방출
 *
 * 구현 메모: EncryptedSharedPreferences 는 Robolectric 에서 Keystore 의존이라
 * [EncryptedAppPreferences] 는 backing 변수를 둔 mock 으로 대체한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppPreferencesDataSourceRawDownloadFlagTest {

    private lateinit var dataSource: AppPreferencesDataSource
    private lateinit var encryptedPrefs: EncryptedAppPreferences
    private var stored: Boolean = true

    @Before
    fun setUp() = runTest {
        val context = RuntimeEnvironment.getApplication()
        // 프로세스 단일 DataStore 인스턴스 → 레거시 키 누수 방지를 위해 선정리하되,
        // clearAllSettings 가 상태 flow 를 true 로 시드하므로 테스트 대상 인스턴스는 새로 만든다.
        AppPreferencesDataSource(context, mockk<EncryptedAppPreferences>(relaxed = true), Dispatchers.Unconfined)
            .clearAllSettings()

        stored = true
        encryptedPrefs = mockk(relaxed = true) {
            every { getRawFileDownloadEnabled(any()) } answers { stored }
            every { setRawFileDownloadEnabled(any()) } answers { stored = firstArg() }
            every { hasRawFileDownloadEnabled() } returns true
        }
        dataSource = AppPreferencesDataSource(context, encryptedPrefs, Dispatchers.Unconfined)
    }

    @Test
    fun `최초 구독은 암호화 저장소 값을 방출`() = runTest {
        stored = false
        assertFalse(
            "디스크에 false 가 저장돼 있으면 첫 방출도 false 여야 한다",
            dataSource.isRawFileDownloadEnabled.first()
        )
    }

    @Test
    fun `set 직후 같은 구독에 새 값이 재방출된다`() = runTest {
        dataSource.isRawFileDownloadEnabled.test {
            assertTrue("초기값(디스크 true)", awaitItem())

            dataSource.setRawFileDownloadEnabled(false)
            assertFalse("set(false) 직후 재방출돼야 한다 — 1회성 flow 회귀 감지", awaitItem())

            dataSource.setRawFileDownloadEnabled(true)
            assertTrue("set(true) 재토글도 재방출돼야 한다", awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `setter 는 암호화 저장소에 실제 기록한다`() = runTest {
        dataSource.setRawFileDownloadEnabled(false)

        verify { encryptedPrefs.setRawFileDownloadEnabled(false) }
        assertFalse("backing 저장값이 갱신돼야 한다", stored)
    }

    @Test
    fun `set 이후의 신규 구독도 최신 값을 받는다`() = runTest {
        dataSource.setRawFileDownloadEnabled(false)
        assertEquals(false, dataSource.isRawFileDownloadEnabled.first())
    }

    @Test
    fun `clearAllSettings 후 기본값 true 가 재방출된다`() = runTest {
        dataSource.isRawFileDownloadEnabled.test {
            assertTrue(awaitItem())

            dataSource.setRawFileDownloadEnabled(false)
            assertFalse(awaitItem())

            dataSource.clearAllSettings()
            assertTrue("초기화 후 기본값 true 로 되돌아와야 한다", awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }
}
