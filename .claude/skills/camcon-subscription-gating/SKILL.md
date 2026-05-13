---
name: camcon-subscription-gating
description: CamCon 구독 티어(FREE/BASIC/PRO/REFERRER/ADMIN) 기능 게이팅. RAW/PNG/JPG 포맷 접근 제어, 다운로드 해상도 제한(2000px), Google Play Billing, Firebase Custom Claims, ValidateImageFormatUseCase 단일 지점 규약. 구독·결제·RAW·포맷 분기·다운로드 제한·티어 권한 관련 코드를 작성/리뷰할 때 반드시 로드. Firebase Auth와 Play Integrity 검증 누락 위험이 큰 영역.
---

# CamCon 구독 / 게이팅 규약

## 티어 정의

| 티어 | 포맷 | 다운로드 | 기타 |
|------|------|---------|------|
| **FREE** | JPG/JPEG | 2000px 제한 | 기본 제어 |
| **BASIC** | JPG/JPEG/PNG | 원본 | 배치 처리 |
| **PRO** | 모든 포맷(RAW 포함) | 원본 | 고급 제어 |
| **REFERRER** | 모든 포맷 | 원본 | PRO + 추천인 |
| **ADMIN** | 모든 포맷 | 원본 | 전체 + 사용자 관리 |

## 핵심 규약: 단일 지점 원칙

**RAW/포맷 접근 제어는 오직 `ValidateImageFormatUseCase`에서만 수행한다.**

이유:
- 분산된 분기는 우회·누락이 쉽다. 한 곳에서만 결정하면 보안 감사와 회귀 테스트가 가능.
- 구독 정책이 바뀌어도(예: BASIC에 TIFF 추가) 한 파일만 수정.
- 클라이언트·서버 권한 확인을 동일한 진입점에서 일관되게 호출.

**위반 패턴 (리뷰에서 BLOCKER):**
```kotlin
// Bad — UseCase 우회
if (subscriptionTier == "FREE" && file.endsWith(".raw")) return  // 분산 분기

// Bad — UI 단계에서만 차단
if (file.isRaw && !user.isPro) hideButton()  // 우회 가능
```

**올바른 패턴:**
```kotlin
val validation = validateImageFormatUseCase(file, currentTier)
when (validation) {
    is Allowed -> proceed()
    is Denied -> showPaywall(validation.reason)
}
```

## 결제 검증 (Google Play Billing)

**클라이언트 측 검증만 의존 금지.** 결제 토큰은 반드시 서버(Cloud Functions 또는 사내 백엔드)에서 Google Play Developer API의 `purchases.subscriptions.get`으로 재검증한다.

체크포인트:
- [ ] `BillingClient.queryPurchasesAsync` 결과를 서버 RPC로 전송하여 진위 확인
- [ ] `acknowledgePurchase` 호출 (미 acknowledge 3일 후 자동 환불)
- [ ] 구매 상태는 **Firebase Custom Claims** 또는 백엔드 권한 테이블에 기록
- [ ] 클라이언트는 Custom Claims 또는 백엔드 응답을 신뢰. SharedPreferences 같은 변조 가능 저장소를 권한 진실원천으로 쓰지 않는다.

## Firebase Custom Claims

```
idTokenResult.claims["tier"]  // "FREE" | "BASIC" | "PRO" | "REFERRER" | "ADMIN"
idTokenResult.claims["admin"] // ADMIN 전용 기능
```

- 클라이언트는 `getIdTokenResult(forceRefresh = true)`로 변경 직후 즉시 반영.
- 백엔드는 결제 검증 성공 시 Custom Claims 갱신.
- claims 만료 시 자동 재발급되므로 클라이언트는 매번 강제 새로고침할 필요 없음 (1시간 캐시 OK).

## Play Integrity

- 결제·로그인 등 민감 액션 진입 전 `IntegrityManager.requestIntegrityToken` 호출.
- 백엔드에서 토큰 검증 후 허용 여부 결정.
- 디버그 빌드에서는 우회 가능하므로 release 빌드 전용 강제.

## 다운로드 해상도 제한 (FREE)

FREE 티어는 2000px(긴 변 기준) 제한. 이 로직도 `ValidateImageFormatUseCase` 또는 동급의 단일 지점에서 결정한다(분산 분기 금지).

## 결제 실패 / 환불 복구

- 결제 실패는 사용자 친화 메시지로 표시(Snackbar/Dialog).
- 환불 시 백엔드가 Custom Claims를 즉시 FREE로 강등 → 클라이언트는 `idTokenResult` 재발급 후 UI 갱신.
- 캐시된 권한이 백엔드와 불일치하면 백엔드를 우선시.

## 보안 리뷰 체크리스트 (camcon-reviewer 용)

`camcon-reviewer`가 결제/구독 변경 PR을 볼 때 강제로 확인:
- [ ] `ValidateImageFormatUseCase` 외부에서 RAW/PNG/JPG 분기 추가 없는가
- [ ] `BillingClient` 결과를 서버 검증 없이 즉시 권한 부여하지 않는가
- [ ] Custom Claims를 SharedPreferences나 in-memory 변수로만 대체하지 않는가
- [ ] `acknowledgePurchase` 호출이 있는가
- [ ] 디버그 모드 우회 코드가 release에 노출되지 않는가
- [ ] Play Integrity 검증이 결제·관리 액션에 적용되는가

## 자주 발견된 결함 (회귀 주의)

CLAUDE.md "알려진 이슈"에는 결제 관련 보안 결함 일괄 수정 이력이 있음:
- 8b0bc39 — Google Play Billing 8.3.0 마이그레이션 (Play 정책 준수)
- 6836920 — 결제 보안 결함 일괄 수정 (CRITICAL 4개 + HIGH 3개 + MEDIUM/LOW)
- 1eda304 — 보안 재리뷰 발견 결함 후속 수정 (MEDIUM 2개 + LOW 1개)

이 영역을 손볼 때는 위 커밋들을 우선 참조하여 **회귀 방지**.
