# ADR-003: Unsupported ShootingMode 에러 처리 전략

**날짜**: 2026-04-22  
**상태**: Accepted

## 컨텍스트

CamCon 앱이 지원하지 않는 촬영 모드(BURST, TIMELAPSE, BRACKETING, BULB)에서 사용자가 촬영을 시도할 때, 애플리케이션의 처리 방식이 불명확했다. 기존에는 `CameraRepositoryImpl`에서 묵음 실패(silent failure)를 하고 있어서 사용자가 촬영이 실패했는지 인식할 수 없었다.

### 문제점

1. **사용자 피드백 부재**: 미지원 모드 사용 시 앱이 아무 반응을 보이지 않음
2. **디버깅 어려움**: 로그에도 명시적인 에러 메시지가 없음
3. **아키텍처 위반**: Data 레이어에서 직접 조용히 실패를 처리

## 결정

### 1. Domain 레이어에 도메인 예외 정의

Domain 모델에 `UnsupportedShootingModeException` 정의:
- 지원하지 않는 촬영 모드 사용 시 명시적으로 발생
- `CameraException`을 상속받아 카메라 관련 예외 계층 구조 확보

### 2. Repository에서 예외 발생

`CameraRepositoryImpl.capturePhoto()` 호출 시, 지원하지 않는 모드 감지 시 예외 발생:
```kotlin
if (shootingMode.isSupported().not()) {
    throw UnsupportedShootingModeException(shootingMode.name)
}
```

### 3. ViewModel에서 예외 처리 및 UI 표시

`CameraViewModel`의 `capturePhoto()` 메서드에서:
- 예외를 `catch`하여 `_uiEvent: SharedFlow<CameraUiEvent>`로 방출
- Presentation 레이어 컴포넌트에서 이벤트 구독

### 4. Composable 컴포넌트로 에러 UI 표시

`UnsupportedShootingModeSnackbar` 컴포넌트에서 SnackBar 형태로 사용자에게 피드백 제공.

## 결과

### 긍정적 결과

1. **명확한 에러 전파**: Domain → Presentation의 명확한 예외 경로
2. **사용자 경험 개선**: SnackBar를 통한 즉각적인 피드백
3. **테스트 용이성**: `UnsupportedShootingModeException` 발생을 단위 테스트로 검증 가능
4. **확장성**: 향후 새로운 미지원 모드 추가 시 동일 패턴 적용 가능

### 부정적 결과 / 주의사항

1. **Exception 오버헤드**: 예상 가능한 조건을 예외로 처리하므로 성능상 약간의 오버헤드 가능
2. **메시지 현지화**: 예외 메시지의 다국어 처리는 별도 관리 필요

## 이행 현황

- 2026-04-22: 모든 변경사항 구현 및 테스트 완료
  - `domain/model/CameraException.kt` + `UnsupportedShootingModeException`
  - `domain/model/ShootingMode.kt`에 `isSupported()` 헬퍼 메서드 추가
  - `CameraRepositoryImpl.capturePhoto()` 모드 검증 로직
  - `presentation/ui/screens/components/UnsupportedShootingModeSnackbar.kt` UI 구현

## 참고

- 관련 이슈: W2 (미구현 촬영 모드)
- 관련 파일:
  - `/app/src/main/java/com/inik/camcon/domain/model/CameraException.kt`
  - `/app/src/main/java/com/inik/camcon/presentation/ui/screens/components/UnsupportedShootingModeSnackbar.kt`
