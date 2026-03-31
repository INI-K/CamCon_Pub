package com.inik.camcon.data.datasource.nativesource

import com.inik.camcon.CameraNative
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeConfigDataSource @Inject constructor() {

    fun buildWidgetJson(): String = CameraNative.buildWidgetJson()

    fun listAllConfigs(): String = CameraNative.listAllConfigs()

    fun getConfigString(key: String): String? = CameraNative.getConfigString(key)

    fun setConfigString(key: String, value: String): Int = CameraNative.setConfigString(key, value)

    fun getConfigInt(key: String): Int = CameraNative.getConfigInt(key)

    fun setConfigInt(key: String, value: Int): Int = CameraNative.setConfigInt(key, value)

    fun getCameraManual(): String = CameraNative.getCameraManual()

    fun readGphotoSettings(): String = CameraNative.readGphotoSettings()

    // Manufacturer-specific wrappers (convenience — internally use config system)
    fun setCanonColorTemperature(kelvin: Int): Int = CameraNative.setCanonColorTemperature(kelvin)
    fun getCanonColorTemperature(): Int = CameraNative.getCanonColorTemperature()
    fun setCanonPictureStyle(style: String): Int = CameraNative.setCanonPictureStyle(style)
    fun setCanonWhiteBalanceAdjust(adjustBA: Int, adjustGM: Int): Int = CameraNative.setCanonWhiteBalanceAdjust(adjustBA, adjustGM)

    fun setNikonActiveSlot(slot: String): Int = CameraNative.setNikonActiveSlot(slot)
    fun setNikonVideoMode(enable: Boolean): Int = CameraNative.setNikonVideoMode(enable)
    fun setNikonExposureDelayMode(enable: Boolean): Int = CameraNative.setNikonExposureDelayMode(enable)
    fun getNikonBatteryLevel(): Int = CameraNative.getNikonBatteryLevel()

    fun setSonyFocusArea(area: String): Int = CameraNative.setSonyFocusArea(area)
    fun setSonyLiveViewEffect(enable: Boolean): Int = CameraNative.setSonyLiveViewEffect(enable)
    fun setSonyManualFocusing(steps: Int): Int = CameraNative.setSonyManualFocusing(steps)

    fun setFujiFilmSimulation(simulation: String): Int = CameraNative.setFujiFilmSimulation(simulation)
    fun setFujiColorSpace(colorSpace: String): Int = CameraNative.setFujiColorSpace(colorSpace)
    fun getFujiShutterCounter(): Int = CameraNative.getFujiShutterCounter()

    fun setPanasonicMovieRecording(enable: Boolean): Int = CameraNative.setPanasonicMovieRecording(enable)
    fun setPanasonicManualFocusDrive(steps: Int): Int = CameraNative.setPanasonicManualFocusDrive(steps)

    companion object {
        private const val TAG = "NativeConfigDS"
    }
}
