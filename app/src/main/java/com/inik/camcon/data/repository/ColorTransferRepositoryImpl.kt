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
import com.inik.camcon.domain.model.ColorTransferResult
import com.inik.camcon.domain.repository.ColorTransferRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ColorTransferRepositoryImpl @Inject constructor(
    private val colorTransferProcessor: ColorTransferProcessor
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
        try {
            val inputBitmap = if (maxSize > 0) {
                loadScaledBitmap(targetImagePath, maxSize)
            } else {
                loadBitmapFromPath(targetImagePath)
            } ?: return@withContext null

            // Get cached reference stats
            val referenceStats = colorTransferProcessor.getCachedReferenceStats(referenceImagePath)
                ?: run {
                    inputBitmap.recycle()
                    return@withContext null
                }

            // Apply GPU-cached color transfer
            val result = colorTransferProcessor.applyColorTransferWithGPUCached(
                inputBitmap,
                referenceStats,
                intensity
            )

            // GPU fallback to CPU if needed
            val transferred = result ?: colorTransferProcessor.applyColorTransferWithCachedStatsOptimized(
                inputBitmap,
                referenceStats,
                intensity
            )

            inputBitmap.recycle()

            // Save result to temp file
            saveBitmapToTempFile(transferred)
        } catch (e: Exception) {
            Log.e(TAG, "applyColorTransferWithGPUCached failed: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    override suspend fun applyColorTransfer(
        targetImagePath: String,
        referenceImagePath: String,
        intensity: Float,
        maxSize: Int
    ): String? = withContext(Dispatchers.Default) {
        try {
            val inputBitmap = if (maxSize > 0) {
                loadScaledBitmap(targetImagePath, maxSize)
            } else {
                loadBitmapFromPath(targetImagePath)
            } ?: return@withContext null

            val referenceBitmap = if (maxSize > 0) {
                loadScaledBitmap(referenceImagePath, maxSize)
            } else {
                loadBitmapFromPath(referenceImagePath)
            } ?: run {
                inputBitmap.recycle()
                return@withContext null
            }

            // Try optimized native first, fallback to Kotlin
            val transferred = try {
                colorTransferProcessor.applyColorTransferOptimized(
                    inputBitmap,
                    referenceBitmap,
                    intensity
                )
            } catch (e: Exception) {
                colorTransferProcessor.applyColorTransfer(inputBitmap, referenceBitmap, intensity)
            }

            inputBitmap.recycle()
            referenceBitmap.recycle()

            // Save result to temp file
            saveBitmapToTempFile(transferred)
        } catch (e: Exception) {
            Log.e(TAG, "applyColorTransfer failed: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    override suspend fun applyColorTransferWithGPU(
        inputImagePath: String,
        referenceImagePath: String,
        intensity: Float
    ): String? = withContext(Dispatchers.Default) {
        try {
            Log.d(TAG, "GPU color transfer: ${File(inputImagePath).name}")

            // Load images with EXIF orientation
            val inputBitmap = loadBitmapWithOrientation(inputImagePath) ?: return@withContext null
            val referenceBitmap = loadBitmapWithOrientation(referenceImagePath) ?: run {
                inputBitmap.recycle()
                return@withContext null
            }

            try {
                // Try GPU-accelerated color transfer
                val result = colorTransferProcessor.applyColorTransferWithGPU(
                    inputBitmap,
                    referenceBitmap,
                    intensity
                )

                if (result != null) {
                    Log.d(TAG, "GPU color transfer succeeded")
                    referenceBitmap.recycle()
                    inputBitmap.recycle()
                    return@withContext saveBitmapToTempFile(result)
                } else {
                    Log.w(TAG, "GPU color transfer failed - CPU fallback")
                }
            } catch (e: Exception) {
                Log.w(TAG, "GPU color transfer exception - CPU fallback: ${e.message}")
            }

            // CPU fallback
            Log.d(TAG, "CPU fallback processing")
            val result = colorTransferProcessor.applyColorTransferOptimized(
                inputBitmap,
                referenceBitmap,
                intensity
            )

            referenceBitmap.recycle()
            inputBitmap.recycle()

            Log.d(TAG, "CPU fallback complete")
            saveBitmapToTempFile(result)
        } catch (e: Exception) {
            Log.e(TAG, "Color transfer failed: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    override suspend fun applyColorTransferAndSave(
        inputImagePath: String,
        referenceImagePath: String,
        originalImagePath: String,
        outputPath: String,
        intensity: Float
    ): ColorTransferResult? = withContext(Dispatchers.IO) {
        try {
            // Get cached reference stats
            val referenceStats = colorTransferProcessor.getCachedReferenceStats(referenceImagePath)
                ?: return@withContext null

            val inputBitmap = loadBitmapFromPath(inputImagePath) ?: return@withContext null

            // Try optimized native first, fallback to Kotlin
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

            // Save result to file
            val outputFile = File(outputPath)
            outputFile.parentFile?.mkdirs()

            FileOutputStream(outputFile).use { outputStream ->
                transferredBitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
            }

            // Copy EXIF metadata from original image
            try {
                copyExifMetadata(originalImagePath, outputPath)
                Log.d(TAG, "EXIF metadata copied successfully")
            } catch (e: Exception) {
                Log.w(TAG, "EXIF metadata copy failed: ${e.message}")
            }

            val result = ColorTransferResult(
                outputPath = outputPath,
                width = transferredBitmap.width,
                height = transferredBitmap.height
            )

            inputBitmap.recycle()
            transferredBitmap.recycle()

            result
        } catch (e: Exception) {
            Log.e(TAG, "applyColorTransferAndSave failed: ${e.message}")
            e.printStackTrace()
            null
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

    // ---- Private helpers ----

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
            // Get image dimensions first
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(imagePath, options)

            // Calculate scaling
            val scale = maxOf(options.outWidth, options.outHeight) / maxSize.toFloat()
            val sampleSize = if (scale > 1f) scale.toInt() else 1

            // Load bitmap with sample size
            val loadOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val bitmap = BitmapFactory.decodeFile(imagePath, loadOptions) ?: return null

            // Resize to exact max size if needed
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
