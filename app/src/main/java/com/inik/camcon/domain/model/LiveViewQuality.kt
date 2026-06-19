package com.inik.camcon.domain.model

/**
 * 라이브뷰 미리보기 화질. liveviewsize 위젯 choice 선택 전략.
 * value 는 DataStore 저장 / JNI 전달용 안정 정수 (재정렬 금지).
 */
enum class LiveViewQuality(val value: Int) {
    SPEED(0),     // liveviewsize choice[0] (최소, 기존 강제 동작)
    BALANCED(1),  // 해상도 강제 안 함 (카메라 현재 값 유지)
    QUALITY(2);   // liveviewsize choice[count-1] (최대) — 기본값

    companion object {
        // 손상/미지 정수의 보수적 폴백 — 해상도 강제 안 하는 BALANCED 유지(기본값 QUALITY와 무관).
        fun fromValue(v: Int): LiveViewQuality =
            values().firstOrNull { it.value == v } ?: BALANCED
    }
}
