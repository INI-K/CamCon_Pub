package com.inik.camcon.domain.repository

import com.inik.camcon.domain.model.UsbDeviceInfo
import kotlinx.coroutines.flow.StateFlow

interface UsbDeviceRepository {
    fun getCameraDevices(): List<UsbDeviceInfo>
    fun requestPermission(deviceId: String)

    // USB 카메라 연결 관리용 메서드
    val connectedDeviceCount: StateFlow<Int>
    val hasUsbPermission: StateFlow<Boolean>
    val isNativeCameraConnected: StateFlow<Boolean>
    fun requestPermissionForFirstDevice()
    suspend fun connectToFirstCamera()
    fun getCurrentDeviceInfo(): UsbDeviceInfo?
    suspend fun checkPowerStateAndTest()
    fun cleanup()
}
