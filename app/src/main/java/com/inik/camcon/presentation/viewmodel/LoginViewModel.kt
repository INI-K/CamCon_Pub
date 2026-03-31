package com.inik.camcon.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.domain.model.User
import com.inik.camcon.domain.usecase.auth.SignInWithGoogleUseCase
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

data class LoginUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false
)

/**
 * 일회성 UI 이벤트를 위한 sealed class
 * SharedFlow로 emit되어 스크린 회전 후에도 중복 표시되지 않음
 */
sealed class LoginUiEvent {
    data class ShowError(val message: String) : LoginUiEvent()
    data class ShowReferralMessage(val message: String) : LoginUiEvent()
    object NavigateToHome : LoginUiEvent()
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
                            _uiEvent.emit(
                                LoginUiEvent.ShowError(
                                    "로그인 실패: ${error.message ?: "알 수 없는 오류가 발생했습니다"}"
                                )
                            )
                        }
                    )
            } catch (e: Exception) {
                Log.e("LoginViewModel", "Unexpected error during sign in", e)
                _uiState.update { it.copy(isLoading = false) }
                _uiEvent.emit(
                    LoginUiEvent.ShowError(
                        "예상치 못한 오류: ${e.message ?: "알 수 없는 오류가 발생했습니다"}"
                    )
                )
            }
        }
    }

    private suspend fun processReferralCode(referralCode: String, user: User) {
        try {
            userReferralUseCase.useReferralCode(referralCode)
                .fold(
                    onSuccess = { success ->
                        _uiState.update { it.copy(isLoading = false, isLoggedIn = true) }
                        if (success) {
                            Log.i("LoginViewModel", "추천 코드 사용 성공: $referralCode")
                            _uiEvent.emit(
                                LoginUiEvent.ShowReferralMessage("추천 코드가 성공적으로 적용되었습니다!")
                            )
                        } else {
                            Log.w("LoginViewModel", "추천 코드 사용 실패: $referralCode")
                            _uiEvent.emit(
                                LoginUiEvent.ShowReferralMessage("추천 코드 처리에 실패했습니다.")
                            )
                        }
                        _uiEvent.emit(LoginUiEvent.NavigateToHome)
                    },
                    onFailure = { error ->
                        Log.e("LoginViewModel", "추천 코드 처리 오류: $referralCode", error)
                        _uiState.update { it.copy(isLoading = false, isLoggedIn = true) }
                        _uiEvent.emit(
                            LoginUiEvent.ShowReferralMessage("추천 코드 오류: ${error.message}")
                        )
                        _uiEvent.emit(LoginUiEvent.NavigateToHome)
                    }
                )
        } catch (e: Exception) {
            Log.e("LoginViewModel", "추천 코드 처리 예외: $referralCode", e)
            _uiState.update { it.copy(isLoading = false, isLoggedIn = true) }
            _uiEvent.emit(
                LoginUiEvent.ShowReferralMessage("추천 코드 처리 중 오류가 발생했습니다.")
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
