package com.inik.camcon.data.network.ptpip.connection
import android.util.Log
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.PtpipCameraInfo
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class PtpipConnectionManager @Inject constructor() {
    companion object {
        private const val TAG = "PtpipConnectionManager"
    }
    private var currentSocket: Socket? = null
    private var currentInfo: PtpipCameraInfo? = null
    fun establishConnection(camera: PtpipCamera): Boolean {
        return try {
            closeConnections()
            val socket = Socket()
            socket.connect(InetSocketAddress(camera.ipAddress, camera.port), 1500)
            currentSocket = socket
            val isNikon = camera.name.contains("nikon", ignoreCase = true)
            currentInfo = PtpipCameraInfo(
                manufacturer = if (isNikon) "Nikon" else "Unknown",
                model = camera.name,
                version = "1.0",
                serialNumber = camera.ipAddress
            )
            true
        } catch (e: Exception) {
            Log.w(TAG, "PTP/IP 연결 실패: ${camera.ipAddress}:${camera.port}", e)
            false
        }
    }
    fun getDeviceInfo(): PtpipCameraInfo? = currentInfo
    fun closeSession() {
        closeConnections(true)
    }
    fun closeConnections(closeSession: Boolean = true) {
        if (closeSession) {
            runCatching { currentSocket?.close() }
            currentSocket = null
        }
        currentInfo = null
    }
}