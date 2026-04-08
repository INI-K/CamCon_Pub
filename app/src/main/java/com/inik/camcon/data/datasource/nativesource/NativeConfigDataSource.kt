package com.inik.camcon.data.datasource.nativesource

import com.inik.camcon.CameraNative
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeConfigDataSource @Inject constructor(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun buildWidgetJson(): String = withContext(ioDispatcher) {
        CameraNative.buildWidgetJson()
    }

    suspend fun listAllConfigs(): String = withContext(ioDispatcher) {
        CameraNative.listAllConfigs()
    }

    suspend fun getConfigString(key: String): String? = withContext(ioDispatcher) {
        CameraNative.getConfigString(key)
    }

    suspend fun setConfigString(key: String, value: String): Int = withContext(ioDispatcher) {
        CameraNative.setConfigString(key, value)
    }

    suspend fun getConfigInt(key: String): Int = withContext(ioDispatcher) {
        CameraNative.getConfigInt(key)
    }

    suspend fun setConfigInt(key: String, value: Int): Int = withContext(ioDispatcher) {
        CameraNative.setConfigInt(key, value)
    }

    suspend fun getCameraManual(): String = withContext(ioDispatcher) {
        CameraNative.getCameraManual()
    }

    suspend fun readGphotoSettings(): String = withContext(ioDispatcher) {
        CameraNative.readGphotoSettings()
    }

    // Manufacturer-specific wrappers (convenience -- internally use config system)
    suspend fun setCanonColorTemperature(kelvin: Int): Int = withContext(ioDispatcher) {
        CameraNative.setCanonColorTemperature(kelvin)
    }

    suspend fun getCanonColorTemperature(): Int = withContext(ioDispatcher) {
        CameraNative.getCanonColorTemperature()
    }

    suspend fun setCanonPictureStyle(style: String): Int = withContext(ioDispatcher) {
        CameraNative.setCanonPictureStyle(style)
    }

    suspend fun setCanonWhiteBalanceAdjust(adjustBA: Int, adjustGM: Int): Int = withContext(ioDispatcher) {
        CameraNative.setCanonWhiteBalanceAdjust(adjustBA, adjustGM)
    }

    suspend fun setNikonActiveSlot(slot: String): Int = withContext(ioDispatcher) {
        CameraNative.setNikonActiveSlot(slot)
    }

    suspend fun setNikonVideoMode(enable: Boolean): Int = withContext(ioDispatcher) {
        CameraNative.setNikonVideoMode(enable)
    }

    suspend fun setNikonExposureDelayMode(enable: Boolean): Int = withContext(ioDispatcher) {
        CameraNative.setNikonExposureDelayMode(enable)
    }

    suspend fun getNikonBatteryLevel(): Int = withContext(ioDispatcher) {
        CameraNative.getNikonBatteryLevel()
    }

    suspend fun setSonyFocusArea(area: String): Int = withContext(ioDispatcher) {
        CameraNative.setSonyFocusArea(area)
    }

    suspend fun setSonyLiveViewEffect(enable: Boolean): Int = withContext(ioDispatcher) {
        CameraNative.setSonyLiveViewEffect(enable)
    }

    suspend fun setSonyManualFocusing(steps: Int): Int = withContext(ioDispatcher) {
        CameraNative.setSonyManualFocusing(steps)
    }

    suspend fun setFujiFilmSimulation(simulation: String): Int = withContext(ioDispatcher) {
        CameraNative.setFujiFilmSimulation(simulation)
    }

    suspend fun setFujiColorSpace(colorSpace: String): Int = withContext(ioDispatcher) {
        CameraNative.setFujiColorSpace(colorSpace)
    }

    suspend fun getFujiShutterCounter(): Int = withContext(ioDispatcher) {
        CameraNative.getFujiShutterCounter()
    }

    suspend fun setPanasonicMovieRecording(enable: Boolean): Int = withContext(ioDispatcher) {
        CameraNative.setPanasonicMovieRecording(enable)
    }

    suspend fun setPanasonicManualFocusDrive(steps: Int): Int = withContext(ioDispatcher) {
        CameraNative.setPanasonicManualFocusDrive(steps)
    }

    companion object {
        private const val TAG = "NativeConfigDS"
    }
}
