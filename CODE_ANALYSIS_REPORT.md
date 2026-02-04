# CamCon 프로젝트 코드 품질 및 보안 분석 보고서

## 📊 프로젝트 규모
- **총 코드 라인 수**: 약 64,436줄
- **C++ 파일**: 20+ 개
- **Kotlin 파일**: 100+ 개
- **테스트 파일**: 최소 (심각한 문제)

---

## 1️⃣ 하드코딩된 값 (Magic Numbers & Strings)

### 🔴 Critical Issues

#### a) 포트 번호 하드코딩
| 파일 | 라인 | 문제 | 심각도 |
|------|------|------|--------|
| `data/constants/PtpipConstants.kt` | - | `const val PTPIP_DEFAULT_PORT = 15740` | 🟡 중간 |
| `presentation/viewmodel/PtpipViewModel.kt` | 390 | `val currentPortValue = 15740` | 🟡 중간 |
| `presentation/ui/screens/components/PtpipCommonComponents.kt` | 648, 667 | `port = 15740` (2회 반복) | 🟡 중간 |
| `presentation/ui/screens/components/ApModeContent.kt` | 420 | `port = 15740` | 🟡 중간 |

**문제점**:
- 포트 번호가 여러 곳에 반복되어 있음 (DRY 원칙 위반)
- 향후 포트 변경 시 모든 위치를 찾아 수정해야 함
- 설정 파일이 아닌 코드에 하드코딩됨

**권장사항**:
```kotlin
// 단일 소스 활용
object NetworkConfig {
    const val PTPIP_DEFAULT_PORT = 15740
    const val PTPIP_COMMAND_SOCKET_TIMEOUT_MS = 5000
    const val PTPIP_EVENT_SOCKET_TIMEOUT_MS = 3000
}
```

---

#### b) 경로/디렉토리 하드코딩
| 파일 | 라인 | 하드코딩 값 | 문제점 |
|------|------|-----------|--------|
| `native-lib.cpp` | 109-110 | `/data/data/com.inik.camcon/files` | 패키지명 하드코딩 |
| `native-lib.cpp` | 110 | `/data/data/com.inik.camcon/files/.config` | 환경설정 경로 고정 |
| `camera_init.cpp` | 9 | `/data/data/com.inik.camcon/lib` | 라이브러리 경로 고정 |
| `camera_ptpip.cpp` | 103 | `%s/../files/.config/libgphoto2` | 상대 경로 + 하드코딩 |
| `data/datasource/ptpip/PtpipDataSource.kt` | 528-529 | `gphoto2_plugins` 및 버전 디렉토리 | 버전 명시 |

**보안 위험**:
```cpp
// ❌ 패키지명이 하드코딩됨 - 앱 복제 시 문제 발생 가능
setenv("HOME", "/data/data/com.inik.camcon/files", 1);

// ✅ 올바른 방식 - JNI에서 Context를 통해 동적으로 얻기
std::string appDataDir = getAppDataDir(env, thiz);
```

---

#### c) 타임아웃 값 하드코딩
| 파일 | 라인 | 값 | 문제점 |
|------|------|-----|--------|
| `PtpipDataSource.kt` | 117-118 | `RECONNECT_DELAY_MS = 3000L`, `DUP_WINDOW_MS = 1500L` | 네트워크 상황에 맞지 않을 수 있음 |
| `camera_ptpip.cpp` | 37 | `"30000"` (30초 타임아웃) | Nikon Z8 대응용 하드코딩 |
| `UsbConnectionManager.kt` | 242 | `delay(500)` | USB 연결 안정화 지연값 |
| `PtpipDataSource.kt` | 335 | `delay(5000)` | 자동 재연결 재시도 지연 |
| `camera_nikon_auth.cpp` | 322, 363, 377 등 | `5000`, `3000` (ms) | 소켓 타임아웃 하드코딩 |

**권장사항**:
```kotlin
// 설정 가능하게 구성
object NetworkConfig {
    var PTPIP_CONNECTION_TIMEOUT_MS = 30000
    var PTPIP_SOCKET_TIMEOUT_MS = 5000
    var AUTO_RECONNECT_DELAY_MS = 3000
}
```

---

### 🟡 Medium Issues

#### d) 버전 번호 하드코딩
| 파일 | 라인 | 값 |
|------|------|-----|
| `UsbConnectionManager.kt` | 63-64 | `libgphoto2/2.5.33.1`, `libgphoto2_port/0.12.2` |
| `PtpipDataSource.kt` | 528-529 | 동일한 버전 |

**문제**: 라이브러리 업데이트 시 모든 코드를 수정해야 함

---

## 2️⃣ 에러 핸들링 패턴

### 🔴 Critical Issues

#### a) 예외 처리 누락
| 파일 | 라인 | 문제 |
|------|------|------|
| `native-lib.cpp` | 108-127 | `setenv` 호출 시 반환값 확인 없음 |
| `camera_ptpip.cpp` | 97-104 | 환경변수 설정 후 검증 없음 |
| `camera_nikon_auth.cpp` | 85-93 | `memcpy` 범위 검사 없음 |

```cpp
// ❌ 문제: 실패 여부를 확인하지 않음
setenv("HOME", "/data/data/com.inik.camcon/files", 1);
setenv("XDG_CONFIG_HOME", "/data/data/com.inik.camcon/files/.config", 1);

// ✅ 권장
if (setenv("HOME", "/data/data/com.inik.camcon/files", 1) != 0) {
    LOGE("Failed to set HOME environment variable");
    return false;
}
```

---

#### b) 버퍼 오버플로우 위험
| 파일 | 라인 | 문제 | 심각도 |
|------|------|------|--------|
| `camera_init.cpp` | 14 | `char newPath[1024]` + `snprintf` | 🔴 높음 |
| `camera_ptpip.cpp` | 102-107 | 256바이트 버퍼 + 경로 조합 | 🔴 높음 |
| `camera_nikon_auth.cpp` | 336-337 | `uint8_t ackBuffer[1024]` 고정 크기 | 🔴 높음 |

```cpp
// ❌ 위험: 버퍼 크기 초과 가능
char newPath[1024];
snprintf(newPath, sizeof(newPath), "%s:%s", libDir, existingPath);

// ✅ 권장
std::string newPath = libDir;
if (existingPath) {
    newPath += ":";
    newPath += existingPath;
}
setenv("LD_LIBRARY_PATH", newPath.c_str(), 1);
```

---

#### c) null 포인터 확인 부족
| 파일 | 라인 | 문제 |
|------|------|------|
| `native-lib.cpp` | 137-152 | `camera` null 체크 있지만 일부 경로 누락 |
| `camera_ptpip.cpp` | 315-320 | EXIF 정보 조회 시 포인터 검증 부족 |
| `PtpipDataSource.kt` | 446-463 | JSON 파싱 시 예외 처리만 있고 null 체크 없음 |

```cpp
// ❌ 위험
const char *levelStr;
switch (level) {
    case GP_LOG_ERROR:
        levelStr = "ERROR";
        break;
    // ... 기본값 없음
}
// levelStr이 초기화되지 않을 수 있음

// ✅ 권장
const char *levelStr = "UNKNOWN";
switch (level) {
    // ...
}
```

---

### 🟡 Medium Issues

#### d) 타임아웃 처리 일관성 부족
| 파일 | 라인 | 문제 |
|------|------|------|
| `camera_nikon_auth.cpp` | 40-76 | 수동 select() 기반 타임아웃 |
| `PtpipDataSource.kt` | 1071 | `withTimeoutOrNull(5000)` |
| `UsbConnectionManager.kt` | 242 | 고정 `delay(500)` |

**일관성 부족**: 다양한 타임아웃 구현 방식이 섞여 있음

---

## 3️⃣ 네이티브 코드(C++) 연동 부분의 안정성

### 🔴 Critical Issues

#### a) 메모리 누수 위험
| 파일 | 라인 | 문제 | 심각도 |
|------|------|------|--------|
| `camera_common.cpp` | - | `gp_file_new()` 후 예외 발생 시 `gp_file_free()` 미호출 | 🔴 높음 |
| `camera_files.cpp` | - | malloc 호출 후 에러 시 미해제 | 🔴 높음 |
| `camera_ptpip.cpp` | 58-82 | `GPPortInfoList` 할당 후 에러 경로에서 해제 | 🟡 중간 |

```cpp
// ❌ 위험: 메모리 누수
CameraFile *file;
int ret = gp_file_new(&file);
if (ret < GP_OK) {
    // file이 부분적으로 할당되었을 수 있음
    return nullptr; // 해제 없이 반환
}

// ✅ 권장: RAII 패턴 사용
class GPFileHandle {
public:
    GPFileHandle() { gp_file_new(&file); }
    ~GPFileHandle() { if (file) gp_file_free(file); }
private:
    CameraFile *file = nullptr;
};
```

---

#### b) 전역 상태 관리 문제
| 파일 | 라인 | 문제 |
|------|------|------|
| `native-lib.cpp` | 12-23 | 전역 변수 `camera`, `context`, 콜백 포인터들 |
| `camera_nikon_auth.cpp` | 29-35 | 전역 소켓/GUID 상태 |
| `camera_ptpip.cpp` | 15-20 | 전역 로그 파일 경로/상태 |

**문제점**:
- 스레드 안전성 부족 (일부 뮤텍스 보호 있지만 완전하지 않음)
- 테스트 어려움 (리셋 메커니즘 필요)
- 카메라 재연결 시 상태 불일치 위험

```cpp
// 현재 상태
extern Camera *camera;  // 전역 (위험)
extern std::mutex cameraMutex;  // 뮤텍스로 보호하려 시도

// 권장: 싱글톤 패턴
class CameraManager {
    static CameraManager& getInstance();
private:
    Camera *camera = nullptr;
    mutable std::mutex mutex;
};
```

---

#### c) JNI 안정성 문제
| 파일 | 라인 | 문제 | 심각도 |
|------|------|------|--------|
| `native-lib.cpp` | 280 | `GetStringUTFChars` 반환값 미확인 | 🔴 높음 |
| `camera_ptpip.cpp` | 88-89 | 포인터 매개변수 null 체크 없음 | 🔴 높음 |
| 전역 | - | `ReleaseStringUTFChars` 누락 가능성 | 🔴 높음 |

```cpp
// ❌ 위험: Null 체크 없음
const char *pathStr = env->GetStringUTFChars(photoPath, 0);
size_t lastSlash = fullPath.find_last_of('/'); // pathStr 사용 전 체크 필요

// ✅ 권장
const char *pathStr = env->GetStringUTFChars(photoPath, nullptr);
if (!pathStr) {
    LOGE("Failed to get path string");
    return nullptr;
}
```

---

#### d) Nikon 인증 소켓 관리
| 파일 | 라인 | 문제 |
|------|------|------|
| `camera_nikon_auth.cpp` | 80-120 | 소켓 연결 실패 시 리소스 해제 부분적 |
| `camera_nikon_auth.cpp` | 314-577 | 전역 소켓 변수 재사용 (상태 추적 어려움) |

```cpp
// ⚠️ 문제: 소켓이 전역으로 관리됨
static int g_nikonCmdSocket = -1;
static int g_nikonEventSocket = -1;

// 여러 연결 시도 시 이전 소켓이 제대로 정리되지 않을 수 있음
```

---

## 4️⃣ 카메라/PTP 관련 리소스 관리

### 🔴 Critical Issues

#### a) PTP 세션 생명주기 관리
| 파일 | 라인 | 문제 | 심각도 |
|------|------|------|--------|
| `PtpipDataSource.kt` | 1132-1181 | `disconnect()` 호출 전 이벤트 리스너 정리 필요 | 🔴 높음 |
| `PtpipDataSource.kt` | 1048-1127 | `stopAutomaticFileReceiving()` 복잡한 흐름 | 🔴 높음 |
| `camera_ptpip.cpp` | 141-150 | 연결 해제 시 여러 번 `gp_camera_exit` 호출 | 🟡 중간 |

**문제 분석**:
```kotlin
// ❌ 복잡한 정리 로직
private suspend fun stopAutomaticFileReceiving() {
    // Step 1: CameraEventManager 중지
    // Step 2: 지연 대기
    // Step 3: 네이티브 이벤트 리스너 중지 (여러 시도 포함)
    // Step 4: 추가 안전 대기
    // 총 10+ 초 소요 가능
}
```

**권장사항**: 상태 머신으로 관리
```kotlin
enum class PTPSessionState {
    DISCONNECTED,
    CONNECTING,
    INITIALIZING_EVENTS,
    CONNECTED,
    DISCONNECTING
}
```

---

#### b) 파일 다운로드 중복 처리
| 파일 | 라인 | 문제 |
|------|------|------|
| `PtpipDataSource.kt` | 1219-1231 | `recentProcessingGuard` 중복 방지 (1.5초 윈도우) |
| `camera_events.cpp` | - | `processedFiles` unordered_set 사용 |

**문제**: 
- 중복 파일 처리 방지를 위해 1.5초 윈도우를 사용하는데, 네트워크 지연 시 문제 가능
- 파일명 기반 중복 검사이므로 동일 파일명 재촬영 시 문제

```kotlin
// ⚠️ 문제: 충분하지 않은 중복 방지
private val recentProcessingGuard = object {
    private val map = ConcurrentHashMap<String, Long>()
    fun tryMark(key: String, now: Long): Boolean {
        val last = map[key]
        return if (last == null || now - last > DUP_WINDOW_MS) {  // 1.5초
            map[key] = now
            true
        } else {
            false
        }
    }
}

// ✅ 권장: 파일 해시 + 타임스탬프
data class FileFingerprint(val hash: String, val timestamp: Long)
```

---

#### c) Wi-Fi 네트워크 바인딩
| 파일 | 라인 | 문제 | 심각도 |
|------|------|------|--------|
| `PtpipDataSource.kt` | 536 | `NetworkBind` 설정 후 정리 로직이 불명확 | 🟡 중간 |
| `WifiNetworkHelper.kt` | - | Wi-Fi 연결 해제 시 바인딩 해제 여부 불명확 | 🟡 중간 |

**문제**: 앱 종료 또는 강제 종료 시 Wi-Fi 바인딩이 남아있을 수 있음

---

### 🟡 Medium Issues

#### d) 카메라 기능 지원 확인
| 파일 | 라인 | 문제 |
|------|------|------|
| `PtpipDataSource.kt` | 613 | `isDownloadOnly()` 메서드 호출만 하고 로그만 남김 |
| `camera_abilities.cpp` | - | 기능 지원 여부를 정확히 파싱하지 않음 |

```kotlin
// ❌ 현재: 경고만 로그하고 계속 진행
if (abilities.supports.isDownloadOnly()) {
    Log.w(TAG, "⚠️ 이 카메라는 다운로드만 지원합니다")
}
// UI는 원격 촬영 옵션을 계속 노출할 수 있음
```

---

## 5️⃣ 사용되지 않는 코드 및 TODO 주석

### 🟡 Medium Issues

#### a) TODO 주석들
| 파일 | 라인 | 내용 |
|------|------|------|
| `ServerPhotosScreen.kt` | 476 | `// TODO: 사진 상세 보기` |
| `SettingsActivity.kt` | 560, 595, 601, 614, 798 | `// TODO` (5회) |
| `data_extraction_rules.xml` | 8 | XML 주석: `<!-- TODO: Use <include> and <exclude> -->`  |

---

#### b) 주석 처리된 코드
| 파일 | 라인 | 코드 |
|------|------|------|
| `camera_ptpip.cpp` | 134-137 | `// initializeFileLogging`, `// gp_log_add_func` (주석 처리) |
| `PtpipDataSource.kt` | 1140 | `// discoveryService.stopDiscovery()` (카메라 목록 유지용 주석) |
| `native-lib.cpp` | 243 | `// gp_log_add_func(GP_LOG_DEBUG, file_log_callback, nullptr);` |

**권장**: 주석 처리된 코드는 제거하고 Git 히스토리 참고

---

## 6️⃣ 보안 관련 이슈

### 🔴 Critical Issues

#### a) 패키지명 하드코딩
| 파일 | 라인 | 값 | 위험도 |
|------|------|-----|--------|
| `native-lib.cpp` | 109-110 | `/data/data/com.inik.camcon/files` | 🔴 높음 |
| `camera_init.cpp` | 9-10 | `/data/data/com.inik.camcon/lib` | 🔴 높음 |

**보안 문제**:
```
1. 앱을 다시 패키지명으로 배포 시 경로가 맞지 않음
2. 악의적 앱이 경로를 알 수 있음
3. 다중 사용자 환경에서 문제 가능
```

**권장 해결**:
```cpp
// ✅ JNI에서 동적으로 얻기
std::string getAppDataDir(JNIEnv *env, jobject context) {
    // context.getFilesDir() 호출
    char path[PATH_MAX];
    // ... 구현
    return std::string(path);
}
```

---

#### b) Wi-Fi 비밀번호 입력 & 저장
| 파일 | 라인 | 문제 |
|------|------|------|
| `PtpipConnectionScreen.kt` | 189-432 | Wi-Fi 비밀번호가 `mutableStateOf`에 저장 |

**보안 문제**:
```kotlin
// ⚠️ 위험: 메모리에 평문 저장
var passwordForSsid by remember { mutableStateOf("") }

// 문제:
// 1. 메모리 덤프 시 노출 가능
// 2. Compose 재컴포지션 시 메모리 사본 생성
// 3. 백그라운드 앱 언제든 접근 가능
```

**권장**:
```kotlin
// ✅ 민감한 데이터 처리
// 1. Android Keystore 사용
// 2. 즉시 사용 후 메모리에서 제거
// 3. 플레인텍스트로 저장하지 않기

var passwordForSsid by remember {
    mutableStateOf<CharArray?>(null)
}

// 사용 후
passwordForSsid?.fill('\u0000')  // 메모리 제거
```

---

#### c) 데이터 저장 경로 보안
| 파일 | 라인 | 문제 |
|------|------|------|
| `UsbConnectionManager.kt` | 62 | 플러그인 파일이 앱 private 디렉토리가 아닌 외부에 복사될 수 있음 |
| `camera_ptpip.cpp` | 125 | 로그 파일이 `/data/data/.../files/`에 저장되지만 접근 제어 불명확 |

---

#### d) Debug 플래그와 로깅
| 파일 | 라인 | 문제 | 심각도 |
|------|------|------|--------|
| `camera_ptpip.cpp` | 100 | `setenv("GP_DEBUG", "1", 1)` - 항상 활성화 | 🟡 중간 |
| `camera_init.cpp` | 22-24 | `#ifdef DEBUG` - 빌드 타입에만 의존 | 🟡 중간 |
| `native-lib.cpp` | 72-79 | 로그 파일에 모든 정보 기록 | 🟡 중간 |

**문제**: 
- Release 빌드에서도 디버그 로그가 남을 수 있음
- 민감한 정보 (GUID, 소켓 번호 등) 로그에 기록됨

```cpp
// ❌ 현재: 조건 없이 GUID 로깅
snprintf(g_nikonAuthGuid, sizeof(g_nikonAuthGuid),
    "%02x%02x%02x%02x-%02x%02x-%02x%02x-%02x%02x-%02x%02x%02x%02x%02x%02x",
    // ...
);

// ✅ 권장: 민감한 정보 로깅 제한
if (BuildConfig.DEBUG) {
    LOGD("GUID: %s", g_nikonAuthGuid);
}
```

---

#### e) 네트워크 통신 보안
| 파일 | 라인 | 문제 |
|------|------|------|
| `camera_ptpip.cpp` | 341-343 | PTP/IP 연결이 평문 통신 (PTPS/암호화 없음) |
| `camera_nikon_auth.cpp` | 314-577 | Nikon 인증도 평문 소켓 통신 |

**제한사항**: PTP/IP 프로토콜이 본래 암호화를 지원하지 않음
- 그러나 Wi-Fi AP 모드에서만 사용되므로 상대적으로 안전
- STA 모드에서는 사용자 SSID/비밀번호로 보호됨

---

## 7️⃣ 테스트 코드 존재 여부

### 🔴 Critical Issue

#### 테스트 코드 거의 없음
| 테스트 유형 | 파일 수 | 상태 |
|------------|--------|------|
| Unit Tests | 0 | ❌ 없음 |
| Integration Tests | 0 | ❌ 없음 |
| Instrumented Tests | 1 | ⚠️ 기본 템플릿만 존재 |
| UI Tests | 0 | ❌ 없음 |

**유일한 테스트 파일**:
```kotlin
// app/src/androidTest/java/com/inik/camcon/ExampleInstrumentedTest.kt
@Test
fun useAppContext() {
    val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    assertEquals("com.inik.camcon", appContext.packageName)
}
```

**결과**: 
- 패키지명 확인만 하는 의미 없는 테스트
- C++ 네이티브 코드 테스트 없음
- PTP/IP 연결 로직 테스트 없음
- 카메라 기능 호출 테스트 없음

---

## 📋 요약 및 우선순위 개선 사항

### 🔴 즉시 해결 필요 (1순위)

| # | 항목 | 파일 | 심각도 | 예상 작업시간 |
|---|------|------|--------|--------------|
| 1 | 버퍼 오버플로우 보안 | `camera_init.cpp`, `camera_ptpip.cpp` | Critical | 4-6시간 |
| 2 | 패키지명 하드코딩 제거 | `native-lib.cpp`, `camera_init.cpp` | Critical | 3-4시간 |
| 3 | 메모리 누수 (malloc/gp_file_new) | C++ 전체 | Critical | 6-8시간 |
| 4 | Wi-Fi 비밀번호 보안 처리 | `PtpipConnectionScreen.kt` | Critical | 2-3시간 |
| 5 | JNI null 포인터 확인 | C++ 전체 | Critical | 4-5시간 |

### 🟡 높은 우선순위 (2순위)

| # | 항목 | 파일 | 심각도 | 예상 작업시간 |
|---|------|------|--------|--------------|
| 6 | 전역 상태 관리 개선 (싱글톤) | `native-lib.cpp`, 카메라 모듈 | High | 8-10시간 |
| 7 | 기본 unit 테스트 작성 | 전체 | High | 16-20시간 |
| 8 | 하드코딩 값 설정화 | 네트워크/경로 상수 | High | 4-5시간 |
| 9 | 디버그 로그 제거/필터링 | C++ 전체 | High | 3-4시간 |
| 10 | 타임아웃 처리 일관성 | PTP/네트워크 모듈 | High | 4-6시간 |

### 🟢 낮은 우선순위 (3순위)

| # | 항목 | 파일 | 심각도 | 예상 작업시간 |
|---|------|------|--------|--------------|
| 11 | TODO 주석 처리 | 전체 | Medium | 2-3시간 |
| 12 | 주석 처리된 코드 제거 | 전체 | Medium | 1-2시간 |
| 13 | PTP 세션 상태 머신 | `PtpipDataSource.kt` | Medium | 6-8시간 |
| 14 | Integration 테스트 | 카메라 모듈 | Medium | 12-16시간 |

---

## 🛠️ 권장 개선 방안 코드 예시

### 예시 1: 안전한 환경변수 설정
```cpp
// ✅ 권장 구현
bool setupEnvironmentVariables(JNIEnv *env, jobject context, const std::string& pluginDir) {
    // 동적 패키지명 획득
    std::string appDir = getAppFilesDir(env, context);
    
    // 환경변수 설정과 검증
    std::vector<std::pair<const char*, std::string>> envVars = {
        {"HOME", appDir},
        {"CAMLIBS", pluginDir},
        {"IOLIBS", pluginDir},
        {"LIBGPHOTO2_CONFIG_DIR", appDir + "/.config/libgphoto2"},
    };
    
    for (const auto& [key, value] : envVars) {
        if (setenv(key, value.c_str(), 1) != 0) {
            LOGE("Failed to set %s: %s", key, strerror(errno));
            return false;
        }
        LOGI("Successfully set %s=%s", key, value.c_str());
    }
    
    return true;
}
```

### 예시 2: 안전한 메모리 관리
```cpp
// ✅ RAII 패턴
class GPFileHandle {
public:
    GPFileHandle() {
        int ret = gp_file_new(&file);
        if (ret < GP_OK) {
            throw std::runtime_error("Failed to create GPFile");
        }
    }
    
    ~GPFileHandle() {
        if (file) {
            gp_file_free(file);
            file = nullptr;
        }
    }
    
    CameraFile* get() const { return file; }
    CameraFile* operator->() const { return file; }
    
private:
    CameraFile* file = nullptr;
};

// 사용
{
    GPFileHandle file;  // 자동 생성
    // ... 파일 사용
    // 범위 벗어남 -> 자동 해제
}
```

### 예시 3: 설정 값 중앙화
```kotlin
// ✅ 단일 소스
object CamConConfig {
    // Network
    object Network {
        const val PTPIP_DEFAULT_PORT = 15740
        const val PTPIP_SOCKET_TIMEOUT_MS = 5000
        const val PTPIP_CONNECTION_TIMEOUT_MS = 30000
        const val AUTO_RECONNECT_DELAY_MS = 3000
        const val DUPLICATE_WINDOW_MS = 1500L
    }
    
    // Camera
    object Camera {
        const val INIT_DELAY_MS = 500L
        const val USB_READ_TIMEOUT_MS = 5000
    }
    
    // Plugin versions
    object PluginVersions {
        const val LIBGPHOTO2_VERSION = "2.5.33.1"
        const val LIBGPHOTO2_PORT_VERSION = "0.12.2"
    }
}
```

---

## 📊 최종 평가

| 항목 | 점수 | 평가 |
|------|------|------|
| **코드 품질** | 2/5 | ⚠️ 많은 개선 필요 |
| **보안** | 2/5 | ⚠️ 심각한 문제 있음 |
| **테스트** | 1/5 | 🔴 거의 없음 |
| **유지보수성** | 2.5/5 | ⚠️ 하드코딩과 전역 상태 많음 |
| **문서화** | 3/5 | 중간 |
| **에러 처리** | 2/5 | ⚠️ 불일관적 |
| **리소스 관리** | 2.5/5 | ⚠️ 메모리 누수 위험 |

**총점: 2.2/5 ⭐⭐**

---

## 🎯 권장 다음 단계

1. **긴급 보안 패치** (1주일)
   - 패키지명 동적 획득
   - 버퍼 오버플로우 수정
   - Wi-Fi 비밀번호 암호화

2. **코드 리팩토링** (2-3주)
   - 메모리 누수 제거
   - 싱글톤/상태 관리 개선
   - 설정값 중앙화

3. **테스트 추가** (2-3주)
   - Unit 테스트 기본 작성
   - 중요 로직 Integration 테스트
   - CI/CD 파이프라인 구축

4. **문서화** (1주일)
   - API 문서화
   - PTP 프로토콜 플로우 다이어그램
   - 트러블슈팅 가이드

