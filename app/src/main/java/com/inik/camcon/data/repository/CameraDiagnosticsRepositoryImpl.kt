package com.inik.camcon.data.repository

import com.inik.camcon.CameraNative
import com.inik.camcon.data.datasource.nativesource.NativeDiagnosticsDataSource
import com.inik.camcon.di.IoDispatcher
import com.inik.camcon.domain.model.diagnostics.MemoryPoolStatus
import com.inik.camcon.domain.model.diagnostics.TransferProgress
import com.inik.camcon.domain.repository.CameraDiagnosticsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraDiagnosticsRepositoryImpl @Inject constructor(
    private val nativeDataSource: NativeDiagnosticsDataSource,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : CameraDiagnosticsRepository {

    private val _transferProgress = MutableSharedFlow<TransferProgress>(extraBufferCapacity = 1)
    private val _statusMessages = MutableSharedFlow<String>(extraBufferCapacity = 10)

    override suspend fun diagnoseCameraIssues(): Result<String> = withContext(ioDispatcher) {
        runCatching {
            nativeDataSource.diagnoseCameraIssues()
        }
    }

    override suspend fun diagnoseUSBConnection(): Result<String> = withContext(ioDispatcher) {
        runCatching {
            nativeDataSource.diagnoseUSBConnection()
        }
    }

    override suspend fun getErrorHistory(count: Int): Result<String> = withContext(ioDispatcher) {
        runCatching {
            nativeDataSource.getErrorHistory(count)
        }
    }

    override suspend fun clearErrorHistory(): Result<Boolean> = withContext(ioDispatcher) {
        runCatching {
            nativeDataSource.clearErrorHistory()
            true
        }
    }

    override suspend fun getCameraFilePoolCount(): Result<Int> = withContext(ioDispatcher) {
        runCatching {
            nativeDataSource.getCameraFilePoolCount()
        }
    }

    override suspend fun clearCameraFilePool(): Result<Boolean> = withContext(ioDispatcher) {
        runCatching {
            nativeDataSource.clearCameraFilePool()
            true
        }
    }

    override suspend fun getMemoryPoolStatus(): Result<MemoryPoolStatus> = withContext(ioDispatcher) {
        runCatching {
            val json = nativeDataSource.getMemoryPoolStatus()
            parseMemoryPoolStatus(json)
        }
    }

    override suspend fun getLogFilePath(): Result<String> = withContext(ioDispatcher) {
        runCatching {
            nativeDataSource.getLogFilePath()
        }
    }

    override suspend fun isLogFileActive(): Result<Boolean> = withContext(ioDispatcher) {
        runCatching {
            nativeDataSource.isLogFileActive()
        }
    }

    override suspend fun isOperationCanceled(): Result<Boolean> = withContext(ioDispatcher) {
        runCatching {
            nativeDataSource.isOperationCanceled()
        }
    }

    override fun getTransferProgress(): Flow<TransferProgress> = _transferProgress.asSharedFlow()

    override fun getStatusMessages(): Flow<String> = _statusMessages.asSharedFlow()

    override suspend fun registerHookCallback(): Result<Boolean> = withContext(ioDispatcher) {
        runCatching {
            val callback = object : CameraNative.HookEventCallback {
                override fun onHookEvent(action: String, argument: String) {
                    // 훅 이벤트는 여기서 처리할 수 있음
                }
            }
            nativeDataSource.registerHookCallback(callback) == 0
        }
    }

    override suspend fun unregisterHookCallback(): Result<Boolean> = withContext(ioDispatcher) {
        runCatching {
            nativeDataSource.unregisterHookCallback()
            true
        }
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
