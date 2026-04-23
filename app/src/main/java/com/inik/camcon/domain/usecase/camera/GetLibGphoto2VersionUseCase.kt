package com.inik.camcon.domain.usecase.camera

import com.inik.camcon.domain.repository.CameraRepository
import javax.inject.Inject

class GetLibGphoto2VersionUseCase @Inject constructor(
    private val repository: CameraRepository
) {
    suspend operator fun invoke(): String = repository.getLibGphoto2Version()
}
