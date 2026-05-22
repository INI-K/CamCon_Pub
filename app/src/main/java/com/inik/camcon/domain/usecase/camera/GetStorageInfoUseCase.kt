package com.inik.camcon.domain.usecase.camera

import com.inik.camcon.domain.model.StorageInfo
import com.inik.camcon.domain.repository.CameraConfigRepository
import javax.inject.Inject

/**
 * 카메라 첫 스토리지(주 슬롯) 정보 조회.
 * 미지원 카메라 또는 capacity/free 미보고 시 null Result.success(null).
 */
class GetStorageInfoUseCase @Inject constructor(
    private val repository: CameraConfigRepository
) {
    suspend operator fun invoke(): Result<StorageInfo?> = repository.getStorageInfo()
}
