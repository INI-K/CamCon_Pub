# PTPIP 카메라별 무선 연결 분석

## 📋 목차

1. [개요](#개요)
2. [PTP/IP 프로토콜 기본](#ptpip-프로토콜-기본)
3. [카메라 제조사별 차이점](#카메라-제조사별-차이점)
4. [libgphoto2 ptp2 드라이버 상세 분석](#libgphoto2-ptp2-드라이버-상세-분석)
5. [발견된 문제와 해결 방안](#발견된-문제와-해결-방안)
6. [디버깅 전략](#디버깅-전략)

---

## 개요

libgphoto2의 **ptp2 드라이버**는 각 카메라 제조사의 벤더 확장(Vendor Extension)을 처리합니다.
무선(PTP/IP) 연결 시 카메라마다 다른 초기화 절차와 명령어를 사용합니다.

### 지원하는 제조사

- **Canon**: 가장 완벽한 지원 (EOS, PowerShot)
- **Nikon**: DSLR 및 Coolpix 지원 (AP/STA 모드 구분 필요)
- **Sony**: 제한적 지원 (일부 모델은 다운로드만 가능)
- **Fujifilm**: 부분 지원 (X 시리즈는 자체 PTPIP 변형 사용)
- **Olympus**: 제한적 지원 (E 시리즈)
- **Panasonic**: 기본 지원 (Lumix)

---

## PTP/IP 프로토콜 기본

### 표준 연결 절차

```
1. Command 채널 연결 (TCP 15740 포트)
   ├─ Init_Command_Request (Type 1)
   │  ├─ 16 byte GUID
   │  ├─ Client Name (UTF-16LE)
   │  └─ Version (0x00010001)
   └─ Init_Command_Ack (Type 2)
      ├─ Connection Number
      ├─ 16 byte GUID (카메라)
      └─ Camera Name

2. Event 채널 연결 (TCP 15740 포트, 별도)
   ├─ Init_Event_Request (Type 3)
   │  └─ Connection Number (위에서 받은 값)
   └─ Init_Event_Ack (Type 4)

3. PTP 명령 전송
   ├─ Operation_Request (Type 6)
   ├─ Data_Packet (Type 9/10/12)
   └─ Operation_Response (Type 7)
```

### 패킷 구조

```c
struct PtpipPacket {
    uint32_t length;      // 패킷 전체 길이 (헤더 포함)
    uint32_t type;        // 패킷 타입 (1-14)
    uint8_t  data[];      // 가변 길이 페이로드
};
```

---

## 카메라 제조사별 차이점

### 1️⃣ Canon (VendorExtensionID: 0x0000000B)

#### 특징

- **가장 표준적인 PTP/IP 구현**
- Wi-Fi 지원 모델: EOS (WLAN), PowerShot SD430 등
- 별도 인증 절차 **불필요**

#### 연결 절차

```cpp
1. Init_Command_Request/Ack
2. Init_Event_Request/Ack
3. OpenSession (0x1002)
4. GetDeviceInfo (0x1001)
5. ✅ 바로 사용 가능
```

#### Canon 전용 명령어

```cpp
// EOS 전용
0x9101  // EOS_GetStorageIDs
0x9102  // EOS_GetStorageInfo
0x9103  // EOS_GetObjectInfo
0x9104  // EOS_GetObject
0x9105  // EOS_DeleteObject
0x9106  // EOS_FormatStore
0x9107  // EOS_GetPartialObject
0x9108  // EOS_GetDeviceInfoEx
0x910C  // EOS_GetLiveViewPicture
0x911B  // EOS_RemoteRelease (셔터 제어)
0x911C  // EOS_SetDevicePropValueEx
0x9153  // EOS_GetEvent (이벤트 폴링)
```

#### 설정 예시

```bash
# 캐논은 별도 Wi-Fi 설정 불필요
gphoto2 --port ptpip:192.168.1.1 --summary
gphoto2 --port ptpip:192.168.1.1 --capture-image-and-download
```

---

### 2️⃣ Nikon (VendorExtensionID: 0x0000000A)

#### 특징

- **AP 모드 vs STA 모드 구분 필수**
- STA 모드는 **페어링 인증 절차** 필요
- Wi-Fi 지원 모델: D90, D7000, Z 시리즈, Coolpix P1/P2/P3

#### AP 모드 (Access Point)

카메라가 직접 Wi-Fi AP 생성

```cpp
1. 카메라 SSID에 연결
2. 카메라 IP는 보통 192.168.1.1
3. Init_Command_Request/Ack
4. Init_Event_Request/Ack
5. OpenSession
6. ✅ 바로 사용 가능
```

#### STA 모드 (Station, 공유기 연결)

카메라와 클라이언트가 같은 공유기에 연결

```cpp
1. Init_Command_Request/Ack
2. Init_Event_Request/Ack
3. OpenSession
4. GetDeviceInfo

5. ⚠️ 페어링 인증 절차 시작 ⚠️
   Phase 1:
   ├─ 0x9201: StartLiveView (?)
   ├─ 0x90C8: DeviceReady
   └─ 0x952b: STA 모드 초기화 (?)

   Phase 2:
   ├─ 0x935a: 연결 승인 요청
   ├─ 카메라 LCD에 "연결을 허용하시겠습니까?" 표시
   └─ 사용자가 OK 버튼 누름

6. ✅ 인증 완료 후 사용 가능
```

#### Nikon 전용 명령어

```cpp
// 기본 명령어
0x90C0  // Capture (촬영)
0x90C1  // AfDrive (AF 구동)
0x90C2  // SetControlMode (제어 모드 설정)
0x90C3  // DelImageSDRAM (SDRAM 이미지 삭제)
0x90C7  // CheckEvent (이벤트 확인)
0x90C8  // DeviceReady (장치 준비 확인)
0x90CA  // GetVendorPropCodes (벤더 속성 코드 조회)
0x90CB  // AfCaptureSDRAM (AF 후 SDRAM 촬영)

// 라이브뷰 관련
0x9200  // GetPreviewImg (미리보기 이미지)
0x9201  // StartLiveView (라이브뷰 시작)
0x9202  // EndLiveView (라이브뷰 종료)
0x9203  // GetLiveViewImg (라이브뷰 이미지)
0x9204  // MfDrive (수동 포커스 구동)
0x9205  // ChangeAfArea (AF 영역 변경)

// STA 모드 전용 (추정)
0x935a  // 연결 승인 요청 (추정)
0x952b  // STA 모드 초기화 (추정)
```

#### 설정 예시

```bash
# AP 모드 (간단)
gphoto2 --port ptpip:192.168.1.1 --summary

# STA 모드 (복잡, 카메라 승인 필요)
# 1. 연결 시작
gphoto2 --port ptpip:192.168.1.5 --auto-detect
# 2. 카메라 LCD에서 "OK" 누르기
# 3. 연결 완료 후 사용
gphoto2 --port ptpip:192.168.1.5 --capture-image
```

---

### 3️⃣ Sony (VendorExtensionID: 0x00000011)

#### 특징

- **제한적인 PTP 구현** (다운로드 위주)
- 원격 제어 기능 매우 제한적
- **같은 USB ID** 사용으로 혼란 야기
- 일부 모델은 PTP/IP 미지원

#### 지원 기능

```
✅ 파일 다운로드
✅ 썸네일 조회
❌ 원격 촬영 (대부분 모델)
❌ 설정 변경 (대부분 모델)
⚠️  라이브뷰 (일부 Alpha 시리즈만)
```

#### 제한 사항

```cpp
// Sony는 대부분 표준 PTP 명령만 지원
// 벤더 확장 명령어가 거의 없음
// A7 시리즈 일부만 원격 제어 지원
```

---

### 4️⃣ Fujifilm (VendorExtensionID: 0x00000006)

#### 특징

- **자체 PTPIP 변형 사용** (X 시리즈)
- 표준 PTPIP와 호환되지 않음
- 전용 앱(Fujifilm Camera Remote) 필요
- 역공학으로 부분 지원 가능 (fuji-cam-wifi-tool)

#### 프로토콜 차이점

```cpp
// 표준 PTPIP 포트: 15740
// Fuji는: 55740 포트 사용

// 헤더 필드 값이 다름
// 응답 값이 다름
// 초기화 핸드셰이크가 다름
```

#### 지원 모델별 차이

```
X-T1, X-T2, X-T3, X-T4: Wi-Fi 지원 (자체 프로토콜)
X-T10, X-T20, X-T30: Wi-Fi 미지원 (USB만)
GFX 시리즈: 표준 PTPIP 지원 (libgphoto2 2.5.19+)
```

---

### 5️⃣ Olympus (VendorExtensionID: 0x0000000C)

#### 특징

- **XML 래핑** 사용 (E 시리즈)
- PictBridge 모드와 Control 모드 구분
- Control 모드에서만 원격 제어 가능

#### 연결 절차

```cpp
1. 표준 PTP/IP 연결
2. GetDeviceInfo (외부 - PictBridge)
3. Olympus_GetDeviceInfo (내부 - XML 형식)
4. 두 DeviceInfo 병합
5. Control 모드 활성화
```

---

### 6️⃣ Panasonic (VendorExtensionID: 0x00000015)

#### 특징

- **MTP 모드 해킹** 필요
- Windows MTP Initiator로 위장해야 함
- 초기화 후 추가 명령어 활성화됨

#### 초기화 트릭

```cpp
// Panasonic은 MTP 모드로 시작
// MTP SessionInitiatorInfo 설정 필요
propval.str = "Windows/6.2.9200 MTPClassDriver/6.2.9200.16384";
ptp_setdevicepropvalue(params, PTP_DPC_MTP_SessionInitiatorInfo, 
                       &propval, PTP_DTC_STR);

// 이후 GetDeviceInfo 재호출하면 PTP 명령어들이 추가됨
ptp_getdeviceinfo(params, di);
```

#### Panasonic 전용 명령어

```cpp
0x1001  // InitiateCapture
0x1016  // Liveview
0x1017  // LiveviewImage
0x1018  // MovieRecControl
```

---

## libgphoto2 ptp2 드라이버 상세 분석

### 드라이버 파일 구조

```
libgphoto2/camlibs/ptp2/
├── ptp.c              # PTP 프로토콜 핵심 로직
├── ptp.h              # PTP 구조체, 상수 정의
├── ptpip.c            # PTP/IP 네트워크 구현
├── ptpip.h            # PTP/IP 헤더 정의
├── config.c           # 카메라 설정 관리
├── library.c          # 드라이버 엔트리 포인트
├── ptp-pack.c         # 패킷 인코딩/디코딩
└── usb.c              # USB 전송 (참고용)
```

### VendorExtensionID 자동 감지

libgphoto2는 GetDeviceInfo 응답에서 제조사를 감지하고 VendorExtensionID를 변경합니다:

```cpp
// library.c에서 발췌
if (di->VendorExtensionID == PTP_VENDOR_MICROSOFT) {
    // MTP 모드로 시작한 카메라들
    
    if (strstr(di->Manufacturer, "Canon"))
        di->VendorExtensionID = PTP_VENDOR_CANON;
    
    if (strstr(di->Manufacturer, "Nikon"))
        di->VendorExtensionID = PTP_VENDOR_NIKON;
    
    if (a.usb_vendor == 0x4a9)  // Canon USB ID
        di->VendorExtensionID = PTP_VENDOR_CANON;
    
    if (a.usb_vendor == 0x4b0)  // Nikon USB ID
        di->VendorExtensionID = PTP_VENDOR_NIKON;
        
    if (a.usb_vendor == 0x4cb)  // Fuji USB ID
        di->VendorExtensionID = PTP_VENDOR_FUJI;
}
```

### 숨겨진 명령어 추가 (Nikon 예시)

Nikon은 일부 모델(D3xxx 시리즈)에서 명령어를 숨깁니다.
libgphoto2는 모델 번호로 감지하고 수동으로 추가:

```cpp
// D3200 시리즈
if ((nikond >= 3200) && (nikond < 3299)) {
    // 숨겨진 명령어 수동 추가
    di->Operations[di->Operations_len+0] = PTP_OC_NIKON_GetEvent;
    di->Operations[di->Operations_len+1] = PTP_OC_NIKON_InitiateCaptureRecInSdram;
    di->Operations[di->Operations_len+2] = PTP_OC_NIKON_AfDrive;
    // ... 총 14개 명령어 추가
    di->Operations_len += 14;
}
```

---

## 발견된 문제와 해결 방안

### 문제 1: PTPIP 포트 드라이버 로딩 실패

#### 증상

```
E/CameraNative: PTP/IP 포트 드라이버 찾기 실패: Unknown port (코드: -3)
E/CameraNative: IOLIBS 환경변수 확인 필요: /data/app/.../lib/arm64
```

#### 원인

- `libgphoto2_port_iolib_ptpip.so`를 찾지 못함
- 심볼릭 링크가 제대로 생성되지 않음

#### 해결

```cpp
// setupLibraryPathsOnce에서 심볼릭 링크 생성
snprintf(linkCmd, sizeof(linkCmd),
         "ln -sf %s/libgphoto2_port_iolib_ptpip.so "
         "%s/libgphoto2_port/0.12.2/ptpip.so",
         libDir, libDir);
system(linkCmd);
```

### 문제 2: Nikon STA 모드 인증 실패

#### 증상

```
I/PtpipConnectionManager: ✅ PTPIP 연결 성공
I/PtpipConnectionManager: GetDeviceInfo 요청
E/PtpipDataSource: 니콘 STA 모드 인증 실패
```

#### 원인

- STA 모드는 사용자 승인 절차 필요
- 0x935a, 0x952b 같은 비표준 명령어 필요
- 타이밍이 중요 (명령 순서, 대기 시간)

#### 해결 전략

```kotlin
// NikonAuthenticationService.kt
suspend fun performStaAuthentication(camera: PtpipCamera): Boolean {
    // 1단계: 기본 연결
    if (!connectionManager.establishConnection(camera)) return false
    
    // 2단계: 디바이스 정보 확인
    val deviceInfo = connectionManager.getDeviceInfo() ?: return false
    
    // 3단계: STA 초기화 명령 (0x952b)
    sendPtpCommand(0x952b)
    delay(500)
    
    // 4단계: 연결 승인 요청 (0x935a)
    sendPtpCommand(0x935a)
    
    // 5단계: 사용자가 카메라에서 OK 누를 때까지 대기
    // 타임아웃: 30초
    delay(30000)
    
    // 6단계: 연결 상태 재확인
    return connectionManager.getDeviceInfo() != null
}
```

### 문제 3: 타임아웃 설정

#### 증상

```
E/CameraNative: ❌ 카메라 초기화 실패: Timeout (코드: -7)
```

#### 원인별 해결

| 원인 | 기본 타임아웃 | 권장 값 |
|------|--------------|--------|
| 일반 USB | 5000ms | 5000ms (유���) |
| Wi-Fi AP 모드 | 5000ms | 10000ms |
| Wi-Fi STA 모드 | 5000ms | 15000ms |
| 느린 네트워크 | 5000ms | 20000ms |

```cpp
// camera_ptpip_setup.cpp에서 적용됨
gp_setting_set("ptp2_ip", "timeout", "10000");  // 10초
```

### 문제 4: GUID 관리

#### 기존 방식 (과도한 재설정)

```cpp
// 연결할 때마다 GUID 삭제
clearPtpipSettings();  // 매번 호출 ❌
```

#### 개선된 방식 (보존 우선)

```cpp
// 기존 GUID 확인
char guidBuf[256] = {0};
if (gp_setting_get("ptp2_ip", "guid", guidBuf) == GP_OK && guidBuf[0] != '\0') {
    LOGI("기존 GUID 유지: %s", guidBuf);
    // GUID 유지 - 페어링 정보 보존 ✅
} else {
    LOGI("GUID 없음 - 자동 생성 예정");
}

// 연결 실패 시에만 재설정
if (connect_failed && retry_with_new_guid) {
    resetPtpipGuid();  // 실패 시에만 ✅
}
```

---

## 디버깅 전략

### 1️⃣ 로그 레벨별 확인

#### Android Logcat

```bash
# 전체 PTPIP 로그
adb logcat -s CameraNative:* Ptpip*:*

# 연결 과정만
adb logcat | grep -E "init|connect|session"

# 에러만
adb logcat *:E
```

#### libgphoto2 내부 로그

```cpp
// C++ 코드에서 활성화
setenv("GP_DEBUG", "1", 1);
gp_log_add_func(GP_LOG_DEBUG, log_callback, nullptr);

// 로그 파일 위치
/data/data/com.inik.camcon/files/libgphoto2_ptpip_*.txt
```

### 2️⃣ Wireshark 패킷 캡처

Wi-Fi 연결 시 패킷을 캡처하여 분석:

```bash
# Android에서 tcpdump 사용
adb shell "su -c 'tcpdump -i wlan0 -w /sdcard/ptpip.pcap'"

# PC로 복사
adb pull /sdcard/ptpip.pcap

# Wireshark에서 필터
tcp.port == 15740
```

### 3️⃣ 제조사별 디버깅 체크리스트

#### Canon 디버깅

- [ ] 카메라가 PTP 모드인가? (MTP 아님)
- [ ] Wi-Fi 연결 상태 확인
- [ ] 기본 포트 15740 사용 확인
- [ ] USB 연결로 먼저 테스트

#### Nikon 디버깅

- [ ] AP 모드인가 STA 모드인가?
- [ ] STA 모드: 카메라 LCD에 승인 메시지 표시되는가?
- [ ] STA 모드: 같은 공유기에 연결되어 있는가?
- [ ] 카메라 Wi-Fi 설정에서 "스마트 기기와 연결" 활성화되어 있는가?
- [ ] 모델 시리즈 확인 (D3xxx는 제한적)

#### Sony 디버깅

- [ ] 카메라가 PTP 모드를 지원하는가? (많은 모델이 미지원)
- [ ] USB로 먼저 테스트 (Wi-Fi는 더 제한적)
- [ ] Alpha 시리즈인가? (더 나은 지원)
- [ ] "PC 리모트" 모드 활성화되어 있는가?

#### Fuji 디버깅

- [ ] GFX 시리즈인가? (표준 PTPIP 지원)
- [ ] X-T 시리즈인가? (자체 프로토콜, 55740 포트)
- [ ] X-A, X-E 시리즈는 원격 제어 미지원
- [ ] USB로 테스트 (Wi-Fi는 특수 처리 필요)

---

## 실전 테스트 절차

### 단계 1: USB 연결 테스트

```bash
# 먼저 USB로 작동 확인
adb shell
cd /data/local/tmp

# gphoto2 바이너리가 있다면
./gphoto2 --auto-detect
./gphoto2 --summary
./gphoto2 --capture-image
```

### 단계 2: PTPIP 포트 확인

```bash
# 카메라 IP 핑 테스트
ping 192.168.1.1

# 포트 오픈 확인
nc -zv 192.168.1.1 15740

# 또는 telnet
telnet 192.168.1.1 15740
```

### 단계 3: 간단한 연결 테스트

```kotlin
// Kotlin 코드에서
suspend fun quickPtpipTest(ip: String): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(ip, 15740), 3000)
            
            // Init Command Request 전송
            val guid = ByteArray(16) { it.toByte() }
            val clientName = "Test\u0000".toByteArray(Charsets.UTF_16LE)
            
            val packet = ByteBuffer.allocate(4 + 4 + 16 + clientName.size + 4)
            packet.order(ByteOrder.LITTLE_ENDIAN)
            packet.putInt(packet.capacity())
            packet.putInt(1) // Init_Command_Request
            packet.put(guid)
            packet.put(clientName)
            packet.putInt(0x00010001)
            
            socket.getOutputStream().write(packet.array())
            
            // 응답 읽기
            val response = ByteArray(1024)
            val bytesRead = socket.getInputStream().read(response)
            
            socket.close()
            
            // Type 2 (Init_Command_Ack) 확인
            if (bytesRead >= 8) {
                val responseType = ByteBuffer.wrap(response)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .getInt(4)
                return@withContext responseType == 2
            }
            
            false
        } catch (e: Exception) {
            Log.e(TAG, "PTPIP 테스트 실패: ${e.message}")
            false
        }
    }
}
```

### 단계 4: 전체 연결 테스트

```bash
# 앱에서 실제 연결 시도
# Logcat으로 전체 과정 추적

adb logcat -c  # 로그 클리어
# 앱에서 연결 시도
adb logcat -s CameraNative:* Ptpip*:* | tee ptpip_connection.log

# 로그 분석
grep "초기화 실패" ptpip_connection.log
grep "에러 분석" ptpip_connection.log
```

---

## 제조사별 권장 설정

### Canon

```cpp
// camera_ptpip.cpp 설정
gp_setting_set("ptp2_ip", "client_name", "Android");
gp_setting_set("ptp2_ip", "timeout", "10000");  // 10초
// GUID는 자동 생성
```

### Nikon

```cpp
// AP 모드
gp_setting_set("ptp2_ip", "client_name", "Android");
gp_setting_set("ptp2_ip", "timeout", "10000");

// STA 모드
gp_setting_set("ptp2_ip", "client_name", "Android");
gp_setting_set("ptp2_ip", "timeout", "15000");  // 15초 (승인 대기)
gp_setting_set("ptp2_ip", "auto_pairing", "1");  // 자동 페어링 시도
```

### Sony

```cpp
// 기본 설정만
gp_setting_set("ptp2_ip", "timeout", "8000");
// Sony는 원격 제어 제한적이므로 다운로드 위주
```

### Fuji

```cpp
// GFX 시리즈 (표준 PTPIP)
gp_setting_set("ptp2_ip", "client_name", "Android");
gp_setting_set("ptp2_ip", "timeout", "10000");

// X-T 시리즈는 자체 구현 필요
// 포트: 55740
// 비표준 핸드셰이크
```

---

## 참고 자료

### 공식 문서

1. **libgphoto2 GitHub**: https://github.com/gphoto/libgphoto2
2. **ptp2 드라이버**: https://github.com/gphoto/libgphoto2/tree/master/camlibs/ptp2
    - `ptp.c` - PTP 프로토콜 구현
    - `ptpip.c` - PTP/IP 네트워크 구현
    - `library.c` - 드라이버 초기화 (1000+ 라인)
    - `config.c` - 설정 관리
3. **PTP/IP 문서**: http://www.gphoto.org/doc/ptpip.php

### 프로토콜 스펙

1. **CIPA DC-005**: Picture Transfer Protocol over IP
2. **ISO 15740**: Photography — Picture transfer protocol
3. **PTP 1.0/1.1 스펙**: 표준 PTP 명령어 및 속성 정의

### 역공학 프로젝트

1. **libptp2**: https://libptp.sourceforge.net/ (독립 PTP 라이브러리)
2. **fuji-cam-wifi-tool**: https://github.com/mzealey/fuji-cam-wifi-tool (Fuji 전용)
3. **gphoto2pp**: https://github.com/maldworth/gphoto2pp (C++ 래퍼)

### 커뮤니티

1. **gphoto-devel 메일링 리스트**: gphoto-devel@lists.sourceforge.net
2. **GitHub Issues**: https://github.com/gphoto/libgphoto2/issues
3. **SourceForge 포럼**: https://sourceforge.net/p/gphoto/discussion/

---

## 요약

### 🎯 핵심 포인트

1. **카메라마다 명령어가 다릅니다**
    - Canon: 표준 PTP + EOS 확장
    - Nikon: 표준 PTP + Nikon 확장 + AP/STA 구분
    - Sony: 표준 PTP만 (제한적)
    - Fuji: 자체 프로토콜 (X 시리즈)

2. **연결 절차가 다릅니다**
    - Canon: 표준 PTPIP만으로 충분
    - Nikon AP: 표준 PTPIP만으로 충분
    - Nikon STA: 추가 인증 절차 필요
    - Fuji X: 완전히 다른 프로토콜

3. **설정 키가 제조사마다 다릅니다**
    - `ptp2_ip:guid` - 모든 제조사 공통
    - `ptp2_ip:timeout` - 모든 제조사 공통
    - `ptp2_ip:client_name` - 모든 제조사 공통
    - 추가 설정은 제조사별로 다를 수 있음

4. **libgphoto2가 자동으로 처리하는 것**
    - VendorExtensionID 감지 및 변경
    - 숨겨진 명령어 수동 추가 (일부 모델)
    - MTP → PTP 전환 (일부 모델)

---

**작성일**: 2025-01-22  
**버전**: 1.0  
**참고**: libgphoto2 2.5.32.1 기준
