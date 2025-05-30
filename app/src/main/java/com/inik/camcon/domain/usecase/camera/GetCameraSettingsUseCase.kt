package com.inik.camcon.domain.usecase.camera

import com.inik.camcon.domain.model.CameraSettings
import com.inik.camcon.domain.repository.CameraRepository
import javax.inject.Inject

class GetCameraSettingsUseCase @Inject constructor(
    private val cameraRepository: CameraRepository
) {
    suspend operator fun invoke(): Result<CameraSettings> {
        return cameraRepository.getCameraSettings()
    }
}