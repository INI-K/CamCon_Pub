package com.inik.camcon.domain.repository

import com.inik.camcon.domain.model.capture.IntervalCaptureStatus

interface CameraAdvancedCaptureRepository {
    suspend fun startBulbCapture(): Result<Boolean>
    suspend fun endBulbCapture(): Result<Boolean>
    suspend fun bulbCaptureWithDuration(seconds: Int): Result<Boolean>
    suspend fun startVideoRecording(): Result<Boolean>
    suspend fun stopVideoRecording(): Result<Boolean>
    suspend fun isVideoRecording(): Result<Boolean>
    suspend fun startIntervalCapture(intervalSeconds: Int, totalFrames: Int): Result<Boolean>
    suspend fun stopIntervalCapture(): Result<Boolean>
    suspend fun getIntervalCaptureStatus(): Result<IntervalCaptureStatus>
    suspend fun captureDualMode(keepRawOnCard: Boolean, downloadJpeg: Boolean): Result<Boolean>
    suspend fun triggerCapture(): Result<Boolean>
    suspend fun captureAudio(): Result<Boolean>
}
