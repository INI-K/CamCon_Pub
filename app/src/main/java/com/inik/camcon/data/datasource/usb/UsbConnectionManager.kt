package com.inik.camcon.data.datasource.usb

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.util.Log
import com.inik.camcon.CameraNative
import com.inik.camcon.di.ApplicationScope
import com.inik.camcon.di.IoDispatcher
import com.inik.camcon.utils.LogMask
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
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
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val libgphoto2PluginInstaller: com.inik.camcon.data.datasource.Libgphoto2PluginInstaller
) {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val _isNativeCameraConnected = MutableStateFlow(false)
    val isNativeCameraConnected: StateFlow<Boolean> = _isNativeCameraConnected.asStateFlow()

    /**
     * 최근 실패 사유. UI는 이 값을 관찰해 다이얼로그 본문/도움말을 띄운다.
     * 성공/초기 상태에서는 null.
     */
    private val _lastErrorKind = MutableStateFlow<UsbErrorKind?>(null)
    val lastErrorKind: StateFlow<UsbErrorKind?> = _lastErrorKind.asStateFlow()

    private var currentDevice: UsbDevice? = null
    private var currentConnection: UsbDeviceConnection? = null

    // 초기화 상태 관리
    private val isInitializingNativeCamera = AtomicBoolean(false)
    private var lastInitializedFd = -1
    private val initializationMutex = Mutex()

    // USB 분리 처리 상태 추가 (무한 루프 방지)
    private val isHandlingDisconnection = AtomicBoolean(false)

    // 연결 해제 콜백
    private var disconnectionCallback: (() -> Unit)? = null

    companion object {
        private const val TAG = "USB연결관리자"

        /**
         * 신뢰하는 DSLR/미러리스 제조사 USB Vendor ID 화이트리스트.
         *
         * device_filter.xml 은 사용자에게 USB 연결 의도를 묻기 위해 더 넓은 풀을 노출하지만,
         * 실제로 libgphoto2 초기화를 진행하는 시점에서는 PTP/IP 카메라 제조사만 허용한다.
         * 임의의 PTP-호환 디바이스(예: 스토리지/플로터/스캐너 변형)가 통과해
         * 네이티브 USB I/O 경로에 부담을 주는 일을 차단한다.
         */
        private val ALLOWED_CAMERA_VENDOR_IDS: Set<Int> = setOf(
            0x04A9, // Canon
            0x04B0, // Nikon
            0x054C, // Sony
            0x04CB, // Fujifilm
            0x04DA, // Panasonic
            0x07B4, // Olympus / OM System
            0x25FB, // Pentax / Ricoh
            0x1843, // Leica
            0x1EDB, // Blackmagic Design (시네마)
        )
    }

    /**
     * VID 화이트리스트 검증. 화이트리스트 외 디바이스는 명시적으로 거부한다.
     */
    private fun isAllowedCameraVendor(device: UsbDevice): Boolean {
        return device.vendorId in ALLOWED_CAMERA_VENDOR_IDS
    }

    /**
     * USB 디바이스에 연결하고 네이티브 카메라를 초기화합니다.
     *
     * VID 화이트리스트(`ALLOWED_CAMERA_VENDOR_IDS`)에 속하지 않는 디바이스는
     * PTP 클래스라도 명시적으로 거부한다. (사용자에게는 연결 실패 상태로 노출)
     */
    fun connectToCamera(device: UsbDevice) {
        scope.launch(ioDispatcher) {
            try {
                Log.d(TAG, "카메라 연결 시작: ${LogMask.path(device.deviceName)}")

                if (!isAllowedCameraVendor(device)) {
                    Log.w(
                        TAG,
                        "허용되지 않은 USB VID=0x${device.vendorId.toString(16)}, " +
                                "PID=0x${device.productId.toString(16)} - 카메라 연결 거부"
                    )
                    reportError(UsbErrorKind.Unsupported)
                    updateConnectionState(false, "지원하지 않는 USB 디바이스")
                    return@launch
                }

                if (_isNativeCameraConnected.value) {
                    Log.d(TAG, "네이티브 카메라가 이미 연결되어 있음 - 중복 연결 방지")
                    return@launch
                }

                // 초기화 게이트를 원자적으로 선점해 동시 진입 시 openDevice 중복 호출(USB 연결 누수) 방지.
                // 선점 실패(이미 다른 코루틴이 초기화 중)면 즉시 종료한다.
                if (!isInitializingNativeCamera.compareAndSet(false, true)) {
                    Log.d(TAG, "네이티브 카메라가 이미 초기화 중 - 중복 연결 방지")
                    return@launch
                }

                // 이 시점부터 isInitializingNativeCamera=true 를 점유하므로,
                // 초기화에 도달하지 못하는 모든 경로에서 게이트를 직접 해제해야 한다.
                val connection = usbManager.openDevice(device)
                if (connection == null) {
                    Log.e(TAG, "USB 디바이스 열기 실패: ${LogMask.path(device.deviceName)}")
                    isInitializingNativeCamera.set(false)
                    reportError(UsbErrorKind.PermissionDenied)
                    updateConnectionState(false, "USB 디바이스 열기 실패")
                    return@launch
                }

                currentConnection = connection
                currentDevice = device
                val fd = connection.fileDescriptor

                Log.d(TAG, "USB 디바이스 연결 성공. FD: $fd")
                logDeviceInfo(device)

                // 네이티브 카메라 초기화 시도 (finally 에서 게이트 해제)
                initializeNativeCamera(fd)
            } catch (e: Exception) {
                Log.e(TAG, "카메라 연결 실패", e)
                // 게이트를 점유한 채 예외로 빠져나가는 경우를 대비해 해제한다.
                isInitializingNativeCamera.set(false)
                reportError(UsbErrorKind.Restart)
                updateConnectionState(false, "카메라 연결 실패")
            }
        }
    }

    /**
     * 사용자에게 보고할 마지막 에러 종류 갱신.
     * 성공 시 [clearError] 호출로 null로 되돌린다.
     */
    private fun reportError(kind: UsbErrorKind) {
        _lastErrorKind.value = kind
    }

    /** 외부에서 에러 표시를 닫았을 때 호출. */
    fun clearError() {
        _lastErrorKind.value = null
    }

    private suspend fun initializeNativeCamera(fd: Int) = withContext(ioDispatcher) {
        initializationMutex.withLock {
            try {
                // 중복 FD 초기화 방지
                if (fd == lastInitializedFd && _isNativeCameraConnected.value) {
                    Log.d(TAG, "동일한 FD로 이미 초기화 완료 - 중복 방지: $fd")
                    return@withLock
                }

                // 이미 카메라가 연결되어 있으면 재초기화 차단
                if (_isNativeCameraConnected.value) {
                    Log.d(TAG, "카메라가 이미 연결되어 있음 - 재초기화 차단: $fd")
                    return@withLock
                }

                // isInitializingNativeCamera 게이트는 connectToCamera 진입 시
                // compareAndSet(false, true)로 이미 선점되어 있고, 본 함수의 finally 에서 해제한다.
                Log.d(TAG, "네이티브 카메라 초기화 시작: fd=$fd")
                lastInitializedFd = fd

                // libgphoto2 플러그인 디렉토리 확인 및 생성 (앱 private 디렉토리에)
                // ⚠️ nativeLibraryDir가 아닌 플러그인 디렉토리를 사용해야 함!
                // 자가 재추출 가드는 PTP/IP 경로와 공유(Libgphoto2PluginInstaller).
                val pluginDir = libgphoto2PluginInstaller.ensurePluginDirs()

                // USB 연결 안정화를 위한 짧은 지연
                delay(500)

                val result = CameraNative.initCameraWithFd(fd, pluginDir)
                Log.d(TAG, "네이티브 카메라 초기화 결과: $result")

                when (result) {
                    0 -> { // GP_OK
                        Log.d(TAG, "네이티브 카메라 초기화 성공")
                        clearError()
                        updateConnectionState(true, "초기화 성공")
                    }

                    -52 -> { // GP_ERROR_IO_USB_FIND
                        Log.e(TAG, "USB 포트에서 카메라를 찾을 수 없음 - 카메라 연결 실패")
                        reportError(UsbErrorKind.fromInitResult(result))
                        handleUsbError(result)
                    }

                    -7 -> { // GP_ERROR_IO
                        Log.e(TAG, "USB I/O 오류 - 권한 또는 커널 드라이버 문제")
                        reportError(UsbErrorKind.fromInitResult(result))
                        updateConnectionState(false, "USB I/O 오류")
                        lastInitializedFd = -1
                        currentConnection?.close()
                        currentConnection = null
                        currentDevice = null
                    }

                    -10 -> { // 타임아웃
                        Log.e(TAG, "카메라 초기화 타임아웃")
                        reportError(UsbErrorKind.fromInitResult(result))
                        updateConnectionState(false, "카메라 초기화 타임아웃")
                        lastInitializedFd = -1
                        currentConnection?.close()
                        currentConnection = null
                        currentDevice = null
                    }

                    -1000 -> { // 앱 재시작 필요
                        Log.e(TAG, "카메라 초기화 실패 - 앱 재시작 필요")
                        reportError(UsbErrorKind.fromInitResult(result))
                        updateConnectionState(false, "앱 재시작 필요")
                        lastInitializedFd = -1
                        currentConnection?.close()
                        currentConnection = null
                        currentDevice = null
                    }

                    -2000 -> { // PTP 타임아웃
                        Log.e(TAG, "PTP 타임아웃 오류")
                        reportError(UsbErrorKind.fromInitResult(result))
                        updateConnectionState(false, "PTP 타임아웃")
                        lastInitializedFd = -1
                        currentConnection?.close()
                        currentConnection = null
                        currentDevice = null
                    }

                    else -> {
                        Log.e(TAG, "네이티브 카메라 초기화 실패: $result")
                        reportError(UsbErrorKind.fromInitResult(result))
                        updateConnectionState(false, "초기화 실패 (코드: $result)")
                        lastInitializedFd = -1
                        // 기타 에러의 경우에만 일반 초기화 시도
                        if (result != -52 && result != -7 && result != -10) {
                            tryGeneralInit()
                        } else {
                            currentConnection?.close()
                            currentConnection = null
                            currentDevice = null
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "네이티브 카메라 초기화 중 예외 발생", e)
                reportError(UsbErrorKind.Restart)
                updateConnectionState(false, "예외 발생")
                lastInitializedFd = -1
                tryGeneralInit()
            } finally {
                isInitializingNativeCamera.set(false)
            }
        }
    }

    private suspend fun tryGeneralInit() = withContext(ioDispatcher) {
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
    suspend fun disconnectCamera() {
        withContext(ioDispatcher) {
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

                    // 카메라 연결 완전 해제 (이미 IO 디스패처에서 실행 중)
                    try {
                        CameraNative.closeCamera()
                        Log.d(TAG, "카메라 네이티브 연결 해제 완료")
                    } catch (e: Exception) {
                        Log.e(TAG, "카메라 네이티브 연결 해제 중 오류", e)
                    }

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
    }

    /**
     * USB 디바이스 분리 처리
     */
    suspend fun handleUsbDisconnection() = withContext(ioDispatcher) {
        // 중복 처리 방지 - 원자적 연산으로 체크
        if (!isHandlingDisconnection.compareAndSet(false, true)) {
            Log.d(TAG, "USB 분리 처리가 이미 진행 중 - 중복 방지")
            return@withContext
        }

        Log.d(TAG, "USB 디바이스 분리 이벤트 처리 시작")

        try {
            // 즉시 연결 상태를 false로 업데이트 (UI 반영)
            updateConnectionState(false, "USB 디바이스 분리됨")

            // 카메라 이벤트 리스너 즉시 중지
            try {
                CameraNative.stopListenCameraEvents()
            } catch (e: Exception) {
                Log.w(TAG, "이벤트 리스너 중지 중 오류", e)
            }

            // 카메라 연결 완전 해제 (이미 IO 디스패처에서 실행 중)
            try {
                CameraNative.closeCamera()
                Log.d(TAG, "USB 분리 - 카메라 종료 완료")
            } catch (e: Exception) {
                Log.w(TAG, "USB 분리 - 카메라 종료 중 오류", e)
            }

            // USB 연결 상태 초기화
            currentDevice = null
            currentConnection?.close()
            currentConnection = null
            lastInitializedFd = -1
            isInitializingNativeCamera.set(false)

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
            isInitializingNativeCamera.set(false)
            disconnectionCallback?.invoke()
        } finally {
            // 정리 완료 즉시 게이트 해제. 진입 시 compareAndSet 이 동시 중복 진입을 이미 막으므로
            // 별도 쿨다운(delay) 없이 리셋해야 빠른 재연결 중 후속 분리/에러 처리가 누락되지 않는다.
            isHandlingDisconnection.set(false)
            Log.d(TAG, "USB 분리 처리 상태 리셋")
        }
    }

    /**
     * -52 에러 감지 시 USB 분리 처리
     */
    fun handleUsbError(errorCode: Int) {
        if (errorCode == -52 || errorCode == -4) { // GP_ERROR_IO_USB_FIND 또는 libusb disconnected
            Log.e(TAG, "USB 에러 감지 (코드: $errorCode) - USB 분리로 처리")
            scope.launch(ioDispatcher) {
                handleUsbDisconnection()
            }
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
        Log.d(
            TAG,
            "디바이스 정보: 이름=${LogMask.path(device.deviceName)} " +
                    "VID=0x${device.vendorId.toString(16)} PID=0x${device.productId.toString(16)} " +
                    "클래스=${device.deviceClass}/${device.deviceSubclass}/${device.deviceProtocol} " +
                    "인터페이스수=${device.interfaceCount}"
        )
    }

    fun cleanup() {
        scope.launch(ioDispatcher) {
            disconnectCamera()
        }
    }
}