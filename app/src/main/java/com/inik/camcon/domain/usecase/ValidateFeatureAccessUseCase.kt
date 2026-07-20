package com.inik.camcon.domain.usecase

import com.inik.camcon.domain.model.SubscriptionTier
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 파이프라인 후처리 기능(필름 시뮬레이션 / 색감 전송)을 구분하는 축.
 */
enum class PipelineFeature { FILM_SIMULATION, COLOR_TRANSFER }

/**
 * 배타 토글 판정 결과.
 *  - [Allow] : 그대로 켜도 됨(다른 쪽 유지).
 *  - [Swap]  : [disable] 쪽을 끄고 켜야 함(비허용 티어의 자동 스왑).
 */
sealed interface ToggleDecision {
    object Allow : ToggleDecision
    data class Swap(val disable: PipelineFeature) : ToggleDecision
}

/**
 * 현재 유효한 파이프라인 상태.
 * [needsReconcile] 가 true 이면 저장된 플래그가 비허용 티어에서 '둘 다 ON' 이므로 정합화가 필요하다.
 */
data class ActivePipeline(
    val filmEnabled: Boolean,
    val colorEnabled: Boolean,
    val needsReconcile: Boolean
)

/**
 * 구독 티어 기반 기능 접근 판정 단일 지점(순수 함수).
 *
 * RAW/포맷 축은 [ValidateImageFormatUseCase] 단일 지점이 담당한다(CLAUDE.md §2). 본 UseCase 는
 * 그와 별개로 '미리보기 탭 접근'과 '필름/색감 동시 사용' 두 기능 축만 판정하며, I/O 없이 순수
 * boolean 만 다루므로 메인/JNI/임의 스레드 어디서든 안전하다.
 */
@Singleton
class ValidateFeatureAccessUseCase @Inject constructor() {

    companion object {
        /**
         * 무료(전 티어) 시그니처 필름 LUT id (= FilmLut.id = film_luts.json lut_file).
         * 교체는 이 목록 한 곳만 수정한다. 항목 실존은 FreeFilmLutIdsCatalogTest 가 검증한다.
         */
        val FREE_FILM_LUT_IDS: Set<String> = setOf(
            "luts/negative_new/kodak_portra_400.cube",              // 컬러 네거티브 대표
            "luts/colorslide/fuji_velvia_50.cube",                 // 슬라이드(풍경) 대표
            "luts/bw/kodak_tri-x_400.cube",                        // 흑백 대표
            "luts/fujixtransiii/fuji_xtrans_iii_classic_chrome.cube", // 디지털 필름심 대표
            "luts/instant_consumer/polaroid_px-680.cube"           // 인스턴트 대표
        )
    }

    /** 미리보기 탭 접근 허용 티어 — [ValidateImageFormatUseCase.isRawAllowedForTier] 와 동일 집합. */
    fun isPhotoPreviewAllowed(tier: SubscriptionTier): Boolean =
        tier == SubscriptionTier.PRO ||
                tier == SubscriptionTier.REFERRER ||
                tier == SubscriptionTier.ADMIN

    /** 전체 LUT 카탈로그 사용 허용 티어 — [isPhotoPreviewAllowed] 와 동일 집합(PRO/REFERRER/ADMIN). */
    fun isFullLutCatalogAllowed(tier: SubscriptionTier): Boolean =
        tier == SubscriptionTier.PRO ||
                tier == SubscriptionTier.REFERRER ||
                tier == SubscriptionTier.ADMIN

    /** 해당 LUT 사용 가능 여부. 빈 id(선택 없음)는 항상 true. */
    fun isFilmLutAllowed(tier: SubscriptionTier, lutId: String): Boolean =
        lutId.isEmpty() || isFullLutCatalogAllowed(tier) || lutId in FREE_FILM_LUT_IDS

    /**
     * 수신 자동 적용 폴백: 잠긴 LUT 면 "" 반환(→ 기존 isNotEmpty 가드가 필름 스텝을 스킵).
     * 마스킹만 하고 선택 id 를 영속화하지 않는다 — 재업그레이드 시 선택이 그대로 살아난다.
     */
    fun resolveEffectiveLutId(tier: SubscriptionTier, selectedLutId: String): String =
        if (isFilmLutAllowed(tier, selectedLutId)) selectedLutId else ""

    /**
     * 라이브뷰 표시 허용 티어 — ADMIN 전용.
     * 기능 게이팅 단일 지점 규약에 따라 티어 판정을 이 UseCase 로 모은다(UI/ViewModel 직접 분기 금지).
     */
    fun isLiveViewAllowed(tier: SubscriptionTier): Boolean =
        tier == SubscriptionTier.ADMIN

    /** 필름·색감 동시 사용(듀얼 파이프라인) 허용 티어. */
    fun isDualPipelineAllowed(tier: SubscriptionTier): Boolean =
        tier == SubscriptionTier.PRO ||
                tier == SubscriptionTier.REFERRER ||
                tier == SubscriptionTier.ADMIN

    /**
     * 켜려는 쪽이 [enabling]. FREE/BASIC 이고 다른 쪽이 이미 ON 이면 그쪽을 끄고 켠다(자동 스왑).
     * 허용 티어이거나 다른 쪽이 OFF 면 그대로 켠다.
     */
    fun resolveExclusiveToggle(
        tier: SubscriptionTier,
        enabling: PipelineFeature,
        otherEnabled: Boolean
    ): ToggleDecision =
        if (!otherEnabled || isDualPipelineAllowed(tier)) {
            ToggleDecision.Allow
        } else {
            ToggleDecision.Swap(
                disable = if (enabling == PipelineFeature.FILM_SIMULATION) {
                    PipelineFeature.COLOR_TRANSFER
                } else {
                    PipelineFeature.FILM_SIMULATION
                }
            )
        }

    /**
     * 둘 다 ON + 비허용 티어 → 필름 유지·색감 OFF + 정합화 필요 표시. 그 외 입력 그대로.
     * (색감 전송은 참조 이미지 미설정 시 no-op 이고 필름 LUT 가 주 look 수단이라 필름을 유지한다.)
     */
    fun resolveActivePipeline(
        tier: SubscriptionTier,
        filmOn: Boolean,
        colorOn: Boolean
    ): ActivePipeline =
        if (filmOn && colorOn && !isDualPipelineAllowed(tier)) {
            ActivePipeline(filmEnabled = true, colorEnabled = false, needsReconcile = true)
        } else {
            ActivePipeline(filmOn, colorOn, false)
        }
}
