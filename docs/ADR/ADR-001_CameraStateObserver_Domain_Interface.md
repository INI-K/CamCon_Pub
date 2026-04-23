# ADR-001: CameraStateObserver 도메인 인터페이스 도입

**날짜**: 2026-04-21  
**상태**: Accepted  
**Issue**: H-1

## 컨텍스트

Clean Architecture의 핵심 원칙은 계층 간 의존성 방향 준수이다: **Presentation → Domain ← Data**.

그러나 `CameraUiStateManager`는 다음과 같은 구조 문제를 가지고 있었다:
- **위치**: `presentation/viewmodel/state/CameraUiStateManager.kt` (Presentation 레이어)
- **의존성**: Data 레이어의 5개 컴포넌트에 직접 주입됨:
  - `NativeCameraDataSource`
  - `PtpipDataSource`
  - `UsbDataSource`
  - `PhotoDownloadManager`
  - `CameraRepositoryImpl`

이는 **역방향 의존성** 패턴을 발생시킨다: `Data → Presentation` (위반)

결과:
- Data 레이어 컴포넌트들이 Presentation의 구체 클래스에 의존
- UI 상태 관리가 data 처리 레이어에 강하게 결합
- 테스트 시 mock 주입 불가능 (구체 클래스 의존)

## 결정

**의존성 역전 원칙(DIP)** 을 적용하여 도메인 인터페이스를 도입한다:

### 1. CameraStateObserver 인터페이스 정의 (Domain 레이어)

```kotlin
// domain/manager/CameraStateObserver.kt
interface CameraStateObserver {
    fun updateCameraAbilities(abilities: CameraAbilitiesInfo)
    fun updateCameraCapabilities(capabilities: CameraCapabilities?)
    fun updateCameraInitialization(initialized: Boolean)
    fun showCameraStatusCheckDialog(show: Boolean)
    fun updatePtpipConnectionState(connected: Boolean)
    fun updatePtpSessionState(state: PtpSessionState)
}
```

### 2. CameraUiStateManager가 인터페이스 구현

```kotlin
// presentation/viewmodel/state/CameraUiStateManager.kt
@Singleton
class CameraUiStateManager @Inject constructor(
    // ... 기존 필드들
) : CameraStateObserver {
    override fun updateCameraAbilities(abilities: CameraAbilitiesInfo) {
        // 기존 로직
    }
    // ... 나머지 6개 메서드 구현
}
```

### 3. Hilt 바인딩 (RepositoryModule)

```kotlin
// di/RepositoryModule.kt
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindCameraStateObserver(
        manager: CameraUiStateManager
    ): CameraStateObserver
}
```

### 4. Data 레이어는 도메인 인터페이스에 의존

```kotlin
// data/datasource/nativesource/NativeCameraDataSource.kt
class NativeCameraDataSource @Inject constructor(
    private val observer: CameraStateObserver  // 구체 클래스 아님
) { ... }
```

## 결과

### 긍정적 영향
- ✅ **의존성 방향 준수**: Data → Domain ← Presentation (올바른 구조)
- ✅ **테스트 가능성 향상**: Data 레이어 단위 테스트 시 CameraStateObserver Mock 주입 가능
- ✅ **결합도 감소**: Data 레이어가 Presentation 구체 클래스에 더 이상 의존하지 않음
- ✅ **SRP 준수**: 상태 업데이트 책임이 인터페이스로 명시적화
- ✅ **향후 확장성**: 다른 관찰자 구현 추가 가능 (logging, analytics 등)

### 구현 세부사항
- **6개 메서드**: `updateCameraAbilities`, `updateCameraCapabilities`, `updateCameraInitialization`, `showCameraStatusCheckDialog`, `updatePtpipConnectionState`, `updatePtpSessionState`
- **파일 생성**: `domain/manager/CameraStateObserver.kt`
- **파일 수정**: `CameraUiStateManager.kt` (인터페이스 구현), `RepositoryModule.kt` (@Binds 추가), 5개 DataSource 클래스 (의존성 타입 변경)

### 검증
- **Kotlin 컴파일**: ✅ BUILD SUCCESSFUL
- **Hilt 어노테이션 처리**: ✅ 성공
- **기존 코드 호환성**: ✅ 유지 (CameraUiStateManager는 @Singleton 바인딩)

## 대안 검토

1. **Service Locator 패턴**: 권고하지 않음 (Hidden Dependencies, 테스트 어려움)
2. **Callback + Observer 분리**: 검토했으나 6개 메서드 통합이 명확함
3. **Repository 패턴 강화**: 고려했으나 상태 객체 업데이트가 주요 요구사항

## 관련 이슈

- CLAUDE.md §3.2 — "알려진 아키텍처 위반" 항목 제거
- Clean Architecture 준수 확인
- Data 레이어 단위 테스트 기반 마련

## 승인자

- Code Review: ✅ Architecture adherence verified
- Security Review: N/A (도메인 인터페이스만 해당)
- Performance Review: ✅ No runtime overhead

---

**구현 일자**: 2026-04-21  
**상태**: Production  
**변경 영향도**: 5개 DataSource 클래스, 1개 RepositoryModule 수정  
**롤백 난이도**: 중간 (interface 제거 후 구체 클래스 주입 복귀)
