package com.inik.camcon.domain.repository

import com.inik.camcon.domain.model.FilmLut
import com.inik.camcon.domain.model.FilmLutResult

/**
 * 필름 시뮬레이션(3D LUT) Repository 인터페이스.
 * 색감전송([ColorTransferRepository])과 동일하게 원시/도메인 타입만 노출한다.
 * GPU 초기화·라이브 프리뷰 룩업처럼 Android 타입이 불가피한 지점은 `Any` 로 우회한다.
 */
interface FilmLutRepository {

    /** 번들된 전체 필름 LUT 카탈로그. */
    suspend fun getAvailableLuts(): List<FilmLut>

    /**
     * 입력 이미지에 필름 LUT 를 적용하고 EXIF 를 보존해 outputPath 에 저장한다(자동 적용·편집 내보내기 공용).
     * @return 결과 정보, 실패 시 null.
     */
    suspend fun applyFilmLutAndSave(
        inputImagePath: String,
        lutId: String,
        originalImagePath: String,
        outputPath: String,
        intensity: Float
    ): FilmLutResult?

    /**
     * 입력 이미지에 필름 LUT 를 적용한 결과를 임시 파일로 저장한다.
     * @param maxSize 입력을 스케일할 최대 긴 변 px(0 = 스케일 안 함).
     * @return 임시 파일 경로, 실패 시 null.
     */
    suspend fun applyFilmLut(
        inputImagePath: String,
        lutId: String,
        intensity: Float,
        maxSize: Int = 0
    ): String?

    /**
     * 라이브 프리뷰용 512×512 룩업 비트맵을 반환한다.
     * 도메인 레이어가 Android(Bitmap) 의존성을 갖지 않도록 `Any` 로 선언한다(실제 타입 Bitmap).
     */
    suspend fun loadLookupBitmap(lutId: String): Any?

    /** 주어진 경로가 유효한 이미지인지 검증한다. */
    fun isValidImageFile(imagePath: String): Boolean

    /** GPU 처리를 초기화한다(contextProvider 는 Android Context). */
    fun initializeGPU(contextProvider: Any)

    /** GPU/EGL 리소스를 해제한다(앱 종료 시점에만). */
    fun releaseGpu()
}
