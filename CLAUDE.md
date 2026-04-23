# CLAUDE.md

CamCon에서 Claude Code(claude.ai/code)가 작업할 때 참고하는 가이드. 구조는 [awesome-android-agent-skills `Agent.md`](https://github.com/new-silvermoon/awesome-android-agent-skills/blob/main/Agent.md)의 7섹션 스켈레톤을 따르되, 각 섹션은 CamCon의 실제 스펙·아키텍처로 채웠다.

---

## 1. Project Specifications

DSLR/미러리스 카메라를 안드로이드로 원격 제어하는 전문가용 앱.

| 항목 | 값 |
|------|-----|
| 패키지 | `com.inik.camcon` |
| minSdk / targetSdk | **29 (Android 10) / 36** |
| ABI | **`arm64-v8a` 전용** — 16KB 페이지(Android 15+) 대응 |
| Language | Kotlin 1.9+ |
| Build System | Gradle (Groovy DSL 사용 중, Kotlin DSL 전환은 별도 의제) |
| UI | Jetpack Compose + Material 3, **항상 다크 테마** (`UnifiedDarkColorScheme` 고정) |
| 폰트 | Pretendard (Regular/Medium/SemiBold/Bold) |
| 다국어 | 8개 (ko, ja, zh, de, es, fr, it + 기본) |
| DI | **Hilt + KSP** (KAPT 아님) |

### 빌드·테스트 규약

JetBrains Runtime 21을 고정 사용하므로 `JAVA_HOME` 지정 필수.

```bash
export JAVA_HOME=/Users/ini-k/Library/Java/JavaVirtualMachines/jbr-21.0.9/Contents/Home

./gradlew assembleDebug
./gradlew assembleRelease        # key.properties 필요 (gitignore됨)
./gradlew compileDebugKotlin 2>&1 | tail -10   # 빠른 컴파일 체크

./gradlew :app:testDebugUnitTest
./gradlew :app:testDebugUnitTest --tests "com.inik.camcon.presentation.viewmodel.AuthViewModelTest.methodName"
./gradlew :app:connectedDebugAndroidTest

./gradlew printVersion
```

### 버전 관리
- `app/build.gradle`의 `majorVersion`·`minorVersion`·`patchVersion` 수동 관리.
- 버전 코드는 **Git 커밋 카운트 기반 자동 생성**.
- 브랜치별 suffix 자동 부여 (`-dev.hash`, `-rc`, `-hotfix.hash` 등).

---

## 2. Architecture & Design Patterns

**Clean Architecture + MVVM**, 의존 방향 `presentation → domain ← data`. Unidirectional Data Flow(UDF).

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
│   ├── network/ptpip/     # PTP/IP TCP/UDP (포트 15740)
│   └── service/           # 백그라운드 서비스 (AutoConnect, BackgroundSync, WifiMonitoring)
└── presentation/
    ├── ui/                # Activity들 (Splash → Login → Main)
    ├── ui/screens/        # Compose 스크린 (CameraControl, PhotoPreview, PtpipConnection 등)
    ├── ui/screens/components/
    └── viewmodel/
        ├── state/         # CameraUiStateManager
        └── photo/         # 사진 매니저 (PhotoImageManager, PhotoListManager 등)
```

### 핵심 설계 원칙

**CameraViewModel 위임 구조** — ViewModel은 직접 로직을 갖지 않고 4개 매니저에 위임:
- `CameraConnectionManager` (presentation) — USB 디바이스 관찰·연결
- `CameraOperationsManager` — 촬영·라이브뷰·타임랩스·AF
- `CameraSettingsManager` (domain) — ISO/SS/Aperture/WB 조회·변경
- `ErrorHandlingManager` (domain) — JNI 에러 코드 처리

**⚠️ 동명 클래스 주의** — `CameraConnectionManager`가 **2개** 존재:
- `presentation/viewmodel/CameraConnectionManager.kt` — ViewModel helper (USB 이벤트)
- `data/repository/managers/CameraConnectionManager.kt` — Mutex 기반 실제 연결 로직

**CameraUiStateManager** — `@Singleton` Hilt 바인딩. `CameraUiState`(40+ 필드)를 `StateFlow`로 노출. ViewModel·DataSource 양쪽에서 주입받아 공유.

**알려진 아키텍처 위반** — `CameraUiStateManager`(Presentation)가 Data 레이어 5개 컴포넌트에 주입됨. 의도적 허용, 향후 `CameraStateObserver` 도메인 인터페이스로 분리 예정.

**연결 모드 2가지**
- USB OTG: `NativeCameraDataSource → JNI → libnative-lib.so → libgphoto2`
- Wi-Fi PTP/IP: `PtpipDataSource → PtpipConnectionManager(TCP) + PtpipDiscoveryService(UDP)`

### 의존성 주입
- 싱글톤은 `AppModule`, Repository 바인딩은 `RepositoryModule`.
- `PtpipDataSource`는 `Lazy<AutoConnectTaskRunner>` 주입으로 순환 의존성 방지.

### JNI / Native 레이어
- `CameraNative.kt` — 코틀린 싱글톤, **80+ external 함수** 선언
- `app/src/main/cpp/native-lib.cpp` — JNI 엔트리포인트
- `app/src/main/cpp/` — C++17 소스 22개 (기능별 분리)
- `app/src/main/jniLibs/arm64-v8a/` — 사전 빌드 `.so` (libgphoto2, libgphoto2_port, libltdl)
- 로딩 실패 시 `CameraNative.init`이 `RuntimeException`을 던짐

### 구독 티어 (기능 게이팅)

| 티어 | 포맷 | 제한 |
|------|------|------|
| FREE | JPG/JPEG | 2000px 다운로드 제한 |
| BASIC | JPG/JPEG/PNG | 배치 처리 |
| PRO | 모든 포맷(RAW 포함) | 고급 제어·RAW 다운로드 |
| REFERRER | 모든 포맷 | PRO + 추천인 혜택 |
| ADMIN | 모든 포맷 | 전체 + 사용자 관리 |

**RAW 접근 제어는 `ValidateImageFormatUseCase` 단일 지점**에서 처리. 구독·포맷 관련 코드 수정 시 반드시 이 UseCase를 확인한다.

### Firebase
`google-services.json`은 `app/` 아래 커밋되어 있음. Auth, Firestore, Analytics, Remote Config, Messaging 사용.

---

## 3. Asynchronous Programming

- **Coroutines 전용** — 신규 코드에서 RxJava 금지 (기존 RxJava 코드는 점진 마이그레이션).
- `Dispatchers.IO` 하드코딩 금지 → 생성자/Hilt로 주입하여 테스트 가능성 확보.
  ```kotlin
  // Bad
  CoroutineScope(Dispatchers.IO).launch { ... }

  // Good
  class MyClass @Inject constructor(
      private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
  )
  ```
- 비구조화 `CoroutineScope(...)` 생성 금지 → 클래스 레벨 managed scope 또는 호출자 scope 사용.

---

## 4. UI Framework

- **신규 UI는 Jetpack Compose** — XML은 레거시 유지 보수 용도로만.
- **Navigation**: Jetpack Navigation Compose.
- Composable 내 사이드 이펙트 금지 → `LaunchedEffect` / `DisposableEffect` 사용.
- 상태 호이스팅(state hoisting) 원칙 준수, 수정자(Modifier) 체인 규약 따름.
- **테마는 다크 고정**: `UnifiedDarkColorScheme` 외 다른 테마 분기 추가 금지.

---

## 5. Testing Philosophy

- **단위 테스트**: `app/src/test/` — JUnit4, MockK, Turbine(Flow), Robolectric, Hilt testing.
- **계측 테스트**: `app/src/androidTest/` — 커스텀 `HiltTestRunner`.
- ViewModel 테스트는 `arch-core-testing`의 `InstantTaskExecutorRule` 활용.
- Fake 클래스 위치: `src/test/.../fake/`.
- **ViewModel 테스트 원칙**: 구현 세부사항이 아닌 **StateFlow/SharedFlow 방출**을 검증한다.
- **현재 커버리지 약 8%** (단위 테스트 24개).
- **자동 테스트 불가 경로**: JNI / USB / PTP-IP / GPU — 실물 장비 필요.

---

## 6. External Documentation

구현이 불확실할 때는 아래 공식 문서를 먼저 참고한다.
- [Android Developer Documentation](https://developer.android.com)
- [Kotlin Documentation](https://kotlinlang.org)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [libgphoto2](http://www.gphoto.org/doc/) — JNI 계층 카메라 프로토콜 원본

프로젝트 내 상세 문서는 `docs/DEV_DOCUMENT.md`(알려진 이슈·아키텍처 결정사항)를 우선 참조.

---

## 7. Useful Agent Skills Recap

프로젝트 로컬 스킬은 `.claude/skills/` 에 설치되어 있다 ([awesome-android-agent-skills](https://github.com/new-silvermoon/awesome-android-agent-skills)에서 가져옴). Agent.md의 6개 도메인 분류를 그대로 따른다.

| 도메인 | 스킬 | 용도 |
|--------|------|------|
| **architecture** | `android-architecture`, `android-viewmodel`, `android-data-layer` | Clean Architecture, ViewModel/StateFlow, Repository/Room/Retrofit |
| **ui** | `compose-ui`, `compose-navigation`, `coil-compose`, `android-accessibility` | Compose 베스트 프랙티스, 이미지 로딩, 접근성 |
| **performance** | `compose-performance-audit`, `gradle-build-performance` | Recomposition 감사, 빌드 속도 |
| **migration** | `xml-to-compose-migration`, `rxjava-to-coroutines-migration` | 레거시 마이그레이션 |
| **testing_and_automation** | `android-testing`, `android-emulator-skill` | 단위/UI 테스트, 에뮬레이터·adb 자동화 |
| **concurrency_and_networking** | `android-coroutines`, `android-retrofit`, `kotlin-concurrency-expert` | 코루틴, HTTP, 스레드 안전성 |
| **build_and_tooling** | `android-gradle-logic` | Gradle 빌드 로직 |

### 의도 → 스킬 매핑 (CamCon 실무 라우팅)

| 의도 키워드 | 1순위 스킬 |
|------------|-----------|
| 아키텍처, UseCase, Repository, Hilt | `android-architecture`, `android-data-layer` |
| UI, Compose, 화면, 레이아웃 | `compose-ui`, `compose-navigation` |
| ViewModel, StateFlow, UI 상태 | `android-viewmodel` |
| 테스트, 커버리지 | `android-testing` |
| 에뮬레이터, adb, UI 자동화 | `android-emulator-skill` |
| 코루틴 버그, 스레드 안전성 | `android-coroutines`, `kotlin-concurrency-expert` |
| 네트워크(HTTP) | `android-retrofit` |
| Compose 성능, Recomposition | `compose-performance-audit` |
| 빌드 속도, Gradle | `gradle-build-performance`, `android-gradle-logic` |
| XML → Compose 전환 | `xml-to-compose-migration` |
| RxJava → Coroutines 전환 | `rxjava-to-coroutines-migration` |
| 접근성(TalkBack, 터치 타겟) | `android-accessibility` |
| 이미지 로딩 | `coil-compose` |

### 멀티 레이어 요청 처리 원칙

여러 레이어(UI + Domain + Data)를 동시에 손대야 하는 요청, "새 기능 만들어줘" / "X 기능 추가" / "기획부터 리뷰까지" 같은 통합 요청에서는 개별 스킬로 바로 진입하지 말고 사용자에게 **"어떤 레이어부터 손댈지"** 또는 **"전체 설계 → 구현 → 리뷰 순서로 진행할지"** 를 먼저 확인한다. 단일 레이어 작업(버그 수정, 소규모 리팩터링)은 확인 없이 해당 스킬로 바로 진행.

---

## 알려진 이슈 (docs/DEV_DOCUMENT.md §5)

### 해소됨
- ✅ **WifiSuggestionBroadcastReceiver** (C-2): goAsync 패턴 적용
- ✅ **CameraViewModel CameraNative 직접 호출** (C-3): Repository 경유로 변경
- ✅ **CameraPreviewArea Bitmap 디코딩 성능** (C-1): IO 오프로드
- ✅ **Bitmap DisposableEffect recycle** (W-2): recycle 추가
- ✅ **EXIF 회전 역방향** (C7, 2026-04-22): 90/270도 차원 교환, EXIF 33개 태그 보존, orientation 5-8 지원
- ✅ **미구현 촬영 모드** (W2, 2026-04-22): `UnsupportedShootingModeException` + Snackbar + 8개 언어 문자열
- ✅ **processedFiles OOM 회귀 테스트** (C5, 2026-04-22): LRU 1000개 제한 + `CameraRepositoryImplLruCacheTest.kt`
- ✅ **FullScreenPhotoViewer 분해** (W-1, 2026-04-22): 365줄로 기능별 분해 완료

### 잔존
- **processedFiles OOM** (`CameraRepositoryImpl:90`, C5) — LRU 1000개 제한 적용됨. 시간 기반 자동 만료는 후속 검토.

---

*아키텍처가 바뀌거나 새로운 규약이 생기면 이 파일을 갱신해 에이전트가 최신 컨벤션을 따르도록 유지한다.*
