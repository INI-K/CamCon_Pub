package com.inik.camcon.i18n

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * PTP/IP 후보 선택 UI 신규 문자열 26키 × 8로케일 패리티 가드.
 *
 * 기존 [I18nResourceConsistencyTest]는 `strings.xml` **단독**만 스캔하므로 재사용할 수 없다.
 * CamCon은 로케일별로 키가 `strings.xml` / `strings_connect.xml` /
 * `strings_zzz_i18n_backfill.xml` 중 어디에 정의돼 있는지 다르고, Android는 이들을 병합한다.
 * 그래서 본 가드는 각 `values` 로케일 디렉터리의 `strings` 접두 XML **전체를 union**으로 스캔한다.
 *
 * 동시에 **중복 정의도 잡는다** — 같은 로케일에 같은 키가 2번 정의되면 빌드가 파손된다
 * (실제로 백필 파일과 개별 파일 사이에서 반복 발생한 함정).
 */
class PtpipDiscoveryI18nParityTest {

    private val resRoot = File("src/main/res")

    private val locales: List<String> = listOf(
        "values",       // 기본 (영문)
        "values-ko",
        "values-ja",
        "values-zh",
        "values-de",
        "values-es",
        "values-fr",
        "values-it",
    )

    /** Wave 1~2 신규 키 26종. UI(DiscoveredCameraList)와 정책 문구가 참조한다. */
    private val trackedKeys: List<String> = listOf(
        "ptpip_discovery_found_title",
        "ptpip_candidate_unnamed_fmt",
        "ptpip_candidate_known",
        "ptpip_source_mdns",
        "ptpip_source_ssdp",
        "ptpip_source_gateway",
        "ptpip_source_cached",
        "ptpip_source_manual",
        "ptpip_vendor_confirmed_fmt",
        "ptpip_vendor_likely_fmt",
        "ptpip_vendor_unknown",
        "ptpip_discovery_empty_title",
        "ptpip_discovery_empty_camera_first",
        "ptpip_discovery_no_network",
        "ptpip_discovery_ap_empty",
        "ptpip_discovery_busy",
        "ptpip_discovery_error_fmt",
        "ptpip_shared_network_notice",
        "ptpip_connect_confirm_title",
        "ptpip_connect_confirm_body",
        "ptpip_connect_confirm_action",
        "ptpip_manual_source_hint",
        "ptpip_connect_cancelling",
        // Wave 2 — 서브넷 스윕
        "ptpip_sweep_action",
        "ptpip_source_scan",
        "ptpip_protocol_unsupported",
    )

    /** `%1$s` 등 위치 지정 포맷 인자 추출. */
    private val formatArgPattern = Regex("""%(\d+)\$[sd]""")

    private fun stringsFiles(locale: String): List<File> =
        File(resRoot, locale).listFiles()
            ?.filter { it.isFile && it.name.startsWith("strings") && it.name.endsWith(".xml") }
            ?.sortedBy { it.name }
            ?: emptyList()

    /** 로케일 내 키 → 정의 목록(파일명 + 값). union 스캔. */
    private fun definitionsOf(locale: String, key: String): List<Pair<String, String>> {
        val pattern = Regex(
            """<string\s+name\s*=\s*"$key"[^>]*>(.*?)</string>""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )
        return stringsFiles(locale).flatMap { file ->
            pattern.findAll(file.readText()).map { file.name to it.groupValues[1] }.toList()
        }
    }

    @Test
    fun `신규 26키가 8로케일에 각각 정확히 1회 정의된다`() {
        require(resRoot.isDirectory) {
            "테스트는 :app 모듈 디렉터리에서 실행되어야 함: ${resRoot.absolutePath}"
        }

        val violations = mutableListOf<String>()
        locales.forEach { locale ->
            trackedKeys.forEach { key ->
                val defs = definitionsOf(locale, key)
                when {
                    defs.isEmpty() -> violations += "[$locale] 누락 키: $key"
                    defs.size > 1 -> violations +=
                        "[$locale] 중복 정의(빌드 파손): $key → ${defs.map { it.first }}"
                }
            }
        }

        assertTrue(
            "PTP/IP 후보 선택 i18n 위반 ${violations.size}건:\n" + violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    @Test
    fun `포맷 인자 개수가 로케일 간 일치한다`() {
        require(resRoot.isDirectory)

        val violations = mutableListOf<String>()
        trackedKeys.forEach { key ->
            val baseline = definitionsOf("values", key).firstOrNull()?.second ?: return@forEach
            val expected = formatArgPattern.findAll(baseline).map { it.groupValues[1] }.toSet()
            locales.drop(1).forEach { locale ->
                val value = definitionsOf(locale, key).firstOrNull()?.second ?: return@forEach
                val actual = formatArgPattern.findAll(value).map { it.groupValues[1] }.toSet()
                if (actual != expected) {
                    violations += "[$locale] $key 포맷 인자 불일치: 기대=$expected 실제=$actual"
                }
            }
        }

        assertTrue(
            "포맷 인자 불일치 ${violations.size}건:\n" + violations.joinToString("\n"),
            violations.isEmpty()
        )
    }
}
