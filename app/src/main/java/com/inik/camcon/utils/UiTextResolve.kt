package com.inik.camcon.utils

import android.content.Context
import com.inik.camcon.domain.model.UiText

/**
 * [UiText] 를 실제 문자열로 해석하는 공용 확장 함수.
 *
 * Data/Presentation 양쪽(예: CameraEventManager, PhotoPreviewViewModel)이 동일한 방식으로
 * 메시지를 해석할 수 있도록 단일 정의로 둔다. Context 를 받는 프레임워크 헬퍼이므로 domain 이 아닌
 * 레이어 중립 공용 패키지(utils)에 배치한다 — domain 을 순수 Kotlin 으로 유지하기 위함.
 *
 * [UiText.Resource.args] 요소가 다시 [UiText] 인 경우(예: 티어 라벨 resId 를 중첩한 경우)
 * 재귀적으로 resolve 해 `String.format` 인자로 넘긴다.
 */
fun UiText.resolve(context: Context): String = when (this) {
    is UiText.Empty -> ""
    is UiText.Raw -> value
    is UiText.Resource -> if (args.isEmpty()) {
        context.getString(resId)
    } else {
        val resolvedArgs = args.map { arg ->
            if (arg is UiText) arg.resolve(context) else arg
        }
        context.getString(resId, *resolvedArgs.toTypedArray())
    }
}
