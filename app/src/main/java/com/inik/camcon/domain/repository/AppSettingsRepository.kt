package com.inik.camcon.domain.repository

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
    suspend fun clearAllSettings()
}
