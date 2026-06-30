package com.inik.camcon.domain.repository

import com.inik.camcon.domain.model.FilmAdjustments

/**
 * 필름 편집 프리뷰 렌더 + 컨택트 시트 썸네일 생성의 도메인 포트.
 *
 * Bitmap 등 Android 타입은 [FilmLutRepository.loadLookupBitmap] 선례대로 `Any` 로 우회한다
 * (도메인 레이어를 android.graphics 비의존으로 유지 — 멀티모듈 분할 대비).
 * GPU/EGL 수명(releaseGpu)은 의도적으로 노출하지 않는다 — 전역 싱글톤 파괴 위험(CamCon.kt 전용).
 */
interface FilmEditProcessor {

    /**
     * 입력에 LUT + 강도 + 조정 8종을 1회 적용한 프리뷰 결과를 반환한다.
     * @return 결과(실제 타입 Bitmap). GPU 미초기화/실패 시 null.
     */
    suspend fun renderPreview(
        inputBitmap: Any,        // Bitmap
        lookupBitmap: Any?,      // Bitmap?
        intensity: Float,
        adjustments: FilmAdjustments
    ): Any?                      // Bitmap?

    /**
     * [sourceId]/[thumbSource](둘 다 Bitmap)에 [lutId] LUT 를 적용한 썸네일을 반환한다.
     * @return 썸네일(캐시 소유 — 회수 금지). 실패 시 null.
     */
    suspend fun generateThumbnail(
        sourceId: String,
        thumbSource: Any,        // Bitmap
        lutId: String
    ): Any?                      // Bitmap?

    /** 썸네일 LRU 캐시를 비운다(소스 교체/세션 종료). */
    fun clearThumbnails()
}
