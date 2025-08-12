package com.inik.camcon.data.datasource.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * USB 디바이스 감지 및 권한 관리를 담당하는 클래스
 */
@Singleton
class UsbDeviceDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val _connectedDevices = MutableStateFlow<List<UsbDevice>>(emptyList())
    val connectedDevices: StateFlow<List<UsbDevice>> = _connectedDevices.asStateFlow()

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private var currentDevice: UsbDevice? = null
    private var deviceListCache: List<UsbDevice>? = null
    private var lastUpdateTime = 0L

    // USB 분리 콜백
    private var disconnectionCallback: ((UsbDevice) -> Unit)? = null

    // USB 권한 승인 콜백 (권한이 승인되면 알림)
    private var permissionGrantedCallback: ((UsbDevice) -> Unit)? = null

    companion object {
        private const val TAG = "USB디바이스감지기"
        private const val ACTION_USB_PERMISSION = "com.inik.camcon.USB_PERMISSION"
        private const val CACHE_TIMEOUT = 1000L // 1초간 캐시 유효

        // 알려진 카메라 제조사 ID 목록
        private val KNOWN_CAMERA_VENDOR_IDS = listOf(
            0x04A9, // Canon
            0x04B0, // Nikon  
            0x054C, // Sony
            0x04E8, // Samsung
            0x04DA, // Panasonic/Lumix
            0x07B4, // Olympus/OM System
            0x0A03, // Pentax/Ricoh
            0x0471, // Leica
            0x05AB, // Sigma
            0x0483, // Fujifilm
            0x0711, // Hasselblad
            0x0554, // Phase One
            0x2770, // Insta360
            0x2207, // DJI
            0x2731, // GoPro
            0x27C6, // Garmin VIRB
            0x2B1E, // YI Technology
            0x1B8C, // Blackmagic Design
            0x1954, // RED Digital Cinema
            0x2040, // ARRI
            0x3D8D, // Z CAM
            0x0451, // ZCAM
            0x2E04  // Kinefinity
        )
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> handlePermissionResult(intent)
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> handleDeviceAttached(intent)
                UsbManager.ACTION_USB_DEVICE_DETACHED -> handleDeviceDetached(intent)
            }
        }
    }

    init {
        registerUsbReceiver()
        initializeDeviceList()
    }

    private fun registerUsbReceiver() {
        // 커스텀 USB 권한 브로드캐스트
        val permissionFilter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.registerReceiver(usbReceiver, permissionFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            ContextCompat.registerReceiver(
                context,
                usbReceiver,
                permissionFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }

        // 시스템 USB 브로드캐스트
        val systemFilter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.registerReceiver(usbReceiver, systemFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(usbReceiver, systemFilter)
        }
    }

    private fun handlePermissionResult(intent: Intent) {
        synchronized(this) {
            val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)

            if (granted) {
                device?.let {
                    Log.d(TAG, "USB 권한이 승인되었습니다: ${it.deviceName}")
                    _hasPermission.value = true
                    currentDevice = it
                    // 권한 승인 시 상위 계층에 알림 (초기화 트리거)
                    permissionGrantedCallback?.invoke(it)
                }
            } else {
                Log.d(TAG, "USB 권한이 거부되었습니다: ${device?.deviceName}")
                _hasPermission.value = false
            }
        }
    }

    private fun handleDeviceAttached(intent: Intent) {
        val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
        device?.let {
            Log.d(TAG, "USB 디바이스가 연결되었습니다: ${it.deviceName}")
            if (isCameraDevice(it)) {
                val hasActualPermission = usbManager.hasPermission(it)
                Log.d(TAG, "연결된 디바이스의 실제 권한 상태: $hasActualPermission")

                if (hasActualPermission) {
                    _hasPermission.value = true
                    currentDevice = it
                } else {
                    _hasPermission.value = false
                    currentDevice = null
                }

                updateDeviceList()
            }
        }
    }

    private fun handleDeviceDetached(intent: Intent) {
        val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
        device?.let {
            Log.d(TAG, "USB 디바이스가 분리되었습니다: ${it.deviceName}")
            if (it == currentDevice) {
                _hasPermission.value = false
                currentDevice = null
                disconnectionCallback?.invoke(it)
            }
            updateDeviceList()
        }
    }

    private fun initializeDeviceList() {
        Log.d(TAG, "USB 디바이스 목록 초기화 시작")
        updateDeviceList()
    }

    fun getCameraDevices(): List<UsbDevice> {
        val now = System.currentTimeMillis()

        // 캐시된 결과가 있고 아직 유효하면 캐시 반환
        deviceListCache?.let { cached ->
            if (now - lastUpdateTime < CACHE_TIMEOUT) {
                Log.d(TAG, "캐시된 USB 디바이스 목록 반환: ${cached.size}개")
                return cached
            }
        }

        // 캐시가 없거나 만료되었으면 새로 가져오기
        val devices = getUsbDevicesInternal()
        deviceListCache = devices
        lastUpdateTime = now

        return devices
    }

    private fun getUsbDevicesInternal(): List<UsbDevice> {
        val allDevices = usbManager.deviceList.values.toList()
        Log.d(TAG, "총 USB 디바이스 발견: ${allDevices.size}")

        return allDevices.filter { device ->
            val isCamera = isCameraDevice(device)
            Log.d(TAG, "디바이스 ${device.deviceName}가 카메라인지: $isCamera")
            isCamera
        }
    }

    private fun updateDeviceList() {
        deviceListCache = null
        val cameraDevices = getCameraDevices()
        _connectedDevices.value = cameraDevices
        Log.d(TAG, "카메라 디바이스 목록 업데이트: ${cameraDevices.size}개")
    }

    private fun isCameraDevice(device: UsbDevice): Boolean {
        // USB 인터페이스 클래스 확인
        for (i in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(i)
            if (usbInterface.interfaceClass == 6 || // Still Image Capture Device (PTP)
                usbInterface.interfaceClass == 255 || // Vendor Specific
                usbInterface.interfaceClass == 8
            ) { // Mass Storage
                return true
            }
        }

        // 제조사 ID로 확인
        if (device.vendorId in KNOWN_CAMERA_VENDOR_IDS) {
            return true
        }

        // 디바이스 이름으로 확인
        val deviceName = device.deviceName.lowercase()
        return deviceName.contains("camera") ||
                deviceName.contains("canon") ||
                deviceName.contains("nikon") ||
                deviceName.contains("sony") ||
                deviceName.contains("fuji")
    }

    fun requestPermission(device: UsbDevice) {
        Log.d(TAG, "USB 권한 요청 시작: ${device.deviceName}")

        if (usbManager.hasPermission(device)) {
            Log.d(TAG, "이미 권한이 있습니다: ${device.deviceName}")
            _hasPermission.value = true
            currentDevice = device
            return
        }

        val permissionIntent = PendingIntent.getBroadcast(
            context,
            device.deviceId,
            Intent(ACTION_USB_PERMISSION).apply {
                putExtra(UsbManager.EXTRA_DEVICE, device)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        usbManager.requestPermission(device, permissionIntent)
    }

    fun setDisconnectionCallback(callback: (UsbDevice) -> Unit) {
        disconnectionCallback = callback
    }

    fun setPermissionGrantedCallback(callback: (UsbDevice) -> Unit) {
        permissionGrantedCallback = callback
    }

    fun getCurrentDevice(): UsbDevice? = currentDevice

    fun cleanup() {
        try {
            context.unregisterReceiver(usbReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "USB 리시버 등록 해제 실패", e)
        }
    }
}