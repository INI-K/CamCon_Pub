# ADR-002: 구독 검증 TTL 및 Fail-Closed 패턴

**날짜**: 2026-04-21  
**상태**: Accepted  
**Issue**: H-3

## 컨텍스트

구독 정보(subscription tier)는 앱의 기능 게이팅을 제어하는 중요한 정보다:
- FREE: 2000px 다운로드 제한
- BASIC: JPG/PNG 포맷 제한
- PRO: 모든 포맷(RAW) 액세스
- REFERRER, ADMIN: 확장 권한

**보안 위협**:
1. **네트워크 장애 시 stale 구독 정보 사용**: 사용자 구독이 만료된 후에도 캐시된 정보로 기능 해제 불가능
2. **토큰 탈취 공격**: 공격자가 만료된 구독으로 높은 권한 기능 사용 시도
3. **TTL 없는 장시간 캐시**: 로컬 캐시만 의존하면 서버와 동기화 불가능

**기존 상태**:
- 구독 정보는 DataStore에 캐시됨 (만료 시간 없음)
- 실패 시 기본값(FREE) 적용 가능하나 조건이 모호함
- SplashActivity에서 로딩하지만 타이밍 보장 없음

## 결정

**Fail-Closed 원칙** + **5분 TTL**을 적용하여 다음을 보장한다:

### 1. 구독 캐시에 TTL 추가

```kotlin
// domain/model/SubscriptionData.kt
data class SubscriptionData(
    val tier: SubscriptionTier,
    val expiresAt: Instant,  // TTL deadline
    val fetchedAt: Instant   // 캐시 생성 시간
) {
    fun isValid(now: Instant = Clock.System.now()): Boolean {
        // 5분 이내 이전 데이터만 신뢰
        return now < expiresAt && (now - fetchedAt) < 5.minutes
    }
}
```

### 2. Fail-Closed: TTL 만료 시 FREE 강등

```kotlin
// data/repository/SubscriptionRepositoryImpl.kt
class SubscriptionRepositoryImpl @Inject constructor(
    private val remoteDataSource: FirebaseSubscriptionDataSource,
    private val localDataSource: DataStoreSubscriptionDataSource
) : SubscriptionRepository {
    
    override suspend fun getSubscriptionTier(): SubscriptionTier = withContext(ioDispatcher) {
        try {
            // 1. 로컬 캐시 확인
            val cached = localDataSource.getSubscriptionData()
            if (cached?.isValid() == true) {
                return@withContext cached.tier  // ✅ 유효한 캐시 사용
            }
            
            // 2. 캐시 만료 → 서버 재확인
            val fresh = remoteDataSource.fetchSubscriptionTier()  // Firebase call
            localDataSource.saveSubscriptionData(
                SubscriptionData(
                    tier = fresh,
                    expiresAt = now + 5.minutes,
                    fetchedAt = now
                )
            )
            return@withContext fresh
        } catch (e: Exception) {
            // 3. 네트워크 실패 + 유효한 캐시 없음 → Fail-Closed
            Timber.e(e, "Failed to fetch subscription, downgrading to FREE")
            return@withContext SubscriptionTier.FREE  // 강등
        }
    }
}
```

### 3. 재시도 정책 (선택적)

```kotlin
// ExponentialBackoff: 1s → 2s → 4s → 8s (최대 3회)
private suspend fun fetchWithRetry(
    attempt: Int = 0
): SubscriptionTier {
    return try {
        remoteDataSource.fetchSubscriptionTier()
    } catch (e: Exception) {
        if (attempt < 3) {
            delay((1 shl attempt).seconds)  // 2^attempt
            fetchWithRetry(attempt + 1)
        } else {
            throw e
        }
    }
}
```

## 결과

### 긍정적 영향
- ✅ **보안 강화**: TTL 만료 후 stale 정보 자동 폐기 → Fail-Closed 강등
- ✅ **네트워크 복원력**: 장시간 오프라인 시에도 FREE tier로 안전 운영
- ✅ **탈취 공격 제한**: 토큰 탈취 후 구독 취소되면 최대 5분 내 자동 제한
- ✅ **명확한 TTL 경계**: "5분 이내" 규칙이 코드에 명시적
- ✅ **감사 로깅**: Firebase에서 구독 변화를 감시 가능

### 부정적 영향 최소화
- **오프라인 사용자**: 5분 이내 캐시는 동작 (충분한 grace period)
- **서버 부하**: 5분 TTL로 재확인 빈도 자동 조절
- **UX**: 캐시 유효 시간 동안 로딩 지연 없음

### 구현 세부사항
- **파일 생성**: `domain/model/SubscriptionData.kt`
- **파일 수정**:
  - `SubscriptionRepositoryImpl.kt` (TTL 검증 로직)
  - `DataStoreSubscriptionDataSource.kt` (expiresAt 저장)
  - `SplashActivity.kt` (로딩 후 강등 감지)

### 검증
- **Kotlin 컴파일**: ✅ BUILD SUCCESSFUL
- **테스트**: TTL 만료 시나리오 단위 테스트 추가
- **통합 테스트**: Firebase 연결 실패 시 FREE 강등 확인

## 대안 검토

1. **즉시 실패 (Fail-Fast)**: 권고하지 않음 (사용자 경험 악화)
   - TTL 만료 후 구독 정보 없음 → 앱 중단
   - 네트워크 재설정 필요

2. **무제한 캐시**: 현재 상태 (보안 위험)
   - stale 정보 노출 기간 무제한
   - 탈취 공격 복구 불가능

3. **서버 동기 필수**: 과도한 요구사항
   - 네트워크 장애 시 모든 기능 중단
   - 구독 정보 로딩 속도 저하

## 규정 준수

- **GDPR**: 사용자 권한 변화 감지 (구독 취소 후 5분 내 제한)
- **PCI DSS** (IAP): 유효하지 않은 인증 거부
- **Google Play Policy**: 구독 상태 정확성

## 승인자

- Security Review: ✅ Fail-Closed pattern verified
- Architecture Review: ✅ DIP + TTL 패턴 준수
- Privacy Review: ✅ GDPR 호환성 확인

---

**구현 일자**: 2026-04-21  
**상태**: Production  
**변경 영향도**: 2개 파일 생성, 3개 파일 수정  
**롤백 난이도**: 낮음 (TTL 검증 로직 제거만 하면 기존 무제한 캐시로 복귀)

## 모니터링

- **Firebase Analytics**: `subscription_tier_changed` 이벤트 로깅
- **Crashlytics**: TTL 만료로 인한 Fail-Closed 강등 추적
- **Dashboard**: 일일 강등 건수, 평균 재인증 시간
