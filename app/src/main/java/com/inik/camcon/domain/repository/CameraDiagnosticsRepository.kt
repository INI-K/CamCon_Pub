package com.inik.camcon.domain.repository

import com.inik.camcon.domain.model.diagnostics.MemoryPoolStatus
import com.inik.camcon.domain.model.diagnostics.TransferProgress
import kotlinx.coroutines.flow.Flow

interface CameraDiagnosticsRepository {
    suspend fun diagnoseCameraIssues(): Result<String>
    suspend fun diagnoseUSBConnection(): Result<String>
    suspend fun getErrorHistory(count: Int): Result<String>
    suspend fun clearErrorHistory(): Result<Boolean>
    suspend fun getCameraFilePoolCount(): Result<Int>
    suspend fun clearCameraFilePool(): Result<Boolean>
    suspend fun getMemoryPoolStatus(): Result<MemoryPoolStatus>
    suspend fun getLogFilePath(): Result<String>
    suspend fun isLogFileActive(): Result<Boolean>
    suspend fun isOperationCanceled(): Result<Boolean>
    fun getTransferProgress(): Flow<TransferProgress>
    fun getStatusMessages(): Flow<String>
    suspend fun registerHookCallback(): Result<Boolean>
    suspend fun unregisterHookCallback(): Result<Boolean>
}
