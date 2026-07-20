package com.inik.camcon.domain.usecase

import app.cash.turbine.test
import com.inik.camcon.domain.model.Subscription
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.model.User
import com.inik.camcon.domain.repository.AppSettingsRepository
import com.inik.camcon.domain.repository.AuthRepository
import com.inik.camcon.domain.repository.SubscriptionRepository
import com.inik.camcon.domain.util.Logger
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GetSubscriptionUseCaseTest {

    private lateinit var useCase: GetSubscriptionUseCase
    private lateinit var subscriptionRepository: SubscriptionRepository
    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var logger: Logger

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        subscriptionRepository = mockk()
        // init 이 결제 검증 신호를 collect 하므로 기본은 무신호(emptyFlow)로 stub.
        every { subscriptionRepository.subscriptionRefreshSignals } returns emptyFlow()
        appSettingsRepository = mockk(relaxed = true)
        // seedFromCache()가 first()에서 멈추지 않도록 캐시 티어 기본 stub.
        every { appSettingsRepository.subscriptionTierEnum } returns flowOf(SubscriptionTier.FREE)
        authRepository = mockk()
        // 기본은 비로그인 — 로그인 관찰이 추가 refresh 를 트리거하지 않는다.
        every { authRepository.getCurrentUser() } returns flowOf(null)
        logger = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `초기 상태는 FREE 티어`() = runTest {
        // Given
        coEvery { subscriptionRepository.getUserSubscription() } returns flowOf(
            Subscription(tier = SubscriptionTier.FREE)
        )

        // When
        useCase = GetSubscriptionUseCase(subscriptionRepository, appSettingsRepository, authRepository, logger, this)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        val result = useCase().value
        assertEquals(SubscriptionTier.FREE, result.tier)
    }

    @Test
    fun `getSubscriptionTier는 현재 티어를 Flow로 반환`() = runTest {
        // Given
        val proSubscription = Subscription(tier = SubscriptionTier.PRO)
        coEvery { subscriptionRepository.getUserSubscription() } returns flowOf(proSubscription)

        useCase = GetSubscriptionUseCase(subscriptionRepository, appSettingsRepository, authRepository, logger, this)
        testDispatcher.scheduler.advanceUntilIdle()

        // When & Then
        useCase.getSubscriptionTier().test {
            val tier = awaitItem()
            assertEquals(SubscriptionTier.PRO, tier)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `tier가 null이면 FREE를 반환`() = runTest {
        // Given
        val nullTierSubscription = Subscription(tier = null)
        coEvery { subscriptionRepository.getUserSubscription() } returns flowOf(nullTierSubscription)

        useCase = GetSubscriptionUseCase(subscriptionRepository, appSettingsRepository, authRepository, logger, this)
        testDispatcher.scheduler.advanceUntilIdle()

        // When & Then
        useCase.getSubscriptionTier().test {
            val tier = awaitItem()
            assertEquals(SubscriptionTier.FREE, tier)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `refreshSubscription with forceSync true면 syncSubscriptionStatus 호출`() = runTest {
        // Given
        coEvery { subscriptionRepository.getUserSubscription() } returns flowOf(
            Subscription(tier = SubscriptionTier.FREE)
        )
        coEvery { subscriptionRepository.syncSubscriptionStatus() } returns Unit

        useCase = GetSubscriptionUseCase(subscriptionRepository, appSettingsRepository, authRepository, logger, this)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        useCase.refreshSubscription(forceSync = true)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify { subscriptionRepository.syncSubscriptionStatus() }
    }

    @Test
    fun `ADMIN 티어 구독 정보 반환`() = runTest {
        // Given
        val adminSubscription = Subscription(tier = SubscriptionTier.ADMIN, isActive = true)
        coEvery { subscriptionRepository.getUserSubscription() } returns flowOf(adminSubscription)

        useCase = GetSubscriptionUseCase(subscriptionRepository, appSettingsRepository, authRepository, logger, this)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        val result = useCase().value

        // Then
        assertEquals(SubscriptionTier.ADMIN, result.tier)
        assertEquals(true, result.isActive)
    }

    @Test
    fun `syncSubscriptionStatus 호출시 구독 상태 동기화`() = runTest {
        // Given
        coEvery { subscriptionRepository.getUserSubscription() } returns flowOf(
            Subscription(tier = SubscriptionTier.FREE)
        )
        coEvery { subscriptionRepository.syncSubscriptionStatus() } returns Unit

        useCase = GetSubscriptionUseCase(subscriptionRepository, appSettingsRepository, authRepository, logger, this)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        useCase.syncSubscriptionStatus()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify { subscriptionRepository.syncSubscriptionStatus() }
    }

    @Test
    fun `캐시가 PRO면 Firestore 갱신 전 초기값이 PRO`() = runTest {
        // Given: 캐시 티어는 PRO, Firestore 조회는 끝나지 않는 Flow(아직 갱신 전 상황 모사)
        every { appSettingsRepository.subscriptionTierEnum } returns flowOf(SubscriptionTier.PRO)
        coEvery { subscriptionRepository.getUserSubscription() } returns emptyFlow()

        // When
        useCase = GetSubscriptionUseCase(subscriptionRepository, appSettingsRepository, authRepository, logger, this)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: Firestore가 값을 주지 않았으므로 seed된 PRO가 유지
        val result = useCase().value
        assertEquals(SubscriptionTier.PRO, result.tier)
    }

    @Test
    fun `캐시 PRO여도 Firestore가 FREE면 최종 FREE로 수렴`() = runTest {
        // Given: 캐시는 PRO지만 Firestore 실값은 FREE
        every { appSettingsRepository.subscriptionTierEnum } returns flowOf(SubscriptionTier.PRO)
        coEvery { subscriptionRepository.getUserSubscription() } returns flowOf(
            Subscription(tier = SubscriptionTier.FREE)
        )

        // When
        useCase = GetSubscriptionUseCase(subscriptionRepository, appSettingsRepository, authRepository, logger, this)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: refresh가 seed를 덮어써 최종 FREE
        val result = useCase().value
        assertEquals(SubscriptionTier.FREE, result.tier)
    }

    // === 회귀: logCurrentTier는 서버 재조회 없이 캐시값만 읽는다 (Firestore read 절감) ===

    @Test
    fun `logCurrentTier는 서버 재조회(getUserSubscription)를 트리거하지 않는다`() = runTest {
        // Given: init에서 정당한 refresh 1회만 수행
        coEvery { subscriptionRepository.getUserSubscription() } returns flowOf(
            Subscription(tier = SubscriptionTier.PRO)
        )
        coEvery { subscriptionRepository.syncSubscriptionStatus() } returns Unit

        useCase = GetSubscriptionUseCase(subscriptionRepository, appSettingsRepository, authRepository, logger, this)
        testDispatcher.scheduler.advanceUntilIdle()

        // init의 호출 기록만 지우고 stub(answers)은 유지 → logCurrentTier 단독 행위만 관찰
        clearMocks(subscriptionRepository, answers = false)

        // When: 진단 로그 호출
        useCase.logCurrentTier()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 서버 재조회/동기화가 전혀 일어나지 않는다
        coVerify(exactly = 0) { subscriptionRepository.getUserSubscription() }
        coVerify(exactly = 0) { subscriptionRepository.syncSubscriptionStatus() }
    }

    @Test
    fun `logCurrentTier는 캐시된 현재 티어값을 로그로 출력한다`() = runTest {
        // Given: init refresh로 캐시 StateFlow가 PRO로 채워짐
        coEvery { subscriptionRepository.getUserSubscription() } returns flowOf(
            Subscription(tier = SubscriptionTier.PRO)
        )

        useCase = GetSubscriptionUseCase(subscriptionRepository, appSettingsRepository, authRepository, logger, this)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        useCase.logCurrentTier()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 캐시된 값(PRO)이 티어 로그로 출력된다
        verify { logger.i("GetSubscriptionUseCase", " 티어: PRO") }
    }

    // === item B: 로그인 성공 후 구독 재조회 트리거 ===

    @Test
    fun `로그인 이벤트가 감지되면 구독을 재조회한다`() = runTest {
        // Given: 비로그인 시점에 init refresh 실행(캐시 FREE), 이후 로그인 사용자 등장
        every { appSettingsRepository.subscriptionTierEnum } returns flowOf(SubscriptionTier.FREE)
        every { authRepository.getCurrentUser() } returns flowOf(
            User(id = "uid-1", email = "u@x.com", displayName = "U")
        )
        coEvery { subscriptionRepository.getUserSubscription() } returns flowOf(
            Subscription(tier = SubscriptionTier.PRO, isActive = true)
        )

        // When
        useCase = GetSubscriptionUseCase(subscriptionRepository, appSettingsRepository, authRepository, logger, this)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 로그인 관찰이 init(1회) 위에 재조회를 추가로 트리거 → 서버 티어(PRO)로 수렴
        coVerify(atLeast = 2) { subscriptionRepository.getUserSubscription() }
        assertEquals(SubscriptionTier.PRO, useCase().value.tier)
    }

    @Test
    fun `생성 후 로그인 발생 시 재조회가 트리거된다 (getCurrentUser 반응형)`() = runTest {
        // Given: 생성 시점 비로그인(재설치/로그아웃 후 부팅), 반응형 인증 소스를 MutableStateFlow 로 모사
        every { appSettingsRepository.subscriptionTierEnum } returns flowOf(SubscriptionTier.FREE)
        val userFlow = MutableStateFlow<User?>(null)
        every { authRepository.getCurrentUser() } returns userFlow
        coEvery { subscriptionRepository.getUserSubscription() } returns flowOf(
            Subscription(tier = SubscriptionTier.PRO, isActive = true)
        )
        // getCurrentUser 가 무한 hot flow 이므로 취소 가능한 별도 scope 사용(runTest 누수 방지)
        val ucScope = CoroutineScope(testDispatcher)

        // When: 생성 — 아직 비로그인
        useCase = GetSubscriptionUseCase(
            subscriptionRepository, appSettingsRepository, authRepository, logger, ucScope
        )
        testDispatcher.scheduler.advanceUntilIdle()
        // 생성 직후: init refresh 1회만(로그인 관찰은 uid=null 이라 미발화)
        coVerify(exactly = 1) { subscriptionRepository.getUserSubscription() }

        // When: 앱 기동 후 로그인 발생(getCurrentUser 가 새 사용자 방출)
        userFlow.value = User(id = "uid-1", email = "u@x", displayName = "U")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 로그인 관찰이 재조회를 추가 트리거 → 서버 티어(PRO)로 수렴
        coVerify(atLeast = 2) { subscriptionRepository.getUserSubscription() }
        assertEquals(SubscriptionTier.PRO, useCase().value.tier)

        ucScope.cancel()
    }

    @Test
    fun `비로그인이면 로그인 트리거 재조회가 없다 (init 1회만)`() = runTest {
        // Given: getCurrentUser 가 null 만 방출(setUp 기본) → 로그인 트리거 없음
        every { appSettingsRepository.subscriptionTierEnum } returns flowOf(SubscriptionTier.FREE)
        coEvery { subscriptionRepository.getUserSubscription() } returns flowOf(
            Subscription(tier = SubscriptionTier.FREE, isAuthoritative = false)
        )

        // When
        useCase = GetSubscriptionUseCase(subscriptionRepository, appSettingsRepository, authRepository, logger, this)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: init refresh 1회만 (로그인 관찰은 uid=null 이라 refresh 안 함)
        coVerify(exactly = 1) { subscriptionRepository.getUserSubscription() }
    }

    // === item A 보강 / (d): seed 가드가 drop(1) 전제를 보존한다 ===

    @Test
    fun `캐시가 FREE면 seed는 StateFlow를 시딩하지 않는다 (drop(1) 전제 보존)`() = runTest {
        // Given: 캐시 FREE, refresh 는 값을 주지 않음(seed 단독 관찰)
        every { appSettingsRepository.subscriptionTierEnum } returns flowOf(SubscriptionTier.FREE)
        coEvery { subscriptionRepository.getUserSubscription() } returns emptyFlow()

        // When
        useCase = GetSubscriptionUseCase(subscriptionRepository, appSettingsRepository, authRepository, logger, this)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: seed 가드(cache==FREE)로 시딩 스킵 → 초기 비권위 기본값 그대로(추가 emit 없음)
        val result = useCase().value
        assertEquals(SubscriptionTier.FREE, result.tier)
        assertEquals(false, result.isAuthoritative)
    }

    @Test
    fun `초기 StateFlow 기본값은 비권위 FREE (권위 오인 방지)`() = runTest {
        // Given: refresh 미완(값 없음)이라 초기 기본값이 그대로 노출되는 상황
        every { appSettingsRepository.subscriptionTierEnum } returns flowOf(SubscriptionTier.FREE)
        coEvery { subscriptionRepository.getUserSubscription() } returns emptyFlow()

        // When
        useCase = GetSubscriptionUseCase(subscriptionRepository, appSettingsRepository, authRepository, logger, this)
        // advance 하지 않아 refresh 전 초기값을 관찰

        // Then: 초기 기본값은 isAuthoritative=false (ObserveEffectiveTierUseCase 가 pref 를 무시하지 않도록)
        val initial = useCase().value
        assertEquals(SubscriptionTier.FREE, initial.tier)
        assertEquals(false, initial.isAuthoritative)
    }

    // === item 2: 계정 전환 시 in-memory 구독 리셋 (크로스 계정 PRO 상속 방지) ===

    @Test
    fun `계정 전환 시 이전 계정 PRO 가 새 계정에 상속되지 않는다`() = runTest {
        // Given: A(PRO) 로그인 상태로 생성, 이후 로그아웃 → B 로그인(오프라인 조회 실패)
        every { appSettingsRepository.subscriptionTierEnum } returns flowOf(SubscriptionTier.FREE)
        val userFlow = MutableStateFlow<User?>(
            User(id = "uid-A", email = "a@x", displayName = "A")
        )
        every { authRepository.getCurrentUser() } returns userFlow
        // A: 권위 PRO
        coEvery { subscriptionRepository.getUserSubscription() } returns flowOf(
            Subscription(tier = SubscriptionTier.PRO, isActive = true, isAuthoritative = true)
        )
        val ucScope = CoroutineScope(testDispatcher)

        useCase = GetSubscriptionUseCase(
            subscriptionRepository, appSettingsRepository, authRepository, logger, ucScope
        )
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(SubscriptionTier.PRO, useCase().value.tier)

        // When: 로그아웃(uid → null)
        userFlow.value = null
        testDispatcher.scheduler.advanceUntilIdle()
        // Then: in-memory 구독이 리셋되어 이전 PRO 가 사라진다
        assertEquals(SubscriptionTier.FREE, useCase().value.tier)

        // When: 새 계정 B 로그인 + 조회 실패(오프라인 비권위 FREE)
        coEvery { subscriptionRepository.getUserSubscription() } returns flowOf(
            Subscription(tier = SubscriptionTier.FREE, isAuthoritative = false)
        )
        userFlow.value = User(id = "uid-B", email = "b@x", displayName = "B")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 이전 계정 PRO 를 상속하지 않고 FREE (fail-closed)
        assertEquals(SubscriptionTier.FREE, useCase().value.tier)

        ucScope.cancel()
    }

    @Test
    fun `같은 사용자 오프라인 조회 실패는 기존 PRO 티어를 유지한다 (H10 보존)`() = runTest {
        // Given: 캐시 PRO 로 시드된 뒤 동일 사용자 유지 중 조회가 비권위 FREE(오프라인)로 실패
        every { appSettingsRepository.subscriptionTierEnum } returns flowOf(SubscriptionTier.PRO)
        every { authRepository.getCurrentUser() } returns flowOf(
            User(id = "uid-A", email = "a@x", displayName = "A")
        )
        coEvery { subscriptionRepository.getUserSubscription() } returns flowOf(
            Subscription(tier = SubscriptionTier.FREE, isAuthoritative = false)
        )

        // When
        useCase = GetSubscriptionUseCase(subscriptionRepository, appSettingsRepository, authRepository, logger, this)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 계정 전환이 아니므로 리셋되지 않고, 비권위 FREE 폴백이 seed PRO 를 덮지 못한다
        assertEquals(SubscriptionTier.PRO, useCase().value.tier)
    }

    // === item 1(b): 결제 검증 신호 → 구독 재조회 ===

    @Test
    fun `결제 검증 신호를 받으면 구독을 재조회한다`() = runTest {
        // Given: 초기 조회는 FREE, 신호 후 조회는 PRO(결제 반영)
        every { appSettingsRepository.subscriptionTierEnum } returns flowOf(SubscriptionTier.FREE)
        val signals = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        every { subscriptionRepository.subscriptionRefreshSignals } returns signals
        coEvery { subscriptionRepository.getUserSubscription() } returns flowOf(
            Subscription(tier = SubscriptionTier.FREE, isAuthoritative = true)
        )
        val ucScope = CoroutineScope(testDispatcher)

        useCase = GetSubscriptionUseCase(
            subscriptionRepository, appSettingsRepository, authRepository, logger, ucScope
        )
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(SubscriptionTier.FREE, useCase().value.tier)

        // When: 결제 검증 성공 신호 방출 + 이후 서버 응답은 PRO
        coEvery { subscriptionRepository.getUserSubscription() } returns flowOf(
            Subscription(tier = SubscriptionTier.PRO, isActive = true, isAuthoritative = true)
        )
        signals.tryEmit(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 신호 관찰이 재조회를 트리거해 PRO 로 수렴
        assertEquals(SubscriptionTier.PRO, useCase().value.tier)

        ucScope.cancel()
    }

    // === item 1: refreshOnForeground 스로틀 ===

    @Test
    fun `refreshOnForeground 는 스로틀 내 반복 호출 시 재조회하지 않는다`() = runTest {
        // Given
        coEvery { subscriptionRepository.getUserSubscription() } returns flowOf(
            Subscription(tier = SubscriptionTier.FREE)
        )
        useCase = GetSubscriptionUseCase(subscriptionRepository, appSettingsRepository, authRepository, logger, this)
        testDispatcher.scheduler.advanceUntilIdle()
        // init 의 refresh 호출 기록만 지우고 stub 은 유지
        clearMocks(subscriptionRepository, answers = false)

        // When: 첫 포그라운드 재조회
        useCase.refreshOnForeground()
        testDispatcher.scheduler.advanceUntilIdle()
        // 즉시 재호출 — 스로틀(15분) 내이므로 스킵되어야 함
        useCase.refreshOnForeground()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 서버 재조회는 정확히 1회
        coVerify(exactly = 1) { subscriptionRepository.getUserSubscription() }
    }
}
