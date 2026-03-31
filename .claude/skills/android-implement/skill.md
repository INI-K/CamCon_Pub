---
name: android-implement
description: "CamCon 기능 구현 스킬. 아키텍처/UI 설계 명세를 Kotlin 코드로 변환. Clean Architecture 레이어 순서(domain→data→di→presentation) 준수. '구현', '코드 작성', '기능 개발' 요청 시 반드시 사용할 것."
---

# Android Implement Skill — CamCon 기능 구현

## 목적

설계 명세를 실제 작동하는 코드로 변환한다. 레이어 경계를 지키면서 최소 변경으로 기능을 완성한다.

## 실행 절차

### 1. 명세 분석

```
Read: _workspace/02_architect_spec.md  → 신규/변경 클래스 목록, Hilt 변경사항
Read: _workspace/02_designer_spec.md   → Compose 컴포넌트, UI 상태 모델
Read: _workspace/01_planner_spec.md    → 비즈니스 규칙, 구독 경계 확인
```

### 2. 영향 파일 탐색

명세에 명시된 파일을 Read하여 현재 상태 파악:
- 수정 대상 파일은 반드시 먼저 Read
- 의존 클래스도 참조하여 인터페이스/타입 확인

### 3. 구현 순서 (레이어 의존성 준수)

```
Step 1: domain/model/          ← 신규 데이터 모델
Step 2: domain/repository/     ← Repository 인터페이스 (신규/수정)
Step 3: domain/usecase/        ← UseCase 구현
Step 4: data/datasource/       ← DataSource 구현
Step 5: data/repository/       ← Repository Impl
Step 6: di/                    ← Hilt 모듈 바인딩 추가
Step 7: presentation/viewmodel/ ← ViewModel 및 UiState
Step 8: presentation/ui/screens/ ← Compose 화면/컴포넌트
```

### 4. 구현 규칙

#### Kotlin 코드 패턴
```kotlin
// UseCase — suspend 함수 또는 Flow 반환
class {Feature}UseCase @Inject constructor(
    private val repository: {Feature}Repository
) {
    suspend operator fun invoke(...): Result<T> = ...
    // 또는
    fun invoke(...): Flow<T> = ...
}

// Repository Impl — 예외를 Result로 래핑
override suspend fun {method}(...): Result<T> = runCatching {
    // 구현
}

// ViewModel — StateFlow + SharedFlow
@HiltViewModel
class {Feature}ViewModel @Inject constructor(...) : ViewModel() {
    private val _uiState = MutableStateFlow<{Feature}UiState>(...)
    val uiState: StateFlow<{Feature}UiState> = _uiState.asStateFlow()
}
```

#### Compose 컴포넌트
```kotlin
@Composable
fun {Feature}Screen(
    viewModel: {Feature}ViewModel = hiltViewModel(),
    onNavigate: (...) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // UI 구현
}
```

#### Hilt 모듈 추가
```kotlin
// AppModule.kt 또는 RepositoryModule.kt에 추가
@Provides
@Singleton
fun provide{Feature}(...): {Interface} = {Impl}(...)

// RepositoryModule.kt — 인터페이스 바인딩
@Binds
@Singleton
abstract fun bind{Feature}Repository(impl: {Feature}RepositoryImpl): {Feature}Repository
```

### 5. 빌드 검증

```bash
# 단위 테스트 실행
./gradlew :app:testDebugUnitTest

# 빌드만 확인 (테스트 없이 빠른 검증)
./gradlew :app:compileDebugKotlin
```

빌드 실패 시:
- 컴파일 에러: 타입 불일치, import 누락 확인
- Hilt 에러: 모듈 바인딩, `@Singleton` 스코프 충돌 확인
- 테스트 실패: 기존 테스트 깨짐 여부 확인

### 6. 구현 로그 작성

출력 파일: `_workspace/02_implementer_log.md`

```markdown
# 구현 로그: {기능명}

## 변경 파일 목록

### 신규 생성
- `{패키지 경로}/{파일명}.kt` — {역할}

### 수정
- `{패키지 경로}/{파일명}.kt` — {변경 내용 한 줄}

### Hilt 모듈 변경
- `di/AppModule.kt` — {추가된 바인딩}

## 빌드 결과
- 컴파일: 성공 / 실패: {오류}
- 단위 테스트: 성공 {N}개 / 실패 {N}개

## 리뷰어/테스터 주의사항
- {주의사항 1}
- {주의사항 2}
```

## CamCon 도메인 참조

| 도메인 | 기존 패턴 참조 파일 |
|--------|-------------------|
| 카메라 연결 | `data/datasource/usb/UsbCameraManager.kt` |
| PTP/IP | `data/datasource/ptpip/PtpipDataSource.kt` |
| 라이브뷰 | `data/datasource/nativesource/NativeCameraDataSource.kt` |
| 인증 | `data/repository/AuthRepositoryImpl.kt` |
| 구독 | `data/repository/SubscriptionRepositoryImpl.kt` |
| ViewModel 패턴 | `presentation/viewmodel/CameraViewModel.kt` |
| Compose 화면 | `presentation/ui/screens/CameraControlScreen.kt` |
