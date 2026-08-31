package com.inik.camcon.presentation.viewmodel

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.inik.camcon.data.repository.managers.PhotoLibraryLocation
import com.inik.camcon.domain.repository.CameraRepository
import com.inik.camcon.domain.usecase.ValidateImageFormatUseCase
import io.mockk.coEvery
import io.mockk.coVerify
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
 * 갤러리 다중 선택의 **일괄 내보내기 집계** 회귀.
 *
 * 사용자에게 보이는 것은 "N장 내보냄, M장은 이미 기기 저장소"라는 한 줄뿐이라, 그 숫자가
 * 실제로 일어난 일과 어긋나면 사용자는 내보내지 못한 사진을 내보낸 것으로 믿게 된다. 개별
 * 실패를 건너뛰되 집계에는 싣는지, 진행 표시가 끝에 사라지는지, 멀티 선택이 풀리는지를 건다.
 *
 * ContentResolver 질의(기기 저장소 개수)가 있어 Robolectric 이 필요하다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ServerPhotosViewModelExportTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var context: Context
    private lateinit var cameraRepository: CameraRepository
    private lateinit var validateImageFormatUseCase: ValidateImageFormatUseCase
    private lateinit var photoLibraryLocation: PhotoLibraryLocation

    private val date = "2026-08-31"
    private lateinit var dayFolder: File

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        cameraRepository = mockk(relaxed = true)
        validateImageFormatUseCase = mockk(relaxed = true)

        val root = temporaryFolder.newFolder("camcon_app_private")
        dayFolder = File(root, "${date}_100NCZ_8_Z8").apply { mkdirs() }
        listOf("A.JPG", "B.JPG", "C.JPG").forEach {
            File(dayFolder, it).writeBytes(ByteArray(16))
        }

        photoLibraryLocation = mockk(relaxed = true) {
            every { appPrivateRoot() } returns root
            every { listDateFolders() } returns listOf(
                PhotoLibraryLocation.DateFolderSummary(date, listOf(dayFolder), 3)
            )
            // C.JPG 만 기기 저장소에 있는 사진으로 취급한다 — 내보낼 것이 없어 건너뛰어야 한다.
            every { isInAppPrivateStorage(any()) } answers {
                !firstArg<String>().endsWith("C.JPG")
            }
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

    /** 그룹을 열고 전부 선택한 상태까지 만든다. */
    private fun kotlinx.coroutines.test.TestScope.openAndSelectAll(
        viewModel: ServerPhotosViewModel
    ) {
        viewModel.openGroup(GalleryGroupKey.Date(date))
        advanceUntilIdle()
        viewModel.startMultiSelectMode(viewModel.uiState.value.photos.first().id)
        viewModel.selectAllPhotos()
    }

    @Test
    fun `성공 실패 건너뜀이 한 집계에 모두 실린다`() = runTest {
        // B.JPG 만 실패시킨다 — 나머지는 계속 진행돼야 한다.
        coEvery { photoLibraryLocation.exportToDeviceGallery(any()) } answers {
            !firstArg<File>().name.startsWith("B")
        }

        val viewModel = createViewModel()
        advanceUntilIdle()
        openAndSelectAll(viewModel)

        viewModel.exportSelectedPhotos()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(GalleryExportSummary(exported = 1, alreadyInDeviceStorage = 1, failed = 1), state.exportSummary)
        // 진행 표시는 끝나면 사라진다.
        assertNull(state.exportProgress)
        // 삭제와 마찬가지로 동작이 끝나면 선택 모드를 푼다.
        assertFalse(state.isMultiSelectMode)
        assertEquals(emptySet<String>(), state.selectedPhotos)

        // 기기 저장소 사진(C.JPG)에는 아예 손대지 않는다.
        coVerify(exactly = 2) { photoLibraryLocation.exportToDeviceGallery(any()) }
        coVerify(exactly = 0) {
            photoLibraryLocation.exportToDeviceGallery(File(dayFolder, "C.JPG"))
        }
    }

    @Test
    fun `전부 기기 저장소 사진이면 내보내기를 시도하지 않고 이유만 알린다`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openGroup(GalleryGroupKey.Date(date))
        advanceUntilIdle()
        val deviceStoragePhoto = viewModel.uiState.value.photos.first { it.filePath.endsWith("C.JPG") }
        viewModel.startMultiSelectMode(deviceStoragePhoto.id)

        viewModel.exportSelectedPhotos()
        advanceUntilIdle()

        assertEquals(
            GalleryExportSummary(exported = 0, alreadyInDeviceStorage = 1, failed = 0),
            viewModel.uiState.value.exportSummary
        )
        coVerify(exactly = 0) { photoLibraryLocation.exportToDeviceGallery(any()) }
    }

    @Test
    fun `확인 다이얼로그용 미리보기가 실제 내보낼 장수와 같다`() = runTest {
        coEvery { photoLibraryLocation.exportToDeviceGallery(any()) } returns true

        val viewModel = createViewModel()
        advanceUntilIdle()
        openAndSelectAll(viewModel)

        // 다이얼로그가 보여줄 숫자.
        val preview = viewModel.previewExportTargets()
        assertEquals(2, preview.targets.size)
        assertEquals(1, preview.alreadyInDeviceStorage)

        // 실제 실행 결과가 그 숫자와 어긋나면 사용자에게 거짓을 보여준 것이다.
        viewModel.exportSelectedPhotos()
        advanceUntilIdle()

        val summary = viewModel.uiState.value.exportSummary
        assertEquals(preview.targets.size, summary?.exported)
        assertEquals(preview.alreadyInDeviceStorage, summary?.alreadyInDeviceStorage)
    }

    @Test
    fun `대상이 없으면 미리보기가 비어 화면이 다이얼로그를 열지 않는다`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openGroup(GalleryGroupKey.Date(date))
        advanceUntilIdle()
        val deviceStoragePhoto =
            viewModel.uiState.value.photos.first { it.filePath.endsWith("C.JPG") }
        viewModel.startMultiSelectMode(deviceStoragePhoto.id)

        val preview = viewModel.previewExportTargets()
        assertEquals(emptyList<Any>(), preview.targets)
        assertEquals(1, preview.alreadyInDeviceStorage)
    }

    @Test
    fun `안내를 닫으면 집계가 사라진다`() = runTest {
        coEvery { photoLibraryLocation.exportToDeviceGallery(any()) } returns true

        val viewModel = createViewModel()
        advanceUntilIdle()
        openAndSelectAll(viewModel)

        viewModel.exportSelectedPhotos()
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.exportSummary?.exported)

        viewModel.clearExportSummary()
        assertNull(viewModel.uiState.value.exportSummary)
    }
}
