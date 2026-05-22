package com.inik.camcon.presentation.theme

import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.compositionLocalOf

/**
 * 현재 윈도우 사이즈 클래스를 트리에 전파한다.
 *
 * MainActivity의 setContent에서 calculateWindowSizeClass(this)로 계산한 값을
 * CompositionLocalProvider로 provide 한다. CamConTheme은 변경하지 않는다.
 *
 * 사용 예시:
 * ```
 * val sizeClass = LocalWindowSizeClass.current
 * when (sizeClass.widthSizeClass) {
 *     WindowWidthSizeClass.Compact -> { ... }
 *     WindowWidthSizeClass.Medium -> { ... }
 *     WindowWidthSizeClass.Expanded -> { ... }
 * }
 * ```
 *
 * Provider가 없는 트리(프리뷰 등)에서 접근하면 의도적으로 throw 한다 —
 * 누락된 provide를 일찍 발견하기 위함. 프리뷰에서 분기 로직을 검증하려면
 * 명시적으로 [LocalWindowSizeClass] provides 하라.
 */
val LocalWindowSizeClass = compositionLocalOf<WindowSizeClass> {
    error("LocalWindowSizeClass not provided. Wrap content with CompositionLocalProvider.")
}

/**
 * Compact 너비 여부 (phone portrait, ≤ 600dp).
 */
val WindowSizeClass.isCompactWidth: Boolean
    get() = widthSizeClass == WindowWidthSizeClass.Compact

/**
 * Medium 이상 너비 여부 (foldable, tablet portrait/landscape).
 * NavigationRail / 가로 분기 컨텐츠를 사용해야 하는 폭.
 */
val WindowSizeClass.isMediumOrWider: Boolean
    get() = widthSizeClass == WindowWidthSizeClass.Medium ||
            widthSizeClass == WindowWidthSizeClass.Expanded
