package com.inik.camcon.domain.usecase
import com.inik.camcon.domain.repository.CameraRepository

class CapturePhotoUseCase(private val repository: CameraRepository) {
    suspend operator fun invoke() = repository.capturePhoto()
}