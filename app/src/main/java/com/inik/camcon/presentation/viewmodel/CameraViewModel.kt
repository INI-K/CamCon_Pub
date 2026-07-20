package com.inik.camcon.presentation.viewmodel

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.presentation.viewmodel.state.CameraSettingsManager
import com.inik.camcon.presentation.viewmodel.state.ErrorHandlingManager
import com.inik.camcon.domain.model.Camera
import com.inik.camcon.domain.model.LiveViewFrame
import com.inik.camcon.domain.model.UiText
import com.inik.camcon.domain.model.ShootingMode
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.repository.AppSettingsRepository
import com.inik.camcon.domain.repository.CameraRepository
import com.inik.camcon.domain.repository.PtpipPreferencesRepository
import com.inik.camcon.domain.repository.UsbDeviceRepository
import com.inik.camcon.domain.usecase.GetSubscriptionUseCase
import com.inik.camcon.presentation.viewmodel.state.CameraUiStateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import com.inik.camcon.di.IoDispatcher
import javax.inject.Inject

/**
 * 카메라 기능을 위한 ViewModel - MVVM 패턴 준수
 * 단일책임: UI 상태 관리 및 매니저들 간의 조정만 담당
 * View Layer와 Domain Layer 사이의 중재자 역할
 */
@HiltViewModel
class CameraViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cameraRepository: CameraRepository,
    private val usbDeviceRepository: UsbDeviceRepository,
    private val getSubscriptionUseCase: GetSubscriptionUseCase,
    private val uiStateManager: CameraUiStateManager,

    // 매니저 의존성 주입 (단일책임원칙 적용)
    private val usbAutoConnectManager: UsbAutoConnectManager,
    private val operationsManager: CameraOperationsManager,
    private val settingsManager: CameraSettingsManager,
    private val errorHandlingManager: ErrorHandlingManager,
    private val handoffTracker: ConnectionHandoffTracker,
    private val appSettingsRepository: AppSettingsRepository,
    private val ptpipPreferencesRepository: PtpipPreferencesRepository,

    // 신규 매니저 의존성 주입
    private val advancedCaptureManager: CameraAdvancedCaptureManager,
    private val focusManager: CameraFocusManager,
    private val fileManager: CameraFileManager,
    private val streamingManager: CameraStreamingManager,
    private val diagnosticsManager: CameraDiagnosticsManager,

    // Hilt 로 주입되는 IO 디스패처 — 테스트 시 교체 가능하도록 하드코딩 회피.
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    companion object {
        private const val TAG = "카메라뷰모델"

        // 화질 변경 후 라이브뷰 재시작 시 native stop 완료(isLiveViewStopping=false)를 폴링하는 간격/상한.
        // 고정 delay가 아니라 실제 stop 완료를 기다리되, 응답 없을 때 영구 대기하지 않도록 타임아웃 폴백.
        private const val LIVEVIEW_STOP_POLL_INTERVAL_MS = 50L
        private const val LIVEVIEW_STOP_POLL_TIMEOUT_MS = 3000L

        // 자동 재연결(CONNECTED 전이) 직후 이벤트 리스너/세션 안정화를 기다리는 지연.
        // 별도 플러시 완료 신호가 없어 소폭 지연으로 근사한다(스펙: 1~2s 허용).
        private const val LIVEVIEW_RESUME_DELAY_MS = 1500L
    }

    // 화질 변경에 따른 라이브뷰 재시작을 직렬화한다(연타·중복 재시작 차단).
    // stop→폴링→start 2-단계가 원자적이어야 하므로 onEach(직렬) + 이 Mutex로 보호한다.
    private val liveViewRestartMutex = Mutex()

    // UI 상태는 StateManager에 위임
    val uiState: StateFlow<CameraUiState> = uiStateManager.uiState

    /** 라이브뷰 프레임 — 초당 수십 회 업데이트되므로 uiState와 분리 */
    val liveViewFrame: StateFlow<LiveViewFrame?> = uiStateManager.liveViewFrame

    /** 1-shot 정보 메시지(예: AF 성공) — 에러 채널과 분리. UI가 snackbar로 소비한다. */
    val infoMessage: kotlinx.coroutines.flow.SharedFlow<com.inik.camcon.presentation.viewmodel.state.InfoMessage> =
        uiStateManager.infoMessage

    // ✅ 라이브뷰 Bitmap 디코딩 (IO 디스패처에서 처리) — CRITICAL-1 해결
    private val _decodedLiveViewBitmap = MutableStateFlow<android.graphics.Bitmap?>(null)
    val decodedLiveViewBitmap: StateFlow<android.graphics.Bitmap?> = _decodedLiveViewBitmap.asStateFlow()

    // F25: 라이브뷰 Bitmap 생명주기를 ViewModel 단일 소유로 관리한다.
    // 새 프레임을 대입할 때 '직전' 프레임을 즉시 recycle하면 RenderThread 드로잉 중
    // use-after-recycle 가 발생하므로, 한 세대 지연 회수한다(직전이 아니라 그 이전 pending 을 회수).
    private var pendingRecycleBitmap: android.graphics.Bitmap? = null

    // 라이브뷰 히스토그램 데이터 — 토글 OFF 시 null. IO 디스패처에서 계산.
    private val _histogramData =
        MutableStateFlow<com.inik.camcon.presentation.util.HistogramData?>(null)
    val histogramData: StateFlow<com.inik.camcon.presentation.util.HistogramData?> =
        _histogramData.asStateFlow()

    // 카메라 피드 (Repository에서 직접 가져오기 - 단순 위임 UseCase 제거)
    val cameraFeed: StateFlow<List<Camera>> = cameraRepository.getCameraFeed()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // 카메라 설정 상태 (SettingsManager에서 관리)
    val cameraSettings = settingsManager.cameraSettings
    val cameraCapabilities = settingsManager.cameraCapabilities
    val isLoadingSettings = settingsManager.isLoadingSettings
    val isUpdatingSettings = settingsManager.isUpdatingSettings

    // 노출 보정(EV) 현재값/선택지 — SettingsManager에서 관리
    val exposureCompensation = settingsManager.exposureCompensation

    // 카메라 스토리지 정보 — SettingsManager에서 관리
    val cameraStorageInfo = settingsManager.storageInfo

    // 연결 상태 (ConnectionManager에서 관리)
    val isAutoConnecting = usbAutoConnectManager.isAutoConnecting

    // PTPIP 연결 상태 (사진 미리보기 차단용)
    val isPtpipConnected: StateFlow<Boolean> = cameraRepository.isPtpipConnected()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    /**
     * 백그라운드 자동 검색이 armed 상태인지(= WifiMonitoringService 폴링이 재연결을 시도할 조건).
     * UI가 관찰 가능한 신호만으로 근사한다: auto_connect ON && 직전 카메라 존재.
     * 실제 연결 여부(!isConnected/!isConnecting)는 UI(CameraControlScreen)에서 uiState와 조합해
     * "검색 중" 표시를 최종 판정한다(hotspot 활성 여부는 suspend 라 UI 근사에서 생략 — 스펙 허용).
     */
    val isAutoSearchArmed: StateFlow<Boolean> = combine(
        ptpipPreferencesRepository.isAutoConnectEnabled,
        ptpipPreferencesRepository.lastConnectedName
    ) { autoConnect, lastName ->
        autoConnect && !lastName.isNullOrBlank()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    init {
        initializeViewModel()
        loadSubscriptionTierAtStartup()
        loadLiveViewQualityAtStartup()
        observeRawDownloadSetting()
        observeLiveViewQuality()
    }

    /**
     * ViewModel 초기화 - 매니저들 설정
     */
    private fun initializeViewModel() {
        // 에러 처리 시스템 초기화
        errorHandlingManager.initialize()

        // 옵저버들 설정
        setupObservers()

        // 카메라 리포지토리 초기화
        initializeCameraRepository()

        // RAW 파일 제한 콜백 등록
        registerRawLimitCallback()

        Log.d(TAG, "ViewModel 초기화 완료")
    }

    /**
     * 앱 시작 시 구독 티어 미리 로드
     */
    private fun loadSubscriptionTierAtStartup() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "🔑 앱 시작 시 구독 티어 로드 시작")

                // 로컬 캐시된 구독 티어를 먼저 적용 (Firebase 오프라인 대비)
                val cachedTier = appSettingsRepository.subscriptionTierEnum.first()

                // C-3 수정: Repository를 통한 간접 호출
                cameraRepository.setSubscriptionTier(cachedTier)
                    .onSuccess {
                        Log.d(TAG, "✅ 앱 시작 시 구독 티어 설정 완료: $cachedTier")
                    }
                    .onFailure { e ->
                        Log.e(TAG, "❌ 구독 티어 설정 실패", e)
                    }

                // RAW 파일 다운로드 설정도 함께 로드
                val isRawDownloadEnabled = appSettingsRepository.isRawFileDownloadEnabled.first()
                cameraRepository.setRawFileDownloadEnabled(isRawDownloadEnabled)
                    .onSuccess {
                        Log.d(TAG, "✅ 앱 시작 시 RAW 파일 다운로드 설정 완료: $isRawDownloadEnabled")
                    }
                    .onFailure { e ->
                        Log.e(TAG, "❌ RAW 파일 다운로드 설정 실패", e)
                    }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "❌ 앱 시작 시 구독 티어 로드 실패", e)
                // 실패 시 기본값(FREE) 설정
                cameraRepository.setSubscriptionTier(SubscriptionTier.FREE)
                cameraRepository.setRawFileDownloadEnabled(true)
            }
        }
    }

    /**
     * 앱 시작 시 저장된 라이브뷰 화질을 1회 네이티브에 push.
     *
     * tier/raw 로더와 동일 패턴: DataStore 저장값을 first()로 읽어 setLiveViewQuality(push)만 한다(재시작 없음).
     * observeLiveViewQuality()는 drop(1)로 초기 emit을 스킵하므로 초기 push를 하지 않는다 —
     * 이 로더가 없으면 native g_liveViewQuality가 기본값(BALANCED)으로 남아 첫 라이브뷰가 저장값으로
     * 시작되지 않는다. drop(1)이 동일 초기값의 중복 push를 막으므로 이 push와 겹치지 않는다.
     */
    private fun loadLiveViewQualityAtStartup() {
        viewModelScope.launch {
            try {
                val quality = appSettingsRepository.liveViewQuality.first()
                cameraRepository.setLiveViewQuality(quality)
                    .onSuccess {
                        Log.d(TAG, "✅ 앱 시작 시 라이브뷰 화질 설정 완료: $quality")
                    }
                    .onFailure { e ->
                        Log.e(TAG, "❌ 앱 시작 시 라이브뷰 화질 설정 실패", e)
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "❌ 앱 시작 시 라이브뷰 화질 로드 실패", e)
            }
        }
    }

    private fun subscriptionTierToInt(tier: SubscriptionTier): Int = when (tier) {
        SubscriptionTier.FREE -> 0
        SubscriptionTier.BASIC -> 1
        SubscriptionTier.PRO -> 2
        SubscriptionTier.REFERRER -> 2
        SubscriptionTier.ADMIN -> 2
    }

    /**
     * RAW 파일 다운로드 설정 관찰
     */
    private fun observeRawDownloadSetting() {
        appSettingsRepository.isRawFileDownloadEnabled
            .onEach { enabled ->
                // C-3 수정: Repository를 통한 간접 호출.
                // onEach 본문이 이미 suspend 컨텍스트이므로 내부 launch 불필요 —
                // 별도 launch 는 토글 연타 시 순서 보장이 깨지고 코루틴만 누적시킨다.
                try {
                    cameraRepository.setRawFileDownloadEnabled(enabled)
                        .onSuccess {
                            Log.d(TAG, "🎯 RAW 파일 다운로드 설정 업데이트: $enabled")
                        }
                        .onFailure { e ->
                            Log.e(TAG, "❌ RAW 파일 다운로드 설정 업데이트 실패", e)
                        }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "RAW 파일 설정 업데이트 중 예외 발생", e)
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * 라이브뷰 화질 설정 관찰 — 변경 시점에 네이티브 push + (라이브뷰 활성 중이면) 안전 재시작.
     *
     * - distinctUntilChanged: 동일값 재방출 무시(연타·중복 collect 방어).
     * - drop(1): 앱 시작 시 DataStore 초기값 emit으로 라이브뷰를 죽이지 않도록 첫 emit 스킵.
     * - onEach(collectLatest 아님): stop→폴링→start 가 원자적이어야 하므로 직렬 처리.
     *   중간 취소되면 LV가 stop된 채 start 전에 끊겨 영구 off될 수 있다.
     * - 순서 불변식: (a) setLiveViewQuality(suspend) await로 g_liveViewQuality.store 완료 보장 후
     *   (b) 재시작 → 재시작의 enableLiveView가 새 값을 읽는다(observeRawDownloadSetting과 동일 근거).
     */
    private fun observeLiveViewQuality() {
        appSettingsRepository.liveViewQuality
            .distinctUntilChanged()
            .drop(1)
            .onEach { quality ->
                try {
                    // (a) native push — suspend, 완료까지 await (순서 불변식)
                    cameraRepository.setLiveViewQuality(quality)
                        .onSuccess {
                            Log.d(TAG, "🎯 라이브뷰 화질 설정 업데이트: $quality")
                        }
                        .onFailure { e ->
                            Log.e(TAG, "❌ 라이브뷰 화질 설정 업데이트 실패", e)
                        }

                    // (b) 라이브뷰 활성 && 비촬영/비타임랩스일 때만 재시작해 즉시 반영.
                    //     비활성이면 push만(다음 startLiveView 진입 시 적용),
                    //     촬영/타임랩스 중이면 재시작 스킵(셔터 오살 방지, push만).
                    if (operationsManager.isLiveViewActive()) {
                        if (uiState.value.isCapturing || operationsManager.isTimelapseActive()) {
                            Log.d(TAG, "촬영/타임랩스 중 — 화질 재시작 스킵(push만, 다음 LV 시작 때 반영)")
                        } else {
                            Log.d(TAG, "라이브뷰 활성 중 — 화질 적용 위해 안전 재시작")
                            restartLiveViewForQuality()
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "라이브뷰 화질 설정 업데이트 중 예외 발생", e)
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * 화질 변경 반영용 라이브뷰 안전 재시작.
     * 기존 stop/start 경로(OperationsManager/UseCase)만 재사용한다(신규 LV 제어 경로 금지).
     * stop은 native에서 viewfinder=0 + 콜백 해제를 커맨드 큐에 비동기 submit하므로,
     * isLiveViewStopping()이 false가 될 때까지 폴링해 stop 완료를 보장한 뒤 start해야
     * stop의 viewfinder=0 큐 커맨드와 새 start의 enableLiveView 경합을 피할 수 있다.
     */
    private suspend fun restartLiveViewForQuality() {
        liveViewRestartMutex.withLock {
            // Mutex 대기 중 LV가 꺼졌거나 촬영/타임랩스로 전환됐으면 재시작하지 않는다.
            if (!operationsManager.isLiveViewActive()) return
            if (uiState.value.isCapturing || operationsManager.isTimelapseActive()) return

            stopLiveView()
            awaitLiveViewStopped()

            // stop 대기 중 끊김/미초기화 됐으면 재시작하지 않는다([[camcon-liveview-reconnect-silent-fail]]).
            val initialized = cameraRepository.isCameraInitializedNow().getOrNull() ?: false
            if (uiState.value.isConnected && initialized) {
                startLiveView()
            } else {
                Log.w(TAG, "화질 재시작 중단 — 연결 끊김 또는 미초기화 (connected=${uiState.value.isConnected}, init=$initialized)")
            }
        }
    }

    /**
     * native stop 완료(isLiveViewStopping=false)까지 폴링. 응답 없으면 타임아웃 폴백(영구 대기 금지).
     * 고정 delay가 아니라 실제 완료 신호를 본다.
     */
    private suspend fun awaitLiveViewStopped() {
        var waited = 0L
        while (cameraRepository.isLiveViewStopping() && waited < LIVEVIEW_STOP_POLL_TIMEOUT_MS) {
            delay(LIVEVIEW_STOP_POLL_INTERVAL_MS)
            waited += LIVEVIEW_STOP_POLL_INTERVAL_MS
        }
        if (waited >= LIVEVIEW_STOP_POLL_TIMEOUT_MS) {
            Log.w(TAG, "라이브뷰 stop 완료 폴링 타임아웃(${LIVEVIEW_STOP_POLL_TIMEOUT_MS}ms) — 그대로 재시작 진행")
        }
    }

    /**
     * 옵저버들 설정 - 각 매니저의 상태를 UI에 반영
     */
    private fun setupObservers() {
        // 카메라 연결 상태 관찰
        observeCameraConnection()

        // 촬영된 사진 관찰
        observeCapturedPhotos()

        // 다운로드/처리 진행 카운트 관찰 (요구 E5)
        observeTransferQueue()

        // USB 디바이스 상태 관찰 (UsbAutoConnectManager에 위임)
        usbAutoConnectManager.observeUsbDevices(viewModelScope, uiStateManager)

        // 에러 이벤트 관찰
        observeErrorEvents()

        // 기타 상태들 관찰
        observeOtherStates()

        // PTPIP 연결 상태 관찰
        observePtpipConnection()

        // USB 네이티브 연결 상태 관찰 (observePtpipConnection 대칭 — 死writer 배선)
        observeNativeCameraConnection()

        // ① 라이브뷰 중 카메라 설정(노출 스트립) 주기 갱신
        observeLiveSettingsPolling()
    }

    /**
     * PTPIP 연결 상태 관찰.
     * 최신 연결 상태만 의미를 가지므로 collectLatest 로 이전 emission 처리를 취소.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observePtpipConnection() {
        viewModelScope.launch {
            var wasConnected = isPtpipConnected.value
            isPtpipConnected.collectLatest { isConnected ->
                // 끊김(true→false) 시 라이브뷰 활성 여부를 uiState 정리 전에 판정해 재개 예약을 걸어야 하므로
                // updatePtpipConnectionState 를 먼저 호출한다(내부에서 예약 set).
                uiStateManager.updatePtpipConnectionState(isConnected)

                // false→true(재연결) 전이에서만 라이브뷰 자동 재개를 시도한다.
                if (isConnected && !wasConnected) {
                    resumeLiveViewAfterReconnectIfNeeded()
                }
                wasConnected = isConnected
            }
        }
    }

    /**
     * USB 네이티브 연결 상태 관찰. observePtpipConnection 대칭.
     * usbDeviceRepository.isNativeCameraConnected(UsbCameraManager 백업)는 USB 전용 진실이라
     * PTP/IP에서는 false를 유지하므로 isNativeCameraConnected 오염이 없다.
     */
    private fun observeNativeCameraConnection() {
        viewModelScope.launch {
            usbDeviceRepository.isNativeCameraConnected.collect { isConnected ->
                uiStateManager.updateNativeCameraConnection(isConnected)
            }
        }
    }

    /**
     * 비자발적 끊김 시점에 라이브뷰가 활성이었다면 재연결 성공 후 자동 재개한다.
     * 기존 startLiveView 경로(isCameraInitialized 선검사·첫프레임 워치독)를 그대로 재사용하며,
     * 신규 LV 제어 경로를 만들지 않는다([[camcon-liveview-reconnect-silent-fail]]).
     *
     * 회귀 가드:
     *  (a) 수동으로 LV를 끈 경우엔 끊김 시점 isLiveViewActive=false 라 예약 자체가 없다(예약은 끊김 전이에서만 set).
     *  (b) 촬영/타임랩스 중이면 재개를 보류(예약은 유지 — 촬영 종료 후 다음 재연결에서 재개될 수 있다).
     *  (c) 이벤트 리스너 플러시를 기다리는 별도 신호가 없으므로 CONNECTED 전이 후 소폭 지연을 둔다.
     */
    private fun resumeLiveViewAfterReconnectIfNeeded() {
        viewModelScope.launch {
            if (!uiStateManager.consumeResumeLiveViewAfterReconnect()) return@launch

            // 촬영/타임랩스 중이면 세션 방해를 피해 재개 보류.
            if (uiState.value.isCapturing || operationsManager.isTimelapseActive()) {
                Log.d(TAG, "촬영/타임랩스 중 — 라이브뷰 자동 재개 보류")
                return@launch
            }

            // 재연결 직후 이벤트 리스너/세션 안정화를 위한 소폭 지연.
            delay(LIVEVIEW_RESUME_DELAY_MS)

            // 지연 사이 상태가 바뀔 수 있으므로 재확인: 여전히 연결됐고 초기화됐고 LV가 꺼져 있을 때만 시작.
            val initialized = cameraRepository.isCameraInitializedNow().getOrNull() ?: false
            if (uiState.value.isConnected && initialized && !operationsManager.isLiveViewActive()) {
                Log.d(TAG, "재연결 후 라이브뷰 자동 재개")
                startLiveView()
            } else {
                Log.w(
                    TAG,
                    "라이브뷰 자동 재개 스킵 (connected=${uiState.value.isConnected}, init=$initialized, active=${operationsManager.isLiveViewActive()})"
                )
            }
        }
    }

    /**
     * 라이브뷰 동안 노출 스트립을 실시간화한다. 라이브뷰일 때만 가동(collectLatest 가 LV off 시 전부 취소):
     * (1) 진입 시 1회 seed, (2) 본체 설정 변경 푸시 구동 재조회(debounce 150ms — DevicePropChanged),
     * (3) 이벤트 미발생(예: Nikon Z8 auto-ISO 펌웨어가 0x500F 변경 이벤트를 안 쏘는 경우) 대비 1초 안전망.
     * 경량 getter는 큐에서 coalesce 되므로 이벤트+폴링이 겹쳐도 중복 호출은 1회로 접힌다. 실패는 조용히 무시.
     */
    @OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    private fun observeLiveSettingsPolling() {
        // settingsManager.cameraSettings → uiState.cameraSettings 브리지(노출 스트립이 읽는 곳). 값 미러링만(네이티브 호출 없음)이라 항상 가동.
        viewModelScope.launch {
            settingsManager.cameraSettings.collect { s ->
                if (s != null) uiStateManager.updateCameraSettings(s)
            }
        }
        // 라이브뷰일 때만: seed + 이벤트 구동 재조회 + 1초 안전망. LV off 시 collectLatest 가 자식까지 전부 취소.
        viewModelScope.launch {
            uiState
                .map { it.isLiveViewActive }
                .distinctUntilChanged()
                .collectLatest { active ->
                    if (!active) return@collectLatest
                    settingsManager.refreshCameraSettingsQuiet() // seed
                    // 이벤트 구동(정석): 본체에서 설정이 바뀌면 onPropertyChanged 푸시 → debounce 후 경량 재조회.
                    launch {
                        cameraRepository.settingChanged()
                            .debounce(150L)
                            .collect { settingsManager.refreshCameraSettingsQuiet() }
                    }
                    // 놓친 이벤트/이벤트 미지원 대비 1초 안전망(자가 치유).
                    while (isActive) {
                        delay(1000L)
                        settingsManager.refreshCameraSettingsQuiet()
                    }
                }
        }
    }

    /**
     * 카메라 연결 상태 관찰.
     * 연결 상태가 잇따라 토글되는 동안 이전 구독 티어 collect 가 살아남으면 안되므로
     * collectLatest 로 이전 작업을 자동 취소한다.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeCameraConnection() {
        viewModelScope.launch {
            cameraRepository.isCameraConnected().collectLatest { isConnected ->
                uiStateManager.updateConnectionState(isConnected)
                if (isConnected) {
                    // 연결 시 설정 로딩을 생략하여 연결 속도 향상 (15초 단축)
                    // 설정은 사용자가 설정 탭을 열 때 로드됨
                    // settingsManager.loadCameraSettings()
                    // settingsManager.loadCameraCapabilities()

                    // EV/Storage 칩 로드. EV 조회는 네이티브에서 단일 config(get_single_config)로
                    // 처리되어 PTP/IP에서도 가볍다(전체 config walk 제거 — camera_widget_access.cpp).
                    launch {
                        try {
                            settingsManager.loadExposureCompensation()
                            settingsManager.loadStorageInfo()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.e(TAG, "EV/Storage 초기 로딩 실패", e)
                        }
                    }

                    // Firebase에서 최신 구독 티어를 가져와 업데이트
                    // AP 모드에서는 Firebase 오프라인으로 실패할 수 있으며,
                    // 그 경우 loadSubscriptionTierAtStartup에서 설정한 로컬 캐시 값이 유지됨
                    try {
                        getSubscriptionUseCase.getSubscriptionTier()
                            .collect { tier ->
                                // C-3 수정: Repository를 통한 간접 호출
                                cameraRepository.setSubscriptionTier(tier)
                                    .onSuccess {
                                        Log.d(TAG, "🔄 구독 티어 업데이트: $tier")
                                    }
                                    .onFailure { e ->
                                        Log.e(TAG, "구독 티어 업데이트 실패", e)
                                    }
                            }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "구독 티어 업데이트 실패 (로컬 캐시 값 유지)", e)
                    }
                }
            }
        }
    }

    /**
     * 촬영된 사진 관찰
     */
    private fun observeCapturedPhotos() {
        cameraRepository.getCapturedPhotos()
            .onEach { photos ->
                uiStateManager.updateCapturedPhotos(photos)
            }
            .launchIn(viewModelScope)
    }

    /**
     * 다운로드/처리 진행 카운트 관찰 (요구 E5).
     * capturedPhotos 와 동일한 repo Flow → UiState 경로.
     */
    private fun observeTransferQueue() {
        cameraRepository.getTransferQueue()
            .onEach { queue ->
                uiStateManager.updateTransferQueue(queue)
            }
            .launchIn(viewModelScope)
    }

    /**
     * 에러 이벤트 관찰
     */
    private fun observeErrorEvents() {
        // 일반 에러 이벤트
        errorHandlingManager.errorEvent
            .onEach { errorEvent ->
                // 네이티브/이벤트 원문(영문 등)을 그대로 표시 — i18n 불가한 동적 문자열이라 Raw.
                uiStateManager.setError(UiText.Raw(errorEvent.message))
                Log.e(TAG, "에러 이벤트 수신: ${errorEvent.type} - ${errorEvent.message}")
            }
            .launchIn(viewModelScope)

        // 네이티브 에러 이벤트  
        errorHandlingManager.nativeErrorEvent
            .onEach { nativeErrorEvent ->
                uiStateManager.setError(UiText.Raw(nativeErrorEvent.userFriendlyMessage))

                // 특별한 액션이 필요한 경우 처리
                when (nativeErrorEvent.actionRequired) {
                    com.inik.camcon.domain.manager.ErrorAction.RESTART_APP -> {
                        uiStateManager.showRestartDialog(true)
                    }
                    com.inik.camcon.domain.manager.ErrorAction.RECONNECT_CAMERA -> {
                        // 자동 재연결 시도
                        disconnectCamera()
                    }
                    else -> {
                        // 기본 에러 표시
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * 기타 상태들 관찰.
     * 최신 값만 의미를 가지는 흐름은 collectLatest 로 이전 emission 처리를 취소한다.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeOtherStates() {
        // 이벤트 리스너 상태 — 최신 값만 의미.
        viewModelScope.launch {
            cameraRepository.isEventListenerActive().collectLatest { isActive ->
                uiStateManager.updateEventListenerState(isActive)
            }
        }

        // 카메라 초기화 상태 - 시작만 감지하고 해제는 onFlushComplete에서 처리.
        viewModelScope.launch {
            cameraRepository.isInitializing().collectLatest { isInitializing ->
                // 초기화 시작만 UI에 반영하고, 해제는 onFlushComplete 콜백에서 처리
                if (isInitializing) {
                    uiStateManager.updateCameraInitialization(true)
                }
                // 카메라 초기화 완료(false)는 여기서 처리하지 않음 - onFlushComplete에서만 처리
            }
        }

        // ✅ 라이브뷰 프레임 Bitmap 디코딩 (IO 디스패처에서 처리) — CRITICAL-1 해결.
        // StateFlow 라 conflate 불필요. 디코딩 진행 중 새 프레임이 오면 이전 프레임은
        // 버려도 안전하므로 collectLatest 사용 — 디코딩 작업이 자동 취소되어 백프레셔 완화.
        viewModelScope.launch {
            liveViewFrame.collectLatest { frame ->
                if (frame != null) {
                    decodeLiveViewFrameAsync(frame)
                } else {
                    publishLiveViewBitmap(null)
                }
            }
        }
    }

    /**
     * F25: 라이브뷰 Bitmap 을 한 세대 지연 회수 정책으로 교체한다.
     * - 현재 표시 중인 비트맵은 RenderThread 가 아직 드로잉 중일 수 있으므로 즉시 recycle하지 않는다.
     * - 직전(pending) 비트맵만 회수하고, 현재 비트맵을 새 pending 으로 보관한다.
     */
    @Synchronized
    private fun publishLiveViewBitmap(newBitmap: android.graphics.Bitmap?) {
        val toRecycle = pendingRecycleBitmap
        pendingRecycleBitmap = _decodedLiveViewBitmap.value
        _decodedLiveViewBitmap.value = newBitmap
        try {
            if (toRecycle != null && toRecycle !== newBitmap && !toRecycle.isRecycled) {
                toRecycle.recycle()
            }
        } catch (e: Exception) {
            Log.w(TAG, "라이브뷰 Bitmap recycle 실패", e)
        }
    }

    /**
     * 카메라 리포지토리 초기화
     */
    private fun initializeCameraRepository() {
        viewModelScope.launch {
            try {
                uiStateManager.updateInitializingState(true)
                cameraRepository.setPhotoPreviewMode(false)
                uiStateManager.updateInitializingState(false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "카메라 리포지토리 초기화 실패", e)
                uiStateManager.updateInitializingState(false)
                errorHandlingManager.emitError(
                    com.inik.camcon.domain.manager.ErrorType.INITIALIZATION,
                    "카메라 초기화 실패: ${e.message}",
                    e,
                    com.inik.camcon.domain.manager.ErrorSeverity.HIGH
                )
            }
        }
    }

    /**
     * RAW 파일 제한 콜백 등록
     */
    private fun registerRawLimitCallback() {
        try {
            cameraRepository.setRawFileRestrictionCallback { fileName, restrictionMessage ->
                Log.d(TAG, "RAW 파일 제한: $fileName - $restrictionMessage")
                uiStateManager.setRawFileRestriction(fileName, restrictionMessage)
            }
            Log.d(TAG, "RAW 파일 제한 콜백 등록 완료")
        } catch (e: Exception) {
            Log.e(TAG, "RAW 파일 제한 콜백 등록 실패", e)
        }
    }

    // MARK: - Public Methods (UI에서 호출)

    /**
     * 카메라 연결 (UsbAutoConnectManager에 위임)
     */
    fun connectCamera(cameraId: String) {
        usbAutoConnectManager.connectCamera(cameraId, uiStateManager)
    }

    /**
     * 카메라 연결 해제 (UsbAutoConnectManager에 위임)
     */
    fun disconnectCamera() {
        // 사용자가 명시적으로 끊으면 핸드오프 보호 해제 — 이후 onCleared 정리가 정상 동작하도록.
        handoffTracker.clear()

        // 진행 중인 작업들 먼저 중단
        operationsManager.cleanup()

        // 연결 해제
        usbAutoConnectManager.disconnectCamera(uiStateManager)
    }

    /**
     * USB 디바이스 새로고침 (UsbAutoConnectManager에 위임)
     */
    fun refreshUsbDevices() {
        usbAutoConnectManager.refreshUsbDevices(uiStateManager)
    }

    /**
     * USB 권한 요청 (UsbAutoConnectManager에 위임)
     */
    fun requestUsbPermission() {
        usbAutoConnectManager.requestUsbPermission(uiStateManager)
    }

    /**
     * 사진 촬영 (OperationsManager에 위임).
     * 촬영 완료 후 스토리지 잔량 칩을 최신화하기 위해 백그라운드로 storage 재조회.
     */
    fun capturePhoto() {
        operationsManager.capturePhoto(uiState.value.shootingMode, uiStateManager)
        viewModelScope.launch {
            try {
                settingsManager.loadStorageInfo()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "촬영 후 스토리지 갱신 실패", e)
            }
        }
    }

    /**
     * 노출 보정 값 설정 (SettingsManager에 위임).
     * value 는 ExposureCompensation.available 의 raw 문자열(예: "+1/3", "0").
     */
    fun setExposureCompensation(value: String) {
        viewModelScope.launch {
            settingsManager.setExposureCompensation(value)
        }
    }

    /**
     * 라이브뷰 시작 (OperationsManager에 위임)
     */
    fun startLiveView() {
        if (operationsManager.isLiveViewActive()) {
            Log.d(TAG, "라이브뷰가 이미 활성화되어 있음")
            return
        }

        operationsManager.startLiveView(
            uiState.value.isConnected,
            cameraCapabilities.value,
            uiStateManager
        )
    }

    /**
     * 라이브뷰 중지 (OperationsManager에 위임)
     */
    fun stopLiveView() {
        operationsManager.stopLiveView(uiStateManager)
    }

    /**
     * 타임랩스 시작 (OperationsManager에 위임)
     */
    fun startTimelapse(interval: Int, totalShots: Int) {
        if (operationsManager.isTimelapseActive()) return

        operationsManager.startTimelapse(interval, totalShots, uiStateManager)
    }

    /**
     * 타임랩스 중지 (OperationsManager에 위임)
     */
    fun stopTimelapse() {
        operationsManager.stopTimelapse(uiStateManager)
    }

    /**
     * 자동초점 (OperationsManager에 위임)
     */
    fun performAutoFocus() {
        operationsManager.performAutoFocus(uiStateManager)
    }

    /**
     * 촬영 모드 설정
     */
    fun setShootingMode(mode: ShootingMode) {
        uiStateManager.setShootingMode(mode)
    }

    /**
     * 카메라 설정 업데이트 (SettingsManager에 위임)
     */
    fun updateCameraSetting(key: String, value: String) {
        viewModelScope.launch {
            settingsManager.updateCameraSetting(key, value)
        }
    }

    /**
     * 카메라 파일 목록 상태 확인 및 로드
     */
    fun checkCameraStatusAndLoadFiles(onFilesLoaded: (String) -> Unit) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "카메라 파일 목록 로드 전 상태 확인")
                uiStateManager.updateLoadingState(true)
                uiStateManager.clearError()

                // C-3 수정: Repository를 통한 간접 호출
                // 연결 상태 확인
                val isConnectedResult = cameraRepository.isCameraConnectedNow()
                val isConnected = isConnectedResult.getOrNull() ?: false

                if (!uiState.value.isConnected || !isConnected) {
                    Log.w(TAG, "카메라가 연결되지 않았거나 전원이 꺼져 있음")
                    uiStateManager.updateLoadingState(false)
                    errorHandlingManager.emitError(
                        com.inik.camcon.domain.manager.ErrorType.CONNECTION,
                        "카메라 연결을 확인해주세요.\n카메라가 켜져 있고 올바르게 연결되어 있는지 확인하십시오.",
                        null,
                        com.inik.camcon.domain.manager.ErrorSeverity.MEDIUM
                    )
                    return@launch
                }

                // 초기화 상태 확인
                val isInitializedResult = cameraRepository.isCameraInitializedNow()
                val isInitialized = isInitializedResult.getOrNull() ?: false

                if (!isInitialized) {
                    Log.w(TAG, "네이티브 카메라가 초기화되지 않음")
                    uiStateManager.updateLoadingState(false)
                    errorHandlingManager.emitError(
                        com.inik.camcon.domain.manager.ErrorType.INITIALIZATION,
                        "카메라 초기화가 필요합니다.\n카메라를 다시 연결해주세요.",
                        null,
                        com.inik.camcon.domain.manager.ErrorSeverity.MEDIUM
                    )
                    return@launch
                }

                // 파일 목록 가져오기
                Log.d(TAG, "카메라 파일 목록 로드 시작")
                val fileListResult = cameraRepository.getCameraFileListNow()

                fileListResult.onSuccess { fileList ->
                    if (fileList.isEmpty()) {
                        Log.w(TAG, "카메라 파일 목록이 비어있음")
                        uiStateManager.updateLoadingState(false)
                        errorHandlingManager.emitError(
                            com.inik.camcon.domain.manager.ErrorType.FILE_SYSTEM,
                            "카메라에서 파일 목록을 불러올 수 없습니다.\n카메라 상태를 확인하고 다시 시도해주세요.",
                            null,
                            com.inik.camcon.domain.manager.ErrorSeverity.MEDIUM
                        )
                        return@onSuccess
                    }

                    Log.d(TAG, "카메라 파일 목록 로드 성공: ${fileList.size}개")
                    uiStateManager.updateLoadingState(false)
                    uiStateManager.clearError()
                    onFilesLoaded(fileList.joinToString(","))
                }.onFailure { e ->
                    Log.w(TAG, "카메라 파일 목록 로드 실패: ${e.message}")
                    uiStateManager.updateLoadingState(false)
                    errorHandlingManager.emitError(
                        com.inik.camcon.domain.manager.ErrorType.FILE_SYSTEM,
                        "카메라에서 파일 목록을 불러올 수 없습니다.\n카메라 상태를 확인하고 다시 시도해주세요.",
                        null,
                        com.inik.camcon.domain.manager.ErrorSeverity.MEDIUM
                    )
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "카메라 파일 목록 확인 중 예외 발생", e)
                uiStateManager.updateLoadingState(false)
                errorHandlingManager.emitError(
                    com.inik.camcon.domain.manager.ErrorType.FILE_SYSTEM,
                    "카메라 상태 확인 중 오류가 발생했습니다.\n카메라 연결을 확인해주세요.",
                    e,
                    com.inik.camcon.domain.manager.ErrorSeverity.MEDIUM
                )
            }
        }
    }

    /**
     * 카메라 전원 상태 확인
     * Note: 동기 함수이므로 Repository 호출을 위해 viewModelScope에서 비동기로 처리
     */
    fun checkCameraPowerStatus() {
        viewModelScope.launch {
            try {
                // C-3 수정: Repository를 통한 간접 호출
                val isConnectedResult = cameraRepository.isCameraConnectedNow()
                val isInitializedResult = cameraRepository.isCameraInitializedNow()

                val isConnected = isConnectedResult.getOrNull() ?: false
                val isInitialized = isInitializedResult.getOrNull() ?: false

                Log.d(TAG, "카메라 상태 확인 - 연결: $isConnected, 초기화: $isInitialized")

                if (!isConnected || !isInitialized) {
                    errorHandlingManager.emitError(
                        com.inik.camcon.domain.manager.ErrorType.CONNECTION,
                        "카메라 전원을 확인해주세요.\n카메라가 켜져 있고 정상적으로 연결되어 있는지 확인하십시오.",
                        null,
                        com.inik.camcon.domain.manager.ErrorSeverity.MEDIUM
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "카메라 전원 상태 확인 실패", e)
                errorHandlingManager.emitError(
                    com.inik.camcon.domain.manager.ErrorType.CONNECTION,
                    "카메라 상태를 확인할 수 없습니다.\n카메라 연결을 다시 확인해주세요.",
                    e,
                    com.inik.camcon.domain.manager.ErrorSeverity.MEDIUM
                )
            }
        }
    }

    /**
     * 이벤트 리스너 시작
     */
    fun startEventListener() {
        viewModelScope.launch {
            try {
                cameraRepository.startCameraEventListener()
                    .onSuccess {
                        Log.d(TAG, "이벤트 리스너 시작 성공")
                    }
                    .onFailure { error ->
                        Log.e(TAG, "이벤트 리스너 시작 실패", error)
                        errorHandlingManager.emitError(
                            com.inik.camcon.domain.manager.ErrorType.OPERATION,
                            "이벤트 리스너 시작 실패: ${error.message}",
                            error,
                            com.inik.camcon.domain.manager.ErrorSeverity.MEDIUM
                        )
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "이벤트 리스너 시작 중 예외 발생", e)
                errorHandlingManager.emitError(
                    com.inik.camcon.domain.manager.ErrorType.OPERATION,
                    "이벤트 리스너 시작 실패: ${e.message}",
                    e,
                    com.inik.camcon.domain.manager.ErrorSeverity.MEDIUM
                )
            }
        }
    }

    /**
     * 이벤트 리스너 중지
     */
    fun stopEventListener(onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                cameraRepository.stopCameraEventListener()
                    .onSuccess {
                        Log.d(TAG, "이벤트 리스너 중지 성공")
                        onComplete?.invoke()
                    }
                    .onFailure { error ->
                        Log.e(TAG, "이벤트 리스너 중지 실패", error)
                        errorHandlingManager.emitError(
                            com.inik.camcon.domain.manager.ErrorType.OPERATION,
                            "이벤트 리스너 중지 실패: ${error.message}",
                            error,
                            com.inik.camcon.domain.manager.ErrorSeverity.MEDIUM
                        )
                        onComplete?.invoke()
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "이벤트 리스너 중지 중 예외 발생", e)
                errorHandlingManager.emitError(
                    com.inik.camcon.domain.manager.ErrorType.OPERATION,
                    "이벤트 리스너 중지 실패: ${e.message}",
                    e,
                    com.inik.camcon.domain.manager.ErrorSeverity.MEDIUM
                )
                onComplete?.invoke()
            }
        }
    }

    // MARK: - State Management Methods (UiStateManager에 위임)

    fun clearError() = uiStateManager.clearError()
    fun clearPtpTimeout() = uiStateManager.clearPtpTimeout()
    fun clearUsbDisconnection() = uiStateManager.clearUsbDisconnection()
    fun clearRawFileRestriction() = uiStateManager.clearRawFileRestriction()
    fun dismissRestartDialog() = uiStateManager.showRestartDialog(false)
    fun dismissCameraStatusCheckDialog() = uiStateManager.showCameraStatusCheckDialog(false)

    /**
     * 카메라 기능 제한 안내 닫기
     */
    fun clearCameraFunctionLimitation() {
        uiStateManager.clearCameraFunctionLimitation()
        Log.d(TAG, "카메라 기능 제한 안내 닫기")
    }

    /**
     * Nikon STA 경고 닫기
     */
    fun dismissNikonStaWarning() {
        uiStateManager.dismissNikonStaWarning()
        Log.d(TAG, "Nikon STA 경고 닫기")
    }

    fun setTabSwitchFlag(isReturning: Boolean) {
        Log.d(TAG, "탭 전환 플래그 설정: $isReturning")
        // UiStateManager에서 관리하도록 변경 가능
    }

    fun getAndClearTabSwitchFlag(): Boolean {
        Log.d(TAG, "탭 전환 플래그 확인 및 초기화")
        // UiStateManager에서 관리하도록 변경 가능
        return false
    }

    fun refreshCameraCapabilities() {
        viewModelScope.launch {
            settingsManager.loadCameraCapabilities()
        }
    }

    /**
     * 카메라 설정 수동 로드 (설정 탭을 열 때 호출)
     */
    fun loadCameraSettingsManually() {
        viewModelScope.launch {
            settingsManager.loadCameraSettings()
            settingsManager.loadCameraCapabilities()
        }
    }

    // === Advanced Capture ===
    fun startBulbCapture() = advancedCaptureManager.startBulbCapture()
    fun endBulbCapture() = advancedCaptureManager.endBulbCapture()
    fun bulbCaptureWithDuration(seconds: Int) = advancedCaptureManager.bulbCaptureWithDuration(seconds)
    fun startVideoRecording() = advancedCaptureManager.startVideoRecording()
    fun stopVideoRecording() = advancedCaptureManager.stopVideoRecording()
    fun startIntervalCapture(intervalSeconds: Int, totalFrames: Int) = advancedCaptureManager.startIntervalCapture(intervalSeconds, totalFrames)
    fun stopIntervalCapture() = advancedCaptureManager.stopIntervalCapture()
    fun captureDualMode(keepRawOnCard: Boolean, downloadJpeg: Boolean) = advancedCaptureManager.captureDualMode(keepRawOnCard, downloadJpeg)
    fun triggerCapture() = advancedCaptureManager.triggerCapture()
    fun captureAudio() = advancedCaptureManager.captureAudio()
    val bulbState get() = advancedCaptureManager.bulbState
    val videoState get() = advancedCaptureManager.videoState
    val intervalStatus get() = advancedCaptureManager.intervalStatus

    // === Focus ===
    fun setAFMode(mode: String) = focusManager.setAFMode(mode)
    fun refreshAFMode() = focusManager.refreshAFMode()
    fun setAFArea(x: Int, y: Int, width: Int, height: Int) = focusManager.setAFArea(x, y, width, height)
    fun driveManualFocus(steps: Int) = focusManager.driveManualFocus(steps)
    val focusConfig get() = focusManager.focusConfig
    val isFocusDriving get() = focusManager.isFocusDriving

    // === File ===
    fun refreshStorageInfo() = fileManager.refreshStorageInfo()
    fun downloadAllRawFiles(folder: String) = fileManager.downloadAllRawFiles(folder)
    fun uploadFileToCamera(folder: String, filename: String, data: ByteArray) = fileManager.uploadFileToCamera(folder, filename, data)
    fun deleteAllFilesInFolder(folder: String) = fileManager.deleteAllFilesInFolder(folder)
    fun createFolder(parentFolder: String, folderName: String) = fileManager.createFolder(parentFolder, folderName)
    fun removeFolder(parentFolder: String, folderName: String) = fileManager.removeFolder(parentFolder, folderName)
    fun initializeCache() = fileManager.initializeCache()
    fun invalidateFileCache() = fileManager.invalidateFileCache()
    val storageInfo get() = fileManager.storageInfo

    // === Streaming ===
    fun startStreaming() = streamingManager.startStreaming()
    fun stopStreaming() = streamingManager.stopStreaming()
    fun setStreamingParameters(width: Int, height: Int, fps: Int) = streamingManager.setStreamingParameters(width, height, fps)
    val isStreaming get() = streamingManager.isStreaming
    val currentStreamFrame get() = streamingManager.currentFrame

    // === Diagnostics ===
    fun runFullDiagnostics() = diagnosticsManager.runFullDiagnostics()
    fun clearErrorHistory() = diagnosticsManager.clearErrorHistory()
    fun refreshMemoryPoolStatus() = diagnosticsManager.refreshMemoryPoolStatus()
    fun clearCameraFilePool() = diagnosticsManager.clearCameraFilePool()
    val diagnosticsReport get() = diagnosticsManager.diagnosticsReport
    val memoryPoolStatus get() = diagnosticsManager.memoryPoolStatus

    /**
     * 라이브뷰 핫패스(프레임당 1회, ~33ms 주기)에서 매번 DataStore 디스크 읽기
     * (`isHistogramEnabled.first()`)를 하지 않도록 토글을 StateFlow 로 캐시한다.
     */
    private val isHistogramEnabledCached: StateFlow<Boolean> by lazy {
        appSettingsRepository.isHistogramEnabled
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    }

    /**
     * ✅ 라이브뷰 프레임 Bitmap 디코딩 (IO 디스패처에서 처리)
     *
     * CRITICAL-1 해결:
     * - Bitmap 디코딩을 Compose 렌더 스레드에서 IO 디스패처로 오프로드
     * - 렌더 스레드 블로킹 제거 → 프레임 드롭 50% 이상 감소
     * - 이전 Bitmap은 DisposableEffect에서 자동 recycle (W-2 해결)
     *
     * suspend 로 collectLatest 블록 안에서 직접 실행된다 — 이전에는 내부에서
     * viewModelScope.launch 를 별도로 띄워 collectLatest 의 취소(최신 프레임 우선)가
     * 디코딩 작업에 전파되지 않았고, 프레임마다 코루틴이 누적됐다.
     *
     * 추가로 히스토그램 토글이 ON 이면 동일 디스패처에서 히스토그램까지 계산한다.
     * 토글 OFF 면 히스토그램 계산을 스킵하여 비용 제로.
     */
    private suspend fun decodeLiveViewFrameAsync(frame: LiveViewFrame) {
        try {
            val decodedBitmap = withContext(ioDispatcher) {
                val bitmapOptions = BitmapFactory.Options().apply {
                    inMutable = true
                    inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                }
                try {
                    BitmapFactory.decodeByteArray(
                        frame.data, 0, frame.data.size, bitmapOptions
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "라이브뷰 Bitmap 디코딩 실패", e)
                    null
                }
            }

            // 디코딩 후 취소되었으면(다음 프레임 도착) 방금 만든 비트맵은 대입 없이 즉시 회수.
            if (!coroutineContext.isActive) {
                decodedBitmap?.let { if (!it.isRecycled) it.recycle() }
                return
            }

            publishLiveViewBitmap(decodedBitmap)

            if (decodedBitmap != null && isHistogramEnabledCached.value) {
                val hist = withContext(ioDispatcher) {
                    try {
                        com.inik.camcon.presentation.util.computeHistogram(decodedBitmap)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "히스토그램 계산 실패", e)
                        null
                    }
                }
                _histogramData.value = hist
            } else {
                _histogramData.value = null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "라이브뷰 프레임 처리 중 오류", e)
        }
    }

    override fun onCleared() {
        super.onCleared()

        // 핸드오프 중(연결 성공 직후 카메라 컨트롤로 이동하며 중간 Activity가 파괴되는 구간)에는
        // 싱글톤 매니저 정리를 건너뛴다. 정리하면 방금 맺은 싱글톤 카메라 연결이 끊긴다.
        // (operations/usbAutoConnect/settings 매니저는 모두 @Singleton이라 다른 Activity의
        //  CameraViewModel이 동일 인스턴스를 계속 사용하므로 스킵해도 누수 없음)
        if (!handoffTracker.isActive) {
            // 매니저들 정리
            operationsManager.cleanup()
            usbAutoConnectManager.cleanup()
            settingsManager.cleanup()
        } else {
            Log.d(TAG, "연결 핸드오프 중 - CameraViewModel 매니저 정리 생략(연결 유지)")
        }
        // errorHandlingManager는 @Singleton이며 앱 전역 단일 네이티브 에러 콜백(setErrorCallback)을 보유한다.
        // 이 콜백의 usbDisconnectedEvent 소비자는 ViewModel이 아니라 프로세스 생명주기 동안 살아있는
        // CameraLifecycleRepositoryImpl(ApplicationScope)이므로, Activity 스코프 ViewModel의 onCleared에서
        // cleanup()을 호출하면 앱 전역의 USB 분리/타임아웃 네이티브 에러 수신이 끊긴다. 따라서 여기서 해제하지 않는다.

        // RAW 제한 콜백 해제
        cameraRepository.setRawFileRestrictionCallback(null)

        // F25: 라이브뷰 Bitmap 최종 회수 (현재 표시본 + 보관 중인 pending)
        try {
            pendingRecycleBitmap?.let { if (!it.isRecycled) it.recycle() }
            pendingRecycleBitmap = null
            _decodedLiveViewBitmap.value?.let { if (!it.isRecycled) it.recycle() }
            _decodedLiveViewBitmap.value = null
        } catch (e: Exception) {
            Log.w(TAG, "라이브뷰 Bitmap 최종 회수 실패", e)
        }

        Log.d(TAG, "ViewModel 정리 완료")
    }
}
