package com.inik.camcon.domain.repository

import com.inik.camcon.domain.model.UsbDeviceInfo

interface UsbDeviceRepository {
    fun getCameraDevices(): List<UsbDeviceInfo>
    fun requestPermission(deviceId: String)
}
