package com.inik.camcon.domain.usecase

import com.inik.camcon.domain.model.ColorTransferResult
import com.inik.camcon.domain.repository.ColorTransferRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin UseCase wrapper that delegates all color transfer logic to the repository.
 * Domain layer remains free of Android dependencies.
 */
@Singleton
class ColorTransferUseCase @Inject constructor(
    private val colorTransferRepository: ColorTransferRepository
) {

    /**
     * Applies color transfer using GPU acceleration with cached reference stats.
     * @param targetImagePath path to the input image
     * @param referenceImagePath path to the reference image
     * @param intensity color transfer intensity (0.0 ~ 1.0)
     * @param maxSize optional max size to scale the input bitmap (0 = no scaling)
     * @return path to the result image file, or null on failure
     */
    suspend fun applyColorTransferWithGPUCached(
        targetImagePath: String,
        referenceImagePath: String,
        intensity: Float,
        maxSize: Int = 0
    ): String? =
        colorTransferRepository.applyColorTransferWithGPUCached(
            targetImagePath, referenceImagePath, intensity, maxSize
        )

    /**
     * Applies color transfer between two images (CPU path).
     * @param targetImagePath path to the input image
     * @param referenceImagePath path to the reference image
     * @param intensity color transfer intensity (0.0 ~ 1.0)
     * @param maxSize optional max size to scale both bitmaps (0 = no scaling)
     * @return path to the result image file, or null on failure
     */
    suspend fun applyColorTransfer(
        targetImagePath: String,
        referenceImagePath: String,
        intensity: Float,
        maxSize: Int = 0
    ): String? =
        colorTransferRepository.applyColorTransfer(
            targetImagePath, referenceImagePath, intensity, maxSize
        )

    /**
     * Applies color transfer using GPU acceleration.
     * @param inputImagePath path to the input image
     * @param referenceImagePath path to the reference image
     * @param intensity color transfer intensity (0.0 ~ 1.0)
     * @return path to the result image file, or null on failure
     */
    suspend fun applyColorTransferWithGPU(
        inputImagePath: String,
        referenceImagePath: String,
        intensity: Float
    ): String? =
        colorTransferRepository.applyColorTransferWithGPU(inputImagePath, referenceImagePath, intensity)

    /**
     * Applies color transfer and saves result with EXIF metadata preservation.
     */
    suspend fun applyColorTransferAndSave(
        inputImagePath: String,
        referenceImagePath: String,
        originalImagePath: String,
        outputPath: String,
        intensity: Float = 0.03f
    ): ColorTransferResult? =
        colorTransferRepository.applyColorTransferAndSave(
            inputImagePath, referenceImagePath, originalImagePath, outputPath, intensity
        )

    /**
     * Validates whether the file at the given path is a valid image.
     */
    fun isValidImageFile(imagePath: String): Boolean =
        colorTransferRepository.isValidImageFile(imagePath)

    /**
     * Clears the cached reference image statistics.
     */
    fun clearReferenceCache() =
        colorTransferRepository.clearReferenceCache()

    /**
     * Initializes GPU processing.
     */
    fun initializeGPU(contextProvider: Any) =
        colorTransferRepository.initializeGPU(contextProvider)
}
