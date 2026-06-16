package com.inik.camcon.presentation.viewmodel

import android.content.Context
import android.util.Log
import com.inik.camcon.domain.repository.AppSettingsRepository
import com.inik.camcon.domain.repository.CameraRepository
import com.inik.camcon.domain.repository.UsbDeviceRepository
import com.inik.camcon.domain.usecase.camera.ConnectCameraUseCase
import com.inik.camcon.domain.usecase.camera.DisconnectCameraUseCase
import com.inik.camcon.domain.usecase.usb.RefreshUsbDevicesUseCase
import com.inik.camcon.domain.usecase.usb.RequestUsbPermissionUseCase
import com.inik.camcon.presentation.viewmodel.state.CameraUiStateManager
import com.inik.camcon.utils.Constants
import com.inik.camcon.utils.LogMask
import com.inik.camcon.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * USB 자동 연결/해제 전용 매니저
 * 단일책임: USB 디바이스 감지 및 자동 연결 상태 관리만 담당
 */
@Singleton
class UsbAutoConnectManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cameraRepository: CameraRepository,
    private val connectCameraUseCase: ConnectCameraUseCase,
    private val disconnectCameraUseCase: DisconnectCameraUseCase,
    private val refreshUsbDevicesUseCase: RefreshUsbDevicesUseCase,
    private val requestUsbPermissionUseCase: RequestUsbPermissionUseCase,
    private val usbDeviceRepository: UsbDeviceRepository,
    private val appSettingsRepository: AppSettingsRepository,
    @ApplicationScope private val appScope: CoroutineScope
) {

    companion object {
        private const val TAG = "카메라연결매니저"

        // 매니페스트 USB_DEVICE_ATTACHED('이 USB 기기에 기본으로 사용' 1회 체크)가 재연결 시 권한을
        // 자동 부여하기를 기다리는 유예시간. 이 안에 권한이 들어오면 프로그램적 권한요청을 생략한다.
        private const val PERMISSION_GRACE_MS = 2000L
    }

    // 앱 scope의 자식 scope — cancelChildren해도 앱 scope에 영향 없음
    private var managerScope = createManagerScope()

    private fun createManagerScope(): CoroutineScope =
        CoroutineScope(appScope.coroutineContext + SupervisorJob(appScope.coroutineContext.job))

    // 내부 상태
    private val _isAutoConnecting = MutableStateFlow(false)
    val isAutoConnecting: StateFlow<Boolean> = _isAutoConnecting.asStateFlow()

    private var connectionJob: Job? = null

    // attach-intent('기본으로 사용') 자동 권한부여를 기다렸다가, 미부여 시에만 프로그램적 권한요청을
    // 띄우는 디바운스 Job. 권한이 들어오면 취소한다(불필요한 권한 다이얼로그 churn 방지).
    private var pendingPermissionJob: Job? = null

    // 연결 Job 교체(취소→대입)를 직렬화해 동시 호출 시 Job 참조 유실 방지
    private val connectionMutex = Mutex()

    /**
     * USB 디바이스 및 권한 상태 관찰
     */
    fun observeUsbDevices(
        scope: CoroutineScope,
        uiStateManager: CameraUiStateManager
    ) {
        // USB 디바이스 상태 관찰
        usbDeviceRepository.connectedDeviceCount
            .onEach { deviceCount ->
                uiStateManager.updateUsbDeviceState(
                    deviceCount,
                    usbDeviceRepository.hasUsbPermission.value
                )

                if (deviceCount > 0 && !usbDeviceRepository.hasUsbPermission.value && !_isAutoConnecting.value) {
                    // 재연결 시 매니페스트 USB_DEVICE_ATTACHED 인텐트필터('기본으로 사용' 1회 체크)가
                    // 권한을 자동 부여한다. 그 자동부여가 도착할 시간을 잠깐 준 뒤에도 미보유일 때만
                    // 프로그램적 requestPermission을 띄운다 — 그래야 attach-intent 자동부여와
                    // 프로그램적 권한 다이얼로그가 충돌해 거부→앱 재시작으로 이어지는 churn을 막는다.
                    pendingPermissionJob?.cancel()
                    pendingPermissionJob = scope.launch {
                        delay(PERMISSION_GRACE_MS)
                        if (usbDeviceRepository.connectedDeviceCount.value > 0 &&
                            !usbDeviceRepository.hasUsbPermission.value &&
                            !_isAutoConnecting.value
                        ) {
                            Log.d(TAG, "USB 디바이스 감지됨 - 권한 자동부여 미도착, 프로그램적 권한 요청(폴백)")
                            requestUsbPermission()
                        } else {
                            Log.d(TAG, "USB 권한이 attach-intent로 자동 부여됨 - 프로그램적 요청 생략(churn 방지)")
                        }
                    }
                }
            }
            .launchIn(scope)

        // USB 권한 상태 관찰
        usbDeviceRepository.hasUsbPermission
            .onEach { hasPermission ->
                val deviceCount = usbDeviceRepository.connectedDeviceCount.value
                uiStateManager.updateUsbDeviceState(deviceCount, hasPermission)

                if (hasPermission) {
                    // 권한이 들어오면(attach-intent 자동부여 등) 대기 중인 프로그램적 요청을 취소한다.
                    pendingPermissionJob?.cancel()
                }

                if (hasPermission && deviceCount > 0 && !_isAutoConnecting.value) {
                    Log.d(TAG, "USB 권한 획득 - 자동 연결 시작")
                    autoConnectCamera(uiStateManager)
                }
            }
            .launchIn(scope)

        // 통합 연결 로직
        combine(
            usbDeviceRepository.connectedDeviceCount,
            usbDeviceRepository.hasUsbPermission
        ) { deviceCount, hasPermission ->
            Pair(deviceCount, hasPermission)
        }.onEach { (deviceCount, hasPermission) ->
            if (deviceCount > 0 && hasPermission && !_isAutoConnecting.value) {
                Log.d(TAG, "USB 디바이스 및 권한 확인 완료 - 자동 연결 시작")
                autoConnectCamera(uiStateManager)
            }
        }.launchIn(scope)
    }

    /**
     * 자동 카메라 연결
     */
    fun autoConnectCamera(uiStateManager: CameraUiStateManager) {
        managerScope.launch {
            // 기존 연결 Job 취소 + 가드 설정 + 새 Job 대입을 원자적으로 처리
            val job = connectionMutex.withLock {
                if (_isAutoConnecting.value) {
                    Log.d(TAG, "자동 카메라 연결이 이미 진행 중")
                    return@launch
                }
                _isAutoConnecting.value = true
                connectionJob?.cancelAndJoin()

                managerScope.launch {
                    try {
                        Log.d(TAG, "자동 카메라 연결 시작")
                        uiStateManager.updateUsbInitialization(true, "USB 카메라 초기화 중...")

                        connectCameraUseCase("auto")
                            .onSuccess {
                                Log.d(TAG, "자동 카메라 연결 성공")
                                uiStateManager.onConnectionSuccess()

                                // 카메라 전원 상태 확인
                                checkCameraPowerStateAndTest()

                                // 자동 연결 완료 후 이벤트 리스너 자동 시작 시도
                                tryAutoStartEventListener(uiStateManager)
                            }
                            .onFailure { error ->
                                Log.e(TAG, "자동 카메라 연결 실패", error)
                                // 에러 메시지와 함께 연결 상태 업데이트
                                uiStateManager.updateConnectionState(false, error.message)
                                uiStateManager.updateUsbInitialization(false, null)
                                // PtpTimeoutException인 경우 재시작 다이얼로그 표시
                                if (error is com.inik.camcon.domain.model.PtpTimeoutException) {
                                    Log.d(TAG, "PTP 타임아웃 또는 I/O 오류 감지 - 재시작 다이얼로그 표시")
                                    uiStateManager.handlePtpTimeout(error)
                                    uiStateManager.showRestartDialog(true)
                                }
                            }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "자동 카메라 연결 중 예외 발생", e)
                        uiStateManager.updateConnectionState(false, e.message)
                        uiStateManager.updateUsbInitialization(false, null) // USB 초기화 상태 해제 추가
                    } finally {
                        _isAutoConnecting.value = false
                        Log.d(TAG, "자동 카메라 연결 완료")
                    }
                }.also { connectionJob = it }
            }
            job.join()
        }
    }

    /**
     * 자동 연결 완료 후 이벤트 리스너 자동 시작 시도
     */
    private fun tryAutoStartEventListener(uiStateManager: CameraUiStateManager) {
        managerScope.launch {
            try {
                // 자동 시작 설정 확인
                val isAutoStartEnabled =
                    appSettingsRepository.isAutoStartEventListenerEnabled.first()

                if (!isAutoStartEnabled) {
                    Log.d(TAG, "이벤트 리스너 자동 시작 설정이 비활성화됨")
                    return@launch
                }

                Log.d(TAG, "자동 연결 완료 - 이벤트 리스너 자동 시작 시도")

                // 추가 안정화 대기 (네이티브 초기화 완료 확보)
                kotlinx.coroutines.delay(100)

                // 연결 상태 재확인 - USB 또는 PTPIP 연결 확인
                val isConnected = uiStateManager.uiState.value.isConnected
                val isNativeCameraConnected = usbDeviceRepository.isNativeCameraConnected.value
                val isPtpipConnected = uiStateManager.uiState.value.isPtpipConnected

                Log.d(TAG, "연결 상태 재확인: UI=$isConnected, native=$isNativeCameraConnected, ptpip=$isPtpipConnected")

                val isAnyConnectionActive = isNativeCameraConnected || isPtpipConnected
                if (!isConnected || !isAnyConnectionActive) {
                    Log.w(TAG, "연결 상태 재확인 실패 - 이벤트 리스너 시작 중단")
                    return@launch
                }

                // 이미 실행 중인지 확인
                if (cameraRepository.isEventListenerActive().first()) {
                    Log.d(TAG, "이벤트 리스너가 이미 활성화되어 있음")
                    return@launch
                }

                // 저장 디렉토리 준비
                val tempDir = File(context.cacheDir, Constants.FilePaths.TEMP_CACHE_DIR)
                if (!tempDir.exists()) {
                    tempDir.mkdirs()
                }
                val saveDirectory = tempDir.absolutePath
                Log.d(TAG, "이벤트 리스너 저장 디렉토리: ${LogMask.path(saveDirectory)}")

                // 이벤트 리스너 시작 - CameraRepository를 통해 시작
                val result = cameraRepository.startCameraEventListener()

                result.onSuccess {
                    Log.d(TAG, "자동 연결 완료 후 이벤트 리스너 자동 시작 성공")
                }.onFailure { error ->
                    Log.e(TAG, "자동 연결 완료 후 이벤트 리스너 시작 실패", error)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "이벤트 리스너 자동 시작 중 예외", e)
            }
        }
    }

    /**
     * 수동 카메라 연결
     */
    fun connectCamera(cameraId: String, uiStateManager: CameraUiStateManager) {
        managerScope.launch {
            // 진행 중인 자동/수동 연결 Job 취소 + 새 Job 대입을 원자적으로 처리
            val job = connectionMutex.withLock {
                connectionJob?.cancelAndJoin()

                managerScope.launch {
                    try {
                        uiStateManager.updateLoadingState(true)
                        uiStateManager.clearError()

                        connectCameraUseCase(cameraId)
                            .onSuccess {
                                Log.d(TAG, "카메라 연결 성공")
                                uiStateManager.onConnectionSuccess()
                            }
                            .onFailure { error ->
                                Log.e(TAG, "카메라 연결 실패", error)
                                // 에러 메시지와 함께 연결 상태 업데이트
                                uiStateManager.updateConnectionState(false, error.message)
                                // PtpTimeoutException인 경우 재시작 다이얼로그 표시
                                if (error is com.inik.camcon.domain.model.PtpTimeoutException) {
                                    Log.d(TAG, "PTP 타임아웃 또는 I/O 오류 감지 - 재시작 다이얼로그 표시")
                                    uiStateManager.handlePtpTimeout(error)
                                    uiStateManager.showRestartDialog(true)
                                }
                            }

                        uiStateManager.updateLoadingState(false)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "카메라 연결 중 예외 발생", e)
                        uiStateManager.updateLoadingState(false)
                        uiStateManager.updateConnectionState(false, e.message)
                        uiStateManager.updateUsbInitialization(false, null) // USB 초기화 상태 해제 추가
                    }
                }.also { connectionJob = it }
            }
            job.join()
        }
    }

    /**
     * 카메라 연결 해제
     */
    fun disconnectCamera(uiStateManager: CameraUiStateManager) {
        Log.d(TAG, "카메라 연결 해제 요청")

        managerScope.launch {
            // 진행 중인 연결 Job이 disconnect 이후 onConnectionSuccess를 호출하지 못하도록
            // 완전히 취소(cancelAndJoin)한 뒤 해제 진행
            connectionMutex.withLock {
                connectionJob?.cancelAndJoin()
                connectionJob = null
            }

            try {
                disconnectCameraUseCase()
                uiStateManager.onCameraDisconnected()
                Log.i(TAG, "카메라 연결 해제 성공")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "카메라 연결 해제 실패", e)
                uiStateManager.setError("카메라 연결 해제 실패: ${e.message}")
            }
        }
    }

    /**
     * USB 디바이스 새로고침
     */
    fun refreshUsbDevices(uiStateManager: CameraUiStateManager) {
        managerScope.launch {
            try {
                Log.d(TAG, "USB 디바이스 새로고침 시작")

                val devices = refreshUsbDevicesUseCase()
                val hasPermission = usbDeviceRepository.hasUsbPermission.value
                val isConnected = usbDeviceRepository.isNativeCameraConnected.value

                uiStateManager.updateUsbDeviceState(devices.size, hasPermission)

                if (devices.isNotEmpty()) {
                    val device = devices.first()

                    if (!hasPermission) {
                        // 권한이 없으면 권한 요청
                        Log.d(TAG, "USB 권한 없음 - 권한 요청")
                        requestUsbPermissionUseCase(device.deviceId)
                        uiStateManager.setError("USB 권한을 요청했습니다. 대화상자에서 승인해주세요.")
                    } else if (!isConnected) {
                        // 권한이 있고 연결되지 않은 경우 자동 연결 시도
                        Log.d(TAG, "USB 권한 있음 & 미연결 상태 - 자동 연결 시도")
                        uiStateManager.updateUsbInitialization(true, "USB 카메라 연결 시도 중...")

                        // 직접 연결 시도
                        connectCameraUseCase("auto")
                            .onSuccess {
                                Log.d(TAG, "새로고침 후 카메라 연결 성공")
                                uiStateManager.onConnectionSuccess()

                                // 카메라 전원 상태 확인
                                checkCameraPowerStateAndTest()

                                // 이벤트 리스너 자동 시작 시도
                                tryAutoStartEventListener(uiStateManager)
                            }
                            .onFailure { error ->
                                Log.e(TAG, "새로고침 후 카메라 연결 실패", error)
                                uiStateManager.updateConnectionState(false, error.message)
                                uiStateManager.updateUsbInitialization(false, null)
                                // PtpTimeoutException인 경우 재시작 다이얼로그 표시
                                if (error is com.inik.camcon.domain.model.PtpTimeoutException) {
                                    Log.d(TAG, "PTP 타임아웃 또는 I/O 오류 감지 - 재시작 다이얼로그 표시")
                                    uiStateManager.handlePtpTimeout(error)
                                    uiStateManager.showRestartDialog(true)
                                }
                            }
                    } else {
                        // 이미 연결되어 있는 경우
                        Log.d(TAG, "이미 카메라가 연결되어 있음")
                        uiStateManager.setError("카메라가 이미 연결되어 있습니다")
                    }
                } else {
                    Log.d(TAG, "USB 디바이스가 감지되지 않음")
                    uiStateManager.setError("USB 카메라가 감지되지 않았습니다")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "USB 디바이스 새로고침 실패", e)
                uiStateManager.setError("USB 디바이스 확인 실패: ${e.message}")
            }
        }
    }

    /**
     * USB 권한 요청
     */
    fun requestUsbPermission(uiStateManager: CameraUiStateManager? = null) {
        managerScope.launch {
            try {
                uiStateManager?.updateUsbInitialization(true, "USB 권한 요청 중...")

                val devices = refreshUsbDevicesUseCase()
                if (devices.isNotEmpty()) {
                    val device = devices.first()
                    requestUsbPermissionUseCase(device.deviceId)
                    uiStateManager?.setError("USB 권한을 요청했습니다. 대화상자에서 승인해주세요.")
                    uiStateManager?.updateUsbInitialization(false, "USB 권한 대기 중...")
                } else {
                    uiStateManager?.setError("USB 카메라가 감지되지 않았습니다")
                    uiStateManager?.updateUsbInitialization(false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "USB 권한 요청 실패", e)
                uiStateManager?.setError("USB 권한 요청 실패: ${e.message}")
                uiStateManager?.updateUsbInitialization(false)
            }
        }
    }

    /**
     * 카메라 전원 상태 확인 및 테스트
     */
    private fun checkCameraPowerStateAndTest() {
        managerScope.launch {
            try {
                Log.d(TAG, "자동 연결 완료 후 카메라 전원 상태 확인 중...")
                usbDeviceRepository.checkPowerStateAndTest()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "카메라 전원 상태 확인 중 오류", e)
            }
        }
    }

    /**
     * 정리
     */
    fun cleanup() {
        managerScope.coroutineContext.job.cancel()
        managerScope = createManagerScope()
        connectionJob = null
        _isAutoConnecting.value = false
    }
}
