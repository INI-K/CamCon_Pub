package com.inik.camcon.domain.model.config

data class CameraConfigTree(
    val widgets: List<ConfigWidget>
)

data class ConfigWidget(
    val name: String,
    val label: String,
    val type: ConfigWidgetType,
    val value: String? = null,
    val choices: List<String> = emptyList(),
    val range: ConfigRange? = null,
    val readonly: Boolean = false,
    val changed: Boolean = false,
    val info: String? = null,
    val children: List<ConfigWidget> = emptyList()
)

data class ConfigRange(
    val min: Float,
    val max: Float,
    val step: Float
)

enum class ConfigWidgetType {
    WINDOW, SECTION, TEXT, RANGE, TOGGLE, RADIO, MENU, BUTTON, DATE
}

sealed class ManufacturerSetting(val key: String, val value: String) {
    // Canon
    class CanonColorTemperature(kelvin: Int) : ManufacturerSetting("colortemperature", kelvin.toString())
    class CanonPictureStyle(style: String) : ManufacturerSetting("picturestyle", style)
    class CanonWhiteBalanceAdjust(adjustBA: Int, adjustGM: Int) : ManufacturerSetting("whitebalanceadjusta", "$adjustBA,$adjustGM")

    // Nikon
    class NikonActiveSlot(slot: String) : ManufacturerSetting("activeslot", slot)
    class NikonVideoMode(enable: Boolean) : ManufacturerSetting("videomode", if (enable) "1" else "0")
    class NikonExposureDelayMode(enable: Boolean) : ManufacturerSetting("exposuredelaymode", if (enable) "1" else "0")

    // Sony
    class SonyFocusArea(area: String) : ManufacturerSetting("focusarea", area)
    class SonyLiveViewEffect(enable: Boolean) : ManufacturerSetting("liveviewsettingeffect", if (enable) "1" else "0")
    class SonyManualFocusing(steps: Int) : ManufacturerSetting("manualfocusing", steps.toString())

    // Fujifilm
    class FujiFilmSimulation(simulation: String) : ManufacturerSetting("filmsimulation", simulation)
    class FujiColorSpace(colorSpace: String) : ManufacturerSetting("colorspace", colorSpace)

    // Panasonic
    class PanasonicMovieRecording(enable: Boolean) : ManufacturerSetting("recording", if (enable) "1" else "0")
    class PanasonicManualFocusDrive(steps: Int) : ManufacturerSetting("mfadjust", steps.toString())
}

data class ManufacturerSettingQuery(val key: String)
