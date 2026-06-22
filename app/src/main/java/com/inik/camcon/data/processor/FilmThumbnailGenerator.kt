package com.inik.camcon.data.processor

import android.graphics.Bitmap
import android.util.LruCache
import com.inik.camcon.data.repository.FilmLutCatalogLoader
import com.inik.camcon.di.IoDispatcher
import com.inik.camcon.utils.LogcatManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 컨택트 시트 그리드용 썸네일 생성기.
 *
 * 이미 작게(긴 변 ~200px) 다운스케일된 "썸네일 소스" 비트맵에 **LUT 룩업만** 적용해 작은 결과
 * 비트맵을 만든다. 조정 8종은 적용하지 않는다(설계 §5: 그리드는 필름 룩 식별용).
 *
 * - 적용은 [FilmLutProcessor](단일 GPUImage + gpuMutex)에 위임해 EGL race 를 차단하고 패턴을 재사용한다.
 * - `(sourceId, lutId)` 키 LRU 캐시(약 300). 세션 내 소스 비트맵이 고정이면 [sourceId] 만으로 가시 집합을
 *   안정 식별한다. 캐시가 소유한 결과 비트맵은 회수 금지(use-after-recycle 방지) — 호출부도 회수하지 않는다.
 * - GPU 미가용/실패 시 CPU 삼선형([FilmLutProcessor.applyFilmLutCpu])으로 폴백.
 * - 코루틴 취소(스크롤로 셀이 화면 밖으로) 협조: 진입/단계마다 [ensureActive].
 */
@Singleton
class FilmThumbnailGenerator @Inject constructor(
    private val catalogLoader: FilmLutCatalogLoader,
    private val filmLutProcessor: FilmLutProcessor,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    private val cache = LruCache<String, Bitmap>(MAX_CACHE)

    /**
     * [thumbSource] 에 [lutId] LUT 를 강도 1.0 으로 적용한 썸네일을 반환한다.
     *
     * @param sourceId 썸네일 소스의 안정 식별자(세션 내 고정). 캐시 키 일부.
     * @param thumbSource ~200px 로 다운스케일된 소스 비트맵(호출부/VM 소유, 회수 책임도 호출부).
     *   본 메서드는 소스를 회수하지 않는다.
     * @param lutId 적용할 필름 LUT id.
     * @return 썸네일 비트맵(캐시 소유 — 회수 금지). 실패 시 null.
     */
    suspend fun generate(
        sourceId: String,
        thumbSource: Bitmap,
        lutId: String
    ): Bitmap? = withContext(ioDispatcher) {
        val key = cacheKey(sourceId, lutId)
        cache.get(key)?.let { if (!it.isRecycled) return@withContext it }

        coroutineContext.ensureActive()
        val lut = catalogLoader.getLut(lutId) ?: run {
            LogcatManager.w(TAG, "썸네일 LUT 미발견: $lutId")
            return@withContext null
        }

        try {
            coroutineContext.ensureActive()
            val lookup = catalogLoader.loadLookup(lut.assetPath)
            val result: Bitmap? = if (lookup != null && filmLutProcessor.isGpuReady) {
                filmLutProcessor.applyFilmLutWithGPU(thumbSource, lookup, INTENSITY)
            } else {
                null
            }

            val finalBitmap = result ?: run {
                coroutineContext.ensureActive()
                val lut3d = catalogLoader.loadLut3D(lut.assetPath) ?: return@withContext null
                filmLutProcessor.applyFilmLutCpu(thumbSource, lut3d, INTENSITY)
            }

            cache.put(key, finalBitmap)
            finalBitmap
        } catch (e: CancellationException) {
            throw e
        } catch (oom: OutOfMemoryError) {
            LogcatManager.w(TAG, "썸네일 생성 OOM: $lutId")
            cache.evictAll()
            null
        } catch (e: Exception) {
            LogcatManager.w(TAG, "썸네일 생성 실패: $lutId", e)
            null
        }
    }

    /** 세션 종료/소스 교체 시 캐시를 비운다(캐시 소유 비트맵 일괄 해제). */
    fun clear() {
        cache.evictAll()
    }

    private fun cacheKey(sourceId: String, lutId: String): String = "$sourceId::$lutId"

    companion object {
        private const val TAG = "FilmThumbnailGen"
        private const val MAX_CACHE = 300
        private const val INTENSITY = 1f
    }
}
