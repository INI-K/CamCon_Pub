package com.inik.camcon.data.repository

import com.inik.camcon.data.datasource.ptpip.PtpipDataSource
import com.inik.camcon.data.datasource.usb.UsbCameraManager
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.PtpipConnectionState
import com.inik.camcon.domain.model.WifiNetworkState
import com.inik.camcon.domain.repository.CameraConnectionStateProvider
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraConnectionStateProviderImpl @Inject constructor(
    private val ptpipDataSource: PtpipDataSource,
    private val usbCameraManager: UsbCameraManager
) : CameraConnectionStateProvider {
    override val isUsbCameraConnected: StateFlow<Boolean>
        get() = usbCameraManager.isNativeCameraConnected
    override val ptpipConnectionState: StateFlow<PtpipConnectionState>
        get() = ptpipDataSource.connectionState
    override val wifiNetworkState: StateFlow<WifiNetworkState>
        get() = ptpipDataSource.wifiNetworkState
    override val discoveredCameras: StateFlow<List<PtpipCamera>>
        get() = ptpipDataSource.discoveredCameras
}
