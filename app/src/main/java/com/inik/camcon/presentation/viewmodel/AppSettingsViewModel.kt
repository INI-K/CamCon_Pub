package com.inik.camcon.presentation.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.domain.model.ThemeMode
import com.inik.camcon.domain.repository.AppSettingsRepository
import com.inik.camcon.domain.usecase.ColorTransferUseCase
import com.inik.camcon.domain.usecase.GetSubscriptionUseCase
import com.inik.camcon.domain.usecase.camera.ReadNativeLogUseCase
import com.inik.camcon.domain.usecase.camera.StartNativeLogUseCase
import com.inik.camcon.domain.usecase.camera.StopNativeLogUseCase
import com.inik.camcon.domain.model.SubscriptionTier
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appSettingsRepository: AppSettingsRepository,
    private val colorTransferUseCase: ColorTransferUseCase,
    private val getSubscriptionUseCase: GetSubscriptionUseCase,
    private val startNativeLogUseCase: StartNativeLogUseCase,
    private val stopNativeLogUseCase: StopNativeLogUseCase,
    private val readNativeLogUseCase: ReadNativeLogUseCase
) : ViewModel() {

    companion object {
        private const val TAG = "AppSettingsViewModel"
    }

    /**
     * 카메라 컨트롤 표시 여부
     */
    val isCameraControlsEnabled: StateFlow<Boolean> =
        appSettingsRepository.isCameraControlsEnabled
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
        appSettingsRepository.isLiveViewEnabled,
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
    val isDarkModeEnabled: StateFlow<Boolean> = appSettingsRepository.isDarkModeEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    /**
     * 자동 이벤트 리스너 시작 여부
     */
    val isAutoStartEventListenerEnabled: StateFlow<Boolean> =
        appSettingsRepository.isAutoStartEventListenerEnabled
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = true
            )

    /**
     * 비활성화 시 최신 사진 표시 여부
     */
    val isShowLatestPhotoWhenDisabled: StateFlow<Boolean> =
        appSettingsRepository.isShowLatestPhotoWhenDisabled
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = true
            )

    /**
     * 색감 전송 기능 활성화 여부
     */
    val isColorTransferEnabled: StateFlow<Boolean> = appSettingsRepository.isColorTransferEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    /**
     * 색감 전송 참조 이미지 경로
     */
    val colorTransferReferenceImagePath: StateFlow<String?> =
        appSettingsRepository.colorTransferReferenceImagePath
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null
            )

    /**
     * 색감 전송 강도 (0.0 ~ 1.0)
     */
    val colorTransferIntensity: StateFlow<Float> = appSettingsRepository.colorTransferIntensity
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.03f
        )

    /**
     * 색감 전송 대상 이미지 경로
     */
    val colorTransferTargetImagePath: StateFlow<String?> =
        appSettingsRepository.colorTransferTargetImagePath
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null
            )

    /**
     * RAW 파일 다운로드 활성화 여부
     */
    val isRawFileDownloadEnabled: StateFlow<Boolean> =
        appSettingsRepository.isRawFileDownloadEnabled
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = true
            )

    /**
     * 현재 구독 티어 - Preferences에 저장된 값 우선, 없으면 Firebase에서 가져옴
     */
    val subscriptionTier: StateFlow<com.inik.camcon.domain.model.SubscriptionTier> =
        combine(
            appSettingsRepository.subscriptionTierEnum,
            getSubscriptionUseCase.getSubscriptionTier()
        ) { prefTier, firebaseTier ->
            // Preferences에 저장된 티어가 FREE가 아니면 우선 사용
            // FREE인 경우는 초기값일 수 있으므로 Firebase 값 확인
            if (prefTier != SubscriptionTier.FREE) {
                prefTier
            } else {
                firebaseTier ?: SubscriptionTier.FREE
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = com.inik.camcon.domain.model.SubscriptionTier.FREE
        )

    /**
     * 테마 모드 설정
     */
    val themeMode: StateFlow<ThemeMode> = appSettingsRepository.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ThemeMode.FOLLOW_SYSTEM
        )

    /**
     * 네이티브 로그 캡처 활성화 여부
     */
    val isNativeLogCaptureEnabled: StateFlow<Boolean> =
        appSettingsRepository.isNativeLogCaptureEnabled
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = false
            )

    // 로그 파일 경로 생성
    private fun getLogFilePath(): String {
        val logDir = context.filesDir
        return "${logDir}/libgphoto2_debug_${System.currentTimeMillis()}.txt"
    }

    /**
     * 카메라 컨트롤 표시 여부 설정
     */
    fun setCameraControlsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setCameraControlsEnabled(enabled)
        }
    }

    /**
     * 라이브뷰 표시 여부 설정
     */
    fun setLiveViewEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setLiveViewEnabled(enabled)
        }
    }

    /**
     * 다크 모드 활성화/비활성화
     */
    fun setDarkModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setDarkModeEnabled(enabled)
        }
    }

    /**
     * 자동 이벤트 리스너 시작 활성화/비활성화
     */
    fun setAutoStartEventListenerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setAutoStartEventListenerEnabled(enabled)
        }
    }

    /**
     * 비활성화 시 최신 사진 표시 활성화/비활성화
     */
    fun setShowLatestPhotoWhenDisabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setShowLatestPhotoWhenDisabled(enabled)
        }
    }

    /**
     * 색감 전송 기능 활성화/비활성화
     */
    fun setColorTransferEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setColorTransferEnabled(enabled)
        }
    }

    /**
     * 색감 전송 참조 이미지 경로 설정
     */
    fun setColorTransferReferenceImagePath(path: String?) {
        viewModelScope.launch {
            colorTransferUseCase.clearReferenceCache()
            appSettingsRepository.setColorTransferReferenceImagePath(path)
        }
    }

    /**
     * 색감 전송 강도 설정 (0.0 ~ 1.0)
     */
    fun setColorTransferIntensity(intensity: Float) {
        viewModelScope.launch {
            appSettingsRepository.setColorTransferIntensity(intensity)
        }
    }

    /**
     * 색감 전송 대상 이미지 경로 설정
     */
    fun setColorTransferTargetImagePath(path: String?) {
        viewModelScope.launch {
            appSettingsRepository.setColorTransferTargetImagePath(path)
        }
    }

    /**
     * RAW 파일 다운로드 활성화 여부 설정
     */
    fun setRawFileDownloadEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setRawFileDownloadEnabled(enabled)
        }
    }

    /**
     * 테마 모드 설정
     */
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            appSettingsRepository.setThemeMode(mode)
        }
    }

    /**
     * 네이티브 로그 캡처 활성화/비활성화
     */
    fun setNativeLogCaptureEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setNativeLogCaptureEnabled(enabled)

            if (enabled) {
                // 로그 캡처 시작
                val logPath = getLogFilePath()
                // GP_LOG_DEBUG 레벨로 설정 (DATA는 전송 바이너리 hexdump 포함하여 로그 폭증)
                val result = startNativeLogUseCase(logPath)

                if (result) {
                    Log.i(TAG, "네이티브 로그 캡처 시작: $logPath")
                } else {
                    Log.e(TAG, "네이티브 로그 파일 시작 실패")
                    // 실패 시 설정 원복
                    appSettingsRepository.setNativeLogCaptureEnabled(false)
                }
            } else {
                // 로그 캡처 중지
                stopNativeLogUseCase()
                Log.i(TAG, "네이티브 로그 캡처 중지")
            }
        }
    }

    /**
     * 로그 파일 내용 가져오기
     */
    suspend fun getLogFileContent(): String {
        return try {
            val logFiles = context.filesDir.listFiles { file ->
                file.name.startsWith("libgphoto2_debug_") && file.name.endsWith(".txt")
            }

            val latestLog = logFiles?.maxByOrNull { it.lastModified() }

            if (latestLog != null) {
                readNativeLogUseCase(latestLog.absolutePath)
            } else {
                "로그 파일이 없습니다."
            }
        } catch (e: Exception) {
            Log.e(TAG, "로그 파일 읽기 실패", e)
            "로그 파일 읽기 실패: ${e.message}"
        }
    }

    /**
     * 로그 파일 목록 가져오기
     */
    fun getLogFiles(): List<String> {
        return try {
            val logFiles = context.filesDir.listFiles { file ->
                file.name.startsWith("libgphoto2_debug_") && file.name.endsWith(".txt")
            }

            logFiles?.map { it.absolutePath }?.sortedDescending() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "로그 파일 목록 조회 실패", e)
            emptyList()
        }
    }

    /**
     * 모든 설정 초기화
     */
    fun resetAllSettings() {
        viewModelScope.launch {
            appSettingsRepository.clearAllSettings()
        }
    }
}