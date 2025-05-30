package com.inik.camcon.domain.usecase.camera

import com.inik.camcon.domain.model.CameraPhoto
import com.inik.camcon.domain.repository.CameraRepository
import javax.inject.Inject

class GetCameraPhotosUseCase @Inject constructor(
    private val cameraRepository: CameraRepository
) {
    suspend operator fun invoke(): Result<List<CameraPhoto>> {
        return cameraRepository.getCameraPhotos()
    }
}