# PTPIP 연결 문제 해결 가이드

## 📋 목차

1. [개요](#개요)
2. [libgphoto2 ptp2 드라이버 분석](#libgphoto2-ptp2-드라이버-분석)
3. [발견된 문제점](#발견된-문제점)
4. [개선 사항](#개선-사항)
5. [디버깅 방법](#디버깅-방법)
6. [참고 문서](#참고-문서)
7. [Nikon Z6 GetVendorPropCodes 문제 해결](#nikon-z6-getvendorpropcodes-문제-해결)

---

## 개요

이 프로젝트는 **libgphoto2의 ptp2 드라이버**를 사용하여 PTPIP (PTP over IP) 연결을 구현합니다.

### 사용 중인 컴포넌트

- **libgphoto2**: 2.5.32.1
- **libgphoto2_port**: 0.12.2
- **PTP2 드라이버**: `camlibs/ptp2` (libgphoto2 내부)
- **PTPIP 포트 드라이버**: `libgphoto2_port_iolib_ptpip.so`

### 아키텍처

```
Kotlin 레이어
    └── JNI (CameraNative.kt)
        └── C++ 레이어 (camera_ptpip.cpp)
            └── libgphoto2
                ├── ptp2 드라이버 (PTP 프로토콜)
                └── ptpip 포트 (네트워크 I/O)
```

---

## libgphoto2 ptp2 드라이버 분석

### 주요 파일 구조

libgphoto2의 ptp2 드라이버는 다음 파일들로 구성됩니다:

```
libgphoto2/camlibs/ptp2/
├── ptp.c           # PTP 프로토콜 핵심 구현
├── ptp.h           # PTP 데이터 구조 및 상수
├── ptpip.c         # PTP/IP 네트워크 구현
├── ptpip.h         # PTP/IP 헤더
├── config.c        # 카메라 설정 관리
├── library.c       # 드라이버 엔트리 포인트
└── ptp-pack.c      # PTP 패킷 처리
```

### ptp2_ip 설정 키

libgphoto2는 `gp_setting_get/set` API를 통해 내부 설정을 관리합니다.
ptp2 드라이버가 사용하는 주요 설정 키:

| 설정 키 | 타입 | 설명 | 기본값 |
|---------|------|------|--------|
| `ptp2_ip:guid` | string | 클라이언트 GUID (16바이트 hex) | 자동 생성 |
| `ptp2_ip:client_name` | string | 클라이언트 이름 | "libgphoto2" |
| `ptp2_ip:timeout` | int | 타임아웃 (밀리초) | 5000 |
| `ptp2_ip:connection_id` | int | 연결 ID | - |
| `ptp2_ip:session_id` | int | 세션 ID | - |

---

## 발견된 문제점

### 1️⃣ PTPIP 포트 경로 설정 방식

**문제:**

```cpp
// 현재 코드
snprintf(ptpipPortPath, sizeof(ptpipPortPath), "ptpip:%s:%d", ipAddress, port);
```

**분석:**

- libgphoto2의 ptpip 포트는 기본 포트(15740)를 가정하도록 설계됨
- 포트 번호 생략 시 더 안정적인 연결 가능

**해결:**

```cpp
// 개선된 코드
if (port == 15740) {
    snprintf(ptpipPortPath, sizeof(ptpipPortPath), "ptpip:%s", ipAddress);
} else {
    snprintf(ptpipPortPath, sizeof(ptpipPortPath), "ptpip:%s:%d", ipAddress, port);
}
```

### 2️⃣ GUID 관리

**문제:**

- GUID 삭제/재설정 로직이 과도하게 실행됨
- libgphoto2는 GUID를 자동으로 생성/관리하도록 설계됨

**분석:**

- ptp2 드라이버는 GUID가 없으면 자동으로 생성
- 기존 GUID 유지가 연결 안정성에 유리 (카메라 페어링 정보)

**해결:**

- GUID는 가능한 유지하고, 연결 실패 시에만 재설정
- 재설정 시 충분한 대기 시간(200ms) 부여

### 3️⃣ 타임아웃 설정

**문제:**

```cpp
gp_setting_set("ptp2_ip", "timeout", "5000");  // 5초
```

**분석:**

- 네트워크 환경에 따라 5초는 부족할 수 있음
- 특히 Wi-Fi AP 모드에서는 더 긴 타임아웃 필요

**해결:**

```cpp
gp_setting_set("ptp2_ip", "timeout", "10000");  // 10초
```

### 4️⃣ 에러 처리 부족

**문제:**

- 에러 코드만 출력하고 구체적인 원인 분석 없음

**해결:**

- 에러 코드별 상세 분석 추가
- 문제 해결 제안 메시지 추가

```cpp
switch (ret) {
    case -7:  // GP_ERROR_TIMEOUT
        LOGE("타임아웃 - 카메라가 응답하지 않습니다");
        break;
    case -6:  // GP_ERROR_NOT_SUPPORTED
        LOGE("지원되지 않음 - 드라이버 호환성 문제");
        break;
    case -110: // GP_ERROR_IO
        LOGE("I/O 오류 - 네트워크 연결 문제");
        break;
}
```

---

## 개선 사항

### ✅ 적용된 개선 사항

#### 1. 상세 로깅 추가

- 모든 주요 단계에서 상태 로깅
- 에러 코드와 설명 동시 출력
- 재시도 과정 추적

#### 2. 포트 경로 최적화

- 기본 포트(15740) 사용 시 단순화된 경로 사용
- 대체 경로 검색 로직 추가

#### 3. GUID 관리 개선

- 기존 GUID 유지 우선
- GUID 백업 및 로깅
- 재설정 시 충분한 대기 시간

#### 4. 설정 초기화 개선

- 모든 설정 키 상태 확인
- 초기화 결과 개별 로깅
- 타임아웃 값 증가 (10초)

#### 5. 에러 분석 추가

- 에러 코드별 상세 분석
- 문제 해결 제안 메시지
- 재시도 과정 상세 로깅

---

## 디버깅 방법

### 1️⃣ 로그 확인

#### Android Logcat

```bash
adb logcat -s CameraNative:* PtpipConnectionManager:* PtpipDataSource:*
```

주요 로그 패턴:

```
I/CameraNative: === initCameraWithPtpip 호출 시작 ===
I/CameraNative: IP: 192.168.1.1, Port: 15740
I/CameraNative: === PTP/IP 기본 설정 적용 시작 ===
I/CameraNative: 기존 GUID 유지: xxxx-xxxx-xxxx-xxxx
I/CameraNative: === 카메라 초기화 시작 (1차 시도) ===
E/CameraNative: ❌ 카메라 초기화 실패: Timeout (코드: -7)
I/CameraNative: === 재시도 1: 포트 경로 단순화 ===
```

#### libgphoto2 내부 로그

프로젝트는 libgphoto2 로그를 파일로 저장합니다:

```
/data/data/com.inik.camcon/files/libgphoto2_ptpip_YYYYMMDD_HHMMSS.txt
```

로그 파일 가져오기:

```bash
adb pull /data/data/com.inik.camcon/files/libgphoto2_ptpip_*.txt
```

### 2️⃣ 네트워크 테스트

#### PTPIP 포트 연결 테스트

```bash
# Android 기기에서
adb shell telnet 192.168.1.1 15740
```

또는 Kotlin 코드:

```kotlin
suspend fun testPtpipConnection(ip: String, port: Int = 15740): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(ip, port), 5000)
            socket.close()
            true
        } catch (e: Exception) {
            Log.e(TAG, "연결 테스트 실패: ${e.message}")
            false
        }
    }
}
```

### 3️⃣ libgphoto2 설정 확인

C++ 디버깅 코드:

```cpp
void debugPtp2Settings() {
    char buf[256] = {0};
    
    if (gp_setting_get((char*)"ptp2_ip", (char*)"guid", buf) == GP_OK) {
        LOGI("현재 GUID: %s", buf);
    }
    
    memset(buf, 0, sizeof(buf));
    if (gp_setting_get((char*)"ptp2_ip", (char*)"timeout", buf) == GP_OK) {
        LOGI("현재 timeout: %s", buf);
    }
    
    memset(buf, 0, sizeof(buf));
    if (gp_setting_get((char*)"ptp2_ip", (char*)"client_name", buf) == GP_OK) {
        LOGI("현재 client_name: %s", buf);
    }
}
```

### 4️⃣ 카메라 상태 확인

#### 니콘 카메라

1. 메뉴 → 네트워크 설정 → Wi-Fi
2. 연결 모드 확인 (AP/STA)
3. PTP 모드 활성화 확인

#### 캐논 카메라

1. 메뉴 → 통신 설정
2. Wi-Fi 기능 → PTP/IP 활성화

---

## 참고 문서

### libgphoto2 공식 문서

- GitHub: https://github.com/gphoto/libgphoto2
- ptp2 드라이버: https://github.com/gphoto/libgphoto2/tree/master/camlibs/ptp2
- API 문서: http://www.gphoto.org/doc/api/

### PTP/IP 프로토콜

- CIPA DC-005: Picture Transfer Protocol over IP
- ISO 15740: Photography — Picture transfer protocol

### 프로젝트 관련 파일

```
app/src/main/cpp/
├── camera_ptpip.cpp           # PTPIP 초기화 (개선됨 ✅)
├── camera_ptpip_setup.cpp     # 포트 및 설정 (개선됨 ✅)
├── camera_ptpip_commands.cpp  # PTP 명령어
└── camera_common.h            # 공통 헤더

app/src/main/java/com/inik/camcon/
├── data/network/ptpip/
│   ├── connection/PtpipConnectionManager.kt
│   ├── discovery/PtpipDiscoveryService.kt
│   └── authentication/NikonAuthenticationService.kt
└── data/datasource/ptpip/
    └── PtpipDataSource.kt
```

---

## 문제 해결 체크리스트

### 연결 실패 시 확인 사항

- [ ] 카메라가 PTP/IP 모드로 설정되어 있는가?
- [ ] Wi-Fi 네트워크에 정상 연결되어 있는가?
- [ ] 카메라 IP 주소가 정확한가?
- [ ] 포트 15740이 열려 있는가?
- [ ] 카메라 펌웨어가 최신인가?
- [ ] libgphoto2가 카메라를 지원하는가?

### 로그 확인 사항

- [ ] "PTP/IP 포트 드라이버 찾기 실패" → IOLIBS 환경변수 확인
- [ ] "타임아웃" → 네트워크 속도 확인, 타임아웃 값 증가
- [ ] "지원되지 않음" → 카메라 모델 호환성 확인
- [ ] "I/O 오류" → 네트워크 연결 상태 확인

### 재시도 로직

현재 구현된 재시도 단계:

1. **1차 시도**: 표준 포트 경로 (`ptpip:IP:PORT`)
2. **재시도 1**: 단순화된 경로 (`ptpip:IP`)
3. **재시도 2**: GUID 재설정 후 재시도

---

## 향후 개선 방향

### 1. 카메라별 최적화

- 니콘, 캐논 등 제조사별 특화 설정
- 모델별 타임아웃 및 재시도 전략

### 2. 자동 복구

- 연결 끊김 감지 및 자동 재연결
- GUID 충돌 자동 해결

### 3. 성능 최적화

- 연결 캐싱
- 빠른 재연결 경로

### 4. 사용자 피드백

- 진행 상황 상세 표시
- 에러 메시지 한글화

---

## Nikon Z6 GetVendorPropCodes 문제 해결

###  

- PTP/IP 연결은 성공
- 인증도 완료됨
- 하지만 `Device Capabilities`에서 `No Image Capture`로 표시
- 촬영 시도 시 `Unsupported operation` 오류 (에러 코드 -6)
- `gp_camera_capture` 실행 시 "Sorry, your camera does not support generic capture" 메시지

#### 원인 분석

Nikon Z6와 같은 최신 Nikon 카메라는 PTP/IP 연결 시 보안을 위해 **초기 `GetDeviceInfo`에서 제한된 capabilities만 반환**합니다.

**libgphoto2 로그 비교:**

**인증 전 (제한된 capabilities):**

```
Device Capabilities:
  File Download, No File Deletion, No File Upload
  No Image Capture, No Open Capture, No vendor specific capture

Supported operations:
  0x1001 (Get device info)
  0x1002 (Open session)
  0x1003 (Close session)
  0x1004 (Get storage IDs)
  ... (기본 PTP 명령만)
```

**인증 + GetVendorPropCodes 후 (전체 capabilities):**

```
Device Capabilities:
  File Download, File Deletion, File Upload
  Image Capture, Open Capture, Vendor specific capture

Supported operations:
  0x1001 (Get device info)
  0x1002 (Open session)
  ...
  0x90CA (GetVendorPropCodes) 
  0x9207 (InitiateCaptureRecInMedia) 
  0x9201 (StartLiveView) 
  0x9203 (GetLiveViewImg) 
```

**핵심:** 인증 후 `GetVendorPropCodes` (0x90CA)를 호출해야 추가 104개의 vendor property 코드와 operation 코드가 로드됩니다.

#### 해결 방법

##### 1. C++ 네이티브 레벨 수정 (권장)

`app/src/main/cpp/camera_ptpip_commands.cpp` 파일 수정:

```cpp
// 니콘 STA 모드 인증 함수
int performNikonStaAuthentication(Camera *camera) {
    if (!camera) {
        LOGE("카메라가 초기화되지 않음");
        return -1;
    }

    LOGI("=== 니콘 STA 모드 인증 시작 ===");

    try {
        // Step 1-5: 기존 인증 프로세스
        // ... (OpenSession, 0x952b, 0x935a 등)

        // Step 6: GetVendorPropCodes로 추가 기능 정보 가져오기 (중요!)
        LOGI("Step 6: GetVendorPropCodes (0x90CA) 호출");
        ret = sendPtpOperationReal(camera, PTP_OC_NIKON_GetVendorPropCodes, 0, 0, 0);
        if (ret != 0) {
            LOGW(" GetVendorPropCodes 실패 - 일부 기능 제한됨");
        } else {
            LOGI(" GetVendorPropCodes 성공 - 추가 기능 로드됨");
        }

        // Step 7: 인증 후 GetDeviceInfo로 업데이트된 기능 정보 가져오기
        LOGI("Step 7: 업데이트된 DeviceInfo 재조회");
        ret = sendPtpOperationReal(camera, PTP_OC_GetDeviceInfo, 0, 0, 0);
        if (ret != 0) {
            LOGW("DeviceInfo 재조회 실패");
        } else {
            LOGI("DeviceInfo 재조회 성공 - 업데이트된 capabilities 확인");
        }

        // Step 8: gp_camera_init를 다시 호출하여 libgphoto2가 새로운 capabilities 인식
        LOGI("Step 8: gp_camera_exit + gp_camera_init으로 재초기화");
        ret = gp_camera_exit(camera, context);
        if (ret  0) {
            LOGW(" gp_camera_exit 실패: %s (계속 진행)", gp_result_as_string(ret));
        }
        
        // 카메라 재초기화
        ret = gp_camera_init(camera, context);
        if (ret  0) {
            LOGE(" gp_camera_init 재초기화 실패: %s", gp_result_as_string(ret));
            return -1;
        }
        
        LOGI(" 카메라 재초기화 성공 - 인증된 capabilities 로드 완료");

        LOGI(" 니콘 STA 모드 인증 완료");
        return 0;

    } catch (...) {
        LOGE("니콘 STA 모드 인증 중 예외 발생");
        return -1;
    }
}
```

##### 2. Kotlin/Android 레벨 수정

`app/src/main/java/com/inik/camcon/data/network/ptpip/authentication/NikonAuthenticationService.kt`:

```kotlin
private suspend fun performPhase2Authentication(camera: PtpipCamera): Boolean =
    withContext(Dispatchers.IO) {
        // ... 기존 인증 코드 ...
        
        // Step 2-5: GetVendorPropCodes 호출 (중요!)
        LogcatManager.i(TAG, "Step 2-5: GetVendorPropCodes (0x90CA) 호출")
        if (!sendGetVendorPropCodes(commandSocket)) {
            LogcatManager.w(TAG, " GetVendorPropCodes 실패 - 일부 기능 제한됨")
        } else {
            LogcatManager.i(TAG, " GetVendorPropCodes 성공 - 촬영 기능 활성화")
        }
        
        // Step 2-6: 업데이트된 DeviceInfo 다시 가져오기
        LogcatManager.i(TAG, "Step 2-6: 업데이트된 DeviceInfo 재조회")
        sendGetDeviceInfo(commandSocket)
        
        return@withContext true
    }

/**
 * GetVendorPropCodes (0x90CA) 전송
 */
private fun sendGetVendorPropCodes(socket: Socket): Boolean {
    return try {
        LogcatManager.d(TAG, "GetVendorPropCodes (0x90CA) 전송")

        val output = socket.getOutputStream()
        val packet = createOperationRequest(0x90CA, transactionId)

        output.write(packet)
        output.flush()

        // 응답 수신
        socket.soTimeout = 10000
        val response = ByteArray(4096)
        val bytesRead = socket.getInputStream().read(response)

        if (bytesRead > 0) {
            LogcatManager.d(TAG, "GetVendorPropCodes 응답: $bytesRead bytes")
            
            // 응답 타입 확인
            val buffer = ByteBuffer.wrap(response).order(ByteOrder.LITTLE_ENDIAN)
            buffer.position(4)
            val packetType = buffer.int
            
            if (packetType == PtpipConstants.PTPIP_START_DATA || 
                packetType == PtpipConstants.PTPIP_DATA) {
                LogcatManager.i(TAG, " Vendor Prop Codes 데이터 수신 성공")
                return true
            }
        }
        
        false
    } catch (e: Exception) {
        LogcatManager.e(TAG, "GetVendorPropCodes 실패: ${e.message}")
        false
    }
}
```

#### 중요 포인트

1. **순서가 중요합니다:**
    - 인증 완료 → GetVendorPropCodes → GetDeviceInfo 재조회 → 카메라 재초기화

2. **GetVendorPropCodes는 인증 후에만 호출 가능**
    - 인증 전 호출 시 `Access Denied` 또는 `Not Supported` 에러 발생

3. **응답에는 104개의 추가 property 코드 포함:**
   ```
   0xd015 (Reset Bank 0)
   0xd017 (Auto White Balance Bias)
   0xd018 (Tungsten White Balance Bias)
   ... (총 104개)
   ```

4. **libgphoto2를 사용하는 경우 재초기화 필수:**
    - `gp_camera_exit()` → `gp_camera_init()`
    - 이렇게 해야 libgphoto2가 새로운 capabilities를 인식함

#### 검증 방법

##### 로그 확인:

```bash
# Android 로그
adb logcat -s NikonAuthService:*

# 확인할 내용:
 GetVendorPropCodes (0x90CA) 호출
 Vendor Prop Codes 데이터 수신 성공  
 DeviceInfo 재조회 성공
 카메라 재초기화 성공
```

##### libgphoto2 디버그 로그:

```bash
gphoto2 --port ptpip:192.168.1.100 --debug --debug-logfile=debug.log --summary

# 로그에서 확인:
ptp_usb_sendreq (2): Sending PTP_OC 0x90ca (GetVendorPropCodes) request...
ptp_usb_getdata (2): Reading PTP_OC 0x90ca data...
# ... 104개의 prop 코드 수신 ...

# Device Capabilities 확인:
Device Capabilities:
  File Download, File Deletion, File Upload
  Image Capture, Open Capture, Vendor specific capture 
```

##### 촬영 테스트:

```kotlin
// 이제 촬영이 작동해야 함
val result = CameraNative.requestCapture()
// "OK" 또는 성공 메시지 반환
```

#### 참고 문헌

- **libgphoto2 GitHub Issue #976**: "Nikon Z6ii build in wifi - Access Denied"
    - https://github.com/gphoto/libgphoto2/issues/976

- **libgphoto2 GitHub Issue #1135**: "Nikon PTP/IP connection - limited capabilities"
    - https://github.com/gphoto/libgphoto2/issues/1135

- **libgphoto2 소스코드**: `camlibs/ptp2/library.c`
    - GetVendorPropCodes 구현 및 Nikon Z6 특화 로직

- **gphoto2 Issue #521**: "Can't detect Nikon Z50"
    - https://github.com/gphoto/gphoto2/issues/521
    - 유사한 문제 및 해결 과정

#### 추가 디버깅 팁

##### 1. GetVendorPropCodes 응답 분석:

```cpp
// 응답 파싱 예제 (참고용)
void parseVendorPropCodes(uint8_t* data, int length) {
    // PTP Array 구조:
    // [4 bytes] Array length
    // [2 bytes each] Property codes
    
    uint32_t count = *(uint32_t*)data;
    LOGI("Vendor Prop Codes 개수: %u", count);
    
    uint16_t* codes = (uint16_t*)(data + 4);
    for (uint32_t i = 0; i  count; i++) {
        LOGI("  Prop[%u]: 0x%04x", i, codes[i]);
    }
}
```

##### 2. 촬영 명령 순서:

```
1. GetVendorPropCodes (0x90CA) 
2. DeviceInfo 재조회 
3. SetControlMode (0x90C2) 
4. InitiateCaptureRecInMedia (0x9207) 
```

##### 3. 문제 지속 시 확인 사항:

- [ ] 카메라 펌웨어가 최신인가?
- [ ] Wi-Fi 연결이 안정적인가?
- [ ] 인증이 정상 완료되었는가?
- [ ] GetVendorPropCodes가 성공했는가?
- [ ] 카메라 재초기화가 성공했는가?

---
