package com.inik.camcon.domain.usecase.camera

import com.inik.camcon.domain.model.LiveViewFrame
import com.inik.camcon.domain.repository.CameraRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class StartLiveViewUseCase @Inject constructor(
    private val cameraRepository: CameraRepository
) {
    operator fun invoke(): Flow<LiveViewFrame> {
        return cameraRepository.startLiveView()
    }
}