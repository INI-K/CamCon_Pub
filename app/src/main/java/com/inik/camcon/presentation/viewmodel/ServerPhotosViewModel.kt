package com.inik.camcon.presentation.viewmodel

import android.content.Context
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.di.IoDispatcher
import com.inik.camcon.domain.model.CapturedPhoto
import com.inik.camcon.data.repository.managers.PhotoLibraryLocation
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
    /** null 이면 루트(날짜 목록), 아니면 2단(사진 그리드). */
    val openedGroup: GalleryGroupKey? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedPhotos: Set<String> = emptySet(), // 선택된 사진들의 ID 집합
    val isMultiSelectMode: Boolean = false, // 멀티 선택 모드 여부
    /** 일괄 내보내기가 도는 동안에만 non-null. */
    val exportProgress: GalleryExportProgress? = null,
    /** 일괄 내보내기가 끝난 직후의 집계. 안내를 닫으면 null 로 돌아간다. */
    val exportSummary: GalleryExportSummary? = null,
    val pendingDeleteRequest: android.app.RecoverableSecurityException? = null, // 권한 요청이 필요한 삭제 작업
    val pendingDeletePhotoIds: List<String> = emptyList() // 삭제 대기 중인 사진 ID들
)

@HiltViewModel
class ServerPhotosViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cameraRepository: CameraRepository,
    private val validateImageFormatUseCase: ValidateImageFormatUseCase,
    private val photoLibraryLocation: com.inik.camcon.data.repository.managers.PhotoLibraryLocation,
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
    private fun loadAppPrivatePhotos(date: String?): List<CapturedPhoto> {
        val root = photoLibraryLocation.appPrivateRoot()
        if (!root.exists() || !root.isDirectory) return emptyList()

        // date 가 주어지면 그 날짜 폴더만 훑는다 — 2단 화면은 한 날짜만 필요하다.
        // null 이면 전체(중복 접기 기준을 만들 때 쓴다).
        val dirs = root.listFiles()
            ?.filter { it.isDirectory && (date == null || it.name.startsWith("${date}_") || it.name == date) }
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
        // 2단 화면을 열어 둔 채 돌아온 경우(뷰어에서 복귀 등)에는 그 그룹도 다시 읽는다.
        _uiState.value.openedGroup?.let { loadPhotosForGroup(it) }
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
        _uiState.value = _uiState.value.copy(openedGroup = key, photos = emptyList())
        loadPhotosForGroup(key)
    }

    /** 2단에서 루트(날짜 목록)로 돌아간다. 뒤로가기 버튼과 시스템 백이 같이 부른다. */
    fun closeGroup() {
        _uiState.value = _uiState.value.copy(
            openedGroup = null,
            photos = emptyList(),
            selectedPhotos = emptySet(),
            isMultiSelectMode = false
        )
    }

    private fun loadPhotosForGroup(key: GalleryGroupKey) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val photos = withContext(ioDispatcher) {
                    when (key) {
                        is GalleryGroupKey.Date -> loadAppPrivatePhotos(key.date)
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
        val allPhotoIds = _uiState.value.photos.map { it.id }.toSet()
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
            _uiState.value = _uiState.value.copy(
                photos = _uiState.value.photos.filter { !deletedIds.contains(it.id) }
            )
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