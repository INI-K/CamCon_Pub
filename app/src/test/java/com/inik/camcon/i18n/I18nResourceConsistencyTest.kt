package com.inik.camcon.i18n

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * i18n 자원 키 일관성 정적 회귀.
 *
 * CamCon 은 8 개 로케일을 지원: 기본(영문) + ko/ja/zh/de/es/fr/it.
 * RAW 게이팅·업그레이드 메시지 등 핵심 키가 8 개 로케일 strings.xml 모두에 존재해야 한다.
 *
 * AGP `missingTranslation` lint 도 같은 누락을 잡지만:
 *  - lint 는 빌드 시점에 실행되므로 PR 단위 즉시 노출이 느리다.
 *  - 본 unit test 는 `./gradlew :app:testDebugUnitTest` 한 번에 PR 단위 즉시 노출.
 *
 * **변경 가이드**: 새 게이팅·구독 메시지 자원 키를 추가하면 [TRACKED_KEYS] 에도 추가한다.
 * 매핑이 누락된 키는 본 가드의 보호를 받지 못한다.
 */
class I18nResourceConsistencyTest {

    private val resRoot = File("src/main/res")

    /**
     * 8 개 로케일의 strings.xml 경로 (디렉터리명 → 식별자).
     */
    private val locales: List<String> = listOf(
        "values",       // 기본 (영문)
        "values-ko",    // 한국어
        "values-ja",    // 일본어
        "values-zh",    // 중국어
        "values-de",    // 독일어
        "values-es",    // 스페인어
        "values-fr",    // 프랑스어
        "values-it",    // 이탈리아어
    )

    /**
     * 본 가드가 일관성을 강제하는 핵심 자원 키.
     *
     * - RAW 게이팅 4 종
     * - 포맷 검증 2 종
     * - 업그레이드 메시지 5 종
     *
     * 총 11 종 × 8 로케일 = 88 개 매핑.
     */
    private val trackedKeys: List<String> = listOf(
        // raw_restriction_*
        "raw_restriction_free",
        "raw_restriction_basic",
        "raw_restriction_generic",
        "raw_restriction_download_disabled",
        // format_unsupported_*
        "format_unsupported",
        "format_unsupported_in_subscription",
        // upgrade_message_*
        "upgrade_message_free",
        "upgrade_message_basic",
        "upgrade_message_pro",
        "upgrade_message_referrer",
        "upgrade_message_admin",
    )

    @Test
    fun `i18n 회귀 - 모든 추적 키가 8 개 로케일 strings 에 존재`() {
        require(resRoot.isDirectory) {
            "테스트는 :app 모듈 디렉터리에서 실행되어야 함: ${resRoot.absolutePath}"
        }

        val violations = mutableListOf<String>()

        locales.forEach { locale ->
            val stringsXml = File(resRoot, "$locale/strings.xml")
            if (!stringsXml.exists()) {
                violations += "[$locale] strings.xml 파일이 존재하지 않습니다: ${stringsXml.path}"
                return@forEach
            }
            val xml = stringsXml.readText()
            trackedKeys.forEach { key ->
                // `name="key"` 패턴 매칭.
                val pattern = Regex("""name\s*=\s*"$key"""")
                if (!pattern.containsMatchIn(xml)) {
                    violations += "[$locale] 누락 키: $key"
                }
            }
        }

        assertTrue(
            "i18n 자원 키 누락 ${violations.size} 건:\n" + violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    /**
     * 본 가드 자체가 의미를 가지려면 추적 키 목록이 비어있지 않아야 한다.
     * 누군가 [trackedKeys] 를 실수로 비우면 가드가 무력화되므로 명시 검증.
     */
    @Test
    fun `자기 검증 - trackedKeys 비어있지 않음`() {
        assertTrue("trackedKeys 가 비어있음", trackedKeys.isNotEmpty())
    }

    /**
     * 로케일 목록은 CamCon 8 개 언어와 일치해야 한다.
     * 누군가 로케일을 실수로 줄이면 가드가 약해지므로 명시 검증.
     */
    @Test
    fun `자기 검증 - locales 가 정확히 8 개`() {
        assertTrue("locales 개수 비정상: ${locales.size}", locales.size == 8)
    }
}
