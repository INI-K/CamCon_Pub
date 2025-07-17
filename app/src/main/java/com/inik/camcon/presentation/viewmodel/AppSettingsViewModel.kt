package com.inik.camcon.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.data.datasource.local.AppPreferencesDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    private val appPreferencesDataSource: AppPreferencesDataSource
) : ViewModel() {

    /**
     * 카메라 컨트롤 표시 여부
     */
    val isCameraControlsEnabled: StateFlow<Boolean> =
        appPreferencesDataSource.isCameraControlsEnabled
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = true
            )

    /**
     * 라이브뷰 표시 여부
     */
    val isLiveViewEnabled: StateFlow<Boolean> = appPreferencesDataSource.isLiveViewEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
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
     * 모든 설정 초기화
     */
    fun resetAllSettings() {
        viewModelScope.launch {
            appPreferencesDataSource.clearAllSettings()
        }
    }
}