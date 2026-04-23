package com.inik.camcon.domain.usecase.camera

import com.inik.camcon.domain.model.NativeLogLevel
import com.inik.camcon.domain.repository.CameraRepository
import javax.inject.Inject

class StartNativeLogUseCase @Inject constructor(
    private val repository: CameraRepository
) {
    suspend operator fun invoke(
        logPath: String,
        level: Int = NativeLogLevel.DEBUG
    ): Boolean = repository.startNativeLog(logPath, level)
}
