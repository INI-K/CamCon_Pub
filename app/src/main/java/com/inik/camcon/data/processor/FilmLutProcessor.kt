package com.inik.camcon.data.processor

import android.content.Context
import android.graphics.Bitmap
import com.inik.camcon.utils.LogcatManager
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageLookupFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 필름 3D LUT 를 이미지에 적용하는 프로세서.
 *
 * GPU 경로는 GPUImage 내장 [GPUImageLookupFilter](512×512 룩업 + intensity)를 그대로 재사용한다.
 * `.cube` → 512×512 룩업 변환은 [FilmLutAtlasBuilder] 가 담당하며, 본 클래스는 변환된 룩업
 * 비트맵을 받아 GPU 에 적용한다. 색감전송([ColorTransferProcessor])과 동일하게 단일 GPUImage
 * 인스턴스를 [gpuMutex] 로 직렬화해 EGL/필터 상태 race 를 차단하고, OOM 시 다운스케일 1회 재시도한다.
 *
 * GPU 가 초기화되지 않았거나 실패하면 null 을 반환해 호출부가 [applyFilmLutCpu] 폴백으로 넘어가게 한다.
 */
@Singleton
class FilmLutProcessor @Inject constructor() {

    private var gpuImage: GPUImage? = null
    private val gpuMutex = Mutex()

    /** ApplicationContext 로 GPUImage 를 초기화한다(메모리 누수 방지). 색감전송과 동일 패턴. */
    fun initializeGPUImage(context: Context) {
        if (gpuImage == null) {
            gpuImage = GPUImage(context.applicationContext)
        }
    }

    /** GPUImage/EGL 리소스를 해제한다. 앱 종료 시점(Application.onTerminate)에만 호출한다. */
    fun cleanup() {
        gpuImage?.deleteImage()
        gpuImage = null
    }

    val isGpuReady: Boolean get() = gpuImage != null

    /**
     * GPU 로 필름 LUT 를 적용한다.
     *
     * @param inputBitmap 처리할 입력 이미지(호출부가 GPU 한도 내로 다운스케일해 전달).
     * @param lookupBitmap [FilmLutAtlasBuilder] 가 만든 512×512 룩업. 필터가 회수하지 못하도록
     *   내부에서 사본을 만들어 전달하므로, 호출부는 캐시된 원본을 그대로 보관/재사용할 수 있다.
     * @param intensity 0(원본) ~ 1(LUT 완전 적용).
     * @return 결과 비트맵, GPU 미초기화/실패 시 null.
     */
    suspend fun applyFilmLutWithGPU(
        inputBitmap: Bitmap,
        lookupBitmap: Bitmap,
        intensity: Float
    ): Bitmap? = withContext(Dispatchers.Default) {
        return@withContext try {
            val gpu = gpuImage ?: run {
                LogcatManager.w(TAG, "⚠️ GPUImage 미초기화 - CPU 폴백")
                return@withContext null
            }
            val filter = GPUImageLookupFilter().apply {
                setIntensity(intensity.coerceIn(0f, 1f))
                // setBitmap 은 입력을 회수하지 않고 GL 텍스처로만 업로드하므로(검증 완료),
                // 캐시가 소유하는 룩업을 복사 없이 그대로 넘긴다. gpuMutex 로 GPU 구간이 직렬화되고
                // 룩업은 immutable 이라 editor GPUImageView 와 동시 읽기도 안전하다.
                setBitmap(lookupBitmap)
            }
            runGpuFilterApply(gpu, filter, inputBitmap)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogcatManager.w(TAG, "Film LUT GPU 적용 실패", e)
            null
        }
    }

    /**
     * 단일 GPUImage 를 Mutex 로 직렬화해 setFilter+getBitmapWithFilterApplied 를 적용한다.
     * OOM 시 입력을 다운스케일해 1회 재시도하고, 그래도 실패하면 null → 호출부 CPU 폴백.
     */
    private suspend fun runGpuFilterApply(
        gpu: GPUImage,
        filter: GPUImageFilter,
        inputBitmap: Bitmap
    ): Bitmap? = gpuMutex.withLock {
        try {
            gpu.setFilter(filter)
            gpu.getBitmapWithFilterApplied(inputBitmap)
        } catch (oom: OutOfMemoryError) {
            LogcatManager.w(
                TAG,
                "Film LUT GPU OOM - 다운스케일 재시도 (${inputBitmap.width}x${inputBitmap.height})"
            )
            System.gc()
            val scaled = scaleDownBitmap(inputBitmap, GPU_OOM_RETRY_MAX_SIZE)
            try {
                gpu.setFilter(filter)
                gpu.getBitmapWithFilterApplied(scaled)
            } catch (oom2: OutOfMemoryError) {
                LogcatManager.w(TAG, "Film LUT GPU 재시도도 OOM - CPU 폴백")
                null
            } finally {
                if (scaled != inputBitmap) scaled.recycle()
            }
        }
    }

    private fun scaleDownBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxSize) return bitmap
        val scale = maxSize.toFloat() / longest
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    /**
     * CPU 폴백 — [Lut3D] 를 픽셀별 삼선형 보간으로 직접 적용한다(행 청크 병렬).
     * GPU 가 없거나 실패한 환경에서도 동일 결과를 보장한다.
     */
    suspend fun applyFilmLutCpu(
        inputBitmap: Bitmap,
        lut: Lut3D,
        intensity: Float
    ): Bitmap = withContext(Dispatchers.Default) {
        val w = inputBitmap.width
        val h = inputBitmap.height
        val pixels = IntArray(w * h)
        inputBitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val t = intensity.coerceIn(0f, 1f)

        val cores = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
        val rowsPerChunk = (h + cores - 1) / cores
        coroutineScope {
            (0 until cores).map { c ->
                async {
                    val start = c * rowsPerChunk
                    val end = minOf(start + rowsPerChunk, h)
                    val out = FloatArray(3)
                    var idx = start * w
                    var y = start
                    while (y < end) {
                        var x = 0
                        while (x < w) {
                            val p = pixels[idx]
                            val a = (p ushr 24) and 0xFF
                            val r = ((p ushr 16) and 0xFF) / 255f
                            val g = ((p ushr 8) and 0xFF) / 255f
                            val b = (p and 0xFF) / 255f
                            lut.sampleTrilinear(r, g, b, out)
                            val nr = ((r + (out[0] - r) * t).coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                            val ng = ((g + (out[1] - g) * t).coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                            val nb = ((b + (out[2] - b) * t).coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                            pixels[idx] = (a shl 24) or (nr shl 16) or (ng shl 8) or nb
                            x++
                            idx++
                        }
                        y++
                    }
                }
            }.awaitAll()
        }

        Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    companion object {
        private const val TAG = "FilmLutProcessor"
        private const val GPU_OOM_RETRY_MAX_SIZE = 2048
    }
}
