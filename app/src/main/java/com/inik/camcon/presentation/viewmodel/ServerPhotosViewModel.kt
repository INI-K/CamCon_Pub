package com.inik.camcon.presentation.viewmodel

import android.content.Context
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.domain.model.CapturedPhoto
import com.inik.camcon.domain.repository.CameraRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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
    val error: String? = null
)

@HiltViewModel
class ServerPhotosViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cameraRepository: CameraRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServerPhotosUiState())
    val uiState: StateFlow<ServerPhotosUiState> = _uiState.asStateFlow()

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
                val photos = withContext(Dispatchers.IO) {
                    loadPhotosFromDCIM()
                }

                _uiState.value = _uiState.value.copy(
                    photos = photos,
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

        try {
            // 가능한 외부 저장소 경로들
            val possiblePaths = listOf(
                "/storage/emulated/0/DCIM/CamCon",
                "/storage/self/primary/DCIM/CamCon",
                "/sdcard/DCIM/CamCon"
            )

            for (path in possiblePaths) {
                val photoDir = File(path)
                if (photoDir.exists() && photoDir.isDirectory) {
                    Log.d("ServerPhotosViewModel", "DCIM/CamCon 폴더 발견: $path")

                    val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "bmp")
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

        } catch (e: Exception) {
            Log.e("ServerPhotosViewModel", "직접 파일 시스템 접근 실패", e)
        }

        return photos
    }

    /**
     * 사진 삭제 (실제 파일 삭제)
     */
    fun deletePhoto(photoId: String) {
        viewModelScope.launch {
            try {
                val photo = _uiState.value.photos.find { it.id == photoId }
                if (photo != null) {
                    withContext(Dispatchers.IO) {
                        val file = File(photo.filePath)
                        if (file.exists()) {
                            val deleted = file.delete()
                            if (!deleted) {
                                throw Exception("파일 삭제 실패: ${photo.filePath}")
                            }
                        }
                    }

                    // UI에서 제거
                    _uiState.value = _uiState.value.copy(
                        photos = _uiState.value.photos.filter { it.id != photoId }
                    )

                    Log.d("ServerPhotosViewModel", "사진 파일 삭제 완료: ${photo.filePath}")
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
}