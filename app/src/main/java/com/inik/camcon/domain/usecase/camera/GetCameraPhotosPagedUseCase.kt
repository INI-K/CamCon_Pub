package com.inik.camcon.domain.usecase.camera

import com.inik.camcon.domain.model.PaginatedCameraPhotos
import com.inik.camcon.domain.repository.CameraRepository
import com.inik.camcon.domain.util.Logger
import javax.inject.Inject

class GetCameraPhotosPagedUseCase @Inject constructor(
    private val cameraRepository: CameraRepository,
    private val logger: Logger
) {
    companion object {
        private const val TAG = "GetCameraPhotosPagedUseCase"
    }

    suspend operator fun invoke(page: Int, pageSize: Int = 20): Result<PaginatedCameraPhotos> {
        return try {
            // PTPIP(Wi-Fi)에서도 네이티브 커맨드 큐가 FILE_LIST와 EVENT_POLL을 직렬화하므로
            // 파일 목록 조회를 허용한다 (과거 차단은 큐 도입 이전의 레거시 제약).
            logger.d(TAG, "파일 목록 가져오기 시작 (페이지: $page, 크기: $pageSize)")
            cameraRepository.getCameraPhotosPaged(page, pageSize)
        } catch (e: Exception) {
            logger.e(TAG, "파일 목록 가져오기 실패", e)
            Result.failure(e)
        }
    }
}

class GetCameraThumbnailUseCase @Inject constructor(
    private val cameraRepository: CameraRepository
) {
    suspend operator fun invoke(photoPath: String): Result<ByteArray> {
        return cameraRepository.getCameraThumbnail(photoPath)
    }
}