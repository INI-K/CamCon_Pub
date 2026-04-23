package com.inik.camcon.data.repository

import com.inik.camcon.data.datasource.usb.UsbCameraManager
import com.inik.camcon.domain.model.UsbDeviceInfo
import com.inik.camcon.domain.repository.UsbDeviceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
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

    override val connectedDeviceCount: StateFlow<Int>
        get() = object : StateFlow<Int> {
            private val source = usbCameraManager.connectedDevices
            override val value: Int get() = source.value.size
            override val replayCache: List<Int> get() = listOf(value)
            override suspend fun collect(collector: kotlinx.coroutines.flow.FlowCollector<Int>): Nothing {
                source.collect { collector.emit(it.size) }
            }
        }

    override val hasUsbPermission: StateFlow<Boolean>
        get() = usbCameraManager.hasUsbPermission

    override val isNativeCameraConnected: StateFlow<Boolean>
        get() = usbCameraManager.isNativeCameraConnected

    override fun requestPermissionForFirstDevice() {
        val devices = usbCameraManager.getCameraDevices()
        if (devices.isNotEmpty()) {
            usbCameraManager.requestPermission(devices.first())
        }
    }

    override suspend fun connectToFirstCamera() {
        val devices = usbCameraManager.getCameraDevices()
        if (devices.isNotEmpty()) {
            withContext(Dispatchers.Main) {
                usbCameraManager.connectToCamera(devices.first())
            }
        }
    }

    override fun getCurrentDeviceInfo(): UsbDeviceInfo? {
        val device = usbCameraManager.getCurrentDevice() ?: return null
        return UsbDeviceInfo(
            deviceId = device.deviceId.toString(),
            deviceName = device.deviceName ?: "Unknown",
            vendorId = device.vendorId,
            productId = device.productId
        )
    }

    override suspend fun checkPowerStateAndTest() {
        usbCameraManager.checkPowerStateAndTest()
    }

    override fun cleanup() {
        usbCameraManager.cleanup()
    }
}
