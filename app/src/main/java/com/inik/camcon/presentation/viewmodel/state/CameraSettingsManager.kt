package com.inik.camcon.presentation.viewmodel.state

import com.inik.camcon.di.IoDispatcher
import com.inik.camcon.domain.manager.ErrorSeverity
import com.inik.camcon.domain.manager.ErrorType
import com.inik.camcon.domain.model.CameraCapabilities
import com.inik.camcon.domain.model.ExposureCompensation
import com.inik.camcon.domain.model.StorageInfo
import com.inik.camcon.domain.util.Logger
import com.inik.camcon.domain.model.CameraSettings
import com.inik.camcon.domain.usecase.camera.GetCameraCapabilitiesUseCase
import com.inik.camcon.domain.usecase.camera.GetCameraSettingsUseCase
import com.inik.camcon.domain.usecase.camera.GetExposureCompensationUseCase
import com.inik.camcon.domain.usecase.camera.GetStorageInfoUseCase
import com.inik.camcon.domain.usecase.camera.SetExposureCompensationUseCase
import com.inik.camcon.domain.usecase.camera.UpdateCameraSettingUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
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
    private val getExposureCompensationUseCase: GetExposureCompensationUseCase,
    private val setExposureCompensationUseCase: SetExposureCompensationUseCase,
    private val getStorageInfoUseCase: GetStorageInfoUseCase,
    private val errorHandlingManager: ErrorHandlingManager,
    private val logger: Logger,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
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

    // 노출 보정(EV) — current/available pair. 미지원 카메라 시 null
    private val _exposureCompensation = MutableStateFlow<ExposureCompensation?>(null)
    val exposureCompensation: StateFlow<ExposureCompensation?> =
        _exposureCompensation.asStateFlow()

    // 카메라 스토리지 정보 — 연결/촬영 완료 시 갱신
    private val _storageInfo = MutableStateFlow<StorageInfo?>(null)
    val storageInfo: StateFlow<StorageInfo?> = _storageInfo.asStateFlow()

    // 설정 캐시 (성능 최적화용) — IO 멀티스레드 동시 접근 대비 ConcurrentHashMap(F24)
    private val settingsCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    // 기능 정보 캐시 — ConcurrentHashMap(F24)
    private val capabilitiesCache = java.util.concurrent.ConcurrentHashMap<String, CameraCapabilities>()

    /**
     * 카메라 설정 로드
     */
    suspend fun loadCameraSettings(cameraId: String? = null) {
        withContext(ioDispatcher) {
            try {
                _isLoadingSettings.value = true
                logger.d(TAG, "카메라 설정 로딩 시작")

                // 캐시에서 먼저 확인
                if (cameraId != null && settingsCache.isNotEmpty()) {
                    logger.d(TAG, "설정 캐시에서 로딩 시도")
                }

                getCameraSettingsUseCase()
                    .onSuccess { settings ->
                        _cameraSettings.value = settings

                        // 개별 설정을 캐시에 저장
                        cacheIndividualSettings(settings)

                        logger.d(TAG, "카메라 설정 로딩 성공")
                        logger.d(TAG, "로딩된 설정: ${settings.availableSettings.size}개")
                    }
                    .onFailure { exception ->
                        logger.e(TAG, "카메라 설정 로딩 실패", exception)
                        val errorMessage = errorHandlingManager.handleConnectionError(exception)
                        errorHandlingManager.emitError(
                            ErrorType.OPERATION,
                            errorMessage,
                            exception,
                            ErrorSeverity.MEDIUM
                        )
                    }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(TAG, "카메라 설정 로딩 중 예외 발생", e)
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
    suspend fun loadCameraCapabilities(cameraId: String? = null) {
        withContext(ioDispatcher) {
            try {
                logger.d(TAG, "카메라 기능 정보 로딩 시작")

                // 캐시에서 먼저 확인
                if (cameraId != null) {
                    capabilitiesCache[cameraId]?.let { cachedCapabilities ->
                        logger.d(TAG, "기능 정보 캐시에서 로딩")
                        _cameraCapabilities.value = cachedCapabilities
                        return@withContext
                    }
                }

                getCameraCapabilitiesUseCase()
                    .onSuccess { capabilitiesNullable ->
                        val capabilities = capabilitiesNullable ?: run {
                            logger.e(TAG, "카메라 기능 정보가 null 입니다.")
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

                        logger.d(TAG, "카메라 기능 정보 로딩 성공")
                        logger.d(
                            TAG,
                            "지원 기능: 라이브뷰=${capabilities.canLiveView}, 타임랩스=${capabilities.canTimelapse}"
                        )
                    }
                    .onFailure { exception ->
                        logger.e(TAG, "카메라 기능 정보 로딩 실패", exception)
                        val errorMessage = errorHandlingManager.handleConnectionError(exception)
                        errorHandlingManager.emitError(
                            ErrorType.OPERATION,
                            errorMessage,
                            exception,
                            ErrorSeverity.LOW
                        )
                    }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(TAG, "카메라 기능 정보 로딩 중 예외 발생", e)
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
    suspend fun updateCameraSetting(key: String, value: String) {
        withContext(ioDispatcher) {
            try {
                _isUpdatingSettings.value = true
                logger.d(TAG, "카메라 설정 업데이트: $key = $value")

                updateCameraSettingUseCase(key, value)
                    .onSuccess {
                        // 캐시 업데이트
                        settingsCache[key] = value

                        logger.d(TAG, "카메라 설정 업데이트 성공: $key = $value")

                        // 설정 다시 로드하여 동기화
                        loadCameraSettings()
                    }
                    .onFailure { exception ->
                        logger.e(TAG, "카메라 설정 업데이트 실패: $key = $value", exception)
                        val errorMessage = errorHandlingManager.handleConnectionError(exception)
                        errorHandlingManager.emitError(
                            ErrorType.OPERATION,
                            errorMessage,
                            exception,
                            ErrorSeverity.MEDIUM
                        )
                    }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(TAG, "카메라 설정 업데이트 중 예외 발생", e)
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
    suspend fun updateCameraSettings(settings: Map<String, String>) {
        withContext(ioDispatcher) {
            try {
                _isUpdatingSettings.value = true
                logger.d(TAG, "다중 카메라 설정 업데이트 시작: ${settings.size}개 설정")

                var successCount = 0
                var failCount = 0

                settings.forEach { (key, value) ->
                    try {
                        updateCameraSettingUseCase(key, value)
                            .onSuccess {
                                settingsCache[key] = value
                                successCount++
                                logger.d(TAG, "설정 업데이트 성공: $key = $value")
                            }
                            .onFailure { exception ->
                                failCount++
                                logger.e(TAG, "설정 업데이트 실패: $key = $value", exception)
                            }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failCount++
                        logger.e(TAG, "설정 업데이트 중 예외: $key = $value", e)
                    }
                }

                logger.d(TAG, "다중 카메라 설정 업데이트 완료: 성공 $successCount 개, 실패 $failCount 개")

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

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(TAG, "다중 카메라 설정 업데이트 중 예외 발생", e)
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
     * 노출 보정(EV) 현재값/선택지 로드.
     * 미지원 카메라 또는 widget 부재 시 null 유지.
     */
    suspend fun loadExposureCompensation() {
        withContext(ioDispatcher) {
            try {
                getExposureCompensationUseCase()
                    .onSuccess { ev ->
                        _exposureCompensation.value = ev
                        logger.d(TAG, "EV 로딩 결과: current=${ev?.current}, count=${ev?.available?.size}")
                    }
                    .onFailure { e ->
                        logger.e(TAG, "EV 로딩 실패", e)
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(TAG, "EV 로딩 중 예외", e)
            }
        }
    }

    /**
     * 노출 보정 값 설정 후 재조회로 동기화.
     * value 는 카메라 widget이 돌려준 raw 문자열(예: "+1/3").
     */
    suspend fun setExposureCompensation(value: String) {
        withContext(ioDispatcher) {
            try {
                _isUpdatingSettings.value = true
                setExposureCompensationUseCase(value)
                    .onSuccess {
                        logger.d(TAG, "EV 설정 성공: $value")
                        loadExposureCompensation()
                    }
                    .onFailure { e ->
                        logger.e(TAG, "EV 설정 실패: $value", e)
                        val msg = errorHandlingManager.handleConnectionError(e)
                        errorHandlingManager.emitError(
                            ErrorType.OPERATION,
                            msg,
                            e,
                            ErrorSeverity.MEDIUM
                        )
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(TAG, "EV 설정 중 예외", e)
            } finally {
                _isUpdatingSettings.value = false
            }
        }
    }

    /**
     * 스토리지 정보 로드. 미지원/조회 실패 시 null 유지.
     */
    suspend fun loadStorageInfo() {
        withContext(ioDispatcher) {
            try {
                getStorageInfoUseCase()
                    .onSuccess { info ->
                        _storageInfo.value = info
                        logger.d(
                            TAG,
                            "스토리지 정보 로딩: free=${info?.freeBytes}, imagesFree=${info?.imagesFree}"
                        )
                    }
                    .onFailure { e ->
                        logger.e(TAG, "스토리지 정보 로딩 실패", e)
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(TAG, "스토리지 정보 로딩 중 예외", e)
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
        logger.d(TAG, "설정 캐시 업데이트 완료: ${settingsCache.size}개 설정")
    }

    /**
     * 설정 캐시 초기화
     */
    fun clearSettingsCache() {
        settingsCache.clear()
        capabilitiesCache.clear()
        logger.d(TAG, "설정 캐시 초기화 완료")
    }

    /**
     * 현재 설정 상태 로깅 (디버깅용)
     */
    fun logCurrentSettings() {
        val settings = _cameraSettings.value
        val capabilities = _cameraCapabilities.value

        logger.d(
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
        _exposureCompensation.value = null
        _storageInfo.value = null
        settingsCache.clear()
        capabilitiesCache.clear()
        _isLoadingSettings.value = false
        _isUpdatingSettings.value = false
        logger.d(TAG, "카메라 설정 매니저 정리 완료")
    }
}