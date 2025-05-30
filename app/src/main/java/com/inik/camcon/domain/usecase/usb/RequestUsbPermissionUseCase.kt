package com.inik.camcon.domain.usecase.usb

import android.hardware.usb.UsbDevice
import com.inik.camcon.data.datasource.usb.UsbCameraManager
import javax.inject.Inject

class RequestUsbPermissionUseCase @Inject constructor(
    private val usbCameraManager: UsbCameraManager
) {
    operator fun invoke(device: UsbDevice) {
        usbCameraManager.requestPermission(device)
    }
}