package com.inik.camcon.domain.usecase.camera

import com.inik.camcon.domain.model.CapturedPhoto
import com.inik.camcon.domain.model.ShootingMode
import com.inik.camcon.domain.repository.CameraRepository
import javax.inject.Inject

class CapturePhotoUseCase @Inject constructor(
    private val cameraRepository: CameraRepository
) {
    suspend operator fun invoke(shootingMode: ShootingMode): Result<CapturedPhoto> {
        return cameraRepository.capturePhoto(shootingMode)
    }
}