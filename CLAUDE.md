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

#### libgphoto2 Android 빌드
- **업스트림 소스**: [github.com/gphoto/libgphoto2](https://github.com/gphoto/libgphoto2)
- **빌드 방식**: CamCon 저장소 **외부**에서 Android NDK 툴체인으로 크로스 컴파일 후, 산출물(`.so`)만 `app/src/main/jniLibs/arm64-v8a/` 에 체크인.
- **대상 ABI**: `arm64-v8a` 전용. Android 15+ **16KB 페이지** 호환을 위해 링커 플래그 `-Wl,-z,max-page-size=16384` 로 빌드 필요.
- **포함 라이브러리**:
  - `libgphoto2.so` — 카메라 제어 상위 레벨
  - `libgphoto2_port.so` — USB/PTP-IP 포트 드라이버
  - `libltdl.so` — 동적 모듈 로더 (gphoto2 camlibs)
- **카메라 드라이버(camlibs)**: libgphoto2 내부에 정적 링크 또는 같은 jniLibs 디렉터리에 배치. 업스트림에 추가된 신규 카메라는 libgphoto2 재빌드 후 `.so` 교체.
- **업데이트 절차**: 업스트림 변경 시 (1) gphoto/libgphoto2 최신 태그 체크아웃, (2) Android NDK로 재빌드, (3) `.so` 3개 교체, (4) `CameraNative.kt` external 시그니처 동기화 확인, (5) `./gradlew assembleDebug` 로 링킹 검증.

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
- [libgphoto2 문서](http://www.gphoto.org/doc/) — JNI 계층 카메라 프로토콜 원본
- [libgphoto2 GitHub](https://github.com/gphoto/libgphoto2) — 네이티브 라이브러리 소스(Android용으로 외부 크로스 컴파일하여 `jniLibs/arm64-v8a/`에 `.so` 체크인)

프로젝트 내 상세 문서는 `docs/DEV_DOCUMENT.md`(알려진 이슈·아키텍처 결정사항)를 우선 참조.

---

## 하네스: CamCon 개발

**목표:** CamCon 코드 작성·수정·리뷰·디버깅을 explorer / architect / implementer / reviewer / tdd-tester 5명 서브 에이전트 팀으로 분배해 처리한다.

**트리거:** CamCon 코드 변경·설계·리뷰·테스트가 필요한 모든 작업은 `camcon-orchestrator` 스킬로 진입한다. 단순 질의(개념 설명, 위치 찾기)는 직접 응답 가능. 에이전트·스킬 정의는 `.claude/agents/`, `.claude/skills/` 참조.

**변경 이력:**
| 날짜 | 변경 내용 | 대상 | 사유 |
|------|----------|------|------|
| 2026-05-13 | §7(가상 라우팅) 제거 | CLAUDE.md, .claude/commands/camcon-dev.md | `everything-claude-code:*` 플러그인 미설치로 8개 에이전트 라우팅 환상이었음 |
| 2026-05-13 | 초기 하네스 구성 | 전체 (.claude/agents/ × 5, .claude/skills/camcon-* × 4) | 신규 구축. 서브 에이전트 패턴, 기존 Android 스킬 18개는 그대로 보존 |

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
