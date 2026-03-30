---
name: android-arch-design
description: "CamCon Clean Architecture 설계 스킬. UseCase/Repository/DataSource 클래스 설계, Hilt 모듈 의존성 그래프, Coroutines Flow 데이터 흐름, JNI 영향도 분석. '아키텍처 설계', 'UseCase 설계', 'Hilt 모듈', '데이터 흐름' 요청 시 반드시 사용할 것. 추가로 android-architecture 스킬 병행 참조."
---

# Android Architecture Design Skill — CamCon 아키텍처 설계

## 목적

기획 명세와 UI 계약을 CamCon Clean Architecture 클래스 설계로 전환한다.

> 이 스킬 실행 전 `Skill 도구로 android-architecture 호출`하여 Clean Architecture 원칙 내면화.

## 실행 절차

### 1. 기존 구조 파악
- `di/AppModule.kt`, `di/RepositoryModule.kt` 확인
- 관련 UseCase, Repository 인터페이스, DataSource 읽기
- JNI 관련 시: `CameraNative.kt`, `NativeCameraDataSource.kt` 확인

### 2. 아키텍처 명세 작성

출력 파일: `_workspace/02_architect_spec.md`

```markdown
# 아키텍처 설계 명세: {기능명}

## 레이어별 변경 요약

| 레이어 | 신규 | 변경 | 삭제 |
|--------|------|------|------|
| domain/usecase | | | |
| domain/model | | | |
| domain/repository | | | |
| data/repository | | | |
| data/datasource | | | |
| di | | | |

## 신규/변경 클래스 명세

### {ClassName}
- **경로**: com.inik.camcon.{layer}.{package}.{ClassName}
- **책임**: (단일 책임 원칙 기준 1~2문장)
- **인터페이스**:
  ```kotlin
  interface/class {ClassName} {
      suspend fun {method}({params}): {returnType}
      fun {flowMethod}(): Flow<{type}>
  }
  ```
- **의존성**: {주입받는 클래스 목록}

## Hilt 모듈 변경

모듈 파일: di/{ModuleName}.kt
변경 내용:
```kotlin
@Provides
@Singleton
fun provide{Type}(...): {Interface} = {Impl}(...)
```

## Flow 데이터 흐름

```
NativeCameraDataSource / UsbCameraManager / Firebase
    ↓ (Flow<{Type}>)
{RepositoryImpl}
    ↓ (Flow<{DomainModel}>)
{UseCase}
    ↓ (StateFlow / SharedFlow)
{ViewModel}
    ↓ (UiState)
{Screen Composable}
```

## JNI 영향도

- 변경 필요: 없음 | 있음
- 있을 경우: 변경 대상 함수, 영향받는 CameraNative.kt 메서드

## ViewModel 계약 (디자이너에게)

```kotlin
@HiltViewModel
class {Name}ViewModel @Inject constructor(...) : ViewModel() {
    val uiState: StateFlow<{UiState}> = ...
    val events: SharedFlow<{Event}> = ...
    fun onEvent(event: {UiEvent}) { ... }
}
```

## 테스터에게 (핵심 테스트 포인트)
- {UseCase명}: {테스트해야 할 핵심 로직}
- Fake 필요 DataSource: {목록}
```

### 3. 설계 원칙 체크

- [ ] domain 레이어에 `android.*` import 없음
- [ ] 모든 UseCase는 단일 `operator fun invoke` 또는 명확한 메서드명 1개
- [ ] Repository impl은 domain interface를 구현
- [ ] Hilt `@Singleton` 스코프 남용 없음 (Camera 관련은 연결 라이프사이클 주의)
- [ ] coroutineScope / supervisorScope 선택 근거 명시

## CamCon 레이어 경계 참조

```
금지: data → domain 방향 의존 (역방향)
금지: domain에서 Context, Activity 참조
허용: domain/usecase에서 domain/repository 인터페이스 사용
허용: data/repository에서 여러 DataSource 조합
```
