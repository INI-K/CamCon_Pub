package com.inik.camcon.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.Matrix
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.inik.camcon.data.processor.ColorTransferProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 색감 전송 기능을 위한 UseCase
 */
@Singleton
class ColorTransferUseCase @Inject constructor(
    private val colorTransferProcessor: ColorTransferProcessor
) {

    /**
     * 캐시된 참조 이미지 통계를 사용하여 색감 전송을 수행하고 EXIF 메타데이터를 보존합니다.
     * @param inputBitmap 색감을 적용할 입력 이미지
     * @param referenceImagePath 참조할 이미지의 파일 경로
     * @param originalImagePath 원본 이미지 파일 경로 (EXIF 메타데이터 복사용)
     * @param outputPath 결과 이미지 저장 경로
     * @param intensity 색감 전송 강도 (0.0 ~ 1.0, 기본값 0.03)
     * @return 색감이 적용된 결과 이미지, 실패 시 null
     */
    suspend fun applyColorTransferAndSave(
        inputBitmap: Bitmap,
        referenceImagePath: String,
        originalImagePath: String,
        outputPath: String,
        intensity: Float = 0.03f
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // 참조 이미지 통계를 캐시에서 가져오거나 계산
            val referenceStats = colorTransferProcessor.getCachedReferenceStats(referenceImagePath)
                ?: return@withContext null

            // 최적화된 네이티브 함수를 우선 사용하여 색감 전송 적용
            val transferredBitmap = try {
                colorTransferProcessor.applyColorTransferWithCachedStatsOptimized(
                    inputBitmap,
                    referenceStats,
                    intensity
                )
            } catch (e: Exception) {
                // 네이티브 함수 실패 시 코틀린 폴백
                colorTransferProcessor.applyColorTransferWithCachedStats(
                    inputBitmap,
                    referenceStats,
                    intensity
                )
            }

            // 결과 이미지를 파일로 저장
            val outputFile = File(outputPath)
            outputFile.parentFile?.mkdirs()

            java.io.FileOutputStream(outputFile).use { outputStream ->
                transferredBitmap.compress(
                    android.graphics.Bitmap.CompressFormat.JPEG,
                    95,
                    outputStream
                )
            }

            // 원본 이미지의 EXIF 메타데이터를 결과 이미지로 복사
            try {
                val originalExif = androidx.exifinterface.media.ExifInterface(originalImagePath)
                val resultExif = androidx.exifinterface.media.ExifInterface(outputPath)

                // 중요한 EXIF 태그들을 복사
                val tagsToPreserve = arrayOf(
                    androidx.exifinterface.media.ExifInterface.TAG_DATETIME,
                    androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL,
                    androidx.exifinterface.media.ExifInterface.TAG_DATETIME_DIGITIZED,
                    androidx.exifinterface.media.ExifInterface.TAG_MAKE,
                    androidx.exifinterface.media.ExifInterface.TAG_MODEL,
                    androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                    androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE,
                    androidx.exifinterface.media.ExifInterface.TAG_GPS_LONGITUDE,
                    androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE_REF,
                    androidx.exifinterface.media.ExifInterface.TAG_GPS_LONGITUDE_REF,
                    androidx.exifinterface.media.ExifInterface.TAG_EXPOSURE_TIME,
                    androidx.exifinterface.media.ExifInterface.TAG_F_NUMBER,
                    androidx.exifinterface.media.ExifInterface.TAG_ISO_SPEED_RATINGS,
                    androidx.exifinterface.media.ExifInterface.TAG_FOCAL_LENGTH,
                    androidx.exifinterface.media.ExifInterface.TAG_APERTURE_VALUE,
                    androidx.exifinterface.media.ExifInterface.TAG_SHUTTER_SPEED_VALUE,
                    androidx.exifinterface.media.ExifInterface.TAG_WHITE_BALANCE,
                    androidx.exifinterface.media.ExifInterface.TAG_FLASH
                )

                var copiedTags = 0
                for (tag in tagsToPreserve) {
                    originalExif.getAttribute(tag)?.let { value ->
                        resultExif.setAttribute(tag, value)
                        copiedTags++
                    }
                }

                // 처리된 이미지임을 표시
                resultExif.setAttribute(
                    androidx.exifinterface.media.ExifInterface.TAG_SOFTWARE,
                    "CamCon - Color Transfer Applied (Optimized)"
                )

                // EXIF 데이터 저장
                resultExif.saveAttributes()
                android.util.Log.d("ColorTransferUseCase", "✅ EXIF 메타데이터 복사 완료: $copiedTags 개 태그")
            } catch (e: Exception) {
                android.util.Log.w("ColorTransferUseCase", "⚠️ EXIF 메타데이터 복사 실패: ${e.message}")
            }

            transferredBitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 파일 경로의 참조 이미지를 사용하여 색감 전송을 수행하고 EXIF 메타데이터를 보존합니다.
     * @param inputBitmap 색감을 적용할 입력 이미지
     * @param referenceBitmap 참조할 색감의 이미지
     * @param originalImagePath 원본 이미지 파일 경로 (EXIF 메타데이터 복사용)
     * @param outputPath 결과 이미지 저장 경로
     * @param intensity 색감 전송 강도 (0.0 ~ 1.0, 기본값 0.03)
     * @return 색감이 적용된 결과 이미지, 실패 시 null
     */
    suspend fun applyColorTransferAndSave(
        inputBitmap: Bitmap,
        referenceBitmap: Bitmap,
        originalImagePath: String,
        outputPath: String,
        intensity: Float = 0.03f
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            colorTransferProcessor.applyColorTransferAndSave(
                inputBitmap,
                referenceBitmap,
                originalImagePath,
                outputPath,
                intensity
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 파일 경로의 참조 이미지를 사용하여 색감 전송을 수행합니다.
     * @param inputBitmap 색감을 적용할 입력 이미지
     * @param referenceImagePath 참조할 이미지의 파일 경로
     * @param intensity 색감 전송 강도 (0.0 ~ 1.0, 기본값 0.03)
     * @return 색감이 적용된 결과 이미지, 실패 시 null
     */
    suspend fun applyColorTransfer(
        inputBitmap: Bitmap,
        referenceImagePath: String,
        intensity: Float = 0.03f
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // 캐시된 참조 이미지 통계 사용
            val referenceStats = colorTransferProcessor.getCachedReferenceStats(referenceImagePath)
                ?: return@withContext null

            // 최적화된 네이티브 함수를 우선 사용
            try {
                colorTransferProcessor.applyColorTransferWithCachedStatsOptimized(
                    inputBitmap,
                    referenceStats,
                    intensity
                )
            } catch (e: Exception) {
                // 네이티브 함수 실패 시 코틀린 폴백
                colorTransferProcessor.applyColorTransferWithCachedStats(
                    inputBitmap,
                    referenceStats,
                    intensity
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 비트맵을 직접 사용하여 색감 전송을 수행합니다.
     * @param inputBitmap 색감을 적용할 입력 이미지
     * @param referenceBitmap 참조할 색감의 이미지
     * @param intensity 색감 전송 강도 (0.0 ~ 1.0, 기본값 0.03)
     * @return 색감이 적용된 결과 이미지, 실패 시 null
     */
    suspend fun applyColorTransfer(
        inputBitmap: Bitmap,
        referenceBitmap: Bitmap,
        intensity: Float = 0.03f
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // 최적화된 네이티브 함수를 우선 사용
            try {
                colorTransferProcessor.applyColorTransferOptimized(
                    inputBitmap,
                    referenceBitmap,
                    intensity
                )
            } catch (e: Exception) {
                // 네이티브 함수 실패 시 코틀린 폴백
                colorTransferProcessor.applyColorTransfer(inputBitmap, referenceBitmap, intensity)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 참조 이미지가 변경되었을 때 캐시를 초기화합니다.
     */
    fun clearReferenceCache() {
        colorTransferProcessor.clearCache()
    }

    /**
     * 이미지 파일이 유효한지 확인합니다.
     * @param imagePath 확인할 이미지 파일 경로
     * @return 유효한 이미지 파일인 경우 true
     */
    fun isValidImageFile(imagePath: String): Boolean {
        return try {
            val file = File(imagePath)
            if (!file.exists()) return false

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(imagePath, options)

            // 이미지가 정상적으로 디코딩되었는지 확인
            options.outWidth > 0 && options.outHeight > 0
        } catch (e: Exception) {
            false
        }
    }

    private fun loadImageFromPath(imagePath: String): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB) // sRGB 색공간 설정
            }
        }
        return BitmapFactory.decodeFile(imagePath, options)
    }

    /**
     * GPUImage를 초기화합니다.
     * @param context Android Context
     */
    fun initializeGPU(context: Context) {
        colorTransferProcessor.initializeGPUImage(context)
    }

    /**
     * GPU 가속을 사용하여 색감 전송을 수행합니다.
     * @param inputImagePath 색감을 적용할 입력 이미지 경로
     * @param referenceImagePath 참조할 색감의 이미지 경로
     * @param intensity 색감 전송 강도 (0.0 ~ 1.0, 기본값 0.03)
     * @return 색감이 적용된 결과 이미지, 실패 시 null
     */
    suspend fun applyColorTransferWithGPU(
        inputImagePath: String,
        referenceImagePath: String,
        intensity: Float = 0.03f
    ): Bitmap? = withContext(Dispatchers.Default) {
        try {
            android.util.Log.d(
                "ColorTransferUseCase",
                "🎮 GPU 색감 전송 시도: ${File(inputImagePath).name}"
            )

            // 입력 이미지 로드
            val inputBitmap = loadBitmapWithOrientation(inputImagePath) ?: return@withContext null
            
            // 참조 이미지 로드
            val referenceBitmap = loadBitmapWithOrientation(referenceImagePath) ?: return@withContext null
            
            try {
                // GPU 가속 색감 전송 적용
                val result = colorTransferProcessor.applyColorTransferWithGPU(
                    inputBitmap,
                    referenceBitmap,
                    intensity
                )

                if (result != null) {
                    android.util.Log.d("ColorTransferUseCase", "✅ GPU 색감 전송 성공")
                    // 메모리 해제
                    referenceBitmap.recycle()
                    inputBitmap.recycle()
                    return@withContext result
                } else {
                    android.util.Log.w("ColorTransferUseCase", "⚠️ GPU 색감 전송 실패 - CPU 폴백")
                }
                
            } catch (e: Exception) {
                android.util.Log.w("ColorTransferUseCase", "❌ GPU 색감 전송 예외 - CPU 폴백: ${e.message}")
            }

            // GPU 실패 시 CPU 폴백
            android.util.Log.d("ColorTransferUseCase", "🔄 CPU 폴백 처리 시작")
            val result = colorTransferProcessor.applyColorTransferOptimized(
                inputBitmap,
                referenceBitmap,
                intensity
            )

            referenceBitmap.recycle()
            inputBitmap.recycle()

            android.util.Log.d("ColorTransferUseCase", "✅ CPU 폴백 처리 완료")
            result

        } catch (e: Exception) {
            android.util.Log.e("ColorTransferUseCase", "❌ 색감 전송 전체 실패: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * GPU 가속을 사용하여 색감 전송을 수행하고 결과를 저장합니다.
     * @param inputImagePath 색감을 적용할 입력 이미지 경로
     * @param referenceImagePath 참조할 색감의 이미지 경로
     * @param outputPath 결과 이미지 저장 경로
     * @param intensity 색감 전송 강도 (0.0 ~ 1.0, 기본값 0.03)
     * @return 색감이 적용된 결과 이미지, 실패 시 null
     */
    suspend fun applyColorTransferWithGPUAndSave(
        inputImagePath: String,
        referenceImagePath: String,
        outputPath: String,
        intensity: Float = 0.03f
    ): Bitmap? = withContext(Dispatchers.Default) {
        try {
            android.util.Log.d("ColorTransferUseCase", "🎮 GPU 색감 전송 저장 시작")
            android.util.Log.d("ColorTransferUseCase", "  입력: ${File(inputImagePath).name}")
            android.util.Log.d("ColorTransferUseCase", "  참조: ${File(referenceImagePath).name}")
            android.util.Log.d("ColorTransferUseCase", "  출력: ${File(outputPath).name}")
            
            // GPU 가속 색감 전송 적용
            val transferredBitmap =
                applyColorTransferWithGPU(inputImagePath, referenceImagePath, intensity)
            
            if (transferredBitmap != null) {
                android.util.Log.d("ColorTransferUseCase", "✅ GPU 색감 전송 성공 - 파일 저장 중")
                
                // 결과 이미지를 파일로 저장
                val outputFile = File(outputPath)
                FileOutputStream(outputFile).use { outputStream ->
                    transferredBitmap.compress(
                        Bitmap.CompressFormat.JPEG,
                        95,
                        outputStream
                    )
                }
                
                android.util.Log.d("ColorTransferUseCase", "✅ GPU 색감 전송 파일 저장 완료")
                
                // EXIF 메타데이터 복사
                try {
                    copyExifMetadata(inputImagePath, outputPath)
                    android.util.Log.d("ColorTransferUseCase", "✅ EXIF 메타데이터 복사 완료")
                } catch (e: Exception) {
                    android.util.Log.w("ColorTransferUseCase", "⚠️ EXIF 메타데이터 복사 실패: ${e.message}")
                }
                
                return@withContext transferredBitmap
            } else {
                android.util.Log.w("ColorTransferUseCase", "⚠️ GPU 색감 전송 실패 - CPU 폴백 시도")
                
                // CPU 폴백
                val inputBitmap = loadBitmapWithOrientation(inputImagePath)
                val referenceBitmap = loadBitmapWithOrientation(referenceImagePath)
                
                if (inputBitmap != null && referenceBitmap != null) {
                    val result = colorTransferProcessor.applyColorTransferOptimized(
                        inputBitmap,
                        referenceBitmap,
                        intensity
                    )
                    
                    // 결과 저장
                    val outputFile = File(outputPath)
                    FileOutputStream(outputFile).use { outputStream ->
                        result.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                    }
                    
                    // EXIF 복사
                    try {
                        copyExifMetadata(inputImagePath, outputPath)
                    } catch (e: Exception) {
                        android.util.Log.w("ColorTransferUseCase", "⚠️ EXIF 메타데이터 복사 실패: ${e.message}")
                    }
                    
                    inputBitmap.recycle()
                    referenceBitmap.recycle()
                    
                    android.util.Log.d("ColorTransferUseCase", "✅ CPU 폴백 완료")
                    return@withContext result
                } else {
                    android.util.Log.e("ColorTransferUseCase", "❌ 이미지 로드 실패")
                    return@withContext null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ColorTransferUseCase", "❌ GPU 색감 전송 저장 실패: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * 캐시된 참조 통계와 GPU 가속을 사용하여 색감 전송을 수행합니다.
     * @param inputBitmap 색감을 적용할 입력 이미지
     * @param referenceImagePath 참조 이미지 경로
     * @param intensity 색감 전송 강도 (0.0 ~ 1.0, 기본값 0.03)
     * @return 색감이 적용된 결과 이미지, 실패 시 null
     */
    suspend fun applyColorTransferWithGPUCached(
        inputBitmap: Bitmap,
        referenceImagePath: String,
        intensity: Float = 0.03f
    ): Bitmap? = withContext(Dispatchers.Default) {
        try {
            // 캐시된 참조 통계 가져오기
            val referenceStats = colorTransferProcessor.getCachedReferenceStats(referenceImagePath)
                ?: return@withContext null
            
            // GPU 가속 색감 전송 적용
            val result = colorTransferProcessor.applyColorTransferWithGPUCached(
                inputBitmap,
                referenceStats,
                intensity
            )
            
            // GPU 실패 시 CPU 폴백
            return@withContext result ?: colorTransferProcessor.applyColorTransferWithCachedStatsOptimized(
                inputBitmap,
                referenceStats,
                intensity
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun loadBitmapWithOrientation(imagePath: String): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(imagePath, options)

        val exif = ExifInterface(imagePath)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

        val bitmap = BitmapFactory.decodeFile(imagePath, BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        })

        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270)
            else -> bitmap
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun copyExifMetadata(inputImagePath: String, outputPath: String) {
        val inputExif = ExifInterface(inputImagePath)
        val outputExif = ExifInterface(outputPath)

        val tagsToPreserve = arrayOf(
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_ISO_SPEED_RATINGS,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_APERTURE_VALUE,
            ExifInterface.TAG_SHUTTER_SPEED_VALUE,
            ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_FLASH
        )

        var copiedTags = 0
        for (tag in tagsToPreserve) {
            inputExif.getAttribute(tag)?.let { value ->
                outputExif.setAttribute(tag, value)
                copiedTags++
            }
        }

        outputExif.setAttribute(
            ExifInterface.TAG_SOFTWARE,
            "CamCon - Color Transfer Applied (Optimized)"
        )

        outputExif.saveAttributes()
    }
}