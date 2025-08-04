package com.inik.camcon.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.domain.model.User
import com.inik.camcon.domain.usecase.auth.SignInWithGoogleUseCase
import com.inik.camcon.domain.usecase.auth.UserReferralUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLoggedIn: Boolean = false,
    val referralCodeMessage: String? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase,
    private val userReferralUseCase: UserReferralUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun signInWithGoogle(idToken: String, referralCode: String? = null) {
        Log.d("LoginViewModel", "signInWithGoogle called with idToken: ${idToken.take(10)}...")

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                signInWithGoogleUseCase(idToken)
                    .fold(
                        onSuccess = { user ->
                            Log.d("LoginViewModel", "Sign in successful for user: ${user.email}")

                            // 추천 코드가 있으면 처리
                            if (!referralCode.isNullOrBlank()) {
                                processReferralCode(referralCode, user)
                            } else {
                                _uiState.value = _uiState.value.copy(
                                    isLoading = false,
                                    isLoggedIn = true
                                )
                            }
                        },
                        onFailure = { error ->
                            Log.e("LoginViewModel", "Sign in failed", error)
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = "로그인 실패: ${error.message ?: "알 수 없는 오류가 발생했습니다"}"
                            )
                        }
                    )
            } catch (e: Exception) {
                Log.e("LoginViewModel", "Unexpected error during sign in", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "예상치 못한 오류: ${e.message ?: "알 수 없는 오류가 발생했습니다"}"
                )
            }
        }
    }

    private suspend fun processReferralCode(referralCode: String, user: User) {
        try {
            userReferralUseCase.useReferralCode(referralCode)
                .fold(
                    onSuccess = { success ->
                        if (success) {
                            Log.i("LoginViewModel", "추천 코드 사용 성공: $referralCode")
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                isLoggedIn = true,
                                referralCodeMessage = "추천 코드가 성공적으로 적용되었습니다!"
                            )
                        } else {
                            Log.w("LoginViewModel", "추천 코드 사용 실패: $referralCode")
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                isLoggedIn = true,
                                referralCodeMessage = "추천 코드 처리에 실패했습니다."
                            )
                        }
                    },
                    onFailure = { error ->
                        Log.e("LoginViewModel", "추천 코드 처리 오류: $referralCode", error)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isLoggedIn = true,
                            referralCodeMessage = "추천 코드 오류: ${error.message}"
                        )
                    }
                )
        } catch (e: Exception) {
            Log.e("LoginViewModel", "추천 코드 처리 예외: $referralCode", e)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isLoggedIn = true,
                referralCodeMessage = "추천 코드 처리 중 오류가 발생했습니다."
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearReferralMessage() {
        _uiState.value = _uiState.value.copy(referralCodeMessage = null)
    }
}
