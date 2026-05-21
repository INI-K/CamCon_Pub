package com.inik.camcon.data.network.ptpip.connection

import android.util.Log
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.PtpipCameraInfo
import com.inik.camcon.utils.Constants
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PTP/IP TCP 소켓 연결 관리.
 *
 * 책임: PTP/IP 포트(15740 기본)에 단순 TCP 연결을 열고 닫는다.
 * **PTP/IP InitCommandRequest 핸드셰이크는 수행하지 않는다** — 그 책임은 네이티브의
 * `CameraNative.initCameraWithPtpip` / `initCameraForAPMode`(libgphoto2)에 위임한다.
 *
 * 이 클래스의 용도:
 * - 연결 가능성을 빠르게 확인하기 위한 reachability check
 * - 디버그 UI에서 "기본 연결 테스트" 액션의 백엔드
 *
 * 따라서 [getDeviceInfo]가 반환하는 [PtpipCameraInfo]는 **소켓 응답이 아닌 호출자가 제공한
 * 메타데이터(SSID 등)에서 구성된 가벼운 placeholder**다. 진짜 디바이스 정보는 libgphoto2가 채운다.
 */
@Singleton
class PtpipConnectionManager @Inject constructor() {
    companion object {
        private const val TAG = "PtpipConnectionManager"
        private const val CONNECT_TIMEOUT_MS = 1500
    }

    private var currentSocket: Socket? = null
    private var currentInfo: PtpipCameraInfo? = null

    /**
     * 카메라 PTP/IP 포트에 TCP 연결을 시도한다.
     *
     * @return 연결 성공 여부. true여도 PTP/IP 핸드셰이크는 아직 수행되지 않은 상태.
     */
    fun establishConnection(camera: PtpipCamera): Boolean {
        return try {
            closeConnections()
            val socket = Socket()
            socket.connect(InetSocketAddress(camera.ipAddress, camera.port), CONNECT_TIMEOUT_MS)
            currentSocket = socket
            currentInfo = PtpipCameraInfo(
                manufacturer = "Unknown",
                model = camera.name,
                version = "n/a",
                serialNumber = camera.ipAddress
            )
            true
        } catch (e: Exception) {
            Log.w(TAG, "PTP/IP 연결 실패: ${camera.ipAddress}:${camera.port}", e)
            false
        }
    }

    /**
     * 도달성만 확인하고 즉시 닫는 가벼운 probe. 디바이스 정보는 채우지 않는다.
     */
    fun probeReachability(ip: String, port: Int = Constants.Network.PTPIP_DEFAULT_PORT): Boolean = try {
        Socket().use { s -> s.connect(InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS) }
        true
    } catch (e: Exception) {
        false
    }

    fun getDeviceInfo(): PtpipCameraInfo? = currentInfo

    fun closeSession() {
        closeConnections(closeSession = true)
    }

    fun closeConnections(closeSession: Boolean = true) {
        if (closeSession) {
            runCatching { currentSocket?.close() }
            currentSocket = null
        }
        currentInfo = null
    }
}
