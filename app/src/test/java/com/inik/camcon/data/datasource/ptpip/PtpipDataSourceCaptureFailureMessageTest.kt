package com.inik.camcon.data.datasource.ptpip

import android.app.Application
import com.inik.camcon.R
import com.inik.camcon.domain.manager.ErrorNotifier
import com.inik.camcon.domain.manager.ErrorSeverity
import com.inik.camcon.domain.manager.ErrorType
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `PtpipDataSource.notifyCaptureFailed()` 의 실패 코드 → 사용자 문구 매핑 테스트.
 *
 * 배경: 네이티브(camera_events.cpp)는 그동안 다운로드 실패를 전부 `GP_ERROR_IO` 로 하드코딩해
 * 올렸다. 이제 실제 gp 코드를 그대로 올리므로, Kotlin 이 "재시도해도 소용없는 실패"와
 * "일시적 실패"를 다른 문구로 안내해야 한다.
 *  - -6 (GP_ERROR_NOT_SUPPORTED): 카메라가 그 객체의 전송 자체를 거부. 네이티브가 3회에서
 *    자동 중단하므로 사용자에게는 "오류 -6" 이 아니라 취할 수 있는 행동을 안내한다.
 *  - 그 외(-7/-10 등): 종전 문구 유지(회귀 방지).
 *
 * Robolectric 을 쓰는 이유: 실제 `strings.xml` 을 읽어 두 문구가 서로 다른 리소스로 해석되는지를
 * 검증하기 위해서다(mock Context 는 둘 다 빈 문자열이라 분기를 증명하지 못한다).
 * `application = Application::class` — 실제 @HiltAndroidApp 은 onCreate 에서 libgphoto2 를
 * 로딩해 호스트 JVM 에서 UnsatisfiedLinkError 가 난다(InvoluntaryDisconnect 선례).
 *
 * 단위 테스트 불가 경로(명시): 결정적 실패 3회 상한과 재발행 무시는 네이티브
 * `camera_events.cpp` 의 경로별 카운터에 있어 호스트 JVM 에서 검증할 수 없다(실기기 + JNI).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PtpipDataSourceCaptureFailureMessageTest {

    private fun createDataSource(errorNotifier: ErrorNotifier): PtpipDataSource {
        val dispatcher = StandardTestDispatcher()
        return PtpipDataSource(
            context = org.robolectric.RuntimeEnvironment.getApplication(),
            discoveryService = mockk(relaxed = true),
            connectionManager = mockk(relaxed = true),
            nikonAuthService = mockk(relaxed = true),
            wifiHelper = mockk(relaxed = true),
            cameraEventManager = mockk(relaxed = true),
            cameraStateObserver = mockk(relaxed = true),
            errorNotifier = errorNotifier,
            photoDownloadManager = mockk(relaxed = true),
            autoConnectManager = mockk(relaxed = true),
            autoConnectTaskRunnerProvider = mockk(relaxed = true),
            ptpipPreferencesDataSource = mockk(relaxed = true),
            tetherService = mockk(relaxed = true),
            nativeCameraDataSource = mockk(relaxed = true),
            libgphoto2PluginInstaller = mockk(relaxed = true),
            nikonApplicationModeManager = mockk(relaxed = true),
            nikonLinkDiagnostics = mockk(relaxed = true),
            coroutineScope = TestScope(dispatcher),
            ioDispatcher = dispatcher
        )
    }

    @Test
    fun `전송 거부(-6)는 전용 안내 문구로 통지한다`() = runTest {
        val errorNotifier = mockk<ErrorNotifier>(relaxed = true)
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val expected = app.getString(R.string.photo_transfer_rejected_by_camera)

        createDataSource(errorNotifier).notifyCaptureFailed(-6)

        verify {
            errorNotifier.emitError(
                ErrorType.OPERATION,
                expected,
                any(),
                ErrorSeverity.HIGH
            )
        }
    }

    @Test
    fun `일시적 실패(-7)는 기존 오류코드 문구를 유지한다`() = runTest {
        val errorNotifier = mockk<ErrorNotifier>(relaxed = true)
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val expected = app.getString(R.string.photo_capture_failed, -7)

        createDataSource(errorNotifier).notifyCaptureFailed(-7)

        verify {
            errorNotifier.emitError(
                ErrorType.OPERATION,
                expected,
                any(),
                ErrorSeverity.HIGH
            )
        }
    }

    /** 두 문구가 실제로 다른 리소스여야 분기가 의미를 갖는다. */
    @Test
    fun `두 실패 문구는 서로 다른 문자열이다`() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val rejected = app.getString(R.string.photo_transfer_rejected_by_camera)
        val generic = app.getString(R.string.photo_capture_failed, -6)
        org.junit.Assert.assertNotEquals(rejected, generic)
    }
}
