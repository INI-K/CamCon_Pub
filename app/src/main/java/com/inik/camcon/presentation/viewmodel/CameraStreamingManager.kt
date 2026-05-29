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
    private var stopJob: Job? = null

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
        // 이미 active인 stopJob이 있으면 재실행하지 않음
        if (stopJob?.isActive == true) return

        stopJob = scope.launch {
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

    /**
     * 스트리밍 정리(F23).
     * @ApplicationScope 위의 무한 collect Job(streamingJob)을 취소하고 네이티브 스트리밍을 중지한다.
     *
     * 주의: 현재 startStreaming/stopStreaming 호출부가 0건인 미배선(dead) 기능이라
     * onCleared 등에서의 호출 배선은 추가하지 않았다. 기능이 활성화될 때 호출부에서 연결해야 한다.
     */
    fun cleanup() {
        streamingJob?.cancel()
        streamingJob = null
        scope.launch {
            stopStreamingUseCase()
            _isStreaming.value = false
            _currentFrame.value = null
        }
    }
}
