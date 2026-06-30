package com.inik.camcon.data.processor

import android.graphics.Bitmap
import com.inik.camcon.domain.model.FilmAdjustments
import com.inik.camcon.domain.repository.FilmEditProcessor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [FilmEditProcessor] 도메인 포트의 data 구현. GPU 처리 디테일([FilmAdjustmentProcessor],
 * [FilmThumbnailGenerator])을 도메인 인터페이스 뒤로 감춰 presentation→data.processor
 * 컴파일 의존을 끊는다. `Any` ↔ Bitmap 변환은 이 경계에서만 일어난다.
 */
@Singleton
class FilmEditProcessorImpl @Inject constructor(
    private val adjustmentProcessor: FilmAdjustmentProcessor,
    private val thumbnailGenerator: FilmThumbnailGenerator
) : FilmEditProcessor {

    override suspend fun renderPreview(
        inputBitmap: Any,
        lookupBitmap: Any?,
        intensity: Float,
        adjustments: FilmAdjustments
    ): Any? = adjustmentProcessor.apply(
        inputBitmap as Bitmap,
        lookupBitmap as? Bitmap,
        intensity,
        adjustments
    )

    override suspend fun generateThumbnail(
        sourceId: String,
        thumbSource: Any,
        lutId: String
    ): Any? = thumbnailGenerator.generate(sourceId, thumbSource as Bitmap, lutId)

    override fun clearThumbnails() = thumbnailGenerator.clear()
}
