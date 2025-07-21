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

    /**
     * 미리보기용 색감 전송 처리
     */
    suspend fun processColorTransfer(
        referenceImagePath: String,
        targetImagePath: String,
        intensity: Float
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("ColorTransferViewModel", "🚀 미리보기 색감 전송 시작")
            android.util.Log.d(
                "ColorTransferViewModel",
                "  참조: ${java.io.File(referenceImagePath).name}"
            )
            android.util.Log.d(
                "ColorTransferViewModel",
                "  대상: ${java.io.File(targetImagePath).name}"
            )
            android.util.Log.d("ColorTransferViewModel", "  강도: ${intensity * 100}%")

            updateProcessingStatus("색감 전송 처리 중...", 0.5f)

            // 미리보기용으로 크기를 줄여서 빠른 처리
            val targetBitmap = loadScaledBitmap(targetImagePath, 400)
            val referenceBitmap = loadScaledBitmap(referenceImagePath, 400)

            if (targetBitmap == null || referenceBitmap == null) {
                _errorMessage.value = "이미지 로드에 실패했습니다"
                android.util.Log.e("ColorTransferViewModel", "❌ 이미지 로드 실패")
                return@withContext null
            }

            android.util.Log.d(
                "ColorTransferViewModel",
                "📐 미리보기 이미지 크기: ${targetBitmap.width}x${targetBitmap.height}"
            )

            val startTime = System.currentTimeMillis()

            // GPU 가속을 시도하기 위해 캐시된 참조 통계 사용
            android.util.Log.d("ColorTransferViewModel", "🎮 GPU 가속 색감 전송 시도...")
            val result = try {
                // GPU 가속 메서드 사용
                colorTransferUseCase.applyColorTransferWithGPUCached(
                    targetBitmap,
                    referenceImagePath,
                    intensity
                )
            } catch (e: Exception) {
                android.util.Log.w("ColorTransferViewModel", "⚠️ GPU 가속 실패, CPU 폴백: ${e.message}")
                // GPU 실패 시 일반 메서드 사용
                colorTransferUseCase.applyColorTransfer(
                    targetBitmap,
                    referenceBitmap,
                    intensity
                )
            }

            val processingTime = System.currentTimeMillis() - startTime

            if (result != null) {
                android.util.Log.d("ColorTransferViewModel", "✅ 색감 전송 성공 (${processingTime}ms)")
                updatePerformanceInfo(processingTime, true)
                updateProcessingStatus("처리 완료", 1f)
            } else {
                android.util.Log.e("ColorTransferViewModel", "❌ 색감 전송 실패")
                _errorMessage.value = "색감 전송 처리에 실패했습니다"
            }

            // 메모리 해제
            targetBitmap.recycle()
            referenceBitmap.recycle()

            result
        } catch (e: Exception) {
            android.util.Log.e("ColorTransferViewModel", "❌ 색감 전송 처리 예외: ${e.message}")
            _errorMessage.value = "색감 전송 처리 중 오류가 발생했습니다: ${e.message}"
            null
        }
    }

    /**
     * 원본 크기 색감 전송 처리
     */
    suspend fun processColorTransferFullSize(
        referenceImagePath: String,
        targetImagePath: String,
        intensity: Float
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("ColorTransferViewModel", "🖼️ 원본 크기 색감 전송 시작")
            android.util.Log.d(
                "ColorTransferViewModel",
                "  참조: ${java.io.File(referenceImagePath).name}"
            )
            android.util.Log.d(
                "ColorTransferViewModel",
                "  대상: ${java.io.File(targetImagePath).name}"
            )
            android.util.Log.d("ColorTransferViewModel", "  강도: ${intensity * 100}%")

            updateProcessingStatus("원본 크기로 색감 전송 처리 중...", 0.3f)

            val targetBitmap = BitmapFactory.decodeFile(targetImagePath)
            val referenceBitmap = BitmapFactory.decodeFile(referenceImagePath)

            if (targetBitmap == null || referenceBitmap == null) {
                _errorMessage.value = "이미지 로드에 실패했습니다"
                android.util.Log.e("ColorTransferViewModel", "❌ 원본 이미지 로드 실패")
                return@withContext null
            }

            android.util.Log.d(
                "ColorTransferViewModel",
                "📐 원본 이미지 크기: ${targetBitmap.width}x${targetBitmap.height}"
            )

            updateProcessingStatus("GPU 가속 색감 전송 적용 중...", 0.7f)

            val startTime = System.currentTimeMillis()

            // GPU 가속 메서드를 우선 사용
            android.util.Log.d("ColorTransferViewModel", "🎮 GPU 가속 원본 크기 처리 시도...")
            val result = try {
                colorTransferUseCase.applyColorTransferWithGPU(
                    targetImagePath,
                    referenceImagePath,
                    intensity
                )
            } catch (e: Exception) {
                android.util.Log.w("ColorTransferViewModel", "⚠️ GPU 가속 실패, CPU 폴백: ${e.message}")
                // GPU 실패 시 CPU 폴백
                colorTransferUseCase.applyColorTransfer(
                    targetBitmap,
                    referenceBitmap,
                    intensity
                )
            }

            val processingTime = System.currentTimeMillis() - startTime

            if (result != null) {
                android.util.Log.d(
                    "ColorTransferViewModel",
                    "✅ 원본 크기 색감 전송 성공 (${processingTime}ms)"
                )
                updatePerformanceInfo(processingTime, true)
                updateProcessingStatus("처리 완료", 1f)
            } else {
                android.util.Log.e("ColorTransferViewModel", "❌ 원본 크기 색감 전송 실패")
                _errorMessage.value = "원본 크기 색감 전송에 실패했습니다"
            }

            // 메모리 해제
            targetBitmap.recycle()
            referenceBitmap.recycle()

            result
        } catch (e: Exception) {
            android.util.Log.e("ColorTransferViewModel", "❌ 원본 크기 색감 전송 예외: ${e.message}")
            _errorMessage.value = "원본 크기 색감 전송 중 오류가 발생했습니다: ${e.message}"
            null
        }
    }

    /**
     * 지정된 크기로 비트맵을 로드합니다
     */
    private fun loadScaledBitmap(imagePath: String, maxSize: Int): Bitmap? {
        return try {
            // 먼저 이미지 크기 정보만 가져오기
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(imagePath, options)

            // 스케일링 계산
            val scale = maxOf(options.outWidth, options.outHeight) / maxSize.toFloat()
            val sampleSize = if (scale > 1f) scale.toInt() else 1

            // 실제 비트맵 로드
            val loadOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val bitmap = BitmapFactory.decodeFile(imagePath, loadOptions)

            // 정확한 크기로 리사이즈
            if (bitmap != null && (bitmap.width > maxSize || bitmap.height > maxSize)) {
                val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
                val (newWidth, newHeight) = if (aspectRatio > 1) {
                    maxSize to (maxSize / aspectRatio).toInt()
                } else {
                    (maxSize * aspectRatio).toInt() to maxSize
                }

                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                if (scaledBitmap != bitmap) {
                    bitmap.recycle()
                }
                scaledBitmap
            } else {
                bitmap
            }
        } catch (e: Exception) {
            null
        }
    }
}