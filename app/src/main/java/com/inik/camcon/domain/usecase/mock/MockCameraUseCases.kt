package com.inik.camcon.domain.usecase.mock

import com.inik.camcon.domain.model.mock.MockCameraConfig
import com.inik.camcon.domain.model.mock.MockCameraInfo
import com.inik.camcon.domain.repository.CameraMockRepository
import javax.inject.Inject

class EnableMockCameraUseCase @Inject constructor(private val repository: CameraMockRepository) {
    suspend operator fun invoke(enable: Boolean): Result<Boolean> = repository.enableMockCamera(enable)
}

class IsMockCameraEnabledUseCase @Inject constructor(private val repository: CameraMockRepository) {
    suspend operator fun invoke(): Result<Boolean> = repository.isMockCameraEnabled()
}

class SetMockCameraModelUseCase @Inject constructor(private val repository: CameraMockRepository) {
    suspend operator fun invoke(config: MockCameraConfig): Result<Boolean> = repository.setMockCameraModel(config)
}

class GetMockCameraModelUseCase @Inject constructor(private val repository: CameraMockRepository) {
    suspend operator fun invoke(): Result<String> = repository.getMockCameraModel()
}

class SetMockCameraImagesUseCase @Inject constructor(private val repository: CameraMockRepository) {
    suspend operator fun invoke(imagePaths: List<String>): Result<Boolean> = repository.setMockCameraImages(imagePaths)
}

class AddMockCameraImageUseCase @Inject constructor(private val repository: CameraMockRepository) {
    suspend operator fun invoke(imagePath: String): Result<Boolean> = repository.addMockCameraImage(imagePath)
}

class ClearMockCameraImagesUseCase @Inject constructor(private val repository: CameraMockRepository) {
    suspend operator fun invoke(): Result<Boolean> = repository.clearMockCameraImages()
}

class GetMockCameraImageCountUseCase @Inject constructor(private val repository: CameraMockRepository) {
    suspend operator fun invoke(): Result<Int> = repository.getMockCameraImageCount()
}

class SetMockCameraDelayUseCase @Inject constructor(private val repository: CameraMockRepository) {
    suspend operator fun invoke(delayMs: Int): Result<Boolean> = repository.setMockCameraDelay(delayMs)
}

class GetMockCameraDelayUseCase @Inject constructor(private val repository: CameraMockRepository) {
    suspend operator fun invoke(): Result<Int> = repository.getMockCameraDelay()
}

class SimulateCameraErrorUseCase @Inject constructor(private val repository: CameraMockRepository) {
    suspend operator fun invoke(errorCode: Int, errorMessage: String): Result<Boolean> = repository.simulateCameraError(errorCode, errorMessage)
}

class SetMockCameraAutoCaptureUseCase @Inject constructor(private val repository: CameraMockRepository) {
    suspend operator fun invoke(enable: Boolean, intervalMs: Int): Result<Boolean> = repository.setMockCameraAutoCapture(enable, intervalMs)
}

class GetMockCameraInfoUseCase @Inject constructor(private val repository: CameraMockRepository) {
    suspend operator fun invoke(): Result<MockCameraInfo> = repository.getMockCameraInfo()
}
