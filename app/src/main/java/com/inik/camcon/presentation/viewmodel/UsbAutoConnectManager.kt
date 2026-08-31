package com.inik.camcon.presentation.viewmodel

import android.content.Context
import android.util.Log
import com.inik.camcon.R
import com.inik.camcon.domain.model.UiText
import com.inik.camcon.domain.repository.AppSettingsRepository
import com.inik.camcon.domain.repository.CameraRepository
import com.inik.camcon.domain.repository.UsbDeviceRepository
import com.inik.camcon.domain.usecase.camera.ConnectCameraUseCase
import com.inik.camcon.domain.usecase.camera.DisconnectCameraUseCase
import com.inik.camcon.domain.usecase.usb.RefreshUsbDevicesUseCase
import com.inik.camcon.domain.usecase.usb.RequestUsbPermissionUseCase
import com.inik.camcon.presentation.viewmodel.state.CameraUiStateManager
import com.inik.camcon.presentation.viewmodel.state.InfoMessage
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

        /**
         * 케이블을 **방금 꽂은** 상황에서 시스템 앱 선택지("한 번만 / 항상")에 사람이 응답할
         * 시간을 주는 유예시간.
         *
         * 종전 2초는 기계가 자동 부여를 배달하는 시간만 본 값이라, 사람이 선택지를 읽고 "항상"을
         * 고르는 몇 초를 못 기다렸다. 그래서 첫 연결에서 시스템 선택지와 앱 자체 권한 다이얼로그
         * **두 개가 겹쳐 떴다**(실기 로그: ATTACHED 2초 뒤 "권한 자동부여 미도착, 프로그램적 권한
         * 요청(폴백)"). 이 값은 사용자가 선택지를 무시했을 때 폴백까지 걸리는 시간이기도 하므로
         * 무한정 늘리지 않는다.
         */
        private const val ATTACH_PERMISSION_GRACE_MS = 12000L

        /** 유예 중 권한 도착을 확인하는 주기. 도착 즉시 진행해 체감 지연을 만들지 않는다. */
        private const val PERMISSION_POLL_INTERVAL_MS = 1000L

        /**
         * 이 시간 안에 부착 브로드캐스트를 봤으면 "방금 꽂은 문맥"으로 본다.
         *
         * 설정 화면의 수동 재시도나 콜드 스타트에서 이미 꽂혀 있던 장치는 이 문맥이 아니다 —
         * 그 경로에는 시스템 선택지가 애초에 뜨지 않으므로 기다릴 이유가 없고, 기다리면 사용자가
         * 누른 버튼이 12초 동안 아무 반응 없어 보인다.
         */
        private const val ATTACH_CONTEXT_WINDOW_MS = 5000L
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
                        awaitSystemGrantThenFallback(uiStateManager)
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
     * 시스템이 권한을 자동 부여하기를 기다렸다가, 유예가 끝나도록 오지 않으면 그때서야
     * 앱 자체 권한 다이얼로그를 띄운다.
     *
     * 케이블을 방금 꽂은 문맥에서는 [ATTACH_PERMISSION_GRACE_MS] 만큼 넉넉히 기다린다. 그
     * 순간 화면에는 시스템 앱 선택지가 떠 있고, 사용자가 "항상"을 고르면 권한이 함께 부여되기
     * 때문이다. 짧게 끊으면 사용자가 선택지를 읽는 동안 앱 다이얼로그가 그 위에 겹쳐 뜬다.
     *
     * 기다리는 동안 [PERMISSION_POLL_INTERVAL_MS] 마다 권한 도착을 확인해, 부여되는 즉시
     * 폴백 없이 빠져나온다 — 유예를 늘려도 연결이 늦어지지 않는 이유다.
     *
     * 반대로 그 문맥이 아니면(설정 화면의 수동 재시도, 앱 재기동 등) **기다리지 않는다.**
     * 시스템 선택지가 뜨지 않았으니 자동 부여도 올 수 없어서, 유예는 사용자가 누른 버튼이
     * 아무 반응 없어 보이는 시간일 뿐이다.
     */
    private suspend fun awaitSystemGrantThenFallback(uiStateManager: CameraUiStateManager) {
        val justAttached =
            usbDeviceRepository.msSinceCameraAttached() <= ATTACH_CONTEXT_WINDOW_MS

        if (!justAttached) {
            // 케이블을 꽂은 순간이 아니면 시스템 앱 선택지가 뜨지 않았고, 따라서 자동 부여가
            // 올 데도 없다. 기다려 봐야 아무 일도 일어나지 않으므로 곧바로 요청한다.
            //
            // 판정은 **시스템에 직접 물어서** 한다. 상태 흐름의 캐시는 브로드캐스트로만
            // 갱신되어 낡아 있을 수 있다(실기 로그: "권한=false" 바로 뒤에 "이미 권한이
            // 있습니다"). 캐시를 믿으면 권한이 있는데도 대화상자를 띄우게 된다.
            if (usbDeviceRepository.hasPermissionForAttachedCamera()) {
                // 권한은 이미 있고 캐시만 낡았다. 아래 호출은 대화상자를 띄우지 않고
                // 권한 상태를 실제 값으로 맞춰 주기만 한다(그래야 자동 연결이 이어진다).
                Log.d(TAG, "부착 문맥 아님 - 실검사 결과 권한 보유(캐시가 낡음), 상태만 맞춘다")
            } else {
                Log.d(TAG, "부착 문맥 아님 + 권한 없음 확정 - 유예 없이 즉시 권한 요청")
            }
            requestUsbPermission()
            return
        }

        val graceMs = ATTACH_PERMISSION_GRACE_MS
        Log.d(TAG, "케이블 부착 직후 - 시스템 앱 선택지 응답을 ${graceMs}ms 까지 기다린다")
        maybeShowFirstConnectionHint(uiStateManager)

        var waitedMs = 0L
        while (waitedMs < graceMs) {
            val slice = minOf(PERMISSION_POLL_INTERVAL_MS, graceMs - waitedMs)
            delay(slice)
            waitedMs += slice

            if (usbDeviceRepository.hasUsbPermission.value) {
                Log.d(TAG, "USB 권한이 시스템 경로로 부여됨(${waitedMs}ms) - 프로그램적 요청 생략(churn 방지)")
                return
            }
            if (usbDeviceRepository.connectedDeviceCount.value == 0 || _isAutoConnecting.value) {
                Log.d(TAG, "대기 중 상태 변화(장치 분리 또는 연결 진행) - 권한 요청 폴백 취소")
                return
            }
        }

        if (usbDeviceRepository.connectedDeviceCount.value > 0 &&
            !usbDeviceRepository.hasUsbPermission.value &&
            !_isAutoConnecting.value
        ) {
            // 사용자가 시스템 선택지에서 "한 번만"을 골랐거나 그냥 지나친 경우다. 이 폴백이
            // 없으면 연결할 길이 사라진다.
            Log.d(TAG, "USB 디바이스 감지됨 - 권한 자동부여 미도착, 프로그램적 권한 요청(폴백)")
            requestUsbPermission()
        } else {
            Log.d(TAG, "USB 권한이 attach-intent로 자동 부여됨 - 프로그램적 요청 생략(churn 방지)")
        }
    }

    /**
     * 앱을 쓰면서 처음 USB 카메라를 꽂은 순간, "항상"을 고르면 다음부터 자동으로 연결된다는
     * 사실을 1회만 알린다. 시스템 선택지의 문구는 우리가 바꿀 수 없으므로 앱 안에서 덧붙이는
     * 안내다.
     *
     * 표시 완료 플래그는 스낵바가 실제로 화면에 뜬 뒤 UI 레이어가 저장한다 — 여기서 저장하면
     * 화면이 없어 아무도 못 본 안내가 "본 것"으로 기록된다.
     */
    private suspend fun maybeShowFirstConnectionHint(uiStateManager: CameraUiStateManager) {
        try {
            if (appSettingsRepository.hasSeenUsbPermissionHint.first()) return
            uiStateManager.emitInfoMessage(InfoMessage.UsbPermissionAlwaysHint)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "첫 USB 연결 안내 표시 실패(연결에는 영향 없음)", e)
        }
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
                        uiStateManager.updateUsbInitialization(
                            true,
                            UiText.Resource(R.string.camera_control_usb_initializing)
                        )

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
                uiStateManager.setError(
                    UiText.Resource(
                        R.string.usb_error_disconnect_failed,
                        listOf(e.message ?: UiText.Resource(R.string.error_unknown))
                    )
                )
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
                        uiStateManager.setError(UiText.Resource(R.string.usb_error_permission_requested))
                    } else if (!isConnected) {
                        // 권한이 있고 연결되지 않은 경우 자동 연결 시도
                        Log.d(TAG, "USB 권한 있음 & 미연결 상태 - 자동 연결 시도")
                        uiStateManager.updateUsbInitialization(
                            true,
                            UiText.Resource(R.string.usb_init_connecting_camera)
                        )

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
                        uiStateManager.setError(UiText.Resource(R.string.usb_error_already_connected))
                    }
                } else {
                    Log.d(TAG, "USB 디바이스가 감지되지 않음")
                    uiStateManager.setError(UiText.Resource(R.string.usb_error_camera_not_detected))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "USB 디바이스 새로고침 실패", e)
                uiStateManager.setError(
                    UiText.Resource(
                        R.string.usb_error_device_check_failed,
                        listOf(e.message ?: UiText.Resource(R.string.error_unknown))
                    )
                )
            }
        }
    }

    /**
     * USB 권한 요청
     */
    fun requestUsbPermission(uiStateManager: CameraUiStateManager? = null) {
        managerScope.launch {
            try {
                uiStateManager?.updateUsbInitialization(
                    true,
                    UiText.Resource(R.string.usb_init_requesting_permission)
                )

                val devices = refreshUsbDevicesUseCase()
                if (devices.isNotEmpty()) {
                    val device = devices.first()
                    requestUsbPermissionUseCase(device.deviceId)
                    uiStateManager?.setError(UiText.Resource(R.string.usb_error_permission_requested))
                    uiStateManager?.updateUsbInitialization(
                        false,
                        UiText.Resource(R.string.usb_init_waiting_permission)
                    )
                } else {
                    uiStateManager?.setError(UiText.Resource(R.string.usb_error_camera_not_detected))
                    uiStateManager?.updateUsbInitialization(false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "USB 권한 요청 실패", e)
                uiStateManager?.setError(
                    UiText.Resource(
                        R.string.usb_error_permission_request_failed,
                        listOf(e.message ?: UiText.Resource(R.string.error_unknown))
                    )
                )
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
