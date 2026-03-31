package com.inik.camcon.domain.usecase.capture

import com.inik.camcon.domain.model.capture.IntervalCaptureStatus
import com.inik.camcon.domain.repository.CameraAdvancedCaptureRepository
import javax.inject.Inject

class StartBulbCaptureUseCase @Inject constructor(
    private val repository: CameraAdvancedCaptureRepository
) {
    suspend operator fun invoke(): Result<Boolean> = repository.startBulbCapture()
}

class EndBulbCaptureUseCase @Inject constructor(
    private val repository: CameraAdvancedCaptureRepository
) {
    suspend operator fun invoke(): Result<Boolean> = repository.endBulbCapture()
}

class BulbCaptureWithDurationUseCase @Inject constructor(
    private val repository: CameraAdvancedCaptureRepository
) {
    suspend operator fun invoke(seconds: Int): Result<Boolean> = repository.bulbCaptureWithDuration(seconds)
}

class StartVideoRecordingUseCase @Inject constructor(
    private val repository: CameraAdvancedCaptureRepository
) {
    suspend operator fun invoke(): Result<Boolean> = repository.startVideoRecording()
}

class StopVideoRecordingUseCase @Inject constructor(
    private val repository: CameraAdvancedCaptureRepository
) {
    suspend operator fun invoke(): Result<Boolean> = repository.stopVideoRecording()
}

class IsVideoRecordingUseCase @Inject constructor(
    private val repository: CameraAdvancedCaptureRepository
) {
    suspend operator fun invoke(): Result<Boolean> = repository.isVideoRecording()
}

class StartIntervalCaptureUseCase @Inject constructor(
    private val repository: CameraAdvancedCaptureRepository
) {
    suspend operator fun invoke(intervalSeconds: Int, totalFrames: Int): Result<Boolean> =
        repository.startIntervalCapture(intervalSeconds, totalFrames)
}

class StopIntervalCaptureUseCase @Inject constructor(
    private val repository: CameraAdvancedCaptureRepository
) {
    suspend operator fun invoke(): Result<Boolean> = repository.stopIntervalCapture()
}

class GetIntervalCaptureStatusUseCase @Inject constructor(
    private val repository: CameraAdvancedCaptureRepository
) {
    suspend operator fun invoke(): Result<IntervalCaptureStatus> = repository.getIntervalCaptureStatus()
}

class CaptureDualModeUseCase @Inject constructor(
    private val repository: CameraAdvancedCaptureRepository
) {
    suspend operator fun invoke(keepRawOnCard: Boolean, downloadJpeg: Boolean): Result<Boolean> =
        repository.captureDualMode(keepRawOnCard, downloadJpeg)
}

class TriggerCaptureUseCase @Inject constructor(
    private val repository: CameraAdvancedCaptureRepository
) {
    suspend operator fun invoke(): Result<Boolean> = repository.triggerCapture()
}

class CaptureAudioUseCase @Inject constructor(
    private val repository: CameraAdvancedCaptureRepository
) {
    suspend operator fun invoke(): Result<Boolean> = repository.captureAudio()
}
