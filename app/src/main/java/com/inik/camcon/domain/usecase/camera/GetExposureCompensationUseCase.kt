package com.inik.camcon.domain.usecase.camera

import com.inik.camcon.domain.model.ExposureCompensation
import com.inik.camcon.domain.repository.CameraConfigRepository
import javax.inject.Inject

/**
 * 현재 노출 보정(EV) 값과 카메라가 지원하는 선택지 목록을 조회한다.
 * 미지원 카메라/widget 부재 시 null Result.success(null) 반환.
 */
class GetExposureCompensationUseCase @Inject constructor(
    private val repository: CameraConfigRepository
) {
    suspend operator fun invoke(): Result<ExposureCompensation?> = runCatching {
        repository.getExposureCompensation().getOrNull()
    }
}
