---
name: android-test-strategy
description: "CamCon Android 테스트 전략 스킬. 아키텍처 명세 기반 단위/통합/Compose UI 테스트 케이스 설계, Fake/Mock 전략, 커버리지 목표. '테스트 전략', '테스트 케이스 설계', '커버리지 계획', 'Fake 설계' 요청 시 반드시 사용할 것. android-testing 스킬 병행 참조."
---

# Android Test Strategy Skill — CamCon 테스트 전략

## 목적

아키텍처 명세를 기반으로 신뢰할 수 있는 테스트 계획을 수립한다. JNI/하드웨어 의존성을 격리하여 자동화 가능한 범위를 최대화한다.

> 이 스킬 실행 전 `Skill 도구로 android-testing 호출`하여 테스트 전략 내면화.

## 실행 절차

### 1. 명세 분석
- `_workspace/02_architect_spec.md`: 신규/변경 클래스 목록
- `_workspace/02_designer_spec.md`: UI 상태 모델
- 기존 테스트 파일: `app/src/test/`, `app/src/androidTest/`

### 2. 테스트 계획 작성

출력 파일: `_workspace/03_tester_plan.md`

```markdown
# 테스트 계획: {기능명}

## 테스트 범위 분류

### 단위 테스트 (app/src/test/)
JVM에서 실행. 하드웨어/Android 프레임워크 의존성 없음.

| 대상 | 테스트 케이스 | Fake 전략 |
|------|--------------|----------|
| {UseCase} | given/when/then | {Fake Repository} |
| {ViewModel} | given/when/then | {Fake UseCase} |

### 계측 테스트 (app/src/androidTest/)
실제 Android 환경. Hilt DI 포함.

| 대상 | 테스트 케이스 | 주의사항 |
|------|--------------|---------|
| {Repository} | 실제 DataStore/Firebase | Firebase emulator 필요 여부 |
| {Compose UI} | ComposeTestRule 사용 | |

### 자동화 불가 항목
| 항목 | 이유 | 수동 테스트 절차 |
|------|------|----------------|
| USB 연결 | 실물 기기 필요 | 1. 기기 연결 2. ... |
| PTP/IP 라이브뷰 | 실물 카메라 필요 | 1. ... |

## Fake/Stub/Mock 설계

### Fake DataSource 목록
```kotlin
class Fake{DataSourceName} : {DataSourceInterface} {
    var shouldThrow = false
    override suspend fun {method}(): {Type} {
        if (shouldThrow) throw IOException("Fake error")
        return {fakeData}
    }
}
```

## 테스트 케이스 상세

### {ClassName}Test
```
given: {초기 상태}
when: {동작}
then: {기대 결과}
```
(Happy path, Edge case, Error case 각 1개 이상)

## 커버리지 목표
- 핵심 UseCase: 80%+ 라인 커버리지
- ViewModel: 70%+ (UI 이벤트 분기 포함)
- Repository impl: 60%+ (Fake DataSource 사용)
```

### 3. 테스트 원칙 체크

- [ ] Hilt 테스트는 mock이 아닌 Fake 사용 (`hilt.shareTestComponents` 활용)
- [ ] Flow 테스트: `turbine` 또는 `runTest` 사용
- [ ] Compose UI 테스트: `createComposeRule()` 사용, ViewModel 없이 순수 UI 테스트
- [ ] 테스트 명명: `given_when_then` 또는 `{메서드명}_success/failure`
