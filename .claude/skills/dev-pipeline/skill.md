---
name: dev-pipeline
description: "CamCon 개발 파이프라인 오케스트레이터. 기획→설계→구현→품질검증→완성도 검사 팀을 조율하여 기능을 완성. '개발 파이프라인', '전체 개발 프로세스', '기능 개발 시작', '팀 개발' 요청 시 반드시 사용할 것. 신규 기능 개발 또는 대규모 변경 시 이 오케스트레이터를 실행하여 전체 팀이 협업하도록 한다."
---

# CamCon Dev Pipeline Orchestrator

CamCon Android 앱의 기획→설계→구현→품질검증→완성도 파이프라인을 에이전트 팀으로 조율한다.

## 실행 모드: 에이전트 팀 (파이프라인 + 팬아웃 복합)

## 에이전트 구성

| 팀원 | 역할 | 메인 스킬 | 참조 스킬 | Phase |
|------|------|----------|----------|-------|
| planner | 기능 기획 명세 | android-planning | — | 1 |
| designer | Compose UI 설계 | android-compose-design | compose-ui, compose-navigation, coil-compose, android-accessibility | 2 (병렬) |
| architect | Clean Architecture 설계 | android-arch-design | android-architecture, android-viewmodel, android-data-layer, android-coroutines | 2 (병렬) |
| implementer | 코드 구현 | android-implement | android-coroutines, android-retrofit, android-data-layer, android-viewmodel, compose-ui | 2.5 |
| tester | 테스트 전략 수립 | android-test-strategy | android-testing, android-emulator-skill | 3 (병렬) |
| reviewer | 코드 품질 리뷰 | android-code-review | kotlin-concurrency-expert, compose-performance-audit, android-coroutines | 3 (병렬) |
| performance-auditor | 성능 감사 | compose-performance-audit | gradle-build-performance, kotlin-concurrency-expert | 3 (병렬) |
| completeness-inspector | 출시 준비도 최종 검사 | android-release-readiness | android-accessibility | 4 |

### 스킬 출처

| 유형 | 스킬 |
|------|------|
| CamCon 전용 | android-planning, android-arch-design, android-compose-design, android-implement, android-test-strategy, android-code-review, android-release-readiness |
| Android 범용 (awesome-android-agent-skills) | android-architecture, android-viewmodel, android-data-layer, android-coroutines, android-retrofit, kotlin-concurrency-expert, compose-ui, compose-navigation, coil-compose, compose-performance-audit, gradle-build-performance, android-testing, android-emulator-skill, android-accessibility, android-gradle-logic, rxjava-to-coroutines-migration, xml-to-compose-migration |

> 에이전트는 메인 스킬을 먼저 로드한 후 참조 스킬을 보조적으로 활용한다.

## 워크플로우

### Phase 0: 준비

1. 사용자 요청 분석 — 기능명, 범위, 우선순위 파악
2. `_workspace/` 디렉토리 생성 (프로젝트 루트 기준)
3. 사용자 입력을 `_workspace/00_input.md`에 저장

### Phase 1: 기획 팀

**팀 구성:**
```
TeamCreate(
  team_name: "planning-team",
  members: [
    {
      name: "planner",
      agent_type: "planner",
      model: "opus",
      prompt: "당신은 CamCon 기획자입니다. android-planning 스킬을 사용하여 _workspace/00_input.md를 읽고 기획 명세를 _workspace/01_planner_spec.md에 작성하세요. 완료 후 리더에게 알립니다."
    }
  ]
)
```

**작업 등록:**
```
TaskCreate(tasks: [
  {
    title: "기능 기획 명세 작성",
    description: "_workspace/00_input.md 기반으로 사용자 스토리, Acceptance Criteria, 의존성 분석 포함한 기획 명세 작성 → _workspace/01_planner_spec.md",
    assignee: "planner"
  }
])
```

**완료 조건:** planner가 리더에게 완료 알림 발신

**팀 재구성:** Phase 1 완료 후 `TeamDelete("planning-team")` → Phase 2 팀 구성

---

### Phase 2: 설계 팀 (팬아웃 병렬)

**팀 구성:**
```
TeamCreate(
  team_name: "design-team",
  members: [
    {
      name: "designer",
      agent_type: "designer",
      model: "opus",
      prompt: "당신은 CamCon UI/UX 디자이너입니다. android-compose-design 스킬을 메인으로, compose-ui·compose-navigation·coil-compose·android-accessibility 스킬을 참조하여 사용하세요. _workspace/01_planner_spec.md를 읽고 Compose UI 설계 명세를 _workspace/02_designer_spec.md에 작성하세요. ViewModel 계약 초안 완성 시 아키텍트에게 SendMessage로 공유. 완료 후 리더에게 알립니다."
    },
    {
      name: "architect",
      agent_type: "architect",
      model: "opus",
      prompt: "당신은 CamCon 아키텍트입니다. android-arch-design 스킬을 메인으로, android-architecture·android-viewmodel·android-data-layer·android-coroutines 스킬을 참조하여 사용하세요. _workspace/01_planner_spec.md를 읽고 Clean Architecture 설계 명세를 _workspace/02_architect_spec.md에 작성하세요. 디자이너로부터 ViewModel 계약 메시지 수신 시 통합하여 명세 완성. 완료 후 리더에게 알립니다."
    }
  ]
)
```

**작업 등록:**
```
TaskCreate(tasks: [
  {
    title: "Compose UI 설계",
    description: "기획 명세 기반 컴포넌트 계층, UI 상태 모델, Material3 매핑, 네비게이션 그래프, 접근성 체크리스트 → _workspace/02_designer_spec.md",
    assignee: "designer"
  },
  {
    title: "Clean Architecture 설계",
    description: "UseCase/Repository/DataSource 클래스 설계, Hilt 모듈, Flow 흐름, ViewModel StateFlow/SharedFlow 계약, Coroutines 스코프 설계 → _workspace/02_architect_spec.md",
    assignee: "architect"
  }
])
```

**팀원 간 통신 규칙:**
- designer → architect: ViewModel 상태/이벤트 계약 초안 SendMessage
- architect → designer: 데이터 모델 변경 시 UI 영향 SendMessage

**완료 조건:** designer + architect 모두 완료 알림 수신

**팀 재구성:** Phase 2 완료 후 `TeamDelete("design-team")` → Phase 2.5 팀 구성

---

### Phase 2.5: 구현 팀

**팀 구성:**
```
TeamCreate(
  team_name: "implementation-team",
  members: [
    {
      name: "implementer",
      agent_type: "implementer",
      model: "opus",
      prompt: "당신은 CamCon 구현 개발자입니다. android-implement 스킬을 메인으로, android-coroutines·android-retrofit·android-data-layer·android-viewmodel·compose-ui 스킬을 참조하여 사용하세요. _workspace/02_architect_spec.md와 _workspace/02_designer_spec.md를 읽고 Clean Architecture 레이어 순서(domain→data→di→presentation)로 코드를 구현하세요. 구현 결과 요약을 _workspace/02_5_implementation_log.md에 작성하세요. 완료 후 리더에게 알립니다."
    }
  ]
)
```

**작업 등록:**
```
TaskCreate(tasks: [
  {
    title: "설계 명세 기반 코드 구현",
    description: "아키텍처/UI 설계 명세를 Kotlin 코드로 변환. domain→data→di→presentation 순서. Coroutines 모범 사례 준수, Retrofit 네트워킹, Room 데이터 계층, ViewModel StateFlow, Compose UI 구현 → _workspace/02_5_implementation_log.md",
    assignee: "implementer"
  }
])
```

**완료 조건:** implementer가 리더에게 완료 알림 발신

**팀 재구성:** Phase 2.5 완료 후 `TeamDelete("implementation-team")` → Phase 3 팀 구성

---

### Phase 3: 품질 팀 (팬아웃 병렬 — 3팀)

**팀 구성:**
```
TeamCreate(
  team_name: "quality-team",
  members: [
    {
      name: "tester",
      agent_type: "tester",
      model: "opus",
      prompt: "당신은 CamCon 테스터입니다. android-test-strategy 스킬을 메인으로, android-testing·android-emulator-skill 스킬을 참조하여 사용하세요. _workspace/02_architect_spec.md와 _workspace/02_designer_spec.md와 _workspace/02_5_implementation_log.md를 읽고 테스트 전략을 _workspace/03_tester_plan.md에 작성하세요. 커버리지 공백 발견 시 reviewer에게 SendMessage. 완료 후 리더에게 알립니다."
    },
    {
      name: "reviewer",
      agent_type: "reviewer",
      model: "opus",
      prompt: "당신은 CamCon 코드 리뷰어입니다. android-code-review 스킬을 메인으로, kotlin-concurrency-expert·compose-performance-audit·android-coroutines 스킬을 참조하여 사용하세요. _workspace/02_architect_spec.md와 _workspace/02_5_implementation_log.md를 읽고 기존 코드를 탐색하여 품질 리뷰를 _workspace/03_reviewer_report.md에 작성하세요. 특히 Coroutines 안전성, Compose 성능, 아키텍처 위반을 중점 검토하세요. 테스터로부터 커버리지 공백 메시지 수신 시 반영. 완료 후 리더에게 알립니다."
    },
    {
      name: "performance-auditor",
      agent_type: "performance-auditor",
      model: "opus",
      prompt: "당신은 CamCon 성능 감사관입니다. compose-performance-audit 스킬을 메인으로, gradle-build-performance·kotlin-concurrency-expert 스킬을 참조하여 사용하세요. _workspace/02_5_implementation_log.md를 읽고 구현된 코드를 탐색하여 성능 감사 리포트를 _workspace/03_performance_report.md에 작성하세요. Compose Recomposition storms, 불안정한 keys, Coroutines Dispatcher 오용, Gradle 빌드 병목을 중점 검토하세요. CRITICAL 성능 이슈 발견 시 reviewer에게 SendMessage. 완료 후 리더에게 알립니다."
    }
  ]
)
```

**작업 등록:**
```
TaskCreate(tasks: [
  {
    title: "테스트 전략 수립",
    description: "단위/통합/UI 테스트 케이스, Fake 전략, 커버리지 목표, Screenshot 테스트 계획, 에뮬레이터 자동화 → _workspace/03_tester_plan.md",
    assignee: "tester"
  },
  {
    title: "코드 품질 리뷰",
    description: "아키텍처 위반, Coroutines 안전성 (kotlin-concurrency-expert 기준), Compose 성능, 보안 체크 → _workspace/03_reviewer_report.md",
    assignee: "reviewer"
  },
  {
    title: "성능 감사",
    description: "Compose Recomposition 분석, Gradle 빌드 성능, Coroutines 스레드 안전성, 메모리 누수 → _workspace/03_performance_report.md",
    assignee: "performance-auditor"
  }
])
```

**팀원 간 통신 규칙:**
- tester → reviewer: 커버리지 공백 영역 SendMessage
- reviewer → tester: CRITICAL 발견 시 테스트 추가 요청 가능
- performance-auditor → reviewer: CRITICAL 성능 이슈 공유 SendMessage
- reviewer → performance-auditor: 아키텍처 수준 성능 우려 사항 공유 가능

**완료 조건:** tester + reviewer + performance-auditor 모두 완료 알림 수신

**팀 재구성:** Phase 3 완료 후 `TeamDelete("quality-team")` → Phase 4 팀 구성

---

### Phase 4: 완성도 팀

**팀 구성:**
```
TeamCreate(
  team_name: "completion-team",
  members: [
    {
      name: "completeness-inspector",
      agent_type: "completeness-inspector",
      model: "opus",
      prompt: "당신은 CamCon 완성도 검사관입니다. android-release-readiness 스킬을 메인으로, android-accessibility 스킬을 참조하여 사용하세요. _workspace/ 디렉토리의 모든 산출물(01~03)을 읽고 교차 검증하여 최종 판정 리포트를 _workspace/04_completeness_report.md와 completeness_report.md에 작성하세요. 성능 감사 리포트(03_performance_report.md)도 반드시 반영하세요. 완료 후 리더에게 판정 결과를 알립니다."
    }
  ]
)
```

**작업 등록:**
```
TaskCreate(tasks: [
  {
    title: "출시 준비도 최종 검사",
    description: "전체 파이프라인 산출물 교차 검증 (기획·설계·구현·테스트·리뷰·성능 감사), 접근성 체크, Ship/No-Ship/Conditional-Ship 판정",
    assignee: "completeness-inspector"
  }
])
```

**완료 조건:** completeness-inspector가 판정 결과 알림 수신

---

### Phase 5: 정리

1. `TeamDelete("completion-team")`
2. `_workspace/` 디렉토리 보존 (감사 추적용)
3. 사용자에게 결과 요약:
   - 각 단계 산출물 경로
   - 최종 판정 (SHIP / NO-SHIP / CONDITIONAL-SHIP)
   - 다음 액션 아이템

## 데이터 흐름

```
사용자 요청 → _workspace/00_input.md
                    ↓
              [planner]
                    ↓
         _workspace/01_planner_spec.md
                    ↓
         ┌──────────┴──────────┐
    [designer]           [architect]
         ↕ SendMessage          ↕
    02_designer_spec   02_architect_spec
         └──────────┬──────────┘
                    ↓
            [implementer]
                    ↓
         02_5_implementation_log.md
                    ↓
    ┌───────────────┼───────────────┐
 [tester]      [reviewer]   [performance-auditor]
    ↕ SendMessage   ↕ SendMessage      ↕
 03_tester     03_reviewer    03_performance
    └───────────────┼───────────────┘
                    ↓
         [completeness-inspector]
                    ↓
         04_completeness_report.md
         completeness_report.md (최종본)
```

## 에러 핸들링

| 상황 | 전략 |
|------|------|
| 팀원 1명 실패 | 리더가 SendMessage로 상태 확인 → 재시작 시도 |
| 재시작 실패 | 해당 산출물 없이 다음 Phase 진행, 보고서에 "미완료" 명시 |
| 기획 명세 불충분 | planner가 사용자에게 재질의 후 재작성 |
| CRITICAL 5개 이상 (리뷰어) | 리더가 사용자에게 알림 후 Phase 4 진행 여부 확인 |
| 성능 CRITICAL 발견 (감사관) | reviewer와 공유 후 리더에게 알림 → 구현 수정 여부 사용자 확인 |
| 산출물 파일 누락 | completeness-inspector가 리더에게 알림 → 해당 Phase 재실행 |

## 테스트 시나리오

### 정상 흐름
1. 사용자: "USB 연결 화면에 연결 상태 인디케이터 추가"
2. Phase 1: planner가 AC 3개 포함 기획 명세 생성
3. Phase 2: designer(compose-ui 참조) + architect(android-architecture 참조) 병렬 → ViewModel 계약 동기화
4. Phase 2.5: implementer가 domain→data→di→presentation 순서로 구현
5. Phase 3: tester(android-testing 참조) + reviewer(kotlin-concurrency-expert 참조) + performance-auditor(compose-performance-audit) 병렬 → 3팀 교차 공유
6. Phase 4: completeness-inspector → CRITICAL 0 → SHIP 판정
7. 산출물: `_workspace/` 7개 파일 + `completeness_report.md`

### 에러 흐름
1. Phase 3에서 performance-auditor가 Recomposition storm CRITICAL 발견
2. reviewer에게 공유, 리더가 CRITICAL 목록을 사용자에게 공유
3. 사용자가 "Phase 4 계속 진행" 결정
4. completeness-inspector → 성능 CRITICAL 미해소 → CONDITIONAL-SHIP 판정
5. 최종 리포트에 "성능 이슈 해소 후 릴리즈 권장" 명시

## 빠른 실행 (단일 Phase)

전체 파이프라인 대신 특정 Phase만 실행할 수 있다:
- **기획만**: "기획 에이전트 실행해서 {기능명} 명세 작성해줘"
- **구현만**: "구현 에이전트로 설계 명세 기반 코드 작성해줘"
- **리뷰만**: "리뷰어 에이전트로 현재 코드 리뷰해줘"
- **성능 감사만**: "성능 감사관으로 Compose 성능 검토해줘"
- **완성도 검사만**: "완성도 검사관 실행해서 출시 판정해줘"
