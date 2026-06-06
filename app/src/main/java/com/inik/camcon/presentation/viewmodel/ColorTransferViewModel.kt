package com.inik.camcon.presentation.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.di.IoDispatcher
import com.inik.camcon.domain.usecase.ColorTransferUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * 색감 전송 처리 단계. 화면에서 stringResource 로 매핑하기 위한 로케일 독립 키.
 */
enum class ColorTransferStage {
    PREVIEW_PROCESSING,
    FULL_SIZE_PREPARING,
    FULL_SIZE_APPLYING,
    DONE
}

/**
 * 색감 전송 기능을 위한 ViewModel.
 */
@HiltViewModel
class ColorTransferViewModel @Inject constructor(
    private val colorTransferUseCase: ColorTransferUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _processingProgress = MutableStateFlow(0f)
    val processingProgress: StateFlow<Float> = _processingProgress.asStateFlow()

    private val _processingStatus = MutableStateFlow<String?>(null)
    val processingStatus: StateFlow<String?> = _processingStatus.asStateFlow()

    private val _processingStage = MutableStateFlow<ColorTransferStage?>(null)
    val processingStage: StateFlow<ColorTransferStage?> = _processingStage.asStateFlow()

    private val _performanceInfo = MutableStateFlow<String?>(null)
    val performanceInfo: StateFlow<String?> = _performanceInfo.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _availableImages = MutableStateFlow<List<String>>(emptyList())
    val availableImages: StateFlow<List<String>> = _availableImages.asStateFlow()

    private val _selectedImagePath = MutableStateFlow<String?>(null)
    val selectedImagePath: StateFlow<String?> = _selectedImagePath.asStateFlow()

    private fun updateProcessingStatus(
        status: String,
        progress: Float = 0f,
        stage: ColorTransferStage? = null
    ) {
        _processingStatus.value = status
        _processingProgress.value = progress
        if (stage != null) {
            _processingStage.value = stage
        }
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

                // URI에서 파일로 복사
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    FileOutputStream(targetFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                // 이미지 파일 유효성 검사
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
        _processingStage.value = null
    }

    /**
     * 미리보기 색감 전송 처리.
     * 빠른 처리를 위해 축소된 이미지(maxSize=400)를 사용한다.
     * UseCase에서 결과 파일을 로드하여 UI 표시용 Bitmap을 반환한다.
     */
    suspend fun processColorTransfer(
        referenceImagePath: String,
        targetImagePath: String,
        intensity: Float
    ): Bitmap? = withContext(ioDispatcher) {
        try {
            _isLoading.value = true
            updateProcessingStatus("색감 전송 처리 중...", 0.5f, ColorTransferStage.PREVIEW_PROCESSING)

            val startTime = System.currentTimeMillis()

            // 먼저 축소된 미리보기 크기로 GPU 캐시 방식 시도
            val resultPath = try {
                colorTransferUseCase.applyColorTransferWithGPUCached(
                    targetImagePath,
                    referenceImagePath,
                    intensity,
                    maxSize = 400
                )
            } catch (e: Exception) {
                android.util.Log.w("ColorTransferViewModel", "GPU-cached failed, CPU fallback", e)
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
                // 임시 파일 정리
                File(resultPath).delete()

                if (bitmap != null) {
                    updatePerformanceInfo(processingTime, true)
                    updateProcessingStatus("처리 완료", 1f, ColorTransferStage.DONE)
                    return@withContext bitmap
                }
            }

            android.util.Log.e("ColorTransferViewModel", "Color transfer failed")
            _errorMessage.value = "색감 전송 처리에 실패했습니다"
            null
        } catch (e: Exception) {
            android.util.Log.e("ColorTransferViewModel", "Color transfer exception", e)
            _errorMessage.value = "색감 전송 처리 중 오류가 발생했습니다: ${e.message}"
            null
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * 원본 크기 색감 전송 처리.
     * UseCase에서 결과 파일을 로드하여 UI 표시용 Bitmap을 반환한다.
     */
    suspend fun processColorTransferFullSize(
        referenceImagePath: String,
        targetImagePath: String,
        intensity: Float
    ): Bitmap? = withContext(ioDispatcher) {
        try {
            _isLoading.value = true
            updateProcessingStatus("원본 크기로 색감 전송 처리 중...", 0.3f, ColorTransferStage.FULL_SIZE_PREPARING)

            updateProcessingStatus("GPU 가속 색감 전송 적용 중...", 0.7f, ColorTransferStage.FULL_SIZE_APPLYING)

            val startTime = System.currentTimeMillis()

            // 먼저 GPU 방식 시도 (원본 크기, maxSize 제한 없음)
            val resultPath = try {
                colorTransferUseCase.applyColorTransferWithGPU(
                    targetImagePath,
                    referenceImagePath,
                    intensity
                )
            } catch (e: Exception) {
                android.util.Log.w("ColorTransferViewModel", "GPU failed, CPU fallback", e)
                colorTransferUseCase.applyColorTransfer(
                    targetImagePath,
                    referenceImagePath,
                    intensity
                )
            }

            val processingTime = System.currentTimeMillis() - startTime

            if (resultPath != null) {
                val bitmap = BitmapFactory.decodeFile(resultPath)
                // 임시 파일 정리
                File(resultPath).delete()

                if (bitmap != null) {
                    updatePerformanceInfo(processingTime, true)
                    updateProcessingStatus("처리 완료", 1f, ColorTransferStage.DONE)
                    return@withContext bitmap
                }
            }

            android.util.Log.e("ColorTransferViewModel", "Full-size color transfer failed")
            _errorMessage.value = "원본 크기 색감 전송에 실패했습니다"
            null
        } catch (e: Exception) {
            android.util.Log.e("ColorTransferViewModel", "Full-size color transfer exception", e)
            _errorMessage.value = "원본 크기 색감 전송 중 오류가 발생했습니다: ${e.message}"
            null
        } finally {
            _isLoading.value = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        // 화면 단위 참조 통계 캐시만 정리한다.
        // GPU/EGL 자원은 앱 전역 싱글톤이므로 여기서 해제하지 않는다(앱 종료 시 CamCon.onTerminate에서 해제).
        colorTransferUseCase.clearReferenceCache()
    }
}
