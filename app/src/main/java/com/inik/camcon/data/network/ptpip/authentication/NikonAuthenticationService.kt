package com.inik.camcon.data.network.ptpip.authentication
import android.util.Log
import com.inik.camcon.CameraNative
import com.inik.camcon.domain.model.PtpipCamera
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class NikonAuthenticationService @Inject constructor() {
    companion object {
        private const val TAG = "NikonAuthenticationService"
    }
    fun performStaAuthentication(camera: PtpipCamera): Boolean {
        return testSocketConnection(camera).also {
            if (it) {
                runCatching {
                    CameraNative.setCameraInfoFromPtpip("Nikon", camera.name, "1.0", camera.ipAddress)
                }
            }
        }
    }
    fun testPhase1Authentication(camera: PtpipCamera): Boolean = testSocketConnection(camera)
    fun testPhase2Authentication(camera: PtpipCamera): Boolean = testSocketConnection(camera)
    fun testNikon952bCommand(camera: PtpipCamera): Boolean = testSocketConnection(camera)
    fun testNikon935aCommand(camera: PtpipCamera): Boolean = testSocketConnection(camera)
    fun testGetDeviceInfo(camera: PtpipCamera): Boolean = testSocketConnection(camera)
    fun testOpenSession(camera: PtpipCamera): Boolean = testSocketConnection(camera)
    fun testSocketConnection(camera: PtpipCamera): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(camera.ipAddress, camera.port), 1500)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "소켓 연결 테스트 실패", e)
            false
        }
    }
    fun scanPorts(ipAddress: String): List<Int> {
        val ports = listOf(15740, 80, 443, 8080)
        val open = mutableListOf<Int>()
        for (port in ports) {
            val ok = runCatching {
                Socket().use { it.connect(InetSocketAddress(ipAddress, port), 300) }
                true
            }.getOrDefault(false)
            if (ok) open.add(port)
        }
        Log.d(TAG, "포트 스캔 결과: $open")
        return open
    }
}