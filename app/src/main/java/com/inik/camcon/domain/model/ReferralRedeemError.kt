package com.inik.camcon.domain.model

/**
 * 추천 코드 적용(redeemReferralCode CF) 거부 사유.
 *
 * 서버 HttpsError → 이 사유로 매핑되어 UI 가 화면별 메시지를 고를 수 있게 한다.
 * (data 레이어가 FirebaseFunctionsException.code 를 이 enum 으로 변환)
 */
enum class ReferralRedeemReason {
    /** 존재하지 않는 코드 (not-found) */
    NOT_FOUND,

    /** 이미 사용된 코드 / 이미 추천을 받은 계정 (already-exists) */
    ALREADY_USED,

    /** 본인 추천 코드 (permission-denied) */
    SELF_REFERRAL,

    /** 부여 불가 티어·관리자·유료 구독 이용 중 (failed-precondition) */
    NOT_GRANTABLE,

    /** 미인증 (unauthenticated) */
    UNAUTHENTICATED,

    /** 네트워크성 오류 (unavailable/deadline-exceeded/IO) */
    NETWORK,

    /** 그 외 알 수 없는 오류 */
    UNKNOWN
}

/**
 * 추천 코드 적용 실패를 사유와 함께 전달하는 예외.
 * `Result.failure(ReferralRedeemException(reason, cause))` 형태로 상위(UseCase/ViewModel)에 흐른다.
 */
class ReferralRedeemException(
    val reason: ReferralRedeemReason,
    cause: Throwable? = null
) : Exception("referral redeem failed: $reason", cause)
