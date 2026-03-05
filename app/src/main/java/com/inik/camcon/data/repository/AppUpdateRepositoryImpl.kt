package com.inik.camcon.data.repository
import com.inik.camcon.BuildConfig
import com.inik.camcon.domain.model.AppVersionInfo
import com.inik.camcon.domain.repository.AppUpdateRepository
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class AppUpdateRepositoryImpl @Inject constructor() : AppUpdateRepository {
    override suspend fun checkForUpdate(): Result<AppVersionInfo> {
        val current = BuildConfig.VERSION_NAME
        return Result.success(
            AppVersionInfo(
                currentVersion = current,
                latestVersion = current,
                isUpdateRequired = false,
                isUpdateAvailable = false
            )
        )
    }
    override suspend fun startImmediateUpdate(): Result<Unit> {
        return Result.success(Unit)
    }
}