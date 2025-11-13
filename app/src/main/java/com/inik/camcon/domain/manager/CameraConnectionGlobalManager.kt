package com.inik.camcon.domain.manager

import com.inik.camcon.domain.model.CameraConnectionType
import com.inik.camcon.domain.model.GlobalCameraConnectionState
import kotlinx.coroutines.flow.StateFlow

interface CameraConnectionGlobalManager {
    val globalConnectionState: StateFlow<GlobalCameraConnectionState>
    val activeConnectionType: StateFlow<CameraConnectionType?>
    val connectionStatusMessage: StateFlow<String>

    fun getCurrentActiveConnectionType(): CameraConnectionType?
    fun isConnectionTypeActive(type: CameraConnectionType): Boolean
    fun isAnyCameraConnected(): Boolean
    fun isApModeConnected(): Boolean
    fun isStaModeConnected(): Boolean
    fun isUsbConnected(): Boolean
    fun setPtpipPhotoCapturedCallback(callback: (String, String) -> Unit)
    fun cleanup()
}