package com.inik.camcon.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.R
import com.inik.camcon.domain.model.UiText
import com.inik.camcon.domain.model.User
import com.inik.camcon.domain.usecase.auth.SignInWithGoogleUseCase
import com.inik.camcon.domain.usecase.auth.UserReferralUseCase
import com.inik.camcon.utils.LogMask
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

data class LoginUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false
)

/**
 * 일회성 UI 이벤트를 위한 sealed class
 * SharedFlow로 emit되어 스크린 회전 후에도 중복 표시되지 않음
 */
sealed class LoginUiEvent {
    data class ShowError(val message: UiText) : LoginUiEvent()
    data class ShowReferralMessage(val message: UiText) : LoginUiEvent()
    object NavigateToHome : LoginUiEvent()
}

/**
 * 로그인 흐름에서 사용자에게 보여줄 친화 메시지로 raw 에러를 분류한다.
 * raw `error.message`는 로그캣에만 남기고 사용자에겐 분류된 메시지(네트워크/인증/알수없음)만 노출한다.
 */
private fun classifyLoginError(error: Throwable): UiText = when (error) {
    is IOException -> UiText.Resource(R.string.login_error_network)
    else -> UiText.Resource(R.string.login_error_auth)
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase,
    private val userReferralUseCase: UserReferralUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // 일회성 이벤트용 SharedFlow (replay = 0으로 화면 회전 시 중복 방지)
    private val _uiEvent = MutableSharedFlow<LoginUiEvent>(replay = 0)
    val uiEvent: SharedFlow<LoginUiEvent> = _uiEvent.asSharedFlow()

    fun signInWithGoogle(idToken: String, referralCode: String? = null) {
        Log.d("LoginViewModel", "signInWithGoogle called")

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                signInWithGoogleUseCase(idToken)
                    .fold(
                        onSuccess = { user ->
                            if (com.inik.camcon.BuildConfig.DEBUG) {
                                Log.d("LoginViewModel", "Sign in successful for user: ${user.email}")
                            }

                            // 추천 코드가 있으면 처리
                            if (!referralCode.isNullOrBlank()) {
                                processReferralCode(referralCode, user)
                            } else {
                                _uiState.update { it.copy(isLoading = false, isLoggedIn = true) }
                                _uiEvent.emit(LoginUiEvent.NavigateToHome)
                            }
                        },
                        onFailure = { error ->
                            Log.e("LoginViewModel", "Sign in failed", error)
                            _uiState.update { it.copy(isLoading = false) }
                            _uiEvent.emit(LoginUiEvent.ShowError(classifyLoginError(error)))
                        }
                    )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("LoginViewModel", "Unexpected error during sign in", e)
                _uiState.update { it.copy(isLoading = false) }
                _uiEvent.emit(LoginUiEvent.ShowError(UiText.Resource(R.string.login_error_unknown)))
            }
        }
    }

    /**
     * Activity 측(GoogleSignInLauncher 콜백)에서 발생한 로그인 취소/실패를 사용자에게 알리는 진입점.
     * ViewModel 의 SharedFlow 단일 채널로 모아 일관된 snackbar 표시를 보장한다.
     */
    fun onSignInUiError(message: UiText) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = false) }
            _uiEvent.emit(LoginUiEvent.ShowError(message))
        }
    }

    private suspend fun processReferralCode(referralCode: String, user: User) {
        try {
            userReferralUseCase.useReferralCode(referralCode)
                .fold(
                    onSuccess = { success ->
                        _uiState.update { it.copy(isLoading = false, isLoggedIn = true) }
                        if (success) {
                            Log.i("LoginViewModel", "추천 코드 사용 성공: ${LogMask.id(referralCode)}")
                            _uiEvent.emit(
                                LoginUiEvent.ShowReferralMessage(
                                    UiText.Resource(R.string.login_referral_applied)
                                )
                            )
                        } else {
                            Log.w("LoginViewModel", "추천 코드 사용 실패: ${LogMask.id(referralCode)}")
                            _uiEvent.emit(
                                LoginUiEvent.ShowReferralMessage(
                                    UiText.Resource(R.string.login_referral_failed)
                                )
                            )
                        }
                        _uiEvent.emit(LoginUiEvent.NavigateToHome)
                    },
                    onFailure = { error ->
                        Log.e("LoginViewModel", "추천 코드 처리 오류: ${LogMask.id(referralCode)}", error)
                        _uiState.update { it.copy(isLoading = false, isLoggedIn = true) }
                        _uiEvent.emit(
                            LoginUiEvent.ShowReferralMessage(
                                UiText.Resource(R.string.login_referral_error)
                            )
                        )
                        _uiEvent.emit(LoginUiEvent.NavigateToHome)
                    }
                )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("LoginViewModel", "추천 코드 처리 예외: ${LogMask.id(referralCode)}", e)
            _uiState.update { it.copy(isLoading = false, isLoggedIn = true) }
            _uiEvent.emit(
                LoginUiEvent.ShowReferralMessage(
                    UiText.Resource(R.string.login_referral_error)
                )
            )
            _uiEvent.emit(LoginUiEvent.NavigateToHome)
        }
    }

    /**
     * @deprecated SharedFlow 이벤트로 대체됨. 이벤트는 자동으로 소비됨.
     */
    @Deprecated("Use SharedFlow event instead", ReplaceWith(""))
    fun clearError() {
        // SharedFlow 사용으로 더 이상 필요 없음
    }

    /**
     * @deprecated SharedFlow 이벤트로 대체됨. 이벤트는 자동으로 소비됨.
     */
    @Deprecated("Use SharedFlow event instead", ReplaceWith(""))
    fun clearReferralMessage() {
        // SharedFlow 사용으로 더 이상 필요 없음
    }
}
