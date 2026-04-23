# CamCon 데이터 레이어 - 빠른 참조 가이드

## 📂 파일 구조

```
app/src/main/java/com/inik/camcon/
│
├── 🎯 data/ (데이터 레이어)
│   ├── repository/
│   │   ├── CameraRepositoryImpl.kt (★ 메인 Repository - 1041줄)
│   │   ├── AuthRepositoryImpl.kt
│   │   ├── SubscriptionRepositoryImpl.kt
│   │   ├── AppUpdateRepositoryImpl.kt
│   │   └── managers/
│   │       ├── CameraConnectionManager.kt (카메라 연결 상태)
│   │       ├── CameraEventManager.kt (외부 셔터 이벤트)
│   │       ├── PhotoDownloadManager.kt (사진 다운로드 처리)
│   │       └── CameraConnectionGlobalManagerImpl.kt
│   │
│   ├── datasource/
│   │   ├── local/
│   │   │   ├── AppPreferencesDataSource.kt (★ 앱 설정 - DataStore)
│   │   │   ├── PtpipPreferencesDataSource.kt
│   │   │   └── LocalCameraDataSource.kt
│   │   │
│   │   ├── remote/
│   │   │   ├── AuthRemoteDataSource.kt (Firebase Auth)
│   │   │   ├── AuthRemoteDataSourceImpl.kt
│   │   │   ├── RemoteCameraDataSource.kt (미구현)
│   │   │   ├── PlayStoreVersionDataSource.kt
│   │   │   └── BillingDataSource.kt
│   │   │
│   │   ├── nativesource/
│   │   │   ├── NativeCameraDataSource.kt (★ libgphoto2 JNI 호출)
│   │   │   ├── CameraCaptureListener.kt (콜백)
│   │   │   └── LiveViewCallback.kt (콜백)
│   │   │
│   │   ├── usb/
│   │   │   ├── UsbCameraManager.kt (USB 카메라 Facade)
│   │   │   ├── UsbDeviceDetector.kt
│   │   │   ├── UsbConnectionManager.kt
│   │   │   ├── CameraCapabilitiesManager.kt
│   │   │   └── UsbConnectionRecovery.kt
│   │   │
│   │   ├── ptpip/
│   │   │   └── PtpipDataSource.kt (★ Wi-Fi PTPIP 통합)
│   │   │
│   │   └── billing/
│   │       ├── BillingDataSource.kt
│   │       └── BillingDataSourceImpl.kt
│   │
│   ├── network/
│   │   └── ptpip/
│   │       ├── discovery/
│   │       │   └── PtpipDiscoveryService.kt (mDNS 검색)
│   │       ├── connection/
│   │       │   └── PtpipConnectionManager.kt (TCP 연결)
│   │       ├── authentication/
│   │       │   └── NikonAuthenticationService.kt (STA/AP 인증)
│   │       └── wifi/
│   │           └── WifiNetworkHelper.kt (Wi-Fi 관리)
│   │
│   ├── service/
│   │   ├── BackgroundSyncService.kt
│   │   ├── WifiMonitoringService.kt
│   │   ├── AutoConnectManager.kt
│   │   ├── AutoConnectForegroundService.kt
│   │   └── AutoConnectTaskRunner.kt
│   │
│   ├── receiver/
│   │   ├── WifiSuggestionBroadcastReceiver.kt
│   │   └── BootCompletedReceiver.kt
│   │
│   ├── processor/
│   │   └── ColorTransferProcessor.kt
│   │
│   └── constants/
│       └── PtpipConstants.kt
│
├── 🎭 domain/ (도메인 계층)
│   ├── repository/
│   │   ├── CameraRepository.kt (인터페이스)
│   │   ├── AuthRepository.kt
│   │   ├── SubscriptionRepository.kt
│   │   └── AppUpdateRepository.kt
│   │
│   ├── model/
│   │   ├── Camera.kt
│   │   ├── CameraPhoto.kt (카메라 메모리의 사진)
│   │   ├── CameraConnectionModels.kt
│   │   ├── CameraAbilities.kt
│   │   ├── CameraFeature.kt
│   │   ├── CameraError.kt
│   │   ├── PtpipModels.kt
│   │   ├── User.kt
│   │   ├── Subscription.kt
│   │   ├── SubscriptionTier.kt
│   │   ├── SubscriptionProduct.kt
│   │   ├── ImageFormat.kt
│   │   ├── AppVersionInfo.kt
│   │   ├── ReferralCode.kt
│   │   └── AutoConnectNetworkConfig.kt
│   │
│   ├── manager/
│   │   ├── CameraSettingsManager.kt
│   │   ├── CameraConnectionGlobalManager.kt (인터페이스)
│   │   ├── ErrorHandlingManager.kt
│   │   └── NativeLogManager.kt
│   │
│   └── usecase/
│       ├── camera/
│       │   ├── CapturePhotoUseCase.kt
│       │   ├── GetCameraPhotosUseCase.kt
│       │   ├── GetCameraPhotosPagedUseCase.kt
│       │   ├── ConnectCameraUseCase.kt
│       │   ├── StartLiveViewUseCase.kt
│       │   ├── PerformAutoFocusUseCase.kt
│       │   └── ...
│       └── auth/
│           ├── SignInWithGoogleUseCase.kt
│           └── ...
│
├── 💉 di/ (의존성 주입)
│   ├── AppModule.kt
│   ├── RepositoryModule.kt (★ Repository 바인딩)
│   └── ...
│
└── 🎨 presentation/ (프레젠테이션 계층)
    ├── viewmodel/
    ├── ui/
    └── theme/
```

---

## 🏗️ 아키텍처 다이어그램

### 전체 데이터 흐름

```
┌─────────────────────────────────────────────────────────────────┐
│                      🎨 Presentation Layer                      │
│                    (Activities, Composables)                    │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│                    🧠 ViewModel Layer                            │
│           (CameraViewModel, AppSettingsViewModel)               │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│                   🎯 Domain Layer                                │
│              (Repository Interfaces, UseCases)                  │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│              🔨 Repository Implementation Layer                  │
│    ┌─────────────────────────────────────────────────────────┐  │
│    │ CameraRepositoryImpl (1041줄 - Main Facade)             │  │
│    │ • 사진 촬영 (USB/PTPIP)                                 │  │
│    │ • 라이브뷰 스트리밍                                     │  │
│    │ • 카메라 설정 제어                                      │  │
│    │ • 사진 목록 조회 (페이징)                               │  │
│    └─────────────────────────────────────────────────────────┘  │
│    ┌─────────────────────────────────────────────────────────┐  │
│    │ AuthRepositoryImpl | SubscriptionRepositoryImpl           │  │
│    └─────────────────────────────────────────────────────────┘  │
└────────────────────────┬────────────────────────────────────────┘
                         │
        ┌────────────────┼────────────────┐
        │                │                │
        ▼                ▼                ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ Manager      │  │ Service      │  │ DataSource   │
│ Layer        │  │ Layer        │  │ Layer        │
├──────────────┤  ├──────────────┤  ├──────────────┤
│ • Camera     │  │ • PTPIP      │  │ • Native     │
│   Connection │  │   Connection │  │   (libgphoto2)
│   Manager    │  │   Manager    │  │             │
│ • Photo      │  │ • PTPIP      │  │ • USB        │
│   Download   │  │   Discovery  │  │   Devices    │
│   Manager    │  │   Service    │  │             │
│ • Camera     │  │ • Nikon      │  │ • Local      │
│   Event      │  │   Auth       │  │   Storage    │
│   Manager    │  │   Service    │  │             │
│ • Camera     │  │ • WiFi       │  │ • Remote     │
│   Capabilities   │   Network    │  │   (Firebase) │
│   Manager    │  │   Helper     │  │             │
│              │  │ • Background │  │ • Billing    │
│              │  │   Sync       │  │             │
└──────────────┘  └──────────────┘  └──────────────┘
       │                │                   │
       └────────────────┼───────────────────┘
                        │
                        ▼
        ┌───────────────────────────────┐
        │  📡 Physical Connections      │
        ├───────────────────────────────┤
        │ • USB (PTP Protocol)          │
        │ • Wi-Fi (PTPIP Protocol)      │
        │ • Bluetooth (향후)            │
        │ • Network (Firebase/API)      │
        │ • Local Storage (Room/SP)     │
        └───────────────────────────────┘
```

---

## 🔄 주요 데이터 흐름

### 1️⃣ 사진 촬영 흐름 (USB)

```
UI (CameraControlScreen)
  │
  ├─ capturePhoto() 클릭
  │
  ▼
ViewModel.capturePhoto()
  │
  ▼
CameraRepository.capturePhoto(ShootingMode.SINGLE)
  │
  ├─ 1. 카메라 연결 상태 검증
  │     └─ ConnectionManager.isConnected.value = true?
  │
  ├─ 2. NativeCameraDataSource.capturePhotoAsync(listener, saveDir)
  │     └─ JNI → libgphoto2 API 호출
  │
  ├─ 3. CameraCaptureListener 콜백 수신
  │     │
  │     ├─ onPhotoCaptured(fullPath, fileName)
  │     │   └─ 사진 캡처 완료 신호
  │     │
  │     └─ onPhotoDownloaded(fullPath, fileName, imageData)
  │         │
  │         ├─ 파일 포맷 검증 (jpg, raw, tiff)
  │         │
  │         ├─ PhotoDownloadManager.handlePhotoDownload()
  │         │   ├─ 이미지 디코딩
  │         │   ├─ FREE 티어 리사이징 (2000px)
  │         │   ├─ 색감 전송 처리 (optional)
  │         │   ├─ MediaStore 등록
  │         │   └─ Room DB 저장
  │         │
  │         └─ _capturedPhotos StateFlow 업데이트
  │
  ▼
suspendCancellableCoroutine 반환 (Result<CapturedPhoto>)
  │
  ▼
UI 업데이트 (최신 사진 표시)
```

### 2️⃣ 카메라 사진 조회 흐름

```
UI (ServerPhotosScreen)
  │
  ├─ 페이지 로드
  │
  ▼
ViewModel.getCameraPhotos(page = 0, pageSize = 20)
  │
  ▼
CameraRepository.getCameraPhotosPaged()
  │
  ├─ PhotoDownloadManager.getCameraPhotosPaged()
  │   │
  │   ├─ 1. 카메라 연결 확인
  │   │     └─ if (!isOnline) return cachedPhotos
  │   │
  │   ├─ 2. NativeDataSource.getCameraPhotosPaged(page, pageSize)
  │   │     └─ libgphoto2: 카메라 메모리 스캔 (느림 ⚠️)
  │   │
  │   ├─ 3. 사진 메타데이터 추출
  │   │     ├─ 파일명, 크기, 해상도, 촬영 시간
  │   │     └─ 썸네일 경로
  │   │
  │   └─ 4. Room DB에 저장 (동기화)
  │
  ├─ 이벤트 리스너 상태 확인
  │   └─ if (!eventManager.isRunning()) restart()
  │
  ▼
Result<PaginatedCameraPhotos>
  │
  ├─ currentPage: 0
  ├─ pageSize: 20
  ├─ totalItems: 150
  ├─ totalPages: 8
  └─ photos: List<CameraPhoto>
      └─ [
           {path: "/store/DCIM/105KAY_1/KY6_0035.JPG", ...},
           ...
         ]
  │
  ▼
UI 업데이트 (사진 그리드 표시)
```

### 3️⃣ PTPIP 연결 흐름

```
UI (PtpipConnectionScreen)
  │
  ├─ "Wi-Fi 카메라 검색" 클릭
  │
  ▼
PtpipViewModel.discoverCameras()
  │
  ▼
PtpipDataSource.discoverCameras()
  │
  ├─ 1. PtpipDiscoveryService.discoverCameras()
  │     │
  │     ├─ Jmdns.list("_ptp._tcp.local.")
  │     │   └─ Canon, Nikon Legacy
  │     │
  │     └─ Jmdns.list("_ptpip._tcp.local.")
  │         └─ Nikon Z, Canon R 등
  │
  ├─ 2. 결과: List<PtpipCamera>
  │     └─ [
  │          {ipAddress: "192.168.1.100", port: 15740, name: "Nikon D850"},
  │          ...
  │        ]
  │
  ├─ _discoveredCameras StateFlow 업데이트
  │
  ▼
UI: 카메라 목록 표시
  │
  ├─ 카메라 선택
  │
  ▼
PtpipDataSource.connectToCamera(camera)
  │
  ├─ 1. WifiNetworkHelper.detectCameraMode()
  │     └─ AP 모드? STA 모드?
  │
  ├─ 2. NikonAuthenticationService.authenticate()
  │     ├─ AP 모드: 직접 TCP 연결 + 기본 인증
  │     └─ STA 모드: Nikon 고유 인증 프로토콜
  │
  ├─ 3. PtpipConnectionManager.establishConnection()
  │     └─ TCP 소켓 생성 (IP:15740)
  │
  ├─ 4. PTPIP 핸드셰이크
  │     └─ GetDeviceInfo, GetStorageInfo, ...
  │
  └─ _connectionState = PtpipConnectionState.CONNECTED
     │
     ▼
   UI: "카메라에 연결되었습니다" 메시지
```

### 4️⃣ 라이브뷰 스트리밍 흐름

```
UI (CameraControlScreen)
  │
  ├─ "라이브뷰 시작" 버튼 클릭
  │
  ▼
ViewModel.startLiveView()
  │
  ▼
CameraRepository.startLiveView(): Flow<LiveViewFrame>
  │
  ├─ callbackFlow {
  │   │
  │   ├─ 1. 연결 상태 확인 (USB OR PTPIP)
  │   │
  │   ├─ 2. NativeDataSource.startLiveView(callback)
  │   │     │
  │   │     └─ object : LiveViewCallback {
  │   │         override fun onLiveViewFrame(ByteBuffer) {
  │   │           val frame = ByteArray(byteBuffer.remaining())
  │   │           byteBuffer.get(frame)
  │   │           trySend(LiveViewFrame(data = frame, ...))
  │   │         }
  │   │       }
  │   │
  │   └─ awaitClose {
  │       stopLiveView()
  │     }
  │ }
  │
  ▼
UI: flow.collect { frame ->
      val bitmap = JPEGDecoder.decode(frame.data)
      updatePreviewImage(bitmap)  // ← 30fps 라이브 피드
    }
```

---

## 📊 데이터 소스별 특성

| 데이터 소스 | 프로토콜 | 속도 | 신뢰성 | 기능 범위 |
|-----------|--------|------|-------|---------|
| **USB Native** | PTP | 🟢 빠름 | 🟢 높음 | 🟢 완전 |
| **PTPIP (Wi-Fi)** | PTPIP | 🟡 중간 | 🟡 중간 | 🟡 제한적 |
| **Local Storage** | File I/O | 🟢 빠름 | 🟢 높음 | 🟡 캐시만 |
| **Firebase** | HTTP/REST | 🟡 중간 | 🟡 네트워크 | 🟢 인증/구독 |
| **Google Play** | HTTP/REST | 🟡 중간 | 🟡 네트워크 | 🟡 구독만 |

---

## 🔑 핵심 클래스

### CameraRepositoryImpl (1041줄)

**책임**: USB, PTPIP, Local 데이터 소스를 통합하는 Facade

```kotlin
@Singleton
class CameraRepositoryImpl @Inject constructor(
    // 데이터 소스 (11개 의존성)
    private val nativeDataSource: NativeCameraDataSource,      // USB
    private val ptpipDataSource: PtpipDataSource,              // Wi-Fi
    private val usbCameraManager: UsbCameraManager,            // USB 관리
    private val photoCaptureEventManager: PhotoCaptureEventManager,
    private val appPreferencesDataSource: AppPreferencesDataSource, // Local
    private val colorTransferUseCase: ColorTransferUseCase,
    private val connectionManager: CameraConnectionManager,    // 연결 상태
    private val eventManager: CameraEventManager,              // 이벤트
    private val downloadManager: PhotoDownloadManager,         // 사진 다운로드
    private val uiStateManager: CameraUiStateManager,
    private val getSubscriptionUseCase: GetSubscriptionUseCase,
    private val errorHandlingManager: ErrorHandlingManager
) : CameraRepository {
    // StateFlow 기반 반응형 상태 관리
    private val _capturedPhotos = MutableStateFlow<List<CapturedPhoto>>(emptyList())
    private val _cameraSettings = MutableStateFlow<CameraSettings?>(null)
    
    // 중복 처리 방지
    private val processedFiles = ConcurrentHashMap.newKeySet<String>()
    
    // 별도 코루틴 스코프 (생명주기 분리)
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
```

### PhotoDownloadManager

**책임**: 사진 다운로드, 리사이징, 색감 전송, MediaStore 등록

```kotlin
@Singleton
class PhotoDownloadManager @Inject constructor(
    @ApplicationContext context: Context,
    private val nativeDataSource: NativeCameraDataSource,
    private val appPreferencesDataSource: AppPreferencesDataSource,
    private val colorTransferUseCase: ColorTransferUseCase,
    private val photoCaptureEventManager: PhotoCaptureEventManager,
    private val getSubscriptionUseCase: GetSubscriptionUseCase
) {
    // 주요 메서드
    suspend fun getCameraPhotos(): Result<List<CameraPhoto>>
    suspend fun getCameraPhotosPaged(page: Int, pageSize: Int, ...): Result<PaginatedCameraPhotos>
    suspend fun handlePhotoDownload(...): CapturedPhoto?
    suspend fun handleNativePhotoDownload(...): CapturedPhoto?
    suspend fun getCameraThumbnail(photoPath: String): Result<ByteArray>
}
```

### CameraConnectionManager

**책임**: 카메라 연결 상태 추적, 초기화 상태 관리

```kotlin
@Singleton
class CameraConnectionManager @Inject constructor(
    @ApplicationContext context: Context,
    private val nativeDataSource: NativeCameraDataSource,
    private val usbCameraManager: UsbCameraManager,
    private val uiStateManager: CameraUiStateManager
) {
    // StateFlow
    val isConnected: StateFlow<Boolean>     // USB 연결 여부
    val isInitializing: StateFlow<Boolean>  // 초기화 중 여부 (UI 블록)
    val isPtpipConnected: StateFlow<Boolean> // PTPIP 연결 여부
    val cameraCapabilities: StateFlow<CameraCapabilities?>
    
    // 주요 메서드
    suspend fun connectCamera(cameraId: String): Result<Boolean>
    suspend fun disconnectCamera(): Result<Boolean>
}
```

### PtpipDataSource

**책임**: Wi-Fi PTPIP 연결, 발견, 사진 전송 통합

```kotlin
@Singleton
class PtpipDataSource @Inject constructor(
    private val discoveryService: PtpipDiscoveryService,
    private val connectionManager: PtpipConnectionManager,
    private val nikonAuthService: NikonAuthenticationService,
    private val wifiHelper: WifiNetworkHelper,
    ...
) {
    // StateFlow
    val connectionState: StateFlow<PtpipConnectionState>
    val discoveredCameras: StateFlow<List<PtpipCamera>>
    val connectionProgressMessage: StateFlow<String>
    
    // 콜백 등록
    fun setPhotoDownloadedCallback(callback: (String, String, ByteArray) -> Unit)
    fun setConnectionLostCallback(callback: () -> Unit)
    
    // 주요 메서드
    suspend fun discoverCameras(): Result<List<PtpipCamera>>
    suspend fun connectToCamera(camera: PtpipCamera): Result<Boolean>
    suspend fun capturePhoto(saveDir: String): Result<CapturedPhoto>
}
```

---

## 🚀 사용 예시

### 사진 촬영

```kotlin
// ViewModel에서
viewModelScope.launch {
    val result = cameraRepository.capturePhoto(ShootingMode.SINGLE)
    result.onSuccess { photo ->
        Log.d("Photo", "촬영 완료: ${photo.filePath}")
        _photoState.value = PhotoState.Success(photo)
    }.onFailure { error ->
        _photoState.value = PhotoState.Error(error.message)
    }
}
```

### 라이브뷰 구독

```kotlin
// ViewModel에서
private val _liveViewFrames = cameraRepository.startLiveView()
    .onEach { frame ->
        val bitmap = decodeJpeg(frame.data)
        _previewImage.value = bitmap
    }
    .catch { error ->
        Log.e("LiveView", "Error: ${error.message}")
    }
    .launchIn(viewModelScope)  // 자동 취소

// 중지
cameraRepository.stopLiveView()
```

### 카메라 사진 조회

```kotlin
// ViewModel에서
viewModelScope.launch {
    val result = cameraRepository.getCameraPhotosPaged(
        page = currentPage,
        pageSize = 20
    )
    result.onSuccess { paginated ->
        _photos.value = paginated.photos
        _hasNextPage.value = paginated.hasNext
    }
}
```

---

## ⚠️ 주의사항

### 1. 메모리 관리
- `repositoryScope`는 자동으로 취소되지 않음 (Singleton)
- 콜백 기반 설계로 인한 메모리 누수 위험
- 이벤트 리스너 정지 필수

### 2. 동시성
- `Mutex`로 중복 연결 방지
- `ConcurrentHashMap`으로 파일 중복 처리 방지

### 3. 네트워크 조건
- PTPIP는 Wi-Fi 네트워크에 크게 의존
- USB보다 느리고 덜 안정적
- 연결 끊김 시 자동 재연결 필요

### 4. 구독 기반 제한
- FREE 티어: 사진 2000px 리사이징
- ADMIN 티어: 라이브뷰 활성화

---

## 📚 참고 자료

- libgphoto2: `build_libgphoto2.sh`로 CMake 빌드
- PTPIP: Nikon/Canon 고유 프로토콜
- DataStore: SharedPreferences 대체 기술
- Firebase: 인증, 구독 관리, 분석

