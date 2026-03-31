package com.inik.camcon.presentation.viewmodel

import com.inik.camcon.domain.model.diagnostics.DiagnosticsReport
import com.inik.camcon.domain.model.diagnostics.MemoryPoolStatus
import com.inik.camcon.domain.model.diagnostics.TransferProgress
import com.inik.camcon.domain.usecase.diagnostics.*
import com.inik.camcon.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraDiagnosticsManager @Inject constructor(
    private val diagnoseCameraIssuesUseCase: DiagnoseCameraIssuesUseCase,
    private val diagnoseUSBConnectionUseCase: DiagnoseUSBConnectionUseCase,
    private val getErrorHistoryUseCase: GetErrorHistoryUseCase,
    private val clearErrorHistoryUseCase: ClearErrorHistoryUseCase,
    private val getCameraFilePoolCountUseCase: GetCameraFilePoolCountUseCase,
    private val clearCameraFilePoolUseCase: ClearCameraFilePoolUseCase,
    private val getMemoryPoolStatusUseCase: GetMemoryPoolStatusUseCase,
    private val getLogFilePathUseCase: GetLogFilePathUseCase,
    private val isLogFileActiveUseCase: IsLogFileActiveUseCase,
    private val isOperationCanceledUseCase: IsOperationCanceledUseCase,
    private val getTransferProgressUseCase: GetTransferProgressUseCase,
    private val getStatusMessagesUseCase: GetStatusMessagesUseCase,
    private val registerHookCallbackUseCase: RegisterHookCallbackUseCase,
    private val unregisterHookCallbackUseCase: UnregisterHookCallbackUseCase,
    @ApplicationScope private val scope: CoroutineScope
) {
    private val _diagnosticsReport = MutableStateFlow<DiagnosticsReport?>(null)
    val diagnosticsReport: StateFlow<DiagnosticsReport?> = _diagnosticsReport.asStateFlow()

    private val _memoryPoolStatus = MutableStateFlow<MemoryPoolStatus?>(null)
    val memoryPoolStatus: StateFlow<MemoryPoolStatus?> = _memoryPoolStatus.asStateFlow()

    fun runFullDiagnostics() {
        scope.launch {
            val cameraIssues = diagnoseCameraIssuesUseCase().getOrDefault("N/A")
            val usbDiag = diagnoseUSBConnectionUseCase().getOrDefault("N/A")
            _diagnosticsReport.value = DiagnosticsReport(cameraIssues, usbDiag)
        }
    }

    suspend fun getErrorHistory(count: Int): Result<String> = getErrorHistoryUseCase(count)

    fun clearErrorHistory() {
        scope.launch { clearErrorHistoryUseCase() }
    }

    fun refreshMemoryPoolStatus() {
        scope.launch {
            getMemoryPoolStatusUseCase().onSuccess {
                _memoryPoolStatus.value = it
            }
        }
    }

    fun clearCameraFilePool() {
        scope.launch { clearCameraFilePoolUseCase() }
    }

    suspend fun getLogFilePath(): Result<String> = getLogFilePathUseCase()

    suspend fun isLogFileActive(): Result<Boolean> = isLogFileActiveUseCase()

    suspend fun isOperationCanceled(): Result<Boolean> = isOperationCanceledUseCase()

    fun getTransferProgress(): Flow<TransferProgress> = getTransferProgressUseCase()

    fun getStatusMessages(): Flow<String> = getStatusMessagesUseCase()

    fun registerHookCallback() {
        scope.launch { registerHookCallbackUseCase() }
    }

    fun unregisterHookCallback() {
        scope.launch { unregisterHookCallbackUseCase() }
    }
}
