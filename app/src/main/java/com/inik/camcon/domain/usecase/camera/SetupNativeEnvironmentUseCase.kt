package com.inik.camcon.domain.usecase.camera

import com.inik.camcon.domain.repository.CameraRepository
import javax.inject.Inject

class SetupNativeEnvironmentUseCase @Inject constructor(
    private val repository: CameraRepository
) {
    /** 플러그인 경로는 data 레이어가 결정한다 — 호출자가 넘기면 틀린 경로를 넘길 수 있다. */
    suspend operator fun invoke(): Boolean = repository.setupNativeEnvironment()
}
