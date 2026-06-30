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
 * Pretendard 단일 폰트 + 무게/스케일 콘트라스트 + Monospace 텔레메트리.
 * Display 티어(34/28sp) 복원 + 11~24sp 9개 슬롯 + Monospace 수치 readout.
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

private fun mono(
    size: Int,
    weight: FontWeight,
    line: Int,
    letterSpacing: Double = 0.0
) = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = letterSpacing.sp
)

// ---- Display 티어 (Technical HUD 히어로: 스플래시/연결/빈 화면) ----
val DisplayL = pretendard(34, FontWeight.Bold, 40, letterSpacing = -0.4)
val DisplayM = pretendard(28, FontWeight.Bold, 34, letterSpacing = -0.2)

// ---- V2 Typography 슬롯 ----
val HeadingXL = pretendard(24, FontWeight.Bold, 28)
val HeadingL = pretendard(20, FontWeight.SemiBold, 26)
val HeadingM = pretendard(16, FontWeight.SemiBold, 22)
val Body = pretendard(14, FontWeight.Normal, 20, letterSpacing = 0.1)
val BodySmall = pretendard(13, FontWeight.Normal, 18, letterSpacing = 0.1)
val Caption = pretendard(12, FontWeight.Medium, 16, letterSpacing = 0.2)
val Micro = pretendard(11, FontWeight.Medium, 14, letterSpacing = 0.3)
val ButtonText = pretendard(14, FontWeight.SemiBold, 16, letterSpacing = 0.2)

// ---- 모노스페이스 텔레메트리 (ISO/SS/F/EV/WB 등 카메라 수치) ----
val MonoReadout = mono(16, FontWeight.Medium, 20, letterSpacing = 0.5)   // HUD 노출 스트립
val MonoNumeric = mono(12, FontWeight.Normal, 16, letterSpacing = 0.5)   // 인라인 탭형 수치

// ---- Material 3 Typography 매핑 ----
val Typography = Typography(
    displayLarge = DisplayL,
    displayMedium = DisplayM,
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
