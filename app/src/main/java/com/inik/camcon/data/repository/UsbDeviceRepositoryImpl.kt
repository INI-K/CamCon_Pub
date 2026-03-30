package com.inik.camcon.data.repository

import com.inik.camcon.data.datasource.usb.UsbCameraManager
import com.inik.camcon.domain.model.UsbDeviceInfo
import com.inik.camcon.domain.repository.UsbDeviceRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsbDeviceRepositoryImpl @Inject constructor(
    private val usbCameraManager: UsbCameraManager
) : UsbDeviceRepository {
    override fun getCameraDevices(): List<UsbDeviceInfo> {
        return usbCameraManager.getCameraDevices().map { device ->
            UsbDeviceInfo(
                deviceId = device.deviceId.toString(),
                deviceName = device.deviceName ?: "Unknown",
                vendorId = device.vendorId,
                productId = device.productId
            )
        }
    }

    override fun requestPermission(deviceId: String) {
        val device = usbCameraManager.getCameraDevices().find {
            it.deviceId.toString() == deviceId
        }
        if (device != null) {
            usbCameraManager.requestPermission(device)
        }
    }
}
