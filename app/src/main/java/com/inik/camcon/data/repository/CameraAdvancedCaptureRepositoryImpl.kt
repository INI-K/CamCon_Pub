package com.inik.camcon.data.repository

import com.inik.camcon.data.datasource.nativesource.NativeAdvancedCaptureDataSource
import com.inik.camcon.di.IoDispatcher
import com.inik.camcon.domain.model.capture.IntervalCaptureStatus
import com.inik.camcon.domain.repository.CameraAdvancedCaptureRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraAdvancedCaptureRepositoryImpl @Inject constructor(
    private val nativeDataSource: NativeAdvancedCaptureDataSource,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : CameraAdvancedCaptureRepository {

    override suspend fun startBulbCapture(): Result<Boolean> = withContext(ioDispatcher) {
        runCatching {
            nativeDataSource.startBulbCapture() == 0
        }
    }

    override suspend fun endBulbCapture(): Result<Boolean> = withContext(ioDispatcher) {
        runCatching {
            nativeDataSource.endBulbCapture() == 0
        }
    }

    override suspend fun bulbCaptureWithDuration(seconds: Int): Result<Boolean> = withContext(ioDispatcher) {
        runCatching {
            nativeDataSource.bulbCaptureWithDuration(seconds) == 0
        }
    }

    override suspend fun startVideoRecording(): Result<Boolean> = withContext(ioDispatcher) {
        runCatching {
            nativeDataSource.startVideoRecording() == 0
        }
    }

    override suspend fun stopVideoRecording(): Result<Boolean> = withContext(ioDispatcher) {
        runCatching {
            nativeDataSource.stopVideoRecording() == 0
        }
    }

    override suspend fun isVideoRecording(): Result<Boolean> = withContext(ioDispatcher) {
        runCatching {
            nativeDataSource.isVideoRecording()
        }
    }

    override suspend fun startIntervalCapture(intervalSeconds: Int, totalFrames: Int): Result<Boolean> = withContext(ioDispatcher) {
        runCatching {
            nativeDataSource.startIntervalCapture(intervalSeconds, totalFrames) == 0
        }
    }

    override suspend fun stopIntervalCapture(): Result<Boolean> = withContext(ioDispatcher) {
        runCatching {
            nativeDataSource.stopIntervalCapture() == 0
        }
    }

    override suspend fun getIntervalCaptureStatus(): Result<IntervalCaptureStatus> = withContext(ioDispatcher) {
        runCatching {
            val status = nativeDataSource.getIntervalCaptureStatus()
            // 네이티브 반환 순서(native-lib.cpp:getIntervalCaptureStatus)는
            // [0]=isRunning, [1]=capturedFrames, [2]=totalFrames.
            IntervalCaptureStatus(
                currentFrame = status[1],
                totalFrames = status[2],
                isRunning = status[0] == 1
            )
        }
    }

    override suspend fun captureDualMode(keepRawOnCard: Boolean, downloadJpeg: Boolean): Result<Boolean> = withContext(ioDispatcher) {
        runCatching {
            nativeDataSource.captureDualMode(keepRawOnCard, downloadJpeg) == 0
        }
    }

    override suspend fun triggerCapture(): Result<Boolean> = withContext(ioDispatcher) {
        runCatching {
            nativeDataSource.triggerCapture() == 0
        }
    }

    override suspend fun captureAudio(): Result<Boolean> = withContext(ioDispatcher) {
        runCatching {
            // 오디오 캡처용 C++ JNI 바인딩이 추가되면 구현 예정
            throw UnsupportedOperationException("Audio capture C++ binding not yet implemented")
        }
    }
}
