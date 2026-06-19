package com.inik.camcon.data.repository

import com.inik.camcon.data.datasource.nativesource.NativeCameraDataSource
import com.inik.camcon.data.datasource.ptpip.PtpipDataSource
import com.inik.camcon.data.datasource.usb.UsbCameraManager
import com.inik.camcon.data.repository.managers.CameraConnectionManager
import com.inik.camcon.data.repository.managers.CameraEventManager
import com.inik.camcon.data.repository.managers.PhotoDownloadManager
import com.inik.camcon.domain.model.LiveViewQuality
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * 라이브뷰 화질 → 네이티브 위임 단위 테스트.
 *
 * 설계서: `.claude/_workspace/liveview_quality_design.md` §11
 * 대상: [CameraControlRepositoryImpl.setLiveViewQuality] (L365~374).
 *
 * 검증:
 *  - setLiveViewQuality(q) → nativeDataSource.setLiveViewQuality(q.value) 위임 (MockK verify)
 *    · SPEED→0, BALANCED→1, QUALITY→2 매핑
 *  - 정상 시 Result.success(Unit)
 *  - 네이티브가 예외를 던지면 Result.failure (try/catch 보호)
 *  - CancellationException 은 삼키지 않고 재throw (구조적 동시성 보존)
 *
 * 협력자: 위임 외 6개는 위임에 무관 → relaxed mock. nativeDataSource 만 명시 stub/verify.
 *
 * 단위 테스트 불가 경로(명시): `nativeDataSource.setLiveViewQuality(int)` →
 * `CameraNative.setLiveViewQuality` JNI → `camera_liveview.cpp` `g_liveViewQuality` atomic →
 * `enableLiveView` 의 `liveviewsize` choice 분기 적용은 USB/실기기·camlib 영역으로 호스트 JVM 불가.
 * 본 테스트는 Repository→DataSource 위임 계약과 Result 래핑만 검증한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CameraControlRepositoryImplLiveViewQualityTest {

    private lateinit var nativeDataSource: NativeCameraDataSource
    private lateinit var repository: CameraControlRepositoryImpl

    @Before
    fun setUp() {
        nativeDataSource = mockk(relaxed = true)
        repository = CameraControlRepositoryImpl(
            nativeDataSource = nativeDataSource,
            ptpipDataSource = mockk<PtpipDataSource>(relaxed = true),
            usbCameraManager = mockk<UsbCameraManager>(relaxed = true),
            connectionManager = mockk<CameraConnectionManager>(relaxed = true),
            eventManager = mockk<CameraEventManager>(relaxed = true),
            downloadManager = mockk<PhotoDownloadManager>(relaxed = true),
            ioDispatcher = UnconfinedTestDispatcher()
        )
    }

    @Test
    fun `QUALITY 위임 시 네이티브에 2 전달하고 success`() = runTest {
        val result = repository.setLiveViewQuality(LiveViewQuality.QUALITY)

        verify(exactly = 1) { nativeDataSource.setLiveViewQuality(2) }
        assertTrue("정상 위임 시 Result.success 여야 한다", result.isSuccess)
    }

    @Test
    fun `SPEED 위임 시 네이티브에 0 전달`() = runTest {
        repository.setLiveViewQuality(LiveViewQuality.SPEED)
        verify(exactly = 1) { nativeDataSource.setLiveViewQuality(0) }
    }

    @Test
    fun `BALANCED 위임 시 네이티브에 1 전달`() = runTest {
        repository.setLiveViewQuality(LiveViewQuality.BALANCED)
        verify(exactly = 1) { nativeDataSource.setLiveViewQuality(1) }
    }

    @Test
    fun `네이티브 예외 시 Result_failure`() = runTest {
        val boom = RuntimeException("native boom")
        every { nativeDataSource.setLiveViewQuality(any()) } throws boom

        val result = repository.setLiveViewQuality(LiveViewQuality.QUALITY)

        assertTrue("네이티브 예외는 Result.failure 로 래핑돼야 한다", result.isFailure)
        assertEquals(boom, result.exceptionOrNull())
    }

    @Test
    fun `CancellationException 은 삼키지 않고 재throw`() = runTest {
        every { nativeDataSource.setLiveViewQuality(any()) } throws CancellationException("cancelled")

        try {
            repository.setLiveViewQuality(LiveViewQuality.SPEED)
            fail("CancellationException 은 재throw 되어야 하며 Result.failure 로 삼키면 안 된다")
        } catch (e: CancellationException) {
            // 기대 동작 — 구조적 동시성 보존
        }
    }
}
