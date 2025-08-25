package com.inik.camcon.data.datasource.usb

import android.content.Context
import android.hardware.usb.UsbDevice
import android.util.Log
import com.inik.camcon.domain.model.CameraCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * USB 카메라 관리를 위한 파사드 클래스
 * 각 구성 요소들을 조합하여 통합된 인터페이스를 제공합니다
 */
@Singleton
class UsbCameraManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceDetector: UsbDeviceDetector,
    private val connectionManager: UsbConnectionManager,
    private val capabilitiesManager: CameraCapabilitiesManager
) {
    // StateFlow 위임
    val connectedDevices: StateFlow<List<UsbDevice>> = deviceDetector.connectedDevices
    val hasUsbPermission: StateFlow<Boolean> = deviceDetector.hasPermission
    val cameraCapabilities: StateFlow<CameraCapabilities?> = capabilitiesManager.cameraCapabilities
    val isNativeCameraConnected: StateFlow<Boolean> = connectionManager.isNativeCameraConnected

    companion object {
        private const val TAG = "USB카메라매니저"
    }

    init {
        // USB 분리 콜백 설정
        setupDisconnectionCallbacks()

        // 기존 카메라 연결 상태 확인
        checkExistingCameraConnection()

        // USB 권한 승인 시 자동 연결 트리거
        deviceDetector.setPermissionGrantedCallback { device ->
            try {
                Log.d(TAG, "권한 승인 콜백 수신 - 자동 연결 시도: ${device.deviceName}")
                if (!isNativeCameraConnected.value) {
                    connectToCamera(device)
                } else {
                    Log.d(TAG, "이미 네이티브 카메라 연결됨 - 자동 연결 생략")
                }
            } catch (e: Exception) {
                Log.w(TAG, "권한 승인 콜백 처리 중 오류", e)
            }
        }
    }

    private fun setupDisconnectionCallbacks() {
        // 디바이스 감지기의 분리 콜백
        deviceDetector.setDisconnectionCallback { device ->
            Log.d(TAG, "USB 디바이스 분리 감지: ${device.deviceName}")
            // 연결 매니저에서 분리 처리
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                connectionManager.handleUsbDisconnection()
                capabilitiesManager.reset()
            }
        }

        // 연결 매니저의 분리 콜백
        connectionManager.setDisconnectionCallback {
            Log.d(TAG, "USB 연결 매니저에서 분리 처리 완료")
            capabilitiesManager.reset()
        }
    }

    /**
     * 앱 재개 시 기존 카메라 연결 상태를 확인
     */
    private fun checkExistingCameraConnection() {
        try {
            // 네이티브 카메라가 이미 초기화되어 있는지 확인
            val summary = capabilitiesManager.getCachedOrFetchSummary()
            if (summary.isNotEmpty() && !summary.contains("에러", ignoreCase = true) &&
                !summary.contains("error", ignoreCase = true)
            ) {
                Log.d(TAG, "앱 재개 시 기존 카메라 연결이 유지되고 있음")
            } else {
                Log.d(TAG, "앱 재개 시 새로운 카메라 초기화 필요")

                // 권한과 디바이스 상태 확인 후 자동 연결 시도
                val devices = deviceDetector.getCameraDevices()
                val hasPermission = hasUsbPermission.value
                if (devices.isNotEmpty() && hasPermission && !isNativeCameraConnected.value) {
                    Log.d(TAG, "재개 시 자동 연결 조건 충족 - 연결 시도: ${devices.first().deviceName}")
                    connectToCamera(devices.first())
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "기존 카메라 연결 상태 확인 실패: ${e.message}")
        }
    }

    /**
     * USB 디바이스 목록을 가져옵니다
     */
    fun getCameraDevices(): List<UsbDevice> = deviceDetector.getCameraDevices()

    /**
     * USB 권한을 요청합니다
     */
    fun requestPermission(device: UsbDevice) {
        deviceDetector.requestPermission(device)
    }

    /**
     * 카메라에 연결을 시도합니다
     */
    fun connectToCamera(device: UsbDevice) {
        connectionManager.connectToCamera(device)
    }

    /**
     * 카메라 연결을 해제합니다
     */
    fun disconnectCamera() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            connectionManager.disconnectCamera()
            capabilitiesManager.reset()
        }
    }

    /**
     * 카메라 기능 정보를 새로고침합니다
     */
    fun refreshCameraCapabilities() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            capabilitiesManager.refreshCameraCapabilities()
        }
    }

    /**
     * 파일 디스크립터를 가져옵니다
     */
    fun getFileDescriptor(): Int? = connectionManager.getFileDescriptor()

    /**
     * 현재 연결된 디바이스를 가져옵니다
     */
    fun getCurrentDevice(): UsbDevice? = connectionManager.getCurrentDevice()

    /**
     * 라이브뷰 지원 여부를 확인합니다
     */
    suspend fun isLiveViewSupported(): Boolean = capabilitiesManager.isLiveViewSupported()

    /**
     * 특정 기능 지원 여부를 확인합니다
     */
    suspend fun hasCapability(capability: String): Boolean =
        capabilitiesManager.hasCapability(capability)

    /**
     * 위젯 JSON을 가져옵니다
     */
    suspend fun buildWidgetJsonFromMaster(): String =
        capabilitiesManager.buildWidgetJsonFromMaster()

    /**
     * 카메라 능력 정보를 가져옵니다
     */
    suspend fun getCameraAbilitiesFromMaster(): String =
        capabilitiesManager.getCameraAbilitiesFromMaster()

    /**
     * USB 분리 콜백을 설정합니다 (외부에서 사용)
     */
    fun setUsbDisconnectionCallback(callback: () -> Unit) {
        // 기존 콜백에 추가로 연결
        val originalCallback = connectionManager::handleUsbDisconnection
        connectionManager.setDisconnectionCallback {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                originalCallback.invoke()
                callback.invoke()
            }
        }
    }

    /**
     * USB 분리 처리
     */
    suspend fun handleUsbDisconnection() {
        connectionManager.handleUsbDisconnection()
        capabilitiesManager.reset()
    }

    /**
     * 카메라 전원 상태를 확인하고 필요시 테스트 실행
     */
    fun checkPowerStateAndTest() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                Log.d(TAG, "카메라 전원 상태 확인 및 테스트 실행")

                // 카메라 요약 정보를 가져와 전원 상태 확인 및 테스트 실행
                // 이 과정에서 NativeCameraDataSource의 getCameraSummary()가 호출되어
                // 전원 상태 확인 및 자동 테스트가 실행됩니다
                capabilitiesManager.getCachedOrFetchSummary()

            } catch (e: Exception) {
                Log.e(TAG, "카메라 전원 상태 확인 중 오류", e)
            }
        }
    }

    /**
     * 정리 작업
     */
    fun cleanup(onCleanupComplete: (() -> Unit)? = null) {
        try {
            Log.d(TAG, "카메라 정리 작업 시작")

            deviceDetector.cleanup()
            connectionManager.cleanup()
            capabilitiesManager.reset()

            // 네이티브 콜백을 사용한 안전한 카메라 정리
            com.inik.camcon.CameraNative.closeCameraAsync(
                object : com.inik.camcon.CameraCleanupCallback {
                    override fun onCleanupComplete(success: Boolean, message: String) {
                        Log.d(TAG, "카메라 네이티브 정리 완료: success=$success, message=$message")

                        // 로그 파일 정리
                        try {
                            com.inik.camcon.CameraNative.closeLogFile()
                            Log.d(TAG, "로그 파일 정리 완료")
                        } catch (e: Exception) {
                            Log.w(TAG, "로그 파일 정리 중 오류", e)
                        }

                        // 메인 스레드에서 콜백 호출
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
                            .launch {
                                Log.d(TAG, "카메라 정리 작업 완료")
                                onCleanupComplete?.invoke()
                            }
                    }
                }
            )

        } catch (e: Exception) {
            Log.w(TAG, "정리 작업 중 오류", e)
            // 오류가 발생해도 콜백은 호출
            onCleanupComplete?.invoke()
        }
    }
}
