# PTPIP 연결 문제 해결 가이드

## 📋 목차

1. [개요](#개요)
2. [libgphoto2 ptp2 드라이버 분석](#libgphoto2-ptp2-드라이버-분석)
3. [발견된 문제점](#발견된-문제점)
4. [개선 사항](#개선-사항)
5. [디버깅 방법](#디버깅-방법)
6. [참고 문서](#참고-문서)

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
- 문�� 해결 제안 메시지 추가

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
- [ ] 포트 15740이 열려 있는��?
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

**마지막 업데이트**: 2025-01-22  
**작성자**: AI Assistant  
**버전**: 1.0
