package com.inik.camcon.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.R
import com.inik.camcon.domain.model.ReferralRedeemException
import com.inik.camcon.domain.model.UiText
import com.inik.camcon.domain.usecase.GetSubscriptionUseCase
import com.inik.camcon.domain.usecase.auth.UserReferralUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReferralRedeemUiState(
    val isLoading: Boolean = false
)

/**
 * 설정 화면 '추천 코드 등록' 결과 1회성 이벤트.
 * 메시지는 [UiText] 로 담아 화면에서 resolve 한다.
 */
sealed class ReferralRedeemEvent {
    data class Success(val message: UiText) : ReferralRedeemEvent()
    data class Error(val message: UiText) : ReferralRedeemEvent()
}

/**
 * 로그인 후 설정 화면에서 추천 코드를 등록하는 소형 ViewModel.
 * 적용 성공 시 구독을 새로고침해 REFERRER 티어가 재로그인 없이 반영되게 한다.
 */
@HiltViewModel
class ReferralRedeemViewModel @Inject constructor(
    private val userReferralUseCase: UserReferralUseCase,
    private val getSubscriptionUseCase: GetSubscriptionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReferralRedeemUiState())
    val uiState: StateFlow<ReferralRedeemUiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<ReferralRedeemEvent>(replay = 0)
    val uiEvent: SharedFlow<ReferralRedeemEvent> = _uiEvent.asSharedFlow()

    fun redeem(rawCode: String) {
        if (_uiState.value.isLoading) return
        val code = rawCode.trim()
        if (code.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            userReferralUseCase.useReferralCode(code).fold(
                onSuccess = {
                    // 서버가 티어를 갱신했으므로 재로그인 없이 반영되도록 구독을 새로고침·영속화.
                    getSubscriptionUseCase.refreshSubscription(forceSync = false)
                    getSubscriptionUseCase.persistSubscriptionTier(
                        getSubscriptionUseCase.invoke().value.tier
                    )
                    _uiState.update { it.copy(isLoading = false) }
                    _uiEvent.emit(
                        ReferralRedeemEvent.Success(
                            UiText.Resource(R.string.settings_referral_redeem_success)
                        )
                    )
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isLoading = false) }
                    val message = (error as? ReferralRedeemException)?.reason?.toUiText()
                        ?: UiText.Resource(R.string.login_referral_error)
                    _uiEvent.emit(ReferralRedeemEvent.Error(message))
                }
            )
        }
    }
}
