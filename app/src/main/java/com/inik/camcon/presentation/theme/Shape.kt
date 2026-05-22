package com.inik.camcon.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * CamCon Design System V2 — Shape
 *
 * Tight radius (4-12dp). 큰 라운드 폐기.
 * 디자인 가이드는 docs/DESIGN_SYSTEM_V2.md §5 참조.
 */

val Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),   // 인풋/버튼/칩
    small = RoundedCornerShape(6.dp),        // 카드
    medium = RoundedCornerShape(8.dp),       // 다이얼로그
    large = RoundedCornerShape(12.dp),       // BottomSheet
    extraLarge = RoundedCornerShape(16.dp)   // 대형 시트 (드물게)
)
