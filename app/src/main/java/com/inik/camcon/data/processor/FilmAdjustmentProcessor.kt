package com.inik.camcon.data.processor

import android.content.Context
import android.graphics.Bitmap
import com.inik.camcon.di.IoDispatcher
import com.inik.camcon.domain.model.FilmAdjustments
import com.inik.camcon.domain.model.FilmEdit
import com.inik.camcon.utils.LogcatManager
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.filter.GPUImageContrastFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageExposureFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilterGroup
import jp.co.cyberagent.android.gpuimage.filter.GPUImageHighlightShadowFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageLookupFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageSaturationFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageWhiteBalanceFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LUT(룩업+강도) + 조정 8종을 [GPUImageFilterGroup] 으로 **1회** 적용하는 프로세서.
 *
 * [FilmLutProcessor] 와 동일하게 단일 [GPUImage] 인스턴스를 [gpuMutex] 로 직렬화해 EGL/필터 상태
 * race 를 차단하고, OOM 시 다운스케일 1회 재시도한다. GPU 미초기화/실패 시 null 을 반환해
 * 호출부가 CPU LUT-only 폴백([FilmLutProcessor.applyFilmLutCpu], 조정 무시)으로 넘어가게 한다.
 *
 * 조정 적용 순서(설계 §6 고정):
 *  LUT → Exposure → WhiteBalance(색온도) → Contrast → HighlightShadow → Saturation
 *  → Grain(커스텀) → ChromaticAberration(커스텀).
 *
 * 입력 [FilmAdjustments] 는 UI 단위(중립 0)이며, GPU 필터 파라미터로의 매핑은 본 클래스가 단일
 * 지점에서 수행한다([toGpuFilters]).
 */
@Singleton
class FilmAdjustmentProcessor @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    private var gpuImage: GPUImage? = null
    private val gpuMutex = Mutex()

    /** ApplicationContext 로 GPUImage 를 초기화한다(메모리 누수 방지). */
    fun initializeGPUImage(context: Context) {
        if (gpuImage == null) {
            gpuImage = GPUImage(context.applicationContext)
        }
    }

    /** GPUImage/EGL 리소스를 해제한다. 앱 종료 시점에만 호출한다. */
    fun cleanup() {
        gpuImage?.deleteImage()
        gpuImage = null
    }

    val isGpuReady: Boolean get() = gpuImage != null

    /**
     * LUT + 조정 8종을 GPU 로 1회 적용한다.
     *
     * @param inputBitmap 처리할 입력 이미지(호출부가 GPU 한도 내로 다운스케일해 전달, 회수 책임도 호출부).
     * @param lookupBitmap [FilmLutAtlasBuilder] 가 만든 512×512 룩업. 캐시 소유이므로 회수 금지.
     *   null 이면 LUT 단계를 생략하고 조정만 적용한다.
     * @param intensity LUT 강도 0~1.
     * @param adjustments 조정 8종(UI 단위). 중립이고 lookup 도 없으면 입력 사본을 그대로 반환.
     * @return 결과 비트맵, GPU 미초기화/실패 시 null.
     */
    suspend fun apply(
        inputBitmap: Bitmap,
        lookupBitmap: Bitmap?,
        intensity: Float,
        adjustments: FilmAdjustments
    ): Bitmap? = withContext(ioDispatcher) {
        return@withContext try {
            val gpu = gpuImage ?: run {
                LogcatManager.w(TAG, "⚠️ GPUImage 미초기화 - CPU 폴백")
                return@withContext null
            }
            val filters = buildFilters(lookupBitmap, intensity, adjustments)
            if (filters.isEmpty()) {
                // LUT 도 없고 조정도 전부 중립 → 변환 없음. 입력 사본 반환(소유권 분리).
                return@withContext inputBitmap.copy(Bitmap.Config.ARGB_8888, false)
            }
            val group = GPUImageFilterGroup(filters)
            runGpuFilterApply(gpu, group, inputBitmap)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogcatManager.w(TAG, "필름 조정 GPU 적용 실패", e)
            null
        }
    }

    /**
     * **필터그룹 빌더 단일 지점**(설계 §6). `(lookup, edit)` → 적용 순서대로 구성된 GPU 필터 리스트.
     *
     * 라이브 프리뷰([FilmEditPreview])와 내보내기([apply] 경로)가 동일 결과를 내도록 이 메서드 하나만
     * 사용한다. UI 단위 → GPU 파라미터 매핑·범위 clamp 도 여기 단일 지점에서 수행([FilmAdjustments.normalized]).
     *
     * @param lookup [FilmLutAtlasBuilder] 가 만든 512×512 룩업(캐시 소유, 회수 금지). null 이면 LUT 단계 생략.
     * @param edit per-photo 편집 상태(lutId 는 호출부가 이미 [lookup] 으로 해석, intensity/adjustments 사용).
     * @return 적용 순서대로의 필터 리스트. 비어 있으면(변환 없음) 호출부가 원본 그대로 표시/반환한다.
     */
    fun buildFilters(lookup: Bitmap?, edit: FilmEdit): List<GPUImageFilter> =
        buildFilters(lookup, edit.intensity, edit.adjustments)

    /** lookup + 조정에서 적용 순서대로 GPU 필터 리스트를 구성한다(중립 단계는 생략). 범위 clamp 후 매핑. */
    private fun buildFilters(
        lookupBitmap: Bitmap?,
        intensity: Float,
        adjustments: FilmAdjustments
    ): List<GPUImageFilter> {
        val a = adjustments.normalized()
        val filters = ArrayList<GPUImageFilter>(8)

        // 1. LUT(룩업 + 강도)
        if (lookupBitmap != null) {
            filters.add(GPUImageLookupFilter().apply {
                setIntensity(intensity.coerceIn(0f, 1f))
                setBitmap(lookupBitmap)
            })
        }
        // 2. 노출(EV 직접 전달)
        if (a.exposure != FilmAdjustments.NEUTRAL) {
            filters.add(GPUImageExposureFilter(a.exposure))
        }
        // 3. 색온도: GPUImageWhiteBalanceFilter 셰이더의 K→보정계수가 cool(<5000K, 0.0004/K)과
        //    warm(>5000K, 0.00006/K)로 ~6.7배 비대칭이라, 동일 Kelvin offset 을 쓰면 -100(강한 파랑)과
        //    +100(약한 주황)의 체감이 어긋난다. UI 양끝(±100)에서 보정 강도가 대칭(|계수|≈0.5)이 되도록
        //    Kelvin offset 을 비대칭으로 매핑한다(cool 1250K, warm 8333K). 최종 강도는 실기기 보정 대상.
        if (a.temperature != FilmAdjustments.NEUTRAL) {
            val t = a.temperature / 100f
            val kelvin = if (t >= 0f) 5000f + t * 8333f else 5000f + t * 1250f
            filters.add(GPUImageWhiteBalanceFilter(kelvin, 0f))
        }
        // 4. 대비: ±100 → 0.5..1.5 (1.0 중립)
        if (a.contrast != FilmAdjustments.NEUTRAL) {
            filters.add(GPUImageContrastFilter(1f + (a.contrast / 100f) * 0.5f))
        }
        // 5. 하이라이트/섀도: HighlightShadow(highlights 0..1[1중립,낮추면 누름], shadows 0..1[0중립,올리면 들어올림])
        if (a.shadows != FilmAdjustments.NEUTRAL || a.highlights != FilmAdjustments.NEUTRAL) {
            // shadows UI ±100 → 0..1 (양수만 의미, 음수는 0 으로 clamp 되어 무변화)
            val shadows = (a.shadows / 100f).coerceIn(0f, 1f)
            // highlights UI ±100, 0=중립(1.0). 음수면 1.0→0.0 으로 눌러 디테일 보존, 양수는 1.0 유지(무변화)
            val highlights = (1f - (-a.highlights / 100f)).coerceIn(0f, 1f)
            filters.add(GPUImageHighlightShadowFilter(shadows, highlights))
        }
        // 6. 채도: ±100 → 0..2 (1.0 중립)
        if (a.saturation != FilmAdjustments.NEUTRAL) {
            filters.add(GPUImageSaturationFilter(1f + (a.saturation / 100f)))
        }
        // 7. 그레인(커스텀)
        if (a.grain > 0f) {
            filters.add(FilmGrainFilter(a.grain))
        }
        // 8. 색수차(커스텀)
        if (a.chromaticAberration > 0f) {
            filters.add(FilmChromaticAberrationFilter(a.chromaticAberration))
        }
        return filters
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
                "필름 조정 GPU OOM - 다운스케일 재시도 (${inputBitmap.width}x${inputBitmap.height})"
            )
            System.gc()
            val scaled = scaleDownBitmap(inputBitmap, GPU_OOM_RETRY_MAX_SIZE)
            try {
                gpu.setFilter(filter)
                gpu.getBitmapWithFilterApplied(scaled)
            } catch (oom2: OutOfMemoryError) {
                LogcatManager.w(TAG, "필름 조정 GPU 재시도도 OOM - CPU 폴백")
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

    companion object {
        private const val TAG = "FilmAdjustProcessor"
        private const val GPU_OOM_RETRY_MAX_SIZE = 2048
    }
}
