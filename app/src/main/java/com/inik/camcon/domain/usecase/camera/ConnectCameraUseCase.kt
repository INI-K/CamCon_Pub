package com.inik.camcon.domain.usecase.camera

import com.inik.camcon.domain.repository.CameraRepository
import javax.inject.Inject

class ConnectCameraUseCase @Inject constructor(
    private val cameraRepository: CameraRepository
) {
    suspend operator fun invoke(cameraId: String): Result<Boolean> {
        return cameraRepository.connectCamera(cameraId)
    }
}