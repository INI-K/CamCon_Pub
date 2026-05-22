package com.inik.camcon.domain.usecase.camera

import com.inik.camcon.domain.repository.CameraConfigRepository
import javax.inject.Inject

/**
 * 노출 보정(EV) 값을 설정한다.
 * 카메라가 RADIO/MENU widget으로 제공하는 raw 문자열 값(예: "+1/3")을 그대로 넘긴다.
 */
class SetExposureCompensationUseCase @Inject constructor(
    private val repository: CameraConfigRepository
) {
    suspend operator fun invoke(value: String): Result<Unit> =
        repository.setExposureCompensation(value)
}
