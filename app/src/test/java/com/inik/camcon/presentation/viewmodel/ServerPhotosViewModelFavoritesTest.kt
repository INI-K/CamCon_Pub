package com.inik.camcon.presentation.viewmodel

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.inik.camcon.data.datasource.local.PhotoFavoritesDataSource
import com.inik.camcon.data.repository.managers.PhotoLibraryLocation
import com.inik.camcon.domain.repository.CameraRepository
import com.inik.camcon.domain.usecase.ValidateImageFormatUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
 * 갤러리 좋아요(컬링)의 ViewModel 계약.
 *
 * 가장 중요한 것은 **필터와 다중 선택의 합성**이다. 필터를 켠 채 전체 선택을 눌렀는데 화면 밖
 * 사진까지 잡히면, 사용자가 고르지 않은 사진이 일괄 내보내기·삭제 대상이 된다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ServerPhotosViewModelFavoritesTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var context: Context
    private lateinit var cameraRepository: CameraRepository
    private lateinit var validateImageFormatUseCase: ValidateImageFormatUseCase
    private lateinit var photoLibraryLocation: PhotoLibraryLocation
    private lateinit var photoFavorites: PhotoFavoritesDataSource

    /** 저장소를 대신하는 가짜. 실제 계약(토글이 흐름으로 되돌아온다)을 그대로 흉내 낸다. */
    private val favoritesFlow = MutableStateFlow<Set<String>>(emptySet())

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
            every { listCameraFolders(date) } returns listOf(
                PhotoLibraryLocation.CameraFolderSummary("100NCZ_8", 3)
            )
            every { folderLabelOf(any()) } returns "100NCZ_8"
            every { isInAppPrivateStorage(any()) } returns true
        }

        photoFavorites = mockk(relaxed = true) {
            every { favorites } returns favoritesFlow
            coEvery { toggle(any()) } answers {
                val path = firstArg<String>()
                val liked = path !in favoritesFlow.value
                favoritesFlow.value =
                    if (liked) favoritesFlow.value + path else favoritesFlow.value - path
                liked
            }
            coEvery { remove(any()) } answers {
                favoritesFlow.value = favoritesFlow.value - firstArg<Collection<String>>().toSet()
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
        photoFavorites,
        testDispatcher
    )

    private fun kotlinx.coroutines.test.TestScope.openedViewModel(): ServerPhotosViewModel {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.openGroup(GalleryGroupKey.Date(date))
        advanceUntilIdle()
        return viewModel
    }

    private fun ServerPhotosViewModel.pathOf(name: String) =
        uiState.value.photos.first { it.filePath.endsWith(name) }.filePath

    @Test
    fun `좋아요를 토글하면 상태에 반영된다`() = runTest {
        val viewModel = openedViewModel()

        viewModel.toggleFavorite(viewModel.pathOf("A.JPG"))
        advanceUntilIdle()
        assertTrue(viewModel.pathOf("A.JPG") in viewModel.uiState.value.favorites)

        viewModel.toggleFavorite(viewModel.pathOf("A.JPG"))
        advanceUntilIdle()
        assertEquals(emptySet<String>(), viewModel.uiState.value.favorites)
    }

    @Test
    fun `필터를 켜면 전체 선택이 좋아요한 사진만 잡는다`() = runTest {
        val viewModel = openedViewModel()
        val likedPath = viewModel.pathOf("B.JPG")
        viewModel.toggleFavorite(likedPath)
        advanceUntilIdle()

        viewModel.toggleFavoritesFilter()
        val likedId = viewModel.uiState.value.photos.first { it.filePath == likedPath }.id
        viewModel.startMultiSelectMode(likedId)
        viewModel.selectAllPhotos()

        // ⚠️ 전체 3장 중 좋아요는 1장. 여기서 3이 나오면 사용자가 고르지 않은 사진이
        // 일괄 내보내기·삭제 대상이 된다.
        assertEquals(setOf(likedId), viewModel.uiState.value.selectedPhotos)
    }

    @Test
    fun `필터가 꺼져 있으면 전체 선택은 목록 전부를 잡는다`() = runTest {
        val viewModel = openedViewModel()
        viewModel.startMultiSelectMode(viewModel.uiState.value.photos.first().id)

        viewModel.selectAllPhotos()

        assertEquals(3, viewModel.uiState.value.selectedPhotos.size)
    }

    @Test
    fun `필터를 뒤집으면 선택이 풀린다`() = runTest {
        val viewModel = openedViewModel()
        viewModel.startMultiSelectMode(viewModel.uiState.value.photos.first().id)
        viewModel.selectAllPhotos()

        viewModel.toggleFavoritesFilter()

        // 필터 안에서 고른 선택이 필터 밖 목록으로 넘어가면 일괄 동작의 대상이 흐려진다.
        val state = viewModel.uiState.value
        assertTrue(state.showFavoritesOnly)
        assertEquals(emptySet<String>(), state.selectedPhotos)
        assertFalse(state.isMultiSelectMode)
    }

    @Test
    fun `내보내기 대상은 선택한 것만이다`() = runTest {
        val viewModel = openedViewModel()
        val likedPath = viewModel.pathOf("C.JPG")
        viewModel.toggleFavorite(likedPath)
        advanceUntilIdle()

        viewModel.toggleFavoritesFilter()
        val likedId = viewModel.uiState.value.photos.first { it.filePath == likedPath }.id
        viewModel.startMultiSelectMode(likedId)
        viewModel.selectAllPhotos()

        // 필터 → 전체 선택 → 일괄 내보내기가 "골라낸 것만 갤러리로"가 되는 지점.
        val plan = viewModel.previewExportTargets()
        assertEquals(1, plan.targets.size)
        assertEquals(likedPath, plan.targets.single().filePath)
    }

    @Test
    fun `셀렉정보는 선택한 사진만 담고 좋아요를 반영한다`() = runTest {
        val viewModel = openedViewModel()
        val likedPath = viewModel.pathOf("A.JPG")
        viewModel.toggleFavorite(likedPath)
        advanceUntilIdle()

        val likedId = viewModel.uiState.value.photos.first { it.filePath == likedPath }.id
        val otherId = viewModel.uiState.value.photos.first { it.filePath.endsWith("B.JPG") }.id
        viewModel.startMultiSelectMode(likedId)
        viewModel.togglePhotoSelection(otherId)

        viewModel.exportSelectInfo()
        advanceUntilIdle()

        val share = viewModel.uiState.value.selectInfoShare
        assertEquals(2, share?.photoCount)
        val root = com.google.gson.JsonParser.parseString(share!!.file.readText()).asJsonObject
        val names = root["photos"].asJsonArray.map { it.asJsonObject["fileName"].asString }
        // 고르지 않은 C.JPG 가 새어 들어가면 데스크톱에서 선택이 부풀려진다.
        assertEquals(listOf("A.JPG", "B.JPG"), names.sorted())
        val liked = root["photos"].asJsonArray
            .single { it.asJsonObject["fileName"].asString == "A.JPG" }
            .asJsonObject
        assertTrue(liked["favorite"].asBoolean)

        // 동작이 끝나면 다중 선택은 푼다(내보내기와 같은 규약).
        assertFalse(viewModel.uiState.value.isMultiSelectMode)
    }

    @Test
    fun `선택이 없으면 셀렉정보를 만들지 않는다`() = runTest {
        val viewModel = openedViewModel()

        viewModel.exportSelectInfo()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.selectInfoShare)
    }

    @Test
    fun `좋아요는 그룹을 닫아도 남는다`() = runTest {
        val viewModel = openedViewModel()
        val likedPath = viewModel.pathOf("A.JPG")
        viewModel.toggleFavorite(likedPath)
        advanceUntilIdle()

        viewModel.closeGroup()
        advanceUntilIdle()

        // 좋아요는 화면 상태가 아니라 저장소의 것이다(내보내기·삭제와도 독립).
        assertTrue(likedPath in viewModel.uiState.value.favorites)
    }
}
