# Phase 2: 코루틴/안정성 정리 설계

## 목표

비구조화 코루틴(`CoroutineScope(Dispatchers.IO).launch`)을 MVVM Clean Architecture 원칙에 맞게 구조화하고, JNI 경계 안전성을 확보한다.

## MVVM Clean Architecture 코루틴 원칙

```
Presentation (ViewModel)
  └─ viewModelScope.launch { }       ← lifecycle 바인딩
       └─ useCase()                   ← suspend fun, 호출자 scope 따름
            └─ repository.method()    ← suspend fun
                 └─ withContext(IO) { } ← 내부에서 스레드 전환

Compose UI
  └─ collectAsStateWithLifecycle()    ← lifecycle 인식 수집
```

**금지 패턴:** `CoroutineScope(Dispatchers.IO).launch` (비구조화, 취소 불가)
**허용 예외:** `@Singleton` 클래스가 자체 모니터링 루프를 돌릴 때 → 클래스 레벨 `scope` + `cleanup()`에서 `scope.cancel()`

## Group A: 코루틴 스코프 관리

### A-1: CameraSettingsManager — `CoroutineScope(IO).launch` 4회

**현재:** 각 메서드에서 `CoroutineScope(Dispatchers.IO).launch { ... }`
**수정:** 메서드를 `suspend fun`으로 변환. 호출자(ViewModel)가 `viewModelScope`에서 호출.

```kotlin
// Before
fun loadCameraSettings(cameraId: String? = null) {
    CoroutineScope(Dispatchers.IO).launch { ... }
}

// After
suspend fun loadCameraSettings(cameraId: String? = null) {
    withContext(Dispatchers.IO) { ... }
}
```

호출부(ViewModel)에서: `viewModelScope.launch { cameraSettingsManager.loadCameraSettings() }`

### A-2: CameraOperationsManager — `CoroutineScope(IO).launch` 5회+

동일 패턴 적용. 모든 public 메서드를 `suspend fun`으로 변환.

### A-3: CameraConnectionManager (presentation) — `CoroutineScope(IO).launch` 7회+

동일 패턴 적용. 이 클래스는 presentation helper이므로 `viewModelScope`를 생성자로 받거나, 메서드를 `suspend fun`으로 변환.

### A-4: UsbCameraManager — `CoroutineScope(IO).launch` 6회+

`@Singleton` data 레이어 클래스. 클래스 레벨 `scope` 사용하고 `cleanup()`에서 `scope.cancel()` 호출.

### A-5: CameraRepositoryImpl — `CoroutineScope(IO).launch` 3회

suspend fun으로 변환하거나, Repository 내부 scope를 관리.

### A-6: collectAsState → collectAsStateWithLifecycle

**의존성 추가:** `implementation "androidx.lifecycle:lifecycle-runtime-compose:2.7.0"`

전체 Compose 화면에서 `collectAsState()` → `collectAsStateWithLifecycle()` 일괄 교체. import 변경: `androidx.compose.runtime.collectAsState` → `androidx.lifecycle.compose.collectAsStateWithLifecycle`

## Group B: JNI/Native 안전성

### B-1: NativeCameraDataSource — runBlocking 제거

```kotlin
// Before
fun initCameraWithFd(fd: Int): Boolean {
    return runBlocking { mutex.withLock { ... } }
}

// After
suspend fun initCameraWithFd(fd: Int): Boolean {
    return mutex.withLock { withContext(Dispatchers.IO) { ... } }
}
```

호출부도 `suspend` 컨텍스트에서 호출하도록 변경.

### B-2: NativeCameraDataSource — Thread.sleep → delay

```kotlin
// Before
Thread { Thread.sleep(1000); isListening.set(false) }.start()

// After (클래스 scope 사용)
scope.launch { delay(1000); isListening.set(false) }
```

### B-3: CameraRepositoryImpl — continuation 다중 resume 방지

```kotlin
// Before
suspendCancellableCoroutine { continuation ->
    onPhotoCaptured = { continuation.resume(it) }
    onCaptureFailed = { continuation.resume(Result.failure(it)) }
}

// After
suspendCancellableCoroutine { continuation ->
    onPhotoCaptured = {
        if (continuation.isActive) continuation.resume(it)
    }
    onCaptureFailed = {
        if (continuation.isActive) continuation.resume(Result.failure(it))
    }
}
```

## Group C: 기타 안정성

### C-1: CameraViewModel — Activity 참조 제거

`onCleared()`에서 `currentActivity = null` 추가. 가능하면 Activity 참조 자체를 제거하고 대안(Application Context 등) 사용.

### C-2: PtpipDataSource — 재귀 재연결 → 루프 변환

```kotlin
// Before (재귀)
private suspend fun attemptAutoReconnect() {
    delay(5000)
    connect()
    if (failed) attemptAutoReconnect() // 재귀
}

// After (루프)
private suspend fun attemptAutoReconnect() {
    var attempts = 0
    while (attempts < MAX_RECONNECT_ATTEMPTS && isActive) {
        delay(5000)
        if (connect()) return
        attempts++
    }
}
```

### C-3: UsbConnectionManager — 빈 catch 블록에 로깅 추가

```kotlin
// Before
catch (_: Exception) { }

// After
catch (e: Exception) { Log.w(TAG, "USB 연결 해제 중 오류", e) }
```

### C-4: CameraConnectionGlobalManager — scope.cancel() 추가

`cleanup()`에서 `scope.cancel()` 호출 추가.

## 수정하지 않는 것

- BackgroundSyncService (기능 구현이 없으므로 Phase 6에서 제거/구현 결정)
- `StateFlow<Map<String, ByteArray>>` 이미지 캐시 (Phase 6 메모리 관리에서)
- @Singleton 과다 사용 (scope 변경과 동시에 하면 위험 — 별도 Phase)

## 완료 기준

- [ ] domain에 `CoroutineScope(Dispatchers.IO).launch` 0건
- [ ] presentation helper에 `CoroutineScope(Dispatchers.IO).launch` 0건
- [ ] data에서 비구조화 코루틴 → 클래스 scope 또는 suspend 변환
- [ ] `runBlocking` 0건 (NativeCameraDataSource)
- [ ] `Thread.sleep` 0건 (NativeCameraDataSource)
- [ ] continuation `isActive` 체크 추가
- [ ] `collectAsStateWithLifecycle` 전면 도입
- [ ] 빌드 성공
