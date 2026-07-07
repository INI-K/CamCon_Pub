package com.inik.camcon.presentation.viewmodel

import com.inik.camcon.R
import com.inik.camcon.domain.model.ReferralRedeemReason
import com.inik.camcon.domain.model.UiText

/**
 * 추천 코드 적용 거부 사유 → 사용자용 메시지 매핑.
 *
 * 로그인 화면과 설정 화면이 공유하는 순수 함수(테스트 대상). UNAUTHENTICATED/UNKNOWN 은
 * 기존 일반 문구([R.string.login_referral_error])를 재사용한다.
 */
fun ReferralRedeemReason.toUiText(): UiText = when (this) {
    ReferralRedeemReason.NOT_FOUND -> UiText.Resource(R.string.referral_error_not_found)
    ReferralRedeemReason.ALREADY_USED -> UiText.Resource(R.string.referral_error_already_used)
    ReferralRedeemReason.SELF_REFERRAL -> UiText.Resource(R.string.referral_error_self)
    ReferralRedeemReason.NOT_GRANTABLE -> UiText.Resource(R.string.referral_error_not_grantable)
    ReferralRedeemReason.NETWORK -> UiText.Resource(R.string.referral_error_network)
    ReferralRedeemReason.UNAUTHENTICATED,
    ReferralRedeemReason.UNKNOWN -> UiText.Resource(R.string.login_referral_error)
}
