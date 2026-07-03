package com.inik.camcon.data.processor

import android.graphics.Bitmap
import android.util.LruCache
import com.inik.camcon.data.repository.FilmLutCatalogLoader
import com.inik.camcon.di.IoDispatcher
import com.inik.camcon.utils.LogcatManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 컨택트 시트 그리드용 썸네일 생성기.
 *
 * 이미 작게(긴 변 ~512px) 다운스케일된 "썸네일 소스" 비트맵에 **LUT 룩업만** 적용해 작은 결과
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

    // 바이트 예산 LRU: 소스가 512px로 커지며(화질 개선) 개수 기반(300개)이면 최대 ~210MB까지
    // 자랄 수 있어 저사양 기기에서 위험 → 총 48MB 예산으로 제한(512px 썸네일 약 70장 상당).
    private val cache = object : LruCache<String, Bitmap>(MAX_CACHE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.byteCount / 1024).coerceAtLeast(1)
    }

    /** 동시 썸네일 생성 수 제한(에셋 경합·메모리 폭주 방지). GPU 직렬화와 별개로 .cube 읽기/CPU 적용을 제한. */
    private val genSemaphore = Semaphore(MAX_CONCURRENT)

    /**
     * [thumbSource] 에 [lutId] LUT 를 강도 1.0 으로 적용한 썸네일을 반환한다.
     *
     * @param sourceId 썸네일 소스의 안정 식별자(세션 내 고정). 캐시 키 일부.
     * @param thumbSource ~512px 로 다운스케일된 소스 비트맵(호출부/VM 소유, 회수 책임도 호출부).
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
        // 동시 생성 수를 제한(썬더링 허드 방지): 많은 셀이 한꺼번에 .cube 를 열어 AssetManager 경합·메모리
        // 폭주를 일으키던 문제를 막는다. 대기 중 다른 job 이 이미 만들었으면 캐시 히트로 즉시 반환.
        genSemaphore.withPermit {
            cache.get(key)?.let { if (!it.isRecycled) return@withPermit it }
            coroutineContext.ensureActive()
            val lut = catalogLoader.getLut(lutId) ?: run {
                LogcatManager.w(TAG, "썸네일 LUT 미발견: $lutId")
                return@withPermit null
            }
            try {
                coroutineContext.ensureActive()
                // 썸네일은 작아서(≈512px) 512² GPU 아틀라스를 만들 필요 없이 CPU 삼선형으로 직접 적용한다.
                // (296개 아틀라스 빌드·캐시 축출 폭주와 1MB×N 메모리 churn 을 제거. 편집 프리뷰/내보내기는
                //  여전히 GPU 아틀라스 경로를 쓴다.)
                val lut3d = catalogLoader.loadLut3D(lut.assetPath) ?: return@withPermit null
                coroutineContext.ensureActive()
                val finalBitmap = filmLutProcessor.applyFilmLutCpu(thumbSource, lut3d, INTENSITY)
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
    }

    /** 세션 종료/소스 교체 시 캐시를 비운다(캐시 소유 비트맵 일괄 해제). */
    fun clear() {
        cache.evictAll()
    }

    private fun cacheKey(sourceId: String, lutId: String): String = "$sourceId::$lutId"

    companion object {
        private const val TAG = "FilmThumbnailGen"

        /** 썸네일 캐시 총 예산(KB). 48MB ≈ 512px 썸네일(~700KB) 70장. */
        private const val MAX_CACHE_KB = 48 * 1024
        private const val MAX_CONCURRENT = 2
        private const val INTENSITY = 1f
    }
}
