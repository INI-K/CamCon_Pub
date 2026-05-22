package com.inik.camcon.domain.model

/**
 * 노출 보정(EV) 상태.
 *
 * @property current 현재 값 (예: "0", "+1/3", "-2/3"). libgphoto2 widget이 돌려준 raw 문자열.
 * @property available 카메라가 지원하는 선택지(RADIO/MENU choices) 순서대로 정렬된 목록.
 */
data class ExposureCompensation(
    val current: String,
    val available: List<String>
)
