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

// ---- V2 Surface Tier (5단계) — CINE INSTRUMENT: 앰버 모니터, 순흑 계측기 ----
// 순수 무채색 회색(R=G=B)을 폐기하고 미세한 쿨 캐스트를 부여해 '계기판' 톤을 낸다.
// 깊이는 표면 tier 차이 + 0.5dp 헤어라인 + 활성 시 앰버 엣지 1px로만 낸다(그림자 0).
val Surface0 = Color(0xFF050607)   // 앱 배경 — 순흑 하한(더 어둡게 금지, OLED 스미어)
val Surface1 = Color(0xFF0C0E11)   // 메인 패널
val Surface2 = Color(0xFF14171C)   // 카드 / 시트
val Surface3 = Color(0xFF1C2026)   // 입력 / 포커스
val Surface4 = Color(0xFF252A31)   // 호버 / 액티브
// tier 경계를 그리는 0.5dp 헤어라인. 발광 대비를 위해 대비를 낮춘 다크 슬레이트.
val DividerLine = Color(0xFF262C33)
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

// ---- FeatureBadge 데코 (V2 시맨틱 톤으로 정제) ----
val FeatureBadgeTimeLapse = Color(0xFF8A6FB0)   // 차분한 퍼플
val FeatureBadgeBurst = Color(0xFFD68A3D)       // 앰버 변형
val FeatureBadgeBracketing = Color(0xFF5AA9E0)  // 브라케팅 — Info 계열(Color.Cyan 오염 대체)
