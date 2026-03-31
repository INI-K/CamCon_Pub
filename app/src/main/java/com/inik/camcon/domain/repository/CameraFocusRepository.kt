package com.inik.camcon.domain.repository

interface CameraFocusRepository {
    suspend fun setAFMode(mode: String): Result<Boolean>
    suspend fun getAFMode(): Result<String>
    suspend fun setAFArea(x: Int, y: Int, width: Int, height: Int): Result<Boolean>
    suspend fun driveManualFocus(steps: Int): Result<Boolean>
}
