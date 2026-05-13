---
name: camcon-domain-context
description: CamCon(안드로이드 DSLR/미러리스 테더링 촬영 앱) 도메인 핵심 규약. Clean Arch 의존 방향, ViewModel 위임 4매니저, USB OTG / Wi-Fi PTP-IP 두 연결 모드, JNI + libgphoto2, 다크 테마 고정, Coroutines 전용, 구독 게이팅 단일 지점 등. CamCon 코드를 작성·리뷰·탐색하거나 카메라 제어·테더링·라이브뷰·타임랩스 작업을 할 때 반드시 로드한다.
---

# CamCon 도메인 컨텍스트

이 스킬은 CamCon에서 코드를 만지는 모든 에이전트가 공유하는 **고정 규약**을 모은다. CLAUDE.md §1~§6의 내용을 에이전트가 빠르게 흡수할 수 있도록 발췌·구조화했다. 본문은 짧게 유지하고, 세부는 CLAUDE.md를 참조한다.

## 빌드 / 환경

- **minSdk 29 / targetSdk 36**, ABI는 **`arm64-v8a` 전용** (16KB 페이지 지원 — Android 15+).
- **JBR 21 필수.** 빌드 전 `export JAVA_HOME=/Users/ini-k/Library/Java/JavaVirtualMachines/jbr-21.0.9/Contents/Home`.
- DI는 **Hilt + KSP**. KAPT 추가 금지.
- 다국어 8개 (ko, ja, zh, de, es, fr, it + 기본).
- 폰트 Pretendard.

## 아키텍처 의존 방향

```
presentation → domain ← data
```

**위반 사례 (의도적 허용):** `CameraUiStateManager`(Presentation 패키지)가 Data 레이어 5개 컴포넌트에 주입됨. 향후 `CameraStateObserver` 도메인 인터페이스로 분리 예정. 새로운 위반은 만들지 않는다.

## ViewModel 위임 4매니저

`CameraViewModel`은 직접 로직 없이 4개 매니저에 위임:
- `CameraConnectionManager` (presentation/viewmodel) — USB 디바이스 관찰
- `CameraOperationsManager` — 촬영·라이브뷰·타임랩스·AF
- `CameraSettingsManager` (domain) — ISO/SS/Aperture/WB
- `ErrorHandlingManager` (domain) — JNI 에러 코드

## ⚠️ 동명 클래스 함정

`CameraConnectionManager` **두 개**:
- `presentation/viewmodel/CameraConnectionManager.kt` — ViewModel helper (USB 이벤트)
- `data/repository/managers/CameraConnectionManager.kt` — Mutex 기반 실제 연결 로직

새 코드에 같은 패턴을 만들지 말고, 인용 시 어느 쪽인지 명시한다.

## 연결 모드 (두 갈래)

| 모드 | 진입점 | 경로 |
|------|--------|------|
| **USB OTG** | `NativeCameraDataSource` | JNI → `libnative-lib.so` → `libgphoto2` |
| **Wi-Fi PTP/IP** | `PtpipDataSource` | `PtpipConnectionManager`(TCP) + `PtpipDiscoveryService`(UDP, 포트 15740) |

`PtpipDataSource`는 `Lazy<AutoConnectTaskRunner>` 주입으로 순환 의존성 회피.

## 동시성 규약 (CLAUDE.md §3)

- **Coroutines 전용**. 신규 코드 RxJava 금지(기존 RxJava는 점진 마이그레이션, `rxjava-to-coroutines-migration` 스킬 참조).
- `Dispatchers.IO` **하드코딩 금지** — 생성자/Hilt로 `CoroutineDispatcher` 주입.

```kotlin
// Bad
CoroutineScope(Dispatchers.IO).launch { ... }

// Good
class MyClass @Inject constructor(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
)
```

- 비구조화 `CoroutineScope(...)` 생성 금지. 클래스 managed scope 또는 호출자 scope 사용.

## UI 규약 (CLAUDE.md §4)

- 신규 UI는 **Jetpack Compose**. XML은 레거시 유지보수만.
- Navigation: Jetpack Navigation Compose.
- Composable 내 side effect는 `LaunchedEffect` / `DisposableEffect` 사용.
- **다크 테마 고정** — `UnifiedDarkColorScheme` 외 다른 테마 분기 추가 금지.

## 구독 / RAW 게이팅

| 티어 | 포맷 | 제한 |
|------|------|------|
| FREE | JPG/JPEG | 2000px 다운로드 제한 |
| BASIC | JPG/JPEG/PNG | 배치 처리 |
| PRO | 모든 포맷(RAW 포함) | 고급 제어·RAW 다운로드 |
| REFERRER | 모든 포맷 | PRO + 추천인 혜택 |
| ADMIN | 모든 포맷 | 전체 + 사용자 관리 |

**RAW/포맷 접근 제어는 `ValidateImageFormatUseCase` 단일 지점.** 다른 곳에서 포맷·티어 분기 추가 금지. 자세한 규약은 `camcon-subscription-gating` 스킬.

## JNI 레이어

- `CameraNative.kt` — 코틀린 싱글톤, **80+ external 함수**.
- `app/src/main/cpp/native-lib.cpp` — JNI 엔트리포인트.
- `app/src/main/cpp/` — C++17 소스 29개 (기능별 분리).
- `app/src/main/jniLibs/arm64-v8a/` — 사전 빌드 `.so`:
  - `libgphoto2.so`, `libgphoto2_port.so`, `libltdl.so` 코어 3개
  - **camlib 19개** (`libgphoto2_camlib_*.so`), **port iolib 5개** (`libgphoto2_port_iolib_*.so`) — 동적 로딩
- 로딩 실패 시 `CameraNative.init`이 `RuntimeException`.

libgphoto2 빌드 절차는 `camcon-jni-protocol` 스킬 참조.

## 빌드 / 테스트 명령

```bash
./gradlew assembleDebug
./gradlew assembleRelease        # key.properties 필요 (gitignore)
./gradlew compileDebugKotlin 2>&1 | tail -10
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew printVersion
```

## 버전 관리

- `app/build.gradle`의 `majorVersion`·`minorVersion`·`patchVersion` 수동.
- versionCode는 Git 커밋 카운트 기반 자동 생성.
- 브랜치별 suffix 자동 (`-dev.hash`, `-rc`, `-hotfix.hash` 등).

## 커밋 메시지 규약

- 사용자의 글로벌 CLAUDE.md: **Claude/Anthropic 공동저자 트레일러 금지**. "Generated with Claude Code" 푸터도 금지.
- 짧은 한 줄 선호: `fix: handle empty input`, `feat: add CSV export`.

## 백그라운드 / 무인 작업 (장시간 안정성)

CamCon은 타임랩스(최대 9999장), 자동 재연결, 무인 이벤트 리스닝 같은 장시간 작업이 핵심 차별점이다. 다음 컴포넌트가 관련 도메인:

- **`BackgroundSyncService`** — Foreground Service Type `CONNECTED_DEVICE` (Android 14+ 필수). Wake Lock 획득. `onCreate`/`onDestroy` 라이프사이클로 scope 관리.
- **`WifiMonitoringService`** — Wi-Fi 상태 모니터링 + STA/AP 전환 감지.
- **`AutoConnectManager` + `AutoConnectTaskRunner`** — 마지막 카메라 자동 재연결.
- **`UsbConnectionRecovery`** — USB 끊김 자동 복구.

규약:
- Service에서 `CoroutineScope(...)` 비구조화 생성은 `onDestroy`에서 명시적 cancel 필수 — 누락 시 누수.
- 권장 패턴: `LifecycleService` + `lifecycleScope` 또는 Hilt `@ApplicationScope` 주입.
- Wake Lock은 반드시 try-finally로 release 보장.
- Foreground Service Type을 `CONNECTED_DEVICE`(USB 카메라) 또는 `DATA_SYNC`(자동 동기)로 명시. SDK 34+ 필수.

## 알려진 이슈 참조

상세는 `docs/DEV_DOCUMENT.md` §5. 잔존 이슈:
- `processedFiles` OOM (`CameraRepositoryImpl:90`) — LRU 1000개 제한 적용. 시간 기반 자동 만료는 후속 검토.

## 외부 문서

- libgphoto2: http://www.gphoto.org/doc/
- libgphoto2 GitHub: https://github.com/gphoto/libgphoto2
- Compose: https://developer.android.com/jetpack/compose
