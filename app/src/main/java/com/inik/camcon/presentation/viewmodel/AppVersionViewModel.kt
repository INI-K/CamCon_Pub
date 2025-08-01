package com.inik.camcon.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.domain.model.AppVersionInfo
import com.inik.camcon.domain.usecase.CheckAppVersionUseCase
import com.inik.camcon.domain.usecase.StartImmediateUpdateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppVersionUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val versionInfo: AppVersionInfo? = null,
    val showUpdateDialog: Boolean = false
)

@HiltViewModel
class AppVersionViewModel @Inject constructor(
    private val checkAppVersionUseCase: CheckAppVersionUseCase,
    private val startImmediateUpdateUseCase: StartImmediateUpdateUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppVersionUiState())
    val uiState: StateFlow<AppVersionUiState> = _uiState.asStateFlow()

    fun checkForUpdate() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            checkAppVersionUseCase().fold(
                onSuccess = { versionInfo ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            versionInfo = versionInfo,
                            showUpdateDialog = versionInfo.isUpdateRequired || versionInfo.isUpdateAvailable
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "버전 체크 중 오류가 발생했습니다"
                        )
                    }
                }
            )
        }
    }

    fun startUpdate() {
        viewModelScope.launch {
            startImmediateUpdateUseCase().fold(
                onSuccess = {
                    // 업데이트 시작 성공
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(error = error.message ?: "업데이트 시작 중 오류가 발생했습니다")
                    }
                }
            )
        }
    }

    fun dismissUpdateDialog() {
        _uiState.update { it.copy(showUpdateDialog = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}