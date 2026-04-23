package com.inik.camcon.data.repository

import com.inik.camcon.data.datasource.nativesource.NativeMockCameraDataSource
import com.inik.camcon.domain.model.mock.MockCameraConfig
import com.inik.camcon.domain.model.mock.MockCameraInfo
import com.inik.camcon.domain.repository.CameraMockRepository
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraMockRepositoryImpl @Inject constructor(
    private val nativeDataSource: NativeMockCameraDataSource
) : CameraMockRepository {

    override suspend fun enableMockCamera(enable: Boolean): Result<Boolean> = runCatching {
        nativeDataSource.enableMockCamera(enable)
    }

    override suspend fun isMockCameraEnabled(): Result<Boolean> = runCatching {
        nativeDataSource.isMockCameraEnabled()
    }

    override suspend fun setMockCameraModel(config: MockCameraConfig): Result<Boolean> = runCatching {
        nativeDataSource.setMockCameraModel(config.manufacturer, config.model)
    }

    override suspend fun getMockCameraModel(): Result<String> = runCatching {
        nativeDataSource.getMockCameraModel()
    }

    override suspend fun setMockCameraImages(imagePaths: List<String>): Result<Boolean> = runCatching {
        nativeDataSource.setMockCameraImages(imagePaths.toTypedArray())
    }

    override suspend fun addMockCameraImage(imagePath: String): Result<Boolean> = runCatching {
        nativeDataSource.addMockCameraImage(imagePath)
    }

    override suspend fun clearMockCameraImages(): Result<Boolean> = runCatching {
        nativeDataSource.clearMockCameraImages()
    }

    override suspend fun getMockCameraImageCount(): Result<Int> = runCatching {
        nativeDataSource.getMockCameraImageCount()
    }

    override suspend fun setMockCameraDelay(delayMs: Int): Result<Boolean> = runCatching {
        nativeDataSource.setMockCameraDelay(delayMs)
    }

    override suspend fun getMockCameraDelay(): Result<Int> = runCatching {
        nativeDataSource.getMockCameraDelay()
    }

    override suspend fun simulateCameraError(errorCode: Int, errorMessage: String): Result<Boolean> = runCatching {
        nativeDataSource.simulateCameraError(errorCode, errorMessage)
    }

    override suspend fun setMockCameraAutoCapture(enable: Boolean, intervalMs: Int): Result<Boolean> = runCatching {
        nativeDataSource.setMockCameraAutoCapture(enable, intervalMs)
    }

    override suspend fun getMockCameraInfo(): Result<MockCameraInfo> = runCatching {
        val json = nativeDataSource.getMockCameraInfo()
        val obj = JSONObject(json)
        MockCameraInfo(
            isEnabled = obj.optBoolean("enabled", false),
            model = obj.optString("model", ""),
            imageCount = obj.optInt("imageCount", 0),
            delayMs = obj.optInt("delayMs", 0),
            details = json
        )
    }
}
