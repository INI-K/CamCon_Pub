package com.inik.camcon.presentation.viewmodel.photo

import android.util.Log
import com.inik.camcon.domain.model.CameraPhoto
import com.inik.camcon.domain.repository.CameraRepository
import com.inik.camcon.domain.usecase.camera.DownloadCameraPhotoUseCase
import com.inik.camcon.domain.usecase.camera.GetCameraPhotoExifJsonUseCase
import com.inik.camcon.domain.usecase.camera.GetCameraThumbnailUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * PhotoImageManager 썸네일 중복 로드 방지(check-then-act 원자화) 회귀 테스트.
 *
 * 검증 대상 (issue index[6], F31):
 *  - containsKey/contains 판정과 _loadingThumbnails 등록이 동일 락 블록에서 원자적으로 수행되어
 *    같은 path 에 대한 동시 요청이 중복 로드(useCase 2회 호출)되지 않는다.
 *
 * 원칙: 구현 세부가 아닌 외부에서 관측 가능한 효과(useCase 호출 횟수)와 StateFlow 상태를 검증.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PhotoImageManagerThumbnailDedupTest {

    private val dispatcher = StandardTestDispatcher()

    // 매니저 주입용 appScope: 테스트 dispatcher(같은 스케줄러)를 쓰되 runTest job 의 자식이 아닌
    // 독립 SupervisorJob 으로 둔다. 그래야 (1) advanceUntilIdle 로 매니저 코루틴이 실행되고
    // (2) 매니저가 만드는 자식 SupervisorJob 이 runTest 종료를 막지 않는다(UncompletedCoroutinesError 회피).
    private val appScope = CoroutineScope(dispatcher + SupervisorJob())

    private lateinit var cameraRepository: CameraRepository
    private lateinit var getCameraThumbnailUseCase: GetCameraThumbnailUseCase
    private lateinit var downloadCameraPhotoUseCase: DownloadCameraPhotoUseCase
    private lateinit var getCameraPhotoExifJsonUseCase: GetCameraPhotoExifJsonUseCase

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.d(any(), any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0

        cameraRepository = mockk(relaxed = true)
        getCameraThumbnailUseCase = mockk()
        downloadCameraPhotoUseCase = mockk()
        getCameraPhotoExifJsonUseCase = mockk()
    }

    @After
    fun tearDown() {
        appScope.cancel()
        unmockkStatic(Log::class)
    }

    private fun photo(path: String) =
        CameraPhoto(path = path, name = path, size = 1L, date = 0L)

    private fun newManager(scope: CoroutineScope) = PhotoImageManager(
        cameraRepository = cameraRepository,
        getCameraThumbnailUseCase = getCameraThumbnailUseCase,
        downloadCameraPhotoUseCase = downloadCameraPhotoUseCase,
        getCameraPhotoExifJsonUseCase = getCameraPhotoExifJsonUseCase,
        appScope = scope,
        ioDispatcher = dispatcher
    )

    @Test
    fun `같은 path 동시 요청은 썸네일 useCase를 한 번만 호출`() = runTest(dispatcher) {
        val path = "/store/IMG_0001.JPG"
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00)

        // 첫 로드를 게이트로 멈춰 _loadingThumbnails 에 등록된 상태를 만든다.
        val gate = CompletableDeferred<Unit>()
        coEvery { getCameraThumbnailUseCase(path) } coAnswers {
            gate.await()
            Result.success(jpeg)
        }

        val manager = newManager(appScope)

        // 첫 번째 요청 → 등록 후 게이트에서 대기
        manager.loadThumbnailsForPhotos(listOf(photo(path)))
        advanceUntilIdle()

        // 등록된 동안 두 번째 요청 → 원자적 check-then-act 로 skip 되어야 함
        manager.loadThumbnailsForPhotos(listOf(photo(path)))
        advanceUntilIdle()

        // 게이트 해제 후 마무리
        gate.complete(Unit)
        advanceUntilIdle()

        // 중복 로드 방지: 정확히 1회만 호출
        coVerify(exactly = 1) { getCameraThumbnailUseCase(path) }
        assertEquals(jpeg.toList(), manager.getThumbnail(path)?.toList())
    }

    @Test
    fun `한 리스트에 중복 path가 있어도 썸네일 useCase는 path당 한 번만 호출`() =
        runTest(dispatcher) {
            val path = "/store/IMG_0002.JPG"
            val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00)
            coEvery { getCameraThumbnailUseCase(path) } returns Result.success(jpeg)

            val manager = newManager(appScope)

            manager.loadThumbnailsForPhotos(listOf(photo(path), photo(path)))
            advanceUntilIdle()

            coVerify(exactly = 1) { getCameraThumbnailUseCase(path) }
        }
}
