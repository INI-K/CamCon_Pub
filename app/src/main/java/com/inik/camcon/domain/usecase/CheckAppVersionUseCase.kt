package com.inik.camcon.domain.usecase

import com.inik.camcon.domain.model.AppVersionInfo
import com.inik.camcon.domain.repository.AppUpdateRepository
import javax.inject.Inject

class CheckAppVersionUseCase @Inject constructor(
    private val appUpdateRepository: AppUpdateRepository
) {
    suspend operator fun invoke(): Result<AppVersionInfo> {
        return appUpdateRepository.checkForUpdate()
    }
}