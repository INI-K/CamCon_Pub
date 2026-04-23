package com.inik.camcon.domain.repository

import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.PtpipCameraInfo

/**
 * PTP/IP 디버그 테스트 기능을 위한 Repository 인터페이스.
 *
 * data 레이어의 내부 컴포넌트(ConnectionManager, NikonAuthService)를
 * 직접 노출하지 않고 상위 수준 메서드로 래핑한다.
 */
interface PtpipDebugRepository {

    /** 기본 PTP/IP 연결 테스트 (소켓 연결 + 디바이스 정보 획득) */
    suspend fun testBasicConnection(camera: PtpipCamera): DebugTestResult

    /** Nikon Phase 1 인증 테스트 */
    suspend fun testPhase1Auth(camera: PtpipCamera): Boolean

    /** Nikon Phase 2 인증 테스트 */
    suspend fun testPhase2Auth(camera: PtpipCamera): Boolean

    /** Nikon 개별 명령 테스트 */
    suspend fun testNikonCommand(camera: PtpipCamera, command: String): Boolean

    /** 소켓 연결 테스트 */
    suspend fun testSocketConnection(camera: PtpipCamera): Boolean

    /** 포트 스캔 */
    suspend fun scanPorts(ipAddress: String): List<Int>
}

/** 기본 연결 테스트 결과 */
data class DebugTestResult(
    val success: Boolean,
    val deviceInfo: PtpipCameraInfo?,
    val message: String
)
