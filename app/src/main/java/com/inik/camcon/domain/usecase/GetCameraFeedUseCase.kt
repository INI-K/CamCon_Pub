package com.inik.camcon.domain.usecase

import com.inik.camcon.domain.model.Camera
import com.inik.camcon.domain.repository.CameraRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetCameraFeedUseCase @Inject constructor(
    private val cameraRepository: CameraRepository
) {
    operator fun invoke(): Flow<List<Camera>> {
        return cameraRepository.getCameraFeed()
    }
}
