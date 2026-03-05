package com.inik.camcon.data.datasource.usb
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class UsbDeviceDetector @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "UsbDeviceDetector"
        private const val ACTION_USB_PERMISSION = "com.inik.camcon.USB_PERMISSION"
    }
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val _connectedDevices = MutableStateFlow<List<UsbDevice>>(emptyList())
    val connectedDevices: StateFlow<List<UsbDevice>> = _connectedDevices.asStateFlow()
    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()
    private var disconnectionCallback: ((UsbDevice) -> Unit)? = null
    init {
        refresh()
    }
    fun setDisconnectionCallback(callback: (UsbDevice) -> Unit) {
        disconnectionCallback = callback
    }
    fun getCameraDevices(): List<UsbDevice> {
        refresh()
        return _connectedDevices.value
    }
    fun requestPermission(device: UsbDevice) {
        try {
            val intent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION_USB_PERMISSION),
                PendingIntent.FLAG_IMMUTABLE
            )
            usbManager.requestPermission(device, intent)
            _hasPermission.value = usbManager.hasPermission(device)
        } catch (e: Exception) {
            Log.w(TAG, "USB 권한 요청 실패", e)
        }
    }
    fun refresh() {
        val devices = usbManager.deviceList.values.toList()
        val previous = _connectedDevices.value
        _connectedDevices.value = devices
        _hasPermission.value = devices.any { usbManager.hasPermission(it) }
        if (previous.isNotEmpty() && devices.isEmpty()) {
            previous.firstOrNull()?.let { disconnectionCallback?.invoke(it) }
        }
    }
    fun cleanup() {
        disconnectionCallback = null
    }
}