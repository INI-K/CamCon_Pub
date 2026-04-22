# CamCon 마스터 개발 문서

> **버전**: 1.0.0
> **작성일**: 2026-03-31
> **기준 브랜치**: `refactor/phase1-architecture-cleanup`
> **상태**: 코드 분석 기반 (추측 없음)

---

## 목차

1. [프로젝트 개요](#1-프로젝트-개요)
2. [기능 명세](#2-기능-명세)
3. [아키텍처](#3-아키텍처)
4. [UI/UX](#4-uiux)
5. [코드 품질 현황](#5-코드-품질-현황)
6. [테스트 전략](#6-테스트-전략)

---

## 1. 프로젝트 개요

| 항목 | 값 |
|------|-----|
| **앱 이름** | CamCon |
| **목적** | DSLR/미러리스 카메라를 Android 기기로 원격 제어 |
| **패키지** | `com.inik.camcon` |
| **버전** | 1.2.1 (majorVersion=1, minorVersion=2, patchVersion=1) |
| **minSdk** | 29 (Android 10) |
| **targetSdk** | 36 |
| **ABI** | arm64-v8a 전용 (16KB 페이지 크기 지원) |
| **UI** | Jetpack Compose + Material Design 3 |
| **아키텍처** | Clean Architecture + MVVM + Hilt DI |
| **연결 방식** | USB OTG (libgphoto2 JNI) / Wi-Fi PTP/IP |
| **백엔드** | Firebase (Auth, Firestore, Analytics, Remote Config, Messaging) |
| **결제** | Google Play Billing |
| **네이티브** | C++17 + libgphoto2 2.5.x (JNI, arm64-v8a) |

### 구독 등급

| 등급 | 포맷 지원 | 주요 기능 |
|------|----------|----------|
| FREE | JPG/JPEG (2000px 제한) | 기본 카메라 제어 |
| BASIC | JPG/JPEG/PNG | 기본 카메라 제어, 배치 처리 |
| PRO | 모든 포맷 (RAW 포함) | 고급 제어, RAW 다운로드 |
| REFERRER | 모든 포맷 | 모든 PRO 기능 + 추천인 혜택 |
| ADMIN | 모든 포맷 | 전체 기능 + 사용자 관리 |

---

## 2. 기능 명세

### 2.1 카메라 연결

| 기능 | 주요 클래스 | 설명 | 구독 등급 |
|------|------------|------|----------|
| USB OTG 연결 | `ConnectCameraUseCase`, `UsbCameraManager`, `UsbConnectionManager` | USB 케이블 PTP 프로토콜 연결 | 무료 |
| USB 자동 감지 | `SplashActivity` (`USB_DEVICE_ATTACHED`) | USB 카메라 연결 시 앱 자동 실행 | 무료 |
| USB 연결 복구 | `UsbConnectionRecovery` | 연결 끊김 시 자동 재연결 | 무료 |
| Wi-Fi AP 모드 | `PtpipConnectionManager`, `ApModeContent` | 카메라 AP에 직접 연결 | 무료 |
| Wi-Fi STA 모드 | `PtpipConnectionManager`, `NikonAuthenticationService` | 공유기 경유 연결 (Nikon STA 인증 포함) | 무료 |
| mDNS 자동 검색 | `PtpipDiscoveryService` | UDP 브로드캐스트, mDNS 카메라 발견 | 무료 |
| Wi-Fi 자동 연결 | `AutoConnectManager`, `WifiMonitoringService` | 마지막 카메라 자동 재연결 | 무료 |

### 2.2 라이브뷰

| 기능 | 주요 클래스 | 설명 |
|------|------------|------|
| 라이브뷰 시작 | `StartLiveViewUseCase` | 실시간 스트림 (Flow<LiveViewFrame>, 30 FPS) |
| 라이브뷰 중지 | `StopLiveViewUseCase` | 스트림 중지 |
| 전체화면 모드 | `CameraControlScreen` | 더블클릭으로 전환 |

### 2.3 촬영

| 기능 | 주요 클래스 | 촬영 모드 |
|------|------------|---------|
| 원격 촬영 | `CapturePhotoUseCase` | SINGLE |
| 연속 촬영 | `CapturePhotoUseCase` | BURST |
| 장노출 | `CapturePhotoUseCase` | BULB (1초~60분) |
| HDR 브라켓팅 | `CapturePhotoUseCase`, `BracketingSettings` | HDR_BRACKET |
| 타임랩스 | `StartTimelapseUseCase`, `TimelapseSettings` | TIMELAPSE |
| 오토포커스 | `PerformAutoFocusUseCase` | 원격 AF |
| 물리 버튼 감지 | `CameraEventManager`, `PhotoCaptureEventManager` | 카메라 셔터 이벤트 수신 |

### 2.4 사진/파일 관리

| 기능 | 주요 클래스 | 구독 등급 |
|------|------------|----------|
| 사진 목록 조회 | `GetCameraPhotosPagedUseCase` | 무료 (페이지네이션 20장) |
| 썸네일 조회 | `GetCameraThumbnailUseCase` | 무료 |
| 사진 다운로드 | `PhotoDownloadManager` | 무료 (FREE: 2000px 제한) |
| RAW 파일 지원 | `ValidateImageFormatUseCase` | PRO/ADMIN/REFERRER (25가지 RAW 포맷) |
| 갤러리 저장 | MediaStore (Scoped Storage) | 무료 |

### 2.5 카메라 설정

| 기능 | 주요 클래스 |
|------|------------|
| 설정 조회 | `GetCameraSettingsUseCase` (ISO, SS, Aperture, WB, FocusMode) |
| 설정 변경 | `UpdateCameraSettingUseCase` |
| 기능 조회 | `GetCameraCapabilitiesUseCase` |

### 2.6 색감 전환 (GPU)

| 기능 | 주요 클래스 | 성능 |
|------|------------|------|
| GPU 색감 전환 (캐시) | `ColorTransferUseCase.applyColorTransferWithGPUCached` | CPU 대비 3-5배 빠름 |
| GPU 색감 전환 | `ColorTransferUseCase.applyColorTransferWithGPU` | GPUImage 기반 |
| CPU 색감 전환 | `ColorTransferUseCase.applyColorTransfer` | 폴백 |
| EXIF 보존 저장 | `ColorTransferUseCase.applyColorTransferAndSave` | EXIF 메타데이터 유지 |

### 2.7 사용자 인증

- Google Sign-In → Firebase Auth
- 추천 코드 검증/사용 (`UserReferralUseCase`)
- 자동 로그인 (기존 세션 감지)

### 2.8 구독/결제

- Google Play Billing 구독 (구매/복원/동기화)
- Firebase Firestore 구독 정보 동기화
- 실시간 티어 변화 반영 (`GetSubscriptionUseCase`, `@Singleton`)

### 2.9 관리자 기능 (ADMIN 전용)

- 전체 사용자 조회/검색/티어 변경/비활성화
- 추천 코드 생성/조회/삭제 (한번에 30개 대량 생성)
- 가상 카메라 (Mock Camera) — 실 카메라 없이 테스트
- CameraAbilities 상세 조회

### 2.10 백그라운드 컴포넌트

| 컴포넌트 | 유형 | 역할 |
|---------|------|------|
| `BackgroundSyncService` | ForegroundService | Firebase 연결 유지, 이벤트 리스너, Wake Lock |
| `WifiMonitoringService` | ForegroundService | Wi-Fi 네트워크 변화 감지, 자동 연결 트리거 |
| `AutoConnectForegroundService` | ForegroundService | Wi-Fi 조건 충족 시 자동 연결 수행 |
| `WifiSuggestionBroadcastReceiver` | BroadcastReceiver | WiFi Suggestion 콜백, 커스텀 브로드캐스트 |
| `BootCompletedReceiver` | BroadcastReceiver | 재부팅 시 서비스 재시작 |

---

## 3. 아키텍처

### 3.1 레이어 구조

```
Presentation Layer
  CameraViewModel ──→ CameraConnectionManager (presentation)
                  ──→ CameraOperationsManager
                  ──→ CameraSettingsManager (domain)
                  ──→ ErrorHandlingManager (domain)
                  ──→ CameraUiStateManager (@Singleton, 공유)

Domain Layer (Android 독립)
  UseCases (26개): camera/, auth/, usb/, root/
  Repository Interfaces: CameraRepository, AuthRepository, ...
  Domain Managers: CameraSettingsManager, ErrorHandlingManager,
                   CameraConnectionGlobalManager

Data Layer
  DataSources: NativeCameraDataSource (JNI), PtpipDataSource (Wi-Fi),
               UsbCameraManager, AppPreferencesDataSource, ...
  Repository Impls: CameraRepositoryImpl (Facade), AuthRepositoryImpl, ...
  Repo Managers: CameraConnectionManager (data), CameraEventManager,
                 PhotoDownloadManager
  Background Services, Network (PTP/IP)

Native/JNI Layer
  CameraNative.kt (80+ external 메서드)
  C++ 모듈 (22개 파일): native-lib, camera_*, color_transfer_native
  libgphoto2.so, libgphoto2_port.so, libltdl.so
```

### 3.2 의존성 방향

```
presentation → domain ← data
                ↑
            (interfaces)
```

**알려진 위반**: `CameraUiStateManager` (Presentation)가 `@Singleton`으로 Data 레이어 5개 컴포넌트에 주입됨 (아키텍처 명세 Appendix에 기록됨)

### 3.3 CameraViewModel 위임 구조

`CameraViewModel`은 직접 비즈니스 로직 없이 4개 매니저에 위임:

| 위임 대상 | 역할 |
|----------|------|
| `CameraConnectionManager` (presentation) | USB 디바이스 관찰, 연결 |
| `CameraOperationsManager` | 촬영, 라이브뷰, 타임랩스, AF |
| `CameraSettingsManager` (domain) | ISO/SS/Aperture/WB 조회·변경 |
| `ErrorHandlingManager` (domain) | JNI 에러 코드 처리 |

### 3.4 핵심 데이터 흐름

#### USB 카메라 연결

```
CameraViewModel → CameraConnectionManager(presentation) → CameraRepositoryImpl
→ CameraConnectionManager(data, Mutex) → UsbCameraManager → UsbConnectionManager (FD 획득)
→ NativeCameraDataSource → CameraNative (JNI) → gp_camera_init()
→ CameraUiStateManager.onConnectionSuccess()
```

#### 사진 촬영 후 다운로드

```
CameraViewModel → CameraOperationsManager → CameraRepositoryImpl → NativeCameraDataSource
→ CameraNative.capturePhotoAsync() → C++ camera_capture.cpp
→ CameraCaptureListener.onPhotoCaptured()
→ CameraEventManager → ValidateImageFormatUseCase (구독 티어 검증)
  → [통과] PhotoDownloadManager → MediaStore 저장 → PhotoCaptureEventManager
  → [차단] CameraUiStateManager.rawFileRestriction 업데이트
```

#### 라이브뷰

```
CameraViewModel → CameraOperationsManager → CameraRepositoryImpl
→ callbackFlow { NativeCameraDataSource.startLiveView(callback) }
→ LiveViewCallback.onLiveViewFrame(ByteBuffer) → Flow<LiveViewFrame> emit
→ CameraUiStateManager.updateLiveViewState(frame)
→ CameraControlScreen → CameraPreviewArea → BitmapFactory.decodeByteArray → Image()
```

### 3.5 JNI 인터페이스

**CameraNative.kt** (80+ external 메서드 카테고리):
카메라 초기화(6), 이벤트(3), 촬영(7), 비디오(3), 인터벌(3), 라이브뷰(3),
AF/MF(5), 파일 관리(12), 설정/진단(8), PTP/IP(5), 제조사별(12), RAW(6),
구독/제어(6), Mock/테스트(12), 메모리/로그(10), 콜백(3)

**C++ 소스 (22개 파일)**: `native-lib.cpp`, `camera_init.cpp`, `camera_init_usb.cpp`,
`camera_capture.cpp`, `camera_config.cpp`, `camera_files.cpp`, `camera_liveview.cpp`,
`camera_events.cpp`, `camera_ptpip.cpp`, `camera_nikon_auth.cpp`,
`camera_abilities.cpp`, `color_transfer_native.cpp` 등

### 3.6 Hilt DI

- **AppModule**: `@Provides @Singleton` — NativeCameraDataSource, UsbCameraManager, PtpipDataSource, CameraEventManager, PhotoDownloadManager, CameraUiStateManager 등
- **RepositoryModule**: `@Binds @Singleton` — 12개 인터페이스 → 구현체 바인딩
- **특이사항**: `PtpipDataSource`는 `Lazy<AutoConnectTaskRunner>` 주입으로 순환 의존성 방지

### 3.7 도메인 모델 핵심

| 모델 | 주요 필드 |
|------|---------|
| `CameraSettings` | iso, shutterSpeed, aperture, whiteBalance, focusMode, exposureCompensation, availableSettings |
| `CapturedPhoto` | filePath, thumbnailPath, captureTime, cameraModel, settings, size, isDownloading |
| `CameraCapabilities` | canCapturePhoto/Video, canLiveView, supportsBurst/Timelapse/Bracketing/BulbMode, availableIso/SS/Aperture/WB (40+ 필드) |
| `CameraUiState` | 40+ 필드 — 연결 상태, 촬영 상태, 라이브뷰, USB, 에러, 다이얼로그, 동적 UI |
| `SubscriptionTier` | FREE(0), BASIC(1), PRO(2), REFERRER(2), ADMIN(2) |
| `ShootingMode` | SINGLE, BURST, TIMELAPSE, BULB, HDR_BRACKET |
| `CameraError` | sealed — ConnectionError, CaptureError, NetworkError, UsbError, FileSystemError, TimeoutError, PermissionError, UnknownError |

---

## 4. UI/UX

### 4.1 화면 구조

```
SplashActivity (LAUNCHER)
  ├→ [미로그인] LoginActivity
  │     └→ [Google 로그인 성공] MainActivity
  └→ [로그인됨] MainActivity
        ├─ CameraControlScreen (기본 시작 탭)
        │     └→ [더블클릭] FullscreenCameraLayout
        ├─ PhotoPreviewScreen
        │     └→ [사진 선택] FullScreenPhotoViewer
        ├─ MyPhotosScreen
        │     └→ [사진 선택] FullScreenPhotoViewer
        └─ SettingsActivity (별도 Activity)
              ├→ PtpipConnectionActivity
              ├→ MockCameraActivity (ADMIN)
              ├→ ColorTransferSettingsActivity
              ├→ CameraAbilitiesActivity (ADMIN)
              └→ OpenSourceLicensesActivity
```

### 4.2 화면별 상세

#### CameraControlScreen
- **포트레이트 레이아웃**: TopControlsBar → CameraPreviewArea (라이브뷰) / AnimatedPhotoSwitcher → ShootingModeSelector → CameraSettingsControls → CaptureControls → RecentCapturesRow
- **전체화면 레이아웃**: CameraPreviewArea + FullscreenControlPanel
- **다이얼로그**: TimelapseSettingsDialog, CameraConnectionHelpDialog, RawFileRestrictionNotification, ModalBottomSheet (카메라 설정)

#### PhotoPreviewScreen
- **그리드**: LazyVerticalStaggeredGrid 2열, 첫 번째 사진 전체 너비 강조
- **기능**: Pull-to-refresh, 무한 스크롤(페이징), 멀티 선택/다운로드, 파일 타입 필터(ALL/RAW/JPG)
- **PTPIP 연결 시**: 미리보기 탭 차단 오버레이

#### MyPhotosScreen
- **그리드**: LazyVerticalStaggeredGrid 4열, LRU 썸네일 캐시 (64MB)
- **기능**: 사진 삭제 (단일/멀티), 새로고침
- **성능**: Semaphore(4) 동시 로드 제한, produceState 비동기 썸네일 로딩

### 4.3 테마 시스템

**컨셉**: "Navy Dark" — 전문가용 카메라 앱 다크 테마

| 토큰 | 색상 | 용도 |
|------|------|------|
| Primary | `#5B8DEF` | 주요 액션, 강조 |
| Background | `#0A0F1C` | 전체 배경 |
| Surface | `#141B2D` | 카드/패널 |
| TextPrimary | `#E8EEF5` | 주요 텍스트 |
| TextSecondary | `#8FA3BF` | 보조 텍스트 |
| Success | `#4ADE80` | 연결됨 |
| Error | `#FB7185` | 에러, 끊김 |

- **중요**: `themeMode` 설정과 무관하게 항상 `UnifiedDarkColorScheme` 적용
- **폰트**: Pretendard (Regular, Medium, SemiBold, Bold)
- **다국어**: 8개 언어 (ko, ja, zh, de, es, fr, it + 기본)

### 4.4 핵심 컴포넌트

| 컴포넌트 | 역할 |
|---------|------|
| `CameraPreviewArea` | 라이브뷰 프레임 렌더링 (BitmapFactory 직접 디코딩) |
| `CaptureControls` | 촬영/포커스/갤러리 버튼 |
| `TopControlsBar` | 연결 상태, 설정 버튼 |
| `CameraSettingsControls` | ISO/SS/Aperture 드롭다운 |
| `ShootingModeSelector` | 촬영 모드 가로 스크롤 선택기 |
| `FullScreenPhotoViewer` | 전체화면 뷰어 (줌, 스와이프) |
| `UsbInitializationOverlay` | USB 초기화 블로킹 오버레이 |
| `ZoomableImage` | 핀치 줌 지원 이미지 뷰 |

### 4.5 Compose 성능 최적화

- `@Stable` `AppSettings` data class로 설정값 묶음
- `remember`, `rememberSaveable`로 상태 캐싱
- `collectAsStateWithLifecycle` (lifecycle-runtime-compose) 전면 사용
- LazyRow/Grid에 `key` 파라미터 명시
- `DisposableEffect(lifecycleOwner)` 라이브뷰 생명주기 관리

### 4.6 ViewModel 목록

| ViewModel | 역할 |
|-----------|------|
| `CameraViewModel` | 카메라 제어 전반 (4개 매니저 위임) |
| `PhotoPreviewViewModel` | 카메라 내 사진 미리보기/페이징 |
| `ServerPhotosViewModel` | 로컬 사진 관리 |
| `PtpipViewModel` | Wi-Fi 연결 (개별 StateFlow 관리) |
| `LoginViewModel` | Google 로그인 |
| `AuthViewModel` | 인증 상태/로그아웃 |
| `AppSettingsViewModel` | 앱 설정 (테마, 기능 토글) |
| `AppVersionViewModel` | 버전 체크/업데이트 |
| `MockCameraViewModel` | Mock 카메라 (ADMIN/개발) |
| `AdminReferralCodeViewModel` | 레퍼럴 코드 관리 |
| `ColorTransferViewModel` | 색감 전송 참조 이미지 |
| `CameraAbilitiesViewModel` | 카메라 기능 정보 |

---

## 5. 코드 품질 현황

> **기준일**: 2026-03-31 | **종합 준수도**: 70/100

### 5.1 요약

| 구분 | 건수 |
|------|------|
| CRITICAL | 8건 |
| WARNING | 9건 |
| SUGGESTION | 7건 |

### 5.2 CRITICAL (즉시 수정 필요)

| # | 위치 | 문제 | 영향 |
|---|------|------|------|
| C1 | CameraRepositoryImpl, NativeCameraDataSource 외 3곳 | Data 레이어가 `CameraUiStateManager` (Presentation) 직접 의존 | Clean Architecture 위반, Data 레이어 독립 테스트 불가 |
| C2 | UsbConnectionManager 외 6곳 | 클래스 레벨 `CoroutineScope(SupervisorJob())` 패턴 — 외부 취소 불가, 테스트 불가 | `AppModule`에 `@Singleton` applicationScope 제공 필요 |
| C3 | UsbConnectionManager:357, 412 외 7곳 (총 9곳) | 코루틴 내 `Thread { }.start()` 직접 생성 | 취소 신호 무시, JNI 소멸 객체 접근으로 크래시 위험 |
| C4 | ValidateImageFormatUseCase:3 | Domain UseCase가 `AppPreferencesDataSource` (Data) 직접 의존 | Domain 독립성 파괴 |
| C5 | CameraRepositoryImpl:90 | `processedFiles` Set 무한 증가 | 타임랩스 장시간 사용 시 OOM 가능 |
| C6 | UsbConnectionManager:43 | `isInitializingNativeCamera` — `@Volatile` 없이 멀티스레드 접근 | CPU stale read로 중복 초기화 방지 무력화 |
| C7 | PhotoDownloadManager:1068-1082 | EXIF 회전 방향 역방향 적용 (ROTATE_90 → 270f 등) | 세로 촬영 사진 가로 저장, 결과물 품질 직접 영향 |
| C8 | SplashActivity:312-316 | 구독 티어 (ADMIN 포함) 스플래시 화면 평문 노출 | 화면 녹화/공유 시 보안 정보 노출 |

### 5.3 WARNING (릴리즈 전 수정 권장)

| # | 위치 | 문제 |
|---|------|------|
| W1 | CameraRepositoryImpl:338 | `parseWidgetJsonToSettings()` 하드코딩 더미값 (ISO "100", SS "1/125") |
| W2 | CameraRepositoryImpl:537-558 | `startBurstCapture`, `startTimelapse`, `startBracketing`, `startBulbCapture` 4개 미구현 (묵음 실패) |
| W3 | UsbCameraManager:61, 109 외 4곳 | 익명 `CoroutineScope(Dispatchers.IO).launch` 6곳 — 취소 불가 |
| W4 | PhotoDownloadManager:1373 | `MediaStore.Images.Media.DATA` deprecated (Android 10+, null 반환 가능) |
| W5 | PhotoDownloadManager:1400 외 2곳 | `/storage/emulated/0/DCIM/CamCon` 하드코딩 — 멀티유저/SD카드 환경 오류 |
| W6 | CameraEventManager:496, 545 | `scope.launch {}` 내 `scope.launch(Dispatchers.Main) {}` 중첩 — `withContext(Main)` 사용 필요 |
| W7 | SplashActivity:257 | `animateFloatAsState` `.value` 직접 접근 (`by` 위임 패턴 불일치) |
| W8 | CameraRepositoryImpl:87 | `scope.cancel()` 미호출 — 종료 시 정리 경로 없음 |
| W9 | CameraEventManager:303 | `catch (CancellationException)` rethrow 없음 — 구조적 동시성 위반 |

### 5.4 아키텍처 준수도

| 항목 | 상태 |
|------|------|
| Domain Android 독립성 | 대체로 양호 (위반 1건) |
| Data → Presentation 비참조 | **위반** (5개 파일) |
| UseCase 단일 책임 | 양호 |
| Coroutines 구조적 동시성 | **미흡** (비구조화 스코프 7곳, Thread{} 9곳) |
| JNI 리소스 해제 | 부분적 |

### 5.5 양호한 부분

- `AtomicBoolean`으로 중복 실행 방지 (CameraEventManager)
- `Mutex` 기반 네이티브 초기화 보호 (UsbConnectionManager)
- `callbackFlow + awaitClose` 라이브뷰 리소스 정리
- `suspendCancellableCoroutine + invokeOnCancellation` 올바른 사용
- MediaStore `IS_PENDING` 플래그로 불완전 파일 노출 방지
- `collectAsStateWithLifecycle` 전면 사용
- 토큰 로깅 제거, cleartext traffic 제한, 백업 비활성화 완료 (`6d31ee3`)

---

## 6. 테스트 전략

### 6.1 현황

| 구분 | 현황 | 커버리지 추정 |
|------|------|------------|
| 단위 테스트 | 4개 파일, 24개 케이스 | ~8% |
| 계측 테스트 | 2개 파일 (인프라만) | 실질 0% |
| Compose UI 테스트 | 0개 | 0% |
| **전체** | | **약 8%** |

**테스트 인프라**: MockK 1.13.13, Turbine 1.2.0, Robolectric 4.14.1, Hilt Testing 2.53.1, Coroutines Test 1.10.2, Compose UI Test — 모두 완비

### 6.2 기존 테스트

| 파일 | 대상 | 케이스 수 |
|------|------|---------|
| `GetSubscriptionUseCaseTest.kt` | `GetSubscriptionUseCase` | 6개 |
| `AuthViewModelTest.kt` | `AuthViewModel` | 5개 |
| `LoginViewModelTest.kt` | `LoginViewModel` | 7개 |
| `MockCameraViewModelTest.kt` | `MockCameraViewModel` | 6개 |

### 6.3 갭 분석 (우선순위별)

#### P0 — 즉시 (수익 직결)

| 대상 | 이유 | 테스트 수 |
|------|------|---------|
| `ValidateImageFormatUseCase` | RAW 파일 접근 제어 — 오류 시 구독 수익 직접 영향 | 15개 설계 |
| `CameraUiStateManager` | 40+ 필드 상태 전이 — 전체 UI 영향 | 14개 설계 |

#### P1 — 릴리즈 전

- `GetCameraSettingsUseCase`, `UpdateCameraSettingUseCase`
- `ConnectCameraUseCase`, `DisconnectCameraUseCase`
- `CapturePhotoUseCase`, `StartLiveViewUseCase`, `StopLiveViewUseCase`
- `GetCameraPhotosPagedUseCase`
- `CameraSettingsManager`, `ErrorHandlingManager`
- `CameraViewModel`, `CameraOperationsManager`

#### P2 — 이후

- `AppPreferencesDataSource` (Robolectric)
- `AdminUserManagementUseCase`, `PurchaseSubscriptionUseCase`
- `CameraConnectionGlobalManagerImpl`
- Compose UI 테스트 (CameraControlScreen, LoginScreen, PhotoPreviewScreen)

### 6.4 Fake 전략

| 클래스 | Fake 방식 | 위치 |
|--------|---------|------|
| `CameraRepository` | `FakeCameraRepository : CameraRepository` | `src/test/.../fake/` |
| `AuthRepository` | `FakeAuthRepository : AuthRepository` | `src/test/.../fake/` |
| `SubscriptionRepository` | `FakeSubscriptionRepository` | `src/test/.../fake/` |
| `AppPreferencesDataSource` | Robolectric 실 인스턴스 또는 Fake | `src/test/.../fake/` |
| `Logger` | `NoOpLogger : Logger` | `src/test/.../fake/` |
| `NativeCameraDataSource` | `mockkObject` 또는 Fake | 테스트 파일 내 |

### 6.5 자동화 불가 영역 (수동 테스트 필요)

| 영역 | 불가 이유 |
|------|---------|
| `NativeCameraDataSource`, `CameraNative` | `libgphoto2.so` 실 로딩 필요 |
| `UsbConnectionManager` | USB OTG 실물 케이블+카메라 필요 |
| `PtpipDataSource` Wi-Fi 연결 | 물리적 PTP/IP 카메라 + 동일 네트워크 필요 |
| `NikonAuthenticationService` | Nikon 실물 카메라 STA 인증 필요 |
| `ColorTransferUseCase` GPU 경로 | 실 기기 GPU 필요 |
| `BackgroundSyncService` 장기 실행 | 실 디바이스 환경 필요 |

**수동 테스트 시나리오**: MT-01 USB 연결 전체 흐름, MT-02 PTP/IP AP 모드, MT-03 Nikon STA 인증, MT-04 RAW 구독 제한, MT-05 라이브뷰 성능 (상세 절차: `_workspace/03_tester_plan.md` 참조)

### 6.6 커버리지 목표

| 레이어 | 현재 | 목표 |
|--------|------|------|
| Domain UseCase (26개) | ~4% | 80%+ |
| Domain Manager (4개) | 0% | 70%+ |
| ViewModel (20개) | 15% | 70%+ |
| Repository Impl (8개) | 0% | 50%+ |
| Compose UI | 0% | 40%+ |
| **전체** | **~8%** | **60%+** |

---

## 부록

### 산출물 파일 목록

| 파일 | 내용 |
|------|------|
| `_workspace/01_planner_spec.md` | 전체 기능 명세 (562줄) |
| `_workspace/02_architect_spec.md` | 전체 아키텍처 문서 (1174줄) |
| `_workspace/02_designer_spec.md` | 전체 UI/UX 문서 (815줄) |
| `_workspace/03_reviewer_report.md` | 코드 품질 리뷰 리포트 (151줄) |
| `_workspace/03_tester_plan.md` | 테스트 현황 및 갭 분석 (723줄) |

### 다음 작업 우선순위

**갱신일: 2026-04-22**

#### 에이전트 파이프라인 완료 후 해소된 이슈

- ✅ **C7** EXIF 회전 방향 수정 (`PhotoDownloadManager.kt` + `ExifHandlingUtils.kt`) — 2026-04-22
- ✅ **W2** 미구현 촬영 모드 에러 처리 (`UnsupportedShootingModeException`, Snackbar UI) — 2026-04-22
- ✅ **C5** `processedFiles` OOM 회귀 테스트 작성 (`CameraRepositoryImplLruCacheTest.kt`) — 2026-04-22
- ✅ **W-1** FullScreenPhotoViewer 라인 수 재측정 및 검증 (365줄 확인, 분해 불필요) — 2026-04-22

#### 잔존 우선순위

1. **[CRITICAL] C8** SplashActivity 구독 티어 노출 제거 — 사용자 신뢰도 영향
2. **[CRITICAL] C6** `isInitializingNativeCamera` → `AtomicBoolean` — 스레드 안전성
3. **[CRITICAL] C3** `Thread { }.start()` → `scope.launch(Dispatchers.IO)` — 구조적 동시성
4. **[P0 테스트]** `ValidateImageFormatUseCase`, `CameraUiStateManager` 테스트 작성 — 커버리지
5. **[CRITICAL] C4** `ValidateImageFormatUseCase` DataSource 의존성 분리 (`AppSettingsRepository` 인터페이스)
6. **[CRITICAL] C1** `CameraUiStateManager` Domain 인터페이스화 (`CameraStateObserver`)
