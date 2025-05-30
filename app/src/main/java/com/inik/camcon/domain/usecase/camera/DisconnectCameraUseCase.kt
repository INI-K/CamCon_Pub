package com.inik.camcon.domain.usecase.camera

import com.inik.camcon.domain.repository.CameraRepository
import javax.inject.Inject

class DisconnectCameraUseCase @Inject constructor(
    private val cameraRepository: CameraRepository
) {
    suspend operator fun invoke(): Result<Boolean> {
        return cameraRepository.disconnectCamera()
    }
}