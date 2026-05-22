# CamCon Design System V2 — Editorial Pro Tool

Phase 1-8(V1)의 일관성 작업을 토대로, 시각 정체성을 Capture One/Lightroom Classic 풍 **Editorial Pro Tool** 톤으로 통째 재정의한다. 사용자 검토 후 Phase 10-13에서 코드 일괄 적용.

> 이전 시스템 V1은 `app/src/main/java/com/inik/camcon/presentation/theme/{Color,Type,Dimensions,Shape,Theme}.kt`. V2는 같은 파일을 재작성한다(파일 추가 없이).

---

## 0. 결정 요약

| 항목 | 결정 |
|------|------|
| 비주얼 톤 | Editorial Pro Tool (Capture One·Lightroom Classic) |
| 액센트 컬러 | **앰버 골드 `#E8A245` 유지** (카메라 도메인 정체성) |
| 폰트 | **Pretendard 4 weight 단일 유지**. 숫자 정렬은 `fontFeatureSettings("tnum")`로 보완 |
| 진행 방식 | 시스템 통째 재정의 → 일괄 적용 |
| 문서화 | 이 문서 (`docs/DESIGN_SYSTEM_V2.md`) |

---

## 1. 컬러 팔레트

### 1.1 Neutral (모노크롬 5 surface tier + 4 text tier)

| 토큰 | Hex | 용도 |
|------|-----|------|
| `Surface0` | `#0E0E0E` | 앱 배경 (Scaffold container) |
| `Surface1` | `#1A1A1A` | 메인 패널 (Lightroom 베이스) |
| `Surface2` | `#232323` | 카드 / BottomSheet |
| `Surface3` | `#2E2E2E` | 입력 / 포커스 |
| `Surface4` | `#3A3A3A` | 호버 / 액티브 |
| `DividerLine` | `#2A2A2A` | 1px hairline |
| `TextPrimary` | `#F2F2F2` | 주 텍스트 |
| `TextSecondary` | `#B3B3B3` | 보조 |
| `TextTertiary` | `#808080` | 캡션 |
| `TextDisabled` | `#4D4D4D` | 비활성 |

### 1.2 Accent (단일)

| 토큰 | Hex | 용도 |
|------|-----|------|
| `Accent` | `#E8A245` | Primary CTA, 활성 상태, 셔터 강조 |
| `AccentMuted` | `#E8A245` @ 18% | Container/Selected bg |
| `AccentStrong` | `#F0B865` | Hover/Focus 강조 (보조) |
| `OnAccent` | `#1C1005` | Accent 위 텍스트 |

### 1.3 Semantic (덜 형광)

| 토큰 | Hex |
|------|-----|
| `Success` | `#5DB075` |
| `Warning` | `#E0A33E` |
| `Error` | `#D9534F` |
| `Info` | `#5A93C2` |

### 1.4 Material 3 ColorScheme 매핑

```
primary = Accent
onPrimary = OnAccent
primaryContainer = AccentMuted
secondary = AccentStrong
background = Surface0
surface = Surface1
surfaceVariant = Surface2
surfaceContainerLow = Surface1
surfaceContainer = Surface2
surfaceContainerHigh = Surface3
surfaceContainerHighest = Surface4
onBackground / onSurface = TextPrimary
onSurfaceVariant = TextSecondary
outline = DividerLine
outlineVariant = Surface3
error = Error
onError = TextPrimary
scrim = #B3000000
```

---

## 2. 타이포그래피

폰트는 **Pretendard 단일**(Regular/Medium/SemiBold/Bold). 숫자 정렬 필요 시 `fontFeatureSettings = "tnum"` 적용.

### 2.1 슬롯 (V1 대비 디스플레이 슬롯 폐기, 정보 밀도 우선)

| 슬롯 | sp | weight | lineHeight | 용도 |
|------|-----|--------|-----------|------|
| `HeadingXL` | 24 | Bold | 28 | 화면 타이틀(드물게) |
| `HeadingL` | 20 | SemiBold | 26 | 섹션 헤더 |
| `HeadingM` | 16 | SemiBold | 22 | 카드 헤더 |
| `Body` | 14 | Regular | 20 | 본문 표준 |
| `BodySmall` | 13 | Regular | 18 | 보조 |
| `Caption` | 12 | Medium | 16 | 라벨 |
| `Micro` | 11 | Medium | 14 | 메타 |
| `ButtonText` | 14 | SemiBold | 16 | CTA |
| `MonoNumeric` | 12 | Regular | 16 | EXIF/숫자 (tnum) |

### 2.2 Material 3 Typography 매핑

```
displayLarge/Medium/Small = HeadingXL (다 24sp Bold로 강등)
headlineLarge/Medium/Small = HeadingL / HeadingM / HeadingM
titleLarge = HeadingM (16sp SemiBold)
titleMedium = HeadingM
titleSmall = Caption
bodyLarge = Body
bodyMedium = Body
bodySmall = BodySmall
labelLarge = ButtonText
labelMedium = Caption
labelSmall = Micro
```

---

## 3. 스페이싱 (8pt grid, V1 대비 12% 촘촘)

| 토큰 | dp | 용도 |
|------|-----|------|
| `xs` | 4 | 라벨↔아이콘 |
| `sm` | 8 | 인라인 요소 간 |
| `md` | 12 | 그룹 내 |
| `base` | 14 | **카드/패널 표준 패딩** |
| `lg` | 20 | 섹션 간 |
| `xl` | 28 | 화면 외곽 |

> V1의 `Padding`과 `Spacing` 중복 폐기, **단일 `Spacing`으로 통합**.

---

## 4. Surface 시스템 — Flat + Border

- `elevation = 0.dp` 표준 (그림자 폐지)
- 깊이는 surface tier(0→4) + 1px `DividerLine`으로만 표현
- 예외: BottomSheet는 `elevation = 8.dp` 1회 허용

---

## 5. Shape — Tight Radius

| 컴포넌트 | Radius |
|---------|--------|
| 인풋/버튼/칩 | 4.dp |
| 카드 | 6.dp |
| BottomSheet 상단 | 12.dp |
| 다이얼로그 | 8.dp |

V1의 12-36dp 큰 라운드 폐기.

---

## 6. 모션 표준

| 케이스 | duration | easing |
|--------|----------|--------|
| 마이크로 (toggle, press) | 100ms | `FastOutLinearIn` |
| 표준 (state change) | 200ms | `FastOutSlowIn` |
| 패널 전환 | 250ms | `FastOutSlowIn` |
| 시트 진입/탈출 | 300ms | `FastOutSlowIn` |

**규칙**: `transform`/`opacity`만, 큰 페이드/슬라이드 폐기, 반복 펄싱 최소 사용.

---

## 7. 컴포넌트 카탈로그 (Phase 11)

| 컴포넌트 | 신규/재작성 | 핵심 사양 |
|---------|------------|---------|
| `Surface` | 재 | tier(0-4) 파라미터, 1px border 옵션 |
| `DividerLine` | 신 | `Modifier.height(1.dp).background(DividerLine)` |
| `Section` | 신 | 헤더(HeadingL) + 컨텐츠 + 하단 spacing.lg |
| `PrimaryButton` | 재 | Accent bg, OnAccent text, 4dp radius, h=40dp |
| `SecondaryButton` | 재 | Border 1px DividerLine, TextPrimary, 4dp radius |
| `IconButton` | 재 | 40dp 터치 타깃, 24dp 아이콘 |
| `Chip` | 재 | h=28dp, 4dp radius, Caption text |
| `FilterChip` | 재 | Outlined 기본, Accent 선택 시 |
| `Card` | 재 | Surface2 bg, 6dp radius, 0 elev, optional 1px border |
| `RowItem` | 신 | Settings 행: leading icon + label + trailing(switch/value/chev) |
| `StatusIndicator` | 신 | 8dp dot + Caption label. variants: idle/connecting/connected/error |
| `Toast` | 재 | Surface3 + Accent left bar, 4dp radius |
| `SkeletonLoader` | 신 | shimmer Accent@10% over Surface2 |
| `EmptyState` | 신 | 32dp icon + HeadingM + BodySmall + optional CTA |
| `ProgressBar` | 신 | 선형 1px height, Accent fill |

---

## 8. UX 페인포인트 해결

### 8.1 "주요 액션까지 탭이 많다" → **1탭 도달 원칙**
- CameraControl: 셔터·라이브뷰·모드 전환을 메인 화면 직접 노출 (모달 폐지)
- PhotoPreview: 다운로드를 longpress 다중선택 진입 없이 단일 사진 풀스크린 액션바에서 즉시
- PtpipConnection: AP/STA/Hotspot 모드 진입을 탭이 아닌 카드 클릭 1회로

### 8.2 "상태/피드백 부족" → **글로벌 StatusBar**
- 화면 상단 32dp 영역에 StatusIndicator + 진행 표시 + 토스트 통합
- 모든 화면에서 동일 위치 (BottomNav와 짝)
- 4가지 variant: `idle`(투명) / `connecting`(Accent 회전) / `connected`(Accent dot) / `error`(Error bar)

### 8.3 "정보 밀도 안 맞음" → **화면별 밀도 등급**

| 등급 | 화면 | spacing 표준 |
|------|------|-------------|
| Dense | EXIF, Settings, CameraSettings | `base=12dp`, line-height tight |
| Standard | PhotoGrid, PtpipConnection | `base=14dp` (기본) |
| Airy | Login, Splash, EmptyState | `base=20dp`, 여백 충분 |

### 8.4 "터치 타깃/제스처 어색" → **앱 전체 통일 규칙**
- 최소 터치 타깃 **44dp** (보조 컨트롤 40dp 허용, 그 이하 금지)
- 사진 그리드: tap=풀스크린, longpress=다중선택 진입, swipe-down=시트닫기
- BottomSheet: drag handle 표시, 외부 스크림 탭=닫기
- IconButton: ripple 명시(Pretendard tnum)

---

## 9. 화면별 골격 (Phase 12-13)

### CameraControl (재구성, 1탭 도달 원칙)
```
┌─────────────────────────────────────┐
│ StatusBar  ●  Connected · USB ★65%  │ 32dp
├─────────────────────────────────────┤
│                                     │
│         Live View Area              │ 1f weight
│                                     │
├─────────────────────────────────────┤
│ Mode: [Single] Burst Timelapse ...  │ 44dp (LazyRow)
│                                     │
│       ●●● Shutter (88dp)            │
│   [Settings] [Gallery] [More]       │
└─────────────────────────────────────┘
```

### PhotoPreview
```
StatusBar  ·  124 photos · Filter: All ▼
─────────────────────────────────────────
[2-3 col Staggered Grid, gap 4dp]
─────────────────────────────────────────
(multi-select 진입 시 하단 액션바)
```

### PtpipConnection (가로 분할, Expanded 시)
```
┌────────┬────────────────────────────┐
│ Mode   │ Selected mode content       │
│ ─AP    │                             │
│  STA   │ [SSID list]                 │
│  Hot.. │                             │
└────────┴────────────────────────────┘
```

### Settings (Lightroom 환경설정 톤)
```
Section: 일반
─ 알림                       [Switch]
─ 언어                       한국어 ›
─ 테마                       다크 (고정)
Section: 구독
─ 플랜                       PRO ›
─ RAW 다운로드 제한          해제됨
```

---

## 10. 마이그레이션 로드맵

| Phase | 산출물 | 의존성 |
|-------|--------|--------|
| **10** | `Color.kt`/`Type.kt`/`Dimensions.kt`/`Shape.kt`/`Theme.kt` V2로 재작성. 기존 `Dark*` 토큰은 ColorScheme 슬롯으로 흡수 후 제거 | Phase 9 |
| **11** | 컴포넌트 카탈로그 15종 신규/재작성. `presentation/ui/components/v2/` 폴더에 배치 후 검증 끝나면 기존 위치로 이동 | Phase 10 |
| **12** | CameraControl, PhotoPreview, PtpipConnection 3개 메인 화면 리뉴얼 | Phase 11 |
| **13** | Splash/Login/Settings/MainActivity 다이얼로그 통일, 잔존 V1 토큰 클린업, 8개 언어 strings 재정합, 통합 빌드 검증 | Phase 12 |

각 Phase 종료 시 BUILD SUCCESSFUL 필수, 회귀 시 즉시 롤백·수정.

---

## 11. 회귀 위험 / 호환성

- **V1 토큰 호출처 광범위**: `Primary`, `Background`, `Surface`, `TextPrimary` 등 자주 쓰임. V2는 같은 이름을 유지하되 색만 교체 — 호출처 코드 변경 0 (Color.kt만 수정).
- **`Dark*` 토큰 23종**: V1에서 도입(Phase 2). V2에서는 ColorScheme 슬롯으로 흡수 → 토큰 제거 + 호출처 일괄 리팩터(약 1개 파일 — DarkThemeComponents.kt).
- **`AppTitle`/`BadgeText`/`Caption`/`CaptionSmall`** (Phase 3 도입): V2의 `HeadingXL`/`Caption`/`Micro`로 매핑.
- **`Dimensions.kt` `Padding`/`Spacing` 중복**: V2에서 `Spacing` 단일화. `Padding` 별칭 호출처 일괄 변경.
- **`Shapes` 큰 라운드(28-36dp) 호출처**: 보존 vs 변경 — 화면별 시각 회귀 평가 후 결정.

---

## 12. 비고

- 이 문서는 V2 작업 동안 살아있는 정의서. Phase 10-13 진행 중 변경 사항 발견 시 즉시 갱신.
- Pretendard `tnum` 적용은 `androidx.compose.ui.text.font.FontFeature.SimpleFontFeature("tnum", 1)` 사용. EXIF/숫자 라벨 컴포넌트에 적용.
- V1 → V2 마이그레이션 도중 빌드가 깨질 수 있음. 각 Phase 안에서 자체 완결성 유지(컴파일 통과).
