package com.inik.camcon.presentation.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.di.IoDispatcher
import com.inik.camcon.domain.model.SubscriptionProduct
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.repository.FilmEditProcessor
import com.inik.camcon.domain.usecase.FilmLutUseCase
import com.inik.camcon.domain.usecase.GetSubscriptionUseCase
import com.inik.camcon.domain.usecase.PurchaseSubscriptionUseCase
import com.inik.camcon.domain.usecase.ValidateFeatureAccessUseCase
import com.inik.camcon.utils.LogcatManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 구독 페이월 화면 상태.
 *
 * - [currentTier]: 현재 사용자 티어. 게이팅 단일 진실원천인 [GetSubscriptionUseCase] StateFlow를 구독해 반영.
 * - [products]: Google Play Billing에서 조회한 상품 목록. 조회 실패/빈 결과면 비어 있다.
 * - [billingUnavailable]: Billing 상품 조회가 빈 목록을 돌려준 경우(개발 빌드, Play Console 미등록 등).
 *   true면 화면은 티어 enum 기반 정적 카탈로그로 폴백한다.
 * - [purchaseInProgress]: 결제 시트 호출~결과 반영 사이 진행 상태.
 * - [purchaseSuccess]: 결제 시트가 정상 호출되었음(실제 권한 부여는 서버 검증 후 Custom Claims로 반영).
 */
data class SubscriptionUiState(
    val currentTier: SubscriptionTier = SubscriptionTier.FREE,
    val products: List<SubscriptionProduct> = emptyList(),
    val isLoading: Boolean = true,
    val billingUnavailable: Boolean = false,
    val purchaseInProgress: Boolean = false,
    val purchaseSuccess: Boolean = false,
    val error: String? = null
)

/**
 * 페이월 PRO 카드에 시각 증거로 노출하는 필름 룩 썸네일 1건.
 *
 * @param lutId 필름 LUT id.
 * @param name 표시 이름.
 * @param thumbnail 샘플 이미지에 LUT 을 적용한 썸네일(생성기 캐시 소유 → 회수 금지). 생성 전/실패 시 null.
 * @param locked FREE 시그니처 5종 밖(PRO 전용)이면 true — 자물쇠 오버레이 표시.
 */
data class FilmLookSample(
    val lutId: String,
    val name: String,
    val thumbnail: Bitmap?,
    val locked: Boolean
)

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val getSubscriptionUseCase: GetSubscriptionUseCase,
    private val purchaseSubscriptionUseCase: PurchaseSubscriptionUseCase,
    private val filmLutUseCase: FilmLutUseCase,
    private val filmEditProcessor: FilmEditProcessor,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubscriptionUiState())
    val uiState: StateFlow<SubscriptionUiState> = _uiState.asStateFlow()

    /** PRO 카드 필름 룩 스트립. 썸네일이 도착하는 대로 개별 항목이 채워진다. */
    private val _filmLookSamples = MutableStateFlow<List<FilmLookSample>>(emptyList())
    val filmLookSamples: StateFlow<List<FilmLookSample>> = _filmLookSamples.asStateFlow()

    /** 전체 필름 룩 카탈로그 규모("N가지 필름 룩" 카운터). 하드코딩 금지 — 카탈로그 size 그대로. */
    private val _filmLookCount = MutableStateFlow(0)
    val filmLookCount: StateFlow<Int> = _filmLookCount.asStateFlow()

    /** 썸네일 소스(번들 샘플 다운스케일). VM 소유 → [onCleared] 에서 회수. */
    private var sampleSource: Bitmap? = null

    init {
        observeCurrentTier()
        loadProducts()
        loadFilmLookPreview()
    }

    /**
     * 게이팅 단일 진실원천인 [GetSubscriptionUseCase] StateFlow를 구독해 현재 티어를 반영한다.
     * 결제 성공 시 서버 검증 → Custom Claims 갱신 → 이 Flow로 티어 변경이 전파된다.
     */
    private fun observeCurrentTier() {
        viewModelScope.launch {
            getSubscriptionUseCase.getSubscriptionTier().collect { tier ->
                _uiState.update { it.copy(currentTier = tier) }
            }
        }
    }

    private fun loadProducts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val products = withContext(ioDispatcher) {
                    purchaseSubscriptionUseCase.getAvailableSubscriptions()
                }
                _uiState.update {
                    it.copy(
                        products = products,
                        billingUnavailable = products.isEmpty(),
                        isLoading = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        products = emptyList(),
                        billingUnavailable = true,
                        isLoading = false,
                        error = e.localizedMessage
                    )
                }
            }
        }
    }

    /**
     * 결제 시트를 호출한다. 반환 Boolean은 "결제 시트가 정상적으로 떠올랐는지"이며,
     * 실제 권한 부여는 서버(Cloud Function)의 결제 검증 + Custom Claims 갱신 후
     * [observeCurrentTier]를 통해 티어로 반영된다.
     */
    fun purchase(productId: String) {
        if (_uiState.value.purchaseInProgress) return
        viewModelScope.launch {
            _uiState.update { it.copy(purchaseInProgress = true, error = null, purchaseSuccess = false) }
            try {
                val launched = withContext(ioDispatcher) {
                    purchaseSubscriptionUseCase.purchaseSubscription(productId)
                }
                _uiState.update {
                    it.copy(
                        purchaseInProgress = false,
                        purchaseSuccess = launched,
                        error = if (launched) null else it.error
                    )
                }
                if (launched) {
                    // 결제 시트가 떠오른 뒤 활성 구독을 서버 동기화·재조회한다(restore 와 동일 패턴).
                    // 구매 완료의 주 반영 경로는 BillingDataSource 업데이트 → 서버 검증 → refresh 신호이며,
                    // 이 호출은 이미 완료/복원 가능한 구매를 즉시 포착하는 보조 경로다.
                    getSubscriptionUseCase.refreshSubscription(forceSync = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        purchaseInProgress = false,
                        purchaseSuccess = false,
                        error = e.localizedMessage
                    )
                }
            }
        }
    }

    /** Play Store 재설치/디바이스 변경 시 활성 구독 복원. */
    fun restore() {
        if (_uiState.value.purchaseInProgress) return
        viewModelScope.launch {
            _uiState.update { it.copy(purchaseInProgress = true, error = null) }
            try {
                withContext(ioDispatcher) {
                    purchaseSubscriptionUseCase.restoreSubscription()
                }
                getSubscriptionUseCase.refreshSubscription(forceSync = true)
                _uiState.update { it.copy(purchaseInProgress = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(purchaseInProgress = false, error = e.localizedMessage)
                }
            }
        }
    }

    fun consumeError() {
        _uiState.update { it.copy(error = null) }
    }

    fun consumePurchaseSuccess() {
        _uiState.update { it.copy(purchaseSuccess = false) }
    }

    /**
     * PRO 카드용 필름 룩 시각 증거를 만든다. 번들 샘플 이미지에 대표 LUT 을 적용한 작은 썸네일 스트립이다.
     *
     * 재사용 경로: 컨택트 시트와 동일한 [FilmEditProcessor.generateThumbnail]([FilmThumbnailGenerator])을
     * 그대로 쓴다. 이 경로는 CPU 삼선형(applyFilmLutCpu)이라 **GPU/EGL 을 건드리지 않는다** — 페이월을 위해
     * GPU 파이프라인을 새로 세우지 않으며 releaseGpu/initializeGPU 도 호출하지 않는다(전역 EGL 싱글톤 불변).
     * 썸네일은 생성기 LRU(48MB 예산) 소유 → 회수 금지. 소스 비트맵만 VM 소유로 [onCleared] 에서 회수한다.
     *
     * 대표 선정: FREE 시그니처 5종(잠금 없음) + 그 밖 카탈로그 3종(잠금 오버레이) = 최대 8종.
     */
    private fun loadFilmLookPreview() {
        viewModelScope.launch {
            try {
                val luts = filmLutUseCase.getAvailableLuts()
                if (luts.isEmpty()) return@launch
                _filmLookCount.value = luts.size

                val byId = luts.associateBy { it.id }
                val free = ValidateFeatureAccessUseCase.FREE_FILM_LUT_IDS.mapNotNull { byId[it] }
                val locked = luts.asSequence()
                    .filter { it.id !in ValidateFeatureAccessUseCase.FREE_FILM_LUT_IDS }
                    .take(FILM_LOOK_LOCKED_SAMPLES)
                    .toList()
                val picks = (free + locked).take(FILM_LOOK_MAX_SAMPLES)
                if (picks.isEmpty()) return@launch

                // 스켈레톤 상태로 먼저 노출(썸네일 null) → 도착하는 대로 개별 채움.
                _filmLookSamples.value = picks.map { lut ->
                    FilmLookSample(
                        lutId = lut.id,
                        name = lut.name,
                        thumbnail = null,
                        locked = lut.id !in ValidateFeatureAccessUseCase.FREE_FILM_LUT_IDS
                    )
                }

                val source = withContext(ioDispatcher) { decodeSampleSource() } ?: return@launch
                sampleSource = source

                picks.forEach { lut ->
                    val bmp = filmEditProcessor.generateThumbnail(
                        FILM_LOOK_SOURCE_ID, source, lut.id
                    ) as? Bitmap
                    if (bmp != null && !bmp.isRecycled) {
                        _filmLookSamples.update { list ->
                            list.map { if (it.lutId == lut.id) it.copy(thumbnail = bmp) else it }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogcatManager.w(TAG, "페이월 필름 룩 프리뷰 생성 실패", e)
            }
        }
    }

    /** 번들 샘플(assets/[FILM_LOOK_SAMPLE_ASSET])을 썸네일 소스 크기로 다운스케일 디코딩한다. IO 스레드 호출. */
    private fun decodeSampleSource(): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.assets.open(FILM_LOOK_SAMPLE_ASSET).use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        if (longEdge <= 0) return@runCatching null
        var sample = 1
        if (longEdge > FILM_LOOK_SOURCE_EDGE) {
            while (longEdge / (sample * 2) >= FILM_LOOK_SOURCE_EDGE) sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        context.assets.open(FILM_LOOK_SAMPLE_ASSET).use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }.getOrNull()

    override fun onCleared() {
        super.onCleared()
        // 소스 비트맵만 VM 소유 → 회수. 썸네일은 생성기 캐시 소유이므로 참조만 드롭(회수 금지).
        sampleSource?.let { if (!it.isRecycled) it.recycle() }
        sampleSource = null
    }

    companion object {
        private const val TAG = "SubscriptionViewModel"

        /** 페이월 필름 룩 썸네일 소스(번들 샘플 asset). 컨택트 시트 자동 로드와 동일 파일. */
        private const val FILM_LOOK_SAMPLE_ASSET = "film_sample.webp"

        /** 페이월 썸네일 생성 시 생성기 캐시 키(세션 고정). */
        private const val FILM_LOOK_SOURCE_ID = "paywall_film_sample"

        /** 샘플 소스 다운스케일 목표 긴 변(px). 컨택트 시트 THUMB_SOURCE_EDGE 와 동일. */
        private const val FILM_LOOK_SOURCE_EDGE = 512

        /** 페이월 스트립 최대 표본 수. */
        private const val FILM_LOOK_MAX_SAMPLES = 8

        /** 잠금(PRO 전용) 표본 수 — 나머지는 FREE 시그니처 5종. */
        private const val FILM_LOOK_LOCKED_SAMPLES = 3
    }
}
