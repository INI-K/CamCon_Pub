package com.inik.camcon.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.inik.camcon.R
import com.inik.camcon.domain.model.User
import com.inik.camcon.domain.usecase.auth.DeleteAccountUseCase
import com.inik.camcon.domain.usecase.auth.GetCurrentUserUseCase
import com.inik.camcon.domain.usecase.auth.SignOutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val isSignOutSuccess: Boolean = false,
    val isDeletingAccount: Boolean = false,
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
    object AccountDeleteSuccess : AuthUiEvent()
    data class AccountDeleteFailure(val message: String) : AuthUiEvent()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val signOutUseCase: SignOutUseCase,
    private val deleteAccountUseCase: DeleteAccountUseCase,
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
                        android.util.Log.e("AuthViewModel", "로그아웃 실패", error)
                        _uiState.update { it.copy(isLoading = false) }
                        _uiEvent.emit(AuthUiEvent.ShowError(signOutErrorDetail(error)))
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("AuthViewModel", "로그아웃 예외", e)
                _uiState.update { it.copy(isLoading = false) }
                _uiEvent.emit(AuthUiEvent.ShowError(signOutErrorDetail(e)))
            }
        }
    }

    /**
     * 계정·데이터 삭제. 서버(deleteAccount CF)가 서버측 데이터·Auth 사용자까지 삭제한 뒤
     * 로컬 로그아웃까지 수행한다. 성공 시 로그인 화면으로 이동(NavigateToLogin), 실패 시 사유 토스트.
     */
    fun deleteAccount() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeletingAccount = true) }
            try {
                deleteAccountUseCase().fold(
                    onSuccess = {
                        // 서버가 Auth 사용자를 삭제했고 repo가 signOut 했으므로 Google 클라이언트도 정리한다.
                        signOutFromGoogle()
                        _uiState.update { it.copy(isDeletingAccount = false, currentUser = null) }
                        _uiEvent.emit(AuthUiEvent.AccountDeleteSuccess)
                        _uiEvent.emit(AuthUiEvent.NavigateToLogin)
                    },
                    onFailure = { error ->
                        android.util.Log.e("AuthViewModel", "계정 삭제 실패", error)
                        _uiState.update { it.copy(isDeletingAccount = false) }
                        _uiEvent.emit(AuthUiEvent.AccountDeleteFailure(signOutErrorDetail(error)))
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("AuthViewModel", "계정 삭제 예외", e)
                _uiState.update { it.copy(isDeletingAccount = false) }
                _uiEvent.emit(AuthUiEvent.AccountDeleteFailure(signOutErrorDetail(e)))
            }
        }
    }

    /**
     * 로그아웃 실패 사유를 사용자 친화 메시지로 분류한다.
     * raw `error.message`는 로그캣에만 남기고, 여기서는 i18n 가능한 분류 메시지만 반환한다.
     * 이 문자열은 `settings_toast_logout_failed`("…: %s")의 %s 자리에 들어간다.
     */
    private fun signOutErrorDetail(error: Throwable): String = when (error) {
        is IOException -> context.getString(R.string.auth_error_network_detail)
        else -> context.getString(R.string.auth_error_unknown_detail)
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
            android.util.Log.w("AuthViewModel", "Google 로그아웃 실패 (비치명적)", e)
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