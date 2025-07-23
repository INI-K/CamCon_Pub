package com.inik.camcon.data.datasource.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.inik.camcon.CameraNative
import com.inik.camcon.domain.model.CameraCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsbCameraManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val _connectedDevices = MutableStateFlow<List<UsbDevice>>(emptyList())
    val connectedDevices: StateFlow<List<UsbDevice>> = _connectedDevices.asStateFlow()

    private val _hasUsbPermission = MutableStateFlow(false)
    val hasUsbPermission: StateFlow<Boolean> = _hasUsbPermission.asStateFlow()

    private val _cameraCapabilities = MutableStateFlow<CameraCapabilities?>(null)
    val cameraCapabilities: StateFlow<CameraCapabilities?> = _cameraCapabilities.asStateFlow()

    private val _isNativeCameraConnected = MutableStateFlow(false)
    val isNativeCameraConnected: StateFlow<Boolean> = _isNativeCameraConnected.asStateFlow()

    private var currentDevice: UsbDevice? = null
    private var currentConnection: UsbDeviceConnection? = null

    companion object {
        private const val TAG = "USB카메라매니저"
        private const val ACTION_USB_PERMISSION = "com.inik.camcon.USB_PERMISSION"
    }

    // 디바이스 캐시 관련 변수 추가
    private var lastDeviceListUpdate = 0L
    private val deviceListCacheTimeout = 1000L // 1초간 캐시 유효
    private var cachedDeviceList: List<UsbDevice>? = null

    // 카메라 기능 정보 중복 호출 방지를 위한 플래그 추가
    private var isFetchingCapabilities = false

    // 네이티브 카메라 초기화 중복 방지를 위한 플래그 추가
    private var isInitializingNativeCamera = false

    private val knownCameraVendorIds = listOf(
        // 주요 DSLR/미러리스 제조사
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

        // 액션카메라/드론 제조사
        0x2770, // Insta360
        0x2207, // DJI
        0x2731, // GoPro
        0x27C6, // Garmin VIRB
        0x2B1E, // YI Technology

        // 시네마/프로 카메라 제조사
        0x1B8C, // Blackmagic Design
        0x1954, // RED Digital Cinema
        0x2040, // ARRI
        0x3D8D, // Z CAM
        0x0451, // ZCAM
        0x2E04, // Kinefinity

        // 기타 카메라 제조사
        0x040A, // Kodak
        0x03F0, // HP
        0x05AC, // Apple
        0x0A5C, // Broadcom (일부 카메라에서 사용)
        0x0B05, // ASUS (일부 카메라에서 사용)
        0x413C, // Dell (일부 카메라에서 사용)
        0x0E8D, // MediaTek (일부 카메라에서 사용)
        0x1004, // LG Electronics
        0x19D2, // ZTE (일부 카메라에서 사용)
        0x2717, // Xiaomi (일부 카메라에서 사용)
        0x22B8, // Motorola (일부 카메라에서 사용)
        0x18D1, // Google (Pixel 카메라)
        0x0BB4, // HTC
        0x1D4D, // Peaq
        0x0FCE, // Sony Ericsson
        0x0409, // NEC
        0x0930, // Toshiba
        0x04F2, // Chicony Electronics
        0x058F, // Alcor Micro
        0x0C45, // Microdia
        0x046D, // Logitech (웹캠 등)
        0x1E4E, // Cubeternet
        0x174F, // Syntek
        0x05E3, // Genesys Logic
        0x1415, // Nam Tai E&E Products
        0x090C, // Silicon Motion
        0x13FE, // Kingston Technology
        0x0781, // SanDisk

        // 전문 방송/스튜디오 장비
        0x2A70, // OnSetLighting
        0x2BF9, // AVMATRIX
        0x1FCF, // Garmin
        0x1B3F, // Generalplus Technology
        0x2304, // Pinnacle Systems
        0x1131, // Integrated Technology Express
        0x0CCD, // TerraTec Electronic
        0x2040, // Hauppauge
        0x1164, // YUAN High-Tech Development
        0x0EB1, // WIS Technologies
        0x1F4D, // G-Technology
        0x059F, // LaCie
        0x1058, // Western Digital
        0x04E6, // SCM Microsystems
        0x0D64, // DXG Technology

        // 스마트폰 제조사 (카메라 기능)
        0x05C6, // Qualcomm
        0x1BBB, // T & A Mobile Phones
        0x2A45, // Meizu
        0x2916, // Android
        0x1F53, // NextIO
        0x2232, // Silicon Integrated Systems
        0x1004, // LG Electronics
        0x04E8, // Samsung Electronics
        0x12D1, // Huawei Technologies
        0x19A5, // BYD Company
        0x1D09, // TechFaith
        0x201E, // Haier
        0x8087  // Intel
    )

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    synchronized(this) {
                        val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            device?.let {
                                Log.d(TAG, "USB 권한이 승인되었습니다: ${it.deviceName}")
                                _hasUsbPermission.value = true
                                currentDevice = it
                                connectToCamera(it)
                            }
                        } else {
                            Log.d(TAG, "USB 권한이 거부되었습니다: ${device?.deviceName}")
                            _hasUsbPermission.value = false
                        }
                    }
                }

                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    device?.let {
                        Log.d(TAG, "USB 디바이스가 연결되었습니다: ${it.deviceName}")
                        if (isCameraDevice(it)) {
                            updateDeviceList()
                        }
                    }
                }

                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    device?.let {
                        Log.d(TAG, "USB 디바이스가 분리되었습니다: ${it.deviceName}")
                        if (it == currentDevice) {
                            _hasUsbPermission.value = false
                            currentDevice = null
                            currentConnection?.close()
                            currentConnection = null
                        }
                        updateDeviceList()
                    }
                }
            }
        }
    }

    init {
        registerUsbReceiver()
        initializeDeviceList()
    }

    private fun registerUsbReceiver() {
        // 커스텀 USB 권한 브로드캐스트 (앱 전용)
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

        // 시스템 USB 브로드캐스트 (시스템 전용)
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

    private fun initializeDeviceList() {
        Log.d(TAG, "USB 디바이스 목록 초기화 시작")

        // 디바이스 목록을 한 번만 가져와서 캐시
        val devices = getUsbDevicesInternal()
        cachedDeviceList = devices
        lastDeviceListUpdate = System.currentTimeMillis()

        Log.d(TAG, "초기 USB 디바이스 발견: ${devices.size}개")

        // 연결된 디바이스 목록 업데이트
        _connectedDevices.value = devices

        // 권한 확인 및 연결 시도
        devices.forEach { device ->
            Log.d(
                TAG,
                "디바이스: ${device.deviceName}, 제조사ID: 0x${device.vendorId.toString(16)}, 제품ID: 0x${
                    device.productId.toString(16)
                }"
            )

            val hasPermission = usbManager.hasPermission(device)
            Log.d(TAG, "디바이스 ${device.deviceName}에 대한 권한 상태: $hasPermission")

            if (!hasPermission) {
                Log.d(TAG, "디바이스에 대한 권한을 요청합니다: ${device.deviceName}")
                requestPermission(device)
            } else {
                Log.d(TAG, "이미 권한이 있는 디바이스입니다: ${device.deviceName}")
                _hasUsbPermission.value = true
                currentDevice = device
                connectToCamera(device)
            }
        }
    }

    private fun checkInitialDevices() {
        // 이 메서드는 더 이상 필요 없음 - initializeDeviceList()에서 처리
        Log.d(TAG, "checkInitialDevices() 호출됨 - initializeDeviceList()에서 이미 처리됨")
    }

    fun getCameraDevices(): List<UsbDevice> {
        // 캐시된 결과가 있고 아직 유효하면 캐시 반환
        val now = System.currentTimeMillis()
        cachedDeviceList?.let { cached ->
            if (now - lastDeviceListUpdate < deviceListCacheTimeout) {
                Log.d(TAG, "캐시된 USB 디바이스 목록 반환: ${cached.size}개")
                return cached
            }
        }

        // 캐시가 없거나 만료되었으면 새로 가져오기
        val devices = getUsbDevicesInternal()
        cachedDeviceList = devices
        lastDeviceListUpdate = now

        return devices
    }

    private fun getUsbDevicesInternal(): List<UsbDevice> {
        val allDevices = usbManager.deviceList.values.toList()
        Log.d(TAG, "총 USB 디바이스 발견: ${allDevices.size}")

        allDevices.forEach { device ->
            Log.d(TAG, "디바이스: ${device.deviceName}")
            Log.d(TAG, "  제조사ID: 0x${device.vendorId.toString(16)}")
            Log.d(TAG, "  제품ID: 0x${device.productId.toString(16)}")
            Log.d(TAG, "  인터페이스 개수: ${device.interfaceCount}")

            for (i in 0 until device.interfaceCount) {
                val usbInterface = device.getInterface(i)
                Log.d(
                    TAG,
                    "  인터페이스 $i: 클래스=${usbInterface.interfaceClass}, 서브클래스=${usbInterface.interfaceSubclass}, 프로토콜=${usbInterface.interfaceProtocol}"
                )
            }
        }

        return allDevices.filter { device ->
            val isCamera = isCameraDevice(device)
            Log.d(TAG, "디바이스 ${device.deviceName}가 카메라인지: $isCamera")
            isCamera
        }
    }

    private fun updateDeviceList() {
        // 캐시 무효화
        cachedDeviceList = null

        val cameraDevices = getCameraDevices()
        _connectedDevices.value = cameraDevices
        Log.d(TAG, "카메라 디바이스 목록 업데이트: ${cameraDevices.size}개")
    }

    private fun isCameraDevice(device: UsbDevice): Boolean {
        // 먼저 USB 인터페이스 클래스 확인
        for (i in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(i)
            // 클래스 6 = Still Image Capture Device (PTP)
            // 클래스 255 = Vendor Specific (많은 카메라가 사용)
            // 클래스 8 = Mass Storage (일부 카메라가 사용)
            if (usbInterface.interfaceClass == 6 ||
                usbInterface.interfaceClass == 255 ||
                usbInterface.interfaceClass == 8
            ) {
                Log.d(
                    TAG,
                    "인터페이스 클래스로 카메라로 식별됨: ${usbInterface.interfaceClass}"
                )
                return true
            }
        }

        // 제조사 ID로 확인 (더 관대하게)
        val isKnownVendor = device.vendorId in knownCameraVendorIds
        if (isKnownVendor) {
            Log.d(
                TAG,
                "제조사 ID로 카메라로 식별됨: 0x${device.vendorId.toString(16)}"
            )
            return true
        }

        // 디바이스 이름으로 확인
        val deviceName = device.deviceName?.lowercase() ?: ""
        val hasCamera = deviceName.contains("camera") ||
                deviceName.contains("canon") ||
                deviceName.contains("nikon") ||
                deviceName.contains("sony") ||
                deviceName.contains("fuji")

        if (hasCamera) {
            Log.d(TAG, "디바이스 이름으로 카메라로 식별됨: ${device.deviceName}")
            return true
        }

        Log.d(TAG, "카메라로 식별되지 않은 디바이스: ${device.deviceName}")
        return false
    }

    fun requestPermission(device: UsbDevice) {
        Log.d(TAG, "USB 권한 요청 시작: ${device.deviceName}")

        // 이미 권한이 있는지 다시 한번 확인
        if (usbManager.hasPermission(device)) {
            Log.d(TAG, "이미 권한이 있습니다: ${device.deviceName}")
            _hasUsbPermission.value = true
            currentDevice = device
            connectToCamera(device)
            return
        }

        val permissionIntent = PendingIntent.getBroadcast(
            context,
            device.deviceId, // 디바이스별 고유 ID 사용
            Intent(ACTION_USB_PERMISSION).apply {
                putExtra(UsbManager.EXTRA_DEVICE, device)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        Log.d(TAG, "USB 권한 대화상자를 표시합니다: ${device.deviceName}")
        usbManager.requestPermission(device, permissionIntent)
    }

    private fun connectToCamera(device: UsbDevice) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "백그라운드에서 카메라 연결 시작: ${device.deviceName}")

                val connection = usbManager.openDevice(device)
                connection?.let {
                    currentConnection = it
                    val fd = it.fileDescriptor
                    Log.d(TAG, "카메라에 연결되었습니다. 파일 디스크립터: $fd")

                    // USB 디바이스 정보 로깅
                    Log.d(TAG, "디바이스 정보:")
                    Log.d(TAG, "  이름: ${device.deviceName}")
                    Log.d(TAG, "  제조사ID: 0x${device.vendorId.toString(16)}")
                    Log.d(TAG, "  제품ID: 0x${device.productId.toString(16)}")
                    Log.d(TAG, "  클래스: ${device.deviceClass}")
                    Log.d(TAG, "  서브클래스: ${device.deviceSubclass}")
                    Log.d(TAG, "  프로토콜: ${device.deviceProtocol}")

                    // 인터페이스 정보
                    for (i in 0 until device.interfaceCount) {
                        val intf = device.getInterface(i)
                        Log.d(TAG, "  인터페이스 $i:")
                        Log.d(TAG, "    클래스: ${intf.interfaceClass}")
                        Log.d(TAG, "    서브클래스: ${intf.interfaceSubclass}")
                        Log.d(TAG, "    프로토콜: ${intf.interfaceProtocol}")
                        Log.d(TAG, "    엔드포인트 수: ${intf.endpointCount}")
                    }

                    // 네이티브 카메라 초기화 시도
                    initializeNativeCamera(fd)
                } ?: run {
                    Log.e(TAG, "USB 디바이스 열기 실패: ${device.deviceName}")
                    withContext(Dispatchers.Main) {
                        _isNativeCameraConnected.value = false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "카메라 연결에 실패했습니다", e)
                withContext(Dispatchers.Main) {
                    _isNativeCameraConnected.value = false
                }
            }
        }
    }

    private suspend fun initializeNativeCamera(fd: Int) = withContext(Dispatchers.IO) {
        try {
            // 중복 초기화 방지
            if (isInitializingNativeCamera) {
                Log.d(TAG, "네이티브 카메라 초기화가 이미 진행 중입니다. FD: $fd")
                return@withContext
            }

            isInitializingNativeCamera = true

            val nativeLibDir = context.applicationInfo.nativeLibraryDir
            Log.d(TAG, "네이티브 라이브러리 디렉토리: $nativeLibDir")

            // USB 연결 안정화를 위한 짧은 지연
            delay(500)

            // 네이티브 함수 호출을 IO 스레드에서 실행
            val result = CameraNative.initCameraWithFd(fd, nativeLibDir)
            
            if (result == 0) { // GP_OK
                Log.d(TAG, "네이티브 카메라 초기화 성공")
                withContext(Dispatchers.Main) {
                    _isNativeCameraConnected.value = true
                }

                // 카메라 요약 정보 가져오기 - 한 번만 호출
                val summary = CameraNative.getCameraSummary()
                Log.d(TAG, "카메라 요약: $summary")

                // 카메라 기능 정보 가져오기 - 중복 방지하면서 한 번만 실행
                fetchCameraCapabilitiesIfNeeded()

            } else if (result == -52) { // GP_ERROR_IO_USB_FIND
                Log.e(TAG, "USB 포트에서 카메라를 찾을 수 없음. 재시도 중...")

                // USB 재초기화 시도
                currentDevice?.let { device ->
                    // 기존 연결 닫기
                    delay(1000)

                    // 다시 연결 시도
                    val connection = usbManager.openDevice(device)
                    connection?.let { conn ->
                        val newFd = conn.fileDescriptor
                        Log.d(TAG, "USB 재연결 시도 with FD: $newFd")

                        val retryResult = CameraNative.initCameraWithFd(newFd, nativeLibDir)
                        if (retryResult == 0) {
                            Log.d(TAG, "재시도 성공!")
                            withContext(Dispatchers.Main) {
                                _isNativeCameraConnected.value = true
                            }
                            // 중복 방지하면서 capabilities 가져오기
                            fetchCameraCapabilitiesIfNeeded()
                        } else {
                            Log.e(TAG, "재시도도 실패: $retryResult")
                            tryGeneralInit()
                        }
                    }
                } ?: tryGeneralInit()

            } else {
                Log.e(TAG, "네이티브 카메라 초기화 실패: $result")
                withContext(Dispatchers.Main) {
                    _isNativeCameraConnected.value = false
                }
                
                // USB 초기화가 실패하면 일반 초기화 시도
                tryGeneralInit()
            }
        } catch (e: Exception) {
            Log.e(TAG, "네이티브 카메라 초기화 중 예외 발생", e)
            withContext(Dispatchers.Main) {
                _isNativeCameraConnected.value = false
            }
            tryGeneralInit()
        } finally {
            isInitializingNativeCamera = false
        }
    }

    private suspend fun tryGeneralInit() = withContext(Dispatchers.IO) {
        Log.d(TAG, "일반 카메라 초기화 시도...")

        try {
            // USB FD를 사용하지 않는 일반 초기화
            val generalResult = CameraNative.initCamera()
            Log.d(TAG, "일반 카메라 초기화 결과: $generalResult")

            if (generalResult.contains("OK", ignoreCase = true)) {
                withContext(Dispatchers.Main) {
                    _isNativeCameraConnected.value = true
                }
                // 중복 방지하면서 capabilities 가져오기
                fetchCameraCapabilitiesIfNeeded()
            } else {
                Log.e(TAG, "일반 초기화도 실패: $generalResult")

                // 마지막으로 카메라 감지 시도
                val detected = CameraNative.detectCamera()
                Log.d(TAG, "카메라 감지 결과: $detected")

                if (!detected.contains("No camera detected")) {
                    // 카메라가 감지되면 다시 초기화 시도
                    delay(1000)
                    val finalResult = CameraNative.initCamera()
                    if (finalResult.contains("OK", ignoreCase = true)) {
                        withContext(Dispatchers.Main) {
                            _isNativeCameraConnected.value = true
                        }
                        // 중복 방지하면서 capabilities 가져오기
                        fetchCameraCapabilitiesIfNeeded()
                    } else {
                        // 일반 초기화 실패 시 최종 처리
                        withContext(Dispatchers.Main) {
                            _isNativeCameraConnected.value = false
                        }
                    }
                } else {
                    // 카메라 감지 실패 시 최종 처리
                    withContext(Dispatchers.Main) {
                        _isNativeCameraConnected.value = false
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "일반 카메라 초기화 중 예외 발생", e)
            withContext(Dispatchers.Main) {
                _isNativeCameraConnected.value = false
            }
        }
    }

    // 중복 방지가 있는 capabilities 가져오기 함수
    private suspend fun fetchCameraCapabilitiesIfNeeded() {
        // 이미 가져오는 중이면 건너뛰기
        if (isFetchingCapabilities) {
            Log.d(TAG, "카메라 기능 정보 가져오기 중복 호출 방지")
            return
        }

        isFetchingCapabilities = true
        try {
            fetchCameraCapabilities()
        } finally {
            isFetchingCapabilities = false
        }
    }

    private suspend fun fetchCameraCapabilities() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "카메라 기능 정보 가져오기 시작")

            // 카메라 능력 정보 가져오기 - 네이티브 호출을 IO 스레드에서
            val abilitiesJson = CameraNative.listCameraAbilities()
            Log.d(TAG, "카메라 능력 정보: $abilitiesJson")

            // 카메라 위젯 정보 가져오기 (설정 가능한 옵션들) - 무거운 작업
            val widgetJson = CameraNative.buildWidgetJson()
            Log.d(TAG, "카메라 위젯 정보 길이: ${widgetJson.length}")

            // JSON 파싱하여 CameraCapabilities 객체 생성 - 무거운 작업
            val capabilities = parseCameraCapabilities(abilitiesJson, widgetJson)

            // UI 업데이트만 메인 스레드에서
            withContext(Dispatchers.Main) {
                _cameraCapabilities.value = capabilities
            }

            Log.d(TAG, "카메라 기능 정보 업데이트 완료")

        } catch (e: Exception) {
            Log.e(TAG, "카메라 기능 정보 가져오기 실패", e)
            withContext(Dispatchers.Main) {
                _cameraCapabilities.value = null
            }
        }
    }

    private fun parseCameraCapabilities(abilitiesJson: String, widgetJson: String): CameraCapabilities {
        return try {
            val abilitiesObj = JSONObject(abilitiesJson)
            
            // 기본 기능들 파싱
            val captureImage = abilitiesObj.optBoolean("captureImage", false)
            val captureVideo = abilitiesObj.optBoolean("captureVideo", false)
            val capturePreview = abilitiesObj.optBoolean("capturePreview", false)
            val config = abilitiesObj.optBoolean("config", false)
            val triggerCapture = abilitiesObj.optBoolean("triggerCapture", false)
            
            // 파일 작업
            val fileDownload = abilitiesObj.optBoolean("fileDownload", false)
            val fileDelete = abilitiesObj.optBoolean("fileDelete", false)
            val filePreview = abilitiesObj.optBoolean("filePreview", false)
            
            // 폴더 작업
            val deleteAll = abilitiesObj.optBoolean("deleteAll", false)
            val putFile = abilitiesObj.optBoolean("putFile", false)
            val makeDir = abilitiesObj.optBoolean("makeDir", false)
            
            // 위젯에서 설정 가능한 기능들 추출
            val hasAutofocus = widgetJson.contains("autofocus", ignoreCase = true)
            val hasManualFocus = widgetJson.contains("manualfocus", ignoreCase = true)
            val hasLiveView = widgetJson.contains("liveview", ignoreCase = true) || capturePreview
            val hasTimelapse = captureImage && triggerCapture
            val hasBracketing = captureImage && config
            val hasBurstMode = captureImage && triggerCapture
            
            // ISO 설정 확인
            val isoSettings = extractSettingOptions(widgetJson, "iso")
            val shutterSpeedSettings = extractSettingOptions(widgetJson, "shutter")
            val apertureSettings = extractSettingOptions(widgetJson, "aperture")
            val whiteBalanceSettings = extractSettingOptions(widgetJson, "whitebalance")
            
            CameraCapabilities(
                model = abilitiesObj.optString("model", "알 수 없음"),
                
                // 기본 촬영 기능
                canCapturePhoto = captureImage,
                canCaptureVideo = captureVideo,
                canLiveView = hasLiveView,
                canTriggerCapture = triggerCapture,
                
                // 고급 촬영 기능
                supportsBurstMode = hasBurstMode,
                supportsTimelapse = hasTimelapse,
                supportsBracketing = hasBracketing,
                supportsBulbMode = widgetJson.contains("bulb", ignoreCase = true),
                
                // 초점 기능
                supportsAutofocus = hasAutofocus,
                supportsManualFocus = hasManualFocus,
                supportsFocusPoint = hasManualFocus,
                
                // 파일 관리
                canDownloadFiles = fileDownload,
                canDeleteFiles = fileDelete,
                canPreviewFiles = filePreview,
                
                // 설정 가능한 옵션들
                availableIsoSettings = isoSettings,
                availableShutterSpeeds = shutterSpeedSettings,
                availableApertures = apertureSettings,
                availableWhiteBalanceSettings = whiteBalanceSettings,
                
                // 기타
                supportsRemoteControl = config,
                supportsConfigChange = config,
                batteryLevel = null // 추후 구현
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "카메라 기능 정보 파싱 실패", e)
            CameraCapabilities(
                model = "파싱 실패",
                canCapturePhoto = false,
                canCaptureVideo = false,
                canLiveView = false,
                canTriggerCapture = false,
                supportsBurstMode = false,
                supportsTimelapse = false,
                supportsBracketing = false,
                supportsBulbMode = false,
                supportsAutofocus = false,
                supportsManualFocus = false,
                supportsFocusPoint = false,
                canDownloadFiles = false,
                canDeleteFiles = false,
                canPreviewFiles = false,
                availableIsoSettings = emptyList(),
                availableShutterSpeeds = emptyList(),
                availableApertures = emptyList(),
                availableWhiteBalanceSettings = emptyList(),
                supportsRemoteControl = false,
                supportsConfigChange = false,
                batteryLevel = null
            )
        }
    }

    private fun extractSettingOptions(widgetJson: String, settingName: String): List<String> {
        return try {
            val json = JSONObject(widgetJson)
            val options = mutableListOf<String>()
            
            // JSON에서 해당 설정의 선택지들을 재귀적으로 찾기
            extractOptionsFromJson(json, settingName.lowercase(), options)
            
            options.distinct()
        } catch (e: Exception) {
            Log.w(TAG, "$settingName 설정 옵션 추출 실패", e)
            emptyList()
        }
    }

    private fun extractOptionsFromJson(json: JSONObject, settingName: String, options: MutableList<String>) {
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.opt(key)

            if (key.lowercase().contains(settingName) && json.has("choices")) {
                val choices = json.optJSONArray("choices")
                if (choices != null) {
                    for (i in 0 until choices.length()) {
                        options.add(choices.optString(i))
                    }
                }
            } else if (value is JSONObject) {
                extractOptionsFromJson(value, settingName, options)
            } else if (value is org.json.JSONArray) {
                for (i in 0 until value.length()) {
                    val item = value.opt(i)
                    if (item is JSONObject) {
                        extractOptionsFromJson(item, settingName, options)
                    }
                }
            }
        }
    }

    /**
     * 현재 연결된 카메라의 기능 정보를 새로고침합니다.
     */
    fun refreshCameraCapabilities() {
        if (_isNativeCameraConnected.value) {
            CoroutineScope(Dispatchers.IO).launch {
                fetchCameraCapabilitiesIfNeeded()
            }
        }
    }

    /**
     * 카메라 연결 해제
     */
    fun disconnectCamera() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (_isNativeCameraConnected.value) {
                    Log.d(TAG, "카메라 PC 모드 완전 종료 시작")

                    // 카메라 이벤트 리스너 중지
                    try {
                        CameraNative.stopListenCameraEvents()
                        Log.d(TAG, "카메라 이벤트 리스너 중지 완료")
                    } catch (e: Exception) {
                        Log.w(TAG, "카메라 이벤트 리스너 중지 중 오류 (정상적일 수 있음)", e)
                    }

                    // 카메라 연결 완전 해제 (PC 모드 종료 포함)
                    CameraNative.closeCamera()
                    Log.d(TAG, "카메라 네이티브 연결 해제 완료")

                    withContext(Dispatchers.Main) {
                        _isNativeCameraConnected.value = false
                        _cameraCapabilities.value = null
                    }
                }

                currentDevice = null
                withContext(Dispatchers.Main) {
                    _hasUsbPermission.value = false
                }

                currentConnection?.close()
                currentConnection = null

                Log.d(TAG, "카메라 연결 해제 완료 - PC 모드에서 완전히 해제됨")
            } catch (e: Exception) {
                Log.e(TAG, "카메라 연결 해제 중 오류", e)

                // 오류가 발생해도 상태는 초기화
                withContext(Dispatchers.Main) {
                    _isNativeCameraConnected.value = false
                    _cameraCapabilities.value = null
                    _hasUsbPermission.value = false
                }
                currentDevice = null
                currentConnection?.close()
                currentConnection = null
            }
        }
    }

    fun getCurrentDevice(): UsbDevice? = currentDevice

    fun getFileDescriptor(): Int? {
        return currentDevice?.let { device ->
            try {
                // 기존 연결이 있다면 재사용
                currentConnection?.let { existingConnection ->
                    Log.d(TAG, "기존 연결 재사용: FD=${existingConnection.fileDescriptor}")
                    return existingConnection.fileDescriptor
                }

                // 새 연결 생성
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

    fun cleanup() {
        try {
            context.unregisterReceiver(usbReceiver)
            disconnectCamera()
        } catch (e: Exception) {
            Log.w(TAG, "USB 리시버 등록 해제 실패", e)
        }
    }
}
