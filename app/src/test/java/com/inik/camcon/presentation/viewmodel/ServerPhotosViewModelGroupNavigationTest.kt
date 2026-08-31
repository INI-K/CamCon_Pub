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
import org.junit.Assert.assertTrue
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
    private lateinit var appPrivateRoot: File

    /** 저장 폴더 이름 → 메타에 적힌 카메라 원본 폴더. 실제 구현의 메타 파일을 대신한다. */
    private val folderLabels = mutableMapOf("${date}_100NCZ_8_Z8" to "100NCZ_8")

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

        appPrivateRoot = root
        photoLibraryLocation = mockk(relaxed = true) {
            every { appPrivateRoot() } returns root
            every { listDateFolders() } returns listOf(
                PhotoLibraryLocation.DateFolderSummary(date, listOf(dayFolder), 1)
            )
            // 기본은 원본 폴더 하나 — 대부분의 날짜가 그렇다(그 날짜는 2단을 건너뛴다).
            every { listCameraFolders(date) } returns listOf(
                PhotoLibraryLocation.CameraFolderSummary("100NCZ_8", 1)
            )
            // 프로덕션과 같은 규칙: 저장 시점에 적어 둔 메타(원본 폴더)가 정본이다.
            every { folderLabelOf(any()) } answers { folderLabels[firstArg<File>().name] }
        }
    }

    /** 카메라가 폴더를 넘긴 날을 만든다: `100NCZ_8` 1장 + `101NCZ_8` 2장. */
    private fun givenTwoCameraFolders() {
        val second = File(appPrivateRoot, "${date}_101NCZ_8_Z8").apply { mkdirs() }
        File(second, "DSC_0002.jpg").writeBytes(ByteArray(16))
        File(second, "DSC_0003.jpg").writeBytes(ByteArray(16))
        folderLabels["${date}_101NCZ_8_Z8"] = "101NCZ_8"
        every { photoLibraryLocation.listCameraFolders(date) } returns listOf(
            PhotoLibraryLocation.CameraFolderSummary("100NCZ_8", 1),
            PhotoLibraryLocation.CameraFolderSummary("101NCZ_8", 2)
        )
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

    // ── 3단 내비게이션 (날짜 → 원본 폴더 → 사진) ──

    @Test
    fun `폴더가 하나뿐인 날짜는 2단을 건너뛰고 사진으로 들어간다`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openGroup(GalleryGroupKey.Date(date))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(CameraFolderSelection("100NCZ_8"), state.openedFolder)
        // 들어온 경로를 기억해야 뒤로가기가 없는 화면(폴더 목록)으로 나가지 않는다.
        assertTrue(state.skippedFolderLevel)
        assertEquals(1, state.photos.size)
    }

    @Test
    fun `폴더가 여럿이면 2단에서 멈추고 고른 폴더의 사진만 연다`() = runTest {
        givenTwoCameraFolders()
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openGroup(GalleryGroupKey.Date(date))
        advanceUntilIdle()

        // 2단: 사진은 아직 읽지 않는다.
        val folderLevel = viewModel.uiState.value
        assertNull(folderLevel.openedFolder)
        assertEquals(2, folderLevel.folders.size)
        assertEquals(emptyList<Any>(), folderLevel.photos)

        viewModel.openFolder(CameraFolderSelection("101NCZ_8"))
        advanceUntilIdle()

        val photoLevel = viewModel.uiState.value
        assertEquals(CameraFolderSelection("101NCZ_8"), photoLevel.openedFolder)
        assertFalse(photoLevel.skippedFolderLevel)
        // 다른 폴더(100NCZ_8)의 1장이 섞이면 안 된다.
        assertEquals(2, photoLevel.photos.size)
        assertTrue(photoLevel.photos.all { it.filePath.contains("101NCZ_8") })
    }

    @Test
    fun `3단에서 뒤로 가면 폴더 목록으로 돌아간다`() = runTest {
        givenTwoCameraFolders()
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openGroup(GalleryGroupKey.Date(date))
        advanceUntilIdle()
        viewModel.openFolder(CameraFolderSelection("101NCZ_8"))
        advanceUntilIdle()

        viewModel.closeFolder()

        val state = viewModel.uiState.value
        assertNull(state.openedFolder)
        // 날짜는 그대로 열려 있어야 한다 — 한 번에 한 단계씩만 내려간다.
        assertEquals(GalleryGroupKey.Date(date), state.openedGroup)
        assertEquals(2, state.folders.size)
    }

    @Test
    fun `2단을 건너뛰고 들어왔으면 뒤로가기는 날짜 목록으로 나간다`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openGroup(GalleryGroupKey.Date(date))
        advanceUntilIdle()

        viewModel.closeFolder()

        // 없는 화면(폴더 하나짜리 목록)을 만들어 보여주지 않는다.
        val state = viewModel.uiState.value
        assertNull(state.openedGroup)
        assertNull(state.openedFolder)
        assertFalse(state.skippedFolderLevel)
    }

    @Test
    fun `기기 저장소는 폴더 단 없이 바로 사진으로 들어간다`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openGroup(GalleryGroupKey.DeviceStorage)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(GalleryGroupKey.DeviceStorage, state.openedGroup)
        assertEquals(CameraFolderSelection(null), state.openedFolder)
        assertTrue(state.skippedFolderLevel)
    }
}
