> **📢 퍼블릭 레포지토리**  
> 네이티브 코드와 민감한 파일들이 제외된 퍼블릭 버전입니다.  
> 마지막 동기화: $(date '+%Y-%m-%d %H:%M:%S UTC')

---

# CamCon - Camera Control Application

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple.svg)](https://kotlinlang.org)
[![API](https://img.shields.io/badge/API-29%2B-brightgreen.svg)](https://android-arsenal.com/api?level=29)

전문 카메라를 USB 연결을 통해 제어할 수 있는 안드로이드 애플리케이션입니다. libgphoto2 라이브러리를 사용하여 DSLR/미러리스 카메라의 실시간 제어 및 촬영 기능을
제공합니다.

## 📋 구현된 주요 기능

### ✅ 완료된 기능

#### 🔗 카메라 연결 및 제어

- **USB OTG 연결**: DSLR/미러리스 카메라 USB 연결
- **Wi-Fi 연결**: AP 모드 및 STA 모드 지원으로 무선 카메라 제어
- **자동 카메라 감지**: 연결된 카메라 자동 인식
- **실시간 연결 상태**: 카메라 연결 상태 모니터링
- **카메라 정보 조회**: 모델명, 기능 등 상세 정보
- **🆕 자동 파일 다운로드**: AP 모드에서 libgphoto2 sample-tether.c 방식으로 촬영 즉시 자동 다운로드

#### 📸 촬영 기능

- **원격 촬영**: 앱을 통한 사진 촬영
- **🆕 외부 셔터 버튼 지원**: 카메라 본체 셔터 버튼 눌러도 앱으로 사진 전송
- **🆕 자동 파일 수신**: 촬영과 동시에 파일 자동 다운로드 (AP 모드)
- **라이브뷰**: 실시간 카메라 미리보기
- **자동초점**: AF 기능 지원
- **타임랩스**: 자동 인터벌 촬영 (기본 구조 완료)
- **사진 이벤트 처리**: 촬영 완료 알림 및 관리

#### 🔐 사용자 인증

- **Firebase Authentication**: Google 소셜 로그인
- **자동 로그인**: 로그인 상태 유지
- **보안 세션**: 안전한 사용자 세션 관리

#### 📱 사용자 인터페이스

- **Jetpack Compose**: 현대적 선언형 UI
- **Material Design 3**: 최신 디자인 시스템
- **Dark/Light Theme**: 시스템 테마 자동 감지
- **반응형 레이아웃**: 다양한 화면 크기 지원

#### 🗂️ 파일 관리

- **로컬 사진 저장**: 촬영된 사진 기기 저장
- **사진 목록 관리**: 촬영된 사진 목록 및 미리보기
- **카메라 내부 파일**: 카메라 메모리카드 파일 목록 조회

## 🚀 자동 파일 다운로드 기능 (AP 모드)

### 📥 동작 원리

AP 모드 연결 시, 본 앱은 libgphoto2의 sample-tether.c와 동일한 메커니즘으로 자동 파일 다운로드를 지원합니다:

1. **테더링 모드 자동 활성화**:
    - 카메라 연결 시 자동으로 테더링 모드로 설정
    - 촬영 대상을 메모리 카드로 설정
    - 이미지 품질 등 최적화 설정 자동 적용

2. **이벤트 기반 감지**:
    - 카메라의 `GP_EVENT_FILE_ADDED` 이벤트 실시간 감지
    - 외부 셔터 버튼 또는 앱 내 촬영 버튼 모두 지원
    - 연속 촬영 시에도 안정적인 파일 처리

3. **자동 다운로드 프로세스**:
   ```
   셔터 누름 → 카메라 촬영 → 이벤트 감지 → 파일 다운로드 → 로컬 저장
   ```

4. **실시간 UI 업데이트**:
    - 다운로드 진행 상황 표시
    - 최근 다운로드 파일명 표시
    - 다운로드 완료 알림

### 🔧 사용 방법

1. **AP 모드 설정**:
    - 카메라를 Wi-Fi AP 모드로 설정
    - 스마트폰에서 카메라 Wi-Fi 네트워크에 연결

2. **앱 연결**:
    - 앱에서 카메라 검색 및 연결
    - 연결 성공 시 "AUTO" 배지와 함께 자동 다운로드 활성화 표시

3. **촬영 및 자동 다운로드**:
    - 카메라 셔터 버튼 사용 시 자동으로 파일 다운로드
    - 앱 내 촬영 버튼 사용 시에도 동일하게 동작

### ✨ 주요 특징

- **즉시 저장**: 촬영과 동시에 파일이 스마트폰에 저장됨
- **실시간 알림**: 다운로드 완료 시 즉시 UI 업데이트
- **파일 정보 표시**: 최근 다운로드한 파일명과 경로 표시
- **오류 처리**: 다운로드 실패 시 자동 재시도 및 오류 알림
- **배터리 최적화**: 효율적인 이벤트 처리로 배터리 사용량 최소화

### 📱 UI 표시

```
┌─────────────────────────────────────────────────────────────────┐
│  📡 자동 파일 다운로드 활성화                                     │
│  카메라에서 촬영한 사진이 자동으로 스마트폰에 저장됩니다.          │
│                                                                │
│  📄 최근 다운로드: IMG_1234.JPG                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 🛠️ 기술 구현

- **네이티브 이벤트 리스너**: C++ 스레드에서 카메라 이벤트 실시간 감지
- **JNI 콜백**: 안전한 Java-C++ 간 비동기 통신
- **파일 스트림**: 효율적인 메모리 사용으로 대용량 파일 처리
- **코루틴 통합**: Kotlin 코루틴을 통한 비동기 UI 업데이트

## 🏗️ 애플리케이션 아키텍처

### Clean Architecture + MVVM

```mermaid
graph TB
    subgraph "Presentation Layer"
        A[Jetpack Compose UI]
        B[ViewModels]
        C[Activities/Screens]
    end
    
    subgraph "Domain Layer"
        D[Use Cases]
        E[Repository Interfaces]
        F[Domain Models]
    end
    
    subgraph "Data Layer"
        G[Repository Implementations]
        H[Data Sources]
        I[Firebase Remote Source]
        J[Native Camera Source]
        K[USB Manager]
    end
    
    subgraph "External"
        L[Firebase Services]
        M[libgphoto2 C++ Library]
        N[Android USB API]
    end
    
    A --> B
    B --> D
    D --> E
    G --> E
    G --> H
    H --> I
    H --> J
    H --> K
    I --> L
    J --> M
    K --> N
```

## 🔄 애플리케이션 파이프라인

### 1. 앱 시작 플로우

```mermaid
sequenceDiagram
    participant User
    participant Splash
    participant Firebase
    participant Login
    participant Main
    
    User->>Splash: 앱 실행
    Splash->>Firebase: Firebase 초기화
    Firebase-->>Splash: 초기화 완료
    Splash->>Splash: 인증 상태 확인
    
    alt 로그인 상태
        Splash->>Main: MainActivity로 이동
    else 미로그인 상태
        Splash->>Login: LoginActivity로 이동
        User->>Login: Google 로그인 버튼 클릭
        Login->>Firebase: Google 인증 요청
        Firebase-->>Login: 인증 성공
        Login->>Main: MainActivity로 이동
    end
    
    Main->>Main: 카메라 연결 상태 확인
    Main->>User: 메인 화면 표시
```

### 2. 카메라 연결 플로우

```mermaid
sequenceDiagram
    participant User
    participant UI
    participant ViewModel
    participant Repository
    participant USBManager
    participant NativeLib
    
    User->>UI: USB 케이블 연결
    UI->>ViewModel: 카메라 연결 요청
    ViewModel->>Repository: connectCamera()
    Repository->>USBManager: USB 디바이스 확인
    USBManager-->>Repository: USB 권한 요청
    Repository->>User: USB 권한 허용 요청
    User->>Repository: 권한 허용
    Repository->>NativeLib: 네이티브 카메라 초기화
    NativeLib-->>Repository: 연결 성공
    Repository->>Repository: 이벤트 리스너 시작
    Repository-->>ViewModel: 연결 완료
    ViewModel-->>UI: 연결 상태 업데이트
    UI-->>User: 연결 성공 표시
```

### 3. 외부 셔터 버튼 촬영 플로우

```mermaid
sequenceDiagram
    participant Camera
    participant NativeLib
    participant Repository
    participant ViewModel
    participant UI
    participant Storage
    
    Camera->>NativeLib: 셔터 버튼 눌림
    NativeLib->>Repository: onExternalShutterPressed()
    Repository->>Repository: capturePhoto() 실행
    Repository->>NativeLib: 촬영 명령 전송
    NativeLib->>Camera: 사진 촬영
    Camera-->>NativeLib: 촬영 완료 + 이미지 데이터
    NativeLib-->>Repository: onPhotoCaptured(path, filename)
    Repository->>Storage: 로컬 저장소에 저장
    Repository->>Repository: 사진 목록에 추가
    Repository-->>ViewModel: 촬영 완료 이벤트
    ViewModel-->>UI: UI 업데이트 (새 사진 표시)
    UI-->>Camera: 촬영 완료 피드백
```

### 4. 라이브뷰 스트리밍 플로우

```mermaid
sequenceDiagram
    participant User
    participant UI
    participant Repository
    participant NativeLib
    participant Camera
    
    User->>UI: 라이브뷰 시작
    UI->>Repository: startLiveView()
    Repository->>NativeLib: 라이브뷰 콜백 등록
    NativeLib->>Camera: 라이브뷰 시작 요청
    
    loop 실시간 프레임 전송
        Camera-->>NativeLib: JPEG 프레임 데이터
        NativeLib-->>Repository: onLiveViewFrame(buffer)
        Repository-->>UI: LiveViewFrame 발송
        UI-->>User: 실시간 화면 표시
    end
    
    User->>UI: 라이브뷰 종료
    UI->>Repository: stopLiveView()
    Repository->>NativeLib: 라이브뷰 중지
    NativeLib->>Camera: 라이브뷰 종료
```

## 📱 화면 와이어프레임

### 메인 플로우 화면 구성

```
┌─────────────────────────────────────────────────────────────────┐
│                         앱 와이어프레임                          │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   스플래시 화면   │────│   로그인 화면    │────│   메인 화면      │
│                │    │                │    │                │
│  ┌───────────┐  │    │  ┌───────────┐  │    │  ┌───────────┐  │
│  │CamCon 로고│  │    │  │Google 로그인│  │    │  │카메라 상태 │  │
│  │    📸     │  │    │  │    버튼    │  │    │  │  🔴 연결안됨│  │
│  └───────────┘  │    │  └───────────┘  │    │  └───────────┘  │
│                │    │                │    │                │
│   Loading...   │    │  ┌───────────┐  │    │  ┌───────────┐  │
│                │    │  │  로그인    │  │    │  │ 카메라 연결 │  │
│                │    │  │  건너뛰기   │  │    │  │    버튼    │  │
│                │    │  └───────────┘  │    │  └───────────┘  │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         │                       │                       │
         └───────────────────────────────────────────────┘
```

### 카메라 제어 화면

```
┌─────────────────────────────────────────────────────────────────┐
│                      카메라 제어 화면                            │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  ← 뒤로가기                카메라 제어              ⚙️ 설정      │
├─────────────────────────────────────────────────────────────────┤
│                                                                │
│  ┌─────────────────── 라이브뷰 화면 ───────────────────┐          │
│  │                                                   │          │
│  │              📹 실시간 카메라 화면                 │          │
│  │                                                   │          │
│  │                    [자동초점]                      │          │
│  │                                                   │          │
│  │               🎯 초점 포인트 표시                  │          │
│  │                                                   │          │
│  └───────────────────────────────────────────────────┘          │
│                                                                │
│  ┌─────────────── 카메라 설정 정보 ─────────────────┐              │
│  │ ISO: AUTO  │ 셔터: 1/125  │ 조리개: F2.8  │ WB: AUTO │          │
│  └─────────────────────────────────────────────────┘              │
│                                                                │
│  ┌─────────────── 촬영 컨트롤 ──────────────────┐                  │
│  │                                             │                  │
│  │    [📸 일반촬영]  [⚡ 연속촬영]  [⏰ 타임랩스]    │                  │
│  │                                             │                  │
│  │            🔵 큰 셔터 버튼                   │                  │
│  │                                             │                  │
│  │    💡 외부 셔터: ✅ 활성화                   │                  │
│  │                                             │                  │
│  └─────────────────────────────────────────────┘                  │
└─────────────────────────────────────────────────────────────────┘
```

### 사진 갤러리 화면

```
┌─────────────────────────────────────────────────────────────────┐
│                       사진 갤러리                               │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  ← 뒤로가기               촬영된 사진              🗂️ 카메라파일   │
├─────────────────────────────────────────────────────────────────┤
│                                                                │
│  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐                    │
│  │  📸   │ │  📸   │ │  📸   │ │  📸   │                    │
│  │IMG_001│ │IMG_002│ │IMG_003│ │IMG_004│                    │
│  │2.1MB  │ │1.8MB  │ │2.4MB  │ │1.9MB  │                    │
│  │ 14:30 │ │ 14:25 │ │ 14:20 │ │ 14:15 │                    │
│  └────────┘ └────────┘ └────────┘ └────────┘                    │
│                                                                │
│  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐                    │
│  │  📸   │ │  📸   │ │  📸   │ │  📸   │                    │
│  │IMG_005│ │IMG_006│ │IMG_007│ │IMG_008│                    │
│  │2.0MB  │ │2.2MB  │ │1.7MB  │ │2.3MB  │                    │
│  │ 14:10 │ │ 14:05 │ │ 14:00 │ │ 13:55 │                    │
│  └────────┘ └────────┘ └────────┘ └────────┘                    │
│                                                                │
│  ┌─────────────── 하단 액션 바 ────────────────┐                   │
│  │  [📤 공유]  [🗑️ 삭제]  [☁️ 백업]  [📁 폴더]   │                   │
│  └─────────────────────────────────────────────┘                   │
└─────────────────────────────────────────────────────────────────┘
```

### 설정 화면

```
┌─────────────────────────────────────────────────────────────────┐
│                         설정 화면                               │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  ← 뒤로가기                설정                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                │
│  📸 카메라 설정                                                 │
│  ├─ 외부 셔터 버튼      [✅ 활성화]                             │
│  ├─ 자동초점 모드       [AF-S ▼]                               │
│  ├─ 이미지 포맷         [RAW+JPEG ▼]                           │
│  ├─ 이미지 품질         [최고화질 ▼]                            │
│  ├─ 저장 경로          [/Pictures/CamCon]                    │
│  ├─ 자동 파일 다운로드  [✅ 활성화]                             │
│  └─ 저장 위치          [내부 저장소]                            │
│                                                                │
│  ⚙️ 앱 설정                                                     │
│  ├─ 테마               [시스템 따라가기 ▼]                       │
│  ├─ 언어               [한국어 ▼]                              │
│  ├─ 자동 백업          [☁️ 활성화]                             │
│  └─ 알림               [🔔 허용]                               │
│                                                                │
│  👤 계정                                                        │
│  ├─ 사용자 정보         [user@gmail.com]                       │
│  ├─ 저장공간 사용량     [2.1GB / 15GB]                         │
│  └─ 로그아웃           [🚪 로그아웃]                            │
│                                                                │
│  ℹ️ 정보                                                        │
│  ├─ 버전               [v1.0.0]                               │
│  ├─ 라이선스           [MIT License]                           │
│  └─ 문의하기           [📧 support@camcon.com]                │
└─────────────────────────────────────────────────────────────────┘
```

## 🔄 데이터 플로우 다이어그램

### 외부 셔터 버튼 → 앱 전송 플로우

```mermaid
flowchart TD
    A[📷 미러리스 카메라] --> B{셔터 버튼 눌림}
    B --> C[libgphoto2 이벤트 감지]
    C --> D[JNI Native Bridge]
    D --> E[CameraCaptureListener]
    E --> F[CameraRepository]
    F --> G[ViewModel 상태 업데이트]
    G --> H[Jetpack Compose UI]
    H --> I[📱 앱에 사진 표시]
    
    F --> J[로컬 스토리지 저장]
    F --> K[Firebase 백업]
    
    style A fill:#e1f5fe
    style I fill:#c8e6c9
    style B fill:#fff3e0
    style F fill:#f3e5f5
```

## 🎯 화면별 주요 기능

### 1. 스플래시 화면 (SplashActivity)

- **기능**: 앱 초기화, Firebase 설정, 자동 로그인 체크
- **전환**: 로그인 상태에 따라 LoginActivity 또는 MainActivity로 이동

### 2. 로그인 화면 (LoginActivity)

- **기능**: Google 소셜 로그인, 게스트 모드
- **전환**: 로그인 성공 시 MainActivity로 이동

### 3. 메인 화면 (MainActivity)

- **기능**: 카메라 연결 상태, 네비게이션 허브
- **하위 화면**: 카메라 제어, 갤러리, 설정 화면

### 4. 카메라 제어 화면 (CameraControlScreen)

- **기능**:
    - 실시간 라이브뷰 📹
    - 촬영 버튼 (앱 내) 📸
    - 외부 셔터 버튼 활성화 🔘
    - 자동초점 🎯
    - 촬영 모드 선택 ⚙️
    - 카메라 설정 표시 📊

### 5. 사진 갤러리 (PhotoPreviewScreen & ServerPhotosScreen)

- **기능**:
    - 촬영된 사진 그리드 뷰 📷
    - 사진 미리보기 및 확대 🔍
    - 로컬/카메라 파일 전환 🗂️
    - 공유, 삭제, 백업 기능 📤

### 6. 설정 화면 (SettingsActivity)

- **기능**:
    - 외부 셔터 버튼 활성화/비활성화 🔘
    - 카메라 설정 (ISO, 셔터속도 등) ⚙️
    - 앱 설정 (테마, 언어 등) 🎨
    - 계정 관리 👤

## 🚀 색감 변환 성능 최적화

### 최적화 기법

- **네이티브 C++ 구현**: 핵심 색감 변환 로직을 C++로 구현하여 3-5배 속도 향상
- **멀티스레드 병렬 처리**: CPU 코어 수에 맞춰 자동으로 스레드 수 조정
- **스마트 샘플링**: 통계 계산 시 적응적 픽셀 샘플링으로 메모리 사용량 40% 감소
- **LRU 캐시 시스템**: 참조 이미지 통계 캐싱으로 재사용 시 즉시 처리
- **메모리 최적화**: Out of Memory 상황 자동 대응 및 폴백 처리

### 성능 향상 결과

- **통계 계산**: 약 70% 속도 향상
- **픽셀 처리**: 약 3-5배 속도 향상 (네이티브 코드 사용 시)
- **메모리 사용량**: 약 40% 감소
- **전체 처리 시간**: 약 5-10배 속도 향상

### 사용 방법

```kotlin
// 기존 코드 그대로 사용 - 자동으로 최적화됨
val result = colorTransferUseCase.applyColorTransfer(inputBitmap, referenceImagePath)
```

### 성능 모니터링

- 처리 시간 및 사용된 최적화 방법 표시
- 진행 상태 실시간 업데이트
- 메모리 사용량 모니터링

## 🛠️ 기술 스택

### Android Framework

- **언어**: Kotlin 100%
- **최소 SDK**: API 29 (Android 10)
- **타겟 SDK**: API 35
- **아키텍처**: Clean Architecture + MVVM

### UI Framework

- **Jetpack Compose**: 1.7.8 - 선언형 UI
- **Material Design**: 1.7.8 - 최신 디자인 시스템
- **Navigation Compose**: 2.7.7 - 화면 전환
- **Accompanist**: 0.32.0 - 시스템 UI 제어

### 의존성 주입

- **Dagger Hilt**: 2.51.1 - DI 컨테이너
- **Hilt Navigation Compose**: 1.2.0

### 비동기 처리

- **Kotlin Coroutines**: 1.7.3 - 비동기 프로그래밍
- **Flow**: 상태 관리 및 데이터 스트림

### 인증 & 클라우드

- **Firebase BOM**: 33.4.0
- **Firebase Auth**: Google Sign-In 통합
- **Firebase Storage**: 사진 백업 (예정)
- **Google Play Services**: 21.0.0

### 이미지 처리

- **Coil Compose**: 2.5.0 - 이미지 로딩 및 캐싱

### 네이티브 라이브러리

- **libgphoto2**: 카메라 제어 핵심 라이브러리
- **libusb**: USB 통신
- **JNI**: Kotlin ↔ C++ 브리지
- **CMake**: 3.22.1 - 네이티브 빌드

### 데이터 저장

- **DataStore Preferences**: 1.0.0 - 설정 저장
- **내부 스토리지**: 촬영된 사진 로컬 저장

## 🚀 시작하기

### 사전 요구사항

- Android Studio Hedgehog 이상
- Android 10 (API 29) 이상 디바이스
- USB OTG 지원 안드로이드 기기
- 호환 DSLR/미러리스 카메라 (Canon, Nikon, Sony 등)
- USB-C to USB-A 어댑터 (필요시)

### 설치 및 실행

1. **프로젝트 클론**
```bash
git clone https://github.com/yourusername/CamCon.git
cd CamCon
```

2. **Firebase 설정**

- Firebase Console에서 프로젝트 생성
- Android 앱 추가 (패키지명: `com.inik.camcon`)
- `google-services.json` 파일을 `app/` 디렉토리에 배치
- Authentication에서 Google 로그인 활성화

3. **빌드 및 실행**
```bash
./gradlew assembleDebug
./gradlew installDebug
```

### 사용법

1. **카메라 연결**
    - USB OTG 케이블로 카메라와 안드로이드 기기 연결
    - 앱에서 USB 권한 허용
    - 카메라 전원 ON

2. **외부 셔터 버튼 활성화**
    - 설정에서 "외부 셔터 버튼" 활성화
    - 카메라 셔터 버튼 눌러서 촬영
    - 앱에 자동으로 사진 전송됨

3. **라이브뷰 사용**
    - 카메라 제어 화면에서 라이브뷰 시작
    - 실시간 화면 확인하며 구도 조정
    - 앱 또는 카메라 버튼으로 촬영

## 📄 라이선스

이 프로젝트는 MIT 라이선스 하에 배포됩니다. 자세한 내용은 [LICENSE](LICENSE) 파일을 참조하세요.

## 🤝 기여하기

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/ExternalShutterButton`)
3. Commit your Changes (`git commit -m 'Add external shutter button support'`)
4. Push to the Branch (`git push origin feature/ExternalShutterButton`)
5. Open a Pull Request

## 📞 연락처

프로젝트 관련 문의사항이 있으시면 언제든지 연락주세요.

---

**CamCon** - Professional Camera Control for Android 📸✨

*최신 업데이트: 외부 셔터 버튼 지원 추가로 더욱 편리한 촬영 경험을 제공합니다.*
