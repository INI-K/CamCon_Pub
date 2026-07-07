package com.inik.camcon.presentation.viewmodel

import com.inik.camcon.R
import com.inik.camcon.domain.model.ReferralRedeemReason
import com.inik.camcon.domain.model.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ReferralRedeemReason.toUiText] 순수 매퍼 검증.
 * 사유 → 사용자 메시지 리소스 매핑이 계약대로인지 확인한다.
 */
class ReferralErrorMapperTest {

    @Test
    fun `사유별 리소스 매핑이 계약대로다`() {
        assertEquals(
            UiText.Resource(R.string.referral_error_not_found),
            ReferralRedeemReason.NOT_FOUND.toUiText()
        )
        assertEquals(
            UiText.Resource(R.string.referral_error_already_used),
            ReferralRedeemReason.ALREADY_USED.toUiText()
        )
        assertEquals(
            UiText.Resource(R.string.referral_error_self),
            ReferralRedeemReason.SELF_REFERRAL.toUiText()
        )
        assertEquals(
            UiText.Resource(R.string.referral_error_not_grantable),
            ReferralRedeemReason.NOT_GRANTABLE.toUiText()
        )
        assertEquals(
            UiText.Resource(R.string.referral_error_network),
            ReferralRedeemReason.NETWORK.toUiText()
        )
        // UNAUTHENTICATED/UNKNOWN 은 일반 문구를 재사용한다.
        assertEquals(
            UiText.Resource(R.string.login_referral_error),
            ReferralRedeemReason.UNAUTHENTICATED.toUiText()
        )
        assertEquals(
            UiText.Resource(R.string.login_referral_error),
            ReferralRedeemReason.UNKNOWN.toUiText()
        )
    }

    @Test
    fun `모든 사유가 리소스 텍스트로 매핑된다`() {
        ReferralRedeemReason.values().forEach { reason ->
            assertTrue(reason.toUiText() is UiText.Resource)
        }
    }
}
