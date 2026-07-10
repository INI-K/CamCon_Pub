package com.inik.camcon.presentation.viewmodel

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.domain.repository.FilmEditProcessor
import com.inik.camcon.di.IoDispatcher
import com.inik.camcon.domain.model.FilmAdjustments
import com.inik.camcon.domain.model.FilmEdit
import com.inik.camcon.domain.model.FilmLut
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.repository.AppSettingsRepository
import com.inik.camcon.domain.usecase.FilmFavoritesUseCase
import com.inik.camcon.domain.usecase.FilmLutUseCase
import com.inik.camcon.domain.usecase.ObserveEffectiveTierUseCase
import com.inik.camcon.domain.usecase.ValidateFeatureAccessUseCase
import com.inik.camcon.presentation.ui.screens.components.ImageProcessingUtils
import com.inik.camcon.utils.LogcatManager
import com.inik.camcon.utils.MediaStoreVolumes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * 필름 시뮬레이션 풀 에디터 공유 ViewModel (Phase 2 + Phase 3).
 *
 * 컨택트 시트(화면1)와 편집(화면2)이 [FilmEditorActivity] 내에서 공유한다.
 * - 컨택트 시트: 대상 이미지 디코딩, LUT 카탈로그/검색/카테고리/즐겨찾기 → 파생 [visibleLuts],
 *   지연 썸네일 생성([requestThumbnail]/[thumbnails]), 선택([selectedLutId]).
 * - 편집(Phase 3): 편집 상태 [filmEdit](lutId/intensity/adjustments) + 프리뷰 룩업 [lookupBitmap] +
 *   슬라이더 조작([setIntensity]/[setAdjustment]/[resetAdjustments]) + 갤러리 내보내기([export]).
 *   프리뷰는 [previewBitmap](VM 소유) + [lookupBitmap](캐시 소유) + [filmEdit] 로 GPU 필터그룹을 구성한다.
 *
 * 비트맵 소유/회수 규약(설계 §8):
 * - [previewBitmap], [thumbSource] = VM 소유 → [onCleared] 에서 회수.
 * - 썸네일 결과([thumbnails] 값) = [FilmThumbnailGenerator] 캐시 소유 → **회수 금지**(표시만).
 *
 * GPU 생명주기: [FilmLutUseCase.initializeGPU] 는 init 에서 1회(idempotent). releaseGpu 는 앱 전역
 * 싱글톤 GPUImage/EGL 을 파괴(자동적용 경로·CamCon.kt 와 공유)하므로 **VM 에서 호출하지 않는다** —
 * GPU 싱글톤은 앱 수명 동안 유지하고, [onCleared] 는 VM 소유 비트맵 회수 + 썸네일 LRU clear 만 한다.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class FilmEditorViewModel @Inject constructor(
    private val filmLutUseCase: FilmLutUseCase,
    private val filmFavoritesUseCase: FilmFavoritesUseCase,
    private val filmEditProcessor: FilmEditProcessor,
    private val observeEffectiveTierUseCase: ObserveEffectiveTierUseCase,
    private val validateFeatureAccessUseCase: ValidateFeatureAccessUseCase,
    private val appSettingsRepository: AppSettingsRepository,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    // ---- 카탈로그 / 필터 상태 ----

    private val _availableLuts = MutableStateFlow<List<FilmLut>>(emptyList())
    val availableLuts: StateFlow<List<FilmLut>> = _availableLuts.asStateFlow()

    private val _categoryFilter = MutableStateFlow(CATEGORY_ALL)
    val categoryFilter: StateFlow<String> = _categoryFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val favorites: StateFlow<Set<String>> = filmFavoritesUseCase.favorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    // ---- 티어 게이팅(무료 시그니처 필름 + PRO 전체 카탈로그) ----
    // 주의: visibleLuts 가 lockedLutIds 를 참조하므로 반드시 그보다 먼저 선언(프로퍼티 초기화 순서).

    /** pref 우선 병합 유효 티어(cold). 첫 방출이 병합 티어 → PRO 잠금 플래시 없음(H1). */
    private val effectiveTier: Flow<SubscriptionTier> = observeEffectiveTierUseCase()

    /**
     * 현재 티어에서 잠긴(선택 불가) LUT id 집합. 배지 표시·정렬 전용.
     *
     * 초기값 emptySet = 배지 없음 → PRO 잠금 플래시 없음(photoPreviewAccess null 초기값 관례와 동형).
     * FREE 는 수 ms 후 '카탈로그 − 무료셋' 을 방출한다 — 이 창의 셀 탭은 [selectLutGated] 가 first()
     * 로 정확히 판정하므로 우회되지 않는다. 전체 허용 티어는 항상 emptySet.
     */
    val lockedLutIds: StateFlow<Set<String>> =
        combine(_availableLuts, effectiveTier) { luts, tier ->
            if (validateFeatureAccessUseCase.isFullLutCatalogAllowed(tier)) {
                emptySet()
            } else {
                luts.mapTo(HashSet()) { it.id } - ValidateFeatureAccessUseCase.FREE_FILM_LUT_IDS
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /**
     * 카테고리(전체/즐겨찾기/특정) + 검색어(name 부분일치, 대소문자 무시)로 거른 가시 LUT 목록.
     * 그리드/편집 필름 전환 스트립의 단일 진실원.
     * 사용 가능한(잠기지 않은) 필름이 항상 상단에 오도록 안정 정렬한다 — PRO 는 잠금이 없어 원순서 유지.
     */
    val visibleLuts: StateFlow<List<FilmLut>> =
        combine(
            _availableLuts,
            _categoryFilter,
            _searchQuery,
            favorites,
            lockedLutIds
        ) { luts, category, query, favs, locked ->
            luts.asSequence()
                .filter { lut ->
                    when (category) {
                        CATEGORY_ALL -> true
                        CATEGORY_FAVORITES -> lut.id in favs
                        else -> lut.category == category
                    }
                }
                .filter { lut ->
                    query.isBlank() || lut.name.contains(query.trim(), ignoreCase = true)
                }
                .sortedBy { it.id in locked }
                .toList()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedLutId = MutableStateFlow("")
    val selectedLutId: StateFlow<String> = _selectedLutId.asStateFlow()

    /**
     * 게이트를 통과한 선택 반영 신호(선택된 lutId). 호스트가 편집 화면 이동/결과 반환에 사용한다.
     * 편집 화면 하단 스트립도 [selectLutGated] 를 쓰므로 이 이벤트가 발화한다 — 호스트 수집기는
     * 반드시 현재 라우트가 컨택트 시트일 때만 네비게이션한다(재네비게이션 방지).
     * tryEmit 드롭 방지를 위해 extraBufferCapacity = 1 (pipelineSwapEvent 관례).
     */
    private val _lutSelectionAccepted = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val lutSelectionAccepted: SharedFlow<String> = _lutSelectionAccepted.asSharedFlow()

    /** 잠긴 LUT 탭 시 안내 신호(1회성). 호스트가 토스트로 표시한다. */
    private val _lutLockNotice = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val lutLockNotice: SharedFlow<Unit> = _lutLockNotice.asSharedFlow()

    // ---- 편집 상태(Phase 3) ----

    /**
     * per-photo 편집 상태(lutId = 현재 선택, intensity, 조정 8종). 슬라이더 드래그마다 즉시 갱신되어
     * 슬라이더 UI 가 끊김 없이 반응한다. GPU 프리뷰는 디바운스된 [previewEdit] 를 소비한다.
     */
    private val _filmEdit = MutableStateFlow(FilmEdit(lutId = ""))
    val filmEdit: StateFlow<FilmEdit> = _filmEdit.asStateFlow()

    /**
     * [filmEdit] 의 디바운스(~[PREVIEW_DEBOUNCE_MS]ms) 사본. 프리뷰([FilmEditPreview])가 이 값으로
     * GPU 필터그룹을 재구성한다. 과다 갱신을 막아 EGL 부담을 줄인다(설계 §8).
     * lutId 변경 즉시 반영을 위해 lutId 만 분리 갱신할 수도 있으나, 디바운스 짧아 단일화한다.
     */
    val previewEdit: StateFlow<FilmEdit> =
        _filmEdit
            .debounce(PREVIEW_DEBOUNCE_MS)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FilmEdit(lutId = ""))

    /** 현재 [filmEdit].lutId 의 512×512 룩업 비트맵(캐시 소유 → 회수 금지). LUT 미선택이면 null. */
    private val _lookupBitmap = MutableStateFlow<Bitmap?>(null)
    val lookupBitmap: StateFlow<Bitmap?> = _lookupBitmap.asStateFlow()

    /** 진행 중인 lookup 로드 job. lutId 가 빠르게 바뀌면 이전 로드를 취소한다. */
    private var lookupJob: Job? = null

    /**
     * 편집 프리뷰 "풀 룩" 렌더 결과 — PixelBuffer 경로로 **강도 1.0 고정** 렌더.
     * 표시단([FilmEditPreview])이 원본 위에 알파(=강도)로 합성해 강도 드래그를 실시간 반영한다
     * (LUT 셰이더의 mix 와 동일 수식). 내보내기는 항상 정확 경로(_filmEdit 실강도)로 렌더된다.
     * VM 소유 → 교체 시 이전 비트맵 회수, [onCleared] 에서도 회수.
     */
    private val _renderedPreview = MutableStateFlow<Bitmap?>(null)
    val renderedPreview: StateFlow<Bitmap?> = _renderedPreview.asStateFlow()

    // ---- 내보내기(Phase 3) ----

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    /** 내보내기 결과 신호. [MESSAGE_OK]/[MESSAGE_FAIL]. 화면이 토스트 표시 후 [clearMessage]. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    // ---- 대상 이미지 ----

    private val _sourcePath = MutableStateFlow<String?>(null)
    val sourcePath: StateFlow<String?> = _sourcePath.asStateFlow()

    /** 세션 내 고정 식별자(경로 + mtime). 썸네일 캐시 키 일부. */
    private val _sourceId = MutableStateFlow<String?>(null)
    val sourceId: StateFlow<String?> = _sourceId.asStateFlow()

    /** 편집 화면(Phase 3)용 프리뷰 비트맵(긴 변 ~1280). VM 소유 → 회수. */
    private val _previewBitmap = MutableStateFlow<Bitmap?>(null)
    val previewBitmap: StateFlow<Bitmap?> = _previewBitmap.asStateFlow()

    /** 프리뷰 표시용 픽셀 해상도(원본 다운스케일 후). 대상사진 바 표기용. */
    private val _previewSize = MutableStateFlow<Pair<Int, Int>?>(null)
    val previewSize: StateFlow<Pair<Int, Int>?> = _previewSize.asStateFlow()

    /** 썸네일 소스 비트맵(긴 변 THUMB_SOURCE_EDGE). VM 소유 → 회수. 그리드 셀이 직접 소비하지 않고 VM 만 사용. */
    private var thumbSource: Bitmap? = null

    /** lutId → 썸네일 비트맵(캐시 소유, 회수 금지). 그리드가 표시만 한다. */
    private val _thumbnails = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    val thumbnails: StateFlow<Map<String, Bitmap>> = _thumbnails.asStateFlow()

    /** 진행 중인 셀별 썸네일 생성 job. 셀이 화면을 벗어나면 [cancelThumbnail] 로 취소. */
    private val thumbnailJobs = mutableMapOf<String, Job>()

    private var gpuInitialized = false

    init {
        viewModelScope.launch {
            try {
                _availableLuts.value = filmLutUseCase.getAvailableLuts()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogcatManager.e(TAG, "LUT 목록 로드 실패", e)
            }
        }
        initializeGpu()

        // 편집 프리뷰 렌더: previewBitmap·lookup·조정(디바운스) 변화 시 PixelBuffer 경로로
        // **강도 1.0 고정** "풀 룩" 1장을 렌더한다. 강도는 GPU 재렌더 없이 표시단에서
        // 원본 위 알파(=강도) 합성으로 반영된다([FilmEditPreview]) — LUT 셰이더의
        // mix(original, lut, intensity)와 동일 수식이라 드래그가 실시간으로 보인다.
        // (조정 8종이 비중립이면 합성 순서 차이로 드래그 중 근사이며, 내보내기는 항상
        //  정확 경로(_filmEdit 실강도)로 렌더된다. 조정 기본값=중립에서는 완전 일치.)
        // 강도만 바뀔 땐 distinctUntilChanged 로 렌더를 아예 건너뛴다(저사양 GPU 절약).
        viewModelScope.launch {
            combine(_previewBitmap, _lookupBitmap, previewEdit) { src, lookup, edit ->
                Triple(src, lookup, edit.adjustments)
            }.distinctUntilChanged()
                .collectLatest { (src, lookup, adjustments) ->
                    val out = if (src == null || src.isRecycled) {
                        null
                    } else {
                        runCatching {
                            filmEditProcessor.renderPreview(src, lookup, 1f, adjustments) as? Bitmap
                        }.getOrNull()
                    }
                    setRenderedPreview(out)
                }
        }
    }

    /** 렌더 결과 교체 + 이전 결과 비트맵 회수(VM 소유). */
    private fun setRenderedPreview(bmp: Bitmap?) {
        val old = _renderedPreview.value
        _renderedPreview.value = bmp
        if (old != null && old !== bmp && old !== _previewBitmap.value && !old.isRecycled) {
            old.recycle()
        }
    }

    // ---- 필터 조작 ----

    fun setCategory(category: String) {
        _categoryFilter.value = category
    }

    fun setSearch(query: String) {
        _searchQuery.value = query
    }

    /**
     * 필름 LUT 을 선택한다. [selectedLutId] 와 [filmEdit].lutId 를 갱신하고 룩업 비트맵을 로드한다.
     *
     * **티어 게이트 미적용** — 화면 진입 경로(컨택트 시트 셀 탭·편집 스트립)는 반드시 [selectLutGated] 를
     * 사용한다. 이 함수는 게이트를 통과한 선택의 반영과 내부·테스트 용도로만 직접 호출한다.
     */
    fun selectLut(lutId: String) {
        if (_selectedLutId.value == lutId && _lookupBitmap.value != null) return
        _selectedLutId.value = lutId
        _filmEdit.value = _filmEdit.value.copy(lutId = lutId)
        loadLookup(lutId)
    }

    /**
     * 티어 게이트를 통과한 선택만 반영한다. 화면 진입 경로(컨택트 시트 셀 탭·편집 스트립)가 사용한다.
     *
     * 허용 시 [selectLut] 로 반영하고 [lutSelectionAccepted] 를 방출한다. 잠긴 LUT 면 상태를 바꾸지 않고
     * [lutLockNotice] 만 방출한다. 티어는 `.value` 스냅샷이 아니라 [effectiveTier].first() 로 읽는다(H2).
     */
    fun selectLutGated(lutId: String) {
        viewModelScope.launch {
            val tier = effectiveTier.first()
            if (validateFeatureAccessUseCase.isFilmLutAllowed(tier, lutId)) {
                selectLut(lutId)
                // 에디터에서 마지막으로 고른 필름 = 수신 자동 적용 기본값으로 영속
                // (2026-07-07 제품 결정 — 설정의 '기본 필름 선택'을 거치지 않아도 기억).
                // 게이트를 통과한 lutId 만 도달하므로 티어 재검증 불필요.
                try {
                    appSettingsRepository.setSelectedFilmLutId(lutId)
                } catch (e: Exception) {
                    LogcatManager.w(TAG, "마지막 사용 필름 저장 실패(선택 동작에는 영향 없음)", e)
                }
                _lutSelectionAccepted.tryEmit(lutId)
            } else {
                _lutLockNotice.tryEmit(Unit)
            }
        }
    }

    /** [lutId] 의 512×512 룩업을 로드해 [lookupBitmap] 에 노출한다(캐시 소유). 비면 null. */
    private fun loadLookup(lutId: String) {
        lookupJob?.cancel()
        lookupJob = viewModelScope.launch {
            try {
                _lookupBitmap.value = if (lutId.isBlank()) {
                    null
                } else {
                    filmLutUseCase.loadLookupBitmap(lutId) as? Bitmap
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogcatManager.e(TAG, "룩업 비트맵 로드 실패: $lutId", e)
                _lookupBitmap.value = null
            }
        }
    }

    // ---- 편집 조작(Phase 3) ----

    /** LUT 강도 0..1 로 clamp 후 [filmEdit] 갱신. */
    fun setIntensity(value: Float) {
        _filmEdit.value = _filmEdit.value.copy(intensity = value.coerceIn(0f, 1f))
    }

    /** 조정 8종을 필드별로 갱신한다. 전달된 필드만 변경하고 [FilmAdjustments] 범위로 clamp 한다. */
    fun setAdjustment(
        exposure: Float? = null,
        temperature: Float? = null,
        contrast: Float? = null,
        shadows: Float? = null,
        highlights: Float? = null,
        saturation: Float? = null,
        grain: Float? = null,
        chromaticAberration: Float? = null
    ) {
        val cur = _filmEdit.value.adjustments
        val next = cur.copy(
            exposure = exposure ?: cur.exposure,
            temperature = temperature ?: cur.temperature,
            contrast = contrast ?: cur.contrast,
            shadows = shadows ?: cur.shadows,
            highlights = highlights ?: cur.highlights,
            saturation = saturation ?: cur.saturation,
            grain = grain ?: cur.grain,
            chromaticAberration = chromaticAberration ?: cur.chromaticAberration
        ).normalized()
        _filmEdit.value = _filmEdit.value.copy(adjustments = next)
    }

    /** 조정 8종을 전부 중립(0)으로 복귀시킨다. LUT/강도는 유지. */
    fun resetAdjustments() {
        _filmEdit.value = _filmEdit.value.copy(adjustments = FilmAdjustments())
    }

    fun toggleFavorite(id: String) {
        viewModelScope.launch {
            try {
                filmFavoritesUseCase.toggle(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogcatManager.e(TAG, "즐겨찾기 토글 실패: $id", e)
            }
        }
    }

    // ---- 대상 이미지 ----

    /**
     * 대상 이미지를 설정한다. 프리뷰(긴 변 ~[MAX_PREVIEW_EDGE])와 썸네일 소스(긴 변 ~[THUMB_SOURCE_EDGE])
     * 를 1회 디코딩해 VM 이 소유한다. 소스가 바뀌면 이전 비트맵·썸네일 캐시를 회수/무효화한다.
     */
    fun setSourceImage(path: String) {
        _sourcePath.value = path
        viewModelScope.launch {
            val mtime = runCatching { File(path).lastModified() }.getOrDefault(0L)
            val newSourceId = "$path@$mtime"

            val (preview, previewDim, thumb) = withContext(ioDispatcher) {
                val preview = decodeDownscaled(path, MAX_PREVIEW_EDGE)
                val dim = preview?.let { it.width to it.height }
                val thumb = decodeDownscaled(path, THUMB_SOURCE_EDGE)
                Triple(preview, dim, thumb)
            }

            // 새 값을 먼저 반영(렌더 플로우가 새 src 로 전환되며 collectLatest 가 옛 렌더를 취소)한 뒤
            // 옛 소유 비트맵을 회수한다(렌더 in-flight 중 src 회수 경합 완화). 썸네일 캐시/job 도 무효화.
            val oldPreview = _previewBitmap.value
            val oldThumb = thumbSource
            cancelAllThumbnailJobs()
            filmEditProcessor.clearThumbnails()
            _thumbnails.value = emptyMap()
            _previewBitmap.value = preview
            _previewSize.value = previewDim
            thumbSource = thumb
            _sourceId.value = newSourceId
            if (oldPreview != null && oldPreview !== preview && !oldPreview.isRecycled) oldPreview.recycle()
            if (oldThumb != null && oldThumb !== thumb && !oldThumb.isRecycled) oldThumb.recycle()
        }
    }

    // ---- 지연 썸네일 ----

    /**
     * [lutId] 썸네일을 지연 생성한다. 그리드 셀이 화면에 진입할 때 호출한다.
     * 이미 있거나 진행 중이거나 소스 비트맵이 없으면 무시한다(취소 협조: [cancelThumbnail]).
     */
    fun requestThumbnail(lutId: String) {
        val source = thumbSource ?: return
        val sid = _sourceId.value ?: return
        if (_thumbnails.value.containsKey(lutId)) return
        if (thumbnailJobs.containsKey(lutId)) return

        val job = viewModelScope.launch {
            try {
                val bmp = filmEditProcessor.generateThumbnail(sid, source, lutId) as? Bitmap
                if (bmp != null && !bmp.isRecycled) {
                    _thumbnails.value = _thumbnails.value + (lutId to bmp)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogcatManager.w(TAG, "썸네일 생성 실패: $lutId")
            } finally {
                thumbnailJobs.remove(lutId)
            }
        }
        thumbnailJobs[lutId] = job
    }

    /** 셀이 화면을 벗어나면 호출해 불필요한 썸네일 생성을 중단한다. */
    fun cancelThumbnail(lutId: String) {
        thumbnailJobs.remove(lutId)?.cancel()
    }

    // ---- 내보내기(Phase 3) ----

    /**
     * 현재 [filmEdit](LUT + 강도 + 조정 8종)을 [sourcePath] 원본에 풀해상도 적용해 갤러리
     * (`Pictures/CamCon`)에 저장한다. 적용은 [FilmAdjustmentProcessor] 단일 빌더(프리뷰와 동일 결과),
     * 저장은 MediaStore(IS_PENDING) → 실패 시 외부 파일 폴백.
     */
    fun export() {
        val path = _sourcePath.value
        if (path == null) {
            _message.value = MESSAGE_FAIL
            return
        }
        if (_isExporting.value) return
        viewModelScope.launch {
            _isExporting.value = true
            try {
                val tmp = filmLutUseCase.applyEditToTemp(path, _filmEdit.value, 0)
                if (tmp == null) {
                    _message.value = MESSAGE_FAIL
                    return@launch
                }
                val tmpFile = File(tmp)
                val saved = withContext(ioDispatcher) { saveToGallery(tmpFile) }
                _message.value = if (saved) MESSAGE_OK else MESSAGE_FAIL
                if (saved) {
                    // 내보내기 = 이 강도를 '실제로 사용' 확정한 시점 — 자동 적용 기본 강도로 기억.
                    try {
                        appSettingsRepository.setFilmSimulationIntensity(_filmEdit.value.intensity)
                    } catch (e: Exception) {
                        LogcatManager.w(TAG, "마지막 사용 강도 저장 실패(내보내기에는 영향 없음)", e)
                    }
                }
                if (tmpFile.exists()) tmpFile.delete()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogcatManager.e(TAG, "필름 편집 내보내기 실패", e)
                _message.value = MESSAGE_FAIL
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    /** 결과 임시 파일을 MediaStore(Pictures/CamCon)에 저장한다. 실패 시 외부 파일 폴백. */
    private fun saveToGallery(source: File): Boolean {
        val displayName = "camcon_film_${System.currentTimeMillis()}.jpg"
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/CamCon"
                )
                // IS_PENDING 으로 기록 완료 전까지 갤러리/타 앱 노출·고아 엔트리를 막는다(repo 공통 패턴).
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            // 제거식 SD카드가 있으면 SD카드에 우선 저장, 없거나 탈거 경합이면 primary 볼륨으로 폴백.
            val preferredUri = MediaStoreVolumes.preferredImagesUri(context)
            // EXTERNAL_CONTENT_URI(집계 external)와는 볼륨명 표기가 달라 == 비교가 성립하지 않으므로 표기 통일.
            val primaryUri =
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            var uri = resolver.insert(preferredUri, values)
            if (uri == null && preferredUri != primaryUri) {
                LogcatManager.w(TAG, "선호 볼륨 저장 실패 — primary 볼륨으로 재시도")
                uri = resolver.insert(primaryUri, values)
            }
            if (uri == null) {
                saveToExternalFiles(source)
            } else {
                val written = resolver.openOutputStream(uri)?.use { output ->
                    source.inputStream().use { input -> input.copyTo(output) }
                    true
                } ?: false
                if (!written) {
                    resolver.delete(uri, null, null)
                    return saveToExternalFiles(source)
                }
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                    null,
                    null
                )
                true
            }
        } catch (e: Exception) {
            LogcatManager.e(TAG, "MediaStore 저장 실패, 외부 파일 폴백", e)
            saveToExternalFiles(source)
        }
    }

    private fun saveToExternalFiles(source: File): Boolean {
        return try {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return false
            if (!dir.exists()) dir.mkdirs()
            val target = File(dir, "camcon_film_${System.currentTimeMillis()}.jpg")
            source.inputStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            true
        } catch (e: Exception) {
            LogcatManager.e(TAG, "외부 파일 저장 실패", e)
            false
        }
    }

    // ---- GPU 생명주기 ----

    private fun initializeGpu() {
        if (gpuInitialized) return
        try {
            filmLutUseCase.initializeGPU(context)
            gpuInitialized = true
        } catch (e: Exception) {
            LogcatManager.e(TAG, "GPU 초기화 실패", e)
        }
    }

    // ---- 내부 디코딩 ----

    private fun decodeDownscaled(path: String, maxEdge: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
            if (longEdge <= 0) return null
            var sample = 1
            if (longEdge > maxEdge) {
                while (longEdge / (sample * 2) >= maxEdge) {
                    sample *= 2
                }
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeFile(path, options) ?: return null
            // EXIF orientation 적용(세로 사진 눕힘 방지). 회전 시 입력 비트맵은 recycle 되고 새 비트맵 반환.
            // export 경로는 픽셀 미회전 + orientation 태그 보존이므로, 프리뷰만 픽셀 회전해도 최종 표시 일치.
            ImageProcessingUtils.applyExifOrientationFromFile(decoded, path)
        } catch (e: OutOfMemoryError) {
            LogcatManager.w(TAG, "대상 이미지 디코딩 OOM(maxEdge=$maxEdge)")
            null
        } catch (e: Exception) {
            LogcatManager.e(TAG, "대상 이미지 디코딩 실패", e)
            null
        }
    }

    private fun cancelAllThumbnailJobs() {
        thumbnailJobs.values.forEach { it.cancel() }
        thumbnailJobs.clear()
    }

    private fun recyclePreview() {
        _previewBitmap.value?.let { if (!it.isRecycled) it.recycle() }
        _previewBitmap.value = null
        _previewSize.value = null
    }

    private fun recycleThumbSource() {
        thumbSource?.let { if (!it.isRecycled) it.recycle() }
        thumbSource = null
    }

    override fun onCleared() {
        super.onCleared()
        cancelAllThumbnailJobs()
        lookupJob?.cancel()
        setRenderedPreview(null)
        recyclePreview()
        recycleThumbSource()
        // 썸네일 결과 비트맵·lookupBitmap 은 생성기/카탈로그 캐시 소유 → VM 이 회수하지 않는다(참조만 해제).
        // releaseGpu()는 앱 전역 싱글톤 GPUImage/EGL 을 파괴(자동적용 경로·CamCon.kt 와 공유)하므로
        // ViewModel 소멸 시 호출 금지. GPU 싱글톤은 앱 수명 동안 유지하고, 썸네일 LRU 만 비운다.
        _lookupBitmap.value = null
        _thumbnails.value = emptyMap()
        filmEditProcessor.clearThumbnails()
    }

    companion object {
        private const val TAG = "FilmEditorViewModel"
        private const val MAX_PREVIEW_EDGE = 1280

        /**
         * 컨택트 시트 썸네일 소스의 긴 변(px).
         * 200이었을 때 2열 그리드 타일(태블릿 실측 ~580px)에 3배 가까이 확대되어 뿌옇게 보였다.
         * 512 = 대부분 기기에서 타일 실픽셀에 근접(화질), CPU LUT·메모리 비용은
         * FilmThumbnailGenerator의 동시성 2 제한 + 바이트 예산 캐시가 흡수한다.
         */
        private const val THUMB_SOURCE_EDGE = 512

        /** 슬라이더 드래그 → 프리뷰 GPU 재구성 디바운스(ms). 설계 §8: 과다 갱신 방지. */
        private const val PREVIEW_DEBOUNCE_MS = 200L

        /** 가상 카테고리 칩: 전체. */
        const val CATEGORY_ALL = "__all__"

        /** 가상 카테고리 칩: 즐겨찾기. */
        const val CATEGORY_FAVORITES = "__favorites__"

        /** 내보내기 성공 신호([message]). */
        const val MESSAGE_OK = "ok"

        /** 내보내기 실패 신호([message]). */
        const val MESSAGE_FAIL = "fail"
    }
}
