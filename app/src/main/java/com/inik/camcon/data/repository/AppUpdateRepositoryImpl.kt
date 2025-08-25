package com.inik.camcon.data.repository

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.inik.camcon.BuildConfig
import com.inik.camcon.data.datasource.remote.PlayStoreVersionDataSource
import com.inik.camcon.domain.model.AppVersionInfo
import com.inik.camcon.domain.repository.AppUpdateRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playStoreVersionDataSource: PlayStoreVersionDataSource
) : AppUpdateRepository {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("app_version_config", Context.MODE_PRIVATE)
    }

    override suspend fun checkForUpdate(): Result<AppVersionInfo> {
        return try {
            Log.d("AppUpdateRepository", "앱 업데이트 확인 중...")

            val currentVersionCode = BuildConfig.VERSION_CODE
            val currentVersionName = BuildConfig.VERSION_NAME
            val packageName = context.packageName

            // 1단계: Play Store에서 실제 최신 버전 정보 가져오기 시도
            val playStoreInfo = playStoreVersionDataSource.getPlayStoreVersionInfo(packageName)

            if (playStoreInfo != null && playStoreInfo.isAvailable) {
                Log.d(
                    "AppUpdateRepository",
                    "Play Store 버전 정보 조회 성공: ${playStoreInfo.versionName}"
                )

                // Play Store 버전과 현재 버전 비교
                val isUpdateAvailable =
                    compareVersions(playStoreInfo.versionName, currentVersionName) > 0

                // 강제 업데이트 정책은 로컬 설정에서 관리
                val forceUpdateEnabled = prefs.getBoolean("force_update_enabled", false)
                val minimumVersionCode = prefs.getInt("minimum_version_code", 1)

                val isUpdateRequired =
                    forceUpdateEnabled && (currentVersionCode < minimumVersionCode)

                val versionInfo = AppVersionInfo(
                    currentVersion = currentVersionName,
                    latestVersion = playStoreInfo.versionName,
                    isUpdateRequired = isUpdateRequired,
                    isUpdateAvailable = isUpdateAvailable
                )

                Log.d(
                    "AppUpdateRepository", """
                    Play Store 버전 확인 결과:
                    현재 버전: $currentVersionName (코드: $currentVersionCode)
                    최신 버전: ${playStoreInfo.versionName}
                    업데이트 가능: $isUpdateAvailable
                    업데이트 필수: $isUpdateRequired
                    마지막 업데이트: ${playStoreInfo.lastUpdated}
                    설치 수: ${playStoreInfo.installs}
                """.trimIndent()
                )

                return Result.success(versionInfo)
            } else {
                Log.w(
                    "AppUpdateRepository",
                    "Play Store 버전 조회 실패, 로컬 설정으로 폴백"
                )
            }

            // 2단계: Play Store 조회 실패 시 기존 SharedPreferences 방식으로 폴백
            return checkVersionWithLocalConfig(currentVersionCode, currentVersionName)

        } catch (e: Exception) {
            Log.e("AppUpdateRepository", "버전 확인 실패", e)

            // 에러 발생 시 로컬 설정으로 폴백
            return checkVersionWithLocalConfig(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)
        }
    }

    private fun checkVersionWithLocalConfig(
        currentVersionCode: Int,
        currentVersionName: String
    ): Result<AppVersionInfo> {
        return try {
            val minimumVersionCode = prefs.getInt("minimum_version_code", 1)
            val latestVersionCode = prefs.getInt("latest_version_code", currentVersionCode)
            val latestVersionName =
                prefs.getString("latest_version_name", currentVersionName) ?: currentVersionName
            val forceUpdateEnabled = prefs.getBoolean("force_update_enabled", false)

            // 기본값 설정 (처음 실행 시)
            if (!prefs.contains("minimum_version_code")) {
                prefs.edit()
                    .putInt("minimum_version_code", 1)
                    .putInt("latest_version_code", currentVersionCode)
                    .putString("latest_version_name", currentVersionName)
                    .putBoolean("force_update_enabled", false)
                    .apply()
            }

            val isUpdateRequired = forceUpdateEnabled && (currentVersionCode < minimumVersionCode)
            val isUpdateAvailable = currentVersionCode < latestVersionCode

            val versionInfo = AppVersionInfo(
                currentVersion = currentVersionName,
                latestVersion = latestVersionName,
                isUpdateRequired = isUpdateRequired,
                isUpdateAvailable = isUpdateAvailable
            )

            Log.d("AppUpdateRepository", "로컬 설정 버전 확인 결과: $versionInfo")
            Result.success(versionInfo)
        } catch (e: Exception) {
            // 최종 폴백: 업데이트 불필요로 처리
            val versionInfo = AppVersionInfo(
                currentVersion = BuildConfig.VERSION_NAME,
                latestVersion = BuildConfig.VERSION_NAME,
                isUpdateRequired = false,
                isUpdateAvailable = false
            )
            Result.success(versionInfo)
        }
    }

    /**
     * 버전 문자열 비교 (예: "1.2.3" vs "1.2.4")
     * @return 0: 같음, 1: v1이 더 높음, -1: v2가 더 높음
     */
    private fun compareVersions(v1: String, v2: String): Int {
        return try {
            val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
            val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }

            val maxLength = maxOf(parts1.size, parts2.size)

            for (i in 0 until maxLength) {
                val part1 = parts1.getOrElse(i) { 0 }
                val part2 = parts2.getOrElse(i) { 0 }

                when {
                    part1 > part2 -> return 1
                    part1 < part2 -> return -1
                }
            }
            0
        } catch (e: Exception) {
            Log.e("AppUpdateRepository", "버전 비교 실패: $v1 vs $v2", e)
            0
        }
    }

    override suspend fun startImmediateUpdate(): Result<Unit> {
        return try {
            Log.d("AppUpdateRepository", "즉시 업데이트 시작...")

            // Google Play Store로 이동
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://details?id=${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Play Store 앱이 설치되어 있지 않은 경우 웹 브라우저로 이동
            if (intent.resolveActivity(context.packageManager) == null) {
                intent.data =
                    Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")
            }

            context.startActivity(intent)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("AppUpdateRepository", "업데이트 시작 실패", e)
            Result.failure(e)
        }
    }

    // 개발/테스트용 로컬 정책 설정 메서드들
    fun setMinimumVersionCode(versionCode: Int) {
        prefs.edit().putInt("minimum_version_code", versionCode).apply()
    }

    fun setLatestVersion(versionCode: Int, versionName: String) {
        prefs.edit()
            .putInt("latest_version_code", versionCode)
            .putString("latest_version_name", versionName)
            .apply()
    }

    fun setForceUpdateEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("force_update_enabled", enabled).apply()
    }
}