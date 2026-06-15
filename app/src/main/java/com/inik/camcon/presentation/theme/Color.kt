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

// ---- V2 Surface Tier (5단계) — Technical HUD: 쿨 블랙, 최고 대비 ----
// 순수 무채색 회색(R=G=B)을 폐기하고 미세한 쿨 캐스트를 부여해 '계기판' 톤을 낸다.
val Surface0 = Color(0xFF0A0B0D)   // 앱 배경 (쿨 니어블랙)
val Surface1 = Color(0xFF131519)   // 메인 패널
val Surface2 = Color(0xFF1D2128)   // 카드 / 시트
val Surface3 = Color(0xFF272C34)   // 입력 / 포커스
val Surface4 = Color(0xFF353B42)   // 호버 / 액티브
// 엔지니어드 그리드용 하이라인/구분선. 쿨 슬레이트(WCAG 비텍스트 대비 우선).
val DividerLine = Color(0xFF4A525C)
// HUD 액티브 패널 1px 엣지라이트 (앰버 40%).
val AccentEdge = Color(0x66E8A245)

// ---- V2 Text Tier (4단계) — 쿨 틴트, 최고 대비 ----
val TextPrimaryV2 = Color(0xFFF6F7F8)
val TextSecondaryV2 = Color(0xFFB6BCC6)
val TextTertiary = Color(0xFF7E848E)
val TextDisabled = Color(0xFF474D56)

// ---- V2 Accent (앰버 골드 유지) ----
val Accent = Color(0xFFE8A245)
val AccentStrong = Color(0xFFF0B865)
val AccentMuted = Color(0x2EE8A245)   // 18% alpha
val OnAccent = Color(0xFF1C1005)

// ---- V2 Semantic — Technical HUD 시그널 트리오 ----
// 앰버(주액션)=Accent / 그린(라이브·성공) / 레드(녹화·에러). 전부 다크 유지.
val SuccessV2 = Color(0xFF4FD18B)   // 라이브 / 연결 / 성공
val WarningV2 = Color(0xFFE0A33E)   // 경고
val ErrorV2 = Color(0xFFFF5A52)     // 녹화 / 에러 / 위험
val Info = Color(0xFF5AA9E0)        // 정보 (테크니컬 시안블루)

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
val Overlay = Color(0xB30A0B0D)   // 70% alpha Surface0

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
