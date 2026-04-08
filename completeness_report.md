# CamCon 프로젝트 품질 종합 판정 리포트

## 검사 일시: 2026-04-02
## 검사 범위: 프로젝트 전체 (코드 품질, UI/UX 설계, Compose 성능, 빌드 인프라)
## 검사 유형: 운영 중인 앱의 품질 상태 판정

---

## 1. 품질 팀 산출물 요약

| 팀 | 산출물 | CRITICAL | WARNING | SUGGESTION |
|----|--------|----------|---------|------------|
| 코드 리뷰어 | `03_reviewer_report.md` | 7 | 12 | 8 |
| UI/UX 디자이너 | `03_designer_review.md` | 8 | 14 (접근성 4 포함) | 12 |
| 성능 감사관 | `03_performance_report.md` | 5 | 11 | 8 |
| **합계 (중복 포함)** | | **20** | **37** | **28** |
| **중복 제거 후** | | **13** | **27** | **23** |

### 중복 매핑 상세

다음 이슈들은 2개 이상의 리포트에서 독립적으로 발견되었다.

| 통합 ID | 코드 리뷰 | 디자인 리뷰 | 성능 감사 | 통합 제목 |
|---------|-----------|-----------|-----------|----------|
| UC-01 | C-5 (W-5) | C-03 | C01 | CameraUiState God Object (40+ 필드, 불안정 타입) |
| UC-02 | C-5 (W-5) | C-02 | C02 | 라이브뷰 Recomposition 격리 실패 + Bitmap 매 프레임 디코딩 |
| UC-03 | W-6 | C-01 | C04 | ViewModel 직접 전달 (불안정 파라미터, Skip 불가) |
| UC-04 | C-1 | - | C14 | CancellationException 삼킴 |
| UC-05 | C-2,C-3,C-7 | - | C11 | 비구조화 CoroutineScope 다수 |
| UC-06 | C-4,C-5,C-6 | - | C13 | Dispatchers.IO 하드코딩 (30곳+) |
| UC-07 | W-7 | W-04 | C03 | AnimatedPhotoSwitcher EXIF I/O 메인 스레드 |

**중복 제거 결과**: 실질적 고유 CRITICAL 이슈는 13건이다.

---

## 2. 통합 CRITICAL 이슈 (중복 제거, 우선순위순)

### P0: 즉시 수정 필요 -- 사용자 체감 성능 직결

#### UC-01: CameraUiState God Object -- 전체 UI Recomposition의 근본 원인
- **발견자**: 코드 리뷰어 (W-5), 디자이너 (C-03), 성능 감사 (C01)
- **파일**: `presentation/viewmodel/CameraUiState.kt`
- **영향도**: **최고** -- 모든 화면의 Recomposition 성능에 영향
- **근본 원인**: 40개 이상의 필드를 가진 단일 `data class`에 `List<CapturedPhoto>`(불안정), `LiveViewFrame?`, `CameraSettings?`, `CameraCapabilities?` 등 불안정 타입이 혼재. 어떤 필드 하나가 변경되면 이 상태를 구독하는 모든 Composable이 Recomposition 대상이 된다.
- **다운스트림 효과**: UC-02 (라이브뷰 성능), UC-03 (ViewModel 전달 문제)의 직접적 원인
- **권장 수정**: 기능별 sub-state 분리 (`CameraConnectionState`, `CameraLiveViewState`, `CameraCaptureState`, `CameraConfigState`, `CameraUiFlags`). `kotlinx.collections.immutable` 도입.
- **예상 공수**: 2-3일

#### UC-02: 라이브뷰 Bitmap 매 프레임 디코딩 + Recomposition 격리 실패
- **발견자**: 디자이너 (C-02), 성능 감사 (C02), 코드 리뷰어 (W-5)
- **파일**: `presentation/ui/screens/components/CameraPreviewArea.kt:88-95`
- **영향도**: **최고** -- 라이브뷰 30fps 기준 초당 30회 Bitmap 디코딩 + GC 압력
- **근본 원인**: `remember(frame.timestamp)`에서 `BitmapFactory.decodeByteArray`를 Composition 페이즈에서 실행. `liveViewFrame`이 `CameraUiState`에 포함되어 프레임 변경 시 전체 UI 트리 재평가.
- **권장 수정**: (1) LiveViewFrame을 CameraUiState에서 분리하여 별도 StateFlow로 노출, (2) 비트맵 디코딩을 LaunchedEffect + Dispatchers.Default로 이동, (3) inBitmap 재활용
- **예상 공수**: 1-2일

#### UC-07: AnimatedPhotoSwitcher EXIF I/O 메인 스레드 실행
- **발견자**: 코드 리뷰어 (W-7), 디자이너 (W-04), 성능 감사 (C03)
- **파일**: `presentation/ui/screens/CameraControlScreen.kt:1113-1145`
- **영향도**: **높음** -- 사진 수신 시 UI 프레임 드랍 (RAW 파일 시 수백ms)
- **근본 원인**: Coil `onSuccess` 콜백(메인 스레드)에서 `ExifInterface` 파일 I/O + 다수의 Log.d 호출
- **권장 수정**: EXIF 처리를 `LaunchedEffect` + `withContext(Dispatchers.IO)`로 이동. 릴리즈 빌드에서 해당 로깅 제거.
- **참고**: ProGuard 규칙에서 `Log.d` 제거가 설정되어 있으나, `ExifInterface` 생성 자체는 제거되지 않음.
- **예상 공수**: 0.5일

### P1: 다음 스프린트 -- 구조적 안전성 직결

#### UC-04: CancellationException 삼킴 -- 구조적 동시성 파괴
- **발견자**: 코드 리뷰어 (C-1, C-6), 성능 감사 (C14)
- **파일**: `CameraOperationsManager.kt` (5곳), `PtpipDataSource.kt` (3곳+)
- **영향도**: **높음** -- 라이브뷰 중지, 타임랩스 중지가 제대로 작동하지 않을 수 있음
- **근본 원인**: `catch (e: Exception)` 블록에서 `CancellationException`을 rethrow하지 않음
- **권장 수정**: `catch (e: CancellationException) { throw e }` 추가 또는 `ensureActive()` 패턴
- **예상 공수**: 1일

#### UC-05: 비구조화 CoroutineScope -- 메모리 누수 + 취소 불가
- **발견자**: 코드 리뷰어 (C-2, C-3, C-7), 성능 감사 (C11, C12)
- **파일**: 7개 클래스 (GetSubscriptionUseCase, PhotoListManager, PhotoImageManager, CameraCapabilitiesManager, UsbCameraManager, CameraConnectionGlobalManagerImpl, CameraConnectionManager(presentation))
- **영향도**: **높음** -- 메모리 누수, Singleton에서 scope.cancel() 후 재사용 불가
- **근본 원인**: `CoroutineScope(SupervisorJob() + Dispatchers.IO)` 직접 생성, cancel 메커니즘 부재
- **특별 주의**: `CameraConnectionManager(presentation).cleanup()`에서 `scope.cancel()` 호출 시 Singleton이므로 이후 모든 코루틴 실패 (성능 감사 C12)
- **권장 수정**: `@ApplicationScope` 주입, cleanup에서는 `cancelChildren()` 사용
- **참고**: CLAUDE.md에 "Phase 2 리팩터링 대상"으로 명시됨
- **예상 공수**: 2일

#### UC-06: Dispatchers.IO 하드코딩 30곳+ -- 테스트 불가능
- **발견자**: 코드 리뷰어 (C-4, C-5, C-6), 성능 감사 (C13)
- **파일**: `CameraSettingsManager`, `CameraRepositoryImpl` (13곳), `PtpipDataSource` (10곳+)
- **영향도**: **높음** -- 현재 8% 테스트 커버리지의 주요 원인
- **근본 원인**: CLAUDE.md 개발 규칙 위반. 생성자 주입 대신 직접 하드코딩.
- **권장 수정**: `@IoDispatcher`, `@MainDispatcher` Hilt 한정자 정의 후 주입
- **예상 공수**: 2일

### P1: 다음 스프린트 -- 접근성/법적 컴플라이언스

#### DC-07: 촬영 버튼에 contentDescription 없음
- **발견자**: 디자이너 (C-07)
- **파일**: `presentation/ui/screens/components/CaptureControls.kt`
- **영향도**: **높음** -- TalkBack 사용자가 앱의 핵심 기능을 사용 불가
- **권장 수정**: `Modifier.semantics { contentDescription = "촬영"; role = Role.Button }`
- **예상 공수**: 0.5일

#### DC-08: 더블클릭 전용 인터랙션에 접근성 대안 없음
- **발견자**: 디자이너 (C-08)
- **파일**: `CameraControlScreen.kt` 전체화면 진입/종료
- **영향도**: **높음** -- TalkBack 사용자가 전체화면 모드를 제어 불가
- **권장 수정**: 접근 가능한 버튼 별도 제공 또는 `Modifier.semantics { customActions }` 활용
- **예상 공수**: 0.5일

### P1: 다음 스프린트 -- UI/UX 구조

#### DC-01: Screen Composable이 ViewModel에 직접 의존
- **발견자**: 디자이너 (C-01, C-04), 성능 감사 (C04)
- **파일**: `CameraControlScreen.kt`, `PtpipConnectionScreen.kt`
- **영향도**: **높음** -- Preview 불가, 상태 호이스팅 위반, 테스트 불가
- **권장 수정**: `(uiState, onEvent)` 패턴으로 전환. 콜백 람다 개별 전달.
- **예상 공수**: 2-3일

#### DC-04: PtpipConnectionScreen 800줄 단일 Composable
- **발견자**: 디자이너 (C-04)
- **파일**: `presentation/ui/screens/PtpipConnectionScreen.kt`
- **영향도**: **중간** -- 가독성/유지보수성 심각, 전체 트리 Recomposition
- **예상 공수**: 1-2일

#### DC-05: LaunchedEffect 내 delay + 문자열 비교 기반 네비게이션
- **발견자**: 디자이너 (C-05), 코드 리뷰어 (W-12)
- **파일**: `PtpipConnectionScreen.kt:454-521`
- **영향도**: **중간** -- 다국어 지원 시 로직 파손, race condition
- **예상 공수**: 1일

#### DC-06: PhotoGrid Composition 단계에서 Log 호출 (재분류: WARNING)
- **발견자**: 디자이너 (C-06)
- **파일**: `PhotoPreviewScreen.kt`
- **재분류 근거**: `proguard-rules.pro`에 `Log.d` 제거 규칙이 확인됨. 릴리즈 빌드에서는 영향 없음.

---

## 3. 이슈 인과관계 분석

### Root Cause Map

```
[ROOT CAUSE 1] CameraUiState God Object (UC-01)
    |
    +---> 라이브뷰 Recomposition 폭풍 (UC-02)
    |        |
    |        +---> 라이브뷰 프레임 드랍
    |        +---> 촬영 버튼/설정 패널 불필요 Recomposition
    |
    +---> ViewModel 직접 전달 불가피 (UC-03/DC-01)
    |        |
    |        +---> 모든 하위 Composable Skip 불가
    |        +---> Preview/테스트 불가
    |
    +---> 성능 최적화 노력 무효화
             (개별 Composable을 최적화해도 uiState 전체 변경으로 무효)

[ROOT CAUSE 2] 비구조화 CoroutineScope 패턴 (UC-05)
    |
    +---> 메모리 누수 (7개 클래스)
    +---> scope.cancel() 후 Singleton 재사용 불가 (PERF-12)
    +---> Dispatchers.IO 하드코딩 강요 (UC-06)
    |        |
    |        +---> 테스트 커버리지 8%의 구조적 원인
    |
    +---> CancellationException 삼킴 (UC-04)
             |
             +---> 라이브뷰/타임랩스 중지 실패
             +---> 좀비 코루틴 발생

[ROOT CAUSE 3] Compose 아키텍처 미성숙
    |
    +---> Screen Composable에 비즈니스 로직 혼합 (DC-04)
    +---> LaunchedEffect + delay 기반 네비게이션 (DC-05)
    +---> 메인 스레드 I/O (UC-07)
    +---> 접근성 미고려 (DC-07, DC-08)
```

### 핵심 인사이트

1. **UC-01 (CameraUiState)이 가장 영향 범위가 넓은 근본 원인이다.** 이것을 해결하면 UC-02, UC-03이 자연스럽게 완화되고, 향후 개별 Composable 최적화가 비로소 효과를 발휘한다.

2. **UC-05 (비구조화 CoroutineScope)는 테스트 가능성과 안정성의 근본 원인이다.** 이것을 해결해야 UC-06 (Dispatcher 주입)이 가능해지고, 테스트 커버리지 향상의 구조적 장벽이 제거된다.

3. **UC-07 (EXIF I/O)은 독립적인 이슈로, 가장 적은 공수로 체감 효과가 큰 수정이다.**

---

## 4. WARNING 요약 (카테고리별 그룹핑)

### Coroutines 안전성 (6건)
| ID | 내용 | 출처 |
|----|------|------|
| W-02 | Service 클래스 Job() 대신 SupervisorJob() 필요 | 코드, 성능 |
| W-03 | WifiSuggestionBroadcastReceiver 비구조화 scope | 코드 |
| W-04 | MainActivity companion object 내 영구 CoroutineScope | 코드 |
| W-10 | _capturedPhotos StateFlow 비원자적 업데이트 | 코드 |
| C-14 | CancellationException 미처리 (WARNING급 위치) | 성능 |
| C-15 | Service Job() 패턴 | 성능 |

### Compose 성능 (6건)
| ID | 내용 | 출처 |
|----|------|------|
| C-05 | TopControlsBar infiniteTransition 항상 실행 | 성능 |
| C-06 | LaunchedEffect(Unit) Activity 속성 반복 설정 | 성능 |
| C-07 | recentPhotos remember key 불완전 | 성능 |
| C-08 | PtpipConnectionScreen 15개 StateFlow 과다 수집 | 성능 |
| W-15 | unstable 파라미터를 받는 컴포넌트들 | 디자인 |
| W-05 | combinedClickable 빈 onClick 핸들러 | 디자인 |

### UI/UX 일관성 (6건)
| ID | 내용 | 출처 |
|----|------|------|
| W-01 | 하드코딩된 테마 컬러 직접 참조 | 디자인 |
| W-02 | Preview에서 ThemeMode.LIGHT 사용 (실제 다크 고정) | 디자인 |
| W-07 | ApNetworkStatusCard / StaNetworkStatusCard 95% 중복 | 디자인 |
| W-12 | Color.LightGray, Color.Red 하드코딩 | 디자인 |
| W-13 | TopAppBar 컬러 화면 간 불일치 | 디자인 |
| W-14 | Icons.Default.ArrowBack deprecated | 디자인 |

### 접근성 (4건)
| ID | 내용 | 출처 |
|----|------|------|
| A-01 | 장식적 아이콘 contentDescription 불일관 | 디자인 |
| A-02 | 터치 타겟 48dp 미달 (36dp) | 디자인 |
| A-03 | semantics heading 미사용 | 디자인 |
| A-04 | 멀티 선택 모드 상태 변경 미공지 | 디자인 |

### 빌드 인프라 (4건)
| ID | 내용 | 출처 |
|----|------|------|
| C-22 | shrinkResources false (릴리즈) | 성능 |
| C-23 | material-icons-extended 전체 포함 | 성능 |
| C-24 | accompanist-systemuicontroller deprecated | 성능 |
| W-09 | PtpipDataSource init 블록 JNI 크래시 위험 | 코드 |

### 코드 품질 (3건)
| ID | 내용 | 출처 |
|----|------|------|
| W-01 | Domain 레이어 android.util.Log import | 코드 |
| W-08 | PtpipConnectionManager 하드코딩 GUID | 코드 |
| W-03 | Screen 파일에 비즈니스 로직 170줄 존재 | 디자인 |

---

## 5. 출시 인프라 체크리스트

| 항목 | 상태 | 비고 |
|------|------|------|
| versionName / versionCode | 정상 | Git 커밋 카운트 기반 자동 생성, 브랜치별 suffix 정상 |
| 다국어 strings.xml | **경고** | 8개 언어 strings.xml 존재하나, `resConfigs`에 `"zh"`, `"es"` 누락 |
| key.properties | 정상 | `.gitignore`에 포함, 코드 미포함 |
| google-services.json | 정상 | 리포지토리에서 제거됨 |
| proguard-rules.pro | 정상 | Log 제거 규칙 적절. `-dontoptimize` 설정 주의 필요 |
| minSdk 29 | 정상 | Android 10 이상 호환 |
| arm64-v8a | 정상 | 27개 .so 라이브러리 빌드 확인 |
| Android 15 (SDK 35+) | 정상 | targetSdk 36, 16KB 페이지 크기 지원 |
| shrinkResources | **미적용** | 릴리즈 빌드에서 `shrinkResources false` |

---

## 6. 판정

### CONDITIONAL-SHIP

### 판정 근거

**NO-SHIP이 아닌 이유:**
- 운영 중인 앱의 품질 상태 판정이며, 이미 프로덕션에서 동작하고 있다.
- 13건의 CRITICAL 중 앱 크래시나 데이터 손실을 직접 유발하는 이슈는 1건(scope.cancel() 후 Singleton 재사용 불가)이다.
- 나머지 CRITICAL은 성능 저하, 테스트 불가능, 접근성 미준수 등 "품질 부채"에 해당한다.
- ProGuard 규칙이 릴리즈 빌드에서 Log 호출을 제거하여 일부 성능 이슈가 완화된다.

**SHIP이 아닌 이유:**
- CRITICAL 13건이 존재하며, UC-01 (CameraUiState God Object)은 모든 화면의 Recomposition 성능에 영향을 미치는 근본 원인이다.
- scope.cancel() 후 Singleton 재사용 불가는 특정 시나리오에서 앱 재시작을 강요한다.
- 접근성 CRITICAL 2건은 법적 컴플라이언스 관점에서 위험하다.
- `resConfigs`에서 zh/es 누락으로 2개 언어 사용자에게 영어 폴백이 발생한다.

### CONDITIONAL-SHIP 조건 (2주 이내 해결)

| 순위 | 조건 | 이슈 | 데드라인 |
|------|------|------|---------|
| 1 | scope.cancel() -> cancelChildren() 변경 | PERF-12 | 1주차 |
| 2 | CancellationException rethrow 추가 | UC-04 | 1주차 |
| 3 | 셔터 버튼 contentDescription 추가 | DC-07 | 1주차 |
| 4 | resConfigs에 zh, es 추가 | 인프라 | 1주차 |
| 5 | EXIF I/O를 백그라운드 스레드로 이동 | UC-07 | 2주차 |
| 6 | 더블클릭 접근성 대안 제공 | DC-08 | 2주차 |

나머지 CRITICAL (UC-01, UC-02, UC-05, UC-06, DC-01, DC-04, DC-05)은 "구조적 품질 부채"로 분류하여 계획적 리팩터링으로 진행한다.

---

## 7. 권장 액션 플랜 (우선순위순)

### Phase A: 긴급 수정 (1주, 2.5일 공수)

| # | 작업 | 관련 이슈 | 공수 |
|---|------|----------|------|
| A-1 | `CameraConnectionManager.cleanup()`에서 `scope.cancel()` -> `cancelChildren()` | PERF-12 | 0.5일 |
| A-2 | CancellationException rethrow 추가 (8곳) | UC-04 | 0.5일 |
| A-3 | 셔터 버튼 semantics 추가 | DC-07 | 0.25일 |
| A-4 | build.gradle resConfigs에 "zh", "es" 추가 | 인프라 | 0.1일 |
| A-5 | AnimatedPhotoSwitcher EXIF 처리 백그라운드 이동 | UC-07 | 0.5일 |
| A-6 | 전체화면 접근성 대안 버튼 추가 | DC-08 | 0.5일 |

### Phase B: 구조적 리팩터링 (2-3주, 8일 공수)

| # | 작업 | 관련 이슈 | 공수 |
|---|------|----------|------|
| B-1 | CameraUiState를 기능별 sub-state로 분리 | UC-01 | 3일 |
| B-2 | LiveViewFrame 별도 StateFlow 분리 + Bitmap 디코딩 비동기화 | UC-02 | 1.5일 |
| B-3 | 7개 클래스의 비구조화 CoroutineScope -> @ApplicationScope 주입 | UC-05 | 2일 |
| B-4 | @IoDispatcher / @MainDispatcher Hilt 한정자 도입 + 30곳 교체 | UC-06 | 2일 |

### Phase C: UI/UX 품질 향상 (백로그)

| # | 작업 | 관련 이슈 | 공수 |
|---|------|----------|------|
| C-1 | Screen Composable (uiState, onEvent) 패턴 전환 | DC-01 | 3일 |
| C-2 | PtpipConnectionScreen 분리 + 네비게이션 이벤트 방식 전환 | DC-04, DC-05 | 2일 |
| C-3 | 공통 컴포넌트 추출 (ErrorHandler, EmptyState, LoadingState) | S-01~S-03 | 2일 |
| C-4 | shrinkResources 활성화 + material-icons-extended 정리 | C-22, C-23 | 1일 |
| C-5 | 하드코딩 문자열 strings.xml 이동 | S-05 | 2일 |
| C-6 | 테마 컬러 MaterialTheme.colorScheme 통일 | W-01, W-12 | 1일 |
| C-7 | accompanist-systemuicontroller -> enableEdgeToEdge 마이그레이션 | C-24 | 1일 |

---

## 8. 긍정적 사항

1. **GlobalScope 미사용**: 전체 코드베이스에서 GlobalScope 사용 없음
2. **collectAsStateWithLifecycle 올바른 사용**: Lifecycle-aware 수집 패턴 적용
3. **LazyRow/LazyColumn key 적절한 사용**: `key = { it.id }` 패턴 올바르게 적용
4. **CameraStateObserver 도메인 인터페이스**: 레이어 경계 위반을 줄이려는 올바른 설계 방향
5. **ProGuard 로그 제거 규칙**: 릴리즈 빌드에서 Log 호출 완전 제거
6. **processedFiles OOM 수정 완료**: LRU 방식(1000개 제한)으로 기존 알려진 이슈 해결
7. **JNI 안전성 양호**: 네이티브 호출 스레드 관리, 리소스 해제, 예외 처리 적절
8. **보안 양호**: API 키/비밀번호 하드코딩 없음, key.properties gitignore 처리

---

*검사관: Completeness Inspector*
*검사 기준: android-release-readiness + android-accessibility 스킬*
*다음 검사 예정: Phase A 완료 후 SHIP 전환 판정*
