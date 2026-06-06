package com.inik.camcon.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.Matrix
import android.os.Build
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.inik.camcon.data.processor.ColorTransferProcessor
import com.inik.camcon.di.IoDispatcher
import com.inik.camcon.domain.model.ColorTransferResult
import com.inik.camcon.domain.repository.ColorTransferRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ColorTransferRepositoryImpl @Inject constructor(
    private val colorTransferProcessor: ColorTransferProcessor,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ColorTransferRepository {

    companion object {
        private const val TAG = "ColorTransferRepo"
    }

    override suspend fun applyColorTransferWithGPUCached(
        targetImagePath: String,
        referenceImagePath: String,
        intensity: Float,
        maxSize: Int
    ): String? = withContext(Dispatchers.Default) {
        // finally에서 항상 recycle 하기 위한 추적 변수
        var inputBitmapToRecycle: Bitmap? = null
        try {
            val inputBitmap = (if (maxSize > 0) {
                loadScaledBitmap(targetImagePath, maxSize)
            } else {
                loadBitmapFromPath(targetImagePath)
            } ?: return@withContext null)
            inputBitmapToRecycle = inputBitmap

            // 캐시된 참조 통계 가져오기
            val referenceStats = colorTransferProcessor.getCachedReferenceStats(referenceImagePath)
                ?: return@withContext null

            // GPU 캐시 색감 전송 적용
            val result = colorTransferProcessor.applyColorTransferWithGPUCached(
                inputBitmap,
                referenceStats,
                intensity
            )

            // 필요 시 GPU에서 CPU로 폴백
            val transferred = result ?: colorTransferProcessor.applyColorTransferWithCachedStatsOptimized(
                inputBitmap,
                referenceStats,
                intensity
            )

            // 결과를 임시 파일로 저장
            saveBitmapToTempFile(transferred)
        } catch (e: Exception) {
            Log.e(TAG, "applyColorTransferWithGPUCached failed", e)
            null
        } finally {
            // 예외/조기 반환/성공 모든 경로에서 입력 비트맵 누수 방지
            inputBitmapToRecycle?.recycle()
        }
    }

    override suspend fun applyColorTransfer(
        targetImagePath: String,
        referenceImagePath: String,
        intensity: Float,
        maxSize: Int
    ): String? = withContext(Dispatchers.Default) {
        var inputBitmapToRecycle: Bitmap? = null
        var referenceBitmapToRecycle: Bitmap? = null
        try {
            val inputBitmap = (if (maxSize > 0) {
                loadScaledBitmap(targetImagePath, maxSize)
            } else {
                loadBitmapFromPath(targetImagePath)
            } ?: return@withContext null)
            inputBitmapToRecycle = inputBitmap

            val referenceBitmap = (if (maxSize > 0) {
                loadScaledBitmap(referenceImagePath, maxSize)
            } else {
                loadBitmapFromPath(referenceImagePath)
            } ?: return@withContext null)
            referenceBitmapToRecycle = referenceBitmap

            // 최적화된 네이티브 먼저 시도, 실패 시 Kotlin으로 폴백
            val transferred = try {
                colorTransferProcessor.applyColorTransferOptimized(
                    inputBitmap,
                    referenceBitmap,
                    intensity
                )
            } catch (e: Exception) {
                colorTransferProcessor.applyColorTransfer(inputBitmap, referenceBitmap, intensity)
            }

            // 결과를 임시 파일로 저장
            saveBitmapToTempFile(transferred)
        } catch (e: Exception) {
            Log.e(TAG, "applyColorTransfer failed", e)
            null
        } finally {
            // 예외/조기 반환/성공 모든 경로에서 입력·참조 비트맵 누수 방지
            inputBitmapToRecycle?.recycle()
            referenceBitmapToRecycle?.recycle()
        }
    }

    override suspend fun applyColorTransferWithGPU(
        inputImagePath: String,
        referenceImagePath: String,
        intensity: Float
    ): String? = withContext(Dispatchers.Default) {
        var inputBitmapToRecycle: Bitmap? = null
        var referenceBitmapToRecycle: Bitmap? = null
        try {
            Log.d(TAG, "GPU color transfer: ${File(inputImagePath).name}")

            // EXIF 방향 정보를 반영하여 이미지 로드
            val inputBitmap = loadBitmapWithOrientation(inputImagePath) ?: return@withContext null
            inputBitmapToRecycle = inputBitmap
            val referenceBitmap =
                loadBitmapWithOrientation(referenceImagePath) ?: return@withContext null
            referenceBitmapToRecycle = referenceBitmap

            try {
                // GPU 가속 색감 전송 시도
                val result = colorTransferProcessor.applyColorTransferWithGPU(
                    inputBitmap,
                    referenceBitmap,
                    intensity
                )

                if (result != null) {
                    Log.d(TAG, "GPU color transfer succeeded")
                    return@withContext saveBitmapToTempFile(result)
                } else {
                    Log.w(TAG, "GPU color transfer failed - CPU fallback")
                }
            } catch (e: Exception) {
                Log.w(TAG, "GPU color transfer exception - CPU fallback: ${e.message}")
            }

            // CPU 폴백
            Log.d(TAG, "CPU fallback processing")
            val result = colorTransferProcessor.applyColorTransferOptimized(
                inputBitmap,
                referenceBitmap,
                intensity
            )

            Log.d(TAG, "CPU fallback complete")
            saveBitmapToTempFile(result)
        } catch (e: Exception) {
            Log.e(TAG, "Color transfer failed", e)
            null
        } finally {
            // 성공·실패·조기 반환 모든 경로에서 입력·참조 비트맵 누수 방지
            inputBitmapToRecycle?.recycle()
            referenceBitmapToRecycle?.recycle()
        }
    }

    override suspend fun applyColorTransferAndSave(
        inputImagePath: String,
        referenceImagePath: String,
        originalImagePath: String,
        outputPath: String,
        intensity: Float
    ): ColorTransferResult? = withContext(ioDispatcher) {
        var inputBitmapToRecycle: Bitmap? = null
        var transferredBitmapToRecycle: Bitmap? = null
        try {
            // 캐시된 참조 통계 가져오기
            val referenceStats = colorTransferProcessor.getCachedReferenceStats(referenceImagePath)
                ?: return@withContext null

            val inputBitmap = loadBitmapFromPath(inputImagePath) ?: return@withContext null
            inputBitmapToRecycle = inputBitmap

            // 최적화된 네이티브 먼저 시도, 실패 시 Kotlin으로 폴백
            val transferredBitmap = try {
                colorTransferProcessor.applyColorTransferWithCachedStatsOptimized(
                    inputBitmap,
                    referenceStats,
                    intensity
                )
            } catch (e: Exception) {
                colorTransferProcessor.applyColorTransferWithCachedStats(
                    inputBitmap,
                    referenceStats,
                    intensity
                )
            }
            transferredBitmapToRecycle = transferredBitmap

            // 결과를 파일로 저장
            val outputFile = File(outputPath)
            outputFile.parentFile?.mkdirs()

            FileOutputStream(outputFile).use { outputStream ->
                transferredBitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
            }

            // 원본 이미지의 EXIF 메타데이터 복사
            try {
                copyExifMetadata(originalImagePath, outputPath)
                Log.d(TAG, "EXIF metadata copied successfully")
            } catch (e: Exception) {
                Log.w(TAG, "EXIF metadata copy failed: ${e.message}")
            }

            ColorTransferResult(
                outputPath = outputPath,
                width = transferredBitmap.width,
                height = transferredBitmap.height
            )
        } catch (e: Exception) {
            Log.e(TAG, "applyColorTransferAndSave failed", e)
            null
        } finally {
            // FileOutputStream/compress/EXIF 단계 예외 시에도, 성공 시에도 비트맵 누수 방지
            inputBitmapToRecycle?.recycle()
            transferredBitmapToRecycle?.recycle()
        }
    }

    override fun isValidImageFile(imagePath: String): Boolean {
        return try {
            val file = File(imagePath)
            if (!file.exists()) return false

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(imagePath, options)

            options.outWidth > 0 && options.outHeight > 0
        } catch (e: Exception) {
            false
        }
    }

    override fun clearReferenceCache() {
        colorTransferProcessor.clearCache()
    }

    override fun initializeGPU(contextProvider: Any) {
        val context = contextProvider as Context
        colorTransferProcessor.initializeGPUImage(context)
    }

    override fun releaseGpu() {
        colorTransferProcessor.cleanup()
    }

    // ---- 내부 헬퍼 ----

    private fun loadBitmapFromPath(imagePath: String): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
            }
        }
        return BitmapFactory.decodeFile(imagePath, options)
    }

    private fun loadBitmapWithOrientation(imagePath: String): Bitmap? {
        val bitmap = BitmapFactory.decodeFile(imagePath, BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: return null

        val exif = ExifInterface(imagePath)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )

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

    private fun loadScaledBitmap(imagePath: String, maxSize: Int): Bitmap? {
        return try {
            // 먼저 이미지 크기 가져오기
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(imagePath, options)

            // 스케일 계산
            val scale = maxOf(options.outWidth, options.outHeight) / maxSize.toFloat()
            val sampleSize = if (scale > 1f) scale.toInt() else 1

            // 샘플 크기를 적용하여 비트맵 로드
            val loadOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val bitmap = BitmapFactory.decodeFile(imagePath, loadOptions) ?: return null

            // 필요 시 정확한 최대 크기로 리사이즈
            if (bitmap.width > maxSize || bitmap.height > maxSize) {
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
        Log.d(TAG, "EXIF metadata copied: $copiedTags tags")
    }

    private fun saveBitmapToTempFile(bitmap: Bitmap): String? {
        return try {
            val tempFile = File.createTempFile("color_transfer_", ".jpg")
            FileOutputStream(tempFile).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
            }
            bitmap.recycle()
            tempFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save bitmap to temp file: ${e.message}")
            bitmap.recycle()
            null
        }
    }
}
