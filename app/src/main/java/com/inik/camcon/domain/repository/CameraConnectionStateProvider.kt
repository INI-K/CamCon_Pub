package com.inik.camcon.domain.repository

import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.PtpipConnectionState
import com.inik.camcon.domain.model.WifiNetworkState
import kotlinx.coroutines.flow.StateFlow

interface CameraConnectionStateProvider {
    val isUsbCameraConnected: StateFlow<Boolean>
    val ptpipConnectionState: StateFlow<PtpipConnectionState>
    val wifiNetworkState: StateFlow<WifiNetworkState>
    val discoveredCameras: StateFlow<List<PtpipCamera>>
}
