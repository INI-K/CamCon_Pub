package com.inik.camcon.presentation.viewmodel

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.inik.camcon.data.repository.managers.PhotoLibraryLocation
import com.inik.camcon.domain.repository.CameraRepository
import com.inik.camcon.domain.usecase.ValidateImageFormatUseCase
import io.mockk.every
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 갤러리 2단 내비게이션 상태 보존 회귀.
 *
 * 뷰어(전체화면)는 컴포저블 지역 상태라 단위 테스트로 잡히지 않지만, **그 뒤에 남아 있어야 하는
 * 2단 상태**(열린 그룹과 그 그룹의 사진)는 ViewModel 의 StateFlow 에 있다. 뷰어를 닫고 돌아온
 * 화면이 새로고침을 부르는 경로([ServerPhotosViewModel.refreshPhotos])에서 `openedGroup` 이
 * 날아가면 사용자에게는 "뒤로가기 한 번에 1단 날짜 목록까지 건너뛴" 것과 같아 보인다.
 *
 * ContentResolver 질의(기기 저장소 개수)가 있어 Robolectric 이 필요하다.
 * application=Application::class: 실제 CamCon 은 onCreate 에서 네이티브를 로드해 JVM 에서 실패.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ServerPhotosViewModelGroupNavigationTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var context: Context
    private lateinit var cameraRepository: CameraRepository
    private lateinit var validateImageFormatUseCase: ValidateImageFormatUseCase
    private lateinit var photoLibraryLocation: PhotoLibraryLocation

    private val date = "2026-08-31"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        cameraRepository = mockk(relaxed = true)
        validateImageFormatUseCase = mockk(relaxed = true)

        // 앱 전용 저장소를 실제 임시 디렉터리로 흉내 낸다 — 폴더 체계는 `날짜_원본폴더_기종`.
        val root = temporaryFolder.newFolder("camcon_app_private")
        val dayFolder = File(root, "${date}_100NCZ_8_Z8").apply { mkdirs() }
        File(dayFolder, "DSC_0001.jpg").writeBytes(ByteArray(16))

        photoLibraryLocation = mockk(relaxed = true) {
            every { appPrivateRoot() } returns root
            every { listDateFolders() } returns listOf(
                PhotoLibraryLocation.DateFolderSummary(date, listOf(dayFolder), 1)
            )
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = ServerPhotosViewModel(
        context,
        cameraRepository,
        validateImageFormatUseCase,
        photoLibraryLocation,
        testDispatcher
    )

    @Test
    fun `새로고침해도 열린 그룹과 그 사진이 유지된다`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openGroup(GalleryGroupKey.Date(date))
        advanceUntilIdle()
        assertEquals(GalleryGroupKey.Date(date), viewModel.uiState.value.openedGroup)
        assertEquals(1, viewModel.uiState.value.photos.size)

        // 뷰어를 닫고 화면으로 돌아오는 경로가 부르는 새로고침.
        viewModel.refreshPhotos()
        advanceUntilIdle()

        assertEquals(GalleryGroupKey.Date(date), viewModel.uiState.value.openedGroup)
        assertEquals(1, viewModel.uiState.value.photos.size)
    }

    @Test
    fun `그룹을 닫으면 1단으로 돌아가고 선택 상태도 비워진다`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openGroup(GalleryGroupKey.Date(date))
        advanceUntilIdle()
        val photoId = viewModel.uiState.value.photos.first().id
        viewModel.startMultiSelectMode(photoId)

        viewModel.closeGroup()

        val state = viewModel.uiState.value
        assertNull(state.openedGroup)
        assertEquals(emptyList<Any>(), state.photos)
        assertEquals(emptySet<String>(), state.selectedPhotos)
        assertFalse(state.isMultiSelectMode)
    }
}
