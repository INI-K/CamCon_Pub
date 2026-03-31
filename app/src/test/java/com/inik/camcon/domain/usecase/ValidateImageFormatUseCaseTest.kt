package com.inik.camcon.domain.usecase

import android.util.Log
import com.inik.camcon.domain.model.ImageFormat
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.repository.AppSettingsRepository
import com.inik.camcon.domain.util.Logger
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.inik.camcon.domain.model.Subscription
import com.inik.camcon.domain.repository.SubscriptionRepository
import io.mockk.coEvery
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalCoroutinesApi::class)
class ValidateImageFormatUseCaseTest {

    private lateinit var useCase: ValidateImageFormatUseCase
    private lateinit var getSubscriptionUseCase: GetSubscriptionUseCase
    private lateinit var subscriptionRepository: SubscriptionRepository
    private lateinit var appSettingsRepository: FakeAppSettingsRepository
    private lateinit var logger: Logger

    private val testDispatcher = StandardTestDispatcher()

    // Fake AppSettingsRepository 구현
    private class FakeAppSettingsRepository : AppSettingsRepository {
        private val _isRawFileDownloadEnabled = MutableStateFlow(true)
        override val isRawFileDownloadEnabled = _isRawFileDownloadEnabled

        fun setRawFileDownloadEnabled(enabled: Boolean) {
            _isRawFileDownloadEnabled.value = enabled
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.i(any(), any()) } returns 0
        subscriptionRepository = mockk()
        appSettingsRepository = FakeAppSettingsRepository()
        logger = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    /**
     * GetSubscriptionUseCase의 init 블록이 Dispatchers.IO에서 refreshSubscription을 실행하므로
     * subscriptionStateFlow가 업데이트될 때까지 대기해야 한다.
     */
    private suspend fun createUseCase(tier: SubscriptionTier) {
        coEvery { subscriptionRepository.getUserSubscription() } returns flowOf(
            Subscription(tier = tier)
        )
        getSubscriptionUseCase = GetSubscriptionUseCase(subscriptionRepository, logger)
        useCase = ValidateImageFormatUseCase(
            getSubscriptionUseCase,
            appSettingsRepository,
            logger
        )
        // init의 IO 코루틴이 subscriptionStateFlow를 업데이트할 때까지 대기
        waitForSubscriptionTier(tier)
    }

    private suspend fun waitForSubscriptionTier(expectedTier: SubscriptionTier) {
        val startTime = System.currentTimeMillis()
        while (getSubscriptionUseCase.getSubscriptionTier().first() != expectedTier) {
            if (System.currentTimeMillis() - startTime > 2000) {
                throw AssertionError("2초 내에 구독 티어가 $expectedTier 로 업데이트되지 않음")
            }
            kotlinx.coroutines.yield()
        }
    }

    // --- isFormatSupported: JPEG ---

    @Test
    fun `JPEG 파일은 모든 티어에서 지원됨`() = runTest {
        for (tier in SubscriptionTier.values()) {
            createUseCase(tier)
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue("$tier 에서 JPG 지원 실패", useCase.isFormatSupported("photo.jpg"))
            assertTrue("$tier 에서 JPEG 지원 실패", useCase.isFormatSupported("photo.jpeg"))
        }
    }

    // --- isFormatSupported: RAW + PRO ---

    @Test
    fun `PRO 티어에서 RAW 다운로드 활성화시 RAW 파일 지원`() = runTest {
        createUseCase(SubscriptionTier.PRO)
        appSettingsRepository.setRawFileDownloadEnabled(true)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(useCase.isFormatSupported("photo.nef"))
        assertTrue(useCase.isFormatSupported("photo.cr2"))
        assertTrue(useCase.isFormatSupported("photo.arw"))
    }

    @Test
    fun `PRO 티어에서 RAW 다운로드 비활성화시 RAW 파일 미지원`() = runTest {
        createUseCase(SubscriptionTier.PRO)
        appSettingsRepository.setRawFileDownloadEnabled(false)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(useCase.isFormatSupported("photo.nef"))
    }

    @Test
    fun `ADMIN 티어에서 RAW 파일 지원`() = runTest {
        createUseCase(SubscriptionTier.ADMIN)
        appSettingsRepository.setRawFileDownloadEnabled(true)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(useCase.isFormatSupported("photo.cr2"))
    }

    @Test
    fun `REFERRER 티어에서 RAW 파일 지원`() = runTest {
        createUseCase(SubscriptionTier.REFERRER)
        appSettingsRepository.setRawFileDownloadEnabled(true)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(useCase.isFormatSupported("photo.arw"))
    }

    // --- isFormatSupported: RAW + FREE/BASIC ---

    @Test
    fun `FREE 티어에서 RAW 파일 미지원`() = runTest {
        createUseCase(SubscriptionTier.FREE)
        appSettingsRepository.setRawFileDownloadEnabled(true)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(useCase.isFormatSupported("photo.nef"))
    }

    @Test
    fun `BASIC 티어에서 RAW 파일 미지원`() = runTest {
        createUseCase(SubscriptionTier.BASIC)
        appSettingsRepository.setRawFileDownloadEnabled(true)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(useCase.isFormatSupported("photo.cr2"))
    }

    // --- isFormatSupported: 알 수 없는 확장자 ---

    @Test
    fun `지원하지 않는 확장자는 false 반환`() = runTest {
        createUseCase(SubscriptionTier.PRO)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(useCase.isFormatSupported("photo.bmp"))
        assertFalse(useCase.isFormatSupported("photo.png"))
    }

    // --- validateFormat: 상세 검증 ---

    @Test
    fun `validateFormat - FREE 티어에서 RAW 파일 접근시 needsUpgrade true`() = runTest {
        createUseCase(SubscriptionTier.FREE)
        appSettingsRepository.setRawFileDownloadEnabled(true)
        testDispatcher.scheduler.advanceUntilIdle()

        val result = useCase.validateFormat("photo.nef")

        assertFalse(result.isSupported)
        assertTrue(result.needsUpgrade)
        assertTrue(result.isRawFile)
        assertEquals("Nikon", result.manufacturer)
        assertNotNull(result.restrictionMessage)
    }

    @Test
    fun `validateFormat - PRO 티어에서 RAW 다운로드 활성화시 지원`() = runTest {
        createUseCase(SubscriptionTier.PRO)
        appSettingsRepository.setRawFileDownloadEnabled(true)
        testDispatcher.scheduler.advanceUntilIdle()

        val result = useCase.validateFormat("photo.cr2")

        assertTrue(result.isSupported)
        assertTrue(result.isRawFile)
        assertEquals("Canon", result.manufacturer)
        assertNull(result.restrictionMessage)
    }

    @Test
    fun `validateFormat - RAW 다운로드 비활성화시 설정 비활성화 메시지`() = runTest {
        createUseCase(SubscriptionTier.PRO)
        appSettingsRepository.setRawFileDownloadEnabled(false)
        testDispatcher.scheduler.advanceUntilIdle()

        val result = useCase.validateFormat("photo.nef")

        assertFalse(result.isSupported)
        assertFalse(result.needsUpgrade)
        assertTrue(result.isRawFile)
        assertTrue(result.restrictionMessage!!.contains("비활성화"))
    }

    @Test
    fun `validateFormat - JPEG 파일은 모든 티어에서 지원`() = runTest {
        createUseCase(SubscriptionTier.FREE)
        testDispatcher.scheduler.advanceUntilIdle()

        val result = useCase.validateFormat("photo.jpg")

        assertTrue(result.isSupported)
        assertFalse(result.isRawFile)
    }

    @Test
    fun `validateFormat - 지원하지 않는 확장자는 미지원 메시지`() = runTest {
        createUseCase(SubscriptionTier.PRO)
        testDispatcher.scheduler.advanceUntilIdle()

        val result = useCase.validateFormat("photo.xyz")

        assertFalse(result.isSupported)
        assertNotNull(result.restrictionMessage)
    }

    // --- validateRawFileAccess ---

    @Test
    fun `validateRawFileAccess - RAW가 아닌 파일은 항상 통과`() = runTest {
        createUseCase(SubscriptionTier.FREE)
        testDispatcher.scheduler.advanceUntilIdle()

        val result = useCase.validateRawFileAccess("photo.jpg")

        assertTrue(result.isSupported)
    }

    @Test
    fun `validateRawFileAccess - BASIC 티어에서 RAW 파일 접근시 BASIC용 메시지`() = runTest {
        createUseCase(SubscriptionTier.BASIC)
        appSettingsRepository.setRawFileDownloadEnabled(true)
        testDispatcher.scheduler.advanceUntilIdle()

        val result = useCase.validateRawFileAccess("photo.arw")

        assertFalse(result.isSupported)
        assertTrue(result.needsUpgrade)
        assertTrue(result.isRawFile)
        assertEquals("Sony", result.manufacturer)
        assertTrue(result.restrictionMessage!!.contains("PRO"))
    }

    // --- isFormatSupportedForTier ---

    @Test
    fun `isFormatSupportedForTier - JPG는 모든 티어에서 지원`() = runTest {
        for (tier in SubscriptionTier.values()) {
            createUseCase(tier)
            assertTrue(useCase.isFormatSupportedForTier(ImageFormat.JPG, tier))
        }
    }

    // --- AppSettingsRepository 인터페이스 기반 검증 ---

    @Test
    fun `AppSettingsRepository의 isRawFileDownloadEnabled가 false에서 true로 변경되면 RAW 접근 가능`() = runTest {
        createUseCase(SubscriptionTier.PRO)
        testDispatcher.scheduler.advanceUntilIdle()

        // Given: RAW 다운로드 비활성화
        appSettingsRepository.setRawFileDownloadEnabled(false)
        assertFalse(useCase.isFormatSupported("photo.nef"))

        // When: RAW 다운로드 활성화
        appSettingsRepository.setRawFileDownloadEnabled(true)

        // Then: RAW 파일 지원
        assertTrue(useCase.isFormatSupported("photo.nef"))
    }
}
