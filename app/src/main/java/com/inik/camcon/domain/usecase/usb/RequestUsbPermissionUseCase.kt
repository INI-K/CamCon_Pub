package com.inik.camcon.domain.usecase.usb

import com.inik.camcon.domain.repository.UsbDeviceRepository
import javax.inject.Inject

class RequestUsbPermissionUseCase @Inject constructor(
    private val usbDeviceRepository: UsbDeviceRepository
) {
    operator fun invoke(deviceId: String) {
        usbDeviceRepository.requestPermission(deviceId)
    }
}
