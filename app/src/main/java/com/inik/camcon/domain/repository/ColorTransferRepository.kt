package com.inik.camcon.domain.repository

import com.inik.camcon.domain.model.ColorTransferResult

/**
 * 컬러 트랜스퍼 Repository 인터페이스.
 * 모든 메서드는 원시/도메인 타입만 사용한다 (Bitmap, Context 같은 Android 전용 타입 없음).
 */
interface ColorTransferRepository {

    /**
     * 캐시된 레퍼런스 통계를 활용해 GPU 가속으로 컬러 트랜스퍼를 적용한다.
     * targetImagePath에서 입력을 로드하고 캐시된 레퍼런스 통계를 적용한 뒤 결과를 임시 파일로 저장한다.
     * @param targetImagePath 입력 이미지 경로
     * @param referenceImagePath 레퍼런스 이미지 경로 (캐시된 통계용)
     * @param intensity 컬러 트랜스퍼 강도 (0.0 ~ 1.0)
     * @param maxSize 입력 비트맵을 스케일할 최대 크기 (선택, 0 = 스케일 안 함)
     * @return 결과 이미지 파일 경로, 실패 시 null
     */
    suspend fun applyColorTransferWithGPUCached(
        targetImagePath: String,
        referenceImagePath: String,
        intensity: Float,
        maxSize: Int = 0
    ): String?

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
    ): String?

    /**
     * GPU 가속으로 컬러 트랜스퍼를 적용한다.
     * 경로에서 이미지를 로드하고 트랜스퍼를 적용한 뒤 결과를 저장한다.
     * @param inputImagePath 입력 이미지 경로
     * @param referenceImagePath 레퍼런스 이미지 경로
     * @param intensity 컬러 트랜스퍼 강도 (0.0 ~ 1.0)
     * @return 결과 이미지 파일 경로, 실패 시 null
     */
    suspend fun applyColorTransferWithGPU(
        inputImagePath: String,
        referenceImagePath: String,
        intensity: Float
    ): String?

    /**
     * 컬러 트랜스퍼를 적용하고 EXIF 메타데이터를 보존하며 결과를 저장한다.
     * @param inputImagePath 입력 이미지 경로
     * @param referenceImagePath 레퍼런스 이미지 경로
     * @param originalImagePath EXIF 메타데이터 복사를 위한 원본 이미지 경로
     * @param outputPath 결과를 저장할 경로
     * @param intensity 컬러 트랜스퍼 강도 (0.0 ~ 1.0)
     * @return 결과 정보, 실패 시 null
     */
    suspend fun applyColorTransferAndSave(
        inputImagePath: String,
        referenceImagePath: String,
        originalImagePath: String,
        outputPath: String,
        intensity: Float = 0.03f
    ): ColorTransferResult?

    /**
     * 주어진 경로의 파일이 유효한 이미지인지 검증한다.
     * @param imagePath 이미지 파일 경로
     * @return 이미지가 유효하면 true
     */
    fun isValidImageFile(imagePath: String): Boolean

    /**
     * 캐시된 레퍼런스 이미지 통계를 비운다.
     */
    fun clearReferenceCache()

    /**
     * GPU 처리를 초기화한다. contextProvider는 Android Context로 기대되지만,
     * 도메인 레이어가 Android 의존성을 갖지 않도록 Any 타입으로 선언한다.
     */
    fun initializeGPU(contextProvider: Any)

    /**
     * 프로세서가 보유한 GPU/EGL 리소스를 해제한다.
     * 프로세서와 GPU가 앱 범위 싱글톤이므로, 화면 단위가 아니라
     * 애플리케이션 종료 시점(예: Application.onTerminate)에만 호출해야 한다.
     */
    fun releaseGpu()
}
