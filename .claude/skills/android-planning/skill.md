---
name: android-planning
description: "CamCon 기능 기획 스킬. 신규 기능 요청을 기획 명세로 전환. 사용자 스토리, Acceptance Criteria, 개발 범위, 의존성 분석. '기획', '요구사항 분석', '스펙 작성', '기능 정의' 요청 시 반드시 사용할 것."
---

# Android Planning Skill — CamCon 기획 명세 작성

## 목적

사용자 요청을 구체적이고 검증 가능한 기획 명세로 전환한다. 아키텍트/디자이너가 즉시 설계에 착수할 수 있는 수준의 명세를 생성한다.

## 실행 절차

### 1. 요청 분석
- 기능 유형 분류: 신규 기능 / 기존 기능 변경 / 버그 수정
- 영향 범위 파악: 어떤 화면/레이어/프로토콜에 영향을 주는가
- JNI/NDK 변경 필요 여부 판단 (불명확 시 "아키텍트 검토 필요"로 플래그)

### 2. 명세 작성

출력 파일: `_workspace/01_planner_spec.md`

```markdown
# 기능 명세: {기능명}

## 개요
- 목적: (무엇을 해결하는가)
- 영향 범위: (화면 / 레이어 / 프로토콜)
- 구독 경계: 무료 | 프리미엄 | 관리자 전용

## 사용자 스토리
- As a {사용자 유형}, I want {기능}, So that {목적}
(2~5개)

## Acceptance Criteria
1. {검증 가능한 조건}
2. ...
(Happy path + Edge case + Error case 포함)

## 의존성 / 제약사항
- 기존 기능 의존성:
- 플랫폼 제약: (minSdk, 권한, 하드웨어)
- JNI 변경 여부: 필요 없음 | 아키텍트 검토 필요

## 아키텍트에게
- 비즈니스 로직 핵심:
- 필요한 데이터 모델:
- 외부 API/프로토콜 변경:

## 디자이너에게
- 신규 화면: 있음 | 없음
- UI 흐름 변경:
- 다국어 필요 텍스트: (en/ko 기준)

## 제외 범위 (Out of Scope)
- (이번 기획에 포함하지 않는 항목 명시)
```

### 3. 검토 기준

- Acceptance Criteria가 "기능이 동작한다"처럼 모호하면 구체화한다
- 구독 경계가 불명확한 기능은 반드시 명시
- 다국어 텍스트는 최소 en/ko로 초안 제시

## CamCon 도메인 참조

| 도메인 | 핵심 클래스 |
|--------|------------|
| 카메라 연결 | ConnectCameraUseCase, UsbCameraManager, PtpipConnectionManager |
| 라이브뷰 | StartLiveViewUseCase, NativeCameraDataSource |
| 촬영 | CapturePhotoUseCase, PhotoCaptureEventManager |
| 설정 | GetCameraSettingsUseCase, UpdateCameraSettingUseCase |
| 인증 | SignInWithGoogleUseCase, AuthRepositoryImpl |
| 구독 | GetSubscriptionUseCase, PurchaseSubscriptionUseCase |
