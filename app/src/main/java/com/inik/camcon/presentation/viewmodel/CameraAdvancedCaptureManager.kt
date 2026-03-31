package com.inik.camcon.presentation.viewmodel

import com.inik.camcon.domain.model.capture.BulbCaptureState
import com.inik.camcon.domain.model.capture.IntervalCaptureStatus
import com.inik.camcon.domain.model.capture.VideoRecordingState
import com.inik.camcon.domain.usecase.capture.*
import com.inik.camcon.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraAdvancedCaptureManager @Inject constructor(
    private val startBulbCaptureUseCase: StartBulbCaptureUseCase,
    private val endBulbCaptureUseCase: EndBulbCaptureUseCase,
    private val bulbCaptureWithDurationUseCase: BulbCaptureWithDurationUseCase,
    private val startVideoRecordingUseCase: StartVideoRecordingUseCase,
    private val stopVideoRecordingUseCase: StopVideoRecordingUseCase,
    private val isVideoRecordingUseCase: IsVideoRecordingUseCase,
    private val startIntervalCaptureUseCase: StartIntervalCaptureUseCase,
    private val stopIntervalCaptureUseCase: StopIntervalCaptureUseCase,
    private val getIntervalCaptureStatusUseCase: GetIntervalCaptureStatusUseCase,
    private val captureDualModeUseCase: CaptureDualModeUseCase,
    private val triggerCaptureUseCase: TriggerCaptureUseCase,
    private val captureAudioUseCase: CaptureAudioUseCase,
    @ApplicationScope private val scope: CoroutineScope
) {
    private val _bulbState = MutableStateFlow(BulbCaptureState(isActive = false))
    val bulbState: StateFlow<BulbCaptureState> = _bulbState.asStateFlow()

    private val _videoState = MutableStateFlow(VideoRecordingState(isRecording = false))
    val videoState: StateFlow<VideoRecordingState> = _videoState.asStateFlow()

    private val _intervalStatus = MutableStateFlow<IntervalCaptureStatus?>(null)
    val intervalStatus: StateFlow<IntervalCaptureStatus?> = _intervalStatus.asStateFlow()

    fun startBulbCapture() {
        scope.launch {
            startBulbCaptureUseCase().onSuccess {
                _bulbState.value = BulbCaptureState(isActive = true)
            }
        }
    }

    fun endBulbCapture() {
        scope.launch {
            endBulbCaptureUseCase().onSuccess {
                _bulbState.value = BulbCaptureState(isActive = false)
            }
        }
    }

    fun bulbCaptureWithDuration(seconds: Int) {
        scope.launch {
            _bulbState.value = BulbCaptureState(isActive = true)
            bulbCaptureWithDurationUseCase(seconds)
            _bulbState.value = BulbCaptureState(isActive = false)
        }
    }

    fun startVideoRecording() {
        scope.launch {
            startVideoRecordingUseCase().onSuccess {
                _videoState.value = VideoRecordingState(isRecording = true)
            }
        }
    }

    fun stopVideoRecording() {
        scope.launch {
            stopVideoRecordingUseCase().onSuccess {
                _videoState.value = VideoRecordingState(isRecording = false)
            }
        }
    }

    fun startIntervalCapture(intervalSeconds: Int, totalFrames: Int) {
        scope.launch {
            startIntervalCaptureUseCase(intervalSeconds, totalFrames)
        }
    }

    fun stopIntervalCapture() {
        scope.launch {
            stopIntervalCaptureUseCase()
            _intervalStatus.value = null
        }
    }

    fun refreshIntervalStatus() {
        scope.launch {
            getIntervalCaptureStatusUseCase().onSuccess {
                _intervalStatus.value = it
            }
        }
    }

    fun captureDualMode(keepRawOnCard: Boolean, downloadJpeg: Boolean) {
        scope.launch {
            captureDualModeUseCase(keepRawOnCard, downloadJpeg)
        }
    }

    fun triggerCapture() {
        scope.launch {
            triggerCaptureUseCase()
        }
    }

    fun captureAudio() {
        scope.launch {
            captureAudioUseCase()
        }
    }

    suspend fun checkVideoRecording(): Boolean {
        return isVideoRecordingUseCase().getOrDefault(false)
    }
}
