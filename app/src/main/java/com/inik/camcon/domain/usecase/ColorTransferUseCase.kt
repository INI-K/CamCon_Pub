package com.inik.camcon.domain.usecase

import com.inik.camcon.domain.model.ColorTransferResult
import com.inik.camcon.domain.repository.ColorTransferRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 모든 컬러 트랜스퍼 로직을 Repository에 위임하는 얇은 UseCase 래퍼.
 * 도메인 레이어는 Android 의존성을 갖지 않는다.
 */
@Singleton
class ColorTransferUseCase @Inject constructor(
    private val colorTransferRepository: ColorTransferRepository
) {

    /**
     * 캐시된 레퍼런스 통계를 활용해 GPU 가속으로 컬러 트랜스퍼를 적용한다.
     * @param targetImagePath 입력 이미지 경로
     * @param referenceImagePath 레퍼런스 이미지 경로
     * @param intensity 컬러 트랜스퍼 강도 (0.0 ~ 1.0)
     * @param maxSize 입력 비트맵을 스케일할 최대 크기 (선택, 0 = 스케일 안 함)
     * @return 결과 이미지 파일 경로, 실패 시 null
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
     * 두 이미지 사이에 컬러 트랜스퍼를 적용한다 (CPU 경로).
     * @param targetImagePath 입력 이미지 경로
     * @param referenceImagePath 레퍼런스 이미지 경로
     * @param intensity 컬러 트랜스퍼 강도 (0.0 ~ 1.0)
     * @param maxSize 두 비트맵을 스케일할 최대 크기 (선택, 0 = 스케일 안 함)
     * @return 결과 이미지 파일 경로, 실패 시 null
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
     * GPU 가속으로 컬러 트랜스퍼를 적용한다.
     * @param inputImagePath 입력 이미지 경로
     * @param referenceImagePath 레퍼런스 이미지 경로
     * @param intensity 컬러 트랜스퍼 강도 (0.0 ~ 1.0)
     * @return 결과 이미지 파일 경로, 실패 시 null
     */
    suspend fun applyColorTransferWithGPU(
        inputImagePath: String,
        referenceImagePath: String,
        intensity: Float
    ): String? =
        colorTransferRepository.applyColorTransferWithGPU(inputImagePath, referenceImagePath, intensity)

    /**
     * 컬러 트랜스퍼를 적용하고 EXIF 메타데이터를 보존하며 결과를 저장한다.
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
     * 주어진 경로의 파일이 유효한 이미지인지 검증한다.
     */
    fun isValidImageFile(imagePath: String): Boolean =
        colorTransferRepository.isValidImageFile(imagePath)

    /**
     * 캐시된 레퍼런스 이미지 통계를 비운다.
     */
    fun clearReferenceCache() =
        colorTransferRepository.clearReferenceCache()

    /**
     * GPU 처리를 초기화한다.
     */
    fun initializeGPU(contextProvider: Any) =
        colorTransferRepository.initializeGPU(contextProvider)

    /**
     * GPU/EGL 리소스를 해제한다. 애플리케이션 종료 시점에만 호출한다.
     */
    fun releaseGpu() =
        colorTransferRepository.releaseGpu()
}
