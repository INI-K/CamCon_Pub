package com.inik.camcon.data.repository.managers

import android.app.Application
import com.inik.camcon.data.datasource.nativesource.NativeCameraDataSource
import com.inik.camcon.data.datasource.usb.UsbCameraManager
import com.inik.camcon.domain.manager.CameraStateObserver
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * `CameraConnectionManager.disconnectCamera()` 가 disconnect 체인에서 UsbConnectionManager 의
 * 상태 리셋을 위임하는지 검증 (감사 항목 A: 재연결 무력화).
 *
 * 회귀 방어: 과거 disconnect 는 `nativeDataSource.closeCamera()` 와 자기 플래그만 리셋하고
 *  `UsbConnectionManager._isNativeCameraConnected / lastInitializedFd / currentConnection` 을
 *  건드리지 않아, 재연결이 stale true 로 '이미 연결됨' 조기 성공(네이티브는 닫힘)했다.
 *  이제 disconnectCamera 는 `usbCameraManager.resetConnectionStateForDisconnect()` 를 호출해야 한다.
 *
 * `application = Application::class`: @HiltAndroidApp 의 libgphoto2 로딩 회피(기존 선례).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CameraConnectionManagerDisconnectResetTest {

    @Test
    fun `disconnectCamera는 UsbConnectionManager 상태 리셋을 위임하고 네이티브를 닫는다`() =
        runBlocking {
            val context = RuntimeEnvironment.getApplication()
            val nativeDataSource: NativeCameraDataSource = mockk(relaxed = true)
            // observeNativeCameraConnection 컬렉터가 실제 StateFlow 를 수집하도록 스텁.
            val usbCameraManager: UsbCameraManager = mockk(relaxed = true) {
                every { isNativeCameraConnected } returns MutableStateFlow(false)
            }
            val cameraStateObserver: CameraStateObserver = mockk(relaxed = true)

            val manager = CameraConnectionManager(
                context = context,
                nativeDataSource = nativeDataSource,
                usbCameraManager = usbCameraManager,
                cameraStateObserver = cameraStateObserver,
                scope = CoroutineScope(Dispatchers.Unconfined),
                ioDispatcher = Dispatchers.Unconfined
            )

            val result = manager.disconnectCamera()

            assertTrue("disconnect 는 성공을 반환해야 한다", result.isSuccess)
            coVerify(exactly = 1) { nativeDataSource.closeCamera() }
            verify(exactly = 1) { usbCameraManager.resetConnectionStateForDisconnect() }
        }
}
