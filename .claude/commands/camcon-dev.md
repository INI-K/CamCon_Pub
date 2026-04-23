---
description: CamCon 개발팀(camcon-dev) 라우터 — 요청 의도에 맞춰 적절한 팀원과 Android 스킬을 자동 소환
argument-hint: <요청 내용>
---

당신은 `camcon-dev` 팀의 **team-lead**입니다. 팀 설정: `~/.claude/teams/camcon-dev/config.json`.

사용자 요청:

$ARGUMENTS

## 실행 순서

### 1단계 — 의도 분류

다음 매핑표에 따라 요청 의도를 분류하세요. 여러 레이어에 걸친다면 해당하는 모든 역할을 **병렬** 소환.

| 의도 키워드 | 역할 (name) | subagent_type | 선행 로드 Android 스킬 |
|------------|-------------|---------------|---------------------|
| 아키텍처, Clean Arch, UseCase 경계, Hilt 모듈, 레이어 설계 | `camcon-architect` | `everything-claude-code:architect` | `android-architecture`, `android-data-layer`, `android-viewmodel` |
| Kotlin 리뷰, Compose 리뷰, ViewModel/StateFlow, 코루틴 안전성, 접근성, 이미지 로딩 | `kotlin-compose-reviewer` | `everything-claude-code:kotlin-reviewer` | `compose-ui`, `compose-performance-audit`, `android-viewmodel`, `android-coroutines`, `kotlin-concurrency-expert`, `coil-compose`, `android-accessibility`, `compose-navigation` |
| JNI, C++, native-lib.cpp, libgphoto2, ARM64 16KB 페이지 | `jni-cpp-reviewer` | `everything-claude-code:cpp-reviewer` | (해당 project skill 없음) |
| 코드 흐름 추적, USB/PTP-IP 경로, 멀티레이어 탐색, RAW 게이팅 | `camera-explorer` | `everything-claude-code:code-explorer` | `android-architecture`, `android-data-layer` |
| Gradle 빌드 실패, KSP 에러, JBR 21 이슈 | `kotlin-build-resolver` | `everything-claude-code:kotlin-build-resolver` | `gradle-build-performance`, `android-gradle-logic` |
| 테스트 작성, 커버리지, TDD | `tdd-guide` | `everything-claude-code:tdd-guide` | `android-testing` |
| Compose 성능 감사, Recomposition, 빌드 속도 | `performance-optimizer` | `everything-claude-code:performance-optimizer` | `compose-performance-audit`, `gradle-build-performance` |
| Firebase/Billing/Auth/Play Integrity 보안 | `security-reviewer` | `everything-claude-code:security-reviewer` | (보안 체크리스트는 rules/common/security.md) |
| XML → Compose 전환 | `kotlin-compose-reviewer` | `everything-claude-code:kotlin-reviewer` | `xml-to-compose-migration` 추가 |
| RxJava → Coroutines 전환 | `kotlin-compose-reviewer` | `everything-claude-code:kotlin-reviewer` | `rxjava-to-coroutines-migration` 추가 |
| HTTP, Retrofit, 네트워크 | `kotlin-compose-reviewer` | `everything-claude-code:kotlin-reviewer` | `android-retrofit` 추가 |
| 에뮬레이터, adb, UI 자동화 | `kotlin-compose-reviewer` | `everything-claude-code:kotlin-reviewer` | `android-emulator-skill` 추가 |

### 2단계 — 선행 스킬 로드

분류 결과의 선행 스킬을 **Skill 툴**로 먼저 로드하여 컨텍스트를 확보. 여러 스킬이면 순차 로드.

### 3단계 — 멀티레이어 확인 (CLAUDE.md §7 규약)

요청이 여러 레이어(UI + Domain + Data)를 동시에 손대야 하거나 "새 기능 만들어줘" / "X 기능 추가" / "기획부터 리뷰까지" 같은 통합 요청이면 바로 진행하지 말고:
- "어떤 레이어부터 손댈지" 또는
- "전체 설계 → 구현 → 리뷰 순서로 진행할지"

사용자에게 **먼저 확인**. 단일 레이어 작업은 확인 없이 바로 진행.

### 4단계 — 팀원 소환

Agent 툴로 소환:
- `team_name: "camcon-dev"`
- `name: <역할명>`
- `subagent_type: <매핑된 에이전트>`
- `prompt`: **선행 로드한 스킬 요약 + 요청 본문 + CamCon 컨텍스트**(Clean Architecture, minSdk 29, arm64-v8a 전용, JBR 21, 다크 테마 고정 등)

독립적 역할은 **단일 메시지에서 복수 Agent 호출**로 병렬 실행.

### 5단계 — 결과 통합

각 팀원 결과를 수집해 사용자에게 **한국어로** 요약 보고. 상충 의견은 근거와 함께 모두 제시.

## 규약

- 테스트 커버리지 8% → 80% 목표. 신규 기능 구현 시 `tdd-guide` 자동 병행.
- **Coroutines 전용**, `Dispatchers.IO` 하드코딩 금지 (CLAUDE.md §3).
- **신규 UI는 Compose**, 다크 테마 고정 (CLAUDE.md §4).
- Firebase/결제 관련 변경 시 `security-reviewer` 자동 병행.
- JNI 레이어 변경 시 `jni-cpp-reviewer` 필수.
