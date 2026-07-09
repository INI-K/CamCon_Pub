package com.inik.camcon.presentation.viewmodel

import android.app.Application
import android.content.Context
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.inik.camcon.domain.repository.CameraRepository
import com.inik.camcon.domain.usecase.ValidateImageFormatUseCase
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.fakes.RoboCursor

/**
 * [ServerPhotosViewModel] own-media 로딩의 File.exists() 게이트 제거 회귀.
 *
 * 사진권한 정책 변경 전에는 MediaStore row 마다 `File(path).exists()` 로 필터링해서, 스코프드
 * 스토리지(API29+)에서 raw 경로 접근이 막힌 own-media 가 목록에서 통째로 사라지는 회귀가 났다.
 * 지금은 게이트를 제거하고 `_ID` 를 그대로 노출해 content URI 로 관통한다.
 *
 * 검증: **파일시스템에 존재하지 않는** 경로의 MediaStore row 를 주입(RoboCursor)해도
 * `uiState.photos` 방출에 그대로 실린다(size == 주입 row 수). 예전 게이트가 살아있으면 0 이 된다.
 *
 * ContentResolver 쿼리라 Robolectric 필요(sdk=34: Robolectric 4.14 지원 한계).
 * application=Application::class: 실제 CamCon 은 onCreate 에서 네이티브를 로드해 JVM 에서 실패.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ServerPhotosViewModelOwnMediaTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var context: Context
    private lateinit var cameraRepository: CameraRepository
    private lateinit var validateImageFormatUseCase: ValidateImageFormatUseCase

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        cameraRepository = mockk(relaxed = true)
        validateImageFormatUseCase = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * DATA 경로가 파일시스템에 없는 row 2개를 주입한다. 예전 File.exists() 게이트라면 둘 다
     * 걸러져 photos 가 빈다. 게이트 제거로 둘 다 실려야 한다.
     */
    private fun injectMediaStoreRows() {
        val projection = listOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT
        )
        val cursor = RoboCursor().apply {
            setColumnNames(projection)
            setResults(
                arrayOf(
                    // _ID, name, DATA(존재하지 않는 경로), size, date, width, height
                    arrayOf<Any?>(
                        1L, "IMG_0001.jpg",
                        "/nonexistent/DCIM/CamCon/IMG_0001.jpg",
                        1_000L, 1_700_000_000_000L, 100, 200
                    ),
                    arrayOf<Any?>(
                        2L, "IMG_0002.jpg",
                        "/nonexistent/DCIM/CamCon/IMG_0002.jpg",
                        2_000L, 1_700_000_000_500L, 300, 400
                    )
                )
            )
        }
        shadowOf(context.contentResolver)
            .setCursor(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor)
    }

    @Test
    fun `존재하지 않는 경로의 own-media 도 photos 방출에 실린다`() = runTest {
        injectMediaStoreRows()

        val viewModel = ServerPhotosViewModel(
            context,
            cameraRepository,
            validateImageFormatUseCase,
            testDispatcher
        )

        viewModel.uiState.test {
            // 초기 상태(로드 실행 전): 빈 목록
            assertEquals(emptyList<Any>(), awaitItem().photos)

            // init { loadLocalPhotos() } 구동
            advanceUntilIdle()

            val loaded = expectMostRecentItem()
            assertFalse(loaded.isLoading)
            // File.exists() 게이트가 살아있으면 0. 제거됐으므로 2.
            assertEquals(2, loaded.photos.size)
            // captureTime 내림차순 정렬 + _ID → CapturedPhoto.id 매핑 확인
            assertEquals("2", loaded.photos[0].id)
            assertEquals("1", loaded.photos[1].id)

            cancelAndConsumeRemainingEvents()
        }
    }
}
