package com.inik.camcon.data.repository

import com.inik.camcon.data.datasource.nativesource.NativeFocusDataSource
import com.inik.camcon.domain.repository.CameraFocusRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraFocusRepositoryImpl @Inject constructor(
    private val nativeDataSource: NativeFocusDataSource
) : CameraFocusRepository {

    override suspend fun setAFMode(mode: String): Result<Boolean> = runCatching {
        nativeDataSource.setAFMode(mode) == 0
    }

    override suspend fun getAFMode(): Result<String> = runCatching {
        nativeDataSource.getAFMode() ?: throw IllegalStateException("AF mode not available")
    }

    override suspend fun setAFArea(x: Int, y: Int, width: Int, height: Int): Result<Boolean> = runCatching {
        nativeDataSource.setAFArea(x, y, width, height) == 0
    }

    override suspend fun driveManualFocus(steps: Int): Result<Boolean> = runCatching {
        nativeDataSource.driveManualFocus(steps) == 0
    }
}
