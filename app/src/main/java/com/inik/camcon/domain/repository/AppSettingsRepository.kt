package com.inik.camcon.domain.repository

import com.inik.camcon.domain.model.LiveViewQuality
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/**
 * 앱 설정에 대한 Repository 인터페이스.
 * Domain 레이어에서 Data 레이어 직접 참조 없이 설정에 접근한다.
 */
interface AppSettingsRepository {

    // === 읽기 전용 Flow ===
    val isRawFileDownloadEnabled: Flow<Boolean>
    val isCameraControlsEnabled: Flow<Boolean>
    val isLiveViewEnabled: Flow<Boolean>
    val isDarkModeEnabled: Flow<Boolean>
    val isAutoStartEventListenerEnabled: Flow<Boolean>
    val isShowLatestPhotoWhenDisabled: Flow<Boolean>
    val isColorTransferEnabled: Flow<Boolean>
    val colorTransferReferenceImagePath: Flow<String?>
    val colorTransferTargetImagePath: Flow<String?>
    val colorTransferIntensity: Flow<Float>
    val subscriptionTierEnum: Flow<SubscriptionTier>
    val themeMode: Flow<ThemeMode>
    val isNativeLogCaptureEnabled: Flow<Boolean>
    val isOnboardingCompleted: Flow<Boolean>

    // === Capture UX (Group 3) ===
    val isShutterSoundEnabled: Flow<Boolean>
    val isLiveViewGridEnabled: Flow<Boolean>
    val liveViewQuality: Flow<LiveViewQuality>
    val hasSeenCaptureCoachmark: Flow<Boolean>
    val lastTimelapseInterval: Flow<Int>
    val lastTimelapseCount: Flow<Int>

    // === Live View Overlays (Group 7) ===
    val isHistogramEnabled: Flow<Boolean>
    val isFocusPeakingEnabled: Flow<Boolean>

    // === Film Simulation (Group 8) ===
    val isFilmSimulationEnabled: Flow<Boolean>
    val selectedFilmLutId: Flow<String>
    val filmSimulationIntensity: Flow<Float>

    /** 즐겨찾기한 필름 LUT id 집합(기본값: 빈 집합). */
    val favoriteFilmLutIds: Flow<Set<String>>

    // === 쓰기 ===
    suspend fun setCameraControlsEnabled(enabled: Boolean)
    suspend fun setLiveViewEnabled(enabled: Boolean)
    suspend fun setDarkModeEnabled(enabled: Boolean)
    suspend fun setAutoStartEventListenerEnabled(enabled: Boolean)
    suspend fun setShowLatestPhotoWhenDisabled(enabled: Boolean)
    suspend fun setColorTransferEnabled(enabled: Boolean)
    suspend fun setColorTransferReferenceImagePath(path: String?)
    suspend fun setColorTransferTargetImagePath(path: String?)
    suspend fun setColorTransferIntensity(intensity: Float)
    suspend fun setRawFileDownloadEnabled(enabled: Boolean)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setNativeLogCaptureEnabled(enabled: Boolean)
    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun setShutterSoundEnabled(enabled: Boolean)
    suspend fun setLiveViewGridEnabled(enabled: Boolean)
    suspend fun setLiveViewQuality(quality: LiveViewQuality)
    suspend fun setHasSeenCaptureCoachmark(seen: Boolean)
    suspend fun setLastTimelapseInterval(seconds: Int)
    suspend fun setLastTimelapseCount(count: Int)
    suspend fun setHistogramEnabled(enabled: Boolean)
    suspend fun setFocusPeakingEnabled(enabled: Boolean)
    suspend fun setFilmSimulationEnabled(enabled: Boolean)
    suspend fun setSelectedFilmLutId(id: String)
    suspend fun setFilmSimulationIntensity(intensity: Float)

    /** 주어진 LUT id 의 즐겨찾기 상태를 토글한다(있으면 제거, 없으면 추가). */
    suspend fun toggleFavoriteFilmLut(id: String)
    /**
     * 구독 티어를 영속 캐시에 저장한다.
     *
     * @param authoritative 권위있는 출처(온라인 서버/Play 확인)에서 온 값이면 true.
     *   - false(기본): 오프라인 폴백 등 비권위 경로. 유지 중인 상위 티어를 FREE로 덮어쓰지 않는다
     *     (부팅 race / 일시적 read 실패에서 게이팅이 FREE로 깜빡이는 것을 방지).
     *   - true: 서버가 확인한 실제 강등을 반영하기 위해 FREE 다운그레이드를 허용한다
     *     (만료/환불 후 PRO 영구 잔존 방지).
     */
    suspend fun saveSubscriptionTier(tier: SubscriptionTier?, authoritative: Boolean = false)
    suspend fun clearAllSettings()
}
