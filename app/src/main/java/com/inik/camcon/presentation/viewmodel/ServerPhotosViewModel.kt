package com.inik.camcon.presentation.viewmodel

import android.content.Context
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.di.IoDispatcher
import com.inik.camcon.domain.model.CapturedPhoto
import com.inik.camcon.data.datasource.local.PhotoFavoritesDataSource
import com.inik.camcon.data.repository.managers.PhotoLibraryLocation
import com.inik.camcon.data.util.ExifCameraModel
import com.inik.camcon.data.util.ExifCaptureTime
import com.inik.camcon.data.util.SelectInfoJson
import com.inik.camcon.data.util.SelectInfoPhoto
import com.inik.camcon.domain.repository.CameraRepository
import com.inik.camcon.domain.usecase.ValidateImageFormatUseCase
import com.inik.camcon.utils.LogMask
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/**
 * 갤러리 탭의 그룹 하나. 루트 화면(날짜 목록)의 항목이자 2단 화면의 입력이다.
 *
 * **구획은 소스로 가른다.** 앱 내부 저장분은 날짜별로 나뉘고([Date]), 기기 저장소(MediaStore)는
 * 통째로 하나다([DeviceStorage]). 배포본 사용자들이 예전 방식으로 저장해 온 사진이 후자에 있는데,
 * 그것을 날짜로 쪼개 앞쪽 구획에 섞으면 "앱이 관리하는 사진"과 "기기에 남아 있는 사진"의 경계가
 * 사라진다.
 */
sealed interface GalleryGroupKey {
    /** 앱 내부 저장분의 날짜 하나(`yyyy-MM-dd`). */
    data class Date(val date: String) : GalleryGroupKey

    /** MediaStore 의 CamCon 사진 전부(`DCIM/CamCon` + `Pictures/CamCon`). */
    data object DeviceStorage : GalleryGroupKey
}

/** 루트 화면에 그릴 항목. 사진 자체는 담지 않는다 — 열 때 그 그룹만 읽는다. */
data class GalleryGroup(
    val key: GalleryGroupKey,
    val photoCount: Int
)

/**
 * 2단(원본 폴더 목록)의 항목. [folder] 가 null 이면 원본 폴더 조각이 없는 "기타" 묶음이다.
 *
 * 카메라가 일정 장수마다 폴더를 넘기므로(`100NCZ_8` → `101NCZ_8`) 하루가 여러 폴더로 갈린다.
 */
data class CameraFolderGroup(
    val folder: String?,
    val photoCount: Int
)

/** 3단에서 열린 원본 폴더. [name] 이 null 이면 "기타" 묶음이다(열림/닫힘과 구분하려고 감쌌다). */
data class CameraFolderSelection(val name: String?)

/** 일괄 내보내기 진행 상황. 수십 장이면 수 초 걸리므로 장수를 화면에 보여 준다. */
data class GalleryExportProgress(
    val done: Int,
    val total: Int
)

/**
 * 일괄 내보내기 결과 집계. 문구는 화면이 만든다 — ViewModel 이 로케일 문자열을 들고 있지 않도록.
 *
 * @param alreadyInDeviceStorage 이미 기기 저장소에 있어 내보낼 것이 없던 사진 수.
 */
data class GalleryExportSummary(
    val exported: Int,
    val alreadyInDeviceStorage: Int,
    val failed: Int
)

/**
 * 화면에 실제로 그릴 사진들. "좋아요만 보기"가 켜져 있으면 좋아요한 것만 남는다.
 *
 * **다중 선택·전체 선택·일괄 내보내기가 모두 이 목록을 기준으로 돈다.** 필터를 켠 채 전체
 * 선택을 눌렀는데 화면 밖의 사진까지 잡히면 필터가 무의미해지고, 사용자가 고르지 않은 사진이
 * 갤러리로 나간다 — 컬링 흐름에서 가장 위험한 어긋남이라 순수 함수로 떼어 테스트한다.
 */
fun visibleGalleryPhotos(
    photos: List<CapturedPhoto>,
    favorites: Set<String>,
    showFavoritesOnly: Boolean
): List<CapturedPhoto> =
    if (showFavoritesOnly) photos.filter { it.filePath in favorites } else photos

/** 만들어진 셀렉정보 파일과 담긴 장수. 화면이 공유 시트를 띄우는 데 쓴다. */
data class SelectInfoShare(
    val file: File,
    val photoCount: Int
)

/** [selectGalleryExportTargets] 의 결과. */
data class GalleryExportTargets(
    val targets: List<CapturedPhoto>,
    val alreadyInDeviceStorage: Int
)

/**
 * 선택된 사진 중 **실제로 내보낼 것**만 고르고, 건너뛴 수를 함께 돌려준다.
 *
 * 기기 저장소(MediaStore) 사진은 이미 폰 갤러리에 있으므로 내보낼 것이 없다. 뷰어의 단건
 * 내보내기 버튼이 앱 내부 사진에만 뜨는 것과 같은 원칙이고, 일괄에서는 버튼을 감추는 대신
 * 건너뛴 수를 결과 안내에 정직하게 싣는다.
 *
 * 파일시스템을 직접 보지 않는 순수 함수다 — 판정은 [isAppPrivate] 에 위임한다.
 */
fun selectGalleryExportTargets(
    photos: List<CapturedPhoto>,
    selectedIds: Set<String>,
    isAppPrivate: (String) -> Boolean
): GalleryExportTargets {
    val selected = photos.filter { it.id in selectedIds }
    val (targets, skipped) = selected.partition { isAppPrivate(it.filePath) }
    return GalleryExportTargets(targets = targets, alreadyInDeviceStorage = skipped.size)
}

data class ServerPhotosUiState(
    /** 지금 열린 그룹의 사진들. 루트(날짜 목록)에서는 비어 있다. */
    val photos: List<CapturedPhoto> = emptyList(),
    /** 루트 화면의 항목들. 앱 내부 날짜들 + 맨 뒤에 기기 저장소 하나. */
    val groups: List<GalleryGroup> = emptyList(),
    /** null 이면 1단(날짜 목록). 값이 있으면 2단이나 3단이다. */
    val openedGroup: GalleryGroupKey? = null,
    /** 2단 화면의 항목들(그 날짜의 원본 폴더). 3단으로 바로 들어간 경우에도 유지한다. */
    val folders: List<CameraFolderGroup> = emptyList(),
    /** null 이면 2단(폴더 목록), 값이 있으면 3단(사진 그리드). */
    val openedFolder: CameraFolderSelection? = null,
    /**
     * 폴더가 하나뿐이라 2단을 건너뛰고 3단으로 바로 들어왔는가.
     *
     * 뒤로가기가 **들어온 경로 그대로** 나가야 하므로 기억한다 — 건너뛴 경우 3단에서 뒤로 가면
     * 폴더 목록이 아니라 날짜 목록이다.
     */
    val skippedFolderLevel: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedPhotos: Set<String> = emptySet(), // 선택된 사진들의 ID 집합
    val isMultiSelectMode: Boolean = false, // 멀티 선택 모드 여부
    /** 좋아요한 사진 경로들. 컬링 작업대의 상태다. */
    val favorites: Set<String> = emptySet(),
    /** "좋아요만 보기" 필터. 3단에서만 의미가 있다. */
    val showFavoritesOnly: Boolean = false,
    /** 일괄 내보내기가 도는 동안에만 non-null. */
    val exportProgress: GalleryExportProgress? = null,
    /** 일괄 내보내기가 끝난 직후의 집계. 안내를 닫으면 null 로 돌아간다. */
    val exportSummary: GalleryExportSummary? = null,
    /** 셀렉정보 파일이 만들어지면 채워진다. 화면이 공유 시트를 띄운 뒤 비운다. */
    val selectInfoShare: SelectInfoShare? = null,
    val pendingDeleteRequest: android.app.RecoverableSecurityException? = null, // 권한 요청이 필요한 삭제 작업
    val pendingDeletePhotoIds: List<String> = emptyList() // 삭제 대기 중인 사진 ID들
)

@HiltViewModel
class ServerPhotosViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cameraRepository: CameraRepository,
    private val validateImageFormatUseCase: ValidateImageFormatUseCase,
    private val photoLibraryLocation: com.inik.camcon.data.repository.managers.PhotoLibraryLocation,
    private val photoFavorites: PhotoFavoritesDataSource,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServerPhotosUiState())
    val uiState: StateFlow<ServerPhotosUiState> = _uiState.asStateFlow()

    /** 진행 중인 일괄 내보내기. 취소와 중복 실행 차단에만 쓴다. */
    private var exportJob: Job? = null

    /**
     * 파일 경로가 RAW 인지 판정한다. RAW 판정은 [ValidateImageFormatUseCase] 단일 지점에 위임(CLAUDE.md §2).
     * 필름 에디터 진입점 게이팅(RAW 비노출)에 사용한다.
     */
    fun isRawFile(path: String): Boolean = validateImageFormatUseCase.isRawFile(path)

    init {
        loadLocalPhotos()
        observeFavorites()
    }

    /** 좋아요 집합을 화면 상태에 잇는다. 저장소가 정본이라 토글 결과도 이 경로로 돌아온다. */
    private fun observeFavorites() {
        viewModelScope.launch {
            photoFavorites.favorites.collect { paths ->
                _uiState.value = _uiState.value.copy(favorites = paths)
            }
        }
    }

    /** 사진 한 장의 좋아요를 뒤집는다. 뷰어와 그리드 타일이 같이 부른다. */
    fun toggleFavorite(photoPath: String) {
        viewModelScope.launch {
            val liked = photoFavorites.toggle(photoPath)
            Log.d("ServerPhotosViewModel", "좋아요 ${if (liked) "추가" else "해제"}: ${LogMask.path(photoPath)}")
        }
    }

    /**
     * "좋아요만 보기"를 켜고 끈다.
     *
     * 목록을 다시 읽지 않는다 — 이미 읽어 둔 사진에서 파생만 한다. 필터를 끌 때 다중 선택은
     * 풀어 준다: 필터 안에서 고른 선택이 필터 밖 목록으로 그대로 넘어가면, 사용자가 보지 않은
     * 상태에서 "몇 장 선택됨"이 남아 일괄 동작의 대상이 흐려진다.
     */
    fun toggleFavoritesFilter() {
        val next = !_uiState.value.showFavoritesOnly
        _uiState.value = _uiState.value.copy(
            showFavoritesOnly = next,
            selectedPhotos = emptySet(),
            isMultiSelectMode = false
        )
        Log.d("ServerPhotosViewModel", "좋아요 필터 ${if (next) "켬" else "끔"}")
    }

    /** 화면에 실제로 보이는 사진들(필터 반영). 선택·내보내기의 기준 목록이다. */
    private fun visiblePhotos(): List<CapturedPhoto> = _uiState.value.let { state ->
        visibleGalleryPhotos(state.photos, state.favorites, state.showFavoritesOnly)
    }

    /**
     * 외부 저장소 DCIM/CamCon 폴더에서 사진들을 로드
     */
    private fun loadLocalPhotos() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val photos = withContext(ioDispatcher) { loadPhotosFromDCIM() }

                _uiState.value = _uiState.value.copy(
                    photos = photos.sortedByDescending { it.captureTime }, // 확실히 최신순으로 재정렬
                    isLoading = false
                )

                Log.d("ServerPhotosViewModel", "DCIM/CamCon 사진 로드 완료: ${photos.size}개")
            } catch (e: Exception) {
                Log.e("ServerPhotosViewModel", "DCIM/CamCon 사진 로드 실패", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "사진을 불러오는 중 오류가 발생했습니다: ${e.message}"
                )
            }
        }
    }

    /**
     * MediaStore를 사용하여 DCIM/CamCon 폴더의 사진들을 로드
     */
    private suspend fun loadPhotosFromDCIM(): List<CapturedPhoto> {
        val photos = mutableListOf<CapturedPhoto>()

        try {
            // MediaStore를 사용하여 DCIM/CamCon 폴더의 사진들 쿼리
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT
            )

            val selection = "${MediaStore.Images.Media.DATA} LIKE ?"
            val selectionArgs = arrayOf("%/DCIM/CamCon/%")
            val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

            val cursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.use { c ->
                val idColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val pathColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                val sizeColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val widthColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)

                while (c.moveToNext()) {
                    val id = c.getLong(idColumn)
                    val name = c.getString(nameColumn)
                    val path = c.getString(pathColumn)
                    val size = c.getLong(sizeColumn)
                    val date = c.getLong(dateColumn)
                    val width = c.getInt(widthColumn)
                    val height = c.getInt(heightColumn)

                    // 스코프드 스토리지(API29+)에서는 own-media 라도 raw File.exists() 가
                    // false 를 반환할 수 있어 존재 게이트를 두면 목록이 비는 회귀가 난다.
                    // MediaStore 쿼리 결과(own-media 무권한 반환)를 그대로 노출하고,
                    // 실제 로드는 content URI 로 관통한다(_ID 는 CapturedPhoto.id 로 전달).
                    photos.add(
                        CapturedPhoto(
                            id = id.toString(),
                            filePath = path,
                            thumbnailPath = null,
                            captureTime = date,
                            cameraModel = "Unknown",
                            settings = null,
                            size = size,
                            width = width,
                            height = height,
                            isDownloading = false
                        )
                    )
                }
            }

            Log.d("ServerPhotosViewModel", "MediaStore에서 DCIM/CamCon 사진 로드: ${photos.size}개")

        } catch (e: Exception) {
            Log.e("ServerPhotosViewModel", "MediaStore 쿼리 실패", e)

            // MediaStore 실패 시 직접 파일 시스템 접근으로 폴백
            return loadPhotosFromFileSystem()
        }

        return photos
    }

    /**
     * 앱 전용 저장소(`Android/data/<패키지>/files/DCIM/CamCon`)의 사진을 읽는다.
     *
     * 기본 저장 위치라 대부분의 사진이 여기 있다. 미디어 스캔 대상이 아니어서 MediaStore 질의로는
     * 절대 나오지 않으므로 파일시스템을 직접 훑는다. 폴더가 없으면(설정을 계속 켜 두었다면)
     * 빈 목록이고, 그건 오류가 아니다.
     */
    private fun loadAppPrivatePhotos(
        date: String?,
        cameraFolder: CameraFolderSelection? = null
    ): List<CapturedPhoto> {
        val root = photoLibraryLocation.appPrivateRoot()
        if (!root.exists() || !root.isDirectory) return emptyList()

        // date 가 주어지면 그 날짜 폴더만 훑는다 — 3단 화면은 한 날짜만 필요하다.
        // null 이면 전체(중복 접기 기준을 만들 때 쓴다).
        // cameraFolder 까지 주어지면 그 원본 폴더 조각을 가진 저장 폴더로 한 번 더 좁힌다.
        val dirs = root.listFiles()
            ?.filter { it.isDirectory && (date == null || it.name.startsWith("${date}_") || it.name == date) }
            // 폴더 판정은 저장 시점에 기록한 메타가 정본이다(없으면 레거시 폴백).
            // 2단 목록과 **같은 함수**를 써야 항목의 장수와 실제로 열리는 장수가 어긋나지 않는다.
            ?.filter {
                cameraFolder == null || photoLibraryLocation.folderLabelOf(it) == cameraFolder.name
            }
            .orEmpty()

        return dirs.asSequence()
            .flatMap { it.listFiles()?.asSequence().orEmpty() }
            .filter { it.isFile && PhotoLibraryLocation.isImage(it.name) }
            .map { file ->
                CapturedPhoto(
                    id = UUID.randomUUID().toString(),
                    filePath = file.absolutePath,
                    thumbnailPath = null,
                    captureTime = file.lastModified(),
                    cameraModel = "Unknown",
                    settings = null,
                    size = file.length(),
                    width = 0,
                    height = 0,
                    isDownloading = false
                )
            }
            .toList()
    }

    /**
     * 직접 파일 시스템 접근으로 DCIM/CamCon 폴더의 사진들을 로드 (폴백)
     */
    private fun loadPhotosFromFileSystem(): List<CapturedPhoto> {
        val photos = mutableListOf<CapturedPhoto>()

        // 가능한 외부 저장소 경로들
        val possiblePaths = listOf(
            "/storage/emulated/0/DCIM/CamCon",
            "/storage/self/primary/DCIM/CamCon",
            "/sdcard/DCIM/CamCon"
        )

        // 예외를 삼키지 않고 상위로 전파해, 폴백마저 실패하면 loadLocalPhotos()의
        // catch에서 uiState.error를 설정하도록 한다(빈 목록과 로딩 실패를 구분).
        for (path in possiblePaths) {
            val photoDir = File(path)
            if (photoDir.exists() && photoDir.isDirectory) {
                Log.d("ServerPhotosViewModel", "DCIM/CamCon 폴더 발견: ${LogMask.path(path)}")

                val imageExtensions =
                    setOf("jpg", "jpeg", "png", "webp", "bmp", "nef", "cr2", "arw", "dng")
                val photoFiles = photoDir.listFiles { file ->
                    file.isFile && file.extension.lowercase() in imageExtensions
                } ?: continue

                photos.addAll(
                    photoFiles
                        .sortedByDescending { it.lastModified() }
                        .map { file ->
                            CapturedPhoto(
                                id = UUID.randomUUID().toString(),
                                filePath = file.absolutePath,
                                thumbnailPath = null,
                                captureTime = file.lastModified(),
                                cameraModel = "Unknown",
                                settings = null,
                                size = file.length(),
                                width = 0,
                                height = 0,
                                isDownloading = false
                            )
                        }
                )

                Log.d("ServerPhotosViewModel", "직접 파일 시스템에서 사진 로드: ${photos.size}개")
                break // 첫 번째로 발견된 경로에서 로드
            }
        }

        return photos
    }

    /**
     * 사진 삭제 (MediaStore API 사용)
     */
    fun deletePhoto(photoId: String) {
        viewModelScope.launch {
            try {
                val photo = _uiState.value.photos.find { it.id == photoId }
                if (photo != null) {
                    val deleted = withContext(ioDispatcher) {
                        deletePhotoFromMediaStore(photo.filePath, photoId)
                    }

                    if (deleted) {
                        // UI에서 제거
                        _uiState.value = _uiState.value.copy(
                            photos = _uiState.value.photos.filter { it.id != photoId }
                        )
                        // 사라진 사진의 좋아요는 그 자리에서 지운다(스윕이 아니라 삭제 시점 정리).
                        photoFavorites.remove(listOf(photo.filePath))
                        Log.d("ServerPhotosViewModel", "사진 파일 삭제 완료: ${LogMask.path(photo.filePath)}")
                    } else {
                        throw Exception("MediaStore를 통한 파일 삭제 실패: ${photo.filePath}")
                    }
                }
            } catch (e: Exception) {
                Log.e("ServerPhotosViewModel", "사진 삭제 실패", e)
                _uiState.value = _uiState.value.copy(
                    error = "사진 삭제 중 오류가 발생했습니다: ${e.message}"
                )
            }
        }
    }

    /**
     * MediaStore를 통해 파일 삭제
     */
    private suspend fun deletePhotoFromMediaStore(filePath: String, photoId: String): Boolean {
        return try {
            // MediaStore에서 파일 찾기
            val projection = arrayOf(MediaStore.Images.Media._ID)
            val selection = "${MediaStore.Images.Media.DATA} = ?"
            val selectionArgs = arrayOf(filePath)

            val cursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )

            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val mediaId = c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                    val contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    val imageUri = android.net.Uri.withAppendedPath(contentUri, mediaId.toString())

                    // MediaStore를 통해 삭제 시도
                    try {
                        val deletedRows = context.contentResolver.delete(imageUri, null, null)
                        if (deletedRows > 0) {
                            Log.d("ServerPhotosViewModel", "MediaStore를 통해 파일 삭제 성공: ${LogMask.path(filePath)}")
                            return true
                        }
                    } catch (securityException: SecurityException) {
                        Log.w(
                            "ServerPhotosViewModel",
                            "MediaStore 삭제 권한 부족: ${LogMask.path(filePath)}",
                            securityException
                        )

                        // RecoverableSecurityException인 경우 사용자에게 권한 요청
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            if (securityException is android.app.RecoverableSecurityException) {
                                Log.i(
                                    "ServerPhotosViewModel",
                                    "RecoverableSecurityException - 사용자 권한 요청 필요: ${LogMask.path(filePath)}"
                                )
                                _uiState.value = _uiState.value.copy(
                                    pendingDeleteRequest = securityException,
                                    pendingDeletePhotoIds = listOf(photoId)
                                )
                                return false
                            }
                        }
                        // 일반적인 SecurityException은 삭제 실패로 처리
                        throw securityException
                    }
                }
            }

            Log.w("ServerPhotosViewModel", "MediaStore에서 파일을 찾을 수 없음: ${LogMask.path(filePath)}")
            false
        } catch (e: Exception) {
            Log.e("ServerPhotosViewModel", "파일 삭제 중 예외 발생: ${LogMask.path(filePath)}", e)
            false
        }
    }

    /**
     * 사진 목록 새로고침
     */
    fun refreshPhotos() {
        Log.d("ServerPhotosViewModel", "갤러리 새로고침")
        loadGroups()
        // 아래 단을 열어 둔 채 돌아온 경우(뷰어에서 복귀 등)에는 그 단도 다시 읽는다.
        // 3단이면 사진을, 2단이면 폴더 목록을 다시 읽는다 — 지금 보고 있는 화면만 갱신한다.
        val state = _uiState.value
        val key = state.openedGroup ?: return
        if (state.openedFolder != null) {
            loadPhotosForGroup(key, state.openedFolder)
        } else if (key is GalleryGroupKey.Date) {
            loadFoldersForDate(key.date)
        }
    }

    /**
     * 루트 화면(날짜 목록)을 채운다.
     *
     * **파일 메타를 읽지 않는다.** 앱 내부는 디렉터리 목록과 파일 개수만 세고, 기기 저장소는
     * 개수만 질의한다. 탭에 들어올 때마다 도는 경로라 여기서 전체 스캔을 하면 진입이 느려진다.
     */
    private fun loadGroups() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val groups = withContext(ioDispatcher) {
                    val dateGroups = photoLibraryLocation.listDateFolders().map {
                        GalleryGroup(GalleryGroupKey.Date(it.date), it.photoCount)
                    }
                    val deviceCount = countDeviceStoragePhotos()
                    // 기기 저장소는 항상 목록 맨 뒤다 — 앱이 관리하는 최신 사진이 위에 오도록.
                    if (deviceCount > 0) {
                        dateGroups + GalleryGroup(GalleryGroupKey.DeviceStorage, deviceCount)
                    } else {
                        dateGroups
                    }
                }
                _uiState.value = _uiState.value.copy(groups = groups, isLoading = false)
                Log.d("ServerPhotosViewModel", "갤러리 그룹 ${groups.size}개")
            } catch (e: Exception) {
                Log.e("ServerPhotosViewModel", "갤러리 그룹 로드 실패", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "사진을 불러오는 중 오류가 발생했습니다: ${e.message}"
                )
            }
        }
    }

    /** 그룹을 열어 2단(사진 그리드)으로 들어간다. */
    fun openGroup(key: GalleryGroupKey) {
        _uiState.value = _uiState.value.copy(
            openedGroup = key,
            photos = emptyList(),
            folders = emptyList(),
            openedFolder = null,
            skippedFolderLevel = false
        )

        when (key) {
            // 기기 저장소는 폴더 단이 없다(아래 [loadFoldersForDate] 주석 참조) — 곧장 사진으로.
            GalleryGroupKey.DeviceStorage -> {
                _uiState.value = _uiState.value.copy(
                    openedFolder = CameraFolderSelection(null),
                    skippedFolderLevel = true
                )
                loadPhotosForGroup(key)
            }

            is GalleryGroupKey.Date -> loadFoldersForDate(key.date)
        }
    }

    /**
     * 2단(원본 폴더 목록)을 채운다. **폴더가 하나뿐이면 2단을 건너뛰고 3단으로 바로 들어간다.**
     *
     * 대부분의 날짜는 폴더가 하나다(카메라가 폴더를 넘길 만큼 찍은 날만 여럿이다). 그런 날까지
     * "폴더 하나짜리 목록"을 거치게 하면 탭이 한 번 늘 뿐이다. 건너뛴 사실은
     * [ServerPhotosUiState.skippedFolderLevel] 에 남겨 뒤로가기가 들어온 경로 그대로 나가게 한다.
     */
    private fun loadFoldersForDate(date: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val folders = withContext(ioDispatcher) {
                    photoLibraryLocation.listCameraFolders(date)
                        .map { CameraFolderGroup(it.folder, it.photoCount) }
                }
                _uiState.value = _uiState.value.copy(folders = folders, isLoading = false)
                Log.d("ServerPhotosViewModel", "$date 원본 폴더 ${folders.size}개: " +
                        folders.joinToString(", ") { "${it.folder ?: "기타"}(${it.photoCount})" })

                if (folders.size <= 1) {
                    val only = folders.firstOrNull()?.folder
                    openFolder(CameraFolderSelection(only), skipped = true)
                }
            } catch (e: Exception) {
                Log.e("ServerPhotosViewModel", "원본 폴더 목록 로드 실패: $date", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "사진을 불러오는 중 오류가 발생했습니다: ${e.message}"
                )
            }
        }
    }

    /** 2단에서 원본 폴더를 열어 3단(사진 그리드)으로 들어간다. */
    fun openFolder(folder: CameraFolderSelection, skipped: Boolean = false) {
        val key = _uiState.value.openedGroup ?: return
        _uiState.value = _uiState.value.copy(
            openedFolder = folder,
            skippedFolderLevel = skipped,
            photos = emptyList()
        )
        loadPhotosForGroup(key, folder)
    }

    /**
     * 3단에서 한 단계 뒤로. **들어온 경로 그대로 나간다** — 폴더 단을 건너뛰고 들어왔으면
     * 폴더 목록이 아니라 날짜 목록으로 나간다(없는 화면을 만들어 보여주지 않는다).
     */
    fun closeFolder() {
        if (_uiState.value.skippedFolderLevel) {
            closeGroup()
            return
        }
        _uiState.value = _uiState.value.copy(
            openedFolder = null,
            photos = emptyList(),
            selectedPhotos = emptySet(),
            isMultiSelectMode = false
        )
    }

    /** 2단(또는 건너뛴 3단)에서 루트(날짜 목록)로 돌아간다. 뒤로가기 버튼과 시스템 백이 같이 부른다. */
    fun closeGroup() {
        _uiState.value = _uiState.value.copy(
            openedGroup = null,
            folders = emptyList(),
            openedFolder = null,
            skippedFolderLevel = false,
            photos = emptyList(),
            selectedPhotos = emptySet(),
            isMultiSelectMode = false
        )
    }

    private fun loadPhotosForGroup(
        key: GalleryGroupKey,
        folder: CameraFolderSelection? = _uiState.value.openedFolder
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val photos = withContext(ioDispatcher) {
                    when (key) {
                        is GalleryGroupKey.Date -> loadAppPrivatePhotos(key.date, folder)
                        GalleryGroupKey.DeviceStorage -> {
                            // 내보내기로 양쪽에 있는 파일은 앱 내부 쪽에만 보이게 접는다.
                            val appPrivate = loadAppPrivatePhotos(date = null)
                            val seen = appPrivate.mapTo(HashSet()) { fingerprint(it) }
                            loadPhotosFromDCIM().filterNot { fingerprint(it) in seen }
                        }
                    }
                }
                // 2단 진입 시 파일명을 한 줄로 남긴다. 중복 파일명이나 같은 사진이 두 번 뜨는지
                // 로그만으로 판정할 수 있어야 한다(연사와 중복 표시는 화면으로 구분되지 않는다).
                Log.d(
                    "ServerPhotosViewModel",
                    "그룹 $key 사진 ${photos.size}장: " +
                            photos.joinToString(", ") { it.filePath.substringAfterLast('/') }
                )
                _uiState.value = _uiState.value.copy(
                    photos = photos.sortedByDescending { it.captureTime },
                    isLoading = false
                )
            } catch (e: Exception) {
                Log.e("ServerPhotosViewModel", "그룹 사진 로드 실패: $key", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "사진을 불러오는 중 오류가 발생했습니다: ${e.message}"
                )
            }
        }
    }

    /** 중복 접기 기준. 파일명 + 크기 — 같은 사진이 두 소스에 있으면 둘 다 같다. */
    private fun fingerprint(photo: CapturedPhoto): String =
        "${photo.filePath.substringAfterLast('/')}:${photo.size}"

    /**
     * 기기 저장소(MediaStore)의 CamCon 사진 개수.
     *
     * ⚠️ 앱을 재설치하면 과거에 기여한 항목의 소유권이 사라져 스코프드 스토리지에서 안 읽힐 수 있다.
     * 그때는 조용한 빈 화면 대신 로그를 남기고, 읽히는 것만 보여준다(권한 요청은 범위 밖).
     */
    private suspend fun countDeviceStoragePhotos(): Int {
        val count = runCatching {
            // 목록과 **같은 접기 규칙**을 쓴다. 내보내기로 양쪽에 있게 된 사진을 개수에서만 세면,
            // "기기 저장소 12장"을 열었을 때 3장만 나오는 어긋남이 생긴다.
            val appPrivate = loadAppPrivatePhotos(date = null).mapTo(HashSet()) { fingerprint(it) }
            loadPhotosFromDCIM().count { fingerprint(it) !in appPrivate }
        }.getOrElse { e ->
            Log.w("ServerPhotosViewModel", "기기 저장소 조회 실패 — 읽히는 것만 표시한다", e)
            0
        }
        if (count == 0) {
            Log.i(
                "ServerPhotosViewModel",
                "기기 저장소에 CamCon 사진이 없다. 앱을 재설치했다면 과거 기여분의 소유권이 " +
                        "사라져 읽히지 않을 수 있다(스코프드 스토리지)."
            )
        }
        return count
    }

    /**
     * 에러 메시지 클리어
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * 멀티 선택 모드 시작
     */
    fun startMultiSelectMode(photoId: String) {
        _uiState.value = _uiState.value.copy(
            isMultiSelectMode = true,
            selectedPhotos = setOf(photoId)
        )
        Log.d("ServerPhotosViewModel", "멀티 선택 모드 시작: $photoId")
    }

    /**
     * 멀티 선택 모드 종료
     */
    fun exitMultiSelectMode() {
        _uiState.value = _uiState.value.copy(
            isMultiSelectMode = false,
            selectedPhotos = emptySet()
        )
        Log.d("ServerPhotosViewModel", "멀티 선택 모드 종료")
    }

    /**
     * 사진 선택/해제 토글
     */
    fun togglePhotoSelection(photoId: String) {
        val currentSelection = _uiState.value.selectedPhotos
        val newSelection = if (currentSelection.contains(photoId)) {
            currentSelection - photoId
        } else {
            currentSelection + photoId
        }

        Log.d("ServerPhotosViewModel", "사진 선택 토글: $photoId, 선택된 사진 수: ${newSelection.size}")

        // 선택된 사진이 하나도 없으면 멀티 선택 모드를 종료
        if (newSelection.isEmpty()) {
            exitMultiSelectMode()
        } else {
            _uiState.value = _uiState.value.copy(selectedPhotos = newSelection)
        }
    }

    /**
     * 모든 사진 선택
     */
    fun selectAllPhotos() {
        // ⚠️ 화면에 **보이는** 사진만 잡는다. 필터가 켜져 있는데 전체 목록을 잡으면 사용자가
        // 고르지 않은 사진까지 내보내기·삭제 대상이 된다(컬링 흐름의 핵심 계약).
        val allPhotoIds = visiblePhotos().map { it.id }.toSet()
        _uiState.value = _uiState.value.copy(selectedPhotos = allPhotoIds)
        Log.d("ServerPhotosViewModel", "모든 사진 선택: ${allPhotoIds.size}개")
    }

    /**
     * 모든 사진 선택 해제
     */
    fun deselectAllPhotos() {
        _uiState.value = _uiState.value.copy(selectedPhotos = emptySet())
        Log.d("ServerPhotosViewModel", "모든 사진 선택 해제")
    }

    /**
     * 확인 다이얼로그에 보여줄 대상 집계. 내보내기를 실행하지는 않는다.
     *
     * [exportSelectedPhotos] 와 **같은 순수 함수**로 계산하므로 미리 보여준 숫자와 실제로
     * 내보내는 장수가 어긋나지 않는다. 파일시스템 왕복도 없다(경로 문자열 판정뿐).
     */
    fun previewExportTargets(): GalleryExportTargets {
        val state = _uiState.value
        return selectGalleryExportTargets(
            photos = state.photos,
            selectedIds = state.selectedPhotos,
            isAppPrivate = photoLibraryLocation::isInAppPrivateStorage
        )
    }

    /**
     * 선택된 사진들을 기기 갤러리(MediaStore)로 내보낸다. 원본은 그대로 둔다.
     *
     * 규칙은 뷰어의 단건 내보내기와 같다 — 같은 폴더 체계로 복사하고, 설정 토글과 무관하게
     * 동작한다(사용자가 명시적으로 고른 행동이므로).
     *
     * 개별 실패는 건너뛰고 집계에만 싣는다. 한 장이 실패했다고 나머지를 멈추면 사용자가
     * 어디까지 됐는지 알 수 없다.
     */
    fun exportSelectedPhotos() {
        if (exportJob?.isActive == true) {
            Log.d("ServerPhotosViewModel", "내보내기가 이미 진행 중이라 무시")
            return
        }

        val state = _uiState.value
        val plan = selectGalleryExportTargets(
            photos = state.photos,
            selectedIds = state.selectedPhotos,
            isAppPrivate = photoLibraryLocation::isInAppPrivateStorage
        )

        if (plan.targets.isEmpty()) {
            // 전부 기기 저장소 사진이면 할 일이 없다. 조용히 끝내지 않고 이유를 알린다.
            _uiState.value = state.copy(
                exportSummary = GalleryExportSummary(
                    exported = 0,
                    alreadyInDeviceStorage = plan.alreadyInDeviceStorage,
                    failed = 0
                ),
                isMultiSelectMode = false,
                selectedPhotos = emptySet()
            )
            return
        }

        exportJob = viewModelScope.launch {
            var exported = 0
            var failed = 0
            _uiState.value = _uiState.value.copy(
                exportProgress = GalleryExportProgress(0, plan.targets.size),
                exportSummary = null
            )
            Log.d("ServerPhotosViewModel", "일괄 내보내기 시작: ${plan.targets.size}장")

            try {
                plan.targets.forEachIndexed { index, photo ->
                    val ok = photoLibraryLocation.exportToDeviceGallery(File(photo.filePath))
                    if (ok) exported++ else failed++
                    _uiState.value = _uiState.value.copy(
                        exportProgress = GalleryExportProgress(index + 1, plan.targets.size)
                    )
                }
            } finally {
                // 취소로 들어와도 여기까지 내보낸 만큼은 정직하게 집계한다(상태 갱신은 중단되지 않는다).
                _uiState.value = _uiState.value.copy(
                    exportProgress = null,
                    exportSummary = GalleryExportSummary(
                        exported = exported,
                        alreadyInDeviceStorage = plan.alreadyInDeviceStorage,
                        failed = failed
                    ),
                    isMultiSelectMode = false,
                    selectedPhotos = emptySet()
                )
                Log.i(
                    "ServerPhotosViewModel",
                    "일괄 내보내기 종료: 성공 $exported, 실패 $failed, 건너뜀 ${plan.alreadyInDeviceStorage}"
                )
                // 목록을 다시 읽는다. viewModelScope 의 별도 자식이라 취소된 뒤에도 돈다.
                refreshPhotos()
            }
        }
    }

    /**
     * 선택한 사진들의 **셀렉정보**(컬링 결과)를 JSON 파일로 만들고 공유 시트를 띄운다.
     *
     * 사진 자체가 아니라 "무엇을 골랐는가"를 데스크톱으로 넘기는 갈래다. 서버 연동은 없다 —
     * 파일을 만들고 사용자가 고른 앱(드라이브·메일 등)으로 건네는 데서 멈춘다.
     *
     * 사진 파일을 다시 읽는 곳은 EXIF 두 값(촬영 시각·기종)뿐이라 헤더만 훑는다.
     */
    fun exportSelectInfo() {
        val state = _uiState.value
        val selected = state.photos.filter { it.id in state.selectedPhotos }
        if (selected.isEmpty()) return

        viewModelScope.launch {
            try {
                val share = withContext(ioDispatcher) {
                    val rows = selected.map { photo ->
                        val file = File(photo.filePath)
                        SelectInfoPhoto(
                            fileName = file.name,
                            // 원본 폴더는 저장 시점에 적어 둔 메타가 정본이다(없으면 폴백 라벨).
                            srcFolder = file.parentFile?.let { photoLibraryLocation.folderLabelOf(it) },
                            // EXIF 가 없으면 null 그대로 둔다 — 수신 시각으로 채우지 않는다.
                            capturedAtMillis = ExifCaptureTime.parseMillis(file),
                            favorite = photo.filePath in state.favorites,
                            cameraModel = ExifCameraModel.parse(file)
                        )
                    }

                    val json = SelectInfoJson.build(rows, System.currentTimeMillis())
                    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
                        .format(Date(System.currentTimeMillis()))
                    val dir = File(context.getExternalFilesDir(null), "select-info")
                        .apply { mkdirs() }
                    val target = File(dir, "camcon-select_$stamp.json")
                    target.writeText(json)
                    SelectInfoShare(target, rows.size)
                }

                Log.i(
                    "ServerPhotosViewModel",
                    "셀렉정보 파일 생성: ${share.photoCount}장 → ${LogMask.path(share.file.absolutePath)}"
                )
                _uiState.value = _uiState.value.copy(
                    selectInfoShare = share,
                    isMultiSelectMode = false,
                    selectedPhotos = emptySet()
                )
            } catch (e: Exception) {
                Log.e("ServerPhotosViewModel", "셀렉정보 만들기 실패", e)
                _uiState.value = _uiState.value.copy(
                    error = "셀렉정보를 만들지 못했습니다: ${e.message}"
                )
            }
        }
    }

    /** 공유 시트를 띄운 뒤 호출한다. 같은 파일로 시트가 두 번 뜨지 않게 한다. */
    fun clearSelectInfoShare() {
        _uiState.value = _uiState.value.copy(selectInfoShare = null)
    }

    /** 진행 중인 일괄 내보내기를 멈춘다. 이미 내보낸 사진은 되돌리지 않는다. */
    fun cancelExport() {
        exportJob?.cancel()
    }

    /** 내보내기 결과 안내를 닫는다. */
    fun clearExportSummary() {
        _uiState.value = _uiState.value.copy(exportSummary = null)
    }

    /**
     * 선택된 사진들 삭제 (MediaStore API 사용)
     */
    fun deleteSelectedPhotos() {
        viewModelScope.launch {
            val selectedIds = _uiState.value.selectedPhotos.toList()
            Log.d("ServerPhotosViewModel", "선택된 사진들 삭제 시작: ${selectedIds.size}개")

            try {
                // Android 11+ 에서는 한 번에 여러 파일 삭제 권한 요청 가능
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    deleteBatchWithModernApi(selectedIds)
                } else {
                    deleteBatchLegacy(selectedIds)
                }
            } catch (e: Exception) {
                Log.e("ServerPhotosViewModel", "선택된 사진들 삭제 실패", e)
                _uiState.value = _uiState.value.copy(
                    error = "사진 삭제 중 오류가 발생했습니다: ${e.message}"
                )
            }
        }
    }

    /**
     * Android 11+ 용 배치 삭제 (createDeleteRequest 사용)
     */
    private suspend fun deleteBatchWithModernApi(selectedIds: List<String>) {
        val urisToDelete = mutableListOf<android.net.Uri>()
        val idsMapping = mutableMapOf<android.net.Uri, String>() // URI와 photoId 매핑

        withContext(ioDispatcher) {
            // 먼저 모든 선택된 파일의 MediaStore URI를 수집
            selectedIds.forEach { photoId ->
                val photo = _uiState.value.photos.find { it.id == photoId }
                if (photo != null) {
                    try {
                        val projection = arrayOf(MediaStore.Images.Media._ID)
                        val selection = "${MediaStore.Images.Media.DATA} = ?"
                        val selectionArgs = arrayOf(photo.filePath)

                        val cursor = context.contentResolver.query(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            projection,
                            selection,
                            selectionArgs,
                            null
                        )

                        cursor?.use { c ->
                            if (c.moveToFirst()) {
                                val mediaId =
                                    c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                                val contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                                val uri =
                                    android.net.Uri.withAppendedPath(contentUri, mediaId.toString())
                                urisToDelete.add(uri)
                                idsMapping[uri] = photoId
                                Log.d(
                                    "ServerPhotosViewModel",
                                    "삭제 대상 URI 추가: $uri (${LogMask.path(photo.filePath)})"
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("ServerPhotosViewModel", "URI 수집 실패: ${LogMask.path(photo.filePath)}", e)
                    }
                }
            }
        }

        if (urisToDelete.isEmpty()) {
            Log.w("ServerPhotosViewModel", "삭제할 URI가 없음")
            _uiState.value = _uiState.value.copy(
                isMultiSelectMode = false,
                selectedPhotos = emptySet(),
                error = "삭제할 파일을 찾을 수 없습니다."
            )
            return
        }

        try {
            // Android 11+ createDeleteRequest 사용
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val deleteRequest =
                    MediaStore.createDeleteRequest(context.contentResolver, urisToDelete)

                Log.d(
                    "ServerPhotosViewModel",
                    "createDeleteRequest 생성 완료, ${urisToDelete.size}개 파일"
                )

                // PendingIntent를 IntentSender로 변환하여 UI에 전달
                val intentSender = deleteRequest.intentSender
                val recoverableException = android.app.RecoverableSecurityException(
                    SecurityException("${urisToDelete.size}개의 사진 삭제 권한이 필요합니다"),
                    "삭제하려면 권한이 필요합니다",
                    android.app.RemoteAction(
                        android.graphics.drawable.Icon.createWithResource(
                            context,
                            android.R.drawable.ic_delete
                        ),
                        "삭제",
                        "선택된 사진들을 삭제합니다",
                        deleteRequest
                    )
                )

                _uiState.value = _uiState.value.copy(
                    pendingDeleteRequest = recoverableException,
                    pendingDeletePhotoIds = selectedIds
                )
                return
            }
        } catch (securityException: SecurityException) {
            if (securityException is android.app.RecoverableSecurityException) {
                Log.i("ServerPhotosViewModel", "배치 삭제 권한 요청 필요")
                _uiState.value = _uiState.value.copy(
                    pendingDeleteRequest = securityException,
                    pendingDeletePhotoIds = selectedIds
                )
                return
            }
        } catch (e: Exception) {
            Log.e("ServerPhotosViewModel", "createDeleteRequest 실패", e)
        }

        // createDeleteRequest 실패 시 개별 삭제로 폴백
        deleteBatchLegacy(selectedIds)
    }

    /**
     * Android 10 이하 또는 폴백용 개별 삭제
     */
    private suspend fun deleteBatchLegacy(selectedIds: List<String>) {
        val deletedIds = mutableListOf<String>()
        val failedIds = mutableListOf<String>()
        var needsPermission = false
        var securityException: android.app.RecoverableSecurityException? = null

        withContext(ioDispatcher) {
            selectedIds.forEach { photoId ->
                val photo = _uiState.value.photos.find { it.id == photoId }
                if (photo != null) {
                    val deleted = deletePhotoFromMediaStore(photo.filePath, photoId)
                    if (deleted) {
                        deletedIds.add(photoId)
                    } else {
                        // RecoverableSecurityException이 발생했는지 확인
                        val currentPendingRequest = _uiState.value.pendingDeleteRequest
                        if (currentPendingRequest != null && !needsPermission) {
                            needsPermission = true
                            securityException = currentPendingRequest
                            // 나머지 삭제 작업도 대기 상태로 설정
                        } else {
                            failedIds.add(photoId)
                            Log.w("ServerPhotosViewModel", "파일 삭제 실패: ${LogMask.path(photo.filePath)}")
                        }
                    }
                }
            }
        }

        // 권한 요청이 필요한 경우
        if (needsPermission && securityException != null) {
            _uiState.value = _uiState.value.copy(
                pendingDeleteRequest = securityException,
                pendingDeletePhotoIds = selectedIds
            )
            return
        }

        // 삭제된 사진들만 UI에서 제거
        if (deletedIds.isNotEmpty()) {
            val deletedPaths = _uiState.value.photos
                .filter { deletedIds.contains(it.id) }
                .map { it.filePath }
            _uiState.value = _uiState.value.copy(
                photos = _uiState.value.photos.filter { !deletedIds.contains(it.id) }
            )
            // 사라진 사진의 좋아요도 함께 지운다(경로 목록은 지우기 전에 뽑아 둔다).
            photoFavorites.remove(deletedPaths)
        }

        // 멀티 선택 모드 종료
        _uiState.value = _uiState.value.copy(
            isMultiSelectMode = false,
            selectedPhotos = emptySet()
        )

        Log.d(
            "ServerPhotosViewModel",
            "선택된 사진들 삭제 완료: 성공 ${deletedIds.size}개, 실패 ${failedIds.size}개"
        )

        // 일부 삭제 실패 시 사용자에게 알림
        if (failedIds.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                error = "${failedIds.size}개의 사진을 삭제할 수 없습니다. 파일이 다른 앱에서 사용 중이거나 권한이 없을 수 있습니다."
            )
        }
    }

    /**
     * 권한 요청 대기 상태 클리어
     */
    fun clearPendingDeleteRequest() {
        _uiState.value = _uiState.value.copy(
            pendingDeleteRequest = null,
            pendingDeletePhotoIds = emptyList()
        )
    }

    /**
     * 권한 승인 후 대기 중인 삭제 작업 재시도
     */
    fun retryPendingDelete() {
        val pendingIds = _uiState.value.pendingDeletePhotoIds
        clearPendingDeleteRequest()

        viewModelScope.launch {
            try {
                if (pendingIds.isNotEmpty()) {
                    // 권한 승인 후에는 실제 파일 삭제가 이미 완료되었을 수 있음
                    // UI에서 삭제된 항목들을 제거
                    _uiState.value = _uiState.value.copy(
                        photos = _uiState.value.photos.filter { !pendingIds.contains(it.id) },
                        isMultiSelectMode = false,
                        selectedPhotos = emptySet()
                    )

                    Log.d("ServerPhotosViewModel", "권한 승인 후 UI에서 ${pendingIds.size}개 사진 제거 완료")
                }
            } catch (e: Exception) {
                Log.e("ServerPhotosViewModel", "권한 승인 후 처리 실패", e)
                _uiState.value = _uiState.value.copy(
                    error = "삭제 완료 처리 중 오류가 발생했습니다: ${e.message}"
                )
            }
        }
    }
}