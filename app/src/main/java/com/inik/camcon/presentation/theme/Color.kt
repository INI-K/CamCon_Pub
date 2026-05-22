package com.inik.camcon.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * CamCon Cinema Dark + Amber Color Palette
 * 전문가용 카메라 앱 — 근 OLED 블랙 + 렌즈 앰버 골드
 */

// Primary - 따뜻한 앰버 골드 (렌즈/조리개 느낌)
val Primary = Color(0xFFE8A245)
val PrimaryLight = Color(0xFFF0B865)
val PrimaryDark = Color(0xFFCC8830)

// Background - 근 OLED 블랙
val Background = Color(0xFF0A0A0E)
val BackgroundSurface = Color(0xFF0F0F14)

// Surface - 차분한 다크
val Surface = Color(0xFF131317)
val SurfaceElevated = Color(0xFF1E1E26)

// Text - 약간 따뜻한 화이트 (차가운 블루 제거)
val TextPrimary = Color(0xFFF0EEE9)
val TextSecondary = Color(0xFF9A9590)
val TextMuted = Color(0xFF585450)

// Border - 반투명 유리 느낌 (rgba 255,255,255, 0.08)
val Border = Color(0x14FFFFFF)

// On-Primary - 앰버 버튼 위 어두운 텍스트
val OnPrimary = Color(0xFF1C1005)

// Status
val Success = Color(0xFF4ADE80)
val Error = Color(0xFFFB7185)
val Warning = Color(0xFFFBBF24)

// Transparent overlays
val Overlay = Color(0x800A0A0E)

// === DarkThemeComponents 전용 토큰 (Cinema Dark blue-grey 계열) ===
// 카드 / 텍스트
val DarkCardBackground = Color(0xE6151C2A)
val DarkCardBorder = Color(0x44FFD1A8)
val DarkBodyText = Color(0xFFB8C0CF)
val DarkTitleText = Color(0xFFFFC892)
val DarkIconButtonTint = Color(0xFFFFD6AE)
val DarkStatusBadgeText = Color(0xFFFAF3EA)

// 탭 / 칩 텍스트
val DarkTabSelectedText = Color(0xFFFFD7B1)
val DarkTabUnselectedText = Color(0xFFBAC2D2)

// 배경 그라데이션
val DarkBackgroundGradientStart = Color(0xFF0E121A)
val DarkBackgroundGradientMid = Color(0xFF151B27)
val DarkBackgroundGradientEnd = Color(0xFF111722)

// 탑바 그라데이션 (반투명)
val DarkTopBarGradientStart = Color(0xCC1A2232)
val DarkTopBarGradientEnd = Color(0xCC131A27)

// 반투명 인터랙션 배경/보더
val DarkIconButtonBackground = Color(0x55374455)
val DarkTabRowBackground = Color(0x66131B2A)
val DarkTabRowBorder = Color(0x66FFD1A8)
val DarkTabSelectedBackground = Color(0x66A14F1D)

// 필터칩
val DarkFilterChipSelectedBackground = Color(0x663D4457)
val DarkFilterChipUnselectedBackground = Color(0x33131B2A)
val DarkFilterChipSelectedBorder = Color(0x99FFC88C)
val DarkFilterChipUnselectedBorder = Color(0x558D99AD)
val DarkFilterChipLockedText = Color(0x66BAC2D2)

// === Feature Badge 데코레이션 색상 ===
val FeatureBadgeTimeLapse = Color(0xFF9C27B0)
val FeatureBadgeBurst = Color(0xFFFF9800)
