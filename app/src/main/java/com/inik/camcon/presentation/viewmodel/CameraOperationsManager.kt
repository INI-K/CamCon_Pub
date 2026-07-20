package com.inik.camcon.presentation.viewmodel

import android.util.Log
import com.inik.camcon.domain.model.ShootingMode
import com.inik.camcon.domain.model.TimelapseSettings
import com.inik.camcon.domain.model.UnsupportedShootingModeException
import com.inik.camcon.domain.usecase.camera.CapturePhotoUseCase
import com.inik.camcon.domain.usecase.camera.PerformAutoFocusUseCase
import com.inik.camcon.domain.usecase.camera.StartLiveViewUseCase
import com.inik.camcon.domain.usecase.camera.StartTimelapseUseCase
import com.inik.camcon.domain.usecase.camera.StopLiveViewUseCase
import com.inik.camcon.presentation.viewmodel.state.CameraUiStateManager
import com.inik.camcon.presentation.viewmodel.state.InfoMessage
import com.inik.camcon.di.ApplicationScope
import com.inik.camcon.utils.LogMask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 카메라 작업(촬영, 라이브뷰, 타임랩스) 전용 매니저
 * 단일책임: 카메라 촬영 작업만 담당
 */
@Singleton
class CameraOperationsManager @Inject constructor(
    private val capturePhotoUseCase: CapturePhotoUseCase,
    private val startLiveViewUseCase: StartLiveViewUseCase,
    private val stopLiveViewUseCase: StopLiveViewUseCase,
    private val performAutoFocusUseCase: PerformAutoFocusUseCase,
    private val startTimelapseUseCase: StartTimelapseUseCase,
    @ApplicationScope private val appScope: CoroutineScope
) {

    companion object {
        private const val TAG = "카메라작업매니저"

        /**
         * 미지원 촬영 모드 식별 마커 prefix.
         * ViewModel 레이어는 Context가 없으므로 raw 메시지 대신 이 prefix + ShootingMode.name 형태로
         * shootingModeError에 저장하고, UI 레이어에서 stringResource로 i18n 변환한다.
         */
        const val UNSUPPORTED_MODE_PREFIX = "UNSUPPORTED_MODE:"
    }

    // 앱 scope의 자식 scope — cleanup해도 앱 scope에 영향 없음
    private var managerScope = createManagerScope()

    private fun createManagerScope(): CoroutineScope =
        CoroutineScope(appScope.coroutineContext + SupervisorJob(appScope.coroutineContext.job))

    // 작업 관리
    // liveViewJob/timelapseJob은 메인 스레드 진입점(ViewModel 호출)과 managerScope(Default) 워커가
    // 교차 접근하므로 모든 읽기/쓰기(체크-후-취소, 할당)를 jobLock으로 직렬화한다.
    private val jobLock = Any()
    private var liveViewJob: Job? = null
    private var timelapseJob: Job? = null

    /**
     * 사진 촬영
     */
    fun capturePhoto(
        shootingMode: ShootingMode,
        uiStateManager: CameraUiStateManager
    ) {
        managerScope.launch {
            try {
                Log.d(TAG, "사진 촬영 요청 시작")
                uiStateManager.updateCapturingState(true)
                uiStateManager.clearError()

                // 라이브뷰 활성 상태면 Job 취소 + UI 상태 업데이트
                // 네이티브 라이브뷰 중지는 capturePhotoAsync() 내부에서 동기적으로 처리
                val wasLiveViewActive = synchronized(jobLock) {
                    if (liveViewJob?.isActive == true) {
                        liveViewJob?.cancel()
                        liveViewJob = null
                        true
                    } else {
                        false
                    }
                }
                if (wasLiveViewActive) {
                    Log.d(TAG, "라이브뷰 활성 상태 — 촬영 전 라이브뷰 Job 취소")
                    uiStateManager.updateLiveViewState(
                        isActive = false,
                        isLoading = false,
                        frame = null
                    )
                }

                capturePhotoUseCase(shootingMode)
                    .onSuccess { photo ->
                        Log.d(TAG, "사진 촬영 성공: ${LogMask.path(photo.filePath)}")
                    }
                    .onFailure { error ->
                        if (error is UnsupportedShootingModeException) {
                            Log.w(TAG, "지원하지 않는 촬영 모드: ${error.message}")
                            // raw 영어 메시지 대신 모드 식별 마커를 전달 → UI가 stringResource로 i18n 처리
                            uiStateManager.setShootingModeError(UNSUPPORTED_MODE_PREFIX + error.mode.name)
                        } else {
                            Log.e(TAG, "사진 촬영 실패", error)
                            uiStateManager.setError("사진 촬영 실패: ${error.message ?: "알 수 없는 오류"}")
                        }
                    }

                uiStateManager.updateCapturingState(false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "사진 촬영 중 예외 발생", e)
                uiStateManager.updateCapturingState(false)
                uiStateManager.setError("사진 촬영 실패: ${e.message}")
            }
        }
    }

    /**
     * 라이브뷰 시작
     */
    fun startLiveView(
        isConnected: Boolean,
        cameraCapabilities: com.inik.camcon.domain.model.CameraCapabilities?,
        uiStateManager: CameraUiStateManager
    ) {
        synchronized(jobLock) {
            if (liveViewJob?.isActive == true) {
                Log.d(TAG, "라이브뷰가 이미 활성화되어 있음")
                return
            }

            Log.d(TAG, "라이브뷰 시작 요청")
            Log.d(TAG, "  isConnected=$isConnected, canLiveView=${cameraCapabilities?.canLiveView}, capabilities=${cameraCapabilities != null}")
            liveViewJob = managerScope.launch {
            try {
                Log.d(TAG, "라이브뷰 코루틴 시작됨")
                if (cameraCapabilities != null && !cameraCapabilities.canLiveView) {
                    Log.w(TAG, "카메라가 라이브뷰를 지원하지 않음")
                    uiStateManager.setError("이 카메라는 라이브뷰를 지원하지 않습니다.")
                    return@launch
                }

                if (!isConnected) {
                    Log.e(TAG, "카메라가 연결되지 않은 상태 — isConnected=$isConnected")
                    uiStateManager.setError("카메라가 연결되지 않았습니다. 먼저 카메라를 연결해주세요.")
                    return@launch
                }

                Log.d(TAG, "라이브뷰 UseCase 호출 시작")
                uiStateManager.updateLiveViewState(isLoading = true)
                uiStateManager.clearError()

                startLiveViewUseCase()
                    .catch { error ->
                        Log.e(TAG, "라이브뷰 Flow 오류", error)
                        uiStateManager.updateLiveViewState(isActive = false, isLoading = false)
                        uiStateManager.setError("라이브뷰 시작 실패: ${error.message}")
                    }
                    .collect { frame ->
                        uiStateManager.updateLiveViewState(
                            isActive = true,
                            isLoading = false,
                            frame = frame
                        )
                        uiStateManager.clearError()
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "라이브뷰 시작 중 예외 발생", e)
                uiStateManager.updateLiveViewState(
                    isActive = false,
                    isLoading = false,
                    frame = null
                )
                uiStateManager.setError("라이브뷰 시작 실패: ${e.message}")
            }
        }
        }
    }

    /**
     * 라이브뷰 중지
     */
    fun stopLiveView(uiStateManager: CameraUiStateManager) {
        synchronized(jobLock) {
            liveViewJob?.cancel()
            liveViewJob = null
        }

        managerScope.launch {
            try {
                stopLiveViewUseCase()
                uiStateManager.updateLiveViewState(
                    isActive = false,
                    isLoading = false,
                    frame = null
                )
                Log.d(TAG, "라이브뷰 중지 성공")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "라이브뷰 중지 중 예외 발생", e)
                uiStateManager.updateLiveViewState(
                    isActive = false,
                    isLoading = false,
                    frame = null
                )
                uiStateManager.setError("라이브뷰 중지 실패: ${e.message}")
            }
        }
    }

    /**
     * 타임랩스 시작
     */
    fun startTimelapse(
        interval: Int,
        totalShots: Int,
        uiStateManager: CameraUiStateManager
    ) {
        synchronized(jobLock) {
            if (timelapseJob?.isActive == true) return

            val settings = TimelapseSettings(
                interval = interval,
                totalShots = totalShots,
                duration = (interval * totalShots) / 60
            )

            timelapseJob = managerScope.launch {
            try {
                uiStateManager.updateCapturingState(true)
                uiStateManager.setShootingMode(ShootingMode.TIMELAPSE)

                startTimelapseUseCase(settings)
                    .catch { error ->
                        if (error is UnsupportedShootingModeException) {
                            Log.w(TAG, "지원하지 않는 촬영 모드: ${error.message}")
                            // raw 영어 메시지 대신 모드 식별 마커를 전달 → UI가 stringResource로 i18n 처리
                            uiStateManager.setShootingModeError(UNSUPPORTED_MODE_PREFIX + error.mode.name)
                        } else {
                            Log.e(TAG, "타임랩스 실행 중 오류", error)
                            uiStateManager.setError("타임랩스 시작 실패: ${error.message ?: "알 수 없는 오류"}")
                        }
                        uiStateManager.updateCapturingState(false)
                        // 실패 시 모드를 SINGLE로 되돌려 셔터가 TIMELAPSE 무한 실패 루프에 묶이지 않게 한다.
                        uiStateManager.setShootingMode(ShootingMode.SINGLE)
                    }
                    .collect { photo ->
                        Log.d(TAG, "타임랩스 사진 촬영: ${LogMask.path(photo.filePath)}")
                    }

                uiStateManager.updateCapturingState(false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "타임랩스 중 예외 발생", e)
                uiStateManager.updateCapturingState(false)
                uiStateManager.setError("타임랩스 실패: ${e.message}")
            }
        }
        }
    }

    /**
     * 타임랩스 중지
     */
    fun stopTimelapse(uiStateManager: CameraUiStateManager) {
        synchronized(jobLock) {
            timelapseJob?.cancel()
            timelapseJob = null
        }
        uiStateManager.updateCapturingState(false)
    }

    /**
     * 자동초점
     */
    fun performAutoFocus(uiStateManager: CameraUiStateManager) {
        managerScope.launch {
            try {
                uiStateManager.updateFocusingState(true)

                performAutoFocusUseCase()
                    .onSuccess {
                        uiStateManager.updateFocusingState(false)
                        // 성공은 에러 채널이 아닌 1-shot 정보 메시지로 전달 (의미 오용 제거)
                        uiStateManager.emitInfoMessage(InfoMessage.AutoFocusCompleted)
                    }
                    .onFailure { error ->
                        Log.e(TAG, "자동초점 실패", error)
                        uiStateManager.updateFocusingState(false)
                        uiStateManager.setError("자동초점 실패: ${error.message ?: "알 수 없는 오류"}")
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "자동초점 중 예외 발생", e)
                uiStateManager.updateFocusingState(false)
                uiStateManager.setError("자동초점 실패: ${e.message}")
            }
        }
    }

    /**
     * 라이브뷰가 활성화되어 있는지 확인
     */
    fun isLiveViewActive(): Boolean = synchronized(jobLock) {
        liveViewJob?.isActive == true
    }

    /**
     * 타임랩스가 진행 중인지 확인
     */
    fun isTimelapseActive(): Boolean = synchronized(jobLock) {
        timelapseJob?.isActive == true
    }

    /**
     * 진행 중인 작업만 중지 (scope은 유지 — @Singleton이므로 재사용됨)
     */
    fun cleanup() {
        synchronized(jobLock) {
            liveViewJob?.cancel()
            timelapseJob?.cancel()
            liveViewJob = null
            timelapseJob = null
        }
        managerScope.coroutineContext.job.cancel()
        managerScope = createManagerScope()
    }
}