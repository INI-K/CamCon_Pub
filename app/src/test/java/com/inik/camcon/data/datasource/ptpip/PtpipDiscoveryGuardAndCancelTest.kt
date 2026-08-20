package com.inik.camcon.data.datasource.ptpip

import android.app.Application
import com.inik.camcon.data.repository.managers.CameraEventManager
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.PtpipConnectionState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 세션 점유 가드 확장 + 협조적 연결 취소 회귀 테스트.
 *
 * 회귀 배경:
 *  (1) 검색 금지 가드가 `== CONNECTING` 단독이라 **CONNECTED를 막지 않았다**. 카메라는 PTP/IP 세션을
 *      1개만 허용하므로 점유 중 프로브는 자기 카메라를 '미개방'으로 오판하고, 고아 소켓이 남으면
 *      새 TCP가 -7/End-of-stream으로 거부되어 앱 재시작까지 복구되지 않는다.
 *  (2) 취소가 실효하지 않았다. `connectToCamera`가 `connectionStateMutex`를 잡은 채 Nikon 승인 대기
 *      60초를 폴링하고 `disconnect()`는 같은 mutex를 대기하므로, mutex 경유 취소는 영영 성립하지 않는다.
 *      → mutex와 무관한 세대 카운터(attemptId) 기반 협조적 취소로 해결.
 *
 * 네이티브(JNI) 경로는 호스트 JVM에서 구동 불가하므로, 네이티브를 호출하지 않는 순수 Kotlin
 * 가드/플래그 불변식만 검증한다(인프라는 `PtpipDataSourceStateRecoveryTest`와 동일).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PtpipDiscoveryGuardAndCancelTest {

    private fun createDataSource(
        scope: CoroutineScope,
        dispatcher: TestDispatcher,
        cameraEventManager: CameraEventManager = mockk(relaxed = true)
    ): PtpipDataSource {
        val context: android.content.Context = org.robolectric.RuntimeEnvironment.getApplication()
        return PtpipDataSource(
            context = context,
            discoveryService = mockk(relaxed = true),
            connectionManager = mockk(relaxed = true),
            nikonAuthService = mockk(relaxed = true),
            wifiHelper = mockk(relaxed = true),
            cameraEventManager = cameraEventManager,
            cameraStateObserver = mockk(relaxed = true),
            errorNotifier = mockk(relaxed = true),
            photoDownloadManager = mockk(relaxed = true),
            autoConnectManager = mockk(relaxed = true),
            autoConnectTaskRunnerProvider = mockk(relaxed = true),
            ptpipPreferencesDataSource = mockk(relaxed = true),
            tetherService = mockk(relaxed = true),
            nativeCameraDataSource = mockk(relaxed = true),
            libgphoto2PluginInstaller = mockk(relaxed = true),
            nikonApplicationModeManager = mockk(relaxed = true),
            nikonLinkDiagnostics = mockk(relaxed = true),
            coroutineScope = scope,
            ioDispatcher = dispatcher
        )
    }

    // ── reflection 헬퍼 ──

    @Suppress("UNCHECKED_CAST")
    private fun setConnectionState(target: PtpipDataSource, state: PtpipConnectionState) {
        val f = PtpipDataSource::class.java.getDeclaredField("_connectionState")
        f.isAccessible = true
        (f.get(target) as kotlinx.coroutines.flow.MutableStateFlow<PtpipConnectionState>).value =
            state
    }

    private fun setLongField(target: PtpipDataSource, name: String, value: Long) {
        val f = PtpipDataSource::class.java.getDeclaredField(name)
        f.isAccessible = true
        f.setLong(target, value)
    }

    private fun getLongField(target: PtpipDataSource, name: String): Long {
        val f = PtpipDataSource::class.java.getDeclaredField(name)
        f.isAccessible = true
        return f.getLong(target)
    }

    private fun isCancelRequested(target: PtpipDataSource, attemptId: Long): Boolean {
        val m = PtpipDataSource::class.java
            .getDeclaredMethod("isCancelRequested", Long::class.javaPrimitiveType)
        m.isAccessible = true
        return m.invoke(target, attemptId) as Boolean
    }

    /** 물리 셔터 무선 수신 중 상태를 만든다(tetheringController의 Job을 활성으로). */
    private fun markShutterListening(target: PtpipDataSource) {
        val controllerField = PtpipDataSource::class.java.getDeclaredField("tetheringController")
        controllerField.isAccessible = true
        val controller = controllerField.get(target)
        val jobField = controller.javaClass.getDeclaredField("shutterListenerJob")
        jobField.isAccessible = true
        jobField.set(controller, Job())
    }

    private fun connectionStateMutex(target: PtpipDataSource): Mutex {
        val f = PtpipDataSource::class.java.getDeclaredField("connectionStateMutex")
        f.isAccessible = true
        return f.get(target) as Mutex
    }

    // ───────────────────────── 검색 금지 가드 ─────────────────────────

    @Test
    fun `isDiscoveryBlocked - CONNECTING과 CONNECTED 모두 검색을 막는다`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val ds = createDataSource(scope, dispatcher)
        advanceUntilIdle()

        setConnectionState(ds, PtpipConnectionState.CONNECTING)
        assertTrue("CONNECTING은 기존 계약", ds.isDiscoveryBlocked())

        setConnectionState(ds, PtpipConnectionState.CONNECTED)
        assertTrue("CONNECTED를 막지 않으면 고아 소켓/오판 프로브가 세션을 죽인다", ds.isDiscoveryBlocked())

        setConnectionState(ds, PtpipConnectionState.DISCONNECTED)
        assertFalse(ds.isDiscoveryBlocked())

        setConnectionState(ds, PtpipConnectionState.ERROR)
        assertFalse("ERROR는 검색을 막지 않는다(자동 경로 복원 대상)", ds.isDiscoveryBlocked())

        scope.cancel()
    }

    @Test
    fun `isDiscoveryBlocked - 물리 셔터 무선 수신 중이면 검색을 막는다`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val ds = createDataSource(scope, dispatcher)
        advanceUntilIdle()

        setConnectionState(ds, PtpipConnectionState.DISCONNECTED)
        assertFalse(ds.isDiscoveryBlocked())

        markShutterListening(ds)
        assertTrue("무선 수신이 단일 세션 소켓을 점유 중", ds.isDiscoveryBlocked())

        scope.cancel()
    }

    @Test
    fun `USB 활성은 검색을 막지 않고 자동 연결만 막는다(J8 의미 보존)`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val eventManager = mockk<CameraEventManager>(relaxed = true)
        every { eventManager.isUsbCameraActive() } returns true
        val ds = createDataSource(scope, dispatcher, eventManager)
        advanceUntilIdle()

        setConnectionState(ds, PtpipConnectionState.DISCONNECTED)

        assertFalse(
            "mDNS/SSDP 수신 자체는 공유 네이티브 핸들에 무해하다",
            ds.isDiscoveryBlocked()
        )
        assertTrue(
            "initCameraWithPtpip는 USB 핸들을 무경고 파괴하므로 자동 연결은 금지",
            ds.isAutoConnectBlocked()
        )

        scope.cancel()
    }

    @Test
    fun `isAutoConnectBlocked는 검색 금지 조건을 포함한다`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val ds = createDataSource(scope, dispatcher)
        advanceUntilIdle()

        setConnectionState(ds, PtpipConnectionState.DISCONNECTED)
        setLongField(ds, "autoConnectSuppressUntilMs", 0L)
        assertFalse(ds.isAutoConnectBlocked())

        setConnectionState(ds, PtpipConnectionState.CONNECTED)
        assertTrue(ds.isAutoConnectBlocked())

        scope.cancel()
    }

    // ───────────────────────── 협조적 취소 ─────────────────────────

    @Test
    fun `취소는 현재 시도만 죽이고 다음 시도에는 영향이 없다(세대 카운터)`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val ds = createDataSource(scope, dispatcher)
        advanceUntilIdle()

        // 진행 중 시도 id = 7 을 가정
        setLongField(ds, "currentAttemptId", 7L)
        ds.requestConnectCancel()

        assertTrue("진행 중 시도는 취소 대상", isCancelRequested(ds, 7L))
        assertFalse("늦게 도착한 취소가 다음 시도를 죽이면 안 된다", isCancelRequested(ds, 8L))
        assertFalse("이전 시도에도 반응하지 않는다", isCancelRequested(ds, 6L))

        scope.cancel()
    }

    @Test
    fun `진행 중 시도가 없을 때의 취소는 무해하다`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val ds = createDataSource(scope, dispatcher)
        advanceUntilIdle()

        // currentAttemptId 초기값 0 — 실제 시도는 1부터 발급된다.
        ds.requestConnectCancel()
        assertFalse(isCancelRequested(ds, 1L))
        assertFalse("attemptId 0은 유효한 시도가 아니다", isCancelRequested(ds, 0L))

        scope.cancel()
    }

    @Test
    fun `requestConnectCancel은 connectionStateMutex를 획득하지 않는다`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val ds = createDataSource(scope, dispatcher)
        advanceUntilIdle()

        val mutex = connectionStateMutex(ds)
        mutex.lock() // 연결 시도가 락을 쥐고 승인 대기 60초를 폴링하는 상황 재현
        setLongField(ds, "currentAttemptId", 3L)

        // mutex를 경유하면 여기서 영영 반환하지 못한다.
        ds.requestConnectCancel()

        assertTrue("락은 여전히 연결 시도가 보유", mutex.isLocked)
        assertTrue("락 보유 중에도 취소가 즉시 성립해야 한다", isCancelRequested(ds, 3L))

        mutex.unlock()
        scope.cancel()
    }

    @Test
    fun `취소 후 쿨다운 동안 자동 연결이 차단된다`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val ds = createDataSource(scope, dispatcher)
        advanceUntilIdle()

        setConnectionState(ds, PtpipConnectionState.DISCONNECTED)
        assertFalse(ds.isAutoConnectBlocked())

        ds.requestConnectCancel()
        assertTrue(
            "취소 직후 배경 폴링이 같은 카메라를 4초 뒤 다시 붙잡으면 안 된다",
            ds.isAutoConnectBlocked()
        )

        val suppressUntil = getLongField(ds, "autoConnectSuppressUntilMs")
        assertTrue("쿨다운 만료 시각이 미래로 설정된다", suppressUntil > 0L)

        // 쿨다운 경과 시뮬레이션
        setLongField(ds, "autoConnectSuppressUntilMs", 0L)
        assertFalse(ds.isAutoConnectBlocked())

        scope.cancel()
    }

    @Test
    fun `사용자 명시 연결은 취소 쿨다운을 즉시 해제한다`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val ds = createDataSource(scope, dispatcher)
        advanceUntilIdle()

        ds.requestConnectCancel()
        assertTrue(getLongField(ds, "autoConnectSuppressUntilMs") > 0L)

        // connectToCamera는 mutex 획득 직후 쿨다운을 해제한 뒤 내부 연결로 진입한다.
        // 내부 연결은 CameraNative(JNI) 로딩에서 호스트 JVM 한계로 실패하므로 결과는 무시하고,
        // 그 앞단에서 성립해야 하는 쿨다운 해제 불변식만 확인한다.
        runCatching {
            ds.connectToCamera(
                PtpipCamera("192.168.49.137", 15740, "Z_8_5003869"),
                forceApMode = false
            )
        }
        advanceUntilIdle()

        assertEquals(
            "사용자 의도가 배경 쿨다운을 이긴다",
            0L,
            getLongField(ds, "autoConnectSuppressUntilMs")
        )

        scope.cancel()
    }
}
