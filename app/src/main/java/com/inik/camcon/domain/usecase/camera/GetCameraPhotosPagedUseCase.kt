package com.inik.camcon.domain.usecase.camera

import com.inik.camcon.domain.model.PaginatedCameraPhotos
import com.inik.camcon.domain.repository.CameraRepository
import javax.inject.Inject

class GetCameraPhotosPagedUseCase @Inject constructor(
    private val cameraRepository: CameraRepository
) {
    suspend operator fun invoke(page: Int, pageSize: Int = 20): Result<PaginatedCameraPhotos> {
        return cameraRepository.getCameraPhotosPaged(page, pageSize)
    }
}

class GetCameraThumbnailUseCase @Inject constructor(
    private val cameraRepository: CameraRepository
) {
    suspend operator fun invoke(photoPath: String): Result<ByteArray> {
        return cameraRepository.getCameraThumbnail(photoPath)
    }
}