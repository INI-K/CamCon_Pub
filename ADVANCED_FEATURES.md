# 🎥 CamConT 고급 촬영 기능 가이드

libgphoto2의 고급 기능들을 활용한 전문가급 촬영 기능들입니다.

## 📋 목차

1. [Trigger Capture](#1-trigger-capture)
2. [Bulb 모드](#2-bulb-모드-장노출-촬영)
3. [인터벌 촬영 / 타임랩스](#3-인터벌-촬영--타임랩스)
4. [비디오 녹화](#4-비디오-녹화)
5. [고급 AF 설정](#5-고급-af-설정)

---

## 1. Trigger Capture

카메라가 자체적으로 촬영을 트리거하도록 지시하는 기능입니다. 테더 촬영에 유용합니다.

### ✅ 지원 카메라

- Canon EOS 시리즈 (대부분)
- Nikon DSLR (D700, D800, D850 등)
- Sony Alpha (A7 시리즈)

### 📱 사용 방법

```kotlin
// Kotlin에서 호출
val result = CameraNative.triggerCapture()
if (result == 0) {
    println("✅ Trigger Capture 성공")
} else {
    println("❌ 실패: $result")
}
```

### 💡 활용 사례

- 스튜디오 촬영에서 여러 카메라 동시 제어
- 외부 트리거와 연동
- 자동화된 촬영 워크플로우

---

## 2. Bulb 모드 (장노출 촬영)

셔터를 열어둔 채로 지정된 시간 동안 노출하는 장노출 촬영 기능입니다.

### ✅ 지원 카메라

- Canon EOS (Bulb 설정 지원 모델)
- Nikon DSLR (D600, D7100, D5200 이상)
- Sony Alpha (일부 모델)

### 📱 사용 방법

#### 방법 1: 수동 제어

```kotlin
// Bulb 모드 시작
CameraNative.startBulbCapture()

// 촬영 실행
CameraNative.capturePhoto()

// 원하는 시간 대기...
delay(30000) // 30초

// Bulb 모드 종료
CameraNative.endBulbCapture()
```

#### 방법 2: 자동 타이머 (권장)

```kotlin
// 30초 동안 자동 노출
val result = CameraNative.bulbCaptureWithDuration(30)
if (result == 0) {
    println("✅ 30초 장노출 촬영 완료")
}
```

### 💡 활용 사례

- **별 궤적 촬영**: 30분~1시간 장노출
- **야경 촬영**: 5~30초 노출로 빛 표현
- **Light Painting**: 수초~수분 동안 빛으로 그림 그리기
- **폭포/물 흐름**: 부드러운 물결 표현

### ⚙️ 권장 설정

- ISO: 100-400 (낮을수록 좋음)
- 조리개: f/8-f/16 (별 촬영은 f/2.8-f/4)
- 삼각대 필수
- 미러 업 활성화 (흔들림 방지)

---

## 3. 인터벌 촬영 / 타임랩스

일정 간격으로 자동 촬영하여 타임랩스 영상을 만드는 기능입니다.

### ✅ 지원 카메라

- 모든 libgphoto2 지원 카메라

### 📱 사용 방법

```kotlin
// 5초 간격으로 100장 촬영
val result = CameraNative.startIntervalCapture(
    intervalSeconds = 5,
    totalFrames = 100
)

if (result == 0) {
    println("✅ 인터벌 촬영 시작")
    
    // 진행 상황 확인
    val status = CameraNative.getIntervalCaptureStatus()
    // status[0]: 실행 중 여부 (1=실행중, 0=중지)
    // status[1]: 촬영 완료된 프레임 수
    // status[2]: 총 프레임 수
    
    println("진행: ${status[1]}/${status[2]} (${status[0]})")
}

// 중단하려면
CameraNative.stopIntervalCapture()
```

### 💡 활용 사례

#### 1. 일출/일몰 타임랩스

- 간격: 3-5초
- 총 프레임: 300-500장
- 예상 시간: 15-40분
- 최종 영상: 10-20초 (30fps 기준)

#### 2. 구름 흐름

- 간격: 2-3초
- 총 프레임: 600-1000장
- 예상 시간: 20-50분

#### 3. 도시 야경 (차량 흐름)

- 간격: 1-2초
- 총 프레임: 1000-2000장
- 예상 시간: 30-60분

#### 4. 식물 성장

- 간격: 300초 (5분)
- 총 프레임: 1440장 (5일)
- 예상 시간: 5일

### ⚙️ 타임랩스 계산

```
최종 영상 길이 (초) = 총 프레임 수 / FPS
촬영 소요 시간 (초) = 총 프레임 수 × 간격 (초)

예) 30fps, 10초 영상을 만들려면:
- 필요 프레임: 30 × 10 = 300장
- 간격 5초 → 촬영 시간: 300 × 5 = 1500초 = 25분
```

---

## 4. 비디오 녹화

카메라를 통한 비디오 녹화 기능입니다.

### ✅ 지원 카메라

- Canon EOS (최신 모델)
- Nikon DSLR (무비 모드 지원 모델)
- Panasonic GH 시리즈
- Sony Alpha (일부 모델)

### 📱 사용 방법

```kotlin
// 녹화 시작
val result = CameraNative.startVideoRecording()
if (result == 0) {
    println("🎥 녹화 시작")
}

// 녹화 중 확인
if (CameraNative.isVideoRecording()) {
    println("⏺️  녹화 중...")
}

// 녹화 중지
CameraNative.stopVideoRecording()
println("⏹️  녹화 중지")
```

### 💡 활용 사례

- 인터뷰 촬영
- 이벤트 기록
- 제품 리뷰 영상
- 원격 모니터링

### ⚠️ 주의사항

- 카메라 모델에 따라 지원 여부 다름
- 녹화된 비디오는 카메라 SD 카드에 저장됨
- 배터리 소모가 큼

---

## 5. 고급 AF 설정

정밀한 오토포커스 제어 기능입니다.

### ✅ 지원 카메라

- Canon EOS (최신 모델)
- Nikon DSLR (D500 이상)
- Sony Alpha (A7 III 이상)
- Fuji X 시리즈

### 📱 사용 방법

#### AF 모드 설정

```kotlin
// 단일 AF
CameraNative.setAFMode("One Shot")

// 연속 AF
CameraNative.setAFMode("AI Servo")

// 현재 AF 모드 확인
val currentMode = CameraNative.getAFMode()
println("현재 AF 모드: $currentMode")
```

#### AF 영역 설정

```kotlin
// 중앙 영역에 AF 포인트 설정
CameraNative.setAFArea(
    x = 1920,      // 중심 X 좌표
    y = 1080,      // 중심 Y 좌표
    width = 200,   // 영역 너비
    height = 200   // 영역 높이
)
```

#### 수동 포커스 드라이브

```kotlin
// 가까운 쪽으로 10 스텝 이동
CameraNative.driveManualFocus(10)

// 먼 쪽으로 10 스텝 이동
CameraNative.driveManualFocus(-10)
```

### 💡 활용 사례

- 정밀한 제품 촬영
- 인물 사진에서 정확한 눈 초점
- 매크로 촬영에서 미세 조정
- 동영상 촬영 시 수동 포커스 풀링

---

## 🔧 에러 코드

모든 네이티브 함수는 정수 반환 값을 가집니다:

- `0` (GP_OK): 성공
- `-1` ~ `-999`: libgphoto2 에러 코드
    - `-6`: 지원되지 않는 기능
    - `-7`: I/O 에러
    - `-2`: 잘못된 파라미터
    - `-53`: 카메라가 busy 상태

```kotlin
fun handleResult(result: Int) {
    when (result) {
        0 -> println("✅ 성공")
        -6 -> println("❌ 이 카메라는 해당 기능을 지원하지 않습니다")
        -7 -> println("❌ 통신 오류")
        -53 -> println("❌ 카메라가 busy 상태입니다. 잠시 후 다시 시도하세요")
        else -> println("❌ 오류 발생: $result")
    }
}
```

---

## 📚 추가 참고 자료

- [libgphoto2 공식 문서](http://www.gphoto.org/doc/)
- [gphoto2 명령어 가이드](http://www.gphoto.org/doc/manual/)
- [PTP 프로토콜 스펙](http://www.cipa.jp/std/documents/e/DC-001-2003_E.pdf)

---

## 🤝 기여하기

새로운 기능 제안이나 버그 리포트는 GitHub Issues로 부탁드립니다!

**CamConT** - 전문가를 위한 카메라 제어
