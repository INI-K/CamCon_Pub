package com.inik.camcon.domain.usecase.streaming

import com.inik.camcon.domain.model.streaming.StreamFrame
import com.inik.camcon.domain.repository.CameraStreamingRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class StartStreamingUseCase @Inject constructor(
    private val repository: CameraStreamingRepository
) {
    suspend operator fun invoke(): Result<Boolean> = repository.startStreaming()
}

class StopStreamingUseCase @Inject constructor(
    private val repository: CameraStreamingRepository
) {
    suspend operator fun invoke(): Result<Boolean> = repository.stopStreaming()
}

class GetStreamFramesUseCase @Inject constructor(
    private val repository: CameraStreamingRepository
) {
    operator fun invoke(): Flow<StreamFrame> = repository.getStreamFrames()
}

class SetStreamingParametersUseCase @Inject constructor(
    private val repository: CameraStreamingRepository
) {
    suspend operator fun invoke(width: Int, height: Int, fps: Int): Result<Boolean> =
        repository.setStreamingParameters(width, height, fps)
}
