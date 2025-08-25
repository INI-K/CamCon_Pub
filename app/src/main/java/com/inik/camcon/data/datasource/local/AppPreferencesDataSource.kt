package com.inik.camcon.data.datasource.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

/**
 * 테마 모드 설정 옵션
 */
enum class ThemeMode(val value: Int) {
    FOLLOW_SYSTEM(0),   // 기기 설정 따름
    LIGHT(1),          // 라이트 모드
    DARK(2)            // 다크 모드
}

/**
 * 앱 설정 정보를 관리하는 DataSource
 */
@Singleton
class AppPreferencesDataSource @Inject constructor(
    private val context: Context
) {
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
    }

    /**
     * 카메라 컨트롤 표시 여부 (기본값: false)
     */
    val isCameraControlsEnabled: Flow<Boolean> = context.appDataStore.data
        .map { preferences ->
            preferences[CAMERA_CONTROLS_ENABLED] ?: false
        }

    /**
     * 라이브뷰 표시 여부 (기본값: false - ADMIN 티어에서만 활성화 가능)
     */
    val isLiveViewEnabled: Flow<Boolean> = context.appDataStore.data
        .map { preferences ->
            preferences[LIVE_VIEW_ENABLED] ?: false
        }

    /**
     * 테마 모드 설정 (기본값: FOLLOW_SYSTEM - 기기 설정 따름)
     */
    val themeMode: Flow<ThemeMode> = context.appDataStore.data
        .map { preferences ->
            val modeValue = preferences[THEME_MODE] ?: ThemeMode.FOLLOW_SYSTEM.value
            ThemeMode.values().find { it.value == modeValue } ?: ThemeMode.FOLLOW_SYSTEM
        }

    /**
     * 다크 모드 활성화 여부 (기존 호환성을 위해 유지, themeMode 기반으로 계산)
     */
    val isDarkModeEnabled: Flow<Boolean> = themeMode
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
    val isAutoStartEventListenerEnabled: Flow<Boolean> = context.appDataStore.data
        .map { preferences ->
            preferences[AUTO_START_EVENT_LISTENER] ?: true
        }

    /**
     * 카메라 컨트롤이 비활성화된 경우 최신 사진 표시 여부 (기본값: true)
     */
    val isShowLatestPhotoWhenDisabled: Flow<Boolean> = context.appDataStore.data
        .map { preferences ->
            preferences[SHOW_LATEST_PHOTO_WHEN_DISABLED] ?: true
        }

    /**
     * 색감 전송 기능 활성화 여부 (기본값: false)
     */
    val isColorTransferEnabled: Flow<Boolean> = context.appDataStore.data
        .map { preferences ->
            preferences[COLOR_TRANSFER_ENABLED] ?: false
        }

    /**
     * 색감 전송 참조 이미지 경로 (기본값: null)
     */
    val colorTransferReferenceImagePath: Flow<String?> = context.appDataStore.data
        .map { preferences ->
            preferences[COLOR_TRANSFER_REFERENCE_IMAGE_PATH]
        }

    /**
     * 색감 전송 대상 이미지 경로 (기본값: null)
     */
    val colorTransferTargetImagePath: Flow<String?> = context.appDataStore.data
        .map { preferences ->
            preferences[COLOR_TRANSFER_TARGET_IMAGE_PATH]
        }

    /**
     * 색감 전송 강도 (기본값: 0.03)
     */
    val colorTransferIntensity: Flow<Float> = context.appDataStore.data
        .map { preferences ->
            preferences[COLOR_TRANSFER_INTENSITY] ?: 0.03f
        }

    /**
     * 카메라 컨트롤 표시 여부 설정
     */
    suspend fun setCameraControlsEnabled(enabled: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[CAMERA_CONTROLS_ENABLED] = enabled
        }
    }

    /**
     * 라이브뷰 표시 여부 설정
     */
    suspend fun setLiveViewEnabled(enabled: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[LIVE_VIEW_ENABLED] = enabled
        }
    }

    /**
     * 다크 모드 활성화/비활성화 (기존 호환성을 위해 유지)
     */
    suspend fun setDarkModeEnabled(enabled: Boolean) {
        val newMode = if (enabled) ThemeMode.DARK else ThemeMode.LIGHT
        setThemeMode(newMode)
    }

    /**
     * 자동 이벤트 리스너 시작 활성화/비활성화
     */
    suspend fun setAutoStartEventListenerEnabled(enabled: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[AUTO_START_EVENT_LISTENER] = enabled
        }
    }

    /**
     * 비활성화 시 최신 사진 표시 활성화/비활성화
     */
    suspend fun setShowLatestPhotoWhenDisabled(enabled: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[SHOW_LATEST_PHOTO_WHEN_DISABLED] = enabled
        }
    }

    /**
     * 색감 전송 기능 활성화/비활성화
     */
    suspend fun setColorTransferEnabled(enabled: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[COLOR_TRANSFER_ENABLED] = enabled
        }
    }

    /**
     * 색감 전송 참조 이미지 경로 설정
     */
    suspend fun setColorTransferReferenceImagePath(path: String?) {
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
    suspend fun setColorTransferTargetImagePath(path: String?) {
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
    suspend fun setColorTransferIntensity(intensity: Float) {
        context.appDataStore.edit { preferences ->
            preferences[COLOR_TRANSFER_INTENSITY] = intensity.coerceIn(0.0f, 1.0f)
        }
    }

    /**
     * 테마 모드 설정
     */
    suspend fun setThemeMode(mode: ThemeMode) {
        context.appDataStore.edit { preferences ->
            preferences[THEME_MODE] = mode.value
        }
    }

    /**
     * 모든 앱 설정 초기화 (기본값으로 되돌림)
     */
    suspend fun clearAllSettings() {
        context.appDataStore.edit { preferences ->
            preferences.clear()
        }
    }
}