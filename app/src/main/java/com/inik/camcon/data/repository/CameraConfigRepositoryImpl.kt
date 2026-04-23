package com.inik.camcon.data.repository

import com.inik.camcon.data.datasource.nativesource.NativeConfigDataSource
import com.inik.camcon.domain.model.config.CameraConfigTree
import com.inik.camcon.domain.model.config.ConfigRange
import com.inik.camcon.domain.model.config.ConfigWidget
import com.inik.camcon.domain.model.config.ConfigWidgetType
import com.inik.camcon.domain.model.config.ManufacturerSetting
import com.inik.camcon.domain.model.config.ManufacturerSettingQuery
import com.inik.camcon.domain.repository.CameraConfigRepository
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraConfigRepositoryImpl @Inject constructor(
    private val nativeDataSource: NativeConfigDataSource
) : CameraConfigRepository {

    override suspend fun getConfigTree(): Result<CameraConfigTree> = runCatching {
        val json = nativeDataSource.buildWidgetJson()
        parseConfigTree(json)
    }

    override suspend fun listAllConfigs(): Result<List<String>> = runCatching {
        val json = nativeDataSource.listAllConfigs()
        val obj = JSONObject(json)
        val arr = obj.getJSONArray("configs")
        (0 until arr.length()).map { arr.getString(it) }
    }

    override suspend fun getConfigValue(key: String): Result<String> = runCatching {
        nativeDataSource.getConfigString(key)
            ?: throw IllegalStateException("Config value not found: $key")
    }

    override suspend fun setConfigValue(key: String, value: String): Result<Boolean> = runCatching {
        nativeDataSource.setConfigString(key, value) == 0
    }

    override suspend fun getConfigInt(key: String): Result<Int> = runCatching {
        nativeDataSource.getConfigInt(key)
    }

    override suspend fun setConfigInt(key: String, value: Int): Result<Boolean> = runCatching {
        nativeDataSource.setConfigInt(key, value) == 0
    }

    override suspend fun getCameraManual(): Result<String> = runCatching {
        nativeDataSource.getCameraManual()
    }

    override suspend fun getCameraAbout(): Result<String> = runCatching {
        // Will be implemented when C++ JNI binding for getCameraAbout is added
        throw UnsupportedOperationException("getCameraAbout C++ binding not yet implemented")
    }

    override suspend fun readGphotoSettings(): Result<String> = runCatching {
        nativeDataSource.readGphotoSettings()
    }

    override suspend fun setManufacturerSetting(setting: ManufacturerSetting): Result<Boolean> = runCatching {
        val result = when (setting) {
            is ManufacturerSetting.CanonColorTemperature -> nativeDataSource.setCanonColorTemperature(setting.value.toInt())
            is ManufacturerSetting.CanonPictureStyle -> nativeDataSource.setCanonPictureStyle(setting.value)
            is ManufacturerSetting.CanonWhiteBalanceAdjust -> {
                val parts = setting.value.split(",")
                nativeDataSource.setCanonWhiteBalanceAdjust(parts[0].toInt(), parts[1].toInt())
            }
            is ManufacturerSetting.NikonActiveSlot -> nativeDataSource.setNikonActiveSlot(setting.value)
            is ManufacturerSetting.NikonVideoMode -> nativeDataSource.setNikonVideoMode(setting.value == "1")
            is ManufacturerSetting.NikonExposureDelayMode -> nativeDataSource.setNikonExposureDelayMode(setting.value == "1")
            is ManufacturerSetting.SonyFocusArea -> nativeDataSource.setSonyFocusArea(setting.value)
            is ManufacturerSetting.SonyLiveViewEffect -> nativeDataSource.setSonyLiveViewEffect(setting.value == "1")
            is ManufacturerSetting.SonyManualFocusing -> nativeDataSource.setSonyManualFocusing(setting.value.toInt())
            is ManufacturerSetting.FujiFilmSimulation -> nativeDataSource.setFujiFilmSimulation(setting.value)
            is ManufacturerSetting.FujiColorSpace -> nativeDataSource.setFujiColorSpace(setting.value)
            is ManufacturerSetting.PanasonicMovieRecording -> nativeDataSource.setPanasonicMovieRecording(setting.value == "1")
            is ManufacturerSetting.PanasonicManualFocusDrive -> nativeDataSource.setPanasonicManualFocusDrive(setting.value.toInt())
        }
        result == 0
    }

    override suspend fun getManufacturerSetting(query: ManufacturerSettingQuery): Result<String> = runCatching {
        when (query.key) {
            "colortemperature" -> nativeDataSource.getCanonColorTemperature().toString()
            "batterylevel" -> nativeDataSource.getNikonBatteryLevel().toString()
            "shuttercounter" -> nativeDataSource.getFujiShutterCounter().toString()
            else -> nativeDataSource.getConfigString(query.key)
                ?: throw IllegalStateException("Manufacturer setting not found: ${query.key}")
        }
    }

    private fun parseConfigTree(json: String): CameraConfigTree {
        val obj = JSONObject(json)
        val widgets = parseWidgetChildren(obj)
        return CameraConfigTree(widgets = widgets)
    }

    private fun parseWidgetChildren(obj: JSONObject): List<ConfigWidget> {
        val children = obj.optJSONArray("children") ?: return emptyList()
        return (0 until children.length()).map { parseWidget(children.getJSONObject(it)) }
    }

    private fun parseWidget(obj: JSONObject): ConfigWidget {
        return ConfigWidget(
            name = obj.optString("name", ""),
            label = obj.optString("label", ""),
            type = parseWidgetType(obj.optString("type", "text")),
            value = obj.optString("value", null),
            choices = parseChoices(obj.optJSONArray("choices")),
            range = parseRange(obj),
            readonly = obj.optBoolean("readonly", false),
            changed = obj.optBoolean("changed", false),
            info = obj.optString("info", null),
            children = parseWidgetChildren(obj)
        )
    }

    private fun parseWidgetType(type: String): ConfigWidgetType = when (type.lowercase()) {
        "window" -> ConfigWidgetType.WINDOW
        "section" -> ConfigWidgetType.SECTION
        "text" -> ConfigWidgetType.TEXT
        "range" -> ConfigWidgetType.RANGE
        "toggle" -> ConfigWidgetType.TOGGLE
        "radio" -> ConfigWidgetType.RADIO
        "menu" -> ConfigWidgetType.MENU
        "button" -> ConfigWidgetType.BUTTON
        "date" -> ConfigWidgetType.DATE
        else -> ConfigWidgetType.TEXT
    }

    private fun parseChoices(arr: JSONArray?): List<String> {
        arr ?: return emptyList()
        return (0 until arr.length()).map { arr.getString(it) }
    }

    private fun parseRange(obj: JSONObject): ConfigRange? {
        if (!obj.has("min") || !obj.has("max")) return null
        return ConfigRange(
            min = obj.optDouble("min", 0.0).toFloat(),
            max = obj.optDouble("max", 0.0).toFloat(),
            step = obj.optDouble("step", 1.0).toFloat()
        )
    }
}
