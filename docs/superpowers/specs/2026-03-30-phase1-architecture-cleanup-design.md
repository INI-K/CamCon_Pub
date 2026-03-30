# Phase 1: Domain 순수성 복구 — 아키텍처 정리 설계

## 목표

CamCon 프로젝트의 Clean Architecture 레이어 경계를 복구한다.
domain 레이어에서 android.*, data 구체 클래스, 외부 라이브러리 의존성을 완전히 제거한다.

## 현재 문제 요약

| 위반 유형 | 건수 | 예시 |
|----------|------|------|
| domain -> data 직접 참조 | 5 클래스 | CameraConnectionGlobalManager -> PtpipDataSource |
| domain -> android.* | 10 클래스 | ErrorHandlingManager -> CameraNative, Log |
| domain -> 외부 라이브러리 | 1 클래스 | SubscriptionProduct -> Gson |
| presentation -> data impl | 8+ 클래스 | CameraViewModel -> CameraRepositoryImpl |
| data -> domain UseCase (역방향) | 1 클래스 | CameraRepositoryImpl -> ColorTransferUseCase |

## 단계별 설계

---

### Step 1-1: domain 인터페이스 추출 + data 바인딩

**문제:** CameraConnectionGlobalManager가 PtpipDataSource, UsbCameraManager를 직접 의존

**해결:**
1. `domain/repository/`에 인터페이스 추출:

```kotlin
// domain/repository/CameraConnectionStateProvider.kt (신규)
interface CameraConnectionStateProvider {
    val isUsbCameraConnected: StateFlow<Boolean>
    val ptpipConnectionState: StateFlow<PtpipConnectionState>
    val wifiNetworkState: StateFlow<WifiNetworkState>
    val discoveredCameras: StateFlow<List<PtpipCamera>>
}

// domain/repository/UsbDeviceRepository.kt (신규)
interface UsbDeviceRepository {
    fun getCameraDevices(): List<UsbDeviceInfo>
    fun requestPermission(deviceId: String)
}
```

2. `data/repository/`에 구현체:

```kotlin
// data/repository/CameraConnectionStateProviderImpl.kt (신규)
@Singleton
class CameraConnectionStateProviderImpl @Inject constructor(
    private val ptpipDataSource: PtpipDataSource,
    private val usbCameraManager: UsbCameraManager
) : CameraConnectionStateProvider {
    override val isUsbCameraConnected get() = usbCameraManager.isNativeCameraConnected
    override val ptpipConnectionState get() = ptpipDataSource.connectionState
    override val wifiNetworkState get() = ptpipDataSource.wifiNetworkState
    override val discoveredCameras get() = ptpipDataSource.discoveredCameras
}

// data/repository/UsbDeviceRepositoryImpl.kt (신규)
@Singleton
class UsbDeviceRepositoryImpl @Inject constructor(
    private val usbCameraManager: UsbCameraManager
) : UsbDeviceRepository {
    override fun getCameraDevices(): List<UsbDeviceInfo> =
        usbCameraManager.getCameraDevices().map { it.toUsbDeviceInfo() }
    override fun requestPermission(deviceId: String) =
        usbCameraManager.requestPermissionById(deviceId)
}
```

3. `di/RepositoryModule.kt`에 바인딩 추가

4. `domain/model/UsbDeviceInfo.kt` (신규) — `UsbDevice` 대신 순수 Kotlin 모델:
```kotlin
data class UsbDeviceInfo(
    val deviceId: String,
    val deviceName: String,
    val vendorId: Int,
    val productId: Int
)
```

5. CameraConnectionGlobalManager 수정:
   - `PtpipDataSource` → `CameraConnectionStateProvider`
   - `UsbCameraManager` → `CameraConnectionStateProvider`

6. USB UseCase 수정:
   - `UsbCameraManager` → `UsbDeviceRepository`
   - `UsbDevice` → `UsbDeviceInfo`

**영향 파일:**
- 수정: CameraConnectionGlobalManager.kt, RequestUsbPermissionUseCase.kt, RefreshUsbDevicesUseCase.kt
- 신규: CameraConnectionStateProvider.kt, CameraConnectionStateProviderImpl.kt, UsbDeviceRepository.kt, UsbDeviceRepositoryImpl.kt, UsbDeviceInfo.kt
- 수정: RepositoryModule.kt, AppModule.kt
- 수정: USB UseCase 호출부 (presentation 레이어)

---

### Step 1-2: ErrorHandlingManager에서 JNI 의존성 제거

**문제:** domain/manager/ErrorHandlingManager가 CameraNative, NativeErrorCallback을 직접 참조

**해결:**
1. `domain/manager/`에 인터페이스 추출:

```kotlin
// domain/manager/NativeErrorCallbackRegistrar.kt (신규)
interface NativeErrorCallbackRegistrar {
    fun registerErrorCallback(onError: (errorCode: Int, errorMessage: String) -> Unit)
    fun unregisterErrorCallback()
}
```

2. `data/datasource/nativesource/`에 구현체:

```kotlin
// data/datasource/nativesource/NativeErrorCallbackRegistrarImpl.kt (신규)
@Singleton
class NativeErrorCallbackRegistrarImpl @Inject constructor() : NativeErrorCallbackRegistrar {
    override fun registerErrorCallback(onError: (Int, String) -> Unit) {
        CameraNative.setErrorCallback(object : NativeErrorCallback {
            override fun onNativeError(errorCode: Int, errorMessage: String) {
                onError(errorCode, errorMessage)
            }
        })
    }
    override fun unregisterErrorCallback() {
        CameraNative.setErrorCallback(null)
    }
}
```

3. ErrorHandlingManager 수정:
   - `CameraNative` → `NativeErrorCallbackRegistrar` (생성자 주입)
   - `android.util.Log` → Logger 인터페이스 (Step 1-4에서 처리)

**영향 파일:**
- 수정: ErrorHandlingManager.kt
- 신규: NativeErrorCallbackRegistrar.kt, NativeErrorCallbackRegistrarImpl.kt
- 수정: di/RepositoryModule.kt (바인딩 추가)

---

### Step 1-3: ColorTransferUseCase → data 레이어 이동

**문제:** 538줄짜리 UseCase가 Android Bitmap, Context, ExifInterface 등을 직접 사용

**해결:**
1. 기존 `domain/usecase/ColorTransferUseCase.kt`의 구현 로직을 `data/processor/ColorTransferProcessorImpl.kt`로 이동

2. domain에 얇은 인터페이스 + UseCase 래퍼:

```kotlin
// domain/repository/ColorTransferRepository.kt (신규)
interface ColorTransferRepository {
    suspend fun applyColorTransfer(
        sourceImageUri: String,
        referenceImageUri: String,
        intensity: Float
    ): Result<String>  // 결과 이미지 URI
}
```

3. data에 구현:
```kotlin
// data/repository/ColorTransferRepositoryImpl.kt (신규)
// 기존 ColorTransferUseCase의 로직을 여기로 이동
```

4. domain UseCase는 얇은 래퍼:
```kotlin
// domain/usecase/ColorTransferUseCase.kt (수정 — 대폭 축소)
class ColorTransferUseCase @Inject constructor(
    private val colorTransferRepository: ColorTransferRepository
) {
    suspend operator fun invoke(
        sourceUri: String, referenceUri: String, intensity: Float
    ): Result<String> = colorTransferRepository.applyColorTransfer(sourceUri, referenceUri, intensity)
}
```

**영향 파일:**
- 수정: ColorTransferUseCase.kt (538줄 → ~15줄)
- 신규: ColorTransferRepository.kt (domain), ColorTransferRepositoryImpl.kt (data)
- 수정: RepositoryModule.kt
- 수정: 호출부 (ColorTransferSettingsActivity 등)

---

### Step 1-4: domain 모델 정리

**SubscriptionProduct — Gson 제거:**
```kotlin
// domain/model/SubscriptionProduct.kt (수정)
data class SubscriptionProduct(
    val productId: String,
    val tier: SubscriptionTier,
    val title: String,
    val description: String,
    val price: String,
    val currencyCode: String,
    val priceAmountMicros: Long,
    val billingPeriod: String
)
```
- data 레이어에서 직렬화가 필요하면 data/model/SubscriptionProductDto.kt에 @SerializedName 포함 DTO를 두고 mapper로 변환

**ThemeMode — 레이어 이동:**
- `data/datasource/local/ThemeMode.kt` → `domain/model/ThemeMode.kt`
- presentation/theme/Theme.kt와 SettingsActivity에서 import 경로만 변경

**PtpTimeoutException — 레이어 이동:**
- `data/repository/managers/PtpTimeoutException.kt` → `domain/model/PtpTimeoutException.kt`

**CapturePhotoUseCase 중복 제거:**
- `domain/usecase/CapturePhotoUseCase.kt` (루트, @Inject 없음) 삭제
- `domain/usecase/camera/CapturePhotoUseCase.kt` 유지

**SubscriptionRepository 바인딩 추가:**
- SubscriptionRepositoryImpl을 data에 생성하거나, 기존 코드에서 사용하지 않는다면 인터페이스 제거

---

### Step 1-5: android.util.Log → Logger 추상화

**문제:** domain 10개 클래스에서 android.util.Log 직접 사용

**해결:**
1. domain에 Logger 인터페이스:
```kotlin
// domain/util/Logger.kt (신규)
interface Logger {
    fun d(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable? = null)
    fun w(tag: String, message: String, throwable: Throwable? = null)
}
```

2. data에 구현:
```kotlin
// data/util/AndroidLogger.kt (신규)
@Singleton
class AndroidLogger @Inject constructor() : Logger {
    override fun d(tag: String, message: String) = Log.d(tag, message)
    override fun e(tag: String, message: String, throwable: Throwable?) =
        if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
    override fun w(tag: String, message: String, throwable: Throwable?) =
        if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
}
```

3. DI 바인딩 추가

4. domain 10개 클래스에서 `android.util.Log` → `Logger` 주입으로 교체:
   - CameraConnectionGlobalManager
   - ErrorHandlingManager
   - CameraSettingsManager
   - ValidateImageFormatUseCase
   - AdminUserManagementUseCase
   - GetSubscriptionUseCase
   - UserReferralUseCase
   - ColorTransferUseCase (Step 1-3에서 이미 이동)
   - GetCurrentUserUseCase (확인 필요)
   - SignInWithGoogleUseCase (확인 필요)

---

### Step 1-6: presentation → data 직접 참조 제거

**CameraViewModel → CameraRepositoryImpl 다운캐스팅 제거:**
- `CameraRepository` 인터페이스에 필요한 메서드 추가 (현재 다운캐스팅으로 접근하는 기능)
- `is CameraRepositoryImpl` 분기 제거

**CameraViewModel → CameraNative 직접 호출 제거:**
- `isCameraConnected()`, `isCameraInitialized()`, `getCameraFileList()` → `CameraRepository` 인터페이스에 추가
- ViewModel에서 CameraNative import 제거

**기타 presentation → data 직접 참조:**
- presentation에서 DataSource(AppPreferencesDataSource, PtpipDataSource 등)를 직접 참조하는 부분은 기존 Repository에 메서드 추가하거나 새 Repository 인터페이스로 래핑
- CameraConnectionManager(presentation) → CameraConnectionStateProvider 사용
- PtpipViewModel → PtpipRepository (신규) 인터페이스로 래핑
- AppSettingsViewModel → AppSettingsRepository (신규) 인터페이스로 래핑
- Theme.kt → ThemeMode가 Step 1-4에서 domain으로 이동되므로 import 경로만 변경

---

## 빌드 안전 전략

- 각 Step을 독립 커밋으로 분리
- Step마다 빌드 확인 (`./gradlew assembleDebug`)
- Step 1-1 ~ 1-4는 독립적으로 수정 가능
- Step 1-5 (Logger)는 모든 domain 클래스에 영향 → 1-1~1-4 완료 후 수행
- Step 1-6 (presentation 정리)은 1-1~1-5의 인터페이스가 준비된 후 수행

## 완료 기준

- [ ] domain 레이어에 `import android.*` 0건 (android.util.Log 포함)
- [ ] domain 레이어에 `import com.inik.camcon.data.*` 0건
- [ ] domain 레이어에 `import com.google.gson.*` 0건
- [ ] domain 레이어에 `import com.inik.camcon.CameraNative` 0건
- [ ] presentation에서 data impl 클래스 직접 참조 0건
- [ ] `./gradlew assembleDebug` 빌드 성공
- [ ] 기존 테스트 (`./gradlew test`) 통과

## Phase 1에서 수정하지 않는 것

- Coroutine 구조 개선 (Phase 2)
- Material2/3 통합 (Phase 4)
- UX 개선 (Phase 5)
- @Singleton 과다 사용 (Phase 2에서 scope 정리와 함께)
- UseCase SRP 위반 (AdminUserManagementUseCase 분리 등 — Phase 6)
