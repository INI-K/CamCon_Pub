package com.inik.camcon.presentation.viewmodel

import android.content.Context
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.di.IoDispatcher
import com.inik.camcon.domain.model.CapturedPhoto
import com.inik.camcon.domain.repository.CameraRepository
import com.inik.camcon.domain.usecase.ValidateImageFormatUseCase
import com.inik.camcon.utils.LogMask
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class ServerPhotosUiState(
    val photos: List<CapturedPhoto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedPhotos: Set<String> = emptySet(), // 선택된 사진들의 ID 집합
    val isMultiSelectMode: Boolean = false, // 멀티 선택 모드 여부
    val pendingDeleteRequest: android.app.RecoverableSecurityException? = null, // 권한 요청이 필요한 삭제 작업
    val pendingDeletePhotoIds: List<String> = emptyList() // 삭제 대기 중인 사진 ID들
)

@HiltViewModel
class ServerPhotosViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cameraRepository: CameraRepository,
    private val validateImageFormatUseCase: ValidateImageFormatUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServerPhotosUiState())
    val uiState: StateFlow<ServerPhotosUiState> = _uiState.asStateFlow()

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
                val photos = withContext(ioDispatcher) {
                    loadPhotosFromDCIM()
                }

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

                    // 파일이 실제로 존재하는지 확인
                    val file = File(path)
                    if (file.exists()) {
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
        Log.d("ServerPhotosViewModel", "DCIM/CamCon 사진 목록 새로고침")
        loadLocalPhotos()
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