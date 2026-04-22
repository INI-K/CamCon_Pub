# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 개요

CamCon은 DSLR/미러리스 카메라를 안드로이드 기기로 제어하는 전문가용 앱이다.
- **패키지**: `com.inik.camcon`
- **minSdk**: 29 (Android 10), **targetSdk**: 36
- **ABI**: `arm64-v8a` 전용 (16KB 페이지 크기 지원, Android 15+ 필수)
- **UI**: Jetpack Compose + Material Design 3 (항상 다크 테마 — `UnifiedDarkColorScheme` 고정, 설정 무관)
- **폰트**: Pretendard (Regular, Medium, SemiBold, Bold)
- **다국어**: 8개 언어 (ko, ja, zh, de, es, fr, it + 기본)

## 빌드 및 테스트

gradlew 명령 실행 시 JAVA_HOME 명시 필요:

```bash
export JAVA_HOME=/Users/ini-k/Library/Java/JavaVirtualMachines/jbr-21.0.9/Contents/Home

# 빌드
./gradlew assembleDebug
./gradlew assembleRelease        # key.properties 필요 (gitignore됨)

# 코틀린 컴파일 확인 (빠름)
./gradlew compileDebugKotlin 2>&1 | tail -10

# 테스트
./gradlew :app:testDebugUnitTest
./gradlew :app:testDebugUnitTest --tests "com.inik.camcon.presentation.viewmodel.AuthViewModelTest.methodName"
./gradlew :app:connectedDebugAndroidTest

# 버전 확인
./gradlew printVersion
```

## 아키텍처

Clean Architecture + MVVM. 레이어 의존 방향: `presentation` → `domain` ← `data`

### 레이어 구조

```
com.inik.camcon/
├── di/                    # Hilt 모듈 (AppModule, RepositoryModule)
├── domain/
│   ├── model/             # 도메인 모델 (Camera, CameraPhoto, Subscription 등)
│   ├── repository/        # Repository 인터페이스
│   ├── usecase/
│   │   ├── camera/        # 카메라 조작 UseCase (26개)
│   │   ├── auth/          # 인증 UseCase
│   │   └── usb/           # USB 장치 UseCase
│   └── manager/           # 도메인 매니저 (CameraSettingsManager, ErrorHandlingManager, CameraConnectionGlobalManager)
├── data/
│   ├── datasource/
│   │   ├── nativesource/  # JNI 네이티브 카메라 데이터소스
│   │   ├── usb/           # USB 연결 관리 (UsbCameraManager, UsbConnectionManager 등)
│   │   ├── ptpip/         # PTP/IP Wi-Fi 데이터소스
│   │   ├── local/         # DataStore 기반 로컬 설정
│   │   ├── remote/        # Firebase 원격 데이터소스
│   │   └── billing/       # Google Play Billing
│   ├── repository/
│   │   └── managers/      # CameraConnectionManager(data), CameraEventManager, PhotoDownloadManager
│   ├── network/ptpip/     # PTP/IP TCP/UDP 프로토콜 (포트 15740)
│   └── service/           # 백그라운드 서비스 (AutoConnect, BackgroundSync, WifiMonitoring)
└── presentation/
    ├── ui/                # Activity들 (Splash → Login → Main)
    ├── ui/screens/        # Compose 스크린 (CameraControl, PhotoPreview, PtpipConnection 등)
    ├── ui/screens/components/  # 재사용 Compose 컴포넌트
    └── viewmodel/         # ViewModel 및 하위 매니저
        ├── state/         # CameraUiStateManager
        └── photo/         # 사진 관련 매니저 (PhotoImageManager, PhotoListManager 등)
```

### 핵심 설계 원칙

**CameraViewModel 위임 구조**: `CameraViewModel`은 직접 로직을 갖지 않고 4개의 매니저에 위임한다.
- `CameraConnectionManager` (presentation) — USB 디바이스 관찰, 연결
- `CameraOperationsManager` — 촬영, 라이브뷰, 타임랩스, AF
- `CameraSettingsManager` (domain) — ISO/SS/Aperture/WB 조회·변경
- `ErrorHandlingManager` (domain) — JNI 에러 코드 처리

**동명 클래스 주의**: `CameraConnectionManager`는 두 개다.
- `presentation/viewmodel/CameraConnectionManager.kt` — ViewModel helper (USB 디바이스 이벤트 처리)
- `data/repository/managers/CameraConnectionManager.kt` — Data 레이어 (Mutex 기반 실제 연결 로직)

**CameraUiStateManager**: `@Singleton` Hilt 바인딩. `CameraUiState` (40+ 필드)를 `StateFlow`로 노출. ViewModel과 DataSource 양쪽에서 주입받아 상태를 공유한다.

**알려진 아키텍처 위반**: `CameraUiStateManager` (Presentation)가 Data 레이어 5개 컴포넌트에 주입됨. 의도적으로 허용된 상태이며 향후 `CameraStateObserver` 도메인 인터페이스로 분리 예정.

**연결 모드 2가지**:
- USB OTG: `NativeCameraDataSource` → JNI → `libnative-lib.so` → libgphoto2
- Wi-Fi PTP/IP: `PtpipDataSource` → `PtpipConnectionManager` (TCP) + `PtpipDiscoveryService` (UDP)

### JNI/Native 레이어

- `CameraNative.kt` — 코틀린 싱글톤 객체, 80+ external 함수 선언
- `app/src/main/cpp/native-lib.cpp` — JNI 엔트리포인트
- `app/src/main/cpp/` — C++17 소스 22개 파일 (카메라 기능별 분리)
- `app/src/main/jniLibs/arm64-v8a/` — 사전 빌드된 `.so` 라이브러리들 (libgphoto2, libgphoto2_port, libltdl)
- 네이티브 라이브러리 로딩 실패 시 `CameraNative.init`에서 `RuntimeException`을 던짐

## 의존성 주입

모든 싱글톤은 `AppModule`에, Repository 바인딩은 `RepositoryModule`에 정의된다. KAPT 대신 **KSP** 사용.

특이사항: `PtpipDataSource`는 `Lazy<AutoConnectTaskRunner>` 주입으로 순환 의존성 방지.

## 구독 티어 (기능 게이팅 기준)

| 티어 | 포맷 | 주요 제한 |
|------|------|----------|
| FREE | JPG/JPEG | 2000px 다운로드 제한 |
| BASIC | JPG/JPEG/PNG | 배치 처리 가능 |
| PRO | 모든 포맷 (RAW 포함) | 고급 제어, RAW 다운로드 |
| REFERRER | 모든 포맷 | PRO + 추천인 혜택 |
| ADMIN | 모든 포맷 | 전체 기능 + 사용자 관리 |

RAW 파일 접근 제어는 `ValidateImageFormatUseCase`가 담당한다. 구독 제한 관련 코드 수정 시 반드시 이 UseCase를 확인할 것.

## 개발 규칙

### 비동기 처리
- 신규 코드에서 RxJava 사용 금지 — Kotlin Coroutines만 사용한다.
- `Dispatchers.IO` 하드코딩 금지. 테스트 가능성을 위해 생성자/Hilt로 주입한다.
  ```kotlin
  // Bad
  CoroutineScope(Dispatchers.IO).launch { ... }

  // Good
  class MyClass @Inject constructor(
      private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
  )
  ```
- 비구조화 `CoroutineScope(...)` 생성 금지. 클래스 레벨 managed scope 또는 호출자 scope를 사용한다.

### Compose
- 신규 UI는 Jetpack Compose로 작성. XML은 레거시 유지 보수 용도로만.
- Composable 내 사이드 이펙트 금지. `LaunchedEffect`, `DisposableEffect` 활용.
- 상태는 호이스팅 원칙 준수.

## 테스트 구조

- **단위 테스트**: `app/src/test/` — MockK, Turbine(Flow 테스트), Robolectric, Hilt testing
- **계측 테스트**: `app/src/androidTest/` — `HiltTestRunner` 사용 (커스텀 runner)
- ViewModel 테스트는 `arch-core-testing`의 `InstantTaskExecutorRule` 활용
- 현재 커버리지 약 8% (24개 단위 테스트). Fake 클래스는 `src/test/.../fake/`에 위치.
- JNI/USB/PTP-IP/GPU 경로는 실물 장비 필요 — 자동 테스트 불가.
- ViewModel 테스트는 구현 세부사항이 아닌 **상태 방출(StateFlow/SharedFlow)** 을 검증하는 방향으로 작성한다.

## Firebase

`google-services.json`은 `app/` 아래에 커밋되어 있다. Auth, Firestore, Analytics, Remote Config, Messaging 사용.

## 버전 관리

`app/build.gradle`에서 `majorVersion`, `minorVersion`, `patchVersion`으로 수동 관리. 버전 코드는 Git 커밋 카운트 기반 자동 생성. 브랜치별 suffix 자동 부여 (`-dev.hash`, `-rc`, `-hotfix.hash` 등).

## 핵심 알려진 이슈 (docs/DEV_DOCUMENT.md §5 참조)

### 해소된 이슈
- ✅ **WifiSuggestionBroadcastReceiver** (C-2): goAsync 패턴 적용 완료
- ✅ **CameraViewModel CameraNative 직접 호출** (C-3): Repository 경유로 변경 완료
- ✅ **CameraPreviewArea Bitmap 디코딩 성능** (C-1): IO 오프로드 완료
- ✅ **Bitmap DisposableEffect recycle** (W-2): DisposableEffect에서 recycle 추가 완료
- ✅ **EXIF 회전 역방향** (C7): Bitmap.createBitmap() Matrix 변환 시 90/270도 회전 차원 교환 적용, EXIF 33개 주요 태그 보존, orientation 5-8 모든 각도 지원 완료 (2026-04-22)
- ✅ **미구현 촬영 모드** (W2): `UnsupportedShootingModeException` 도메인 예외 추가, CameraRepositoryImpl 검증 로직, CameraOperationsManager 에러 처리, UI Snackbar 통합, 8개 언어 문자열 추가 완료 (2026-04-22)
- ✅ **processedFiles OOM 회귀 테스트** (C5): LRU 1000개 제한 적용 후 `CameraRepositoryImplLruCacheTest.kt` 단위 테스트 추가로 회귀 방지 (2026-04-22)
- ✅ **FullScreenPhotoViewer 분해** (W-1): 실제 365줄로 이미 기능별 분해 완료 — 추가 분해 불필요 (2026-04-22)

### 잔존 이슈
- **processedFiles OOM** (`CameraRepositoryImpl:90`, C5): 타임랩스 장기 사용 시 메모리 증가 — LRU 1000개 제한 + 단위 테스트 적용됨. 시간 기반 자동 만료는 후속 검토 사항.

## 스킬 라우팅 권고 (Android 우선 · 권고 수준)

이 프로젝트는 Android 전용이다. `.kt`/`.kts` 파일이나 Android 도메인 키워드가 보이면 `android-*` 전역 스킬을 우선 고려한다. 강제가 아닌 권고이며, 언어·빌드 수준 작업에는 kotlin-* / gradle-* 스킬을 자유롭게 사용한다.

### 의도 → 스킬 / 에이전트 매핑

| 의도 키워드 | 1순위 | 2순위 (fallback) |
|------------|-------|------------------|
| 리뷰, 검토, 품질 | `reviewer` 에이전트 (자립형 체크리스트) | `kotlin-reviewer` 에이전트 |
| 테스트, 커버리지 | `tester` 에이전트 + `android-testing` 스킬 | `kotlin-testing` |
| 아키텍처, UseCase, Repository, Hilt | `architect` 에이전트 + `android-architecture` | `android-clean-architecture` |
| UI, Compose, 화면, 레이아웃 | `designer` 에이전트 + `compose-ui` | `android-viewmodel` |
| 구현, 코딩, 기능 개발 | `implementer` 에이전트 | — |
| 기획, 스펙, PRD, 요구사항 | `planner` 에이전트 | — |
| 출시, 릴리즈, Ship 판정 | `completeness-inspector` 에이전트 | — |
| Compose 성능, Recomposition | `performance-auditor` 에이전트 + `compose-performance-audit` | — |
| 코루틴 버그, 스레드 안전성 | `android-coroutines` | `kotlin-concurrency-expert` |
| 빌드 에러 | `gradle-build` | `kotlin-build` |
| 문서, CLAUDE.md 갱신 | `doc-writer` 에이전트 | — |

### 에이전트 파이프라인 오케스트레이션

여러 레이어(UI + Domain + Data)를 동시에 손대야 하는 요청, "새 기능 만들어줘" / "X 기능 추가" / "기획부터 리뷰까지" 같은 통합 요청에서는 개별 스킬로 바로 진입하지 말고 사용자에게 **"에이전트 파이프라인으로 진행할까요?"** 를 먼저 제안한다.

승인 시 다음 순서로 에이전트를 순차 호출한다:

```
planner → architect → designer → implementer → reviewer / tester (병렬) →
  performance-auditor → completeness-inspector → doc-writer
```

거절 시 해당 단계에 해당하는 개별 스킬·에이전트만 호출한다. 단일 레이어 작업(버그 수정, 소규모 리팩터링)에선 제안 없이 개별 스킬로 진행.

### 예외 허용

언어·빌드 전용 스킬은 Android 프로젝트에서도 그대로 사용 가능:
- `kotlin-build`, `kotlin-patterns`, `gradle-build-performance`
- `kotlin-ktor-patterns`, `kotlin-exposed-patterns`는 백엔드 전용이므로 이 프로젝트에선 비사용.

## 사용 가능한 스킬 (전역 `~/.claude/skills/`)

이 프로젝트는 전역 스킬만 사용하며 프로젝트 로컬 전용 스킬은 두지 않는다. 대신 위의 에이전트(`architect`, `designer`, `implementer`, `reviewer`, `tester`, `completeness-inspector`, `performance-auditor`, `planner`, `doc-writer`)가 프로젝트 고유 체크리스트를 담당한다.

| 스킬 | 용도 |
|------|------|
| `android-architecture`, `android-clean-architecture` | Clean Architecture · 모듈 · 의존성 방향 |
| `android-data-layer`, `android-retrofit` | Repository · Room · 네트워크 |
| `android-viewmodel` | ViewModel · StateFlow · UI 상태 |
| `android-coroutines`, `kotlin-concurrency-expert` | 코루틴 · 구조적 동시성 · 스레드 안전성 |
| `compose-ui`, `compose-navigation` | Compose UI · 네비게이션 |
| `compose-performance-audit` | Recomposition 성능 감사 |
| `android-testing`, `kotlin-testing` | 테스트 전략 · 커버리지 |
| `android-accessibility` | 접근성 · TalkBack · 터치 타겟 |
| `android-gradle-logic`, `gradle-build-performance` | Gradle · 빌드 로직 · 빌드 속도 |
| `android-emulator-skill` | 에뮬레이터 · adb · UI 자동화 |
| `coil-compose` | 이미지 로딩 |
| `kotlin-patterns` | Kotlin 언어 문법 · idiomatic 패턴 |
