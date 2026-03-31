# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 개요

CamCon은 DSLR/미러리스 카메라를 안드로이드 기기로 제어하는 전문가용 앱이다.
- **패키지**: `com.inik.camcon`
- **minSdk**: 29 (Android 10), **targetSdk**: 36
- **ABI**: `arm64-v8a` 전용 (16KB 페이지 크기 지원, Android 15+ 필수)

## 빌드 및 테스트

```bash
# 빌드
./gradlew assembleDebug
./gradlew assembleRelease        # key.properties 필요

# 테스트
./gradlew :app:testDebugUnitTest
./gradlew :app:testDebugUnitTest --tests "com.inik.camcon.presentation.viewmodel.AuthViewModelTest.methodName"
./gradlew :app:connectedDebugAndroidTest

# 버전 확인
./gradlew printVersion
```

릴리즈 빌드는 프로젝트 루트의 `key.properties` 파일이 필요하다 (gitignore됨).

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
│   │   ├── camera/        # 카메라 조작 UseCase
│   │   ├── auth/          # 인증 UseCase
│   │   └── usb/           # USB 장치 UseCase
│   └── manager/           # 도메인 매니저 인터페이스
├── data/
│   ├── datasource/
│   │   ├── nativesource/  # JNI 네이티브 카메라 데이터소스
│   │   ├── usb/           # USB 연결 관리 (UsbCameraManager, UsbConnectionManager 등)
│   │   ├── ptpip/         # PTP/IP Wi-Fi 데이터소스
│   │   ├── local/         # DataStore 기반 로컬 설정
│   │   ├── remote/        # Firebase 원격 데이터소스
│   │   └── billing/       # Google Play Billing
│   ├── repository/
│   │   └── managers/      # CameraConnectionManager, CameraEventManager, PhotoDownloadManager
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
- `CameraConnectionManager` — 연결/해제
- `CameraOperationsManager` — 촬영, 라이브뷰, 타임랩스
- `CameraSettingsManager` — 카메라 설정 변경
- `ErrorHandlingManager` — 에러 처리

**CameraUiStateManager**: `@Singleton` Hilt 바인딩. ViewModel과 DataSource 양쪽에서 주입받아 상태를 공유한다. `CameraUiState`를 `StateFlow`로 노출한다.

**연결 모드 2가지**:
- USB OTG: `NativeCameraDataSource` → JNI → `libnative-lib.so` → libgphoto2
- Wi-Fi PTP/IP: `PtpipDataSource` → `PtpipConnectionManager` (TCP) + `PtpipDiscoveryService` (UDP)

### JNI/Native 레이어

- `CameraNative.kt` — 코틀린 싱글톤 객체, 모든 native 함수 선언
- `app/src/main/cpp/native-lib.cpp` — JNI 엔트리포인트
- `app/src/main/cpp/` — C++17 소스 (카메라 기능별 분리)
- `app/src/main/jniLibs/arm64-v8a/` — 사전 빌드된 `.so` 라이브러리들 (libgphoto2, libgphoto2_port, libltdl)
- 네이티브 라이브러리 로딩 실패 시 `CameraNative.init`에서 `RuntimeException`을 던짐

## 의존성 주입

모든 싱글톤은 `AppModule`에, Repository 바인딩은 `RepositoryModule`에 정의된다.
어노테이션 처리기로 KAPT 대신 **KSP**를 사용한다.

## 테스트 구조

- **단위 테스트**: `app/src/test/` — MockK, Turbine(Flow 테스트), Robolectric, Hilt testing
- **계측 테스트**: `app/src/androidTest/` — `HiltTestRunner` 사용 (커스텀 runner)
- ViewModel 테스트는 `arch-core-testing`의 `InstantTaskExecutorRule` 활용

## Firebase

`google-services.json`은 `app/` 아래에 커밋되어 있다. Auth, Firestore, Analytics, Remote Config, Messaging을 사용한다.

## 버전 관리

`app/build.gradle`에서 직접 관리한다: `majorVersion`, `minorVersion`, `patchVersion`. 버전 코드는 Git 커밋 카운트 기반으로 자동 생성된다. 브랜치별 버전 suffix가 자동으로 붙는다 (`-dev.hash`, `-rc` 등).

## 진행 중인 작업

현재 브랜치 `refactor/phase1-architecture-cleanup`에서 아키텍처 정리 중. 관련 계획 문서: `docs/superpowers/plans/2026-03-30-phase2-coroutine-stability.md`
