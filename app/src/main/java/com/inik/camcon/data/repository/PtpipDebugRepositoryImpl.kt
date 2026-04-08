package com.inik.camcon.data.repository

import com.inik.camcon.data.datasource.ptpip.PtpipDataSource
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.repository.DebugTestResult
import com.inik.camcon.domain.repository.PtpipDebugRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PtpipDataSource의 내부 컴포넌트(ConnectionManager, NikonAuthService)를
 * 상위 수준 메서드로 래핑하여 domain의 PtpipDebugRepository 인터페이스를 구현한다.
 *
 * presentation 레이어에서 getConnectionManager(), getNikonAuthService() 같은
 * data 내부 컴포넌트에 직접 접근하지 않도록 캡슐화한다.
 */
@Singleton
class PtpipDebugRepositoryImpl @Inject constructor(
    private val ptpipDataSource: PtpipDataSource
) : PtpipDebugRepository {

    override suspend fun testBasicConnection(camera: PtpipCamera): DebugTestResult {
        val connectionManager = ptpipDataSource.getConnectionManager()
        val success = connectionManager.establishConnection(camera)
        if (!success) {
            return DebugTestResult(
                success = false,
                deviceInfo = null,
                message = "기본 PTPIP 연결 실패"
            )
        }
        val deviceInfo = connectionManager.getDeviceInfo()
        // 연결 유지 (카메라 Wi-Fi 종료 방지)
        return DebugTestResult(
            success = true,
            deviceInfo = deviceInfo,
            message = if (deviceInfo != null) {
                "기본 연결 성공: ${deviceInfo.manufacturer} ${deviceInfo.model}"
            } else {
                "기본 연결 성공하지만 디바이스 정보 없음"
            }
        )
    }

    override suspend fun testPhase1Auth(camera: PtpipCamera): Boolean =
        ptpipDataSource.getNikonAuthService().testPhase1Authentication(camera)

    override suspend fun testPhase2Auth(camera: PtpipCamera): Boolean =
        ptpipDataSource.getNikonAuthService().testPhase2Authentication(camera)

    override suspend fun testNikonCommand(camera: PtpipCamera, command: String): Boolean {
        val authService = ptpipDataSource.getNikonAuthService()
        return when (command) {
            "0x952b" -> authService.testNikon952bCommand(camera)
            "0x935a" -> authService.testNikon935aCommand(camera)
            "GetDeviceInfo" -> authService.testGetDeviceInfo(camera)
            "OpenSession" -> authService.testOpenSession(camera)
            else -> false
        }
    }

    override suspend fun testSocketConnection(camera: PtpipCamera): Boolean =
        ptpipDataSource.getNikonAuthService().testSocketConnection(camera)

    override suspend fun scanPorts(ipAddress: String): List<Int> =
        ptpipDataSource.getNikonAuthService().scanPorts(ipAddress)
}
