package com.inik.camcon.domain.model

data class UsbDeviceInfo(
    val deviceId: String,
    val deviceName: String,
    val vendorId: Int,
    val productId: Int
)
