package com.inik.camcon.data.datasource.local
import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
private val Context.appPreferences by preferencesDataStore(name = "app_preferences")
@Singleton
class AppPreferencesDataSource @Inject constructor(
    private val context: Context
) {
    private object Keys {
        val CAMERA_CONTROLS_ENABLED = booleanPreferencesKey("camera_controls_enabled")
        val LIVE_VIEW_ENABLED = booleanPreferencesKey("live_view_enabled")
        val DARK_MODE_ENABLED = booleanPreferencesKey("dark_mode_enabled")
        val AUTO_START_EVENT_LISTENER = booleanPreferencesKey("auto_start_event_listener")
        val SHOW_LATEST_PHOTO_WHEN_DISABLED =
            booleanPreferencesKey("show_latest_photo_when_disabled")
        val COLOR_TRANSFER_ENABLED = booleanPreferencesKey("color_transfer_enabled")
        val COLOR_TRANSFER_REFERENCE_PATH = stringPreferencesKey("color_transfer_reference_path")
        val COLOR_TRANSFER_TARGET_PATH = stringPreferencesKey("color_transfer_target_path")
        val COLOR_TRANSFER_INTENSITY = floatPreferencesKey("color_transfer_intensity")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }
    val isCameraControlsEnabled: Flow<Boolean> = context.appPreferences.data.map {
        it[Keys.CAMERA_CONTROLS_ENABLED] ?: false
    }
    val isLiveViewEnabled: Flow<Boolean> = context.appPreferences.data.map {
        it[Keys.LIVE_VIEW_ENABLED] ?: false
    }
    val isDarkModeEnabled: Flow<Boolean> = context.appPreferences.data.map {
        it[Keys.DARK_MODE_ENABLED] ?: false
    }
    val isAutoStartEventListenerEnabled: Flow<Boolean> = context.appPreferences.data.map {
        it[Keys.AUTO_START_EVENT_LISTENER] ?: true
    }
    val isShowLatestPhotoWhenDisabled: Flow<Boolean> = context.appPreferences.data.map {
        it[Keys.SHOW_LATEST_PHOTO_WHEN_DISABLED] ?: true
    }
    val isColorTransferEnabled: Flow<Boolean> = context.appPreferences.data.map {
        it[Keys.COLOR_TRANSFER_ENABLED] ?: false
    }
    val colorTransferReferenceImagePath: Flow<String?> = context.appPreferences.data.map {
        it[Keys.COLOR_TRANSFER_REFERENCE_PATH]
    }
    val colorTransferTargetImagePath: Flow<String?> = context.appPreferences.data.map {
        it[Keys.COLOR_TRANSFER_TARGET_PATH]
    }
    val colorTransferIntensity: Flow<Float> = context.appPreferences.data.map {
        it[Keys.COLOR_TRANSFER_INTENSITY] ?: 0.03f
    }
    val themeMode: Flow<ThemeMode> = context.appPreferences.data.map { prefs ->
        prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.FOLLOW_SYSTEM
    }
    suspend fun setCameraControlsEnabled(enabled: Boolean) = setBoolean(Keys.CAMERA_CONTROLS_ENABLED, enabled)
    suspend fun setLiveViewEnabled(enabled: Boolean) = setBoolean(Keys.LIVE_VIEW_ENABLED, enabled)
    suspend fun setDarkModeEnabled(enabled: Boolean) = setBoolean(Keys.DARK_MODE_ENABLED, enabled)
    suspend fun setAutoStartEventListenerEnabled(enabled: Boolean) = setBoolean(Keys.AUTO_START_EVENT_LISTENER, enabled)
    suspend fun setShowLatestPhotoWhenDisabled(enabled: Boolean) = setBoolean(Keys.SHOW_LATEST_PHOTO_WHEN_DISABLED, enabled)
    suspend fun setColorTransferEnabled(enabled: Boolean) = setBoolean(Keys.COLOR_TRANSFER_ENABLED, enabled)
    suspend fun setColorTransferReferenceImagePath(path: String?) = setString(Keys.COLOR_TRANSFER_REFERENCE_PATH, path)
    suspend fun setColorTransferTargetImagePath(path: String?) = setString(Keys.COLOR_TRANSFER_TARGET_PATH, path)
    suspend fun setColorTransferIntensity(intensity: Float) {
        context.appPreferences.edit { it[Keys.COLOR_TRANSFER_INTENSITY] = intensity.coerceIn(0f, 1f) }
    }
    suspend fun setThemeMode(mode: ThemeMode) {
        context.appPreferences.edit { it[Keys.THEME_MODE] = mode.name }
    }
    suspend fun clearAllSettings() {
        context.appPreferences.edit { it.clear() }
    }
    private suspend fun setBoolean(key: Preferences.Key<Boolean>, value: Boolean) {
        context.appPreferences.edit { it[key] = value }
    }
    private suspend fun setString(key: Preferences.Key<String>, value: String?) {
        context.appPreferences.edit {
            if (value == null) it.remove(key) else it[key] = value
        }
    }
}