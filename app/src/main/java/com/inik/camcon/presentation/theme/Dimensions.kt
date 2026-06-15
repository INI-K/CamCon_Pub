package com.inik.camcon.presentation.theme

import androidx.compose.ui.unit.dp

/**
 * CamCon Design System V2 — Dimensions
 *
 * 8pt grid, V1 대비 12% 촘촘. Padding/Spacing 단일화.
 * 디자인 가이드는 docs/DESIGN_SYSTEM_V2.md §3 참조.
 */

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val base = 14.dp   // V2 카드/패널 표준 패딩
    val lg = 20.dp
    val xl = 28.dp
}

/** V1 호환 별칭 — 점진 폐기. 신규 코드는 `Spacing` 사용. */
object Padding {
    val xs = Spacing.xs
    val sm = Spacing.sm
    val md = Spacing.md
    val base = Spacing.base
    val lg = Spacing.lg
    val xl = Spacing.xl
}

object IconSize {
    val xs = 12.dp
    val sm = 16.dp
    val md = 20.dp
    val lg = 24.dp
    val xl = 32.dp
}

/**
 * WCAG 2.2 Success Criterion 2.5.8 (Target Size Minimum) — 최소 24×24px.
 * Material 3 권장 최소는 48dp. AA 기준 안전선은 44dp.
 * `min` 은 토글/탭 가능한 모든 인터랙티브 요소의 절대 하한이다.
 */
object TouchTarget {
    val min = 44.dp
    val std = 44.dp
    val lg = 48.dp
    val xl = 52.dp
}

object Radius {
    val sm = 4.dp    // 인풋/버튼/칩 표준
    val md = 6.dp    // 카드 표준
    val lg = 8.dp    // 다이얼로그
    val xl = 12.dp   // BottomSheet 상단
}

object StrokeWidth {
    val hairline = 0.5.dp
    val thin = 1.dp
    val regular = 1.5.dp
    val thick = 2.dp
    val heavy = 2.5.dp
    val focusRing = 2.dp   // 애니메이션 포커스 링
}

/** Technical HUD 깊이 계층 — 그림자/elevation 토큰. 평면 박스 탈피. */
object Elevation {
    val none = 0.dp
    val low = 2.dp      // 정지 카드
    val medium = 6.dp   // 떠 있는 패널 / 시트
    val high = 12.dp    // 다이얼로그 / HUD 레일
    val overlay = 20.dp // 셔터 / 모달 오버레이
}

/** 카메라 도메인 전용 치수. */
object CameraSpec {
    val shutterOuter = 88.dp
    val shutterInner = 68.dp
    val shutterRing = 4.dp
    val badgeHeight = 24.dp
    val thumbnailGrid = 120.dp
    val featuredThumbnailAspect = 16f / 9f
    val liveviewIndicator = 8.dp
    val statusBarHeight = 32.dp
}
