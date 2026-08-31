package com.inik.camcon.domain.usecase.camera

import com.inik.camcon.di.IoDispatcher
import com.inik.camcon.domain.manager.CameraConnectionGlobalManager
import com.inik.camcon.domain.model.CameraConnectionType
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.repository.PtpipPreferencesRepository
import com.inik.camcon.domain.repository.PtpipRepository
import com.inik.camcon.domain.repository.UnattendedSessionRepository
import com.inik.camcon.domain.repository.UsbDeviceRepository
import com.inik.camcon.domain.util.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Clock
import javax.inject.Inject

/**
 * 프로세스가 죽었다 되살아난 뒤 무인 수신 세션을 다시 세운다.
 *
 * 시스템이 서비스를 재기동하면 메모리의 연결은 이미 사라진 뒤다. 남은 근거는 디스크의 세션
 * 기록([UnattendedSessionRepository])뿐이므로, 그 기록이 가리키는 연결 방식으로만 한 번
 * 되살려 본다. 아무 때나 연결을 시도하면 사용자가 이미 끝낸 세션을 되살리는 셈이 된다.
 *
 * 시도 구간에는 [RECOVERY_CAP_MS] 상한이 있다. 상한 없는 복구는 붙지 않는 카메라를 향해
 * WakeLock 을 쥔 채 배터리만 먹는다. 시각 계산은 [Clock] 을 주입받으므로 테스트가 45초를
 * 실제로 기다리지 않고 상한 경과를 검증할 수 있다.
 */
class ResumeUnattendedSessionUseCase @Inject constructor(
    private val sessionRepository: UnattendedSessionRepository,
    private val usbDeviceRepository: UsbDeviceRepository,
    private val ptpipPreferencesRepository: PtpipPreferencesRepository,
    private val ptpipRepository: PtpipRepository,
    private val globalConnectionManager: CameraConnectionGlobalManager,
    private val logger: Logger,
    private val clock: Clock,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    companion object {
        private const val TAG = "무인세션복구"

        /** 복구 시도 구간의 상한. 대기 상태는 반드시 상한을 갖는다. */
        const val RECOVERY_CAP_MS = 45_000L
    }

    /**
     * @return 복구에 성공해 다시 수신할 수 있게 됐으면 true.
     */
    suspend operator fun invoke(): Boolean = withContext(ioDispatcher) {
        val saved = sessionRepository.load()
        if (saved == null) {
            logger.d(TAG, "되살릴 세션 기록이 없다 - 복구하지 않는다")
            return@withContext false
        }

        if (globalConnectionManager.globalConnectionState.value.isAnyConnectionActive) {
            logger.d(TAG, "이미 연결이 살아 있다 - 복구 불필요")
            return@withContext true
        }

        val deadline = clock.millis() + RECOVERY_CAP_MS
        logger.i(TAG, "세션 복구 시작(방식=${saved.connectionType}, 상한 ${RECOVERY_CAP_MS}ms)")

        val recovered = withTimeoutOrNull(RECOVERY_CAP_MS) {
            when (saved.connectionType) {
                CameraConnectionType.USB -> resumeUsb(deadline)
                CameraConnectionType.AP_MODE, CameraConnectionType.STA_MODE -> resumeWifi(deadline)
            }
        } ?: false

        logger.i(TAG, "세션 복구 결과: ${if (recovered) "성공" else "실패"}")
        recovered
    }

    /**
     * USB 복구. **장치가 실제로 붙어 있고 권한도 이미 있을 때만** 재초기화한다.
     *
     * 권한이 없으면 시도조차 하지 않는다 — 권한 요청은 화면이 있어야 하는 대화상자라,
     * 무인 상태에서 띄우면 아무도 응답하지 못한 채 상한까지 시간만 태운다.
     */
    private suspend fun resumeUsb(deadline: Long): Boolean {
        if (usbDeviceRepository.getCameraDevices().isEmpty()) {
            logger.d(TAG, "USB 카메라가 붙어 있지 않다 - 복구 중단")
            return false
        }
        if (!usbDeviceRepository.hasPermissionForAttachedCamera()) {
            logger.w(TAG, "USB 권한이 없다 - 무인 상태에서는 요청하지 않고 복구를 포기한다")
            return false
        }

        usbDeviceRepository.connectToFirstCamera()
        return awaitConnection(deadline)
    }

    /**
     * Wi-Fi 복구. 자동 재연결이 켜져 있을 때만 마지막 카메라로 **한 번** 시도한다.
     * 설정이 꺼져 있는데 배경에서 붙으면 사용자가 끈 동작을 앱이 되살리는 셈이 된다.
     */
    private suspend fun resumeWifi(deadline: Long): Boolean {
        if (!ptpipPreferencesRepository.isAutoReconnectEnabled.first()) {
            logger.d(TAG, "자동 재연결이 꺼져 있다 - Wi-Fi 복구하지 않는다")
            return false
        }

        val lastCamera = ptpipPreferencesRepository.getLastConnectedCameraInfo()
        if (lastCamera == null) {
            logger.d(TAG, "마지막 연결 카메라 기록이 없다 - Wi-Fi 복구 중단")
            return false
        }

        val (ip, name) = lastCamera
        val port = ptpipPreferencesRepository.ptpipPort.first()
        val connected = ptpipRepository.connectToCamera(
            PtpipCamera(ipAddress = ip, port = port, name = name ?: ip)
        )
        if (!connected) return false

        return awaitConnection(deadline)
    }

    /**
     * 남은 상한 안에 연결이 실제로 살아나는지 지켜본다. 연결 요청이 성공을 돌려줘도 전역
     * 연결 상태가 서는 데는 시간이 걸리고, 촬영 게이트가 보는 값은 그 전역 상태다.
     */
    private suspend fun awaitConnection(deadline: Long): Boolean {
        val remaining = deadline - clock.millis()
        if (remaining <= 0) return false

        return withTimeoutOrNull(remaining) {
            globalConnectionManager.globalConnectionState.first { it.isAnyConnectionActive }
            true
        } ?: false
    }
}
