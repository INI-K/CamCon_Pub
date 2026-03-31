package com.inik.camcon.domain.model.diagnostics

data class DiagnosticsReport(
    val cameraIssues: String,
    val usbDiagnostics: String
)

data class MemoryPoolStatus(
    val activeCount: Int,
    val totalAllocated: Long,
    val details: String
)

data class TransferProgress(
    val current: Float,
    val total: Float,
    val percentage: Int,
    val description: String
)
