package com.inik.camcon.domain.usecase.camera

import com.inik.camcon.domain.model.CapturedPhoto
import com.inik.camcon.domain.model.TimelapseSettings
import com.inik.camcon.domain.repository.CameraRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class StartTimelapseUseCase @Inject constructor(
    private val cameraRepository: CameraRepository
) {
    operator fun invoke(settings: TimelapseSettings): Flow<CapturedPhoto> {
        return cameraRepository.startTimelapse(settings)
    }
}