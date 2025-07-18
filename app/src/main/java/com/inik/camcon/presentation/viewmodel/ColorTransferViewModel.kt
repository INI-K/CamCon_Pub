package com.inik.camcon.presentation.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.domain.usecase.ColorTransferUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * 색감 전송 기능을 위한 ViewModel
 */
@HiltViewModel
class ColorTransferViewModel @Inject constructor(
    private val colorTransferUseCase: ColorTransferUseCase
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _processingProgress = MutableStateFlow(0f)
    val processingProgress: StateFlow<Float> = _processingProgress.asStateFlow()

    private val _processingStatus = MutableStateFlow<String?>(null)
    val processingStatus: StateFlow<String?> = _processingStatus.asStateFlow()

    private val _performanceInfo = MutableStateFlow<String?>(null)
    val performanceInfo: StateFlow<String?> = _performanceInfo.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _availableImages = MutableStateFlow<List<String>>(emptyList())
    val availableImages: StateFlow<List<String>> = _availableImages.asStateFlow()

    private val _selectedImagePath = MutableStateFlow<String?>(null)
    val selectedImagePath: StateFlow<String?> = _selectedImagePath.asStateFlow()

    /**
     * 색감 전송 처리 상태를 업데이트합니다
     */
    private fun updateProcessingStatus(status: String, progress: Float = 0f) {
        _processingStatus.value = status
        _processingProgress.value = progress
    }

    /**
     * 성능 정보를 업데이트합니다
     */
    private fun updatePerformanceInfo(processingTime: Long, isNativeUsed: Boolean) {
        val method = if (isNativeUsed) "네이티브 최적화" else "코틀린 폴백"
        _performanceInfo.value = "처리 시간: ${processingTime}ms ($method)"
    }

    /**
     * 사용 가능한 이미지 목록을 로드합니다
     */
    fun loadAvailableImages(context: Context) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val imageDir = File(context.filesDir, "color_transfer_images")
                if (!imageDir.exists()) {
                    imageDir.mkdirs()
                }

                val imageFiles = imageDir.listFiles { file ->
                    file.isFile && file.name.lowercase().let { name ->
                        name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                                name.endsWith(".png") || name.endsWith(".webp")
                    }
                }

                val imagePaths = imageFiles?.map { it.absolutePath } ?: emptyList()
                _availableImages.value = imagePaths

            } catch (e: Exception) {
                _errorMessage.value = "이미지 목록을 불러올 수 없습니다: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 갤러리에서 선택한 이미지를 처리합니다
     */
    fun handleImageSelection(uri: Uri, context: Context) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val imageDir = File(context.filesDir, "color_transfer_images")
                if (!imageDir.exists()) {
                    imageDir.mkdirs()
                }

                val fileName = "color_ref_${System.currentTimeMillis()}.jpg"
                val targetFile = File(imageDir, fileName)

                // URI에서 파일로 복사
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    FileOutputStream(targetFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                // 이미지 파일 유효성 검사
                if (colorTransferUseCase.isValidImageFile(targetFile.absolutePath)) {
                    _selectedImagePath.value = targetFile.absolutePath
                    loadAvailableImages(context) // 목록 새로고침
                } else {
                    targetFile.delete()
                    _errorMessage.value = "유효하지 않은 이미지 파일입니다"
                }

            } catch (e: Exception) {
                _errorMessage.value = "이미지 저장 중 오류가 발생했습니다: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 이미지 경로를 선택합니다
     */
    fun selectImagePath(path: String) {
        _selectedImagePath.value = path
    }

    /**
     * 에러 메시지를 초기화합니다
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * 성능 정보를 초기화합니다
     */
    fun clearPerformanceInfo() {
        _performanceInfo.value = null
    }

    /**
     * 처리 상태를 초기화합니다
     */
    fun clearProcessingStatus() {
        _processingStatus.value = null
        _processingProgress.value = 0f
    }
}