package com.inik.camcon.presentation.viewmodel

import com.inik.camcon.domain.model.streaming.StreamFrame
import com.inik.camcon.domain.usecase.streaming.*
import com.inik.camcon.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraStreamingManager @Inject constructor(
    private val startStreamingUseCase: StartStreamingUseCase,
    private val stopStreamingUseCase: StopStreamingUseCase,
    private val getStreamFramesUseCase: GetStreamFramesUseCase,
    private val setStreamingParametersUseCase: SetStreamingParametersUseCase,
    @ApplicationScope private val scope: CoroutineScope
) {
    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _currentFrame = MutableStateFlow<StreamFrame?>(null)
    val currentFrame: StateFlow<StreamFrame?> = _currentFrame.asStateFlow()

    private var streamingJob: Job? = null

    fun startStreaming() {
        scope.launch {
            startStreamingUseCase().onSuccess {
                _isStreaming.value = true
                streamingJob = scope.launch {
                    getStreamFramesUseCase().collect { frame ->
                        _currentFrame.value = frame
                    }
                }
            }
        }
    }

    fun stopStreaming() {
        scope.launch {
            streamingJob?.cancel()
            streamingJob = null
            stopStreamingUseCase()
            _isStreaming.value = false
            _currentFrame.value = null
        }
    }

    fun setStreamingParameters(width: Int, height: Int, fps: Int) {
        scope.launch {
            setStreamingParametersUseCase(width, height, fps)
        }
    }
}
