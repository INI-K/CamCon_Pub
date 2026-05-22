package com.inik.camcon.util

import android.content.Context
import com.inik.camcon.R
import io.mockk.every

/**
 * CamCon 표준 테스트 헬퍼.
 *
 * `Context.getString(R.string.xxx)` 호출을 한국어 원문(`values-ko`)으로 stub 처리하는 단일 진입점.
 * 테스트 코드가 직접 `every { context.getString(...) } returns "..."` 를 11+ 회 반복하는 부담을 제거하고,
 * i18n 자원 키 ↔ 한국어 원문 매핑을 한 곳에서 관리한다.
 *
 * ### 사용
 * ```kotlin
 * @Before
 * fun setUp() {
 *     context = mockk()
 *     KoreanStringStubs.applyTo(context)
 * }
 * ```
 *
 * ### 매칭 범위
 * - 한국어 원문 부분 매칭이 가능한 키만 stub (예: `.contains("비활성화")`, `.contains("PRO")`)
 * - PR-7 도입 시점의 11 개 RAW/포맷 게이팅 키. 새 키 추가 시 [KEYS] 매핑에 추가.
 *
 * ### 단일 출처 원칙
 * 테스트 단의 한국어 원문은 본 객체의 [KEYS] 가 유일한 진실의 원천. 개별 테스트 파일에서
 * 같은 키의 stub 을 중복 정의하지 않는다.
 */
object KoreanStringStubs {

    /**
     * 자원 키 → 한국어 원문 (values-ko 매칭).
     *
     * 본 매핑은 `app/src/main/res/values-ko/strings.xml` 의 원문과 정확히 일치해야 한다.
     * 변경 시 두 파일을 동시에 갱신한다.
     */
    val KEYS: Map<Int, String> = mapOf(
        R.string.raw_restriction_download_disabled to
            "설정에서 RAW 파일 다운로드가 비활성화되어 있습니다.",
        R.string.raw_restriction_free to
            "RAW 파일전송은 준비중입니다.\nJPG로만 촬영해주세요!",
        R.string.raw_restriction_basic to
            "RAW 파일은 PRO 구독에서만 사용할 수 있습니다.\nPRO로 업그레이드하여 고급 RAW 파일 편집 기능을 이용해보세요!",
        R.string.raw_restriction_generic to
            "RAW 파일에 접근할 수 없습니다.",
        R.string.format_unsupported to
            "지원되지 않는 파일 형식입니다.",
        R.string.format_unsupported_in_subscription to
            "현재 구독에서는 지원되지 않는 파일 형식입니다.",
        R.string.upgrade_message_free to
            "BASIC으로 업그레이드: PNG 미지원\nPRO로 업그레이드: RAW 지원",
        R.string.upgrade_message_basic to
            "PRO로 업그레이드: RAW 파일 편집",
        R.string.upgrade_message_pro to
            "이미 최고 등급을 사용 중입니다!",
        R.string.upgrade_message_referrer to
            "추천인 특별 등급입니다! 모든 기능을 이용하실 수 있습니다.",
        R.string.upgrade_message_admin to
            "관리자 등급입니다! 모든 기능을 이용하실 수 있습니다.",
        // PR-G7: 통일 게이팅 메시지 + 티어 라벨.
        R.string.raw_gating_tier_label_FREE to "FREE",
        R.string.raw_gating_tier_label_BASIC to "BASIC",
        R.string.raw_gating_tier_label_PRO to "PRO",
        R.string.raw_gating_tier_label_REFERRER to "REFERRER",
        R.string.raw_gating_tier_label_ADMIN to "ADMIN",
    )

    /**
     * PR-G7: 통일 RAW 게이팅 메시지의 한국어 원문 포맷 (`values-ko/strings_preview.xml` 매칭).
     * `String.format`의 `%1$s`에 티어 라벨이 채워진다.
     */
    private const val UNIFIED_GATING_MESSAGE_KO: String =
        "RAW 파일은 PRO 구독에서 사용 가능합니다. 현재 티어: %1\$s"

    /**
     * PR-G7: 통일 메시지를 한국어 원문 + 티어 라벨로 포매팅.
     * 테스트가 `assertEquals`로 비교할 때 사용.
     */
    fun unifiedGatingMessageKo(tierLabel: String): String =
        UNIFIED_GATING_MESSAGE_KO.format(tierLabel)

    /**
     * 주어진 [context] mockk 에 [KEYS] 전 항목을 한 번에 stub.
     *
     * 호출자는 사전에 `context = mockk()` 만 준비하면 된다(relaxed 여부 무관).
     *
     * PR-G7: `raw_gating_unified_message`는 포맷 인자(`%1$s` 티어 라벨)를 받는
     * `context.getString(resId, vararg)` 형태로 호출되므로 5 티어 각각에 대응하는
     * stub을 함께 등록한다.
     */
    fun applyTo(context: Context) {
        KEYS.forEach { (resId, value) ->
            every { context.getString(resId) } returns value
        }
        // 통일 메시지(포맷 인자 포함) — 5 티어 라벨에 대응.
        listOf("FREE", "BASIC", "PRO", "REFERRER", "ADMIN").forEach { label ->
            every {
                context.getString(R.string.raw_gating_unified_message, label)
            } returns unifiedGatingMessageKo(label)
        }
    }

    /** 자원 키의 한국어 원문 조회. 매핑 부재 시 `IllegalArgumentException`. */
    fun korean(resId: Int): String =
        KEYS[resId] ?: throw IllegalArgumentException(
            "KoreanStringStubs.KEYS 에 매핑이 없는 자원 키: $resId"
        )
}
