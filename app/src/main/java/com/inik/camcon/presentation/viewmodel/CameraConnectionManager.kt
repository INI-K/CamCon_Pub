package com.inik.camcon.presentation.viewmodel

import android.content.Context
import android.util.Log
import com.inik.camcon.data.datasource.usb.UsbCameraManager
import com.inik.camcon.domain.repository.CameraRepository
import com.inik.camcon.domain.usecase.camera.ConnectCameraUseCase
import com.inik.camcon.domain.usecase.camera.DisconnectCameraUseCase
import com.inik.camcon.domain.usecase.usb.RefreshUsbDevicesUseCase
import com.inik.camcon.domain.usecase.usb.RequestUsbPermissionUseCase
import com.inik.camcon.presentation.viewmodel.state.CameraUiStateManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 카메라 연결/해제 및 USB 관리 전용 매니저
 * 단일책임: 카메라 연결 상태 관리만 담당
 */
@Singleton
class CameraConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cameraRepository: CameraRepository,
    private val connectCameraUseCase: ConnectCameraUseCase,
    private val disconnectCameraUseCase: DisconnectCameraUseCase,
    private val refreshUsbDevicesUseCase: RefreshUsbDevicesUseCase,
    private val requestUsbPermissionUseCase: RequestUsbPermissionUseCase,
    private val usbCameraManager: UsbCameraManager
) {

    companion object {
        private const val TAG = "카메라연결매니저"
    }

    // 내부 상태
    private val _isAutoConnecting = MutableStateFlow(false)
    val isAutoConnecting: StateFlow<Boolean> = _isAutoConnecting.asStateFlow()

    private var connectionJob: Job? = null

    /**
     * USB 디바이스 및 권한 상태 관찰
     */
    fun observeUsbDevices(
        scope: CoroutineScope,
        uiStateManager: CameraUiStateManager
    ) {
        // USB 디바이스 상태 관찰
        usbCameraManager.connectedDevices
            .onEach { devices ->
                uiStateManager.updateUsbDeviceState(
                    devices.size,
                    usbCameraManager.hasUsbPermission.value
                )

                if (devices.isNotEmpty() && !usbCameraManager.hasUsbPermission.value && !_isAutoConnecting.value) {
                    Log.d(TAG, "USB 디바이스 감지됨 - 권한 자동 요청")
                    requestUsbPermission()
                }
            }
            .launchIn(scope)

        // USB 권한 상태 관찰
        usbCameraManager.hasUsbPermission
            .onEach { hasPermission ->
                val deviceCount = usbCameraManager.connectedDevices.value.size
                uiStateManager.updateUsbDeviceState(deviceCount, hasPermission)

                if (hasPermission && deviceCount > 0 && !_isAutoConnecting.value) {
                    Log.d(TAG, "USB 권한 획득 - 자동 연결 시작")
                    autoConnectCamera(uiStateManager)
                }
            }
            .launchIn(scope)

        // 통합 연결 로직
        combine(
            usbCameraManager.connectedDevices,
            usbCameraManager.hasUsbPermission
        ) { devices, hasPermission ->
            Pair(devices.size, hasPermission)
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
        if (_isAutoConnecting.value) {
            Log.d(TAG, "자동 카메라 연결이 이미 진행 중")
            return
        }

        _isAutoConnecting.value = true

        connectionJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "자동 카메라 연결 시작")
                uiStateManager.updateUsbInitialization(true, "USB 카메라 초기화 중...")

                connectCameraUseCase("auto")
                    .onSuccess {
                        Log.d(TAG, "자동 카메라 연결 성공")
                        uiStateManager.onConnectionSuccess()

                        // 카메라 전원 상태 확인
                        checkCameraPowerStateAndTest()
                    }
                    .onFailure { error ->
                        Log.e(TAG, "자동 카메라 연결 실패", error)
                        uiStateManager.onConnectionFailure(error)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "자동 카메라 연결 중 예외 발생", e)
                uiStateManager.onConnectionFailure(e)
            } finally {
                _isAutoConnecting.value = false
                Log.d(TAG, "자동 카메라 연결 완료")
            }
        }
    }

    /**
     * 수동 카메라 연결
     */
    fun connectCamera(cameraId: String, uiStateManager: CameraUiStateManager) {
        connectionJob = CoroutineScope(Dispatchers.IO).launch {
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
                        uiStateManager.onConnectionFailure(error)
                    }

                uiStateManager.updateLoadingState(false)
            } catch (e: Exception) {
                Log.e(TAG, "카메라 연결 중 예외 발생", e)
                uiStateManager.updateLoadingState(false)
                uiStateManager.onConnectionFailure(e)
            }
        }
    }

    /**
     * 카���라 연결 해제
     */
    fun disconnectCamera(uiStateManager: CameraUiStateManager) {
        Log.d(TAG, "카메라 연결 해제 요청")
        connectionJob?.cancel()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                disconnectCameraUseCase()
                uiStateManager.onCameraDisconnected()
                Log.i(TAG, "카메라 연결 해제 성공")
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
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val devices = refreshUsbDevicesUseCase()
                uiStateManager.updateUsbDeviceState(
                    devices.size,
                    usbCameraManager.hasUsbPermission.value
                )

                devices.firstOrNull()?.let { device ->
                    if (!usbCameraManager.hasUsbPermission.value) {
                        requestUsbPermissionUseCase(device)
                    }
                }
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
        CoroutineScope(Dispatchers.IO).launch {
            try {
                uiStateManager?.updateUsbInitialization(true, "USB 권한 요청 중...")

                val devices = refreshUsbDevicesUseCase()
                if (devices.isNotEmpty()) {
                    val device = devices.first()
                    requestUsbPermissionUseCase(device)
                    uiStateManager?.setError("USB 권한을 요청했습니다. 대화상자에서 승인해주세요.")
                    uiStateManager?.updateUsbInitialization(false, "USB 권한 대기 중...")
                } else {
                    uiStateManager?.setError("USB 카메라가 감지되지 않았습니다")
                    uiStateManager?.updateUsbInitialization(false)
                }
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
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "자동 연결 완료 후 카메라 전원 상태 확인 중...")
                usbCameraManager.checkPowerStateAndTest()
            } catch (e: Exception) {
                Log.e(TAG, "카메라 전원 상태 확인 중 오류", e)
            }
        }
    }

    /**
     * 정리
     */
    fun cleanup() {
        connectionJob?.cancel()
        _isAutoConnecting.value = false
    }
}