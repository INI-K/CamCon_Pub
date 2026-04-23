package com.inik.camcon.domain.usecase.camera

import com.inik.camcon.domain.repository.CameraRepository
import javax.inject.Inject

class SetupNativeEnvironmentUseCase @Inject constructor(
    private val repository: CameraRepository
) {
    suspend operator fun invoke(pluginDir: String): Boolean =
        repository.setupNativeEnvironment(pluginDir)
}
