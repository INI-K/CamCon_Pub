package com.inik.camcon.domain.usecase

import com.inik.camcon.domain.model.FilmEdit
import com.inik.camcon.domain.model.FilmLut
import com.inik.camcon.domain.model.FilmLutResult
import com.inik.camcon.domain.repository.FilmLutRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 필름 시뮬레이션 로직을 Repository 에 위임하는 얇은 UseCase 래퍼.
 * 도메인 레이어는 Android 의존성을 갖지 않는다([ColorTransferUseCase] 와 동일 패턴).
 */
@Singleton
class FilmLutUseCase @Inject constructor(
    private val filmLutRepository: FilmLutRepository
) {

    suspend fun getAvailableLuts(): List<FilmLut> =
        filmLutRepository.getAvailableLuts()

    suspend fun applyFilmLutAndSave(
        inputImagePath: String,
        lutId: String,
        originalImagePath: String,
        outputPath: String,
        intensity: Float
    ): FilmLutResult? =
        filmLutRepository.applyFilmLutAndSave(
            inputImagePath, lutId, originalImagePath, outputPath, intensity
        )

    suspend fun applyFilmLut(
        inputImagePath: String,
        lutId: String,
        intensity: Float,
        maxSize: Int = 0
    ): String? =
        filmLutRepository.applyFilmLut(inputImagePath, lutId, intensity, maxSize)

    suspend fun loadLookupBitmap(lutId: String): Any? =
        filmLutRepository.loadLookupBitmap(lutId)

    suspend fun applyEditAndSave(
        inputImagePath: String,
        edit: FilmEdit,
        originalImagePath: String,
        outputPath: String
    ): FilmLutResult? =
        filmLutRepository.applyEditAndSave(inputImagePath, edit, originalImagePath, outputPath)

    suspend fun applyEditToTemp(
        inputImagePath: String,
        edit: FilmEdit,
        maxSize: Int = 0
    ): String? =
        filmLutRepository.applyEditToTemp(inputImagePath, edit, maxSize)

    fun isValidImageFile(imagePath: String): Boolean =
        filmLutRepository.isValidImageFile(imagePath)

    fun initializeGPU(contextProvider: Any) =
        filmLutRepository.initializeGPU(contextProvider)

    fun releaseGpu() =
        filmLutRepository.releaseGpu()
}
