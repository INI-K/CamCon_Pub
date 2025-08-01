package com.inik.camcon.domain.usecase

import com.inik.camcon.domain.repository.AppUpdateRepository
import javax.inject.Inject

class StartImmediateUpdateUseCase @Inject constructor(
    private val appUpdateRepository: AppUpdateRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        return appUpdateRepository.startImmediateUpdate()
    }
}