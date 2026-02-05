# CamCon Android 프로젝트 구조 분석 보고서

**작성일**: 2026년 2월 5일  
**프로젝트**: CamCon (카메라 제어 애플리케이션)  
**패키지명**: `com.inik.camcon`

---

## 1️⃣ 모듈 구조

### 현재 모듈 구성
- **app** (메인 모듈): CamCon의 모든 기능을 포함하는 단일 모듈 구조

### 빌드 설정
```
settings.gradle:
  - rootProject.name = "CamCon"
  - include ':app'
```

**분석**: 현재는 **단일 모듈 구조**로 운영 중이며, 향후 기능 확장 시 feature 모듈로 분리 가능

---

## 2️⃣ 주요 패키지 구조

### 📱 Clean Architecture 기반 3-Layer 구조 준수

```
com.inik.camcon/
├── 🎨 presentation/          (프레젠테이션 계층)
│   ├── navigation/           (Navigation Compose 라우팅)
│   │   ├── AppNavigation.kt
│   │   ├── MainNavigation.kt
│   │   └── PtpipNavigation.kt
│   ├── theme/               (Compose 테마 설정)
│   │   ├── Color.kt
│   │   ├── Shape.kt
│   │   ├── Theme.kt
│   │   └── Type.kt
│   ├── ui/                  (Activities & Screens)
│   │   ├── MainActivity.kt
│   │   ├── LoginActivity.kt
│   │   ├── SplashActivity.kt
│   │   ├── SettingsActivity.kt
│   │   ├── ColorTransferSettingsActivity.kt
│   │   ├── PtpipConnectionActivity.kt
│   │   ├── OpenSourceLicensesActivity.kt
│   │   ├── screens/         (Composable 화면)
│   │   │   ├── CameraControlScreen.kt
│   │   │   ├── PhotoPreviewScreen.kt
│   │   │   ├── PtpipConnectionScreen.kt
│   │   │   ├── ServerPhotosScreen.kt
│   │   │   └── ColorTransferImagePickerScreen.kt
│   │   └── screens/components/  (UI 컴포넌트)
│   │       ├── CameraPreviewArea.kt
│   │       ├── CaptureControls.kt
│   │       ├── FullScreenPhotoViewer.kt
│   │       ├── PhotoThumbnail.kt
│   │       ├── ShootingModeSelector.kt
│   │       ├── TopControlsBar.kt
│   │       ├── ZoomableImage.kt
│   │       ├── ApModeContent.kt
│   │       ├── StaModeContent.kt
│   │       ├── ColorTransferLivePreview.kt
│   │       ├── WifiScanResultsCard.kt
│   │       └── [더 많은 UI 유틸리티]
│   └── viewmodel/           (MVVM - ViewModel + State)
│       ├── CameraViewModel.kt
│       ├── AuthViewModel.kt
│       ├── LoginViewModel.kt
│       ├── PtpipViewModel.kt
│       ├── ColorTransferViewModel.kt
│       ├── PhotoPreviewViewModel.kt
│       ├── ServerPhotosViewModel.kt
│       ├── AppSettingsViewModel.kt
│       ├── AppVersionViewModel.kt
│       ├── photo/           (포토 관리)
│       │   ├── PhotoListManager.kt
│       │   ├── PhotoImageManager.kt
│       │   └── PhotoSelectionManager.kt
│       └── state/           (UI 상태 관리)
│           └── CameraUiStateManager.kt
│
├── 🎯 domain/               (도메인 계층 - 비즈니스 로직)
│   ├── model/               (Data Models)
│   │   ├── Camera.kt
│   │   ├── CameraPhoto.kt
│   │   ├── CameraAbilities.kt
│   │   ├── CameraConnectionModels.kt
│   │   ├── CameraError.kt
│   │   ├── CameraFeature.kt
│   │   ├── User.kt
│   │   ├── Subscription.kt
│   │   ├── SubscriptionProduct.kt
│   │   ├── SubscriptionTier.kt
│   │   ├── AppVersionInfo.kt
│   │   ├── ReferralCode.kt
│   │   ├── PtpipModels.kt
│   │   ├── ImageFormat.kt
│   │   ├── AutoConnectNetworkConfig.kt
│   │   └── [기타 도메인 모델]
│   ├── repository/          (Repository Interfaces)
│   │   ├── CameraRepository.kt
│   │   ├── AuthRepository.kt
│   │   ├── SubscriptionRepository.kt
│   │   └── AppUpdateRepository.kt
│   ├── usecase/             (Use Cases / Business Logic)
│   │   ├── camera/          (카메라 관련 Use Cases)
│   │   │   ├── ConnectCameraUseCase.kt
│   │   │   ├── DisconnectCameraUseCase.kt
│   │   │   ├── CapturePhotoUseCase.kt
│   │   │   ├── StartLiveViewUseCase.kt
│   │   │   ├── StopLiveViewUseCase.kt
│   │   │   ├── StartTimelapseUseCase.kt
│   │   │   ├── GetCameraPhotosUseCase.kt
│   │   │   ├── GetCameraPhotosPagedUseCase.kt
│   │   │   ├── GetCameraCapabilitiesUseCase.kt
│   │   │   ├── GetCameraSettingsUseCase.kt
│   │   │   ├── UpdateCameraSettingUseCase.kt
│   │   │   ├── PerformAutoFocusUseCase.kt
│   │   │   └── PhotoCaptureEventManager.kt
│   │   ├── auth/            (인증 관련 Use Cases)
│   │   │   ├── SignInWithGoogleUseCase.kt
│   │   │   ├── SignOutUseCase.kt
│   │   │   ├── GetCurrentUserUseCase.kt
│   │   │   ├── AdminUserManagementUseCase.kt
│   │   │   └── UserReferralUseCase.kt
│   │   ├── usb/             (USB 관련 Use Cases)
│   │   │   ├── RefreshUsbDevicesUseCase.kt
│   │   │   └── RequestUsbPermissionUseCase.kt
│   │   ├── CapturePhotoUseCase.kt
│   │   ├── CheckAppVersionUseCase.kt
│   │   ├── ColorTransferUseCase.kt
│   │   ├── GetSubscriptionUseCase.kt
│   │   ├── PurchaseSubscriptionUseCase.kt
│   │   ├── StartImmediateUpdateUseCase.kt
│   │   ├── ValidateImageFormatUseCase.kt
│   │   └── [기타 Use Cases]
│   └── manager/             (Domain Managers)
│       ├── CameraConnectionGlobalManager.kt (카메라 연결 관리)
│       ├── CameraSettingsManager.kt
│       ├── ErrorHandlingManager.kt
│       └── NativeLogManager.kt
│
└── 💾 data/                 (데이터 계층 - 데이터 접근)
    ├── datasource/          (Data Sources - 데이터 원본)
    │   ├── local/           (로컬 데이터)
    │   │   ├── AppPreferencesDataSource.kt (DataStore)
    │   │   ├── LocalCameraDataSource.kt
    │   │   └── PtpipPreferencesDataSource.kt
    │   ├── remote/          (원격 데이터)
    │   │   ├── AuthRemoteDataSource.kt (Firebase Auth)
    │   │   ├── AuthRemoteDataSourceImpl.kt
    │   │   ├── RemoteCameraDataSource.kt
    │   │   └── PlayStoreVersionDataSource.kt
    │   ├── usb/             (USB 카메라)
    │   │   ├── UsbCameraManager.kt
    │   │   ├── UsbConnectionManager.kt
    │   │   ├── UsbDeviceDetector.kt
    │   │   ├── UsbConnectionRecovery.kt
    │   │   └── CameraCapabilitiesManager.kt
    │   ├── ptpip/           (PTP-IP 프로토콜)
    │   │   └── PtpipDataSource.kt
    │   ├── nativesource/    (Native C++ 데이터)
    │   │   ├── NativeCameraDataSource.kt
    │   │   ├── CameraCaptureListener.kt
    │   │   └── LiveViewCallback.kt
    │   ├── billing/         (Google Play Billing)
    │   │   ├── BillingDataSource.kt
    │   │   └── BillingDataSourceImpl.kt
    │   └── [기타 데이터 소스]
    ├── repository/          (Repository Implementations)
    │   ├── CameraRepositoryImpl.kt
    │   ├── AuthRepositoryImpl.kt
    │   ├── SubscriptionRepositoryImpl.kt
    │   ├── AppUpdateRepositoryImpl.kt
    │   └── managers/        (비즈니스 로직 매니저)
    │       ├── CameraConnectionManager.kt
    │       ├── CameraConnectionGlobalManagerImpl.kt
    │       ├── CameraEventManager.kt
    │       └── PhotoDownloadManager.kt
    ├── network/             (네트워크 계층)
    │   └── ptpip/           (PTP-IP 네트워크 프로토콜)
    │       ├── authentication/
    │       │   └── NikonAuthenticationService.kt
    │       ├── connection/
    │       │   └── PtpipConnectionManager.kt
    │       ├── discovery/
    │       │   └── PtpipDiscoveryService.kt
    │       └── wifi/
    │           └── WifiNetworkHelper.kt
    ├── service/             (백그라운드 서비스)
    │   ├── AutoConnectForegroundService.kt
    │   ├── AutoConnectManager.kt
    │   ├── AutoConnectTaskRunner.kt
    │   ├── BackgroundSyncService.kt
    │   └── WifiMonitoringService.kt
    ├── processor/           (이미지 처리)
    │   └── ColorTransferProcessor.kt
    ├── receiver/            (Broadcast Receivers)
    │   ├── BootCompletedReceiver.kt
    │   └── WifiSuggestionBroadcastReceiver.kt
    ├── constants/           (상수)
    │   └── PtpipConstants.kt
    └── [기타 데이터 계층 구성요소]

├── di/                      (Dependency Injection - Hilt)
│   ├── AppModule.kt         (App 레벨 의존성)
│   └── RepositoryModule.kt  (Repository 의존성)

├── utils/                   (유틸리티)
│   ├── Constants.kt
│   ├── LogcatManager.kt
│   ├── ResultExtensions.kt
│   └── SubscriptionUtils.kt

├── CamCon.kt               (Application 클래스)
└── CameraNative.kt         (Native 메서드 래퍼)
```

---

## 3️⃣ 사용된 주요 라이브러리 및 의존성

### 🏗️ 핵심 Android Framework
```gradle
androidx-core-ktx: 1.15.0           # Kotlin extensions
androidx-appcompat: 1.7.0           # AppCompat support
material: 1.12.0                    # Material Design 2
androidx-constraintlayout: 2.2.1    # 레이아웃 엔진
androidx-activity: 1.10.1           # Activity API
```

### 🎨 Jetpack Compose (UI Framework)
```gradle
compose-bom: 2024.12.01             # Compose Bill of Materials
androidx-compose-ui: 1.3.0          # Compose UI
androidx-compose-material: 1.3.0    # Material Design components
androidx-compose-material3: 1.3.2   # Material 3
androidx-compose-foundation: ~       # Foundation components
androidx-activity-compose: 1.10.1   # Activity integration
androidx-navigation-compose: 2.7.7  # Navigation routing
```

### 🔌 의존성 주입 (Hilt/Dagger)
```gradle
hilt-android: 2.53.1                # Dependency injection
hilt-android-compiler: 2.53.1       # Code generation
hilt-navigation-compose: 1.0.0      # Navigation integration
dagger.hilt.android: 2.49            # Gradle plugin
```

### ⏱️ 비동기 처리 (Kotlin Coroutines)
```gradle
kotlinx-coroutines-android: 1.10.2  # Android coroutines
kotlinx-coroutines-play-services: 1.10.2  # Firebase integration
```

### 🔐 인증 & 백엔드 (Firebase)
```gradle
firebase-bom: 33.4.0                # Firebase Bill of Materials
firebase-auth-ktx                   # Firebase Authentication
firebase-firestore-ktx              # Firestore (실시간 DB)
firebase-messaging-ktx              # FCM (푸시 알림)
firebase-analytics                  # Analytics
firebase-config-ktx                 # Remote Config
play-services-auth: 21.1.1          # Google Sign-In
credentials: 1.3.0                  # Credentials API
credentials-play-services-auth: 1.3.0
googleid: 1.1.1                     # Google ID library
```

### 📷 카메라 & 이미지 처리
```gradle
coil-compose: 2.7.0                 # 이미지 로딩 (Compose)
gpuimage: 2.1.0                     # GPU 기반 이미지 처리 (색상 전이)
exifinterface: 1.4.1                # 이미지 메타데이터
zoomimage-compose-coil2: 1.4.0-beta04  # 이미지 줌/팬 제어
ImageViewer: 1.0.3                  # 이미지 뷰어 (GitHub: 0xZhangKe)
```

### 💾 로컬 데이터 저장
```gradle
datastore-preferences: 1.0.0        # 키-값 저장소 (SharedPreferences 대체)
```

### 🌐 네트워킹
```gradle
retrofit2: 2.9.0                    # HTTP 클라이언트
retrofit2-converter-gson: 2.9.0     # JSON 변환
okhttp3: 4.12.0                     # HTTP 라이브러리
okhttp3-logging-interceptor: 4.12.0 # HTTP 로깅
```

### 💰 구독/결제 (Google Play Billing)
```gradle
billing-ktx: 6.1.0                  # In-App Billing Library
```

### 🎯 기타 유틸리티
```gradle
gson: 2.10.1                        # JSON 직렬화
Toasty: 1.5.2                       # 스타일 좋은 Toast
accompanist-systemuicontroller: 0.32.0  # 시스템 UI 제어
play-services-oss-licenses: 17.1.0  # OSS 라이선스 표시
```

### 📝 코드 생성 & 컴파일러
```gradle
kotlin: 2.1.0                       # Kotlin 컴파일러
compose-compiler: (kotlin 기반)     # Compose 컴파일러 플러그인
ksp: 2.1.0-1.0.29                   # Kotlin Symbol Processing
```

### ✅ 테스트 라이브러리
```gradle
junit: 4.13.2                       # Unit Testing Framework
androidx-junit: 1.2.1               # AndroidX 테스트 확장
androidx-espresso-core: 3.6.1       # UI 자동화 테스트
```

---

## 4️⃣ Gradle 설정 파일 위치 및 버전 정보

### 📄 Gradle 파일 구조
```
project_root/
├── build.gradle                    # Top-level build file (10줄)
├── settings.gradle                 # Project configuration (19줄)
├── gradle.properties               # Gradle properties (13줄)
├── gradle/
│   ├── libs.versions.toml          # Version catalog (50줄)
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
└── app/
    └── build.gradle                # App module build file (457줄)
```

### 🔖 주요 버전 정보 (libs.versions.toml)
```toml
[versions]
agp = "8.7.2"                          # Android Gradle Plugin
kotlin = "2.1.0"                       # Kotlin 버전
compileSdk = 36                        # Android 15 (최신)
minSdk = 29                            # Android 10
targetSdk = 36                         # Android 15

# Core AndroidX
coreKtx = "1.15.0"
appcompat = "1.7.0"
material = "1.12.0"
constraintlayout = "2.2.1"
activity = "1.10.1"

# Compose & UI
compose = "1.3.0"
activity-compose = "1.6.1"
composeMaterial = "1.4.1"
material3Android = "1.3.2"
runtimeLivedata = "1.7.8"

# DI & Architecture
hilt = "2.53.1"
hilt-navigation-compose = "1.0.0"
ksp = "2.1.0-1.0.29"

# Async
coroutines = "1.6.4"

# Testing
junit = "4.13.2"
junitVersion = "1.2.1"
espressoCore = "3.6.1"
```

### 📋 앱 모듈 빌드 설정 (app/build.gradle)
```gradle
android {
    namespace 'com.inik.camcon'
    compileSdk 36
    
    defaultConfig {
        applicationId "com.inik.camcon"
        minSdk 29
        targetSdk 36
        versionCode getAppVersionCode()      # 동적 생성
        versionName getAppVersionName()      # 동적 생성 (브랜치별)
        
        # 지원 ABI: ARM64 (16KB 페이지 지원 - Android 15+)
        ndk { abiFilters "arm64-v8a" }
        
        # 지원 언어
        resConfigs "en", "ko", "it", "fr", "de", "ja"
    }
    
    buildTypes {
        debug {
            debuggable = true
            minifyEnabled = false
            versionNameSuffix "-debug"
            buildConfigField "boolean", "SHOW_DEVELOPER_FEATURES", "true"
        }
        
        release {
            debuggable = false
            minifyEnabled = true
            proguardFiles 'proguard-rules.pro'
            buildConfigField "boolean", "SHOW_DEVELOPER_FEATURES", "false"
        }
    }
    
    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true
    }
    
    externalNativeBuild {
        cmake {
            path 'src/main/cpp/CMakeLists.txt'
            version '3.22.1'
        }
    }
    
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_11
        targetCompatibility JavaVersion.VERSION_11
    }
    
    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs += [
            "-opt-in=androidx.compose.material.ExperimentalMaterialApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi"
        ]
    }
}
```

### 🔄 버전 관리 시스템
```kotlin
// 수동 버전 정의
majorVersion = 1
minorVersion = 2
patchVersion = 1

// 브랜치별 자동 버전 식별자
main/master:        "1.2.1"
release/*:          "1.2.1-rc"
hotfix/*:           "1.2.1-hotfix.{hash}"
develop:            "1.2.1-dev.{hash}"
feature/*:          "1.2.1-feature.{hash}"

// Version Code (Play Store 단조증가 보장)
formula: majorVersion(2자리) + minorVersion(2자리) + patchVersion(2자리) + buildNumber
example: 1*1000000 + 2*10000 + 1*100 + buildNumber%100
```

---

## 5️⃣ 테스트 파일 구조

### 📁 테스트 디렉토리 구조
```
app/src/
├── main/                           # 메인 소스 (163개 파일)
├── androidTest/                    # Instrumented Tests (Android 기기/에뮬레이터)
│   └── java/com/inik/camcon/
│       └── ExampleInstrumentedTest.kt  # 예제 테스트
└── [test/ 디렉토리 부재]            # Unit Tests 디렉토리 없음
```

### 🧪 현재 테스트 상황
- ✅ **androidTest**: Instrumented Test 기본 구조 있음 (1개 파일)
- ❌ **test**: Unit Test 디렉토리 미구성
- ⚠️ **테스트 커버리지**: 미미한 상태 (기본 예제만 존재)

### 📦 테스트 의존성 (gradle에서 정의된 부분)
```gradle
dependencies {
    // Unit Testing
    testImplementation libs.junit (4.13.2)
    
    // Android Instrumented Testing
    androidTestImplementation libs.androidx.junit (1.2.1)
    androidTestImplementation libs.androidx.espresso.core (3.6.1)
    
    // 추가 테스트 라이브러리: 없음 (Hilt, Mockito, Coroutine Test 등 미포함)
}
```

### ⚠️ 테스트 구성 현황 분석
| 테스트 타입 | 상태 | 파일 수 | 비고 |
|-----------|------|--------|------|
| Unit Test (test/) | ❌ 미구성 | 0 | - |
| Instrumented Test (androidTest/) | ⚠️ 기본 | 1 | ExampleInstrumentedTest.kt |
| 테스트 Hilt 통합 | ❌ 미설정 | - | Hilt TestComponent 미구성 |
| Coroutine Test | ❌ 미설정 | - | TestDispatchers 미포함 |
| Mock/Stub 라이브러리 | ❌ 미포함 | - | Mockito, MockK 부재 |

---

## 6️⃣ 리소스 구조

### 📱 드로어블 & 미디어
```
res/
├── drawable/              # 벡터 드로어블, 비트맵
├── mipmap-*dpi/          # 앱 아이콘 (6가지 DPI)
└── [애니메이션 리소스]
```

### 🌍 다국어 지원 (i18n)
```
values/                    # 기본 (영어)
values-ko/                 # 한국어
values-de/                 # 독일어
values-es/                 # 스페인어
values-fr/                 # 프랑스어
values-it/                 # 이탈리아어
values-ja/                 # 일본어
values-zh/                 # 중국어
values-night/              # 다크모드
```

### 🎨 폰트
```
font/                      # Custom fonts (Compose에서 사용)
```

### 📐 레이아웃
```
layout/                    # XML 레이아웃 (레거시 또는 특수 용도)
```

### ⚙️ 기타 리소스
```
xml/                       # 네트워크 보안, 시스템 UI, 설정 등
```

---

## 7️⃣ 네이티브 코드 (C++)

### 📂 구조
```
app/src/main/
├── cpp/
│   └── CMakeLists.txt                # CMake 빌드 설정
├── jniLibs/                          # 사전 빌드된 .so 라이브러리
│   └── arm64-v8a/                    # ARM64 아키텍처
│       ├── libyuv-decoder.so         # YUV 디코딩
│       ├── libnative-lib.so          # 메인 네이티브 라이브러리
│       └── [기타 의존 라이브러리]
└── jniLibs_bak/                      # 백업 디렉토리
```

### 🔧 빌드 설정
```gradle
externalNativeBuild {
    cmake {
        path = 'src/main/cpp/CMakeLists.txt'
        version = '3.22.1'
    }
}

# 빌드 타입별 최적화
Debug:        -DCMAKE_BUILD_TYPE=Debug
Release:      -DCMAKE_BUILD_TYPE=Release -O3 -flto -ffast-math
```

### ⚠️ Android 15+ 호환성
- 16KB 페이지 크기 정렬: `-Wl,-z,max-page-size=16384`
- 지원 ABI: `arm64-v8a` (64-bit만)

---

## 8️⃣ 전체 소스 코드 통계

### 📊 파일 및 라인 수
```
총 소스 파일 수: 163개

계층별 분석:
┌─────────────────────┬──────────┬────────────┐
│ 계층                │  파일 수 │  대략 기능 │
├─────────────────────┼──────────┼────────────┤
│ Presentation (UI)   │   65개   │ UI/화면    │
│ Domain (Business)   │   37개   │ 비즈니스   │
│ Data (Repository)   │   41개   │ 데이터     │
│ DI & Utils          │   19개   │ 설정/유틸  │
└─────────────────────┴──────────┴────────────┘

테스트 파일:
- Unit Test:         0개 ❌
- Instrumented Test: 1개 ⚠️
```

---

## 9️⃣ 핵심 기술 스택 요약

### 🏛️ 아키텍처 패턴
- ✅ **Clean Architecture** (Domain/Data/Presentation 분리)
- ✅ **MVVM** (ViewModel + StateFlow/LiveData)
- ✅ **Repository Pattern** (데이터 추상화)
- ✅ **Dependency Injection** (Hilt)

### 🎯 UI 프레임워크
- ✅ **Jetpack Compose** (선언형 UI)
- ✅ **Navigation Compose** (라우팅)
- ✅ **Material Design 3** (디자인 시스템)
- ✅ **Coil** (이미지 로딩)

### 📡 데이터 & 백엔드
- ✅ **Firebase** (Auth, Firestore, Messaging, Analytics)
- ✅ **Google Sign-In** (소셜 로그인)
- ✅ **Google Play Billing** (구독/결제)
- ✅ **DataStore** (로컬 저장소)
- ✅ **Retrofit + OkHttp** (REST API)

### 📷 특화 기능
- ✅ **USB PTP/IP** (카메라 연결)
- ✅ **네이티브 C++** (고성능 이미지 처리)
- ✅ **GPU 이미지 처리** (색상 전이, 필터)
- ✅ **PTP-IP Wi-Fi** (무선 카메라 제어)

### ⚙️ 백그라운드 & 서비스
- ✅ **Kotlin Coroutines** (비동기 처리)
- ✅ **Foreground Service** (지속적 연결)
- ✅ **Broadcast Receiver** (시스템 이벤트)
- ✅ **WorkManager** (백그라운드 작업) - 미포함

---

## 🔟 주요 설정 파일

### 📋 AndroidManifest.xml (216줄)
```xml
주요 구성:
- 권한: CAMERA, USB, INTERNET, WIFI, STORAGE 등
- Activities: MainActivity, LoginActivity, SplashActivity, 등
- Services: AutoConnectForegroundService, BackgroundSyncService
- Receivers: BootCompletedReceiver, WifiSuggestionBroadcastReceiver
- Firebase 설정: google-services.json
```

### 🔑 키 저장소
```
KEY/
├── appKey.jks                  # 앱 서명 키스토어
├── upload_certificate.pem      # Play Store 업로드 인증서
└── (key.properties)            # Gradle에서 참조 (보안 파일)
```

### 🛡️ 보안 설정
```gradle
signingConfigs {
    release {
        keyAlias = properties['keyAlias']
        keyPassword = properties['keyPassword']
        storeFile = file(properties['storeFile'])
        storePassword = properties['storePassword']
    }
}
```

### 🚀 배포 설정
```
app/release/               # 릴리스 APK/AAB 빌드 결과물
proguard-rules.pro         # ProGuard 난독화 규칙
```

---

## 🎯 프로젝트 특이점 및 주목할 사항

### ✨ 강점
1. **명확한 아키텍처**: Clean Architecture 철저히 준수
2. **최신 기술 스택**: Compose, Hilt, Coroutines 등 현대적 라이브러리 사용
3. **다층 데이터 소스**: USB, PTP-IP, 로컬, 원격 데이터 소스 통합
4. **네이티브 성능**: C++ 네이티브 라이브러리로 고성능 이미지 처리
5. **다국어 지원**: 8개 언어 기본 지원
6. **보안**: Firebase 인증, 암호화 키 관리

### ⚠️ 개선 필요 영역
1. **테스트 커버리지 부족**
   - Unit Test (test/) 디렉토리 미구성
   - Instrumented Test 미미한 상태
   - 권장: Hilt Test, Coroutine Test, Mockito 추가 구성

2. **단일 모듈 구조**
   - 향후 피처 증가 시 feature 모듈로 분리 검토

3. **의존성 관리 최적화**
   - 일부 라이브러리 중복 (예: material, exifinterface)
   - gradle 플러그인 버전 통일 검토

---

## 📌 결론

**CamCon 프로젝트는 Clean Architecture 원칙을 철저히 따르는 고도로 구조화된 Android 애플리케이션**입니다.

- 📱 **Presentation Layer**: Jetpack Compose + MVVM 패턴으로 현대적 UI 구현
- 🎯 **Domain Layer**: 37개의 use case로 비즈니스 로직 체계화
- 💾 **Data Layer**: 5개의 다양한 데이터 소스 (USB, 네트워크, 로컬, 네이티브, 결제)
- 🔧 **Infrastructure**: Hilt DI, Firebase 백엔드, 네이티브 C++ 통합

**다음 단계 권장사항**:
1. 테스트 기반 구조 확립 (unit, integration, e2e)
2. CI/CD 파이프라인 구축 (자동 테스트, 빌드, 배포)
3. 성능 모니터링 및 크래시 리포팅 (Firebase Crashlytics)
4. 모듈화 전략 수립 (feature modules)

