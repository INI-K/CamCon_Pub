package com.inik.camcon.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.domain.model.CapturedPhoto
import com.inik.camcon.domain.repository.CameraRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val cameraRepository: CameraRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServerPhotosUiState())
    val uiState: StateFlow<ServerPhotosUiState> = _uiState.asStateFlow()

    // 내부 저장소 경로
    private val internalPhotoDir = "/data/data/com.inik.camcon/files"

    init {
        loadLocalPhotos()
    }

    /**
     * 내부 저장소에서 실제 사진 파일들을 스캔해서 로드
     */
    private fun loadLocalPhotos() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val photos = withContext(Dispatchers.IO) {
                    scanLocalPhotoFiles()
                }

                _uiState.value = _uiState.value.copy(
                    photos = photos,
                    isLoading = false
                )

                Log.d("ServerPhotosViewModel", "로컬 사진 로드 완료: ${photos.size}개")
            } catch (e: Exception) {
                Log.e("ServerPhotosViewModel", "로컬 사진 로드 실패", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "사진을 불러오는 중 오류가 발생했습니다: ${e.message}"
                )
            }
        }
    }

    /**
     * 내부 저장소에서 이미지 파일들을 스캔
     */
    private suspend fun scanLocalPhotoFiles(): List<CapturedPhoto> {
        val photoDir = File(internalPhotoDir)
        if (!photoDir.exists()) {
            Log.d("ServerPhotosViewModel", "사진 디렉토리가 존재하지 않음: $internalPhotoDir")
            return emptyList()
        }

        val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "bmp")
        val photoFiles = photoDir.listFiles { file ->
            file.isFile && file.extension.lowercase() in imageExtensions
        } ?: return emptyList()

        return photoFiles
            .sortedByDescending { it.lastModified() } // 최신 순으로 정렬
            .map { file ->
                CapturedPhoto(
                    id = UUID.randomUUID().toString(),
                    filePath = file.absolutePath,
                    thumbnailPath = null,
                    captureTime = file.lastModified(),
                    cameraModel = "Unknown", // 파일에서는 카메라 모델 정보를 알 수 없음
                    settings = null,
                    size = file.length(),
                    width = 0, // 이미지 크기는 필요시 별도로 로드
                    height = 0,
                    isDownloading = false
                )
            }
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
        Log.d("ServerPhotosViewModel", "사진 목록 새로고침")
        loadLocalPhotos()
    }

    /**
     * 에러 메시지 클리어
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}