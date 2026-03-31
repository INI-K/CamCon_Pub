package com.inik.camcon.data.datasource.nativesource

import com.inik.camcon.CameraNative
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeStreamingDataSource @Inject constructor() {

    fun startPTPStreaming(): Int = CameraNative.startPTPStreaming()

    fun stopPTPStreaming(): Int = CameraNative.stopPTPStreaming()

    fun getPTPStreamFrame(): ByteArray? = CameraNative.getPTPStreamFrame()

    fun setPTPStreamingParameters(width: Int, height: Int, fps: Int): Int =
        CameraNative.setPTPStreamingParameters(width, height, fps)

    companion object {
        private const val TAG = "NativeStreamingDS"
    }
}
