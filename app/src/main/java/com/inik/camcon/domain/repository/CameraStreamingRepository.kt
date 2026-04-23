package com.inik.camcon.domain.repository

import com.inik.camcon.domain.model.streaming.StreamFrame
import kotlinx.coroutines.flow.Flow

interface CameraStreamingRepository {
    suspend fun startStreaming(): Result<Boolean>
    suspend fun stopStreaming(): Result<Boolean>
    fun getStreamFrames(): Flow<StreamFrame>
    suspend fun setStreamingParameters(width: Int, height: Int, fps: Int): Result<Boolean>
}
