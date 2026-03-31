package com.inik.camcon.data.datasource.nativesource

import com.inik.camcon.CameraNative
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeMockCameraDataSource @Inject constructor() {
    fun enableMockCamera(enable: Boolean): Boolean = CameraNative.enableMockCamera(enable)
    fun isMockCameraEnabled(): Boolean = CameraNative.isMockCameraEnabled()
    fun setMockCameraModel(manufacturer: String, model: String): Boolean = CameraNative.setMockCameraModel(manufacturer, model)
    fun getMockCameraModel(): String = CameraNative.getMockCameraModel()
    fun setMockCameraImages(imagePaths: Array<String>): Boolean = CameraNative.setMockCameraImages(imagePaths)
    fun addMockCameraImage(imagePath: String): Boolean = CameraNative.addMockCameraImage(imagePath)
    fun clearMockCameraImages(): Boolean = CameraNative.clearMockCameraImages()
    fun getMockCameraImageCount(): Int = CameraNative.getMockCameraImageCount()
    fun setMockCameraDelay(delayMs: Int): Boolean = CameraNative.setMockCameraDelay(delayMs)
    fun getMockCameraDelay(): Int = CameraNative.getMockCameraDelay()
    fun simulateCameraError(errorCode: Int, errorMessage: String): Boolean = CameraNative.simulateCameraError(errorCode, errorMessage)
    fun setMockCameraAutoCapture(enable: Boolean, intervalMs: Int): Boolean = CameraNative.setMockCameraAutoCapture(enable, intervalMs)
    fun getMockCameraInfo(): String = CameraNative.getMockCameraInfo()
}
