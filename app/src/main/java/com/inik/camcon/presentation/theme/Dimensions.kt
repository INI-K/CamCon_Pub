package com.inik.camcon.presentation.theme

import androidx.compose.ui.unit.dp

/**
 * CamCon 디자인 토큰 — 사이즈/여백/터치 타깃/라디우스/카메라 특화 스펙
 * Color.kt(색상)·Type.kt(타이포)와 함께 디자인 시스템의 3대 축을 구성한다.
 */

object IconSize {
    val xs = 12.dp
    val sm = 16.dp
    val md = 20.dp
    val lg = 24.dp
    val xl = 32.dp
}

object Padding {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val base = 16.dp
    val lg = 20.dp
    val xl = 24.dp
}

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val base = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** Material 3 권장 최소 터치 타깃은 48dp. min=40dp는 보조 컨트롤에만 사용. */
object TouchTarget {
    val min = 40.dp
    val std = 44.dp
    val lg = 48.dp
    val xl = 52.dp
}

object Radius {
    val sm = 4.dp
    val md = 8.dp
    val lg = 12.dp
    val xl = 16.dp
}

object StrokeWidth {
    val hairline = 0.5.dp
    val thin = 1.dp
    val regular = 1.5.dp
    val thick = 2.dp
    val heavy = 2.5.dp
}

/** 카메라 도메인 전용 치수. DSLR 메타포(이중 링 셔터·배지·라이브뷰 인디케이터). */
object CameraSpec {
    val shutterOuter = 88.dp
    val shutterInner = 68.dp
    val shutterRing = 4.dp
    val badgeHeight = 24.dp
    val thumbnailGrid = 120.dp
    val featuredThumbnailAspect = 16f / 9f
    val liveviewIndicator = 8.dp
}
