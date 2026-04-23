package com.inik.camcon.domain.usecase.camera

import com.inik.camcon.domain.repository.CameraRepository
import javax.inject.Inject

class ReadNativeLogUseCase @Inject constructor(
    private val repository: CameraRepository
) {
    suspend operator fun invoke(filePath: String): String =
        repository.readNativeLog(filePath)
}
