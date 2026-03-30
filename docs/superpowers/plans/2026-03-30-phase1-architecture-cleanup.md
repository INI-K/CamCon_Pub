# Phase 1: Domain 순수성 복구 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** domain 레이어에서 android.*, data 구체 클래스, 외부 라이브러리 의존성을 완전히 제거하여 Clean Architecture 레이어 경계 복구

**Architecture:** domain에 인터페이스를 추가하고 data에 구현체를 만들어 Hilt로 바인딩. domain의 android.util.Log를 Logger 추상화로 교체. 잘못된 레이어에 있는 모델을 올바른 레이어로 이동.

**Tech Stack:** Kotlin, Hilt (KSP), Coroutines Flow, Clean Architecture

---

### Task 1: UsbDeviceInfo 도메인 모델 + UsbDeviceRepository 인터페이스 생성

**Files:**
- Create: `app/src/main/java/com/inik/camcon/domain/model/UsbDeviceInfo.kt`
- Create: `app/src/main/java/com/inik/camcon/domain/repository/UsbDeviceRepository.kt`

- [ ] **Step 1: UsbDeviceInfo 도메인 모델 생성**

```kotlin
// domain/model/UsbDeviceInfo.kt
package com.inik.camcon.domain.model

data class UsbDeviceInfo(
    val deviceId: String,
    val deviceName: String,
    val vendorId: Int,
    val productId: Int
)
```

- [ ] **Step 2: UsbDeviceRepository 인터페이스 생성**

```kotlin
// domain/repository/UsbDeviceRepository.kt
package com.inik.camcon.domain.repository

import com.inik.camcon.domain.model.UsbDeviceInfo

interface UsbDeviceRepository {
    fun getCameraDevices(): List<UsbDeviceInfo>
    fun requestPermission(deviceId: String)
}
```

- [ ] **Step 3: 빌드 확인**

Run: `cd /Users/ini-k/CamCon && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/inik/camcon/domain/model/UsbDeviceInfo.kt app/src/main/java/com/inik/camcon/domain/repository/UsbDeviceRepository.kt
git commit -m "feat: add UsbDeviceInfo model and UsbDeviceRepository interface in domain"
```

---

### Task 2: CameraConnectionStateProvider 인터페이스 생성

**Files:**
- Create: `app/src/main/java/com/inik/camcon/domain/repository/CameraConnectionStateProvider.kt`

- [ ] **Step 1: CameraConnectionStateProvider 인터페이스 생성**

```kotlin
// domain/repository/CameraConnectionStateProvider.kt
package com.inik.camcon.domain.repository

import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.PtpipConnectionState
import com.inik.camcon.domain.model.WifiNetworkState
import kotlinx.coroutines.flow.StateFlow

interface CameraConnectionStateProvider {
    val isUsbCameraConnected: StateFlow<Boolean>
    val ptpipConnectionState: StateFlow<PtpipConnectionState>
    val wifiNetworkState: StateFlow<WifiNetworkState>
    val discoveredCameras: StateFlow<List<PtpipCamera>>
}
```

- [ ] **Step 2: 빌드 확인**

Run: `cd /Users/ini-k/CamCon && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/inik/camcon/domain/repository/CameraConnectionStateProvider.kt
git commit -m "feat: add CameraConnectionStateProvider interface in domain"
```

---

### Task 3: NativeErrorCallbackRegistrar 인터페이스 생성

**Files:**
- Create: `app/src/main/java/com/inik/camcon/domain/manager/NativeErrorCallbackRegistrar.kt`

- [ ] **Step 1: NativeErrorCallbackRegistrar 인터페이스 생성**

```kotlin
// domain/manager/NativeErrorCallbackRegistrar.kt
package com.inik.camcon.domain.manager

interface NativeErrorCallbackRegistrar {
    fun registerErrorCallback(onError: (errorCode: Int, errorMessage: String) -> Unit)
    fun unregisterErrorCallback()
}
```

- [ ] **Step 2: 빌드 확인 + Commit**

```bash
cd /Users/ini-k/CamCon && ./gradlew assembleDebug 2>&1 | tail -5
git add app/src/main/java/com/inik/camcon/domain/manager/NativeErrorCallbackRegistrar.kt
git commit -m "feat: add NativeErrorCallbackRegistrar interface in domain"
```

---

### Task 4: Logger 인터페이스 생성

**Files:**
- Create: `app/src/main/java/com/inik/camcon/domain/util/Logger.kt`

- [ ] **Step 1: Logger 인터페이스 생성**

```kotlin
// domain/util/Logger.kt
package com.inik.camcon.domain.util

interface Logger {
    fun d(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable? = null)
    fun w(tag: String, message: String, throwable: Throwable? = null)
    fun i(tag: String, message: String)
}
```

- [ ] **Step 2: 빌드 확인 + Commit**

```bash
cd /Users/ini-k/CamCon && ./gradlew assembleDebug 2>&1 | tail -5
git add app/src/main/java/com/inik/camcon/domain/util/Logger.kt
git commit -m "feat: add Logger interface in domain"
```

---

### Task 5: ColorTransferRepository 인터페이스 생성

**Files:**
- Create: `app/src/main/java/com/inik/camcon/domain/repository/ColorTransferRepository.kt`

- [ ] **Step 1: ColorTransferRepository 인터페이스 생성**

```kotlin
// domain/repository/ColorTransferRepository.kt
package com.inik.camcon.domain.repository

import com.inik.camcon.domain.model.ColorTransferResult

interface ColorTransferRepository {
    suspend fun applyColorTransfer(
        inputImagePath: String,
        referenceImagePath: String,
        originalImagePath: String? = null,
        intensity: Float = 1.0f
    ): Result<ColorTransferResult>

    suspend fun initializeProcessor(contextProvider: Any)
    fun releaseProcessor()
}
```

- [ ] **Step 2: ColorTransferResult 도메인 모델 생성**

Create: `app/src/main/java/com/inik/camcon/domain/model/ColorTransferResult.kt`

```kotlin
// domain/model/ColorTransferResult.kt
package com.inik.camcon.domain.model

data class ColorTransferResult(
    val outputPath: String,
    val width: Int,
    val height: Int
)
```

- [ ] **Step 3: 빌드 확인 + Commit**

```bash
cd /Users/ini-k/CamCon && ./gradlew assembleDebug 2>&1 | tail -5
git add app/src/main/java/com/inik/camcon/domain/repository/ColorTransferRepository.kt app/src/main/java/com/inik/camcon/domain/model/ColorTransferResult.kt
git commit -m "feat: add ColorTransferRepository interface and ColorTransferResult model"
```

---

### Task 6: data 레이어 구현체 생성 — CameraConnectionStateProviderImpl + UsbDeviceRepositoryImpl

**Files:**
- Create: `app/src/main/java/com/inik/camcon/data/repository/CameraConnectionStateProviderImpl.kt`
- Create: `app/src/main/java/com/inik/camcon/data/repository/UsbDeviceRepositoryImpl.kt`

- [ ] **Step 1: CameraConnectionStateProviderImpl 생성**

```kotlin
// data/repository/CameraConnectionStateProviderImpl.kt
package com.inik.camcon.data.repository

import com.inik.camcon.data.datasource.ptpip.PtpipDataSource
import com.inik.camcon.data.datasource.usb.UsbCameraManager
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.PtpipConnectionState
import com.inik.camcon.domain.model.WifiNetworkState
import com.inik.camcon.domain.repository.CameraConnectionStateProvider
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraConnectionStateProviderImpl @Inject constructor(
    private val ptpipDataSource: PtpipDataSource,
    private val usbCameraManager: UsbCameraManager
) : CameraConnectionStateProvider {
    override val isUsbCameraConnected: StateFlow<Boolean>
        get() = usbCameraManager.isNativeCameraConnected
    override val ptpipConnectionState: StateFlow<PtpipConnectionState>
        get() = ptpipDataSource.connectionState
    override val wifiNetworkState: StateFlow<WifiNetworkState>
        get() = ptpipDataSource.wifiNetworkState
    override val discoveredCameras: StateFlow<List<PtpipCamera>>
        get() = ptpipDataSource.discoveredCameras
}
```

- [ ] **Step 2: UsbDeviceRepositoryImpl 생성**

```kotlin
// data/repository/UsbDeviceRepositoryImpl.kt
package com.inik.camcon.data.repository

import com.inik.camcon.data.datasource.usb.UsbCameraManager
import com.inik.camcon.domain.model.UsbDeviceInfo
import com.inik.camcon.domain.repository.UsbDeviceRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsbDeviceRepositoryImpl @Inject constructor(
    private val usbCameraManager: UsbCameraManager
) : UsbDeviceRepository {
    override fun getCameraDevices(): List<UsbDeviceInfo> {
        return usbCameraManager.getCameraDevices().map { device ->
            UsbDeviceInfo(
                deviceId = device.deviceId.toString(),
                deviceName = device.deviceName ?: "Unknown",
                vendorId = device.vendorId,
                productId = device.productId
            )
        }
    }

    override fun requestPermission(deviceId: String) {
        val device = usbCameraManager.getCameraDevices().find {
            it.deviceId.toString() == deviceId
        }
        if (device != null) {
            usbCameraManager.requestPermission(device)
        }
    }
}
```

- [ ] **Step 3: 빌드 확인 + Commit**

```bash
cd /Users/ini-k/CamCon && ./gradlew assembleDebug 2>&1 | tail -5
git add app/src/main/java/com/inik/camcon/data/repository/CameraConnectionStateProviderImpl.kt app/src/main/java/com/inik/camcon/data/repository/UsbDeviceRepositoryImpl.kt
git commit -m "feat: add CameraConnectionStateProviderImpl and UsbDeviceRepositoryImpl"
```

---

### Task 7: data 레이어 구현체 생성 — NativeErrorCallbackRegistrarImpl + AndroidLogger

**Files:**
- Create: `app/src/main/java/com/inik/camcon/data/datasource/nativesource/NativeErrorCallbackRegistrarImpl.kt`
- Create: `app/src/main/java/com/inik/camcon/data/util/AndroidLogger.kt`

- [ ] **Step 1: NativeErrorCallbackRegistrarImpl 생성**

```kotlin
// data/datasource/nativesource/NativeErrorCallbackRegistrarImpl.kt
package com.inik.camcon.data.datasource.nativesource

import com.inik.camcon.CameraNative
import com.inik.camcon.NativeErrorCallback
import com.inik.camcon.domain.manager.NativeErrorCallbackRegistrar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeErrorCallbackRegistrarImpl @Inject constructor() : NativeErrorCallbackRegistrar {
    override fun registerErrorCallback(onError: (errorCode: Int, errorMessage: String) -> Unit) {
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

- [ ] **Step 2: AndroidLogger 생성**

```kotlin
// data/util/AndroidLogger.kt
package com.inik.camcon.data.util

import android.util.Log
import com.inik.camcon.domain.util.Logger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidLogger @Inject constructor() : Logger {
    override fun d(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
    }

    override fun w(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
    }

    override fun i(tag: String, message: String) {
        Log.i(tag, message)
    }
}
```

- [ ] **Step 3: 빌드 확인 + Commit**

```bash
cd /Users/ini-k/CamCon && ./gradlew assembleDebug 2>&1 | tail -5
git add app/src/main/java/com/inik/camcon/data/datasource/nativesource/NativeErrorCallbackRegistrarImpl.kt app/src/main/java/com/inik/camcon/data/util/AndroidLogger.kt
git commit -m "feat: add NativeErrorCallbackRegistrarImpl and AndroidLogger"
```

---

### Task 8: DI 바인딩 추가

**Files:**
- Modify: `app/src/main/java/com/inik/camcon/di/RepositoryModule.kt`

- [ ] **Step 1: RepositoryModule에 새 바인딩 추가**

기존 파일 끝의 `}` 직전에 추가:

```kotlin
    @Binds
    @Singleton
    abstract fun bindCameraConnectionStateProvider(
        impl: CameraConnectionStateProviderImpl
    ): CameraConnectionStateProvider

    @Binds
    @Singleton
    abstract fun bindUsbDeviceRepository(
        impl: UsbDeviceRepositoryImpl
    ): UsbDeviceRepository

    @Binds
    @Singleton
    abstract fun bindNativeErrorCallbackRegistrar(
        impl: NativeErrorCallbackRegistrarImpl
    ): NativeErrorCallbackRegistrar

    @Binds
    @Singleton
    abstract fun bindLogger(
        impl: AndroidLogger
    ): Logger
```

import 추가:
```kotlin
import com.inik.camcon.data.datasource.nativesource.NativeErrorCallbackRegistrarImpl
import com.inik.camcon.data.repository.CameraConnectionStateProviderImpl
import com.inik.camcon.data.repository.UsbDeviceRepositoryImpl
import com.inik.camcon.data.util.AndroidLogger
import com.inik.camcon.domain.manager.NativeErrorCallbackRegistrar
import com.inik.camcon.domain.repository.CameraConnectionStateProvider
import com.inik.camcon.domain.repository.UsbDeviceRepository
import com.inik.camcon.domain.util.Logger
```

- [ ] **Step 2: AppModule에서 CameraConnectionGlobalManager 제거**

`AppModule.kt`에서 `provideCameraConnectionGlobalManager` 메서드 삭제 (109~112줄). CameraConnectionGlobalManager가 이제 `@Inject constructor`로 자체 주입 가능하므로 `@Provides` 불필요.

- [ ] **Step 3: 빌드 확인 + Commit**

```bash
cd /Users/ini-k/CamCon && ./gradlew assembleDebug 2>&1 | tail -5
git add app/src/main/java/com/inik/camcon/di/RepositoryModule.kt app/src/main/java/com/inik/camcon/di/AppModule.kt
git commit -m "feat: add DI bindings for new interfaces"
```

---

### Task 9: CameraConnectionGlobalManager 리팩토링 — domain -> data 의존성 제거

**Files:**
- Modify: `app/src/main/java/com/inik/camcon/domain/manager/CameraConnectionGlobalManager.kt`

- [ ] **Step 1: 생성자를 인터페이스로 교체하고 Log를 Logger로 교체**

전체 파일을 다음으로 교체:

```kotlin
package com.inik.camcon.domain.manager

import com.inik.camcon.domain.model.CameraConnectionType
import com.inik.camcon.domain.model.GlobalCameraConnectionState
import com.inik.camcon.domain.model.PtpipConnectionState
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.WifiNetworkState
import com.inik.camcon.domain.repository.CameraConnectionStateProvider
import com.inik.camcon.domain.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraConnectionGlobalManager @Inject constructor(
    private val connectionStateProvider: CameraConnectionStateProvider,
    private val logger: Logger
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "CameraConnectionGlobalManager"
    }

    private val _globalConnectionState = MutableStateFlow(GlobalCameraConnectionState())
    val globalConnectionState: StateFlow<GlobalCameraConnectionState> =
        _globalConnectionState.asStateFlow()

    private val _activeConnectionType = MutableStateFlow<CameraConnectionType?>(null)
    val activeConnectionType: StateFlow<CameraConnectionType?> = _activeConnectionType.asStateFlow()

    private val _connectionStatusMessage = MutableStateFlow("연결 안됨")
    val connectionStatusMessage: StateFlow<String> = _connectionStatusMessage.asStateFlow()

    init {
        startGlobalStateMonitoring()
    }

    private fun startGlobalStateMonitoring() {
        connectionStateProvider.isUsbCameraConnected
            .onEach { isConnected ->
                logger.d(TAG, "USB 카메라 연결 상태 변경: $isConnected")
                updateGlobalState()
            }
            .launchIn(scope)

        connectionStateProvider.ptpipConnectionState
            .onEach { state ->
                logger.d(TAG, "PTPIP 연결 상태 변경: $state")
                updateGlobalState()
            }
            .launchIn(scope)

        connectionStateProvider.wifiNetworkState
            .onEach { networkState ->
                logger.d(TAG, "WiFi 네트워크 상태 변경: $networkState")
                updateGlobalState()
            }
            .launchIn(scope)

        connectionStateProvider.discoveredCameras
            .onEach { cameras ->
                logger.d(TAG, "발견된 카메라 목록 변경: ${cameras.size}개")
                updateGlobalState()
            }
            .launchIn(scope)
    }

    private fun updateGlobalState() {
        val usbConnected = connectionStateProvider.isUsbCameraConnected.value
        val ptpipState = connectionStateProvider.ptpipConnectionState.value
        val wifiState = connectionStateProvider.wifiNetworkState.value
        val discoveredCameras = connectionStateProvider.discoveredCameras.value

        val activeConnection = when {
            usbConnected -> CameraConnectionType.USB
            ptpipState == PtpipConnectionState.CONNECTED -> {
                if (wifiState.isConnectedToCameraAP) {
                    CameraConnectionType.AP_MODE
                } else {
                    CameraConnectionType.STA_MODE
                }
            }
            else -> null
        }

        if (activeConnection == CameraConnectionType.AP_MODE) {
            scope.launch {
                try {
                    logger.d(TAG, "AP 모드 연결 감지: 자동 파일 수신 대기 시작")
                } catch (e: Exception) {
                    logger.e(TAG, "AP 모드 파일 수신 대기 시작 실패", e)
                }
            }
        }

        val statusMessage = generateStatusMessage(usbConnected, ptpipState, wifiState, discoveredCameras)

        val newState = GlobalCameraConnectionState(
            isUsbConnected = usbConnected,
            ptpipConnectionState = ptpipState,
            wifiNetworkState = wifiState,
            discoveredCameras = discoveredCameras,
            activeConnectionType = activeConnection,
            isAnyConnectionActive = usbConnected || ptpipState == PtpipConnectionState.CONNECTED
        )

        if (_globalConnectionState.value != newState ||
            _activeConnectionType.value != activeConnection ||
            _connectionStatusMessage.value != statusMessage
        ) {
            _globalConnectionState.value = newState
            _activeConnectionType.value = activeConnection
            _connectionStatusMessage.value = statusMessage
            logger.d(TAG, "전역 상태 업데이트: activeConnection=$activeConnection, statusMessage=$statusMessage")
        }
    }

    private fun generateStatusMessage(
        usbConnected: Boolean,
        ptpipState: PtpipConnectionState,
        wifiState: WifiNetworkState,
        discoveredCameras: List<PtpipCamera>
    ): String {
        return when {
            usbConnected -> "USB 카메라 연결됨"
            ptpipState == PtpipConnectionState.CONNECTED -> {
                if (wifiState.isConnectedToCameraAP) "AP 모드 연결됨 (${wifiState.ssid})"
                else "STA 모드 연결됨 (${wifiState.ssid})"
            }
            ptpipState == PtpipConnectionState.CONNECTING -> "카메라 연결 중..."
            ptpipState == PtpipConnectionState.ERROR -> "카메라 연결 오류"
            wifiState.isConnectedToCameraAP -> "카메라 AP 연결됨 - 카메라 검색 가능"
            wifiState.isConnected -> "Wi-Fi 연결됨 - 카메라 검색 가능"
            else -> "연결 안됨"
        }
    }

    fun getCurrentActiveConnectionType(): CameraConnectionType? = activeConnectionType.value
    fun isConnectionTypeActive(type: CameraConnectionType): Boolean = activeConnectionType.value == type
    fun isAnyCameraConnected(): Boolean = globalConnectionState.value.isAnyConnectionActive
    fun isApModeConnected(): Boolean = globalConnectionState.value.wifiNetworkState.isConnectedToCameraAP
    fun isStaModeConnected(): Boolean {
        val state = globalConnectionState.value
        return state.ptpipConnectionState == PtpipConnectionState.CONNECTED &&
                !state.wifiNetworkState.isConnectedToCameraAP
    }
    fun isUsbConnected(): Boolean = globalConnectionState.value.isUsbConnected

    fun cleanup() {
        logger.d(TAG, "리소스 정리 시작")
    }
}
```

- [ ] **Step 2: 빌드 확인**

Run: `cd /Users/ini-k/CamCon && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/inik/camcon/domain/manager/CameraConnectionGlobalManager.kt
git commit -m "refactor: remove data/android dependencies from CameraConnectionGlobalManager"
```

---

### Task 10: ErrorHandlingManager 리팩토링 — CameraNative + Log 제거

**Files:**
- Modify: `app/src/main/java/com/inik/camcon/domain/manager/ErrorHandlingManager.kt`

- [ ] **Step 1: 생성자에 NativeErrorCallbackRegistrar, Logger 추가하고 CameraNative, Log 제거**

변경사항:
- import에서 `android.util.Log`, `com.inik.camcon.CameraNative`, `com.inik.camcon.NativeErrorCallback` 삭제
- import에 `com.inik.camcon.domain.util.Logger` 추가
- 생성자: `ErrorHandlingManager @Inject constructor()` → `ErrorHandlingManager @Inject constructor(private val callbackRegistrar: NativeErrorCallbackRegistrar, private val logger: Logger)`
- `registerNativeErrorCallback()` 내부: `CameraNative.setErrorCallback(object : NativeErrorCallback {...})` → `callbackRegistrar.registerErrorCallback { errorCode, errorMessage -> handleNativeError(errorCode, errorMessage) }`
- `cleanup()` 내부: `CameraNative.setErrorCallback(null)` → `callbackRegistrar.unregisterErrorCallback()`
- 모든 `Log.d(TAG, ...)` → `logger.d(TAG, ...)`
- 모든 `Log.e(TAG, ...)` → `logger.e(TAG, ...)`
- 모든 `Log.w(TAG, ...)` → `logger.w(TAG, ...)`

- [ ] **Step 2: 빌드 확인 + Commit**

```bash
cd /Users/ini-k/CamCon && ./gradlew assembleDebug 2>&1 | tail -5
git add app/src/main/java/com/inik/camcon/domain/manager/ErrorHandlingManager.kt
git commit -m "refactor: remove CameraNative and android.util.Log from ErrorHandlingManager"
```

---

### Task 11: USB UseCases 리팩토링 — data 직접 참조 + UsbDevice 제거

**Files:**
- Modify: `app/src/main/java/com/inik/camcon/domain/usecase/usb/RequestUsbPermissionUseCase.kt`
- Modify: `app/src/main/java/com/inik/camcon/domain/usecase/usb/RefreshUsbDevicesUseCase.kt`

- [ ] **Step 1: RequestUsbPermissionUseCase 수정**

```kotlin
package com.inik.camcon.domain.usecase.usb

import com.inik.camcon.domain.repository.UsbDeviceRepository
import javax.inject.Inject

class RequestUsbPermissionUseCase @Inject constructor(
    private val usbDeviceRepository: UsbDeviceRepository
) {
    operator fun invoke(deviceId: String) {
        usbDeviceRepository.requestPermission(deviceId)
    }
}
```

- [ ] **Step 2: RefreshUsbDevicesUseCase 수정**

```kotlin
package com.inik.camcon.domain.usecase.usb

import com.inik.camcon.domain.model.UsbDeviceInfo
import com.inik.camcon.domain.repository.UsbDeviceRepository
import javax.inject.Inject

class RefreshUsbDevicesUseCase @Inject constructor(
    private val usbDeviceRepository: UsbDeviceRepository
) {
    operator fun invoke(): List<UsbDeviceInfo> {
        return usbDeviceRepository.getCameraDevices()
    }
}
```

- [ ] **Step 3: 호출부 수정 (presentation 레이어에서 UsbDevice → UsbDeviceInfo)**

presentation에서 `RefreshUsbDevicesUseCase`의 반환값이 `List<UsbDevice>` → `List<UsbDeviceInfo>`로 변경되므로, 호출부에서 `device.deviceId` 대신 `device.deviceId` (String), `device.vendorId` 등으로 접근하도록 수정. `RequestUsbPermissionUseCase`는 `UsbDevice` 대신 `String` (deviceId)를 받으므로 호출부도 수정.

Grep으로 호출부 찾기: `grep -rn "RequestUsbPermissionUseCase\|RefreshUsbDevicesUseCase\|requestUsbPermission\|refreshUsbDevices" app/src/main/java/com/inik/camcon/presentation/`

각 호출부에서:
- `requestUsbPermissionUseCase(device)` → `requestUsbPermissionUseCase(device.deviceId)` (UsbDeviceInfo의 deviceId)
- `refreshUsbDevicesUseCase()` 반환 타입을 `List<UsbDeviceInfo>`로 처리

- [ ] **Step 4: 빌드 확인 + Commit**

```bash
cd /Users/ini-k/CamCon && ./gradlew assembleDebug 2>&1 | tail -5
git add -A
git commit -m "refactor: remove data/android dependencies from USB UseCases"
```

---

### Task 12: 모델 정리 — SubscriptionProduct, ThemeMode, PtpTimeoutException, 중복 UseCase

**Files:**
- Modify: `app/src/main/java/com/inik/camcon/domain/model/SubscriptionProduct.kt`
- Move: `data/datasource/local/ThemeMode.kt` → `domain/model/ThemeMode.kt`
- Move: `data/repository/managers/PtpTimeoutException.kt` → `domain/model/PtpTimeoutException.kt`
- Delete: `app/src/main/java/com/inik/camcon/domain/usecase/CapturePhotoUseCase.kt` (루트의 중복본)

- [ ] **Step 1: SubscriptionProduct에서 Gson 제거**

```kotlin
package com.inik.camcon.domain.model

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

Gson `@SerializedName`이 런타임 직렬화에 사용되는지 확인. 사용되지 않으면 단순 제거. 사용된다면 data 레이어에 DTO를 만들어야 하지만, 이 모델은 Google Play Billing에서 직접 파싱하는 것이 아니라 앱 내 생성이므로 Gson 제거 가능.

- [ ] **Step 2: ThemeMode를 domain으로 이동**

1. `app/src/main/java/com/inik/camcon/domain/model/ThemeMode.kt` 파일 생성:
```kotlin
package com.inik.camcon.domain.model
enum class ThemeMode {
    FOLLOW_SYSTEM,
    LIGHT,
    DARK
}
```

2. `app/src/main/java/com/inik/camcon/data/datasource/local/ThemeMode.kt` 삭제

3. 모든 `import com.inik.camcon.data.datasource.local.ThemeMode` → `import com.inik.camcon.domain.model.ThemeMode`로 변경

Grep: `grep -rn "import com.inik.camcon.data.datasource.local.ThemeMode" app/src/main/java/`

- [ ] **Step 3: PtpTimeoutException을 domain으로 이동**

1. `app/src/main/java/com/inik/camcon/domain/model/PtpTimeoutException.kt` 파일 생성:
```kotlin
package com.inik.camcon.domain.model
class PtpTimeoutException(message: String) : RuntimeException(message)
```

2. `app/src/main/java/com/inik/camcon/data/repository/managers/PtpTimeoutException.kt` 삭제

3. 모든 import 경로 변경: `grep -rn "import com.inik.camcon.data.repository.managers.PtpTimeoutException" app/src/main/java/`

- [ ] **Step 4: 중복 CapturePhotoUseCase 삭제**

`app/src/main/java/com/inik/camcon/domain/usecase/CapturePhotoUseCase.kt` (루트) 삭제. `app/src/main/java/com/inik/camcon/domain/usecase/camera/CapturePhotoUseCase.kt` 유지.

이 파일을 참조하는 곳이 없는지 확인: `grep -rn "import com.inik.camcon.domain.usecase.CapturePhotoUseCase" app/src/main/java/`
(camera 하위 패키지의 CapturePhotoUseCase와 구분)

- [ ] **Step 5: 빌드 확인 + Commit**

```bash
cd /Users/ini-k/CamCon && ./gradlew assembleDebug 2>&1 | tail -5
git add -A
git commit -m "refactor: clean up domain models - remove Gson, move ThemeMode/PtpTimeoutException, remove duplicate UseCase"
```

---

### Task 13: CameraSettingsManager에서 Log → Logger 교체

**Files:**
- Modify: `app/src/main/java/com/inik/camcon/domain/manager/CameraSettingsManager.kt`

- [ ] **Step 1: 생성자에 Logger 추가, Log import 제거**

- `import android.util.Log` 제거
- `import com.inik.camcon.domain.util.Logger` 추가
- 생성자에 `private val logger: Logger` 파라미터 추가
- 모든 `Log.d(TAG, ...)` → `logger.d(TAG, ...)`
- 모든 `Log.e(TAG, ...)` → `logger.e(TAG, ...)`

- [ ] **Step 2: 빌드 확인 + Commit**

```bash
cd /Users/ini-k/CamCon && ./gradlew assembleDebug 2>&1 | tail -5
git add app/src/main/java/com/inik/camcon/domain/manager/CameraSettingsManager.kt
git commit -m "refactor: replace android.util.Log with Logger in CameraSettingsManager"
```

---

### Task 14: 나머지 domain UseCase에서 Log → Logger 교체

**Files:**
- Modify: `app/src/main/java/com/inik/camcon/domain/usecase/ValidateImageFormatUseCase.kt`
- Modify: `app/src/main/java/com/inik/camcon/domain/usecase/GetSubscriptionUseCase.kt`
- Modify: `app/src/main/java/com/inik/camcon/domain/usecase/auth/AdminUserManagementUseCase.kt`
- Modify: `app/src/main/java/com/inik/camcon/domain/usecase/auth/UserReferralUseCase.kt`

- [ ] **Step 1: 각 파일에서 동일한 패턴 적용**

각 파일에 대해:
1. `import android.util.Log` 제거
2. `import com.inik.camcon.domain.util.Logger` 추가
3. 생성자에 `private val logger: Logger` 파라미터 추가
4. 모든 `Log.d(TAG, ...)` → `logger.d(TAG, ...)`
5. 모든 `Log.e(TAG, ...)` → `logger.e(TAG, ...)`
6. 모든 `Log.w(TAG, ...)` → `logger.w(TAG, ...)`
7. 모든 `Log.i(TAG, ...)` → `logger.i(TAG, ...)`

- [ ] **Step 2: 빌드 확인 + Commit**

```bash
cd /Users/ini-k/CamCon && ./gradlew assembleDebug 2>&1 | tail -5
git add -A
git commit -m "refactor: replace android.util.Log with Logger in all domain UseCases"
```

---

### Task 15: domain 순수성 최종 검증

**Files:** (수정 없음 — 검증만)

- [ ] **Step 1: domain에서 android.* import 검색**

```bash
grep -rn "import android\." app/src/main/java/com/inik/camcon/domain/
```

Expected: 0 결과. 만약 결과가 있으면 해당 파일 수정.

- [ ] **Step 2: domain에서 data 직접 참조 검색**

```bash
grep -rn "import com.inik.camcon.data\." app/src/main/java/com/inik/camcon/domain/
```

Expected: 0 결과. ColorTransferUseCase는 이 시점에서 아직 data/processor를 참조할 수 있음 — Task 16에서 처리.

- [ ] **Step 3: domain에서 외부 라이브러리 참조 검색**

```bash
grep -rn "import com.google.gson\." app/src/main/java/com/inik/camcon/domain/
```

Expected: 0 결과.

- [ ] **Step 4: domain에서 CameraNative 참조 검색**

```bash
grep -rn "import com.inik.camcon.CameraNative\|import com.inik.camcon.NativeErrorCallback" app/src/main/java/com/inik/camcon/domain/
```

Expected: 0 결과.

- [ ] **Step 5: 전체 빌드 + 테스트**

```bash
cd /Users/ini-k/CamCon && ./gradlew assembleDebug 2>&1 | tail -5
cd /Users/ini-k/CamCon && ./gradlew test 2>&1 | tail -10
```

Expected: 둘 다 성공.

- [ ] **Step 6: 결과 커밋 (필요 시)**

잔여 위반이 발견되어 수정한 경우에만:
```bash
git add -A
git commit -m "fix: resolve remaining domain layer violations"
```

---

### Task 16: ColorTransferUseCase 경량화 (선택 — domain -> data 역참조 제거)

**Note:** ColorTransferUseCase는 538줄로 대규모이며 Android Bitmap API에 깊이 의존. 이 Task는 별도 브랜치에서 진행하는 것을 권장. 핵심 로직을 `data/repository/ColorTransferRepositoryImpl.kt`로 이동하고, domain의 UseCase는 Repository 인터페이스를 통해 호출하는 얇은 래퍼로 변환.

**Files:**
- Create: `app/src/main/java/com/inik/camcon/data/repository/ColorTransferRepositoryImpl.kt`
- Modify: `app/src/main/java/com/inik/camcon/domain/usecase/ColorTransferUseCase.kt` (538줄 → ~30줄)
- Modify: `app/src/main/java/com/inik/camcon/di/RepositoryModule.kt`

- [ ] **Step 1: ColorTransferRepositoryImpl 생성**

기존 `ColorTransferUseCase`의 구현 로직(Bitmap 처리, EXIF 복사, GPU 초기화 등)을 `data/repository/ColorTransferRepositoryImpl.kt`로 이동. `ColorTransferRepository` 인터페이스 구현.

- [ ] **Step 2: ColorTransferUseCase를 얇은 래퍼로 변환**

```kotlin
package com.inik.camcon.domain.usecase

import com.inik.camcon.domain.model.ColorTransferResult
import com.inik.camcon.domain.repository.ColorTransferRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ColorTransferUseCase @Inject constructor(
    private val colorTransferRepository: ColorTransferRepository
) {
    suspend fun applyColorTransfer(
        inputImagePath: String,
        referenceImagePath: String,
        originalImagePath: String? = null,
        intensity: Float = 1.0f
    ): Result<ColorTransferResult> =
        colorTransferRepository.applyColorTransfer(inputImagePath, referenceImagePath, originalImagePath, intensity)

    suspend fun initializeProcessor(contextProvider: Any) =
        colorTransferRepository.initializeProcessor(contextProvider)

    fun releaseProcessor() = colorTransferRepository.releaseProcessor()
}
```

- [ ] **Step 3: DI 바인딩 추가**

RepositoryModule에:
```kotlin
    @Binds
    @Singleton
    abstract fun bindColorTransferRepository(
        impl: ColorTransferRepositoryImpl
    ): ColorTransferRepository
```

- [ ] **Step 4: 호출부 수정**

ColorTransferSettingsActivity 등에서 UseCase의 시그니처가 변경된 부분 수정.

- [ ] **Step 5: 빌드 확인 + Commit**

```bash
cd /Users/ini-k/CamCon && ./gradlew assembleDebug 2>&1 | tail -5
git add -A
git commit -m "refactor: move ColorTransferUseCase implementation to data layer"
```
