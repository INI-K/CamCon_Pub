package com.inik.camcon.data.repository.managers

import android.app.Application
import com.inik.camcon.data.datasource.nativesource.CameraCaptureListener
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `CameraEventManager.createCameraCaptureListener(...)`가 만든 익명 리스너의
 * `onPtpipConnectionLost()` override 로직 단위 테스트.
 *
 * 설계서: `.claude/_workspace/design_disconnect_watchdog.md` §4.1 / §10-3.
 * 대상: 네이티브가 비자발적 끊김을 통지했을 때, 리스너가
 *  - `connectionType == PTPIP` → `onPtpipConnectionLostCallback`을 **호출**
 *  - `connectionType == USB`   → **미호출**(케이블 재부착 인텐트 경로가 담당)
 *  하는지 검증한다(§1.3 USB 오발화 차단).
 *
 * 접근 방식(테스트 가능한 최소 경로):
 *  - 실제 발화 경로는 `CameraNative.listenCameraEvents(listener)`(JNI)라 호스트 JVM에서 실행 불가.
 *  - 그러나 검증 대상인 `onPtpipConnectionLost()` override는 **순수 Kotlin 분기**이므로,
 *    리스너를 만드는 private `createCameraCaptureListener(...)`를 리플렉션으로 호출해 리스너 인스턴스를
 *    얻은 뒤 `onPtpipConnectionLost()`를 직접 호출하고, `onPtpipConnectionLostCallback` 호출 여부를 관찰한다.
 *    (native 실행 없이 override 로직만 격리 검증 → 회귀 방어선.)
 *  - 10개 협력자는 relaxed mock. 이 경로는 그중 무엇도 호출하지 않는다(순수 콜백 라우팅).
 *  - `application = Application::class`: @HiltAndroidApp의 libgphoto2 로딩 회피(EffectiveIso 선례).
 *
 * 단위 테스트 불가 경로(명시): camera_events.cpp가 비자발/자발 종료를 구분해 이 콜백을 발화하는지,
 * 발화 시 JNI 스레드 UAF가 없는지는 실기기 logcat 오라클로만 검증 가능(설계 §8.2).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CameraEventManagerPtpipConnectionLostTest {

    private fun createManager(): CameraEventManager {
        val context: android.content.Context =
            org.robolectric.RuntimeEnvironment.getApplication()
        return CameraEventManager(
            context = context,
            nativeDataSource = mockk(relaxed = true),
            usbCameraManager = mockk(relaxed = true),
            validateImageFormatUseCase = mockk(relaxed = true),
            photoDownloadManager = mockk(relaxed = true),
            transferProgressTracker = mockk(relaxed = true),
            errorHandlingManager = mockk(relaxed = true),
            scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined,
            mainDispatcher = Dispatchers.Unconfined
        )
    }

    /**
     * private `createCameraCaptureListener(connectionType, onPhotoCaptured, onPhotoDownloaded,
     * onFlushComplete, onCaptureFailed)`를 리플렉션으로 호출해 익명 리스너를 얻는다.
     */
    private fun buildListener(
        manager: CameraEventManager,
        connectionType: CameraEventManager.ConnectionType
    ): CameraCaptureListener {
        val method = CameraEventManager::class.java.getDeclaredMethod(
            "createCameraCaptureListener",
            CameraEventManager.ConnectionType::class.java,
            Function2::class.java,      // onPhotoCaptured: (String, String) -> Unit
            Function3::class.java,      // onPhotoDownloaded: ((String, String, ByteArray) -> Unit)?
            Function0::class.java,      // onFlushComplete: () -> Unit
            Function1::class.java       // onCaptureFailed: (Int) -> Unit
        )
        method.isAccessible = true
        val onPhotoCaptured: (String, String) -> Unit = { _, _ -> }
        val onPhotoDownloaded: ((String, String, ByteArray) -> Unit)? = null
        val onFlushComplete: () -> Unit = {}
        val onCaptureFailed: (Int) -> Unit = {}
        return method.invoke(
            manager,
            connectionType,
            onPhotoCaptured,
            onPhotoDownloaded,
            onFlushComplete,
            onCaptureFailed
        ) as CameraCaptureListener
    }

    @Test
    fun `PTPIP 리스너의 onPtpipConnectionLost는 콜백을 호출한다`() {
        val manager = createManager()
        var invoked = 0
        manager.onPtpipConnectionLostCallback = { invoked++ }

        val listener = buildListener(manager, CameraEventManager.ConnectionType.PTPIP)
        listener.onPtpipConnectionLost()

        assertEquals("PTPIP 리스너는 onPtpipConnectionLostCallback을 정확히 1회 호출해야 한다", 1, invoked)
    }

    @Test
    fun `USB 리스너의 onPtpipConnectionLost는 콜백을 호출하지 않는다`() {
        val manager = createManager()
        var invoked = false
        manager.onPtpipConnectionLostCallback = { invoked = true }

        val listener = buildListener(manager, CameraEventManager.ConnectionType.USB)
        listener.onPtpipConnectionLost()

        assertFalse("USB 리스너는 onPtpipConnectionLostCallback을 호출하면 안 된다", invoked)
    }

    @Test
    fun `콜백이 미설정(null)이어도 PTPIP onPtpipConnectionLost는 크래시하지 않는다`() {
        val manager = createManager()
        // onPtpipConnectionLostCallback = null (기본값) — safe-call(?.invoke) 이어야 함.
        val listener = buildListener(manager, CameraEventManager.ConnectionType.PTPIP)

        // 예외 없이 통과하면 성공.
        listener.onPtpipConnectionLost()
        assertTrue(true)
    }
}
