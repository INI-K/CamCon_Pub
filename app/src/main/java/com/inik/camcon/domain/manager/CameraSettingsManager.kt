package com.inik.camcon.domain.manager

import android.util.Log
import com.inik.camcon.domain.model.CameraCapabilities
import com.inik.camcon.domain.model.CameraSettings
import com.inik.camcon.domain.usecase.camera.GetCameraCapabilitiesUseCase
import com.inik.camcon.domain.usecase.camera.GetCameraSettingsUseCase
import com.inik.camcon.domain.usecase.camera.UpdateCameraSettingUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 카메라 설정 관리 전용 매니저
 * 단일책임: 카메라 설정 로딩, 업데이트, 캐싱만 담당
 * Domain Layer: Android Framework에 독립적
 */
@Singleton
class CameraSettingsManager @Inject constructor(
    private val getCameraSettingsUseCase: GetCameraSettingsUseCase,
    private val updateCameraSettingUseCase: UpdateCameraSettingUseCase,
    private val getCameraCapabilitiesUseCase: GetCameraCapabilitiesUseCase,
    private val errorHandlingManager: ErrorHandlingManager
) {

    companion object {
        private const val TAG = "카메라설정매니저"
    }

    // 카메라 설정 상태
    private val _cameraSettings = MutableStateFlow<CameraSettings?>(null)
    val cameraSettings: StateFlow<CameraSettings?> = _cameraSettings.asStateFlow()

    // 카메라 기능 상태
    private val _cameraCapabilities = MutableStateFlow<CameraCapabilities?>(null)
    val cameraCapabilities: StateFlow<CameraCapabilities?> = _cameraCapabilities.asStateFlow()

    // 설정 로딩 상태
    private val _isLoadingSettings = MutableStateFlow(false)
    val isLoadingSettings: StateFlow<Boolean> = _isLoadingSettings.asStateFlow()

    // 설정 업데이트 상태
    private val _isUpdatingSettings = MutableStateFlow(false)
    val isUpdatingSettings: StateFlow<Boolean> = _isUpdatingSettings.asStateFlow()

    // 설정 캐시 (성능 최적화용)
    private val settingsCache = mutableMapOf<String, String>()

    // 기능 정보 캐시
    private val capabilitiesCache = mutableMapOf<String, CameraCapabilities>()

    /**
     * 카메라 설정 로드
     */
    fun loadCameraSettings(cameraId: String? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                _isLoadingSettings.value = true
                Log.d(TAG, "카메라 설정 로딩 시작")

                // 캐시에서 먼저 확인
                if (cameraId != null && settingsCache.isNotEmpty()) {
                    Log.d(TAG, "설정 캐시에서 로딩 시도")
                }

                getCameraSettingsUseCase()
                    .onSuccess { settings ->
                        _cameraSettings.value = settings

                        // 개별 설정을 캐시에 저장
                        cacheIndividualSettings(settings)

                        Log.d(TAG, "카메라 설정 로딩 성공")
                        Log.d(TAG, "로딩된 설정: ${settings.availableSettings.size}개")
                    }
                    .onFailure { exception ->
                        Log.e(TAG, "카메라 설정 로딩 실패", exception)
                        val errorMessage = errorHandlingManager.handleConnectionError(exception)
                        errorHandlingManager.emitError(
                            ErrorType.OPERATION,
                            errorMessage,
                            exception,
                            ErrorSeverity.MEDIUM
                        )
                    }

            } catch (e: Exception) {
                Log.e(TAG, "카메라 설정 로딩 중 예외 발생", e)
                val errorMessage = errorHandlingManager.handleConnectionError(e)
                errorHandlingManager.emitError(
                    ErrorType.OPERATION,
                    errorMessage,
                    e,
                    ErrorSeverity.MEDIUM
                )
            } finally {
                _isLoadingSettings.value = false
            }
        }
    }

    /**
     * 카메라 기능 정보 로드
     */
    fun loadCameraCapabilities(cameraId: String? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "카메라 기능 정보 로딩 시작")

                // 캐시에서 먼저 확인
                if (cameraId != null) {
                    capabilitiesCache[cameraId]?.let { cachedCapabilities ->
                        Log.d(TAG, "기능 정보 캐시에서 로딩")
                        _cameraCapabilities.value = cachedCapabilities
                        return@launch
                    }
                }

                getCameraCapabilitiesUseCase()
                    .onSuccess { capabilitiesNullable ->
                        val capabilities = capabilitiesNullable ?: run {
                            Log.e(TAG, "카메라 기능 정보가 null 입니다.")
                            errorHandlingManager.emitError(
                                ErrorType.OPERATION,
                                "카메라 기능 정보를 불러오지 못했습니다(null)",
                                null,
                                ErrorSeverity.LOW
                            )
                            return@onSuccess
                        }

                        _cameraCapabilities.value = capabilities

                        // 캐시에 저장
                        if (cameraId != null) {
                            capabilitiesCache[cameraId] = capabilities
                        }

                        Log.d(TAG, "카메라 기능 정보 로딩 성공")
                        Log.d(
                            TAG,
                            "지원 기능: 라이브뷰=${capabilities.canLiveView}, 타임랩스=${capabilities.canTimelapse}"
                        )
                    }
                    .onFailure { exception ->
                        Log.e(TAG, "카메라 기능 정보 로딩 실패", exception)
                        val errorMessage = errorHandlingManager.handleConnectionError(exception)
                        errorHandlingManager.emitError(
                            ErrorType.OPERATION,
                            errorMessage,
                            exception,
                            ErrorSeverity.LOW
                        )
                    }

            } catch (e: Exception) {
                Log.e(TAG, "카메라 기능 정보 로딩 중 예외 발생", e)
                val errorMessage = errorHandlingManager.handleConnectionError(e)
                errorHandlingManager.emitError(
                    ErrorType.OPERATION,
                    errorMessage,
                    e,
                    ErrorSeverity.LOW
                )
            }
        }
    }

    /**
     * 카메라 설정 업데이트
     */
    fun updateCameraSetting(key: String, value: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                _isUpdatingSettings.value = true
                Log.d(TAG, "카메라 설정 업데이트: $key = $value")

                updateCameraSettingUseCase(key, value)
                    .onSuccess {
                        // 캐시 업데이트
                        settingsCache[key] = value

                        Log.d(TAG, "카메라 설정 업데이트 성공: $key = $value")

                        // 설정 다시 로드하여 동기화
                        loadCameraSettings()
                    }
                    .onFailure { exception ->
                        Log.e(TAG, "카메라 설정 업데이트 실패: $key = $value", exception)
                        val errorMessage = errorHandlingManager.handleConnectionError(exception)
                        errorHandlingManager.emitError(
                            ErrorType.OPERATION,
                            errorMessage,
                            exception,
                            ErrorSeverity.MEDIUM
                        )
                    }

            } catch (e: Exception) {
                Log.e(TAG, "카메라 설정 업데이트 중 예외 발생", e)
                val errorMessage = errorHandlingManager.handleConnectionError(e)
                errorHandlingManager.emitError(
                    ErrorType.OPERATION,
                    errorMessage,
                    e,
                    ErrorSeverity.MEDIUM
                )
            } finally {
                _isUpdatingSettings.value = false
            }
        }
    }

    /**
     * 여러 카메라 설정을 한 번에 업데이트
     */
    fun updateCameraSettings(settings: Map<String, String>) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                _isUpdatingSettings.value = true
                Log.d(TAG, "다중 카메라 설정 업데이트 시작: ${settings.size}개 설정")

                var successCount = 0
                var failCount = 0

                settings.forEach { (key, value) ->
                    try {
                        updateCameraSettingUseCase(key, value)
                            .onSuccess {
                                settingsCache[key] = value
                                successCount++
                                Log.d(TAG, "설정 업데이트 성공: $key = $value")
                            }
                            .onFailure { exception ->
                                failCount++
                                Log.e(TAG, "설정 업데이트 실패: $key = $value", exception)
                            }
                    } catch (e: Exception) {
                        failCount++
                        Log.e(TAG, "설정 업데이트 중 예외: $key = $value", e)
                    }
                }

                Log.d(TAG, "다중 카메라 설정 업데이트 완료: 성공 $successCount 개, 실패 $failCount 개")

                if (successCount > 0) {
                    // 성공한 설정이 있으면 전체 설정 다시 로드
                    loadCameraSettings()
                }

                if (failCount > 0) {
                    errorHandlingManager.emitError(
                        ErrorType.OPERATION,
                        "$failCount 개의 설정 업데이트에 실패했습니다",
                        null,
                        ErrorSeverity.MEDIUM
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "다중 카메라 설정 업데이트 중 예외 발생", e)
                val errorMessage = errorHandlingManager.handleConnectionError(e)
                errorHandlingManager.emitError(
                    ErrorType.OPERATION,
                    errorMessage,
                    e,
                    ErrorSeverity.MEDIUM
                )
            } finally {
                _isUpdatingSettings.value = false
            }
        }
    }

    /**
     * 특정 설정 값 가져오기 (캐시 우선)
     */
    fun getSettingValue(key: String): String? {
        return settingsCache[key] ?: _cameraSettings.value?.availableSettings?.get(key)
            ?.firstOrNull()
    }

    /**
     * 설정이 지원되는지 확인
     */
    fun isSettingSupported(key: String): Boolean {
        return _cameraSettings.value?.availableSettings?.containsKey(key) == true
    }

    /**
     * 설정의 가능한 값들 가져오기
     */
    fun getAvailableValues(key: String): List<String> {
        return _cameraSettings.value?.availableSettings?.get(key) ?: emptyList()
    }

    /**
     * 카메라 기능 확인
     */
    fun canLiveView(): Boolean {
        return _cameraCapabilities.value?.canLiveView == true
    }

    fun canTimelapse(): Boolean {
        return _cameraCapabilities.value?.canTimelapse == true
    }

    fun canAutoFocus(): Boolean {
        return _cameraCapabilities.value?.canAutoFocus == true
    }

    /**
     * 개별 설정을 캐시에 저장
     */
    private fun cacheIndividualSettings(settings: CameraSettings) {
        settings.availableSettings.forEach { (key, values) ->
            // 현재 값이 있다면 캐시에 저장 (첫 번째 값을 기본값으로 사용)
            if (values.isNotEmpty()) {
                settingsCache[key] = values.first()
            }
        }
        Log.d(TAG, "설정 캐시 업데이트 완료: ${settingsCache.size}개 설정")
    }

    /**
     * 설정 캐시 초기화
     */
    fun clearSettingsCache() {
        settingsCache.clear()
        capabilitiesCache.clear()
        Log.d(TAG, "설정 캐시 초기화 완료")
    }

    /**
     * 현재 설정 상태 로깅 (디버깅용)
     */
    fun logCurrentSettings() {
        val settings = _cameraSettings.value
        val capabilities = _cameraCapabilities.value

        Log.d(
            TAG, """
            현재 카메라 설정 상태:
            - 설정 로딩됨: ${settings != null}
            - 사용 가능한 설정: ${settings?.availableSettings?.size ?: 0}개
            - 캐시된 설정: ${settingsCache.size}개
            - 기능 정보 로딩됨: ${capabilities != null}
            - 라이브뷰 지원: ${capabilities?.canLiveView ?: false}
            - 타임랩스 지원: ${capabilities?.canTimelapse ?: false}
            - 자동초점 지원: ${capabilities?.canAutoFocus ?: false}
        """.trimIndent()
        )
    }

    /**
     * 매니저 정리
     */
    fun cleanup() {
        _cameraSettings.value = null
        _cameraCapabilities.value = null
        settingsCache.clear()
        capabilitiesCache.clear()
        _isLoadingSettings.value = false
        _isUpdatingSettings.value = false
        Log.d(TAG, "카메라 설정 매니저 정리 완료")
    }
}