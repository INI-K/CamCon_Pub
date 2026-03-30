package com.inik.camcon.domain.repository

import com.inik.camcon.domain.model.ColorTransferResult

interface ColorTransferRepository {
    suspend fun applyColorTransfer(
        inputImagePath: String,
        referenceImagePath: String,
        originalImagePath: String? = null,
        intensity: Float = 1.0f
    ): Result<ColorTransferResult>

    suspend fun initializeProcessor(contextProvider: Any)
    fun releaseProcessor()
}
