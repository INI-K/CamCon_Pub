package com.inik.camcon.data.repository

import com.inik.camcon.CameraNative
import com.inik.camcon.data.datasource.nativesource.NativeDiagnosticsDataSource
import com.inik.camcon.domain.model.diagnostics.MemoryPoolStatus
import com.inik.camcon.domain.model.diagnostics.TransferProgress
import com.inik.camcon.domain.repository.CameraDiagnosticsRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraDiagnosticsRepositoryImpl @Inject constructor(
    private val nativeDataSource: NativeDiagnosticsDataSource
) : CameraDiagnosticsRepository {

    private val _transferProgress = MutableSharedFlow<TransferProgress>(extraBufferCapacity = 1)
    private val _statusMessages = MutableSharedFlow<String>(extraBufferCapacity = 10)

    override suspend fun diagnoseCameraIssues(): Result<String> = runCatching {
        nativeDataSource.diagnoseCameraIssues()
    }

    override suspend fun diagnoseUSBConnection(): Result<String> = runCatching {
        nativeDataSource.diagnoseUSBConnection()
    }

    override suspend fun getErrorHistory(count: Int): Result<String> = runCatching {
        nativeDataSource.getErrorHistory(count)
    }

    override suspend fun clearErrorHistory(): Result<Boolean> = runCatching {
        nativeDataSource.clearErrorHistory()
        true
    }

    override suspend fun getCameraFilePoolCount(): Result<Int> = runCatching {
        nativeDataSource.getCameraFilePoolCount()
    }

    override suspend fun clearCameraFilePool(): Result<Boolean> = runCatching {
        nativeDataSource.clearCameraFilePool()
        true
    }

    override suspend fun getMemoryPoolStatus(): Result<MemoryPoolStatus> = runCatching {
        val json = nativeDataSource.getMemoryPoolStatus()
        parseMemoryPoolStatus(json)
    }

    override suspend fun getLogFilePath(): Result<String> = runCatching {
        nativeDataSource.getLogFilePath()
    }

    override suspend fun isLogFileActive(): Result<Boolean> = runCatching {
        nativeDataSource.isLogFileActive()
    }

    override suspend fun isOperationCanceled(): Result<Boolean> = runCatching {
        nativeDataSource.isOperationCanceled()
    }

    override fun getTransferProgress(): Flow<TransferProgress> = _transferProgress.asSharedFlow()

    override fun getStatusMessages(): Flow<String> = _statusMessages.asSharedFlow()

    override suspend fun registerHookCallback(): Result<Boolean> = runCatching {
        val callback = object : CameraNative.HookEventCallback {
            override fun onHookEvent(action: String, argument: String) {
                // Hook events can be processed here
            }
        }
        nativeDataSource.registerHookCallback(callback) == 0
    }

    override suspend fun unregisterHookCallback(): Result<Boolean> = runCatching {
        nativeDataSource.unregisterHookCallback()
        true
    }

    private fun parseMemoryPoolStatus(json: String): MemoryPoolStatus {
        val obj = JSONObject(json)
        return MemoryPoolStatus(
            activeCount = obj.optInt("activeCount", 0),
            totalAllocated = obj.optLong("totalAllocated", 0),
            details = json
        )
    }
}
