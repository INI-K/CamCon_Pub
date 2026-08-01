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
    letterSpacing: Double = 0.0,
    tnum: Boolean = false
) = TextStyle(
    fontFamily = PretendardFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = letterSpacing.sp,
    fontFeatureSettings = if (tnum) TABULAR_NUMERALS else null
)

// 변동 수치는 탭형 숫자로 고정폭을 유지해야 자릿수가 바뀔 때 자리가 흔들리지 않는다.
// Pretendard 4 weight 전부 tnum 피처를 내장하고 있어 Pretendard 슬롯에도 적용 가능하다.
private const val TABULAR_NUMERALS = "tnum"

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
    letterSpacing = letterSpacing.sp,
    fontFeatureSettings = TABULAR_NUMERALS
)

// ---- Display 티어 (Technical HUD 히어로: 스플래시/연결/빈 화면) ----
val DisplayL = pretendard(34, FontWeight.Bold, 40, letterSpacing = -0.4)
val DisplayM = pretendard(28, FontWeight.Bold, 34, letterSpacing = -0.2)

// 카운터 히어로(사진 수·선택 수·가격). Pretendard + tnum이라 CJK 단위가 붙어도 안전하다.
val DisplayNum = pretendard(34, FontWeight.Bold, 40, letterSpacing = -0.4, tnum = true)

// ---- V2 Typography 슬롯 ----
val HeadingXL = pretendard(24, FontWeight.Bold, 28)
val HeadingL = pretendard(20, FontWeight.SemiBold, 26)
val HeadingM = pretendard(16, FontWeight.SemiBold, 22)
// 카드·행 제목. 본문과 크기가 같고 무게로 갈린다(M3 titleSmall이 본문보다 작던 위계 역전 해소).
val HeadingS = pretendard(14, FontWeight.SemiBold, 20, letterSpacing = 0.1)
// 다이얼로그·안내 리드 문장. bodyMedium(14sp)과 2sp 분리해 M3 alias 붕괴를 푼다.
val BodyLarge = pretendard(16, FontWeight.Normal, 24, letterSpacing = 0.1)
val Body = pretendard(14, FontWeight.Normal, 20, letterSpacing = 0.1)
val BodySmall = pretendard(13, FontWeight.Normal, 18, letterSpacing = 0.1)
val Caption = pretendard(12, FontWeight.Medium, 16, letterSpacing = 0.2)
val Micro = pretendard(11, FontWeight.Medium, 14, letterSpacing = 0.3)
val ButtonText = pretendard(14, FontWeight.SemiBold, 16, letterSpacing = 0.2)

// ---- CINE INSTRUMENT 계측기 라벨 ----
// 대문자 트래킹 라벨용. CJK는 스타일 관례만 따르고 `.uppercase()` 호출은 하지 않는다.
val MicroLabel = pretendard(11, FontWeight.Medium, 14, letterSpacing = 1.4)

// ---- 모노스페이스 텔레메트리 (ISO/SS/F/EV/WB 등 카메라 수치) ----
// 화면의 존재 이유가 되는 주 판독값 1개 전용(노출 주값·강도 %). 숫자·영문 전용 —
// FontFamily.Monospace는 시스템 폴백이라 CJK 렌더가 보장되지 않으므로 한글이 들어갈 자리에는 DisplayNum을 쓴다.
val MonoHero = mono(38, FontWeight.Bold, 42, letterSpacing = 0.5)
val MonoReadout = mono(16, FontWeight.Medium, 20, letterSpacing = 1.0)   // HUD 노출 스트립
val MonoNumeric = mono(12, FontWeight.Normal, 16, letterSpacing = 0.5)   // 인라인 탭형 수치
val MonoMicro = mono(11, FontWeight.Normal, 14, letterSpacing = 0.5)     // 미니 수치(썸네일·배지)

// ---- Material 3 Typography 매핑 ----
val Typography = Typography(
    displayLarge = DisplayL,
    displayMedium = DisplayM,
    displaySmall = HeadingXL,

    headlineLarge = HeadingL,
    headlineMedium = HeadingM,
    headlineSmall = HeadingM,

    titleLarge = HeadingL,
    titleMedium = HeadingM,
    titleSmall = HeadingS,

    bodyLarge = BodyLarge,
    bodyMedium = Body,
    bodySmall = BodySmall,

    labelLarge = ButtonText,
    labelMedium = Caption,
    labelSmall = Micro
)
