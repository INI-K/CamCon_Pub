package com.inik.camcon.domain.usecase

import android.content.Context
import android.util.Log
import com.inik.camcon.R
import com.inik.camcon.domain.model.ImageFormat
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import com.inik.camcon.domain.repository.AppSettingsRepository
import com.inik.camcon.domain.util.Logger
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineScope
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
import com.inik.camcon.util.KoreanStringStubs
import io.mockk.coEvery
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalCoroutinesApi::class)
class ValidateImageFormatUseCaseTest {

    private lateinit var useCase: ValidateImageFormatUseCase
    private lateinit var context: Context
    private lateinit var getSubscriptionUseCase: GetSubscriptionUseCase
    private lateinit var subscriptionRepository: SubscriptionRepository
    private lateinit var appSettingsRepository: FakeAppSettingsRepository
    private lateinit var logger: Logger

    // PR-7 i18n: UseCase 내부 메시지가 R.string.* 자원에서 가져오므로 test 환경에서는
    // KoreanStringStubs.applyTo(context) 한 번 호출로 11 개 키를 모두 한국어 원문으로 stub.
    // 한국어 부분 매칭 검증("비활성화", "PRO" 등)은 stub 값에 자연스럽게 포함되어 무수정 통과.

    private val testDispatcher = StandardTestDispatcher()

    // Fake AppSettingsRepository 구현
    private class FakeAppSettingsRepository : AppSettingsRepository {
        private val _isRawFileDownloadEnabled = MutableStateFlow(true)
        override val isRawFileDownloadEnabled: Flow<Boolean> = _isRawFileDownloadEnabled
        override val isCameraControlsEnabled: Flow<Boolean> = MutableStateFlow(true)
        override val isLiveViewEnabled: Flow<Boolean> = MutableStateFlow(true)
        override val isDarkModeEnabled: Flow<Boolean> = MutableStateFlow(true)
        override val isAutoStartEventListenerEnabled: Flow<Boolean> = MutableStateFlow(true)
        override val isShowLatestPhotoWhenDisabled: Flow<Boolean> = MutableStateFlow(false)
        override val isColorTransferEnabled: Flow<Boolean> = MutableStateFlow(false)
        override val colorTransferReferenceImagePath: Flow<String?> = MutableStateFlow(null)
        override val colorTransferTargetImagePath: Flow<String?> = MutableStateFlow(null)
        override val colorTransferIntensity: Flow<Float> = MutableStateFlow(0.5f)
        override val subscriptionTierEnum: Flow<SubscriptionTier> = MutableStateFlow(SubscriptionTier.FREE)
        override val themeMode: Flow<ThemeMode> = MutableStateFlow(ThemeMode.DARK)
        override val isNativeLogCaptureEnabled: Flow<Boolean> = MutableStateFlow(false)
        override val hasSeenPtpipPreviewWarning: Flow<Boolean> = MutableStateFlow(false)
        override val isShutterSoundEnabled: Flow<Boolean> = MutableStateFlow(true)
        override val isLiveViewGridEnabled: Flow<Boolean> = MutableStateFlow(false)
        override val hasSeenCaptureCoachmark: Flow<Boolean> = MutableStateFlow(false)
        override val lastTimelapseInterval: Flow<Int> = MutableStateFlow(5)
        override val lastTimelapseCount: Flow<Int> = MutableStateFlow(100)
        override val isHistogramEnabled: Flow<Boolean> = MutableStateFlow(false)
        override val isFocusPeakingEnabled: Flow<Boolean> = MutableStateFlow(false)

        override suspend fun setCameraControlsEnabled(enabled: Boolean) {}
        override suspend fun setLiveViewEnabled(enabled: Boolean) {}
        override suspend fun setDarkModeEnabled(enabled: Boolean) {}
        override suspend fun setAutoStartEventListenerEnabled(enabled: Boolean) {}
        override suspend fun setShowLatestPhotoWhenDisabled(enabled: Boolean) {}
        override suspend fun setColorTransferEnabled(enabled: Boolean) {}
        override suspend fun setColorTransferReferenceImagePath(path: String?) {}
        override suspend fun setColorTransferTargetImagePath(path: String?) {}
        override suspend fun setColorTransferIntensity(intensity: Float) {}
        override suspend fun setRawFileDownloadEnabled(enabled: Boolean) {
            _isRawFileDownloadEnabled.value = enabled
        }
        override suspend fun setThemeMode(mode: ThemeMode) {}
        override suspend fun setNativeLogCaptureEnabled(enabled: Boolean) {}
        override suspend fun setHasSeenPtpipPreviewWarning(seen: Boolean) {}
        override suspend fun setShutterSoundEnabled(enabled: Boolean) {}
        override suspend fun setLiveViewGridEnabled(enabled: Boolean) {}
        override suspend fun setHasSeenCaptureCoachmark(seen: Boolean) {}
        override suspend fun setLastTimelapseInterval(seconds: Int) {}
        override suspend fun setLastTimelapseCount(count: Int) {}
        override suspend fun setHistogramEnabled(enabled: Boolean) {}
        override suspend fun setFocusPeakingEnabled(enabled: Boolean) {}
        override suspend fun saveSubscriptionTier(tier: SubscriptionTier?) {}
        override suspend fun clearAllSettings() {}
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

        // PR-7 i18n: R.string.* 자원을 한국어 원문으로 stub (values-ko 매칭).
        // 키 ↔ 한국어 원문 매핑의 단일 진실은 KoreanStringStubs.KEYS.
        context = mockk()
        KoreanStringStubs.applyTo(context)
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
    private suspend fun createUseCase(tier: SubscriptionTier, scope: CoroutineScope = CoroutineScope(testDispatcher)) {
        coEvery { subscriptionRepository.getUserSubscription() } returns flowOf(
            Subscription(tier = tier)
        )
        getSubscriptionUseCase = GetSubscriptionUseCase(subscriptionRepository, appSettingsRepository, logger, scope)
        useCase = ValidateImageFormatUseCase(
            context,
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

    // ── PR1 회귀: isRawFile 공개 메서드 (게이팅 단일 지점 강제) ──

    @Test
    fun `isRawFile - RAW 확장자는 true 반환 (Nikon Canon Sony 등)`() = runTest {
        createUseCase(SubscriptionTier.FREE)
        testDispatcher.scheduler.advanceUntilIdle()

        // 티어와 무관하게 파일 확장자만 보고 판단해야 한다.
        assertTrue(useCase.isRawFile("photo.nef"))
        assertTrue(useCase.isRawFile("photo.cr2"))
        assertTrue(useCase.isRawFile("photo.arw"))
        assertTrue(useCase.isRawFile("/some/path/photo.NEF"))
    }

    @Test
    fun `isRawFile - JPEG PNG 등 비-RAW 파일은 false 반환`() = runTest {
        createUseCase(SubscriptionTier.PRO)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(useCase.isRawFile("photo.jpg"))
        assertFalse(useCase.isRawFile("photo.jpeg"))
        assertFalse(useCase.isRawFile("photo.png"))
        assertFalse(useCase.isRawFile("photo.bmp"))
    }

    @Test
    fun `isRawFile - 알 수 없는 확장자는 false 반환`() = runTest {
        createUseCase(SubscriptionTier.PRO)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(useCase.isRawFile("photo.xyz"))
        assertFalse(useCase.isRawFile("photo"))
        assertFalse(useCase.isRawFile(""))
    }

    // ── PR1 회귀: 5곳 호출 지점이 단일 게이팅 지점을 우회하지 않는지 정적 검증 ──

    /**
     * 정책: `ValidateImageFormatUseCase` 가 RAW 게이팅 단일 진입점. 다른 main 소스에서 직접
     * `SubscriptionUtils.isRawFile` 를 호출하면 게이팅 정책을 우회하므로 회귀로 본다.
     *
     * 본 테스트는 정적 grep 으로 호출 위치를 검증한다(런타임 의존성 없음).
     * 허용 파일: `ValidateImageFormatUseCase.kt` 단 1곳.
     */
    @Test
    fun `정적 회귀 - SubscriptionUtils isRawFile 직접 호출은 ValidateImageFormatUseCase 한 곳에서만 발생해야 한다`() {
        val mainSrcRoot = java.io.File("src/main/java")
        require(mainSrcRoot.isDirectory) { "테스트는 :app 모듈 디렉터리에서 실행되어야 함: ${mainSrcRoot.absolutePath}" }

        val allowedFile = "ValidateImageFormatUseCase.kt"
        val pattern = Regex("""SubscriptionUtils\.isRawFile\s*\(""")
        val violations = mutableListOf<String>()

        mainSrcRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != allowedFile }
            .forEach { ktFile ->
                ktFile.readLines().forEachIndexed { idx, line ->
                    if (pattern.containsMatchIn(line)) {
                        violations += "${ktFile.relativeTo(mainSrcRoot)}:${idx + 1}  $line"
                    }
                }
            }

        assertTrue(
            "RAW 게이팅 단일 지점 위반: 다음 위치에서 SubscriptionUtils.isRawFile 을 직접 호출함.\n" +
                "허용 위치는 ValidateImageFormatUseCase.kt 뿐. 대신 useCase.isRawFile(path) 를 사용하세요.\n" +
                violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    /**
     * 5곳 호출 지점이 `ValidateImageFormatUseCase` 를 주입받고 있는지 정적 검증.
     *
     * 회귀 시나리오: 누군가 `ValidateImageFormatUseCase` 의존성을 제거하고 자체 로직으로 게이팅을
     * 시도하면 본 테스트가 즉시 실패한다.
     */
    @Test
    fun `정적 회귀 - 5곳 호출 지점은 ValidateImageFormatUseCase 를 주입받아야 한다`() {
        val mainSrcRoot = java.io.File("src/main/java")
        require(mainSrcRoot.isDirectory) { "테스트는 :app 모듈 디렉터리에서 실행되어야 함" }

        val expectedInjectionSites = listOf(
            "data/repository/managers/CameraEventManager.kt",
            "presentation/viewmodel/PhotoPreviewViewModel.kt",
            "presentation/viewmodel/photo/PhotoListManager.kt"
            // PhotoPreviewScreen 은 ViewModel 의 downloadPhoto(photo) 경유라 직접 주입 X.
            // AppModule 은 DI factory 라 주입자 목록과는 별개로 검증 대상에서 제외.
        )

        val pattern = Regex("""ValidateImageFormatUseCase""")
        val missing = expectedInjectionSites.filterNot { rel ->
            val file = java.io.File(mainSrcRoot, "com/inik/camcon/$rel")
            file.exists() && pattern.containsMatchIn(file.readText())
        }

        assertTrue(
            "RAW 게이팅 회귀: 다음 호출 지점이 ValidateImageFormatUseCase 를 더 이상 주입받지 않습니다.\n" +
                "PR1 의 단일 지점 정책이 깨졌습니다.\n" +
                missing.joinToString("\n"),
            missing.isEmpty()
        )
    }

    /**
     * `PhotoPreviewScreen` 이 RAW 게이팅을 직접 수행하지 않고 ViewModel 의 `downloadPhoto(photo)` 를
     * 경유하는지 정적 검증. Compose 화면에서 직접 `SubscriptionUtils` 호출은 금지.
     */
    @Test
    fun `정적 회귀 - PhotoPreviewScreen 은 RAW 게이팅을 직접 수행하지 않는다`() {
        val mainSrcRoot = java.io.File("src/main/java")
        require(mainSrcRoot.isDirectory)
        val screen = java.io.File(mainSrcRoot, "com/inik/camcon/presentation/ui/screens/PhotoPreviewScreen.kt")
        if (!screen.exists()) return // 파일 구조 변경 시 본 테스트는 자연스럽게 무의미해짐(빌드 실패가 우선 신호)

        val text = screen.readText()
        val forbidden = Regex("""SubscriptionUtils\.isRawFile\s*\(""")
        val directUseCaseCall = Regex("""validateImageFormatUseCase\s*\.""")

        assertFalse(
            "PhotoPreviewScreen 이 SubscriptionUtils.isRawFile 을 직접 호출함 (정책 위반)",
            forbidden.containsMatchIn(text)
        )
        assertFalse(
            "PhotoPreviewScreen 은 ViewModel.downloadPhoto(photo) 경유여야 하며 UseCase 를 직접 호출하면 안 됨",
            directUseCaseCall.containsMatchIn(text)
        )
    }

    // ── PR1 추가 회귀: canAccessRawFiles 자체 분기 제거 ──

    /**
     * implementer 가 PR1 정정 보고에서 `PhotoPreviewViewModel.canAccessRawFiles` /
     * `PhotoListManager.canAccessRawFiles` 자체 분기를 모두 삭제했다.
     * 누군가 같은 이름으로 다시 자체 분기를 도입하면 단일 지점 정책이 깨진다.
     *
     * main 소스 전체에서 `canAccessRawFiles` 토큰이 0건이어야 한다.
     */
    @Test
    fun `정적 회귀 - canAccessRawFiles 자체 분기 함수는 main 소스에서 0건이어야 한다`() {
        val mainSrcRoot = java.io.File("src/main/java")
        require(mainSrcRoot.isDirectory)

        val pattern = Regex("""\bcanAccessRawFiles\b""")
        val violations = mutableListOf<String>()

        mainSrcRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { ktFile ->
                ktFile.readLines().forEachIndexed { idx, line ->
                    if (pattern.containsMatchIn(line)) {
                        violations += "${ktFile.relativeTo(mainSrcRoot)}:${idx + 1}  $line"
                    }
                }
            }

        assertTrue(
            "canAccessRawFiles 자체 분기 부활: 단일 지점 정책 위반. " +
                "대신 useCase.isRawAllowedForTier / resolveRawAccess 사용.\n" +
                violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    // ── PR1 신규: isRawAllowedForTier 동기 메서드 5 tier 검증 ──

    @Test
    fun `isRawAllowedForTier - PRO ADMIN REFERRER 는 true 반환`() = runTest {
        createUseCase(SubscriptionTier.FREE) // useCase 인스턴스만 필요 (순수 함수)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(useCase.isRawAllowedForTier(SubscriptionTier.PRO))
        assertTrue(useCase.isRawAllowedForTier(SubscriptionTier.ADMIN))
        assertTrue(useCase.isRawAllowedForTier(SubscriptionTier.REFERRER))
    }

    @Test
    fun `isRawAllowedForTier - FREE BASIC 은 false 반환`() = runTest {
        createUseCase(SubscriptionTier.PRO)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(useCase.isRawAllowedForTier(SubscriptionTier.FREE))
        assertFalse(useCase.isRawAllowedForTier(SubscriptionTier.BASIC))
    }

    // ── PR1 신규: resolveRawAccess 동기 메서드 매트릭스 ──
    // 매트릭스: {non-RAW, RAW} × {PRO/ADMIN/REFERRER, FREE, BASIC} × {rawDownloadEnabled true/false}

    @Test
    fun `resolveRawAccess - 비-RAW 파일은 tier 와 설정 무관하게 isSupported true`() = runTest {
        createUseCase(SubscriptionTier.FREE)
        testDispatcher.scheduler.advanceUntilIdle()

        val result = useCase.resolveRawAccess("photo.jpg", SubscriptionTier.FREE, isRawDownloadEnabled = false)
        assertTrue(result.isSupported)
        assertFalse(result.isRawFile)
        assertNull(result.restrictionMessage)
        assertFalse(result.needsUpgrade)
    }

    @Test
    fun `resolveRawAccess - RAW + PRO + rawDownload true 는 허용`() = runTest {
        createUseCase(SubscriptionTier.PRO)
        testDispatcher.scheduler.advanceUntilIdle()

        val result = useCase.resolveRawAccess("photo.cr2", SubscriptionTier.PRO, isRawDownloadEnabled = true)
        assertTrue(result.isSupported)
        assertTrue(result.isRawFile)
        assertEquals("Canon", result.manufacturer)
        assertNull(result.restrictionMessage)
    }

    @Test
    fun `resolveRawAccess - RAW + ADMIN REFERRER 도 허용`() = runTest {
        createUseCase(SubscriptionTier.ADMIN)
        testDispatcher.scheduler.advanceUntilIdle()

        val admin = useCase.resolveRawAccess("photo.nef", SubscriptionTier.ADMIN, isRawDownloadEnabled = true)
        assertTrue(admin.isSupported)
        assertEquals("Nikon", admin.manufacturer)

        val referrer = useCase.resolveRawAccess("photo.arw", SubscriptionTier.REFERRER, isRawDownloadEnabled = true)
        assertTrue(referrer.isSupported)
        assertEquals("Sony", referrer.manufacturer)
    }

    @Test
    fun `resolveRawAccess - RAW + FREE + rawDownload true 는 needsUpgrade true + FREE 메시지`() = runTest {
        createUseCase(SubscriptionTier.FREE)
        testDispatcher.scheduler.advanceUntilIdle()

        val result = useCase.resolveRawAccess("photo.nef", SubscriptionTier.FREE, isRawDownloadEnabled = true)
        assertFalse(result.isSupported)
        assertTrue(result.needsUpgrade)
        assertTrue(result.isRawFile)
        assertEquals("Nikon", result.manufacturer)
        // PR-G7: 통일 RAW 게이팅 메시지(FREE 티어 라벨 포맷).
        assertEquals(KoreanStringStubs.unifiedGatingMessageKo("FREE"), result.restrictionMessage)
    }

    @Test
    fun `resolveRawAccess - RAW + BASIC + rawDownload true 는 needsUpgrade true + BASIC 메시지`() = runTest {
        createUseCase(SubscriptionTier.BASIC)
        testDispatcher.scheduler.advanceUntilIdle()

        val result = useCase.resolveRawAccess("photo.arw", SubscriptionTier.BASIC, isRawDownloadEnabled = true)
        assertFalse(result.isSupported)
        assertTrue(result.needsUpgrade)
        assertTrue(result.isRawFile)
        assertEquals("Sony", result.manufacturer)
        // PR-G7: 통일 RAW 게이팅 메시지(BASIC 티어 라벨 포맷).
        assertEquals(KoreanStringStubs.unifiedGatingMessageKo("BASIC"), result.restrictionMessage)
    }

    @Test
    fun `resolveRawAccess - rawDownload false 는 tier 무관하게 isSupported false + 설정 비활성화 메시지`() = runTest {
        createUseCase(SubscriptionTier.PRO)
        testDispatcher.scheduler.advanceUntilIdle()

        // PRO 라도 rawDownload 비활성화 시 차단.
        val pro = useCase.resolveRawAccess("photo.cr2", SubscriptionTier.PRO, isRawDownloadEnabled = false)
        assertFalse(pro.isSupported)
        assertFalse("rawDownload 설정 비활성화는 업그레이드 권유 케이스가 아님", pro.needsUpgrade)
        assertTrue(pro.isRawFile)
        assertTrue("설정 비활성화 메시지 포함", pro.restrictionMessage!!.contains("비활성화"))

        // FREE 도 동일 차단 (메시지는 설정 비활성화 우선 — 업그레이드 권유 없음).
        val free = useCase.resolveRawAccess("photo.nef", SubscriptionTier.FREE, isRawDownloadEnabled = false)
        assertFalse(free.isSupported)
        assertFalse(free.needsUpgrade)
        assertTrue(free.restrictionMessage!!.contains("비활성화"))
    }
}
