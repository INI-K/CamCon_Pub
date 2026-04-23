package com.inik.camcon.domain.usecase.focus

import com.inik.camcon.domain.repository.CameraFocusRepository
import javax.inject.Inject

class SetAFModeUseCase @Inject constructor(
    private val repository: CameraFocusRepository
) {
    suspend operator fun invoke(mode: String): Result<Boolean> = repository.setAFMode(mode)
}

class GetAFModeUseCase @Inject constructor(
    private val repository: CameraFocusRepository
) {
    suspend operator fun invoke(): Result<String> = repository.getAFMode()
}

class SetAFAreaUseCase @Inject constructor(
    private val repository: CameraFocusRepository
) {
    suspend operator fun invoke(x: Int, y: Int, width: Int, height: Int): Result<Boolean> =
        repository.setAFArea(x, y, width, height)
}

class DriveManualFocusUseCase @Inject constructor(
    private val repository: CameraFocusRepository
) {
    suspend operator fun invoke(steps: Int): Result<Boolean> = repository.driveManualFocus(steps)
}
