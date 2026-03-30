package com.inik.camcon.domain.usecase.usb

import com.inik.camcon.domain.model.UsbDeviceInfo
import com.inik.camcon.domain.repository.UsbDeviceRepository
import javax.inject.Inject

class RefreshUsbDevicesUseCase @Inject constructor(
    private val usbDeviceRepository: UsbDeviceRepository
) {
    operator fun invoke(): List<UsbDeviceInfo> {
        return usbDeviceRepository.getCameraDevices()
    }
}
