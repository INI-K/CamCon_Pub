# CamCon 프로젝트 데이터 레이어 상세 분석

## 📊 개요

CamCon은 캐논, 니콘 등 DSLR/미러리스 카메라를 원격으로 제어하는 안드로이드 애플리케이션입니다. 데이터 레이어는 **USB, Wi-Fi(PTPIP), Native(libgphoto2)** 세 가지 데이터 소스를 통합 관리합니다.

---

## 1️⃣ 데이터 소스 아키텍처

### 1.1 데이터 소스 구조도

```
┌─────────────────────────────────────────────────────────────────┐
│                    Repository 계층                              │
│               CameraRepositoryImpl (Facade)                      │
│                    SubscriptionRepositoryImpl                    │
│                    AuthRepositoryImpl                            │
│                    AppUpdateRepositoryImpl                       │
└──────────────────────┬──────────────────────────────────────────┘
                       │
        ┌──────────────┼──────────────┬──────────────────┐
        │              │              │                  │
        ▼              ▼              ▼                  ▼
    ┌─────────┐  ┌─────────┐  ┌──────────┐  ┌──────────────┐
    │  Local  │  │ Remote  │  │  Native  │  │    Network   │
    │ Sources │  │ Sources │  │ Sources  │  │   Components │
    └─────────┘  └─────────┘  └──────────┘  └──────────────┘
        │              │              │              │
        │              │              │              │
        ▼              ▼              ▼              ▼
    ┌───────────────────────────────────────────────────────┐
    │         Manager & Service Layer                       │
    ├───────────────────────────────────────────────────────┤
    │ • CameraConnectionManager                             │
    │ • PhotoDownloadManager                                │
    │ • CameraEventManager                                  │
    │ • PtpipConnectionManager                              │
    │ • NikonAuthenticationService                          │
    │ • WifiNetworkHelper                                   │
    └───────────────────────────────────────────────────────┘
```

### 1.2 각 데이터 소스별 상세 분석

#### A. Local Data Sources (SharedPreferences/DataStore)

**파일 위치**: `app/src/main/java/com/inik/camcon/data/datasource/local/`

| 데이터 소스 | 용도 | 저장소 | 주요 데이터 |
|-----------|-----|-------|---------|
| **AppPreferencesDataSource** | 앱 설정 | DataStore | 테마 모드, 카메라 제어 활성화, 색감 전송 설정, 구독 티어 |
| **PtpipPreferencesDataSource** | PTPIP 연결 설정 | SharedPreferences | 최후 연결 카메라 정보, 자동 연결 설정 |
| **LocalCameraDataSource** | 카메라 로컬 정보 | Room (예상) | 촬영된 사진 메타데이터 |

**구현 예시 - AppPreferencesDataSource**:
```kotlin
@Singleton
class AppPreferencesDataSource @Inject constructor(context: Context) {
    // DataStore를 사용한 타입 안전 저장소
    private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(
        name = "app_settings"
    )
    
    // Flow 기반 반응형 데이터 제공
    val isCameraControlsEnabled: Flow<Boolean>
    val isLiveViewEnabled: Flow<Boolean>
    val themeMode: Flow<ThemeMode>  // FOLLOW_SYSTEM, LIGHT, DARK
    val subscriptionTier: Flow<String?>
    val colorTransferIntensity: Flow<Float>
    
    // 비동기 쓰기 작업
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setSubscriptionTier(tier: String?)
}
```

---

#### B. Remote Data Sources (API/Firebase)

**파일 위치**: `app/src/main/java/com/inik/camcon/data/datasource/remote/`

| 데이터 소스 | 용도 | API/서비스 | 주요 메서드 |
|-----------|-----|----------|-----------|
| **AuthRemoteDataSource** | Google 인증 | Firebase Auth | signInWithGoogle(), signOut() |
| **RemoteCameraDataSource** | 클라우드 카메라 데이터 | (구현 예정) | - |
| **PlayStoreVersionDataSource** | 앱 버전 확인 | Play Store API | checkLatestVersion() |
| **BillingDataSource** | 구독 관리 | Google Play Billing | querySkuDetails(), purchaseProduct() |

**구현 예시 - AuthRemoteDataSource**:
```kotlin
interface AuthRemoteDataSource {
    suspend fun signInWithGoogle(idToken: String): Result<User>
    suspend fun signOut(): Result<Boolean>
    suspend fun getUserProfile(): Result<User>
    suspend fun deleteAccount(): Result<Boolean>
}

class AuthRemoteDataSourceImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : AuthRemoteDataSource {
    override suspend fun signInWithGoogle(idToken: String): Result<User> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = firebaseAuth.signInWithCredential(credential).await()
            // Firebase -> Domain Model 매핑
            Result.success(result.user?.toUser())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

---

#### C. Native Data Sources (libgphoto2/USB)

**파일 위치**: `app/src/main/java/com/inik/camcon/data/datasource/nativesource/`

**libgphoto2**: C/C++ 라이브러리로 USB/PTP 카메라 제어 (CMake로 빌드)

| 데이터 소스 | 용도 | 연결 타입 | 주요 기능 |
|-----------|-----|---------|---------|
| **NativeCameraDataSource** | 카메라 제어 | USB/AP(강제) | 사진 촬영, 라이브뷰, 설정 제어 |
| **CameraCaptureListener** | 촬영 콜백 | USB | onPhotoCaptured, onPhotoDownloaded |
| **LiveViewCallback** | 라이브뷰 스트림 | USB | onLiveViewFrame(ByteBuffer) |

**구현 예시 - NativeCameraDataSource**:
```kotlin
@Singleton
class NativeCameraDataSource @Inject constructor(
    @ApplicationContext context: Context,
    private val uiStateManager: CameraUiStateManager
) {
    // JNI 호출을 통한 네이티브 기능
    fun listenCameraEvents(callback: CameraCaptureListener) {
        CameraNative.listenCameraEvents(callback)  // JNI 호출
    }
    
    suspend fun capturePhotoAsync(
        listener: CameraCaptureListener,
        saveDir: String
    )
    
    fun startLiveView(callback: LiveViewCallback)
    
    fun autoFocus(): Boolean
    
    suspend fun getCameraPhotos(): List<NativePhoto>
    
    suspend fun getCameraSummary(): CameraSummary
    
    suspend fun getCameraCapabilities(): CameraCapabilities?
}
```

---

#### D. Network Data Sources (PTPIP/Wi-Fi)

**파일 위치**: `app/src/main/java/com/inik/camcon/data/datasource/ptpip/`

**PTPIP**: Picture Transfer Protocol over IP (Wi-Fi를 통한 카메라 제어)

| 서비스 | 용도 | 주요 기능 |
|------|-----|---------|
| **PtpipDataSource** | PTPIP 통합 관리 | 연결, 발견, 인증, 사진 전송 |
| **PtpipDiscoveryService** | mDNS 카메라 검색 | `_ptp._tcp` / `_ptpip._tcp` |
| **PtpipConnectionManager** | 연결 상태 관리 | 연결, 재연결, 분리 |
| **NikonAuthenticationService** | STA/AP 모드 인증 | Nikon 고유 인증 프로토콜 |
| **WifiNetworkHelper** | Wi-Fi 네트워크 관리 | AP 검색, STA 연결, SSID 확인 |

**구현 예시 - PtpipDataSource**:
```kotlin
@Singleton
class PtpipDataSource @Inject constructor(
    private val discoveryService: PtpipDiscoveryService,
    private val connectionManager: PtpipConnectionManager,
    private val nikonAuthService: NikonAuthenticationService,
    private val wifiHelper: WifiNetworkHelper
) {
    // 상태 Flow
    val connectionState: StateFlow<PtpipConnectionState>
    val discoveredCameras: StateFlow<List<PtpipCamera>>
    val connectionProgressMessage: StateFlow<String>
    
    // 콜백 등록
    fun setPhotoDownloadedCallback(
        callback: (filePath: String, fileName: String, imageData: ByteArray) -> Unit
    )
    
    fun setConnectionLostCallback(callback: () -> Unit)
    
    // 주요 메서드
    suspend fun discoverCameras(): Result<List<PtpipCamera>>
    suspend fun connectToCamera(camera: PtpipCamera): Result<Boolean>
    suspend fun disconnectCamera(): Result<Boolean>
    suspend fun capturePhoto(saveDir: String): Result<CapturedPhoto>
}
```

---

#### E. USB 데이터 소스

**파일 위치**: `app/src/main/java/com/inik/camcon/data/datasource/usb/`

| 컴포넌트 | 용도 | 책임 |
|---------|-----|-----|
| **UsbCameraManager** | USB 카메라 Facade | 각 구성요소 조합 |
| **UsbDeviceDetector** | USB 디바이스 감지 | 디바이스 스캔, 권한 관리 |
| **UsbConnectionManager** | USB 연결 관리 | 연결, 분리, 상태 추적 |
| **CameraCapabilitiesManager** | 카메라 기능 캐시 | 기능 정보 저장 |
| **UsbConnectionRecovery** | 연결 복구 | 재연결 로직 |

**구현 구조**:
```kotlin
@Singleton
class UsbCameraManager @Inject constructor(
    private val deviceDetector: UsbDeviceDetector,
    private val connectionManager: UsbConnectionManager,
    private val capabilitiesManager: CameraCapabilitiesManager
) {
    // StateFlow 위임
    val connectedDevices: StateFlow<List<UsbDevice>>
    val hasUsbPermission: StateFlow<Boolean>
    val cameraCapabilities: StateFlow<CameraCapabilities?>
    val isNativeCameraConnected: StateFlow<Boolean>
    
    suspend fun requestPermission(device: UsbDevice)
    suspend fun connectToCamera(device: UsbDevice): Result<Boolean>
    suspend fun disconnect(): Result<Boolean>
    fun buildWidgetJsonFromMaster(): String  // 설정 캐시
}
```

---

### 1.3 데이터 소스 선택 전략

```kotlin
// CameraRepositoryImpl의 데이터 소스 선택 로직
private suspend fun getWidgetJsonFromSource(): String {
    return when {
        // 우선순위 1: USB 카메라가 연결되어 있으면 USB 사용
        usbCameraManager.isNativeCameraConnected.value -> {
            usbCameraManager.buildWidgetJsonFromMaster()
        }
        // 우선순위 2: 마스터 데이터(캐시)가 있으면 사용
        usbCameraManager.buildWidgetJsonFromMaster().isNotEmpty() -> {
            usbCameraManager.buildWidgetJsonFromMaster()
        }
        // 우선순위 3: 직접 네이티브 호출
        else -> nativeDataSource.buildWidgetJson()
    }
}
```

---

## 2️⃣ Repository 구현 분석

### 2.1 Repository 인터페이스 (Domain 계층)

**파일**: `domain/repository/CameraRepository.kt`

```kotlin
interface CameraRepository {
    // ═══════════════════════════════════════════════════════════════
    // 1️⃣ 카메라 연결 관리
    // ═══════════════════════════════════════════════════════════════
    fun getCameraFeed(): Flow<List<Camera>>
    suspend fun connectCamera(cameraId: String): Result<Boolean>
    suspend fun disconnectCamera(): Result<Boolean>
    fun isCameraConnected(): Flow<Boolean>
    fun isInitializing(): Flow<Boolean>
    fun isPtpipConnected(): Flow<Boolean>
    
    // ═══════════════════════════════════════════════════════════════
    // 2️⃣ 카메라 정보 및 설정
    // ═══════════════════════════════════════════════════════════════
    suspend fun getCameraInfo(): Result<String>
    suspend fun getCameraSettings(): Result<CameraSettings>
    suspend fun getCameraCapabilities(): Result<CameraCapabilities?>
    suspend fun updateCameraSetting(key: String, value: String): Result<Boolean>
    
    // ═══════════════════════════════════════════════════════════════
    // 3️⃣ 이벤트 리스너 (카메라 셔터, 외부 신호)
    // ═══════════════════════════════════════════════════════════════
    suspend fun startCameraEventListener(): Result<Boolean>
    suspend fun stopCameraEventListener(): Result<Boolean>
    fun setPhotoPreviewMode(enabled: Boolean)
    fun isEventListenerActive(): Flow<Boolean>
    
    // ═══════════════════════════════════════════════════════════════
    // 4️⃣ 촬영 모드
    // ═══════════════════════════════════════════════════════════════
    suspend fun capturePhoto(mode: ShootingMode = ShootingMode.SINGLE): Result<CapturedPhoto>
    fun startBurstCapture(count: Int): Flow<CapturedPhoto>
    fun startTimelapse(settings: TimelapseSettings): Flow<CapturedPhoto>
    fun startBracketing(settings: BracketingSettings): Flow<CapturedPhoto>
    suspend fun startBulbCapture(): Result<Boolean>
    suspend fun stopBulbCapture(): Result<CapturedPhoto>
    
    // ═══════════════════════════════════════════════════════════════
    // 5️⃣ 라이브뷰 & 포커스
    // ═══════════════════════════════════════════════════════════════
    fun startLiveView(): Flow<LiveViewFrame>
    suspend fun stopLiveView(): Result<Boolean>
    suspend fun autoFocus(): Result<Boolean>
    suspend fun manualFocus(x: Float, y: Float): Result<Boolean>
    suspend fun setFocusPoint(x: Float, y: Float): Result<Boolean>
    
    // ═══════════════════════════════════════════════════════════════
    // 6️⃣ 사진 관리 (로컬 + 원격)
    // ═══════════════════════════════════════════════════════════════
    fun getCapturedPhotos(): Flow<List<CapturedPhoto>>
    suspend fun getCameraPhotos(): Result<List<CameraPhoto>>
    suspend fun getCameraPhotosPaged(page: Int, pageSize: Int = 20): Result<PaginatedCameraPhotos>
    suspend fun getCameraThumbnail(photoPath: String): Result<ByteArray>
    suspend fun deletePhoto(photoId: String): Result<Boolean>
    suspend fun downloadPhotoFromCamera(photoId: String): Result<CapturedPhoto>
    fun setRawFileRestrictionCallback(callback: ((fileName: String, restrictionMessage: String) -> Unit)?)
}
```

---

### 2.2 Repository 구현체 (CameraRepositoryImpl)

**파일**: `data/repository/CameraRepositoryImpl.kt` (1041줄)

**핵심 구조**:
```kotlin
@Singleton
class CameraRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
    // 데이터 소스 의존성 (총 11개)
    private val nativeDataSource: NativeCameraDataSource,
    private val ptpipDataSource: PtpipDataSource,
    private val usbCameraManager: UsbCameraManager,
    private val photoCaptureEventManager: PhotoCaptureEventManager,
    private val appPreferencesDataSource: AppPreferencesDataSource,
    private val colorTransferUseCase: ColorTransferUseCase,
    private val connectionManager: CameraConnectionManager,
    private val eventManager: CameraEventManager,
    private val downloadManager: PhotoDownloadManager,
    private val uiStateManager: CameraUiStateManager,
    private val getSubscriptionUseCase: GetSubscriptionUseCase,
    private val errorHandlingManager: ErrorHandlingManager
) : CameraRepository {
    
    // StateFlow 기반 상태 관리
    private val _capturedPhotos = MutableStateFlow<List<CapturedPhoto>>(emptyList())
    private val _cameraSettings = MutableStateFlow<CameraSettings?>(null)
    
    // 중복 처리 방지용 Set (파일 키 기반)
    private val processedFiles = ConcurrentHashMap.newKeySet<String>()
    
    // 별도의 Coroutine Scope
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
```

**역할별 메서드 분류**:

| 메서드 그룹 | 목적 | 데이터 소스 |
|-----------|-----|-----------|
| `connectCamera()` | USB/PTPIP 선택적 연결 | ConnectionManager |
| `capturePhoto()` | 단일/연속/타임랩스 촬영 | NativeDataSource |
| `startLiveView()` | 라이브 카메라 피드 | NativeDataSource (CallbackFlow) |
| `getCameraPhotos()` | 카메라 메모리 사진 조회 | PhotoDownloadManager |
| `getCameraPhotosPaged()` | 페이징된 사진 조회 | PhotoDownloadManager |

**콜백 기반 설계의 핵심**:
```kotlin
// 1️⃣ PTPIP 사진 다운로드 콜백
ptpipDataSource.setPhotoDownloadedCallback { filePath, fileName, imageData ->
    repositoryScope.launch {
        val capturedPhoto = downloadManager.handleNativePhotoDownload(
            filePath, fileName, imageData,
            connectionManager.cameraCapabilities.value,
            _cameraSettings.value
        )
        if (capturedPhoto != null) {
            updateDownloadedPhoto(capturedPhoto)
        }
    }
}

// 2️⃣ USB 사진 촬영 콜백
nativeDataSource.capturePhotoAsync(object : CameraCaptureListener {
    override fun onPhotoDownloaded(filePath: String, fileName: String, imageData: ByteArray) {
        handleNativePhotoDownload(filePath, fileName, imageData)
    }
    
    override fun onCaptureFailed(errorCode: Int) {
        continuation.resumeWithException(Exception("Capture failed: $errorCode"))
    }
}, saveDir)
```

---

### 2.3 다른 Repository 구현체들

#### AuthRepository & AuthRepositoryImpl

```kotlin
interface AuthRepository {
    suspend fun signInWithGoogle(idToken: String): Result<User>
    suspend fun signOut(): Result<Boolean>
    fun getCurrentUser(): Flow<User?>
    suspend fun getUserProfile(): Result<User>
    suspend fun updateUserProfile(updates: Map<String, String>): Result<Boolean>
}

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val remoteDataSource: AuthRemoteDataSource  // Firebase
) : AuthRepository {
    private val _currentUser = MutableStateFlow<User?>(null)
    
    override suspend fun signInWithGoogle(idToken: String): Result<User> {
        return remoteDataSource.signInWithGoogle(idToken).also { result ->
            result.onSuccess { _currentUser.value = it }
        }
    }
}
```

#### SubscriptionRepository & SubscriptionRepositoryImpl

```kotlin
interface SubscriptionRepository {
    suspend fun getSubscription(): Result<Subscription>
    suspend fun purchaseSubscription(productId: String): Result<Boolean>
    suspend fun restorePurchases(): Result<Boolean>
}

@Singleton
class SubscriptionRepositoryImpl @Inject constructor(
    private val billingDataSource: BillingDataSource
) : SubscriptionRepository {
    override suspend fun getSubscription(): Result<Subscription> {
        // Google Play Billing Library 사용
        return billingDataSource.queryPurchases()
            .map { purchases -> purchases.toSubscription() }
    }
}
```

#### AppUpdateRepository & AppUpdateRepositoryImpl

```kotlin
interface AppUpdateRepository {
    suspend fun checkForUpdates(): Result<AppVersionInfo>
    suspend fun startImmediateUpdate(): Result<Boolean>
}

@Singleton
class AppUpdateRepositoryImpl @Inject constructor(
    private val playStoreDataSource: PlayStoreVersionDataSource,
    private val inAppUpdateManager: InAppUpdateManager
) : AppUpdateRepository {
    override suspend fun checkForUpdates(): Result<AppVersionInfo> {
        return playStoreDataSource.getLatestVersion()
    }
}
```

---

## 3️⃣ 네트워크 설정

### 3.1 PTPIP 연결 아키텍처

```
┌────────────────────────────────────────────────────────────┐
│                   PTPIP 카메라 발견 & 연결                 │
├────────────────────────────────────────────────────────────┤
│                                                            │
│ 1️⃣ mDNS 발견 (PtpipDiscoveryService)                       │
│    ├─ Jmdns 라이브러리 사용                               │
│    ├─ "_ptp._tcp" / "_ptpip._tcp" 서비스 탐색             │
│    └─ 결과: List<PtpipCamera> 반환                         │
│                                                            │
│ 2️⃣ Wi-Fi 네트워크 상태 확인 (WifiNetworkHelper)           │
│    ├─ ConnectivityManager 사용                            │
│    ├─ AP 모드 여부 확인                                   │
│    └─ SSID, IP 주소 확인                                   │
│                                                            │
│ 3️⃣ 인증 (NikonAuthenticationService)                     │
│    ├─ AP 모드: 직접 연결 + 기본 인증                      │
│    ├─ STA 모드: Nikon 고유 인증 프로토콜                  │
│    └─ 세션 토큰 획득                                      │
│                                                            │
│ 4️⃣ PTPIP 연결 (PtpipConnectionManager)                    │
│    ├─ TCP 소켓 생성 (IP:15740)                             │
│    ├─ PTPIP 핸드셰이크 수행                               │
│    └─ 연결 상태 StateFlow 업데이트                        │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

**파일 위치**: `data/network/ptpip/`

| 파일 | 책임 |
|-----|-----|
| `discovery/PtpipDiscoveryService.kt` | mDNS 기반 카메라 검색 |
| `connection/PtpipConnectionManager.kt` | TCP 연결 및 상태 관리 |
| `authentication/NikonAuthenticationService.kt` | 니콘 STA/AP 모드 인증 |
| `wifi/WifiNetworkHelper.kt` | Wi-Fi 상태 및 AP 관리 |

**mDNS 발견 구현 예시**:
```kotlin
class PtpipDiscoveryService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val jmdns = JmDNS.create()
    
    suspend fun discoverCameras(): Result<List<PtpipCamera>> {
        return withContext(Dispatchers.IO) {
            try {
                val services = jmdns.list("_ptp._tcp.local.")  // Nikon D850 등
                    .plus(jmdns.list("_ptpip._tcp.local."))    // Canon 등
                
                val cameras = services.map { service ->
                    PtpipCamera(
                        ipAddress = service.hostAddresses.firstOrNull() ?: "",
                        port = service.port,
                        name = service.name
                    )
                }
                Result.success(cameras)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
```

### 3.2 OkHttp 인터셉터 (향후)

현재는 Retrofit 사용 없음. 이전에 인증 토큰 관리가 필요한 경우:

```kotlin
class AuthInterceptor @Inject constructor(
    private val appPreferencesDataSource: AppPreferencesDataSource
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val authToken = runBlocking {
            appPreferencesDataSource.authToken.first()
        }
        
        val authenticatedRequest = originalRequest.newBuilder()
            .addHeader("Authorization", "Bearer $authToken")
            .build()
        
        return chain.proceed(authenticatedRequest)
    }
}
```

### 3.3 Firebase 설정

**파일**: `app/google-services.json`

```json
{
  "type": "service_account",
  "project_id": "camcon-app",
  "private_key_id": "...",
  "private_key": "...",
  "client_email": "firebase-adminsdk-...",
  "client_id": "...",
  "auth_uri": "https://accounts.google.com/o/oauth2/auth",
  "token_uri": "https://oauth2.googleapis.com/token"
}
```

**Firebase 모듈 (build.gradle)**:
```gradle
dependencies {
    implementation platform('com.google.firebase:firebase-bom:32.3.1')
    implementation 'com.google.firebase:firebase-auth'
    implementation 'com.google.firebase:firebase-firestore'
    implementation 'com.google.firebase:firebase-storage'
    implementation 'com.google.firebase:firebase-analytics'
}
```

---

## 4️⃣ 데이터 모델

### 4.1 Domain 모델 (Pure Kotlin Objects)

**파일**: `domain/model/`

#### Camera 관련 모델

```kotlin
// 기본 카메라 정보
data class Camera(
    val id: String,           // USB 디바이스 경로 또는 IP:Port
    val name: String,         // "Canon EOS R5"
    val isActive: Boolean     // 현재 선택된 카메라 여부
)

// 촬영된 사진
data class CapturedPhoto(
    val id: String,                    // UUID
    val filePath: String,              // "/storage/emulated/0/DCIM/CamCon/..."
    val thumbnailPath: String?,        // 썸네일 경로
    val captureTime: Long,             // System.currentTimeMillis()
    val cameraModel: String,           // "Canon EOS R5"
    val settings: CameraSettings?,     // 촬영 시 설정
    val size: Long,                    // 바이트 단위
    val width: Int,                    // 너비 (FREE 티어 최대 2000px)
    val height: Int,                   // 높이
    val isDownloading: Boolean         // 다운로드 진행 중 여부
)

// 카메라 메모리의 사진 목록
data class CameraPhoto(
    val path: String,           // 카메라 내부 경로 (/store_00010001/DCIM/...)
    val name: String,           // 파일명
    val size: Long,             // 파일 크기
    val date: Long,             // 촬영 시간
    val width: Int = 0,         // 이미지 너비
    val height: Int = 0,        // 이미지 높이
    val thumbnailPath: String? = null
)

// 페이징된 카메라 사진
data class PaginatedCameraPhotos(
    val photos: List<CameraPhoto>,
    val currentPage: Int,
    val pageSize: Int,
    val totalItems: Int,
    val totalPages: Int,
    val hasNext: Boolean
) {
    val hasPrevious: Boolean get() = currentPage > 0
}

// 카메라 설정 정보
data class CameraSettings(
    val iso: String,                    // "100", "200", "400", ...
    val shutterSpeed: String,           // "1/4000", "1/125", "1"
    val aperture: String,               // "1.8", "2.8", "5.6", ...
    val whiteBalance: String,           // "자동", "주광", "백열등", ...
    val focusMode: String,              // "AF-S", "AF-C", "MF"
    val exposureCompensation: String    // "-2.0", "0", "+2.0"
)

// 카메라 기능 정보
data class CameraCapabilities(
    val model: String,                  // 카메라 모델명
    val manufacturer: String,           // "Canon", "Nikon", ...
    val supportsLiveView: Boolean,      // 라이브뷰 지원 여부
    val supportsTimelapse: Boolean,     // 타임랩스 지원 여부
    val supportsBurst: Boolean,         // 연사 지원 여부
    val maxBurstCount: Int,             // 최대 연사 장수
    val supportedImageFormats: List<String> // ["jpg", "raw", "tiff"]
)
```

#### PTPIP 관련 모델

```kotlin
// PTPIP 연결 상태
enum class PtpipConnectionState {
    DISCONNECTED,      // 연결 안 됨
    CONNECTING,        // 연결 중
    CONNECTED,         // 연결됨
    ERROR              // 오류
}

// PTPIP 카메라
data class PtpipCamera(
    val ipAddress: String,             // "192.168.1.100"
    val port: Int,                     // 15740 (기본값)
    val name: String,                  // "Nikon D850"
    val isOnline: Boolean = true,
    val discoveredServiceType: String? // "_ptp._tcp" 또는 "_ptpip._tcp"
)

// Wi-Fi 네트워크 상태
data class WifiNetworkState(
    val isConnected: Boolean,          // Wi-Fi 연결 여부
    val isConnectedToCameraAP: Boolean, // 카메라 AP에 연결 여부
    val ssid: String?,                 // 네트워크 이름
    val detectedCameraIP: String?      // 감지된 카메라 IP
)

// 카메라 연결 타입
enum class CameraConnectionType {
    USB,        // USB 연결 (libgphoto2)
    AP_MODE,    // 카메라가 AP 제공 (PTPIP)
    STA_MODE    // 카메라가 기존 네트워크 연결 (PTPIP)
}
```

#### 라이브뷰 모델

```kotlin
// 라이브뷰 프레임
data class LiveViewFrame(
    val data: ByteArray,          // JPEG 압축 데이터
    val width: Int,               // 프레임 너비
    val height: Int,              // 프레임 높이
    val timestamp: Long           // 프레임 시간
)

// 촬영 모드
enum class ShootingMode {
    SINGLE,        // 단일 촬영
    BURST,         // 연사
    TIMELAPSE,     // 타임랩스
    BRACKETING,    // 브라켓팅
    BULB           // 벌브
}

// 타임랩스 설정
data class TimelapseSettings(
    val interval: Long,            // 촬영 간격 (ms)
    val duration: Long,            // 전체 지속 시간 (ms)
    val frameRate: Int             // 최종 비디오 FPS
)

// 브라켓팅 설정
data class BracketingSettings(
    val shotCount: Int,            // 촬영 장수
    val exposureStep: Float        // EV 스텝
)
```

### 4.2 DTO 클래스 (API 응답)

현재는 Retrofit 사용 최소화 (Firebase 메인)

```kotlin
// Firebase Firestore 문서 -> Kotlin 객체 변환
data class UserDTO {
    @DocumentId val id: String = ""
    val email: String = ""
    val displayName: String? = null
    val subscriptionTier: String = "FREE"
    val createdAt: Timestamp? = null
    
    // Domain 모델로 변환
    fun toDomain(): User = User(
        id = id,
        email = email,
        displayName = displayName,
        subscriptionTier = SubscriptionTier.valueOf(subscriptionTier)
    )
}

data class SubscriptionDTO {
    val productId: String = ""
    val title: String = ""
    val description: String = ""
    val price: String = ""
    val priceMicros: Long = 0
    val isActive: Boolean = false
    
    fun toDomain(): SubscriptionProduct = SubscriptionProduct(
        id = productId,
        title = title,
        description = description,
        price = price,
        isActive = isActive
    )
}
```

### 4.3 Room Entity (로컬 DB)

현재는 기본 구현만 존재. 향후 확장 예상:

```kotlin
@Entity(tableName = "captured_photos")
data class CapturedPhotoEntity(
    @PrimaryKey val id: String,
    val filePath: String,
    val thumbnailPath: String?,
    val captureTime: Long,
    val cameraModel: String,
    val size: Long,
    val width: Int,
    val height: Int,
    val isDownloading: Boolean,
    @Embedded val settings: CameraSettingsEntity?
)

@Entity(tableName = "camera_photos")
data class CameraPhotoEntity(
    @PrimaryKey val path: String,
    val name: String,
    val size: Long,
    val date: Long,
    val width: Int,
    val height: Int,
    val thumbnailPath: String?,
    val cameraId: String  // 외래키
)

// Room DAO
@Dao
interface CapturedPhotoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: CapturedPhotoEntity)
    
    @Query("SELECT * FROM captured_photos ORDER BY captureTime DESC")
    fun getAllPhotos(): Flow<List<CapturedPhotoEntity>>
    
    @Query("SELECT * FROM captured_photos LIMIT :pageSize OFFSET :offset")
    suspend fun getPhotosPaged(offset: Int, pageSize: Int): List<CapturedPhotoEntity>
}
```

### 4.4 Domain ↔ Data 모델 매핑

```kotlin
// Extension 함수를 통한 자동 변환
fun CapturedPhotoEntity.toDomain(): CapturedPhoto = CapturedPhoto(
    id = id,
    filePath = filePath,
    thumbnailPath = thumbnailPath,
    captureTime = captureTime,
    cameraModel = cameraModel,
    settings = settings?.toDomain(),
    size = size,
    width = width,
    height = height,
    isDownloading = isDownloading
)

fun CameraPhotoEntity.toDomain(): CameraPhoto = CameraPhoto(
    path = path,
    name = name,
    size = size,
    date = date,
    width = width,
    height = height,
    thumbnailPath = thumbnailPath
)

// 역방향 변환
fun CapturedPhoto.toEntity(): CapturedPhotoEntity = CapturedPhotoEntity(
    id = id,
    filePath = filePath,
    thumbnailPath = thumbnailPath,
    captureTime = captureTime,
    cameraModel = cameraModel,
    settings = settings?.toEntity(),
    size = size,
    width = width,
    height = height,
    isDownloading = isDownloading
)
```

---

## 5️⃣ 온라인/오프라인 동기화 전략

### 5.1 현재 동기화 모델

```
┌─────────────────────────────────────────────────────────┐
│                  온라인/오프라인 상태                   │
├─────────────────────────────────────────────────────────┤
│                                                         │
│ 📡 온라인 (Wi-Fi/USB 연결됨)                              │
│    ├─ 사진 실시간 다운로드                              │
│    ├─ 라이브뷰 스트리밍                                 │
│    ├─ 카메라 설정 실시간 제어                          │
│    └─ PTPIP/USB 통신 활성화                            │
│                                                         │
│ 🔌 오프라인 (Wi-Fi/USB 분리됨)                           │
│    ├─ 로컬 Room DB 사진 조회 (구현 예정)              │
│    ├─ 캐시된 카메라 설정 표시                          │
│    ├─ 라이브뷰 비활성화                                │
│    └─ 촬영 불가능                                      │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### 5.2 동기화 메커니즘

#### A. 설정 동기화 (AppPreferencesDataSource)

```kotlin
// 로컬 우선 정책
suspend fun getCameraSettings(): Result<CameraSettings> {
    return withContext(Dispatchers.IO) {
        try {
            // 1️⃣ 캐시된 설정이 있으면 우선 반환
            _cameraSettings.value?.let { cachedSettings ->
                Log.d(TAG, "캐시된 카메라 설정 반환")
                return@withContext Result.success(cachedSettings)
            }
            
            // 2️⃣ 네이티브/PTPIP에서 실시간으로 가져오기
            val widgetJson = getWidgetJsonFromSource()
            val settings = parseWidgetJsonToSettings(widgetJson)
            
            // 3️⃣ 로컬 캐시에 저장
            _cameraSettings.value = settings
            
            Result.success(settings)
        } catch (e: Exception) {
            // 오프라인 상태: 캐시된 설정 반환
            _cameraSettings.value?.let {
                Result.success(it)
            } ?: Result.failure(e)
        }
    }
}
```

#### B. 사진 동기화 (PhotoDownloadManager)

```kotlin
// 온라인 상태: 카메라에서 직접 다운로드
suspend fun getCameraPhotos(): Result<List<CameraPhoto>> {
    return if (isConnected) {
        // 카메라의 사진 목록을 가져와서 로컬 DB에 저장
        val photos = nativeDataSource.getCameraPhotos()
        // Room에 삽입: repository.insertPhotos(photos)
        Result.success(photos)
    } else {
        // 오프라인: Room DB에서 조회
        Result.success(roomDatabase.cameraPhotoDao().getAllPhotos())
    }
}

// 중복 처리 방지 (파일 해시 기반)
private fun shouldProcessFile(fileKey: String): Boolean {
    return processedFiles.add(fileKey)  // ConcurrentHashMap
}
```

#### C. 구독 정보 동기화 (SubscriptionRepository)

```kotlin
// Firebase 우선, 실패 시 로컬 캐시
suspend fun getSubscription(): Result<Subscription> {
    return try {
        // 온라인: Firebase Firestore에서 가져오기
        val subscription = firestore.collection("subscriptions")
            .document(userId).get().await()
            .toObject(SubscriptionDTO::class.java)?.toDomain()
        
        // 로컬 캐시 업데이트
        appPreferencesDataSource.saveSubscriptionTier(subscription?.tier)
        
        Result.success(subscription)
    } catch (e: Exception) {
        // 오프라인: 캐시된 구독 정보 반환
        val cachedTier = appPreferencesDataSource.subscriptionTierEnum.first()
        Result.success(Subscription(tier = cachedTier))
    }
}
```

### 5.3 오프라인 모드 제한사항

```kotlin
// 이 작업들은 온라인 상태에서만 가능
val canCapturePhoto = isOnline && isCameraConnected
val canStartLiveView = isOnline && isCameraConnected
val canUpdateSettings = isOnline && isCameraConnected

// 이 작업들은 오프라인에서도 가능
val canViewDownloadedPhotos = true  // 로컬 저장된 사진
val canViewCameraPhotos = isOnline  // 카메라 메모리의 사진
val canAccessSettings = true        // 캐시된 설정
```

---

## 6️⃣ 핵심 데이터 플로우

### 6.1 사진 촬영 플로우

```
┌─────────────────────────────────────────────────────────────┐
│ Repository.capturePhoto(mode)                               │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│ 1️⃣ 카메라 연결 상태 검증                                     │
│    └─ if (!connectionManager.isConnected) → Error            │
│                                                             │
│ 2️⃣ NativeCameraDataSource.capturePhotoAsync() 호출         │
│    └─ CameraCaptureListener 콜백 등록                      │
│                                                             │
│ 3️⃣ 콜백 수신:                                              │
│    ├─ onPhotoCaptured(fullPath, fileName)                  │
│    │   └─ 네이티브에서 사진 캡처 완료 신호                 │
│    │                                                       │
│    ├─ onPhotoDownloaded(fullPath, fileName, imageData)    │
│    │   ├─ 파일 포맷 검증 (jpg, raw, tiff)                │
│    │   ├─ PhotoDownloadManager.handlePhotoDownload() 호출 │
│    │   ├─ 리사이징 (FREE 티어: 2000px 제한)               │
│    │   ├─ 색감 전송 처리 (설정된 경우)                    │
│    │   ├─ MediaStore 등록 (Android 앨범)                  │
│    │   ├─ Room DB 저장                                    │
│    │   └─ _capturedPhotos StateFlow 업데이트              │
│    │                                                       │
│    └─ onCaptureFailed(errorCode)                           │
│        └─ continuation.resumeWithException(Exception)      │
│                                                             │
│ 4️⃣ UI에 CapturedPhoto 반환 (suspendCancellableCoroutine) │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 6.2 라이브뷰 플로우

```
┌──────────────────────────────────────────────────────┐
│ Repository.startLiveView(): Flow<LiveViewFrame>      │
├──────────────────────────────────────────────────────┤
│                                                      │
│ callbackFlow {  // Flow 빌더                         │
│   ├─ 연결 상태 확인 (USB OR PTPIP)                  │
│   │                                                  │
│   ├─ NativeDataSource.startLiveView(callback)       │
│   │   └─ object : LiveViewCallback {                │
│   │       override onLiveViewFrame(ByteBuffer) {    │
│   │           val liveViewFrame = LiveViewFrame(...)│
│   │           trySend(liveViewFrame)                │
│   │       }                                          │
│   │   }                                              │
│   │                                                  │
│   └─ awaitClose {                                   │
│       NativeDataSource.stopLiveView()               │
│   }                                                  │
│ }                                                    │
│                                                      │
│ // UI에서 구독                                       │
│ repository.startLiveView()                          │
│     .collect { frame ->                             │
│         decoder.decode(frame.data) // JPEG 디코딩  │
│         updatePreviewImage(bitmap)                  │
│     }                                                │
│                                                      │
└──────────────────────────────────────────────────────┘
```

### 6.3 카메라 사진 조회 플로우

```
┌────────────────────────────────────────────────────────┐
│ Repository.getCameraPhotos()                           │
├────────────────────────────────────────────────────────┤
│                                                        │
│ 1️⃣ PhotoDownloadManager.getCameraPhotos()           │
│    ├─ 연결 상태 확인 (connectionManager.isConnected)  │
│    │                                                  │
│    ├─ 온라인: NativeDataSource.getCameraPhotos()     │
│    │   └─ libgphoto2 API 호출 (카메라 메모리 스캔)   │
│    │                                                  │
│    ├─ 리스트 반환: List<CameraPhoto>                 │
│    │   ├─ path: "/store_00010001/DCIM/105KAY_1/..." │
│    │   ├─ name: "KY6_0035.JPG"                       │
│    │   ├─ size: 8589934592 바이트                    │
│    │   └─ date: 1707148800000                        │
│    │                                                  │
│    ├─ 이벤트 리스너 상태 확인 및 재시작             │
│    │   └─ if (!eventManager.isRunning())             │
│    │       eventManager.startCameraEventListener()   │
│    │                                                  │
│    └─ Room DB에 저장 (동기화)                        │
│                                                        │
│ 2️⃣ 페이징 버전: getCameraPhotosPaged()             │
│    ├─ page: 0, pageSize: 20                          │
│    ├─ NativeDataSource.getCameraPhotosPaged()       │
│    └─ Result<PaginatedCameraPhotos> 반환            │
│                                                        │
└────────────────────────────────────────────────────────┘
```

---

## 7️⃣ DI (Dependency Injection) 설정

### 7.1 RepositoryModule (Hilt)

**파일**: `di/RepositoryModule.kt`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    
    @Binds
    @Singleton
    abstract fun bindCameraRepository(
        impl: CameraRepositoryImpl
    ): CameraRepository
    
    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        impl: AuthRepositoryImpl
    ): AuthRepository
    
    @Binds
    @Singleton
    abstract fun bindSubscriptionRepository(
        impl: SubscriptionRepositoryImpl
    ): SubscriptionRepository
    
    @Binds
    @Singleton
    abstract fun bindAppUpdateRepository(
        impl: AppUpdateRepositoryImpl
    ): AppUpdateRepository
    
    @Binds
    @Singleton
    abstract fun bindCameraConnectionGlobalManager(
        impl: CameraConnectionGlobalManagerImpl
    ): CameraConnectionGlobalManager
}
```

### 7.2 AppModule (기타 의존성)

**파일**: `di/AppModule.kt`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()
    
    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = 
        FirebaseFirestore.getInstance()
    
    @Provides
    @Singleton
    fun provideBillingClient(@ApplicationContext context: Context): BillingClient =
        BillingClient.newBuilder(context)
            .setListener { billingResult, purchases -> }
            .build()
    
    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context = context
}
```

---

## 8️⃣ 주요 설계 패턴

### 8.1 Repository 패턴
- 데이터 소스 추상화
- 비즈니스 로직과 데이터 접근 분리
- 테스트 용이성

### 8.2 Facade 패턴
- `CameraRepositoryImpl`: 여러 데이터 소스를 단일 인터페이스로 통합
- `UsbCameraManager`: USB 컴포넌트들의 조합

### 8.3 Callback 패턴
- 비동기 작업 처리 (사진 촬영, 다운로드)
- `CameraCaptureListener`, `LiveViewCallback`
- Native JNI와의 상호작용

### 8.4 StateFlow 패턴
- 반응형 상태 관리
- UI와 Data Layer 간 자동 동기화
- 생명주기 안전 (Flow)

### 8.5 Manager 패턴
- `CameraConnectionManager`: 연결 상태 관리
- `PhotoDownloadManager`: 사진 다운로드 프로세스
- `CameraEventManager`: 외부 셔터 이벤트 처리

---

## 9️⃣ 문제점 및 개선안

### 9.1 현재 문제점

| 문제 | 심각도 | 설명 |
|------|-------|-----|
| **중복 처리 미흡** | ⚠️ | 파일 해시 기반이지만 완벽하지 않음 |
| **Room DB 미사용** | 🔴 | 로컬 캐싱이 불완전함 |
| **에러 처리 비일관** | ⚠️ | Repository마다 다른 에러 처리 전략 |
| **테스트 커버리지 낮음** | 🔴 | 특히 Native 데이터 소스 |
| **메모리 누수 위험** | ⚠️ | 콜백 기반 설계에서 참조 관리 필요 |

### 9.2 개선안

#### A. Room DB 완전 도입
```kotlin
// 사진 캐싱을 Room에서 관리
@Entity(tableName = "photos")
data class PhotoEntity(
    @PrimaryKey val id: String,
    val filePath: String,
    val size: Long,
    val timestamp: Long,
    @ColumnInfo(name = "camera_id") val cameraId: String
)

// Sync 상태 추적
data class SyncState(
    val lastSyncTime: Long,
    val pendingDownloads: Int,
    val isSyncing: Boolean
)
```

#### B. 통일된 에러 처리
```kotlin
sealed class DataResult<T> {
    data class Success<T>(val data: T) : DataResult<T>()
    data class Error<T>(val code: ErrorCode, val message: String, val exception: Throwable? = null) : DataResult<T>()
    object Loading : DataResult<Nothing>()
}

enum class ErrorCode {
    CAMERA_DISCONNECTED,
    NETWORK_ERROR,
    AUTHENTICATION_FAILED,
    STORAGE_FULL,
    UNSUPPORTED_FORMAT,
    UNKNOWN
}
```

#### C. 메모리 누수 방지
```kotlin
// WeakReference 사용
private var photoDownloadCallback: ((CapturedPhoto) -> Unit)? = null

fun setPhotoDownloadCallback(callback: (CapturedPhoto) -> Unit) {
    this.photoDownloadCallback = callback
}

// 사용 후 명시적 정리
override fun onCleared() {
    photoDownloadCallback = null
}
```

---

## 🔟 결론

### 데이터 레이어의 핵심 특성

1. **다중 데이터 소스 통합**: USB, PTPIP, Local, Remote를 단일 Repository로 관리
2. **강화된 동기화**: 온라인/오프라인 자동 전환, 캐싱 전략
3. **콜백 기반 비동기**: Native JNI와의 통신, 라이브뷰 스트리밍
4. **구독 기반 기능**: FREE 티어 2000px 리사이징, ADMIN 라이브뷰

### 향후 발전 방향

- ✅ Room Database 완전 통합
- ✅ 구조화된 에러 처리 (sealed class)
- ✅ 유닛 테스트 확대
- ✅ 메모리 누수 방지 강화
- ✅ 오프라인 우선 동기화 전략

