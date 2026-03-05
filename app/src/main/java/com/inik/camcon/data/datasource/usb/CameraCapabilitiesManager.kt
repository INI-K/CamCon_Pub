package com.inik.camcon.data.datasource.usb
import android.util.Log
import com.inik.camcon.CameraNative
import com.inik.camcon.domain.model.CameraCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class CameraCapabilitiesManager @Inject constructor(
) {
    companion object {
        private const val TAG = "CameraCapabilitiesManager"
    }
    private val _cameraCapabilities = MutableStateFlow<CameraCapabilities?>(null)
    val cameraCapabilities: StateFlow<CameraCapabilities?> = _cameraCapabilities.asStateFlow()
    private var cachedSummary: String = ""
    fun refreshCameraCapabilities() {
        val parsed = parseCapabilities(CameraNative.getCameraSummary())
        _cameraCapabilities.value = parsed
    }
    fun isLiveViewSupported(): Boolean =
        _cameraCapabilities.value?.canLiveView ?: false
    fun hasCapability(capability: String): Boolean {
        val c = _cameraCapabilities.value ?: return false
        return when (capability.lowercase()) {
            "liveview" -> c.canLiveView
            "capture" -> c.canCapturePhoto
            "autofocus" -> c.supportsAutofocus
            else -> false
        }
    }
    fun buildWidgetJsonFromMaster(): String {
        return runCatching { CameraNative.buildWidgetJson() }.getOrDefault("")
    }
    fun getCameraAbilitiesFromMaster(): String {
        return runCatching { CameraNative.listCameraAbilities() }.getOrDefault("")
    }
    fun getCachedOrFetchSummary(): String {
        if (cachedSummary.isNotEmpty()) return cachedSummary
        cachedSummary = runCatching { CameraNative.getCameraSummary() }.getOrDefault("")
        return cachedSummary
    }
    fun reset() {
        _cameraCapabilities.value = null
        cachedSummary = ""
    }
    private fun parseCapabilities(summary: String): CameraCapabilities? {
        cachedSummary = summary
        return try {
            val json = JSONObject(summary)
            CameraCapabilities(
                model = json.optString("model", "Unknown"),
                canCapturePhoto = true,
                canCaptureVideo = false,
                canLiveView = json.optBoolean("supportsLiveView", false),
                canTriggerCapture = true,
                supportsBurstMode = false,
                supportsTimelapse = false,
                supportsBracketing = false,
                supportsBulbMode = false,
                supportsAutofocus = true,
                supportsManualFocus = true,
                supportsFocusPoint = false,
                canDownloadFiles = true,
                canDeleteFiles = false,
                canPreviewFiles = true,
                availableIsoSettings = emptyList(),
                availableShutterSpeeds = emptyList(),
                availableApertures = emptyList(),
                availableWhiteBalanceSettings = emptyList(),
                supportsRemoteControl = true,
                supportsConfigChange = true
            )
        } catch (e: Exception) {
            Log.w(TAG, "카메라 기능 파싱 실패", e)
            null
        }
    }
}