package com.inik.camcon.data.repository

import com.inik.camcon.data.datasource.nativesource.NativeAdvancedCaptureDataSource
import com.inik.camcon.domain.model.capture.IntervalCaptureStatus
import com.inik.camcon.domain.repository.CameraAdvancedCaptureRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraAdvancedCaptureRepositoryImpl @Inject constructor(
    private val nativeDataSource: NativeAdvancedCaptureDataSource
) : CameraAdvancedCaptureRepository {

    override suspend fun startBulbCapture(): Result<Boolean> = runCatching {
        nativeDataSource.startBulbCapture() == 0
    }

    override suspend fun endBulbCapture(): Result<Boolean> = runCatching {
        nativeDataSource.endBulbCapture() == 0
    }

    override suspend fun bulbCaptureWithDuration(seconds: Int): Result<Boolean> = runCatching {
        nativeDataSource.bulbCaptureWithDuration(seconds) == 0
    }

    override suspend fun startVideoRecording(): Result<Boolean> = runCatching {
        nativeDataSource.startVideoRecording() == 0
    }

    override suspend fun stopVideoRecording(): Result<Boolean> = runCatching {
        nativeDataSource.stopVideoRecording() == 0
    }

    override suspend fun isVideoRecording(): Result<Boolean> = runCatching {
        nativeDataSource.isVideoRecording()
    }

    override suspend fun startIntervalCapture(intervalSeconds: Int, totalFrames: Int): Result<Boolean> = runCatching {
        nativeDataSource.startIntervalCapture(intervalSeconds, totalFrames) == 0
    }

    override suspend fun stopIntervalCapture(): Result<Boolean> = runCatching {
        nativeDataSource.stopIntervalCapture() == 0
    }

    override suspend fun getIntervalCaptureStatus(): Result<IntervalCaptureStatus> = runCatching {
        val status = nativeDataSource.getIntervalCaptureStatus()
        IntervalCaptureStatus(
            currentFrame = status[0],
            totalFrames = status[1],
            isRunning = status[2] == 1
        )
    }

    override suspend fun captureDualMode(keepRawOnCard: Boolean, downloadJpeg: Boolean): Result<Boolean> = runCatching {
        nativeDataSource.captureDualMode(keepRawOnCard, downloadJpeg) == 0
    }

    override suspend fun triggerCapture(): Result<Boolean> = runCatching {
        nativeDataSource.triggerCapture() == 0
    }

    override suspend fun captureAudio(): Result<Boolean> = runCatching {
        // Will be implemented when C++ JNI binding for audio capture is added
        throw UnsupportedOperationException("Audio capture C++ binding not yet implemented")
    }
}
