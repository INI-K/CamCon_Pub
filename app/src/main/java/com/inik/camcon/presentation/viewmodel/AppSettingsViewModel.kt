package com.inik.camcon.presentation.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.domain.model.LiveViewQuality
import com.inik.camcon.domain.model.ThemeMode
import com.inik.camcon.domain.repository.AppSettingsRepository
import com.inik.camcon.domain.usecase.ColorTransferUseCase
import com.inik.camcon.domain.usecase.GetSubscriptionUseCase
import com.inik.camcon.domain.usecase.PipelineFeature
import com.inik.camcon.domain.usecase.ToggleDecision
import com.inik.camcon.domain.usecase.ValidateFeatureAccessUseCase
import com.inik.camcon.domain.usecase.ValidateImageFormatUseCase
import com.inik.camcon.domain.usecase.camera.ReadNativeLogUseCase
import com.inik.camcon.domain.usecase.camera.StartNativeLogUseCase
import com.inik.camcon.domain.usecase.camera.StopNativeLogUseCase
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.utils.LogMask
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appSettingsRepository: AppSettingsRepository,
    private val colorTransferUseCase: ColorTransferUseCase,
    private val getSubscriptionUseCase: GetSubscriptionUseCase,
    private val startNativeLogUseCase: StartNativeLogUseCase,
    private val stopNativeLogUseCase: StopNativeLogUseCase,
    private val readNativeLogUseCase: ReadNativeLogUseCase,
    private val validateImageFormatUseCase: ValidateImageFormatUseCase,
    private val validateFeatureAccessUseCase: ValidateFeatureAccessUseCase
) : ViewModel() {

    companion object {
        private const val TAG = "AppSettingsViewModel"
    }

    /**
     * pref 우선 병합 티어 (cold flow).
     *
     * [subscriptionTier] StateFlow 의 초기값(FREE) 오염 없이 파생 판정(미리보기 접근·정합화·세터)에
     * 사용한다. StateFlow 에서 map 하면 초기값 FREE 가 동기 재방출되어 잠금 플래시·오정합화를 만든다.
     */
    private val effectiveTierFlow: Flow<SubscriptionTier> = combine(
        appSettingsRepository.subscriptionTierEnum,
        getSubscriptionUseCase.getSubscriptionTier()
    ) { prefTier, firebaseTier ->
        // Preferences 티어가 FREE 가 아니면 우선 사용, FREE 면 Firebase 값 확인(기존 로직 그대로).
        if (prefTier != SubscriptionTier.FREE) {
            prefTier
        } else {
            firebaseTier ?: SubscriptionTier.FREE
        }
    }

    /** 필름↔색감 스왑/정합화 발생 시 꺼진 쪽을 통지하는 1회성 이벤트. */
    private val _pipelineSwapEvent = MutableSharedFlow<PipelineFeature>(extraBufferCapacity = 1)
    val pipelineSwapEvent: SharedFlow<PipelineFeature> = _pipelineSwapEvent.asSharedFlow()

    init {
        // 비허용 티어에서 필름·색감이 '둘 다 ON' 인 상태를 정합화한다(색감 OFF).
        // 반드시 effectiveTierFlow(cold)를 사용해야 한다 — subscriptionTier StateFlow 의 초기값(FREE)이
        // PRO 사용자에게도 순간 방출되어 '둘 다 ON' 을 잘못 정합화(색감 영구 OFF)하는 것을 막는다.
        // VM 인스턴스가 화면마다 별개라 이 관찰자가 중복 실행될 수 있으나 setColorTransferEnabled(false)는
        // 멱등·DataStore 직렬화로 안전하고, 토스트는 best-effort 다.
        viewModelScope.launch {
            combine(
                effectiveTierFlow,
                appSettingsRepository.isFilmSimulationEnabled,
                appSettingsRepository.isColorTransferEnabled
            ) { tier, film, color ->
                validateFeatureAccessUseCase.resolveActivePipeline(tier, film, color)
            }.collect { active ->
                if (active.needsReconcile) {
                    // 1회 영속 — 쓰기 후 flow 가 재방출되어 needsReconcile=false 로 자연 종료.
                    appSettingsRepository.setColorTransferEnabled(false)
                    _pipelineSwapEvent.tryEmit(PipelineFeature.COLOR_TRANSFER)
                }
            }
        }
    }

    /**
     * 파일 경로가 RAW 인지 판정한다. RAW 판정은 [ValidateImageFormatUseCase] 단일 지점에 위임(CLAUDE.md §2).
     * 필름 에디터 진입점 게이팅(RAW 비노출)에 사용한다.
     */
    fun isRawFile(path: String): Boolean = validateImageFormatUseCase.isRawFile(path)

    /**
     * 카메라 컨트롤 표시 여부
     */
    val isCameraControlsEnabled: StateFlow<Boolean> =
        appSettingsRepository.isCameraControlsEnabled
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = false
            )

    /**
     * 라이브뷰 표시 여부 - ADMIN 티어에서만 활성화 가능
     * 기본값을 false로 변경하여 USB 연결 시 기본적으로 수신 화면이 표시되도록 수정
     */
    val isLiveViewEnabled: StateFlow<Boolean> = combine(
        appSettingsRepository.isLiveViewEnabled,
        getSubscriptionUseCase.getSubscriptionTier()
    ) { settingEnabled, subscriptionTier ->
        // ADMIN 티어가 아니면 항상 false
        if (subscriptionTier != SubscriptionTier.ADMIN) {
            false
        } else {
            settingEnabled
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    /**
     * 현재 사용자가 ADMIN 티어인지 확인
     */
    val isAdminTier: StateFlow<Boolean> = getSubscriptionUseCase.getSubscriptionTier()
        .map { tier ->
            tier == SubscriptionTier.ADMIN
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    /**
     * 다크 모드 활성화 여부
     */
    val isDarkModeEnabled: StateFlow<Boolean> = appSettingsRepository.isDarkModeEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    /**
     * 자동 이벤트 리스너 시작 여부
     */
    val isAutoStartEventListenerEnabled: StateFlow<Boolean> =
        appSettingsRepository.isAutoStartEventListenerEnabled
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = true
            )

    /**
     * 비활성화 시 최신 사진 표시 여부
     */
    val isShowLatestPhotoWhenDisabled: StateFlow<Boolean> =
        appSettingsRepository.isShowLatestPhotoWhenDisabled
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = true
            )

    /**
     * 색감 전송 기능 활성화 여부
     */
    val isColorTransferEnabled: StateFlow<Boolean> = appSettingsRepository.isColorTransferEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    /**
     * 색감 전송 참조 이미지 경로
     */
    val colorTransferReferenceImagePath: StateFlow<String?> =
        appSettingsRepository.colorTransferReferenceImagePath
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null
            )

    /**
     * 색감 전송 강도 (0.0 ~ 1.0)
     */
    val colorTransferIntensity: StateFlow<Float> = appSettingsRepository.colorTransferIntensity
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.03f
        )

    /**
     * 색감 전송 대상 이미지 경로
     */
    val colorTransferTargetImagePath: StateFlow<String?> =
        appSettingsRepository.colorTransferTargetImagePath
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null
            )

    /**
     * 필름 시뮬레이션(필름 LUT) 기능 활성화 여부
     */
    val isFilmSimulationEnabled: StateFlow<Boolean> = appSettingsRepository.isFilmSimulationEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    /**
     * 선택된 필름 LUT id (빈 문자열이면 선택 없음)
     */
    val selectedFilmLutId: StateFlow<String> = appSettingsRepository.selectedFilmLutId
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    /**
     * 필름 시뮬레이션 강도 (0.0 ~ 1.0)
     */
    val filmSimulationIntensity: StateFlow<Float> = appSettingsRepository.filmSimulationIntensity
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 1.0f
        )

    /**
     * RAW 파일 다운로드 활성화 여부
     */
    val isRawFileDownloadEnabled: StateFlow<Boolean> =
        appSettingsRepository.isRawFileDownloadEnabled
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = true
            )

    /**
     * 현재 구독 티어 - Preferences에 저장된 값 우선, 없으면 Firebase에서 가져옴.
     * 병합 로직은 [effectiveTierFlow] 로 추출되어 파생 판정과 공유된다(동작 무변경).
     */
    val subscriptionTier: StateFlow<com.inik.camcon.domain.model.SubscriptionTier> =
        effectiveTierFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = com.inik.camcon.domain.model.SubscriptionTier.FREE
        )

    /**
     * 미리보기 탭 접근 허용 여부.
     *
     * null = 티어 미확정(pref DataStore 첫 read 까지 수 ms). 반드시 [effectiveTierFlow] 에서 파생한다 —
     * [subscriptionTier] StateFlow 에서 map 하면 초기값 FREE 가 동기 재방출되어 PRO 사용자에게 false
     * 플래시가 발생한다. 첫 방출이 pref 병합 티어이므로 PRO 는 null→true 로만 이어진다.
     */
    val photoPreviewAccess: StateFlow<Boolean?> =
        effectiveTierFlow
            .map { validateFeatureAccessUseCase.isPhotoPreviewAllowed(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null
            )

    /**
     * 테마 모드 설정
     */
    val themeMode: StateFlow<ThemeMode> = appSettingsRepository.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ThemeMode.FOLLOW_SYSTEM
        )

    /**
     * 네이티브 로그 캡처 활성화 여부
     */
    val isNativeLogCaptureEnabled: StateFlow<Boolean> =
        appSettingsRepository.isNativeLogCaptureEnabled
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = false
            )

    /**
     * 셔터 사운드 활성화 여부 (기본값: true)
     */
    val isShutterSoundEnabled: StateFlow<Boolean> =
        appSettingsRepository.isShutterSoundEnabled
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = true
            )

    /**
     * 사진 도착 시 진동 알림 활성화 여부 (기본값: false)
     */
    val isVibrateOnPhotoReceivedEnabled: StateFlow<Boolean> =
        appSettingsRepository.isVibrateOnPhotoReceivedEnabled
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = false
            )

    /**
     * 라이브뷰 그리드 오버레이 활성화 여부 (기본값: false)
     */
    val isLiveViewGridEnabled: StateFlow<Boolean> =
        appSettingsRepository.isLiveViewGridEnabled
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = false
            )

    /**
     * 라이브뷰 화질 (기본값: QUALITY)
     */
    val liveViewQuality: StateFlow<LiveViewQuality> =
        appSettingsRepository.liveViewQuality
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = LiveViewQuality.QUALITY
            )

    /**
     * 촬영 화면 코치마크 표시 완료 여부 (기본값: false)
     */
    val hasSeenCaptureCoachmark: StateFlow<Boolean> =
        appSettingsRepository.hasSeenCaptureCoachmark
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = false
            )

    /**
     * 마지막 타임랩스 간격(초) prefill (기본값: 5)
     */
    val lastTimelapseInterval: StateFlow<Int> =
        appSettingsRepository.lastTimelapseInterval
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = 5
            )

    /**
     * 마지막 타임랩스 총 컷 수 prefill (기본값: 100)
     */
    val lastTimelapseCount: StateFlow<Int> =
        appSettingsRepository.lastTimelapseCount
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = 100
            )

    /**
     * 라이브뷰 히스토그램 오버레이 활성화 여부 (기본값: false)
     */
    val isHistogramEnabled: StateFlow<Boolean> =
        appSettingsRepository.isHistogramEnabled
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = false
            )

    /**
     * 라이브뷰 포커스 피킹 오버레이 활성화 여부 (기본값: false)
     */
    val isFocusPeakingEnabled: StateFlow<Boolean> =
        appSettingsRepository.isFocusPeakingEnabled
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = false
            )

    /**
     * 첫 사용자 온보딩 완료 여부.
     * 첫 emit 전(DataStore 초기값) 동안 깜빡임을 막기 위해 initialValue=null 로 유지한다.
     */
    val isOnboardingCompleted: StateFlow<Boolean?> =
        appSettingsRepository.isOnboardingCompleted
            .map { it }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null
            )

    fun setOnboardingCompleted(completed: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setOnboardingCompleted(completed)
        }
    }

    // 로그 파일 경로 생성
    private fun getLogFilePath(): String {
        val logDir = context.filesDir
        return "${logDir}/libgphoto2_debug_${System.currentTimeMillis()}.txt"
    }

    /**
     * 카메라 컨트롤 표시 여부 설정
     */
    fun setCameraControlsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setCameraControlsEnabled(enabled)
        }
    }

    /**
     * 라이브뷰 표시 여부 설정
     */
    fun setLiveViewEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setLiveViewEnabled(enabled)
        }
    }

    /**
     * 다크 모드 활성화/비활성화
     */
    fun setDarkModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setDarkModeEnabled(enabled)
        }
    }

    /**
     * 자동 이벤트 리스너 시작 활성화/비활성화
     */
    fun setAutoStartEventListenerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setAutoStartEventListenerEnabled(enabled)
        }
    }

    /**
     * 비활성화 시 최신 사진 표시 활성화/비활성화
     */
    fun setShowLatestPhotoWhenDisabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setShowLatestPhotoWhenDisabled(enabled)
        }
    }

    /**
     * 색감 전송 기능 활성화/비활성화.
     *
     * 비허용 티어(FREE/BASIC)에서 필름이 이미 ON 인데 색감을 켜면 필름을 끄고 켠다(자동 스왑).
     * 티어는 `.value` 스냅샷이 아니라 [effectiveTierFlow].first() 로 읽는다 — VM 이 화면마다 별개
     * 인스턴스라 collect 없는 인스턴스의 StateFlow 값은 영영 초기 FREE 이기 때문.
     */
    fun setColorTransferEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                val tier = effectiveTierFlow.first()
                val otherOn = appSettingsRepository.isFilmSimulationEnabled.first()
                val decision = validateFeatureAccessUseCase.resolveExclusiveToggle(
                    tier, PipelineFeature.COLOR_TRANSFER, otherOn
                )
                if (decision is ToggleDecision.Swap) {
                    // OFF 먼저 → ON 나중 순서로 '둘 다 ON' 창을 만들지 않는다.
                    appSettingsRepository.setFilmSimulationEnabled(false)
                    _pipelineSwapEvent.tryEmit(PipelineFeature.FILM_SIMULATION)
                }
            }
            appSettingsRepository.setColorTransferEnabled(enabled)
        }
    }

    /**
     * 색감 전송 참조 이미지 경로 설정
     */
    fun setColorTransferReferenceImagePath(path: String?) {
        viewModelScope.launch {
            colorTransferUseCase.clearReferenceCache()
            appSettingsRepository.setColorTransferReferenceImagePath(path)
        }
    }

    /**
     * 색감 전송 강도 설정 (0.0 ~ 1.0)
     */
    fun setColorTransferIntensity(intensity: Float) {
        viewModelScope.launch {
            appSettingsRepository.setColorTransferIntensity(intensity)
        }
    }

    /**
     * 색감 전송 대상 이미지 경로 설정
     */
    fun setColorTransferTargetImagePath(path: String?) {
        viewModelScope.launch {
            appSettingsRepository.setColorTransferTargetImagePath(path)
        }
    }

    /**
     * 필름 시뮬레이션 기능 활성화/비활성화.
     *
     * 비허용 티어(FREE/BASIC)에서 색감이 이미 ON 인데 필름을 켜면 색감을 끄고 켠다(자동 스왑).
     * 티어는 [setColorTransferEnabled] 와 동일하게 [effectiveTierFlow].first() 로 읽는다.
     */
    fun setFilmSimulationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                val tier = effectiveTierFlow.first()
                val otherOn = appSettingsRepository.isColorTransferEnabled.first()
                val decision = validateFeatureAccessUseCase.resolveExclusiveToggle(
                    tier, PipelineFeature.FILM_SIMULATION, otherOn
                )
                if (decision is ToggleDecision.Swap) {
                    // OFF 먼저 → ON 나중 순서로 '둘 다 ON' 창을 만들지 않는다.
                    appSettingsRepository.setColorTransferEnabled(false)
                    _pipelineSwapEvent.tryEmit(PipelineFeature.COLOR_TRANSFER)
                }
            }
            appSettingsRepository.setFilmSimulationEnabled(enabled)
        }
    }

    /**
     * 선택된 필름 LUT id 설정 (빈 문자열이면 선택 해제)
     */
    fun setSelectedFilmLutId(id: String) {
        viewModelScope.launch {
            appSettingsRepository.setSelectedFilmLutId(id)
        }
    }

    /**
     * 필름 시뮬레이션 강도 설정 (0.0 ~ 1.0)
     */
    fun setFilmSimulationIntensity(intensity: Float) {
        viewModelScope.launch {
            appSettingsRepository.setFilmSimulationIntensity(intensity)
        }
    }

    /**
     * RAW 파일 다운로드 활성화 여부 설정
     */
    fun setRawFileDownloadEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setRawFileDownloadEnabled(enabled)
        }
    }

    /**
     * 테마 모드 설정
     */
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            appSettingsRepository.setThemeMode(mode)
        }
    }

    /**
     * 네이티브 로그 캡처 활성화/비활성화
     */
    fun setNativeLogCaptureEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setNativeLogCaptureEnabled(enabled)

            if (enabled) {
                // 로그 캡처 시작
                val logPath = getLogFilePath()
                // GP_LOG_DEBUG 레벨로 설정 (DATA는 전송 바이너리 hexdump 포함하여 로그 폭증)
                val result = startNativeLogUseCase(logPath)

                if (result) {
                    Log.i(TAG, "네이티브 로그 캡처 시작: ${LogMask.path(logPath)}")
                } else {
                    Log.e(TAG, "네이티브 로그 파일 시작 실패")
                    // 실패 시 설정 원복
                    appSettingsRepository.setNativeLogCaptureEnabled(false)
                }
            } else {
                // 로그 캡처 중지
                stopNativeLogUseCase()
                Log.i(TAG, "네이티브 로그 캡처 중지")
            }
        }
    }

    /**
     * 로그 파일 내용 가져오기
     */
    suspend fun getLogFileContent(): String {
        return try {
            val logFiles = context.filesDir.listFiles { file ->
                file.name.startsWith("libgphoto2_debug_") && file.name.endsWith(".txt")
            }

            val latestLog = logFiles?.maxByOrNull { it.lastModified() }

            if (latestLog != null) {
                readNativeLogUseCase(latestLog.absolutePath)
            } else {
                "로그 파일이 없습니다."
            }
        } catch (e: Exception) {
            Log.e(TAG, "로그 파일 읽기 실패", e)
            "로그 파일 읽기 실패: ${e.message}"
        }
    }

    /**
     * 로그 파일 목록 가져오기
     */
    fun getLogFiles(): List<String> {
        return try {
            val logFiles = context.filesDir.listFiles { file ->
                file.name.startsWith("libgphoto2_debug_") && file.name.endsWith(".txt")
            }

            logFiles?.map { it.absolutePath }?.sortedDescending() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "로그 파일 목록 조회 실패", e)
            emptyList()
        }
    }

    /**
     * 셔터 사운드 활성화 토글
     */
    fun setShutterSoundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setShutterSoundEnabled(enabled)
        }
    }

    /**
     * 사진 도착 시 진동 알림 토글
     */
    fun setVibrateOnPhotoReceivedEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setVibrateOnPhotoReceivedEnabled(enabled)
        }
    }

    /**
     * 라이브뷰 그리드 오버레이 활성화 토글
     */
    fun setLiveViewGridEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setLiveViewGridEnabled(enabled)
        }
    }

    /**
     * 라이브뷰 화질 설정
     */
    fun setLiveViewQuality(quality: LiveViewQuality) {
        viewModelScope.launch {
            appSettingsRepository.setLiveViewQuality(quality)
        }
    }

    /**
     * 라이브뷰 히스토그램 오버레이 활성화 토글
     */
    fun setHistogramEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setHistogramEnabled(enabled)
        }
    }

    /**
     * 라이브뷰 포커스 피킹 오버레이 활성화 토글
     */
    fun setFocusPeakingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setFocusPeakingEnabled(enabled)
        }
    }

    /**
     * 촬영 코치마크 표시 완료 플래그 저장
     */
    fun setHasSeenCaptureCoachmark(seen: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setHasSeenCaptureCoachmark(seen)
        }
    }

    /**
     * 마지막 타임랩스 설정 prefill 저장
     */
    fun setLastTimelapseSettings(intervalSeconds: Int, totalCount: Int) {
        viewModelScope.launch {
            appSettingsRepository.setLastTimelapseInterval(intervalSeconds)
            appSettingsRepository.setLastTimelapseCount(totalCount)
        }
    }

    /**
     * 모든 설정 초기화
     */
    fun resetAllSettings() {
        viewModelScope.launch {
            appSettingsRepository.clearAllSettings()
        }
    }
}