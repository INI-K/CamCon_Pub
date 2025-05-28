package com.inik.camcon.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.domain.model.User
import com.inik.camcon.domain.usecase.auth.SignInWithGoogleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLoggedIn: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun signInWithGoogle(idToken: String) {
        Log.d("LoginViewModel", "signInWithGoogle called with idToken: ${idToken.take(10)}...")

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                signInWithGoogleUseCase(idToken)
                    .fold(
                        onSuccess = { user ->
                            Log.d("LoginViewModel", "Sign in successful for user: ${user.email}")
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                isLoggedIn = true
                            )
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

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
