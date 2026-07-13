package com.inik.camcon.data.repository

import com.inik.camcon.data.datasource.nativesource.NativeCameraDataSource
import com.inik.camcon.data.datasource.ptpip.PtpipDataSource
import com.inik.camcon.data.datasource.usb.UsbCameraManager
import com.inik.camcon.data.repository.managers.CameraConnectionManager
import com.inik.camcon.data.repository.managers.CameraEventManager
import com.inik.camcon.data.repository.managers.PhotoDownloadManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * 카메라 설정 변경(ISO/셔터/조리개) → 네이티브 실호출 위임 단위 테스트.
 *
 * 감사 항목: "카메라 설정 변경(ISO/셔터/조리개)이 네이티브 미호출 스텁 — 성공 위장" (MAJOR, CONFIRMED).
 * 대상: [CameraControlRepositoryImpl.updateCameraSetting].
 *
 * 회귀 방지 검증:
 *  - (a) 설정 변경이 실제로 nativeDataSource.setConfigString(위젯명, value) 네이티브 호출로 이어진다
 *        (과거 스텁은 로그만 남기고 무조건 Result.success(true) — 네이티브 호출 0건).
 *  - UI 키 → libgphoto2 위젯명 매핑: "aperture" → "f-number"(불일치 보정), 그 외는 동일.
 *  - (b) 네이티브 실패(gp code != 0) 시 Result.failure — '성공 위장' 제거.
 *  - 네이티브 성공(gp code == 0) 시 Result.success(true).
 *  - 네이티브 예외 시 Result.failure(try/catch 보호), CancellationException 은 재throw.
 *
 * 협력자: 위임 외 6개는 무관 → relaxed mock. nativeDataSource 만 명시 stub/verify.
 *
 * 단위 테스트 불가 경로(명시): nativeDataSource.setConfigString → CameraNative.setConfigString JNI →
 * camera_config.cpp 의 gp_widget_set_value / gp_camera_set_config 는 USB/실기기·camlib 영역으로 호스트 JVM 불가.
 * 본 테스트는 Repository→DataSource 위임 계약(키 매핑 포함)과 gp code → Result 래핑만 검증한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CameraControlRepositoryImplUpdateSettingTest {

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
    fun `iso 변경 시 네이티브 setConfigString 실호출 + success`() = runTest {
        every { nativeDataSource.setConfigString("iso", "800") } returns 0

        val result = repository.updateCameraSetting("iso", "800")

        // (a) 스텁이 아니라 실제 네이티브 호출로 이어짐
        verify(exactly = 1) { nativeDataSource.setConfigString("iso", "800") }
        assertTrue("gp code 0 이면 Result.success(true)", result.isSuccess)
        assertEquals(true, result.getOrNull())
    }

    @Test
    fun `shutterspeed 변경 시 네이티브 setConfigString 실호출`() = runTest {
        every { nativeDataSource.setConfigString("shutterspeed", "1/250") } returns 0

        repository.updateCameraSetting("shutterspeed", "1/250")

        verify(exactly = 1) { nativeDataSource.setConfigString("shutterspeed", "1/250") }
    }

    @Test
    fun `aperture 는 위젯명 f-number 로 매핑되어 호출`() = runTest {
        every { nativeDataSource.setConfigString("f-number", "f/2.8") } returns 0

        val result = repository.updateCameraSetting("aperture", "f/2.8")

        // UI 키 "aperture" ↔ 위젯명 "f-number" 불일치 보정
        verify(exactly = 1) { nativeDataSource.setConfigString("f-number", "f/2.8") }
        verify(exactly = 0) { nativeDataSource.setConfigString("aperture", any()) }
        assertTrue(result.isSuccess)
    }

    @Test
    fun `네이티브 실패 gp code 시 Result_failure (성공 위장 제거)`() = runTest {
        every { nativeDataSource.setConfigString("iso", "800") } returns -1

        val result = repository.updateCameraSetting("iso", "800")

        // (b) 네이티브가 실패했는데 success 로 위장하면 안 된다
        verify(exactly = 1) { nativeDataSource.setConfigString("iso", "800") }
        assertTrue("gp code != 0 이면 Result.failure 여야 한다", result.isFailure)
        assertFalse(result.isSuccess)
    }

    @Test
    fun `네이티브 예외 시 Result_failure`() = runTest {
        val boom = RuntimeException("native boom")
        every { nativeDataSource.setConfigString(any(), any()) } throws boom

        val result = repository.updateCameraSetting("shutterspeed", "1/250")

        assertTrue("네이티브 예외는 Result.failure 로 래핑돼야 한다", result.isFailure)
        assertEquals(boom, result.exceptionOrNull())
    }

    @Test
    fun `CancellationException 은 삼키지 않고 재throw`() = runTest {
        every { nativeDataSource.setConfigString(any(), any()) } throws CancellationException("cancelled")

        try {
            repository.updateCameraSetting("iso", "800")
            fail("CancellationException 은 재throw 되어야 하며 Result.failure 로 삼키면 안 된다")
        } catch (e: CancellationException) {
            // 기대 동작 — 구조적 동시성 보존
        }
    }
}
