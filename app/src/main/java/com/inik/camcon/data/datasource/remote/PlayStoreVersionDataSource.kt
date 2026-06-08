package com.inik.camcon.data.datasource.remote

import android.util.Log
import com.inik.camcon.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayStoreVersionDataSource @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    /**
     * Google Play Store에서 앱의 최신 버전 정보를 가져옵니다.
     * 방법 1: Play Store 웹페이지 스크래핑
     */
    suspend fun getPlayStoreVersionInfo(packageName: String): PlayStoreVersionInfo? {
        return withContext(ioDispatcher) {
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

                try {
                    val responseCode = connection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val response = BufferedReader(
                            InputStreamReader(connection.inputStream)
                        ).use { it.readText() }

                        return@withContext parsePlayStoreResponse(response, packageName)
                    } else {
                        Log.e("PlayStoreVersion", "HTTP 오류: $responseCode")
                        null
                    }
                } finally {
                    connection.disconnect()
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
        return withContext(ioDispatcher) {
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

                try {
                    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                        val response = connection.inputStream.bufferedReader().use { it.readText() }
                        return@withContext parsePlayStoreHtml(response)
                    } else {
                        Log.e("PlayStoreVersion", "API 오류: ${connection.responseCode}")
                        null
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Log.e("PlayStoreVersion", "JSON API 조회 실패", e)
                null
            }
        }
    }

    private fun parsePlayStoreResponse(html: String, packageName: String): PlayStoreVersionInfo? {
        return try {
            // 현행 Play Store 페이지는 더 이상 "Current Version","x.y.z" 형태를 노출하지 않는다.
            // 견고한 버전 추출은 extractVersionName으로 위임하고, 실패 시 안전 폴백(null)한다(H25).
            val version = extractVersionName(html)

            // 부가 정보(설치 수/업데이트일)는 best-effort. 못 찾으면 Unknown.
            val installs = """"Installs","([^"]+)"""".toRegex().find(html)
                ?.groupValues?.getOrNull(1) ?: "Unknown"
            val lastUpdated = """"Updated","([^"]+)"""".toRegex().find(html)
                ?.groupValues?.getOrNull(1) ?: "Unknown"

            if (version != null) {
                Log.d("PlayStoreVersion", "버전 발견: $version")
                PlayStoreVersionInfo(
                    versionName = version,
                    installs = installs,
                    lastUpdated = lastUpdated,
                    isAvailable = true
                )
            } else {
                Log.w("PlayStoreVersion", "HTML 응답에서 버전을 찾을 수 없습니다 - 폴백 처리")
                null
            }
        } catch (e: Exception) {
            Log.e("PlayStoreVersion", "Play Store 응답 파싱 실패", e)
            null
        }
    }

    private fun parsePlayStoreHtml(html: String): PlayStoreVersionInfo? {
        return try {
            val version = extractVersionName(html)
            if (version != null) {
                Log.d("PlayStoreVersion", "버전 추출 성공: $version")
                return PlayStoreVersionInfo(
                    versionName = version,
                    installs = "Unknown",
                    lastUpdated = "Unknown",
                    isAvailable = true
                )
            }
            Log.w("PlayStoreVersion", "버전 패턴과 일치하는 항목이 없습니다 - 폴백 처리")
            null
        } catch (e: Exception) {
            Log.e("PlayStoreVersion", "HTML 파싱 실패", e)
            null
        }
    }

    /**
     * 현행 Play Store 페이지 구조에 맞춰 버전명을 추출한다(H25).
     *
     * 신뢰 순서로 패턴을 시도하되, 본문/리뷰/변경 이력 텍스트에 흔한 "Version x.y.z" 같은
     * 느슨한 패턴은 사용하지 않아 오탐(엉뚱한 최신 버전 표시)을 방지한다.
     * 어떤 패턴과도 매칭되지 않으면 null을 반환하고 상위 호출자가 로컬 설정으로 안전 폴백한다.
     *
     * 주의: Play Store HTML 스크래핑은 페이지 구조 변경에 취약하고 ToS 리스크가 있다.
     * 정식 In-App Update API(com.google.android.play:app-update)로의 전환을 권장한다.
     */
    private fun extractVersionName(html: String): String? {
        // 현행 Play Store는 버전을 JS 데이터 블롭에 [[["x.y.z"]]] 형태로 임베드한다.
        // 라벨 기반(softwareVersion 등) 패턴을 우선 시도하고, 그다음 구조적 블롭을 시도한다.
        val labeledPatterns = listOf(
            """"softwareVersion"\s*:\s*"([0-9]+(?:\.[0-9]+){1,3})"""".toRegex(),
            """\[\["Current Version"\],\[\["([0-9]+(?:\.[0-9]+){1,3})"\]\]""".toRegex(),
            """Current Version[^0-9]{0,40}?([0-9]+(?:\.[0-9]+){2,3})""".toRegex()
        )
        for (pattern in labeledPatterns) {
            val version = pattern.find(html)?.groupValues?.getOrNull(1)
            if (isValidVersionName(version)) return version
        }

        // 라벨 기반 실패 시 구조적 블롭 [[["x.y.z"]]] 후보들 중 유효한 첫 버전을 채택한다.
        val blobPattern = """\[\[\["([0-9]+(?:\.[0-9]+){1,3})"\]\]\]""".toRegex()
        for (match in blobPattern.findAll(html)) {
            val candidate = match.groupValues.getOrNull(1)
            if (isValidVersionName(candidate)) return candidate
        }
        return null
    }

    /**
     * x.y 또는 x.y.z(.w) 형태의 숫자 버전명만 유효로 간주한다.
     * 빈 문자열·날짜·임의 텍스트를 걸러 오탐을 막는다.
     */
    private fun isValidVersionName(version: String?): Boolean {
        if (version.isNullOrBlank()) return false
        return version.matches("""[0-9]+(\.[0-9]+){1,3}""".toRegex())
    }
}

data class PlayStoreVersionInfo(
    val versionName: String,
    val installs: String,
    val lastUpdated: String,
    val isAvailable: Boolean
)