package com.inik.camcon.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.inik.camcon.data.processor.FilmAdjustmentProcessor
import com.inik.camcon.data.processor.FilmLutProcessor
import com.inik.camcon.data.processor.FilmThumbnailGenerator
import com.inik.camcon.data.util.BitmapIoUtils
import com.inik.camcon.di.IoDispatcher
import com.inik.camcon.domain.model.FilmEdit
import com.inik.camcon.domain.model.FilmLut
import com.inik.camcon.domain.model.FilmLutResult
import com.inik.camcon.domain.repository.FilmLutRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 필름 시뮬레이션 Repository 구현체.
 *
 * 카탈로그/룩업 로딩은 [FilmLutCatalogLoader] 에, 실제 픽셀 적용은 [FilmLutProcessor] 에 위임하고,
 * 본 클래스는 색감전송([ColorTransferRepositoryImpl])과 동일한 디코딩·다운스케일·저장·EXIF 정책을
 * 담당한다. GPU 적용을 먼저 시도하고 실패 시 CPU(Lut3D 삼선형) 폴백한다.
 */
@Singleton
class FilmLutRepositoryImpl @Inject constructor(
    private val catalogLoader: FilmLutCatalogLoader,
    private val filmLutProcessor: FilmLutProcessor,
    private val filmAdjustmentProcessor: FilmAdjustmentProcessor,
    private val filmThumbnailGenerator: FilmThumbnailGenerator,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : FilmLutRepository {

    companion object {
        private const val TAG = "FilmLutRepo"

        // GPU(EGL pbuffer)·CPU 경로 모두 허용하는 최대 긴 변 px. 색감전송과 동일 한도.
        private const val MAX_DIMENSION = 4096
    }

    override suspend fun getAvailableLuts(): List<FilmLut> = catalogLoader.getCatalog()

    override suspend fun applyFilmLutAndSave(
        inputImagePath: String,
        lutId: String,
        originalImagePath: String,
        outputPath: String,
        intensity: Float
    ): FilmLutResult? = withContext(ioDispatcher) {
        var inputBitmapToRecycle: Bitmap? = null
        var resultBitmapToRecycle: Bitmap? = null
        try {
            val lut = catalogLoader.getLut(lutId) ?: run {
                Log.w(TAG, "LUT 미발견: $lutId")
                return@withContext null
            }

            val inputBitmap =
                loadScaledBitmap(inputImagePath, MAX_DIMENSION) ?: return@withContext null
            inputBitmapToRecycle = inputBitmap

            val resultBitmap = applyToBitmap(inputBitmap, lut.assetPath, intensity)
                ?: return@withContext null
            resultBitmapToRecycle = resultBitmap

            val outputFile = File(outputPath)
            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { out ->
                resultBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }

            runCatching { BitmapIoUtils.copyExifMetadata(originalImagePath, outputPath, "CamCon - Film Simulation Applied") }
                .onFailure { Log.w(TAG, "EXIF 복사 실패: ${it.message}") }

            FilmLutResult(
                outputPath = outputPath,
                width = resultBitmap.width,
                height = resultBitmap.height,
                lutId = lutId
            )
        } catch (e: Exception) {
            Log.e(TAG, "applyFilmLutAndSave 실패", e)
            null
        } finally {
            inputBitmapToRecycle?.recycle()
            resultBitmapToRecycle?.recycle()
        }
    }

    override suspend fun applyFilmLut(
        inputImagePath: String,
        lutId: String,
        intensity: Float,
        maxSize: Int
    ): String? = withContext(ioDispatcher) {
        var inputBitmapToRecycle: Bitmap? = null
        try {
            val lut = catalogLoader.getLut(lutId) ?: return@withContext null
            val inputBitmap = (if (maxSize > 0) {
                loadScaledBitmap(inputImagePath, maxSize)
            } else {
                loadScaledBitmap(inputImagePath, MAX_DIMENSION)
            } ?: return@withContext null)
            inputBitmapToRecycle = inputBitmap

            val resultBitmap = applyToBitmap(inputBitmap, lut.assetPath, intensity)
                ?: return@withContext null
            BitmapIoUtils.saveBitmapToTempFile(resultBitmap, "film_lut_")
        } catch (e: Exception) {
            Log.e(TAG, "applyFilmLut 실패", e)
            null
        } finally {
            inputBitmapToRecycle?.recycle()
        }
    }

    override suspend fun loadLookupBitmap(lutId: String): Any? {
        val lut = catalogLoader.getLut(lutId) ?: return null
        return catalogLoader.loadLookup(lut.assetPath)
    }

    override suspend fun applyEditAndSave(
        inputImagePath: String,
        edit: FilmEdit,
        originalImagePath: String,
        outputPath: String
    ): FilmLutResult? = withContext(ioDispatcher) {
        var inputBitmapToRecycle: Bitmap? = null
        var resultBitmapToRecycle: Bitmap? = null
        try {
            val inputBitmap =
                loadScaledBitmap(inputImagePath, MAX_DIMENSION) ?: return@withContext null
            inputBitmapToRecycle = inputBitmap

            val resultBitmap = applyEditToBitmap(inputBitmap, edit) ?: return@withContext null
            resultBitmapToRecycle = resultBitmap

            val outputFile = File(outputPath)
            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { out ->
                resultBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }

            runCatching { BitmapIoUtils.copyExifMetadata(originalImagePath, outputPath, "CamCon - Film Simulation Applied") }
                .onFailure { Log.w(TAG, "EXIF 복사 실패: ${it.message}") }

            FilmLutResult(
                outputPath = outputPath,
                width = resultBitmap.width,
                height = resultBitmap.height,
                lutId = edit.lutId
            )
        } catch (e: Exception) {
            Log.e(TAG, "applyEditAndSave 실패", e)
            null
        } finally {
            inputBitmapToRecycle?.recycle()
            resultBitmapToRecycle?.recycle()
        }
    }

    override suspend fun applyEditToTemp(
        inputImagePath: String,
        edit: FilmEdit,
        maxSize: Int
    ): String? = withContext(ioDispatcher) {
        var inputBitmapToRecycle: Bitmap? = null
        try {
            val target = if (maxSize > 0) maxSize else MAX_DIMENSION
            val inputBitmap = loadScaledBitmap(inputImagePath, target) ?: return@withContext null
            inputBitmapToRecycle = inputBitmap

            val resultBitmap = applyEditToBitmap(inputBitmap, edit) ?: return@withContext null
            val tempPath = BitmapIoUtils.saveBitmapToTempFile(resultBitmap, "film_lut_") ?: return@withContext null
            // 결과는 픽셀 미회전으로 저장하고 원본의 orientation 태그(및 EXIF)를 복사해 보존한다.
            // 프리뷰(FilmEditorViewModel.decodeDownscaled)는 픽셀을 회전해 표시하므로, export 는 태그만
            // 보존하면 갤러리가 회전을 적용해 최종 표시가 프리뷰와 일치한다(이중회전 없음).
            runCatching { BitmapIoUtils.copyExifMetadata(inputImagePath, tempPath, "CamCon - Film Simulation Applied") }
                .onFailure { Log.w(TAG, "EXIF 복사 실패: ${it.message}") }
            tempPath
        } catch (e: Exception) {
            Log.e(TAG, "applyEditToTemp 실패", e)
            null
        } finally {
            inputBitmapToRecycle?.recycle()
        }
    }

    override fun isValidImageFile(imagePath: String): Boolean {
        return try {
            val file = File(imagePath)
            if (!file.exists()) return false
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(imagePath, options)
            options.outWidth > 0 && options.outHeight > 0
        } catch (e: Exception) {
            false
        }
    }

    override fun initializeGPU(contextProvider: Any) {
        val context = contextProvider as Context
        filmLutProcessor.initializeGPUImage(context)
        filmAdjustmentProcessor.initializeGPUImage(context)
    }

    override fun releaseGpu() {
        filmLutProcessor.cleanup()
        filmAdjustmentProcessor.cleanup()
        filmThumbnailGenerator.clear()
    }

    // ---- 내부 ----

    /** GPU 우선 적용 후 실패 시 CPU(Lut3D) 폴백. 입력 비트맵은 호출부가 회수한다. */
    private suspend fun applyToBitmap(
        inputBitmap: Bitmap,
        assetPath: String,
        intensity: Float
    ): Bitmap? {
        val lookup = catalogLoader.loadLookup(assetPath)
        if (lookup != null && filmLutProcessor.isGpuReady) {
            filmLutProcessor.applyFilmLutWithGPU(inputBitmap, lookup, intensity)
                ?.let { return it }
            Log.w(TAG, "GPU 필름 LUT 실패 - CPU 폴백")
        }
        val lut3d = catalogLoader.loadLut3D(assetPath) ?: return null
        return filmLutProcessor.applyFilmLutCpu(inputBitmap, lut3d, intensity)
    }

    /**
     * [FilmEdit](LUT+강도+조정 8종)을 [FilmAdjustmentProcessor] 로 1회 적용한다.
     * GPU 미가용/실패 시 기존 [FilmLutProcessor] CPU 경로로 폴백한다(조정 8종은 무시 — 설계 §6).
     * 입력 비트맵은 호출부가 회수한다.
     */
    private suspend fun applyEditToBitmap(inputBitmap: Bitmap, edit: FilmEdit): Bitmap? {
        val lut = if (edit.lutId.isNotEmpty()) catalogLoader.getLut(edit.lutId) else null
        val lookup = lut?.let { catalogLoader.loadLookup(it.assetPath) }

        if (filmAdjustmentProcessor.isGpuReady) {
            filmAdjustmentProcessor
                .apply(inputBitmap, lookup, edit.intensity, edit.adjustments)
                ?.let { return it }
            Log.w(TAG, "GPU 필름 조정 실패 - CPU LUT-only 폴백(조정 무시)")
        }

        // CPU 폴백: LUT-only(조정 미적용). LUT 도 없으면 입력 사본 반환.
        val assetPath = lut?.assetPath ?: return inputBitmap.copy(Bitmap.Config.ARGB_8888, false)
        val lut3d = catalogLoader.loadLut3D(assetPath)
            ?: return inputBitmap.copy(Bitmap.Config.ARGB_8888, false)
        return filmLutProcessor.applyFilmLutCpu(inputBitmap, lut3d, edit.intensity)
    }

    private fun loadScaledBitmap(imagePath: String, maxSize: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(imagePath, bounds)
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            if (longest <= 0) return null

            var sampleSize = 1
            while (longest / (sampleSize * 2) >= maxSize) {
                sampleSize *= 2
            }

            val loadOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeFile(imagePath, loadOptions) ?: return null

            if (bitmap.width > maxSize || bitmap.height > maxSize) {
                val aspect = bitmap.width.toFloat() / bitmap.height.toFloat()
                val (w, h) = if (aspect > 1) {
                    maxSize to (maxSize / aspect).toInt()
                } else {
                    (maxSize * aspect).toInt() to maxSize
                }
                val scaled = Bitmap.createScaledBitmap(bitmap, w.coerceAtLeast(1), h.coerceAtLeast(1), true)
                if (scaled != bitmap) bitmap.recycle()
                scaled
            } else {
                bitmap
            }
        } catch (e: Exception) {
            null
        }
    }

}
