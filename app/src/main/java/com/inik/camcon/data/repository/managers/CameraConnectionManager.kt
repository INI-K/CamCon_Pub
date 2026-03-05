package com.inik.camcon.data.repository.managers
import android.util.Log
import com.inik.camcon.CameraNative
import com.inik.camcon.data.datasource.nativesource.NativeCameraDataSource
import com.inik.camcon.data.datasource.usb.UsbCameraManager
import com.inik.camcon.domain.model.Camera
import com.inik.camcon.domain.model.CameraCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class CameraConnectionManager @Inject constructor(
    private val nativeDataSource: NativeCameraDataSource,
    private val usbCameraManager: UsbCameraManager
) {
    companion object {
        private const val TAG = "RepoCameraConnectionManager"
    }
    private val scope = CoroutineScope(Dispatchers.IO)
    private val _cameraFeed = MutableStateFlow<List<Camera>>(emptyList())
    val cameraFeed: StateFlow<List<Camera>> = _cameraFeed.asStateFlow()
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    private val _isInitializing = MutableStateFlow(false)
    val isInitializing: StateFlow<Boolean> = _isInitializing.asStateFlow()
    private val _cameraCapabilities = MutableStateFlow<CameraCapabilities?>(null)
    val cameraCapabilities: StateFlow<CameraCapabilities?> = _cameraCapabilities.asStateFlow()
    init {
        refreshCameraFeed()
        usbCameraManager.setUsbDisconnectionCallback {
            _isConnected.value = false
        }
    }
    fun connectCamera(cameraId: String): Result<Boolean> {
        return runCatching {
            Log.d(TAG, "카메라 연결 요청: $cameraId")
            _isInitializing.value = true
            refreshCameraFeed()
            val device = usbCameraManager.getCameraDevices().firstOrNull()
                ?: throw IllegalStateException("USB 카메라를 찾을 수 없습니다")
            if (!usbCameraManager.hasUsbPermission.value) {
                usbCameraManager.requestPermission(device)
                throw IllegalStateException("USB 권한이 필요합니다")
            }
            usbCameraManager.connectToCamera(device)
            val fd = usbCameraManager.getFileDescriptor()
                ?: throw IllegalStateException("USB 파일 디스크립터를 얻지 못했습니다")
            val result = nativeDataSource.initCameraWithFd(fd, "")
            if (result < 0) {
                val detail = when (result) {
                    CameraNative.ERROR_APP_RESTART_REQUIRED ->
                        "카메라 초기화가 반복 실패했습니다. 앱 재시작이 필요합니다."
                    CameraNative.ERROR_PTP_TIMEOUT_PERSISTENT ->
                        "PTP 타임아웃이 지속됩니다. 앱 재시작 후 다시 시도해주세요."
                    else -> "카메라 초기화 실패: $result"
                }
                throw IllegalStateException(detail)
            }
            _cameraCapabilities.value = nativeDataSource.getCameraCapabilities()
            _isConnected.value = true
            true
        }.onFailure {
            Log.e(TAG, "카메라 연결 실패", it)
            _isConnected.value = false
        }.also {
            _isInitializing.value = false
        }
    }
    fun disconnectCamera(): Result<Boolean> {
        return runCatching {
            nativeDataSource.closeCamera()
            usbCameraManager.disconnectCamera()
            _isConnected.value = false
            _cameraCapabilities.value = null
            true
        }
    }
    private fun refreshCameraFeed() {
        scope.launch {
            val feed = usbCameraManager.getCameraDevices().mapIndexed { index, device ->
                Camera(
                    id = "$index:${device.deviceId}",
                    name = device.deviceName,
                    isActive = usbCameraManager.hasUsbPermission.value
                )
            }
            _cameraFeed.value = feed
        }
    }
}