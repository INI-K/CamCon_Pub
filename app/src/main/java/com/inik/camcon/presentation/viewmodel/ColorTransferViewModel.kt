package com.inik.camcon.presentation.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.domain.usecase.ColorTransferUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * ViewModel for color transfer functionality.
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

    private fun updateProcessingStatus(status: String, progress: Float = 0f) {
        _processingStatus.value = status
        _processingProgress.value = progress
    }

    private fun updatePerformanceInfo(processingTime: Long, isNativeUsed: Boolean) {
        val method = if (isNativeUsed) "네이티브 최적화" else "코틀린 폴백"
        _performanceInfo.value = "처리 시간: ${processingTime}ms ($method)"
    }

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

                // Copy from URI to file
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    FileOutputStream(targetFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                // Validate image file
                if (colorTransferUseCase.isValidImageFile(targetFile.absolutePath)) {
                    _selectedImagePath.value = targetFile.absolutePath
                    loadAvailableImages(context)
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

    fun selectImagePath(path: String) {
        _selectedImagePath.value = path
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearPerformanceInfo() {
        _performanceInfo.value = null
    }

    fun clearProcessingStatus() {
        _processingStatus.value = null
        _processingProgress.value = 0f
    }

    /**
     * Preview color transfer processing.
     * Uses scaled images (maxSize=400) for fast processing.
     * Returns a Bitmap for UI display by loading the result file from the UseCase.
     */
    suspend fun processColorTransfer(
        referenceImagePath: String,
        targetImagePath: String,
        intensity: Float
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("ColorTransferViewModel", "Preview color transfer start")

            updateProcessingStatus("색감 전송 처리 중...", 0.5f)

            val startTime = System.currentTimeMillis()

            // Try GPU-cached method first with scaled preview size
            android.util.Log.d("ColorTransferViewModel", "GPU-cached color transfer attempt...")
            val resultPath = try {
                colorTransferUseCase.applyColorTransferWithGPUCached(
                    targetImagePath,
                    referenceImagePath,
                    intensity,
                    maxSize = 400
                )
            } catch (e: Exception) {
                android.util.Log.w("ColorTransferViewModel", "GPU-cached failed, CPU fallback: ${e.message}")
                colorTransferUseCase.applyColorTransfer(
                    targetImagePath,
                    referenceImagePath,
                    intensity,
                    maxSize = 400
                )
            }

            val processingTime = System.currentTimeMillis() - startTime

            if (resultPath != null) {
                val bitmap = BitmapFactory.decodeFile(resultPath)
                // Clean up temp file
                File(resultPath).delete()

                if (bitmap != null) {
                    android.util.Log.d("ColorTransferViewModel", "Color transfer succeeded (${processingTime}ms)")
                    updatePerformanceInfo(processingTime, true)
                    updateProcessingStatus("처리 완료", 1f)
                    return@withContext bitmap
                }
            }

            android.util.Log.e("ColorTransferViewModel", "Color transfer failed")
            _errorMessage.value = "색감 전송 처리에 실패했습니다"
            null
        } catch (e: Exception) {
            android.util.Log.e("ColorTransferViewModel", "Color transfer exception: ${e.message}")
            _errorMessage.value = "색감 전송 처리 중 오류가 발생했습니다: ${e.message}"
            null
        }
    }

    /**
     * Full-size color transfer processing.
     * Returns a Bitmap for UI display by loading the result file from the UseCase.
     */
    suspend fun processColorTransferFullSize(
        referenceImagePath: String,
        targetImagePath: String,
        intensity: Float
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("ColorTransferViewModel", "Full-size color transfer start")

            updateProcessingStatus("원본 크기로 색감 전송 처리 중...", 0.3f)

            updateProcessingStatus("GPU 가속 색감 전송 적용 중...", 0.7f)

            val startTime = System.currentTimeMillis()

            // Try GPU method first (full size, no maxSize limit)
            android.util.Log.d("ColorTransferViewModel", "GPU full-size processing attempt...")
            val resultPath = try {
                colorTransferUseCase.applyColorTransferWithGPU(
                    targetImagePath,
                    referenceImagePath,
                    intensity
                )
            } catch (e: Exception) {
                android.util.Log.w("ColorTransferViewModel", "GPU failed, CPU fallback: ${e.message}")
                colorTransferUseCase.applyColorTransfer(
                    targetImagePath,
                    referenceImagePath,
                    intensity
                )
            }

            val processingTime = System.currentTimeMillis() - startTime

            if (resultPath != null) {
                val bitmap = BitmapFactory.decodeFile(resultPath)
                // Clean up temp file
                File(resultPath).delete()

                if (bitmap != null) {
                    android.util.Log.d("ColorTransferViewModel", "Full-size color transfer succeeded (${processingTime}ms)")
                    updatePerformanceInfo(processingTime, true)
                    updateProcessingStatus("처리 완료", 1f)
                    return@withContext bitmap
                }
            }

            android.util.Log.e("ColorTransferViewModel", "Full-size color transfer failed")
            _errorMessage.value = "원본 크기 색감 전송에 실패했습니다"
            null
        } catch (e: Exception) {
            android.util.Log.e("ColorTransferViewModel", "Full-size color transfer exception: ${e.message}")
            _errorMessage.value = "원본 크기 색감 전송 중 오류가 발생했습니다: ${e.message}"
            null
        }
    }
}
