package com.inik.camcon.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.di.IoDispatcher
import com.inik.camcon.domain.model.SubscriptionProduct
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.usecase.GetSubscriptionUseCase
import com.inik.camcon.domain.usecase.PurchaseSubscriptionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 구독 페이월 화면 상태.
 *
 * - [currentTier]: 현재 사용자 티어. 게이팅 단일 진실원천인 [GetSubscriptionUseCase] StateFlow를 구독해 반영.
 * - [products]: Google Play Billing에서 조회한 상품 목록. 조회 실패/빈 결과면 비어 있다.
 * - [billingUnavailable]: Billing 상품 조회가 빈 목록을 돌려준 경우(개발 빌드, Play Console 미등록 등).
 *   true면 화면은 티어 enum 기반 정적 카탈로그로 폴백한다.
 * - [purchaseInProgress]: 결제 시트 호출~결과 반영 사이 진행 상태.
 * - [purchaseSuccess]: 결제 시트가 정상 호출되었음(실제 권한 부여는 서버 검증 후 Custom Claims로 반영).
 */
data class SubscriptionUiState(
    val currentTier: SubscriptionTier = SubscriptionTier.FREE,
    val products: List<SubscriptionProduct> = emptyList(),
    val isLoading: Boolean = true,
    val billingUnavailable: Boolean = false,
    val purchaseInProgress: Boolean = false,
    val purchaseSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val getSubscriptionUseCase: GetSubscriptionUseCase,
    private val purchaseSubscriptionUseCase: PurchaseSubscriptionUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubscriptionUiState())
    val uiState: StateFlow<SubscriptionUiState> = _uiState.asStateFlow()

    init {
        observeCurrentTier()
        loadProducts()
    }

    /**
     * 게이팅 단일 진실원천인 [GetSubscriptionUseCase] StateFlow를 구독해 현재 티어를 반영한다.
     * 결제 성공 시 서버 검증 → Custom Claims 갱신 → 이 Flow로 티어 변경이 전파된다.
     */
    private fun observeCurrentTier() {
        viewModelScope.launch {
            getSubscriptionUseCase.getSubscriptionTier().collect { tier ->
                _uiState.update { it.copy(currentTier = tier) }
            }
        }
    }

    private fun loadProducts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val products = withContext(ioDispatcher) {
                    purchaseSubscriptionUseCase.getAvailableSubscriptions()
                }
                _uiState.update {
                    it.copy(
                        products = products,
                        billingUnavailable = products.isEmpty(),
                        isLoading = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        products = emptyList(),
                        billingUnavailable = true,
                        isLoading = false,
                        error = e.localizedMessage
                    )
                }
            }
        }
    }

    /**
     * 결제 시트를 호출한다. 반환 Boolean은 "결제 시트가 정상적으로 떠올랐는지"이며,
     * 실제 권한 부여는 서버(Cloud Function)의 결제 검증 + Custom Claims 갱신 후
     * [observeCurrentTier]를 통해 티어로 반영된다.
     */
    fun purchase(productId: String) {
        if (_uiState.value.purchaseInProgress) return
        viewModelScope.launch {
            _uiState.update { it.copy(purchaseInProgress = true, error = null, purchaseSuccess = false) }
            try {
                val launched = withContext(ioDispatcher) {
                    purchaseSubscriptionUseCase.purchaseSubscription(productId)
                }
                _uiState.update {
                    it.copy(
                        purchaseInProgress = false,
                        purchaseSuccess = launched,
                        error = if (launched) null else it.error
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        purchaseInProgress = false,
                        purchaseSuccess = false,
                        error = e.localizedMessage
                    )
                }
            }
        }
    }

    /** Play Store 재설치/디바이스 변경 시 활성 구독 복원. */
    fun restore() {
        if (_uiState.value.purchaseInProgress) return
        viewModelScope.launch {
            _uiState.update { it.copy(purchaseInProgress = true, error = null) }
            try {
                withContext(ioDispatcher) {
                    purchaseSubscriptionUseCase.restoreSubscription()
                }
                getSubscriptionUseCase.refreshSubscription(forceSync = true)
                _uiState.update { it.copy(purchaseInProgress = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(purchaseInProgress = false, error = e.localizedMessage)
                }
            }
        }
    }

    fun consumeError() {
        _uiState.update { it.copy(error = null) }
    }

    fun consumePurchaseSuccess() {
        _uiState.update { it.copy(purchaseSuccess = false) }
    }
}
