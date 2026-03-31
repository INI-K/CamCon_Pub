package com.inik.camcon.domain.model.capture

data class IntervalCaptureStatus(
    val currentFrame: Int,
    val totalFrames: Int,
    val isRunning: Boolean
)

data class BulbCaptureState(
    val isActive: Boolean,
    val elapsedSeconds: Int = 0
)

data class VideoRecordingState(
    val isRecording: Boolean
)
