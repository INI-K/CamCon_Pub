package com.inik.camcon.data.datasource.ptpip

import android.app.Application
import app.cash.turbine.test
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.PtpipConnectionState
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `PtpipDataSource.notifyInvoluntaryPtpipDisconnect()` 단위 테스트.
 *
 * 설계서: `.claude/_workspace/design_disconnect_watchdog.md` §4.2 / §10-3.
 * 대상: 네이티브 이벤트 루프가 비자발적으로 죽었을 때(카메라 OFF/소켓 death) Kotlin 측이
 *       공유 네이티브 핸들을 건드리지 않고 `_connectionState`만 DISCONNECTED로 내리는 경량 경로.
 *
 * 검증(ViewModel 테스트 원칙 = StateFlow 방출 검증):
 *  - CONNECTED 상태에서 호출 → `connectionState`가 **DISCONNECTED** 방출.
 *  - 방출 후 `connectedCamera == null`(리플렉션), `lastConnectedCamera` **보존**(getLastConnectedCamera).
 *  - 이미 !=CONNECTED면 **멱등 no-op**(상태 불변, 추가 방출 없음).
 *
 * 테스트 인프라:
 *  - `@ApplicationScope coroutineScope` / `@IoDispatcher ioDispatcher`는 테스트 디스패처를 주입해
 *    `launch(ioDispatcher){ ... }` 본문이 `advanceUntilIdle`로 관측 가능하게 한다(Dispatchers.IO 하드코딩 금지 규약).
 *  - 나머지 13개 협력자는 relaxed mock. `notifyInvoluntaryPtpipDisconnect`는 그중
 *    `context.getString(R.string.progress_wifi_disconnected)`와 `cameraEventManager.resetListenerStateAfterNativeDeath()`만
 *    실제로 호출한다 → context만 Robolectric Application, cameraEventManager는 relaxed mock으로 no-op.
 *  - `application = Application::class`: 실제 @HiltAndroidApp은 onCreate에서 libgphoto2를 로딩해
 *    호스트 JVM에서 UnsatisfiedLinkError → 빈 스텁 Application 사용(EffectiveIso 선례).
 *
 * 단위 테스트 불가 경로(명시): 네이티브 발화 지점(camera_events.cpp의 onPtpipConnectionLost JNI 콜백)과
 * 재연결까지의 실제 폴링(WifiMonitoringService)은 실기기·JNI 영역이라 호스트 JVM에서 검증 불가.
 * 본 테스트는 콜백이 도달한 뒤의 순수 Kotlin 상태 전이만 검증한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PtpipDataSourceInvoluntaryDisconnectTest {

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
            coroutineScope = scope,
            ioDispatcher = dispatcher
        )
    }

    /** private 필드에 값을 강제 세팅(초기 CONNECTED 상태 시뮬레이션). */
    private fun setPrivate(target: PtpipDataSource, fieldName: String, value: Any?) {
        val f = PtpipDataSource::class.java.getDeclaredField(fieldName)
        f.isAccessible = true
        f.set(target, value)
    }

    private fun getPrivate(target: PtpipDataSource, fieldName: String): Any? {
        val f = PtpipDataSource::class.java.getDeclaredField(fieldName)
        f.isAccessible = true
        return f.get(target)
    }

    /** `_connectionState`(private MutableStateFlow)를 CONNECTED로 올려 초기 상태를 만든다. */
    @Suppress("UNCHECKED_CAST")
    private fun setConnectionState(target: PtpipDataSource, state: PtpipConnectionState) {
        val f = PtpipDataSource::class.java.getDeclaredField("_connectionState")
        f.isAccessible = true
        val flow =
            f.get(target) as kotlinx.coroutines.flow.MutableStateFlow<PtpipConnectionState>
        flow.value = state
    }

    private val sampleCamera = PtpipCamera(
        ipAddress = "192.168.1.10",
        port = 15740,
        name = "Nikon Z8"
    )

    @Test
    fun `CONNECTED에서 호출하면 connectionState가 DISCONNECTED 방출하고 connectedCamera는 null이 된다`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val scope = CoroutineScope(dispatcher)
            val ds = createDataSource(scope, dispatcher)

            // 초기 상태: CONNECTED + 연결된 카메라 + 마지막 연결 카메라.
            setConnectionState(ds, PtpipConnectionState.CONNECTED)
            setPrivate(ds, "connectedCamera", sampleCamera)
            setPrivate(ds, "lastConnectedCamera", sampleCamera)

            ds.connectionState.test {
                // 현재값 CONNECTED 먼저 수집.
                assertEquals(PtpipConnectionState.CONNECTED, awaitItem())

                ds.notifyInvoluntaryPtpipDisconnect()
                advanceUntilIdle()

                assertEquals(PtpipConnectionState.DISCONNECTED, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            // connectedCamera는 null로 내려가고, lastConnectedCamera는 재연결 대상용으로 보존.
            assertNull(getPrivate(ds, "connectedCamera"))
            assertEquals(sampleCamera, ds.getLastConnectedCamera())
        }

    @Test
    fun `이미 DISCONNECTED면 멱등하게 no-op이고 상태가 불변이다`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val ds = createDataSource(scope, dispatcher)

        // 초기 상태: DISCONNECTED(!= CONNECTED). lastConnectedCamera만 세팅.
        setConnectionState(ds, PtpipConnectionState.DISCONNECTED)
        setPrivate(ds, "lastConnectedCamera", sampleCamera)

        ds.notifyInvoluntaryPtpipDisconnect()
        advanceUntilIdle()

        // 상태 불변(추가 방출 없음) + lastConnectedCamera 보존.
        assertEquals(PtpipConnectionState.DISCONNECTED, ds.connectionState.first())
        assertEquals(sampleCamera, ds.getLastConnectedCamera())
    }

    @Test
    fun `ERROR 상태에서도 멱등 no-op이며 상태를 DISCONNECTED로 강제하지 않는다`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val ds = createDataSource(scope, dispatcher)

        // 가드는 `!= CONNECTED` 이므로 ERROR/CONNECTING에서도 no-op 이어야 한다.
        setConnectionState(ds, PtpipConnectionState.ERROR)
        setPrivate(ds, "connectedCamera", sampleCamera)

        ds.notifyInvoluntaryPtpipDisconnect()
        advanceUntilIdle()

        // ERROR 유지(강제 DISCONNECTED 전이 없음), connectedCamera도 그대로.
        assertEquals(PtpipConnectionState.ERROR, ds.connectionState.first())
        assertEquals(sampleCamera, getPrivate(ds, "connectedCamera"))
    }
}
