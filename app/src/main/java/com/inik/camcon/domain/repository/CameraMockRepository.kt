package com.inik.camcon.domain.repository

import com.inik.camcon.domain.model.mock.MockCameraInfo
import com.inik.camcon.domain.model.mock.MockCameraConfig

interface CameraMockRepository {
    suspend fun enableMockCamera(enable: Boolean): Result<Boolean>
    suspend fun isMockCameraEnabled(): Result<Boolean>
    suspend fun setMockCameraModel(config: MockCameraConfig): Result<Boolean>
    suspend fun getMockCameraModel(): Result<String>
    suspend fun setMockCameraImages(imagePaths: List<String>): Result<Boolean>
    suspend fun addMockCameraImage(imagePath: String): Result<Boolean>
    suspend fun clearMockCameraImages(): Result<Boolean>
    suspend fun getMockCameraImageCount(): Result<Int>
    suspend fun setMockCameraDelay(delayMs: Int): Result<Boolean>
    suspend fun getMockCameraDelay(): Result<Int>
    suspend fun simulateCameraError(errorCode: Int, errorMessage: String): Result<Boolean>
    suspend fun setMockCameraAutoCapture(enable: Boolean, intervalMs: Int): Result<Boolean>
    suspend fun getMockCameraInfo(): Result<MockCameraInfo>
}
