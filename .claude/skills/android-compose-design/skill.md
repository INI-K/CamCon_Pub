---
name: android-compose-design
description: "CamCon Jetpack Compose UI 설계 스킬. 컴포넌트 계층 설계, UI 상태 모델, Material3 적용, 상태 호이스팅 경계 결정. 'Compose 설계', 'UI 컴포넌트 설계', '화면 설계', 'UI 상태' 요청 시 반드시 사용할 것. 추가로 compose-ui 스킬도 병행 참조."
---

# Android Compose Design Skill — CamCon UI 설계

## 목적

기획 명세를 Jetpack Compose 컴포넌트 계층과 UI 상태 모델로 변환한다.

> 이 스킬 실행 전 `Skill 도구로 compose-ui 호출`하여 Compose 베스트 프랙티스 내면화.

## 실행 절차

### 1. 기획 명세 분석
- `_workspace/01_planner_spec.md` 읽기
- 영향받는 기존 화면 파악: `presentation/ui/screens/`
- 재사용 가능한 기존 컴포넌트 확인: `presentation/ui/screens/components/`

### 2. 컴포넌트 계층 설계

출력 파일: `_workspace/02_designer_spec.md`

```markdown
# UI 설계 명세: {기능명}

## 컴포넌트 계층

{ScreenName}Screen(
  uiState: {ScreenName}UiState,
  onEvent: ({ScreenName}UiEvent) -> Unit
)
  ├── {ComponentA}(...)
  │   ├── {SubComponentA1}(...)
  │   └── {SubComponentA2}(...)
  └── {ComponentB}(...)

## UI 상태 정의

data class {ScreenName}UiState(
    val isLoading: Boolean = false,
    val {데이터필드}: {타입} = {기본값},
    val error: String? = null
)

sealed interface {ScreenName}UiEvent {
    data class {Action}(...) : {ScreenName}UiEvent
    object {SimpleAction} : {ScreenName}UiEvent
}

## 신규 / 변경 Composable 목록

| Composable | 책임 | 신규/변경 |
|------------|------|----------|
| {Name}     | {역할} | 신규 |

## 공통 컴포넌트 재사용

기존 컴포넌트 재사용: {있음/없음}
- {기존 컴포넌트 경로}: {재사용 방식}

## Material3 컴포넌트 매핑

| UI 요소 | Material3 컴포넌트 |
|---------|-----------------|
| {요소}  | {컴포넌트}       |

## 성능 고려사항

- Recomposition 격리 필요 영역: {있음/없음 + 이유}
- 라이브뷰 영역 포함: {예/아니오}
- @Stable / @Immutable 적용 필요 타입: {목록}

## 아키텍트에게 (ViewModel 계약)

ViewModel이 노출해야 하는:
- StateFlow: {필드명}: {타입}
- SharedFlow (일회성 이벤트): {이벤트명}
```

### 3. 설계 원칙 체크

- [ ] Screen Composable은 ViewModel에 직접 의존하지 않음 (상태/이벤트 파라미터로 수신)
- [ ] 라이브뷰 프레임 렌더 영역은 별도 Composable로 격리
- [ ] 다크모드: MaterialTheme.colorScheme만 사용
- [ ] 접근성: 인터랙티브 요소에 contentDescription 명세
