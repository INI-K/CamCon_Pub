package com.inik.camcon.domain.usecase.camera

import com.inik.camcon.domain.model.CameraCapabilities
import com.inik.camcon.domain.repository.CameraRepository
import javax.inject.Inject

class GetCameraCapabilitiesUseCase @Inject constructor(
    private val cameraRepository: CameraRepository
) {
    suspend operator fun invoke(): Result<CameraCapabilities?> {
        return cameraRepository.getCameraCapabilities()
    }
}