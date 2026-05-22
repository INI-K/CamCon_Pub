package com.inik.camcon.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.inik.camcon.R

/**
 * CamCon Design System V2 — Typography
 *
 * Pretendard 단일 폰트 + 무게 콘트라스트 강조 + 정보 밀도 우선.
 * 디스플레이 슬롯 폐기(24sp 이상 거의 안 씀), 11~24sp 범위에 9개 슬롯 집중.
 * 디자인 가이드는 docs/DESIGN_SYSTEM_V2.md §2 참조.
 */

val PretendardFontFamily = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold)
)

private fun pretendard(
    size: Int,
    weight: FontWeight,
    line: Int,
    letterSpacing: Double = 0.0
) = TextStyle(
    fontFamily = PretendardFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = letterSpacing.sp
)

// ---- V2 Typography 슬롯 ----
val HeadingXL = pretendard(24, FontWeight.Bold, 28)
val HeadingL = pretendard(20, FontWeight.SemiBold, 26)
val HeadingM = pretendard(16, FontWeight.SemiBold, 22)
val Body = pretendard(14, FontWeight.Normal, 20, letterSpacing = 0.1)
val BodySmall = pretendard(13, FontWeight.Normal, 18, letterSpacing = 0.1)
val Caption = pretendard(12, FontWeight.Medium, 16, letterSpacing = 0.2)
val Micro = pretendard(11, FontWeight.Medium, 14, letterSpacing = 0.3)
val ButtonText = pretendard(14, FontWeight.SemiBold, 16, letterSpacing = 0.2)
val MonoNumeric = pretendard(12, FontWeight.Normal, 16)

// ---- V1 호환 별칭 ----
val AppTitle = HeadingXL
val BadgeText = pretendard(10, FontWeight.Bold, 12, letterSpacing = 0.5)   // V1 그대로
val CaptionSmall = Micro                                                   // V1 8sp → Micro 11sp

// ---- Material 3 Typography 매핑 ----
val Typography = Typography(
    displayLarge = HeadingXL,
    displayMedium = HeadingXL,
    displaySmall = HeadingXL,

    headlineLarge = HeadingL,
    headlineMedium = HeadingM,
    headlineSmall = HeadingM,

    titleLarge = HeadingM,
    titleMedium = HeadingM,
    titleSmall = Caption,

    bodyLarge = Body,
    bodyMedium = Body,
    bodySmall = BodySmall,

    labelLarge = ButtonText,
    labelMedium = Caption,
    labelSmall = Micro
)
