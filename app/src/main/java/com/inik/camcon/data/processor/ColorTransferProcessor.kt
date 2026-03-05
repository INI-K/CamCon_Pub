package com.inik.camcon.data.processor
import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.get
import androidx.core.graphics.set
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class ColorTransferProcessor @Inject constructor() {
    data class ReferenceStats(val r: Float, val g: Float, val b: Float)
    private val cache = mutableMapOf<String, ReferenceStats>()
    fun initializeGPUImage(context: Context) {
        // 현재 구현은 CPU 경로를 기본으로 사용한다.
        context.applicationContext
    }
    fun clearCache() {
        cache.clear()
    }
    fun getCachedReferenceStats(referenceImagePath: String): ReferenceStats? {
        return cache[referenceImagePath] ?: run {
            val bmp = android.graphics.BitmapFactory.decodeFile(referenceImagePath) ?: return null
            val stats = computeStats(bmp)
            bmp.recycle()
            cache[referenceImagePath] = stats
            stats
        }
    }
    fun applyColorTransferWithCachedStatsOptimized(
        inputBitmap: Bitmap,
        referenceStats: ReferenceStats,
        intensity: Float
    ): Bitmap = applyWithStats(inputBitmap, referenceStats, intensity)
    fun applyColorTransferWithCachedStats(
        inputBitmap: Bitmap,
        referenceStats: ReferenceStats,
        intensity: Float
    ): Bitmap = applyWithStats(inputBitmap, referenceStats, intensity)
    fun applyColorTransfer(
        inputBitmap: Bitmap,
        referenceBitmap: Bitmap,
        intensity: Float
    ): Bitmap = applyWithStats(inputBitmap, computeStats(referenceBitmap), intensity)
    fun applyColorTransferOptimized(
        inputBitmap: Bitmap,
        referenceBitmap: Bitmap,
        intensity: Float
    ): Bitmap = applyColorTransfer(inputBitmap, referenceBitmap, intensity)
    fun applyColorTransferWithGPU(
        inputBitmap: Bitmap,
        referenceBitmap: Bitmap,
        intensity: Float
    ): Bitmap? = if (inputBitmap.width <= 0 || inputBitmap.height <= 0) {
        null
    } else {
        applyColorTransferOptimized(inputBitmap, referenceBitmap, intensity)
    }
    fun applyColorTransferWithGPUCached(
        inputBitmap: Bitmap,
        referenceStats: ReferenceStats,
        intensity: Float
    ): Bitmap? = if (inputBitmap.width <= 0 || inputBitmap.height <= 0) {
        null
    } else {
        applyColorTransferWithCachedStatsOptimized(inputBitmap, referenceStats, intensity)
    }
    fun applyColorTransferAndSave(
        inputBitmap: Bitmap,
        referenceBitmap: Bitmap,
        originalImagePath: String,
        outputPath: String,
        intensity: Float
    ): Bitmap {
        File(originalImagePath).exists()
        val result = applyColorTransferOptimized(inputBitmap, referenceBitmap, intensity)
        val file = File(outputPath)
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { out ->
            result.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        return result
    }
    private fun computeStats(bitmap: Bitmap): ReferenceStats {
        var r = 0L
        var g = 0L
        var b = 0L
        val total = bitmap.width * bitmap.height
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val p = bitmap[x, y]
                r += android.graphics.Color.red(p)
                g += android.graphics.Color.green(p)
                b += android.graphics.Color.blue(p)
            }
        }
        return ReferenceStats(
            r = r.toFloat() / total.coerceAtLeast(1),
            g = g.toFloat() / total.coerceAtLeast(1),
            b = b.toFloat() / total.coerceAtLeast(1)
        )
    }
    private fun applyWithStats(inputBitmap: Bitmap, stats: ReferenceStats, intensity: Float): Bitmap {
        val amount = intensity.coerceIn(0f, 1f)
        val out = inputBitmap.copy(Bitmap.Config.ARGB_8888, true)
        for (y in 0 until out.height) {
            for (x in 0 until out.width) {
                val p = out[x, y]
                val nr = blend(android.graphics.Color.red(p), stats.r, amount)
                val ng = blend(android.graphics.Color.green(p), stats.g, amount)
                val nb = blend(android.graphics.Color.blue(p), stats.b, amount)
                out[x, y] = android.graphics.Color.argb(android.graphics.Color.alpha(p), nr, ng, nb)
            }
        }
        return out
    }
    private fun blend(source: Int, target: Float, amount: Float): Int {
        return (source + (target - source) * amount).toInt().coerceIn(0, 255)
    }
}