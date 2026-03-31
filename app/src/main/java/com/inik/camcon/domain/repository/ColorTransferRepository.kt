package com.inik.camcon.domain.repository

import com.inik.camcon.domain.model.ColorTransferResult

/**
 * Color transfer repository interface.
 * All methods use primitive/domain types only (no Android-specific types like Bitmap, Context).
 */
interface ColorTransferRepository {

    /**
     * Applies color transfer using GPU acceleration with cached reference stats.
     * Loads input from targetImagePath, applies cached reference stats, saves result to a temp file.
     * @param targetImagePath path to the input image
     * @param referenceImagePath path to the reference image (for cached stats)
     * @param intensity color transfer intensity (0.0 ~ 1.0)
     * @param maxSize optional max size to scale the input bitmap (0 = no scaling)
     * @return path to the result image file, or null on failure
     */
    suspend fun applyColorTransferWithGPUCached(
        targetImagePath: String,
        referenceImagePath: String,
        intensity: Float,
        maxSize: Int = 0
    ): String?

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
    ): String?

    /**
     * Applies color transfer using GPU acceleration.
     * Loads images from paths, applies transfer, saves result.
     * @param inputImagePath path to the input image
     * @param referenceImagePath path to the reference image
     * @param intensity color transfer intensity (0.0 ~ 1.0)
     * @return path to the result image file, or null on failure
     */
    suspend fun applyColorTransferWithGPU(
        inputImagePath: String,
        referenceImagePath: String,
        intensity: Float
    ): String?

    /**
     * Applies color transfer and saves result with EXIF metadata preservation.
     * @param inputImagePath path to the input image
     * @param referenceImagePath path to the reference image
     * @param originalImagePath path to the original image for EXIF metadata copy
     * @param outputPath path to save the result
     * @param intensity color transfer intensity (0.0 ~ 1.0)
     * @return result info, or null on failure
     */
    suspend fun applyColorTransferAndSave(
        inputImagePath: String,
        referenceImagePath: String,
        originalImagePath: String,
        outputPath: String,
        intensity: Float = 0.03f
    ): ColorTransferResult?

    /**
     * Validates whether the file at the given path is a valid image.
     * @param imagePath path to the image file
     * @return true if the image is valid
     */
    fun isValidImageFile(imagePath: String): Boolean

    /**
     * Clears the cached reference image statistics.
     */
    fun clearReferenceCache()

    /**
     * Initializes GPU processing. The contextProvider is expected to be an Android Context
     * but is typed as Any to keep the domain layer free of Android dependencies.
     */
    fun initializeGPU(contextProvider: Any)
}
