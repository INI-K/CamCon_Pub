# Phase 2: Coroutine/Stability Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 비구조화 `CoroutineScope(Dispatchers.IO).launch` 44건을 MVVM Clean Architecture에 맞게 구조화하고, JNI 안전성 확보

**Architecture:** Domain 메서드는 suspend fun으로 변환하여 호출자 scope를 따름. Presentation helper는 CoroutineScope를 생성자로 주입받음. Data @Singleton은 클래스 레벨 scope + cleanup(). Compose는 collectAsStateWithLifecycle.

**Tech Stack:** Kotlin Coroutines, Hilt, Jetpack Compose Lifecycle

---

### Task 1: CameraSettingsManager — suspend fun 변환

**Files:**
- Modify: `app/src/main/java/com/inik/camcon/domain/manager/CameraSettingsManager.kt`

- [ ] **Step 1: 4개 메서드를 suspend fun으로 변환**

`loadCameraSettings`, `loadCameraCapabilities`, `updateCameraSetting`, `updateCameraSettings` 각각에서:

1. `fun` → `suspend fun`
2. `CoroutineScope(Dispatchers.IO).launch { ... }` 래핑 제거
3. 내부 로직을 `withContext(Dispatchers.IO) { ... }`로 감싸기

예시 패턴:
```kotlin
// Before
fun loadCameraSettings(cameraId: String? = null) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            _isLoadingSettings.value = true
            // ... logic ...
        } finally {
            _isLoadingSettings.value = false
        }
    }
}

// After
suspend fun loadCameraSettings(cameraId: String? = null) {
    withContext(Dispatchers.IO) {
        try {
            _isLoadingSettings.value = true
            // ... logic unchanged ...
        } finally {
            _isLoadingSettings.value = false
        }
    }
}
```

import 변경: `import kotlinx.coroutines.CoroutineScope` 제거, `import kotlinx.coroutines.withContext` 추가 (launch도 더 이상 불필요하면 제거)

- [ ] **Step 2: 호출부 수정**

CameraSettingsManager의 메서드를 호출하는 곳을 찾아서 (`grep -rn "cameraSettingsManager\.\(load\|update\)" app/src/main/java/`) 이미 코루틴 컨텍스트에서 호출하는지 확인. 코루틴 밖에서 호출하는 곳이 있으면 `viewModelScope.launch { }` 로 감싸기.

- [ ] **Step 3: 빌드 확인 + Commit**

```bash
JAVA_HOME=/Users/ini-k/Library/Java/JavaVirtualMachines/jbr-21.0.9/Contents/Home ./gradlew compileDebugKotlin 2>&1 | tail -5
git add -A && git commit -m "refactor: convert CameraSettingsManager methods to suspend fun"
```

---

### Task 2: Presentation Helpers — scope 주입 (CameraConnectionManager + CameraOperationsManager)

**Files:**
- Modify: `app/src/main/java/com/inik/camcon/presentation/viewmodel/CameraConnectionManager.kt` (7건)
- Modify: `app/src/main/java/com/inik/camcon/presentation/viewmodel/CameraOperationsManager.kt` (5건)

- [ ] **Step 1: 각 클래스에 CoroutineScope를 생성자로 주입**

이 두 클래스는 presentation helper (`@Singleton`)이지만 ViewModel과 함께 사용됨. 패턴:

```kotlin
// Before
@Singleton
class CameraConnectionManager @Inject constructor(...) {
    // 각 메서드에서
    connectionJob = CoroutineScope(Dispatchers.IO).launch { ... }
}

// After
@Singleton
class CameraConnectionManager @Inject constructor(...) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 각 메서드에서
    connectionJob = scope.launch { ... }

    fun cleanup() {
        scope.cancel()
    }
}
```

`@Singleton` 클래스이므로 viewModelScope를 주입받기 어려움 — 대신 클래스 레벨 managed scope를 사용하고 `cleanup()`에서 `scope.cancel()` 호출.

모든 `CoroutineScope(Dispatchers.IO).launch` → `scope.launch` 교체. `CoroutineScope(Dispatchers.Main).launch` → `withContext(Dispatchers.Main)` 또는 `scope.launch(Dispatchers.Main)`.

- [ ] **Step 2: CameraOperationsManager에 동일 패턴 적용**

동일하게 클래스 레벨 scope + cleanup(). `liveViewJob`, `timelapseJob` 등 기존 Job 변수는 유지하되 새 scope에서 launch.

- [ ] **Step 3: cleanup() 호출 확인**

`grep -rn "\.cleanup()" app/src/main/java/com/inik/camcon/presentation/viewmodel/CameraViewModel.kt` 에서 두 Manager의 cleanup이 `onCleared()`에서 호출되는지 확인. 안 되면 추가.

- [ ] **Step 4: 빌드 확인 + Commit**

```bash
JAVA_HOME=/Users/ini-k/Library/Java/JavaVirtualMachines/jbr-21.0.9/Contents/Home ./gradlew compileDebugKotlin 2>&1 | tail -5
git add -A && git commit -m "refactor: use managed scope in CameraConnectionManager and CameraOperationsManager"
```

---

### Task 3: Photo Managers — scope 관리 (PhotoImageManager + PhotoListManager)

**Files:**
- Modify: `app/src/main/java/com/inik/camcon/presentation/viewmodel/photo/PhotoImageManager.kt` (3건)
- Modify: `app/src/main/java/com/inik/camcon/presentation/viewmodel/photo/PhotoListManager.kt` (3건)

- [ ] **Step 1: PhotoImageManager — 클래스 레벨 scope 추가**

Task 2와 동일 패턴: 클래스 레벨 `scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)`, 모든 `CoroutineScope(Dispatchers.IO).launch` → `scope.launch`, cleanup()에 `scope.cancel()` 추가.

- [ ] **Step 2: PhotoListManager — 동일 패턴 적용**

- [ ] **Step 3: 빌드 확인 + Commit**

```bash
JAVA_HOME=/Users/ini-k/Library/Java/JavaVirtualMachines/jbr-21.0.9/Contents/Home ./gradlew compileDebugKotlin 2>&1 | tail -5
git add -A && git commit -m "refactor: use managed scope in PhotoImageManager and PhotoListManager"
```

---

### Task 4: ImageProcessingUtils + FullScreenPhotoViewer — Compose 패턴 적용

**Files:**
- Modify: `app/src/main/java/com/inik/camcon/presentation/ui/screens/components/ImageProcessingUtils.kt` (~15건)
- Modify: `app/src/main/java/com/inik/camcon/presentation/ui/screens/components/FullScreenPhotoViewer.kt` (1건)

- [ ] **Step 1: ImageProcessingUtils 분석**

이 파일은 유틸리티 함수들이 `CoroutineScope(IO).launch` + `CoroutineScope(Main).launch` 패턴을 사용. Compose Composable 내에서 호출되는 함수들이므로 `LaunchedEffect` 또는 `rememberCoroutineScope()`를 사용해야 함.

패턴: 함수가 콜백 기반이면 `scope: CoroutineScope` 파라미터를 추가하여 호출자(Composable의 rememberCoroutineScope)에서 전달. `CoroutineScope(Main).launch` → `withContext(Dispatchers.Main)`.

```kotlin
// Before
fun processImage(bitmap: Bitmap, onResult: (Bitmap) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
        val result = /* process */
        CoroutineScope(Dispatchers.Main).launch {
            onResult(result)
        }
    }
}

// After
fun processImage(scope: CoroutineScope, bitmap: Bitmap, onResult: (Bitmap) -> Unit) {
    scope.launch(Dispatchers.IO) {
        val result = /* process */
        withContext(Dispatchers.Main) {
            onResult(result)
        }
    }
}
```

또는 suspend fun으로 변환이 가능하면:
```kotlin
suspend fun processImage(bitmap: Bitmap): Bitmap = withContext(Dispatchers.IO) {
    /* process and return */
}
```

호출부(Composable)에서는 `val scope = rememberCoroutineScope()` 사용.

- [ ] **Step 2: FullScreenPhotoViewer — LaunchedEffect로 변환**

```kotlin
// Before (line 94)
CoroutineScope(Dispatchers.IO).launch { ... }

// After
LaunchedEffect(key) {
    withContext(Dispatchers.IO) { ... }
}
```

- [ ] **Step 3: 호출부 수정**

ImageProcessingUtils의 함수 시그니처가 변경되면 호출부도 수정. `grep -rn "ImageProcessingUtils\|processImage\|loadAndProcess" app/src/main/java/com/inik/camcon/presentation/`

- [ ] **Step 4: 빌드 확인 + Commit**

```bash
JAVA_HOME=/Users/ini-k/Library/Java/JavaVirtualMachines/jbr-21.0.9/Contents/Home ./gradlew compileDebugKotlin 2>&1 | tail -5
git add -A && git commit -m "refactor: use Compose coroutine patterns in ImageProcessingUtils and FullScreenPhotoViewer"
```

---

### Task 5: Data 레이어 — CameraRepositoryImpl scope 관리 + continuation 안전성

**Files:**
- Modify: `app/src/main/java/com/inik/camcon/data/repository/CameraRepositoryImpl.kt`

- [ ] **Step 1: 비구조화 CoroutineScope 제거**

3건의 `CoroutineScope(Dispatchers.IO).launch` / `CoroutineScope(Dispatchers.Main).launch`:
- 줄 245: suspend 컨텍스트에서 호출 가능하면 `withContext(Dispatchers.IO)` 로 변환
- 줄 568, 597: 동일 패턴 적용. 또는 클래스 레벨 scope 사용.

CameraRepositoryImpl은 이미 `@Singleton @Inject constructor`이므로 클래스 레벨 scope를 추가:
```kotlin
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
```
cleanup 메서드에서 `scope.cancel()`.

- [ ] **Step 2: continuation 다중 resume 방지**

줄 204, 261, 266, 271, 278의 모든 `continuation.resume` / `continuation.resumeWithException` 호출에 `isActive` 체크 추가:

```kotlin
// Before
continuation.resume(Result.success(photo))

// After
if (continuation.isActive) {
    continuation.resume(Result.success(photo))
}
```

모든 5개 resume 지점에 적용.

- [ ] **Step 3: 빌드 확인 + Commit**

```bash
JAVA_HOME=/Users/ini-k/Library/Java/JavaVirtualMachines/jbr-21.0.9/Contents/Home ./gradlew compileDebugKotlin 2>&1 | tail -5
git add -A && git commit -m "refactor: manage scope and fix continuation safety in CameraRepositoryImpl"
```

---

### Task 6: NativeCameraDataSource — runBlocking/Thread.sleep 제거

**Files:**
- Modify: `app/src/main/java/com/inik/camcon/data/datasource/nativesource/NativeCameraDataSource.kt`

- [ ] **Step 1: runBlocking → suspend fun**

줄 82:
```kotlin
// Before
fun initCameraWithFd(fd: Int, nativeLibDir: String): Int = runBlocking {
    mutex.withLock { ... }
}

// After
suspend fun initCameraWithFd(fd: Int, nativeLibDir: String): Int {
    return mutex.withLock {
        withContext(Dispatchers.IO) { ... }
    }
}
```

호출부에서 이 메서드를 호출하는 곳을 찾아 이미 suspend 컨텍스트인지 확인:
`grep -rn "initCameraWithFd" app/src/main/java/com/inik/camcon/`
호출부가 suspend가 아니면 같이 수정.

- [ ] **Step 2: Thread.sleep → delay**

NativeCameraDataSource에 클래스 레벨 scope 추가 (이미 있으면 확인):
```kotlin
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
```

줄 57, 201, 283의 `Thread { Thread.sleep(N); ... }.start()` 패턴을:
```kotlin
scope.launch {
    delay(N)
    // ... 기존 로직 ...
}
```
으로 변환.

- [ ] **Step 3: cleanup에서 scope.cancel() 확인**

`closeCamera()` 또는 cleanup 계열 메서드에서 `scope.cancel()` 호출 추가.

- [ ] **Step 4: 빌드 확인 + Commit**

```bash
JAVA_HOME=/Users/ini-k/Library/Java/JavaVirtualMachines/jbr-21.0.9/Contents/Home ./gradlew compileDebugKotlin 2>&1 | tail -5
git add -A && git commit -m "refactor: remove runBlocking and Thread.sleep from NativeCameraDataSource"
```

---

### Task 7: collectAsStateWithLifecycle 전면 도입

**Files:**
- Modify: `app/build.gradle` (의존성 추가)
- Modify: 모든 Compose Screen 파일 (76건)

- [ ] **Step 1: lifecycle-runtime-compose 의존성 추가**

`app/build.gradle`의 dependencies에:
```groovy
implementation "androidx.lifecycle:lifecycle-runtime-compose:2.7.0"
```

- [ ] **Step 2: 전체 일괄 교체**

```bash
# import 교체
find app/src/main/java -name "*.kt" -exec sed -i '' 's/import androidx.compose.runtime.collectAsState/import androidx.lifecycle.compose.collectAsStateWithLifecycle/g' {} +

# 함수 호출 교체
find app/src/main/java -name "*.kt" -exec sed -i '' 's/\.collectAsState()/.collectAsStateWithLifecycle()/g' {} +
```

**주의:** `collectAsState(initial = ...)` 패턴도 있을 수 있음. 그 경우:
```bash
find app/src/main/java -name "*.kt" -exec sed -i '' 's/\.collectAsState(initial/\.collectAsStateWithLifecycle(initialValue/g' {} +
```

`collectAsStateWithLifecycle`은 `initial` 대신 `initialValue` 파라미터를 사용.

- [ ] **Step 3: 빌드 확인**

빌드 실패 시 개별 파일 확인. `collectAsState`와 `collectAsStateWithLifecycle`은 파라미터명이 다름:
- `collectAsState(initial = X)` → `collectAsStateWithLifecycle(initialValue = X)`

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "refactor: migrate collectAsState to collectAsStateWithLifecycle"
```

---

### Task 8: 기타 안정성 수정

**Files:**
- Modify: `app/src/main/java/com/inik/camcon/presentation/viewmodel/CameraViewModel.kt` (Activity 참조)
- Modify: `app/src/main/java/com/inik/camcon/data/datasource/ptpip/PtpipDataSource.kt` (재귀 재연결)
- Modify: `app/src/main/java/com/inik/camcon/data/datasource/usb/UsbConnectionManager.kt` (빈 catch)
- Modify: `app/src/main/java/com/inik/camcon/domain/manager/CameraConnectionGlobalManager.kt` (scope.cancel)

- [ ] **Step 1: CameraViewModel — Activity 참조 안전성**

`onCleared()`에 `currentActivity = null` 추가:
```kotlin
override fun onCleared() {
    super.onCleared()
    currentActivity = null
    // ... 기존 cleanup ...
}
```

- [ ] **Step 2: PtpipDataSource — 재귀 → 루프**

`attemptAutoReconnect()` 메서드를 찾아서 재귀 호출 → while 루프 변환:
```kotlin
private suspend fun attemptAutoReconnect() {
    var attempts = 0
    val maxAttempts = 5
    while (attempts < maxAttempts && isActive) {
        delay(5000)
        try {
            if (connectToCamera()) return
        } catch (e: Exception) {
            // log
        }
        attempts++
    }
}
```

- [ ] **Step 3: UsbConnectionManager — 빈 catch에 로깅 추가**

```kotlin
// Before
catch (_: Exception) { }

// After
catch (e: Exception) {
    Log.w(TAG, "USB disconnect error", e)
}
```

- [ ] **Step 4: CameraConnectionGlobalManager — cleanup에 scope.cancel() 추가**

```kotlin
fun cleanup() {
    scope.cancel()
    logger.d(TAG, "리소스 정리 시작")
}
```

- [ ] **Step 5: 빌드 확인 + Commit**

```bash
JAVA_HOME=/Users/ini-k/Library/Java/JavaVirtualMachines/jbr-21.0.9/Contents/Home ./gradlew compileDebugKotlin 2>&1 | tail -5
git add -A && git commit -m "fix: Activity leak, recursive reconnect, empty catches, scope cleanup"
```

---

### Task 9: 최종 검증

- [ ] **Step 1: 비구조화 코루틴 잔여 확인**

```bash
grep -rn "CoroutineScope(Dispatchers" app/src/main/java/com/inik/camcon/domain/ --include="*.kt"
grep -rn "CoroutineScope(Dispatchers" app/src/main/java/com/inik/camcon/presentation/viewmodel/ --include="*.kt"
```

domain: 0건 기대. presentation viewmodel: 0건 기대 (managed scope 변수 선언 제외).

- [ ] **Step 2: runBlocking/Thread.sleep 잔여 확인**

```bash
grep -rn "runBlocking\|Thread.sleep" app/src/main/java/com/inik/camcon/ --include="*.kt"
```

0건 기대.

- [ ] **Step 3: collectAsState 잔여 확인**

```bash
grep -rn "\.collectAsState()" app/src/main/java/com/inik/camcon/ --include="*.kt"
```

0건 기대.

- [ ] **Step 4: 전체 빌드**

```bash
JAVA_HOME=/Users/ini-k/Library/Java/JavaVirtualMachines/jbr-21.0.9/Contents/Home ./gradlew compileDebugKotlin 2>&1 | tail -5
```

BUILD SUCCESSFUL 기대.
