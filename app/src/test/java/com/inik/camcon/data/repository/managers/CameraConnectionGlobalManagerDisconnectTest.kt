package com.inik.camcon.data.repository.managers

import com.inik.camcon.data.datasource.ptpip.PtpipDataSource
import com.inik.camcon.data.datasource.usb.UsbCameraManager
import com.inik.camcon.domain.model.CameraConnectionType
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.PtpipConnectionState
import com.inik.camcon.domain.model.WifiNetworkState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `CameraConnectionGlobalManagerImpl`가 PTP/IP `connectionState`의 DISCONNECTED 전이를
 * 전역 상태로 올바르게 파급하는지 단위 테스트.
 *
 * 설계서: `.claude/_workspace/design_disconnect_watchdog.md` §4.3 / §10-3.
 * 대상: `notifyInvoluntaryPtpipDisconnect`가 `_connectionState = DISCONNECTED`로 내리면,
 *       그걸 구독 중인 GlobalManager가 재계산하여:
 *        - `isAnyConnectionActive = false`
 *        - `activeConnectionType = null`
 *        - `cameraConnectionManager.updatePtpipConnectionStatus(false)` **1회 호출**
 *       하는지 검증한다.
 *
 * 검증 방식(fake로 대체):
 *  - `PtpipDataSource`의 5개 관찰 대상 StateFlow(`connectionState`, `wifiNetworkState`,
 *    `discoveredCameras`, `isApModeForced`)와 `UsbCameraManager.isNativeCameraConnected`를
 *    **실제 MutableStateFlow**로 stub해 주입 → `connectionState`를 CONNECTED→DISCONNECTED로
 *    바꿔 GlobalManager의 `updateGlobalState()` 재계산을 유발한다.
 *  - `appScope`에 `UnconfinedTestDispatcher` scope를 주입해 `launchIn` 컬렉트가 즉시 동작.
 *  - `CameraConnectionGlobalManagerImpl`은 순수 Kotlin(도메인/데이터 매니저)이라 Robolectric 불필요.
 *
 * 단위 테스트 불가 경로(명시): 실제 native 발화→PtpipDataSource→GlobalManager의 end-to-end는
 * JNI 발화가 필요해 실기기 검증(설계 §8.2). 본 테스트는 GlobalManager 입력(connectionState) 대비
 * 출력(전역 상태·updatePtpipConnectionStatus)의 순수 계산만 검증한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CameraConnectionGlobalManagerDisconnectTest {

    private val connectionState = MutableStateFlow(PtpipConnectionState.CONNECTED)
    private val wifiNetworkState =
        MutableStateFlow(WifiNetworkState(true, false, "MyRouter", "192.168.0.50"))
    private val discoveredCameras = MutableStateFlow<List<PtpipCamera>>(emptyList())
    private val isApModeForced = MutableStateFlow(false)
    private val usbConnected = MutableStateFlow(false)

    // 검증 대상 mock — createManager에서 세팅한다.
    private lateinit var lastConnManager: CameraConnectionManager

    private fun createManager(scope: CoroutineScope): CameraConnectionGlobalManagerImpl {
        val ptpipDataSource = mockk<PtpipDataSource>(relaxed = true) {
            every { connectionState } returns this@CameraConnectionGlobalManagerDisconnectTest.connectionState
            every { wifiNetworkState } returns this@CameraConnectionGlobalManagerDisconnectTest.wifiNetworkState
            every { discoveredCameras } returns this@CameraConnectionGlobalManagerDisconnectTest.discoveredCameras
            every { isApModeForced } returns this@CameraConnectionGlobalManagerDisconnectTest.isApModeForced
        }
        val usbCameraManager = mockk<UsbCameraManager>(relaxed = true) {
            every { isNativeCameraConnected } returns usbConnected
        }
        val cameraConnectionManager = mockk<CameraConnectionManager>(relaxed = true)
        lastConnManager = cameraConnectionManager
        return CameraConnectionGlobalManagerImpl(
            ptpipDataSource = ptpipDataSource,
            usbCameraManager = usbCameraManager,
            cameraConnectionManager = cameraConnectionManager,
            appScope = scope
        )
    }

    @Test
    fun `PTPIP CONNECTED에서 DISCONNECTED 전이 시 전역 상태가 비활성으로 내려간다`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        connectionState.value = PtpipConnectionState.CONNECTED
        val manager = createManager(scope)

        // 전이: 비자발적 끊김 → DISCONNECTED
        connectionState.value = PtpipConnectionState.DISCONNECTED

        val global = manager.globalConnectionState.value
        assertFalse("어떤 연결도 활성이 아니어야 한다", global.isAnyConnectionActive)
        assertNull("활성 연결 타입은 null 이어야 한다", manager.activeConnectionType.value)
        assertEquals(PtpipConnectionState.DISCONNECTED, global.ptpipConnectionState)
    }

    @Test
    fun `DISCONNECTED 전이 시 updatePtpipConnectionStatus(false)가 호출된다`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        connectionState.value = PtpipConnectionState.CONNECTED
        val manager = createManager(scope)

        // CONNECTED 초기화 시 true 푸시가 있었을 수 있으므로, 전이 후 false 푸시를 검증한다.
        connectionState.value = PtpipConnectionState.DISCONNECTED

        verify { lastConnManager.updatePtpipConnectionStatus(false) }
    }

    @Test
    fun `USB 병행 연결 중이면 PTPIP DISCONNECTED여도 전역은 활성 USB로 유지된다`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        usbConnected.value = true
        connectionState.value = PtpipConnectionState.CONNECTED
        val manager = createManager(scope)

        connectionState.value = PtpipConnectionState.DISCONNECTED

        // R4 회귀 가드: Wi-Fi death가 USB 세션을 죽이면 안 된다.
        val global = manager.globalConnectionState.value
        assertEquals(CameraConnectionType.USB, manager.activeConnectionType.value)
        assertEquals(true, global.isAnyConnectionActive)
    }
}
