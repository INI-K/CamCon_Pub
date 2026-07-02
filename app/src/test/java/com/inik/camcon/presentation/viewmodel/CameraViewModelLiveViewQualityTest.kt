package com.inik.camcon.presentation.viewmodel

import android.content.Context
import com.inik.camcon.data.repository.fake.FakeCameraRepositoryBasic
import com.inik.camcon.domain.model.LiveViewQuality
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.repository.AppSettingsRepository
import com.inik.camcon.domain.repository.PtpipPreferencesRepository
import com.inik.camcon.domain.usecase.GetSubscriptionUseCase
import com.inik.camcon.presentation.viewmodel.state.CameraSettingsManager
import com.inik.camcon.presentation.viewmodel.state.CameraUiStateManager
import com.inik.camcon.presentation.viewmodel.state.ErrorHandlingManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * CameraViewModel 라이브뷰 화질 실시간 반영(안전 재시작) 단위 테스트.
 *
 * 설계서: `.claude/_workspace/liveview_quality_inline_design.md` §3, §10 (camcon-tdd-tester 위임)
 * 대상: [CameraViewModel.observeLiveViewQuality] / restartLiveViewForQuality / awaitLiveViewStopped (private).
 *
 * 검증 원칙(프로젝트 규약): 구현 세부가 아니라 **행위(협력자 호출 순서/횟수)와 상태**를 검증한다.
 * private 메서드를 직접 호출하지 않고, 관찰 트리거(`appSettingsRepository.liveViewQuality` 방출)와
 * 공개 상태(`uiState`, fake repo의 호출 카운트)만으로 안전 불변식을 방어한다.
 *
 * 안전 불변식(설계 §3.2~§3.6):
 *  - drop(1): 앱 시작 첫 emit(초기값)으로 LV를 죽이지 않는다 → stop/start 0회.
 *  - LV 활성 + 비촬영/비타임랩스: setLiveViewQuality(await) 후 stop→start 순서.
 *  - 촬영/타임랩스 중: push만, 재시작 0회(셔터 오살 방지).
 *  - LV 비활성: push만, 재시작 0회.
 *  - distinctUntilChanged: 동일값 연속 emit은 1회만 처리.
 *  - awaitLiveViewStopped: isLiveViewStopping true→false 전이 시 start 진행, 계속 true면 타임아웃 폴백.
 *  - liveViewRestartMutex: 빠른 연타가 stop/start 인터리브 없이 직렬.
 *
 * 협력자 처리:
 *  - cameraRepository: [FakeCameraRepositoryBasic] — setLiveViewQuality/isLiveViewStopping/isCameraInitializedNow 제어.
 *  - uiStateManager: 실제 [CameraUiStateManager] — uiState.value.isConnected/isCapturing 를 그대로 구동(네이티브 의존 없음).
 *  - operationsManager: relaxed mock — isLiveViewActive/isTimelapseActive 제어 + stop/startLiveView 호출 검증.
 *    stopLiveView 호출 시 liveViewActive 플래그를 false 로 떨궈 실제 동작(job 취소)을 모사한다.
 *  - 나머지 매니저/UseCase: relaxed mock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CameraViewModelLiveViewQualityTest {

    private lateinit var context: Context
    private lateinit var cameraRepository: FakeCameraRepositoryBasic
    private lateinit var getSubscriptionUseCase: GetSubscriptionUseCase
    private lateinit var uiStateManager: CameraUiStateManager
    private lateinit var usbAutoConnectManager: UsbAutoConnectManager
    private lateinit var operationsManager: CameraOperationsManager
    private lateinit var settingsManager: CameraSettingsManager
    private lateinit var errorHandlingManager: ErrorHandlingManager
    private lateinit var handoffTracker: ConnectionHandoffTracker
    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var ptpipPreferencesRepository: PtpipPreferencesRepository
    private lateinit var advancedCaptureManager: CameraAdvancedCaptureManager
    private lateinit var focusManager: CameraFocusManager
    private lateinit var fileManager: CameraFileManager
    private lateinit var streamingManager: CameraStreamingManager
    private lateinit var diagnosticsManager: CameraDiagnosticsManager

    private val testDispatcher = StandardTestDispatcher()

    // 관찰 대상 화질 Flow — SharedFlow(replay=1)로 모델링(중복값도 emit 가능해 distinctUntilChanged 검증 가능).
    private val liveViewQualityFlow = MutableSharedFlow<LiveViewQuality>(
        replay = 1,
        extraBufferCapacity = 8
    )

    // operationsManager.isLiveViewActive() 가 읽는 가변 상태. stopLiveView 호출 시 false 로 전이(실제 job 취소 모사).
    private var liveViewActive = false
    private var timelapseActive = false

    // push/stop/start 가 시간순으로 인터리브 없이 직렬·정렬됐는지 검증하기 위한 공유 호출 로그.
    // (Fake repo 와 MockK operationsManager 를 함께 verifyOrder 할 수 없으므로 수동 로그로 순서를 본다.)
    private val callLog = mutableListOf<String>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        context = mockk(relaxed = true)
        // setLiveViewQuality(push)를 공유 호출 로그에 기록하도록 Fake 를 확장한다.
        cameraRepository = object : FakeCameraRepositoryBasic() {
            override suspend fun setLiveViewQuality(quality: LiveViewQuality): Result<Unit> {
                callLog.add("push")
                return super.setLiveViewQuality(quality)
            }
        }
        getSubscriptionUseCase = mockk(relaxed = true)
        uiStateManager = CameraUiStateManager()
        usbAutoConnectManager = mockk(relaxed = true)
        operationsManager = mockk(relaxed = true)
        settingsManager = mockk(relaxed = true)
        errorHandlingManager = mockk(relaxed = true)
        handoffTracker = mockk(relaxed = true)
        appSettingsRepository = mockk(relaxed = true)
        ptpipPreferencesRepository = mockk(relaxed = true)
        advancedCaptureManager = mockk(relaxed = true)
        focusManager = mockk(relaxed = true)
        fileManager = mockk(relaxed = true)
        streamingManager = mockk(relaxed = true)
        diagnosticsManager = mockk(relaxed = true)

        // init {} 에서 참조하는 Flow 들 — 빈 Flow면 .first()가 영구 대기하므로 명시 stub.
        every { appSettingsRepository.liveViewQuality } returns liveViewQualityFlow
        every { appSettingsRepository.subscriptionTierEnum } returns flowOf(SubscriptionTier.FREE)
        every { appSettingsRepository.isRawFileDownloadEnabled } returns flowOf(true)
        every { appSettingsRepository.isHistogramEnabled } returns flowOf(false)

        // isAutoSearchArmed(init 파생 StateFlow)가 combine 으로 읽는 Flow 2종 — 명시 stub.
        every { ptpipPreferencesRepository.isAutoConnectEnabled } returns flowOf(false)
        every { ptpipPreferencesRepository.lastConnectedName } returns flowOf(null)

        // getSubscriptionUseCase 는 연결 관찰 경로(observeCameraConnection)에서 참조될 수 있음.
        every { getSubscriptionUseCase.getSubscriptionTier() } returns emptyFlow()

        // SharedFlow/StateFlow.collect 의 반환 타입은 Nothing 이라 relaxed mock 이면 KotlinNothingValueException.
        // VM init 에서 실제로 collect 되는 흐름들은 실제(빈) Flow 로 stub 한다.
        every { settingsManager.cameraSettings } returns MutableStateFlow(null)
        every { errorHandlingManager.errorEvent } returns MutableSharedFlow()
        every { errorHandlingManager.nativeErrorEvent } returns MutableSharedFlow()

        // startLiveView()가 인자로 읽는 cameraCapabilities.value — relaxed mock 의 .value 모호성 제거차 실제 StateFlow stub.
        every { settingsManager.cameraCapabilities } returns MutableStateFlow(null)

        // operationsManager 상태 게터 — 가변 플래그를 읽는다.
        every { operationsManager.isLiveViewActive() } answers { liveViewActive }
        every { operationsManager.isTimelapseActive() } answers { timelapseActive }
        // VM.stopLiveView() → operationsManager.stopLiveView(): 실제로는 liveViewJob 취소 → isLiveViewActive=false.
        every { operationsManager.stopLiveView(any()) } answers {
            liveViewActive = false
            callLog.add("stop")
        }
        // VM.startLiveView() → operationsManager.startLiveView(): 실제로는 liveViewJob 재가동 → isLiveViewActive=true.
        // (연속 변경 시 2번째 사이클도 LV 활성으로 인식되어 재시작되도록 모사.)
        every { operationsManager.startLiveView(any(), any(), any()) } answers {
            liveViewActive = true
            callLog.add("start")
        }

        // 기본: 연결됨 + 초기화됨 + stop 즉시 완료.
        cameraRepository.setCameraConnected(true)
        cameraRepository.isCameraInitializedNowResult = Result.success(true)
        cameraRepository.isLiveViewStoppingResult = false
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): CameraViewModel = CameraViewModel(
        context = context,
        cameraRepository = cameraRepository,
        getSubscriptionUseCase = getSubscriptionUseCase,
        uiStateManager = uiStateManager,
        usbAutoConnectManager = usbAutoConnectManager,
        operationsManager = operationsManager,
        settingsManager = settingsManager,
        errorHandlingManager = errorHandlingManager,
        handoffTracker = handoffTracker,
        appSettingsRepository = appSettingsRepository,
        ptpipPreferencesRepository = ptpipPreferencesRepository,
        advancedCaptureManager = advancedCaptureManager,
        focusManager = focusManager,
        fileManager = fileManager,
        streamingManager = streamingManager,
        diagnosticsManager = diagnosticsManager,
        ioDispatcher = testDispatcher as CoroutineDispatcher
    )

    /** uiState.value.isConnected = true 로 만든다(실제 UiStateManager 경유). */
    private fun markConnected() = uiStateManager.updateConnectionState(true)

    /** uiState.value.isCapturing 토글. */
    private fun markCapturing(value: Boolean) = uiStateManager.updateCapturingState(value)

    /**
     * init(loadLiveViewQualityAtStartup)로 인한 startup push 기록을 비워, 이후 테스트 행위(화질 변경)로
     * 인한 push/stop/start만 측정되게 한다.
     *
     * 프로덕션은 init에서 저장된 화질을 1회 native push(setLiveViewQuality)한다(재시작 없음). 이는 의도된
     * 정상 동작이므로 별도 케이스([init에서 저장된 화질이 1회 native push 된다])에서 고정 검증하고,
     * 나머지 케이스는 "행위로 인한" 호출만 보도록 여기서 공유 callLog clear + Fake 호출 카운트를 리셋한다.
     *
     * resetCallCounts()는 isLiveViewStoppingResult/isCameraInitializedNowResult 등 시나리오 제어 필드를
     * 건드리지 않거나 일부만 false로 되돌리므로(설계에 맞게), stopping=true 시나리오는 호출 측에서 리셋 후
     * 재설정한다.
     */
    private fun resetPushTrackingAfterStartup() {
        callLog.clear()
        cameraRepository.resetCallCounts()
    }

    // ─────────────────────────────────────────────────────────────
    // 0. startup push: init(loadLiveViewQualityAtStartup)가 저장값을 1회 push (영속성 회귀 방어)
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `init에서 저장된 화질이 1회 native push 된다 - startup push는 재시작을 유발하지 않는다`() = runTest {
        // LV 활성 + 연결됨이라도 startup push 자체는 재시작 경로(observeLiveViewQuality)가 아니라
        // loadLiveViewQualityAtStartup(push만)에서 일어나므로 stop/start 0회여야 한다.
        liveViewActive = true
        markConnected()
        markCapturing(false)

        // DataStore 저장값을 기본(BALANCED)이 아닌 QUALITY 로 두어 '저장값 그대로' push 됨을 검증.
        // replay=1 이라 loadLiveViewQualityAtStartup 의 first()가 이 값을 읽는다.
        liveViewQualityFlow.tryEmit(LiveViewQuality.QUALITY)

        createViewModel()
        advanceUntilIdle()

        // init 에서 저장값(QUALITY)으로 정확히 1회 push (리셋하지 않고 startup push 자체를 측정).
        assertEquals(1, cameraRepository.setLiveViewQualityCallCount)
        assertEquals(LiveViewQuality.QUALITY, cameraRepository.lastSetLiveViewQuality)

        // startup push 는 push만 — observeLiveViewQuality 의 drop(1)이 초기 QUALITY emit 도 스킵하므로
        // 추가 push/재시작 없음(LV 활성·연결됨에도 stop/start 0회).
        verify(exactly = 0) { operationsManager.stopLiveView(any()) }
        verify(exactly = 0) { operationsManager.startLiveView(any(), any(), any()) }
        assertEquals(listOf("push"), callLog)
    }

    // ─────────────────────────────────────────────────────────────
    // 1. drop(1): 앱 시작 초기 emit → stop/start 0회
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `앱 시작 초기 emit은 drop(1)로 스킵 - LV 활성이어도 stop start 0회`() = runTest {
        liveViewActive = true
        markConnected()

        // 초기값 emit(구독 시 replay) — observeLiveViewQuality의 drop(1) 대상.
        liveViewQualityFlow.tryEmit(LiveViewQuality.BALANCED)

        createViewModel()
        advanceUntilIdle()
        // init의 startup push(저장값 1회 push)는 별도 케이스에서 검증하므로, drop(1) 검증을 위해 리셋.
        resetPushTrackingAfterStartup()
        advanceUntilIdle()

        // 초기 emit은 observeLiveViewQuality의 drop(1)로 무시되므로 행위로 인한 push/재시작 모두 0회.
        // (startup push는 init에서 이미 일어났고 위 리셋으로 제거됨 — 여기서는 drop(1)이 재시작을
        //  유발하지 않음만 본다.)
        assertEquals(0, cameraRepository.setLiveViewQualityCallCount)
        verify(exactly = 0) { operationsManager.stopLiveView(any()) }
        verify(exactly = 0) { operationsManager.startLiveView(any(), any(), any()) }
    }

    @Test
    fun `초기 emit 뒤 첫 실제 변경부터 push 시작`() = runTest {
        liveViewActive = false // 비활성이라 재시작 없음 — push만 검증
        liveViewQualityFlow.tryEmit(LiveViewQuality.BALANCED) // 초기값(drop)
        createViewModel()
        advanceUntilIdle()
        resetPushTrackingAfterStartup() // startup push 제거 — 행위로 인한 push만 측정

        liveViewQualityFlow.emit(LiveViewQuality.QUALITY) // 첫 실제 변경
        advanceUntilIdle()

        assertEquals(1, cameraRepository.setLiveViewQualityCallCount)
        assertEquals(LiveViewQuality.QUALITY, cameraRepository.lastSetLiveViewQuality)
    }

    // ─────────────────────────────────────────────────────────────
    // 2. LV 활성 + 비촬영: setLiveViewQuality await 후 stop→start 순서
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `LV 활성 비촬영 중 화질 변경 - push await 후 stop then start 순서`() = runTest {
        liveViewActive = true
        markConnected()
        markCapturing(false)
        liveViewQualityFlow.tryEmit(LiveViewQuality.BALANCED) // 초기값(drop)
        createViewModel()
        advanceUntilIdle()
        resetPushTrackingAfterStartup() // startup push 제거 — 행위로 인한 push/stop/start만 측정

        liveViewQualityFlow.emit(LiveViewQuality.QUALITY)
        advanceUntilIdle()

        // push 1회 + 재시작(stop→start) 발생.
        assertEquals(1, cameraRepository.setLiveViewQualityCallCount)
        assertEquals(LiveViewQuality.QUALITY, cameraRepository.lastSetLiveViewQuality)
        verify(exactly = 1) { operationsManager.stopLiveView(any()) }
        verify(exactly = 1) { operationsManager.startLiveView(any(), any(), any()) }

        // 순서 불변식(§3.2): push(setLiveViewQuality await) → stop → start.
        // Fake repo + MockK 혼합 verifyOrder 불가 → 공유 호출 로그로 검증.
        assertEquals(listOf("push", "stop", "start"), callLog)
    }

    // ─────────────────────────────────────────────────────────────
    // 3. 촬영/타임랩스 중: push만, 재시작 0회 (셔터 오살 방지)
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `촬영 중 화질 변경 - push만 하고 재시작 0회`() = runTest {
        liveViewActive = true
        markConnected()
        markCapturing(true) // 촬영 중
        liveViewQualityFlow.tryEmit(LiveViewQuality.BALANCED)
        createViewModel()
        advanceUntilIdle()
        resetPushTrackingAfterStartup() // startup push 제거 — 행위로 인한 push만 측정

        liveViewQualityFlow.emit(LiveViewQuality.QUALITY)
        advanceUntilIdle()

        assertEquals(1, cameraRepository.setLiveViewQualityCallCount) // push는 됨
        verify(exactly = 0) { operationsManager.stopLiveView(any()) }
        verify(exactly = 0) { operationsManager.startLiveView(any(), any(), any()) }
    }

    @Test
    fun `타임랩스 중 화질 변경 - push만 하고 재시작 0회`() = runTest {
        liveViewActive = true
        timelapseActive = true // 타임랩스 활성
        markConnected()
        markCapturing(false)
        liveViewQualityFlow.tryEmit(LiveViewQuality.BALANCED)
        createViewModel()
        advanceUntilIdle()
        resetPushTrackingAfterStartup() // startup push 제거 — 행위로 인한 push만 측정

        liveViewQualityFlow.emit(LiveViewQuality.QUALITY)
        advanceUntilIdle()

        assertEquals(1, cameraRepository.setLiveViewQualityCallCount)
        verify(exactly = 0) { operationsManager.stopLiveView(any()) }
        verify(exactly = 0) { operationsManager.startLiveView(any(), any(), any()) }
    }

    // ─────────────────────────────────────────────────────────────
    // 4. LV 비활성: push만, 재시작 0회
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `LV 비활성 중 화질 변경 - push만 하고 재시작 0회`() = runTest {
        liveViewActive = false // 라이브뷰 꺼짐
        markConnected()
        liveViewQualityFlow.tryEmit(LiveViewQuality.BALANCED)
        createViewModel()
        advanceUntilIdle()
        resetPushTrackingAfterStartup() // startup push 제거 — 행위로 인한 push만 측정

        liveViewQualityFlow.emit(LiveViewQuality.SPEED)
        advanceUntilIdle()

        assertEquals(1, cameraRepository.setLiveViewQualityCallCount)
        verify(exactly = 0) { operationsManager.stopLiveView(any()) }
        verify(exactly = 0) { operationsManager.startLiveView(any(), any(), any()) }
    }

    // ─────────────────────────────────────────────────────────────
    // 5. distinctUntilChanged: 동일값 연속 emit → 1회만 처리
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `동일값 연속 emit은 distinctUntilChanged로 1회만 push`() = runTest {
        liveViewActive = false // 재시작 영향 배제, push 횟수로 distinct 검증
        markConnected()
        liveViewQualityFlow.tryEmit(LiveViewQuality.BALANCED) // 초기(drop)
        createViewModel()
        advanceUntilIdle()
        resetPushTrackingAfterStartup() // startup push 제거 — 행위로 인한 push만 측정

        // 동일값 QUALITY 를 3회 연속 emit.
        liveViewQualityFlow.emit(LiveViewQuality.QUALITY)
        liveViewQualityFlow.emit(LiveViewQuality.QUALITY)
        liveViewQualityFlow.emit(LiveViewQuality.QUALITY)
        advanceUntilIdle()

        // distinctUntilChanged 가 중복을 접어 push 1회.
        assertEquals(1, cameraRepository.setLiveViewQualityCallCount)
    }

    @Test
    fun `초기값과 동일한 첫 변경은 distinct로 무시되어 push 0회`() = runTest {
        liveViewActive = true
        markConnected()
        markCapturing(false)
        liveViewQualityFlow.tryEmit(LiveViewQuality.BALANCED) // 초기(drop)
        createViewModel()
        advanceUntilIdle()
        resetPushTrackingAfterStartup() // startup push 제거 — 행위로 인한 push만 측정

        // 초기값과 동일한 BALANCED 재방출 → distinctUntilChanged 가 drop 전에 이미 접음 → onEach 미진입.
        liveViewQualityFlow.emit(LiveViewQuality.BALANCED)
        advanceUntilIdle()

        assertEquals(0, cameraRepository.setLiveViewQualityCallCount)
        verify(exactly = 0) { operationsManager.stopLiveView(any()) }
        verify(exactly = 0) { operationsManager.startLiveView(any(), any(), any()) }
    }

    // ─────────────────────────────────────────────────────────────
    // 6. awaitLiveViewStopped 폴링: stopping true→false 전이 시 start 진행
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `stop 완료 폴링 - stopping true 동안 대기 후 false 전이 시 start 진행`() = runTest {
        liveViewActive = true
        markConnected()
        markCapturing(false)
        cameraRepository.isLiveViewStoppingResult = true // stop 미완료 상태로 시작
        liveViewQualityFlow.tryEmit(LiveViewQuality.BALANCED)
        createViewModel()
        advanceUntilIdle()
        resetPushTrackingAfterStartup() // startup push 제거
        cameraRepository.isLiveViewStoppingResult = true // 리셋이 false로 되돌리므로 시나리오 재설정

        // 변경 트리거 — onEach(viewModelScope)가 push→stop 후 50ms 폴링 루프로 진입.
        liveViewQualityFlow.emit(LiveViewQuality.QUALITY)

        // 50ms 폴링 여러 주기 진행 — 아직 stopping=true 라 루프 탈출 안 함 → start 미진행.
        advanceTimeBy(500)
        verify(exactly = 1) { operationsManager.stopLiveView(any()) }
        verify(exactly = 0) { operationsManager.startLiveView(any(), any(), any()) }

        // stop 완료 신호 → 다음 폴링 주기에서 false 감지 → 루프 탈출 → start.
        cameraRepository.isLiveViewStoppingResult = false
        advanceUntilIdle()

        verify(exactly = 1) { operationsManager.stopLiveView(any()) }
        verify(exactly = 1) { operationsManager.startLiveView(any(), any(), any()) }
    }

    @Test
    fun `stop 완료 폴링 - stopping이 계속 true면 타임아웃 후 start 폴백`() = runTest {
        liveViewActive = true
        markConnected()
        markCapturing(false)
        cameraRepository.isLiveViewStoppingResult = true // 영영 false 안 됨
        liveViewQualityFlow.tryEmit(LiveViewQuality.BALANCED)
        createViewModel()
        advanceUntilIdle()
        resetPushTrackingAfterStartup() // startup push 제거
        cameraRepository.isLiveViewStoppingResult = true // 리셋이 false로 되돌리므로 시나리오 재설정

        liveViewQualityFlow.emit(LiveViewQuality.QUALITY)

        // 타임아웃(3000ms) 직전까지는 start 미진행.
        advanceTimeBy(2900)
        verify(exactly = 0) { operationsManager.startLiveView(any(), any(), any()) }

        // 타임아웃 경과 후 폴백으로 start 진행.
        advanceTimeBy(200)
        advanceUntilIdle()

        verify(exactly = 1) { operationsManager.stopLiveView(any()) }
        verify(exactly = 1) { operationsManager.startLiveView(any(), any(), any()) }
    }

    // ─────────────────────────────────────────────────────────────
    // 7. stop 대기 중 끊김/미초기화 시 start 스킵
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `재시작 중 미초기화면 start 스킵 (stop만)`() = runTest {
        liveViewActive = true
        markConnected()
        markCapturing(false)
        cameraRepository.isCameraInitializedNowResult = Result.success(false) // 미초기화
        liveViewQualityFlow.tryEmit(LiveViewQuality.BALANCED)
        createViewModel()
        advanceUntilIdle()
        resetPushTrackingAfterStartup() // startup push 제거 (isCameraInitializedNowResult는 리셋이 보존)

        liveViewQualityFlow.emit(LiveViewQuality.QUALITY)
        advanceUntilIdle()

        verify(exactly = 1) { operationsManager.stopLiveView(any()) }
        verify(exactly = 0) { operationsManager.startLiveView(any(), any(), any()) }
    }

    @Test
    fun `재시작 중 연결 끊기면 start 스킵 (stop만)`() = runTest {
        liveViewActive = true
        // 연결 끊김 시뮬레이션: repo 연결 Flow=false → observeCameraConnection 이 uiState.isConnected=false 로 수렴.
        // (markConnected 호출 안 함. 초기화는 true 로 두어 'connected=false 때문에 스킵'을 분리 검증.)
        cameraRepository.setCameraConnected(false)
        markCapturing(false)
        cameraRepository.isCameraInitializedNowResult = Result.success(true)
        liveViewQualityFlow.tryEmit(LiveViewQuality.BALANCED)
        createViewModel()
        advanceUntilIdle()
        resetPushTrackingAfterStartup() // startup push 제거 (isCameraInitializedNowResult는 리셋이 보존)

        liveViewQualityFlow.emit(LiveViewQuality.QUALITY)
        advanceUntilIdle()

        verify(exactly = 1) { operationsManager.stopLiveView(any()) }
        verify(exactly = 0) { operationsManager.startLiveView(any(), any(), any()) }
    }

    // ─────────────────────────────────────────────────────────────
    // 8. 연타 직렬화(liveViewRestartMutex): 빠른 2회 변경이 인터리브 없이 직렬
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `빠른 연속 2회 변경 - stop start 인터리브 없이 직렬 처리`() = runTest {
        liveViewActive = true
        markConnected()
        markCapturing(false)
        liveViewQualityFlow.tryEmit(LiveViewQuality.BALANCED)
        createViewModel()
        advanceUntilIdle()
        resetPushTrackingAfterStartup() // startup push 제거 — callLog가 행위로 인한 시퀀스만 담도록

        // 빠르게 2회 변경 — onEach 직렬 + Mutex 로 각 변경의 stop→start 가 원자적으로 직렬.
        liveViewQualityFlow.emit(LiveViewQuality.SPEED)
        liveViewQualityFlow.emit(LiveViewQuality.QUALITY)
        advanceUntilIdle()

        // 두 변경 모두 처리: push 2회, 재시작(stop/start) 각 2회.
        assertEquals(2, cameraRepository.setLiveViewQualityCallCount)
        verify(exactly = 2) { operationsManager.stopLiveView(any()) }
        verify(exactly = 2) { operationsManager.startLiveView(any(), any(), any()) }

        // 인터리브 없음(Mutex 직렬화): 각 사이클이 push→stop→start 로 끝난 뒤 다음 사이클이 시작.
        // start 직후가 다시 push 여야 하며, stop 이 연달아 두 번 나오거나(인터리브) 하면 안 된다.
        assertEquals(
            listOf("push", "stop", "start", "push", "stop", "start"),
            callLog
        )
    }
}
