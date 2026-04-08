---
name: android-code-review
description: "CamCon 코드 품질 리뷰 스킬. 아키텍처 위반, Coroutines 안전성, Compose Recomposition 성능, 보안 취약점, JNI 안전성, 메모리 누수 체계적 검토. '코드 리뷰', '품질 검토', '아키텍처 위반 검사', '성능 리뷰', '보안 검토' 요청 시 반드시 사용할 것."
---

# Android Code Review Skill — CamCon 코드 품질 리뷰

## 목적

CamCon 특화 관점에서 코드를 체계적으로 리뷰하고 심각도별로 분류된 발견 사항을 제시한다.

## 실행 절차

### 1. 리뷰 대상 파악
- `_workspace/02_architect_spec.md`: 신규/변경 클래스 목록
- 실제 코드 파일 Read (변경된 파일 우선)

### 2. 리뷰 체크리스트 실행

#### A. 아키텍처 경계

```
체크: domain 레이어 파일에서 android.* import 검색
체크: UseCase에서 Repository impl 직접 참조 여부
체크: ViewModel에서 Repository 직접 사용 (UseCase 우회) 여부
```

> **알려진 예외**: `CameraUiStateManager`(Presentation)가 Data 레이어 5개 컴포넌트에 주입되는 것은 의도적으로 허용된 상태이다. 이를 아키텍처 위반으로 보고하지 않는다. (향후 `CameraStateObserver` 도메인 인터페이스로 분리 예정)

#### B. Coroutines 안전성

```
체크: GlobalScope.launch 사용 여부
체크: viewModelScope/lifecycleScope 외 커스텀 scope 남용
체크: launch { } 내부 예외 처리 누락
체크: Flow.collect 시 repeatOnLifecycle 없는 lifecycleScope 사용
체크: withContext(Dispatchers.Main) 불필요 사용
체크: Dispatchers.IO 하드코딩 여부 — 생성자/Hilt 주입 필수
체크: 비구조화 CoroutineScope(...) 직접 생성 여부
```

#### C. Compose 성능

```
체크: Composable 파라미터로 List/Map 등 불안정 타입 전달
체크: remember {} 없는 람다 생성 (매 Recomposition마다 새 인스턴스)
체크: 라이브뷰 프레임 영역이 다른 UI 상태와 같은 Composable 내에 존재
체크: derivedStateOf 없이 State 계산 반복
```

#### D. 보안

```
체크: 코드에 하드코딩된 API 키, 패스워드, Secret
체크: Intent extra 신뢰 없이 사용
체크: USB 권한 재요청 없이 연결 시도
```

#### E. JNI/Native

```
체크: JNI 함수 호출이 적절한 스레드(non-Main)에서 실행
체크: Native 리소스 해제 onDestroy/cleanup 확인
체크: JNI 예외 처리 (null 반환 시 처리)
```

#### F. 메모리 누수

```
체크: Context를 companion object / static에 저장
체크: BroadcastReceiver / EventListener 미해제
체크: 코루틴 Job 미취소
```

### 3. 리뷰 리포트 작성

출력 파일: `_workspace/03_reviewer_report.md`

```markdown
# 코드 리뷰 리포트: {기능명}

## 요약
- CRITICAL: {N}개
- WARNING: {N}개
- SUGGESTION: {N}개

## CRITICAL (수정 필수)
### [C-{N}] {이슈 제목}
- **파일**: {경로}:{라인}
- **문제**: {설명}
- **영향**: {잠재적 결과}
- **수정 방향**: {구체적 방법}

## WARNING (수정 권장)
### [W-{N}] {이슈 제목}
(동일 형식)

## SUGGESTION (개선 권장)
### [S-{N}] {이슈 제목}
(동일 형식)

## 아키텍처 준수도
- 레이어 경계: 준수 | 위반 {N}건
- Coroutines 패턴: 양호 | 개선 필요
- Compose 성능: 양호 | 개선 필요
```

### 4. 리뷰 원칙

- 코드를 직접 수정하지 않음 — 발견 사항만 리포트
- CRITICAL이 5개 이상이면 리더에게 즉시 알림
- 기존 코드베이스의 패턴과 일관성을 고려한 제안
- 성능 이슈는 실측 데이터 없이 "추정"임을 명시
