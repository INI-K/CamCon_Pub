package com.inik.camcon.domain.repository

import com.inik.camcon.domain.model.Camera

interface CameraRepository {
    suspend fun getCameraFeed(): List<Camera>
    suspend fun capturePhoto(): Boolean
}