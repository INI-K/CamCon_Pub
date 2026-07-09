package com.inik.camcon.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.inik.camcon.data.processor.ColorTransferProcessor
import com.inik.camcon.data.util.BitmapIoUtils
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

        // GPU(EGL pbuffer) 경로에서 허용하는 최대 긴 변 px.
        // 고화소 원본을 그대로 EGL pbuffer로 만들면 GL_MAX_TEXTURE_SIZE/메모리 초과로
        // 결과가 검게/깨져 나오거나 네이티브 크래시가 난다. 이 한도로 다운스케일해 방지한다.
        private const val MAX_GPU_DIMENSION = 4096

        // CPU(캐시 통계) 색감 전송 경로에서 허용하는 최대 긴 변 px.
        // 풀해상도 ARGB_8888 원본을 그대로 디코딩하면 고화소 카메라에서 100MB+ 비트맵이 되어
        // OOM 위험이 크다. GPU 한도와 동일한 4096px 로 다운스케일해 메모리를 제한한다.
        private const val MAX_CT_DIMENSION = 4096
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
                loadScaledBitmap(targetImagePath, MAX_CT_DIMENSION)
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
            BitmapIoUtils.saveBitmapToTempFile(transferred, "color_transfer_")
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
                loadScaledBitmap(targetImagePath, MAX_CT_DIMENSION)
            } ?: return@withContext null)
            inputBitmapToRecycle = inputBitmap

            val referenceBitmap = (if (maxSize > 0) {
                loadScaledBitmap(referenceImagePath, maxSize)
            } else {
                loadScaledBitmap(referenceImagePath, MAX_CT_DIMENSION)
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
            BitmapIoUtils.saveBitmapToTempFile(transferred, "color_transfer_")
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

            // 고화소 원본을 그대로 EGL pbuffer로 만들면 텍스처 한도/메모리 초과로 손상·크래시가 난다.
            // GPU 경로에 한해 한도 내로 다운스케일한 사본을 사용하고, 사본은 GPU 시도 후 즉시 회수한다.
            // (원본 inputBitmap/referenceBitmap은 CPU 폴백에서 재사용하므로 건드리지 않는다.)
            var gpuInputScaled: Bitmap? = null
            var gpuReferenceScaled: Bitmap? = null
            try {
                gpuInputScaled = scaleDownForGpu(inputBitmap)
                gpuReferenceScaled = scaleDownForGpu(referenceBitmap)

                // GPU 가속 색감 전송 시도
                val result = colorTransferProcessor.applyColorTransferWithGPU(
                    gpuInputScaled,
                    gpuReferenceScaled,
                    intensity
                )

                if (result != null) {
                    Log.d(TAG, "GPU color transfer succeeded")
                    return@withContext BitmapIoUtils.saveBitmapToTempFile(result, "color_transfer_")
                } else {
                    Log.w(TAG, "GPU color transfer failed - CPU fallback")
                }
            } catch (e: Exception) {
                Log.w(TAG, "GPU color transfer exception - CPU fallback: ${e.message}")
            } finally {
                // 다운스케일 사본만 회수 (원본과 동일 인스턴스면 회수하지 않음)
                gpuInputScaled?.let { if (it != inputBitmap) it.recycle() }
                gpuReferenceScaled?.let { if (it != referenceBitmap) it.recycle() }
            }

            // CPU 폴백
            Log.d(TAG, "CPU fallback processing")
            val result = colorTransferProcessor.applyColorTransferOptimized(
                inputBitmap,
                referenceBitmap,
                intensity
            )

            Log.d(TAG, "CPU fallback complete")
            BitmapIoUtils.saveBitmapToTempFile(result, "color_transfer_")
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

            // 풀해상도 원본을 그대로 디코딩하면 고화소에서 100MB+ ARGB_8888 비트맵이 되어 OOM.
            // 4096px 한도로 다운스케일 디코딩해 메모리(~16MB)를 제한한다.
            val inputBitmap =
                loadScaledBitmap(inputImagePath, MAX_CT_DIMENSION) ?: return@withContext null
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
                BitmapIoUtils.copyExifMetadata(originalImagePath, outputPath, "CamCon - Color Transfer Applied (Optimized)")
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

    private fun loadBitmapWithOrientation(imagePath: String): Bitmap? {
        // 풀해상도 ARGB_8888 디코딩은 고화소(45MP=180MB)에서 OOM 위험.
        // applyColorTransferAndSave와 동일하게 4096px 한도로 다운스케일 디코딩한다.
        val bitmap = loadScaledBitmap(imagePath, MAX_CT_DIMENSION) ?: return null

        val exif = ExifInterface(imagePath)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )

        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> BitmapIoUtils.rotateBitmap(bitmap, 90)
            ExifInterface.ORIENTATION_ROTATE_180 -> BitmapIoUtils.rotateBitmap(bitmap, 180)
            ExifInterface.ORIENTATION_ROTATE_270 -> BitmapIoUtils.rotateBitmap(bitmap, 270)
            else -> bitmap
        }
    }


    /**
     * GPU(EGL pbuffer) 경로 입력을 텍스처/메모리 한도 내로 다운스케일한다.
     * 한도 이내면 원본 인스턴스를 그대로 반환하므로 호출부는 == 비교로 사본 여부를 구분한다.
     */
    private fun scaleDownForGpu(bitmap: Bitmap): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= MAX_GPU_DIMENSION) {
            return bitmap
        }
        val scale = MAX_GPU_DIMENSION.toFloat() / longest
        val newWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val newHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun loadScaledBitmap(imagePath: String, maxSize: Int): Bitmap? {
        return try {
            // 먼저 이미지 크기 가져오기
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(imagePath, options)

            // 스케일 계산 — 디코더는 inSampleSize를 2의 거듭제곱으로 내림하므로 명시적으로 계산
            val longest = maxOf(options.outWidth, options.outHeight)
            var sampleSize = 1
            while (longest / (sampleSize * 2) >= maxSize) {
                sampleSize *= 2
            }

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


}
