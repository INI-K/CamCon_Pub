package com.inik.camcon.data.network.ptpip.authentication

import android.util.Log
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.utils.Constants
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 니콘 STA 모드 PTP/IP 도달성 / 디버그 probe.
 *
 * 중요: 실제 니콘 STA 인증(0x952b / 0x935a / GetDeviceInfo / OpenSession) 시퀀스는
 * 네이티브 측 `performNikonStaAuthentication`(camera_ptpip_commands.cpp)이 담당한다.
 * 이 시퀀스는 `CameraNative.initCameraWithPtpip` 진입 시 `gp_camera_init` 실패 시
 * fallback으로 자동 호출되므로 Kotlin에서 별도 인증 호출이 필요 없다.
 *
 * 이 클래스는 다음 두 가지로 책임을 한정한다:
 * 1. **카메라 도달성 ping** ([isReachable]) — PTP/IP 포트가 열려 있는지 TCP connect 시도
 * 2. **개발자 진단** ([scanPorts]) — UI의 "고급 진단" 메뉴 디버그 보조
 */
@Singleton
class NikonAuthenticationService @Inject constructor() {
    companion object {
        private const val TAG = "NikonAuthService"
        private const val REACHABILITY_TIMEOUT_MS = 1500
        private const val PORT_SCAN_TIMEOUT_MS = 300
        private val DEBUG_PORTS = listOf(Constants.Network.PTPIP_DEFAULT_PORT, 80, 443, 8080)
    }

    /**
     * TCP 도달성 확인. PTP/IP 핸드셰이크는 수행하지 않는다.
     */
    fun isReachable(camera: PtpipCamera): Boolean = openProbe(camera.ipAddress, camera.port)

    // ── 디버그용 ──────────────────────────────────────────────────────────
    // 아래 메서드들은 PtpipViewModel의 "고급 진단" 디버그 액션에서만 사용한다.
    // 정상 연결 경로(PtpipDataSource.connectToCamera)에서는 호출하지 않는다.

    /** 레거시 호환 — 본 동작은 [isReachable]과 동일. */
    @Deprecated(
        "Use isReachable. STA authentication is performed by native code.",
        ReplaceWith("isReachable(camera)")
    )
    fun performStaAuthentication(camera: PtpipCamera): Boolean = isReachable(camera)

    fun testPhase1Authentication(camera: PtpipCamera): Boolean = isReachable(camera)
    fun testPhase2Authentication(camera: PtpipCamera): Boolean = isReachable(camera)
    fun testNikon952bCommand(camera: PtpipCamera): Boolean = isReachable(camera)
    fun testNikon935aCommand(camera: PtpipCamera): Boolean = isReachable(camera)
    fun testGetDeviceInfo(camera: PtpipCamera): Boolean = isReachable(camera)
    fun testOpenSession(camera: PtpipCamera): Boolean = isReachable(camera)
    fun testSocketConnection(camera: PtpipCamera): Boolean = isReachable(camera)

    /**
     * 디버그 포트 스캔. PTP/IP + 일반적인 디버그 포트 몇 개를 짧은 타임아웃으로 확인.
     */
    fun scanPorts(ipAddress: String): List<Int> {
        val open = DEBUG_PORTS.filter { port -> openProbe(ipAddress, port, PORT_SCAN_TIMEOUT_MS) }
        Log.d(TAG, "포트 스캔 결과 $ipAddress -> $open")
        return open
    }

    // ─────────────────────────────────────────────────────────────────────

    private fun openProbe(ip: String, port: Int, timeoutMs: Int = REACHABILITY_TIMEOUT_MS): Boolean {
        return try {
            Socket().use { socket -> socket.connect(InetSocketAddress(ip, port), timeoutMs) }
            true
        } catch (e: Exception) {
            Log.v(TAG, "도달성 실패 $ip:$port (${e.javaClass.simpleName})")
            false
        }
    }
}
