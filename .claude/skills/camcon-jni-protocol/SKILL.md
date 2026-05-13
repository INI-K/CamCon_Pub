---
name: camcon-jni-protocol
description: CamCon JNI 계층 / libgphoto2 / native-lib.cpp / USB OTG / PTP-IP 프로토콜 작업 시 사용. CameraNative.kt external 시그니처 변경, app/src/main/cpp/*.cpp 수정, libgphoto2 .so 업데이트, 16KB 페이지 호환, arm64-v8a 빌드, 카메라 드라이버(camlib) 추가, GlobalRef·메모리 관리, JNI 에러 코드 처리, libusb/libltdl 동적 로딩 이슈를 다룰 때 반드시 로드.
---

# CamCon JNI / libgphoto2 프로토콜

CamCon의 네이티브 계층은 **libgphoto2를 안드로이드용으로 외부 크로스 컴파일하여 `.so`로 체크인**하는 구조다. 이 스킬은 JNI 인터페이스와 libgphoto2 빌드·업데이트 절차를 정리한다.

## 파일 배치

```
app/src/main/java/com/inik/camcon/CameraNative.kt   # 코틀린 싱글톤, 80+ external
app/src/main/cpp/
    native-lib.cpp                                   # JNI 엔트리포인트
    camera_capture.cpp                               # 촬영
    camera_liveview.cpp                              # 라이브뷰
    camera_ptpip.cpp / camera_ptpip_*.cpp            # PTP-IP
    camera_init_usb.cpp / camera_init.cpp            # 초기화
    camera_events.cpp                                # 이벤트 큐
    camera_command_queue.{cpp,h}                     # 명령 직렬화
    camera_file_saver.{cpp,h}                        # 파일 저장
    color_transfer_native.cpp / yuv-decoder.c        # 색공간 / YUV
    ... (총 29개)
    include/                                         # 헤더
    CMakeLists.txt
    so_list.h.in
app/src/main/jniLibs/arm64-v8a/
    libgphoto2.so, libgphoto2_port.so, libltdl.so   # 코어 3개
    libgphoto2_camlib_*.so (19개)                    # 카메라 드라이버
    libgphoto2_port_iolib_*.so (5개)                 # USB/PTPIP iolib
```

## JNI 시그니처 동기화 원칙

**`CameraNative.kt`의 external 함수와 `cpp/*.cpp`의 JNI 진입점은 같은 PR에서 동기화 변경한다.** 둘 중 하나만 바뀌면 런타임에 `UnsatisfiedLinkError`로 폭발한다.

체크리스트:
1. external 시그니처 변경 시 JNI 함수명 규칙 준수: `Java_com_inik_camcon_CameraNative_<함수명>`.
2. 인자/반환 타입의 JNI 시그니처(`(Ljava/lang/String;I)V` 등) 정확히 매칭.
3. 새 진입점 추가 시 `native-lib.cpp` 또는 도메인별 cpp 파일에 구현. 추가 후 `CMakeLists.txt` 빌드 대상 갱신 필요 여부 확인.
4. 빌드 검증: `./gradlew assembleDebug` 또는 최소 `compileDebugKotlin` 실행.

## 메모리 / 참조 규약

- **Local ref**는 JNI 호출 종료 시 자동 해제. 같은 호출 안에서 다수 생성 시 `PushLocalFrame` / `PopLocalFrame` 고려.
- **Global ref**는 명시적으로 `NewGlobalRef` / `DeleteGlobalRef` 짝 맞춰 관리. 콜백 핸들로 보관할 때만 사용.
- 콜백에서 JVM에 attach할 때 `AttachCurrentThread` / `DetachCurrentThread` 짝.
- libgphoto2 에러 코드는 `gp_result_as_string`으로 변환 후 `ErrorHandlingManager`로 전달.

## libgphoto2 빌드 (외부)

**원칙:** libgphoto2 소스는 CamCon 저장소에 포함하지 않는다. 업스트림([github.com/gphoto/libgphoto2](https://github.com/gphoto/libgphoto2))을 별도 디렉토리에서 Android NDK로 크로스 컴파일하여 `.so`만 체크인한다.

업데이트 절차:
1. 업스트림 최신 태그 체크아웃.
2. Android NDK 툴체인으로 `arm64-v8a` 크로스 컴파일.
3. **링커 플래그 필수:** `-Wl,-z,max-page-size=16384` (Android 15+ 16KB 페이지 호환).
4. 산출물 교체:
   - 코어 3개: `libgphoto2.so`, `libgphoto2_port.so`, `libltdl.so`
   - camlib `libgphoto2_camlib_*.so` (신규 카메라 추가 시 변동)
   - port iolib `libgphoto2_port_iolib_*.so`
5. `CameraNative.kt` external 시그니처 동기화 (libgphoto2 API 변경 추적).
6. `./gradlew assembleDebug` 로 링킹 검증.
7. 실물 카메라(또는 PTP-IP 시뮬레이션)에서 연결 확인.

## 16KB 페이지 호환

- Android 15+ 일부 디바이스는 16KB 페이지를 사용. 4KB 페이지로 빌드된 `.so`는 로드 실패.
- 모든 사내·업스트림 `.so`를 `-Wl,-z,max-page-size=16384`로 빌드.
- 검증: `readelf -l libgphoto2.so | grep LOAD` → `align (16384)` 표시 확인.

## arm64-v8a 전용

- 다른 ABI(armeabi-v7a, x86_64) 추가 금지. `app/build.gradle`의 `ndk.abiFilters` 또는 splits 설정에 `'arm64-v8a'`만 포함.
- 에뮬레이터에서 USB 카메라 테스트 불가 — 실물 장비 필요.

## 동적 로딩 (camlib / iolib)

libgphoto2는 카메라 드라이버를 **런타임에 동적 로딩**한다. 즉 `libgphoto2.so` 자체에 camlib이 정적 링크되어 있지 않다.

- 앱이 시작될 때 `CameraNative.init`에서 `jniLibs/arm64-v8a/` 경로를 `libgphoto2_setting`로 설정해야 동적 로딩이 작동.
- camlib `.so`가 빠지면 해당 카메라 모델은 인식되지 않음 (다른 모델은 정상).
- 신규 카메라 지원 시 업스트림에 camlib이 있는지 먼저 확인 → 있으면 .so 빌드 후 `jniLibs/arm64-v8a/`에 추가.

## PTP-IP 프로토콜

- 표준 포트: **15740/tcp** (제어), **5353/udp** (mDNS), **15740/udp** (디스커버리).
- `PtpipDiscoveryService`는 UDP 멀티캐스트로 카메라 발견.
- `PtpipConnectionManager`는 TCP 세션 유지, 명령/응답/이벤트 채널 분리.
- 일부 카메라(Nikon)는 인증 핸드셰이크 필요 — `camera_nikon_auth.cpp` 참조.

## 디버그 팁

- libgphoto2 verbose 로그: `gp_log_add_func` 등록 후 logcat으로 펌프 (로그가 매우 시끄러우니 release 빌드에서 비활성).
- 카메라 연결 실패 시 우선 확인:
  1. USB 권한 (UsbManager 권한 인텐트 수신)
  2. abilities 매칭 (`camera_abilities.cpp`)
  3. iolib `.so` 존재 여부
- PTP-IP 끊김은 keepalive 타임아웃이 대부분 — `PtpipConnectionManager`의 heartbeat 검토.

## 자주 깨지는 부분 (회귀 주의)

- **WifiSuggestionBroadcastReceiver의 goAsync 패턴** (C-2 이슈, 해소됨) — broadcast receiver에서 비동기 작업은 반드시 `goAsync()` + `pendingResult.finish()`.
- **EXIF orientation 회전** (C7, 해소됨) — 90/270도일 때 width/height 교환, 33개 EXIF 태그 보존.
- **processedFiles OOM** (C5) — LRU 1000개 제한. 더 늘리지 말 것.

## 외부 참조

- libgphoto2 문서: http://www.gphoto.org/doc/
- libgphoto2 GitHub: https://github.com/gphoto/libgphoto2
- PTP/IP 사양: ISO 15740
