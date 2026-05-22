package com.inik.camcon.domain.model

import androidx.annotation.StringRes

/**
 * Domain 또는 Data 레이어에서 UI에 표시할 텍스트를 표현할 때 사용하는 sealed 타입.
 *
 * Domain/Data 레이어는 Context 또는 Resources를 직접 참조하지 않고
 * 리소스 ID + 포맷 인자만 전달한다. UI 측에서 `resolve(context)`로 실제 문자열을 얻는다.
 */
sealed interface UiText {

    /** 리소스 ID + 포맷 인자 기반 텍스트. */
    data class Resource(
        @StringRes val resId: Int,
        val args: List<Any> = emptyList()
    ) : UiText

    /** 비어 있음 (메시지 클리어용). */
    data object Empty : UiText

    /**
     * 동적 raw 문자열. 가급적 사용하지 않는다.
     * 네이티브 에러 코드 등 i18n 불가한 디버그 문자열에만 한정한다.
     */
    data class Raw(val value: String) : UiText
}
