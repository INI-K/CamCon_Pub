package com.inik.camcon.domain.usecase.camera

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhotoCaptureEventManager @Inject constructor() {

    private val _photoCaptureEvent = MutableSharedFlow<PhotoCaptureEvent>()
    val photoCaptureEvent: SharedFlow<PhotoCaptureEvent> = _photoCaptureEvent.asSharedFlow()

    suspend fun emitPhotoCaptured() {
        _photoCaptureEvent.emit(PhotoCaptureEvent.PhotoCaptured)
    }
}

sealed class PhotoCaptureEvent {
    object PhotoCaptured : PhotoCaptureEvent()
}