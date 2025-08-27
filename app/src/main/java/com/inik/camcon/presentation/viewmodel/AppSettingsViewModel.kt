package com.inik.camcon.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.data.datasource.local.AppPreferencesDataSource
import com.inik.camcon.data.datasource.local.ThemeMode
import com.inik.camcon.domain.usecase.ColorTransferUseCase
import com.inik.camcon.domain.usecase.GetSubscriptionUseCase
import com.inik.camcon.domain.model.SubscriptionTier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    private val appPreferencesDataSource: AppPreferencesDataSource,
    private val colorTransferUseCase: ColorTransferUseCase,
    private val getSubscriptionUseCase: GetSubscriptionUseCase
) : ViewModel() {

    /**
     * 카메라 컨트롤 표시 여부
     */
    val isCameraControlsEnabled: StateFlow<Boolean> =
        appPreferencesDataSource.isCameraControlsEnabled
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = false
            )

    /**
     * 라이브뷰 표시 여부 - ADMIN 티어에서만 활성화 가능
     * 기본값을 false로 변경하여 USB 연결 시 기본적으로 수신 화면이 표시되도록 수정
     */
    val isLiveViewEnabled: StateFlow<Boolean> = combine(
        appPreferencesDataSource.isLiveViewEnabled,
        getSubscriptionUseCase.getSubscriptionTier()
    ) { settingEnabled, subscriptionTier ->
        // ADMIN 티어가 아니면 항상 false
        if (subscriptionTier != SubscriptionTier.ADMIN) {
            false
        } else {
            settingEnabled
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    /**
     * 현재 사용자가 ADMIN 티어인지 확인
     */
    val isAdminTier: StateFlow<Boolean> = getSubscriptionUseCase.getSubscriptionTier()
        .map { tier ->
            tier == SubscriptionTier.ADMIN
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    /**
     * 다크 모드 활성화 여부
     */
    val isDarkModeEnabled: StateFlow<Boolean> = appPreferencesDataSource.isDarkModeEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    /**
     * 자동 이벤트 리스너 시작 여부
     */
    val isAutoStartEventListenerEnabled: StateFlow<Boolean> =
        appPreferencesDataSource.isAutoStartEventListenerEnabled
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = true
            )

    /**
     * 비활성화 시 최신 사진 표시 여부
     */
    val isShowLatestPhotoWhenDisabled: StateFlow<Boolean> =
        appPreferencesDataSource.isShowLatestPhotoWhenDisabled
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = true
            )

    /**
     * 색감 전송 기능 활성화 여부
     */
    val isColorTransferEnabled: StateFlow<Boolean> = appPreferencesDataSource.isColorTransferEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    /**
     * 색감 전송 참조 이미지 경로
     */
    val colorTransferReferenceImagePath: StateFlow<String?> =
        appPreferencesDataSource.colorTransferReferenceImagePath
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null
            )

    /**
     * 색감 전송 강도 (0.0 ~ 1.0)
     */
    val colorTransferIntensity: StateFlow<Float> = appPreferencesDataSource.colorTransferIntensity
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.03f
        )

    /**
     * 색감 전송 대상 이미지 경로
     */
    val colorTransferTargetImagePath: StateFlow<String?> =
        appPreferencesDataSource.colorTransferTargetImagePath
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null
            )

    /**
     * 테마 모드 설정
     */
    val themeMode: StateFlow<ThemeMode> = appPreferencesDataSource.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ThemeMode.FOLLOW_SYSTEM
        )

    /**
     * 카메라 컨트롤 표시 여부 설정
     */
    fun setCameraControlsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appPreferencesDataSource.setCameraControlsEnabled(enabled)
        }
    }

    /**
     * 라이브뷰 표시 여부 설정
     */
    fun setLiveViewEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appPreferencesDataSource.setLiveViewEnabled(enabled)
        }
    }

    /**
     * 다크 모드 활성화/비활성화
     */
    fun setDarkModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appPreferencesDataSource.setDarkModeEnabled(enabled)
        }
    }

    /**
     * 자동 이벤트 리스너 시작 활성화/비활성화
     */
    fun setAutoStartEventListenerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appPreferencesDataSource.setAutoStartEventListenerEnabled(enabled)
        }
    }

    /**
     * 비활성화 시 최신 사진 표시 활성화/비활성화
     */
    fun setShowLatestPhotoWhenDisabled(enabled: Boolean) {
        viewModelScope.launch {
            appPreferencesDataSource.setShowLatestPhotoWhenDisabled(enabled)
        }
    }

    /**
     * 색감 전송 기능 활성화/비활성화
     */
    fun setColorTransferEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appPreferencesDataSource.setColorTransferEnabled(enabled)
        }
    }

    /**
     * 색감 전송 참조 이미지 경로 설정
     */
    fun setColorTransferReferenceImagePath(path: String?) {
        viewModelScope.launch {
            colorTransferUseCase.clearReferenceCache()
            appPreferencesDataSource.setColorTransferReferenceImagePath(path)
        }
    }

    /**
     * 색감 전송 강도 설정 (0.0 ~ 1.0)
     */
    fun setColorTransferIntensity(intensity: Float) {
        viewModelScope.launch {
            appPreferencesDataSource.setColorTransferIntensity(intensity)
        }
    }

    /**
     * 색감 전송 대상 이미지 경로 설정
     */
    fun setColorTransferTargetImagePath(path: String?) {
        viewModelScope.launch {
            appPreferencesDataSource.setColorTransferTargetImagePath(path)
        }
    }

    /**
     * 테마 모드 설정
     */
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            appPreferencesDataSource.setThemeMode(mode)
        }
    }

    /**
     * 모든 설정 초기화
     */
    fun resetAllSettings() {
        viewModelScope.launch {
            appPreferencesDataSource.clearAllSettings()
        }
    }
}