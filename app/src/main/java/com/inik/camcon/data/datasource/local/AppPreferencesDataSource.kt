package com.inik.camcon.data.datasource.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.inik.camcon.BuildConfig
import com.inik.camcon.domain.model.LiveViewQuality
import com.inik.camcon.domain.model.ThemeMode
import com.inik.camcon.domain.repository.AppSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

/**
 * 앱 설정 정보를 관리하는 DataSource.
 *
 * 구독 티어 / RAW 파일 다운로드 플래그처럼 위변조 시 게이팅을 우회당할 수 있는
 * 민감 플래그는 평문 DataStore가 아닌 [EncryptedAppPreferences]에 저장한다.
 * 외부 호출자 시그니처(`subscriptionTier`, `subscriptionTierEnum`,
 * `isRawFileDownloadEnabled`, `setRawFileDownloadEnabled`, `setSubscriptionTier`,
 * `saveSubscriptionTier`)는 그대로 유지하고 내부 라우팅만 변경한다.
 */
@Singleton
class AppPreferencesDataSource @Inject constructor(
    private val context: Context,
    private val encryptedPrefs: EncryptedAppPreferences
) : AppSettingsRepository {
    companion object {
        private val CAMERA_CONTROLS_ENABLED = booleanPreferencesKey("camera_controls_enabled")
        private val LIVE_VIEW_ENABLED = booleanPreferencesKey("live_view_enabled")
        private val THEME_MODE = intPreferencesKey("theme_mode")
        private val DARK_MODE_ENABLED = booleanPreferencesKey("dark_mode_enabled")
        private val AUTO_START_EVENT_LISTENER = booleanPreferencesKey("auto_start_event_listener")
        private val SHOW_LATEST_PHOTO_WHEN_DISABLED =
            booleanPreferencesKey("show_latest_photo_when_disabled")
        private val COLOR_TRANSFER_ENABLED = booleanPreferencesKey("color_transfer_enabled")
        private val COLOR_TRANSFER_REFERENCE_IMAGE_PATH =
            stringPreferencesKey("color_transfer_reference_image_path")
        private val COLOR_TRANSFER_TARGET_IMAGE_PATH =
            stringPreferencesKey("color_transfer_target_image_path")
        private val COLOR_TRANSFER_INTENSITY = floatPreferencesKey("color_transfer_intensity")

        // 평문 DataStore에서 암호화 저장소로 이관 중인 레거시 키 (read-only, 마이그레이션 후 삭제).
        private val LEGACY_RAW_FILE_DOWNLOAD_ENABLED =
            booleanPreferencesKey("raw_file_download_enabled")
        private val LEGACY_SUBSCRIPTION_TIER = stringPreferencesKey("subscription_tier")

        private val ADMIN_NATIVE_LOG_STREAM_ENABLED =
            booleanPreferencesKey("admin_native_log_stream_enabled")
        private val NATIVE_LOG_CAPTURE_ENABLED =
            booleanPreferencesKey("native_log_capture_enabled")

        // Capture UX (Group 3)
        private val SHUTTER_SOUND_ENABLED = booleanPreferencesKey("shutter_sound_enabled")
        private val VIBRATE_ON_PHOTO_RECEIVED =
            booleanPreferencesKey("vibrate_on_photo_received")
        private val LIVE_VIEW_GRID_ENABLED = booleanPreferencesKey("live_view_grid_enabled")
        private val LIVE_VIEW_QUALITY = intPreferencesKey("live_view_quality")
        private val HAS_SEEN_CAPTURE_COACHMARK =
            booleanPreferencesKey("has_seen_capture_coachmark")
        private val LAST_TIMELAPSE_INTERVAL = intPreferencesKey("last_timelapse_interval")
        private val LAST_TIMELAPSE_COUNT = intPreferencesKey("last_timelapse_count")

        // Onboarding (Group 1)
        private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")

        // Live View Overlays (Group 7)
        private val HISTOGRAM_ENABLED = booleanPreferencesKey("liveview_histogram_enabled")
        private val FOCUS_PEAKING_ENABLED = booleanPreferencesKey("liveview_focus_peaking_enabled")

        // Film Simulation (Group 8)
        private val FILM_SIMULATION_ENABLED = booleanPreferencesKey("film_simulation_enabled")
        private val SELECTED_FILM_LUT_ID = stringPreferencesKey("selected_film_lut_id")
        private val FILM_SIMULATION_INTENSITY = floatPreferencesKey("film_simulation_intensity")
        private val FAVORITE_FILM_LUT_IDS = stringSetPreferencesKey("favorite_film_lut_ids")
    }

    /**
     * 첫 사용자 온보딩 완료 여부 (기본값: false).
     * 신규 사용자에게 USB / Wi-Fi / 권한 안내 3-스텝 페이저를 1회 보여주기 위한 플래그.
     */
    override val isOnboardingCompleted: Flow<Boolean> = context.appDataStore.data
        .map { preferences ->
            preferences[ONBOARDING_COMPLETED] ?: false
        }

    /**
     * 온보딩 완료 표시 — true 로 저장하면 다음 실행부터 페이저를 건너뛴다.
     */
    override suspend fun setOnboardingCompleted(completed: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETED] = completed
        }
    }

    /**
     * 평문 DataStore에 남아있는 레거시 민감 플래그를 암호화 저장소로 1회 이관한다.
     *
     * - 이미 암호화 저장소에 키가 있으면 평문 값은 무시한다.
     * - 이관 후 평문 키를 즉시 제거하여 디스크에 잔존하지 않도록 한다.
     */
    private suspend fun migrateSensitiveFlagsIfNeeded() {
        val snapshot = context.appDataStore.data.first()
        val legacyTier = snapshot[LEGACY_SUBSCRIPTION_TIER]
        val legacyRaw = snapshot[LEGACY_RAW_FILE_DOWNLOAD_ENABLED]
        if (legacyTier == null && legacyRaw == null) return

        if (legacyTier != null && !encryptedPrefs.hasSubscriptionTier()) {
            encryptedPrefs.setSubscriptionTierString(legacyTier)
        }
        if (legacyRaw != null && !encryptedPrefs.hasRawFileDownloadEnabled()) {
            encryptedPrefs.setRawFileDownloadEnabled(legacyRaw)
        }
        context.appDataStore.edit { prefs ->
            prefs.remove(LEGACY_SUBSCRIPTION_TIER)
            prefs.remove(LEGACY_RAW_FILE_DOWNLOAD_ENABLED)
        }
    }

    /**
     * 카메라 컨트롤 표시 여부 (기본값: false)
     */
    override val isCameraControlsEnabled: Flow<Boolean> = context.appDataStore.data
        .map { preferences ->
            preferences[CAMERA_CONTROLS_ENABLED] ?: false
        }

    /**
     * 라이브뷰 표시 여부 (기본값: false - ADMIN 티어에서만 활성화 가능)
     */
    override val isLiveViewEnabled: Flow<Boolean> = context.appDataStore.data
        .map { preferences ->
            preferences[LIVE_VIEW_ENABLED] ?: false
        }

    /**
     * 테마 모드 설정 (기본값: FOLLOW_SYSTEM - 기기 설정 따름)
     */
    override val themeMode: Flow<ThemeMode> = context.appDataStore.data
        .map { preferences ->
            val modeValue = preferences[THEME_MODE] ?: ThemeMode.FOLLOW_SYSTEM.value
            ThemeMode.values().find { it.value == modeValue } ?: ThemeMode.FOLLOW_SYSTEM
        }

    /**
     * 다크 모드 활성화 여부 (기존 호환성을 위해 유지, themeMode 기반으로 계산)
     */
    override val isDarkModeEnabled: Flow<Boolean> = themeMode
        .map { mode ->
            when (mode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.FOLLOW_SYSTEM -> false // 시스템 설정은 Theme.kt에서 처리됨
            }
        }

    /**
     * 카메라 제어 탭 진입 시 자동으로 이벤트 리스너 시작 여부 (기본값: true)
     */
    override val isAutoStartEventListenerEnabled: Flow<Boolean> = context.appDataStore.data
        .map { preferences ->
            preferences[AUTO_START_EVENT_LISTENER] ?: true
        }

    /**
     * 카메라 컨트롤이 비활성화된 경우 최신 사진 표시 여부 (기본값: true)
     */
    override val isShowLatestPhotoWhenDisabled: Flow<Boolean> = context.appDataStore.data
        .map { preferences ->
            preferences[SHOW_LATEST_PHOTO_WHEN_DISABLED] ?: true
        }

    /**
     * 색감 전송 기능 활성화 여부 (기본값: false)
     */
    override val isColorTransferEnabled: Flow<Boolean> = context.appDataStore.data
        .map { preferences ->
            preferences[COLOR_TRANSFER_ENABLED] ?: false
        }

    /**
     * 색감 전송 참조 이미지 경로 (기본값: null)
     */
    override val colorTransferReferenceImagePath: Flow<String?> = context.appDataStore.data
        .map { preferences ->
            preferences[COLOR_TRANSFER_REFERENCE_IMAGE_PATH]
        }

    /**
     * 색감 전송 대상 이미지 경로 (기본값: null)
     */
    override val colorTransferTargetImagePath: Flow<String?> = context.appDataStore.data
        .map { preferences ->
            preferences[COLOR_TRANSFER_TARGET_IMAGE_PATH]
        }

    /**
     * 색감 전송 강도 (기본값: 0.03)
     */
    override val colorTransferIntensity: Flow<Float> = context.appDataStore.data
        .map { preferences ->
            preferences[COLOR_TRANSFER_INTENSITY] ?: 0.03f
        }

    /**
     * RAW 파일 다운로드 활성화 여부 (기본값: true).
     * 암호화 저장소에서 읽는다. 레거시 평문 값이 있으면 1회 이관 후 반환.
     * EncryptedSharedPreferences는 변경 통지가 없어 setter 갱신을 구독자에게
     * 재방출하기 위해 MutableStateFlow를 경유한다(최초 구독 시 디스크 값 시드).
     */
    private val rawFileDownloadState = MutableStateFlow<Boolean?>(null)

    override val isRawFileDownloadEnabled: Flow<Boolean> = flow {
        migrateSensitiveFlagsIfNeeded()
        rawFileDownloadState.compareAndSet(
            expect = null,
            update = encryptedPrefs.getRawFileDownloadEnabled(default = true)
        )
        emitAll(rawFileDownloadState.filterNotNull())
    }

    /**
     * 구독 티어 (기본값: null).
     * 암호화 저장소에서 읽는다. 레거시 평문 값이 있으면 1회 이관 후 반환.
     */
    val subscriptionTier: Flow<String?> = flow {
        migrateSensitiveFlagsIfNeeded()
        emit(encryptedPrefs.getSubscriptionTierString())
    }

    /**
     * ADMIN 네이티브 로그 스트리밍 활성화 여부 (기본값: false)
     */
    val isAdminNativeLogStreamingEnabled: Flow<Boolean> = context.appDataStore.data
        .map { preferences ->
            preferences[ADMIN_NATIVE_LOG_STREAM_ENABLED] ?: false
        }

    /**
     * 네이티브 로그 캡처 활성화 여부 (기본값: 디버그 빌드 true, 릴리스 false)
     */
    override val isNativeLogCaptureEnabled: Flow<Boolean> = context.appDataStore.data
        .map { preferences ->
            preferences[NATIVE_LOG_CAPTURE_ENABLED] ?: BuildConfig.DEBUG
        }

    /**
     * 셔터 사운드 사용 여부 (기본값: true)
     */
    override val isShutterSoundEnabled: Flow<Boolean> = context.appDataStore.data
        .map { preferences ->
            preferences[SHUTTER_SOUND_ENABLED] ?: true
        }

    /**
     * 사진 도착 시 진동 알림 사용 여부 (기본값: false)
     */
    override val isVibrateOnPhotoReceivedEnabled: Flow<Boolean> = context.appDataStore.data
        .map { preferences ->
            preferences[VIBRATE_ON_PHOTO_RECEIVED] ?: false
        }

    /**
     * 라이브뷰 그리드 오버레이 사용 여부 (기본값: false)
     */
    override val isLiveViewGridEnabled: Flow<Boolean> = context.appDataStore.data
        .map { preferences ->
            preferences[LIVE_VIEW_GRID_ENABLED] ?: false
        }

    /**
     * 라이브뷰 화질 (기본값: QUALITY — liveviewsize 최대)
     */
    override val liveViewQuality: Flow<LiveViewQuality> = context.appDataStore.data
        .map { preferences ->
            val v = preferences[LIVE_VIEW_QUALITY] ?: LiveViewQuality.QUALITY.value
            LiveViewQuality.fromValue(v)
        }

    /**
     * 촬영 화면 첫 진입 코치마크 표시 완료 여부 (기본값: false)
     */
    override val hasSeenCaptureCoachmark: Flow<Boolean> = context.appDataStore.data
        .map { preferences ->
            preferences[HAS_SEEN_CAPTURE_COACHMARK] ?: false
        }

    /**
     * 마지막 타임랩스 간격 (초) — 다이얼로그 prefill (기본값: 5)
     */
    override val lastTimelapseInterval: Flow<Int> = context.appDataStore.data
        .map { preferences ->
            preferences[LAST_TIMELAPSE_INTERVAL] ?: 5
        }

    /**
     * 마지막 타임랩스 총 컷 수 — 다이얼로그 prefill (기본값: 100)
     */
    override val lastTimelapseCount: Flow<Int> = context.appDataStore.data
        .map { preferences ->
            preferences[LAST_TIMELAPSE_COUNT] ?: 100
        }

    /**
     * 라이브뷰 히스토그램 오버레이 사용 여부 (기본값: false)
     */
    override val isHistogramEnabled: Flow<Boolean> = context.appDataStore.data
        .map { preferences ->
            preferences[HISTOGRAM_ENABLED] ?: false
        }

    /**
     * 라이브뷰 포커스 피킹 오버레이 사용 여부 (기본값: false)
     */
    override val isFocusPeakingEnabled: Flow<Boolean> = context.appDataStore.data
        .map { preferences ->
            preferences[FOCUS_PEAKING_ENABLED] ?: false
        }

    /**
     * 필름 시뮬레이션 기능 활성화 여부 (기본값: false)
     */
    override val isFilmSimulationEnabled: Flow<Boolean> = context.appDataStore.data
        .map { preferences ->
            preferences[FILM_SIMULATION_ENABLED] ?: false
        }

    /**
     * 선택된 필름 LUT id (assets 상대 경로). 기본값: "" (선택 없음)
     */
    override val selectedFilmLutId: Flow<String> = context.appDataStore.data
        .map { preferences ->
            preferences[SELECTED_FILM_LUT_ID] ?: ""
        }

    /**
     * 필름 시뮬레이션 강도 (기본값: 1.0 — 필름 룩 완전 적용)
     */
    override val filmSimulationIntensity: Flow<Float> = context.appDataStore.data
        .map { preferences ->
            preferences[FILM_SIMULATION_INTENSITY] ?: 1.0f
        }

    /**
     * 즐겨찾기한 필름 LUT id 집합 (기본값: 빈 집합)
     */
    override val favoriteFilmLutIds: Flow<Set<String>> = context.appDataStore.data
        .map { preferences ->
            preferences[FAVORITE_FILM_LUT_IDS] ?: emptySet()
        }

    /**
     * 구독 티어 (SubscriptionTier enum으로 변환, 기본값: FREE)
     */
    override val subscriptionTierEnum: Flow<com.inik.camcon.domain.model.SubscriptionTier> = subscriptionTier
        .map { tierName ->
            if (tierName != null) {
                try {
                    com.inik.camcon.domain.model.SubscriptionTier.valueOf(tierName)
                } catch (e: IllegalArgumentException) {
                    com.inik.camcon.domain.model.SubscriptionTier.FREE
                }
            } else {
                com.inik.camcon.domain.model.SubscriptionTier.FREE
            }
        }

    /**
     * 카메라 컨트롤 표시 여부 설정
     */
    override suspend fun setCameraControlsEnabled(enabled: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[CAMERA_CONTROLS_ENABLED] = enabled
        }
    }

    /**
     * 라이브뷰 표시 여부 설정
     */
    override suspend fun setLiveViewEnabled(enabled: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[LIVE_VIEW_ENABLED] = enabled
        }
    }

    /**
     * 다크 모드 활성화/비활성화 (기존 호환성을 위해 유지)
     */
    override suspend fun setDarkModeEnabled(enabled: Boolean) {
        val newMode = if (enabled) ThemeMode.DARK else ThemeMode.LIGHT
        setThemeMode(newMode)
    }

    /**
     * 자동 이벤트 리스너 시작 활성화/비활성화
     */
    override suspend fun setAutoStartEventListenerEnabled(enabled: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[AUTO_START_EVENT_LISTENER] = enabled
        }
    }

    /**
     * 비활성화 시 최신 사진 표시 활성화/비활성화
     */
    override suspend fun setShowLatestPhotoWhenDisabled(enabled: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[SHOW_LATEST_PHOTO_WHEN_DISABLED] = enabled
        }
    }

    /**
     * 색감 전송 기능 활성화/비활성화
     */
    override suspend fun setColorTransferEnabled(enabled: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[COLOR_TRANSFER_ENABLED] = enabled
        }
    }

    /**
     * 색감 전송 참조 이미지 경로 설정
     */
    override suspend fun setColorTransferReferenceImagePath(path: String?) {
        context.appDataStore.edit { preferences ->
            if (path != null) {
                preferences[COLOR_TRANSFER_REFERENCE_IMAGE_PATH] = path
            } else {
                preferences.remove(COLOR_TRANSFER_REFERENCE_IMAGE_PATH)
            }
        }
    }

    /**
     * 색감 전송 대상 이미지 경로 설정
     */
    override suspend fun setColorTransferTargetImagePath(path: String?) {
        context.appDataStore.edit { preferences ->
            if (path != null) {
                preferences[COLOR_TRANSFER_TARGET_IMAGE_PATH] = path
            } else {
                preferences.remove(COLOR_TRANSFER_TARGET_IMAGE_PATH)
            }
        }
    }

    /**
     * 색감 전송 강도 설정 (0.0 ~ 1.0)
     */
    override suspend fun setColorTransferIntensity(intensity: Float) {
        context.appDataStore.edit { preferences ->
            preferences[COLOR_TRANSFER_INTENSITY] = intensity.coerceIn(0.0f, 1.0f)
        }
    }

    /**
     * 필름 시뮬레이션 기능 활성화/비활성화
     */
    override suspend fun setFilmSimulationEnabled(enabled: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[FILM_SIMULATION_ENABLED] = enabled
        }
    }

    /**
     * 선택된 필름 LUT id 설정 (빈 문자열 = 선택 해제)
     */
    override suspend fun setSelectedFilmLutId(id: String) {
        context.appDataStore.edit { preferences ->
            preferences[SELECTED_FILM_LUT_ID] = id
        }
    }

    /**
     * 필름 시뮬레이션 강도 설정 (0.0 ~ 1.0)
     */
    override suspend fun setFilmSimulationIntensity(intensity: Float) {
        context.appDataStore.edit { preferences ->
            preferences[FILM_SIMULATION_INTENSITY] = intensity.coerceIn(0.0f, 1.0f)
        }
    }

    /**
     * 필름 LUT 즐겨찾기 토글 — 있으면 제거, 없으면 추가.
     * DataStore `edit` 은 단일 트랜잭션이라 read-modify-write 가 원자적이다.
     */
    override suspend fun toggleFavoriteFilmLut(id: String) {
        if (id.isEmpty()) return
        context.appDataStore.edit { preferences ->
            val current = preferences[FAVORITE_FILM_LUT_IDS] ?: emptySet()
            preferences[FAVORITE_FILM_LUT_IDS] =
                if (id in current) current - id else current + id
        }
    }

    /**
     * RAW 파일 다운로드 활성화/비활성화 (암호화 저장소에 저장)
     */
    override suspend fun setRawFileDownloadEnabled(enabled: Boolean) {
        migrateSensitiveFlagsIfNeeded()
        encryptedPrefs.setRawFileDownloadEnabled(enabled)
        rawFileDownloadState.value = enabled
    }

    /**
     * ADMIN 네이티브 로그 스트리밍 활성화/비활성화
     */
    suspend fun setAdminNativeLogStreamingEnabled(enabled: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[ADMIN_NATIVE_LOG_STREAM_ENABLED] = enabled
        }
    }

    /**
     * 네이티브 로그 캡처 활성화/비활성화
     */
    override suspend fun setNativeLogCaptureEnabled(enabled: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[NATIVE_LOG_CAPTURE_ENABLED] = enabled
        }
    }

    /**
     * 셔터 사운드 사용 여부 저장.
     */
    override suspend fun setShutterSoundEnabled(enabled: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[SHUTTER_SOUND_ENABLED] = enabled
        }
    }

    /**
     * 사진 도착 시 진동 알림 사용 여부 저장.
     */
    override suspend fun setVibrateOnPhotoReceivedEnabled(enabled: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[VIBRATE_ON_PHOTO_RECEIVED] = enabled
        }
    }

    /**
     * 라이브뷰 그리드 오버레이 사용 여부 저장.
     */
    override suspend fun setLiveViewGridEnabled(enabled: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[LIVE_VIEW_GRID_ENABLED] = enabled
        }
    }

    /**
     * 라이브뷰 화질 저장.
     */
    override suspend fun setLiveViewQuality(quality: LiveViewQuality) {
        context.appDataStore.edit { preferences ->
            preferences[LIVE_VIEW_QUALITY] = quality.value
        }
    }

    /**
     * 촬영 코치마크 표시 완료 플래그 저장.
     */
    override suspend fun setHasSeenCaptureCoachmark(seen: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[HAS_SEEN_CAPTURE_COACHMARK] = seen
        }
    }

    /**
     * 마지막 타임랩스 간격(초) 저장.
     */
    override suspend fun setLastTimelapseInterval(seconds: Int) {
        context.appDataStore.edit { preferences ->
            preferences[LAST_TIMELAPSE_INTERVAL] = seconds.coerceAtLeast(1)
        }
    }

    /**
     * 마지막 타임랩스 총 컷 수 저장.
     */
    override suspend fun setLastTimelapseCount(count: Int) {
        context.appDataStore.edit { preferences ->
            preferences[LAST_TIMELAPSE_COUNT] = count.coerceAtLeast(1)
        }
    }

    /**
     * 라이브뷰 히스토그램 오버레이 활성화/비활성화.
     */
    override suspend fun setHistogramEnabled(enabled: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[HISTOGRAM_ENABLED] = enabled
        }
    }

    /**
     * 라이브뷰 포커스 피킹 오버레이 활성화/비활성화.
     */
    override suspend fun setFocusPeakingEnabled(enabled: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[FOCUS_PEAKING_ENABLED] = enabled
        }
    }

    /**
     * 테마 모드 설정
     */
    override suspend fun setThemeMode(mode: ThemeMode) {
        context.appDataStore.edit { preferences ->
            preferences[THEME_MODE] = mode.value
        }
    }

    /**
     * 구독 티어 설정 (String) — 암호화 저장소에 저장
     */
    suspend fun setSubscriptionTier(tier: String?) {
        migrateSensitiveFlagsIfNeeded()
        encryptedPrefs.setSubscriptionTierString(tier)
    }

    /**
     * SubscriptionTier enum으로 구독 티어 저장 (헬퍼 메서드)
     *
     * authoritative=false(기본)일 때만 "상위 티어를 FREE로 덮어쓰지 않음" 가드를 적용한다.
     * 권위있는 출처(서버/Play 확인)에서 온 FREE 강등은 가드를 우회해 실제 강등을 반영한다
     * (만료/환불 후 PRO 영구 잔존 방지 — H10/H11).
     */
    override suspend fun saveSubscriptionTier(
        tier: com.inik.camcon.domain.model.SubscriptionTier?,
        authoritative: Boolean
    ) {
        if (tier == null) {
            setSubscriptionTier(null)
            return
        }

        if (!authoritative) {
            val currentTier = subscriptionTierEnum.first()
            if (tier == com.inik.camcon.domain.model.SubscriptionTier.FREE &&
                currentTier != com.inik.camcon.domain.model.SubscriptionTier.FREE
            ) {
                // 비권위(오프라인 폴백) FREE 로는 유지 중인 상위 티어를 덮어쓰지 않음
                return
            }
        }

        setSubscriptionTier(tier.name)
    }

    /**
     * 모든 앱 설정 초기화 (기본값으로 되돌림).
     * 평문 DataStore와 암호화 저장소 양쪽을 함께 비운다.
     */
    override suspend fun clearAllSettings() {
        context.appDataStore.edit { preferences ->
            preferences.clear()
        }
        encryptedPrefs.setSubscriptionTierString(null)
        // RAW 플래그는 명시적 기본값(true)이 있으므로 명시적으로 다시 기록한다.
        encryptedPrefs.setRawFileDownloadEnabled(true)
        rawFileDownloadState.value = true
    }
}
