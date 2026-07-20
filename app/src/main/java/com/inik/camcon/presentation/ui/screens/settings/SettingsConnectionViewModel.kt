package com.inik.camcon.presentation.ui.screens.settings

import androidx.lifecycle.ViewModel
import com.inik.camcon.presentation.viewmodel.CameraUiState
import com.inik.camcon.presentation.viewmodel.state.CameraUiStateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * 설정 화면 전용 경량 ViewModel — 연결 상태 표시에 필요한 [CameraUiState]만 노출한다.
 *
 * SettingsActivity 는 별도 Activity 라 CameraViewModel 을 주입하면 인스턴스가 2개 공존하고,
 * 무거운 협력자(operationsManager 등 @Singleton 매니저) cleanup 이 진행 중 라이브뷰를 끊을 수 있다.
 * 이 VM 은 @Singleton [CameraUiStateManager] 의 상태 StateFlow 만 그대로 전달하므로
 * 어떤 협력자도 소유하지 않고, onCleared 에서 cleanup 을 트리거하지 않는다.
 */
@HiltViewModel
class SettingsConnectionViewModel @Inject constructor(
    uiStateManager: CameraUiStateManager
) : ViewModel() {

    val uiState: StateFlow<CameraUiState> = uiStateManager.uiState
}
