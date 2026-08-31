package com.inik.camcon.presentation.viewmodel

import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.di.IoDispatcher
import com.inik.camcon.domain.manager.CameraConnectionGlobalManager
import com.inik.camcon.domain.repository.UsbDeviceRepository
import com.inik.camcon.domain.model.CameraConnectionType
import com.inik.camcon.domain.model.GlobalCameraConnectionState
import com.inik.camcon.domain.model.UiText
import com.inik.camcon.domain.usecase.GetSubscriptionUseCase
import com.inik.camcon.utils.LogcatManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    private val cameraConnectionGlobalManager: CameraConnectionGlobalManager,
    private val usbDeviceRepository: UsbDeviceRepository,
    private val getSubscriptionUseCase: GetSubscriptionUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    companion object {
        private const val TAG = "MainActivityViewModel"
    }

    val globalConnectionState: StateFlow<GlobalCameraConnectionState>
        get() = cameraConnectionGlobalManager.globalConnectionState

    val activeConnectionType: StateFlow<CameraConnectionType?>
        get() = cameraConnectionGlobalManager.activeConnectionType

    val connectionStatusMessage: StateFlow<UiText>
        get() = cameraConnectionGlobalManager.connectionStatusMessage

    init {
        viewModelScope.launch {
            try {
                getSubscriptionUseCase.logCurrentTier()
            } catch (error: Exception) {
                LogcatManager.e(TAG, "사용자 티어 정보 로드 실패", error)
            }
        }
    }

    fun initializeUsbState(initialIntent: Intent?) {
        viewModelScope.launch(ioDispatcher) {
            handleUsbIntentInternal(initialIntent)
            delay(500)
            checkAndInitializeUsbDevicesInternal()
        }
    }

    fun handleUsbIntent(intent: Intent?) {
        viewModelScope.launch(ioDispatcher) {
            handleUsbIntentInternal(intent)
        }
    }

    fun checkUsbPermissionStatus() {
        viewModelScope.launch(ioDispatcher) {
            checkUsbPermissionStatusInternal()
        }
    }

    private suspend fun handleUsbIntentInternal(intent: Intent?) {
        when (intent?.action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                val device = extractUsbDevice(intent) ?: return

                LogcatManager.d(TAG, "USB 카메라 디바이스가 연결됨: ${device.deviceName}")
                LogcatManager.d(
                    TAG,
                    "제조사ID: 0x${device.vendorId.toString(16)}, 제품ID: 0x${
                        device.productId.toString(
                            16
                        )
                    }"
                )

                if (!isUsbCameraDevice(device)) {
                    LogcatManager.d(TAG, "카메라 디바이스가 아님")
                    return
                }

                // 여기서 권한을 바로 요청하지 않는다. 케이블을 막 꽂은 순간에는 시스템 앱
                // 선택지("한 번만 / 항상")가 화면에 떠 있어서, 그 위에 앱 다이얼로그를 겹쳐
                // 띄우면 사용자는 첫 연결에서 프롬프트를 두 개 받는다.
                //
                // 권한 프롬프트의 주인은 UsbAutoConnectManager 한 곳이다. 그쪽이 시스템 부여를
                // 충분히 기다린 뒤, 끝내 오지 않을 때만 앱 다이얼로그를 띄운다. 이미 권한이
                // 있는 경우의 상태 반영은 UsbDeviceDetector 의 부착 브로드캐스트가 처리한다.
                // 케이블로 앱이 처음 기동된 경우에는 USB 브로드캐스트 리시버가 등록되기 전에
                // 시스템 브로드캐스트가 지나가 버린다. 이 인텐트가 "방금 꽂혔다"는 유일한
                // 증거이므로 여기서 부착 문맥을 기록해 준다.
                usbDeviceRepository.noteCameraAttached()
                LogcatManager.d(TAG, "카메라 디바이스 확인됨 - 권한 판단은 자동 연결 매니저에 위임")
            }

            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                val device = extractUsbDevice(intent)
                device?.let {
                    LogcatManager.d(TAG, "USB 디바이스가 분리됨: ${it.deviceName}")
                }
            }
        }
    }

    private suspend fun checkUsbPermissionStatusInternal() {
        try {
            val currentDevice = usbDeviceRepository.getCurrentDeviceInfo()

            if (currentDevice != null) {
                LogcatManager.d(TAG, "앱 재개 시 기존 연결된 디바이스 확인: ${currentDevice.deviceName}")

                if (!usbDeviceRepository.hasUsbPermission.value) {
                    LogcatManager.d(TAG, "기존 디바이스의 권한이 없음, 권한 요청: ${currentDevice.deviceName}")
                    usbDeviceRepository.requestPermission(currentDevice.deviceId)
                } else if (!usbDeviceRepository.isNativeCameraConnected.value) {
                    LogcatManager.d(TAG, "네이티브 연결 없음 - 자동 초기화 시작: ${currentDevice.deviceName}")
                    usbDeviceRepository.connectToFirstCamera()
                } else {
                    LogcatManager.d(TAG, "기존 디바이스에 권한이 있고 네이티브도 연결됨")
                }
                return
            }

            val devices = usbDeviceRepository.getCameraDevices()
            if (devices.isEmpty()) {
                LogcatManager.d(TAG, "앱 재개 시 USB 카메라 디바이스 없음")
                return
            }

            val device = devices.first()
            if (!usbDeviceRepository.hasUsbPermission.value) {
                LogcatManager.d(TAG, "권한이 없는 디바이스 발견, 권한 요청: ${device.deviceName}")
                usbDeviceRepository.requestPermission(device.deviceId)
            } else if (!usbDeviceRepository.isNativeCameraConnected.value) {
                LogcatManager.d(TAG, "네이티브 연결 없음 - 자동 초기화 시작: ${device.deviceName}")
                usbDeviceRepository.connectToFirstCamera()
            } else {
                LogcatManager.d(TAG, "카메라 디바이스 연결됨")
            }
        } catch (error: Exception) {
            LogcatManager.e(TAG, "USB 권한 상태 확인 중 오류", error)
        }
    }

    private suspend fun checkAndInitializeUsbDevicesInternal() {
        try {
            val devices = usbDeviceRepository.getCameraDevices()

            if (devices.isEmpty()) {
                LogcatManager.d(TAG, "연결된 USB 카메라 디바이스 없음")
                return
            }

            val device = devices.first()
            if (!usbDeviceRepository.hasUsbPermission.value) {
                LogcatManager.d(TAG, "디바이스에 대한 권한이 없음, 권한 요청: ${device.deviceName}")
                usbDeviceRepository.requestPermission(device.deviceId)
            } else if (!usbDeviceRepository.isNativeCameraConnected.value) {
                LogcatManager.d(TAG, "네이티브 연결 없음 - 자동 초기화 시작: ${device.deviceName}")
                usbDeviceRepository.connectToFirstCamera()
            } else {
                LogcatManager.d(TAG, "USB 카메라가 이미 초기화되어 있음")
            }
        } catch (error: Exception) {
            LogcatManager.e(TAG, "USB 디바이스 초기화 중 오류", error)
        }
    }

    fun cleanup() {
        viewModelScope.launch(ioDispatcher) {
            try {
                usbDeviceRepository.cleanup()
            } catch (error: Exception) {
                LogcatManager.w(TAG, "USB 카메라 매니저 정리 중 오류", error)
            }

            try {
                cameraConnectionGlobalManager.cleanup()
            } catch (error: Exception) {
                LogcatManager.w(TAG, "전역 카메라 연결 매니저 정리 중 오류", error)
            }
        }
    }

    private fun extractUsbDevice(intent: Intent): UsbDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
    }

    private fun isUsbCameraDevice(device: UsbDevice): Boolean {
        for (index in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(index)
            if (usbInterface.interfaceClass == 6) {
                return true
            }
        }

        val knownCameraVendors = listOf(0x04A9, 0x04B0, 0x054C, 0x04CB)
        return device.vendorId in knownCameraVendors
    }

}
