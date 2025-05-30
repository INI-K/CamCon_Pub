package com.inik.camcon.domain.usecase.camera

import com.inik.camcon.data.datasource.camera.CameraDatabaseManager
import com.inik.camcon.data.datasource.camera.SupportedCamera
import javax.inject.Inject

class CheckCameraSupportUseCase @Inject constructor(
    private val cameraDatabaseManager: CameraDatabaseManager
) {
    suspend operator fun invoke(vendor: String, model: String): SupportedCamera? {
        return cameraDatabaseManager.findSupportedCamera(vendor, model)
    }

    suspend fun initializeDatabase() {
        cameraDatabaseManager.initializeDatabase()
    }
}