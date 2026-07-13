package com.inik.camcon.data.datasource.ptpip

import android.app.Application
import com.inik.camcon.domain.model.PtpipConnectionState
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `PtpipDataSource` 연결 상태 복원 회귀 테스트 (감사 확정 MAJOR 2건).
 *
 *  (C) "ERROR 상태 고착 — 자동 재연결/폴링 체인 영구 차단":
 *      연결/자동 재연결 실패로 ERROR가 되면 폴링(WifiMonitoringService)·handleNetworkStateChange
 *      재연결이 전부 `== DISCONNECTED` 게이트라 카메라가 다시 켜져도 자동 연결이 영구 정지했다.
 *      수정: `startErrorStateRecovery`가 ERROR를 잠시 유지(UI 피드백) 후 DISCONNECTED로 복원해
 *      자동 경로를 되살린다.
 *
 *  (D) "연결 중 코루틴 취소 시 CONNECTING 고착(상태 미복원)":
 *      connectToCameraInternal이 취소 시 CancellationException을 rethrow만 하고 상태를 안 내려
 *      CONNECTING에 영구 고착됐다. 수정: finally에서 `resetIfStuckConnecting()`으로 복원.
 *      실제 취소 경로는 CameraNative(JNI) 네이티브 호출을 거쳐 호스트 JVM에서 구동 불가하므로
 *      (CameraNative.init이 System.loadLibrary → UnsatisfiedLinkError), finally가 호출하는 순수
 *      Kotlin 헬퍼의 상태 전이 불변식을 검증한다.
 *
 * 테스트 인프라는 `PtpipDataSourceInvoluntaryDisconnectTest`와 동일(스텁 Application + relaxed mock +
 * 테스트 디스패처 주입 → init 코루틴을 advanceUntilIdle로 관측). 검증은 StateFlow(_connectionState) 전이.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PtpipDataSourceStateRecoveryTest {

    private fun createDataSource(scope: CoroutineScope, dispatcher: TestDispatcher): PtpipDataSource {
        val context: android.content.Context =
            org.robolectric.RuntimeEnvironment.getApplication()
        return PtpipDataSource(
            context = context,
            discoveryService = mockk(relaxed = true),
            connectionManager = mockk(relaxed = true),
            nikonAuthService = mockk(relaxed = true),
            wifiHelper = mockk(relaxed = true),
            cameraEventManager = mockk(relaxed = true),
            cameraStateObserver = mockk(relaxed = true),
            photoDownloadManager = mockk(relaxed = true),
            autoConnectManager = mockk(relaxed = true),
            autoConnectTaskRunnerProvider = mockk(relaxed = true),
            ptpipPreferencesDataSource = mockk(relaxed = true),
            tetherService = mockk(relaxed = true),
            nativeCameraDataSource = mockk(relaxed = true),
            libgphoto2PluginInstaller = mockk(relaxed = true),
            coroutineScope = scope,
            ioDispatcher = dispatcher
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun setConnectionState(target: PtpipDataSource, state: PtpipConnectionState) {
        val f = PtpipDataSource::class.java.getDeclaredField("_connectionState")
        f.isAccessible = true
        val flow =
            f.get(target) as kotlinx.coroutines.flow.MutableStateFlow<PtpipConnectionState>
        flow.value = state
    }

    private fun invokePrivate(target: PtpipDataSource, name: String) {
        val m = PtpipDataSource::class.java.getDeclaredMethod(name)
        m.isAccessible = true
        m.invoke(target)
    }

    // ───────────────────────── (C) ERROR 자동 복원 ─────────────────────────
    // scheduleErrorReset()은 연결 실패(connectToCamera)·자동 재연결 소진(attemptAutoReconnect)
    // 경로에서 호출된다. 그 경로 전체는 CameraNative(JNI)를 거쳐 호스트 JVM에서 구동 불가하므로,
    // 예약 헬퍼의 지연-복원 불변식을 reflection으로 직접 구동해 검증한다.

    @Test
    fun `scheduleErrorReset은 지연 후 ERROR를 DISCONNECTED로 복원해 자동 재연결_폴링을 허용한다`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val ds = createDataSource(scope, dispatcher)

        setConnectionState(ds, PtpipConnectionState.ERROR)
        invokePrivate(ds, "scheduleErrorReset")
        runCurrent() // 예약 코루틴이 지연 대기에 진입(아직 시간 미경과)

        // 즉시 복원 아님 — ERROR를 잠시 유지해 UI 피드백을 준다.
        assertEquals(PtpipConnectionState.ERROR, ds.connectionState.value)

        advanceUntilIdle() // 복원 지연 경과
        assertEquals(PtpipConnectionState.DISCONNECTED, ds.connectionState.value)

        scope.cancel()
    }

    @Test
    fun `scheduleErrorReset은 그 사이 ERROR가 아니게 되면 복원하지 않는다(멱등 가드)`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val ds = createDataSource(scope, dispatcher)

        setConnectionState(ds, PtpipConnectionState.ERROR)
        invokePrivate(ds, "scheduleErrorReset")
        runCurrent()

        // 지연 도중 새 시도가 CONNECTED로 올려두면, 복원 본문은 CONNECTED를 DISCONNECTED로 끊지 않아야 한다.
        setConnectionState(ds, PtpipConnectionState.CONNECTED)
        advanceUntilIdle()

        assertEquals(PtpipConnectionState.CONNECTED, ds.connectionState.value)
        scope.cancel()
    }

    // ─────────────────── (D) CONNECTING 고착 복원(finally 헬퍼) ───────────────────

    @Test
    fun `resetIfStuckConnecting은 CONNECTING을 DISCONNECTED로 복원한다`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val ds = createDataSource(scope, dispatcher)

        setConnectionState(ds, PtpipConnectionState.CONNECTING)
        invokePrivate(ds, "resetIfStuckConnecting")

        assertEquals(PtpipConnectionState.DISCONNECTED, ds.connectionState.value)
        scope.cancel()
    }

    @Test
    fun `resetIfStuckConnecting은 종료 상태(CONNECTED_ERROR)는 건드리지 않는다`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val ds = createDataSource(scope, dispatcher)

        // CONNECTED: 정상 종료는 그대로 유지(취소 복원이 성공 연결을 끊으면 안 됨).
        setConnectionState(ds, PtpipConnectionState.CONNECTED)
        invokePrivate(ds, "resetIfStuckConnecting")
        assertEquals(PtpipConnectionState.CONNECTED, ds.connectionState.value)

        // ERROR: 실패 종료는 그대로 유지(ERROR 복원은 별도 지연 감시가 담당).
        setConnectionState(ds, PtpipConnectionState.ERROR)
        invokePrivate(ds, "resetIfStuckConnecting")
        assertEquals(PtpipConnectionState.ERROR, ds.connectionState.value)

        scope.cancel()
    }
}
