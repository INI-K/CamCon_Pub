package com.inik.camcon.presentation.viewmodel

import android.util.Log
import com.inik.camcon.domain.model.ShootingMode
import com.inik.camcon.domain.model.TimelapseSettings
import com.inik.camcon.domain.usecase.camera.CapturePhotoUseCase
import com.inik.camcon.domain.usecase.camera.PerformAutoFocusUseCase
import com.inik.camcon.domain.usecase.camera.StartLiveViewUseCase
import com.inik.camcon.domain.usecase.camera.StartTimelapseUseCase
import com.inik.camcon.domain.usecase.camera.StopLiveViewUseCase
import com.inik.camcon.presentation.viewmodel.state.CameraUiStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
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
    private val startTimelapseUseCase: StartTimelapseUseCase
) {

    companion object {
        private const val TAG = "카메라작업매니저"
    }

    // 작업 관리
    private var liveViewJob: Job? = null
    private var timelapseJob: Job? = null

    /**
     * 사진 촬영
     */
    fun capturePhoto(
        shootingMode: ShootingMode,
        uiStateManager: CameraUiStateManager
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "사진 촬영 요청 시작")
                uiStateManager.updateCapturingState(true)
                uiStateManager.clearError()

                capturePhotoUseCase(shootingMode)
                    .onSuccess { photo ->
                        Log.d(TAG, "사진 촬영 성공: ${photo.filePath}")
                    }
                    .onFailure { error ->
                        Log.e(TAG, "사진 촬영 실패", error)
                        uiStateManager.setError("사진 촬영 실패: ${error.message ?: "알 수 없는 오류"}")
                    }

                uiStateManager.updateCapturingState(false)
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
        if (liveViewJob?.isActive == true) {
            Log.d(TAG, "라이브뷰가 이미 활성화되어 있음")
            return
        }

        Log.d(TAG, "라이브뷰 시작 요청")
        liveViewJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                if (cameraCapabilities != null && !cameraCapabilities.canLiveView) {
                    Log.w(TAG, "카메라가 라이브뷰를 지원하지 않음")
                    uiStateManager.setError("이 카메라는 라이브뷰를 지원하지 않습니다.")
                    return@launch
                }

                if (!isConnected) {
                    Log.e(TAG, "카메라가 연결되지 않은 상태")
                    uiStateManager.setError("카메라가 연결되지 않았습니다. 먼저 카메라를 연결해주세요.")
                    return@launch
                }

                uiStateManager.updateLiveViewState(isLoading = true)
                uiStateManager.clearError()

                startLiveViewUseCase()
                    .catch { error ->
                        Log.e(TAG, "라이브뷰 Flow 오류", error)
                        uiStateManager.updateLiveViewState(isActive = false, isLoading = false)
                        uiStateManager.setError("라이브뷰 시작 실패: ${error.message}")
                    }
                    .collect { frame ->
                        Log.d(TAG, "라이브뷰 프레임 수신: ${frame.data.size} bytes")
                        uiStateManager.updateLiveViewState(
                            isActive = true,
                            isLoading = false,
                            frame = frame
                        )
                        uiStateManager.clearError()
                    }
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

    /**
     * 라이브뷰 중지
     */
    fun stopLiveView(uiStateManager: CameraUiStateManager) {
        liveViewJob?.cancel()
        liveViewJob = null

        CoroutineScope(Dispatchers.IO).launch {
            try {
                stopLiveViewUseCase()
                uiStateManager.updateLiveViewState(
                    isActive = false,
                    isLoading = false,
                    frame = null
                )
                Log.d(TAG, "라이브뷰 중지 성공")
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
        if (timelapseJob?.isActive == true) return

        val settings = TimelapseSettings(
            interval = interval,
            totalShots = totalShots,
            duration = (interval * totalShots) / 60
        )

        timelapseJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                uiStateManager.updateCapturingState(true)
                uiStateManager.setShootingMode(ShootingMode.TIMELAPSE)

                startTimelapseUseCase(settings)
                    .catch { error ->
                        Log.e(TAG, "타임랩스 실행 중 오류", error)
                        uiStateManager.updateCapturingState(false)
                        uiStateManager.setError("타임랩스 시작 실패: ${error.message ?: "알 수 없는 오류"}")
                    }
                    .collect { photo ->
                        Log.d(TAG, "타임랩스 사진 촬영: ${photo.filePath}")
                    }

                uiStateManager.updateCapturingState(false)
            } catch (e: Exception) {
                Log.e(TAG, "타임랩스 중 예외 발생", e)
                uiStateManager.updateCapturingState(false)
                uiStateManager.setError("타임랩스 실패: ${e.message}")
            }
        }
    }

    /**
     * 타임랩스 중지
     */
    fun stopTimelapse(uiStateManager: CameraUiStateManager) {
        timelapseJob?.cancel()
        timelapseJob = null
        uiStateManager.updateCapturingState(false)
    }

    /**
     * 자동초점
     */
    fun performAutoFocus(uiStateManager: CameraUiStateManager) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                uiStateManager.updateFocusingState(true)

                performAutoFocusUseCase()
                    .onSuccess {
                        uiStateManager.updateFocusingState(false)
                        uiStateManager.setError("초점 맞춤 완료")

                        delay(1000)
                        uiStateManager.clearError()
                    }
                    .onFailure { error ->
                        Log.e(TAG, "자동초점 실패", error)
                        uiStateManager.updateFocusingState(false)
                        uiStateManager.setError("자동초점 실패: ${error.message ?: "알 수 없는 오류"}")
                    }
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
    fun isLiveViewActive(): Boolean {
        return liveViewJob?.isActive == true
    }

    /**
     * 타임랩스가 진행 중인지 확인
     */
    fun isTimelapseActive(): Boolean {
        return timelapseJob?.isActive == true
    }

    /**
     * 모든 작업 중지 및 정리
     */
    fun cleanup() {
        liveViewJob?.cancel()
        timelapseJob?.cancel()
        liveViewJob = null
        timelapseJob = null
    }
}