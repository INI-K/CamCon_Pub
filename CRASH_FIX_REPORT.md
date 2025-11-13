# 네이티브 카메라 이벤트 리스너 크래시 수정 보고서

## 문제 증상

```
16:16:49.556  A  Cmdline: com.inik.camcon
16:16:49.556  A  pid: 7464, tid: 8564, name: Thread-39  >>> com.inik.camcon <<<
16:16:49.556  A  #00 pc 000000000001eb80  libgphoto2.so (gp_camera_wait_for_event+124)
16:16:49.556  A  #01 pc 00000000000e49fc  libnative-lib.so (waitAndProcessCameraEvents+236)
```

앱이 `gp_camera_wait_for_event` 함수에서 크래시가 발생하며 종료됨.

## 근본 원인 분석

### 1. 타이밍 문제

- 카메라 정리(`closeCamera`)와 이벤트 리스너(`listenCameraEvents`)가 동시에 실행
- 네이티브 레벨에서 카메라 리소스가 정리되는 동안 이벤트를 기다리려고 시도
- `gp_camera_wait_for_event`가 이미 해제된 카메라 객체에 접근

### 2. 백그라운드 서비스에서 코루틴 취소 예외 미처리

```kotlin
16:16:49.331  W  백그라운드 동기화 작업 중 오류
                 kotlinx.coroutines.JobCancellationException: StandaloneCoroutine was cancelled
```

- `CancellationException`을 일반 예외로 처리하여 오류로 로깅
- 실제로는 정상 종료이지만 예외 스택 트레이스가 쌓임

### 3. 상태 검증 부족

- 이벤트 리스너 시작 시 카메라 초기화 및 연결 상태를 충분히 검증하지 않음
- 네이티브 카메라가 초기화되지 않은 상태에서 이벤트 리스닝 시도

## 해결 방법

### 1. CameraEventManager 안전성 강화

#### 1.1 다단계 상태 검증 추가

```kotlin
// 추가 안전성 검증: 네이티브 카메라가 초기화되어 있는지 확인
if (!CameraNative.isCameraInitialized()) {
    LogcatManager.e("카메라이벤트매니저", "네이티브 카메라가 초기화되지 않음 - 이벤트 리스너 시작 중단")
    isEventListenerRunning.set(false)
    return@launch
}

// 네이티브 이벤트 리스너 시작 전 마지막 검증
if (!CameraNative.isCameraConnected()) {
    LogcatManager.e("카메라이벤트매니저", "네이티브 카메라 연결이 끊어짐 - 이벤트 리스너 시작 중단")
    isEventListenerRunning.set(false)
    break
}
```

#### 1.2 CancellationException 정상 처리

```kotlin
} catch (e: kotlinx.coroutines.CancellationException) {
    // 코루틴 취소는 정상적인 종료이므로 로그만 남김
    LogcatManager.d("카메라이벤트매니저", "이벤트 리스너 코루틴이 취소됨")
    isEventListenerRunning.set(false)
    CoroutineScope(Dispatchers.Main).launch {
        _isEventListenerActive.value = false
    }
}
```

#### 1.3 안정화 대기 시간 유지

```kotlin
// 안정화를 위한 추가 대기 시간
kotlinx.coroutines.delay(500)
```

#### 1.4 루프 조건에 실행 상태 추가

```kotlin
while (retryCount < maxRetries && isConnected && isEventListenerRunning.get()) {
    // 실행 중이 아니면 즉시 중단
}
```

### 2. BackgroundSyncService 안전성 강화

#### 2.1 백그라운드 동기화 작업 예외 처리

```kotlin
private fun startBackgroundSync() {
    syncJob?.cancel()
    
    syncJob = serviceScope?.launch {
        try {
            while (true) {
                try {
                    // 동기화 작업
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // 코루틴 취소는 정상 종료
                    LogcatManager.d(TAG, "백그라운드 동기화 작업이 정상적으로 취소됨")
                    throw e // CancellationException은 다시 throw해야 함
                } catch (e: Exception) {
                    LogcatManager.w(TAG, "백그라운드 동기화 작업 중 오류", e)
                    delay(60_000L)
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            LogcatManager.d(TAG, "백그라운드 동기화 작업 종료")
        } catch (e: Exception) {
            LogcatManager.e(TAG, "백그라운드 동기화 작업 중 치명적 오류", e)
        }
    }
}
```

#### 2.2 이벤트 리스너 관리자 예외 처리

```kotlin
private fun startBackgroundEventListenerManager() {
    eventListenerJob?.cancel()
    
    eventListenerJob = serviceScope?.launch {
        try {
            globalConnectionManager.globalConnectionState.collect { state ->
                // 이벤트 리스너 관리
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            LogcatManager.d(TAG, "백그라운드 이벤트 리스너 관리자가 정상적으로 종료됨")
        } catch (e: Exception) {
            LogcatManager.e(TAG, "백그라운드 이벤트 리스너 관리자 중 치명적 오류", e)
        }
    }
}
```

## 적용된 개선 사항

### 1. 카메라 상태 검증 강화

- ✅ 이벤트 리스너 시작 전 카메라 초기화 상태 확인
- ✅ 이벤트 리스너 시작 전 카메라 연결 상태 확인
- ✅ 안정화 대기 시간 후 상태 재확인
- ✅ 루프 조건에 실행 상태 플래그 추가

### 2. 예외 처리 개선

- ✅ `CancellationException`을 정상 종료로 처리
- ✅ `IllegalStateException` 별도 처리 (재시도하지 않음)
- ✅ 백그라운드 서비스의 모든 코루틴에 `CancellationException` 처리 추가

### 3. 중복 실행 방지

- ✅ `AtomicBoolean`을 사용한 중복 시작/중지 방지
- ✅ `compareAndSet`을 통한 원자적 연산

### 4. 완전한 정리 메커니즘

- ✅ `performCompleteCleanup()` 메서드로 일관된 정리
- ✅ 여러 번 시도하는 중지 로직 (최대 3회)
- ✅ 정리 후 상태 플래그 리셋

## 예상 효과

1. **크래시 방지**: 네이티브 카메라 상태 검증을 통해 `gp_camera_wait_for_event` 크래시 방지
2. **안정성 향상**: 코루틴 취소를 정상 처리하여 불필요한 오류 로그 제거
3. **타이밍 문제 해결**: 안정화 대기 시간과 상태 재확인으로 리소스 경합 방지
4. **중복 실행 방지**: 원자적 연산으로 동시 실행 문제 해결

## 테스트 시나리오

### 1. 정상 시나리오

- [x] 카메라 연결 → 이벤트 리스너 시작 → 사진 촬영 → 다운로드 성공

### 2. 비정상 시나리오

- [ ] 카메라 연결 중 앱 종료 → 크래시 없이 정상 종료
- [ ] 이벤트 리스너 실행 중 USB 분리 → 정리 후 정상 종료
- [ ] 백그라운드에서 카메라 연결 해제 → 서비스 정리 후 대기 모드

### 3. 엣지 케이스

- [ ] 빠른 연결/해제 반복 → 중복 실행 없이 정상 동작
- [ ] 카메라 초기화 전 이벤트 리스너 시작 시도 → 중단 후 오류 메시지
- [ ] 네트워크 연결 불안정 (PTPIP) → 재시도 로직 동작

## 추가 권장 사항

### 네이티브 레벨 개선

1. **타임아웃 추가**: `gp_camera_wait_for_event`에 타임아웃 설정
2. **취소 플래그**: 네이티브 코드에 취소 플래그 추가하여 즉시 중단 가능하도록 개선
3. **스레드 안전성**: 네이티브 코드의 스레드 안전성 검증

### Kotlin 레벨 개선

1. **구조화된 동시성**: `SupervisorScope` 사용 검토
2. **재시도 정책**: 지수 백오프(exponential backoff) 적용
3. **메트릭스 수집**: 크래시/오류 발생 횟수 추적

### 모니터링

1. **크래시 리포팅**: Firebase Crashlytics에 상세 정보 전송
2. **성능 모니터링**: 이벤트 리스너 시작/중지 시간 측정
3. **상태 추적**: 카메라 연결 상태 변화 로깅

## 결론

네이티브 카메라 이벤트 리스너의 크래시는 주로 타이밍 문제와 상태 검증 부족에서 발생했습니다.
다단계 상태 검증, 적절한 예외 처리, 중복 실행 방지를 통해 안정성을 크게 향상시켰습니다.

향후 네이티브 코드 레벨에서도 타임아웃과 취소 메커니즘을 추가하면 더욱 견고한 시스템이 될 것입니다.
