---
name: android-release-readiness
description: "CamCon 출시 준비도 최종 검사 스킬. 파이프라인 산출물 교차 검증, 릴리즈 체크리스트, Ship/No-Ship/Conditional-Ship 판정. '출시 준비', '릴리즈 체크', '완성도 검사', 'Ship 판정', '최종 검토' 요청 시 반드시 사용할 것."
---

# Android Release Readiness Skill — CamCon 출시 준비도 검사

## 목적

개발 파이프라인의 모든 산출물을 교차 검증하고 출시 가능 여부를 판정한다.

## 실행 절차

### 1. 산출물 수집
다음 파일을 Read하여 검토:
- `_workspace/01_planner_spec.md` (기획)
- `_workspace/02_designer_spec.md` (UI 설계)
- `_workspace/02_architect_spec.md` (아키텍처 설계)
- `_workspace/03_tester_plan.md` (테스트 계획)
- `_workspace/03_reviewer_report.md` (리뷰 리포트)

### 2. 교차 검증 체크리스트

#### 기능 완결성
- [ ] 기획 Acceptance Criteria 전체 항목이 아키텍처 설계에 반영됨
- [ ] 구독 경계(무료/프리미엄)가 구현 명세에 명확히 표현됨
- [ ] 다국어 텍스트 목록이 설계에 포함됨

#### 코드 품질
- [ ] 리뷰어 CRITICAL 이슈 전체 해소 확인 (해소 근거 명시)
- [ ] WARNING 미해소 항목은 이유 및 후속 조치 계획 명시

#### 테스트 완결성
- [ ] 핵심 UseCase 테스트 케이스 정의됨
- [ ] 자동화 불가 항목에 대한 수동 테스트 절차 정의됨
- [ ] Fake DataSource 설계가 아키텍처 명세와 일치

#### 출시 인프라
- [ ] `versionName` / `versionCode` git 태그 기반 생성 로직 확인
- [ ] `google-services.json` / `key.properties` 코드 미포함
- [ ] `resConfigs "en", "ko", "it", "fr", "de", "ja"` — 6개 언어 strings 누락 없음
- [ ] `BuildConfig.SHOW_DEVELOPER_FEATURES` release 빌드에서 false
- [ ] Firebase Remote Config 버전 체크 업데이트 여부

#### 플랫폼 호환성
- [ ] minSdk 29 이상 API만 사용 (또는 적절한 버전 분기)
- [ ] `abiFilters "arm64-v8a"` — native lib 포함 여부
- [ ] Android 15 (targetSdk 35) 동작 확인

### 3. 판정 리포트 작성

출력 파일:
- `_workspace/04_completeness_report.md` (중간 산출물)
- `completeness_report.md` (프로젝트 루트 최종본)

```markdown
# CamCon 출시 준비도 리포트

## 검사 일시: {날짜}
## 대상 기능: {기능명}

## 파이프라인 산출물 완결성

| 단계 | 산출물 | 상태 |
|------|--------|------|
| 기획 | 01_planner_spec.md | 완료 / 미완료 |
| UI 설계 | 02_designer_spec.md | 완료 / 미완료 |
| 아키텍처 | 02_architect_spec.md | 완료 / 미완료 |
| 테스트 | 03_tester_plan.md | 완료 / 미완료 |
| 리뷰 | 03_reviewer_report.md | 완료 / 미완료 |

## 미해소 이슈

| ID | 심각도 | 내용 | 조치 상태 |
|----|--------|------|----------|
| | CRITICAL | | 미해소 |

## 판정

### **{SHIP / NO-SHIP / CONDITIONAL-SHIP}**

**근거:**
- (주요 근거 3~5개)

**No-Ship / Conditional-Ship 조건 해소 후 액션:**
1. {조건}
2. ...
```

## 판정 기준

| 조건 | 판정 |
|------|------|
| CRITICAL 0개 + 테스트 계획 완료 | SHIP |
| CRITICAL 1개 이상 | NO-SHIP |
| CRITICAL 0개 + WARNING 해소 예정 + 기간 명시 | CONDITIONAL-SHIP |
| 파이프라인 산출물 누락 | NO-SHIP (재실행 요청) |
