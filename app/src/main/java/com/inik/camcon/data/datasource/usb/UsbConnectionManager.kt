package com.inik.camcon.data.datasource.usb

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.util.Log
import com.inik.camcon.CameraNative
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * USB 연결과 네이티브 카메라 초기화를 담당하는 클래스
 */
@Singleton
class UsbConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val _isNativeCameraConnected = MutableStateFlow(false)
    val isNativeCameraConnected: StateFlow<Boolean> = _isNativeCameraConnected.asStateFlow()

    private var currentDevice: UsbDevice? = null
    private var currentConnection: UsbDeviceConnection? = null

    // 초기화 상태 관리
    private var isInitializingNativeCamera = false
    private var lastInitializedFd = -1
    private val initializationMutex = Mutex()

    // USB 분리 처리 상태 추가 (무한 루프 방지)
    private val isHandlingDisconnection = AtomicBoolean(false)

    // 연결 해제 콜백
    private var disconnectionCallback: (() -> Unit)? = null

    companion object {
        private const val TAG = "USB연결관리자"
    }

    /**
     * USB 디바이스에 연결하고 네이티브 카메라를 초기화합니다.
     */
    fun connectToCamera(device: UsbDevice) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "카메라 연결 시작: ${device.deviceName}")

                // 이미 네이티브 카메라가 연결되어 있으면 중복 연결 방지
                if (_isNativeCameraConnected.value) {
                    Log.d(TAG, "네이티브 카메라가 이미 연결되어 있음 - 중복 연결 방지")
                    return@launch
                }

                val connection = usbManager.openDevice(device)
                connection?.let {
                    currentConnection = it
                    currentDevice = device
                    val fd = it.fileDescriptor

                    Log.d(TAG, "USB 디바이스 연결 성공. FD: $fd")
                    logDeviceInfo(device)

                    // 네이티브 카메라 초기화 시도
                    initializeNativeCamera(fd)
                } ?: run {
                    Log.e(TAG, "USB 디바이스 열기 실패: ${device.deviceName}")
                    updateConnectionState(false, "USB 디바이스 열기 실패")
                }
            } catch (e: Exception) {
                Log.e(TAG, "카메라 연결 실패", e)
                updateConnectionState(false, "카메라 연결 실패")
            }
        }
    }

    private suspend fun initializeNativeCamera(fd: Int) = withContext(Dispatchers.IO) {
        initializationMutex.withLock {
            try {
                // 중복 FD 초기화 방지
                if (fd == lastInitializedFd && _isNativeCameraConnected.value) {
                    Log.d(TAG, "동일한 FD로 이미 초기화 완료 - 중복 방지: $fd")
                    return@withLock
                }

                // 초기화 진행 중 체크
                if (isInitializingNativeCamera) {
                    Log.d(TAG, "네이티브 카메라 초기화가 이미 진행 중 - 대기: $fd")
                    return@withLock
                }

                // 이미 카메라가 연결되어 있으면 재초기화 차단
                if (_isNativeCameraConnected.value) {
                    Log.d(TAG, "카메라가 이미 연결되어 있음 - 재초기화 차단: $fd")
                    return@withLock
                }

                Log.d(TAG, "네이티브 카메라 초기화 시작: fd=$fd")
                isInitializingNativeCamera = true
                lastInitializedFd = fd

                val nativeLibDir = context.applicationInfo.nativeLibraryDir

                // USB 연결 안정화를 위한 짧은 지연
                delay(500)

                val result = CameraNative.initCameraWithFd(fd, nativeLibDir)
                Log.d(TAG, "네이티브 카메라 초기화 결과: $result")

                when (result) {
                    0 -> { // GP_OK
                        Log.d(TAG, "네이티브 카메라 초기화 성공")
                        updateConnectionState(true, "초기화 성공")
                    }

                    -52 -> { // GP_ERROR_IO_USB_FIND
                        Log.e(TAG, "USB 포트에서 카메라를 찾을 수 없음. 일반 초기화로 대체")
                        tryGeneralInit()
                    }

                    else -> {
                        Log.e(TAG, "네이티브 카메라 초기화 실패: $result")
                        updateConnectionState(false, "초기화 실패")
                        lastInitializedFd = -1
                        tryGeneralInit()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "네이티브 카메라 초기화 중 예외 발생", e)
                updateConnectionState(false, "예외 발생")
                lastInitializedFd = -1
                tryGeneralInit()
            } finally {
                isInitializingNativeCamera = false
            }
        }
    }

    private suspend fun tryGeneralInit() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "일반 카메라 초기화 시도...")
            val generalResult = CameraNative.initCamera()
            Log.d(TAG, "일반 카메라 초기화 결과: $generalResult")

            if (generalResult.contains("OK", ignoreCase = true)) {
                updateConnectionState(true, "일반 초기화 성공")
            } else {
                Log.e(TAG, "일반 초기화 실패: $generalResult")
                updateConnectionState(false, "일반 초기화 실패")
            }
        } catch (e: Exception) {
            Log.e(TAG, "일반 카메라 초기화 중 예외 발생", e)
            updateConnectionState(false, "예외 발생")
        }
    }

    /**
     * 카메라 연결을 완전히 해제합니다.
     */
    suspend fun disconnectCamera() = withContext(Dispatchers.IO) {
        try {
            if (_isNativeCameraConnected.value) {
                Log.d(TAG, "카메라 연결 해제 시작")

                // 카메라 이벤트 리스너 중지
                try {
                    CameraNative.stopListenCameraEvents()
                    Log.d(TAG, "카메라 이벤트 리스너 중지 완료")
                } catch (e: Exception) {
                    Log.w(TAG, "카메라 이벤트 리스너 중지 중 오류", e)
                }

                // 카메라 연결 완전 해제 - 백그라운드 스레드에서 실행
                Thread {
                    try {
                        CameraNative.closeCamera()
                        Log.d(TAG, "카메라 네이티브 연결 해제 완료")
                    } catch (e: Exception) {
                        Log.e(TAG, "카메라 네이티브 연결 해제 중 오류", e)
                    }
                }.start()

                updateConnectionState(false, "연결 해제")
            }

            // USB 연결 정리
            currentConnection?.close()
            currentConnection = null
            currentDevice = null
            lastInitializedFd = -1

            Log.d(TAG, "카메라 연결 해제 완료")
        } catch (e: Exception) {
            Log.e(TAG, "카메라 연결 해제 중 오류", e)
            updateConnectionState(false, "오류 발생")

            // 오류가 발생해도 상태는 초기화
            currentConnection?.close()
            currentConnection = null
            currentDevice = null
            lastInitializedFd = -1
        }
    }

    /**
     * USB 디바이스 분리 처리
     */
    suspend fun handleUsbDisconnection() = withContext(Dispatchers.IO) {
        // 중복 처리 방지 - 원자적 연산으로 체크
        if (!isHandlingDisconnection.compareAndSet(false, true)) {
            Log.d(TAG, "USB 분리 처리가 이미 진행 중 - 중복 방지")
            return@withContext
        }

        Log.e(TAG, "USB 디바이스 분리 이벤트 처리 시작")

        try {
            // 카메라 이벤트 리스너 즉시 중지
            try {
                CameraNative.stopListenCameraEvents()
            } catch (e: Exception) {
                Log.w(TAG, "이벤트 리스너 중지 중 오류", e)
            }

            // 카메라 연결 완전 해제 - 백그라운드 스레드에서 실행
            Thread {
                try {
                    CameraNative.closeCamera()
                    Log.d(TAG, "USB 분리 - 카메라 종료 완료")
                } catch (e: Exception) {
                    Log.w(TAG, "USB 분리 - 카메라 종료 중 오류", e)
                }
            }.start()

            // 상태 초기화
            updateConnectionState(false, "USB 디바이스 분리")

            // USB 연결 상태 초기화
            currentDevice = null
            currentConnection?.close()
            currentConnection = null
            lastInitializedFd = -1

            // 분리 콜백 호출
            disconnectionCallback?.invoke()

            Log.d(TAG, "USB 분리 처리 완료")

        } catch (e: Exception) {
            Log.e(TAG, "USB 분리 처리 중 오류", e)
            // 오류가 발생해도 상태는 초기화
            updateConnectionState(false, "USB 분리 오류")
            currentDevice = null
            currentConnection?.close()
            currentConnection = null
            lastInitializedFd = -1
            disconnectionCallback?.invoke()
        } finally {
            // 처리 완료 후 상태 리셋 (3초 후)
            delay(3000)
            isHandlingDisconnection.set(false)
            Log.d(TAG, "USB 분리 처리 상태 리셋")
        }
    }

    fun getFileDescriptor(): Int? {
        return currentDevice?.let { device ->
            try {
                currentConnection?.let { existingConnection ->
                    Log.d(TAG, "기존 연결 재사용: FD=${existingConnection.fileDescriptor}")
                    return existingConnection.fileDescriptor
                }

                val connection = usbManager.openDevice(device)
                connection?.let { conn ->
                    currentConnection = conn
                    Log.d(TAG, "새 연결 생성: FD=${conn.fileDescriptor}")
                    conn.fileDescriptor
                } ?: run {
                    Log.e(TAG, "USB 디바이스 연결 실패")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "파일 디스크립터 가져오기 실패", e)
                null
            }
        }
    }

    fun getCurrentDevice(): UsbDevice? = currentDevice

    fun setDisconnectionCallback(callback: () -> Unit) {
        disconnectionCallback = callback
    }

    private fun updateConnectionState(connected: Boolean, reason: String = "") {
        if (_isNativeCameraConnected.value == connected) {
            Log.d(
                TAG,
                "연결 상태 이미 $connected - UI 업데이트 생략 ${if (reason.isNotEmpty()) "($reason)" else ""}"
            )
            return
        }

        _isNativeCameraConnected.value = connected
        Log.d(TAG, "연결 상태 변경: $connected ${if (reason.isNotEmpty()) "($reason)" else ""}")
    }

    private fun logDeviceInfo(device: UsbDevice) {
        Log.d(TAG, "디바이스 정보:")
        Log.d(TAG, "  이름: ${device.deviceName}")
        Log.d(TAG, "  제조사ID: 0x${device.vendorId.toString(16)}")
        Log.d(TAG, "  제품ID: 0x${device.productId.toString(16)}")
        Log.d(TAG, "  클래스: ${device.deviceClass}")
        Log.d(TAG, "  서브클래스: ${device.deviceSubclass}")
        Log.d(TAG, "  프로토콜: ${device.deviceProtocol}")

        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            Log.d(TAG, "  인터페이스 $i:")
            Log.d(TAG, "    클래스: ${intf.interfaceClass}")
            Log.d(TAG, "    서브클래스: ${intf.interfaceSubclass}")
            Log.d(TAG, "    프로토콜: ${intf.interfaceProtocol}")
            Log.d(TAG, "    엔드포인트 수: ${intf.endpointCount}")
        }
    }

    fun cleanup() {
        CoroutineScope(Dispatchers.IO).launch {
            disconnectCamera()
        }
    }
}