package com.inik.camcon.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.domain.model.ReferralCode
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.usecase.auth.AdminUserManagementUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class AdminReferralCodeUiState(
    val isLoading: Boolean = false,
    val isAdmin: Boolean = false,
    val allCodes: List<ReferralCode> = emptyList(),
    val availableCodes: List<ReferralCode> = emptyList(),
    val usedCodes: List<ReferralCode> = emptyList(),
    val statistics: Map<String, Any> = emptyMap(),
    val error: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class AdminReferralCodeViewModel @Inject constructor(
    private val adminUserManagementUseCase: AdminUserManagementUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminReferralCodeUiState())
    val uiState: StateFlow<AdminReferralCodeUiState> = _uiState.asStateFlow()

    init {
        checkAdminStatus()
    }

    /**
     * 관리자 권한 확인
     */
    private fun checkAdminStatus() {
        viewModelScope.launch {
            try {
                val isAdmin = adminUserManagementUseCase.isAdmin()
                _uiState.value = _uiState.value.copy(isAdmin = isAdmin)

                if (isAdmin) {
                    loadReferralCodes()
                    loadStatistics()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "관리자 권한 확인 실패: ${e.message}"
                )
            }
        }
    }

    /**
     * 레퍼럴 코드 목록 새로고침
     */
    fun refreshData() {
        if (_uiState.value.isAdmin) {
            loadReferralCodes()
            loadStatistics()
        }
    }

    /**
     * 레퍼럴 코드 30개 생성
     */
    fun generateReferralCodes(count: Int = 30) {
        if (!_uiState.value.isAdmin) {
            _uiState.value = _uiState.value.copy(error = "관리자 권한이 필요합니다")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                var successCount = 0
                var failCount = 0

                repeat(count) {
                    val code = generateUniqueCode()
                    val result = adminUserManagementUseCase.createReferralCode(
                        code = code,
                        tier = SubscriptionTier.PRO, // 기본적으로 PRO 티어 부여
                        description = "자동 생성된 레퍼럴 코드"
                    )

                    if (result.isSuccess && result.getOrNull() == true) {
                        successCount++
                    } else {
                        failCount++
                    }
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    successMessage = "레퍼럴 코드 생성 완료: 성공 ${successCount}개, 실패 ${failCount}개"
                )

                // 목록 새로고침
                loadReferralCodes()
                loadStatistics()

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "레퍼럴 코드 생성 실패: ${e.message}"
                )
            }
        }
    }

    /**
     * 사용하지 않은 레퍼럴 코드 하나 추출
     */
    fun getOneAvailableCode(): String? {
        val availableCodes = _uiState.value.availableCodes
        return if (availableCodes.isNotEmpty()) {
            availableCodes.first().code
        } else {
            null
        }
    }

    /**
     * 사용 가능한 레퍼럴 코드 하나 추출하고 클립보드에 복사할 수 있도록 반환
     */
    fun extractOneAvailableCode(): String? {
        val code = getOneAvailableCode()
        if (code != null) {
            _uiState.value = _uiState.value.copy(
                successMessage = "레퍼럴 코드 추출: $code"
            )
        } else {
            _uiState.value = _uiState.value.copy(
                error = "사용 가능한 레퍼럴 코드가 없습니다"
            )
        }
        return code
    }

    /**
     * 특정 레퍼럴 코드 삭제
     */
    fun deleteReferralCode(code: String) {
        if (!_uiState.value.isAdmin) {
            _uiState.value = _uiState.value.copy(error = "관리자 권한이 필요합니다")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val result = adminUserManagementUseCase.deleteReferralCode(code)

                if (result.isSuccess && result.getOrNull() == true) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "레퍼럴 코드 '$code' 삭제 완료"
                    )

                    // 목록 새로고침
                    loadReferralCodes()
                    loadStatistics()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "레퍼럴 코드 삭제 실패"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "레퍼럴 코드 삭제 실패: ${e.message}"
                )
            }
        }
    }

    /**
     * 레퍼럴 코드 목록 로드
     */
    private fun loadReferralCodes() {
        viewModelScope.launch {
            try {
                val allCodesResult = adminUserManagementUseCase.getAllReferralCodes()
                val availableCodesResult = adminUserManagementUseCase.getAvailableReferralCodes()
                val usedCodesResult = adminUserManagementUseCase.getUsedReferralCodes()

                if (allCodesResult.isSuccess && availableCodesResult.isSuccess && usedCodesResult.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        allCodes = allCodesResult.getOrNull() ?: emptyList(),
                        availableCodes = availableCodesResult.getOrNull() ?: emptyList(),
                        usedCodes = usedCodesResult.getOrNull() ?: emptyList()
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "레퍼럴 코드 목록 로드 실패: ${e.message}"
                )
            }
        }
    }

    /**
     * 레퍼럴 코드 통계 로드
     */
    private fun loadStatistics() {
        viewModelScope.launch {
            try {
                val result = adminUserManagementUseCase.getReferralCodeStatistics()
                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        statistics = result.getOrNull() ?: emptyMap()
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "통계 로드 실패: ${e.message}"
                )
            }
        }
    }

    /**
     * 고유한 레퍼럴 코드 생성
     */
    private fun generateUniqueCode(): String {
        val uuid = UUID.randomUUID().toString().replace("-", "").uppercase()
        return "REF${uuid.take(8)}"
    }

    /**
     * 에러 메시지 지우기
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * 성공 메시지 지우기
     */
    fun clearSuccessMessage() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }
}