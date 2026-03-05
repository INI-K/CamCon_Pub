package com.inik.camcon.data.datasource.usb
import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class UsbConnectionManager @Inject constructor(
    context: Context
) {
    companion object {
        private const val TAG = "UsbConnectionManager"
    }
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var currentDevice: UsbDevice? = null
    private var currentConnection: UsbDeviceConnection? = null
    private var disconnectionCallback: (() -> Unit)? = null
    private val _isNativeCameraConnected = MutableStateFlow(false)
    val isNativeCameraConnected: StateFlow<Boolean> = _isNativeCameraConnected.asStateFlow()
    fun setDisconnectionCallback(callback: () -> Unit) {
        disconnectionCallback = callback
    }
    fun connectToCamera(device: UsbDevice) {
        disconnectCamera()
        try {
            currentConnection = usbManager.openDevice(device)
            currentDevice = device
            _isNativeCameraConnected.value = currentConnection != null
            Log.d(TAG, "USB 연결 상태: ${_isNativeCameraConnected.value}")
        } catch (e: Exception) {
            Log.e(TAG, "USB 연결 실패", e)
            _isNativeCameraConnected.value = false
        }
    }
    fun disconnectCamera() {
        try {
            currentConnection?.close()
        } catch (_: Exception) {
        } finally {
            currentConnection = null
            currentDevice = null
            _isNativeCameraConnected.value = false
        }
    }
    fun handleUsbDisconnection() {
        disconnectCamera()
        disconnectionCallback?.invoke()
    }
    fun getFileDescriptor(): Int? = currentConnection?.fileDescriptor
    fun getCurrentDevice(): UsbDevice? = currentDevice
    fun cleanup() {
        try {
            currentConnection?.close()
        } catch (_: Exception) {
        }
        currentConnection = null
        currentDevice = null
        _isNativeCameraConnected.value = false
        disconnectionCallback = null
    }
}