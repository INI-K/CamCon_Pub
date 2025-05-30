package com.inik.camcon.domain.usecase.camera

import com.inik.camcon.domain.repository.CameraRepository
import javax.inject.Inject

class UpdateCameraSettingUseCase @Inject constructor(
    private val cameraRepository: CameraRepository
) {
    suspend operator fun invoke(key: String, value: String): Result<Boolean> {
        return cameraRepository.updateCameraSetting(key, value)
    }
}