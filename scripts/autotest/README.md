# CamCon 자율 테스트 하네스

카메라↔안드로이드 디바이스를 **USB 또는 Wi-Fi 로 연결만 해두면**, 사람이 화면을 만지지
않고 `adb` 만으로 설치→권한부여→앱 구동→**logcat 단언**까지 자동 수행한다.

## 동작 원리

CamCon 의 연결/촬영/라이브뷰 파이프라인은 모든 단계에서 명확한 로그를 남긴다
(`PtpipConnectionManager`, `USB카메라매니저`, `카메라캡처레포`, …). 이 하네스는 그
**실제 로그 문자열을 단언 오라클**로 사용한다 — 화면 픽셀이 아니라 동작의 증거를 본다.
화면 조작(촬영 버튼 탭 등)은 접근성 트리(`uiautomator`) 기반 best-effort 이며, 합격/불합격
판정은 전적으로 logcat 으로 한다.

## 전제 조건 (중요)

| 항목 | 요구 | 이유 |
|------|------|------|
| Android | **API 29 이상** | CamCon `minSdk 29`. 미만이면 설치 자체 불가(`INSTALL_FAILED_OLDER_SDK`) |
| ABI | **arm64-v8a** | 앱은 arm64-v8a 전용 |
| 연결 | adb (USB 또는 `adb connect <ip>:5555`) | 무인 구동 |
| `usb` 테스트 | 카메라를 폰에 **USB-OTG** 로 연결 + USB host 지원 폰 | USB 경로 |
| `wifi` 테스트 | 폰을 **카메라 AP/핫스팟**에 연결 + WiFi HW | PTP-IP 경로 |
| `mock` 테스트 | **카메라 불필요** | 내장 Mock 카메라 모드 |

> 먼저 항상 `doctor` 로 디바이스 호환성을 확인하라. 비호환 디바이스는 `doctor` 가 즉시 가려낸다.

## 사용법

```bash
cd /Users/ini-k/CamCon
export JAVA_HOME=/Users/ini-k/Library/Java/JavaVirtualMachines/jbr-21.0.9/Contents/Home
./gradlew assembleDebug            # APK 준비 (한 번)

scripts/autotest/camcon_autotest.sh doctor    # 0) 환경/디바이스 호환성 점검
scripts/autotest/camcon_autotest.sh install   # 1) APK 설치 + 런타임 권한 일괄부여
scripts/autotest/camcon_autotest.sh mock      # 2) [카메라 없이] Mock 촬영 파이프라인
scripts/autotest/camcon_autotest.sh usb       # 3) [USB 카메라 연결 후] 감지→연결→촬영
scripts/autotest/camcon_autotest.sh wifi      # 4) [WiFi 카메라 연결 후] 검색→연결→촬영
scripts/autotest/camcon_autotest.sh all       # 가능한 것 순차 실행
```

### 환경변수
- `ADB=/path/to/adb` — adb 경로 강제(미지정 시 자동탐지)
- `SERIAL=<device>` — 디바이스 여러 개일 때 대상 지정
- `TIMEOUT=30` — logcat 단언 타임아웃(초)

## 단언 오라클 (실제 로그 마커)

| 단계 | 성공 마커(정규식) |
|------|------------------|
| USB 연결 | `새 USB 연결 성공` / `USB 연결 상태 검증 완료` / `카메라 기능 정보 업데이트 완료` |
| PTP-IP 연결 | `PTPIP 연결 성공` / `PTP-IP 연결 성공` / `카메라 검색 완료` / `카메라 발견` |
| 촬영/수신 | `다운로드 완료` / `파일 수신` / `촬영 … 완료` |
| Mock | `MockCameraViewModel` / `MockCameraActivity` |

## 알려진 한계

- **USB 권한 다이얼로그**: 디바이스 동의가 필요한 1회성 팝업. 하네스가 "항상 허용"+"확인"을
  접근성 텍스트로 자동 탭 시도하지만, OEM 스킨에 따라 라벨이 달라 실패할 수 있다. 그 경우
  앱의 USB intent-filter(연결 시 자동 실행) + "기본 앱으로 열기" 체크를 1회 수동 허용하면
  이후 무인 동작한다.
- **`uiautomator dump` idle 실패**: 화면에 연속 애니메이션이 있으면 트리 추출이 실패할 수
  있다(`--compressed`+재시도로 완화). 단언은 logcat 이므로 트리거만 영향받는다.
- **Compose 라벨**: 촬영/검색 버튼의 접근성 텍스트가 코드와 다르면 탭이 빗나갈 수 있다.
  실패 시 `/tmp/camcon_*.png` 스크린샷으로 라벨을 확인해 마커를 보정하라.
