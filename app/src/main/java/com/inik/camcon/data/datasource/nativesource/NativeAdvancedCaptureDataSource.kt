package com.inik.camcon.data.datasource.nativesource

import com.inik.camcon.CameraNative
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeAdvancedCaptureDataSource @Inject constructor() {

    fun startBulbCapture(): Int = CameraNative.startBulbCapture()

    fun endBulbCapture(): Int = CameraNative.endBulbCapture()

    fun bulbCaptureWithDuration(seconds: Int): Int = CameraNative.bulbCaptureWithDuration(seconds)

    fun startVideoRecording(): Int = CameraNative.startVideoRecording()

    fun stopVideoRecording(): Int = CameraNative.stopVideoRecording()

    fun isVideoRecording(): Boolean = CameraNative.isVideoRecording()

    fun startIntervalCapture(intervalSeconds: Int, totalFrames: Int): Int =
        CameraNative.startIntervalCapture(intervalSeconds, totalFrames)

    fun stopIntervalCapture(): Int = CameraNative.stopIntervalCapture()

    fun getIntervalCaptureStatus(): IntArray = CameraNative.getIntervalCaptureStatus()

    fun captureDualMode(keepRawOnCard: Boolean, downloadJpeg: Boolean): Int =
        CameraNative.captureDualMode(keepRawOnCard, downloadJpeg)

    fun triggerCapture(): Int = CameraNative.triggerCapture()

    companion object {
        private const val TAG = "NativeAdvancedCaptureDS"
    }
}
