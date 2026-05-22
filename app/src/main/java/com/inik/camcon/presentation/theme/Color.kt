package com.inik.camcon.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * CamCon Design System V2 — Editorial Pro Tool
 *
 * Capture One / Lightroom Classic 풍 모노크롬 + 앰버 골드 액센트.
 * 디자인 가이드는 docs/DESIGN_SYSTEM_V2.md 참조.
 *
 * V1 이름(Primary/Background/Surface/TextPrimary 등)은 유지하되 색만 V2로 교체.
 * 신규 V2 토큰(Surface0-4, TextPrimary~Disabled, Accent*, Semantic)은 별도 추가.
 */

// ---- V2 Neutral Surface Tier (5단계) ----
val Surface0 = Color(0xFF0E0E0E)   // 앱 배경
val Surface1 = Color(0xFF1A1A1A)   // 메인 패널
val Surface2 = Color(0xFF232323)   // 카드 / 시트
val Surface3 = Color(0xFF2E2E2E)   // 입력 / 포커스
val Surface4 = Color(0xFF3A3A3A)   // 호버 / 액티브
// WCAG 2.2 SC 1.4.11 비텍스트 대비 3:1 충족을 위해 Surface0(#0E0E0E)
// 위에서 약 3.2:1 명도 차이를 갖는 #4D4D4D 으로 상향. (이전 #2A2A2A 는 약 1.5:1.)
val DividerLine = Color(0xFF4D4D4D)

// ---- V2 Text Tier (4단계) ----
val TextPrimaryV2 = Color(0xFFF2F2F2)
val TextSecondaryV2 = Color(0xFFB3B3B3)
val TextTertiary = Color(0xFF808080)
val TextDisabled = Color(0xFF4D4D4D)

// ---- V2 Accent (앰버 골드 유지) ----
val Accent = Color(0xFFE8A245)
val AccentStrong = Color(0xFFF0B865)
val AccentMuted = Color(0x2EE8A245)   // 18% alpha
val OnAccent = Color(0xFF1C1005)

// ---- V2 Semantic (덜 형광) ----
val SuccessV2 = Color(0xFF5DB075)
val WarningV2 = Color(0xFFE0A33E)
val ErrorV2 = Color(0xFFD9534F)
val Info = Color(0xFF5A93C2)

// ---- V1 호환 토큰 — 같은 이름 유지, 색만 V2로 교체 ----
// NOTE: V1 별칭에는 @Deprecated가 부여되어 있다. 호출자는 경고만 받고 컴파일은 통과.
//       마이그레이션이 끝나면 별칭 블록 전체를 일괄 제거할 예정.
@Deprecated("Use V2 token Accent", ReplaceWith("Accent"))
val Primary = Accent

@Deprecated("Use V2 token AccentStrong", ReplaceWith("AccentStrong"))
val PrimaryLight = AccentStrong

@Deprecated("Use V2 token Accent or define a dedicated darker accent", ReplaceWith("Accent"))
val PrimaryDark = Color(0xFFCC8830)

@Deprecated("Use V2 token Surface0", ReplaceWith("Surface0"))
val Background = Surface0

@Deprecated("Use V2 token Surface1", ReplaceWith("Surface1"))
val BackgroundSurface = Surface1

@Deprecated("Use V2 token Surface1", ReplaceWith("Surface1"))
val Surface = Surface1

@Deprecated("Use V2 token Surface2", ReplaceWith("Surface2"))
val SurfaceElevated = Surface2

@Deprecated("Use V2 token TextPrimaryV2", ReplaceWith("TextPrimaryV2"))
val TextPrimary = TextPrimaryV2

@Deprecated("Use V2 token TextSecondaryV2", ReplaceWith("TextSecondaryV2"))
val TextSecondary = TextSecondaryV2

@Deprecated("Use V2 token TextTertiary", ReplaceWith("TextTertiary"))
val TextMuted = TextTertiary

@Deprecated("Use V2 token DividerLine", ReplaceWith("DividerLine"))
val Border = DividerLine

@Deprecated("Use V2 token OnAccent", ReplaceWith("OnAccent"))
val OnPrimary = OnAccent

@Deprecated("Use V2 token SuccessV2", ReplaceWith("SuccessV2"))
val Success = SuccessV2

@Deprecated("Use V2 token ErrorV2", ReplaceWith("ErrorV2"))
val Error = ErrorV2

@Deprecated("Use V2 token WarningV2", ReplaceWith("WarningV2"))
val Warning = WarningV2

@Deprecated("Use Surface0 with an explicit alpha modifier in Compose")
val Overlay = Color(0xB30E0E0E)   // 70% alpha Surface0

// ---- V1에서 도입된 Dark* 토큰 — V2 surface/text tier로 흡수 ----
// 호출처는 DarkThemeComponents.kt 1곳. V2 마이그레이션 중 별칭으로 유지.
@Deprecated("Use V2 token Surface2", ReplaceWith("Surface2"))
val DarkCardBackground = Surface2

@Deprecated("Use V2 token DividerLine", ReplaceWith("DividerLine"))
val DarkCardBorder = DividerLine

@Deprecated("Use V2 token TextSecondaryV2", ReplaceWith("TextSecondaryV2"))
val DarkBodyText = TextSecondaryV2

@Deprecated("Use V2 token Accent", ReplaceWith("Accent"))
val DarkTitleText = Accent

@Deprecated("Use V2 token TextPrimaryV2", ReplaceWith("TextPrimaryV2"))
val DarkIconButtonTint = TextPrimaryV2

@Deprecated("Use V2 token TextPrimaryV2", ReplaceWith("TextPrimaryV2"))
val DarkStatusBadgeText = TextPrimaryV2

@Deprecated("Use V2 token OnAccent", ReplaceWith("OnAccent"))
val DarkTabSelectedText = OnAccent

@Deprecated("Use V2 token TextSecondaryV2", ReplaceWith("TextSecondaryV2"))
val DarkTabUnselectedText = TextSecondaryV2

@Deprecated("Use V2 token Surface0", ReplaceWith("Surface0"))
val DarkBackgroundGradientStart = Surface0

@Deprecated("Use V2 token Surface1", ReplaceWith("Surface1"))
val DarkBackgroundGradientMid = Surface1

@Deprecated("Use V2 token Surface0", ReplaceWith("Surface0"))
val DarkBackgroundGradientEnd = Surface0

@Deprecated("Use V2 token Surface1", ReplaceWith("Surface1"))
val DarkTopBarGradientStart = Surface1

@Deprecated("Use V2 token Surface0", ReplaceWith("Surface0"))
val DarkTopBarGradientEnd = Surface0

@Deprecated("Use V2 token Surface3", ReplaceWith("Surface3"))
val DarkIconButtonBackground = Surface3

@Deprecated("Use V2 token Surface1", ReplaceWith("Surface1"))
val DarkTabRowBackground = Surface1

@Deprecated("Use V2 token DividerLine", ReplaceWith("DividerLine"))
val DarkTabRowBorder = DividerLine

@Deprecated("Use V2 token Accent", ReplaceWith("Accent"))
val DarkTabSelectedBackground = Accent

@Deprecated("Use V2 token AccentMuted", ReplaceWith("AccentMuted"))
val DarkFilterChipSelectedBackground = AccentMuted

@Deprecated("Use V2 token Surface2", ReplaceWith("Surface2"))
val DarkFilterChipUnselectedBackground = Surface2

@Deprecated("Use V2 token Accent", ReplaceWith("Accent"))
val DarkFilterChipSelectedBorder = Accent

@Deprecated("Use V2 token DividerLine", ReplaceWith("DividerLine"))
val DarkFilterChipUnselectedBorder = DividerLine

@Deprecated("Use V2 token TextDisabled", ReplaceWith("TextDisabled"))
val DarkFilterChipLockedText = TextDisabled

// ---- FeatureBadge 데코 (V2 시맨틱 톤으로 정제) ----
val FeatureBadgeTimeLapse = Color(0xFF8A6FB0)   // 차분한 퍼플
val FeatureBadgeBurst = Color(0xFFD68A3D)       // 앰버 변형
