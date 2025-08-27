package com.inik.camcon.data.datasource.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayStoreVersionDataSource @Inject constructor() {

    /**
     * Google Play Store에서 앱의 최신 버전 정보를 가져옵니다.
     * 방법 1: Play Store 웹페이지 스크래핑
     */
    suspend fun getPlayStoreVersionInfo(packageName: String): PlayStoreVersionInfo? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("PlayStoreVersion", "버전 정보 조회 중: $packageName")

                val url = URL("https://play.google.com/store/apps/details?id=$packageName&hl=en")
                val connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "GET"
                    setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36"
                    )
                    connectTimeout = 10000
                    readTimeout = 10000
                }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = reader.readText()
                    reader.close()

                    return@withContext parsePlayStoreResponse(response, packageName)
                } else {
                    Log.e("PlayStoreVersion", "HTTP 오류: $responseCode")
                    null
                }
            } catch (e: Exception) {
                Log.e("PlayStoreVersion", "Play Store 버전 조회 실패", e)
                null
            }
        }
    }

    /**
     * 방법 2: 간접적으로 JSON API 사용 (비공식)
     */
    suspend fun getVersionViaJsonApi(packageName: String): PlayStoreVersionInfo? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("PlayStoreVersion", "JSON API를 통한 조회 중: $packageName")

                // 비공식 API 엔드포인트
                val url = URL("https://play.google.com/store/apps/details?id=$packageName&gl=US")
                val connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                    connectTimeout = 10000
                    readTimeout = 10000
                }

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().readText()
                    return@withContext parsePlayStoreHtml(response)
                } else {
                    Log.e("PlayStoreVersion", "API 오류: ${connection.responseCode}")
                    null
                }
            } catch (e: Exception) {
                Log.e("PlayStoreVersion", "JSON API 조회 실패", e)
                null
            }
        }
    }

    private fun parsePlayStoreResponse(html: String, packageName: String): PlayStoreVersionInfo? {
        return try {
            // Play Store HTML에서 버전 정보 추출
            // 패턴: "Current Version","3.1.0"
            val versionPattern = """"Current Version","([^"]+)"""".toRegex()
            val versionMatch = versionPattern.find(html)

            // 패턴: "Installs","1,000,000+"
            val installsPattern = """"Installs","([^"]+)"""".toRegex()
            val installsMatch = installsPattern.find(html)

            // 패턴: "Updated","March 15, 2024"
            val updatedPattern = """"Updated","([^"]+)"""".toRegex()
            val updatedMatch = updatedPattern.find(html)

            if (versionMatch != null) {
                val version = versionMatch.groupValues[1]
                val installs = installsMatch?.groupValues?.get(1) ?: "Unknown"
                val lastUpdated = updatedMatch?.groupValues?.get(1) ?: "Unknown"

                Log.d("PlayStoreVersion", "버전 발견: $version")

                PlayStoreVersionInfo(
                    versionName = version,
                    installs = installs,
                    lastUpdated = lastUpdated,
                    isAvailable = true
                )
            } else {
                Log.w("PlayStoreVersion", "HTML 응답에서 버전을 찾을 수 없습니다")
                null
            }
        } catch (e: Exception) {
            Log.e("PlayStoreVersion", "Play Store 응답 파싱 실패", e)
            null
        }
    }

    private fun parsePlayStoreHtml(html: String): PlayStoreVersionInfo? {
        return try {
            // 더 정확한 버전 정보 추출 시도
            val patterns = listOf(
                """"softwareVersion":"([^"]+)"""".toRegex(),
                """"Current Version.*?([0-9]+\.[0-9]+\.[0-9]+)"""".toRegex(),
                """Version ([0-9]+\.[0-9]+\.[0-9]+)""".toRegex()
            )

            for (pattern in patterns) {
                val match = pattern.find(html)
                if (match != null) {
                    val version = match.groupValues[1]
                    Log.d("PlayStoreVersion", "버전 추출 성공: $version")

                    return PlayStoreVersionInfo(
                        versionName = version,
                        installs = "Unknown",
                        lastUpdated = "Unknown",
                        isAvailable = true
                    )
                }
            }

            Log.w("PlayStoreVersion", "버전 패턴과 일치하는 항목이 없습니다")
            null
        } catch (e: Exception) {
            Log.e("PlayStoreVersion", "HTML 파싱 실패", e)
            null
        }
    }
}

data class PlayStoreVersionInfo(
    val versionName: String,
    val installs: String,
    val lastUpdated: String,
    val isAvailable: Boolean
)