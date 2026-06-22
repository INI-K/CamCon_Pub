package com.inik.camcon.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache
import com.inik.camcon.data.processor.CubeLutParser
import com.inik.camcon.data.processor.FilmLutAtlasBuilder
import com.inik.camcon.data.processor.Lut3D
import com.inik.camcon.domain.model.FilmLut
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `assets/film_luts.json` 카탈로그와 `assets/luts/**/*.cube` 파일을 로드한다.
 *
 * - 카탈로그는 1회 로드 후 메모리에 보관.
 * - 파싱된 [Lut3D] 와 512×512 룩업 [Bitmap] 은 각각 소형 LRU 캐시로 보관해 슬라이더/LUT 전환
 *   시 재파싱·재변환 비용을 줄인다. 룩업 캐시는 [FilmLutProcessor] 가 적용 시 사본을 만들어 쓰므로
 *   캐시된 비트맵은 회수하지 않는다(use-after-recycle 방지).
 */
@Singleton
class FilmLutCatalogLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val assets get() = context.assets

    @Volatile
    private var catalog: List<FilmLut>? = null
    private val catalogMutex = Mutex()

    private val lut3dCache = LruCache<String, Lut3D>(MAX_LUT3D_CACHE)
    private val lookupCache = LruCache<String, Bitmap>(MAX_LOOKUP_CACHE)

    /** 카탈로그를 반환한다(최초 1회 assets 에서 로드). */
    suspend fun getCatalog(): List<FilmLut> = catalogMutex.withLock {
        catalog ?: loadCatalogFromAssets().also { catalog = it }
    }

    suspend fun getLut(id: String): FilmLut? = getCatalog().firstOrNull { it.id == id }

    private suspend fun loadCatalogFromAssets(): List<FilmLut> = withContext(Dispatchers.IO) {
        runCatching {
            val text = assets.open(CATALOG_PATH).bufferedReader().use { it.readText() }
            val arr = JSONObject(text).getJSONArray("filmLUTs")
            val list = ArrayList<FilmLut>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val lutFile = o.getString("lut_file")
                list.add(
                    FilmLut(
                        id = lutFile,
                        name = o.optString("name", lutFile.substringAfterLast('/').removeSuffix(".cube")),
                        category = o.optString("category", "Other"),
                        assetPath = lutFile
                    )
                )
            }
            list
        }.getOrElse { emptyList() }
    }

    /** assetPath 의 `.cube` 를 파싱한 [Lut3D] 를 반환한다(LRU 캐시). */
    suspend fun loadLut3D(assetPath: String): Lut3D? = withContext(Dispatchers.IO) {
        lut3dCache.get(assetPath)?.let { return@withContext it }
        val text = runCatching {
            assets.open(assetPath).bufferedReader().use { it.readText() }
        }.getOrNull() ?: run {
            Log.w(TAG, "LUT 에셋 읽기 실패: $assetPath")
            return@withContext null
        }
        val lut = CubeLutParser.parse(text) ?: run {
            // 파싱 실패(1D LUT / 비단위 DOMAIN / 데이터 부족 등) — GPU 미초기화와 구분되도록 로깅.
            Log.w(TAG, "LUT 파싱 실패(미지원 포맷/손상): $assetPath")
            return@withContext null
        }
        lut3dCache.put(assetPath, lut)
        lut
    }

    /** assetPath 의 LUT 를 512×512 룩업 [Bitmap] 으로 변환해 반환한다(LRU 캐시). */
    suspend fun loadLookup(assetPath: String): Bitmap? = withContext(Dispatchers.Default) {
        lookupCache.get(assetPath)?.let { if (!it.isRecycled) return@withContext it }
        val lut = loadLut3D(assetPath) ?: return@withContext null
        val atlas = FilmLutAtlasBuilder.build(lut)
        lookupCache.put(assetPath, atlas)
        atlas
    }

    companion object {
        private const val TAG = "FilmLutCatalogLoader"
        private const val CATALOG_PATH = "film_luts.json"
        // 컨택트 시트 스크롤 시 .cube 재파싱을 줄이기 위해 충분히 크게(Lut3D 1개 ≈ 수십 KB).
        private const val MAX_LUT3D_CACHE = 64
        private const val MAX_LOOKUP_CACHE = 6
    }
}
