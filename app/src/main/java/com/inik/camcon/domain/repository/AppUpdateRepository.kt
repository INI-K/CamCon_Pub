package com.inik.camcon.domain.repository

import com.inik.camcon.domain.model.AppVersionInfo

interface AppUpdateRepository {
    suspend fun checkForUpdate(): Result<AppVersionInfo>
    suspend fun startImmediateUpdate(): Result<Unit>
}