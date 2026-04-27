# Cloud Functions — 결제 영수증 검증

CamCon은 클라이언트 결제 신호를 그대로 신뢰하지 않는다. Google Play Billing의 `purchaseToken`을 Cloud Function `verifyAndRecordPurchase`에 전달하면 서버가 Google Play Developer API로 검증한 뒤 Firestore에 기록한다. 클라이언트는 Firestore `users/{uid}/subscriptions/current` 컬렉션에 직접 쓰지 못한다 (`firestore.rules: allow write: if false`).

---

## 1. 사전 준비

### 1.1 로컬 도구

```bash
# Node 20 LTS 설치 (nvm 권장)
nvm install 20 && nvm use 20

# Firebase CLI
npm install -g firebase-tools
firebase --version   # 13.x 이상

# 로그인 (브라우저 OAuth)
firebase login
```

### 1.2 Google Play Developer API 권한

본 함수는 `androidpublisher.purchases.subscriptionsv2.get` 호출이 가능한 서비스 계정이 필요하다.

**옵션 A — Cloud Functions 기본 서비스 계정 사용 (권장)**

1. [Google Play Console](https://play.google.com/console) → **설정 → API 액세스**
2. **Google Cloud 프로젝트 연결**: `camcon-67ad7`
3. **서비스 계정** 섹션에서 Cloud Functions 기본 서비스 계정(`{project-id}@appspot.gserviceaccount.com` 또는 Gen2의 경우 `{project-number}-compute@developer.gserviceaccount.com`)을 찾아 **권한 부여**
4. 권한: **재무 데이터 보기**(Financial data, View) + **앱 정보 보기** 정도면 `purchases.subscriptionsv2.get` 호출 가능

**옵션 B — 별도 서비스 계정 + Secret Manager (필요 시)**

만약 옵션 A가 정책상 불가능하면 별도 service account JSON을 Secret Manager에 등록하고 `functions/index.js`의 `getAndroidPublisher()`에서 명시 로드. (현재 코드는 ADC 사용 — 옵션 A 가정)

### 1.3 Firestore 보안 규칙 배포

`firestore.rules`에 `purchase_tokens/{tokenHash}` 차단 블록이 추가됐다. 함께 배포한다.

```bash
firebase deploy --only firestore:rules
```

---

## 2. Cloud Function 배포

```bash
cd functions
npm install              # 또는 npm ci (lock 파일 미커밋이므로 install 권장)
cd ..
firebase deploy --only functions
```

배포 직후 첫 호출은 cold start로 5~15초 걸릴 수 있다. 한국 사용자 대상이므로 region은 `asia-northeast3` (서울)로 고정.

### 2.1 배포 검증

```bash
# 함수 로그 실시간 모니터
firebase functions:log --only verifyAndRecordPurchase

# 또는
gcloud functions logs read verifyAndRecordPurchase --region=asia-northeast3 --limit=50
```

---

## 3. 동작 흐름

```
[사용자 결제]
    │
    ▼
[Google Play Billing — onPurchasesUpdated 콜백]
    │
    ▼
[BillingDataSourceImpl] ── PURCHASED 필터 + acknowledge 즉시 ──┐
    │                                                              │
    ▼                                                              │
[purchaseUpdateChannel — Channel<List<Purchase>>] ◄────────────────┘
    │
    ▼
[SubscriptionRepositoryImpl.handleVerifiedPurchase]
    │
    ├── 1) acknowledgeSubscription (이미 acknowledged면 skip)
    │
    └── 2) functions.getHttpsCallable("verifyAndRecordPurchase").call({purchaseToken, productId})
            │
            ▼
[Cloud Function] ── Google Play Developer API ──┐
            │                                    │
            ├── 멱등성 체크 (purchase_tokens/{sha256(token)})
            ├── subscriptionState 검증 (ACTIVE/IN_GRACE_PERIOD)
            ├── productId 정합성 검증
            │
            ▼
[Firestore Admin SDK]
    ├── users/{uid}/subscriptions/current  (tier, productId, startDate, endDate, autoRenew, isActive)
    └── purchase_tokens/{sha256(token)}    (uid, productId, claimedAt)
```

---

## 4. 보안 모델

| 항목 | 처리 위치 | 비고 |
|---|---|---|
| 영수증 검증 | Cloud Function | Google Play Developer API로 재검증 — 클라이언트 신호만 신뢰하지 않음 |
| Firestore 쓰기 | Cloud Function (Admin SDK) | 클라이언트 직접 쓰기는 firestore.rules로 차단 |
| purchaseToken 저장 | **저장하지 않음** | Firestore에는 SHA-256 해시만 도큐먼트 ID로 사용 |
| 멱등성 | `purchase_tokens/{tokenHash}` | 다른 uid가 같은 토큰 클레임 시 거부 |
| productId 매핑 | Cloud Function | BASIC/PRO만 — ADMIN/REFERRER는 별도 백엔드 도구로 부여 |
| ADMIN/REFERRER | **클라이언트 결제 경로 차단** | Firebase 콘솔 또는 별도 관리 도구로만 부여 가능 |

---

## 5. 후속 작업 (별도 PR 권장)

- **Real-time Developer Notifications (RTDN)**: 구독 갱신/취소/유예/환불을 Pub/Sub Topic으로 받아 Firestore 자동 갱신. 본 함수만으로는 만료/취소 동기화가 사용자가 앱을 재실행해야 발생한다.
- **만료 정리 함수**: 일일 스케줄로 `users/*/subscriptions/current.endDate < now`인 도큐먼트의 `isActive`를 false로 갱신.
- **ADMIN/REFERRER 부여 도구**: Firebase Auth 커스텀 클레임 또는 Admin SDK 스크립트.
- **테스트**: `firebase-functions-test` 기반 단위 테스트 (현재 devDep만 등록, 테스트 미작성).
- **Firestore 인덱스**: `users` 검색이 늘어나면 `firestore.indexes.json` 갱신.

---

## 6. 문제 해결

| 증상 | 원인 | 조치 |
|---|---|---|
| `permission-denied: 구매 검증에 실패` | service account에 Play Console 권한 없음 | §1.2 옵션 A 권한 부여 후 배포된 함수 인스턴스 재시작 (`firebase deploy --only functions:verifyAndRecordPurchase`) |
| `unauthenticated` | 클라이언트가 미로그인 상태에서 호출 | `FirebaseAuth.getInstance().currentUser` 확인 |
| `failed-precondition` | Google Play API 응답 자체가 에러 | `firebase functions:log`에서 raw error 확인. 잘못된 packageName 또는 API 미활성화 여부 점검 |
| 클라이언트 호출 시 `not-found` | 함수 region 불일치 | `AppModule.provideFirebaseFunctions`가 이미 `asia-northeast3`로 고정 — 함수 region을 변경하면 양쪽 모두 갱신 필요 |
| Cold start 지연 | Cloud Functions 특성 | min instances 옵션(`onCall({ minInstances: 1 })`)으로 완화 가능 (비용 발생) |
