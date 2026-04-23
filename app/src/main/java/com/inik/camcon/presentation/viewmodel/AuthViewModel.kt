package com.inik.camcon.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.inik.camcon.R
import com.inik.camcon.domain.model.User
import com.inik.camcon.domain.usecase.auth.GetCurrentUserUseCase
import com.inik.camcon.domain.usecase.auth.SignOutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val isSignOutSuccess: Boolean = false,
    val currentUser: User? = null
)

/**
 * 일회성 UI 이벤트를 위한 sealed class
 * SharedFlow로 emit되어 스크린 회전 후에도 중복 표시되지 않음
 */
sealed class AuthUiEvent {
    data class ShowError(val message: String) : AuthUiEvent()
    object SignOutSuccess : AuthUiEvent()
    object NavigateToLogin : AuthUiEvent()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val signOutUseCase: SignOutUseCase,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    // 일회성 이벤트용 SharedFlow (replay = 0으로 화면 회전 시 중복 방지)
    private val _uiEvent = MutableSharedFlow<AuthUiEvent>(replay = 0)
    val uiEvent: SharedFlow<AuthUiEvent> = _uiEvent.asSharedFlow()

    init {
        // 현재 사용자 정보를 실시간으로 관찰
        viewModelScope.launch {
            getCurrentUserUseCase().collect { user ->
                _uiState.update { it.copy(currentUser = user) }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isSignOutSuccess = false) }
            try {
                // Firebase 로그아웃
                signOutUseCase().fold(
                    onSuccess = {
                        // Google 로그인 클라이언트에서도 로그아웃
                        signOutFromGoogle()
                        _uiState.update { it.copy(isLoading = false, isSignOutSuccess = true) }
                        _uiEvent.emit(AuthUiEvent.SignOutSuccess)
                        _uiEvent.emit(AuthUiEvent.NavigateToLogin)
                    },
                    onFailure = { error ->
                        _uiState.update { it.copy(isLoading = false) }
                        _uiEvent.emit(AuthUiEvent.ShowError(error.message ?: "로그아웃 실패"))
                    }
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
                _uiEvent.emit(AuthUiEvent.ShowError(e.message ?: "로그아웃 실패"))
            }
        }
    }

    private fun signOutFromGoogle() {
        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(context.getString(R.string.default_web_client_id))
                .requestEmail()
                .build()

            val googleSignInClient = GoogleSignIn.getClient(context, gso)
            googleSignInClient.signOut()
        } catch (e: Exception) {
            // Google 로그아웃 실패는 치명적이지 않으므로 로그만 남김
            e.printStackTrace()
        }
    }

    /**
     * @deprecated SharedFlow 이벤트로 대체됨. 이벤트는 자동으로 소비됨.
     */
    @Deprecated("Use SharedFlow events - events are consumed automatically")
    fun clearError() {
        // SharedFlow 이벤트로 대체되었으므로 더 이상 사용하지 않음
    }
}