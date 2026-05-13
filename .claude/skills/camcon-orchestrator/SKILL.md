---
name: camcon-orchestrator
description: CamCon(안드로이드 DSLR/미러리스 테더링 촬영 앱) 개발 작업을 explorer·architect·implementer·reviewer·tdd-tester 5명 서브 에이전트 팀으로 분배하고 결과를 종합하는 오케스트레이터. 카메라 제어·라이브뷰·타임랩스·테더링·USB OTG·Wi-Fi PTP-IP·JNI·libgphoto2·Compose UI·ViewModel·구독 게이팅·결제·Firebase·Hilt·Coroutines·Gradle 빌드 작업을 비롯해 CamCon 코드를 신규 구현·수정·리뷰·디버깅·재실행·업데이트·보완·개선할 때 트리거. 사용자가 새 기능, 버그 수정, 리팩터링, 마이그레이션, 성능 개선을 요청하면 반드시 이 스킬로 진입한다.
---

# CamCon Orchestrator

CamCon 개발 요청을 받으면 이 스킬이 흐름을 잡는다. 단순 질의는 단일 에이전트만 호출하고, 멀티레이어 변경은 파이프라인으로 진행한다.

## 실행 모드

두 모드 양립. 사용자 요청 성격에 따라 선택:

**서브 에이전트 패턴 (단순 작업 기본).** `Agent` 도구로 1~5명의 서브 에이전트를 호출하고 반환값으로 결과를 수집한다. 병렬이 가능하면 `run_in_background: true`. 단발성 작업·정보 수집·짧은 분석에 적합. 팀 통신이 불필요한 경우 오버헤드를 줄임.

**에이전트 팀 패턴 (복잡 협업).** `TeamCreate` + `TaskCreate` + `SendMessage`로 팀원이 자체 조율. 다음 상황에서 채택:
- 멀티 페이즈 작업 (설계 → 구현 → 테스트 → 리뷰)
- 팀원 간 합의·교차 검증·정보 교환이 필요한 작업
- 같은 산출물을 여러 차례 갱신할 가능성이 있는 작업
- 사용자가 명시적으로 "팀"·"병렬 협업"·"동시 작업"을 요구한 경우

세션당 한 팀만 활성화 가능. 팀 모드 종료 시 `shutdown_request` × N → `TeamDelete`. Phase 간 팀 재구성도 가능.

모든 Agent/팀원 호출에 `model: "opus"` 명시.

## 팀 구성

| 이름 | subagent_type | 책임 |
|------|--------------|------|
| `camcon-explorer` | `Explore` | 읽기 전용 코드 흐름 추적 |
| `camcon-architect` | `Plan` | 레이어 배치 / 인터페이스 설계 |
| `camcon-implementer` | `general-purpose` | 실제 코드 작성 / 빌드 검증 |
| `camcon-reviewer` | `superpowers:code-reviewer` | 구현 검수 / BLOCKER 보고 |
| `camcon-tdd-tester` | `general-purpose` | 테스트 작성 / 커버리지 추적 |

상세 역할은 `.claude/agents/camcon-*.md` 파일 참조.

## Phase 0: 컨텍스트 확인

요청을 받으면 즉시:

1. `.claude/_workspace/` 디렉토리 존재 여부 확인
   - **존재 + 부분 수정 요청** → 해당 에이전트만 재호출
   - **존재 + 새 입력** → `.claude/_workspace/` → `.claude/_workspace_prev/` 이동 후 새 실행
   - **미존재** → 초기 실행, `.claude/_workspace/` 생성
2. 요청 유형 분류 (아래 Phase 1)

## Phase 1: 의도 분류

요청을 8개 흐름 중 하나로 분류:

| 흐름 | 트리거 | 호출 순서 |
|------|-------|----------|
| **A. 단순 탐색** | "어디서 X 처리해?", "Y 흐름 보여줘" | `camcon-explorer` 단독 |
| **B. 단순 리뷰** | "이 PR/변경사항 리뷰해줘" | `camcon-reviewer` 단독 |
| **C. 버그 수정** | "X 안 됨", "Y 에러 발생" | explorer → architect(필요 시) → tdd-tester(회귀 테스트) → implementer → reviewer |
| **D. 신규 기능** | "X 추가해줘", "Y 기능 만들어줘" | architect → tdd-tester(실패 테스트) → implementer → reviewer |
| **E. 마이그레이션** | "RxJava→Coroutines", "XML→Compose" | architect → implementer → tdd-tester → reviewer |
| **F. 성능 튜닝** | "라이브뷰 stuttering", "USB 처리량", "recomposition 폭증", "빌드 속도" | explorer(병목 매핑) → architect(개선 설계) → implementer → tdd-tester(벤치마크) → reviewer |
| **H. 보안 핫픽스** | "Billing/Auth 회귀", "구독 게이팅 우회", "RAW 누출" | reviewer(주도) → implementer → tdd-tester(회귀 테스트) → reviewer 재검증 |
| **I. 문서 갱신** | "CLAUDE.md 갱신", "README 보강", "DEV_DOCUMENT 정정" | 메인 단독 (팀 호출 없이). 검증 필요 시 explorer 1회 |

**멀티레이어 확인:** UI + Domain + Data를 동시에 손대거나 "기획부터 리뷰까지" 같은 통합 요청이면 진행 전에 사용자에게:
- "어떤 레이어부터 손댈지" 또는
- "전체 설계 → 구현 → 리뷰 순서로 진행할지"

먼저 확인. 단일 레이어 작업은 확인 없이 바로 진행.

## Phase 2: 선행 스킬 로드

모든 에이전트 호출 전에 **`camcon-domain-context` 스킬을 반드시 먼저 로드**한다. 추가로 작업 유형에 따라:

| 작업 영역 | 추가 스킬 |
|----------|----------|
| JNI / cpp / libgphoto2 / `.so` | `camcon-jni-protocol` |
| RAW / 포맷 / 구독 / 결제 / Firebase Auth | `camcon-subscription-gating` |
| 아키텍처 / Hilt / Repository | `android-architecture`, `android-data-layer` |
| Compose / ViewModel / 상태 | `compose-ui`, `android-viewmodel`, `compose-navigation` |
| 코루틴 / 스레드 안전성 | `android-coroutines`, `kotlin-concurrency-expert` |
| 테스트 / TDD / 커버리지 | `android-testing` |
| Compose 성능 / Gradle 빌드 | `compose-performance-audit`, `gradle-build-performance` |
| XML→Compose / RxJava→Coroutines | `xml-to-compose-migration` / `rxjava-to-coroutines-migration` |
| HTTP / Retrofit | `android-retrofit` |
| 에뮬레이터 / adb | `android-emulator-skill` |
| 접근성 / TalkBack | `android-accessibility` |
| 이미지 로딩 | `coil-compose` |

## Phase 3: 호출 실행

`Agent` 도구로 호출. 프롬프트 구조:

```
선행 로드한 스킬: <목록>

CamCon 컨텍스트 요약:
- Clean Architecture (presentation → domain ← data)
- minSdk 29 / arm64-v8a 전용 / JBR 21
- Coroutines 전용, Dispatchers.IO 하드코딩 금지
- Compose 다크 테마 고정
- 구독 게이팅은 ValidateImageFormatUseCase 단일 지점

요청 본문: <사용자 원문>

추가 컨텍스트: <이전 Phase 산출물(있으면 _workspace 경로)>

기대 출력: <에이전트 정의의 출력 프로토콜에 따른 형식>
```

병렬 가능한 호출은 단일 메시지에 복수 `Agent` 호출. 순차 의존이면 직전 결과를 다음 호출의 컨텍스트로 사용.

## Phase 4: 결과 종합

각 에이전트 반환값을 모아 사용자에게 **한국어로** 보고:

```markdown
## <기능명> 진행 결과

### 탐색 (camcon-explorer)
<요약>

### 설계 (camcon-architect)
<요약>

### 구현 (camcon-implementer)
<변경 파일 목록 + 빌드 결과>

### 테스트 (camcon-tdd-tester)
<추가 테스트 + 통과 여부>

### 리뷰 (camcon-reviewer)
- BLOCKER: <있으면>
- 후속 조치: <있으면>

### 상충 의견 (있으면)
<출처와 함께 양쪽 다 제시>
```

## 자동 병행 규칙

- **신규 기능 구현 시 `camcon-tdd-tester` 자동 병행** (커버리지 8% → 80% 목표).
- **Firebase / Billing / Auth 변경 시 `camcon-reviewer` 자동 병행** (보안 민감).
- **JNI 레이어 변경 시 `camcon-jni-protocol` 스킬 + `camcon-reviewer` 필수**.
- **`strings.xml` / 다국어 문자열 변경 시**: 8개 언어(ko/ja/zh/de/es/fr/it/en) 동기화 확인 자동 트리거 — reviewer가 i18n 누락 검증.
- **GPU / ColorTransfer / GPUImage / EXIF 작업 시**: `compose-performance-audit` 스킬 자동 로드 + reviewer가 EXIF 보존·메모리 누수 확인.
- **카메라 제조사별 코드 (Canon/Nikon/Sony/Fuji/Panasonic) 변경 시**: `camcon-jni-protocol` 스킬 자동 로드 + reviewer가 camlib 회귀 확인.
- **백그라운드 서비스 (Foreground Service / Wake Lock / AutoConnectManager / WifiMonitoringService) 변경 시**: reviewer 자동 병행 (장시간 무인 안정성 critical).

## 정보 동기화 규약

팀 모드에서 메인이 SendMessage로 결정을 전달하기 전에:
1. `TaskList` 1회 호출로 모든 작업 상태 확인
2. `_workspace/` 디렉토리의 최신 mtime 확인 (다른 팀원이 최근 갱신한 산출물이 있는지)
3. 발견된 갱신 사항이 자신의 결정에 영향을 주는지 검토 후 SendMessage 발송

이유: 메인의 빠른 SendMessage 결정이 팀원의 최신 갱신을 반영 못 하면 모순 사이클(예: 머지 순서 7회 진동)이 발생한다.

## 에러 핸들링

| 상황 | 대응 |
|------|------|
| 에이전트 1회 실패 | 1회 재시도 |
| 재실패 | 해당 결과 누락한 채 진행, 보고서에 명시 |
| 상충 결과 (예: architect vs reviewer 설계관) | 양쪽 출처 병기 후 사용자 결정 위임 |
| 빌드 실패 | implementer가 원인 분석 후 사용자에게 보고 (silent fix 금지) |

## 데이터 전달 프로토콜

- **반환값 기반** (서브 에이전트 패턴 기본).
- 대용량 산출물(설계 문서, 변경 파일 목록 등)은 `.claude/_workspace/{phase}_{agent}_{artifact}.md` 에 저장.
- 최종 산출물(코드 변경)만 사용자 지정 경로에 출력, `_workspace/` 중간 파일은 보존(감사 추적).

## 단순 질의 회피

사용자 요청이 **순수 개념 설명**(예: "이거 무슨 뜻이야?", "Hilt가 뭐야?", "Compose는 어떻게 작동해?")이면 오케스트레이터를 거치지 않고 직접 답변한다. **위치 찾기·코드 흐름 추적은 explorer를 호출**한다 — 직접 답변 X.

## 테스트 시나리오

**정상 흐름 (E. 마이그레이션):**
1. 사용자: "PtpipDataSource의 RxJava를 Coroutines로 마이그레이션해줘"
2. Phase 0: `_workspace/` 없음 → 신규 생성
3. Phase 1: E. 마이그레이션 분류
4. Phase 2: `camcon-domain-context`, `rxjava-to-coroutines-migration`, `android-coroutines`, `kotlin-concurrency-expert` 로드
5. Phase 3: architect → tdd-tester → implementer → reviewer 순차 호출
6. Phase 4: 한국어 종합 보고

**에러 흐름 (구현 빌드 실패):**
1. implementer 호출 후 `./gradlew compileDebugKotlin` 실패
2. implementer가 원인 분석한 결과를 반환
3. 오케스트레이터는 재시도하지 않고 사용자에게 보고
4. 사용자 추가 지시 대기

## 재실행 / 후속 작업 지원

- "다시 실행", "재실행", "업데이트", "수정", "보완"
- "<부분>만 다시"
- "이전 결과 기반으로", "결과 개선"

이런 표현이 있으면 Phase 0에서 `_workspace/` 확인 후 **부분 재실행**. 모든 에이전트는 이전 산출물을 읽고 차이만 갱신하도록 정의되어 있다.
