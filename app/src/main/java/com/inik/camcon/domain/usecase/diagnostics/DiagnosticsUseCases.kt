package com.inik.camcon.domain.usecase.diagnostics

import com.inik.camcon.domain.model.diagnostics.MemoryPoolStatus
import com.inik.camcon.domain.model.diagnostics.TransferProgress
import com.inik.camcon.domain.repository.CameraDiagnosticsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class DiagnoseCameraIssuesUseCase @Inject constructor(
    private val repository: CameraDiagnosticsRepository
) {
    suspend operator fun invoke(): Result<String> = repository.diagnoseCameraIssues()
}

class DiagnoseUSBConnectionUseCase @Inject constructor(
    private val repository: CameraDiagnosticsRepository
) {
    suspend operator fun invoke(): Result<String> = repository.diagnoseUSBConnection()
}

class GetErrorHistoryUseCase @Inject constructor(
    private val repository: CameraDiagnosticsRepository
) {
    suspend operator fun invoke(count: Int): Result<String> = repository.getErrorHistory(count)
}

class ClearErrorHistoryUseCase @Inject constructor(
    private val repository: CameraDiagnosticsRepository
) {
    suspend operator fun invoke(): Result<Boolean> = repository.clearErrorHistory()
}

class GetCameraFilePoolCountUseCase @Inject constructor(
    private val repository: CameraDiagnosticsRepository
) {
    suspend operator fun invoke(): Result<Int> = repository.getCameraFilePoolCount()
}

class ClearCameraFilePoolUseCase @Inject constructor(
    private val repository: CameraDiagnosticsRepository
) {
    suspend operator fun invoke(): Result<Boolean> = repository.clearCameraFilePool()
}

class GetMemoryPoolStatusUseCase @Inject constructor(
    private val repository: CameraDiagnosticsRepository
) {
    suspend operator fun invoke(): Result<MemoryPoolStatus> = repository.getMemoryPoolStatus()
}

class GetLogFilePathUseCase @Inject constructor(
    private val repository: CameraDiagnosticsRepository
) {
    suspend operator fun invoke(): Result<String> = repository.getLogFilePath()
}

class IsLogFileActiveUseCase @Inject constructor(
    private val repository: CameraDiagnosticsRepository
) {
    suspend operator fun invoke(): Result<Boolean> = repository.isLogFileActive()
}

class IsOperationCanceledUseCase @Inject constructor(
    private val repository: CameraDiagnosticsRepository
) {
    suspend operator fun invoke(): Result<Boolean> = repository.isOperationCanceled()
}

class GetTransferProgressUseCase @Inject constructor(
    private val repository: CameraDiagnosticsRepository
) {
    operator fun invoke(): Flow<TransferProgress> = repository.getTransferProgress()
}

class GetStatusMessagesUseCase @Inject constructor(
    private val repository: CameraDiagnosticsRepository
) {
    operator fun invoke(): Flow<String> = repository.getStatusMessages()
}

class RegisterHookCallbackUseCase @Inject constructor(
    private val repository: CameraDiagnosticsRepository
) {
    suspend operator fun invoke(): Result<Boolean> = repository.registerHookCallback()
}

class UnregisterHookCallbackUseCase @Inject constructor(
    private val repository: CameraDiagnosticsRepository
) {
    suspend operator fun invoke(): Result<Boolean> = repository.unregisterHookCallback()
}
