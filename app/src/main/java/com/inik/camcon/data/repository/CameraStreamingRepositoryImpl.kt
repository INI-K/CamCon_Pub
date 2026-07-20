package com.inik.camcon.data.repository

import com.inik.camcon.data.datasource.nativesource.NativeStreamingDataSource
import com.inik.camcon.di.IoDispatcher
import com.inik.camcon.domain.model.streaming.StreamFrame
import com.inik.camcon.domain.repository.CameraStreamingRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class CameraStreamingRepositoryImpl @Inject constructor(
    private val nativeDataSource: NativeStreamingDataSource,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : CameraStreamingRepository {

    override suspend fun startStreaming(): Result<Boolean> = withContext(ioDispatcher) {
        runCatching {
            nativeDataSource.startPTPStreaming() == 0
        }
    }

    override suspend fun stopStreaming(): Result<Boolean> = withContext(ioDispatcher) {
        runCatching {
            nativeDataSource.stopPTPStreaming() == 0
        }
    }

    override fun getStreamFrames(): Flow<StreamFrame> = flow {
        while (coroutineContext.isActive) {
            val frameData = nativeDataSource.getPTPStreamFrame()
            if (frameData != null) {
                emit(
                    StreamFrame(
                        data = frameData,
                        width = 0,
                        height = 0,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
            delay(16) // ~60fps 폴링
        }
    }.flowOn(ioDispatcher)

    override suspend fun setStreamingParameters(width: Int, height: Int, fps: Int): Result<Boolean> = withContext(ioDispatcher) {
        runCatching {
            nativeDataSource.setPTPStreamingParameters(width, height, fps) == 0
        }
    }
}
