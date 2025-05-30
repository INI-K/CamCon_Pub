package com.inik.camcon.domain.usecase.usb

import android.hardware.usb.UsbDevice
import com.inik.camcon.data.datasource.usb.UsbCameraManager
import javax.inject.Inject

class RefreshUsbDevicesUseCase @Inject constructor(
    private val usbCameraManager: UsbCameraManager
) {
    operator fun invoke(): List<UsbDevice> {
        return usbCameraManager.getCameraDevices()
    }
}