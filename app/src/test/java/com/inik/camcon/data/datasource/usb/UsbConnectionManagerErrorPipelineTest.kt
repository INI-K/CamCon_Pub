package com.inik.camcon.data.datasource.usb

import android.app.Application
import android.hardware.usb.UsbDevice
import com.inik.camcon.R
import com.inik.camcon.domain.manager.ErrorNotifier
import com.inik.camcon.domain.manager.ErrorSeverity
import com.inik.camcon.domain.manager.ErrorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * `UsbConnectionManager` 의 에러 안내 파이프라인 단위 테스트 (감사 항목 C: UI 배선 단절).
 *
 * 대상: `reportError(kind)` 가 (1) `lastErrorKind` StateFlow 를 갱신하고 (2) 준비된 현지화 안내를
 *  기존 UI 에러 채널(`ErrorNotifier.emitError` → errorEvent → setError)로 즉시 방출하는지.
 *
 * 접근 방식(네이티브 무의존 경로):
 *  - 미지원 VID 디바이스는 `connectToCamera` 진입 직후 `isAllowedCameraVendor` 검사에서 걸려
 *    `reportError(UsbErrorKind.Unsupported)` 로 직행한다(openDevice/initCameraWithFd 등 JNI 호출 없음).
 *  - `scope`/`ioDispatcher` 를 `Dispatchers.Unconfined` 로 주입해 `scope.launch` 가 즉시 실행된다.
 *  - `application = Application::class`: @HiltAndroidApp 의 libgphoto2 로딩 회피(기존 선례).
 *
 * 단위 테스트 불가 경로(명시): 실제 -7(Mass Storage)/-52 등 초기화 결과 코드 매핑은
 *  `CameraNative.initCameraWithFd`(JNI)가 필요해 실기기에서만 검증 가능. 여기서는 매핑 이후의
 *  방출 배선(reportError→emitError)만 격리 검증한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class UsbConnectionManagerErrorPipelineTest {

    private val errorNotifier: ErrorNotifier = mockk(relaxed = true)

    private fun createManager(): UsbConnectionManager {
        val context = RuntimeEnvironment.getApplication()
        return UsbConnectionManager(
            context = context,
            scope = CoroutineScope(Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined,
            libgphoto2PluginInstaller = mockk(relaxed = true),
            errorNotifier = errorNotifier
        )
    }

    /** 화이트리스트 외 VID(=미지원) 디바이스 목. */
    private fun unsupportedDevice(): UsbDevice = mockk {
        every { vendorId } returns 0x1234
        every { productId } returns 0x0001
        every { deviceName } returns "/dev/bus/usb/001/002"
    }

    @Test
    fun `미지원 VID 연결 시 lastErrorKind에 Unsupported가 담긴다`() {
        val manager = createManager()

        manager.connectToCamera(unsupportedDevice())

        assertEquals(UsbErrorKind.Unsupported, manager.lastErrorKind.value)
    }

    @Test
    fun `미지원 VID 연결 시 현지화 안내가 ErrorNotifier 채널로 방출된다`() {
        val manager = createManager()
        val context = RuntimeEnvironment.getApplication()

        manager.connectToCamera(unsupportedDevice())

        val messageSlot = slot<String>()
        verify(exactly = 1) {
            errorNotifier.emitError(
                type = ErrorType.CONNECTION,
                message = capture(messageSlot),
                exception = null,
                severity = ErrorSeverity.HIGH
            )
        }
        assertEquals(
            context.getString(R.string.error_usb_unsupported),
            messageSlot.captured
        )
    }
}
