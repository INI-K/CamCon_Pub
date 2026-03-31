package com.inik.camcon.data.datasource.nativesource

import com.inik.camcon.CameraNative
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeDiagnosticsDataSource @Inject constructor() {

    fun diagnoseCameraIssues(): String = CameraNative.diagnoseCameraIssues()

    fun diagnoseUSBConnection(): String = CameraNative.diagnoseUSBConnection()

    fun getErrorHistory(count: Int): String = CameraNative.getErrorHistory(count)

    fun clearErrorHistory() = CameraNative.clearErrorHistory()

    fun getCameraFilePoolCount(): Int = CameraNative.getCameraFilePoolCount()

    fun clearCameraFilePool() = CameraNative.clearCameraFilePool()

    fun getMemoryPoolStatus(): String = CameraNative.getMemoryPoolStatus()

    fun getLogFilePath(): String = CameraNative.getLogFilePath()

    fun isLogFileActive(): Boolean = CameraNative.isLogFileActive()

    fun isOperationCanceled(): Boolean = CameraNative.isOperationCanceled()

    fun registerHookCallback(callback: CameraNative.HookEventCallback): Int =
        CameraNative.registerHookCallback(callback)

    fun unregisterHookCallback() = CameraNative.unregisterHookCallback()

    companion object {
        private const val TAG = "NativeDiagnosticsDS"
    }
}
